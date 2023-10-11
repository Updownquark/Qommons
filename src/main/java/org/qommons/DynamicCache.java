package org.qommons;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.qommons.collect.ListenerList;
import org.qommons.ex.ExSupplier;
import org.qommons.threading.QommonsTimer;

/**
 * <p>
 * This class manages instances of some resource, allowing thread-safe sharing of a limited set of instances among any number of threads. It
 * provides features to configure how many resources may exist at a time and provides a cache of idle values with optional dynamic purging
 * after an idle lifetime limit.
 * </p>
 * 
 * <p>
 * This class makes an effort to block as little as possible unless specifically asked to wait for an available resource.
 * </p>
 * 
 * @param <T> The type of the resource being cached
 * @param <X> The type of exception that may be thrown when creating the resource
 */
public class DynamicCache<T, X extends Throwable> {
	/**
	 * A resource retrieved from a {@link DynamicCache}
	 * 
	 * @param <T> The type of the resource
	 */
	public interface Resource<T> extends Supplier<T>, Transaction {}

	private final ExSupplier<T, X> theCreator;
	private final Consumer<? super T> theDestroyer;
	private final int thePersistentCacheLimit;
	private final int theLiveResourceLimit;

	private volatile int theLiveResourceCount;
	private final ListenerList<IdleResource<T>> theIdleResourceCache;
	private final ListenerList<ResourceWaiter<T>> theWaiters;

	private final QommonsTimer.TaskHandle theIdleLifetimeEnforcer;

	DynamicCache(ExSupplier<T, X> creator, Consumer<? super T> destroyer, Duration idleLifetime, int persistentCacheLimit,
		int liveResourceLimit) {
		theCreator = creator;
		theDestroyer = destroyer;
		thePersistentCacheLimit = persistentCacheLimit;
		theLiveResourceLimit = liveResourceLimit;

		theIdleResourceCache = ListenerList.build().build();
		theWaiters = ListenerList.build().build();

		if (idleLifetime != null) {
			IdleLifetimeEnforcer<T> enforcer = new IdleLifetimeEnforcer<>(this, idleLifetime);
			theIdleLifetimeEnforcer = QommonsTimer.getCommonInstance().build(enforcer, TimeUtils.div2(idleLifetime), false);
			enforcer.withHandle(theIdleLifetimeEnforcer);
			theIdleLifetimeEnforcer.setActive(true);
		} else
			theIdleLifetimeEnforcer = null;
	}

	/**
	 * This class enforces the {@link Builder#withIdleLifetime(Duration) idle lifetime}. It's static and only has a weak reference to the
	 * cache so the can be garbage-collected without killing the timer.
	 */
	private static class IdleLifetimeEnforcer<T> implements Runnable {
		private final WeakReference<DynamicCache<T, ?>> theCache;
		private final Duration theLifetime;
		private QommonsTimer.TaskHandle theTaskHandle;

		IdleLifetimeEnforcer(DynamicCache<T, ?> cache, Duration lifetime) {
			theCache = new WeakReference<>(cache);
			theLifetime = lifetime;
		}

		void withHandle(QommonsTimer.TaskHandle taskHandle) {
			theTaskHandle = taskHandle;
		}

		@Override
		public void run() {
			DynamicCache<T, ?> cache = theCache.get();
			if (cache == null) {
				theTaskHandle.setActive(false);
				return;
			}

			long now = System.currentTimeMillis();
			long idledBefore = now - theLifetime.toMillis();
			cache.purgeResources(idledBefore);
		}
	}

	/**
	 * Retrieves a resource from this cache, waiting as long as necessary for one to become available if cache limits have been reached
	 * 
	 * @return The resource containing the value and also acting as a transaction to be {@link Transaction#close() closed} when this usage
	 *         of the resource is finished
	 * @throws X If there was no available cached value and creation of a new value failed
	 */
	public Resource<T> get() throws X {
		return get(Integer.MAX_VALUE);
	}

	/**
	 * Retrieves a resource from this cache, waiting up to the given amount of time for one to become available if cache limits have been
	 * reached
	 * 
	 * @param timeout The length of time (in milliseconds) to wait for a resource to become available if cache limits have been reached
	 * @return The resource containing the value and also acting as a transaction to be {@link Transaction#close() closed} when this usage
	 *         of the resource is finished, or null if cache limits are reached and no resource became available during the wait time.
	 * @throws X If there was no available cached value and creation of a new value failed
	 */
	public Resource<T> get(int timeout) throws X {
		// First see if there's one available right away
		IdleResource<T> resource = theIdleResourceCache.pollValue(0);
		if (resource != null)
			return new ActiveResource(resource.value);
		// If not, we need to be synchronized and more complicated
		return getOrCreateOrWait(timeout);
	}

	/**
	 * Purges idle resources older than a given time
	 * 
	 * @param idledBefore The epoch time before which resources will be deleted
	 * @return The number of idle resources purged from the cache
	 */
	public int purgeResources(long idledBefore) {
		ListenerList.Element<IdleResource<T>> resource = theIdleResourceCache.peekFirst();
		int purged = 0;
		while (resource != null && resource.get().persistedTime < idledBefore) {
			if (resource.remove()) {
				purged++;
				try {
					theDestroyer.accept(resource.get().value);
				} catch (RuntimeException e) {
					e.printStackTrace();
				}
			}
			resource = theIdleResourceCache.peekFirst();
		}
		return purged;
	}

	/**
	 * <p>
	 * If this cache was configured with an {@link Builder#withIdleLifetime(Duration) idle lifetime}, there is a timer for this cache that
	 * runs periodically to clean up idle resources.
	 * </p>
	 * <p>
	 * This method stops that timer to release resources.
	 * </p>
	 */
	public void stopLifetimeMonitor() {
		if (theIdleLifetimeEnforcer != null)
			theIdleLifetimeEnforcer.setActive(false);
	}

	/** @return The number of idle resources in this cache */
	public int getIdleResourceCount() {
		return theIdleResourceCache.size();
	}

	/** @return The number of resources managed by this cache that are currently in use */
	public int getLiveResourceCount() {
		return theLiveResourceCount;
	}

	/** @return The number of threads that are currently waiting for a resource to become available */
	public int getWaitingThreadCount() {
		return theWaiters.size();
	}

	private synchronized Resource<T> getOrCreateOrWait(int timeout) throws X {
		// Check the cache again now that we're locked
		IdleResource<T> resource = theIdleResourceCache.pollValue(0);
		if (resource != null)
			return new ActiveResource(resource.value);
		// If we can create one, do that
		if (theLiveResourceCount < theLiveResourceLimit) {
			T value = theCreator.get();
			theLiveResourceCount++;
			return new ActiveResource(value);
		}
		// If the limit of live resources has been reached, either return null or wait for one to become available
		if (timeout <= 0)
			return null;
		ResourceWaiter<T> waiter = new ResourceWaiter<>(Thread.currentThread());
		ListenerList.Element<ResourceWaiter<T>> remove = theWaiters.add(waiter, false);
		try {
			Thread.sleep(timeout);
		} catch (InterruptedException e) {
			// Normal behavior when a resource becomes available, nothing to do
		}
		// Remove the waiter if we reached our timeout. If already removed, this does nothing.
		if (remove.remove() && waiter.resource == null) {
			// The waiter has been removed in the released(T) method, but hasn't been given the resource yet.
			// This would be a resource leak if we didn't wait for the resource to show up
			try {
				Thread.sleep(5); // Should be almost instant, so this should be fine
			} catch (InterruptedException e) {
				// Normal behavior
			}
		}
		return waiter.resource; // If we were given a resource, return it
	}

	void released(T value) {
		// First, see if anyone is waiting for resources because it's quick
		if (giveToWaiter(value))
			return;
		ResourceWaiter<T> waiter = theWaiters.pollValue(0);
		if (waiter != null) {
			waiter.resource = new ActiveResource(value);
			try {
				waiter.thread.interrupt();
			} catch (SecurityException e) {
				// Hmm. Well, if we can't interrupt the thread, I guess it will wait forever or until its timeout
				// and there's not really anything we can do about that. But don't hold up the resource.
				waiter.resource = null;
				waiter = theWaiters.pollValue(0);
			}
		} else // If not, we need to be synchronized and more complicated
			reCacheIfPermitted(value);
	}

	private boolean giveToWaiter(T value) {
		ResourceWaiter<T> waiter = theWaiters.pollValue(0);
		while (waiter != null) {
			waiter.resource = new ActiveResource(value);
			try {
				waiter.thread.interrupt();
				return true;
			} catch (SecurityException e) {
				// Hmm. Well, if we can't interrupt the thread, I guess it will wait forever or until its timeout
				// and there's not really anything we can do about that. But don't hold up the resource.
				waiter.resource = null;
				waiter = theWaiters.pollValue(0);
			}
		}
		return false;
	}

	private synchronized void reCacheIfPermitted(T value) {
		// Check for a waiter again now that we're locked
		if (giveToWaiter(value))
			return;

		// The resource is no longer live
		theLiveResourceCount--;

		// Add it back into the persistent cache if it's not full
		if (theIdleResourceCache.size() < thePersistentCacheLimit) {
			/* Add to the beginning of the cache, where it will be pulled off more quickly.
			 * This will cause live items to be re-used more frequently,
			 * while items at the end of the cache will be more likely to become stagnant and be purged.
			 * This will help the cache to maintain only as many resources as it needs to meet demand. */
			theIdleResourceCache.addFirst(new IdleResource<>(value));
		}
		else // There's no place for the resource. Dispose of it.
			theDestroyer.accept(value);
	}

	private class ActiveResource implements Resource<T> {
		private final T theValue;
		private boolean isClosed;

		ActiveResource(T value) {
			theValue = value;
		}

		@Override
		public T get() {
			if (isClosed)
				throw new IllegalStateException("This resource has been released, use the cache to obtain a new one");
			return theValue;
		}

		@Override
		public void close() {
			if (!isClosed) {
				isClosed = true;
				released(theValue);
			}
		}

		@Override
		public String toString() {
			return String.valueOf(theValue);
		}
	}

	private static class IdleResource<T> {
		final T value;
		final long persistedTime;

		IdleResource(T value) {
			this.value = value;
			persistedTime = System.currentTimeMillis();
		}
	}

	private static class ResourceWaiter<T> {
		final Thread thread;
		volatile Resource<T> resource;

		ResourceWaiter(Thread thread) {
			this.thread = thread;
		}
	}

	/** @return A Builder to build a {@link DynamicCache} */
	public static Builder build() {
		return build();
	}

	/**
	 * A builder for a {@link DynamicCache} or for something that uses a cache
	 * 
	 * @param <B> The sub-type of builder
	 */
	public static abstract class AbstractBuilder<B extends AbstractBuilder<B>> {
		private Duration theIdleLifetime;
		private int theIdleCacheLimit;
		private int theLiveResourceLimit;

		/** Creates the builder */
		protected AbstractBuilder() {
			theIdleCacheLimit = 5;
			theLiveResourceLimit = Integer.MAX_VALUE;
		}

		/**
		 * @param idleLifetime The time to allow a resource to sit idle in the cache. After being idle for this amount of time, resources
		 *        may be purged.
		 * @return This builder
		 */
		public B withIdleLifetime(Duration idleLifetime) {
			theIdleLifetime = idleLifetime;
			return (B) this;
		}

		/**
		 * @param idleCacheLimit The number of resources to keep in the cache while they are not being used
		 * @return This builder
		 */
		public B withIdleCacheLimit(int idleCacheLimit) {
			theIdleCacheLimit = idleCacheLimit;
			return (B) this;
		}

		/**
		 * @param liveCacheLimit The number of live resources to permit. After this limit is reached, new requests will block until one is
		 *        released or return null.
		 * @return This builder
		 */
		public B withLiveCacheLimit(int liveCacheLimit) {
			theLiveResourceLimit = liveCacheLimit;
			return (B) this;
		}

		/**
		 * Creates a cache for resources that do not need to be destroyed
		 * 
		 * @param <T> The type of the resource to cache
		 * @param <X> The exception thrown if resource creation fails
		 * @param creator The creator to create new resources for the cache
		 * @return The new cache
		 */
		protected <T, X extends Throwable> DynamicCache<T, X> build(ExSupplier<T, X> creator) {
			return build(creator, __ -> {});
		}

		/**
		 * Creates a cache for resources that need to be destroyed
		 * 
		 * @param <T> The type of the resource to cache
		 * @param <X> The exception thrown if resource creation fails
		 * @param creator The creator to create new resources for the cache
		 * @param destroyer The destroyer to dispose of resources that will no longer be managed by the cache
		 * @return The new cache
		 */
		protected <T, X extends Throwable> DynamicCache<T, X> build(ExSupplier<T, X> creator, Consumer<? super T> destroyer) {
			return new DynamicCache<>(creator, destroyer, theIdleLifetime, theIdleCacheLimit, theLiveResourceLimit);
		}
	}

	/** A builder for a {@link DynamicCache} */
	public static class Builder extends AbstractBuilder<Builder> {
		@Override
		public <T, X extends Throwable> DynamicCache<T, X> build(ExSupplier<T, X> creator) {
			return super.build(creator);
		}

		@Override
		public <T, X extends Throwable> DynamicCache<T, X> build(ExSupplier<T, X> creator, Consumer<? super T> destroyer) {
			return super.build(creator, destroyer);
		}
	}
}

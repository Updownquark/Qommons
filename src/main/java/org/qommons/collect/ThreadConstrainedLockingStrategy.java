package org.qommons.collect;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;

/**
 * <p>
 * A {@link CollectionLockingStrategy} that uses no actual locking, but provides thread safety by restricting modification to a single
 * thread.
 * </p>
 * <p>
 * This class supports obtaining a read lock from any thread, but ANY attempt to obtain a write lock on a thread other than the event thread
 * of the configured {@link ThreadConstraint} (even from {@link #tryLock(boolean, Object)}) will result in an {@link IllegalStateException}.
 * </p>
 * <p>
 * Beyond this constraint, this lock behaves as expected:
 * <ul>
 * <li>Unlimited read (non-exclusive) locks are available from any threads simultaneously while no exclusive (write) lock is held.</li>
 * <li>An unlimited number of exclusive (write) locks may be obtained from the event thread.</li>
 * <li>The {@link Transaction}s obtained by successful lock calls on the event thread must be released on the thread they are obtained from.
 * This is not checked for performance reasons, but failure to do this could result in the lock becoming unusable and blocking forever.</li>
 * </ul>
 * </p>
 */
public class ThreadConstrainedLockingStrategy extends FastFailLockingStrategy {
	private final ThreadConstraint theThreadConstraint;
	// These are package-private for lock release performance
	final AtomicInteger theReadLock;
	int theSafeReadLock;
	volatile int theWriteLock;

	/** @param threading The ThreadConstraint defining on which thread exclusive (write) locks may be obtained */
	private ThreadConstrainedLockingStrategy(ThreadConstraint threading) {
		this(threading, null);
	}

	/**
	 * @param threading The ThreadConstraint defining on which thread exclusive (write) locks may be obtained
	 * @param onInitialWriteLock An optional task that will run each time an exclusive lock is initially obtained--i.e. each time the lock
	 *        changes from being NOT exclusively held to being exclusively held
	 */
	private ThreadConstrainedLockingStrategy(ThreadConstraint threading, Runnable onInitialWriteLock) {
		if (threading == ThreadConstraint.ANY)
			throw new IllegalArgumentException(
				ThreadConstrainedLockingStrategy.class.getSimpleName() + " cannot be used with ThreadConstraint.ANY");
		theThreadConstraint = threading;
		theReadLock = new AtomicInteger();
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return theThreadConstraint;
	}

	@Override
	public boolean isLockSupported() {
		return true;
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		boolean onPublicThread = theThreadConstraint.isEventThread();
		if (write) {
			if (!onPublicThread)
				throw new IllegalStateException(ThreadConstraint.MOD_ON_WRONG_THREAD);
			else
				return getWriteLock(false, cause);
		} else if (onPublicThread) {
			theSafeReadLock++;
			return new IntReadLockRelease();
		} else {
			theReadLock.getAndIncrement();
			while (theWriteLock > 0) {
				// Release our non-exclusive lock and wait for the exclusive lock(s) to be released
				theReadLock.decrementAndGet();
				do {
					try {
						Thread.sleep(5);
					} catch (InterruptedException e) {
					}
				} while (theWriteLock > 0);
				// The exclusive lock is released.
				// Make another attempt to obtain a non-exclusive lock and see if we got it before another exclusive lock was obtained
				theReadLock.getAndIncrement();
			}
			// Success
			return new ExtReadLockRelease();
		}
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		boolean onPublicThread = theThreadConstraint.isEventThread();
		if (write) {
			if (onPublicThread)
				return getWriteLock(true, cause);
			else
				throw new IllegalStateException(ThreadConstraint.MOD_ON_WRONG_THREAD);
		} else if (onPublicThread) {
			theSafeReadLock++;
			return new IntReadLockRelease();
		} else {// Read lock off of the public thread
			if (theWriteLock > 0)
				return null;
			theReadLock.getAndIncrement();
			if (theWriteLock > 0) {
				theReadLock.decrementAndGet();
				return null;
			}
			return new ExtReadLockRelease();
		}
	}

	/**
	 * Obtains a write lock. This call assumes we're on the thread constraint's event thread.
	 * 
	 * @param cause The cause for changes that may occur within the lock
	 * @return The transaction to release the lock
	 */
	private Transaction getWriteLock(boolean tryOnly, Object cause) {
		/* Almost all locks share the constraint that it is impossible to safely upgrade from a read (non-exclusive) lock
		 * to a write (exclusive) lock.
		 * But in this lock we can do this safely because unlike those other locks, non-exclusive locks obtained on the event thread
		 * are tracked differently from those obtained from other threads.
		 * So we can wait for all non-exclusive locks from other threads to be released without needing to concern ourselves
		 * with non-exclusive locks obtained on the event thread, which is the current thread.
		 * if (theSafeReadLock > 0)
		 * throw new IllegalStateException("Attempting to upgrade from a read lock to a write lock");
		 */

		theWriteLock++;
		boolean initial = theWriteLock == 1;
		if (initial && theReadLock.get() != 0) {
			if (tryOnly)
				return null; // Can't obtain an exclusive lock immediately as requested
			/* I had thought that here I could grab the write lock and merely wait for all the external read locks to release,
			 * without releasing the lock.
			 * This would give write locks high priority, and only wait for threads that already held read locks to release them.
			 * The external read lock attempts release the lock while waiting, so I thought this was safe.
			 * But eventually this system encountered a deadlock, when an external thread was trying to obtain a *reentrant* read lock.
			 * The thread was releasing the deeper lock while waiting for the write lock to release,
			 * but the shallower read lock was still held lower down on the stack.
			 * So actually, I do have to release the write lock here while waiting for read locks to release,
			 * as this is the best way to allow reentrant read locks to do their work and then be released.
			 */
			// Wait for all external read locks to be released
			while (theReadLock.get() != 0) {
				theWriteLock = 0;
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
				}
				theWriteLock = 1;
			}
		}
		return new WriteLockRelease(super.lock(true, cause));
	}

	@Override
	public void modified() {
		if (theWriteLock == 0)
			throw new IllegalStateException("Not write locked");
		else if (!getThreadConstraint().isEventThread())
			throw new IllegalStateException(ThreadConstraint.MOD_ON_WRONG_THREAD);
		else
			super.modified();
	}

	@Override
	public CoreId getCoreId() {
		return new ThreadSafeCore(theThreadConstraint);
	}

	@Override
	public String toString() {
		return "Safe on " + getThreadConstraint();
	}

	/** A thread-safe lock core */
	public static class ThreadSafeCore extends CoreId {
		private final ThreadConstraint theThreadConstraint;

		/** @param threadConstraint The thread constraint of the core */
		public ThreadSafeCore(ThreadConstraint threadConstraint) {
			theThreadConstraint = threadConstraint;
		}

		/** @return The thread constraint of the core */
		public ThreadConstraint getThreadConstraint() {
			return theThreadConstraint;
		}

		@Override
		public int hashCode() {
			return theThreadConstraint.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ThreadSafeCore && theThreadConstraint.equals(((ThreadSafeCore) obj).theThreadConstraint);
		}

		@Override
		public String toString() {
			return "Safe:" + theThreadConstraint;
		}
	}

	class ExtReadLockRelease implements Transaction {
		private boolean isClosed;

		@Override
		public void close() {
			if (isClosed)
				return;
			isClosed = true;
			theReadLock.decrementAndGet();
		}

		@Override
		public String toString() {
			return "TCLS external read release";
		}
	}

	class IntReadLockRelease implements Transaction {
		private boolean isClosed;

		@Override
		public void close() {
			if (isClosed)
				return;
			isClosed = true;
			theSafeReadLock--;
		}

		@Override
		public String toString() {
			return "TCLS internal read release";
		}
	}

	class WriteLockRelease implements Transaction {
		private final Transaction theSuperTransaction;
		// This is for debugging
		// private final Exception theCallSite;

		WriteLockRelease(Transaction superTransaction) {
			theSuperTransaction = superTransaction;
			// theCallSite = new Exception();
			// theCallSite.fillInStackTrace();
		}

		private boolean isClosed;

		@Override
		public void close() {
			if (isClosed)
				return;
			isClosed = true;
			theWriteLock--;
			theSuperTransaction.close();
		}

		@Override
		protected void finalize() throws Throwable {
			if (!isClosed) {
				System.out.println("Failed to close " + this);
				// theCallSite.printStackTrace();
			}
			super.finalize();
		}

		@Override
		public int hashCode() {
			return super.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof WithInitialWriteLock)
				return equals(((WithInitialWriteLock) obj).theBacking);
			return super.equals(obj);
		}

		@Override
		public String toString() {
			return getThreadConstraint() + " TCLS write release";
		}
	}

	private static final ConcurrentHashMap<ThreadConstraint, ThreadConstrainedLockingStrategy> LOCKS = new ConcurrentHashMap<>();

	/**
	 * @param constraint The thread constraint to get the locking for
	 * @return The locking strategy for the given thread constraint
	 */
	public static CollectionLockingStrategy get(ThreadConstraint constraint) {
		return get(constraint, null);
	}

	/**
	 * @param constraint The thread constraint to get the locking for
	 * @param onInitialWriteLock An optional task to be executed each time this particular lock is initially locked for write
	 * @return The locking strategy for the given thread constraint
	 */
	public static CollectionLockingStrategy get(ThreadConstraint constraint, Runnable onInitialWriteLock) {
		ThreadConstrainedLockingStrategy tcls = LOCKS.computeIfAbsent(constraint, ThreadConstrainedLockingStrategy::new);
		if (onInitialWriteLock == null)
			return tcls;
		else
			return new WithInitialWriteLock(tcls, onInitialWriteLock);
	}

	static class WithInitialWriteLock implements CollectionLockingStrategy {
		private final ThreadConstrainedLockingStrategy theBacking;
		private final Runnable onInitialWriteLock;
		private int theWriteLockCount;

		WithInitialWriteLock(ThreadConstrainedLockingStrategy backing, Runnable onInitialWriteLock) {
			theBacking = backing;
			this.onInitialWriteLock = onInitialWriteLock;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			Transaction lock = theBacking.lock(write, cause);
			if (!write)
				return lock;
			if (write && 0 == theWriteLockCount++) {
				try {
					onInitialWriteLock.run();
				} catch (RuntimeException | Error e) {
					lock.close();
					throw e;
				}
			}
			return new Transaction.ReleaseOnceTransaction(() -> {
				theWriteLockCount--;
				lock.close();
			});
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			Transaction lock = theBacking.tryLock(write, cause);
			if (lock == null || !write)
				return lock;
			if (0 == theWriteLockCount++) {
				try {
					onInitialWriteLock.run();
				} catch (RuntimeException | Error e) {
					lock.close();
					throw e;
				}
			}
			return new Transaction.ReleaseOnceTransaction(() -> {
				theWriteLockCount--;
				lock.close();
			});
		}

		@Override
		public CoreId getCoreId() {
			return theBacking.getCoreId();
		}

		@Override
		public long getStamp() {
			return theBacking.getStamp();
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return theBacking.getCurrentCauses();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theBacking.getThreadConstraint();
		}

		@Override
		public void modified() {
			theBacking.modified();
		}

		@Override
		public int hashCode() {
			return theBacking.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theBacking.equals(obj);
		}

		@Override
		public String toString() {
			return theBacking.toString();
		}
	}
}

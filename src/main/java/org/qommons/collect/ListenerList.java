package org.qommons.collect;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * <p>
 * A very simple, fast, flexible, thread-safe list intended for listeners registered to an event source that provides a subset of collection
 * operations, including:
 * <ul>
 * <li>{@link #add(Object, boolean) Adding} a value to the end of the list</li>
 * <li>Removing that value via executing the {@link Runnable} returned from the {@link #add(Object, boolean) add} operation</li>
 * <li>{@link #forEach(Consumer) Performing} an action on each value in the list</li>
 * <li>{@link #clear() Clearing} the list, removing all values</li>
 * </ul>
 * The advantage of using this class over other thread-safe structures with more features is that it is extremely fast (requires no true
 * locking), light-weight, and tolerates external modification during iteration from the same thread (e.g. a listener that may remove itself
 * or adds another listener).
 * </p>
 * <p>
 * This class is thread-safe, but if listeners are added or removed by * other threads during {@link #forEach(Consumer) iteration}, the
 * added or removed listeners may or may not be acted upon for that * iteration, regardless of the <code>skipCurrent</code> argument to
 * {@link #add(Object, boolean)}.
 * </p>
 * 
 * @param <E> The type of value that this list can store
 */
public class ListenerList<E> {
	/**
	 * An element containing a value in a {@link ListenerList}
	 * 
	 * @param <E> The type of the value that this node holds
	 */
	public interface Element<E> extends Runnable {
		/**
		 * Removes this node from the list
		 * 
		 * @return True if the node was removed as a result of this call, false if it had been removed already
		 */
		boolean remove();

		/** @return The value that this node contains in the list */
		E get();

		/** @param newValue The new value for this node */
		void set(E newValue);

		@Override
		default void run() {
			remove();
		}
	}

	/** A listener to be invoked when a listener is added to an empty list or a solitary listener is removed from a {@link ListenerList} */
	public interface InUseListener {
		/** @param inUse Whether list just went in to (true) or out of (false) use */
		void inUseChanged(boolean inUse);
	}

	/** Builds a ListenerList with customizable options */
	public static class Builder {
		private String theReentrancyError;
		private boolean isForEachSafe;
		private InUseListener theInUseListener;
		private boolean fastSize;
		private SynchronizationType theSynchronizationType;

		Builder() {
			// Initialize with defaults, which mostly lean toward safety and functionality, away from performance
			theReentrancyError = "Reentrancy not allowed";
			isForEachSafe = true;
			fastSize = true;
			theSynchronizationType = SynchronizationType.ELEMENT;
		}

		/**
		 * Configures the list to be built to allow listeners to invoke {@link ListenerList#forEach(Consumer)}, directly or indirectly
		 * 
		 * @return This builder
		 */
		public Builder allowReentrant() {
			return reentrancyError(null);
		}

		/**
		 * @param error The message in the exception that will be thrown if {@link ListenerList#forEach(Consumer)} is invoked by a listener
		 *        of the list, directly or indirectly. If null, this method is the same as {@link #allowReentrant()}.
		 * @return This builder
		 */
		public Builder reentrancyError(String error) {
			theReentrancyError = error;
			return this;
		}

		/**
		 * 
		 * @param safe Whether to ensure thread safety for the reentrancy-prevention and <code>skipCurrent</code> (for
		 *        {@link ListenerList#add(Object, boolean)}) features of {@link ListenerList#forEach(Consumer)}. If false, the list will be
		 *        lighter and a bit more performant, but the above-mentioned features will be error-prone if
		 *        {@link ListenerList#forEach(Consumer)} is ever called by multiple threads concurrently.
		 * @return This builder
		 */
		public Builder forEachSafe(boolean safe) {
			isForEachSafe = safe;
			return this;
		}

		/**
		 * @param listener The listener to be notified when a listener is added to the empty list or a solitary listener removed from it.
		 * @return This builder
		 */
		public Builder withInUse(InUseListener listener) {
			theInUseListener = listener;
			return this;
		}

		/**
		 * @param fast Whether this list should maintain an {@link AtomicInteger} with its current size, so that {@link ListenerList#size()}
		 *        is constant time. If false, this list is a bit lighter, but the {@link ListenerList#size()} operation is linear time. This
		 *        option is ignored (effectively true) if the {@link #withInUse(InUseListener) in-use listener} is set.
		 * @return This builder
		 */
		public Builder withFastSize(boolean fast) {
			fastSize = fast;
			return this;
		}

		/**
		 * @param type The synchronization type to use to thread-secure the list
		 * @return This builder
		 * @see SynchronizationType#NONE
		 * @see SynchronizationType#LIST
		 * @see SynchronizationType#ELEMENT
		 */
		public Builder withSyncType(SynchronizationType type) {
			theSynchronizationType = type;
			return this;
		}

		/**
		 * Optimizes the list for performance, discarding all thread safety mechanisms
		 * 
		 * @return This builder
		 */
		public Builder unsafe() {
			return allowReentrant().forEachSafe(false).withFastSize(false).withSyncType(SynchronizationType.NONE);
		}

		/**
		 * @param <E> The type of the list to build
		 * @return The new list
		 */
		public <E> ListenerList<E> build() {
			return new ListenerList<>(theReentrancyError, isForEachSafe, theInUseListener, fastSize, theSynchronizationType);
		}
	}

	/** Several different synchronization types that can be used to thread-secure this class */
	public enum SynchronizationType {
		/** With this synchronization type, the list will be more performant, but not thread-safe */
		NONE,
		/**
		 * <p>
		 * With this synchronization type, all add/remove operations will synchronize on the whole list, allowing only a single such
		 * operation at a time.
		 * </p>
		 * <p>
		 * This type of synchronization may perform better than {@link #ELEMENT}-level synchronization for very small lists.
		 * </p>
		 */
		LIST,
		/**
		 * <p>
		 * With this synchronization type, add/remove operations occur within 2 monitor holds, representing the link before and after the
		 * node.
		 * </p>
		 * <p>
		 * This type of synchronization may perform better than {@link #LIST} for lists that have more than 2 elements.
		 * </p>
		 */
		ELEMENT;
	}

	/** @return A builder to build a {@link ListenerList} by option */
	public static Builder build() {
		return new Builder();
	}

	private class Node implements Element<E> {
		E theListener;
		volatile Node next;
		volatile Node previous;

		Node(E listener) {
			theListener = listener;
		}

		boolean skipOne(Object firing) {
			return false;
		}

		@Override
		public E get() {
			return theListener;
		}

		@Override
		public void set(E newValue) {
			theListener = newValue;
		}

		@Override
		public void run() {
			remove();
		}

		@Override
		public boolean remove() {
			if (previous.next != this)
				return false;

			switch (theSyncType) {
			case NONE:
				return _remove();
			case LIST:
				synchronized (theTerminal) {
					return _remove();
				}
			case ELEMENT:
				synchronized (this) {
					synchronized (this.next) { // End sync method 2
						return _remove();
					}
				}
			}
			throw new IllegalStateException("Unrecognized sync type " + theSyncType);
		}

		private boolean _remove() {
			if (previous.next != this)
				return false;
			removeListener(this);
			return true;
		}
	}

	private class SkipOneNode extends Node {
		private Object skipOne;

		public SkipOneNode(E listener, Object skipOne) {
			super(listener);
			this.skipOne = skipOne;
		}

		@Override
		boolean skipOne(Object firing) {
			if (firing == skipOne) {
				skipOne = null;
				return true;
			}
			return false;
		}
	}

	private final ThreadLocal<Object> isFiring;
	final Node theTerminal;
	private final String theReentrancyError;
	private final InUseListener theInUseListener;
	final SynchronizationType theSyncType;

	private final AtomicInteger theSize;
	private volatile Object unsafeIterId;

	/**
	 * Creates the listener list
	 * 
	 * @param reentrancyError The message to throw in an {@link IllegalStateException} from {@link #forEach(Consumer)} if the method is
	 *        called as a result of the action being invoked from a higher {@link #forEach(Consumer) forEach} call. Or null if such
	 *        reentrancy is to be allowed.
	 */
	public ListenerList(String reentrancyError) {
		this(reentrancyError, true, null);
	}

	/**
	 * Creates the listener list
	 * 
	 * @param reentrancyError The message to throw in an {@link IllegalStateException} from {@link #forEach(Consumer)} if the method is
	 *        called as a result of the action being invoked from a higher {@link #forEach(Consumer) forEach} call. Or null if such
	 *        reentrancy is to be allowed.
	 * @param safeForEach If the reentrancy and {@link #add(Object, boolean) skipCurrent} features are not thread-safe, this class can
	 *        perform much better. This is safe if the {@link #forEach(Consumer)} method is never called from multiple threads
	 *        simultaneously OR if reentrancy is enabled AND the skipCurrent feature need not be reliable.
	 * @param inUseListener A listener to be notified when this list goes in (true) and out (false) of use (i.e. just before a listener is
	 *        added to the empty list or just after the last listener is removed, respectively). This listener MAY NOT add listeners itself.
	 *        Such an attempt will result in deadlock.
	 */
	public ListenerList(String reentrancyError, boolean safeForEach, InUseListener inUseListener) {
		this(reentrancyError, safeForEach, inUseListener, true, SynchronizationType.ELEMENT);
	}

	ListenerList(String reentrancyError, boolean safeForEach, InUseListener inUseListener, boolean fastSize,
		SynchronizationType synchronizationType) {
		// The code is simpler if all the real listeners can know that there's a non-null node before and after them.
		// The first node's previous pointer and the last node's next pointer would always be null,
		// so there's no need to have different nodes for first and last.
		theTerminal = new Node(null);
		theTerminal.next = theTerminal.previous = theTerminal;

		isFiring = safeForEach ? new ThreadLocal<>() : null;
		theReentrancyError = reentrancyError;
		theInUseListener = inUseListener;
		if (inUseListener != null)
			fastSize = true;
		theSize = fastSize ? new AtomicInteger() : null;
		theSyncType = synchronizationType;
	}

	/**
	 * @param listener The listener to add
	 * @param skipCurrent Whether to skip calling this listener the first time if this addition is a result of the action being invoked from
	 *        a {@link #forEach(Consumer)} call
	 * @return The action to invoke (i.e. {@link Runnable#run()}) to remove this listener
	 */
	public Element<E> add(E listener, boolean skipCurrent) {
		Object firing;
		if (skipCurrent) {
			if (isFiring != null)
				firing = isFiring.get(); // Thread safe because isFiring is a ThreadLocal
			else
				firing = unsafeIterId;
		} else
			firing = null;
		Node newNode = firing == null ? new Node(listener) : new SkipOneNode(listener, firing);
		newNode.next = theTerminal;// We know we'll be adding this node as the last node (excluding the terminal)
		// The next part affects the list's state, so only one at a time
		switch (theSyncType) {
		case NONE:
			_add(newNode);
			break;
		case LIST:
			synchronized (theTerminal) {
				_add(newNode);
			}
			break;
		case ELEMENT:
			synchronized (newNode) {
				synchronized (theTerminal) {
					_add(newNode);
				}
			}
		}
		return newNode;
	}

	private void _add(Node newNode) {
		boolean newInUse = theSize != null && theSize.getAndIncrement() == 0;
		if (newInUse && theInUseListener != null)
			theInUseListener.inUseChanged(true);
		Node oldLast = theTerminal.previous;
		newNode.previous = oldLast;
		theTerminal.previous = newNode;
		oldLast.next = newNode;
	}

	void removeListener(Node node) {
		Node prev = node.previous;
		Node next = node.next;
		prev.next = next;
		next.previous = prev;
		boolean newNotInUse = theSize != null && theSize.decrementAndGet() == 0;
		if (newNotInUse && theInUseListener != null)
			theInUseListener.inUseChanged(false);
	}

	/**
	 * Removes and returns the first element (the head) of this list, if the list is not empty
	 * 
	 * @param waitTime If &gt;0, how long to wait in this method until a value is available
	 * @return The removed node, or null if the list was empty
	 */
	public Element<E> poll(long waitTime) {
		boolean wait = waitTime > 0;
		boolean timeChecking = wait && waitTime < Long.MAX_VALUE;
		long start = timeChecking ? System.currentTimeMillis() : 0;
		int waited = 0;
		do {
			Node remove = theTerminal.next;
			if (remove != theTerminal) {
				if (remove.remove())
					return remove;
			} else if (wait) {
				try {
					Thread.sleep(0, 100_000);
				} catch (InterruptedException e) {}
				if (timeChecking && ++waited == 10) {
					waited = 0;
					if (System.currentTimeMillis() > start + waitTime)
						return null;
				}
			}
		} while (wait);
		return null;
	}

	/** @param action The action to perform on each listener in this list */
	public void forEach(Consumer<E> action) {
		Node node = theTerminal.next;
		Object iterId = new Object();
		Object reentrant;
		if (isFiring != null) {
			reentrant = isFiring.get();
			if (reentrant != null && theReentrancyError != null)
				throw new IllegalStateException(theReentrancyError);
			isFiring.set(iterId);
		} else {
			reentrant = unsafeIterId;
			unsafeIterId = iterId;
		}
		try {
			while (node != theTerminal) {
				Node nextNode = node.next; // Get the next node before calling the listener, since the listener might remove itself
				if (!node.skipOne(iterId))
					action.accept(node.theListener);
				node = nextNode;
			}
		} finally {
			if (isFiring != null) {
				if (reentrant == null)
					isFiring.remove();
				else
					isFiring.set(reentrant);
			} else {
				unsafeIterId = reentrant;
			}
		}
	}

	/** Removes all listeners in this list */
	public void clear() {
		synchronized (theTerminal) {
			Node node = theTerminal;
			Node nextNode = node.next;
			boolean wasInUse = nextNode != theTerminal;
			while (nextNode != theTerminal) {
				node.previous = node;
				node.next = theTerminal;
				node = nextNode;
				nextNode = node.next;
			}
			if (theSize != null)
				theSize.set(0);
			if (wasInUse && theInUseListener != null)
				theInUseListener.inUseChanged(false);
		}
	}

	/** @return Whether this list has no listeners in it */
	public boolean isEmpty() {
		return theTerminal.next == theTerminal;
	}

	/** @return The number of listeners in this list */
	public int size() {
		if (theSize != null)
			return theSize.get();
		Node node = theTerminal.next;
		int sz = 0;
		while (node != theTerminal) {
			sz++;
			node = node.next;
		}
		return sz;
	}

	/**
	 * Adds all of this list's content into the given collection (without removing it from this one)
	 * 
	 * @param <C> The type of the collection
	 * @param collection The collection to put all of this list's listeners into
	 * @return The collection
	 */
	public <C extends Collection<? super E>> C dumpInto(C collection) {
		// Don't use forEach, since this might be useful for debugging during event firing and reentrancy may not be allowed
		Node node = theTerminal.next;
		while (node != theTerminal) {
			collection.add(node.theListener);
			node = node.next;
		}
		return collection;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append('[');
		boolean[] first = new boolean[] { true };
		// Don't use forEach, since toString might be useful for debugging during event firing and reentrancy may not be allowed
		Node node = theTerminal.next;
		while (node != theTerminal) {
			if (!first[0])
				str.append(", ");
			first[0] = false;
			str.append(node.theListener);
			node = node.next;
		}
		str.append(']');
		return str.toString();
	}
}

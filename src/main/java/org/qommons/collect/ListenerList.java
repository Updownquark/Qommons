package org.qommons.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
	private static boolean SWALLOW_EXCEPTIONS = true;

	/**
	 * @return Whether instances of this class (and others who use this flag) will swallow {@link RuntimeException} thrown by a listener
	 *         (printing their stack traces first) in order that later listeners remain unaffected
	 */
	public static boolean isSwallowingExceptions() {
		return SWALLOW_EXCEPTIONS;
	}

	/**
	 * @param swallowExceptions Whether instances of this class (and others who use this flag) should swallow {@link RuntimeException}
	 *        thrown by a listener (printing their stack traces first) in order that later listeners remain unaffected
	 */
	public static void setSwallowExceptions(boolean swallowExceptions) {
		SWALLOW_EXCEPTIONS = swallowExceptions;
	}

	/**
	 * An element containing a value in a {@link ListenerList}
	 * 
	 * @param <E> The type of the value that this node holds
	 */
	public interface Element<E> extends Runnable {
		/** @return Whether this element is still present in the list */
		boolean isPresent();

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
		private boolean isSynchronized;

		Builder() {
			// Initialize with defaults, which mostly lean toward safety and functionality, away from performance
			theReentrancyError = "Reentrancy not allowed";
			isForEachSafe = true;
			fastSize = true;
			isSynchronized = true;
		}

		/**
		 * Configures the list to be built to allow listeners to invoke {@link ListenerList#forEach(Consumer)}, directly or indirectly.
		 * Using this will improve the performance of {@link ListenerList#forEach(Consumer)} by avoiding the need to keep track of call
		 * status for each thread.
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
		 * @param sync Whether the list should be synchronized to prevent loss of data
		 * @return This builder
		 */
		public Builder withSync(boolean sync) {
			isSynchronized = sync;
			return this;
		}

		/**
		 * Optimizes the list for performance, discarding all thread safety mechanisms
		 * 
		 * @return This builder
		 */
		public Builder unsafe() {
			return allowReentrant().forEachSafe(false).withFastSize(false).withSync(false);
		}

		/**
		 * @param <E> The type of the list to build
		 * @return The new list
		 */
		public <E> ListenerList<E> build() {
			return new ListenerList<>(theReentrancyError, isForEachSafe, theInUseListener, fastSize, isSynchronized);
		}
	}

	/** @return A builder to build a {@link ListenerList} by option */
	public static Builder build() {
		return new Builder();
	}

	private class Node implements Element<E> {
		E theListener;
		volatile Node next;
		volatile Node previous;
		volatile boolean present;

		Node(E listener) {
			theListener = listener;
			present = true;
		}

		boolean isInAddFiringRound(Object firing) {
			return false;
		}

		@Override
		public boolean isPresent() {
			return previous.next == this;
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

			if (isSynchronized) {
				synchronized (theTerminal) {
					return _remove();
				}
			} else
				return _remove();
		}

		private boolean _remove() {
			present = false;
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
		boolean isInAddFiringRound(Object firing) {
			if (firing == skipOne) {
				skipOne = null;
				return true;
			}
			return false;
		}
	}

	private class RunLastNode extends SkipOneNode {
		RunLastNode(E listener, Object skipOne) {
			super(listener, skipOne);
		}
	}

	/** Added to the sequence for a listener {@link ListenerList#addLast(Object, boolean) added last} */
	private class TempNode extends SkipOneNode {
		TempNode(E listener, Object skipOne) {
			super(listener, skipOne);
		}
	}

	private final ThreadLocal<Object> isFiringSafe;
	final Node theTerminal;
	private final String theReentrancyError;
	private final InUseListener theInUseListener;
	final boolean isSynchronized;

	private final AtomicInteger theSize;
	private volatile Object unsafeIterId;

	ListenerList(String reentrancyError, boolean safeForEach, InUseListener inUseListener, boolean fastSize,
		boolean sync) {
		// The code is simpler if all the real listeners can know that there's a non-null node before and after them.
		// The first node's previous pointer and the last node's next pointer would always be null,
		// so there's no need to have different nodes for first and last.
		theTerminal = new Node(null);
		theTerminal.next = theTerminal.previous = theTerminal;

		isFiringSafe = safeForEach ? new ThreadLocal<>() : null;
		theReentrancyError = reentrancyError;
		theInUseListener = inUseListener;
		if (inUseListener != null)
			fastSize = true;
		theSize = fastSize ? new AtomicInteger() : null;
		isSynchronized = sync;
	}

	/**
	 * @param listener The listener to add
	 * @param skipCurrent Whether to skip calling this listener the first time if this addition is a result of the action being invoked from
	 *        a {@link #forEach(Consumer)} call
	 * @return The added element
	 */
	public Element<E> add(E listener, boolean skipCurrent) {
		Object firing;
		if (skipCurrent) {
			if (isFiringSafe != null)
				firing = isFiringSafe.get(); // Thread safe because isFiring is a ThreadLocal
			else
				firing = unsafeIterId;
		} else
			firing = null;
		Node newNode = firing == null ? new Node(listener) : new SkipOneNode(listener, firing);
		return addNode(newNode, true);
	}

	/**
	 * Adds a listener to this list, to be executed after all other listeners (that were not themselves added with this method)
	 * 
	 * @param listener The listener to add
	 * @param skipCurrent Whether to skip calling this listener the first time if this addition is a result of the action being invoked from
	 *        a {@link #forEach(Consumer)} call
	 * @return The added element
	 */
	public Element<E> addLast(E listener, boolean skipCurrent) {
		Object firing;
		if (skipCurrent) {
			if (isFiringSafe != null)
				firing = isFiringSafe.get(); // Thread safe because isFiring is a ThreadLocal
			else
				firing = unsafeIterId;
		} else
			firing = null;
		RunLastNode node = new RunLastNode(listener, firing);
		return addNode(node, true);
	}

	/**
	 * @param listener The listener to add
	 * @return The added element
	 */
	public Element<E> addFirst(E listener) {
		Node newNode = new Node(listener);
		return addNode(newNode, false);
	}

	private Element<E> addNode(Node newNode, boolean last) {
		if (last)
			newNode.next = theTerminal;// We know we'll be adding this node as the last node (excluding the terminal)
		else
			newNode.previous = theTerminal;
		// The next part affects the list's state, so only one at a time
		if (isSynchronized) {
			synchronized (theTerminal) {
				_add(newNode, last);
			}
		} else
			_add(newNode, last);
		return newNode;
	}

	private void _add(Node newNode, boolean last) {
		boolean newInUse = theSize != null && theSize.getAndIncrement() == 0;
		if (last) {
			Node oldLast = theTerminal.previous;
			newNode.previous = oldLast;
			theTerminal.previous = newNode;
			oldLast.next = newNode;
		} else {
			Node oldFirst = theTerminal.next;
			newNode.next = oldFirst;
			theTerminal.next = newNode;
			oldFirst.previous = newNode;
		}
		if (newInUse && theInUseListener != null)
			theInUseListener.inUseChanged(true);
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
		RunLastNode runLast = null;
		Node remove = theTerminal.next;
		do {
			if (remove == theTerminal) {//
			} else if (remove instanceof ListenerList.RunLastNode) {
				if (runLast == null)
					runLast = (RunLastNode) remove;
				remove = remove.next;
				continue;
			} else if (remove instanceof ListenerList.TempNode) {
				remove = remove.next;
				continue;
			} else if (!remove.remove()) {
				remove = theTerminal.next;
				continue; // Removed elsewhere--try again
			} else
				return remove;

			if (runLast != null) {
				if (runLast.remove())
					return runLast;
				runLast = null;
				continue;
			}
			if (wait) {
				try {
					Thread.sleep(0, 100_000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				if (timeChecking && ++waited == 10) {
					waited = 0;
					if (System.currentTimeMillis() > start + waitTime)
						return null;
				}
				remove = theTerminal.next;
			}
		} while (wait);
		return null;
	}
	
	/**
	 * Removes and returns the first value (the head) in this list, if the list is not empty
	 * 
	 * @param waitTime If &gt;0, how long to wait in this method until a value is available
	 * @return The removed value, or null if the list was empty
	 */
	public E pollValue(long waitTime) {
		Element<E> polled = poll(waitTime);
		return polled == null ? null : polled.get();
	}

	/** @return The first element in this list, or null if the list is empty */
	public Element<E> peekFirst(){
		RunLastNode runLast = null;
		Node node=theTerminal.next;
		while (node != theTerminal) {
			if (node instanceof ListenerList.RunLastNode) {
				runLast = (ListenerList<E>.RunLastNode) node;
				node = node.next;
			} else if (node instanceof ListenerList.TempNode) {
				node = node.next;
			} else
				break;
		}
		if(node==theTerminal)
			return runLast;
		else
			return node;
	}

	/** @param action The action to perform on each listener in this list */
	public void forEach(Consumer<E> action) {
		Node node = theTerminal.next;
		Object iterId = new Object();
		Object reentrant;
		if (isFiringSafe != null) {
			reentrant = isFiringSafe.get();
			if (reentrant != null && theReentrancyError != null)
				throw new ReentrantNotificationException(theReentrancyError);
			isFiringSafe.set(iterId);
		} else {
			reentrant = unsafeIterId;
			if (reentrant != null && theReentrancyError != null)
				throw new ReentrantNotificationException(theReentrancyError);
			unsafeIterId = iterId;
		}
		try {
			while (node != theTerminal) {
				if (node instanceof ListenerList.TempNode) {
					if (node.isInAddFiringRound(iterId)) {
						node.remove();
						try {
							action.accept(node.theListener);
						} catch (ReentrantNotificationException | AssertionError e) {
							throw e;
						} catch (RuntimeException e) {
							if (SWALLOW_EXCEPTIONS) {
								// If the listener throws an exception, we can't have that gumming up the works
								// If they want better handling, they can try/catch their own code
								e.printStackTrace();
							} else
								throw e;
						}
					}
				} else if (node.isInAddFiringRound(iterId)) { // Don't execute the same round it was added, if so configured
				} else if (node instanceof ListenerList.RunLastNode) {
					TempNode tempNode = new TempNode(node.theListener, iterId);
					addNode(tempNode, true);
				} else {
					try {
						action.accept(node.theListener);
					} catch (ReentrantNotificationException | AssertionError e) {
						throw e;
					} catch (RuntimeException e) {
						if (SWALLOW_EXCEPTIONS) {
							// If the listener throws an exception, we can't have that gumming up the works
							// If they want better handling, they can try/catch their own code
							e.printStackTrace();
						} else
							throw e;
					}
				}

				/* Now we need to get the next listener in the list.
				 * A problem may occur, however, if the list is modified as a result of a listener call.
				 * If, for example, a call to a listener causes it to remove itself and then the next listener in the list,
				 * this node will still be pointing to the next listener that was removed.
				 * We have to find the most recent listener that we called for this action that is still in the list and use its next node.
				 */
				while (!node.present)
					node = node.previous;
				node = node.next;
			}
		} finally {
			if (isFiringSafe != null) {
				if (reentrant == null)
					isFiringSafe.remove();
				else
					isFiringSafe.set(reentrant);
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
			if (!(node instanceof ListenerList.TempNode))
				sz++;
			node = node.next;
		}
		return sz;
	}

	/**
	 * @return Whether {@link #forEach(Consumer)} is currently being called. If this list uses {@link Builder#forEachSafe(boolean) safe
	 *         iteration}, this will only return true in the case that iteration is happening on the current thread. Otherwise, it will
	 *         return true during iteration on any thread.
	 */
	public boolean isFiring() {
		Object reentrant;
		if (isFiringSafe != null)
			reentrant = isFiringSafe.get();
		else
			reentrant = unsafeIterId;
		return reentrant != null;
	}

	/**
	 * Adds all of this list's content into a new list (without removing it from this one)
	 * 
	 * @return The list
	 */
	public List<E> dump() {
		return dumpInto(new ArrayList<>(theSize == null ? 10 : theSize.get() + 3));
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
		List<E> lastNodes = null;
		while (node != theTerminal) {
			if (node instanceof ListenerList.RunLastNode) {
				if (lastNodes == null)
					lastNodes = new ArrayList<>();
				lastNodes.add(node.theListener);
			} else
				collection.add(node.theListener);
			node = node.next;
		}
		if (lastNodes != null)
			collection.addAll(lastNodes);
		return collection;
	}

	/**
	 * Clears this list, providing each value to a consumer before it is removed
	 * 
	 * @param consumer The consumer to accept each value in this list
	 * @return The number of items that were found in the list
	 */
	public int dumpAndClear(Consumer<E> consumer) {
		Node node = theTerminal.next;
		List<E> lastNodes = null;
		int count = 0;
		while (node != theTerminal) {
			count++;
			if (node instanceof ListenerList.RunLastNode) {
				if (lastNodes == null)
					lastNodes = new ArrayList<>();
				lastNodes.add(node.theListener);
			} else if (consumer != null)
				consumer.accept(node.theListener);
			node.remove();
			node = node.next;
		}
		if (lastNodes != null)
			lastNodes.forEach(consumer);
		return count;
	}

	/**
	 * Removes this list's content and returns it in a new list. If items are added to this list externally during this call, the list may
	 * contain the added items after this call returns.
	 * 
	 * @return The list of content that was previously in this list
	 */
	public List<E> dumpAndClear() {
		if (isEmpty())
			return Collections.emptyList();
		int size;
		List<E> list;
		if (theSize != null) {
			size = theSize.get();
			list = new ArrayList<>(size);
		} else {
			size = Integer.MAX_VALUE;
			list = new ArrayList<>();
		}
		int i = 0;
		Node node = theTerminal.next;
		List<E> lastNodes = null;
		while (i < size && node != theTerminal) {
			if (node instanceof ListenerList.RunLastNode) {
				if (lastNodes == null)
					lastNodes = new ArrayList<>();
				lastNodes.add(node.theListener);
			} else
				list.add(node.theListener);
			node.remove();
			node = node.next;
		}
		if (lastNodes != null)
			list.addAll(lastNodes);
		if (node != theTerminal)
			theTerminal.next = node;
		return list;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append('[');
		boolean first = true;
		StringBuilder lastStr = null;
		// Don't use forEach, since toString might be useful for debugging during event firing and reentrancy may not be allowed
		Node node = theTerminal.next;
		while (node != theTerminal) {
			StringBuilder s;
			if (node instanceof ListenerList.RunLastNode) {
				if (lastStr == null)
					lastStr = new StringBuilder();
				else
					lastStr.append(", ");
				s = lastStr;
			} else {
				if (first)
					first = false;
				else
					str.append(", ");
				s = str;
			}
			s.append(node.theListener);
			node = node.next;
		}
		if (lastStr != null)
			str.append(lastStr);
		str.append(']');
		return str.toString();
	}
}

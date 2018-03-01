package org.qommons.collect;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * <p>
 * A very simple, fast, flexible, thread-safe list intended for listeners registered to an event source that allows 4 different operations:
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
 * This class is thread-safe, but if listeners are added or removed by other threads during {@link #forEach(Consumer) iteration}, the added
 * or removed listeners may or may not be acted upon for that iteration.
 * </p>
 * 
 * @param <E> The type of value that this list can store
 */
public class ListenerList<E> {
	private class Node implements Runnable {
		final E theListener;

		Node next;
		private Node previous;
		Object skipOne;

		Node(E listener) {
			theListener = listener;
		}

		@Override
		public void run() {
			obtainLock();
			previous.next = next;
			next.previous = previous;
			theLock.set(false);
		}
	}

	private final ThreadLocal<Object> isFiring;
	private final AtomicBoolean theLock;
	private final Node theTerminal;
	private final String theReentrancyError;

	/**
	 * Creates the listener list
	 * 
	 * @param reentrancyError The message to throw in an {@link IllegalStateException} from {@link #forEach(Consumer)} if the method is
	 *        called as a result of the action being invoked from a higher {@link #forEach(Consumer) forEach} call. Or null if such
	 *        reentrancy is to be allowed.
	 */
	public ListenerList(String reentrancyError) {
		// The code is simpler if all the real listeners can know that there's a non-null node before and after them.
		// The first node's previous pointer and the last node's next pointer would always be null,
		// so there's no need to have different nodes for first and last.
		theTerminal = new Node(null);
		theTerminal.next = theTerminal.previous = theTerminal;
		theLock = new AtomicBoolean();
		isFiring = new ThreadLocal<>();
		theReentrancyError = reentrancyError;
	}

	/**
	 * @param listener The listener to add
	 * @param skipCurrent Whether to skip calling this listener the first time if this addition is a result of the action being invoked from
	 *        a {@link #forEach(Consumer)} call
	 * @return The action to invoke (i.e. {@link Runnable#run()}) to remove this listener
	 */
	public Runnable add(E listener, boolean skipCurrent) {
		Node newNode = new Node(listener);
		newNode.next = theTerminal;// We know we'll be adding this node as the last node (excluding the terminal)
		if (skipCurrent)
			newNode.skipOne = isFiring.get(); // Thread safe because isFiring is a ThreadLocal
		// The next part affects the list's state, so only one at a time
		obtainLock();
		Node oldLast = theTerminal.previous;
		newNode.previous = oldLast;
		theTerminal.previous = newNode;
		oldLast.next = newNode;
		theLock.set(false);
		return newNode;
	}

	private void obtainLock() {
		while (theLock.getAndSet(true)) {
			// Lock is already held. Try again later.
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {}
		}
	}

	/** @param action The action to perform on each listener in this list */
	public void forEach(Consumer<E> action) {
		Node node = theTerminal.next;
		Object reentrant = isFiring.get();
		if (reentrant != null && theReentrancyError != null)
			throw new IllegalStateException(theReentrancyError);
		Object iterId = new Object();
		isFiring.set(iterId);
		while (node != theTerminal) {
			if (node.skipOne == iterId)
				node.skipOne = null;
			else
				action.accept(node.theListener);
			node = node.next;
		}
		if (reentrant == null)
			isFiring.remove();
		else
			isFiring.set(reentrant);
	}

	/**
	 * Removes all listeners in this list. Will have no effect on any current {@link #forEach(Consumer) iterations}, except that listeners
	 * added during the iteration will not appear at the end of the iteration.
	 */
	public void clear() {
		obtainLock();
		theTerminal.next = theTerminal.previous = theTerminal;
		theLock.set(false);
	}

	/** @return Whether this list has no listeners in it */
	public boolean isEmtpy() {
		return theTerminal.next == theTerminal;
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

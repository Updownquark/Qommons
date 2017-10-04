package org.qommons.collect;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * <p>
 * A very simple and flexible list that allows 4 different operations:
 * <ul>
 * <li>{@link #add(Object) Adding} a value to the end of the list</li>
 * <li>Removing that value via executing the {@link Runnable} returned from the {@link #add(Object) add} operation</li>
 * <li>{@link #forEach(Consumer) Iterating} through each value in the list and performing an action on it</li>
 * <li>{@link #clear() Clearing} the list, removing all values</li>
 * </ul>
 * The advantage of using this class over others is that it is extremely light-weight and tolerates external modification during iteration
 * from the same thread (e.g. a listener that may remove itself or adds another listener).
 * </p>
 * <p>
 * This class is thread-safe, but if listeners are added or removed by other threads during {@link #forEach(Consumer) iteration}, the added
 * listeners may or may not be acted upon for that iteration.
 * </p>
 * 
 * @param <E> The type of value that this list can store
 */
public class ListenerList<E> {
	private class Node implements Runnable {
		final E theListener;

		private Node next;
		private Node previous;

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

	private final AtomicBoolean theLock;
	private final Node theFirst;
	private final Node theLast;

	/** Creates the listener list */
	public ListenerList() {
		theFirst = new Node(null);
		theLast = new Node(null);
		theFirst.next = theLast;
		theLast.previous = theFirst;
		theLock = new AtomicBoolean();
	}

	/**
	 * @param listener The listener to add
	 * @return The action to invoke (i.e. {@link Runnable#run()}) to remove this listener
	 */
	public Runnable add(E listener) {
		Node newNode = new Node(listener);
		newNode.next = theLast;
		// The next part affects the list's state, so only one at a time
		obtainLock();
		Node oldLast = theLast.previous;
		newNode.previous = oldLast;
		theLast.previous = newNode;
		oldLast.next = newNode;
		theLock.set(false);
		return newNode;
	}

	void obtainLock() {
		while (theLock.getAndSet(true)) {
			// Lock is already held. Try again later.
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {}
		}
	}

	/** @param action The action to perform on each listener in this list */
	public void forEach(Consumer<E> action) {
		Node node = theFirst.next;
		while (node != theLast) {
			action.accept(node.theListener);
			node = node.next;
		}
	}

	/** Removes all listeners in this list */
	public void clear() {
		obtainLock();
		theFirst.next = theLast;
		theLast.previous = theFirst;
		theLock.set(false);
	}

	/** @return Whether this list has no listeners in it */
	public boolean isEmtpy() {
		return theFirst.next == theLast;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append('[');
		boolean[] first = new boolean[] { true };
		forEach(listener -> {
			if (!first[0])
				str.append(", ");
			first[0] = false;
			str.append(listener);
		});
		str.append(']');
		return str.toString();
	}
}

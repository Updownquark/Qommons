package org.qommons.collect;

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
 * This class is not thread-safe and must be secured externally if needed.
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
			previous.next = next;
			next.previous = previous;
		}
	}

	private final Node theFirst;
	private final Node theLast;

	/** Creates the listener list */
	public ListenerList() {
		theFirst = new Node(null);
		theLast = new Node(null);
		theFirst.next = theLast;
		theLast.previous = theFirst;
	}

	/**
	 * @param listener The listener to add
	 * @return The action to invoke (i.e. {@link Runnable#run()}) to remove this listener
	 */
	public Runnable add(E listener) {
		Node newNode = new Node(listener);
		Node oldLast = theLast.previous;
		oldLast.next = newNode;
		theLast.previous = newNode;
		newNode.previous = oldLast;
		newNode.next = theLast;
		return newNode;
	}

	/** @param action The action to perform on each listener in this list */
	public void forEach(Consumer<E> action) {
		Node node = theFirst;
		while (node.next != theLast) {
			node = node.next;
			action.accept(node.theListener);
		}
	}

	/** Removes all listeners in this list */
	public void clear() {
		theFirst.next = theLast;
		theLast.previous = theFirst;
	}

	/** @return Whether this list has no listeners in it */
	public boolean isEmtpy() {
		return theFirst.next == theLast;
	}
}

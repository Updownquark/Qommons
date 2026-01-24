package org.qommons.collect;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

import org.qommons.Stamped;

/**
 * A double-ended queue that implements {@link Sequenced}
 * 
 * @param <E> The type of values in the queue
 */
public interface SequencedQueue<E> extends Queue<E>, Sequenced<E>, PureQueue<E>, Stamped {
	/**
	 * @param c The collection
	 * @return Whether this collection contains any elements of the given collection
	 */
	boolean containsAny(Collection<?> c);

	/**
	 * @param e The item to add at the end of this queue
	 * @throws IllegalStateException If the queue cannot hold new elements
	 */
	default void addLast(E e) {
		if (!offer(e))
			throw new IllegalStateException("Queue is full");
	}

	@Override
	default boolean add(E e) {
		return offer(e);
	}

	/**
	 * @return The value that was at the head of the queue before it was removed by this method
	 * @throws NoSuchElementException If the queue was empty
	 */
	default E removeFirst() {
		if (isEmpty())
			throw new NoSuchElementException("Queue is empty");
		E v = poll();
		return v;
	}

	/**
	 * @return The value at the head of the queue
	 * @throws NoSuchElementException If the queue was empty
	 */
	default E getFirst() {
		long stamp = getStamp();
		if (isEmpty())
			throw new NoSuchElementException("Queue is empty");
		E v = peek();
		if (stamp != getStamp())
			throw new ConcurrentModificationException("List was modified externally");
		return v;
	}

	/**
	 * @param o The object to remove from the queue
	 * @return Whether the object was found and removed
	 */
	default boolean removeFirstOccurrence(Object o) {
		return remove(o);
	}

	@Override
	default E remove() {
		return removeFirst();
	}

	@Override
	default E element() {
		return getFirst();
	}

	/**
	 * @return The value that was at the head of the queue before it was removed by this method
	 * @throws NoSuchElementException If the queue was empty
	 */
	default E pop() {
		return removeFirst();
	}

	@Override
	default Iterator<E> iterator() {
		return Sequenced.super.iterator();
	}
}

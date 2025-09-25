package org.qommons.collect;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.qommons.Stamped;

/**
 * A double-ended queue that implements {@link Sequenced}
 * 
 * @param <E> The type of values in the queue
 */
public interface SequencedDeque<E> extends Deque<E>, Sequenced<E>, PureQueue<E>, Stamped {
	/**
	 * @param c The collection
	 * @return Whether this collection contains any elements of the given collection
	 */
	boolean containsAny(Collection<?> c);

	@Override
	default void addFirst(E e) {
		if (!offerFirst(e))
			throw new IllegalStateException("List is full");
	}

	@Override
	default void addLast(E e) {
		if (!offerLast(e))
			throw new IllegalStateException("List is full");
	}

	@Override
	default boolean add(E e) {
		return offerLast(e);
	}

	@Override
	default E removeFirst() {
		if (isEmpty())
			throw new NoSuchElementException("List is empty");
		E v = pollFirst();
		return v;
	}

	@Override
	default E removeLast() {
		if (isEmpty())
			throw new NoSuchElementException("List is empty");
		E v = pollLast();
		return v;
	}

	@Override
	default E getFirst() {
		long stamp = getStamp();
		if (isEmpty())
			throw new NoSuchElementException("List is empty");
		E v = peekFirst();
		if (stamp != getStamp())
			throw new ConcurrentModificationException("List was modified externally");
		return v;
	}

	@Override
	default E getLast() {
		long stamp = getStamp();
		if (isEmpty())
			throw new NoSuchElementException("List is empty");
		E v = peekLast();
		if (stamp != getStamp())
			throw new ConcurrentModificationException("List was modified externally");
		return v;
	}

	@Override
	default boolean removeFirstOccurrence(Object o) {
		return remove(o);
	}

	@Override
	default boolean offer(E e) {
		return offerLast(e);
	}

	@Override
	default E remove() {
		return removeFirst();
	}

	@Override
	default E poll() {
		return pollFirst();
	}

	@Override
	default E element() {
		return getFirst();
	}

	@Override
	default E peek() {
		return peekFirst();
	}

	@Override
	default void push(E e) {
		addFirst(e);
	}

	@Override
	default E pop() {
		return removeFirst();
	}

	/**
	 * @param fromBeginning Whether to iterate forward from the beginning or backward from the end
	 * @return The sequence
	 */
	Sequence<E> sequence(boolean fromBeginning);

	@Override
	default Sequence<E> sequence() {
		return sequence(true);
	}

	/**
	 * @param fromBeginning Whether to iterate forward from the beginning or backward from the end
	 * @return The iterator
	 */
	default Iterator<E> iterator(boolean fromBeginning) {
		return new Sequence.SequenceIterator<>(sequence(fromBeginning));
	}

	@Override
	default Iterator<E> iterator() {
		return iterator(true);
	}

	@Override
	default Iterator<E> descendingIterator() {
		return iterator(false);
	}
}

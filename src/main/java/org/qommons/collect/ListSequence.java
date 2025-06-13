package org.qommons.collect;

import java.util.ListIterator;

/**
 * A {@link Sequence} for iterating over values in an indexed collection
 * 
 * @param <E> The type of values to iterate over
 */
public interface ListSequence<E> extends Sequence<E> {
	/**
	 * @return The index of the current element in the collection
	 * @throws IllegalStateException If the sequence's current element does not {@link #exists() exist}
	 */
	int getIndex() throws IllegalStateException;

	/**
	 * @param <E> The type for the sequence
	 * @return An empty sequence
	 */
	public static <E> EmptyListSequence<E> empty() {
		return (EmptyListSequence<E>) EMPTY;
	}

	/**
	 * @param <E> The type of the value
	 * @param value The value to iterate over
	 * @param index The initial index for the sequence:
	 *        <ul>
	 *        <li>&lt;0 to start before the value, so {@link Sequence#advance(boolean) advance(true)} returns true</li>
	 *        <li>0 to start on the value, so {@link Sequence#advance(boolean) advance(boolean)} returns false for any argument</li>
	 *        <li>&gt;0 to start before the value, so {@link Sequence#advance(boolean) advance(false)} returns true</li>
	 *        </ul>
	 * @return The singleton sequence
	 */
	public static <E> ListSequence<E> single(E value, int index) {
		return new SingletonListSequence<>(value, index);
	}

	/**
	 * Implements {@link ListSequence#empty()}
	 * 
	 * @param <E> The type of the sequence
	 */
	static class EmptyListSequence<E> extends Sequence.EmptySequence<E> implements ListSequence<E> {
		@Override
		public int getIndex() throws IllegalStateException {
			throw new IllegalStateException(NO_ELEMENT_AT_POSTION);
		}
	}

	/** Empty {@link ListSequence} singleton */
	public static EmptyListSequence<?> EMPTY = new EmptyListSequence<>();

	/**
	 * A {@link ListIterator} backed by a {@link ListSequence}
	 * 
	 * @param <E> The type of values to iterate over
	 */
	static class ListSequenceIterator<E> extends Sequence.SequenceIterator<E> implements ListIterator<E> {
		public ListSequenceIterator(ListSequence<E> sequence) {
			super(sequence);
		}

		@Override
		protected ListSequence<E> getSequence() {
			return (ListSequence<E>) super.getSequence();
		}

		@Override
		public int nextIndex() {
			int adj = isOnNext() ? 0 : 1;
			return getSequence().getIndex() + adj;
		}

		@Override
		public int previousIndex() {
			int adj = isOnNext() ? -1 : 0;
			return getSequence().getIndex() + adj;
		}
	}

	/**
	 * Implements {@link ListSequence#single(Object, int)}
	 * 
	 * @param <E> The type of the sequence
	 */
	static class SingletonListSequence<E> extends Sequence.SingletonSequence<E> implements ListSequence<E> {
		public SingletonListSequence(E value, int index) {
			super(value, index);
		}
	}
}

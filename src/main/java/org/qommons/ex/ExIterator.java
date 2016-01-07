package org.qommons.ex;

/**
 * A sequence of values, like an {@link java.util.Iterator}, but one that can throw a checked exception from its {@link #hasNext()} or
 * {@link #next()} method.
 *
 * @param <T> The type of value the iterator returns
 * @param <E> The type of exception the iterator may throw
 */
public interface ExIterator<T, E extends Throwable> {
	/**
	 * @return Whether there are more values to be returned from this iterator
	 * @throws E If the result cannot be determined
	 */
	boolean hasNext() throws E;

	/**
	 * @return The next value in the sequence
	 * @throws E If the result cannot be retrieved or created
	 * @throws java.util.NoSuchElementException If there are no more values in this sequence
	 */
	T next() throws E;

	/**
	 * Removes the most recent value returned from {@link #next()} from the sequence
	 *
	 * @throws UnsupportedOperationException If this operation is not supported by this iterator.  This is the default.
	 */
	default void remove(){
		throw new UnsupportedOperationException();
	}
}

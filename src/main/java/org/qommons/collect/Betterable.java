package org.qommons.collect;

/**
 * An Iterable that returns an explicitly {@link ImmutableIterator immutable iterator} as well as an explicitly {@link #mutableIterator()
 * mutable} version
 * 
 * @param <T> The type of values that are returned from this iterator
 */
public interface Betterable<T> extends Iterable<T> {
	/** @return An immutable (Iterator#remove()} will always throw an exception) iterator for iterating across this object's data */
	@Override
	ImmutableIterator<T> iterator();

	/**
	 * @return A mutable iterator that can (typically) not only remove, but also {@link Betterator#set(Object, Object) replace} elements and
	 *         supports queries on whether these operations are supported.
	 */
	Betterator<T> mutableIterator();
}

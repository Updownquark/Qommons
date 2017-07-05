package org.qommons.collect;

import java.util.Spliterator;

/**
 * An Iterable that returns an explicitly {@link ImmutableIterator immutable iterator} as well as an explicitly {@link #mutableIterator()
 * mutable} version
 * 
 * @param <E> The type of values that are returned from this iterator
 */
public interface Betterable<E> extends Iterable<E> {
	/** @return An immutable (Iterator#remove()} will always throw an exception) iterator for iterating across this object's data */
	@Override
	default ImmutableIterator<E> iterator() {
		return new ImmutableIterator.SpliteratorImmutableIterator<>(spliterator());
	}

	/**
	 * @return A mutable iterator that can (typically) not only remove, but also {@link Betterator#set(Object, Object) replace} elements and
	 *         supports queries on whether these operations are supported.
	 */
	default Betterator<E> mutableIterator() {
		return new ElementSpliterator.SpliteratorBetterator<>(mutableSpliterator());
	}

	@Override
	default Spliterator<E> spliterator() {
		return mutableSpliterator().immutable();
	}

	/** @return A (typically) mutable element spliterator to use to iterate over and modify this collection */
	ElementSpliterator<E> mutableSpliterator();
}

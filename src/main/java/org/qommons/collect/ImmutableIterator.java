package org.qommons.collect;

import java.util.Iterator;
import java.util.Spliterator;

/**
 * Same as Iterator, except the dev should not expect remove() to ever be implemented. {@link Betterable}, which supplies this class from
 * its {@link Betterable#iterator()} method, has a {@link Betterable#mutableIterator()} that will typically implement the method (if such a
 * modification is allowed on the collection) in addition to other functionality.
 * 
 * @param <T> The type of value returned from this iterator
 */
public interface ImmutableIterator<T> extends Iterator<T> {
	@Override
	default void remove() throws UnsupportedOperationException {
		throw new UnsupportedOperationException("Try Betterable.mutableIterator()");
	}

	public class SpliteratorImmutableIterator<T> implements ImmutableIterator<T> {
		private final Spliterator<T> theSpliterator;

		private boolean isNextCached;
		private boolean isDone;
		private T cachedNext;

		public SpliteratorImmutableIterator(Spliterator<T> spliterator) {
			theSpliterator = spliterator;
		}

		@Override
		public boolean hasNext() {
			if (!isNextCached && !isDone) {
				cachedNext = null;
				if (theSpliterator.tryAdvance(v -> {
					cachedNext = v;
				}))
					isNextCached = true;
				else
					isDone = true;
			}
			return isNextCached;
		}

		@Override
		public T next() {
			if (!hasNext())
				throw new java.util.NoSuchElementException();
			isNextCached = false;
			return cachedNext;
		}
	}
}

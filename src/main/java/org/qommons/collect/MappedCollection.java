package org.qommons.collect;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Function;

/**
 * A simple collection whose elements are a map of another
 * 
 * @param <S> The type of the source collection
 * @param <T> The type of this collection
 */
public class MappedCollection<S, T> extends AbstractCollection<T> {
	private final Collection<S> theWrapped;
	private final Function<? super S, T> theMap;

	/**
	 * @param wrapped The source collection to map
	 * @param map The mapping function
	 */
	public MappedCollection(Collection<S> wrapped, Function<? super S, T> map) {
		theWrapped = wrapped;
		theMap = map;
	}

	@Override
	public Iterator<T> iterator() {
		return new MappedIterator<>(theWrapped.iterator(), theMap);
	}

	@Override
	public int size() {
		return theWrapped.size();
	}

	@Override
	public void clear() {
		theWrapped.clear();
	}

	/**
	 * Implements {@link MappedCollection#iterator()}
	 * 
	 * @param <S> The type of the source iterator
	 * @param <T> The type of this iterator
	 */
	public static class MappedIterator<S, T> implements Iterator<T> {
		private final Iterator<S> theWrapedIter;
		private final Function<? super S, T> theMap;

		/**
		 * @param wrapedIter The iterator to map
		 * @param map The mapping function
		 */
		public MappedIterator(Iterator<S> wrapedIter, Function<? super S, T> map) {
			theWrapedIter = wrapedIter;
			theMap = map;
		}

		@Override
		public boolean hasNext() {
			return theWrapedIter.hasNext();
		}

		@Override
		public T next() {
			return theMap.apply(theWrapedIter.next());
		}

		@Override
		public void remove() {
			theWrapedIter.remove();
		}
	}
}

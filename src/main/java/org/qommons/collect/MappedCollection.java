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
	private final Collection<S> theSource;
	private final Function<? super S, ? extends T> theMap;

	/**
	 * @param source The source collection to map
	 * @param map The mapping function
	 */
	public MappedCollection(Collection<S> source, Function<? super S, ? extends T> map) {
		theSource = source;
		theMap = map;
	}

	/** @return The source collection */
	protected Collection<S> getSource() {
		return theSource;
	}

	/** @return The mapping function producing values in this collection from values in the source collection */
	protected Function<? super S, ? extends T> getMap() {
		return theMap;
	}

	@Override
	public Iterator<T> iterator() {
		return new MappedIterator<>(theSource.iterator(), theMap);
	}

	@Override
	public int size() {
		return theSource.size();
	}

	@Override
	public void clear() {
		theSource.clear();
	}

	/**
	 * Implements {@link MappedCollection#iterator()}
	 * 
	 * @param <S> The type of the source iterator
	 * @param <T> The type of this iterator
	 */
	public static class MappedIterator<S, T> implements Iterator<T> {
		private final Iterator<S> theWrapedIter;
		private final Function<? super S, ? extends T> theMap;

		/**
		 * @param wrapedIter The iterator to map
		 * @param map The mapping function
		 */
		public MappedIterator(Iterator<S> wrapedIter, Function<? super S, ? extends T> map) {
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

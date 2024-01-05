package org.qommons.collect;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Function;

import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * A simple list whose elements are a map of another
 * 
 * @param <S> The type of the source list
 * @param <T> The type of this list
 */
public class MappedList<S, T> extends AbstractList<T> {
	private final List<S> theWrapped;
	private final Function<? super S, T> theMap;

	/**
	 * @param wrapped The source list to map
	 * @param map The mapping function
	 */
	public MappedList(List<S> wrapped, Function<? super S, T> map) {
		theWrapped = wrapped;
		theMap = map;
	}

	@Override
	public void clear() {
		theWrapped.clear();
	}

	@Override
	public Iterator<T> iterator() {
		return new MappedCollection.MappedIterator<>(theWrapped.iterator(), theMap);
	}

	@Override
	public ListIterator<T> listIterator() {
		return new MappedListIterator<>(theWrapped.listIterator(), theMap);
	}

	@Override
	public T get(int index) {
		return theMap.apply(theWrapped.get(index));
	}

	@Override
	public int size() {
		return theWrapped.size();
	}

	/**
	 * Implements {@link MappedList#listIterator(int)}
	 * 
	 * @param <S> The type of the source iterator
	 * @param <T> The type of this iterator
	 */
	public static class MappedListIterator<S, T> implements ListIterator<T> {
		private final ListIterator<S> theWrappedIter;
		private final Function<? super S, T> theMap;

		/**
		 * @param wrappedIter The iterator to wrap
		 * @param map The mapping function
		 */
		public MappedListIterator(ListIterator<S> wrappedIter, Function<? super S, T> map) {
			theWrappedIter = wrappedIter;
			theMap = map;
		}

		@Override
		public boolean hasNext() {
			return theWrappedIter.hasNext();
		}

		@Override
		public T next() {
			return theMap.apply(theWrappedIter.next());
		}

		@Override
		public boolean hasPrevious() {
			return false;
		}

		@Override
		public T previous() {
			return theMap.apply(theWrappedIter.previous());
		}

		@Override
		public int nextIndex() {
			return theWrappedIter.nextIndex();
		}

		@Override
		public int previousIndex() {
			return theWrappedIter.previousIndex();
		}

		@Override
		public void remove() {
			theWrappedIter.remove();
		}

		@Override
		public void set(T e) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void add(T e) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}
	}
}

package org.qommons;

import java.util.Collection;
import java.util.Iterator;

/**
 * A collection that can be reversed
 * 
 * @param <E> The type of values in the collection
 */
public interface ReversibleCollection<E> extends Collection<E> {
	/** @return An iterable that iterates through this collection's values in reverse */
	Iterable<E> descending();

	/** @return A collection that is identical to this one, but with its elements reversed */
	default ReversibleCollection<E> reverse() {
		return new ReversedCollection<>(this);
	}

	/**
	 * Implements {@link ReversibleCollection#reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ReversedCollection<E> implements ReversibleCollection<E> {
		private final ReversibleCollection<E> theWrapped;

		protected ReversedCollection(ReversibleCollection<E> wrap) {
			theWrapped = wrap;
		}

		protected ReversibleCollection<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public Iterator<E> iterator() {
			return theWrapped.descending().iterator();
		}

		@Override
		public ReversibleCollection<E> reverse() {
			return theWrapped;
		}

		@Override
		public Iterable<E> descending() {
			return () -> theWrapped.iterator();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return theWrapped.contains(o);
		}

		@Override
		public Object[] toArray() {
			Object[] ret = theWrapped.toArray();
			ArrayUtils.reverse(ret);
			return ret;
		}

		@Override
		public <T> T[] toArray(T[] a) {
			T[] ret = theWrapped.toArray(a);
			ArrayUtils.reverse(ret);
			return ret;
		}

		@Override
		public boolean add(E e) {
			return theWrapped.add(e);
		}

		@Override
		public boolean remove(Object o) {
			return theWrapped.remove(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theWrapped.containsAll(c);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return theWrapped.addAll(c);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theWrapped.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theWrapped.retainAll(c);
		}

		@Override
		public void clear() {
			theWrapped.clear();
		}
	}
}

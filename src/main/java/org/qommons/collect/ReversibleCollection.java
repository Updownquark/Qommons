package org.qommons.collect;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

import org.qommons.ArrayUtils;

/**
 * A collection that can be reversed
 * 
 * @param <E> The type of values in the collection
 */
public interface ReversibleCollection<E> extends BetterCollection<E> {
	/** @return An iterable that iterates through this collection's values in reverse */
	default Iterable<E> descending() {
		return () -> descendingIterator();
	}

	/** @return An iterator to traverse this collection's elements in reverse */
	default Betterator<E> descendingIterator() {
		return new ElementSpliterator.SpliteratorBetterator<>(spliterator(false).reverse());
	}

	@Override
	default ReversibleSpliterator<E> spliterator() {
		return spliterator(true);
	}

	/**
	 * @param fromStart Whether the spliterator should begin at the beginning or the end of this collection
	 * @return The spliterator
	 */
	ReversibleSpliterator<E> spliterator(boolean fromStart);

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
		public Iterable<E> descending() {
			return () -> theWrapped.iterator();
		}

		@Override
		public Betterator<E> iterator() {
			return theWrapped.descendingIterator();
		}

		@Override
		public Betterator<E> descendingIterator() {
			return theWrapped.iterator();
		}

		@Override
		public ReversibleSpliterator<E> spliterator(boolean fromStart) {
			return theWrapped.spliterator(!fromStart).reverse();
		}

		@Override
		public ReversibleCollection<E> reverse() {
			return theWrapped;
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
		public boolean containsAny(Collection<?> c) {
			return theWrapped.containsAny(c);
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

		@Override
		public int hashCode() {
			int hash = 0;
			for (E v : this) {
				hash += v == null ? 0 : v.hashCode();
			}
			return hash;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Collection))
				return false;
			Collection<?> c = (Collection<?>) o;
			Iterator<E> iter = iterator();
			Iterator<?> cIter = c.iterator();
			while (iter.hasNext()) {
				if (!cIter.hasNext())
					return false;
				if (!Objects.equals(iter.next(), cIter.next()))
					return false;
			}
			if (cIter.hasNext())
				return false;
			return true;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append('[');
			boolean first = true;
			for (E v : this) {
				if (!first)
					str.append(", ");
				first = false;
				str.append(v);
			}
			str.append(']');
			return str.toString();
		}
	}
}

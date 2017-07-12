package org.qommons.collect;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.qommons.ArrayUtils;

/**
 * A collection that can be reversed
 * 
 * @param <E> The type of values in the collection
 */
public interface ReversibleCollection<E> extends BetterCollection<E>, ReversibleIterable<E> {
	/**
	 * Removes the last occurrence of the given value in this collection, if it exists
	 * 
	 * @param o The value to remove
	 * @return Whether the value was found (and removed)
	 */
	boolean removeLast(Object o);

	@Override
	default boolean forElement(E value, Consumer<? super CollectionElement<? extends E>> onElement) {
		return forElement(value, onElement, true);
	}

	boolean forElement(E value, Consumer<? super CollectionElement<? extends E>> onElement, boolean first);

	/**
	 * Finds a value in this collection matching the given search and performs an action on the {@link CollectionElement} for that element
	 * 
	 * @param search The search function
	 * @param onElement The action to perform on the search's result
	 * @param first Whether to find the first or the last element which passes the test
	 * @return Whether a result was found
	 */
	default boolean find(Predicate<? super E> search, Consumer<? super CollectionElement<? extends E>> onElement, boolean first) {
		ElementSpliterator<E> spliter = mutableSpliterator(first);
		boolean[] found = new boolean[1];
		while (spliter.tryAdvanceElement(el -> {
			if (search.test(el.get())) {
				found[0] = true;
				onElement.accept(el);
			}
		})) {
		}
		return found[0];
	}

	/**
	 * Finds all values in this collection matching the given search and performs an action on the {@link CollectionElement} for each
	 * element
	 * 
	 * @param search The search function
	 * @param onElement The action to perform on the search's results
	 * @param fromStart Whether the spliterator should begin at the beginning or the end of this collection
	 * @return The number of results found
	 */
	@Override
	default int findAll(Predicate<? super E> search, Consumer<? super CollectionElement<? extends E>> onElement, boolean fromStart) {
		ElementSpliterator<E> spliter = mutableSpliterator(fromStart);
		int[] found = new int[1];
		while (spliter.tryAdvanceElement(el -> {
			if (search.test(el.get())) {
				found[0]++;
				onElement.accept(el);
			}
		})) {
		}
		return found[0];
	}

	/** @return A collection that is identical to this one, but with its elements reversed */
	@Override
	default ReversibleCollection<E> reverse() {
		return new ReversedCollection<>(this);
	}

	@Override
	default ReversibleSpliterator<E> spliterator() {
		return ReversibleIterable.super.spliterator();
	}

	@Override
	default ReversibleElementSpliterator<E> mutableSpliterator() {
		return ReversibleIterable.super.mutableSpliterator();
	}

	/**
	 * Implements {@link ReversibleCollection#reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ReversedCollection<E> extends ReversedIterable<E> implements ReversibleCollection<E> {
		protected ReversedCollection(ReversibleCollection<E> wrap) {
			super(wrap);
		}

		@Override
		protected ReversibleCollection<E> getWrapped() {
			return (ReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public int size() {
			return getWrapped().size();
		}

		@Override
		public boolean forElement(E value, Consumer<? super CollectionElement<? extends E>> onElement, boolean first) {
			return getWrapped().forElement(value, el -> onElement.accept(new ReversibleElementSpliterator.ReversedCollectionElement<>(el)),
				!first);
		}

		@Override
		public ReversibleSpliterator<E> spliterator(boolean fromStart) {
			return getWrapped().spliterator(!fromStart).reverse();
		}

		@Override
		public ReversibleElementSpliterator<E> mutableSpliterator(boolean fromStart) {
			return getWrapped().mutableSpliterator(!fromStart).reverse();
		}

		@Override
		public ReversibleCollection<E> reverse() {
			return getWrapped();
		}

		@Override
		public boolean isEmpty() {
			return getWrapped().isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return getWrapped().contains(o);
		}

		@Override
		public Object[] toArray() {
			Object[] ret = getWrapped().toArray();
			ArrayUtils.reverse(ret);
			return ret;
		}

		@Override
		public <T> T[] toArray(T[] a) {
			T[] ret = getWrapped().toArray(a);
			ArrayUtils.reverse(ret);
			return ret;
		}

		@Override
		public boolean add(E e) {
			return getWrapped().add(e);
		}

		@Override
		public boolean remove(Object o) {
			return getWrapped().removeLast(o);
		}

		@Override
		public boolean removeLast(Object o) {
			return getWrapped().remove(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return getWrapped().containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return getWrapped().containsAny(c);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return getWrapped().addAll(c);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return getWrapped().removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return getWrapped().retainAll(c);
		}

		@Override
		public void clear() {
			getWrapped().clear();
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

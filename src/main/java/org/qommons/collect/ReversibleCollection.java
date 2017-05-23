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
public interface ReversibleCollection<E> extends BetterCollection<E> {
	/** @return An iterable that iterates through this collection's values in reverse */
	default Iterable<E> descending() {
		return () -> descendingIterator();
	}

	/** @return An iterator to traverse this collection's elements in reverse */
	default Betterator<E> descendingIterator() {
		return new ElementSpliterator.SpliteratorBetterator<>(spliterator(false).reverse());
	}

	/**
	 * Removes the last occurrence of the given value in this collection, if it exists
	 * 
	 * @param o The value to remove
	 * @return Whether the value was found (and removed)
	 */
	boolean removeLast(Object o);

	@Override
	default ReversibleSpliterator<E> spliterator() {
		return spliterator(true);
	}

	/**
	 * @param fromStart Whether the spliterator should begin at the beginning or the end of this collection
	 * @return The spliterator
	 */
	ReversibleSpliterator<E> spliterator(boolean fromStart);

	/**
	 * @param value The value to search for
	 * @param first Whether to find the first or the last matching element
	 * @return The element in this collection matching the given value, or null if there is no such value in this collection
	 */
	default CollectionElement<E> elementFor(Object value, boolean first) {
		ElementSpliterator<E> spliter = spliterator(first);
		CollectionElement<E>[] foundEl = new CollectionElement[1];
		while (foundEl[0] == null && spliter.tryAdvanceElement(el -> {
			if (Objects.equals(el.get(), value))
				foundEl[0] = el;
		})) {
		}
		return foundEl[0];
	}

	/**
	 * Finds a value in this collection matching the given search and performs an action on the {@link CollectionElement} for that element
	 * 
	 * @param search The search function
	 * @param onElement The action to perform on the search's result
	 * @param first Whether to find the first or the last element which passes the test
	 * @return Whether a result was found
	 */
	default boolean find(Predicate<? super E> search, Consumer<? super CollectionElement<? extends E>> onElement, boolean first) {
		ElementSpliterator<E> spliter = spliterator(first);
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
	default int findAll(Predicate<? super E> search, Consumer<? super CollectionElement<? extends E>> onElement, boolean fromStart) {
		ElementSpliterator<E> spliter = spliterator(fromStart);
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
			return theWrapped.removeLast(o);
		}

		@Override
		public boolean removeLast(Object o) {
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

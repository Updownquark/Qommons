package org.qommons.collect;

import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.qommons.ArrayUtils;

/**
 * A collection that can be reversed
 * 
 * @param <E> The type of values in the collection
 */
public interface ReversibleCollection<E> extends BetterCollection<E>, ReversibleIterable<E>, Deque<E> {
	@Override
	default ImmutableIterator<E> iterator() {
		return BetterCollection.super.iterator();
	}

	@Override
	default boolean contains(Object o) {
		return BetterCollection.super.contains(o);
	}

	/**
	 * Removes the last occurrence of the given value in this collection, if it exists
	 * 
	 * @param o The value to remove
	 * @return Whether the value was found (and removed)
	 */
	default boolean removeLast(Object o) {
		return removeLastOccurrence(o);
	}

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

	@Override
	default void addFirst(E e) {
		try {
			if (!mutableSpliterator(true).tryAdvanceElement(el -> el.add(e, true, null)))
				throw new IllegalStateException("Could not add element");
		} catch (UnsupportedOperationException | IllegalArgumentException ex) {
			throw new IllegalStateException("Could not add element", ex);
		}
	}

	@Override
	default void addLast(E e) {
		try {
			if (!mutableSpliterator(false).tryReverseElement(el -> el.add(e, false, null)))
				throw new IllegalStateException("Could not add element");
		} catch (UnsupportedOperationException | IllegalArgumentException ex) {
			throw new IllegalStateException("Could not add element", ex);
		}
	}

	@Override
	default boolean offerFirst(E e) {
		boolean[] success = new boolean[1];
		if (!mutableSpliterator(true).tryAdvanceElement(el -> {
			if (el.canAdd(e, true) == null) {
				el.add(e, true, null);
				success[0] = true;
			}
		}))
			success[0] = add(e);
		return success[0];
	}

	@Override
	default boolean offerLast(E e) {
		boolean[] success = new boolean[1];
		if (!mutableSpliterator(false).tryReverseElement(el -> {
			if (el.canAdd(e, false) == null) {
				el.add(e, false, null);
				success[0] = true;
			}
		}))
			success[0] = add(e);
		return success[0];
	}

	@Override
	default E removeFirst() {
		Object[] value = new Object[1];
		if (!mutableSpliterator(true).tryAdvanceElement(el -> {
			value[0] = el.get();
			el.remove(null);
		}))
			throw new NoSuchElementException("Empty collection");
		return (E) value[0];
	}

	@Override
	default E removeLast() {
		Object[] value = new Object[1];
		if (!mutableSpliterator(true).tryAdvanceElement(el -> {
			value[0] = el.get();
			el.remove(null);
		}))
			throw new NoSuchElementException("Empty collection");
		return (E) value[0];
	}

	@Override
	default E pollFirst() {
		Object[] value = new Object[1];
		mutableSpliterator(true).tryAdvanceElement(el -> {
			value[0] = el.get();
			el.remove(null); // The Deque contract says nothing about what to do if the element can't be removed, so we'll throw an
								// exception
		});
		return (E) value[0];
	}

	@Override
	default E pollLast() {
		Object[] value = new Object[1];
		mutableSpliterator(false).tryReverseElement(el -> {
			value[0] = el.get();
			el.remove(null); // The Deque contract says nothing about what to do if the element can't be removed, so we'll throw an
								// exception
		});
		return (E) value[0];
	}

	@Override
	default E getFirst() {
		Object[] value = new Object[1];
		if (!spliterator(true).tryAdvance(v -> value[0] = v))
			throw new NoSuchElementException("Empty collection");
		return (E) value[0];
	}

	@Override
	default E getLast() {
		Object[] value = new Object[1];
		spliterator(false).tryReverse(v -> value[0] = v);
		return (E) value[0];
	}

	@Override
	default E peekFirst() {
		Object[] value = new Object[1];
		spliterator(true).tryAdvance(v -> value[0] = v);
		return (E) value[0];
	}

	@Override
	default E peekLast() {
		Object[] value = new Object[1];
		spliterator(false).tryReverse(v -> value[0] = v);
		return (E) value[0];
	}

	@Override
	default boolean removeFirstOccurrence(Object o) {
		return forElement((E) o, el -> SimpleCause.doWith(new SimpleCause(), c -> el.remove(c)), true);
	}

	@Override
	default boolean removeLastOccurrence(Object o) {
		return forElement((E) o, el -> SimpleCause.doWith(new SimpleCause(), c -> el.remove(c)), false);
	}

	@Override
	default boolean offer(E e) {
		return offerLast(e);
	}

	@Override
	default E remove() {
		return removeFirst();
	}

	@Override
	default E poll() {
		return pollFirst();
	}

	@Override
	default E element() {
		return getFirst();
	}

	@Override
	default E peek() {
		return peekFirst();
	}

	@Override
	default void push(E e) {
		addFirst(e);
	}

	@Override
	default E pop() {
		return removeFirst();
	}

	@Override
	default boolean remove(Object o) {
		return removeFirstOccurrence(o);
	}

	@Override
	default Iterator<E> descendingIterator() {
		return reverse().iterator();
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
		public boolean belongs(Object o){
			return getWrapped().belongs(o);
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
		public boolean addAll(Collection<? extends E> c) {
			return getWrapped().addAll(c);
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

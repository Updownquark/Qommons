package org.qommons.collect;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.qommons.ArrayUtils;
import org.qommons.Transactable;
import org.qommons.Transaction;

/**
 * A Collection whose primary means of iteration is backed by {@link #spliterator()} and that also provides a {@link #mutableSpliterator()
 * mutable spliterator}.
 * 
 * @param <E> The type of value in the collection
 */
public interface BetterCollection<E> extends Deque<E> {
	boolean belongs(Object o);

	@Override
	default boolean contains(Object o) {
		if (!belongs(o))
			return false;
		return forMutableElement((E) o, el -> {
		}, true);
	}

	/**
	 * @param c The collection to test
	 * @return Whether this collection contains any of the given collection's elements
	 */
	default boolean containsAny(Collection<?> c) {
		try (Transaction ct = Transactable.lock(c, false, null)) {
			if (c.isEmpty())
				return true;
			if (c.size() < size()) {
				for (Object o : c)
					if (contains(o))
						return true;
				return false;
			} else {
				if (c.isEmpty())
					return false;
				Set<Object> cSet = new HashSet<>(c);
				Spliterator<E> iter = spliterator();
				boolean[] found = new boolean[1];
				while (iter.tryAdvance(next -> {
					found[0] = cSet.contains(next);
				}) && !found[0]) {
				}
				return found[0];
			}
		}
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		try (Transaction ct = Transactable.lock(c, false, null)) {
			if (c.isEmpty())
				return true;
			if (c.size() < size()) {
				for (Object o : c)
					if (!contains(o))
						return false;
				return true;
			} else {
				if (c.isEmpty())
					return false;
				Set<Object> cSet = new HashSet<>(c);
				cSet.removeAll(this);
				return cSet.isEmpty();
			}
		}
	}

	@Override
	default boolean remove(Object o) {
		if (!belongs(o))
			return false;
		return forMutableElement((E) o, el -> el.remove(), true);
	}

	default boolean removeLast(Object o) {
		if (!belongs(o))
			return false;
		return forMutableElement((E) o, el -> el.remove(), false);
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		if (isEmpty() || c.isEmpty())
			return false;
		return findAll(v -> c.contains(v), el -> el.remove(), true) > 0;
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		if (isEmpty())
			return false;
		if (c.isEmpty()) {
			clear();
			return true;
		}
		return findAll(v -> !c.contains(v), el -> el.remove(), true) > 0;
	}

	/**
	 * Optionally replaces each value in this collection with a mapped value. For every element, the map will be applied. If the result is
	 * identically (==) different from the existing value, that element will be replaced with the mapped value.
	 *
	 * @param map The map to apply to each value in this collection
	 * @param soft If true, this method will attempt to determine whether each differing mapped value is acceptable as a replacement. This
	 *        may, but is not guaranteed to, prevent {@link IllegalArgumentException}s
	 * @return Whether any elements were replaced
	 * @throws IllegalArgumentException If a mapped value is not acceptable as a replacement
	 */
	default boolean replaceAll(Function<? super E, ? extends E> map, boolean soft) {
		boolean[] replaced = new boolean[1];
		MutableElementSpliterator<E> iter = mutableSpliterator();
		iter.forEachElementM(el -> {
			E value = el.get();
			E newValue = map.apply(value);
			if (value != newValue && (!soft || el.isAcceptable(newValue) == null)) {
				el.set(newValue);
				replaced[0] = true;
			}
		});
		return replaced[0];
	}

	/**
	 * Replaces each value in this collection with a mapped value. For every element, the operation will be applied. If the result is
	 * identically (==) different from the existing value, that element will be replaced with the mapped value.
	 *
	 * @param op The operation to apply to each value in this collection
	 */
	default void replaceAll(UnaryOperator<E> op) {
		replaceAll(v -> op.apply(v), false);
	}

	/**
	 * Finds an equivalent value in this collection
	 *
	 * @param value The value to find
	 * @param onElement The listener to be called with the equivalent element
	 * @param first Whether to find the first or last occurrence of the value
	 * @return Whether the value was found
	 */
	boolean forElement(E value, Consumer<? super ElementHandle<? extends E>> onElement, boolean first);

	/**
	 * @param value The value to search for
	 * @param onElement The action to perform on the element containing the given value, if found
	 * @return Whether such a value was found
	 */
	boolean forMutableElement(E value, Consumer<? super MutableElementHandle<? extends E>> onElement, boolean first);

	/**
	 * Addresses an element in this collection
	 *
	 * @param elementId The element to get
	 * @param onElement The listener to be called with the element
	 * @throws IllegalArgumentException If the given element ID is unrecognized in this collection
	 */
	default void forElementAt(ElementId elementId, Consumer<? super ElementHandle<? extends E>> onElement) {
		ofElementAt(elementId, el -> {
			onElement.accept(el);
			return null;
		});
	}

	/**
	 * Addresses an element in this collection
	 *
	 * @param elementId The element to get
	 * @param onElement The listener to be called with the mutable element
	 * @throws IllegalArgumentException If the given element ID is unrecognized in this collection
	 */
	default void forMutableElementAt(ElementId elementId, Consumer<? super MutableElementHandle<? extends E>> onElement) {
		ofMutableElementAt(elementId, el -> {
			onElement.accept(el);
			return null;
		});
	}

	/**
	 * Calls a function on an element
	 *
	 * @param elementId The element to apply the function to
	 * @param onElement The function to be called on the element
	 * @return The result of the function
	 * @throws IllegalArgumentException If the given element ID is unrecognized in this collection
	 */
	<T> T ofElementAt(ElementId elementId, Function<? super ElementHandle<? extends E>, T> onElement);

	/**
	 * Calls a function on an element
	 *
	 * @param elementId The element to apply the function to
	 * @param onElement The function to be called on the mutable element
	 * @return The result of the function
	 * @throws IllegalArgumentException If the given element ID is unrecognized in this collection
	 */
	<T> T ofMutableElementAt(ElementId elementId, Function<? super MutableElementHandle<? extends E>, T> onElement);

	/**
	 * Finds a value in this collection matching the given search and performs an action on the {@link MutableElementHandle} for that
	 * element
	 * 
	 * @param search The search function
	 * @param onElement The action to perform on the search's result
	 * @return Whether a result was found
	 */
	default boolean find(Predicate<? super E> search, Consumer<? super MutableElementHandle<? extends E>> onElement, boolean first) {
		MutableElementSpliterator<E> spliter = mutableSpliterator();
		boolean[] found = new boolean[1];
		while (spliter.tryAdvanceElementM(el -> {
			if (search.test(el.get())) {
				found[0] = true;
				onElement.accept(el);
			}
		})) {
		}
		return found[0];
	}

	/**
	 * Finds all values in this collection matching the given search and performs an action on the {@link MutableElementHandle} for each
	 * element
	 * 
	 * @param search The search function
	 * @param onElement The action to perform on the search's results
	 * @return The number of results found
	 */
	default int findAll(Predicate<? super E> search, Consumer<? super MutableElementHandle<? extends E>> onElement, boolean forward) {
		MutableElementSpliterator<E> spliter = mutableSpliterator();
		int[] found = new int[1];
		while (spliter.tryAdvanceElementM(el -> {
			if (search.test(el.get())) {
				found[0]++;
				onElement.accept(el);
			}
		})) {
		}
		return found[0];
	}

	@Override
	default ElementSpliterator<E> spliterator() {
		return spliterator(true);
	}

	default ElementSpliterator<E> spliterator(boolean fromStart) {
		return mutableSpliterator(fromStart).immutable();
	}

	/** @return An immutable (Iterator#remove()} will always throw an exception) iterator for iterating across this object's data */
	@Override
	default ImmutableIterator<E> iterator() {
		return new ImmutableIterator.SpliteratorImmutableIterator<>(spliterator());
	}

	default MutableElementSpliterator<E> mutableSpliterator() {
		return mutableSpliterator(true);
	}

	MutableElementSpliterator<E> mutableSpliterator(boolean fromStart);

	/**
	 * Implements {@link ReversibleCollection#reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ReversedCollection<E> implements BetterCollection<E> {
		private final BetterCollection<E> theWrapped;

		protected ReversedCollection(BetterCollection<E> wrap) {
			theWrapped = wrap;
		}

		protected BetterCollection<E> getWrapped() {
			return (BetterCollection<E>) super.getWrapped();
		}

		@Override
		public boolean belongs(Object o) {
			return getWrapped().belongs(o);
		}

		@Override
		public int size() {
			return getWrapped().size();
		}

		@Override
		public boolean forElement(E value, Consumer<? super MutableElementHandle<? extends E>> onElement, boolean first) {
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

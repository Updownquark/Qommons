package org.qommons.collect;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.qommons.Transactable;
import org.qommons.Transaction;

/**
 * A Collection whose primary means of iteration is backed by {@link #spliterator()} and that also provides a {@link #mutableSpliterator()
 * mutable spliterator}.
 * 
 * @param <E> The type of value in the collection
 */
public interface BetterCollection<E> extends Collection<E>, Betterable<E> {
	boolean belongs(Object o);

	@Override
	default boolean contains(Object o) {
		if (!belongs(o))
			return false;
		return forElement((E) o, el -> {
		});
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
		return forElement((E) o, el -> SimpleCause.doWith(new SimpleCause(), c -> el.remove(c)));
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		return SimpleCause.doWithF(new SimpleCause(), cause -> {
			if (isEmpty() || c.isEmpty())
				return false;
			return findAll(v -> c.contains(v), el -> el.remove(cause), true) > 0;
		});
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		return SimpleCause.doWithF(new SimpleCause(), cause -> {
			if (isEmpty())
				return false;
			if (c.isEmpty()) {
				clear();
				return true;
			}
			return findAll(v -> !c.contains(v), el -> el.remove(cause), true) > 0;
		});
	}

	/**
	 * @param value The value to search for
	 * @param onElement The action to perform on the element containing the given value, if found
	 * @return Whether such a value was found
	 */
	boolean forElement(E value, Consumer<? super CollectionElement<? extends E>> onElement);

	/**
	 * Finds a value in this collection matching the given search and performs an action on the {@link CollectionElement} for that element
	 * 
	 * @param search The search function
	 * @param onElement The action to perform on the search's result
	 * @return Whether a result was found
	 */
	default boolean find(Predicate<? super E> search, Consumer<? super CollectionElement<? extends E>> onElement) {
		ElementSpliterator<E> spliter = mutableSpliterator();
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
	 * @return The number of results found
	 */
	default int findAll(Predicate<? super E> search, Consumer<? super CollectionElement<? extends E>> onElement, boolean forward) {
		ElementSpliterator<E> spliter = mutableSpliterator();
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
	default ImmutableIterator<E> iterator() {
		return Betterable.super.iterator();
	}

	@Override
	default Spliterator<E> spliterator() {
		return Betterable.super.spliterator();
	}
}

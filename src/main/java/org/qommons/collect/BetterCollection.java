package org.qommons.collect;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A Collection whose {@link #spliterator()} method returns an {@link ElementSpliterator}
 * 
 * @param <E> The type of value in the collection
 */
public interface BetterCollection<E> extends Collection<E> {
	@Override
	ElementSpliterator<E> spliterator();

	/**
	 * @param c The collection to test
	 * @return Whether this collection contains any of the given collection's elements
	 */
	boolean containsAny(Collection<?> c);

	/**
	 * @param value The value to search for
	 * @return The element in this collection matching the given value, or null if there is no such value in this collection
	 */
	default CollectionElement<E> elementFor(Object value) {
		ElementSpliterator<E> spliter = spliterator();
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
	 * @return Whether a result was found
	 */
	default boolean find(Predicate<? super E> search, Consumer<? super CollectionElement<? extends E>> onElement) {
		ElementSpliterator<E> spliter = spliterator();
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
	default int findAll(Predicate<? super E> search, Consumer<? super CollectionElement<? extends E>> onElement) {
		ElementSpliterator<E> spliter = spliterator();
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
	default Betterator<E> iterator() {
		return new ElementSpliterator.SpliteratorBetterator<>(spliterator());
	}
}

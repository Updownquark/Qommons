package org.qommons.collect;

import java.util.Collection;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A Collection whose primary means of iteration is backed by {@link #spliterator()} and that also provides a {@link #mutableSpliterator()
 * mutable spliterator}.
 * 
 * @param <E> The type of value in the collection
 */
public interface BetterCollection<E> extends Collection<E>, Betterable<E> {
	/**
	 * @param c The collection to test
	 * @return Whether this collection contains any of the given collection's elements
	 */
	boolean containsAny(Collection<?> c);

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
	default int findAll(Predicate<? super E> search, Consumer<? super CollectionElement<? extends E>> onElement) {
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

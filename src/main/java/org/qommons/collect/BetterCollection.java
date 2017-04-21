package org.qommons.collect;

import java.util.Collection;

/**
 * A Collection whose {@link #spliterator()} method returns an {@link ElementSpliterator}
 * 
 * @param <E> The type of value in the collection
 */
public interface BetterCollection<E> extends Collection<E> {
	@Override
	ElementSpliterator<E> spliterator();

	@Override
	default Betterator<E> iterator() {
		return new ElementSpliterator.SpliteratorBetterator<>(spliterator());
	}
}

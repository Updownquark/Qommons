package org.qommons.collect;

import java.util.Collection;

public interface BetterCollection<E> extends Collection<E> {
	@Override
	ElementSpliterator<E> spliterator();

	@Override
	default Betterator<E> iterator() {
		return new ElementSpliterator.SpliteratorBetterator<>(spliterator());
	}
}

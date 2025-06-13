package org.qommons.collect;

import java.util.Iterator;

import org.qommons.Betterable;

/**
 * An Iterable that can also be iterated over by a {@link Sequence}
 * 
 * @param <E> The type of values this iterable iterates over
 */
public interface Sequenced<E> extends Betterable<E> {
	/** @return A sequence to iterate over this iterable's values */
	Sequence<E> sequence();

	@Override
	default Iterator<E> iterator() {
		return new Sequence.SequenceIterator<>(sequence());
	}
}

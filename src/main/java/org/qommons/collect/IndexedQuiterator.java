package org.qommons.collect;

import java.util.function.Consumer;

public interface IndexedQuiterator<T> extends Quiterator<T> {
	interface IndexedElement<T> extends CollectionElement<T> {
		int getIndex();
	}

	boolean tryIndexedAdvance(Consumer<? super IndexedElement<T>> action);

	default void forEachIndexeElement(Consumer<? super IndexedElement<T>> action) {
		while (tryIndexedAdvance(action)) {
		}
	}

	@Override
	default boolean tryAdvanceElement(Consumer<? super CollectionElement<T>> action) {
		return tryIndexedAdvance(action);
	}
}
package org.qommons.collect;

import java.util.function.Consumer;

public interface IndexedQuiterator<T> extends Quiterator<T>{
	interface IndexedElement<T> extends CollectionElement<T> {
		int getIndex();
	}

	boolean tryIndexedAdvance(Consumer<? super IndexedElement<? extends T>> action);

	@Override
	default boolean tryAdvanceElement(Consumer<? super CollectionElement<? extends T>> action) {
		return tryIndexedAdvance(action);
	}
}
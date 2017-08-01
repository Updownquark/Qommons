package org.qommons.collect;

import java.util.Iterator;

public class SpliteratorIterator<T> implements Iterator<T> {
	private final BetterCollection<T> theCollection;
	private final ElementSpliterator<T> theSpliterator;

	private boolean isNextCached;
	private CollectionElement<T> cachedNext;

	public SpliteratorIterator(BetterCollection<T> collection, ElementSpliterator<T> spliterator) {
		theCollection = collection;
		theSpliterator = spliterator;
	}

	@Override
	public boolean hasNext() {
		if (!isNextCached) {
			cachedNext = null;
			if (theSpliterator.tryAdvanceElement(el -> cachedNext = el))
				isNextCached = true;
		}
		return isNextCached;
	}

	@Override
	public T next() {
		if (!hasNext())
			throw new java.util.NoSuchElementException();
		isNextCached = false;
		return cachedNext.get();
	}

	@Override
	public void remove() {
		if (cachedNext == null)
			throw new IllegalStateException("iterator is finished or has not started");
		theCollection.forMutableElement(cachedNext.getElementId(), el -> el.remove());
	}
}
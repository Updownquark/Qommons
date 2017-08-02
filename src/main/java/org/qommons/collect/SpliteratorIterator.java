package org.qommons.collect;

import java.util.Iterator;

public class SpliteratorIterator<E> implements Iterator<E> {
	private final MutableElementSpliterator<E> theSpliterator;

	private boolean isNextCached;
	private CollectionElement<E> cachedNext;
	private ElementId theLastElement;

	public SpliteratorIterator(MutableElementSpliterator<E> spliterator) {
		theSpliterator = spliterator;
	}

	@Override
	public boolean hasNext() {
		if (!isNextCached) {
			cachedNext = null;
			if (theSpliterator.forElement(el -> cachedNext = el, true)) {
				theLastElement = cachedNext.getElementId();
				isNextCached = true;
			}
		}
		return isNextCached;
	}

	@Override
	public E next() {
		if (!hasNext())
			throw new java.util.NoSuchElementException();
		isNextCached = false;
		E value = cachedNext.get();
		cachedNext = null;
		return value;
	}

	@Override
	public void remove() {
		if (theLastElement == null)
			throw new IllegalStateException("iterator is finished, not started, or the element has been removed");
		if (!theSpliterator.forElementM(el -> {
			if (!el.getElementId().equals(theLastElement))
				throw new IllegalStateException("element has been removed");
			el.remove();
		}, false))
			throw new IllegalStateException("element has been removed");
		theLastElement = null;
	}
}
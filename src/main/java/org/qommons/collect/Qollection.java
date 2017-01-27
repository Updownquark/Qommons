package org.qommons.collect;

import java.util.Collection;
import java.util.Iterator;

public interface Qollection<E> extends Collection<E> {

	@Override
	default Iterator<E> iterator() {
		return QuiteratorIterator<>(spliterator());
	}

	@Override
	abstract Quiterator<E> spliterator();

	class QuiteratorIterator<E> implements Iterator<E> {
		private final Spliterator<E> theSpliterator;

		@Override
		public boolean hasNext() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public E next() {
			// TODO Auto-generated method stub
			return null;
		}
	}
}

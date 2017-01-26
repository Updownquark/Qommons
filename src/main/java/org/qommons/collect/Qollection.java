package org.qommons.collect;

import java.util.Collection;
import java.util.Iterator;

public interface Qollection<E> extends Collection<E> {

	@Override
	default Iterator<E> iterator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	abstract Quiterator<E> spliterator();
}

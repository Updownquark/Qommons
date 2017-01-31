package org.qommons.collect;

import java.util.Iterator;
import java.util.Set;

public interface QSet<E> extends Qollection<E>, Set<E> {
	@Override
	default Iterator<E> iterator() {
		return Qollection.super.iterator();
	}

}

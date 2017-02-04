package org.qommons.collect;

import com.google.common.reflect.TypeToken;

public interface QList<E> extends ReversibleQollection<E>, TransactableList<E> {
	static <E> QList<E> constant(TypeToken<E> type, E... values) {
		// TODO Auto-generated method stub
	}
}

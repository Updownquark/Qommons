package org.qommons.tree;

public interface Tree2<T> {
	T getValue();

	Iterable<? extends Tree2<T>> getChildren();
}

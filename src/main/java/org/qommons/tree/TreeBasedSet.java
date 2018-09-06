package org.qommons.tree;

import org.qommons.collect.BetterSortedSet;

public interface TreeBasedSet<E> extends TreeBasedSortedList<E>, BetterSortedSet<E> {
	@Override
	default BinaryTreeNode<E> getElement(E value, boolean first) {
		return (BinaryTreeNode<E>) BetterSortedSet.super.getElement(value, first);
	}

	@Override
	BinaryTreeNode<E> getOrAdd(E value, boolean first, Runnable added);
}

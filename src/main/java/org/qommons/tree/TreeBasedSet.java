package org.qommons.tree;

import org.qommons.collect.ElementId;
import org.qommons.collect.SplitSpliterable;

public interface TreeBasedSet<E> extends TreeBasedSortedList<E>, SplitSpliterable.SortedSetSplitSpliterable<E> {
	@Override
	default BinaryTreeNode<E> getElement(E value, boolean first) {
		return (BinaryTreeNode<E>) SortedSetSplitSpliterable.super.getElement(value, first);
	}

	@Override
	BinaryTreeNode<E> getOrAdd(E value, ElementId after, ElementId before, boolean first, Runnable added);
}

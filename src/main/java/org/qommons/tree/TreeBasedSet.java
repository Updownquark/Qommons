package org.qommons.tree;

import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.ElementId;
import org.qommons.collect.SplitSpliterable;

/**
 * A tree-based {@link BetterSortedSet}
 * 
 * @param <E> The type of values in the set
 */
public interface TreeBasedSet<E> extends TreeBasedSortedList<E>, SplitSpliterable.SortedSetSplitSpliterable<E> {
	@Override
	default BinaryTreeNode<E> getElement(E value, boolean first) {
		return (BinaryTreeNode<E>) SortedSetSplitSpliterable.super.getElement(value, first);
	}

	@Override
	BinaryTreeNode<E> getOrAdd(E value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd);
}

package org.qommons.tree;

import java.util.Comparator;

import org.qommons.collect.BetterSortedSet.SortedSearchFilter;

public interface TreeBasedSortedList<E> extends TreeBasedList<E> {
	Comparator<? super E> comparator();

	BinaryTreeNode<E> search(Comparable<? super E> search, SortedSearchFilter filter);
}

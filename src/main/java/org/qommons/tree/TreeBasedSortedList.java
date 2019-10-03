package org.qommons.tree;

import java.util.Comparator;

import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.ElementId;

public interface TreeBasedSortedList<E> extends TreeBasedList<E> {
	Comparator<? super E> comparator();

	BinaryTreeNode<E> search(Comparable<? super E> search, SortedSearchFilter filter);

	@Override
	BinaryTreeNode<E> splitBetween(ElementId element1, ElementId element2);
}

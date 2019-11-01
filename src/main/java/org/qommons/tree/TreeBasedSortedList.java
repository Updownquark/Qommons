package org.qommons.tree;

import java.util.Comparator;

import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.ElementId;

public interface TreeBasedSortedList<E> extends TreeBasedList<E> {
	Comparator<? super E> comparator();

	BinaryTreeNode<E> search(Comparable<? super E> search, BetterSortedList.SortedSearchFilter filter);

	@Override
	BinaryTreeNode<E> splitBetween(ElementId element1, ElementId element2);
}

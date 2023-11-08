package org.qommons.tree;

import java.util.Comparator;

import org.qommons.collect.BetterSortedList;
import org.qommons.collect.ElementId;

/**
 * A {@link TreeBasedList} that is sorted, but not necessarily distinct
 * 
 * @param <E> The type of values in the list
 */
public interface TreeBasedSortedList<E> extends TreeBasedList<E> {
	/** @return The sorting for the values */
	Comparator<? super E> comparator();

	/**
	 * Searches the values
	 * 
	 * @param search The search comparable
	 * @param filter The filter to determine when to stop the search
	 * @return The first-encountered node matching the search
	 */
	BinaryTreeNode<E> search(Comparable<? super E> search, BetterSortedList.SortedSearchFilter filter);

	@Override
	BinaryTreeNode<E> splitBetween(ElementId element1, ElementId element2);
}

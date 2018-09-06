package org.qommons.tree;

import org.qommons.collect.BetterList;
import org.qommons.collect.ElementId;

public interface TreeBasedList<E> extends BetterList<E> {
	/** @return The root of this list's backing tree structure */
	BinaryTreeNode<E> getRoot();

	@Override
	BinaryTreeNode<E> getElement(E value, boolean first);

	@Override
	BinaryTreeNode<E> getElement(ElementId id);

	@Override
	BinaryTreeNode<E> getTerminalElement(boolean first);

	@Override
	BinaryTreeNode<E> getAdjacentElement(ElementId elementId, boolean next);

	@Override
	MutableBinaryTreeNode<E> mutableElement(ElementId id);

	@Override
	BinaryTreeNode<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException;

	@Override
	default BinaryTreeNode<E> addElement(E value, boolean first) throws UnsupportedOperationException, IllegalArgumentException {
		return (BinaryTreeNode<E>) BetterList.super.addElement(value, first);
	}

	@Override
	BinaryTreeNode<E> getElement(int index);

	@Override
	default BinaryTreeNode<E> addElement(int index, E element) {
		return (BinaryTreeNode<E>) BetterList.super.addElement(index, element);
	}

	/**
	 * Quickly obtains a tree node that is well-spaced between two other nodes
	 * 
	 * @param element1 The ID of one element
	 * @param element2 The ID of the other element
	 * @return An element in this list that is between the given elements with a spacing suitable for double-bounded binary search; or null
	 *         if the elements are the same or adjacent
	 */
	BinaryTreeNode<E> splitBetween(ElementId element1, ElementId element2);
}

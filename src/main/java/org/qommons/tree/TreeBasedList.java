package org.qommons.tree;

import org.qommons.collect.BetterList;
import org.qommons.collect.ElementId;
import org.qommons.collect.SplitSpliterable;

/**
 * A {@link BetterList} whose elements are all {@link BinaryTreeNode}s
 * 
 * @param <E> The type of values in the list
 */
public interface TreeBasedList<E> extends SplitSpliterable<E> {
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
		return (BinaryTreeNode<E>) SplitSpliterable.super.addElement(value, first);
	}

	@Override
	BinaryTreeNode<E> getElement(int index);

	@Override
	default BinaryTreeNode<E> addElement(int index, E element) {
		return (BinaryTreeNode<E>) SplitSpliterable.super.addElement(index, element);
	}
}

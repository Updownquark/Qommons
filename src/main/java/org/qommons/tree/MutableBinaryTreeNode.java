package org.qommons.tree;

/**
 * A {@link BinaryTreeNode} with the ability to {@link #replace(BinaryTreeNode) replace}, {@link #remove() remove}, or
 * {@link #add(Object, boolean) add} nodes in the tree structure
 * 
 * @param <E> The type of value the nodes hold
 */
public interface MutableBinaryTreeNode<E> extends BinaryTreeNode<E> {
	@Override
	MutableBinaryTreeNode<E> getParent();
	@Override
	MutableBinaryTreeNode<E> getLeft();
	@Override
	MutableBinaryTreeNode<E> getRight();

	@Override
	default MutableBinaryTreeNode<E> getRoot() {
		return (MutableBinaryTreeNode<E>) BinaryTreeNode.super.getRoot();
	}

	@Override
	default MutableBinaryTreeNode<E> getChild(boolean left) {
		return (MutableBinaryTreeNode<E>) BinaryTreeNode.super.getChild(left);
	}

	@Override
	default MutableBinaryTreeNode<E> getSibling() {
		return (MutableBinaryTreeNode<E>) BinaryTreeNode.super.getSibling();
	}

	@Override
	default MutableBinaryTreeNode<E> findClosest(Comparable<BinaryTreeNode<E>> finder, boolean lesser, boolean withExact) {
		return (MutableBinaryTreeNode<E>) BinaryTreeNode.super.findClosest(finder, lesser, withExact);
	}

	void setValue(E value);
	void remove();
	void add(E value, boolean onLeft);
}

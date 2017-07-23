package org.qommons.tree;

public interface MutableBinaryTreeNode<E> extends BinaryTreeNode<E> {
	void replace(BinaryTreeNode<E> node);
	void remove();
}

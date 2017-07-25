package org.qommons.tree;

import org.qommons.collect.MutableElementHandle;

/**
 * A {@link BinaryTreeNode} with the ability to {@link #set(Object) set}, {@link #remove() remove}, or {@link #add(Object, boolean) add}
 * nodes in the tree structure
 * 
 * @param <E> The type of value the nodes hold
 */
public interface MutableBinaryTreeNode<E> extends BinaryTreeNode<E>, MutableElementHandle<E> {
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
	default MutableBinaryTreeNode<E> findClosest(Comparable<BinaryTreeNode<E>> finder, boolean lesser, boolean strictly) {
		return (MutableBinaryTreeNode<E>) BinaryTreeNode.super.findClosest(finder, lesser, strictly);
	}

	@Override
	default MutableBinaryTreeNode<E> reverse() {
		return new ReversedMutableTreeNode<>(this);
	}

	@Override
	BinaryTreeNode<E> add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException;

	class ReversedMutableTreeNode<E> extends ReversedBinaryTreeNode<E> implements MutableBinaryTreeNode<E> {
		public ReversedMutableTreeNode(MutableBinaryTreeNode<E> wrap) {
			super(wrap);
		}

		@Override
		protected MutableBinaryTreeNode<E> getWrapped() {
			return (MutableBinaryTreeNode<E>) super.getWrapped();
		}

		@Override
		public MutableBinaryTreeNode<E> getParent() {
			return (MutableBinaryTreeNode<E>) super.getParent();
		}

		@Override
		public MutableBinaryTreeNode<E> getLeft() {
			return (MutableBinaryTreeNode<E>) super.getLeft();
		}

		@Override
		public MutableBinaryTreeNode<E> getRight() {
			return (MutableBinaryTreeNode<E>) super.getRight();
		}

		@Override
		public String isEnabled() {
			return getWrapped().isEnabled();
		}

		@Override
		public String isAcceptable(E value) {
			return getWrapped().isAcceptable(value);
		}

		@Override
		public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
			getWrapped().set(value);
		}

		@Override
		public String canRemove() {
			return getWrapped().canRemove();
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			getWrapped().remove();
		}

		@Override
		public String canAdd(E value, boolean before) {
			return getWrapped().canAdd(value, !before);
		}

		@Override
		public BinaryTreeNode<E> add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
			return getWrapped().add(value, !before);
		}

		@Override
		public MutableBinaryTreeNode<E> reverse() {
			return getWrapped();
		}
	}
}

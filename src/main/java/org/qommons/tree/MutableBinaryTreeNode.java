package org.qommons.tree;

import org.qommons.collect.MutableCollectionElement;

/**
 * A {@link BinaryTreeNode} with the ability to {@link #set(Object) set}, {@link #remove() remove}, or {@link #add(Object, boolean) add}
 * nodes in the tree structure
 * 
 * @param <E> The type of value the nodes hold
 */
public interface MutableBinaryTreeNode<E> extends BinaryTreeNode<E>, MutableCollectionElement<E> {
	@Override
	MutableBinaryTreeNode<E> getParent();
	@Override
	MutableBinaryTreeNode<E> getLeft();
	@Override
	MutableBinaryTreeNode<E> getRight();
	@Override
	MutableBinaryTreeNode<E> getClosest(boolean left);

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
	default BinaryTreeNode<E> immutable() {
		return new ImmutableTreeNode<>(this);
	}

	@Override
	default MutableBinaryTreeNode<E> reverse() {
		return new ReversedMutableTreeNode<>(this);
	}

	@Override
	BinaryTreeNode<E> add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException;

	class ImmutableTreeNode<E> extends ImmutableCollectionElement<E> implements BinaryTreeNode<E> {
		public ImmutableTreeNode(MutableBinaryTreeNode<E> wrapped) {
			super(wrapped);
		}

		@Override
		protected MutableBinaryTreeNode<E> getWrapped() {
			return (MutableBinaryTreeNode<E>) super.getWrapped();
		}

		@Override
		public BinaryTreeNode<E> getParent() {
			return immutable(getWrapped().getParent());
		}

		@Override
		public BinaryTreeNode<E> getLeft() {
			return immutable(getWrapped().getLeft());
		}

		@Override
		public BinaryTreeNode<E> getRight() {
			return immutable(getWrapped().getRight());
		}

		@Override
		public BinaryTreeNode<E> getClosest(boolean left) {
			return immutable(getWrapped().getClosest(left));
		}

		@Override
		public int size() {
			return getWrapped().size();
		}
	}

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
		public MutableBinaryTreeNode<E> getClosest(boolean left) {
			return (MutableBinaryTreeNode<E>) super.getClosest(left);
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

	static <E> BinaryTreeNode<E> immutable(MutableBinaryTreeNode<E> node) {
		return node == null ? null : node.immutable();
	}

	static <E> MutableBinaryTreeNode<E> reverse(MutableBinaryTreeNode<E> node) {
		return node == null ? null : node.reverse();
	}
}

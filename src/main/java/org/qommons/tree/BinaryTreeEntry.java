package org.qommons.tree;

public interface BinaryTreeEntry<K, V> extends BinaryTreeNode<K> {
	V getValue();

	@Override
	BinaryTreeEntry<K, V> getParent();

	@Override
	BinaryTreeEntry<K, V> getLeft();

	@Override
	BinaryTreeEntry<K, V> getRight();

	@Override
	default BinaryTreeEntry<K, V> getRoot() {
		return (BinaryTreeEntry<K, V>) BinaryTreeNode.super.getRoot();
	}

	@Override
	default BinaryTreeEntry<K, V> getChild(boolean left) {
		return (BinaryTreeEntry<K, V>) BinaryTreeNode.super.getChild(left);
	}

	@Override
	default BinaryTreeEntry<K, V> getSibling() {
		return (BinaryTreeEntry<K, V>) BinaryTreeNode.super.getSibling();
	}

	@Override
	default BinaryTreeEntry<K, V> getClosest(boolean left) {
		return (BinaryTreeEntry<K, V>) BinaryTreeNode.super.getClosest(left);
	}

	@Override
	default BinaryTreeEntry<K, V> get(int index) {
		return (BinaryTreeEntry<K, V>) BinaryTreeNode.super.get(index);
	}

	@Override
	default BinaryTreeEntry<K, V> getTerminal(boolean left) {
		return (BinaryTreeEntry<K, V>) BinaryTreeNode.super.getTerminal(left);
	}

	@Override
	default BinaryTreeEntry<K, V> findClosest(Comparable<BinaryTreeNode<K>> finder, boolean lesser, boolean strictly) {
		return (BinaryTreeEntry<K, V>) BinaryTreeNode.super.findClosest(finder, lesser, strictly);
	}

	@Override
	default BinaryTreeNode<K> reverse() {
		return new ReversedBinaryTreeEntry<>(this);
	}

	class ReversedBinaryTreeEntry<K, V> extends ReversedBinaryTreeNode<K> implements BinaryTreeEntry<K, V> {
		public ReversedBinaryTreeEntry(BinaryTreeEntry<K, V> wrap) {
			super(wrap);
		}

		@Override
		protected BinaryTreeEntry<K, V> getWrapped() {
			return (BinaryTreeEntry<K, V>) super.getWrapped();
		}

		@Override
		public V getValue() {
			return getWrapped().getValue();
		}

		@Override
		public BinaryTreeEntry<K, V> getParent() {
			return (BinaryTreeEntry<K, V>) super.getParent();
		}

		@Override
		public BinaryTreeEntry<K, V> getLeft() {
			return (BinaryTreeEntry<K, V>) super.getLeft();
		}

		@Override
		public BinaryTreeEntry<K, V> getRight() {
			return (BinaryTreeEntry<K, V>) super.getRight();
		}
	}
}

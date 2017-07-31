package org.qommons.tree;

import org.qommons.collect.MapEntryHandle;

public interface BinaryTreeEntry<K, V> extends BinaryTreeNode<V>, MapEntryHandle<K, V> {
	@Override
	BinaryTreeEntry<K, V> getParent();

	@Override
	BinaryTreeEntry<K, V> getLeft();

	@Override
	BinaryTreeEntry<K, V> getRight();

	@Override
	BinaryTreeEntry<K, V> getClosest(boolean left);

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
	default BinaryTreeEntry<K, V> get(int index) {
		return (BinaryTreeEntry<K, V>) BinaryTreeNode.super.get(index);
	}

	@Override
	default BinaryTreeEntry<K, V> getTerminal(boolean left) {
		return (BinaryTreeEntry<K, V>) BinaryTreeNode.super.getTerminal(left);
	}

	@Override
	default BinaryTreeEntry<K, V> reverse() {
		return new ReversedBinaryTreeEntry<>(this);
	}

	class ReversedBinaryTreeEntry<K, V> extends ReversedBinaryTreeNode<V> implements BinaryTreeEntry<K, V> {
		public ReversedBinaryTreeEntry(BinaryTreeEntry<K, V> wrap) {
			super(wrap);
		}

		@Override
		protected BinaryTreeEntry<K, V> getWrapped() {
			return (BinaryTreeEntry<K, V>) super.getWrapped();
		}

		@Override
		public K getKey() {
			return getWrapped().getKey();
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

		@Override
		public BinaryTreeEntry<K, V> getClosest(boolean left) {
			return (BinaryTreeEntry<K, V>) super.getClosest(left);
		}

		@Override
		public BinaryTreeEntry<K, V> reverse() {
			return getWrapped();
		}
	}
}

package org.qommons.tree;

import org.qommons.collect.ElementId;
import org.qommons.collect.MutableMapEntryHandle;

public interface MutableBinaryTreeEntry<K, V> extends BinaryTreeEntry<K, V>, MutableBinaryTreeNode<V>, MutableMapEntryHandle<K, V> {
	@Override
	MutableBinaryTreeEntry<K, V> getParent();

	@Override
	MutableBinaryTreeEntry<K, V> getLeft();

	@Override
	MutableBinaryTreeEntry<K, V> getRight();

	@Override
	MutableBinaryTreeEntry<K, V> getClosest(boolean left);

	@Override
	default String canAdd(V value, boolean before) {
		return StdMsg.UNSUPPORTED_OPERATION;
	}

	@Override
	default ElementId add(V value, boolean before) throws UnsupportedOperationException {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	default MutableBinaryTreeEntry<K, V> reverse() {
		return new ReversedMutableTreeEntry<>(this);
	}

	@Override
	default BinaryTreeEntry<K, V> immutable() {
		return new ImmutableTreeEntry<>(this);
	}

	@Override
	default MutableBinaryTreeEntry<K, V> getRoot() {
		return (MutableBinaryTreeEntry<K, V>) MutableBinaryTreeNode.super.getRoot();
	}

	@Override
	default MutableBinaryTreeEntry<K, V> getSibling() {
		return (MutableBinaryTreeEntry<K, V>) MutableBinaryTreeNode.super.getSibling();
	}

	@Override
	default MutableBinaryTreeEntry<K, V> getChild(boolean left) {
		return (MutableBinaryTreeEntry<K, V>) BinaryTreeEntry.super.getChild(left);
	}

	class ReversedMutableTreeEntry<K, V> extends ReversedBinaryTreeEntry<K, V> implements MutableBinaryTreeEntry<K, V> {
		public ReversedMutableTreeEntry(MutableBinaryTreeEntry<K, V> wrap) {
			super(wrap);
		}

		@Override
		protected MutableBinaryTreeEntry<K, V> getWrapped() {
			return (MutableBinaryTreeEntry<K, V>) super.getWrapped();
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getParent() {
			return (MutableBinaryTreeEntry<K, V>) super.getParent();
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getLeft() {
			return (MutableBinaryTreeEntry<K, V>) super.getLeft();
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getRight() {
			return (MutableBinaryTreeEntry<K, V>) super.getRight();
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getClosest(boolean left) {
			return (MutableBinaryTreeEntry<K, V>) super.getClosest(left);
		}

		@Override
		public String isEnabled() {
			return getWrapped().isEnabled();
		}

		@Override
		public String isAcceptable(V value) {
			return getWrapped().isAcceptable(value);
		}

		@Override
		public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
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
		public String canAdd(V value, boolean before) {
			return getWrapped().canAdd(value, !before);
		}

		@Override
		public ElementId add(V value, boolean before) throws UnsupportedOperationException {
			return getWrapped().add(value, !before).reverse();
		}

		@Override
		public MutableBinaryTreeEntry<K, V> reverse() {
			return getWrapped();
		}
	}

	class ImmutableTreeEntry<K, V> extends ImmutableMapEntryHandle<K, V> implements BinaryTreeEntry<K, V> {
		public ImmutableTreeEntry(MutableBinaryTreeEntry<K, V> wrapped) {
			super(wrapped);
		}

		@Override
		protected MutableBinaryTreeEntry<K, V> getWrapped() {
			return (MutableBinaryTreeEntry<K, V>) super.getWrapped();
		}

		@Override
		public int size() {
			return getWrapped().size();
		}

		@Override
		public BinaryTreeEntry<K, V> getParent() {
			return immutable(getWrapped().getParent());
		}

		@Override
		public BinaryTreeEntry<K, V> getLeft() {
			return immutable(getWrapped().getLeft());
		}

		@Override
		public BinaryTreeEntry<K, V> getRight() {
			return immutable(getWrapped().getRight());
		}

		@Override
		public BinaryTreeEntry<K, V> getClosest(boolean left) {
			return immutable(getWrapped().getClosest(left));
		}
	}

	static <K, V> BinaryTreeEntry<K, V> immutable(MutableBinaryTreeEntry<K, V> entry) {
		return entry == null ? null : entry.immutable();
	}
}

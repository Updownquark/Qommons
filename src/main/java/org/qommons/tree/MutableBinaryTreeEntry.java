package org.qommons.tree;

import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterMap;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableMapEntryHandle;

/**
 * A {@link MutableMapEntryHandle} for a tree-based {@link BetterMap}
 * 
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
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
	MutableBinaryTreeEntry<K, V> getRoot();

	@Override
	MutableBinaryTreeEntry<K, V> getSibling();

	@Override
	default MutableBinaryTreeEntry<K, V> getChild(boolean left) {
		return (MutableBinaryTreeEntry<K, V>) BinaryTreeEntry.super.getChild(left);
	}

	@Override
	MutableBinaryTreeEntry<K, V> get(int index);

	@Override
	MutableBinaryTreeEntry<K, V> findClosest(Comparable<BinaryTreeNode<V>> finder, boolean lesser, boolean strictly);

	/**
	 * A {@link MutableBinaryTreeEntry} that is reversed
	 * 
	 * @param <K> The key type of the entry
	 * @param <V> The value type of the entry
	 */
	class ReversedMutableTreeEntry<K, V> extends ReversedBinaryTreeEntry<K, V> implements MutableBinaryTreeEntry<K, V> {
		public ReversedMutableTreeEntry(MutableBinaryTreeEntry<K, V> wrap) {
			super(wrap);
		}

		@Override
		protected MutableBinaryTreeEntry<K, V> getWrapped() {
			return (MutableBinaryTreeEntry<K, V>) super.getWrapped();
		}

		@Override
		public BetterCollection<V> getCollection() {
			return getWrapped().getCollection().reverse();
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
		public MutableBinaryTreeEntry<K, V> getRoot() {
			return (MutableBinaryTreeEntry<K, V>) super.getRoot();
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getSibling() {
			return (MutableBinaryTreeEntry<K, V>) super.getSibling();
		}

		@Override
		public MutableBinaryTreeEntry<K, V> get(int index) {
			return (MutableBinaryTreeEntry<K, V>) super.get(index);
		}

		@Override
		public MutableBinaryTreeEntry<K, V> findClosest(Comparable<BinaryTreeNode<V>> finder, boolean lesser, boolean strictly) {
			return (MutableBinaryTreeEntry<K, V>) super.findClosest(finder, lesser, strictly);
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

	/**
	 * An immutable {@link BinaryTreeEntry} wrapping a {@link MutableBinaryTreeEntry}
	 * 
	 * @param <K> The key type of the entry
	 * @param <V> The value type of the entry
	 */
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

		@Override
		public boolean getSide() {
			return getWrapped().getSide();
		}

		@Override
		public int getNodesBefore() {
			return getWrapped().getNodesBefore();
		}

		@Override
		public int getNodesAfter() {
			return getWrapped().getNodesAfter();
		}

		@Override
		public BinaryTreeEntry<K, V> findClosest(Comparable<BinaryTreeNode<V>> finder, boolean lesser, boolean strictly) {
			return immutable(getWrapped().findClosest(finder, lesser, strictly));
		}

		@Override
		public BinaryTreeEntry<K, V> getRoot() {
			return getWrapped().getRoot().immutable();
		}

		@Override
		public BinaryTreeEntry<K, V> getSibling() {
			return immutable(getWrapped().getSibling());
		}

		@Override
		public BinaryTreeEntry<K, V> get(int index) {
			return immutable(getWrapped().get(index));
		}
	}

	/**
	 * @param entry The entry to get an immutable copy of
	 * @return The immutable copy of the given entry, or null if entry was null
	 */
	static <K, V> BinaryTreeEntry<K, V> immutable(MutableBinaryTreeEntry<K, V> entry) {
		return entry == null ? null : entry.immutable();
	}
}

package org.qommons.tree;

import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterMap;
import org.qommons.collect.MutableMapEntryHandle;
import org.qommons.collect.OptimisticContext;

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
	MutableBinaryTreeEntry<K, V> get(int index, OptimisticContext ctx);

	@Override
	MutableBinaryTreeEntry<K, V> findClosest(Comparable<BinaryTreeNode<V>> finder, boolean lesser, boolean strictly, OptimisticContext ctx);

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
			return MutableBinaryTreeEntry.reverse((MutableBinaryTreeEntry<K, V>) super.getParent());
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getLeft() {
			return MutableBinaryTreeEntry.reverse((MutableBinaryTreeEntry<K, V>) super.getLeft());
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getRight() {
			return MutableBinaryTreeEntry.reverse((MutableBinaryTreeEntry<K, V>) super.getRight());
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getClosest(boolean left) {
			return MutableBinaryTreeEntry.reverse((MutableBinaryTreeEntry<K, V>) super.getClosest(left));
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getRoot() {
			return MutableBinaryTreeEntry.reverse((MutableBinaryTreeEntry<K, V>) super.getRoot());
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getSibling() {
			return MutableBinaryTreeEntry.reverse((MutableBinaryTreeEntry<K, V>) super.getSibling());
		}

		@Override
		public MutableBinaryTreeEntry<K, V> get(int index, OptimisticContext ctx) {
			return MutableBinaryTreeEntry.reverse((MutableBinaryTreeEntry<K, V>) super.get(index, ctx));
		}

		@Override
		public MutableBinaryTreeEntry<K, V> findClosest(Comparable<BinaryTreeNode<V>> finder, boolean lesser, boolean strictly, OptimisticContext ctx) {
			return MutableBinaryTreeEntry.reverse((MutableBinaryTreeEntry<K, V>) super.findClosest(finder, lesser, strictly, ctx));
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
		public BinaryTreeEntry<K, V> findClosest(Comparable<BinaryTreeNode<V>> finder, boolean lesser, boolean strictly,
			OptimisticContext ctx) {
			return immutable(getWrapped().findClosest(finder, lesser, strictly, ctx));
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
		public BinaryTreeEntry<K, V> get(int index, OptimisticContext ctx) {
			return immutable(getWrapped().get(index, ctx));
		}
	}

	/**
	 * @param <K> The key type of the entry
	 * @param <V> The value type of the entry
	 * @param entry The entry to get an immutable copy of
	 * @return The immutable copy of the given entry, or null if entry was null
	 */
	static <K, V> BinaryTreeEntry<K, V> immutable(MutableBinaryTreeEntry<K, V> entry) {
		return entry == null ? null : entry.immutable();
	}

	/**
	 * @param <K> The key type of the entry
	 * @param <V> The value type of the entry
	 * @param entry The entry to get the reverse of
	 * @return The reverse of the given entry, or null if entry was null
	 */
	static <K, V> MutableBinaryTreeEntry<K, V> reverse(MutableBinaryTreeEntry<K, V> entry) {
		return entry == null ? null : entry.reverse();
	}
}

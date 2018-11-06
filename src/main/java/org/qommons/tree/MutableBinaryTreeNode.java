package org.qommons.tree;

import org.qommons.collect.BetterCollection;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.OptimisticContext;

/**
 * A {@link MutableCollectionElement} for a tree-based {@link BetterCollection}
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
	MutableBinaryTreeNode<E> getRoot();

	@Override
	MutableBinaryTreeNode<E> getSibling();

	@Override
	MutableBinaryTreeNode<E> get(int index, OptimisticContext ctx);

	@Override
	MutableBinaryTreeNode<E> findClosest(Comparable<BinaryTreeNode<E>> finder, boolean lesser, boolean strictly, OptimisticContext ctx);

	@Override
	default MutableBinaryTreeNode<E> getChild(boolean left) {
		return (MutableBinaryTreeNode<E>) BinaryTreeNode.super.getChild(left);
	}

	@Override
	default BinaryTreeNode<E> immutable() {
		return new ImmutableTreeNode<>(this);
	}

	@Override
	default MutableBinaryTreeNode<E> reverse() {
		return new ReversedMutableTreeNode<>(this);
	}

	/**
	 * An immutable copy of a {@link MutableBinaryTreeNode}
	 * 
	 * @param <E> The type of the element
	 */
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

		@Override
		public BinaryTreeNode<E> getRoot() {
			return getWrapped().getRoot().immutable();
		}

		@Override
		public boolean getSide() {
			return getWrapped().getSide();
		}

		@Override
		public BinaryTreeNode<E> getSibling() {
			return MutableBinaryTreeNode.immutable(getWrapped().getSibling());
		}

		@Override
		public BinaryTreeNode<E> get(int index, OptimisticContext ctx) {
			return immutable(getWrapped().get(index, ctx));
		}

		@Override
		public int getNodesBefore() {
			return getWrapped().getNodesBefore();
		}

		@Override
		public int getNodesAfter() {
			return getWrapped().getNodesAfter();
		}
	}

	/**
	 * A {@link MutableBinaryTreeNode} that is reversed
	 * 
	 * @param <E> The type of the element
	 */
	class ReversedMutableTreeNode<E> extends ReversedBinaryTreeNode<E> implements MutableBinaryTreeNode<E> {
		public ReversedMutableTreeNode(MutableBinaryTreeNode<E> wrap) {
			super(wrap);
		}

		@Override
		protected MutableBinaryTreeNode<E> getWrapped() {
			return (MutableBinaryTreeNode<E>) super.getWrapped();
		}

		@Override
		public BetterCollection<E> getCollection() {
			return getWrapped().getCollection().reverse();
		}

		@Override
		public MutableBinaryTreeNode<E> getParent() {
			return MutableBinaryTreeNode.reverse((MutableBinaryTreeNode<E>) super.getParent());
		}

		@Override
		public MutableBinaryTreeNode<E> getLeft() {
			return MutableBinaryTreeNode.reverse((MutableBinaryTreeNode<E>) super.getLeft());
		}

		@Override
		public MutableBinaryTreeNode<E> getRight() {
			return MutableBinaryTreeNode.reverse((MutableBinaryTreeNode<E>) super.getRight());
		}

		@Override
		public MutableBinaryTreeNode<E> getClosest(boolean left) {
			return MutableBinaryTreeNode.reverse((MutableBinaryTreeNode<E>) super.getClosest(!left));
		}

		@Override
		public MutableBinaryTreeNode<E> getRoot() {
			return MutableBinaryTreeNode.reverse((MutableBinaryTreeNode<E>) super.getRoot());
		}

		@Override
		public MutableBinaryTreeNode<E> getSibling() {
			return MutableBinaryTreeNode.reverse((MutableBinaryTreeNode<E>) super.getSibling());
		}

		@Override
		public MutableBinaryTreeNode<E> get(int index, OptimisticContext ctx) {
			return MutableBinaryTreeNode.reverse((MutableBinaryTreeNode<E>) super.get(index, ctx));
		}

		@Override
		public MutableBinaryTreeNode<E> findClosest(Comparable<BinaryTreeNode<E>> finder, boolean lesser, boolean strictly,
			OptimisticContext ctx) {
			return MutableBinaryTreeNode.reverse((MutableBinaryTreeNode<E>) super.findClosest(finder, lesser, strictly, ctx));
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
		public ElementId add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
			return getWrapped().add(value, !before).reverse();
		}

		@Override
		public MutableBinaryTreeNode<E> reverse() {
			return getWrapped();
		}
	}

	/**
	 * @param node The node to get the immutable copy of
	 * @return The immutable copy of the given node, or null if node was null
	 */
	static <E> BinaryTreeNode<E> immutable(MutableBinaryTreeNode<E> node) {
		return node == null ? null : node.immutable();
	}

	/**
	 * @param node The node to reverse
	 * @return The reversed node, or null if node was null
	 */
	static <E> MutableBinaryTreeNode<E> reverse(MutableBinaryTreeNode<E> node) {
		return node == null ? null : node.reverse();
	}
}

package org.qommons.tree;

import java.util.Comparator;

import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.StampedLockingStrategy;

public class BetterTreeSet<E> extends RedBlackNodeList<E> implements BetterSortedSet<E> {
	private final Comparator<? super E> theCompare;

	public BetterTreeSet(boolean safe, Comparator<? super E> compare) {
		this(safe ? new StampedLockingStrategy() : new FastFailLockingStrategy(), compare);
	}

	public BetterTreeSet(CollectionLockingStrategy locker, Comparator<? super E> compare) {
		super(locker);
		theCompare = compare;
	}

	@Override
	public Comparator<? super E> comparator() {
		return theCompare;
	}

	@Override
	public int indexFor(Comparable<? super E> search) {
		BinaryTreeNode<E> root = getRoot();
		return root == null ? -1 : root.indexFor(node -> search.compareTo(node.get()));
	}

	@Override
	public BinaryTreeNode<E> search(Comparable<? super E> search, SortedSearchFilter filter) {
		try (Transaction t = lock(false, null)) {
			if (isEmpty())
				return null;
			BinaryTreeNode<E> node = getRoot().findClosest(n -> search.compareTo(n.get()), filter.less.withDefault(true), filter.strict);
			if (node == null)
				return null;
			if (filter == SortedSearchFilter.OnlyMatch && search.compareTo(node.get()) != 0)
				return null;
			return node;
		}
	}

	@Override
	protected MutableBinaryTreeNode<E> mutableNodeFor(BinaryTreeNode<E> node) {
		return node == null ? null : new SortedMutableTreeNode(super.mutableNodeFor(node));
	}

	@Override
	protected MutableBinaryTreeNode<E> mutableNodeFor(ElementId node) {
		return new SortedMutableTreeNode(super.mutableNodeFor(node));
	}

	@Override
	public BinaryTreeNode<E> addIfEmpty(E value) throws IllegalStateException {
		try (Transaction t = lock(true, null)) {
			if (getRoot() != null)
				throw new IllegalStateException("Tree is not empty");
			return super.addElement(value, null, null, true);
		}
	}

	@Override
	public String canAdd(E value, ElementId after, ElementId before) {
		return BetterSortedSet.super.canAdd(value, after, before);
	}

	@Override
	public BinaryTreeNode<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		return (BinaryTreeNode<E>) BetterSortedSet.super.addElement(value, after, before, first);
	}

	private class SortedMutableTreeNode implements MutableBinaryTreeNode<E> {
		private final MutableBinaryTreeNode<E> theWrapped;

		SortedMutableTreeNode(MutableBinaryTreeNode<E> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public BetterCollection<E> getCollection() {
			return BetterTreeSet.this;
		}

		@Override
		public ElementId getElementId() {
			return theWrapped.getElementId();
		}

		@Override
		public E get() {
			return theWrapped.get();
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public MutableBinaryTreeNode<E> getParent() {
			return mutableNodeFor(theWrapped.getParent());
		}

		@Override
		public MutableBinaryTreeNode<E> getLeft() {
			return mutableNodeFor(theWrapped.getLeft());
		}

		@Override
		public MutableBinaryTreeNode<E> getRight() {
			return mutableNodeFor(theWrapped.getRight());
		}

		@Override
		public MutableBinaryTreeNode<E> getClosest(boolean left) {
			return mutableNodeFor(theWrapped.getClosest(left));
		}

		@Override
		public boolean getSide() {
			return theWrapped.getSide();
		}

		@Override
		public int getNodesBefore() {
			return theWrapped.getNodesBefore();
		}

		@Override
		public int getNodesAfter() {
			return theWrapped.getNodesAfter();
		}

		@Override
		public MutableBinaryTreeNode<E> getRoot() {
			return mutableNodeFor(theWrapped.getRoot());
		}

		@Override
		public MutableBinaryTreeNode<E> getSibling() {
			return mutableNodeFor(theWrapped.getSibling());
		}

		@Override
		public MutableBinaryTreeNode<E> get(int index) {
			return mutableNodeFor(theWrapped.get(index));
		}

		@Override
		public MutableBinaryTreeNode<E> findClosest(Comparable<BinaryTreeNode<E>> finder, boolean lesser, boolean strictly) {
			return mutableNodeFor(theWrapped.findClosest(finder, lesser, strictly));
		}

		@Override
		public String isEnabled() {
			return null;
		}

		@Override
		public String isAcceptable(E value) {
			if (!belongs(value))
				return StdMsg.ILLEGAL_ELEMENT;
			BinaryTreeNode<E> previous = getClosest(true);
			BinaryTreeNode<E> next = getClosest(false);
			if (previous != null && comparator().compare(previous.get(), value) >= 0)
				return StdMsg.UNSUPPORTED_OPERATION;
			if (next != null && comparator().compare(value, next.get()) >= 0)
				return StdMsg.UNSUPPORTED_OPERATION;
			return null;
		}

		@Override
		public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
			String msg = isAcceptable(value);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			theWrapped.set(value);
		}

		@Override
		public String canRemove() {
			return theWrapped.canRemove();
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			theWrapped.remove();
		}

		@Override
		public String canAdd(E value, boolean before) {
			int compare = comparator().compare(value, get());
			if (compare == 0)
				return StdMsg.ELEMENT_EXISTS;
			if (before != (compare < 0))
				return StdMsg.ILLEGAL_ELEMENT_POSITION;
			BinaryTreeNode<E> side = getClosest(before);
			if (side != null) {
				compare = comparator().compare(value, side.get());
				if (compare == 0)
					return StdMsg.ELEMENT_EXISTS;
				if (before != (compare > 0))
					return StdMsg.ILLEGAL_ELEMENT_POSITION;
			}
			return theWrapped.canAdd(value, before);
		}

		@Override
		public ElementId add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
			int compare = comparator().compare(value, get());
			if (compare == 0)
				throw new IllegalArgumentException(StdMsg.ELEMENT_EXISTS);
			if (before != (compare < 0))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
			BinaryTreeNode<E> side = getClosest(before);
			if (side != null) {
				compare = comparator().compare(side.get(), value);
				if (compare == 0)
					throw new IllegalArgumentException(StdMsg.ELEMENT_EXISTS);
				if (before != (compare < 0))
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
			}
			return theWrapped.add(value, before);
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof BetterTreeSet.SortedMutableTreeNode && theWrapped.equals(((SortedMutableTreeNode) obj).theWrapped);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}
}

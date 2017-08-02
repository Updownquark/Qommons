package org.qommons.tree;

import java.util.Comparator;

import org.qommons.Transaction;
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
	public BinaryTreeNode<E> addIfEmpty(E value) throws IllegalStateException {
		try (Transaction t = lock(true, null)) {
			if (getRoot() != null)
				throw new IllegalStateException("Tree is not empty");
			return super.addElement(value, true);
		}
	}

	@Override
	public String canAdd(E value) {
		return BetterSortedSet.super.canAdd(value);
	}

	@Override
	public BinaryTreeNode<E> addElement(E value, boolean first) {
		try (Transaction t = lock(true, null)) {
			if (isEmpty())
				return super.addElement(value, first);
			BinaryTreeNode<E> node = getRoot().findClosest(n -> theCompare.compare(value, n.get()), true, false);
			int compare = theCompare.compare(value, node.get());
			if (compare == 0)
				return null; // Already present
			else
				return getElement(super.mutableNodeFor(node).add(value, compare < 0));
		}
	}

	private class SortedMutableTreeNode implements MutableBinaryTreeNode<E> {
		private final MutableBinaryTreeNode<E> theWrapped;

		SortedMutableTreeNode(MutableBinaryTreeNode<E> wrapped) {
			theWrapped = wrapped;
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
			return new SortedMutableTreeNode(theWrapped.getParent());
		}

		@Override
		public MutableBinaryTreeNode<E> getLeft() {
			return new SortedMutableTreeNode(theWrapped.getLeft());
		}

		@Override
		public MutableBinaryTreeNode<E> getRight() {
			return new SortedMutableTreeNode(theWrapped.getRight());
		}

		@Override
		public MutableBinaryTreeNode<E> getClosest(boolean left) {
			return new SortedMutableTreeNode(theWrapped.getClosest(left));
		}

		@Override
		public String isEnabled() {
			return null;
		}

		@Override
		public String isAcceptable(E value) {
			if (!belongs(value))
				return StdMsg.ILLEGAL_ELEMENT;
			BinaryTreeNode<E> left = getLeft();
			BinaryTreeNode<E> right = getRight();
			if (left != null && comparator().compare(left.get(), value) >= 0)
				return StdMsg.UNSUPPORTED_OPERATION;
			if (right != null && comparator().compare(value, right.get()) >= 0)
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
				return StdMsg.UNSUPPORTED_OPERATION;
			BinaryTreeNode<E> side = getClosest(before);
			if (side != null) {
				compare = comparator().compare(side.get(), value);
				if (compare == 0)
					return StdMsg.ELEMENT_EXISTS;
				if (before != (compare > 0))
					return StdMsg.UNSUPPORTED_OPERATION;
			}
			return null;
		}

		@Override
		public ElementId add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
			String msg = canAdd(value, before);
			if (msg != null)
				throw new IllegalArgumentException(msg);
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
	}
}

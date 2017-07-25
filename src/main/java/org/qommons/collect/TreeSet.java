package org.qommons.collect;

import java.util.Comparator;
import java.util.function.Consumer;

import org.qommons.Transaction;
import org.qommons.tree.BinaryTreeNode;
import org.qommons.tree.MutableBinaryTreeNode;
import org.qommons.tree.RedBlackNodeList;

public class TreeSet<E> extends RedBlackNodeList<E> implements BetterSortedSet<E> {
	private final Comparator<? super E> theCompare;

	public TreeSet(boolean safe, Comparator<? super E> compare) {
		this(safe ? new StampedLockingStrategy() : new FastFailLockingStrategy(), compare);
	}

	public TreeSet(CollectionLockingStrategy locker, Comparator<? super E> compare) {
		super(locker);
		theCompare = compare;
	}

	@Override
	public BinaryTreeNode<E> getRoot() {
		return super.getRoot();
	}

	@Override
	public BinaryTreeNode<E> getTerminalNode(boolean start) {
		return super.getTerminalNode(start);
	}

	@Override
	public BinaryTreeNode<E> nodeFor(ElementId elementId) {
		return super.nodeFor(elementId);
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
	public E relative(Comparable<? super E> search, boolean up) {
		Object[] found = new Object[1];
		if (!forElement(search, el -> found[0] = el.get(), up))
			return null;
		return (E) found[0];
	}

	@Override
	public boolean forElement(Comparable<? super E> search, Consumer<? super ElementHandle<? extends E>> onElement, boolean up) {
		try (Transaction t = lock(false, null)) {
			if (isEmpty())
				return false;
			BinaryTreeNode<E> node = getRoot().findClosest(n -> search.compareTo(n.get()), !up, false);
			if (node == null)
				return false;
			onElement.accept(node);
			return true;
		}
	}

	@Override
	public boolean forMutableElement(Comparable<? super E> search, Consumer<? super MutableElementHandle<? extends E>> onElement,
		boolean up) {
		try (Transaction t = lock(true, null)) {
			if (isEmpty())
				return false;
			BinaryTreeNode<E> node = getRoot().findClosest(n -> search.compareTo(n.get()), !up, false);
			if (node == null)
				return false;
			onElement.accept(mutableNodeFor(node));
			return true;
		}
	}

	@Override
	public MutableElementSpliterator<E> mutableSpliterator(Comparable<? super E> search, boolean higher) {
		try (Transaction t = lock(false, null)) {
			if (getRoot() == null)
				return mutableSpliterator(true);
			BinaryTreeNode<E> node = getRoot() == null ? null : getRoot().findClosest(n -> search.compareTo(n.get()), higher, false);
			if (higher && search.compareTo(node.get()) < 0)
				higher = false;
			return mutableSpliterator(node, higher);
		}
	}

	@Override
	public MutableElementSpliterator<E> mutableSpliterator(BinaryTreeNode<E> node, boolean next) {
		return super.mutableSpliterator(node, next);
	}

	@Override
	protected MutableBinaryTreeNode<E> mutableNodeFor(BinaryTreeNode<E> node) {
		return new SortedMutableTreeNode(super.mutableNodeFor(node));
	}

	@Override
	public BinaryTreeNode<E> addIfEmpty(E value) throws IllegalStateException {
		try (Transaction t = lock(true, null)) {
			if (getRoot() != null)
				throw new IllegalStateException("Tree is not empty");
			return super.addElement(value);
		}
	}

	@Override
	public String canAdd(E value) {
		return BetterSortedSet.super.canAdd(value);
	}

	@Override
	public boolean add(E e) {
		return BetterSortedSet.super.add(e);
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
			int compare = comparator().compare(get(), value);
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
		public BinaryTreeNode<E> add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
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
			return obj instanceof TreeSet.SortedMutableTreeNode && theWrapped.equals(((SortedMutableTreeNode) obj).theWrapped);
		}
	}
}

package org.qommons.collect;

import java.util.Comparator;
import java.util.function.Consumer;

import org.qommons.Transaction;
import org.qommons.tree.BinaryTreeNode;
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
		return root == null ? -1 : root.indexFor(node -> search.compareTo(node.getValue()));
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
			BinaryTreeNode<E> node = getRoot().findClosest(n -> search.compareTo(n.getValue()), !up, true);
			if (node == null)
				return false;
			onElement.accept(handleFor(node));
			return true;
		}
	}

	@Override
	public boolean forMutableElement(Comparable<? super E> search, Consumer<? super MutableElementHandle<? extends E>> onElement,
		boolean up) {
		try (Transaction t = lock(true, null)) {
			if (isEmpty())
				return false;
			BinaryTreeNode<E> node = getRoot().findClosest(n -> search.compareTo(n.getValue()), !up, true);
			if (node == null)
				return false;
			onElement.accept(mutableHandleFor(node));
			return true;
		}
	}

	@Override
	public MutableElementSpliterator<E> mutableSpliterator(Comparable<? super E> search, boolean higher) {
		try (Transaction t = lock(false, null)) {
			BinaryTreeNode<E> node = getRoot() == null ? null : getRoot().findClosest(n -> search.compareTo(n.getValue()), higher, true);
			return mutableSpliterator(node, higher);
		}
	}

	@Override
	public MutableElementSpliterator<E> mutableSpliterator(BinaryTreeNode<E> node, boolean next) {
		return super.mutableSpliterator(node, next);
	}

	@Override
	protected MutableElementHandle<E> mutableHandleFor(ElementId elementId) {
		return new SortedMutableElementHandle<>(super.mutableHandleFor(elementId));
	}

	@Override
	protected MutableElementHandle<E> mutableHandleFor(BinaryTreeNode<E> node) {
		return new SortedMutableElementHandle<>(super.mutableHandleFor(node));
	}

	private class SortedMutableElementHandle<E> implements MutableElementHandle<E> {
		private final MutableElementHandle<E> theWrapped;

		SortedMutableElementHandle(MutableElementHandle<E> wrapped) {
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
		public String isEnabled() {
			return null;
		}

		@Override
		public String isAcceptable(E value) {
			BinaryTreeNode<E> node=nodeFor(theWrapped.getElementId());
			BinaryTreeNode<E> left=node.getC
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
			// TODO Auto-generated method stub

		}

		@Override
		public String canRemove() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			// TODO Auto-generated method stub

		}

		@Override
		public String canAdd(E value, boolean before) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
			// TODO Auto-generated method stub

		}
	}
}

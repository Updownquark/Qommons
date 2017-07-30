package org.qommons.tree;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.*;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public abstract class RedBlackNodeList<E> implements BetterList<E> {
	private final CollectionLockingStrategy theLocker;

	private RedBlackNode<E> theRoot;

	public RedBlackNodeList(CollectionLockingStrategy locker) {
		theLocker = locker;
	}

	public BinaryTreeNode<E> getRoot() {
		return wrap(theRoot);
	}

	public BinaryTreeNode<E> getNode(int index) {
		if (theRoot == null)
			throw new IndexOutOfBoundsException(index + " of 0");
		return wrap(theRoot.get(index));
	}

	public BinaryTreeNode<E> getTerminalNode(boolean start) {
		return theRoot == null ? null : wrap(theRoot.getTerminal(start));
	}

	@Override
	public boolean isLockSupported() {
		return theLocker.isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theLocker.lock(write, cause);
	}

	@Override
	public int size() {
		return RedBlackNode.sizeOf(theRoot);
	}

	@Override
	public boolean isEmpty() {
		return theRoot == null;
	}

	@Override
	public int getElementsBefore(CollectionElement<E> id) {
		return ((BinaryTreeNode<E>) id).getNodesBefore();
	}

	@Override
	public int getElementsAfter(CollectionElement<E> id) {
		return ((BinaryTreeNode<E>) id).getNodesAfter();
	}

	@Override
	public Object[] toArray() {
		try (Transaction t = lock(false, null)) {
			return BetterList.super.toArray();
		}
	}

	@Override
	public <T> T[] toArray(T[] a) {
		try (Transaction t = lock(false, null)) {
			return BetterList.super.toArray(a);
		}
	}

	@Override
	public boolean belongs(Object o) {
		return true;
	}

	@Override
	public MutableElementSpliterator<E> mutableSpliterator(CollectionElement<E> element, boolean asNext) {
		return new MutableNodeSpliterator(checkNode(element.getElementId()).theNode, asNext);
	}

	@Override
	public CollectionElement<E> getElement(int index) {
		RedBlackNode<E> root = theRoot;
		if (root == null)
			throw new IndexOutOfBoundsException(index + " of 0");
		return wrap(root.get(index));
	}

	@Override
	public <T> T ofMutableElement(CollectionElement<E> element, Function<? super MutableCollectionElement<E>, T> onElement) {
		try (Transaction t = lock(true, null)) {
			return onElement.apply(mutableNodeFor(element));
		}
	}

	@Override
	public MutableElementSpliterator<E> mutableSpliterator(boolean fromStart) {
		return mutableSpliterator(wrap(theRoot == null ? null : theRoot.getTerminal(fromStart)), fromStart);
	}

	@Override
	public String canAdd(E value) {
		return null;
	}

	@Override
	public BinaryTreeNode<E> addElement(E value) {
		BinaryTreeNode<E>[] node = new BinaryTreeNode[1];
		try (Transaction t = lock(true, null)) {
			if (theRoot == null)
				node[0] = wrap(theRoot = new RedBlackNode<>(value));
			else
				mutableSpliterator(false).tryReverseElementM(el -> node[0] = (BinaryTreeNode<E>) el.add(value, false));
		}
		return node[0];
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		try (Transaction t = lock(true, null); Transaction t2 = Transactable.lock(c, false, null)) {
			for (E value : c)
				add(value);
			return !c.isEmpty();
		}
	}

	@Override
	public void clear() {
		theRoot = null;
	}

	public BinaryTreeNode<E> nodeFor(CollectionElement<E> element) {
		return wrap(checkNode(element.getElementId()).theNode);
	}

	protected MutableBinaryTreeNode<E> mutableNodeFor(CollectionElement<E> node) {
		return wrapMutable(checkNode(node.getElementId()).theNode);
	}

	protected MutableBinaryTreeNode<E> mutableNodeFor(BinaryTreeNode<E> node) {
		return wrapMutable(checkNode(node.getElementId()).theNode);
	}

	protected MutableElementSpliterator<E> mutableSpliterator(BinaryTreeNode<E> node, boolean next) {
		return mutableSpliterator(node, next, null, null);
	}

	protected MutableElementSpliterator<E> mutableSpliterator(BinaryTreeNode<E> node, boolean next, BinaryTreeNode<E> leftBound,
		BinaryTreeNode<E> rightBound) {
		return new MutableNodeSpliterator(checkNode(node.getElementId()).theNode, next,
			leftBound == null ? null : checkNode(leftBound.getElementId()).theNode,
			rightBound == null ? null : checkNode(rightBound.getElementId()).theNode);
	}

	private NodeId checkNode(ElementId id) {
		if (!(id instanceof RedBlackNodeList.NodeId))
			throw new IllegalArgumentException(StdMsg.NOT_FOUND);
		NodeId nodeId = (NodeId) id;
		if (!nodeId.isPresent())
			throw new IllegalArgumentException(StdMsg.NOT_FOUND);
		return nodeId;
	}

	private NodeWrapper wrap(RedBlackNode<E> node) {
		return node == null ? null : new NodeWrapper(node);
	}

	private MutableNodeWrapper wrapMutable(RedBlackNode<E> node) {
		return node == null ? null : new MutableNodeWrapper(node);
	}

	class NodeId implements ElementId {
		final RedBlackNode<E> theNode;

		NodeId(RedBlackNode<E> node) {
			theNode = node;
		}

		boolean isPresent() {
			return theNode.getParent() != null || theRoot == theNode;
		}

		@Override
		public int compareTo(ElementId id) {
			return theNode.getNodesBefore() - ((NodeId) id).theNode.getNodesBefore();
		}
	}

	class NodeWrapper implements BinaryTreeNode<E> {
		final RedBlackNode<E> theNode;

		NodeWrapper(RedBlackNode<E> node) {
			theNode = node;
		}

		@Override
		public ElementId getElementId() {
			return new NodeId(theNode);
		}

		@Override
		public boolean isPresent() {
			return theNode.getParent() != null || theRoot == theNode;
		}

		@Override
		public E get() {
			if (!isPresent())
				throw new IllegalStateException("This element has been removed");
			return theNode.getValue();
		}

		@Override
		public BinaryTreeNode<E> getParent() {
			if (!isPresent())
				throw new IllegalStateException("This element has been removed");
			return wrap(theNode.getParent());
		}

		@Override
		public BinaryTreeNode<E> getLeft() {
			if (!isPresent())
				throw new IllegalStateException("This element has been removed");
			return wrap(theNode.getLeft());
		}

		@Override
		public BinaryTreeNode<E> getRight() {
			if (!isPresent())
				throw new IllegalStateException("This element has been removed");
			return wrap(theNode.getRight());
		}

		@Override
		public BinaryTreeNode<E> getClosest(boolean left) {
			if (!isPresent())
				throw new IllegalStateException("This element has been removed");
			return wrap(theNode.getClosest(left));
		}

		@Override
		public int size() {
			if (!isPresent())
				throw new IllegalStateException("This element has been removed");
			return theNode.size();
		}

		@Override
		public int hashCode() {
			return theNode.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof RedBlackNodeList.NodeWrapper && theNode.equals(((NodeWrapper) obj).theNode);
		}
	}

	class MutableNodeWrapper extends NodeWrapper implements MutableBinaryTreeNode<E> {
		MutableNodeWrapper(RedBlackNode<E> node) {
			super(node);
		}

		@Override
		public MutableBinaryTreeNode<E> getParent() {
			return (MutableBinaryTreeNode<E>) super.getParent();
		}

		@Override
		public MutableBinaryTreeNode<E> getLeft() {
			return (MutableBinaryTreeNode<E>) super.getLeft();
		}

		@Override
		public MutableBinaryTreeNode<E> getRight() {
			return (MutableBinaryTreeNode<E>) super.getRight();
		}

		@Override
		public MutableBinaryTreeNode<E> getClosest(boolean left) {
			return (MutableBinaryTreeNode<E>) super.getClosest(left);
		}

		@Override
		public String isEnabled() {
			return null;
		}

		@Override
		public String isAcceptable(E value) {
			return null;
		}

		@Override
		public void set(E value) {
			if (!isPresent())
				throw new IllegalStateException("This element has been removed");
			try (Transaction t = lock(true, null)) {
				theNode.setValue(value);
			}
		}

		@Override
		public String canRemove() {
			return null;
		}

		@Override
		public void remove() {
			if (!isPresent())
				throw new IllegalStateException("This element has been removed");
			try (Transaction t = lock(true, null)) {
				theRoot = theNode.delete();
			}
		}

		@Override
		public String canAdd(E value, boolean before) {
			return null;
		}

		@Override
		public BinaryTreeNode<E> add(E value, boolean onLeft) {
			if (!isPresent())
				throw new IllegalStateException("This element has been removed");
			try (Transaction t = lock(true, null)) {
				RedBlackNode<E> newNode = new RedBlackNode<>(value);
				theRoot = theNode.add(newNode, onLeft);
				return wrap(newNode);
			}
		}
	}

	protected class MutableNodeSpliterator implements MutableElementSpliterator<E> {
		private RedBlackNode<E> current;
		private boolean wasNext;

		private RedBlackNode<E> theLeftBound;
		private RedBlackNode<E> theRightBound;

		/**
		 * @param node The node anchor for this spliterator
		 * @param next Whether the given node is to be the next or the previous node this spliterator returns
		 */
		protected MutableNodeSpliterator(RedBlackNode<E> node, boolean next) {
			this(node, next, null, null);
		}

		private MutableNodeSpliterator(RedBlackNode<E> node, boolean next, RedBlackNode<E> left, RedBlackNode<E> right) {
			current = node;
			wasNext = !next;
			theLeftBound = left;
			theRightBound = right;
		}

		@Override
		public long estimateSize() {
			int size;
			if (theRightBound != null)
				size = theRightBound.getNodesBefore();
			else
				size = RedBlackNodeList.this.size();
			if (theLeftBound != null)
				size -= theLeftBound.getNodesBefore();
			return size;
		}

		@Override
		public long getExactSizeIfKnown() {
			return estimateSize();
		}

		@Override
		public int characteristics() {
			return SIZED;
		}

		protected boolean tryElement(boolean left) {
			if (current == null) {
				if (theRoot == null)
					return false;
				current = theRoot.getTerminal(!wasNext);
			}
			// We can tolerate external modification as long as the node that this spliterator is anchored to has not been removed
			// This situation is easy to detect
			if (current.getParent() == null && theRoot != current)
				throw new ConcurrentModificationException(
					"The collection has been modified externally such that this spliterator has been orphaned");
			if (wasNext != left) {
				RedBlackNode<E> next = current.getClosest(left);
				if (next != null && isIncluded(next))
					current = next;
				else {
					wasNext = left;
					return false;
				}
			}
			return true;
		}

		protected boolean isIncluded(RedBlackNode<E> node) {
			if (theLeftBound != null) {
				if (node == theLeftBound)
					return true; // Left bound is included
				if (node.getNodesBefore() < theLeftBound.getNodesBefore())
					return false;
			}
			if (theRightBound != null) {
				if (node == theRightBound)
					return false; // Right bound is excluded
				if (node.getNodesBefore() > theRightBound.getNodesBefore())
					return false;
			}
			return true;
		}

		@Override
		public boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action) {
			try (Transaction t = lock(false, null)) {
				if (!tryElement(false))
					return false;
				action.accept(wrap(current));
				return true;
			}
		}

		@Override
		public boolean tryReverseElement(Consumer<? super CollectionElement<E>> action) {
			try (Transaction t = lock(false, null)) {
				if (!tryElement(true))
					return false;
				action.accept(wrap(current));
				return true;
			}
		}

		@Override
		public boolean tryAdvanceElementM(Consumer<? super MutableCollectionElement<E>> action) {
			try (Transaction t = lock(true, null)) {
				if (!tryElement(false))
					return false;
				action.accept(new MutableSpliteratorNode(current, wrapMutable(current)));
				return true;
			}
		}

		@Override
		public boolean tryReverseElementM(Consumer<? super MutableCollectionElement<E>> action) {
			try (Transaction t = lock(true, null)) {
				if (!tryElement(true))
					return false;
				action.accept(new MutableSpliteratorNode(current, wrapMutable(current)));
				return true;
			}
		}

		@Override
		public void forEachElement(Consumer<? super CollectionElement<E>> action) {
			try (Transaction t = lock(false, null)) {
				while (tryElement(false))
					action.accept(wrap(current));
			}
		}

		@Override
		public void forEachElementReverse(Consumer<? super CollectionElement<E>> action) {
			try (Transaction t = lock(false, null)) {
				while (tryElement(true))
					action.accept(wrap(current));
			}
		}

		@Override
		public void forEachElementM(Consumer<? super MutableCollectionElement<E>> action) {
			try (Transaction t = lock(true, null)) {
				while (tryElement(false))
					action.accept(new MutableSpliteratorNode(current, wrapMutable(current)));
			}
		}

		@Override
		public void forEachElementReverseM(Consumer<? super MutableCollectionElement<E>> action) {
			try (Transaction t = lock(true, null)) {
				while (tryElement(true))
					action.accept(new MutableSpliteratorNode(current, wrapMutable(current)));
			}
		}

		@Override
		public MutableElementSpliterator<E> trySplit() {
			try (Transaction t = lock(false, null)) {
				if (theRoot == null)
					return null;
				if (current == null)
					current = theRoot.getTerminal(!wasNext);
				RedBlackNode<E> divider;
				if (theLeftBound == null) {
					if (theRightBound == null) {
						if (size() <= 1)
							return null;
						divider = theRoot;
					} else
						divider = step(theRightBound, true);
				} else if (theRightBound == null)
					divider = step(theLeftBound, false);
				else {
					int leftIdx = theLeftBound.getNodesBefore();
					int rightIdx = theRightBound.getNodesBefore();
					if (rightIdx - leftIdx <= 1)
						return null;
					divider = theRoot.get((leftIdx + rightIdx) / 2);
				}

				if (divider == null)
					return null;

				MutableNodeSpliterator split;
				if (current.getNodesBefore() < divider.getNodesBefore()) { // We're on the left of the divider
					RedBlackNode<E> right = theRightBound == null ? theRoot.getTerminal(false) : theRightBound;
					RedBlackNode<E> start = current == divider ? right : divider;
					split = new MutableNodeSpliterator(start, true, divider, right);
					theRightBound = divider;
				} else {
					RedBlackNode<E> left = theLeftBound == null ? theRoot.getTerminal(true) : theLeftBound;
					RedBlackNode<E> start = current == divider ? left : divider;
					split = new MutableNodeSpliterator(start, true, left, divider);
					theLeftBound = divider;
				}
				return split;
			}
		}

		private RedBlackNode<E> step(RedBlackNode<E> node, boolean left) {
			if (node.getSide() != left) {
				RedBlackNode<E> parent = node.getParent();
				if (parent != null)
					return parent;
			}
			return node.getChild(left);
		}

		private class MutableSpliteratorNode implements MutableBinaryTreeNode<E> {
			private final RedBlackNode<E> theNode;
			private final MutableBinaryTreeNode<E> theWrapped;

			MutableSpliteratorNode(RedBlackNode<E> node, MutableBinaryTreeNode<E> wrap) {
				theNode = node;
				theWrapped = wrap;
			}

			@Override
			public ElementId getElementId() {
				return theWrapped.getElementId();
			}

			@Override
			public boolean isPresent() {
				return theWrapped.isPresent();
			}

			@Override
			public MutableBinaryTreeNode<E> getClosest(boolean left) {
				RedBlackNode<E> next = theNode.getClosest(left);
				return next == null ? null : new MutableSpliteratorNode(next, theWrapped.getClosest(left));
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
			public String isEnabled() {
				return theWrapped.isEnabled();
			}

			@Override
			public String isAcceptable(E value) {
				return theWrapped.isAcceptable(value);
			}

			@Override
			public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
				theWrapped.set(value);
			}

			@Override
			public String canRemove() {
				return theWrapped.canRemove();
			}

			@Override
			public void remove() {
				try (Transaction t = lock(true, null)) {
					RedBlackNode<E> newCurrent;
					boolean newWasNext;
					if (theNode == current) {
						newCurrent = current.getClosest(true);
						if (newCurrent != null)
							newWasNext = false;
						else {
							newCurrent = current.getClosest(false);
							newWasNext = true;
						}
						current = newCurrent;
					} else {
						newCurrent = current;
						newWasNext = wasNext;
					}
					theWrapped.remove();
					current = newCurrent;
					wasNext = newWasNext;
				}
			}

			@Override
			public String canAdd(E value, boolean before) {
				return theWrapped.canAdd(value, before);
			}

			@Override
			public BinaryTreeNode<E> add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				return theWrapped.add(value, before);
			}

			@Override
			public MutableBinaryTreeNode<E> getParent() {
				RedBlackNode<E> parent = theNode.getParent();
				return parent == null ? null : new MutableSpliteratorNode(parent, theWrapped.getParent());
			}

			@Override
			public MutableBinaryTreeNode<E> getLeft() {
				RedBlackNode<E> left = theNode.getLeft();
				return left == null ? null : new MutableSpliteratorNode(left, theWrapped.getLeft());
			}

			@Override
			public MutableBinaryTreeNode<E> getRight() {
				RedBlackNode<E> right = theNode.getRight();
				return right == null ? null : new MutableSpliteratorNode(right, theWrapped.getRight());
			}

			@Override
			public int hashCode() {
				return theNode.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (!(obj instanceof RedBlackNodeList.NodeId))
					return false;
				return theNode.equals(((NodeId) obj).theNode);
			}
		}
	}
}

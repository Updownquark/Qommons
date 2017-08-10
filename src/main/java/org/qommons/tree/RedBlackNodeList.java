package org.qommons.tree;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.MutableElementSpliterator;

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

	/** For unit tests. Ensures the integrity of the collection. */
	public void checkValid() {
		if (theRoot != null)
			theRoot.checkValid();
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
	public int getElementsBefore(ElementId id) {
		return ((NodeId) id).theNode.getNodesBefore();
	}

	@Override
	public int getElementsAfter(ElementId id) {
		return ((NodeId) id).theNode.getNodesAfter();
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
	public BinaryTreeNode<E> getElement(ElementId id) {
		return wrap(checkNode(id).theNode);
	}

	@Override
	public MutableElementSpliterator<E> mutableSpliterator(ElementId element, boolean asNext) {
		return new MutableNodeSpliterator(checkNode(element).theNode, asNext);
	}

	@Override
	public BinaryTreeNode<E> getElement(int index) {
		RedBlackNode<E> root = theRoot;
		if (root == null)
			throw new IndexOutOfBoundsException(index + " of 0");
		return wrap(root.get(index));
	}

	@Override
	public <T> T ofMutableElement(ElementId element, Function<? super MutableCollectionElement<E>, T> onElement) {
		try (Transaction t = lock(true, null)) {
			return onElement.apply(mutableNodeFor(element));
		}
	}

	@Override
	public MutableElementSpliterator<E> mutableSpliterator(boolean fromStart) {
		return mutableSpliterator(theRoot == null ? null : wrap(theRoot.getTerminal(fromStart)), fromStart);
	}

	@Override
	public String canAdd(E value) {
		return null;
	}

	@Override
	public BinaryTreeNode<E> addElement(E value, boolean first) {
		BinaryTreeNode<E>[] node = new BinaryTreeNode[1];
		try (Transaction t = lock(true, null)) {
			if (theRoot == null)
				node[0] = wrap(theRoot = new RedBlackNode<>(value));
			else
				mutableSpliterator(first).forElementM(el -> node[0] = getElement(el.add(value, first)), first);
		}
		return node[0];
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		return BetterList.super.addAll(c);
	}

	@Override
	public void clear() {
		theRoot = null;
	}

	@Override
	public int hashCode() {
		return BetterCollection.hashCode(this);
	}

	@Override
	public boolean equals(Object o) {
		return BetterCollection.equals(this, o);
	}

	@Override
	public String toString() {
		return BetterCollection.toString(this);
	}

	public BinaryTreeNode<E> nodeFor(CollectionElement<E> element) {
		return wrap(checkNode(element.getElementId()).theNode);
	}

	protected MutableBinaryTreeNode<E> mutableNodeFor(ElementId node) {
		return wrapMutable(checkNode(node).theNode);
	}

	protected MutableBinaryTreeNode<E> mutableNodeFor(BinaryTreeNode<E> node) {
		return wrapMutable(checkNode(node.getElementId()).theNode);
	}

	protected MutableElementSpliterator<E> mutableSpliterator(BinaryTreeNode<E> node, boolean next) {
		return mutableSpliterator(node, next, null, null);
	}

	protected MutableElementSpliterator<E> mutableSpliterator(BinaryTreeNode<E> node, boolean next, BinaryTreeNode<E> leftBound,
		BinaryTreeNode<E> rightBound) {
		return new MutableNodeSpliterator(node == null ? null : checkNode(node.getElementId()).theNode, next,
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

		@Override
		public boolean isPresent() {
			return theNode.getParent() != null || theRoot == theNode;
		}

		@Override
		public int compareTo(ElementId id) {
			int compare = theNode.getNodesBefore() - ((NodeId) id).theNode.getNodesBefore();
			if (isPresent()) {
				if (id.isPresent())
					return compare;
				else
					return compare + 1;
			} else {
				if (id.isPresent())
					return compare - 1;
				else
					return compare;
			}
		}

		@Override
		public int hashCode() {
			return theNode.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof RedBlackNodeList.NodeId && theNode.equals(((NodeId) o).theNode);
		}

		@Override
		public String toString() {
			return new StringBuilder().append('[').append(theNode.getNodesBefore()).append("]: ").append(theNode.getValue()).toString();
		}
	}

	class NodeWrapper implements BinaryTreeNode<E> {
		final RedBlackNode<E> theNode;
		NodeId theId;

		NodeWrapper(RedBlackNode<E> node) {
			theNode = node;
		}

		@Override
		public ElementId getElementId() {
			if (theId == null)
				theId = new NodeId(theNode);
			return theId;
		}

		@Override
		public E get() {
			return theNode.getValue();
		}

		@Override
		public BinaryTreeNode<E> getParent() {
			return wrap(theNode.getParent());
		}

		@Override
		public BinaryTreeNode<E> getLeft() {
			return wrap(theNode.getLeft());
		}

		@Override
		public BinaryTreeNode<E> getRight() {
			return wrap(theNode.getRight());
		}

		@Override
		public BinaryTreeNode<E> getClosest(boolean left) {
			return wrap(theNode.getClosest(left));
		}

		@Override
		public BinaryTreeNode<E> getRoot() {
			return wrap(theNode.getRoot());
		}

		@Override
		public boolean getSide() {
			return theNode.getSide();
		}

		@Override
		public BinaryTreeNode<E> getSibling() {
			return wrap(theNode.getSibling());
		}

		@Override
		public BinaryTreeNode<E> get(int index) {
			return wrap(theNode.get(index));
		}

		@Override
		public int getNodesBefore() {
			return theNode.getNodesBefore();
		}

		@Override
		public int getNodesAfter() {
			return theNode.getNodesAfter();
		}

		@Override
		public int size() {
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

		@Override
		public String toString() {
			return getElementId().toString();
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
		public MutableBinaryTreeNode<E> getRoot() {
			return wrapMutable(theNode.getRoot());
		}

		@Override
		public MutableBinaryTreeNode<E> getSibling() {
			return wrapMutable(theNode.getSibling());
		}

		@Override
		public MutableBinaryTreeNode<E> get(int index) {
			return wrapMutable(theNode.get(index));
		}

		@Override
		public MutableBinaryTreeNode<E> findClosest(Comparable<BinaryTreeNode<E>> finder, boolean lesser, boolean strictly) {
			return mutableNodeFor(super.findClosest(finder, lesser, strictly));
		}

		@Override
		public String isEnabled() {
			return null;
		}

		@Override
		public String isAcceptable(E value) {
			return null;
		}

		private boolean isPresent() {
			return theNode.getParent() != null || theRoot == theNode;
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
		public ElementId add(E value, boolean onLeft) {
			if (!isPresent())
				throw new IllegalStateException("This element has been removed");
			try (Transaction t = lock(true, null)) {
				RedBlackNode<E> newNode = new RedBlackNode<>(value);
				theRoot = theNode.add(newNode, onLeft);
				return new NodeId(newNode);
			}
		}
	}

	protected class MutableNodeSpliterator extends MutableElementSpliterator.SimpleMutableSpliterator<E> {
		private RedBlackNode<E> current;
		private boolean currentIsNext;

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
			super(RedBlackNodeList.this);
			current = node;
			currentIsNext = next;
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
			if (theRoot == null) {
				current = null;
				return false;
			}
			if (current == null) {
				current = theRoot.getTerminal(!currentIsNext);
				currentIsNext = !left;
			}
			// We can tolerate external modification as long as the node that this spliterator is anchored to has not been removed
			// This situation is easy to detect
			if (current.getParent() == null && theRoot != current)
				throw new ConcurrentModificationException(
					"The collection has been modified externally such that this spliterator has been orphaned");
			if (currentIsNext == left) {
				RedBlackNode<E> next = current.getClosest(left);
				if (next != null && isIncluded(next)) {
					current = next;
					currentIsNext = left;
				} else
					return false;
			} else
				currentIsNext = !currentIsNext;
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
		protected boolean internalForElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
			if (!tryElement(!forward))
				return false;
			action.accept(wrap(current));
			return true;
		}

		@Override
		protected boolean internalForElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward) {
			if (!tryElement(!forward))
				return false;
			action.accept(new MutableSpliteratorNode(current, wrapMutable(current)));
			return true;
		}

		@Override
		public MutableElementSpliterator<E> trySplit() {
			try (Transaction t = lock(false, null)) {
				if (theRoot == null)
					return null;
				if (current == null)
					current = theRoot.getTerminal(!currentIsNext);
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

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			RedBlackNode<E> node = theRoot == null ? null : theRoot.getTerminal(true);
			if (theLeftBound == null && theRightBound != null)
				str.append('<');
			while (node != null) {
				if (node == theLeftBound)
					str.append('<');
				if (node == current) {
					if (currentIsNext)
						str.append('^');
					str.append('[');
				}
				str.append(node.getValue());
				if (node == current) {
					str.append(']');
					if (!currentIsNext)
						str.append('^');
				}
				if (node == theRightBound)
					str.append('>');
				node = node.getClosest(false);
				if (node != null)
					str.append(", ");
			}
			if (theRightBound == null && theLeftBound != null)
				str.append('>');
			return str.toString();
		}

		private MutableSpliteratorNode wrapSpliterNode(RedBlackNode<E> node, MutableBinaryTreeNode<E> wrap) {
			return node == null ? null : new MutableSpliteratorNode(node, wrap == null ? wrapMutable(node) : wrap);
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
			public MutableBinaryTreeNode<E> getClosest(boolean left) {
				return wrapSpliterNode(theNode.getClosest(left), null);
			}

			@Override
			public boolean getSide() {
				return theNode.getSide();
			}

			@Override
			public int getNodesBefore() {
				return theNode.getNodesBefore();
			}

			@Override
			public int getNodesAfter() {
				return theNode.getNodesAfter();
			}

			@Override
			public MutableBinaryTreeNode<E> getRoot() {
				return wrapSpliterNode(theNode.getRoot(), null);
			}

			@Override
			public MutableBinaryTreeNode<E> getSibling() {
				return wrapSpliterNode(theNode.getSibling(), null);
			}

			@Override
			public MutableBinaryTreeNode<E> get(int index) {
				return wrapSpliterNode(theNode.get(index), null);
			}

			@Override
			public MutableBinaryTreeNode<E> findClosest(Comparable<BinaryTreeNode<E>> finder, boolean lesser, boolean strictly) {
				MutableBinaryTreeNode<E> found = theWrapped.findClosest(finder, lesser, strictly);
				return found == null ? null : wrapSpliterNode(((MutableNodeWrapper) found).theNode, found);
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
					boolean newNext;
					if (theNode == current) {
						newCurrent = current.getClosest(true);
						newNext = false;
					} else {
						newCurrent = current;
						newNext = currentIsNext;
					}
					theWrapped.remove();
					current = newCurrent;
					currentIsNext = newNext;
				}
			}

			@Override
			public String canAdd(E value, boolean before) {
				return theWrapped.canAdd(value, before);
			}

			@Override
			public ElementId add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
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

			@Override
			public String toString() {
				return theNode.toString();
			}
		}
	}
}

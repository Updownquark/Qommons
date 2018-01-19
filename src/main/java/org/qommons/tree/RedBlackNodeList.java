package org.qommons.tree;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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
	private final RedBlackTree<E> theTree;
	private final CollectionLockingStrategy theLocker;

	public RedBlackNodeList(CollectionLockingStrategy locker) {
		theLocker = locker;
		theTree = new RedBlackTree<>();
	}

	public BinaryTreeNode<E> getRoot() {
		return wrap(theTree.getRoot());
	}

	public BinaryTreeNode<E> getNode(int index) {
		if (theTree.getRoot() == null)
			throw new IndexOutOfBoundsException(index + " of 0");
		return theLocker.doOptimistically(null, (init, ctx) -> wrap(theTree.getRoot().get(index, ctx)), true);
	}

	/** For unit tests. Ensures the integrity of the collection. */
	public void checkValid() {
		if (theTree.getRoot() != null)
			theTree.getRoot().checkValid();
	}

	@Override
	public boolean isLockSupported() {
		return theLocker.isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, boolean structural, Object cause) {
		return theLocker.lock(write, structural, cause);
	}

	@Override
	public long getStamp(boolean structuralOnly) {
		return theLocker.getStatus(structuralOnly);
	}

	@Override
	public int size() {
		return theTree.size();
	}

	@Override
	public boolean isEmpty() {
		return theTree.getRoot() == null;
	}

	@Override
	public int getElementsBefore(ElementId id) {
		return theLocker.doOptimistically(0, (init, ctx) -> ((NodeId) id).theNode.getNodesBefore(ctx), true);
	}

	@Override
	public int getElementsAfter(ElementId id) {
		return theLocker.doOptimistically(0, (init, ctx) -> ((NodeId) id).theNode.getNodesAfter(ctx), true);
	}

	@Override
	public BinaryTreeNode<E> getTerminalElement(boolean first) {
		return wrap(theTree.getTerminal(first));
	}

	@Override
	public BinaryTreeNode<E> getAdjacentElement(ElementId elementId, boolean next) {
		return wrap(checkNode(elementId).theNode).getClosest(!next);
	}

	@Override
	public Object[] toArray() {
		try (Transaction t = lock(false, true, null)) {
			return BetterList.super.toArray();
		}
	}

	@Override
	public <T> T[] toArray(T[] a) {
		try (Transaction t = lock(false, true, null)) {
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
	public MutableElementSpliterator<E> spliterator(ElementId element, boolean asNext) {
		return new MutableNodeSpliterator(checkNode(element).theNode, asNext);
	}

	@Override
	public BinaryTreeNode<E> getElement(int index) {
		RedBlackNode<E> root = theTree.getRoot();
		if (root == null)
			throw new IndexOutOfBoundsException(index + " of 0");
		return theLocker.doOptimistically(null, (init, ctx) -> wrap(root.get(index, ctx)), true);
	}

	@Override
	public MutableBinaryTreeNode<E> mutableElement(ElementId id) {
		return mutableNodeFor(id);
	}

	@Override
	public MutableElementSpliterator<E> spliterator(boolean fromStart) {
		return mutableSpliterator(getTerminalElement(fromStart), fromStart);
	}

	@Override
	public String canAdd(E value, ElementId after, ElementId before) {
		return null;
	}

	@Override
	public BinaryTreeNode<E> addElement(E value, boolean first) {
		return addElement(value, null, null, first);
	}

	@Override
	public BinaryTreeNode<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		RedBlackNode<E> newNode = new RedBlackNode<>(theTree, value);
		if (first && after != null) {
			if (!((NodeId) after).theNode.isPresent())
				throw new IllegalArgumentException("Unrecognized element");
			((NodeId) after).theNode.add(newNode, false);
		} else if (!first && before != null) {
			if (!((NodeId) before).theNode.isPresent())
				throw new IllegalArgumentException("Unrecognized element");
			((NodeId) before).theNode.add(newNode, true);
		} else if (theTree.getRoot() == null)
			theTree.setRoot(newNode);
		else
			theTree.getTerminal(first).add(newNode, first);
		theLocker.changed(true);
		return wrap(newNode);
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		return BetterList.super.addAll(c);
	}

	@Override
	public void clear() {
		theTree.setRoot(null);
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
			return theNode.isPresent();
		}

		@Override
		public int compareTo(ElementId id) {
			return theLocker.doOptimistically(0, (init, ctx) -> {
				int compare = theNode.getNodesBefore(ctx) - ((NodeId) id).theNode.getNodesBefore(ctx);
				if (isPresent()) {
					if (id.isPresent())
						return compare;
					else {
						compare = compare + 1;
						if (compare == 0)
							compare = -1;
						return compare;
					}
				} else {
					if (id.isPresent()) {
						compare = compare - 1;
						if (compare == 0)
							compare = 1;
						return compare;
					} else
						return compare;
				}
			}, true);
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
			int index = theLocker.doOptimistically(0, (init, ctx) -> theNode.getNodesBefore(ctx), true);
			return new StringBuilder().append('[').append(index).append("]: ").append(theNode.getValue()).toString();
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
			return theLocker.doOptimistically(null, (init, ctx) -> wrap(theNode.get(index, ctx)), true);
		}

		@Override
		public int getNodesBefore() {
			return theLocker.doOptimistically(0, (init, ctx) -> theNode.getNodesBefore(ctx), true);
		}

		@Override
		public int getNodesAfter() {
			return theLocker.doOptimistically(0, (init, ctx) -> theNode.getNodesAfter(ctx), true);
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
		public BetterCollection<E> getCollection() {
			return RedBlackNodeList.this;
		}

		@Override
		public MutableBinaryTreeNode<E> getParent() {
			return wrapMutable(theNode.getParent());
		}

		@Override
		public MutableBinaryTreeNode<E> getLeft() {
			return wrapMutable(theNode.getLeft());
		}

		@Override
		public MutableBinaryTreeNode<E> getRight() {
			return wrapMutable(theNode.getRight());
		}

		@Override
		public MutableBinaryTreeNode<E> getClosest(boolean left) {
			return wrapMutable(theNode.getClosest(left));
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
			return theLocker.doOptimistically(null, (init, ctx) -> wrapMutable(theNode.get(index, ctx)), true);
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
			return theNode.isPresent();
		}

		@Override
		public void set(E value) {
			try (Transaction t = lock(true, false, null)) {
				if (!isPresent())
					throw new IllegalStateException("This element has been removed");
				theNode.setValue(value);
				theLocker.changed(false);
			}
		}

		@Override
		public String canRemove() {
			return null;
		}

		@Override
		public void remove() {
			try (Transaction t = lock(true, null)) {
				if (!isPresent())
					throw new IllegalStateException("This element has been removed");
				theNode.delete();
				theLocker.changed(true);
			}
		}

		@Override
		public String canAdd(E value, boolean before) {
			return null;
		}

		@Override
		public ElementId add(E value, boolean onLeft) {
			try (Transaction t = lock(true, null)) {
				if (!isPresent())
					throw new IllegalStateException("This element has been removed");
				RedBlackNode<E> newNode = new RedBlackNode<>(theTree, value);
				theNode.add(newNode, onLeft);
				theLocker.changed(true);
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
			return RedBlackNodeList.this.theLocker.doOptimistically(0, (init, ctx) -> {
				int size;
				if (theRightBound != null)
					size = theRightBound.getNodesBefore(ctx);
				else
					size = RedBlackNodeList.this.size();
				if (theLeftBound != null)
					size -= theLeftBound.getNodesBefore(ctx);
				return size;
			}, true);
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
			if (theTree.getRoot() == null) {
				current = null;
				return false;
			}
			if (current == null) {
				current = theTree.getTerminal(!currentIsNext);
				currentIsNext = !left;
			}
			// We can tolerate external modification as long as the node that this spliterator is anchored to has not been removed
			// This situation is easy to detect
			if (!current.isPresent())
				throw new ConcurrentModificationException(
					"The collection has been modified externally such that this spliterator has been orphaned");
			if (currentIsNext == left) {
				RedBlackNode<E> next = current.getClosest(left);
				if (next != null && isIncluded(next, () -> true)) {
					current = next;
					currentIsNext = left;
				} else
					return false;
			} else
				currentIsNext = !currentIsNext;
			return true;
		}

		protected boolean isIncluded(RedBlackNode<E> node, BooleanSupplier cont) {
			if (theLeftBound != null) {
				if (node == theLeftBound)
					return true; // Left bound is included
				if (node.getNodesBefore(cont) < theLeftBound.getNodesBefore(cont))
					return false;
			}
			if (theRightBound != null) {
				if (node == theRightBound)
					return false; // Right bound is excluded
				if (node.getNodesBefore(cont) > theRightBound.getNodesBefore(cont))
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
			try (Transaction t = lock(false, true, null)) {
				BooleanSupplier cont = () -> true;
				if (theTree.getRoot() == null)
					return null;
				if (current == null)
					current = theTree.getTerminal(!currentIsNext);
				RedBlackNode<E> divider;
				if (theLeftBound == null) {
					if (theRightBound == null) {
						if (size() <= 1)
							return null;
						divider = theTree.getRoot();
					} else
						divider = step(theRightBound, true);
				} else if (theRightBound == null)
					divider = step(theLeftBound, false);
				else {
					int leftIdx = theLeftBound.getNodesBefore(cont);
					int rightIdx = theRightBound.getNodesBefore(cont);
					if (rightIdx - leftIdx <= 1)
						return null;
					divider = theTree.getRoot().get((leftIdx + rightIdx) / 2, cont);
				}

				if (divider == null)
					return null;

				MutableNodeSpliterator split;
				if (current.getNodesBefore(() -> true) < divider.getNodesBefore(cont)) { // We're on the left of the divider
					RedBlackNode<E> right = theRightBound == null ? theTree.getTerminal(false) : theRightBound;
					RedBlackNode<E> start = current == divider ? right : divider;
					split = new MutableNodeSpliterator(start, true, divider, right);
					theRightBound = divider;
				} else {
					RedBlackNode<E> left = theLeftBound == null ? theTree.getTerminal(true) : theLeftBound;
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
			return RedBlackNodeList.this.theLocker.doOptimistically(new StringBuilder(), (init, ctx) -> {
				init.setLength(0);
				RedBlackNode<E> node = theTree.getTerminal(true);
				if (theLeftBound == null && theRightBound != null)
					init.append('<');
				while (node != null) {
					if (node == theLeftBound)
						init.append('<');
					if (node == current) {
						if (currentIsNext)
							init.append('^');
						init.append('[');
					}
					init.append(node.getValue());
					if (node == current) {
						init.append(']');
						if (!currentIsNext)
							init.append('^');
					}
					if (node == theRightBound)
						init.append('>');
					node = node.getClosest(false);
					if (node != null)
						init.append(", ");
				}
				if (theRightBound == null && theLeftBound != null)
					init.append('>');
				return init;
			}, true).toString();
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
			public BetterCollection<E> getCollection() {
				return RedBlackNodeList.this;
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
				return theWrapped.getNodesBefore();
			}

			@Override
			public int getNodesAfter() {
				return theWrapped.getNodesAfter();
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
				return wrapSpliterNode(RedBlackNodeList.this.theLocker.doOptimistically(null, (init, ctx) -> theNode.get(index, ctx), true),
					null);
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

package org.qommons.tree;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementHandle;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableElementHandle;
import org.qommons.collect.MutableElementHandle.StdMsg;
import org.qommons.collect.MutableElementSpliterator;
import org.qommons.collect.TransactableList;

public abstract class RedBlackNodeList<E> implements BetterList<E>, TransactableList<E> {
	private final CollectionLockingStrategy theLocker;

	private RedBlackNode<E> theRoot;

	public RedBlackNodeList(CollectionLockingStrategy locker) {
		theLocker = locker;
	}

	protected BinaryTreeNode<E> getRoot() {
		return wrap(theRoot);
	}

	protected BinaryTreeNode<E> getTerminalNode(boolean start) {
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
	public int getElementsBefore(ElementId id) {
		return ((BinaryTreeNode<E>) id).getNodesBefore();
	}

	@Override
	public int getElementsAfter(ElementId id) {
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
	public <T> T ofElementAt(ElementId elementId, Function<? super ElementHandle<? extends E>, T> onElement) {
		try (Transaction t = lock(false, null)) {
			return onElement.apply(handleFor(elementId));
		}
	}

	@Override
	public <T> T ofMutableElementAt(ElementId elementId, Function<? super MutableElementHandle<? extends E>, T> onElement) {
		try (Transaction t = lock(true, null)) {
			return onElement.apply(mutableHandleFor(elementId));
		}
	}

	@Override
	public <T> T ofElementAt(int index, Function<? super ElementHandle<? extends E>, T> onElement) {
		try (Transaction t = lock(false, null)) {
			if (theRoot == null)
				throw new IndexOutOfBoundsException(index + " of 0");
			return onElement.apply(wrap(theRoot.get(index)));
		}
	}

	@Override
	public <T> T ofMutableElementAt(int index, Function<? super MutableElementHandle<? extends E>, T> onElement) {
		try (Transaction t = lock(true, null)) {
			if (theRoot == null)
				throw new IndexOutOfBoundsException(index + " of 0");
			return onElement.apply(wrapMutable(theRoot.get(index)));
		}
	}

	@Override
	public MutableElementSpliterator<E> mutableSpliterator(boolean fromStart) {
		return mutableSpliterator(wrap(theRoot == null ? null : theRoot.getTerminal(fromStart)), fromStart);
	}

	@Override
	public MutableElementSpliterator<E> mutableSpliterator(int index) {
		try (Transaction t = lock(false, null)) {
			RedBlackNode<E> node;
			if (theRoot != null)
				node = theRoot.get(index);
			else if (index == 0)
				node = null;
			else
				throw new IndexOutOfBoundsException(index + " of 0");

			return new MutableNodeSpliterator(node, true);
		}
	}

	@Override
	public String canAdd(E value) {
		return null;
	}

	@Override
	public boolean add(E e) {
		if (theRoot == null)
			theRoot = new RedBlackNode<>(e);
		else
			mutableSpliterator(false).tryAdvanceElementM(el -> el.add(e, false));
		return true;
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		for (E value : c)
			add(value);
		return !c.isEmpty();
	}

	@Override
	public void clear() {
		theRoot = null;
	}

	protected BinaryTreeNode<E> nodeFor(ElementId elementId) {
		return checkNode(elementId);
	}

	protected MutableBinaryTreeNode<E> mutableNodeFor(ElementId elementId) {
		return wrapMutable(checkNode(elementId).theNode);
	}

	protected MutableBinaryTreeNode<E> mutableNodeFor(BinaryTreeNode<E> node) {
		return wrapMutable(checkNode(node).theNode);
	}

	protected ElementHandle<E> handleFor(ElementId elementId) {
		return checkNode(elementId);
	}

	protected MutableElementHandle<E> mutableHandleFor(ElementId elementId) {
		return wrapMutable(checkNode(elementId).theNode);
	}

	protected MutableElementHandle<E> mutableHandleFor(BinaryTreeNode<E> node) {
		return wrapMutable(checkNode(node).theNode);
	}

	protected MutableElementSpliterator<E> mutableSpliterator(BinaryTreeNode<E> node, boolean next) {
		return mutableSpliterator(node, next, null, null);
	}

	protected MutableElementSpliterator<E> mutableSpliterator(BinaryTreeNode<E> node, boolean next, BinaryTreeNode<E> leftBound,
		BinaryTreeNode<E> rightBound) {
		return new MutableNodeSpliterator(checkNode(node).theNode, next, leftBound == null ? null : checkNode(leftBound).theNode,
			rightBound == null ? null : checkNode(rightBound).theNode);
	}

	private NodeWrapper checkNode(Object nodeRef) {
		if (!(nodeRef instanceof RedBlackNodeList.NodeWrapper))
			throw new IllegalArgumentException(StdMsg.NOT_FOUND);
		NodeWrapper node = (NodeWrapper) nodeRef;
		if (!node.check())
			throw new IllegalArgumentException(StdMsg.NOT_FOUND);
		return node;
	}

	private NodeWrapper wrap(RedBlackNode<E> node) {
		return node == null ? null : new NodeWrapper(node);
	}

	private MutableNodeWrapper wrapMutable(RedBlackNode<E> node) {
		return node == null ? null : new MutableNodeWrapper(node);
	}

	class NodeWrapper implements BinaryTreeNode<E>, ElementHandle<E> {
		final RedBlackNode<E> theNode;

		NodeWrapper(RedBlackNode<E> node) {
			theNode = node;
		}

		protected boolean check() {
			return theNode.getParent() != null || theRoot == theNode;
		}

		@Override
		public ElementId getElementId() {
			return this;
		}

		@Override
		public E get() {
			if (!check())
				throw new IllegalStateException("This element has been removed");
			return theNode.getValue();
		}

		@Override
		public E getValue() {
			if (!check())
				throw new IllegalStateException("This element has been removed");
			return theNode.getValue();
		}

		@Override
		public BinaryTreeNode<E> getParent() {
			if (!check())
				throw new IllegalStateException("This element has been removed");
			return wrap(theNode.getParent());
		}

		@Override
		public BinaryTreeNode<E> getLeft() {
			if (!check())
				throw new IllegalStateException("This element has been removed");
			return wrap(theNode.getLeft());
		}

		@Override
		public BinaryTreeNode<E> getRight() {
			if (!check())
				throw new IllegalStateException("This element has been removed");
			return wrap(theNode.getRight());
		}

		@Override
		public int size() {
			if (!check())
				throw new IllegalStateException("This element has been removed");
			return theNode.size();
		}

		@Override
		public ReversedNodeWrapper reverse() {
			return new ReversedNodeWrapper(this);
		}
	}

	class ReversedNodeWrapper implements ElementId, ElementHandle<E> {
		private final NodeWrapper theNode;

		ReversedNodeWrapper(NodeWrapper node) {
			theNode = node;
		}

		protected NodeWrapper getNode() {
			return theNode;
		}

		@Override
		public ElementId getElementId() {
			return this;
		}

		@Override
		public E get() {
			return theNode.get();
		}

		@Override
		public int compareTo(ElementId other) {
			return theNode.compareTo(((ReversedNodeWrapper) other).theNode);
		}

		@Override
		public NodeWrapper reverse() {
			return theNode;
		}
	}

	class MutableNodeWrapper extends NodeWrapper implements MutableBinaryTreeNode<E>, MutableElementHandle<E> {
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
		public void setValue(E value) {
			if (!check())
				throw new IllegalStateException("This element has been removed");
			try (Transaction t = lock(true, null)) {
				theNode.setValue(value);
			}
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
		public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
			if (!check())
				throw new IllegalStateException("This element has been removed");
			setValue(value);
		}

		@Override
		public String canRemove() {
			return null;
		}

		@Override
		public void remove() {
			if (!check())
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
		public void add(E value, boolean onLeft) {
			if (!check())
				throw new IllegalStateException("This element has been removed");
			try (Transaction t = lock(true, null)) {
				theRoot = theNode.add(new RedBlackNode<>(value), onLeft);
			}
		}

		@Override
		public ReversedMutableNodeWrapper reverse() {
			return new ReversedMutableNodeWrapper(this);
		}
	}

	class ReversedMutableNodeWrapper extends ReversedNodeWrapper implements MutableElementHandle<E> {
		ReversedMutableNodeWrapper(MutableNodeWrapper node) {
			super(node);
		}

		@Override
		protected MutableNodeWrapper getNode() {
			return (MutableNodeWrapper) super.getNode();
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
		public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
			getNode().set(value);
		}

		@Override
		public String canRemove() {
			return null;
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			getNode().remove();
		}

		@Override
		public String canAdd(E value, boolean before) {
			return null;
		}

		@Override
		public void add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
			getNode().add(value, before);
		}

		@Override
		public MutableNodeWrapper reverse() {
			return getNode();
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
		public boolean tryAdvanceElement(Consumer<? super ElementHandle<E>> action) {
			try (Transaction t = lock(false, null)) {
				if (!tryElement(false))
					return false;
				action.accept(wrap(current));
				return true;
			}
		}

		@Override
		public boolean tryReverseElement(Consumer<? super ElementHandle<E>> action) {
			try (Transaction t = lock(false, null)) {
				if (!tryElement(true))
					return false;
				action.accept(wrap(current));
				return true;
			}
		}

		@Override
		public boolean tryAdvanceElementM(Consumer<? super MutableElementHandle<E>> action) {
			try (Transaction t = lock(true, null)) {
				if (!tryElement(false))
					return false;
				action.accept(new MutableSpliteratorHandle(current));
				return true;
			}
		}

		@Override
		public boolean tryReverseElementM(Consumer<? super MutableElementHandle<E>> action) {
			try (Transaction t = lock(true, null)) {
				if (!tryElement(true))
					return false;
				action.accept(new MutableSpliteratorHandle(current));
				return true;
			}
		}

		@Override
		public void forEachElement(Consumer<? super ElementHandle<E>> action) {
			try (Transaction t = lock(false, null)) {
				while (tryElement(false))
					action.accept(wrap(current));
			}
		}

		@Override
		public void forEachElementReverse(Consumer<? super ElementHandle<E>> action) {
			try (Transaction t = lock(false, null)) {
				while (tryElement(true))
					action.accept(wrap(current));
			}
		}

		@Override
		public void forEachElementM(Consumer<? super MutableElementHandle<E>> action) {
			try (Transaction t = lock(true, null)) {
				while (tryElement(false))
					action.accept(new MutableSpliteratorHandle(current));
			}
		}

		@Override
		public void forEachElementReverseM(Consumer<? super MutableElementHandle<E>> action) {
			try (Transaction t = lock(true, null)) {
				while (tryElement(true))
					action.accept(new MutableSpliteratorHandle(current));
			}
		}

		@Override
		public MutableElementSpliterator<E> trySplit() {
			try(Transaction t=lock(false, null)){
				if (theRoot == null)
					return null;
				if (current == null)
					current = theRoot.getTerminal(!wasNext);
				RedBlackNode<E> divider;
				if(theLeftBound==null){
					if(theRightBound==null){
						if(size()<=1)
							return null;
						divider=theRoot;
					} else
						divider=step(theRightBound, true);
				} else if(theRightBound==null)
					divider=step(theLeftBound, false);
				else{
					int leftIdx=theLeftBound.getNodesBefore();
					int rightIdx=theRightBound.getNodesBefore();
					if (rightIdx - leftIdx <= 1)
						return null;
					divider = theRoot.get((leftIdx + rightIdx) / 2);
				}

				if(divider==null)
					return null;
				
				MutableNodeSpliterator split;
				if(current.getNodesBefore()<divider.getNodesBefore()){ //We're on the left of the divider
					RedBlackNode<E> right = theRightBound == null ? theRoot.getTerminal(false) : theRightBound;
					RedBlackNode<E> start=current==divider ? right : divider;
					split= new MutableNodeSpliterator(start, true, divider, right);
					theRightBound=divider;
				} else{
					RedBlackNode<E> left = theLeftBound == null ? theRoot.getTerminal(true) : theLeftBound;
					RedBlackNode<E> start=current==divider ? left : divider;
					split=new MutableNodeSpliterator(start, true, left, divider);
					theLeftBound=divider;
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

		private class MutableSpliteratorHandle extends MutableNodeWrapper {
			MutableSpliteratorHandle(RedBlackNode<E> node) {
				super(node);
			}

			@Override
			public void remove() {
				try (Transaction t = lock(true, null)) {
					if (theNode == current) {
						RedBlackNode<E> newCurrent = current.getClosest(true);
						if (newCurrent != null)
							wasNext = false;
						else {
							newCurrent = current.getClosest(false);
							wasNext = true;
						}
						current = newCurrent;
					}
					super.remove();
				}
			}
		}
	}
}

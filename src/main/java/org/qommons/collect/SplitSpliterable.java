package org.qommons.collect;

import java.util.ConcurrentModificationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.qommons.Transaction;
import org.qommons.tree.BinaryTreeNode;
import org.qommons.tree.MutableBinaryTreeNode;
import org.qommons.tree.RedBlackNode;
import org.qommons.tree.RedBlackNodeList;

public interface SplitSpliterable<E> extends BetterCollection<E> {
	/**
	 * Quickly obtains an element that is well-spaced between two other elements
	 * 
	 * @param element1 The ID of one element
	 * @param element2 The ID of the other element
	 * @return An element in this list that is between the given elements with a spacing suitable for double-bounded binary search; or null
	 *         if the elements are the same or adjacent
	 */
	CollectionElement<E> splitBetween(ElementId element1, ElementId element2);

	@Override
	default MutableElementSpliterator<E> spliterator(boolean fromStart) {
		// TODO Auto-generated method stub
	}

	@Override
	default MutableElementSpliterator<E> spliterator(ElementId element, boolean asNext) {
		// TODO Auto-generated method stub
	}

	/** A spliterator for a {@link RedBlackNodeList} */
	protected class MutableSplittableSpliterator<E> implements MutableElementSpliterator<E> {
		private final SplitSpliterable<E> theCollection;
		private CollectionElement<E> current;
		private boolean currentIsNext;

		private CollectionElement<E> theLeftBound;
		private CollectionElement<E> theRightBound;

		/**
		 * @param node The node anchor for this spliterator
		 * @param next Whether the given node is to be the next or the previous node this spliterator returns
		 */
		protected MutableSplittableSpliterator(SplitSpliterable<E> collection, CollectionElement<E> node, boolean next) {
			this(collection, node, next, null, null);
		}

		private MutableSplittableSpliterator(SplitSpliterable<E> collection, CollectionElement<E> node, boolean next,
			CollectionElement<E> left, CollectionElement<E> right) {
			theCollection = collection;
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
					size = theCollection.size();
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

		/**
		 * Moves this spliterator's cursor one element to the left or right, if possible
		 * 
		 * @param left Whether to move the spliterator left or right in the collection
		 * @return True if the move was successful, false if the spliterator is as far left/right as it can go within its bounds and the
		 *         collection
		 */
		protected boolean tryElement(boolean left) {
			if (theTree.getRoot() == null) {
				current = null;
				return false;
			}
			if (current == null) {
				current = theCollection.getTerminalElement(!currentIsNext);
				currentIsNext = !left;
			}
			// We can tolerate external modification as long as the node that this spliterator is anchored to has not been removed
			// This situation is easy to detect
			if (!current.getElementId().isPresent())
				throw new ConcurrentModificationException(
					"The collection has been modified externally such that this spliterator has been orphaned");
			if (currentIsNext == left) {
				CollectionElement<E> next = theCollection.getAdjacentElement(current.getElementId(), !left);
				if (next != null && isIncluded(next, () -> true)) {
					current = next;
					currentIsNext = left;
				} else
					return false;
			} else
				currentIsNext = !currentIsNext;
			return true;
		}

		/**
		 * @param node The node to check
		 * @param cont The continue boolean to terminate the operation in an optimistic context. This method returns false immediately if
		 *        this ever supplies false
		 * @return Whether the given node is included in this spliterator
		 */
		protected boolean isIncluded(CollectionElement<E> node, BooleanSupplier cont) {
			if (theLeftBound != null && RedBlackNode.compare(node, theLeftBound, cont) < 0)
				return false;
			if (theRightBound != null && RedBlackNode.compare(node, theRightBound, cont) >= 0)
				return false;
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
			action.accept(//
				new MutableSpliteratorNode(current, wrapMutable(current)));
			return true;
		}

		@Override
		public MutableElementSpliterator<E> trySplit() {
			try (Transaction t = theCollection.lock(false, true, null)) {
				CollectionElement<E> left = theLeftBound == null ? theCollection.getTerminalElement(true) : theLeftBound;
				CollectionElement<E> right = theRightBound == null ? theCollection.getTerminalElement(false) : theRightBound;
				if (left == null || right == null)
					return null;
				BooleanSupplier cont = () -> true;
				CollectionElement<E> divider = theCollection.splitBetween(left.getElementId(), right.getElementId());
				if (divider == null)
					return null;

				MutableSplittableSpliterator split;
				if (RedBlackNode.compare(current, divider, cont) < 0) { // We're on the left of the divider
					CollectionElement<E> start = current == divider ? right : divider;
					split = new MutableSplittableSpliterator(start, true, divider, right);
					theRightBound = divider;
				} else {
					CollectionElement<E> start = current == divider ? left : divider;
					split = new MutableSplittableSpliterator(start, true, left, divider);
					theLeftBound = divider;
				}
				return split;
			}
		}

		@Override
		public String toString() {
			return RedBlackNodeList.this.theLocker.doOptimistically(new StringBuilder(), (init, ctx) -> {
				init.setLength(0);
				CollectionElement<E> node = theCollection.getTerminalElement(true);
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
					init.append(node.get());
					if (node == current) {
						init.append(']');
						if (!currentIsNext)
							init.append('^');
					}
					if (node == theRightBound)
						init.append('>');
					node = theCollection.getAdjacentElement(node.getElementId(), true);
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
			private final CollectionElement<E> theNode;
			private final MutableBinaryTreeNode<E> theWrapped;

			MutableSpliteratorNode(CollectionElement<E> node, MutableBinaryTreeNode<E> wrap) {
				theNode = node;
				theWrapped = wrap;
			}

			@Override
			public BetterCollection<E> getCollection() {
				return theCollection;
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
			public MutableBinaryTreeNode<E> get(int index, OptimisticContext ctx) {
				return wrapSpliterNode(RedBlackNodeList.this.theLocker.doOptimistically(null,
					(init, ctx2) -> theNode.get(index, OptimisticContext.and(ctx, ctx2)), true), null);
			}

			@Override
			public MutableBinaryTreeNode<E> findClosest(Comparable<BinaryTreeNode<E>> finder, boolean lesser, boolean strictly,
				OptimisticContext ctx) {
				return RedBlackNodeList.this.theLocker.doOptimistically(null, //
					(init, ctx2) -> {
						MutableBinaryTreeNode<E> found = theWrapped.findClosest(finder, lesser, strictly, OptimisticContext.and(ctx, ctx2));
						return found == null ? null : wrapSpliterNode(((MutableNodeWrapper) found).theNode, found);
					}, true);
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
				try (Transaction t = theCollection.lock(true, null)) {
					CollectionElement<E> newCurrent;
					boolean newNext;
					if (theNode == current) {
						newCurrent = theCollection.getAdjacentElement(current.getElementId(), false);
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

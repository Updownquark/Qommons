package org.qommons.tree;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;

import org.qommons.collect.OptimisticContext;

public class RedBlackTree<E> {
	private RedBlackNode<E> theRoot;
	RedBlackNode<E> theFirst;
	RedBlackNode<E> theLast;
	volatile long theStructureStamp;

	public RedBlackTree() {}

	public int size() {
		return theRoot == null ? 0 : theRoot.size();
	}

	public RedBlackNode<E> getRoot() {
		return theRoot;
	}

	public RedBlackNode<E> setRoot(RedBlackNode root) {
		theStructureStamp++;
		if (root == null)
			theFirst = theLast = null;
		else if (root.size() == 1)
			theFirst = theLast = root;
		return theRoot = root;
	}

	public RedBlackNode<E> getFirst() {
		return theFirst;
	}

	public RedBlackNode<E> getLast() {
		return theLast;
	}

	public RedBlackNode<E> getTerminal(boolean first) {
		return first ? theFirst : theLast;
		// return theRoot == null ? null : theRoot.getTerminal(first, () -> true);
	}

	public RedBlackNode<E> insert(E value, Comparator<? super E> compare, boolean distinct) {
		if (theRoot == null) {
			theRoot = new RedBlackNode<>(this, value);
			return theRoot;
		}
		RedBlackNode<E> found = theRoot.findClosest(n -> compare.compare(value, n.getValue()), false, false, OptimisticContext.TRUE);
		int comp = compare.compare(value, found.getValue());
		if (distinct && comp == 0)
			return null;
		RedBlackNode<E> newNode = new RedBlackNode<>(this, value);
		found.add(newNode, comp < 0);
		return newNode;
	}

	public RedBlackTree<E> copy() {
		RedBlackTree<E> copy = new RedBlackTree<>();
		if (theRoot != null)
			copy.theRoot = RedBlackNode.deepCopy(theRoot, copy, v -> v);
		copy.theStructureStamp = theStructureStamp;
		return copy;
	}

	public interface RepairListener<E, X> {
		/**
		 * <p>
		 * Called after an node is removed from the collection due to a collision with a different node. It will also be called if the node
		 * is no longer compatible with this collection (e.g. a sub-set).
		 * </p>
		 * <p>
		 * For some collection, especially sub-sets, it is not possible for the view to determine whether an node previously belonged to the
		 * set. So this method may be called for nodes that were not present.
		 * </p>
		 * 
		 * @param node The node removed due to a collision or due to the node no longer being compatible with this collection (i.e. a
		 *        sub-set)
		 */
		void removed(RedBlackNode<E> node);

		/**
		 * <p>
		 * Called after an node is removed, before its value is transferred to a new position in the collection. This will be immediately
		 * followed by a call to {@link #postTransfer(RedBlackNode, Object)} with a new node with the same value.
		 * </p>
		 * <p>
		 * For some collection, especially sub-sets, it is not possible for the view to determine whether an node previously belonged to the
		 * set. So this method may be called for nodes that were not present previously, but are now.
		 * </p>
		 * 
		 * @param node The node, having just been removed, which will immediately be transferred to another position in the collection, with
		 *        a corresponding call to {@link #postTransfer(RedBlackNode, Object)}
		 * @return A piece of data which will be given as the second argument to {@link #postTransfer(RedBlackNode, Object)} as a means of
		 *         tracking
		 */
		X preTransfer(RedBlackNode<E> node);

		/**
		 * Called after an node is transferred to a new position in the collection. Typically, this will be immediately after a
		 * corresponding call to {@link #preTransfer(RedBlackNode)}, but if it can be determined that the node was previously not present in
		 * this collection, but now is as a result of the transfer, {@link #preTransfer(RedBlackNode)} may not be called first and
		 * <code>data</code> will be null.
		 * 
		 * @param node The node previously removed (with a corresponding call to {@link #preTransfer(RedBlackNode)}) and now re-added in a
		 *        different position within the collection
		 * @param data The data returned from the {@link #preTransfer(RedBlackNode)} call, or null if the pre-transferred node was not a
		 *        member of this collection
		 */
		void postTransfer(RedBlackNode<E> node, X data);
	}

	/**
	 * <p>
	 * Searches for and fixes any inconsistencies in the tree's storage structure at the given node.
	 * </p>
	 * 
	 * @param <X> The type of the data transferred for the listener
	 * @param node The node at which to repair the structure
	 * @param compare The ordering of the values in the tree
	 * @param distinct Whether the values in the tree should be distinct
	 * @param listener The listener to monitor repairs. May be null.
	 * @return Whether any inconsistencies were found
	 */
	public <X> boolean repair(RedBlackNode<E> node, Comparator<? super E> compare, boolean distinct, RepairListener<E, X> listener) {
		if (node.size() == 1) {
			boolean valid = check(node, compare, distinct);
			if (!valid) {
				X datum = listener == null ? null : listener.preTransfer(node);
				node.delete();
				node = insert(node.getValue(), compare, distinct);
				if (listener != null)
					listener.postTransfer(node, datum);
			}
			return valid;
		}
		// In order to reliably maintain the tree, this method needs to operate on the entire subtree rooted at the given node
		// First, scan the subtree and figure out which nodes need are out-of-place
		RedBlackNode<E> leftMost = node;
		while (leftMost.getLeft() != null)
			leftMost = leftMost.getLeft();
		RedBlackNode<E> rightMost = node;
		while (rightMost.getRight() != null)
			rightMost = rightMost.getRight();
		boolean hasOuterLeftBound = leftMost.getClosest(true) != null;
		E outerLeftBound = hasOuterLeftBound ? leftMost.getClosest(true).getValue() : null;
		boolean hasOuterRightBound = rightMost.getClosest(false) != null;
		E outerRightBound = hasOuterRightBound ? rightMost.getClosest(false).getValue() : null;

		boolean hasInnerLeftBound = false;
		E innerLeftBound = null;

		RedBlackNode<E> n = leftMost;
		int removeFromSubTree = 0;
		int moveWithinSubTree = 0;
		BitSet toMove = new BitSet(node.size());
		int index = 0;
		while (true) {
			if (hasOuterLeftBound && !check(outerLeftBound, n.getValue(), compare, distinct)) {
				toMove.set(index);
				removeFromSubTree++;
			} else if (hasOuterRightBound && !check(n.getValue(), outerRightBound, compare, distinct)) {
				toMove.set(index);
				removeFromSubTree++;
			} else if (hasInnerLeftBound) {
				if (!check(innerLeftBound, n.getValue(), compare, distinct))
					moveWithinSubTree++;
			} else {
				hasInnerLeftBound = true;
				innerLeftBound = n.getValue();
			}
			if (n == rightMost)
				break;
			else {
				n = n.getClosest(false);
				index++;
			}
		}
		if (moveWithinSubTree > 0) {
			// Some nodes which belong in the subtree will need to be rearranged.
			// We want to make an effort to move as few as possible.
			// If using the first such node we came to as the standard causes the move of most of these nodes,
			// perhaps moving that first node instead and using the second node as the standard would let us remove fewer.
			// The only way to do be sure we're moving as few nodes as possible
			// is to do this test for every node that belongs in the subtree, which would take O(n^2) time.
			// This method seems to me a good compromise.
			// At least it will perform optimally when only a single node in the subtree needs to move.
			boolean movePrimary = moveWithinSubTree > (node.size() - removeFromSubTree) / 2;
			// Now mark the subtree-valid nodes that need to be moved
			n = leftMost;
			index = 0;
			hasInnerLeftBound = false;
			while (true) {
				if (toMove.get(index)) {// Doesn't belong in the subtree; ignore
				} else if (hasInnerLeftBound) {
					if (!check(innerLeftBound, n.getValue(), compare, distinct))
						toMove.set(index);
				} else if (movePrimary) {
					// First subtree-valid node, which we've decided to move
					movePrimary = false; // Use the next subtree-valid node as the left bound
				} else {
					hasInnerLeftBound = true;
					innerLeftBound = n.getValue();
				}
				if (n == rightMost)
					break;
				else {
					n = n.getClosest(false);
					index++;
				}
			}
		} else if (removeFromSubTree == 0)
			return false; // All nodes are fine where they are

		int todo = todo; // There are still problems here.
		// For one, there may now be duplicate elements. This code currently only does moves and assumes that insertion will return a
		// non-null node, but if there is a duplicate, the operation should actually be a removal.
		// Determining whether a node has a duplicate in the corrupt tree, however, may not be trivial.
		// For another thing, since this operation operates on subtrees, it's possible that when addressing one problem point in the tree
		// while another unaddressed corruption point still exists, the first repair operation will re-add nodes at the wrong place, but
		// due to rebalancing after addition, these new nodes will not be under the subtree of the second problem node
		// and the problem created when repairing the first problem will go uncorrected.

		// The bit set now contains true for every node we will need to move
		// Remove all the necessary nodes, storing the listener-specific data for later insertion
		// Just to note, this operation may cause rebalancing, so no further logic may reason on the node's parent-child subtree structure
		int moveCount = toMove.cardinality();
		List<X> listenerData = listener == null ? null : new ArrayList<>(moveCount);
		List<E> toReAdd = new ArrayList<>(moveCount);
		n = leftMost;
		index = 0;
		while (true) {
			RedBlackNode<E> next = n.getClosest(false); // Grab next first, since we may be about to remove n
			if (toMove.get(index)) {
				if (listener != null)
					listenerData.add(listener.preTransfer(n));
				toReAdd.add(n.getValue());
			}
			if (n == rightMost)
				break;
			else {
				n = next;
				index++;
			}
		}
		// Now we may regard the tree as having full integrity.
		// This may not be true, of course; it's possible that other points in the tree may have become invalid
		// due to the same external operation as caused the problems we're addressing right now.
		// In that case, the re-insertion here may cause further corruption under those sub-trees.
		// But we'll have to assume that eventually the caller will also check and repair those places,
		// which is why this operation repairs the subtree and not just the node.

		// Now we re-add the removed nodes to the tree
		for (int i = 0; i < toReAdd.size(); i++) {
			n = insert(toReAdd.get(i), compare, distinct);
			if (listener != null)
				listener.postTransfer(n, listenerData.get(i));
		}
		return true; // If we made it this far, that means we actually did to repair work
	}

	/**
	 * <p>
	 * Searches for and fixes any inconsistencies in the entire tree's storage structure.
	 * </p>
	 * 
	 * @param <X> The type of the data transferred for the listener
	 * @param compare The ordering of the values in the tree
	 * @param distinct Whether the values in the tree should be distinct
	 * @param listener The listener to monitor repairs. May be null.
	 * @return Whether any inconsistencies were found
	 */
	public <X> boolean repair(Comparator<? super E> compare, boolean distinct, RepairListener<E, X> listener) {
		RedBlackNode<E> root = theRoot;
		if (root == null)
			return false;
		else
			return repair(root, compare, distinct, listener);
	}

	private static <E> boolean check(RedBlackNode<E> node, Comparator<? super E> compare, boolean distinct) {
		return (node.getClosest(true) == null || check(node.getClosest(true).getValue(), node.getValue(), compare, distinct))//
			&& (node.getClosest(false) == null || check(node.getValue(), node.getClosest(false).getValue(), compare, distinct));
	}

	private static <E> boolean check(E leftVal, E rightVal, Comparator<? super E> compare, boolean distinct) {
		int comp = compare.compare(leftVal, rightVal);
		return comp < 0 || (!distinct && comp == 0);
	}

	@Override
	public String toString() {
		return RedBlackNode.print(theRoot);
	}
}

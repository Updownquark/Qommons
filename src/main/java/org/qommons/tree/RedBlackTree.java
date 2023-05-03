package org.qommons.tree;


import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;

import org.qommons.collect.OptimisticContext;

/**
 * A red-black tree structure
 * 
 * @param <E> The type of values stored in the tree
 */
public class RedBlackTree<E> {
	private RedBlackNode<E> theRoot;
	RedBlackNode<E> theFirst;
	RedBlackNode<E> theLast;
	volatile long theStructureStamp;

	/** @return The number of nodes in this tree */
	public int size() {
		return theRoot == null ? 0 : theRoot.size();
	}

	/** @return The root node of this tree, or null if the tree is empty */
	public RedBlackNode<E> getRoot() {
		return theRoot;
	}

	/** @param root The new root for the tree */
	public void setRoot(RedBlackNode<E> root) {
		theStructureStamp++;
		if (root == null)
			theFirst = theLast = null;
		else if (root.size() == 1)
			theFirst = theLast = root;
		theRoot = root;
	}

	/** @return The left-most node in this tree, or null if the tree is empty */
	public RedBlackNode<E> getFirst() {
		return theFirst;
	}

	/** @return The right-most node in this tree, or null if the tree is empty */
	public RedBlackNode<E> getLast() {
		return theLast;
	}

	/**
	 * @param first Whether to return the left-most or right-most node
	 * @return The left-most (if <code>first</code>) or right-most (otherwise) node in this tree, or null if the tree is empty
	 */
	public RedBlackNode<E> getTerminal(boolean first) {
		return first ? theFirst : theLast;
		// return theRoot == null ? null : theRoot.getTerminal(first, () -> true);
	}

	/**
	 * @param value The value to add
	 * @param compare The value sorting
	 * @param distinct Whether to return null if the values is already present (i.e. {@link Comparator#compare(Object, Object)} returns 0
	 *        for a value in the tree and the argument)
	 * @return The new node, or null if the value was found and <code>distinct</code> was true
	 */
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

	/** @return An independent copy of this tree */
	public RedBlackTree<E> copy() {
		RedBlackTree<E> copy = new RedBlackTree<>();
		if (theRoot != null)
			copy.theRoot = RedBlackNode.deepCopy(theRoot, copy, v -> v);
		copy.theStructureStamp = theStructureStamp;
		return copy;
	}

	/**
	 * Allows tracking of the {@link RedBlackTree#repair(Comparator, boolean, RepairListener)} operation
	 * 
	 * @param <E> The type of values in the tree
	 * @param <X> The custom data object to use to track removed and moved nodes
	 */
	public interface RepairListener<E, X> {
		/** @see org.qommons.collect.ValueStoredCollection.RepairListener#removed(org.qommons.collect.CollectionElement) */
		@SuppressWarnings("javadoc")
		X removed(RedBlackNode<E> node);

		/** @see org.qommons.collect.ValueStoredCollection.RepairListener#disposed(org.qommons.collect.CollectionElement) */
		@SuppressWarnings("javadoc")
		void disposed(E value, X data);

		/** @see org.qommons.collect.ValueStoredCollection.RepairListener#transferred(org.qommons.collect.CollectionElement) */
		@SuppressWarnings("javadoc")
		void transferred(RedBlackNode<E> node, X data);
	}

	/**
	 * Searches for and fixes any inconsistencies in the tree's storage structure at or around the given node.
	 * 
	 * @param <X> The type of the data transferred for the listener
	 * @param node The node at which to repair the structure
	 * @param compare The ordering of the values in the tree
	 * @param distinct Whether the values in the tree should be distinct
	 * @param listener The listener to monitor repairs. May be null.
	 * @return Whether any inconsistencies were found
	 */
	public <X> boolean repair(RedBlackNode<E> node, Comparator<? super E> compare, boolean distinct, RepairListener<E, X> listener) {
		if (check(node, compare, distinct))
			return false;
		// Ok, something's wrong. Remove the node, call the repair listener, and save the data for later.
		// We do this first because if the given node is out of place, we want to make sure it is one of the ones that is moved.
		// E.g. if there are 2 nodes in the tree, they are mis-ordered, and the user calls repair on one of them,
		// it should be that one which is moved, not the other.
		node.delete();
		X nodeData = listener == null ? null : listener.removed(node);
		// Now figure out if the rest of the tree seems ok. We won't check every single node, but just the node's immediate surroundings.
		// If more than one node is out of place, we need to do a full tree repair to be safe.
		RedBlackNode<E> closest = node.getClosest(true);
		if (closest == null) {
			RedBlackNode<E> other = node.getClosest(false);
			if (other != null)
				closest = other;
			else
				return true;
		}
		RedBlackNode<E> left = closest.getClosest(true);
		RedBlackNode<E> right = closest.getClosest(false);
		if ((left == null || check(left.getValue(), closest.getValue(), compare, distinct))//
			&& (right == null || check(closest.getValue(), right.getValue(), compare, distinct))) {
			// Looks like it was just the single node that was corrupt, so we're done
		} else
			repairSubTree(theRoot, compare, distinct, listener); // Full tree repair

		// Attempt to re-add the original node
		E nodeValue = node.getValue();
		node = insert(nodeValue, compare, distinct);
		if (listener != null) {
			if (node != null)
				listener.transferred(node, nodeData);
			else
				listener.disposed(nodeValue, nodeData);
		}
		return true;
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
		if (size() <= 1)
			return false;
		return repairSubTree(theRoot, compare, distinct, listener);
	}

	private <X> boolean repairSubTree(RedBlackNode<E> node, Comparator<? super E> compare, boolean distinct,
		RepairListener<E, X> listener) {
		if (node.size() == 1) {
			boolean valid = check(node, compare, distinct);
			if (!valid) {
				X datum = listener == null ? null : listener.removed(node);
				node.delete();
				RedBlackNode<E> newNode = insert(node.getValue(), compare, distinct);
				if (listener != null) {
					if (newNode != null)
						listener.transferred(newNode, datum);
					else
						listener.disposed(node.getValue(), datum);
				}
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
				else
					innerLeftBound = n.getValue();
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
			n = leftMost;
			index = 0;
			// Some nodes which belong in the subtree will need to be rearranged.
			// We want to make an effort to move as few as possible.
			// If using the first such node we came to as the standard causes the move of most of these nodes,
			// perhaps moving that first node instead and using the second node as the standard would let us remove fewer.
			// The only way to be sure we're moving as few nodes as possible
			// is to do this test for every node that belongs in the subtree, which would take O(n^2) time.
			// This method seems to me a good compromise.
			// At least it will perform optimally when only a single node in the subtree needs to move.
			// Now mark the subtree-valid nodes that need to be moved
			boolean movePrimary = moveWithinSubTree > (node.size() - removeFromSubTree) / 2;
			if (movePrimary) {
				n = n.getClosest(false);
				index = 1;
				if (!toMove.get(0) && !check(leftMost.getValue(), n.getValue(), compare, distinct))
					toMove.set(0);
			}
			hasInnerLeftBound = false;
			while (true) {
				if (toMove.get(index)) {// Doesn't belong in the subtree; ignore
				} else if (hasInnerLeftBound) {
					if (!check(innerLeftBound, n.getValue(), compare, distinct))
						toMove.set(index);
					else
						innerLeftBound = n.getValue();
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
				n.delete();
				if (listener != null)
					listenerData.add(listener.removed(n));
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
			E value = toReAdd.get(i);
			n = insert(value, compare, distinct);
			if (listener != null) {
				if (n != null)
					listener.transferred(n, listenerData.get(i));
				else
					listener.disposed(value, listenerData.get(i));
			}
		}
		return true; // If we made it this far, that means we actually did to repair work
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

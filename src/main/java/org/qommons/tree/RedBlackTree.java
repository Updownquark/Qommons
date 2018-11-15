package org.qommons.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

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
		if (node == theRoot)
			return repair(compare, distinct, listener);
		boolean valid = check(node, compare, distinct);
		if (!valid) {
			int todo = todo;// TODO
		}
		return valid;
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
		List<RedBlackNode<E>> subTreesToRepair = new ArrayList<>();
		LinkedList<RedBlackNode<E>> toCheck = new LinkedList<>();
		RedBlackNode<E> child = root.getLeft();
		if (child != null)
			toCheck.add(child);
		child = root.getRight();
		if (child != null)
			toCheck.add(child);
		while (!toCheck.isEmpty()) {
			RedBlackNode<E> node = toCheck.removeFirst();
			if (check(node, compare, distinct)) {
				child = node.getLeft();
				if (child != null)
					toCheck.add(child);
				child = node.getRight();
				if (child != null)
					toCheck.add(child);
			} else
				subTreesToRepair.add(node);
		}
		// Flatten the sub trees so we can operate on each node without recursion
		for (int i = 0; i < subTreesToRepair.size(); i++) {
			RedBlackNode<E> node = subTreesToRepair.get(i);
			child = node.getLeft();
			if (child != null)
				subTreesToRepair.add(child);
			child = node.getRight();
			if (child != null)
				subTreesToRepair.add(child);
		}
		LinkedHashSet<RedBlackNode<E>> nodesToRepair = new LinkedHashSet<>(subTreesToRepair);
		Iterator<RedBlackNode<E>> nodeIter = nodesToRepair.iterator();
		while (nodeIter.hasNext()) {
			RedBlackNode<E> node = nodeIter.next();
			nodeIter.remove();
			X datum = listener == null ? null : listener.preTransfer(node);
			node.delete();
			// TODO Re-insert the node, but not under any invalid sub-trees
			// This is a problem, because any tree operations may cause rotations that mix up the sub trees
		}
		// TODO
	}

	private boolean check(RedBlackNode<E> node, Comparator<? super E> compare, boolean distinct) {
		boolean valid = true;
		RedBlackNode<E> adj = node.getClosest(true);
		if (adj != null) {
			int comp = compare.compare(adj.getValue(), node.getValue());
			if (comp > 0 || (distinct && comp == 0))
				valid = false;
		}
		if (valid) {
			adj = node.getClosest(true);
			if (adj != null) {
				int comp = compare.compare(adj.getValue(), node.getValue());
				if (comp < 0 || (distinct && comp == 0))
					valid = false;
			}
		}
		return valid;
	}

	@Override
	public String toString() {
		return RedBlackNode.print(theRoot);
	}
}

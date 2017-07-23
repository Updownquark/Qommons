package org.qommons.tree;

public interface BinaryTreeNode<E> {
	E getValue();

	BinaryTreeNode<E> getParent();

	BinaryTreeNode<E> getLeft();
	BinaryTreeNode<E> getRight();

	int size();

	/**
	 * @param node The node to get the size of
	 * @return The size of the given node, or 0 if the node is null
	 */
	static int sizeOf(BinaryTreeNode node) {
		return node == null ? 0 : node.size();
	}

	/** @return The root of the tree structure that this node exists in */
	default BinaryTreeNode<E> getRoot() {
		BinaryTreeNode<E> ret = this;
		while (ret.getParent() != null)
			ret = ret.getParent();
		return ret;
	}

	/** @return Whether this node is on the right (false) or the left (true) of its parent. False for the root. */
	default boolean getSide() {
		if (getParent() == null)
			return false;
		if (this == getParent().getLeft())
			return true;
		return false;
	}

	/**
	 * @param left Whether to get the left or right child
	 * @return The left or right child of this node
	 */
	default BinaryTreeNode<E> getChild(boolean left) {
		return left ? getLeft() : getRight();
	}

	/** @return The other child of this node's parent. Null if the parent is null. */
	default BinaryTreeNode<E> getSibling() {
		if (getParent() == null)
			return null;
		else if (getParent().getLeft() == this)
			return getParent().getRight();
		else
			return getParent().getLeft();
	}

	/**
	 * @param finder The compare operation to use to find the node. Must obey the ordering used to construct this structure.
	 * @return The node in this structure for which finder.compareTo(node)==0, or null if no such node exists.
	 */
	default BinaryTreeNode<E> find(Comparable<BinaryTreeNode<E>> finder) {
		int compare = finder.compareTo(this);
		if (compare == 0)
			return this;
		BinaryTreeNode<E> child = getChild(compare < 0);
		if (child != null)
			return child.find(finder);
		else
			return null;
	}

	/**
	 * Finds the node in this tree that is closest to {@code finder.compareTo(node)==0)}, on either the right or left side.
	 *
	 * @param finder The compare operation to use to find the node. Must obey the ordering used to construct this structure.
	 * @param lesser Whether to search for lesser or greater values (left or right, respectively)
	 * @param withExact Whether to accept an equivalent node, if present (as opposed to strictly left or right of)
	 * @return The found node
	 */
	default BinaryTreeNode<E> findClosest(Comparable<BinaryTreeNode<E>> finder, boolean lesser, boolean withExact) {
		BinaryTreeNode<E> node = this;
		BinaryTreeNode<E> found = null;
		while (true) {
			int compare = finder.compareTo(node);
			if (compare == 0) {
				if (withExact)
					return node;
				BinaryTreeNode<E> child = getChild(lesser);
				if (child == null)
					return found;
				while (child.getChild(!lesser) != null)
					child = child.getChild(!lesser);
				return child;
			}
			if (compare > 0 == lesser)
				found = node;
			BinaryTreeNode<E> child = getChild(compare < 0);
			if (child == null)
				return found;
			node = child;
		}
	}

	/** @return The number of nodes stored before this node in the tree */
	default int getNodesBefore() {
		BinaryTreeNode<E> node = this;
		BinaryTreeNode<E> left = node.getLeft();
		int ret = size(left);
		while (node != null) {
			BinaryTreeNode<E> parent = node.getParent();
			if (parent != null && parent.getRight() == node) {
				left = parent.getLeft();
				ret += size(left) + 1;
			}
			node = parent;
		}
		return ret;
	}

	/** @return The number of nodes stored after this node in the tree */
	default int getNodesAfter() {
		BinaryTreeNode<E> node = this;
		BinaryTreeNode<E> right = node.getRight();
		int ret = size(right);
		while (node != null) {
			BinaryTreeNode<E> parent = node.getParent();
			if (parent != null && parent.getLeft() == node) {
				right = parent.getRight();
				ret += size(right) + 1;
			}
			node = parent;
		}
		return ret;
	}
}

package org.qommons.tree;

public interface BinaryTreeNode<E> {
	E getValue();

	BinaryTreeNode<E> getParent();

	BinaryTreeNode<E> getLeft();

	BinaryTreeNode<E> getRight();

	int size();

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
		class Finder {

			private BinaryTreeNode<E> findClosest(Comparable<BinaryTreeNode<E>> finder, boolean lesser, boolean withExact,
				BinaryTreeNode<E> found) {
				int compare = finder.compareTo(this);
				if (compare == 0) {
					if (withExact)
						return this;
					BinaryTreeNode<E> child = getChild(lesser);
					if (child == null)
						return found;
					while (child.getChild(!lesser) != null)
						child = child.getChild(!lesser);
					return child;
				}
				if (compare > 0 == lesser)
					found = this;
				BinaryTreeNode<E> child = getChild(compare < 0);
				if (child != null)
					return child.findClosest(finder, lesser, withExact, found);
				else
					return found;
			}
		}
		return findClosest(finder, lesser, withExact, null);
	}

}

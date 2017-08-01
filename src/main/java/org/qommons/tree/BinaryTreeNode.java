package org.qommons.tree;

import org.qommons.collect.CollectionElement;

public interface BinaryTreeNode<E> extends CollectionElement<E> {
	BinaryTreeNode<E> getParent();

	BinaryTreeNode<E> getLeft();
	BinaryTreeNode<E> getRight();

	/**
	 * @param left Whether to get the closest node on the left or right
	 * @return The closest (in value) node to this node on one side or the other
	 */
	BinaryTreeNode<E> getClosest(boolean left);

	/** @return The size of this sub-tree */
	int size();

	/**
	 * @param node The node to get the size of
	 * @return The size of the given node, or 0 if the node is null
	 */
	static int sizeOf(BinaryTreeNode<?> node) {
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

	default BinaryTreeNode<E> get(int index) {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		BinaryTreeNode<E> node = this;
		int passed = 0;
		int nodeIndex = sizeOf(getLeft());
		while (node != null && index != nodeIndex) {
			boolean left = index < nodeIndex;
			if (!left)
				passed = nodeIndex;
			node = getChild(left);
			if (node != null)
				nodeIndex = passed + sizeOf(node.getLeft());
		}
		if (node == null)
			throw new IndexOutOfBoundsException(index + " of " + nodeIndex);
		return node;
	}

	default int indexFor(Comparable<? super BinaryTreeNode<? extends E>> search) {
		BinaryTreeNode<E> node = this;
		int passed = 0;
		int compare = search.compareTo(node);
		while (node != null && compare != 0) {
			if (compare > 0) {
				passed += sizeOf(node.getLeft()) + 1;
				node = node.getRight();
			} else
				node = node.getLeft();
			compare = search.compareTo(node);
		}
		if (node != null)
			return passed;
		else
			return -(passed + 1);
	}

	default BinaryTreeNode<E> getTerminal(boolean left) {
		BinaryTreeNode<E> parent = this;
		BinaryTreeNode<E> child = parent.getChild(left);
		while (child != null) {
			parent = child;
			child = parent.getChild(left);
		}
		return parent;
	}

	/**
	 * Finds the node in this tree that is closest to {@code finder.compareTo(node)==0)}, on either the left or right side.
	 *
	 * @param finder The compare operation to use to find the node. Must obey the ordering used to construct this structure.
	 * @param lesser Whether to return the closest node lesser or greater than (to the left or right, respectively) the given search if an
	 *        exact match ({@link Comparable#compareTo(Object) finder.compareTo(node)}==0) is not found
	 * @param strictly If false, this method will return a node that does not obey the <code>lesser</code> parameter if there is no such
	 *        node that obeys it. In other words, if <code>strictly</code> is false, this method will always return a node.
	 * @return The found node
	 */
	default BinaryTreeNode<E> findClosest(Comparable<BinaryTreeNode<E>> finder, boolean lesser, boolean strictly) {
		BinaryTreeNode<E> node = this;
		BinaryTreeNode<E> found = null;
		while (true) {
			int compare = finder.compareTo(node);
			if (compare == 0)
				return node;
			boolean matchesLesser = compare > 0 == lesser;
			if (matchesLesser || (found == null && !strictly))
				found = node;
			BinaryTreeNode<E> child = node.getChild(compare < 0);
			if (child == null)
				return found;
			node = child;
		}
	}

	/** @return The number of nodes stored before this node in the tree */
	default int getNodesBefore() {
		BinaryTreeNode<E> node = this;
		BinaryTreeNode<E> left = node.getLeft();
		int ret = sizeOf(left);
		while (node != null) {
			BinaryTreeNode<E> parent = node.getParent();
			if (parent != null && parent.getRight() == node) {
				left = parent.getLeft();
				ret += sizeOf(left) + 1;
			}
			node = parent;
		}
		return ret;
	}

	/** @return The number of nodes stored after this node in the tree */
	default int getNodesAfter() {
		BinaryTreeNode<E> node = this;
		BinaryTreeNode<E> right = node.getRight();
		int ret = sizeOf(right);
		while (node != null) {
			BinaryTreeNode<E> parent = node.getParent();
			if (parent != null && parent.getLeft() == node) {
				right = parent.getRight();
				ret += sizeOf(right) + 1;
			}
			node = parent;
		}
		return ret;
	}

	@Override
	default BinaryTreeNode<E> reverse() {
		return new ReversedBinaryTreeNode<>(this);
	}

	class ReversedBinaryTreeNode<E> extends ReversedCollectionElement<E> implements BinaryTreeNode<E> {
		public ReversedBinaryTreeNode(BinaryTreeNode<E> wrap) {
			super(wrap);
		}

		@Override
		protected BinaryTreeNode<E> getWrapped() {
			return (BinaryTreeNode<E>) super.getWrapped();
		}

		@Override
		public BinaryTreeNode<E> getParent() {
			return getWrapped().getParent().reverse();
		}

		@Override
		public BinaryTreeNode<E> getLeft() {
			return getWrapped().getRight().reverse();
		}

		@Override
		public BinaryTreeNode<E> getRight() {
			return getWrapped().getLeft().reverse();
		}

		@Override
		public BinaryTreeNode<E> getClosest(boolean left) {
			return BinaryTreeNode.reverse(getWrapped().getClosest(!left));
		}

		@Override
		public int size() {
			return getWrapped().size();
		}

		@Override
		public int getNodesBefore() {
			return getWrapped().getNodesAfter();
		}

		@Override
		public int getNodesAfter() {
			return getWrapped().getNodesBefore();
		}

		@Override
		public BinaryTreeNode<E> reverse() {
			return getWrapped();
		}
	}

	static <E> BinaryTreeNode<E> reverse(BinaryTreeNode<E> node) {
		return node == null ? node : node.reverse();
	}
}

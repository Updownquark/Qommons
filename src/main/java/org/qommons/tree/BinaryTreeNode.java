package org.qommons.tree;

import org.qommons.collect.BetterCollection;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.OptimisticContext;

/**
 * A CollectionElement in a tree-based {@link BetterCollection}
 * 
 * @param <E> The type of values in the collection
 */
public interface BinaryTreeNode<E> extends CollectionElement<E> {
	/** @return The node that is this node's parent in the tree structure */
	BinaryTreeNode<E> getParent();

	/** @return This node's left child in the tree structure */
	BinaryTreeNode<E> getLeft();

	/** @return This node's right child in the tree structure */
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
	BinaryTreeNode<E> getRoot();

	/** @return Whether this node is on the right (false) or the left (true) of its parent. False for the root. */
	boolean getSide();

	/**
	 * @param left Whether to get the left or right child
	 * @return The left or right child of this node
	 */
	default BinaryTreeNode<E> getChild(boolean left) {
		return left ? getLeft() : getRight();
	}

	/** @return The other child of this node's parent. Null if the parent is null. */
	BinaryTreeNode<E> getSibling();

	/**
	 * @param index The index of the node to get
	 * @param ctx The optimistic context which, if provided and returns false, will terminate the operation
	 * @return The node at the given index in this sub-tree
	 */
	BinaryTreeNode<E> get(int index, OptimisticContext ctx);

	/**
	 * @param search The search for nodes in this sub-tree
	 * @param ctx The optimistic context which, if provided and returns false, will terminate the operation
	 * @return Either:
	 *         <ul>
	 *         <li>The index of the node in this sub-tree matching the search
	 *         (<code>search.{@link Comparable#compareTo(Object) compareTo}(node)==0</code>)</li>
	 *         <li>or <code>-(index+1)</code> where <code>index</code> is the index in this sub-tree where a node matching the given search
	 *         would be inserted</li>
	 *         </ul>
	 */
	default int indexFor(Comparable<? super BinaryTreeNode<? extends E>> search, OptimisticContext ctx) {
		BinaryTreeNode<E> node = this;
		int passed = 0;
		int compare = search.compareTo(node);
		while (node != null && compare != 0//
			&& (ctx == null || ctx.getAsBoolean())) {
			if (compare > 0) {
				passed += sizeOf(node.getLeft()) + 1;
				node = node.getRight();
			} else
				node = node.getLeft();
			if (node != null)
				compare = search.compareTo(node);
		}
		if (node != null)
			return passed + sizeOf(node.getLeft());
		else
			return -(passed + 1);
	}

	/**
	 * Finds the node in this tree that is closest to {@code finder.compareTo(node)==0)}, on either the left or right side.
	 *
	 * @param finder The compare operation to use to find the node. Must obey the ordering used to construct this structure.
	 * @param lesser Whether to return the closest node lesser or greater than (to the left or right, respectively) the given search if an
	 *        exact match ({@link Comparable#compareTo(Object) finder.compareTo(node)}==0) is not found
	 * @param strictly If false, this method will return a node that does not obey the <code>lesser</code> parameter if there is no such
	 *        node that obeys it. In other words, if <code>strictly</code> is false, this method will always return a node.
	 * @param ctx The optimistic context which, if provided and returns false, will terminate the operation
	 * @return The found node
	 */
	default BinaryTreeNode<E> findClosest(Comparable<BinaryTreeNode<E>> finder, boolean lesser, boolean strictly, OptimisticContext ctx) {
		BinaryTreeNode<E> node = this;
		BinaryTreeNode<E> found = null;
		boolean foundMatches = false;
		while (ctx == null || ctx.getAsBoolean()) {
			int compare = finder.compareTo(node);
			if (compare == 0)
				return node;
			boolean matchesLesser = compare > 0 == lesser;
			if (matchesLesser) {
				found = node;
				foundMatches = true;
			} else if (!foundMatches && !strictly)
				found = node;
			BinaryTreeNode<E> child = node.getChild(compare < 0);
			if (child == null)
				return found;
			node = child;
		}
		return null;
	}

	/** @return The number of nodes stored before this node in the tree */
	int getNodesBefore();

	/** @return The number of nodes stored after this node in the tree */
	int getNodesAfter();

	@Override
	default BinaryTreeNode<E> reverse() {
		return new ReversedBinaryTreeNode<>(this);
	}

	/**
	 * A {@link BinaryTreeNode} that is reversed
	 * 
	 * @param <E> The type of the node
	 */
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
			return BinaryTreeNode.reverse(getWrapped().getParent());
		}

		@Override
		public BinaryTreeNode<E> getLeft() {
			return BinaryTreeNode.reverse(getWrapped().getRight());
		}

		@Override
		public BinaryTreeNode<E> getRight() {
			return BinaryTreeNode.reverse(getWrapped().getLeft());
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
		public BinaryTreeNode<E> getRoot() {
			return BinaryTreeNode.reverse(getWrapped().getRoot());
		}

		@Override
		public boolean getSide() {
			return !getWrapped().getSide();
		}

		@Override
		public BinaryTreeNode<E> getSibling() {
			return BinaryTreeNode.reverse(getWrapped().getSibling());
		}

		@Override
		public BinaryTreeNode<E> get(int index, OptimisticContext ctx) {
			return BinaryTreeNode.reverse(getWrapped().get(size() - index - 1, ctx));
		}

		@Override
		public BinaryTreeNode<E> reverse() {
			return getWrapped();
		}
	}

	/**
	 * @param <E> The type of value in the node
	 * @param node The node to reverse
	 * @return The reversed node, or null if node is null
	 */
	static <E> BinaryTreeNode<E> reverse(BinaryTreeNode<E> node) {
		return node == null ? node : node.reverse();
	}
}

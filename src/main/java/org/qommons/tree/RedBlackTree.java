package org.qommons.tree;

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

	@Override
	public String toString() {
		return RedBlackNode.print(theRoot);
	}
}

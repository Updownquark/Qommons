package org.qommons.tree;

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

	@Override
	public String toString() {
		return RedBlackNode.print(theRoot);
	}
}

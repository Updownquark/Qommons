package org.qommons.tree;

public class RedBlackTree<E> {
	private RedBlackNode<E> theRoot;
	private RedBlackNode<E> theFirst;
	private RedBlackNode<E> theLast;
	private long theStructureStamp;

	public RedBlackTree() {}

	public int size() {
		return theRoot == null ? 0 : theRoot.size();
	}

	public RedBlackNode<E> getRoot() {
		return theRoot;
	}

	public RedBlackNode<E> setRoot(RedBlackNode root) {
		return theRoot = root;
	}
	public RedBlackNode<E> getFirst() {
		return theFirst;
	}

	public RedBlackNode<E> getLast() {
		return theLast;
	}

	long getStructureStamp() {
		return theStructureStamp;
	}

	void updateFirst(RedBlackNode<E> currentFirst, RedBlackNode<E> newFirst) {
		if (theFirst == currentFirst)
			theFirst = newFirst;
	}

	void updateLast(RedBlackNode<E> currentLast, RedBlackNode<E> newLast) {
		if (theLast == currentLast)
			theLast = newLast;
	}

	public RedBlackNode<E> getTerminal(boolean first) {
		// return first ? theFirst : theLast;
		return theRoot == null ? null : theRoot.getTerminal(first, () -> true);
	}

	@Override
	public String toString() {
		return RedBlackNode.print(theRoot);
	}
}

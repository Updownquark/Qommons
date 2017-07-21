package org.qommons.tree;

import java.util.Map;

/**
 * A node in a red/black binary tree structure. This class does all the work of keeping itself balanced and has hooks that allow specialized
 * tree structures to achieve optimal performance without worrying about balancing.
 */
public abstract class RedBlackNode<E> implements Comparable<RedBlackNode<E>>, Cloneable {
	/** Returned from {@link RedBlackNode#add(RedBlackNode, boolean)} to provide information about the result of the add operation */
	public static class TreeOpResult {
		private RedBlackNode theFoundNode;

		private RedBlackNode theNewNode;

		RedBlackNode theNewRoot;

		TreeOpResult(RedBlackNode foundNode, RedBlackNode newNode, RedBlackNode root) {
			theFoundNode = foundNode;
			theNewNode = newNode;
			theNewRoot = root;
		}

		/** @return The node that was found in the tree that was equivalent to the given node. May be null if no equivalent node was found. */
		public RedBlackNode getFoundNode() {
			return theFoundNode;
		}

		/**
		 * @return The node that is now in the tree that is equivalent to the given node. May be identical to the given node or to the
		 *         {@link #getFoundNode() found node}.
		 */
		public RedBlackNode getNewNode() {
			return theNewNode;
		}

		/**
		 * Due to balancing rotation operations or replacement, the root of the tree may have changed as a result of the tree addition.
		 *
		 * @return The new root for the tree structure
		 */
		public RedBlackNode getNewRoot() {
			return theNewRoot;
		}
	}

	private boolean isRed;

	private RedBlackNode<E> theParent;
	private RedBlackNode<E> theLeft;
	private RedBlackNode<E> theRight;

	private int theSize;

	private boolean isTransaction;
	private int theTransaction;

	private final E theValue;

	/** Creates a node */
	public RedBlackNode(E value) {
		isRed = true;
		theSize = 1;

		theValue = value;
	}

	/** @return This node's value */
	public E getValue() {
		return theValue;
	}

	/** @return Whether this node is red or black */
	public boolean isRed() {
		return isRed;
	}

	/** @return The parent of this node in the tree structure. Will be null if and only if this node is the root (or an orphan). */
	public RedBlackNode<E> getParent() {
		return theParent;
	}

	/** @return The root of the tree structure that this node exists in */
	public RedBlackNode<E> getRoot() {
		RedBlackNode<E> ret = this;
		while(ret.getParent() != null)
			ret = ret.getParent();
		return ret;
	}

	/** Runs debugging checks on this tree structure to assure that all internal constraints are currently met. */
	public final void checkValid() {
		if(theParent != null)
			throw new IllegalStateException("checkValid() may only be called on the root");
		if(isRed)
			throw new IllegalStateException("The root is red!");

		checkValid(initValidationProperties());
	}

	/**
	 * Called by {@link #checkValid()}. May be overridden by subclasses to provide more validation initialization information
	 *
	 * @return A map containing mutable properties that may be used to ensure validation of the structure
	 */
	protected Map<String, Object> initValidationProperties() {
		Map<String, Object> ret = new java.util.LinkedHashMap<>();
		ret.put("black-depth", 0);
		return ret;
	}

	/**
	 * Called by {@link #checkValid()}. May be overridden by subclasses to check internal constraints specific to the subclassed node.
	 *
	 * @param properties The validation properties to use to check validity
	 */
	protected void checkValid(java.util.Map<String, Object> properties) {
		if(theLeft != null && theLeft.theParent != this)
			throw new IllegalStateException("(" + this + "): left (" + theLeft + ")'s parent is not this");
		if(theRight != null && theRight.theParent != this)
			throw new IllegalStateException("(" + this + "): right (" + theRight + ")'s parent is not this");
		Integer blackDepth = (Integer) properties.get("black-depth");
		if(isRed) {
			if((theLeft != null && theLeft.isRed) || (theRight != null && theRight.isRed))
				throw new IllegalStateException("Red node (" + this + ") has red children");
		} else {
			blackDepth = blackDepth + 1;
			properties.put("black-depth", blackDepth);
		}
		if(theLeft == null && theRight == null) {
			Integer leafBlackDepth = (Integer) properties.get("leaf-black-depth");
			if(leafBlackDepth == null) {
				properties.put("leaf-black-depth", leafBlackDepth);
			} else if(!leafBlackDepth.equals(blackDepth))
				throw new IllegalStateException("Different leaf black depths: " + leafBlackDepth + " and " + blackDepth);
		}
		if (theSize != size(getLeft()) + size(getRight()) + 1)
			throw new IllegalStateException("Size is incorrect: " + this);
	}

	/**
	 * @param node The node to get the size of
	 * @return The size of the given node, or 0 if the node is null
	 */
	public static final int size(RedBlackNode<?> node) {
		return node == null ? 0 : node.theSize;
	}

	/**
	 * A specialized and low-performance version of {@link #getRoot()} that avoids infinite loops which may occur if called on nodes in the
	 * middle of an operation. Mostly for debugging.
	 *
	 * @return The root of this tree structure, as far as can be reached from this node without a cycle
	 */
	public RedBlackNode<E> getRootNoCycles() {
		return getRootNoCycles(new java.util.LinkedHashSet<>());
	}

	private RedBlackNode<E> getRootNoCycles(java.util.Set<RedBlackNode<E>> visited) {
		if(theParent == null || !visited.add(this))
			return this;
		return theParent.getRootNoCycles(visited);
	}

	/** @return The child node that is on the left of this node */
	public RedBlackNode<E> getLeft() {
		return theLeft;
	}

	/** @return The child node that is on the right of this node */
	public RedBlackNode<E> getRight() {
		return theRight;
	}

	@Override
	public abstract int compareTo(RedBlackNode<E> node);

	/** @return Whether this node is on the right (false) or the left (true) of its parent. False for the root. */
	public boolean getSide() {
		if(theParent == null)
			return false;
		if(this == theParent.getLeft())
			return true;
		return false;
	}

	/**
	 * @param left Whether to get the left or right child
	 * @return The left or right child of this node
	 */
	public RedBlackNode<E> getChild(boolean left) {
		return left ? theLeft : theRight;
	}

	/** @return The other child of this node's parent. Null if the parent is null. */
	public RedBlackNode<E> getSibling() {
		if(theParent == null)
			return null;
		else if(theParent.getLeft() == this)
			return theParent.getRight();
		else
			return theParent.getLeft();
	}

	/**
	 * @param finder The compare operation to use to find the node. Must obey the ordering used to construct this structure.
	 * @return The node in this structure for which finder.compareTo(node)==0, or null if no such node exists.
	 */
	public RedBlackNode<E> find(Comparable<RedBlackNode<E>> finder) {
		int compare = finder.compareTo(this);
		if(compare == 0)
			return this;
		RedBlackNode<E> child = getChild(compare < 0);
		if(child != null)
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
	public RedBlackNode<E> findClosest(Comparable<RedBlackNode<E>> finder, boolean lesser, boolean withExact) {
		return findClosest(finder, lesser, withExact, null);
	}

	private RedBlackNode<E> findClosest(Comparable<RedBlackNode<E>> finder, boolean lesser, boolean withExact, RedBlackNode<E> found) {
		int compare = finder.compareTo(this);
		if(compare == 0) {
			if(withExact)
				return this;
			RedBlackNode<E> child = getChild(lesser);
			if(child == null)
				return found;
			while(child.getChild(!lesser) != null)
				child = child.getChild(!lesser);
			return child;
		}
		if(compare > 0 == lesser)
			found = this;
		RedBlackNode<E> child = getChild(compare < 0);
		if(child != null)
			return child.findClosest(finder, lesser, withExact, found);
		else
			return found;
	}

	/** @return The number of nodes in this structure (this node plus all its descendants) */
	public int getSize() {
		return theSize;
	}

	/**
	 * This should NEVER be called outside of the {@link CountedRedBlackNode<E>} class, but may be overridden by subclasses if the super is
	 * called.
	 *
	 * @param size The new size
	 */
	protected void setSize(int size) {
		theSize = size;
	}

	/** @return The number of nodes stored before this node in the tree */
	public int getIndex() {
		RedBlackNode<E> node = this;
		RedBlackNode<E> left = node.getLeft();
		int ret = size(left);
		while (node != null) {
			RedBlackNode<E> parent = node.getParent();
			if (parent != null && parent.getRight() == node) {
				left = parent.getLeft();
				ret += size(left) + 1;
			}
			node = parent;
		}
		return ret;
	}

	/** @return The number of nodes stored after this node in the tree */
	public int getElementsGreater() {
		RedBlackNode<E> node = this;
		RedBlackNode<E> right = node.getRight();
		int ret = size(right);
		while (node != null) {
			RedBlackNode<E> parent = node.getParent();
			if (parent != null && parent.getLeft() == node) {
				right = parent.getRight();
				ret += size(right) + 1;
			}
			node = parent;
		}
		return ret;
	}

	@Override
	public RedBlackNode<E> clone() {
		RedBlackNode<E> ret;
		try {
			ret = (RedBlackNode<E>) super.clone();
		} catch(CloneNotSupportedException e) {
			throw new IllegalStateException("Not cloneable");
		}
		if(ret.theLeft != null)
			ret.setChild(ret.theLeft.clone(), true);
		if(ret.theRight != null)
			ret.setChild(ret.theRight.clone(), false);
		return ret;
	}

	/**
	 * Sets this node's parent. This method should <b>NEVER</b> be called from outside of the {@link RedBlackNode} class.
	 *
	 * @param parent The parent for this node
	 * @return The node that was previously this node's parent, or null if it did not have a parent
	 */
	protected RedBlackNode<E> setParent(RedBlackNode<E> parent) {
		if(parent == this)
			throw new IllegalArgumentException("A tree node cannot be its own parent: " + parent);
		RedBlackNode<E> oldParent = theParent;
		theParent = parent;
		return oldParent;
	}

	/**
	 * Sets one of this node's children. This method should <b>NEVER</b> be called from outside of the {@link RedBlackNode} class.
	 *
	 * @param child The new child for this node
	 * @param left Whether to set the left or the right child
	 * @return The child (or null) that was replaced as this node's left or right child
	 */
	protected RedBlackNode<E> setChild(RedBlackNode<E> child, boolean left) {
		if(child == this)
			throw new IllegalArgumentException("A tree node cannot have itself as a child: " + this + " (" + (left ? "left" : "right")
					+ ")");
		RedBlackNode<E> oldChild;
		if(left) {
			oldChild = theLeft;
			theLeft = child;
		} else {
			oldChild = theRight;
			theRight = child;
		}
		if(child != null)
			child.setParent(this);

		int sizeDiff = size(child) - size(oldChild);
		adjustSize(sizeDiff);
		return oldChild;
	}

	/**
	 * Sets this node's color. This method should <b>NEVER</b> be called from outside of the {@link RedBlackNode} class unless this node is
	 * the root of the tree, but may be overridden by subclasses if the super is called with the argument.
	 *
	 * @param red Whether this node will be red or black
	 */
	protected void setRed(boolean red) {
		isRed = red;
	}

	/**
	 * Replaces this node in the tree with the given node, orphaning this node.
	 *
	 * @param node The node to replace this node in the tree
	 */
	protected void replace(RedBlackNode<E> node) {
		RedBlackNode<E> counted = node;
		RedBlackNode<E> parent = getParent();
		startCountTransaction();
		counted.startCountTransaction();
		if (parent != null)
			parent.startCountTransaction();
		try {
			if (node != theLeft)
				node.setChild(theLeft, true);
			if (node != theRight)
				node.setChild(theRight, false);
			setChild(null, true);
			setChild(null, false);
			node.setRed(isRed);
			if (theParent != null) {
				theParent.setChild(node, getSide());
				setParent(null);
			} else
				node.setParent(theParent);
		} finally {
			endCountTransaction();
			counted.endCountTransaction();
			if (parent != null)
				parent.endCountTransaction();
		}
	}

	/**
	 * Causes this node to switch places in the tree with the given node. This method should <b>NEVER</b> be called from outside of the
	 * {@link RedBlackNode} class, but may be overridden by subclasses if the super is called with the argument.
	 *
	 * @param node The node to switch places with
	 */
	protected void switchWith(RedBlackNode<E> node) {
		RedBlackNode<E> counted = node;
		RedBlackNode<E> parent = getParent();
		startCountTransaction();
		counted.startCountTransaction();
		if (parent != null)
			parent.startCountTransaction();
		try {
			boolean thisRed = isRed;
			setRed(node.isRed);
			node.setRed(thisRed);

			if (theParent == node) {
				boolean thisSide = getSide();
				RedBlackNode<E> sib = node.getChild(!thisSide);
				if (node.theParent != null)
					node.theParent.setChild(this, node.getSide());
				else
					setParent(null);
				node.setChild(theLeft, true);
				node.setChild(theRight, false);
				setChild(node, thisSide);
				setChild(sib, !thisSide);
			} else if (node.theParent == this) {
				boolean nodeSide = node.getSide();
				RedBlackNode<E> sib = getChild(!nodeSide);
				if (theParent != null)
					theParent.setChild(node, getSide());
				else
					node.setParent(null);
				setChild(node.theLeft, true);
				setChild(node.theRight, false);
				node.setChild(this, nodeSide);
				node.setChild(sib, !nodeSide);
			} else {
				boolean thisSide = getSide();
				RedBlackNode<E> temp = theParent;
				if (node.theParent != null)
					node.theParent.setChild(this, node.getSide());
				else
					setParent(null);
				if (temp != null)
					temp.setChild(node, thisSide);
				else
					node.setParent(null);

				temp = theLeft;
				setChild(node.theLeft, true);
				node.setChild(temp, true);
				temp = theRight;
				setChild(node.theRight, false);
				node.setChild(temp, false);
			}
		} finally {
			endCountTransaction();
			counted.endCountTransaction();
			if (parent != null)
				parent.endCountTransaction();
		}
	}

	/**
	 * Performs a rotation for balancing. This method should <b>NEVER</b> be called from outside of the {@link RedBlackNode} class, but may
	 * be overridden by subclasses if the super is called with the argument.
	 *
	 * @param left Whether to rotate left or right
	 * @return The new parent of this node
	 */
	protected RedBlackNode<E> rotate(boolean left) {
		RedBlackNode<E> countedChild = getChild(!left);
		RedBlackNode<E> parent = getParent();
		startCountTransaction();
		countedChild.startCountTransaction();
		if (parent != null)
			parent.startCountTransaction();
		try {
			RedBlackNode<E> oldChild = getChild(!left);
			RedBlackNode<E> newChild = oldChild.getChild(left);
			RedBlackNode<E> oldParent = getParent();
			boolean oldSide = getSide();
			oldChild.setChild(this, left);
			setChild(newChild, !left);
			if (oldParent != null)
				oldParent.setChild(oldChild, oldSide);
			else
				oldChild.setParent(null);
			return oldChild;
		} finally {
			endCountTransaction();
			countedChild.endCountTransaction();
			if (parent != null)
				parent.endCountTransaction();
		}
	}

	/**
	 * Adds a new node into the correct place in this structure, rebalancing if necessary
	 *
	 * @param node The node to add
	 * @param replaceIfFound Whether to replace an equivalent node if found, or leave the tree as-is
	 * @return The result of the addition
	 */
	protected TreeOpResult add(RedBlackNode<E> node, boolean replaceIfFound) {
		int compare = node.compareTo(this);
		if(compare == 0) {
			if(replaceIfFound) {
				replace(node);
				return new TreeOpResult(this, node, node);
			} else
				return new TreeOpResult(this, this, this);
		}
		return addOnSide(node, compare < 0, replaceIfFound);
	}

	/** A new implementation of balancing, which causes errors if not called from {@link #add(RedBlackNode, boolean)} on the root. */
	private static final boolean SPECIAL_BALANCING = false;

	static boolean DEBUG_PRINT = false;

	/**
	 * Adds a new node into this structure, rebalancing if necessary
	 *
	 * @param node The node to add
	 * @param left The side on which to place the node
	 * @param replaceIfFound Whether to replace an equivalent node if found, or leave the tree as-is
	 * @return The result of the addition
	 */
	protected TreeOpResult addOnSide(RedBlackNode<E> node, boolean left, boolean replaceIfFound) {
		RedBlackNode<E> child = getChild(left);
		TreeOpResult r;
		if(child == null) {
			setChild(node, left);
			child = node;
			r = new TreeOpResult(null, node, this);
		} else {
			r = child.add(node, replaceIfFound);
			if(r.theNewRoot == theParent) {
				return r;
			}
			r.theNewRoot = this;
		}

		if(SPECIAL_BALANCING) {
			// Rebalance after add
			if(theParent == null) {
				isRed = false; // Root is black
				return r;
			} else if(!isRed || !child.isRed) {
				// We're good, nothing to do
				return r;
			} else {
				boolean parentLeft = getSide();
				RedBlackNode<E> uncle = getSibling(); // Get the other sibling
				if(uncle != null && uncle.isRed) {
					// Switch colors of this node, its parent, and the uncle
					setRed(false);
					uncle.setRed(false);
					theParent.setRed(true);
					return r;
				} else {
					RedBlackNode<E> ret = this;
					if(parentLeft != left) { // New node on right
						ret = rotate(parentLeft);
					}
					ret.setRed(false);
					ret.getParent().setRed(true);
					r.theNewRoot = ret.theParent.rotate(!parentLeft);
					return r;
				}
			}
		} else {
			r.theNewRoot = fixAfterInsertion(this);
			return r;
		}
	}

	/**
	 * Removes this node (but not its children) from its structure, rebalancing if necessary
	 *
	 * @return The new root of this node's structure
	 */
	protected RedBlackNode<E> delete() {
		if(theLeft != null && theRight != null) {
			RedBlackNode<E> successor = getClosest(false);
			switchWith(successor);
			// Now we've switched locations with successor, so we have either 0 or 1 children and can continue with delete
		}
		RedBlackNode<E> replacement = null;
		if(theLeft != null)
			replacement = theLeft;
		else if(theRight != null)
			replacement = theRight;

		if(replacement != null) {
			boolean oldRed = replacement.isRed;
			if(theParent != null)
				theParent.setChild(replacement, getSide());
			else
				replacement.setParent(null);
			replacement.setRed(oldRed);
			setParent(null);
			setChild(null, true);
			setChild(null, false);
			if(!isRed)
				return fixAfterDeletion(replacement);
			else
				return replacement.getRoot();
		} else if(theParent == null)
			return null;
		else {
			RedBlackNode<E> ret = fixAfterDeletion(this);
			theParent.setChild(null, getSide());
			setParent(null);
			return ret;
		}
	}

	private void adjustSize(int diff) {
		if (diff != 0) {
			if (isTransaction) {
				theTransaction += diff;
			} else {
				setSize(theSize + diff);
				RedBlackNode<E> parent = getParent();
				if (parent != null)
					parent.adjustSize(diff);
			}
		}
	}

	private void startCountTransaction() {
		isTransaction = true;
	}

	private void endCountTransaction() {
		isTransaction = false;
		int trans = theTransaction;
		theTransaction = 0;
		adjustSize(trans);
	}

	/**
	 * @param left Whether to get the closest node on the left or right
	 * @return The closest (in value) node to this node on one side or the other
	 */
	public RedBlackNode<E> getClosest(boolean left) {
		RedBlackNode<E> child = getChild(left);
		if(child != null) {
			RedBlackNode<E> next = child.getChild(!left);
			while(next != null) {
				child = next;
				next = next.getChild(!left);
			}
			return child;
		} else {
			RedBlackNode<E> parent = theParent;
			child = this;
			while(parent != null && parent.getChild(left) == child) {
				child = parent;
				parent = parent.theParent;
			}
			return parent;
		}
	}

	@Override
	public String toString() {
		String ret = String.valueOf(theValue) + " (" + (isRed() ? "red" : "black") + ")";
		if (getParent() != null && getParent().getParent() == this)
			return ret;
		ret = ret.substring(0, ret.length() - 1);
		ret += ", " + theSize + ") [" + getIndex() + "]";
		return ret;
	}

	/** This is not used, but here for reference. It is the rebalancing code from {@link java.util.TreeMap}, refactored for RedBlackTree. */
	private static <E> RedBlackNode<E> fixAfterInsertion(RedBlackNode<E> x) {
		while(x != null && isRed(x.theParent) && x.getParent().getParent() != null) {
			boolean parentLeft = x.getParent().getSide();
			RedBlackNode<E> y = x.getParent().getSibling();
			if(isRed(y)) {
				if(DEBUG_PRINT)
					System.out.println("Case 1");
				setRed(x.getParent(), false);
				setRed(y, false);
				setRed(x.getParent().getParent(), true);
				x = x.getParent().getParent();
			} else {
				if(parentLeft != x.getSide()) {
					if(DEBUG_PRINT)
						System.out.println("Case 2, rotate " + (parentLeft ? "left" : "right"));
					x = x.getParent();
					x.rotate(parentLeft);
				}
				if(DEBUG_PRINT)
					System.out.println("Case 3, rotate " + (parentLeft ? "right" : "left"));
				setRed(x.getParent(), false);
				setRed(x.getParent().getParent(), true);
				x.getParent().getParent().rotate(!parentLeft);
			}
		}
		setRed(x.getRoot(), false);
		return x.getRoot();
	}

	private static <E> RedBlackNode<E> fixAfterDeletion(RedBlackNode<E> node) {
		while(node.theParent != null && !isRed(node)) {
			boolean parentLeft = node.getSide();
			RedBlackNode<E> sib = node.theParent.getChild(!parentLeft);

			if(isRed(sib)) {
				setRed(sib, false);
				setRed(node.theParent, true);
				node.theParent.rotate(parentLeft);
				sib = node.theParent.getChild(!parentLeft);
			}
			if(sib == null || !isRed(sib.theLeft) && !isRed(sib.theRight)) {
				setRed(sib, true);
				node = node.theParent;
			} else {
				if(!isRed(sib.getChild(!parentLeft))) {
					setRed(sib.getChild(parentLeft), false);
					setRed(sib, true);
					sib.rotate(!parentLeft);
					sib = node.theParent.getChild(!parentLeft);
				}
				setRed(sib, isRed(node.theParent));
				setRed(node.theParent, false);
				setRed(sib.getChild(!parentLeft), false);
				node.theParent.rotate(parentLeft);
				node = node.getRoot();
			}
		}

		setRed(node, false);
		return node.getRoot();
	}

	private static boolean isRed(RedBlackNode<?> node) {
		return node != null && node.isRed;
	}

	private static void setRed(RedBlackNode<?> node, boolean red) {
		if(node != null)
			node.setRed(red);
	}

	/**
	 * Prints a tree in a way that indicates the position of each node in the tree
	 *
	 * @param tree The tree node to print
	 * @return The printed representation of the node
	 */
	public static String print(RedBlackNode<?> tree) {
		StringBuilder ret = new StringBuilder();
		print(tree, ret, 0);
		return ret.toString();
	}

	/**
	 * Prints a tree in a way that indicates the position of each node in the tree
	 *
	 * @param tree The tree node to print
	 * @param str The string builder to append the printed tree representation to
	 * @param indent The amount of indentation with which to indent the root of the tree
	 */
	public static void print(RedBlackNode<?> tree, StringBuilder str, int indent) {
		if(tree == null) {
			for(int i = 0; i < indent; i++)
				str.append('\t');
			str.append(tree).append('\n');
			return;
		}

		RedBlackNode<?> right = tree.getRight();
		if(right != null)
			print(right, str, indent + 1);

		for(int i = 0; i < indent; i++)
			str.append('\t');
		str.append(tree).append('\n');

		RedBlackNode<?> left = tree.getLeft();
		if(left != null)
			print(left, str, indent + 1);
	}

	/**
	 * A subclass of {@link RedBlackNode<E>} that contains a value, which is (typically) the key by which nodes are compared
	 *
	 * @param <E> The type of value contained in each node
	 */
	public static abstract class ValuedRedBlackNode<E> extends RedBlackNode {
		private final E theValue;

		/** @param value The value for the node */
		public ValuedRedBlackNode(E value) {
			theValue = value;
		}

		/** @return This node's value */
		@Override
		public E getValue() {
			return theValue;
		}

		/**
		 * @param o1 One of the values to compare
		 * @param o2 The other value to compare
		 * @return -1 if o1&lt;o2, 1 if o1&gt;o2, or 0 if the two are logically equal
		 */
		protected abstract int compare(E o1, E o2);

		/**
		 * @param value The value to create the node for
		 * @return The new node for the given value
		 */
		protected abstract ValuedRedBlackNode<E> createNode(E value);

		@Override
		public int compareTo(RedBlackNode node) {
			return compare(getValue(), ((ValuedRedBlackNode<E>) node).getValue());
		}

		/**
		 * Adds a value to this tree
		 *
		 * @param value The value to add
		 * @param replaceIfFound If another equivalent value is found in this tree, whether to replace it with the given value or leave it
		 *            there
		 * @return The tree result of the addition
		 */
		protected TreeOpResult add(E value, boolean replaceIfFound) {
			ValuedRedBlackNode<E> node = createNode(value);
			return add(node, replaceIfFound);
		}

		/**
		 * @param value The value to get the node for
		 * @return The node with the given value (or equivalent) in this tree
		 */
		public ValuedRedBlackNode<E> findValue(E value) {
			return (ValuedRedBlackNode<E>) find(node -> compare(value, ((ValuedRedBlackNode<E>) node).getValue()));
		}

		/**
		 * Finds the given value in the tree or the closest value to one side
		 *
		 * @param value The value to search for
		 * @param lesser Whether to search lesser values or greater
		 * @param withEqual Whether to accept the equivalent of the given value (as opposed to strictly less or greater than)
		 * @return The node in the tree matching the given constraints
		 */
		public ValuedRedBlackNode<E> findClosestValue(E value, boolean lesser, boolean withEqual) {
			return (ValuedRedBlackNode<E>) findClosest(node -> compare(value, ((ValuedRedBlackNode<E>) node).getValue()), lesser, withEqual);
		}

		@Override
		public String toString() {
			return String.valueOf(theValue) + " (" + (isRed() ? "red" : "black") + ")";
		}
	}

	/**
	 * A value node for comparable values
	 *
	 * @param <E> The type of comparable value for the node
	 */
	public static class ComparableValuedRedBlackNode<E extends Comparable<E>> extends ValuedRedBlackNode<E> {
		/** @param value The value for the node */
		public ComparableValuedRedBlackNode(E value) {
			super(value);
		}

		@Override
		public int compareTo(RedBlackNode o) {
			return getValue().compareTo(((ComparableValuedRedBlackNode<E>) o).getValue());
		}

		@Override
		protected ComparableValuedRedBlackNode<E> createNode(E value) {
			return new ComparableValuedRedBlackNode<>(value);
		}

		@Override
		protected int compare(E o1, E o2) {
			return o1.compareTo(o2);
		}

		/**
		 * @param <E> The type of the value
		 * @param value The value for the new node
		 * @return The new node for the given value
		 */
		public static <E extends Comparable<E>> ComparableValuedRedBlackNode<E> valueOf(E value) {
			return new ComparableValuedRedBlackNode<>(value);
		}
	}
}

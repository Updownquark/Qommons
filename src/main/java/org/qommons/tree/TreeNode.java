package org.qommons.tree;

import java.util.Collection;

/**
 * @param <N> The sub-type of the node
 * @param <V> The type of value stored by the node
 */
public interface TreeNode<N extends TreeNode<N, V>, V> {
	/** @return This node's value */
	V getValue();

	/** @return This node's parent node */
	N getParent();

	/** @return All of this node's child nodes */
	Collection<N> getChildren();
}

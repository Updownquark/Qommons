package org.qommons.tree;

import java.util.Collection;

public interface TreeNode<N extends TreeNode<N, V>, V> {
	V getValue();

	N getParent();

	Collection<N> getChildren();
}

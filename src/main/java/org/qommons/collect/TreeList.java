package org.qommons.collect;

import org.qommons.tree.CountedRedBlackNode;
import org.qommons.tree.RedBlackTreeList;

public class TreeList<E> extends RedBlackTreeList<CountedRedBlackNode.DefaultNode<E>, E> {
	public TreeList() {
		super(v -> new CountedRedBlackNode.DefaultNode<>(v, null));
	}
}

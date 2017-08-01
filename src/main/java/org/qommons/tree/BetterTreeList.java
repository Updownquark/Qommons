package org.qommons.tree;

import java.util.Objects;

import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.MutableElementSpliterator;
import org.qommons.collect.StampedLockingStrategy;

public class BetterTreeList<E> extends RedBlackNodeList<E> {
	public BetterTreeList(boolean safe) {
		this(safe ? new StampedLockingStrategy() : new FastFailLockingStrategy());
	}

	public BetterTreeList(CollectionLockingStrategy locking) {
		super(locking);
	}

	// The structure doesn't mean anything except order, so if someone wants to mess with it, they can go right ahead

	@Override
	public MutableBinaryTreeNode<E> mutableNodeFor(ElementId node) {
		return super.mutableNodeFor(node);
	}

	@Override
	public MutableBinaryTreeNode<E> mutableNodeFor(BinaryTreeNode<E> node) {
		return super.mutableNodeFor(node);
	}

	@Override
	public MutableElementSpliterator<E> mutableSpliterator(BinaryTreeNode<E> node, boolean next) {
		return super.mutableSpliterator(node, next);
	}

	@Override
	public CollectionElement<E> getElement(E value, boolean first) {
		try (Transaction t = lock(false, null)) {
			CollectionElement<E>[] element = new CollectionElement[1];
			ElementSpliterator<E> spliter = first ? spliterator(first) : spliterator(first).reverse();
			while (element[0] == null && spliter.onElement(el -> {
				if (Objects.equals(el.get(), value))
					element[0] = first ? el : el.reverse();
			}, true)) {
			}
			return element[0];
		}
	}
}

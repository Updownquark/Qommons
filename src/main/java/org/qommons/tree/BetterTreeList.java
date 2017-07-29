package org.qommons.tree;

import java.util.Objects;
import java.util.function.Consumer;

import org.qommons.Transaction;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.MutableCollectionElement;
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
	public MutableBinaryTreeNode<E> mutableNodeFor(BinaryTreeNode<E> node) {
		return super.mutableNodeFor(node);
	}

	@Override
	public MutableElementSpliterator<E> mutableSpliterator(BinaryTreeNode<E> node, boolean next) {
		return super.mutableSpliterator(node, next);
	}

	@Override
	public boolean forElement(E value, Consumer<? super CollectionElement<? extends E>> onElement, boolean first) {
		boolean[] success = new boolean[1];
		try (Transaction t = lock(false, null)) {
			ElementSpliterator<E> spliter = first ? spliterator(first) : spliterator(first).reverse();
			while (!success[0] && spliter.tryAdvanceElement(el -> {
				if (Objects.equals(el.get(), value)) {
					success[0] = true;
					onElement.accept(el);
				}
			})) {
			}
		}
		return success[0];
	}

	@Override
	public boolean forMutableElement(E value, Consumer<? super MutableCollectionElement<? extends E>> onElement, boolean first) {
		boolean[] success = new boolean[1];
		try (Transaction t = lock(true, null)) {
			MutableElementSpliterator<E> spliter = first ? mutableSpliterator(first) : mutableSpliterator(first).reverse();
			while (!success[0] && spliter.tryAdvanceElementM(el -> {
				if (Objects.equals(el.get(), value)) {
					success[0] = true;
					onElement.accept(el);
				}
			})) {
			}
		}
		return success[0];
	}
}

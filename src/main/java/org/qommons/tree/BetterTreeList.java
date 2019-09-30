package org.qommons.tree;

import java.util.Collection;
import java.util.Objects;

import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.StampedLockingStrategy;

/**
 * <p>
 * A {@link org.qommons.collect.BetterList} implementation backed by a tree structure.
 * </p>
 * 
 * <p>
 * This class follows the contract of {@link java.util.List}, allowing values in arbitrary order, managed entirely by the caller.
 * </p>
 * 
 * <p>
 * The performace characteristics of this class are that of a binary tree, namely O(log(n)) for {@link #add(Object) additions},
 * {@link #add(int, Object) inserts}, {@link #remove(int) removals}, and {@link #get(int) seeks}. Since this list lacks order, normal
 * {@link #indexOf(Object) searches} are linear time.
 * </p>
 * 
 * @param <E> The type of values in the list
 */
public class BetterTreeList<E> extends RedBlackNodeList<E> {
	/** @param safe Whether to secure this collection for thread-safety */
	public BetterTreeList(boolean safe) {
		this(safe ? new StampedLockingStrategy() : new FastFailLockingStrategy());
	}

	/** @param locker The locking strategy for the collection */
	public BetterTreeList(CollectionLockingStrategy locker) {
		super(locker);
	}

	@Override
	public boolean isContentControlled() {
		return false;
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
	public BinaryTreeNode<E> getElement(E value, boolean first) {
		try (Transaction t = lock(false, null)) {
			ValueHolder<CollectionElement<E>> element = new ValueHolder<>();
			ElementSpliterator<E> spliter = first ? spliterator(first) : spliterator(first).reverse();
			while (!element.isPresent() && spliter.forElement(el -> {
				if (Objects.equals(el.get(), value))
					element.accept(first ? el : el.reverse());
			}, true)) {}
			return (BinaryTreeNode<E>) element.get();
		}
	}

	@Override
	public BetterTreeList<E> withAll(Collection<? extends E> values) {
		super.withAll(values);
		return this;
	}
}

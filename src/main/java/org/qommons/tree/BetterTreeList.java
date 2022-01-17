package org.qommons.tree;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;

import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.collect.ValueStoredCollection.RepairListener;

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
 * The performance characteristics of this class are that of a binary tree, namely O(log(n)) for {@link #add(Object) additions},
 * {@link #add(int, Object) inserts}, {@link #remove(int) removals}, and {@link #get(int) seeks}. Since this list lacks order, normal
 * {@link #indexOf(Object) searches} are linear time.
 * </p>
 * 
 * @param <E> The type of values in the list
 */
public class BetterTreeList<E> extends RedBlackNodeList<E> {
	private static final String DEFAULT_DESCRIPTION = "better-tree-list";

	/**
	 * @param <E> The type of elements for the list
	 * @param <B> The sub-type of this builder
	 */
	public static class Builder<E, B extends Builder<E, ? extends B>> extends RBNLBuilder<E, BetterTreeList<E>, B> {
		Builder() {
			super(DEFAULT_DESCRIPTION);
		}

		@Override
		public BetterTreeList<E> build() {
			return new BetterTreeList<>(getLocker(), getDescription());
		}
	}

	/**
	 * @param <E> The type of elements for the list
	 * @return A builder for the new list
	 */
	public static <E> Builder<E, ?> build() {
		return new Builder<>();
	}

	/** @param safe Whether to secure this collection for thread-safety */
	public BetterTreeList(boolean safe) {
		this(v -> safe ? new StampedLockingStrategy(v) : new FastFailLockingStrategy(), DEFAULT_DESCRIPTION);
	}

	/**
	 * @param locker The locker for this list
	 * @param description The description for this list
	 */
	protected BetterTreeList(Function<Object, CollectionLockingStrategy> locker, String description) {
		super(locker, description);
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
			BinaryTreeNode<E> el = getTerminalElement(first);
			while (el != null) {
				if (Objects.equals(el.get(), value))
					return el;
				el = getAdjacentElement(el.getElementId(), first);
			}
			return null;
		}
	}

	@Override
	public String canMove(ElementId valueEl, ElementId after, ElementId before) {
		return null;
	}

	@Override
	public CollectionElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
		throws UnsupportedOperationException, IllegalArgumentException {
		if (first) {
			if (valueEl.equals(after) || Objects.equals(CollectionElement.getElementId(getAdjacentElement(valueEl, false)), after))
				return getElement(valueEl);
		} else {
			if (valueEl.equals(before) || Objects.equals(CollectionElement.getElementId(getAdjacentElement(valueEl, true)), before))
				return getElement(valueEl);
		}
		MutableCollectionElement<E> el = mutableElement(valueEl);
		E value = el.get();
		el.remove();
		if (afterRemove != null)
			afterRemove.run();
		return addElement(value, after, before, first);
	}

	@Override
	public BetterTreeList<E> withAll(Collection<? extends E> values) {
		super.withAll(values);
		return this;
	}

	@Override
	public boolean isConsistent(ElementId element, Comparator<? super E> compare, boolean distinct) {
		return super.isConsistent(element, compare, distinct);
	}

	@Override
	public <X> boolean repair(ElementId element, Comparator<? super E> compare, boolean distinct, RepairListener<E, X> listener) {
		return super.repair(element, compare, distinct, listener);
	}

	@Override
	public boolean checkConsistency(Comparator<? super E> compare, boolean distinct) {
		return super.checkConsistency(compare, distinct);
	}

	@Override
	public <X> boolean repair(Comparator<? super E> compare, boolean distinct, RepairListener<E, X> listener) {
		return super.repair(compare, distinct, listener);
	}
}

package org.qommons.tree;

import java.util.Collection;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.function.Function;

import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.StampedLockingStrategy;

/**
 * A {@link BetterSortedSet} backed by a tree structure.
 * 
 * @param <E> The type of values in the set
 */
public class BetterTreeSet<E> extends SortedTreeList<E> implements TreeBasedSet<E> {
	private static final String DEFAULT_DESCRIPTION = "better-tree-set";

	/**
	 * @param <E> The type of elements for the set
	 * @param <L> The sub-type of set
	 * @param <B> The sub-type of this builder
	 */
	public static class Builder<E, L extends BetterTreeSet<E>, B extends Builder<E, L, ? extends B>>
		extends SortedTreeList.Builder<E, L, B> {
		/** @param compare The comparator for the new set */
		protected Builder(Comparator<? super E> compare) {
			super(compare);
			withDescription(DEFAULT_DESCRIPTION);
		}

		@Override
		public L build() {
			return (L) new BetterTreeSet<>(getLocker(), getDescription(), getCompare());
		}
	}

	/**
	 * @param <E> The type of elements for the set
	 * @param compare The comparator for the set's ordering
	 * @return A builder for the set
	 */
	public static <E> Builder<E, BetterTreeSet<E>, ?> buildTreeSet(Comparator<? super E> compare) {
		return new Builder<>(compare);
	}

	/**
	 * @param safe Whether the set should be thread-safe or fail-fast
	 * @param compare The comparator to order the set's values
	 */
	public BetterTreeSet(boolean safe, Comparator<? super E> compare) {
		this(v -> safe ? new StampedLockingStrategy(v) : new FastFailLockingStrategy(), DEFAULT_DESCRIPTION, compare);
	}

	/**
	 * @param safe Whether the set should be thread-safe
	 * @param values The initial values for the set
	 */
	public BetterTreeSet(boolean safe, SortedSet<E> values) {
		this(v -> safe ? new StampedLockingStrategy(v) : new FastFailLockingStrategy(), DEFAULT_DESCRIPTION, values.comparator());
		initialize(values, v -> v);
	}

	/**
	 * @param locker The locking strategy for the set
	 * @param values The initial values for the set
	 */
	public BetterTreeSet(Function<Object, CollectionLockingStrategy> locker, SortedSet<E> values) {
		this(locker, DEFAULT_DESCRIPTION, values.comparator());
		initialize(values, v -> v);
	}

	/**
	 * @param locker The locking strategy for the set
	 * @param descrip A description of the set
	 * @param compare The value sorting for the set
	 */
	protected BetterTreeSet(Function<Object, CollectionLockingStrategy> locker, String descrip, Comparator<? super E> compare) {
		super(locker, descrip, compare);
	}

	/**
	 * @param locker The locking strategy for the set
	 * @param identity The identity for the set
	 * @param compare The value sorting for the set
	 */
	protected BetterTreeSet(Function<Object, CollectionLockingStrategy> locker, Object identity, Comparator<? super E> compare) {
		super(locker, identity, compare);
	}

	@Override
	public BetterTreeSet<E> withAll(Collection<? extends E> values) {
		super.withAll(values);
		return this;
	}

	@Override
	public ReversedTreeSet<E> reverse() {
		return new ReversedTreeSet<>(this);
	}

	@Override
	public SortedSetSplitSpliterable<E> subList(int fromIndex, int toIndex) {
		return TreeBasedSet.super.subList(fromIndex, toIndex);
	}

	/**
	 * Implements {@link BetterTreeSet#reverse()}
	 * 
	 * @param <E> The type of values in the set
	 */
	public static class ReversedTreeSet<E> extends SortedTreeList.ReversedSortedTreeList<E> implements TreeBasedSet<E> {
		/** @param wrap The set to reverse */
		public ReversedTreeSet(BetterTreeSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected BetterTreeSet<E> getWrapped() {
			return (BetterTreeSet<E>) super.getWrapped();
		}

		@Override
		public BinaryTreeNode<E> getOrAdd(E value, ElementId after, ElementId before, boolean first, Runnable added) {
			return BinaryTreeNode
				.reverse(getWrapped().getOrAdd(value, ElementId.reverse(before), ElementId.reverse(before), !first, added));
		}

		@Override
		public BetterTreeSet<E> reverse() {
			return getWrapped();
		}

		@Override
		public SortedSetSplitSpliterable<E> subList(int fromIndex, int toIndex) {
			return TreeBasedSet.super.subList(fromIndex, toIndex);
		}
	}
}

package org.qommons.tree;

import java.util.Collection;
import java.util.Comparator;
import java.util.SortedSet;

import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.RRWLockingStrategy;

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
	 */
	public static class Builder<E, L extends BetterTreeSet<E>> extends SortedTreeList.Builder<E, L> {
		/** @param compare The comparator for the new set */
		protected Builder(Comparator<? super E> compare) {
			super(compare);
			withDescription(DEFAULT_DESCRIPTION);
		}

		@Override
		public Builder<E, L> withDescription(String descrip) {
			super.withDescription(descrip);
			return this;
		}

		@Override
		public Builder<E, L> safe(boolean safe) {
			super.safe(safe);
			return this;
		}

		@Override
		public Builder<E, L> withLocker(CollectionLockingStrategy locker) {
			super.withLocker(locker);
			return this;
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
	public static <E> Builder<E, BetterTreeSet<E>> buildTreeSet(Comparator<? super E> compare) {
		return new Builder<>(compare);
	}

	/**
	 * @param safe Whether the set should be thread-safe or fail-fast
	 * @param compare The comparator to order the set's values
	 */
	public BetterTreeSet(boolean safe, Comparator<? super E> compare) {
		this(safe ? new RRWLockingStrategy() : new FastFailLockingStrategy(), DEFAULT_DESCRIPTION, compare);
	}

	/**
	 * @param locker The locking strategy for the set
	 * @param compare The comparator to order the set's values
	 */
	public BetterTreeSet(CollectionLockingStrategy locker, Comparator<? super E> compare) {
		this(locker, DEFAULT_DESCRIPTION, compare);
	}

	public BetterTreeSet(boolean safe, SortedSet<E> values) {
		this(safe ? new RRWLockingStrategy() : new FastFailLockingStrategy(), DEFAULT_DESCRIPTION, values.comparator());
		initialize(values, v -> v);
	}

	public BetterTreeSet(CollectionLockingStrategy locker, SortedSet<E> values) {
		this(locker, DEFAULT_DESCRIPTION, values.comparator());
		initialize(values, v -> v);
	}

	protected BetterTreeSet(CollectionLockingStrategy locker, String descrip, Comparator<? super E> compare) {
		super(locker, descrip, compare);
	}

	protected BetterTreeSet(CollectionLockingStrategy locker, Object identity, Comparator<? super E> compare) {
		super(locker, identity, compare);
	}

	@Override
	public BetterTreeSet<E> withAll(Collection<? extends E> values) {
		super.withAll(values);
		return this;
	}
}

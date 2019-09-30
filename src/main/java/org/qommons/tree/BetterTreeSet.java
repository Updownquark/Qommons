package org.qommons.tree;

import java.util.Collection;
import java.util.Comparator;
import java.util.SortedSet;

import org.qommons.collect.CollectionLockingStrategy;

/**
 * A {@link BetterSortedSet} backed by a tree structure.
 * 
 * @param <E> The type of values in the set
 */
public class BetterTreeSet<E> extends SortedTreeList<E> implements TreeBasedSet<E> {
	/**
	 * @param safe Whether the set should be thread-safe or fail-fast
	 * @param compare The comparator to order the set's values
	 */
	public BetterTreeSet(boolean safe, Comparator<? super E> compare) {
		super(safe, compare);
	}

	/**
	 * @param locker The locking strategy for the set
	 * @param compare The comparator to order the set's values
	 */
	public BetterTreeSet(CollectionLockingStrategy locker, Comparator<? super E> compare) {
		super(locker, compare);
	}

	public BetterTreeSet(boolean safe, SortedSet<E> values) {
		super(safe, values);
	}

	public BetterTreeSet(CollectionLockingStrategy locker, SortedSet<E> values) {
		super(locker, values);
	}

	@Override
	public BetterTreeSet<E> withAll(Collection<? extends E> values) {
		super.withAll(values);
		return this;
	}
}

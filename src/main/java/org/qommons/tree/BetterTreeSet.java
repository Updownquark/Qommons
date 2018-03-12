package org.qommons.tree;

import java.util.Comparator;

import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionLockingStrategy;

/**
 * A {@link BetterSortedSet} backed by a tree structure.
 * 
 * @param <E> The type of values in the set
 */
public class BetterTreeSet<E> extends SortedTreeList<E> implements BetterSortedSet<E> {
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
}

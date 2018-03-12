package org.qommons.tree;

import java.util.Comparator;

import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionLockingStrategy;

public class BetterTreeSet<E> extends SortedTreeList<E> implements BetterSortedSet<E> {
	public BetterTreeSet(boolean safe, Comparator<? super E> compare) {
		super(safe, compare);
	}

	public BetterTreeSet(CollectionLockingStrategy locker, Comparator<? super E> compare) {
		super(locker, compare);
	}
}

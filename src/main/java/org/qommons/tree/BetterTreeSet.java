package org.qommons.tree;

import java.util.Collection;
import java.util.Comparator;
import java.util.SortedSet;

import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionLockingStrategy;

public class BetterTreeSet<E> extends SortedTreeList<E> implements BetterSortedSet<E> {
	public BetterTreeSet(boolean safe, Comparator<? super E> compare) {
		super(safe, compare);
	}

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

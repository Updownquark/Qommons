package org.qommons.collect;

import java.util.Comparator;
import java.util.function.Function;

public class MappedBetterSortedSet<S, T> extends MappedBetterList<S, T> implements BetterSortedSet<T> {
	private final Comparator<? super T> theSorting;

	public MappedBetterSortedSet(BetterSortedSet<S> wrapped, Function<? super S, T> map, Function<? super T, ? extends S> reverse,
		Comparator<? super T> sorting) {
		super(wrapped, map, reverse);
		theSorting = sorting;
	}

	@Override
	protected BetterSortedSet<S> getSource() {
		return (BetterSortedSet<S>) super.getSource();
	}

	@Override
	public Comparator<? super T> comparator() {
		return theSorting;
	}

	@Override
	public ListElement<T> search(Comparable<? super T> search, SortedSearchFilter filter) {
		return map(getSource().search(v -> search.compareTo(getMap().apply(v)), filter), getMap());
	}

	@Override
	public int indexFor(Comparable<? super T> search) {
		return getSource().indexFor(v -> search.compareTo(getMap().apply(v)));
	}

	@Override
	public boolean isConsistent(ElementId element) {
		return getSource().isConsistent(element);
	}

	@Override
	public boolean checkConsistency() {
		return getSource().checkConsistency();
	}

	@Override
	public <X> boolean repair(ElementId element, RepairListener<T, X> listener) {
		return getSource().repair(element, listener == null ? null : new MappedRepairListener<>(listener, getMap()));
	}

	@Override
	public <X> boolean repair(RepairListener<T, X> listener) {
		return getSource().repair(listener == null ? null : new MappedRepairListener<>(listener, getMap()));
	}

	static class MappedRepairListener<S, T, X> implements RepairListener<S, X> {
		private final RepairListener<T, X> theSourceListener;
		private final Function<? super S, ? extends T> theMap;

		MappedRepairListener(RepairListener<T, X> sourceListener, Function<? super S, ? extends T> map) {
			theSourceListener = sourceListener;
			theMap = map;
		}

		@Override
		public X removed(CollectionElement<S> element) {
			return theSourceListener.removed(map((ListElement<S>) element, theMap));
		}

		@Override
		public void disposed(S value, X data) {
			theSourceListener.disposed(theMap.apply(value), data);
		}

		@Override
		public void transferred(CollectionElement<S> element, X data) {
			theSourceListener.transferred(map((ListElement<S>) element, theMap), data);
		}
	}
}

package org.qommons.collect;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;

/**
 * A {@link BetterSortedSet} that is the result of a mapping function applied to a different {@link BetterSortedSet}
 * 
 * @param <S> The type of the source set
 * @param <T> The type of this set
 */
public class MappedBetterSortedList<S, T> extends MappedBetterList<S, T> implements BetterSortedList<T> {
	public static class MappedComparator<S, T> implements Comparator<T> {
		private final Comparator<? super S> theSource;
		private final Function<? super T, ? extends S> theReverse;

		public MappedComparator(Comparator<? super S> source, Function<? super T, ? extends S> reverse) {
			theSource = source;
			theReverse = reverse;
		}

		@Override
		public int compare(T o1, T o2) {
			return theSource.compare(//
				theReverse.apply(o1), //
				theReverse.apply(o2));
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, theReverse);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof MappedComparator))
				return false;
			MappedComparator<?, ?> other = (MappedComparator<?, ?>) obj;
			return theSource.equals(other.theSource) && theReverse.equals(other.theReverse);
		}

		@Override
		public String toString() {
			return theSource + ".map(" + theReverse + ")";
		}
	}

	private final Comparator<? super T> theSorting;

	/**
	 * @param source The source set to wrap
	 * @param map The mapping function to produce values of this set from values of the source set
	 * @param reverse The optional mapping function to produce values of the source set from values in this set. Providing this enables the
	 *        {@link #getElement(Object, boolean)} method and other features that use it, which will otherwise fail (with nulls or false
	 *        booleans).
	 * @param sorting The sorting for values in this set
	 */
	public MappedBetterSortedList(BetterSortedList<S> source, Function<? super S, T> map, Function<? super T, ? extends S> reverse,
		Comparator<? super T> sorting) {
		super(source, map, reverse);
		if (sorting != null)
			theSorting = sorting;
		else if (reverse != null)
			theSorting = new MappedComparator<>(source.comparator(), reverse);
		else
			throw new IllegalArgumentException("Either a comparator or a reverse function must be provided");
	}

	@Override
	protected BetterSortedList<S> getSource() {
		return (BetterSortedList<S>) super.getSource();
	}

	@Override
	public Comparator<? super T> comparator() {
		return theSorting;
	}

	@Override
	public ListElement<T> search(Comparable<? super T> search, SortedSearchFilter filter) {
		return wrap(getSource().search(v -> search.compareTo(getMap().apply(v)), filter));
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

	class MappedRepairListener<X> implements RepairListener<S, X> {
		private final RepairListener<T, X> theSourceListener;
		private final Function<? super S, ? extends T> theMap;

		MappedRepairListener(RepairListener<T, X> sourceListener, Function<? super S, ? extends T> map) {
			theSourceListener = sourceListener;
			theMap = map;
		}

		@Override
		public X removed(CollectionElement<S> element) {
			return theSourceListener.removed(wrap(element));
		}

		@Override
		public void disposed(S value, X data) {
			theSourceListener.disposed(theMap.apply(value), data);
		}

		@Override
		public void transferred(CollectionElement<S> element, X data) {
			theSourceListener.transferred(wrap(element), data);
		}
	}
}

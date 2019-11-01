package org.qommons.collect;

import java.util.Comparator;
import java.util.Objects;

import org.qommons.Ternian;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public interface BetterSortedList<E> extends ValueStoredCollection<E>, BetterList<E> {
	/**
	 * A filter on sorted search results. The underlying code for searches in a {@link BetterSortedList} will always return an element
	 * unless the set is empty. The value will either match the search exactly or will be adjacent to where a value matching the search
	 * exactly would be inserted.
	 * 
	 * One of these filters may be used to direct and filter the search results.
	 * 
	 * Note that this class cannot be used to exclude an exact match. This may be done by creating a {@link Comparable search} that never
	 * returns zero. For example, if a result strictly less than a search is desired, create a wrapping search that returns 1 for any values
	 * that the wrapped search returns 0 for.
	 * 
	 * @see BetterSortedList#search(Comparable, SortedSearchFilter)
	 */
	enum SortedSearchFilter {
		/**
		 * Accepts only results less than or equal to a search (i.e. <code>search.{@link Comparable#compareTo compareTo}(value)&gt;=0</code>
		 */
		Less(Ternian.TRUE, true),
		/**
		 * Prefers results less than or equal to a search (i.e. <code>search.{@link Comparable#compareTo compareTo}(value)&gt;=0</code>, but
		 * accepts a greater result if no lesser result exists
		 */
		PreferLess(Ternian.TRUE, false),
		/** Accepts only results for which a search returns 0 */
		OnlyMatch(Ternian.NONE, true),
		/**
		 * Prefers results greater than or equal to a search (i.e. <code>search.{@link Comparable#compareTo compareTo}(value)&lt;=0</code>,
		 * but accepts a lesser result if no greater result exists
		 */
		PreferGreater(Ternian.FALSE, false),
		/**
		 * Accepts only results greater than or equal to a search (i.e.
		 * <code>search.{@link Comparable#compareTo compareTo}(value)&lt;=0</code>
		 */
		Greater(Ternian.FALSE, true);
	
		/** Whether this search prefers values less than an exact match, or {@link Ternian#NONE} for {@link #OnlyMatch} */
		public final Ternian less;
		/** Whether this search allows matches that do not match the #less value */
		public final boolean strict;
	
		private SortedSearchFilter(Ternian less, boolean strict) {
			this.less = less;
			this.strict = strict;
		}
	
		public SortedSearchFilter opposite() {
			switch (this) {
			case Less:
				return Greater;
			case PreferLess:
				return PreferGreater;
			case PreferGreater:
				return PreferLess;
			case Greater:
				return Less;
			default:
				return this;
			}
		}
	
		public static SortedSearchFilter of(Boolean less, boolean strict) {
			for (SortedSearchFilter ssf : SortedSearchFilter.values())
				if (Objects.equals(ssf.less.value, less) && ssf.strict == strict)
					return ssf;
			throw new IllegalArgumentException("No such filter exists");
		}
	
		/** @return A short String representing this filter */
		public String getSymbol() {
			switch (this) {
			case Less:
				return "<";
			case PreferLess:
				return "<?";
			case OnlyMatch:
				return "==";
			case PreferGreater:
				return ">?";
			case Greater:
				return ">";
			default:
				throw new IllegalStateException();
			}
		}
	}

	Comparator<? super E> comparator();

	/**
	 * Searches this sorted set for an element
	 *
	 * @param search The search to navigate through this set for the target value. The search must follow this set's {@link #comparator()
	 *        order}.
	 * @param filter The filter on the result
	 * @return The element that is the best found result of the search, or null if this set is empty or does not contain any element
	 *         matching the given filter
	 */
	CollectionElement<E> search(Comparable<? super E> search, BetterSortedList.SortedSearchFilter filter);

	/**
	 * Same as {@link #search(Comparable, SortedSearchFilter)} but flattens to a value
	 * 
	 * @param search The search to navigate through this set for the target value. The search must follow this set's {@link #comparator()
	 *        order}.
	 * @param filter The filter on the result
	 * @return The value that is the best found result of the search, or null if this set is empty or does not contain any element matching
	 *         the given filter
	 */
	default E searchValue(Comparable<? super E> search, BetterSortedList.SortedSearchFilter filter) {
		CollectionElement<E> el = search(search, filter);
		return el == null ? null : el.get();
	}

	@Override
	default CollectionElement<E> getOrAdd(E value, ElementId after, ElementId before, boolean first, Runnable added) {
		if (after != null || before != null) {
			// If the given elements constrain the search space, we can probably be faster than the general method below
			try (Transaction t = lock(true, null)) {
				ElementId best = first ? after : before;
				ElementId worst = first ? before : after;
				if (best != null) {
					CollectionElement<E> bestEl = getElement(best);
					int comp = comparator().compare(value, bestEl.get());
					if ((comp < 0) == first)
						throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
					while (true) {
						if (comp == 0)
							return bestEl;
						bestEl = getAdjacentElement(bestEl.getElementId(), first);
						if (bestEl == null || (worst != null && (bestEl.getElementId().compareTo(worst) > 0) == first))
							break;
						comp = comparator().compare(value, bestEl.get());
						if ((comp < 0) == first) {
							ElementId addedEl = mutableElement(bestEl.getElementId()).add(value, first);
							if (added != null)
								added.run();
							return getElement(addedEl);
						}
					}
					if (worst == null) {
						ElementId addedEl = mutableElement(getTerminalElement(!first).getElementId()).add(value, !first);
						if (added != null)
							added.run();
						return getElement(addedEl);
					}
				} else {
					CollectionElement<E> worstEl = getElement(worst);
					int comp = comparator().compare(value, worstEl.get());
					if ((comp > 0) == first)
						throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
					while (true) {
						if (comp == 0)
							return worstEl;
						worstEl = getAdjacentElement(worstEl.getElementId(), !first);
						if (worstEl == null)
							break;
						comp = comparator().compare(value, worstEl.get());
						if ((comp > 0) == first) {
							ElementId addedEl = mutableElement(worstEl.getElementId()).add(value, !first);
							if (added != null)
								added.run();
							return getElement(addedEl);
						}
					}
					ElementId addedEl = mutableElement(getTerminalElement(first).getElementId()).add(value, first);
					if (added != null)
						added.run();
					return getElement(addedEl);
				}
			}
		}
		while (true) {
			CollectionElement<E> found = search(searchFor(value, 0), BetterSortedList.SortedSearchFilter.PreferLess);
			if (found == null) {
				found = addElement(value, first);
				if (found != null && added != null)
					added.run();
				return found;
			}
			int compare = comparator().compare(value, found.get());
			if (compare == 0)
				return found;
			try (Transaction t = lock(true, true, null)) {
				MutableCollectionElement<E> mutableElement;
				try {
					mutableElement = mutableElement(found.getElementId());
				} catch (IllegalArgumentException e) {
					continue; // Possible it may have been removed already
				}
				ElementId addedId = mutableElement.add(value, compare < 0);
				if (added != null)
					added.run();
				return getElement(addedId);
			}
		}
	}

	/**
	 * @param search The search to use
	 * @return Either:
	 *         <ul>
	 *         <li>The index of the element <code>search</code>
	 *         (<code>search.{@link Comparable#compareTo(Object) compareTo}(element)==0</code>)</li>
	 *         <li>or <code>-index-1</code>, where <code>index</code> is the index in this sorted set where a value matching
	 *         <code>search</code> would be if it were added</li>
	 *         </ul>
	 */
	int indexFor(Comparable<? super E> search);

	/**
	 * Creates a {@link Comparable} to use in searching this sorted set from a value compatible with the set's comparator
	 * 
	 * @param value The comparable value
	 * @param onExact The value to return when the comparator matches. For example, to search for values strictly less than
	 *        <code>value</code>, an integer &lt;0 should be specified.
	 * @return The search to use with {@link #search(Comparable, SortedSearchFilter)},
	 *         {@link BetterSortedSet#subSet(Comparable, Comparable)}, etc.
	 */
	default Comparable<? super E> searchFor(E value, int onExact) {
		class ValueSearch<V> implements Comparable<V> {
			private final Comparator<? super V> theCompare;
			private final V theValue;
			private final int theOnExact;

			ValueSearch(Comparator<? super V> compare, V val, int _onExact) {
				theCompare = compare;
				theValue = val;
				theOnExact = _onExact;
			}

			@Override
			public int compareTo(V v) {
				int compare = theCompare.compare(theValue, v);
				if (compare == 0)
					compare = theOnExact;
				return compare;
			}

			@Override
			public int hashCode() {
				return Objects.hash(theCompare, theValue, theOnExact);
			}

			@Override
			public boolean equals(Object o) {
				if (!(o instanceof ValueSearch))
					return false;
				ValueSearch<?> other = (ValueSearch<?>) o;
				return theCompare.equals(other.theCompare) && Objects.equals(theValue, other.theValue) && theOnExact == other.theOnExact;
			}

			@Override
			public String toString() {
				if (theOnExact < 0)
					return "<" + theValue;
				else if (theOnExact == 0)
					return String.valueOf(theValue);
				else
					return ">" + theValue;
			}
		}
		return new ValueSearch<>(comparator(), value, onExact);
	}

	/**
	 * @param <E> The type of value to compare
	 * @param c1 The first search
	 * @param c2 The other search
	 * @param low Whether the other search is for the lower or upper bound of a new sorted set
	 * @return A lower or upper bound for both the given search and this sub set's lower or upper bound
	 */
	public static <E> Comparable<? super E> and(Comparable<? super E> c1, Comparable<? super E> c2, boolean low) {
		if (c1 == null)
			return c2;
		else if (c2 == null)
			return c1;
		class AndCompare implements Comparable<E> {
			@Override
			public int compareTo(E v) {
				int comp = c1.compareTo(v);
				if (low && comp <= 0)
					comp = c2.compareTo(v);
				else if (!low && comp >= 0)
					comp = c2.compareTo(v);
				return comp;
			}

			@Override
			public String toString() {
				return c1 + " and " + c2;
			}
		}
		return new AndCompare();
	}

	@Override
	default CollectionElement<E> getElement(E value, boolean first) {
		return search(searchFor(value, 0), BetterSortedList.SortedSearchFilter.OnlyMatch);
	}
}

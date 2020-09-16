package org.qommons.collect;

import java.lang.reflect.Array;
import java.util.Comparator;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.Identifiable;
import org.qommons.QommonsUtils;
import org.qommons.Ternian;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * A {@link BetterList} whose values are maintained in the order of a comparator. This is different than a {@link BetterSortedSet} because
 * distinctness may not be enforced.
 * 
 * @param <E> The type of values in the list
 */
public interface BetterSortedList<E> extends ValueStoredCollection<E>, BetterList<E> {
	/**
	 * A filter on sorted search results. The underlying code for searches in a {@link BetterSortedList} will always return an element
	 * unless the list is empty. The value will either match the search exactly or will be adjacent to where a value matching the search
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

	/** @return The comparator sorting the values */
	Comparator<? super E> comparator();

	/**
	 * Searches this sorted list for an element
	 *
	 * @param search The search to navigate through this list for the target value. The search must follow this list's {@link #comparator()
	 *        order}.
	 * @param filter The filter on the result
	 * @return The element that is the best found result of the search, or null if this list is empty or does not contain any element
	 *         matching the given filter
	 */
	CollectionElement<E> search(Comparable<? super E> search, BetterSortedList.SortedSearchFilter filter);

	/**
	 * Same as {@link #search(Comparable, SortedSearchFilter)} but flattens to a value
	 * 
	 * @param search The search to navigate through this list for the target value. The search must follow this list's {@link #comparator()
	 *        order}.
	 * @param filter The filter on the result
	 * @return The value that is the best found result of the search, or null if this list is empty or does not contain any element matching
	 *         the given filter
	 */
	default E searchValue(Comparable<? super E> search, BetterSortedList.SortedSearchFilter filter) {
		CollectionElement<E> el = search(search, filter);
		return el == null ? null : el.get();
	}

	@Override
	default boolean isContentControlled() {
		return true;
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
			try (Transaction t = lock(true, null)) {
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
	 *         <li>or <code>-index-1</code>, where <code>index</code> is the index in this sorted list where a value matching
	 *         <code>search</code> would be if it were added</li>
	 *         </ul>
	 */
	int indexFor(Comparable<? super E> search);

	/**
	 * Creates a {@link Comparable} to use in searching this sorted list from a value compatible with the list's comparator
	 * 
	 * @param value The comparable value
	 * @param onExact The value to return when the comparator matches. For example, to search for values strictly less than
	 *        <code>value</code>, an integer &lt;0 should be specified.
	 * @return The search to use with {@link #search(Comparable, SortedSearchFilter)},
	 *         {@link BetterSortedList#subSequence(Comparable, Comparable)}, etc.
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
				StringBuilder str = new StringBuilder();
				if (theOnExact < 0)
					str.append('<');
				else if (theOnExact > 0)
					str.append('>');
				str.append(theValue);
				str.append(':').append(theCompare);
				return str.toString();
			}
		}
		return new ValueSearch<>(comparator(), value, onExact);
	}

	/**
	 * @param <E> The type of value to compare
	 * @param c1 The first search
	 * @param c2 The other search
	 * @param low Whether the other search is for the lower or upper bound of a new sorted sequence
	 * @return A lower or upper bound for both the given search and this sub sequence's lower or upper bound
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

	/** @return The first value in this list */
	default E first() {
		return getFirst();
	}

	/** @return The last value in this list */
	default E last() {
		return getLast();
	}

	/**
	 * Returns the greatest element in this list less than or equal to the given element, or {@code null} if there is no such element.
	 *
	 * @param e the value to match
	 * @return the greatest element less than or equal to {@code e}, or {@code null} if there is no such element
	 * @throws ClassCastException if the specified element cannot be compared with the elements currently in the list
	 * @throws NullPointerException if the specified element is null and this list does not permit null elements
	 */
	default E floor(E e) {
		CollectionElement<E> element = search(searchFor(e, 0), BetterSortedList.SortedSearchFilter.Less);
		return element == null ? null : element.get();
	}

	/**
	 * Returns the greatest element in this list strictly less than the given element, or {@code null} if there is no such element.
	 *
	 * @param e the value to match
	 * @return the greatest element less than {@code e}, or {@code null} if there is no such element
	 * @throws ClassCastException if the specified element cannot be compared with the elements currently in the list
	 * @throws NullPointerException if the specified element is null and this list does not permit null elements
	 */
	default E lower(E e) {
		CollectionElement<E> element = search(searchFor(e, -1), BetterSortedList.SortedSearchFilter.Less);
		return element == null ? null : element.get();
	}

	/**
	 * Returns the least element in this list greater than or equal to the given element, or {@code null} if there is no such element.
	 *
	 * @param e the value to match
	 * @return the least element greater than or equal to {@code e}, or {@code null} if there is no such element
	 * @throws ClassCastException if the specified element cannot be compared with the elements currently in the list
	 * @throws NullPointerException if the specified element is null and this list does not permit null elements
	 */
	default E ceiling(E e) {
		CollectionElement<E> element = search(searchFor(e, 0), BetterSortedList.SortedSearchFilter.Greater);
		return element == null ? null : element.get();
	}

	/**
	 * Returns the least element in this list strictly greater than the given element, or {@code null} if there is no such element.
	 *
	 * @param e the value to match
	 * @return the least element greater than {@code e}, or {@code null} if there is no such element
	 * @throws ClassCastException if the specified element cannot be compared with the elements currently in the list
	 * @throws NullPointerException if the specified element is null and this list does not permit null elements
	 */
	default E higher(E e) {
		CollectionElement<E> element = search(searchFor(e, 1), BetterSortedList.SortedSearchFilter.Greater);
		return element == null ? null : element.get();
	}

	/**
	 * Given a value/search, returns an interpolated value within this list's contents
	 * 
	 * @param <T> The type of value to return
	 * @param search The search to search for a position within this list's values
	 * @param onMatchOrTerminal Provides the result in the case that:
	 *        <ul>
	 *        <li>A value in this list matches the search exactly</li>
	 *        <li>The first value in this list is greater than the search</li>
	 *        <li>The last value in this list is less than the search</li>
	 *        </ul>
	 * @param interpolate Provides the result in the case that there are two adjacent values in this list, <code>v1</code> and
	 *        <code>v2</code>, such that <code>v1&lt;search && v2&gt;search</code>
	 * @param onEmpty Provides the result in the case that this list is empty
	 * @return The value supplied by the appropriate function
	 */
	default <T> T between(Comparable<? super E> search, Function<? super E, ? extends T> onMatchOrTerminal,
		BiFunction<? super E, ? super E, ? extends T> interpolate, Supplier<? extends T> onEmpty) {
		return betweenElements(search, //
			el -> onMatchOrTerminal.apply(el.get()), (el1, el2) -> interpolate.apply(el1.get(), el2.get()), onEmpty);
	}

	/**
	 * Same as {@link #between(Comparable, Function, BiFunction, Supplier)}, but supplies the {@link CollectionElement element}s to the
	 * value functions
	 * 
	 * @param <T> The type of value to return
	 * @param search The search to search for a position within this list's values
	 * @param onMatchOrTerminal Provides the result in the case that:
	 *        <ul>
	 *        <li>An element in this list matches the search exactly</li>
	 *        <li>The first element in this list is greater than the search</li>
	 *        <li>The last element in this list is less than the search</li>
	 *        </ul>
	 * @param interpolate Provides the result in the case that there are two adjacent elements in this list whose values, <code>v1</code>
	 *        and <code>v2</code> are such that <code>v1&lt;search && v2&gt;search</code>
	 * @param onEmpty Provides the result in the case that this list is empty
	 * @return The value supplied by the appropriate function
	 */
	default <T> T betweenElements(Comparable<? super E> search, Function<? super CollectionElement<E>, ? extends T> onMatchOrTerminal,
		BiFunction<? super CollectionElement<E>, ? super CollectionElement<E>, ? extends T> interpolate, Supplier<? extends T> onEmpty) {
		CollectionElement<E> found = search(search, BetterSortedList.SortedSearchFilter.Less);
		if (found == null) { // No element <= search
			found = getTerminalElement(true);
			if (found == null)
				return onEmpty.get();
			else
				return onMatchOrTerminal.apply(found);
		} else if (search.compareTo(found.get()) == 0)
			return onMatchOrTerminal.apply(found);
		else {
			CollectionElement<E> next = getAdjacentElement(found.getElementId(), true);
			if (next == null)
				return onMatchOrTerminal.apply(found);
			else
				return interpolate.apply(found, next);
		}
	}

	/**
	 * <p>
	 * Returns a view of the portion of this collection whose elements range from {@code fromElement} to {@code toElement}. If
	 * {@code fromElement} and {@code toElement} are equal, the returned list is empty unless {@code
	 * fromInclusive} and {@code toInclusive} are both true. The returned list is backed by this list, so changes in the returned list are
	 * reflected in this list, and vice-versa. The returned list supports all optional list operations that this list supports.
	 * </p>
	 * 
	 * <p>
	 * Equivalent to {@link #subSequence(Comparable, Comparable)
	 * subSequence}(<code>{@link #searchFor(Object, int) searchFor}(toElement, inclusive ? 0 : 1),
	 *  {@link #searchFor(Object, int) searchFor}(toElement, inclusive ? 0 : -1)</code>)
	 * <p>
	 * The returned list will throw an {@code IllegalArgumentException} on an attempt to insert an element outside its range.
	 * </p>
	 *
	 * @param fromElement low endpoint of the returned collection
	 * @param fromInclusive {@code true} if the low endpoint is to be included in the returned view
	 * @param toElement high endpoint of the returned list
	 * @param toInclusive {@code true} if the high endpoint is to be included in the returned view
	 * @return A view of the portion of this list whose elements range from {@code fromElement}, inclusive, to {@code toElement}, exclusive
	 * @see NavigableSet#subSet(Object, boolean, Object, boolean)
	 */
	default BetterSortedList<E> subSequence(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return subSequence(searchFor(fromElement, fromInclusive ? 0 : 1), searchFor(toElement, toInclusive ? 0 : -1));
	}

	/**
	 * @param from The (optional) lower bound for the sub-list
	 * @param to The (optional) upper bound for the sub-list
	 * @return A sub list containing all of this list's <code>value</code>s for which
	 *         <code>from.{@link Comparable#compareTo(Object) compareTo}(value)&gt;=0</code> (if <code>from</code> is specified) and
	 *         <code>to.{@link Comparable#compareTo(Object) compareTo}(value)&lt;=0</code> (if <code>to</code> is specified).
	 */
	default BetterSortedList<E> subSequence(Comparable<? super E> from, Comparable<? super E> to) {
		return new BetterSubSequence<>(this, from, to);
	}

	/**
	 * Equivalent to {@link #subSequence(Comparable, Comparable)
	 * subSequence}(<code>null, {@link #searchFor(Object, int) searchFor}(toElement, inclusive ? 0 : -1)</code>)
	 *
	 * @param toElement high endpoint of the returned list
	 * @param inclusive {@code true} if the high endpoint is to be included in the returned view
	 * @return A view of the portion of this list whose elements are less than (or equal to, if {@code inclusive} is true) {@code toElement}
	 * @see NavigableSet#headSet(Object, boolean)
	 */
	default BetterSortedList<E> headSequence(E toElement, boolean inclusive) {
		return subSequence(null, searchFor(toElement, inclusive ? 0 : -1));
	}

	/**
	 * Equivalent to {@link #subSequence(Comparable, Comparable)
	 * subSequence}(<code>{@link #searchFor(Object, int) searchFor}(toElement, inclusive ? 0 : 1), null</code>)
	 *
	 * @param fromElement low endpoint of the returned list
	 * @param inclusive {@code true} if the low endpoint is to be included in the returned view
	 * @return A view of the portion of this list whose elements are greater than or equal to {@code fromElement}
	 * @see NavigableSet#tailSet(Object, boolean)
	 */
	default BetterSortedList<E> tailSequence(E fromElement, boolean inclusive) {
		return subSequence(searchFor(fromElement, inclusive ? 0 : 1), null);
	}

	/**
	 * Equivalent to {@code subSequence(fromElement, true, toElement, false)}.
	 * 
	 * @param fromElement low endpoint (inclusive) of the returned list
	 * @param toElement high endpoint (exclusive) of the returned list
	 * @return A view of the portion of this list whose elements range from <tt>fromElement</tt>, inclusive, to <tt>toElement</tt>,
	 *         exclusive
	 */
	default BetterSortedList<E> subSequence(E fromElement, E toElement) {
		return subSequence(fromElement, true, toElement, false);
	}

	/**
	 * Equivalent to {@link #headSequence(Object, boolean) headSequence}(<code>toElement, false</code>)
	 *
	 * @param toElement high endpoint of the returned list
	 * @return A view of the portion of this list whose elements are less than (or equal to, if {@code inclusive} is true) {@code toElement}
	 * @see NavigableSet#headSet(Object, boolean)
	 */
	default BetterSortedList<E> headSequence(E toElement) {
		return headSequence(toElement, false);
	}

	/**
	 * Equivalent to {@code tailSet(fromElement, true)}.
	 * 
	 * @param fromElement low endpoint (inclusive) of the returned list
	 * @return A view of the portion of this list whose elements are greater than or equal to <tt>fromElement</tt>
	 */
	default BetterSortedList<E> tailSequence(E fromElement) {
		return tailSequence(fromElement, true);
	}

	@Override
	default BetterList<E> subList(int fromIndex, int toIndex) {
		if (!BetterCollections.simplifyDuplicateOperations())
			return BetterList.super.subList(fromIndex, toIndex);
		try (Transaction t = lock(false, null)) {
			// Be inclusive so that adds succeed as often as possible
			Comparable<? super E> from = fromIndex == 0 ? null : searchFor(get(fromIndex - 1), 1);
			Comparable<? super E> to = toIndex == size() ? null : searchFor(get(toIndex), -1);
			return subSequence(from, to);
		}
	}

	@Override
	default BetterSortedList<E> reverse() {
		return new ReversedSortedList<>(this);
	}

	/**
	 * @param <E> The type for the list
	 * @param compare The sorting for the list
	 * @return An immutable, empty {@link BetterSortedList}
	 */
	public static <E> BetterSortedList<E> empty(Comparator<? super E> compare) {
		return new EmptySortedList<>(compare);
	}

	/**
	 * Implements {@link BetterSortedList#empty(Comparator)}
	 * 
	 * @param <E> The type of the list
	 */
	class EmptySortedList<E> extends BetterList.EmptyList<E> implements BetterSortedList<E> {
		private final Comparator<? super E> theSorting;

		public EmptySortedList(Comparator<? super E> sorting) {
			theSorting = sorting;
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return false;
		}

		@Override
		public boolean checkConsistency() {
			return false;
		}

		@Override
		public <X> boolean repair(ElementId element, org.qommons.collect.ValueStoredCollection.RepairListener<E, X> listener) {
			return false;
		}

		@Override
		public <X> boolean repair(org.qommons.collect.ValueStoredCollection.RepairListener<E, X> listener) {
			return false;
		}

		@Override
		public Comparator<? super E> comparator() {
			return theSorting;
		}

		@Override
		public CollectionElement<E> search(Comparable<? super E> search, SortedSearchFilter filter) {
			return null;
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			return -1;
		}
	}

	/**
	 * Implements {@link BetterSortedList#subSequence(Comparable, Comparable)}
	 *
	 * @param <E> The type of elements in the list
	 */
	public static class BetterSubSequence<E> implements BetterSortedList<E> {
		private final BetterSortedList<E> theWrapped;

		private final Comparable<? super E> from;
		private final Comparable<? super E> to;

		private Object theIdentity;

		/**
		 * @param list The sorted list that this sub sequence is for
		 * @param from The lower bound for the sub sequence
		 * @param to The upper bound for the sub sequence
		 */
		public BetterSubSequence(BetterSortedList<E> list, Comparable<? super E> from, Comparable<? super E> to) {
			theWrapped = list;
			this.from = from;
			this.to = to;
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.wrap(theWrapped.getIdentity(), "subSequence", from, to);
			return theIdentity;
		}

		/** @return The sorted list that this is a sub-sequence of */
		public BetterSortedList<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theWrapped.tryLock(write, cause);
		}

		@Override
		public long getStamp() {
			return theWrapped.getStamp();
		}

		/** @return This sub-sequence's lower bound (may be null) */
		public Comparable<? super E> getFrom() {
			return from;
		}

		/** @return This sub-sequence's upper bound (may be null) */
		public Comparable<? super E> getTo() {
			return to;
		}

		/**
		 * @param value The value to check
		 * @return
		 *         <ul>
		 *         <li><b>0</b> if the value belongs in this sequence</li>
		 *         <li><b>&lt;0</b> if <code>{@link #getFrom() from}.{@link Comparable#compareTo(Object) compareTo}(value)&gt;0</code></li>
		 *         <li><b>&gt;0</b> if <code>{@link #getTo() to}.{@link Comparable#compareTo(Object) compareTo}(value)&lt;0</code></li>
		 *         </ul>
		 */
		public int isInRange(E value) {
			if (from != null && from.compareTo(value) > 0)
				return -1;
			if (to != null && to.compareTo(value) < 0)
				return 1;
			return 0;
		}

		/**
		 * @param search The search for this sub sequence
		 * @return A search to use within this sub sequence's {@link #getWrapped() super list} that obeys the given search within the
		 *         sub-sequence's bounds, but returns &lt;0 for values below and &gt;0 for values above this sub sequence's bounds
		 */
		protected Comparable<E> boundSearch(Comparable<? super E> search) {
			class BoundedSearch<V> implements Comparable<V> {
				private final BetterSubSequence<V> theSubSequence;
				private final Comparable<? super V> theSearch;

				BoundedSearch(BetterSubSequence<V> subSequence, Comparable<? super V> srch) {
					theSubSequence = subSequence;
					theSearch = srch;
				}

				@Override
				public int compareTo(V v) {
					int compare = -theSubSequence.isInRange(v);
					if (compare == 0)
						compare = theSearch.compareTo(v);
					return compare;
				}

				@Override
				public String toString() {
					StringBuilder str = new StringBuilder("bounded(").append(search);
					if (theSubSequence.from != null)
						str.append(", " + theSubSequence.from);
					if (theSubSequence.to != null)
						str.append(", " + theSubSequence.to);
					str.append(')');
					return str.toString();
				}
			}
			return new BoundedSearch<>(this, search);
		}

		@Override
		public boolean belongs(Object o) {
			return theWrapped.belongs(o) && isInRange((E) o) == 0;
		}

		/** @return The first index in the wrapped sorted sequence that is included in this sequence */
		protected int getMinIndex() {
			if (from == null)
				return 0;
			int index = theWrapped.indexFor(from);
			if (index >= 0)
				return index;
			else
				return -index - 1; // Zeroth element is AFTER the from search
		}

		/** @return The last index in the wrapped */
		protected int getMaxIndex() {
			if (to == null)
				return theWrapped.size();
			int index = theWrapped.indexFor(to);
			if (index >= 0)
				return index + 1;
			else
				return -index - 1;
		}

		@Override
		public Comparator<? super E> comparator() {
			return theWrapped.comparator();
		}

		@Override
		public int size() {
			int minIndex = getMinIndex();
			if (minIndex < 0)
				return 0;
			int maxIndex = getMaxIndex();
			return Math.max(0, maxIndex - minIndex);
		}

		@Override
		public boolean isEmpty() {
			int minIndex = getMinIndex();
			if (minIndex < 0)
				return true;
			int maxIndex = getMaxIndex();
			return minIndex >= maxIndex;
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			int minIndex = getMinIndex();
			int maxIndex = getMaxIndex();
			if (minIndex >= maxIndex)
				return -1;
			int wrapIdx = theWrapped.indexFor(boundSearch(search));
			if (wrapIdx < 0) {
				wrapIdx = -wrapIdx - 1 - minIndex;
				return -wrapIdx - 1;
			} else
				return wrapIdx - minIndex;
		}

		@Override
		public int getElementsBefore(ElementId id) {
			int wIndex = theWrapped.getElementsBefore(strip(id));
			int minIdx = getMinIndex();
			if (wIndex < minIdx)
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			if (wIndex >= getMaxIndex())
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return wIndex - minIdx;
		}

		@Override
		public int getElementsAfter(ElementId id) {
			int wIndex = theWrapped.getElementsBefore(strip(id));
			int maxIdx = getMaxIndex();
			if (wIndex >= maxIdx)
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			if (wIndex < getMinIndex())
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return maxIdx - wIndex - 1;
		}

		@Override
		public Object[] toArray() {
			Object[] array = new Object[size()];
			for (int i = 0; i < array.length; i++)
				array[i] = get(i);
			return array;
		}

		@Override
		public <T> T[] toArray(T[] a) {
			T[] array = a.length >= size() ? a : (T[]) Array.newInstance(a.getClass().getComponentType(), size());
			for (int i = 0; i < array.length; i++)
				array[i] = (T) get(i);
			return array;
		}

		private int checkIndex(int index, boolean includeTerminus) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			int min = getMinIndex();
			int max = getMaxIndex();
			int wrapIndex = min + index;
			if (wrapIndex > max || (wrapIndex == max && !includeTerminus))
				throw new IndexOutOfBoundsException(index + " of " + Math.max(0, max - min));
			return min + index;
		}

		@Override
		public E get(int index) {
			return theWrapped.get(checkIndex(index, false));
		}

		@Override
		public E set(int index, E element) {
			if (!belongs(element))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			try (Transaction t = lock(true, null)) {
				return theWrapped.set(checkIndex(index, false), element);
			}
		}

		@Override
		public String canAdd(E value, ElementId after, ElementId before) {
			if (!belongs(value))
				return StdMsg.ILLEGAL_ELEMENT;
			after = strip(after);
			before = strip(before);
			if (after == null && from != null)
				after = CollectionElement.getElementId(theWrapped.search(from, BetterSortedList.SortedSearchFilter.Less));
			if (before == null && to != null)
				before = CollectionElement.getElementId(theWrapped.search(to, BetterSortedList.SortedSearchFilter.Greater));
			return theWrapped.canAdd(value, after, before);
		}

		@Override
		public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			if (!belongs(value))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			after = strip(after);
			before = strip(before);
			if (after == null && from != null)
				after = CollectionElement.getElementId(theWrapped.search(from, BetterSortedList.SortedSearchFilter.Less));
			if (before == null && to != null)
				before = CollectionElement.getElementId(theWrapped.search(to, BetterSortedList.SortedSearchFilter.Greater));
			return getElement(theWrapped.addElement(value, after, before, first));
		}

		@Override
		public void add(int index, E element) {
			if (!belongs(element))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			try (Transaction t = lock(true, null)) {
				theWrapped.add(checkIndex(index, true), element);
			}
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			valueEl = strip(valueEl);
			after = strip(after);
			before = strip(before);
			return getWrapped().canMove(valueEl, after, before);
		}

		@Override
		public CollectionElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			valueEl = strip(valueEl);
			after = strip(after);
			before = strip(before);
			return getElement(getWrapped().move(valueEl, after, before, first, afterRemove));
		}

		@Override
		public E remove(int index) {
			return theWrapped.remove(checkIndex(index, false));
		}

		@Override
		public CollectionElement<E> getElement(int index) {
			return getElement(theWrapped.getElement(checkIndex(index, false)));
		}

		@Override
		public CollectionElement<E> getElement(E value, boolean first) {
			if (!belongs(value))
				return null;
			return getElement(theWrapped.getElement(value, first));
		}

		@Override
		public CollectionElement<E> getElement(ElementId id) {
			CollectionElement<E> el = theWrapped.getElement(strip(id));
			if (isInRange(el.get()) != 0)
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return getElement(el);
		}

		@Override
		public BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			return QommonsUtils.filterMap(theWrapped.getElementsBySource(sourceEl, sourceCollection), el -> isInRange(el.get()) == 0,
				el -> getElement(el));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(theWrapped.getSourceElements(strip(localElement), theWrapped).stream().map(this::wrap));
			return theWrapped.getSourceElements(strip(localElement), sourceCollection);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			if (!(equivalentEl instanceof BetterSubSequence.BoundedElementId))
				return null;
			ElementId wrappedId = theWrapped.getEquivalentElement(((BoundedElementId) equivalentEl).theSourceId);
			return wrap(wrappedId);
		}

		@Override
		public CollectionElement<E> getTerminalElement(boolean first) {
			CollectionElement<E> wrapTerminal;
			if (first) {
				if (from == null)
					wrapTerminal = theWrapped.getTerminalElement(true);
				else
					wrapTerminal = theWrapped.search(from, BetterSortedList.SortedSearchFilter.Greater);
			} else {
				if (to == null)
					wrapTerminal = theWrapped.getTerminalElement(false);
				else
					wrapTerminal = theWrapped.search(to, BetterSortedList.SortedSearchFilter.Less);
			}
			if (wrapTerminal == null)
				return null;
			else if (from != null && from.compareTo(wrapTerminal.get()) > 0)
				return null;
			else if (to != null && to.compareTo(wrapTerminal.get()) < 0)
				return null;
			else
				return getElement(wrapTerminal);
		}

		@Override
		public CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next) {
			CollectionElement<E> el = theWrapped.getAdjacentElement(strip(elementId), next);
			if (el == null || isInRange(el.get()) != 0)
				return null;
			return getElement(el);
		}

		@Override
		public MutableCollectionElement<E> mutableElement(ElementId id) {
			MutableCollectionElement<E> el = theWrapped.mutableElement(strip(id));
			return new BoundedMutableElement(el);
		}

		@Override
		public CollectionElement<E> search(Comparable<? super E> search, BetterSortedList.SortedSearchFilter filter) {
			CollectionElement<E> wrapResult = theWrapped.search(boundSearch(search), filter);
			if (wrapResult == null)
				return null;
			int range = isInRange(wrapResult.get());
			if (range == 0)
				return getElement(wrapResult);
			if (filter.strict)
				return null;
			return getTerminalElement(range < 0);
		}

		@Override
		public BetterSortedList<E> subSequence(Comparable<? super E> innerFrom, Comparable<? super E> innerTo) {
			if (BetterCollections.simplifyDuplicateOperations())
				return new BetterSubSequence<>(theWrapped, BetterSortedList.and(from, innerFrom, true),
					BetterSortedList.and(to, innerTo, false));
			else
				return BetterSortedList.super.subSequence(innerFrom, innerTo);
		}

		@Override
		public boolean removeLast(Object o) {
			if ((o != null && !theWrapped.belongs(o)) || isInRange((E) o) != 0)
				return false;
			return theWrapped.removeLast(o);
		}

		@Override
		public void clear() {
			CollectionElement<E> bound = from == null ? null : theWrapped.search(from, BetterSortedList.SortedSearchFilter.Less);
			if (bound == null)
				bound = to == null ? null : theWrapped.search(to, BetterSortedList.SortedSearchFilter.Greater);
			if (bound == null) // This sub sequence contains all of the super set's elements
				theWrapped.clear();
			else
				removeIf(v -> true);
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return theWrapped.isConsistent(unwrap(element));
		}

		@Override
		public boolean checkConsistency() {
			return theWrapped.checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<E, X> listener) {
			RepairListener<E, X> subListener = listener == null ? null : new BoundedRepairListener<>(listener);
			return theWrapped.repair(unwrap(element), subListener);
		}

		@Override
		public <X> boolean repair(RepairListener<E, X> listener) {
			RepairListener<E, X> subListener = listener == null ? null : new BoundedRepairListener<>(listener);
			return theWrapped.repair(subListener);
		}

		@Override
		public int hashCode() {
			return BetterCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			StringBuilder ret = new StringBuilder("{");
			boolean first = true;
			for (Object value : this) {
				if (!first) {
					ret.append(", ");
				} else
					first = false;
				ret.append(value);
			}
			ret.append('}');
			return ret.toString();
		}

		/**
		 * @param id The element ID for this sub-sequence
		 * @return The corresponding element ID for the parent list
		 */
		protected ElementId strip(ElementId id) {
			if (id == null)
				return null;
			BoundedElementId boundedId = (BetterSubSequence<E>.BoundedElementId) id;
			if (!boundedId.check())
				throw new NoSuchElementException(StdMsg.ELEMENT_REMOVED);
			return boundedId.theSourceId;
		}

		BoundedElement getElement(CollectionElement<E> element) {
			return element == null ? null : new BoundedElement(element);
		}

		/**
		 * @param wrappedElId The element ID from the wrapped sorted list
		 * @return The element ID for this sequence
		 */
		protected ElementId wrap(ElementId wrappedElId) {
			return wrappedElId == null ? null : new BoundedElementId(wrappedElId);
		}

		/**
		 * @param wrappedElId The element ID from the this sequence
		 * @return The element ID for the wrapped list
		 */
		protected static ElementId unwrap(ElementId wrappedElId) {
			return ((BetterSubSequence.BoundedElementId) wrappedElId).theSourceId;
		}

		class BoundedElementId implements ElementId {
			private final ElementId theSourceId;

			BoundedElementId(ElementId sourceId) {
				theSourceId = sourceId;
			}

			boolean check() {
				if (!theSourceId.isPresent())
					return true;
				return isInRange(getWrapped().getElement(theSourceId).get()) == 0;
			}

			@Override
			public int compareTo(ElementId o) {
				return theSourceId.compareTo(((BoundedElementId) o).theSourceId);
			}

			@Override
			public boolean isPresent() {
				return theSourceId.isPresent() && check();
			}

			@Override
			public int hashCode() {
				return theSourceId.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (obj == this)
					return true;
				else if (!(obj instanceof BetterSubSequence.BoundedElementId))
					return false;
				else
					return theSourceId.equals(((BoundedElementId) obj).theSourceId);
			}

			@Override
			public String toString() {
				String str = theSourceId.toString();
				if (theSourceId.isPresent() && !check())
					str = "(removed) " + str;
				return str;
			}
		}

		class BoundedElement implements CollectionElement<E> {
			private final CollectionElement<E> theWrappedEl;
			private BoundedElementId theId;

			BoundedElement(CollectionElement<E> wrappedEl) {
				theWrappedEl = wrappedEl;
			}

			CollectionElement<E> getWrappedEl() {
				return theWrappedEl;
			}

			@Override
			public BoundedElementId getElementId() {
				if (theId == null)
					theId = new BoundedElementId(theWrappedEl.getElementId());
				return theId;
			}

			@Override
			public E get() {
				return theWrappedEl.get();
			}

			@Override
			public int hashCode() {
				return theWrappedEl.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (obj == this)
					return true;
				else if (!(obj instanceof BetterSubSequence.BoundedElement))
					return false;
				else
					return theWrappedEl.equals(((BoundedElement) obj).theWrappedEl);
			}

			@Override
			public String toString() {
				String str = theWrappedEl.toString();
				if (theWrappedEl.getElementId().isPresent() && !getElementId().check())
					str = "(removed) " + str;
				return str;
			}
		}

		class BoundedMutableElement extends BoundedElement implements MutableCollectionElement<E> {
			BoundedMutableElement(MutableCollectionElement<E> wrappedEl) {
				super(wrappedEl);
			}

			@Override
			MutableCollectionElement<E> getWrappedEl() {
				return (MutableCollectionElement<E>) super.getWrappedEl();
			}

			@Override
			public BetterCollection<E> getCollection() {
				return BetterSubSequence.this;
			}

			@Override
			public String isEnabled() {
				if (!getElementId().check())
					return StdMsg.UNSUPPORTED_OPERATION;
				return getWrappedEl().isEnabled();
			}

			@Override
			public String isAcceptable(E value) {
				if (!getElementId().check())
					return StdMsg.UNSUPPORTED_OPERATION;
				if (isInRange(value) != 0)
					return StdMsg.ILLEGAL_ELEMENT;
				return getWrappedEl().isAcceptable(value);
			}

			@Override
			public void set(E value) throws IllegalArgumentException, UnsupportedOperationException {
				if (!getElementId().check())
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				if (isInRange(value) != 0)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				getWrappedEl().set(value);
			}

			@Override
			public String canRemove() {
				if (!getElementId().check())
					return StdMsg.UNSUPPORTED_OPERATION;
				return getWrappedEl().canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				if (!getElementId().check())
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				getWrappedEl().remove();
			}
		}

		private class BoundedRepairListener<X> implements RepairListener<E, X> {
			private final RepairListener<E, X> theWrappedListener;

			BoundedRepairListener(RepairListener<E, X> wrapped) {
				theWrappedListener = wrapped;
			}

			@Override
			public X removed(CollectionElement<E> element) {
				// As the repair method may be called after any number of changes to the list's values,
				// we cannot assume anything about the previous state of the element, e.g. whether it was previously present in this
				// sub-sequence.
				// It is for this reason that the repair API specifies that this method may be called even for elements that were not
				// present in the sequence.
				return theWrappedListener.removed(getElement(element));
			}

			@Override
			public void disposed(E value, X data) {
				// As the repair method may be called after any number of changes to the list's values,
				// we cannot assume anything about the previous state of the element, e.g. whether it was previously present in this
				// sub-sequence.
				// It is for this reason that the repair API specifies that this method may be called even for elements that were not
				// present in the sequence.
				// Therefore, we need to inform the listener about the element by one of the 2 methods
				theWrappedListener.disposed(value, data);
			}

			@Override
			public void transferred(CollectionElement<E> element, X data) {
				if (isInRange(element.get()) == 0)
					theWrappedListener.transferred(getElement(element), data);
			}
		}
	}

	/**
	 * Implements {@link BetterSortedList#reverse()}
	 * 
	 * @param <E> The type of the list
	 */
	class ReversedSortedList<E> extends BetterList.ReversedList<E> implements BetterSortedList<E> {
		/** @param wrap The sorted list to reverse */
		public ReversedSortedList(BetterSortedList<E> wrap) {
			super(wrap);
		}

		@Override
		protected BetterSortedList<E> getWrapped() {
			return (BetterSortedList<E>) super.getWrapped();
		}

		@Override
		public Comparator<? super E> comparator() {
			return reverse(getWrapped().comparator());
		}

		/**
		 * @param <X> The type to compare
		 * @param search The comparable to reverse
		 * @return The reversed comparable
		 */
		public static <X> Comparable<X> reverse(Comparable<X> search) {
			class ReversedSearch implements Comparable<X> {
				Comparable<X> getWrapped() {
					return search;
				}

				@Override
				public int compareTo(X v) {
					return -search.compareTo(v);
				}

				@Override
				public String toString() {
					return "reverse(" + search + ")";
				}
			}
			if (search instanceof ReversedSearch)
				return ((ReversedSearch) search).getWrapped();
			return new ReversedSearch();
		}

		/**
		 * @param <X> The type to compare
		 * @param compare The comparator to reverse
		 * @return The reversed comparator
		 */
		public static <X> Comparator<X> reverse(Comparator<X> compare) {
			class ReversedCompare implements Comparator<X> {
				Comparator<X> getWrapped() {
					return compare;
				}

				@Override
				public int compare(X v1, X v2) {
					return -compare.compare(v1, v2);
				}

				@Override
				public String toString() {
					return "reverse(" + compare + ")";
				}
			}
			if (compare instanceof ReversedCompare)
				return ((ReversedCompare) compare).getWrapped();
			return new ReversedCompare();
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			int index = getWrapped().indexFor(reverse(search));
			if (index >= 0)
				return size() - index - 1;
			else {
				index = -index - 1;
				index = size() - index;
				return -(index + 1);
			}
		}

		@Override
		public CollectionElement<E> search(Comparable<? super E> search, BetterSortedList.SortedSearchFilter filter) {
			return CollectionElement.reverse(getWrapped().search(reverse(search), filter.opposite()));
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return getWrapped().isConsistent(element.reverse());
		}

		@Override
		public boolean checkConsistency() {
			return getWrapped().checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<E, X> listener) {
			RepairListener<E, X> reversedListener = listener == null ? null
				: new BetterSet.ReversedBetterSet.ReversedRepairListener<>(listener);
			return getWrapped().repair(element, reversedListener);
		}

		@Override
		public <X> boolean repair(RepairListener<E, X> listener) {
			RepairListener<E, X> reversedListener = listener == null ? null
				: new BetterSet.ReversedBetterSet.ReversedRepairListener<>(listener);
			return getWrapped().repair(reversedListener);
		}

		@Override
		public BetterSortedList<E> reverse() {
			if (BetterCollections.simplifyDuplicateOperations())
				return getWrapped();
			else
				return BetterSortedList.super.reverse();
		}
	}
}

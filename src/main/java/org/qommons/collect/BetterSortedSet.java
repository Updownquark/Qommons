package org.qommons.collect;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.Ternian;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * A {@link NavigableSet} that is also a {@link BetterSet}. BetterSortedSets are also indexable (usually in logarithmic time), so
 * BetterSortedSets are also {@link BetterList}s.
 * 
 * BetterSortedSet also contains a great deal more capability than either {@link NavigableSet} and {@link BetterList}. In particular,
 * BetterSortedSets are searchable by {@link Comparable} instead of only by value, so developers can take advantage of optimized
 * searchability based on attributes of values in the set without needing to synthesize an actual value.
 * 
 * See <a href="https://github.com/Updownquark/Qommons/wiki/BetterCollection-API#bettersortedset">the wiki</a> for more detail.
 * 
 * @param <E> The type of values in the set
 */
public interface BetterSortedSet<E> extends BetterSet<E>, BetterList<E>, NavigableSet<E> {
	/**
	 * A filter on sorted search results. The underlying code for searches in a {@link BetterSortedSet} will always return an element unless
	 * the set is empty. The value will either match the search exactly or will be adjacent to where a value matching the search exactly
	 * would be inserted.
	 * 
	 * One of these filters may be used to direct and filter the search results.
	 * 
	 * Note that this class cannot be used to exclude an exact match. This may be done by creating a {@link Comparable search} that never
	 * returns zero. For example, if a result strictly less than a search is desired, create a wrapping search that returns 1 for any values
	 * that the wrapped search returns 0 for.
	 * 
	 * @see BetterSortedSet#search(Comparable, SortedSearchFilter)
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

	/**
	 * Searches this sorted set for an element
	 *
	 * @param search The search to navigate through this set for the target value. The search must follow this set's {@link #comparator()
	 *        order}.
	 * @param filter The filter on the result
	 * @return The element that is the best found result of the search, or null if this set is empty or does not contain any element
	 *         matching the given filter
	 */
	CollectionElement<E> search(Comparable<? super E> search, SortedSearchFilter filter);

	/**
	 * Same as {@link #search(Comparable, SortedSearchFilter)} but flattens to a value
	 * 
	 * @param search The search to navigate through this set for the target value. The search must follow this set's {@link #comparator()
	 *        order}.
	 * @param filter The filter on the result
	 * @return The value that is the best found result of the search, or null if this set is empty or does not contain any element matching
	 *         the given filter
	 */
	default E searchValue(Comparable<? super E> search, SortedSearchFilter filter) {
		CollectionElement<E> el = search(search, filter);
		return el == null ? null : el.get();
	}

	@Override
	default CollectionElement<E> getOrAdd(E value, boolean first, Runnable added) {
		while (true) {
			CollectionElement<E> found = search(searchFor(value, 0), SortedSearchFilter.PreferLess);
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

	@Override
	void clear();

	@Override
	default boolean isContentControlled() {
		return true;
	}

	@Override
	default MutableElementSpliterator<E> spliterator() {
		return BetterList.super.spliterator();
	}

	@Override
	default Iterator<E> iterator() {
		return BetterList.super.iterator();
	}

	@Override
	default boolean contains(Object c) {
		return BetterList.super.contains(c);
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		return BetterList.super.containsAll(c);
	}

	@Override
	default Object[] toArray() {
		return BetterList.super.toArray();
	}

	@Override
	default BetterSortedSet<E> with(E... values) {
		BetterSet.super.with(values);
		return this;
	}

	@Override
	default BetterSortedSet<E> withAll(Collection<? extends E> values) {
		BetterSet.super.withAll(values);
		return this;
	}

	@Override
	default boolean remove(Object c) {
		return BetterList.super.remove(c);
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		return BetterList.super.removeAll(c);
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		return BetterList.super.retainAll(c);
	}

	/**
	 * Creates a {@link Comparable} to use in searching this sorted set from a value compatible with the set's comparator
	 * 
	 * @param value The comparable value
	 * @param onExact The value to return when the comparator matches. For example, to search for values strictly less than
	 *        <code>value</code>, an integer &lt;0 should be specified.
	 * @return The search to use with {@link #search(Comparable, SortedSearchFilter)}, {@link #subSet(Comparable, Comparable)}, etc.
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

	@Override
	default int indexOf(Object o) {
		return BetterList.super.indexOf(o);
	}

	@Override
	default int lastIndexOf(Object o) {
		return BetterList.super.lastIndexOf(o);
	}

	@Override
	default CollectionElement<E> getElement(E value, boolean first) {
		return search(searchFor(value, 0), SortedSearchFilter.OnlyMatch);
	}

	@Override
	default E first() {
		return getFirst();
	}

	@Override
	default E last() {
		return getLast();
	}

	@Override
	default E pollLast() {
		return BetterList.super.pollLast();
	}

	@Override
	default E pollFirst() {
		return BetterList.super.pollFirst();
	}

	@Override
	default E floor(E e) {
		CollectionElement<E> element = search(searchFor(e, 0), SortedSearchFilter.Less);
		return element == null ? null : element.get();
	}

	@Override
	default E lower(E e) {
		CollectionElement<E> element = search(searchFor(e, -1), SortedSearchFilter.Less);
		return element == null ? null : element.get();
	}

	@Override
	default E ceiling(E e) {
		CollectionElement<E> element = search(searchFor(e, 0), SortedSearchFilter.Greater);
		return element == null ? null : element.get();
	}

	@Override
	default E higher(E e) {
		CollectionElement<E> element = search(searchFor(e, 1), SortedSearchFilter.Greater);
		return element == null ? null : element.get();
	}

	/**
	 * Given a value/search, returns an interpolated value within this set's contents
	 * 
	 * @param <T> The type of value to return
	 * @param search The search to search for a position within this set's values
	 * @param onMatchOrTerminal Provides the result in the case that:
	 *        <ul>
	 *        <li>A value in this set matches the search exactly</li>
	 *        <li>The first value in this set is greater than the search</li>
	 *        <li>The last value in this set is less than the search</li>
	 *        </ul>
	 * @param interpolate Provides the result in the case that there are two adjacent values in this set, <code>v1</code> and
	 *        <code>v2</code>, such that <code>v1&lt;search && v2&gt;search</code>
	 * @param onEmpty Provides the result in the case that this set is empty
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
	 * @param search The search to search for a position within this set's values
	 * @param onMatchOrTerminal Provides the result in the case that:
	 *        <ul>
	 *        <li>An element in this set matches the search exactly</li>
	 *        <li>The first element in this set is greater than the search</li>
	 *        <li>The last element in this set is less than the search</li>
	 *        </ul>
	 * @param interpolate Provides the result in the case that there are two adjacent elements in this set whose values, <code>v1</code> and
	 *        <code>v2</code> are such that <code>v1&lt;search && v2&gt;search</code>
	 * @param onEmpty Provides the result in the case that this set is empty
	 * @return The value supplied by the appropriate function
	 */
	default <T> T betweenElements(Comparable<? super E> search, Function<? super CollectionElement<E>, ? extends T> onMatchOrTerminal,
		BiFunction<? super CollectionElement<E>, ? super CollectionElement<E>, ? extends T> interpolate, Supplier<? extends T> onEmpty) {
		CollectionElement<E> found = search(search, SortedSearchFilter.Less);
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

	@Override
	default BetterSortedSet<E> reverse() {
		return new ReversedSortedSet<>(this);
	}

	@Override
	default BetterSortedSet<E> descendingSet() {
		return reverse();
	}

	@Override
	default Iterator<E> descendingIterator() {
		return reverse().iterator();
	}

	@Override
	default BetterSortedSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return subSet(searchFor(fromElement, fromInclusive ? 0 : 1), searchFor(toElement, toInclusive ? 0 : -1));
	}

	/**
	 * @param from The (optional) lower bound for the sub-set
	 * @param to The (optional) upper bound for the sub-set
	 * @return A sub set containing all of this set's <code>value</code>s for which
	 *         <code>from.{@link Comparable#compareTo(Object) compareTo}(value)&gt;=0</code> (if <code>from</code> is specified) and
	 *         <code>to.{@link Comparable#compareTo(Object) compareTo}(value)&lt;=0</code> (if <code>to</code> is specified).
	 */
	default BetterSortedSet<E> subSet(Comparable<? super E> from, Comparable<? super E> to) {
		return new BetterSubSet<>(this, from, to);
	}

	@Override
	default BetterSortedSet<E> headSet(E toElement, boolean inclusive) {
		return subSet(null, searchFor(toElement, inclusive ? 0 : -1));
	}

	@Override
	default BetterSortedSet<E> tailSet(E fromElement, boolean inclusive) {
		return subSet(searchFor(fromElement, inclusive ? 0 : 1), null);
	}

	@Override
	default BetterSortedSet<E> subSet(E fromElement, E toElement) {
		return subSet(fromElement, true, toElement, false);
	}

	@Override
	default BetterSortedSet<E> headSet(E toElement) {
		return headSet(toElement, false);
	}

	@Override
	default BetterSortedSet<E> tailSet(E fromElement) {
		return tailSet(fromElement, true);
	}

	@Override
	default BetterSortedSet<E> subList(int fromIndex, int toIndex) {
		try (Transaction t = lock(false, true, null)) {
			// Be inclusive so that adds succeed as often as possible
			Comparable<? super E> from = fromIndex == 0 ? null : searchFor(get(fromIndex - 1), 1);
			Comparable<? super E> to = toIndex == size() ? null : searchFor(get(toIndex), -1);
			return subSet(from, to);
		}
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

	/**
	 * @param <E> The type of the set
	 * @param compare The comparator for the set
	 * @return An immutable, empty sorted set
	 */
	public static <E> BetterSortedSet<E> empty(Comparator<? super E> compare) {
		return new EmptySortedSet<>(compare);
	}

	/**
	 * Implements {@link BetterSortedSet#subSet(Comparable, Comparable)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class BetterSubSet<E> implements BetterSortedSet<E> {
		private final BetterSortedSet<E> theWrapped;

		private final Comparable<? super E> from;
		private final Comparable<? super E> to;

		/**
		 * @param set The sorted set that this sub set is for
		 * @param from The lower bound for the sub set
		 * @param to The upper bound for the sub set
		 */
		public BetterSubSet(BetterSortedSet<E> set, Comparable<? super E> from, Comparable<? super E> to) {
			theWrapped = set;
			this.from = from;
			this.to = to;
		}

		/** @return The sorted set that this is a sub-set of */
		public BetterSortedSet<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theWrapped.lock(write, structural, cause);
		}

		@Override
		public Transaction tryLock(boolean write, boolean structural, Object cause) {
			return theWrapped.tryLock(write, structural, cause);
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return theWrapped.getStamp(structuralOnly);
		}

		/** @return This sub-set's lower bound (may be null) */
		public Comparable<? super E> getFrom() {
			return from;
		}

		/** @return This sub-set's upper bound (may be null) */
		public Comparable<? super E> getTo() {
			return to;
		}

		/**
		 * @param value The value to check
		 * @return
		 *         <ul>
		 *         <li><b>0</b> if the value belongs in this set</li>
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
		 * @param search The search for this sub set
		 * @return A search to use within this sub set's {@link #getWrapped() super set} that obeys the given search within the sub-set's
		 *         bounds, but returns &lt;0 for values below and &gt;0 for values above this sub set's bounds
		 */
		protected Comparable<E> boundSearch(Comparable<? super E> search) {
			class BoundedSearch<V> implements Comparable<V> {
				private final BetterSubSet<V> theSubSet;
				private final Comparable<? super V> theSearch;

				BoundedSearch(BetterSubSet<V> subSet, Comparable<? super V> srch) {
					theSubSet = subSet;
					theSearch = srch;
				}

				@Override
				public int compareTo(V v) {
					int compare = -theSubSet.isInRange(v);
					if (compare == 0)
						compare = theSearch.compareTo(v);
					return compare;
				}

				@Override
				public String toString() {
					StringBuilder str = new StringBuilder("bounded(").append(search);
					if (theSubSet.from != null)
						str.append(", " + theSubSet.from);
					if (theSubSet.to != null)
						str.append(", " + theSubSet.to);
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

		/** @return The first index in the wrapped sorted set that is included in this set */
		protected int getMinIndex() {
			if (from == null)
				return 0;
			int index = theWrapped.indexFor(from);
			if (index >= 0)
				return index;
			else
				return -index - 1;
		}

		/** @return The last index in the wrapped */
		protected int getMaxIndex() {
			if (to == null)
				return theWrapped.size() - 1;
			int index = theWrapped.indexFor(to);
			if (index >= 0)
				return index;
			else
				return -index - 2;
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
			return Math.max(0, maxIndex - minIndex + 1); // Both minIndex and maxIndex are included here
		}

		@Override
		public boolean isEmpty() {
			int minIndex = getMinIndex();
			if (minIndex < 0)
				return true;
			int maxIndex = getMaxIndex();
			return minIndex > maxIndex; // Both minIndex and maxIndex are included here
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			int minIndex = getMinIndex();
			int maxIndex = getMaxIndex();
			if (minIndex > maxIndex)
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
			int wIndex = theWrapped.getElementsBefore(id);
			int minIdx = getMinIndex();
			if (wIndex < minIdx)
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			if (wIndex > getMaxIndex())
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return wIndex - minIdx;
		}

		@Override
		public int getElementsAfter(ElementId id) {
			int wIndex = theWrapped.getElementsBefore(id);
			int maxIdx = getMaxIndex();
			if (wIndex > maxIdx)
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			if (wIndex < getMinIndex())
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return maxIdx - wIndex;
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
			if (wrapIndex > max + 1 || (wrapIndex == max + 1 && !includeTerminus))
				throw new IndexOutOfBoundsException(index + " of " + Math.max(0, max - min + 1));
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
			try (Transaction t = lock(true, false, null)) {
				return theWrapped.set(checkIndex(index, false), element);
			}
		}

		@Override
		public String canAdd(E value, ElementId after, ElementId before) {
			if (!belongs(value))
				return StdMsg.ILLEGAL_ELEMENT;
			if (after == null && from != null)
				after = CollectionElement.getElementId(theWrapped.search(from, SortedSearchFilter.Less));
			if (before == null && to != null)
				before = CollectionElement.getElementId(theWrapped.search(to, SortedSearchFilter.Greater));
			return theWrapped.canAdd(value, after, before);
		}

		@Override
		public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			if (!belongs(value))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			if (after == null && from != null)
				after = CollectionElement.getElementId(theWrapped.search(from, SortedSearchFilter.Less));
			if (before == null && to != null)
				before = CollectionElement.getElementId(theWrapped.search(to, SortedSearchFilter.Greater));
			return theWrapped.addElement(value, after, before, first);
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
		public E remove(int index) {
			return theWrapped.remove(checkIndex(index, false));
		}

		@Override
		public CollectionElement<E> getElement(int index) {
			return theWrapped.getElement(checkIndex(index, false));
		}

		@Override
		public CollectionElement<E> getElement(E value, boolean first) {
			if (!belongs(value))
				return null;
			return theWrapped.getElement(value, first);
		}

		@Override
		public CollectionElement<E> getElement(ElementId id) {
			CollectionElement<E> el = theWrapped.getElement(id);
			if (isInRange(el.get()) != 0)
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return el;
		}

		@Override
		public CollectionElement<E> getTerminalElement(boolean first) {
			CollectionElement<E> wrapTerminal;
			if (first) {
				if (from == null)
					wrapTerminal = theWrapped.getTerminalElement(true);
				else
					wrapTerminal = theWrapped.search(from, SortedSearchFilter.PreferGreater);
			} else {
				if (to == null)
					wrapTerminal = theWrapped.getTerminalElement(false);
				else
					wrapTerminal = theWrapped.search(to, SortedSearchFilter.PreferLess);
			}
			if (wrapTerminal == null)
				return null;
			else if (from != null && from.compareTo(wrapTerminal.get()) > 0)
				return null;
			else if (to != null && to.compareTo(wrapTerminal.get()) < 0)
				return null;
			else
				return wrapTerminal;
		}

		@Override
		public CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next) {
			CollectionElement<E> el = theWrapped.getAdjacentElement(elementId, next);
			if (el == null || isInRange(el.get()) != 0)
				return null;
			return el;
		}

		@Override
		public MutableCollectionElement<E> mutableElement(ElementId id) {
			MutableCollectionElement<E> el = theWrapped.mutableElement(id);
			if (isInRange(el.get()) != 0)
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return new BoundedMutableElement(el);
		}

		@Override
		public MutableElementSpliterator<E> spliterator(ElementId element, boolean asNext) {
			try (Transaction t = lock(false, null)) {
				if (isInRange(theWrapped.getElement(element).get()) != 0)
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
				return new BoundedMutableSpliterator(theWrapped.spliterator(element, asNext));
			}
		}

		@Override
		public CollectionElement<E> search(Comparable<? super E> search, SortedSearchFilter filter) {
			CollectionElement<E> wrapResult = theWrapped.search(boundSearch(search), filter);
			if (wrapResult == null)
				return null;
			int range = isInRange(wrapResult.get());
			if (range == 0)
				return wrapResult;
			if (filter.strict)
				return null;
			return getTerminalElement(range < 0);
		}

		@Override
		public BetterSortedSet<E> subSet(Comparable<? super E> innerFrom, Comparable<? super E> innerTo) {
			return new BetterSubSet<>(theWrapped, and(from, innerFrom, true), and(to, innerTo, false));
		}

				@Override
		public MutableElementSpliterator<E> spliterator(boolean fromStart) {
			MutableElementSpliterator<E> wrapSpliter;
			if (fromStart) {
				if (from == null)
					wrapSpliter = theWrapped.spliterator(true);
				else {
					CollectionElement<E> element = theWrapped.search(from, SortedSearchFilter.PreferGreater);
					if (element == null)
						wrapSpliter = theWrapped.spliterator(true);
					else
						wrapSpliter = theWrapped.spliterator(element.getElementId(), from.compareTo(element.get()) <= 0);
				}
			} else {
				if (to == null)
					wrapSpliter = theWrapped.spliterator(false);
				else {
					CollectionElement<E> element = theWrapped.search(to, SortedSearchFilter.PreferLess);
					if (element == null)
						wrapSpliter = theWrapped.spliterator(false);
					else
						wrapSpliter = theWrapped.spliterator(element.getElementId(), !(to.compareTo(element.get()) >= 0));
				}
			}
			return new BoundedMutableSpliterator(wrapSpliter);
		}

		@Override
		public boolean removeLast(Object o) {
			if ((o != null && !theWrapped.belongs(o)) || isInRange((E) o) != 0)
				return false;
			return theWrapped.removeLast(o);
		}

		@Override
		public void clear() {
			CollectionElement<E> bound = from == null ? null : theWrapped.search(from, SortedSearchFilter.Less);
			if (bound == null)
				bound = to == null ? null : theWrapped.search(to, SortedSearchFilter.Greater);
			if (bound == null) // This sub set contains all of the super set's elements
				theWrapped.clear();
			else
				removeIf(v -> true);
		}

		@Override
		public CollectionElement<E> searchWithConsistencyDetection(E value, boolean[] invalid) {
			CollectionElement<E> found = theWrapped.searchWithConsistencyDetection(value, invalid);
			if (found == null || isInRange(found.get()) != 0)
				return null;
			return found;
		}

		@Override
		public boolean checkConsistency() {
			return theWrapped.checkConsistency();
		}

		@Override
		public <X> boolean repair(RepairListener<E, X> listener) {
			RepairListener<E, X> subListener = new RepairListener<E, X>() {
				@Override
				public void removed(CollectionElement<E> element) {
					// As the repair method may be called after any number of changes to the set's values,
					// we cannot assume anything about the previous state of the element, e.g. whether it was previously present in this
					// sub-set.
					// It is for this reason that the repair API specifies that this method may be called even for elements that were not
					// present in the set.
					listener.removed(element);
				}

				@Override
				public X preTransfer(CollectionElement<E> element) {
					// As the repair method may be called after any number of changes to the set's values,
					// we cannot assume anything about the previous state of the element, e.g. whether it was previously present in this
					// sub-set.
					// It is for this reason that the repair API specifies that this method may be called even for elements that were not
					// present in the set.
					// Therefore, we need to inform the listener about the element by one of the 2 methods
					if (isInRange(element.get()) == 0)
						return listener.preTransfer(element);
					else {
						listener.removed(element);
						return null;
					}
				}

				@Override
				public void postTransfer(CollectionElement<E> element, X data) {
					if (isInRange(element.get()) == 0)
						listener.postTransfer(element, data);
				}
			};
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

		private class BoundedMutableSpliterator extends MutableElementSpliterator.SimpleMutableSpliterator<E> {
			private final MutableElementSpliterator<E> theWrappedSpliter;

			BoundedMutableSpliterator(MutableElementSpliterator<E> wrappedSpliter) {
				super(BetterSubSet.this);
				theWrappedSpliter = wrappedSpliter;
			}

			@Override
			public long estimateSize() {
				return theWrappedSpliter.estimateSize();
			}

			@Override
			public long getExactSizeIfKnown() {
				return theWrappedSpliter.getExactSizeIfKnown();
			}

			@Override
			public int characteristics() {
				return DISTINCT | ORDERED | SORTED;
			}

			@Override
			public Comparator<? super E> getComparator() {
				return theWrappedSpliter.getComparator();
			}

			@Override
			protected boolean internalForElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward) {
				boolean[] success = new boolean[1];
				if (theWrappedSpliter.forElementM(el -> {
					if (isInRange(el.get()) == 0) {
						success[0] = true;
						action.accept(new BoundedMutableElement(el));
					}
				}, forward) && !success[0]) {
					// If there was a super-set element that was not in range, need to back up back to the last in-range element
					theWrappedSpliter.forElement(v -> {
					}, !forward);
				}
				return success[0];
			}

			@Override
			protected boolean internalForElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
				boolean[] success = new boolean[1];
				if (theWrappedSpliter.forElement(el -> {
					if (isInRange(el.get()) == 0) {
						success[0] = true;
						action.accept(el);
					}
				}, forward) && !success[0]) {
					// If there was a super-set element that was not in range, need to back up back to the last in-range element
					theWrappedSpliter.forElement(v -> {
					}, !forward);
				}
				return success[0];
			}

			@Override
			public MutableElementSpliterator<E> trySplit() {
				MutableElementSpliterator<E> wrapSplit = theWrappedSpliter.trySplit();
				return wrapSplit == null ? null : new BoundedMutableSpliterator(wrapSplit);
			}
		}

		class BoundedMutableElement implements MutableCollectionElement<E> {
			private final MutableCollectionElement<E> theWrappedEl;

			BoundedMutableElement(MutableCollectionElement<E> wrappedEl) {
				theWrappedEl = wrappedEl;
			}

			@Override
			public BetterCollection<E> getCollection() {
				return BetterSubSet.this;
			}

			@Override
			public ElementId getElementId() {
				return theWrappedEl.getElementId();
			}

			@Override
			public E get() {
				return theWrappedEl.get();
			}

			@Override
			public String isEnabled() {
				return theWrappedEl.isEnabled();
			}

			@Override
			public String isAcceptable(E value) {
				if (isInRange(value) != 0)
					return StdMsg.ILLEGAL_ELEMENT;
				return theWrappedEl.isAcceptable(value);
			}

			@Override
			public void set(E value) throws IllegalArgumentException, UnsupportedOperationException {
				if (isInRange(value) != 0)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				theWrappedEl.set(value);
			}

			@Override
			public String canRemove() {
				return theWrappedEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theWrappedEl.remove();
			}

			@Override
			public String toString() {
				return theWrappedEl.toString();
			}
		}
	}

	/**
	 * Implements {@link BetterSortedSet#reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class ReversedSortedSet<E> extends ReversedList<E> implements BetterSortedSet<E> {
		/** @param wrap The sorted set to reverse */
		public ReversedSortedSet(BetterSortedSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected BetterSortedSet<E> getWrapped() {
			return (BetterSortedSet<E>) super.getWrapped();
		}

		@Override
		public Comparator<? super E> comparator() {
			return getWrapped().comparator().reversed();
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

		private static <X> Comparable<X> reverse(Comparable<X> compare) {
			return v -> -compare.compareTo(v);
		}

		@Override
		public CollectionElement<E> search(Comparable<? super E> search, SortedSearchFilter filter) {
			return CollectionElement.reverse(getWrapped().search(reverse(search), filter.opposite()));
		}

		@Override
		public BetterSortedSet<E> reverse() {
			return getWrapped();
		}

		@Override
		public CollectionElement<E> searchWithConsistencyDetection(E value, boolean[] invalid) {
			return CollectionElement.reverse(getWrapped().searchWithConsistencyDetection(value, invalid));
		}

		@Override
		public boolean checkConsistency() {
			return getWrapped().checkConsistency();
		}

		@Override
		public <X> boolean repair(RepairListener<E, X> listener) {
			RepairListener<E, X> reversedListener = listener == null ? null : new RepairListener<E, X>() {
				@Override
				public void removed(CollectionElement<E> element) {
					listener.removed(element.reverse());
				}

				@Override
				public X preTransfer(CollectionElement<E> element) {
					return listener.preTransfer(element.reverse());
				}

				@Override
				public void postTransfer(CollectionElement<E> element, X data) {
					listener.postTransfer(element.reverse(), data);
				}
			};
			return getWrapped().repair(reversedListener);
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
	}

	/**
	 * Implements {@link BetterSortedSet#empty(Comparator)}
	 * 
	 * @param <E> The type of the set
	 */
	public static class EmptySortedSet<E> extends BetterList.EmptyList<E> implements BetterSortedSet<E> {
		private final Comparator<? super E> theCompare;

		EmptySortedSet(Comparator<? super E> compare) {
			theCompare = compare;
		}

		@Override
		public Comparator<? super E> comparator() {
			return theCompare;
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			return -1;
		}

		@Override
		public CollectionElement<E> search(Comparable<? super E> search, SortedSearchFilter filter) {
			return null;
		}

		@Override
		public CollectionElement<E> searchWithConsistencyDetection(E value, boolean[] invalid) {
			return null;
		}

		@Override
		public boolean checkConsistency() {
			return false;
		}

		@Override
		public <X> boolean repair(org.qommons.collect.BetterSet.RepairListener<E, X> listener) {
			return false;
		}
	}
}

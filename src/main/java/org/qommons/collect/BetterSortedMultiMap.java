package org.qommons.collect;

import java.util.Comparator;

import org.qommons.Transaction;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public interface BetterSortedMultiMap<K, V> extends BetterMultiMap<K, V>, SortedMultiMap<K, V> {
	@Override
	BetterSortedSet<K> keySet();

	@Override
	BetterSortedSet<? extends MultiEntry<K, V>> entrySet();

	default Comparator<? super K> comparator() {
		return keySet().comparator();
	}

	/**
	 * Searches this sorted map for a value
	 *
	 * @param search The search to navigate through this map for the target key. The search must follow this map's {@link #comparator()
	 *        order}.
	 * @param filter The filter on the result
	 * @return The result of the search, or null if no such value was found
	 */
	MultiEntryHandle<K, V> search(Comparable<? super K> search, SortedSearchFilter filter);

	@Override
	default MultiEntryHandle<K, V> lowerEntry(K key) {
		return search(keySet().searchFor(key, -1), SortedSearchFilter.Less);
	}

	@Override
	default MultiEntryHandle<K, V> floorEntry(K key) {
		return search(keySet().searchFor(key, 0), SortedSearchFilter.Less);
	}

	@Override
	default MultiEntryHandle<K, V> ceilingEntry(K key) {
		return search(keySet().searchFor(key, 0), SortedSearchFilter.Greater);
	}

	@Override
	default MultiEntryHandle<K, V> higherEntry(K key) {
		return search(keySet().searchFor(key, 1), SortedSearchFilter.Greater);
	}

	@Override
	default MultiEntryHandle<K, V> firstEntry() {
		return search(k -> -1, SortedSearchFilter.PreferGreater);
	}

	@Override
	default MultiEntryHandle<K, V> lastEntry() {
		return search(k -> 1, SortedSearchFilter.PreferGreater);
	}

	@Override
	default BetterSortedMultiMap<K, V> reverse() {
		return new ReversedSortedMultiMap<>(this);
	}

	@Override
	default BetterSortedMultiMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
		return subMap(k -> {
			int compare = comparator().compare(fromKey, k);
			if (!fromInclusive && compare == 0)
				compare = 1;
			return compare;
		}, k -> {
			int compare = comparator().compare(toKey, k);
			if (!toInclusive && compare == 0)
				compare = -1;
			return compare;
		});
	}

	@Override
	default SortedMultiMap<K, V> headMap(K high, boolean highIncluded) {
		return subMap(null, k -> {
			int compare = comparator().compare(high, k);
			if (!highIncluded && compare == 0)
				compare = -1;
			return compare;
		});
	}

	@Override
	default SortedMultiMap<K, V> tailMap(K low, boolean lowIncluded) {
		return subMap(k -> {
			int compare = comparator().compare(low, k);
			if (!lowIncluded && compare == 0)
				compare = 1;
			return compare;
		}, null);
	}

	default BetterSortedMultiMap<K, V> subMap(Comparable<? super K> from, Comparable<? super K> to) {
		return new BetterSubMultiMap<>(this, from, to);
	}

	class ReversedSortedMultiMap<K, V> extends ReversedMultiMap<K, V> implements BetterSortedMultiMap<K, V> {
		public ReversedSortedMultiMap(BetterSortedMultiMap<K, V> source) {
			super(source);
		}

		@Override
		protected BetterSortedMultiMap<K, V> getSource() {
			return (BetterSortedMultiMap<K, V>) super.getSource();
		}

		@Override
		public BetterSortedSet<K> keySet() {
			return getSource().keySet().reverse();
		}

		@Override
		public BetterCollection<V> get(Object key) {
			return getSource().get(key).reverse();
		}

		@Override
		public BetterSortedSet<? extends MultiEntry<K, V>> entrySet() {
			// FIXME This is incorrect--each entry also needs to be reversed
			return getSource().entrySet().reverse();
		}

		@Override
		public MultiEntryHandle<K, V> search(Comparable<? super K> search, SortedSearchFilter filter) {
			return MultiEntryHandle.reverse(getSource().search(v -> -search.compareTo(v), filter.opposite()));
		}

		@Override
		public BetterSortedMultiMap<K, V> reverse() {
			return (BetterSortedMultiMap<K, V>) super.reverse();
		}
	}

	class BetterSubMultiMap<K, V> implements BetterSortedMultiMap<K, V> {
		private final BetterSortedMultiMap<K, V> theWrapped;
		private final Comparable<? super K> theLowerBound;
		private final Comparable<? super K> theUpperBound;

		private final BetterSortedSet<K> theKeySet;

		public BetterSubMultiMap(BetterSortedMultiMap<K, V> wrapped, Comparable<? super K> lowerBound, Comparable<? super K> upperBound) {
			theWrapped = wrapped;
			theLowerBound = lowerBound;
			theUpperBound = upperBound;

			theKeySet = wrapped.keySet().subSet(theLowerBound, theUpperBound);
		}

		protected BetterSortedMultiMap<K, V> getWrapped() {
			return theWrapped;
		}

		protected Comparable<? super K> getLowerBound() {
			return theLowerBound;
		}

		protected Comparable<? super K> getUpperBound() {
			return theUpperBound;
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theWrapped.lock(write, structural, cause);
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return theWrapped.getStamp(structuralOnly);
		}

		@Override
		public BetterSortedSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public BetterSortedSet<? extends MultiEntry<K, V>> entrySet() {
			return theWrapped.entrySet().subSet(//
				entry -> theLowerBound.compareTo(entry.getKey()), //
				entry -> theUpperBound.compareTo(entry.getKey()));
		}

		@Override
		public BetterCollection<V> get(Object key) {
			if (!keySet().belongs(key))
				return BetterCollection.empty();
			return theWrapped.get(key);
		}

		@Override
		public MultiMapEntryHandle<K, V> putEntry(K key, V value, boolean first) {
			if (!theKeySet.belongs(key))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			return theWrapped.putEntry(key, value, first);
		}

		@Override
		public MultiEntryHandle<K, V> getEntry(K key) {
			if (!theKeySet.belongs(key))
				return null;
			return theWrapped.getEntry(key);
		}

		@Override
		public MultiEntryHandle<K, V> getEntry(ElementId entryId) {
			MultiEntryHandle<K, V> entry = theWrapped.getEntry(entryId);
			if (!theKeySet.belongs(entry.getKey()))
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return entry;
		}

		public int isInRange(K value) {
			if (theLowerBound != null && theLowerBound.compareTo(value) > 0)
				return -1;
			if (theUpperBound != null && theUpperBound.compareTo(value) < 0)
				return 1;
			return 0;
		}

		protected Comparable<K> boundSearch(Comparable<? super K> search) {
			return v -> {
				int compare = isInRange(v);
				if (compare == 0)
					compare = search.compareTo(v);
				return compare;
			};
		}

		@Override
		public MultiEntryHandle<K, V> search(Comparable<? super K> search, SortedSearchFilter filter) {
			return theWrapped.search(boundSearch(search), filter);
		}
	}
}

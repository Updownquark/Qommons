package org.qommons.collect;

import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.function.Function;

import org.qommons.Identifiable;
import org.qommons.Transaction;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * A {@link NavigableMap} that provides access to its entries by ID.
 * 
 * {@link BetterSortedSet} also provides enhanced searchability over {@link NavigableMap}, similarly to {@link BetterSortedSet}.
 * 
 * See <a href="https://github.com/Updownquark/Qommons/wiki/BetterMap-API#bettersortedmap">the wiki</a> for more detail.
 * 
 * @param <K> The key type for the map
 * @param <V> The value type for the map
 */
public interface BetterSortedMap<K, V> extends BetterMap<K, V>, NavigableMap<K, V> {
	@Override
	BetterSortedSet<K> keySet();

	@Override
	default BetterSortedSet<Map.Entry<K, V>> entrySet() {
		return new BetterSortedEntrySet<>(this);
	}

	@Override
	default BetterCollection<V> values() {
		return BetterMap.super.values();
	}

	@Override
	default Comparator<? super K> comparator() {
		return keySet().comparator();
	}

	/**
	 * Searches this sorted map's keys for a value
	 *
	 * @param search The search to navigate through this map for the target key. The search must follow this map's {@link #comparator()
	 *        order}.
	 * @param filter The filter on the result
	 * @return The result of the search, or null if no such value was found
	 */
	default MapEntryHandle<K, V> search(Comparable<? super K> search, SortedSearchFilter filter) {
		return searchEntries(//
			entry -> search.compareTo(entry.getKey()), filter);
	}

	/**
	 * Searches this sorted map's entries for a value
	 *
	 * @param search The search to navigate through this map for the target entry. The search must follow this map's
	 *        key-{@link #comparator() order}.
	 * @param filter The filter on the result
	 * @return The result of the search, or null if no such value was found
	 */
	MapEntryHandle<K, V> searchEntries(Comparable<? super Map.Entry<K, V>> search, SortedSearchFilter filter);

	@Override
	default MapEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, boolean first, Runnable added) {
		// Don't lock initially. If we can find it optimistically, we'll do that.
		MapEntryHandle<K, V> found = search(keySet().searchFor(key, 0), SortedSearchFilter.PreferLess);
		int compare = 0;
		if (found != null) {
			compare = comparator().compare(key, found.getKey());
			if (compare == 0)
				return found;
		}
		// Key is not present
		boolean newEntry = false;
		try (Transaction t = lock(true, null)) {
			if (found != null && found.getElementId().isPresent()) {
				// Get the comparison again in case the element's value was replaced
				compare = comparator().compare(key, found.getKey());
				if (compare != 0) {
					CollectionElement<K> adjacent = keySet().getAdjacentElement(found.getElementId(), compare < 0);
					if (adjacent == null) {
						found = putEntry(key, value.apply(key), //
							compare < 0 ? null : found.getElementId(), //
							compare < 0 ? found.getElementId() : null, //
							compare > 0);
						newEntry = true;
					} else {
						int adjCompare = comparator().compare(key, adjacent.get());
						if (adjCompare == 0) {
							found = getEntryById(adjacent.getElementId());
							compare = 0;
						} else if ((adjCompare < 0) == (compare < 0)) {
							// Multiple elements have been added since we got the lock. Do the search again.
							found = search(keySet().searchFor(key, 0), SortedSearchFilter.PreferLess);
							compare = comparator().compare(key, found.getKey());
						} else {
							found = putEntry(key, value.apply(key), //
								compare < 0 ? adjacent.getElementId() : found.getElementId(), //
								compare < 0 ? found.getElementId() : adjacent.getElementId(), //
								compare > 0);
							newEntry = true;
						}
					}
				}
			} else {
				// The map was null (see if it still is) or the found element was removed (do the search again).
				found = search(keySet().searchFor(key, 0), SortedSearchFilter.PreferLess);
				if (found == null) {
					// The map is still null. Add the first entry.
					found = putEntry(key, value.apply(key), first);
					newEntry = true;
				} else {
					compare = comparator().compare(key, found.getKey());
				}
			}
			if (!newEntry && compare != 0) {
				found = putEntry(key, value.apply(key), //
					compare < 0 ? null : found.getElementId(), //
					compare < 0 ? found.getElementId() : null, //
					false);
				newEntry = true;
			}
			if (found != null && newEntry && added != null)
				added.run();
			return found;
		}
	}

	@Override
	default K firstKey() {
		return keySet().first();
	}

	@Override
	default K lastKey() {
		return keySet().last();
	}

	@Override
	default Map.Entry<K, V> lowerEntry(K key) {
		return search(keySet().searchFor(key, -1), SortedSearchFilter.Less);
	}

	@Override
	default K lowerKey(K key) {
		return keyOf(lowerEntry(key));
	}

	/**
	 * @param <K> The key type of the entry
	 * @param entry The entry to get the key of
	 * @return The entry's key, or null if entry is null
	 */
	static <K> K keyOf(Map.Entry<K, ?> entry) {
		return entry == null ? null : entry.getKey();
	}

	@Override
	default Map.Entry<K, V> floorEntry(K key) {
		return search(keySet().searchFor(key, 0), SortedSearchFilter.Less);
	}

	@Override
	default K floorKey(K key) {
		return keyOf(floorEntry(key));
	}

	@Override
	default Map.Entry<K, V> ceilingEntry(K key) {
		return search(keySet().searchFor(key, 0), SortedSearchFilter.Greater);
	}

	@Override
	default K ceilingKey(K key) {
		return keyOf(ceilingEntry(key));
	}

	@Override
	default Map.Entry<K, V> higherEntry(K key) {
		return search(keySet().searchFor(key, 1), SortedSearchFilter.Greater);
	}

	@Override
	default K higherKey(K key) {
		return keyOf(higherEntry(key));
	}

	@Override
	default Map.Entry<K, V> firstEntry() {
		return search(k -> -1, SortedSearchFilter.PreferGreater);
	}

	@Override
	default Map.Entry<K, V> lastEntry() {
		return search(k -> 1, SortedSearchFilter.PreferGreater);
	}

	@Override
	default Map.Entry<K, V> pollFirstEntry() {
		MapEntryHandle<K, V> handle = search(v -> -1, SortedSearchFilter.PreferLess);
		if (handle != null) {
			Map.Entry<K, V> result = new ImmutableMapEntry<>(handle.getKey(), handle.get());
			forMutableEntry(handle.getElementId(), el -> el.remove());
			return result;
		}
		return null;
	}

	@Override
	default Map.Entry<K, V> pollLastEntry() {
		MapEntryHandle<K, V> handle = search(v -> 1, SortedSearchFilter.PreferLess);
		if (handle != null) {
			Map.Entry<K, V> result = new ImmutableMapEntry<>(handle.getKey(), handle.get());
			forMutableEntry(handle.getElementId(), el -> el.remove());
			return result;
		}
		return null;
	}

	@Override
	default BetterSortedMap<K, V> reverse() {
		return new ReversedSortedMap<>(this);
	}

	@Override
	default BetterSortedMap<K, V> descendingMap() {
		return reverse();
	}

	@Override
	default BetterSortedSet<K> navigableKeySet() {
		return keySet();
	}

	@Override
	default BetterSortedSet<K> descendingKeySet() {
		return keySet().reverse();
	}

	@Override
	default BetterSortedMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
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

	/**
	 * @param from The lower bound for the sub-map
	 * @param to The upper bound for the sub-map
	 * @return A {@link BetterSortedMap} with the all of this map's entries whose keys are <code>&gt;=from && &lt;=to</code>
	 */
	default BetterSortedMap<K, V> subMap(Comparable<? super K> from, Comparable<? super K> to) {
		return new BetterSubMap<>(this, from, to);
	}

	@Override
	default BetterSortedMap<K, V> headMap(K toKey, boolean inclusive) {
		return subMap(null, k -> {
			int compare = comparator().compare(toKey, k);
			if (!inclusive && compare == 0)
				compare = -1;
			return compare;
		});
	}

	@Override
	default BetterSortedMap<K, V> tailMap(K fromKey, boolean inclusive) {
		return subMap(k -> {
			int compare = comparator().compare(fromKey, k);
			if (!inclusive && compare == 0)
				compare = 1;
			return compare;
		}, null);
	}

	@Override
	default BetterSortedMap<K, V> subMap(K fromKey, K toKey) {
		return subMap(fromKey, true, toKey, false);
	}

	@Override
	default BetterSortedMap<K, V> headMap(K toKey) {
		return headMap(toKey, false);
	}

	@Override
	default BetterSortedMap<K, V> tailMap(K fromKey) {
		return tailMap(fromKey, true);
	}

	@Override
	default BetterSortedMap<K, V> with(K key, V value) {
		return (BetterSortedMap<K, V>) BetterMap.super.with(key, value);
	}

	@Override
	default BetterSortedMap<K, V> withAll(Map<? extends K, ? extends V> values) {
		return (BetterSortedMap<K, V>) BetterMap.super.withAll(values);
	}

	/**
	 * A map entry whose {@link java.util.Map.Entry#setValue(Object) setValue} method is disabled
	 * 
	 * @param <K>
	 * @param <V>
	 */
	class ImmutableMapEntry<K, V> implements Map.Entry<K, V> {
		private final K theKey;
		private final V theValue;

		public ImmutableMapEntry(K key, V value) {
			theKey = key;
			theValue = value;
		}

		@Override
		public K getKey() {
			return theKey;
		}

		@Override
		public V getValue() {
			return theValue;
		}

		@Override
		public V setValue(V value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theKey);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof Map.Entry && Objects.equals(theKey, ((Map.Entry<?, ?>) obj).getKey());
		}

		@Override
		public String toString() {
			return theKey + "=" + theValue;
		}
	}

	/**
	 * Implements {@link BetterSortedMap#reverse()}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ReversedSortedMap<K, V> extends ReversedMap<K, V> implements BetterSortedMap<K, V> {
		public ReversedSortedMap(BetterSortedMap<K, V> wrapped) {
			super(wrapped);
		}

		@Override
		protected BetterSortedMap<K, V> getWrapped() {
			return (BetterSortedMap<K, V>) super.getWrapped();
		}

		@Override
		public BetterSortedSet<K> keySet() {
			return getWrapped().keySet().reverse();
		}

		@Override
		public MapEntryHandle<K, V> searchEntries(Comparable<? super Map.Entry<K, V>> search, SortedSearchFilter filter) {
			return getWrapped().searchEntries(v -> -search.compareTo(v), filter.opposite());
		}
	}

	/**
	 * A default entry set for a {@link BetterSortedMap}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class BetterSortedEntrySet<K, V> extends BetterEntrySet<K, V> implements BetterSortedSet<Map.Entry<K, V>> {
		public BetterSortedEntrySet(BetterSortedMap<K, V> map) {
			super(map);
		}

		@Override
		protected BetterSortedMap<K, V> getMap() {
			return (BetterSortedMap<K, V>) super.getMap();
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return getMap().keySet().getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return getMap().keySet().getElementsAfter(id);
		}

		@Override
		public CollectionElement<Map.Entry<K, V>> getElement(int index) {
			return getElement(getMap().keySet().getElement(index).getElementId());
		}

		@Override
		public CollectionElement<Entry<K, V>> getAdjacentElement(ElementId elementId, boolean next) {
			CollectionElement<K> keyEl = getMap().keySet().getAdjacentElement(elementId, next);
			return keyEl == null ? null : getElement(keyEl.getElementId());
		}

		@Override
		public CollectionElement<Map.Entry<K, V>> search(Comparable<? super Entry<K, V>> search, SortedSearchFilter filter) {
			MapEntryHandle<K, V> result = getMap().searchEntries(search, filter);
			if (result == null)
				return null;
			return getElement(result.getElementId());
		}

		@Override
		public Comparator<? super Map.Entry<K, V>> comparator() {
			return (e1, e2) -> getMap().keySet().comparator().compare(e1.getKey(), e2.getKey());
		}

		protected Comparable<? super K> keyCompare(Comparable<? super Map.Entry<K, V>> entryCompare) {
			return k -> entryCompare.compareTo(new ImmutableMapEntry<K, V>(k, null));
		}

		@Override
		public int indexFor(Comparable<? super Map.Entry<K, V>> search) {
			return getMap().keySet().indexFor(keyCompare(search));
		}
	}

	/**
	 * Implements {@link BetterSortedMap#subMap(Comparable, Comparable)}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class BetterSubMap<K, V> implements BetterSortedMap<K, V> {
		private final BetterSortedMap<K, V> theSource;
		private final Comparable<? super K> theLowerBound;
		private final Comparable<? super K> theUpperBound;

		private final BetterSortedSet<K> theKeySet;
		private Object theIdentity;

		public BetterSubMap(BetterSortedMap<K, V> source, Comparable<? super K> lowerBound, Comparable<? super K> upperBound) {
			theSource = source;
			theLowerBound = lowerBound;
			theUpperBound = upperBound;

			theKeySet = source.keySet().subSet(theLowerBound, theUpperBound);
		}

		protected BetterSortedMap<K, V> getSource() {
			return theSource;
		}

		protected Comparable<? super K> getLowerBound() {
			return theLowerBound;
		}

		protected Comparable<? super K> getUpperBound() {
			return theUpperBound;
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.wrap(theSource.getIdentity(), "subMap", theLowerBound, theUpperBound);
			return theIdentity;
		}

		@Override
		public BetterSortedSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public MapEntryHandle<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
			if (!theKeySet.belongs(key))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			return theSource.putEntry(key, value, after, before, first);
		}

		@Override
		public MapEntryHandle<K, V> getEntry(K key) {
			if (!theKeySet.belongs(key))
				return null;
			return theSource.getEntry(key);
		}

		@Override
		public MapEntryHandle<K, V> getEntryById(ElementId entryId) {
			MapEntryHandle<K, V> entry = theSource.getEntryById(entryId);
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

		protected Comparable<Map.Entry<K, V>> boundSearch(Comparable<? super Map.Entry<K, V>> search) {
			return entry -> {
				int compare = isInRange(entry.getKey());
				if (compare == 0)
					compare = search.compareTo(entry);
				return compare;
			};
		}

		@Override
		public MapEntryHandle<K, V> searchEntries(Comparable<? super Map.Entry<K, V>> search, SortedSearchFilter filter) {
			return theSource.searchEntries(boundSearch(search), filter);
		}

		@Override
		public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
			MutableMapEntryHandle<K, V> entry = theSource.mutableEntry(entryId);
			if (!keySet().belongs(entry.getKey()))
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return entry;
		}

		@Override
		public int hashCode() {
			return BetterCollection.hashCode(entrySet());
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof Map))
				return false;
			return BetterCollection.equals(entrySet(), ((Map<?, ?>) obj).entrySet());
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}
	}
}

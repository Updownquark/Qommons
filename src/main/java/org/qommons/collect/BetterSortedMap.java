package org.qommons.collect;

import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

import org.qommons.Identifiable;
import org.qommons.Transaction;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
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

	@Override
	default OrderedMapEntry<K, V> putEntry(K key, V value, boolean first) {
		return (OrderedMapEntry<K, V>) BetterMap.super.putEntry(key, value, first);
	}

	@Override
	default OrderedMapEntry<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
		return (OrderedMapEntry<K, V>) BetterMap.super.putEntry(key, value, after, before, first);
	}

	@Override
	OrderedMapEntry<K, V> getEntry(K key);

	@Override
	OrderedMapEntry<K, V> getEntryById(ElementId entryId);

	@Override
	default OrderedMapEntry<K, V> getTerminalEntry(boolean first) {
		return (OrderedMapEntry<K, V>) BetterMap.super.getTerminalEntry(first);
	}

	@Override
	MutableOrderedMapEntry<K, V> mutableEntry(ElementId entryId);

	/**
	 * Searches this sorted map's keys for a value
	 *
	 * @param search The search to navigate through this map for the target key. The search must follow this map's {@link #comparator()
	 *        order}.
	 * @param filter The filter on the result
	 * @return The result of the search, or null if no such value was found
	 */
	default OrderedMapEntry<K, V> search(Comparable<? super K> search, BetterSortedList.SortedSearchFilter filter) {
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
	OrderedMapEntry<K, V> searchEntries(Comparable<? super Map.Entry<K, V>> search, BetterSortedList.SortedSearchFilter filter);

	@Override
	default OrderedMapEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId after, ElementId before,
		boolean first, Runnable preAdd, Runnable postAdd) {
		if (after != null || before != null) {
			// If the given elements constrain the search space, we can probably be faster than the general method below
			try (Transaction t = lock(true, null)) {
				ElementId best = first ? after : before;
				ElementId worst = first ? before : after;
				if (best != null) {
					OrderedMapEntry<K, V> bestEntry = getEntryById(best);
					int comp = comparator().compare(key, bestEntry.getKey());
					if ((comp < 0) == first)
						throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
					while (true) {
						if (comp == 0)
							return bestEntry;
						bestEntry = bestEntry.getAdjacent(first);
						if (bestEntry == null || (worst != null && (bestEntry.getElementId().compareTo(worst) > 0) == first))
							break;
						comp = comparator().compare(key, bestEntry.getKey());
						if ((comp < 0) == first) {
							if (preAdd != null)
								preAdd.run();
							OrderedMapEntry<K, V> addedEntry = putEntry(key, value.apply(key), //
								first ? null : bestEntry.getElementId(), //
								first ? bestEntry.getElementId() : null, first);
							if (postAdd != null)
								postAdd.run();
							return addedEntry;
						}
					}
					if (worst == null) {
						if (preAdd != null)
							preAdd.run();
						OrderedMapEntry<K, V> addedEntry = putEntry(key, value.apply(key), //
							first ? getTerminalEntry(false).getElementId() : null, //
							first ? null : getTerminalEntry(true).getElementId(), !first);
						if (postAdd != null)
							postAdd.run();
						return addedEntry;
					}
				} else {
					OrderedMapEntry<K, V> worstEntry = getEntryById(worst);
					int comp = comparator().compare(key, worstEntry.getKey());
					if ((comp > 0) == first)
						throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
					while (true) {
						if (comp == 0)
							return worstEntry;
						worstEntry = worstEntry.getAdjacent(!first);
						if (worstEntry == null)
							break;
						comp = comparator().compare(key, worstEntry.getKey());
						if ((comp > 0) == first) {
							if (preAdd != null)
								preAdd.run();
							OrderedMapEntry<K, V> addedEntry = putEntry(key, value.apply(key), //
								first ? worstEntry.getElementId() : null, //
								first ? null : worstEntry.getElementId(), !first);
							if (postAdd != null)
								postAdd.run();
							return addedEntry;
						}
					}
					if (preAdd != null)
						preAdd.run();
					OrderedMapEntry<K, V> addedEntry = putEntry(key, value.apply(key), //
						first ? getTerminalEntry(true).getElementId() : null, //
						first ? null : getTerminalEntry(false).getElementId(), first);
					if (postAdd != null)
						postAdd.run();
					return addedEntry;
				}
			}
		}
		// Don't lock initially. If we can find it optimistically, we'll do that.
		OrderedMapEntry<K, V> found = search(keySet().searchFor(key, 0), BetterSortedList.SortedSearchFilter.PreferLess);
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
					OrderedMapEntry<K, V> adjacent = found.getAdjacent(compare < 0);
					if (adjacent == null) {
						if (preAdd != null)
							preAdd.run();
						found = putEntry(key, value.apply(key), //
							compare < 0 ? null : found.getElementId(), //
							compare < 0 ? found.getElementId() : null, //
							compare > 0);
						newEntry = true;
					} else {
						int adjCompare = comparator().compare(key, adjacent.getKey());
						if (adjCompare == 0) {
							found = adjacent;
							compare = 0;
						} else if ((adjCompare < 0) == (compare < 0)) {
							// Multiple elements have been added since we got the lock. Do the search again.
							found = search(keySet().searchFor(key, 0), BetterSortedList.SortedSearchFilter.PreferLess);
							compare = comparator().compare(key, found.getKey());
						} else {
							if (preAdd != null)
								preAdd.run();
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
				found = search(keySet().searchFor(key, 0), BetterSortedList.SortedSearchFilter.PreferLess);
				if (found == null) {
					// The map is still null. Add the first entry.
					if (preAdd != null)
						preAdd.run();
					found = putEntry(key, value.apply(key), first);
					newEntry = true;
				} else {
					compare = comparator().compare(key, found.getKey());
				}
			}
			if (!newEntry && compare != 0) {
				if (preAdd != null)
					preAdd.run();
				found = putEntry(key, value.apply(key), //
					compare < 0 ? null : found.getElementId(), //
					compare < 0 ? found.getElementId() : null, //
					false);
				newEntry = true;
			}
			if (found != null && newEntry && postAdd != null)
				postAdd.run();
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
	default MapEntryHandle<K, V> lowerEntry(K key) {
		return search(keySet().searchFor(key, -1), BetterSortedList.SortedSearchFilter.Less);
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
	default MapEntryHandle<K, V> floorEntry(K key) {
		return search(keySet().searchFor(key, 0), BetterSortedList.SortedSearchFilter.Less);
	}

	@Override
	default K floorKey(K key) {
		return keyOf(floorEntry(key));
	}

	@Override
	default MapEntryHandle<K, V> ceilingEntry(K key) {
		return search(keySet().searchFor(key, 0), BetterSortedList.SortedSearchFilter.Greater);
	}

	@Override
	default K ceilingKey(K key) {
		return keyOf(ceilingEntry(key));
	}

	@Override
	default MapEntryHandle<K, V> higherEntry(K key) {
		return search(keySet().searchFor(key, 1), BetterSortedList.SortedSearchFilter.Greater);
	}

	@Override
	default K higherKey(K key) {
		return keyOf(higherEntry(key));
	}

	@Override
	default MapEntryHandle<K, V> firstEntry() {
		return search(k -> -1, BetterSortedList.SortedSearchFilter.PreferGreater);
	}

	@Override
	default MapEntryHandle<K, V> lastEntry() {
		return search(k -> 1, BetterSortedList.SortedSearchFilter.PreferGreater);
	}

	@Override
	default Map.Entry<K, V> pollFirstEntry() {
		MapEntryHandle<K, V> handle = search(v -> -1, BetterSortedList.SortedSearchFilter.PreferLess);
		if (handle != null) {
			Map.Entry<K, V> result = new ImmutableMapEntry<>(handle.getKey(), handle.get());
			forMutableEntry(handle.getElementId(), el -> el.remove());
			return result;
		}
		return null;
	}

	@Override
	default Map.Entry<K, V> pollLastEntry() {
		MapEntryHandle<K, V> handle = search(v -> 1, BetterSortedList.SortedSearchFilter.PreferLess);
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
		return subMap(keySet().searchFor(fromKey, fromInclusive ? 0 : 1), keySet().searchFor(toKey, toInclusive ? 0 : -1));
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
		return subMap(null, keySet().searchFor(toKey, inclusive ? 0 : -1));
	}

	@Override
	default BetterSortedMap<K, V> tailMap(K fromKey, boolean inclusive) {
		return subMap(keySet().searchFor(fromKey, inclusive ? 0 : 1), null);
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

	@Override
	default BetterSortedMap<K, V> withAll(Iterable<? extends K> keys, V value) {
		BetterMap.super.withAll(keys, value);
		return this;
	}

	/**
	 * @param <K> The key-type for the map
	 * @param <V> The value-type for the map
	 * @param sorting The sorting for the map's key set
	 * @return An immutable {@link BetterSortedMap} with the given key sorting and no entries
	 */
	static <K, V> BetterSortedMap<K, V> empty(Comparator<? super K> sorting) {
		return new EmptyBetterSortedMap<>(sorting);
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
		public OrderedMapEntry<K, V> searchEntries(Comparable<? super Map.Entry<K, V>> search, BetterSortedList.SortedSearchFilter filter) {
			return getWrapped().searchEntries(v -> -search.compareTo(v), filter.opposite());
		}

		@Override
		public OrderedMapEntry<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
			return (OrderedMapEntry<K, V>) super.putEntry(key, value, after, before, first);
		}

		@Override
		public OrderedMapEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId after, ElementId before,
			boolean first, Runnable preAdd, Runnable postAdd) {
			return (OrderedMapEntry<K, V>) super.getOrPutEntry(key, value, after, before, first, preAdd, postAdd);
		}

		@Override
		public OrderedMapEntry<K, V> getEntry(K key) {
			return (OrderedMapEntry<K, V>) super.getEntry(key);
		}

		@Override
		public OrderedMapEntry<K, V> getEntryById(ElementId entryId) {
			return (OrderedMapEntry<K, V>) super.getEntryById(entryId);
		}

		@Override
		public MutableOrderedMapEntry<K, V> mutableEntry(ElementId entryId) {
			return (MutableOrderedMapEntry<K, V>) super.mutableEntry(entryId);
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
		public ListElement<Entry<K, V>> getTerminalElement(boolean first) {
			return (ListElement<Map.Entry<K, V>>) super.getTerminalElement(first);
		}

		@Override
		public ListElement<Map.Entry<K, V>> getElement(ElementId id) {
			return entryFor(getMap().getEntryById(id));
		}

		@Override
		public ListElement<Map.Entry<K, V>> getElement(int index) {
			return getElement(getMap().keySet().getElement(index).getElementId());
		}

		@Override
		public MutableListElement<Map.Entry<K, V>> mutableElement(ElementId id) {
			return new MutableSortedEntryElement(getMap().mutableEntry(id));
		}

		@Override
		public ListElement<Map.Entry<K, V>> search(Comparable<? super Map.Entry<K, V>> search, BetterSortedList.SortedSearchFilter filter) {
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
			return k -> entryCompare.compareTo(new ImmutableMapEntry<>(k, null));
		}

		@Override
		public int indexFor(Comparable<? super Map.Entry<K, V>> search) {
			return getMap().keySet().indexFor(keyCompare(search));
		}

		@Override
		public ListElement<Map.Entry<K, V>> getElement(Map.Entry<K, V> value, boolean first) {
			return (ListElement<Map.Entry<K, V>>) super.getElement(value, first);
		}

		@Override
		public ListElement<Map.Entry<K, V>> getOrAdd(Map.Entry<K, V> value, ElementId after, ElementId before, boolean first,
			Runnable preAdd, Runnable postAdd) {
			return (ListElement<Map.Entry<K, V>>) super.getOrAdd(value, after, before, first, preAdd, postAdd);
		}

		@Override
		public ListElement<Entry<K, V>> addElement(Map.Entry<K, V> value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return (ListElement<Map.Entry<K, V>>) super.addElement(value, after, before, first);
		}

		@Override
		public ListElement<Map.Entry<K, V>> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			return (ListElement<Map.Entry<K, V>>) super.move(valueEl, after, before, first, afterRemove);
		}

		@Override
		protected ListElement<Entry<K, V>> entryFor(MapEntryHandle<K, V> entry) {
			return entry == null ? null : new SortedEntryElement((OrderedMapEntry<K, V>) entry);
		}

		protected class SortedEntryElement extends EntryElement implements ListElement<Map.Entry<K, V>> {
			protected SortedEntryElement(OrderedMapEntry<K, V> entry) {
				super(entry);
			}

			@Override
			protected OrderedMapEntry<K, V> getEntry() {
				return (OrderedMapEntry<K, V>) super.getEntry();
			}

			@Override
			public ListElement<Map.Entry<K, V>> getAdjacent(boolean next) {
				OrderedMapEntry<K, V> adj = getEntry().getAdjacent(next);
				return adj == null ? null : new SortedEntryElement(adj);
			}

			@Override
			public int getElementsBefore() {
				return getEntry().getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return getEntry().getElementsAfter();
			}
		}

		protected class MutableSortedEntryElement extends MutableEntryElement implements MutableListElement<Map.Entry<K, V>> {
			protected MutableSortedEntryElement(MutableOrderedMapEntry<K, V> entry) {
				super(entry);
			}

			@Override
			protected MutableOrderedMapEntry<K, V> getEntry() {
				return (MutableOrderedMapEntry<K, V>) super.getEntry();
			}

			@Override
			public MutableSortedEntryElement getAdjacent(boolean next) {
				MutableOrderedMapEntry<K, V> adj = getEntry().getAdjacent(next);
				return adj == null ? null : new MutableSortedEntryElement(adj);
			}

			@Override
			public int getElementsBefore() {
				return getEntry().getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return getEntry().getElementsAfter();
			}
		}
	}

	/**
	 * Implements {@link BetterSortedMap#subMap(Comparable, Comparable)}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class BetterSubMap<K, V> extends AbstractIdentifiable implements BetterSortedMap<K, V> {
		private final BetterSortedMap<K, V> theSource;
		private final Comparable<? super K> theLowerBound;
		private final Comparable<? super K> theUpperBound;

		private final BetterSortedSet.BetterSubSet<K> theKeySet;

		public BetterSubMap(BetterSortedMap<K, V> source, Comparable<? super K> lowerBound, Comparable<? super K> upperBound) {
			theSource = source;
			theLowerBound = lowerBound;
			theUpperBound = upperBound;

			theKeySet = (BetterSortedSet.BetterSubSet<K>) source.keySet().subSet(theLowerBound, theUpperBound);
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
		protected Object createIdentity() {
			return Identifiable.wrap(theSource.getIdentity(), "subMap", theLowerBound, theUpperBound);
		}

		@Override
		public BetterSortedSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public OrderedMapEntry<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
			if (isInRange(key) != 0)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			return wrap(theSource.putEntry(key, value, //
				theKeySet.strip(after), theKeySet.strip(before), first));
		}

		@Override
		public OrderedMapEntry<K, V> getEntry(K key) {
			if (isInRange(key) != 0)
				return null;
			return wrap(theSource.getEntry(key));
		}

		@Override
		public OrderedMapEntry<K, V> getEntryById(ElementId entryId) {
			OrderedMapEntry<K, V> entry = theSource.getEntryById(//
				theKeySet.strip(entryId));
			return wrap(entry);
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
		public OrderedMapEntry<K, V> searchEntries(Comparable<? super Map.Entry<K, V>> search, BetterSortedList.SortedSearchFilter filter) {
			OrderedMapEntry<K, V> found = theSource.searchEntries(boundSearch(search), filter);
			if (found == null)
				return null;
			int range = theKeySet.isInRange(found.getKey());
			if (range == 0)
				return wrap(found);
			if (filter.strict)
				return null;
			return getTerminalEntry(range < 0);
		}

		@Override
		public MutableOrderedMapEntry<K, V> mutableEntry(ElementId entryId) {
			MutableOrderedMapEntry<K, V> entry = theSource.mutableEntry(theKeySet.strip(entryId));
			return wrap(entry);
		}

		@Override
		public String canPut(K key, V value) {
			if (isInRange(key) != 0)
				return StdMsg.ILLEGAL_ELEMENT;
			return theSource.canPut(key, value);
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

		OrderedMapEntry<K, V> wrap(OrderedMapEntry<K, V> entry) {
			return entry == null ? null : new BoundedMapEntry(entry);
		}

		MutableOrderedMapEntry<K, V> wrap(MutableOrderedMapEntry<K, V> entry) {
			return entry == null ? null : new BoundedMutableEntry(entry);
		}

		class BoundedMapEntry implements OrderedMapEntry<K, V> {
			private final OrderedMapEntry<K, V> theSourceEntry;
			private final ElementId theSubSetId;

			BoundedMapEntry(OrderedMapEntry<K, V> sourceEntry) {
				theSourceEntry = sourceEntry;
				theSubSetId = theKeySet.wrap(sourceEntry.getElementId());
			}

			protected MapEntryHandle<K, V> getSourceEntry() {
				return theSourceEntry;
			}

			@Override
			public ElementId getElementId() {
				return theSubSetId;
			}

			@Override
			public V get() {
				return theSourceEntry.get();
			}

			@Override
			public K getKey() {
				return theSourceEntry.getKey();
			}

			@Override
			public OrderedMapEntry<K, V> getAdjacent(boolean next) {
				OrderedMapEntry<K, V> adj = theSourceEntry.getAdjacent(next);
				if (adj == null)
					return null;
				else if (next) {
					if (theUpperBound != null && theUpperBound.compareTo(adj.getKey()) < 0)
						return null;
				} else {
					if (theLowerBound != null && theLowerBound.compareTo(adj.getKey()) > 0)
						return null;
				}
				return wrap(adj);
			}

			@Override
			public int getElementsBefore() {
				return theSourceEntry.getElementsBefore() - theKeySet.getMinIndex();
			}

			@Override
			public int getElementsAfter() {
				return theKeySet.getMaxIndex() - theSourceEntry.getElementsBefore() - 1;
			}

			@Override
			public int hashCode() {
				return theSourceEntry.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof BetterSubMap.BoundedMapEntry && theSourceEntry.equals(((BoundedMapEntry) obj).theSourceEntry);
			}

			@Override
			public String toString() {
				return theSourceEntry.toString();
			}
		}

		class BoundedMutableEntry extends BoundedMapEntry implements MutableOrderedMapEntry<K, V> {
			public BoundedMutableEntry(MutableOrderedMapEntry<K, V> sourceEntry) {
				super(sourceEntry);
			}

			@Override
			protected MutableOrderedMapEntry<K, V> getSourceEntry() {
				return (MutableOrderedMapEntry<K, V>) super.getSourceEntry();
			}

			@Override
			public MutableOrderedMapEntry<K, V> getAdjacent(boolean next) {
				MutableOrderedMapEntry<K, V> adj = getSourceEntry().getAdjacent(next);
				if (adj == null)
					return null;
				else if (next) {
					if (theUpperBound != null && theUpperBound.compareTo(adj.getKey()) < 0)
						return null;
				} else {
					if (theLowerBound != null && theLowerBound.compareTo(adj.getKey()) > 0)
						return null;
				}
				return wrap(adj);
			}

			@Override
			public String isEnabled() {
				return getSourceEntry().isEnabled();
			}

			@Override
			public String isAcceptable(V value) {
				return getSourceEntry().isAcceptable(value);
			}

			@Override
			public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
				getSourceEntry().setValue(value);
			}

			@Override
			public String canRemove() {
				return getSourceEntry().canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				getSourceEntry().remove();
			}
		}
	}

	/**
	 * An immutable {@link BetterSortedMap} with the given key sorting and no entries
	 * 
	 * @param <K> The key-type for the map
	 * @param <V> The value-type for the map
	 */
	class EmptyBetterSortedMap<K, V> extends BetterMap.EmptyBetterMap<K, V> implements BetterSortedMap<K, V> {
		private final BetterSortedSet<K> theKeySet;

		public EmptyBetterSortedMap(Comparator<? super K> sorting) {
			theKeySet = BetterSortedSet.empty(sorting);
		}

		@Override
		public OrderedMapEntry<K, V> getEntry(K key) {
			return null;
		}

		@Override
		public OrderedMapEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId after, ElementId before,
			boolean first, Runnable preAdd, Runnable postAdd) {
			return null;
		}

		@Override
		public OrderedMapEntry<K, V> getEntryById(ElementId entryId) {
			throw new NoSuchElementException();
		}

		@Override
		public MutableOrderedMapEntry<K, V> mutableEntry(ElementId entryId) {
			throw new NoSuchElementException();
		}

		@Override
		public OrderedMapEntry<K, V> searchEntries(Comparable<? super Entry<K, V>> search, SortedSearchFilter filter) {
			return null;
		}

		@Override
		public BetterSortedSet<K> keySet() {
			return theKeySet;
		}
	}
}

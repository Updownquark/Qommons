package org.qommons.collect;

import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.MutableElementHandle.StdMsg;

public interface BetterSortedMap<K, V> extends BetterMap<K, V>, NavigableMap<K, V> {
	@Override
	abstract BetterSortedSet<K> keySet();

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
	 * Searches this sorted map for a value
	 *
	 * @param search The search to navigate through this map for the target key. The search must follow this map's {@link #comparator()
	 *        order}.
	 * @param filter The filter on the result
	 * @return The result of the search, or null if no such value was found
	 */
	default Map.Entry<K, V> relativeEntry(Comparable<? super K> search, SortedSearchFilter filter) {
		Map.Entry<K, V>[] found = new Map.Entry[1];
		forEntry(search, entry -> found[0] = new ImmutableMapEntry<>(entry.getKey(), entry.get()), filter);
		return found[0];
	}

	/**
	 * @param value The value to search for
	 * @param onElement The action to perform on the element containing the given value, if found
	 * @return Whether such a value was found
	 */
	@Override
	default boolean forEntry(K key, Consumer<? super MapEntryHandle<K, V>> onElement) {
		return forEntry(keySet().searchFor(key, 0), onElement, SortedSearchFilter.OnlyMatch);
	}

	/**
	 * @param value The value to search for
	 * @param onElement The action to perform on the element containing the given value, if found
	 * @return Whether such a value was found
	 */
	@Override
	default boolean forMutableEntry(K key, Consumer<? super MutableMapEntryHandle<K, V>> onElement) {
		return forMutableEntry(keySet().searchFor(key, 0), onElement, SortedSearchFilter.OnlyMatch);
	}

	/**
	 * Searches for an entry in this sorted map
	 * 
	 * @param search The search to use. Must follow this sorted map's key ordering.
	 * @param onEntry The action to perform on the closest found entry in the sorted map
	 * @param filter The filter on the result
	 * @return Whether an entry matching the filter was found in the map
	 */
	boolean forEntry(Comparable<? super K> search, Consumer<? super MapEntryHandle<K, V>> onEntry, SortedSearchFilter filter);

	/**
	 * Like {@link #forEntry(Comparable, Consumer, SortedSearchFilter)}, but provides a mutable entry
	 * 
	 * @param search The search to use. Must follow this sorted set's ordering.
	 * @param onEntry The action to perform on the closest found entry in the sorted map
	 * @param filter The filter on the result
	 * @return Whether an entry matching the filter was found in the map
	 */
	boolean forMutableEntry(Comparable<? super K> search, Consumer<? super MutableMapEntryHandle<K, V>> onEntry, SortedSearchFilter filter);

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
		return relativeEntry(keySet().searchFor(key, 1), SortedSearchFilter.Less);
	}

	@Override
	default K lowerKey(K key) {
		return keyOf(lowerEntry(key));
	}

	static <K> K keyOf(Map.Entry<K, ?> entry) {
		return entry == null ? null : entry.getKey();
	}

	@Override
	default Map.Entry<K, V> floorEntry(K key) {
		return relativeEntry(keySet().searchFor(key, 0), SortedSearchFilter.Less);
	}

	@Override
	default K floorKey(K key) {
		return keyOf(floorEntry(key));
	}

	@Override
	default Map.Entry<K, V> ceilingEntry(K key) {
		return relativeEntry(keySet().searchFor(key, 0), SortedSearchFilter.Greater);
	}

	@Override
	default K ceilingKey(K key) {
		return keyOf(ceilingEntry(key));
	}

	@Override
	default Map.Entry<K, V> higherEntry(K key) {
		return relativeEntry(keySet().searchFor(key, -1), SortedSearchFilter.Greater);
	}

	@Override
	default K higherKey(K key) {
		return keyOf(higherEntry(key));
	}

	@Override
	default Map.Entry<K, V> firstEntry() {
		return relativeEntry(k -> 1, SortedSearchFilter.PreferGreater);
	}

	@Override
	default Map.Entry<K, V> lastEntry() {
		return relativeEntry(k -> -1, SortedSearchFilter.PreferGreater);
	}

	@Override
	default Map.Entry<K, V> pollFirstEntry() {
		Map.Entry<K, V>[] result = new Map.Entry[1];
		forMutableEntry(v -> 1, entry -> {
			result[0] = new ImmutableMapEntry<>(entry.getKey(), entry.get());
			entry.remove();
		}, SortedSearchFilter.PreferLess);
		return result[0];
	}

	@Override
	default Map.Entry<K, V> pollLastEntry() {
		Map.Entry<K, V>[] result = new Map.Entry[1];
		forMutableEntry(v -> -1, entry -> {
			result[0] = new ImmutableMapEntry<>(entry.getKey(), entry.get());
			entry.remove();
		}, SortedSearchFilter.PreferGreater);
		return result[0];
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
		public boolean forEntry(Comparable<? super K> search, Consumer<? super MapEntryHandle<K, V>> onEntry, SortedSearchFilter filter) {
			return getWrapped().forEntry(v -> -search.compareTo(v), entry -> onEntry.accept(entry.reverse()), filter.opposite());
		}

		@Override
		public boolean forMutableEntry(Comparable<? super K> search, Consumer<? super MutableMapEntryHandle<K, V>> onEntry,
			SortedSearchFilter filter) {
			return getWrapped().forMutableEntry(v -> -search.compareTo(v), entry -> onEntry.accept(entry.reverse()), filter.opposite());
		}
	}

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
		public <T> T ofElementAt(int index, Function<? super ElementHandle<? extends Map.Entry<K, V>>, T> onElement) {
			return getMap().keySet().ofElementAt(index,
				keyEl -> getMap().ofEntry(keyEl.getElementId(), entry -> onElement.apply(handleFor(entry))));
		}

		@Override
		public <T> T ofMutableElementAt(int index, Function<? super MutableElementHandle<? extends Map.Entry<K, V>>, T> onElement) {
			return getMap().keySet().ofMutableElementAt(index,
				keyEl -> getMap().ofMutableEntry(keyEl.getElementId(), entry -> onElement.apply(mutableHandleFor(entry))));
		}

		@Override
		public Comparator<? super Map.Entry<K, V>> comparator() {
			return (e1, e2) -> getMap().keySet().comparator().compare(e1.getKey(), e2.getKey());
		}

		protected Comparable<? super K> keyCompare(Comparable<? super Map.Entry<K, V>> entryCompare) {
			return k -> entryCompare.compareTo(new ImmutableMapEntry<K, V>(k, null));
		}

		@Override
		public ElementId addIfEmpty(Map.Entry<K, V> value) throws IllegalStateException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public int indexFor(Comparable<? super Map.Entry<K, V>> search) {
			return getMap().keySet().indexFor(keyCompare(search));
		}

		@Override
		public boolean forElement(Comparable<? super Map.Entry<K, V>> search,
			Consumer<? super ElementHandle<? extends Map.Entry<K, V>>> onElement, SortedSearchFilter filter) {
			return getMap().forEntry(keyCompare(search), entry -> onElement.accept(handleFor(entry)), filter);
		}

		@Override
		public boolean forMutableElement(Comparable<? super Map.Entry<K, V>> search,
			Consumer<? super MutableElementHandle<? extends Map.Entry<K, V>>> onElement, SortedSearchFilter filter) {
			return getMap().forMutableEntry(keyCompare(search), entry -> onElement.accept(mutableHandleFor(entry)), filter);
		}

		@Override
		public MutableElementSpliterator<Map.Entry<K, V>> mutableSpliterator(int index) {
			return wrap(getMap().keySet().mutableSpliterator(index));
		}

		@Override
		public MutableElementSpliterator<Map.Entry<K, V>> mutableSpliterator(Comparable<? super Map.Entry<K, V>> searchForStart,
			boolean higher) {
			return wrap(getMap().keySet().mutableSpliterator(keyCompare(searchForStart), higher));
		}
	}

	class BetterSubMap<K, V> implements BetterSortedMap<K, V> {
		private final BetterSortedMap<K, V> theWrapped;
		private final Comparable<? super K> theLowerBound;
		private final Comparable<? super K> theUpperBound;

		private final BetterSortedSet<K> theKeySet;

		public BetterSubMap(BetterSortedMap<K, V> wrapped, Comparable<? super K> lowerBound, Comparable<? super K> upperBound) {
			theWrapped = wrapped;
			theLowerBound = lowerBound;
			theUpperBound = upperBound;

			theKeySet = wrapped.keySet().subSet(theLowerBound, theUpperBound);
		}

		@Override
		public BetterSortedSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public ElementId putEntry(K key, V value) {
			if (!theKeySet.belongs(key))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			return theWrapped.putEntry(key, value);
		}

		@Override
		public <X> X ofEntry(ElementId entryId, Function<? super MapEntryHandle<K, V>, X> onEntry) {
			return theWrapped.ofEntry(entryId, entry -> {
				if (!keySet().belongs(entry.getKey()))
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
				return onEntry.apply(entry);
			});
		}

		@Override
		public <X> X ofMutableEntry(ElementId entryId, Function<? super MutableMapEntryHandle<K, V>, X> onEntry) {
			return theWrapped.ofMutableEntry(entryId, entry -> {
				if (!keySet().belongs(entry.getKey()))
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
				return onEntry.apply(entry);
			});
		}

		@Override
		public boolean forEntry(Comparable<? super K> search, Consumer<? super MapEntryHandle<K, V>> onEntry, SortedSearchFilter filter) {
			return theKeySet.forElement(search, keyEl -> theWrapped.forEntry(keyEl.getElementId(), onEntry), filter);
		}

		@Override
		public boolean forMutableEntry(Comparable<? super K> search, Consumer<? super MutableMapEntryHandle<K, V>> onEntry,
			SortedSearchFilter filter) {
			return theKeySet.forMutableElement(search, keyEl -> theWrapped.forMutableEntry(keyEl.getElementId(), onEntry), filter);
		}
	}
}

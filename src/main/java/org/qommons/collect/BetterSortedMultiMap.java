package org.qommons.collect;

import java.util.Comparator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;

import org.qommons.Identifiable;
import org.qommons.Transaction;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * A {@link BetterMultiMap} whose {@link #keySet() key set} is sorted by a comparator
 * 
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public interface BetterSortedMultiMap<K, V> extends BetterMultiMap<K, V>, SortedMultiMap<K, V> {
	@Override
	BetterSortedSet<K> keySet();

	@Override
	default BetterSortedSet<? extends MultiEntryHandle<K, V>> entrySet() {
		return new BetterSortedMultiMapEntrySet<>(this);
	}

	/**
	 * Searches this sorted map for a value
	 *
	 * @param search The search to navigate through this map for the target key. The search must follow this map's {@link #comparator()
	 *        order}.
	 * @param filter The filter on the result
	 * @return The result of the search, or null if no such value was found
	 */
	default MultiEntryHandle<K, V> search(Comparable<? super K> search, BetterSortedList.SortedSearchFilter filter) {
		return searchEntries(entry -> search.compareTo(entry.getKey()), filter);
	}

	/**
	 * Searches this sorted map for an entry
	 *
	 * @param search The search to navigate through this map for the target entry. The search must follow this map's {@link #comparator()
	 *        order}.
	 * @param filter The filter on the result
	 * @return The result of the search, or null if no such value was found
	 */
	MultiEntryHandle<K, V> searchEntries(Comparable<? super MultiEntryHandle<K, V>> search,
		BetterSortedList.SortedSearchFilter filter);

	@Override
	default MultiEntryHandle<K, V> lowerEntry(K key) {
		return search(keySet().searchFor(key, -1), BetterSortedList.SortedSearchFilter.Less);
	}

	@Override
	default MultiEntryHandle<K, V> floorEntry(K key) {
		return search(keySet().searchFor(key, 0), BetterSortedList.SortedSearchFilter.Less);
	}

	@Override
	default MultiEntryHandle<K, V> ceilingEntry(K key) {
		return search(keySet().searchFor(key, 0), BetterSortedList.SortedSearchFilter.Greater);
	}

	@Override
	default MultiEntryHandle<K, V> higherEntry(K key) {
		return search(keySet().searchFor(key, 1), BetterSortedList.SortedSearchFilter.Greater);
	}

	@Override
	default MultiEntryHandle<K, V> firstEntry() {
		return search(k -> -1, BetterSortedList.SortedSearchFilter.PreferGreater);
	}

	@Override
	default MultiEntryHandle<K, V> lastEntry() {
		return search(k -> 1, BetterSortedList.SortedSearchFilter.PreferGreater);
	}

	@Override
	default BetterMap<K, V> singleMap(boolean firstValue) {
		return new SortedSingleMultiMap<>(this, firstValue);
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

	/**
	 * @param from The lower bound comparator
	 * @param to The upper bound comparator
	 * @return A multi-map whose contents are those of this map for which it is true of the key:
	 *         <ul>
	 *         <li><code>from.compareTo(key)<=0</code></li> and
	 *         <li><code>to.compareTo(key)>=0</code></li>
	 *         </ul>
	 */
	default BetterSortedMultiMap<K, V> subMap(Comparable<? super K> from, Comparable<? super K> to) {
		return new BetterSubMultiMap<>(this, from, to);
	}

	/**
	 * Implements {@link BetterSortedMultiMap#entrySet()}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class BetterSortedMultiMapEntrySet<K, V> extends BetterMultiMapEntrySet<K, V> implements BetterSortedSet<MultiEntryHandle<K, V>> {
		public BetterSortedMultiMapEntrySet(BetterSortedMultiMap<K, V> map) {
			super(map);
		}

		@Override
		protected BetterSortedMultiMap<K, V> getMap() {
			return (BetterSortedMultiMap<K, V>) super.getMap();
		}

		@Override
		public Comparator<? super MultiEntryHandle<K, V>> comparator() {
			return (entry1, entry2) -> getMap().comparator().compare(entry1.getKey(), entry2.getKey());
		}

		@Override
		public CollectionElement<MultiEntryHandle<K, V>> search(Comparable<? super MultiEntryHandle<K, V>> search,
			SortedSearchFilter filter) {
			TempEntry temp = new TempEntry();
			CollectionElement<K> keyEl = getMap().keySet().search(key -> {
				temp.key = key;
				return search.compareTo(temp);
			}, filter);
			return keyEl == null ? null : entryFor(getMap().getEntryById(keyEl.getElementId()));
		}

		@Override
		public CollectionElement<MultiEntryHandle<K, V>> getElement(int index) throws IndexOutOfBoundsException {
			return entryFor(getMap().getEntryById(getMap().keySet().getElement(index).getElementId()));
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
		public int indexFor(Comparable<? super MultiEntryHandle<K, V>> search) {
			TempEntry temp = new TempEntry();
			return getMap().keySet().indexFor(key -> {
				temp.key = key;
				return search.compareTo(temp);
			});
		}

		class TempEntry implements MultiEntryHandle<K, V> {
			K key;

			@Override
			public K getKey() {
				return null;
			}

			@Override
			public ElementId getElementId() {
				throw new IllegalStateException("This method may not be called from a search");
			}

			@Override
			public BetterCollection<V> getValues() {
				throw new IllegalStateException("This method may not be called from a search");
			}
		}
	}

	/**
	 * Implements {@link BetterSortedMultiMap#singleMap(boolean)}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class SortedSingleMultiMap<K, V> extends SingleMap<K, V> implements BetterSortedMap<K, V> {
		public SortedSingleMultiMap(BetterSortedMultiMap<K, V> outer, boolean firstValue) {
			super(outer, firstValue);
		}

		@Override
		protected BetterSortedMultiMap<K, V> getSource() {
			return (BetterSortedMultiMap<K, V>) super.getSource();
		}

		@Override
		public BetterSortedSet<K> keySet() {
			return getSource().keySet();
		}

		@Override
		public MapEntryHandle<K, V> searchEntries(Comparable<? super Map.Entry<K, V>> search, SortedSearchFilter filter) {
			try (Transaction t = lock(false, null)) {
				MultiEntryHandle<K, V> entry = getSource().searchEntries(e -> search.compareTo(entryFor(e)), filter);
				return entryFor(entry);
			}
		}
	}

	/**
	 * Implements {@link BetterSortedMultiMap#reverse()}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
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
		public BetterSortedSet<? extends MultiEntryHandle<K, V>> entrySet() {
			return getSource().entrySet().reverse();
		}

		@Override
		public MultiEntryHandle<K, V> searchEntries(Comparable<? super MultiEntryHandle<K, V>> search,
			BetterSortedList.SortedSearchFilter filter) {
			return MultiEntryHandle.reverse(getSource().searchEntries(e -> -search.compareTo(e.reverse()), filter.opposite()));
		}

		@Override
		public BetterSortedMultiMap<K, V> reverse() {
			return (BetterSortedMultiMap<K, V>) super.reverse();
		}
	}

	/**
	 * Implements {@link BetterSortedMultiMap#subMap(Comparable, Comparable)}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class BetterSubMultiMap<K, V> implements BetterSortedMultiMap<K, V> {
		private final BetterSortedMultiMap<K, V> theWrapped;
		private final Comparable<? super K> theLowerBound;
		private final Comparable<? super K> theUpperBound;

		private final BetterSortedSet<K> theKeySet;
		private Object theIdentity;

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
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.wrap(theWrapped.getIdentity(), "subMap", theLowerBound, theUpperBound);
			return theIdentity;
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

		@Override
		public BetterSortedSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public BetterSortedSet<? extends MultiEntryHandle<K, V>> entrySet() {
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
		public MultiEntryValueHandle<K, V> putEntry(K key, V value, ElementId afterKey, ElementId beforeKey, boolean first) {
			if (!theKeySet.belongs(key))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			return theWrapped.putEntry(key, value, afterKey, beforeKey, first);
		}

		@Override
		public MultiEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
			ElementId beforeKey, boolean first, Runnable added) {
			if (!theKeySet.belongs(key))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			return theWrapped.getOrPutEntry(key, value, afterKey, beforeKey, first, added);
		}

		@Override
		public int valueSize() {
			int vs = 0;
			MultiEntryHandle<K, V> entry = getWrapped().search(theLowerBound, BetterSortedList.SortedSearchFilter.Greater);
			if (entry == null)
				return 0;
			while (theUpperBound.compareTo(entry.getKey()) >= 0) {
				vs += entry.getValues().size();
				CollectionElement<K> keyEl = getWrapped().keySet().getAdjacentElement(entry.getElementId(), true);
				if (keyEl == null)
					break;
				entry = getWrapped().getEntryById(keyEl.getElementId());
			}
			return vs;
		}

		@Override
		public boolean clear() {
			try (Transaction t = lock(true, null)) {
				MultiEntryHandle<K, V> entry = getWrapped().search(theLowerBound, BetterSortedList.SortedSearchFilter.Greater);
				if (entry == null)
					return false;
				boolean cleared = false;
				while (theUpperBound.compareTo(entry.getKey()) >= 0) {
					int preSize = entry.getValues().size();
					entry.getValues().clear();
					if (!cleared && !entry.getElementId().isPresent() || entry.getValues().size() < preSize)
						cleared = true;

					CollectionElement<K> keyEl = getWrapped().keySet().getAdjacentElement(entry.getElementId(), true);
					if (keyEl == null)
						break;
					entry = getWrapped().getEntryById(keyEl.getElementId());
				}
				return cleared;
			}
		}

		@Override
		public MultiEntryHandle<K, V> getEntry(K key) {
			if (!theKeySet.belongs(key))
				return null;
			return theWrapped.getEntry(key);
		}

		@Override
		public MultiEntryValueHandle<K, V> getEntryById(ElementId keyId, ElementId valueId) {
			MultiEntryValueHandle<K, V> wrappedEntry = getWrapped().getEntryById(keyId, valueId);
			if (theLowerBound.compareTo(wrappedEntry.getKey()) > 0 || theUpperBound.compareTo(wrappedEntry.getKey()) < 0)
				throw new NoSuchElementException();
			return wrappedEntry;
		}

		@Override
		public MultiEntryHandle<K, V> getEntryById(ElementId entryId) {
			MultiEntryHandle<K, V> entry = theWrapped.getEntryById(entryId);
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

		protected Comparable<MultiEntryHandle<K, V>> boundSearch(Comparable<? super MultiEntryHandle<K, V>> search) {
			return entry -> {
				int compare = isInRange(entry.getKey());
				if (compare == 0)
					compare = search.compareTo(entry);
				return compare;
			};
		}

		@Override
		public MultiEntryHandle<K, V> searchEntries(Comparable<? super MultiEntryHandle<K, V>> search,
			BetterSortedList.SortedSearchFilter filter) {
			return theWrapped.searchEntries(boundSearch(search), filter);
		}
	}
}

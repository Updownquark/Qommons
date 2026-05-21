package org.qommons.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.qommons.Identifiable;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;

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

	@Override
	default OrderedMultiEntry<K, V> getEntryById(ElementId keyId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	default OrderedMultiEntry<K, V> getTerminalEntry(boolean first) {
		return (OrderedMultiEntry<K, V>) BetterMultiMap.super.getTerminalEntry(first);
	}

	@Override
	default OrderedMultiEntry<K, V> getAdjacentEntry(ElementId entryId, boolean next) {
		return getEntryById(entryId).getAdjacent(next);
	}

	@Override
	default OrderedMultiEntry<K, V> getEntry(K key) {
		return (OrderedMultiEntry<K, V>) BetterMultiMap.super.getEntry(key);
	}

	@Override
	OrderedMultiEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
		ElementId beforeKey, boolean first, Runnable preAdd, Runnable postAdd);

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
	default MultiEntryHandle<K, V> searchEntries(Comparable<? super MultiEntryHandle<K, V>> search,
		BetterSortedList.SortedSearchFilter filter) {
		return CollectionElement.get(entrySet().search(entry -> search.compareTo(entry), filter));
	}

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
	default <X> CombinedSortedSingleMapBuilder<K, V, X> singleMap(Function<? super BetterCollection<? extends V>, X> combination) {
		return new CombinedSortedSingleMapBuilder<>(this, combination);
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
		public ListElement<MultiEntryHandle<K, V>> getElement(MultiEntryHandle<K, V> value, boolean first) {
			return (ListElement<MultiEntryHandle<K, V>>) super.getElement(value, first);
		}

		@Override
		public ListElement<MultiEntryHandle<K, V>> getElement(ElementId id) {
			return (ListElement<MultiEntryHandle<K, V>>) super.getElement(id);
		}

		@Override
		public ListElement<MultiEntryHandle<K, V>> getTerminalElement(boolean first) {
			return (ListElement<MultiEntryHandle<K, V>>) super.getTerminalElement(first);
		}

		@Override
		public MutableListElement<MultiEntryHandle<K, V>> mutableElement(ElementId id) {
			return mutableEntryFor(getMap().getEntryById(id));
		}

		@Override
		public ListElement<MultiEntryHandle<K, V>> search(Comparable<? super MultiEntryHandle<K, V>> search,
			SortedSearchFilter filter) {
			TempEntry temp = new TempEntry();
			CollectionElement<K> keyEl = getMap().keySet().search(key -> {
				temp.key = key;
				return search.compareTo(temp);
			}, filter);
			return keyEl == null ? null : entryFor(getMap().getEntryById(keyEl.getElementId()));
		}

		@Override
		public ListElement<MultiEntryHandle<K, V>> getElement(int index) throws IndexOutOfBoundsException {
			return entryFor(getMap().getEntryById(getMap().keySet().getElement(index).getElementId()));
		}

		@Override
		public int indexFor(Comparable<? super MultiEntryHandle<K, V>> search) {
			TempEntry temp = new TempEntry();
			return getMap().keySet().indexFor(key -> {
				temp.key = key;
				return search.compareTo(temp);
			});
		}

		@Override
		public ListElement<MultiEntryHandle<K, V>> addElement(MultiEntryHandle<K, V> value, ElementId after, ElementId before,
			boolean first) throws UnsupportedOperationException, IllegalArgumentException {
			return (ListElement<MultiEntryHandle<K, V>>) super.addElement(value, after, before, first);
		}

		@Override
		public ListElement<MultiEntryHandle<K, V>> move(ElementId valueEl, ElementId after, ElementId before, boolean first,
			Runnable afterRemove) throws UnsupportedOperationException, IllegalArgumentException {
			return (ListElement<MultiEntryHandle<K, V>>) super.move(valueEl, after, before, first, afterRemove);
		}

		@Override
		public ListElement<MultiEntryHandle<K, V>> getOrAdd(MultiEntryHandle<K, V> value, ElementId after, ElementId before, boolean first,
			Runnable preAdd, Runnable postAdd) {
			return (ListElement<MultiEntryHandle<K, V>>) super.getOrAdd(value, after, before, first, preAdd, postAdd);
		}

		@Override
		protected ListElement<MultiEntryHandle<K, V>> entryFor(MultiEntryHandle<K, V> entry) {
			return entry == null ? null : new OrderedEntrySetElement((OrderedMultiEntry<K, V>) entry);
		}

		@Override
		protected MutableListElement<MultiEntryHandle<K, V>> mutableEntryFor(MultiEntryHandle<K, V> entry) {
			return entry == null ? null : new OrderedMutableEntrySetElement((OrderedMultiEntry<K, V>) entry);
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

			@Override
			public MultiEntryHandle<K, V> getAdjacent(boolean next) {
				throw new IllegalStateException("This method may not be called from a search");
			}
		}

		class OrderedEntrySetElement extends EntrySetElement implements ListElement<MultiEntryHandle<K, V>> {
			public OrderedEntrySetElement(OrderedMultiEntry<K, V> entry) {
				super(entry);
			}

			@Override
			protected OrderedMultiEntry<K, V> getEntry() {
				return (OrderedMultiEntry<K, V>) super.getEntry();
			}

			@Override
			public ListElement<MultiEntryHandle<K, V>> getAdjacent(boolean next) {
				return entryFor(getEntry().getAdjacent(next));
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

		class OrderedMutableEntrySetElement extends MutableEntrySetElement implements MutableListElement<MultiEntryHandle<K, V>> {
			public OrderedMutableEntrySetElement(OrderedMultiEntry<K, V> entry) {
				super(entry);
			}

			@Override
			protected OrderedMultiEntry<K, V> getEntry() {
				return (OrderedMultiEntry<K, V>) super.getEntry();
			}

			@Override
			public MutableListElement<MultiEntryHandle<K, V>> getAdjacent(boolean next) {
				return mutableEntryFor(getEntry().getAdjacent(next));
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
		public OrderedMapEntry<K, V> getEntry(K key) {
			return (OrderedMapEntry<K, V>) super.getEntry(key);
		}

		@Override
		public OrderedMapEntry<K, V> getEntryById(ElementId entryId) {
			return (OrderedMapEntry<K, V>) super.getEntryById(entryId);
		}

		@Override
		public OrderedMapEntry<K, V> searchEntries(Comparable<? super Map.Entry<K, V>> search, SortedSearchFilter filter) {
			try (Transaction t = lock(false)) {
				MultiEntryHandle<K, V> entry = getSource().searchEntries(e -> search.compareTo(entryFor(e)), filter);
				return entryFor(entry);
			}
		}

		@Override
		public OrderedMapEntry<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
			return (OrderedMapEntry<K, V>) super.putEntry(key, value, after, before, first);
		}

		@Override
		public OrderedMapEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId afterKey, ElementId beforeKey,
			boolean first, Runnable preAdd, Runnable postAdd) {
			return (OrderedMapEntry<K, V>) super.getOrPutEntry(key, value, afterKey, beforeKey, first, preAdd, postAdd);
		}

		@Override
		public MutableOrderedMapEntry<K, V> mutableEntry(ElementId entryId) {
			return mutableEntryFor(getSource().getEntryById(entryId));
		}

		@Override
		protected OrderedMapEntry<K, V> entryFor(MultiEntryHandle<K, V> outerHandle) {
			return outerHandle == null ? null : new OrderedSingleEntry((OrderedMultiEntry<K, V>) outerHandle);
		}

		@Override
		protected MutableOrderedMapEntry<K, V> mutableEntryFor(MultiEntryHandle<K, V> outerHandle) {
			return outerHandle == null ? null : new MutableOrderedSingleEntry((OrderedMultiEntry<K, V>) outerHandle);
		}

		class OrderedSingleEntry extends SingleEntry implements OrderedMapEntry<K, V> {
			protected OrderedSingleEntry(OrderedMultiEntry<K, V> entry) {
				super(entry);
			}

			@Override
			protected OrderedMultiEntry<K, V> getEntry() {
				return (OrderedMultiEntry<K, V>) super.getEntry();
			}

			@Override
			public OrderedMapEntry<K, V> getAdjacent(boolean next) {
				return entryFor(getEntry().getAdjacent(next));
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

		class MutableOrderedSingleEntry extends MutableSingleEntry implements MutableOrderedMapEntry<K, V> {
			protected MutableOrderedSingleEntry(OrderedMultiEntry<K, V> entry) {
				super(entry);
			}

			@Override
			protected OrderedMultiEntry<K, V> getEntry() {
				return (OrderedMultiEntry<K, V>) super.getEntry();
			}

			@Override
			public MutableOrderedMapEntry<K, V> getAdjacent(boolean next) {
				return mutableEntryFor(getEntry().getAdjacent(next));
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
	 * Builds a {@link BetterSortedMap} whose values are a combination of all values of the same key from a {@link BetterSortedMultiMap}
	 * 
	 * @param <K> The key type of both maps
	 * @param <V> The value type of the multi-map
	 * @param <X> The value type of the target map
	 */
	class CombinedSortedSingleMapBuilder<K, V, X> extends CombinedSingleMapBuilder<K, V, X> {
		CombinedSortedSingleMapBuilder(BetterMultiMap<K, V> source,
			Function<? super BetterCollection<? extends V>, ? extends X> combination) {
			super(source, combination);
		}

		@Override
		public CombinedSortedSingleMapBuilder<K, V, X> withReverse(
			BiFunction<? super BetterCollection<? extends V>, ? super X, ? extends X> reverse) {
			super.withReverse(reverse);
			return this;
		}

		@Override
		public CombinedSortedSingleMapBuilder<K, V, X> withReversibility(
			Function<? super BetterCollection<? extends V>, String> reversibilityQuery) {
			super.withReversibility(reversibilityQuery);
			return this;
		}

		@Override
		public CombinedSortedSingleMapBuilder<K, V, X> withValuedReversibility(
			BiFunction<? super BetterCollection<? extends V>, ? super X, String> valuedReversibilityQuery) {
			super.withValuedReversibility(valuedReversibilityQuery);
			return this;
		}

		@Override
		public BetterSortedMap<K, X> build() {
			return new CombinedSortedSingleMap<>(getSource(), getCombination(), getReverse(), //
				getReversibilityQuery(), getValuedReversibilityQuery());
		}
	}

	/**
	 * Implements the map built by {@link BetterSortedMultiMap#singleMap(Function)}
	 * 
	 * @param <K> The key type of both maps
	 * @param <V> The value type of the source multi-map
	 * @param <X> The value type of this map
	 */
	class CombinedSortedSingleMap<K, V, X> extends CombinedSingleMap<K, V, X> implements BetterSortedMap<K, X> {
		CombinedSortedSingleMap(BetterMultiMap<K, V> outer, Function<? super BetterCollection<? extends V>, ? extends X> combination,
			BiFunction<? super BetterCollection<? extends V>, ? super X, ? extends X> reverse,
			Function<? super BetterCollection<? extends V>, String> reversibility,
			BiFunction<? super BetterCollection<? extends V>, ? super X, String> valuedReversibility) {
			super(outer, combination, reverse, reversibility, valuedReversibility);
		}

		@Override
		protected BetterSortedMultiMap<K, V> getSource() {
			return (BetterSortedMultiMap<K, V>) super.getSource();
		}

		@Override
		public BetterSortedSet<K> keySet() {
			return (BetterSortedSet<K>) super.keySet();
		}

		@Override
		public OrderedMapEntry<K, X> getEntry(K key) {
			return (OrderedMapEntry<K, X>) super.getEntry(key);
		}

		@Override
		public OrderedMapEntry<K, X> getEntryById(ElementId entryId) {
			return (OrderedMapEntry<K, X>) super.getEntryById(entryId);
		}

		@Override
		public OrderedMapEntry<K, X> searchEntries(Comparable<? super Map.Entry<K, X>> search, SortedSearchFilter filter) {
			return entryFor(getSource().searchEntries(entry -> search.compareTo(entryFor(entry)), filter));
		}

		@Override
		public OrderedMapEntry<K, X> putEntry(K key, X value, ElementId after, ElementId before, boolean first) {
			return (OrderedMapEntry<K, X>) super.putEntry(key, value, after, before, first);
		}

		@Override
		public OrderedMapEntry<K, X> getOrPutEntry(K key, Function<? super K, ? extends X> value, ElementId afterKey, ElementId beforeKey,
			boolean first, Runnable preAdd, Runnable postAdd) {
			return (OrderedMapEntry<K, X>) super.getOrPutEntry(key, value, afterKey, beforeKey, first, preAdd, postAdd);
		}

		@Override
		public MutableOrderedMapEntry<K, X> mutableEntry(ElementId entryId) {
			return mutableEntryFor(getSource().getEntryById(entryId));
		}

		@Override
		protected OrderedMapEntry<K, X> entryFor(MultiEntryHandle<K, V> outerHandle) {
			return outerHandle == null ? null : new OrderedSingleEntry((OrderedMultiEntry<K, V>) outerHandle);
		}

		@Override
		protected MutableOrderedMapEntry<K, X> mutableEntryFor(MultiEntryHandle<K, V> outerHandle) {
			return outerHandle == null ? null : new MutableOrderedSingleEntry((OrderedMultiEntry<K, V>) outerHandle);
		}

		class OrderedSingleEntry extends SingleEntry implements OrderedMapEntry<K, X> {
			protected OrderedSingleEntry(OrderedMultiEntry<K, V> entry) {
				super(entry);
			}

			@Override
			protected OrderedMultiEntry<K, V> getEntry() {
				return (OrderedMultiEntry<K, V>) super.getEntry();
			}

			@Override
			public OrderedMapEntry<K, X> getAdjacent(boolean next) {
				return entryFor(getEntry().getAdjacent(next));
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

		class MutableOrderedSingleEntry extends MutableSingleEntry implements MutableOrderedMapEntry<K, X> {
			protected MutableOrderedSingleEntry(OrderedMultiEntry<K, V> entry) {
				super(entry);
			}

			@Override
			protected OrderedMultiEntry<K, V> getEntry() {
				return (OrderedMultiEntry<K, V>) super.getEntry();
			}

			@Override
			public MutableOrderedMapEntry<K, X> getAdjacent(boolean next) {
				return mutableEntryFor(getEntry().getAdjacent(next));
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
		public BetterCollection<V> get(K key) {
			return getSource().get(key).reverse();
		}

		@Override
		public BetterSortedSet<? extends MultiEntryHandle<K, V>> entrySet() {
			return getSource().entrySet().reverse();
		}

		@Override
		public OrderedMultiEntry<K, V> getEntryById(ElementId keyId) {
			return (OrderedMultiEntry<K, V>) super.getEntryById(keyId);
		}

		@Override
		public MultiEntryHandle<K, V> searchEntries(Comparable<? super MultiEntryHandle<K, V>> search,
			BetterSortedList.SortedSearchFilter filter) {
			return MultiEntryHandle.reverse(getSource().searchEntries(e -> -search.compareTo(e.reverse()), filter.opposite()));
		}

		@Override
		public OrderedMultiEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
			ElementId beforeKey, boolean first, Runnable preAdd, Runnable postAdd) {
			return (OrderedMultiEntry<K, V>) super.getOrPutEntry(key, value, afterKey, beforeKey, first, preAdd, postAdd);
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
	class BetterSubMultiMap<K, V> extends AbstractIdentifiable implements BetterSortedMultiMap<K, V> {
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
		protected Object createIdentity() {
			return Identifiable.wrap(theWrapped.getIdentity(), "subMap", theLowerBound, theUpperBound);
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theWrapped.getThreadConstraint();
		}

		@Override
		public Transaction lock(boolean tryOnly) {
			return theWrapped.lock(tryOnly);
		}

		@Override
		public Transaction lockWrite(boolean tryOnly, Object cause) {
			return theWrapped.lockWrite(tryOnly, cause);
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return theWrapped.getCurrentCauses();
		}

		@Override
		public CoreId getCoreId() {
			return theWrapped.getCoreId();
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
		public BetterCollection<V> get(K key) {
			return theWrapped.get(key);
		}

		@Override
		public MultiEntryValueHandle<K, V> putEntry(K key, V value, ElementId afterKey, ElementId beforeKey, boolean first) {
			return theWrapped.putEntry(key, value, afterKey, beforeKey, first);
		}

		@Override
		public OrderedMultiEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
			ElementId beforeKey, boolean first, Runnable preAdd, Runnable postAdd) {
			return theWrapped.getOrPutEntry(key, value, afterKey, beforeKey, first, preAdd, postAdd);
		}

		@Override
		public int valueSize() {
			int vs = 0;
			MultiEntryHandle<K, V> entry = getWrapped().search(theLowerBound, BetterSortedList.SortedSearchFilter.Greater);
			if (entry == null)
				return 0;
			while (theUpperBound.compareTo(entry.getKey()) >= 0) {
				vs += entry.getValues().size();
				MultiEntryHandle<K, V> adj = entry.getAdjacent(true);
				if (adj == null)
					break;
				entry = adj;
			}
			return vs;
		}

		@Override
		public boolean clear() {
			try (Transaction t = lockWrite(false, null)) {
				MultiEntryHandle<K, V> entry = getWrapped().search(theLowerBound, BetterSortedList.SortedSearchFilter.Greater);
				if (entry == null)
					return false;
				boolean cleared = false;
				while (theUpperBound.compareTo(entry.getKey()) >= 0) {
					int preSize = entry.getValues().size();
					entry.getValues().clear();
					if (!cleared && !entry.getElementId().isPresent() || entry.getValues().size() < preSize)
						cleared = true;

					MultiEntryHandle<K, V> adj = entry.getAdjacent(true);
					if (adj == null)
						break;
					entry = adj;
				}
				return cleared;
			}
		}

		@Override
		public OrderedMultiEntry<K, V> getEntry(K key) {
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
		public OrderedMultiEntry<K, V> getEntryById(ElementId entryId) {
			return theWrapped.getEntryById(entryId);
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

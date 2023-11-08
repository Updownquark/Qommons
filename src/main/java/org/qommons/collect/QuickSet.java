package org.qommons.collect;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.qommons.ArrayUtils;

/**
 * <p>
 * A sorted set of strings that is optimized for indexOf operations on small sets. Large performance gains can be obtained by using
 * instances of this set as key sets and {@link #createMap() creating maps} with it, as the same index can be used across all the maps.
 * </p>
 * <p>
 * In addition, instances of QuickSet and QuickMap are thread safe without locking because QuickSet is immutable.
 * </p>
 * 
 * @param <E> The type of value in the set
 */
public final class QuickSet<E> extends AbstractSet<E> implements Comparable<QuickSet<E>> {
	private static final QuickSet<?> EMPTY = new QuickSet<>(null, new Object[0]);
	private static final QuickMap<?, ?> EMPTY_MAP = new EmptyQuickMap<>();
	private static final int MAX_CACHED_MAPS = 100;

	/**
	 * @param <K> The type of value for the set
	 * @return An empty QuickSet
	 */
	public static <K> QuickSet<K> empty() {
		return (QuickSet<K>) EMPTY;
	}

	/**
	 * @param <E> The type of comparable to create the set for
	 * @param keys The values for the set
	 * @return A QuickSet containing the given values
	 */
	public static <E extends Comparable<E>> QuickSet<E> of(E... keys) {
		if (keys.length == 0)
			return (QuickSet<E>) EMPTY;
		else
			return new QuickSet<>(Comparable::compareTo, keys);
	}

	/**
	 * @param <E> The type of comparable to create the set for
	 * @param keys The values for the set
	 * @return A QuickSet containing the given values
	 */
	public static <E extends Comparable<E>> QuickSet<E> of(Collection<? extends E> keys) {
		if (keys.isEmpty())
			return (QuickSet<E>) EMPTY;
		else
			return new QuickSet<>(Comparable::compareTo, keys);
	}

	/**
	 * @param <E> The type of comparable to create the set for
	 * @param keys The values for the set
	 * @return A QuickSet containing the given values
	 */
	public static <E extends Comparable<E>> QuickSet<E> ofSorted(SortedSet<E> keys) {
		if (keys.isEmpty())
			return (QuickSet<E>) EMPTY;
		else
			return new QuickSet<>(keys.comparator(), keys);
	}

	/**
	 * @param <E> The values to create the set for
	 * @param compare The comparator for the values
	 * @param keys The values for the set
	 * @return A QuickSet containing the given values
	 */
	public static <E> QuickSet<E> of(Comparator<? super E> compare, E... keys) {
		if (keys.length == 0)
			return (QuickSet<E>) EMPTY;
		else
			return new QuickSet<>(compare, keys);
	}

	/**
	 * @param <E> The values to create the set for
	 * @param compare The comparator for the values
	 * @param keys The values for the set
	 * @return A QuickSet containing the given values
	 */
	public static <E> QuickSet<E> of(Comparator<? super E> compare, Collection<? extends E> keys) {
		if (keys.isEmpty())
			return (QuickSet<E>) EMPTY;
		else
			return new QuickSet<>(compare, keys);
	}

	// Package private here means no synthetic accessors
	final Comparator<? super E> compare;
	final Object[] theKeys;
	private final boolean small;
	private boolean hashed;
	private int hashCode;
	private ConcurrentLinkedQueue<QuickMapImpl<?, ?>> theMapCache;

	/**
	 * @param compare the comparator for this set
	 * @param keys The values for the set
	 */
	public QuickSet(Comparator<? super E> compare, E... keys) {
		this.compare = compare;
		theKeys = keys.clone();
		Arrays.sort(theKeys, (o1, o2) -> compare.compare((E) o1, (E) o2));
		small = keys.length <= 10;
	}

	/**
	 * @param compare the comparator for this set
	 * @param keys The values for the set
	 */
	public QuickSet(Comparator<? super E> compare, Collection<? extends E> keys) {
		this.compare = compare;
		theKeys = keys.toArray();
		Arrays.sort(theKeys, (o1, o2) -> compare.compare((E) o1, (E) o2));
		small = theKeys.length <= 10;
	}
	
	@Override
	public int size() {
		return theKeys.length;
	}

	/**
	 * @param index The index to get the value in
	 * @return The value in this set at the given index
	 */
	public E get(int index) {
		return (E) theKeys[index];
	}

	@Override
	public Iterator<E> iterator() {
		return ((List<E>) Arrays.asList(theKeys)).iterator();
	}

	@Override
	public Object[] toArray() {
		return theKeys.clone();
	}

	/**
	 * @param key The value to find in this set
	 * @return The index of the given value in this set, or <code>-index-1</code> if the value is not found, where <code>index</code> is
	 *         where the value would belong in this set.
	 */
	public int indexOf(E key) {
		if (small) {
			for (int i = 0; i < theKeys.length; i++) {
				if (theKeys[i] == key) {
					return i;
				}
			}
			return ArrayUtils.binarySearch(0, theKeys.length - 1, index -> {
				return compare(index, key);
			});
		}
		return ArrayUtils.binarySearch(0, theKeys.length - 1, index -> {
			if (theKeys[index] == key) {
				return 0;
			}
			return compare(index, key);
		});
	}

	int compare(int keyIndex, E test) {
		E key = (E) theKeys[keyIndex];
		return compare.compare(test, key);
	}

	/**
	 * @param key The object to find in this set
	 * @return Same as {@link #indexOf(Object)}, but catches any {@link ClassCastException} thrown by the comparator and returns -1
	 */
	public int indexOfTolerant(Object key) {
		try {
			return indexOf((E) key);
		} catch (ClassCastException e) {
			return -1;
		}
	}

	@Override
	public boolean contains(Object o) {
		try {
			return indexOf((E) o) >= 0;
		} catch (ClassCastException e) {
			return false;
		}
	}

	/**
	 * Creates a {@link QuickMap} whose key set is this
	 * 
	 * @param <V> The type of values for the map
	 * @return The new map
	 */
	public <V> QuickMap<E, V> createMap() {
		if (theKeys.length == 0)
			return (QuickMap<E, V>) EMPTY_MAP;
		if (theMapCache != null) {
			QuickMapImpl<?, ?> map = theMapCache.poll();
			if (map != null) {
				map.init(null);
				return (QuickMap<E, V>) map;
			}
		}
		return new QuickMapImpl<>(this, null);
	}

	/**
	 * Creates a {@link QuickMap} whose key set is this and whose values are predetermined for each key
	 * 
	 * @param <V> The type of values for the map
	 * @param valueProducer The producer to create or access values for each key in this set
	 * @return The new map
	 */
	public <V> QuickMap<E, V> createMap(IntFunction<V> valueProducer) {
		if (theMapCache != null) {
			QuickMapImpl<?, ?> map = theMapCache.poll();
			if (map != null) {
				((QuickMapImpl<?, V>) map).init(valueProducer);
				return (QuickMap<E, V>) map;
			}
		}
		return new QuickMapImpl<>(this, valueProducer);
	}

	/**
	 * Creates a {@link QuickMap} whose key set is this and whose values are dynamically calculated for each key each time they are accessed
	 * 
	 * @param <V> The type of values for the map
	 * @param valueProducer The producer to create or access values for each key in this set
	 * @return The new map
	 */
	public <V> QuickMap<E, V> createDynamicMap(IntFunction<V> valueProducer) {
		return new DynamicQuickMapImpl<>(this, valueProducer);
	}

	private static volatile int RELEASE_COUNT;
	private static volatile boolean IS_CACHING_MAPS;

	void released(QuickMapImpl<?, ?> map) {
		int maxCached = MAX_CACHED_MAPS;
		if (maxCached == 0) {
			return;
		} else if (theMapCache == null) {
			theMapCache = new ConcurrentLinkedQueue<>();
		}
		int count = RELEASE_COUNT + 1;
		boolean caching;
		if (count % (maxCached / 2) == 0) {
			count = 0;
			IS_CACHING_MAPS = caching = theMapCache.size() < maxCached;
		} else {
			caching=IS_CACHING_MAPS;
		}
		RELEASE_COUNT = count;
		if (caching) {
			theMapCache.add(map);
		}
	}

	/** @return This set as a {@link java.util.Set} */
	public Set<E> asJavaSet() {
		return new JavaSet<>(this);
	}

	@Override
	public int compareTo(QuickSet<E> o) {
		int comp = theKeys.length - o.theKeys.length;
		if (comp != 0) {
			return comp;
		}
		for (int k = 0; k < theKeys.length; k++) {
			if (theKeys[k] == o.theKeys[k]) {
				continue;
			}
			comp = compare(k, (E) o.theKeys[k]);
			if (comp != 0) {
				return comp;
			}
		}
		return 0;
	}

	@Override
	public int hashCode() {
		if (!hashed) {
			int hash = 0;
			for (Object key : theKeys) {
				hash += key.hashCode();
			}
			hashCode = hash;
			hashed = true;
		}
		return hashCode;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		} else if (!(obj instanceof QuickSet)) {
			return false;
		}
		QuickSet<?> other = (QuickSet<?>) obj;
		// Hash code is likely to be more expensive than equals
		// Don't compute the hash code if it's not already, but if we have it, use it
		if (hashed && other.hashed && hashCode != other.hashCode) {
			return false;
		} else if (theKeys.length != other.theKeys.length) {
			return false;
		}
		boolean allIdentical = true;
		for (int i = 0; i < theKeys.length; i++) {
			if (theKeys[i] == other.theKeys[i]) {
				continue;
			}
			allIdentical = false;
		}
		if (allIdentical) {
			return true;
		}
		// We already checked the length
		try {
			for (int i = 0; i < theKeys.length; i++) {
				if (compare.compare((E) theKeys[i], (E) other.theKeys[i]) != 0)
					return false;
			}
		} catch (ClassCastException e) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return Arrays.toString(theKeys);
	}

	// This interface does not extend Map<String, V> because its functionality differs a bit
	// It throws exceptions when a value is requested for a key that does not exist in the key set
	/**
	 * <p>
	 * A "map" whose key set is a {@link QuickSet}. This interface does not extend Map<String, V> because its functionality differs a bit.
	 * In particular it throws exceptions when a value is requested for a key that does not exist in the key set.
	 * </p>
	 * <p>
	 * This map, like {@link QuickSet}, has extremely high performance for small key sets. Indexes for keys in the key set may be used to
	 * access values in this map.
	 * </p>
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	public interface QuickMap<K, V> {
		/**
		 * @param <K> The key type for the map
		 * @param <V> The value type for the map
		 * @return An empty map
		 */
		static <K, V> QuickMap<K, V> empty() {
			return QuickSet.<K> empty().<V> createMap();
		}

		/** @return This map's key set */
		QuickSet<K> keySet();

		/** @return Whether this map's key set is empty */
		default boolean isEmpty() {
			return keySet().isEmpty();
		}

		/** @return The number of keys in this map */
		default int keySize() {
			return keySet().size();
		}

		/**
		 * @param key The key to get the index for
		 * @return The index in this map's key set of the given key
		 * @throws IllegalArgumentException If the key is not found
		 */
		default int keyIndex(K key) throws IllegalArgumentException {
			int index = keySet().indexOf(key);
			if (index < 0) {
				throw new IllegalArgumentException("Key is not present: " + key);
			}
			return index;
		}

		/**
		 * @param key The key to get the index for
		 * @return The index in this map's key set of the given key, or -1 if it is not found
		 */
		default int keyIndexTolerant(Object key) {
			int index;
			try {
				index = keySet().indexOf((K) key);
			} catch (ClassCastException e) {
				return -1;
			}
			return index;
		}

		/** @return The number of non-null values in this map */
		int valueCount();

		/**
		 * @param index The index in the key set for the value to get
		 * @return The value in this map for the key at the given index in the key set
		 */
		V get(int index);

		/**
		 * @param index The index in the key set for the value to set
		 * @param value The value to set for the key at the given index in the key set
		 * @return The previously set value for the key
		 */
		V put(int index, V value);

		/**
		 * @param index The index in the key set for the value to set
		 * @param valueProducer Produces a value for the key if none is set in this map
		 * @return The retrieved or created value
		 */
		V computeIfAbsent(int index, Function<? super K, ? extends V> valueProducer);

		/**
		 * @param key The key whose value to get from the map
		 * @return The value in this map for the given key
		 * @throws IllegalArgumentException If the given key is not present in the map
		 */
		default V get(K key) throws IllegalArgumentException {
			return get(//
					keyIndex(key));
		}

		/**
		 * @param key The key whose value to get from the map
		 * @return The value in this map for the given key, or null if the key is not present in the map
		 */
		default V getIfPresent(K key) {
			int keyIndex = keyIndexTolerant(key);
			if (keyIndex < 0)
				return null;
			return get(keyIndex);
		}

		/**
		 * @param key The key to set the value for
		 * @param value The value for the key
		 * @return The previously set value for the key
		 * @throws IllegalArgumentException If the key is not present in the map
		 */
		default V put(K key, V value) throws IllegalArgumentException {
			return put(keyIndex(key), value);
		}

		/**
		 * @param key The key to get or set the value for
		 * @param valueProducer Produces a value for the key if none is set in this map
		 * @return The retrieved or created value
		 */
		default V computeIfAbsent(K key, Function<? super K, ? extends V> valueProducer) {
			return computeIfAbsent(keyIndex(key), valueProducer);
		}

		/**
		 * Like {@link #put(int, Object)} but returns this map
		 * 
		 * @param keyIndex The index in the key set for the value to set
		 * @param value The value to set for the key at the given index in the key set
		 * @return This map
		 */
		default QuickMap<K, V> with(int keyIndex, V value) {
			put(keyIndex, value);
			return this;
		}

		/**
		 * Like {@link #put(Object, Object)} but returns this map
		 * 
		 * @param key The key to set the value for
		 * @param value The value for the key
		 * @return The previously set value for the key
		 * @throws IllegalArgumentException If the key is not present in the map
		 */
		default QuickMap<K, V> with(K key, V value) throws IllegalArgumentException {
			put(key, value);
			return this;
		}

		/**
		 * Adds all entries in the given map for which the key exists in this map into this map
		 * 
		 * @param values The values to add
		 * @return This map
		 */
		default QuickMap<K, V> withAll(Map<? extends K, ? extends V> values) {
			for (Map.Entry<? extends K, ? extends V> entry : values.entrySet()) {
				int idx = keySet().indexOf(entry.getKey());
				if (idx >= 0)
					put(idx, entry.getValue());
			}
			return this;
		}

		/** Sets all values to null in this map */
		void clear();

		/** @return An iterable over the values in this map for each key in the key set */
		List<V> allValues();

		/** @return An iterable over all values that have been set in this map */
		Iterable<V> values();

		/** @return A stream over all values that have been set in this map */
		default Stream<V> stream() {
			return StreamSupport.stream(Spliterators.spliterator(values().iterator(), valueCount(), 0), false);
		}

		/** @return An array of map entries for each key in this map's key set */
		default Map.Entry<K, V>[] toEntryArray() {
			Map.Entry<K, V>[] array = new Map.Entry[keySet().size()];
			for (int i = 0; i < array.length; i++) {
				array[i] = new SimpleMapEntry<>(keySet().get(i), get(i));
			}
			return array;
		}

		/** @return An independent copy of this map with the same key set and values */
		default QuickMap<K, V> copy() {
			if (keySet().isEmpty())
				return this;
			QuickMap<K, V> copy = keySet().createMap();
			for (int i = 0; i < keySet().size(); i++) {
				copy.put(i, get(i));
			}
			return copy;
		}

		/**
		 * @param value The value to set for each of this map's keys
		 * @return This map
		 */
		default QuickMap<K, V> fill(V value) {
			for (int i = 0; i < keySize(); i++)
				put(i, value);
			return this;
		}

		/** @return An unmodifiable view of this map */
		QuickMap<K, V> unmodifiable();

		/** @return A {@link Map} that behaves according to that class's contract but whose key set and values are the same as this */
		default Map<K, V> asJavaMap() {
			return new ParamMapAsMap<>(this);
		}

		/** If supported, allows this map to be released and re-used later */
		void release();

		/**
		 * @param <K> The key type of the map
		 * @param <V> The value type of the map
		 * @param values The map to create a QuickMap from
		 * @param keyCompare The comparator for the keys
		 * @return A QuickMap whose key set contains all keys from the given map and whose values are the same as those in the given map
		 */
		static <K, V> QuickMap<K, V> of(Map<K, V> values, Comparator<? super K> keyCompare) {
			return QuickSet.of(keyCompare, values.keySet()).<V> createMap().withAll(values);
		}
	}

	private static final class EmptyQuickMap<K, T> implements QuickMap<K, T> {
		@Override
		public QuickSet<K> keySet() {
			return (QuickSet<K>) EMPTY;
		}

		@Override
		public int valueCount() {
			return 0;
		}

		@Override
		public T get(int index) {
			throw new IndexOutOfBoundsException(index + " of 0");
		}

		@Override
		public T put(int index, T value) {
			throw new IndexOutOfBoundsException(index + " of 0");
		}

		@Override
		public T computeIfAbsent(int index, Function<? super K, ? extends T> valueProducer) {
			throw new IndexOutOfBoundsException(index + " of 0");
		}

		@Override
		public void clear() {}

		@Override
		public List<T> allValues() {
			return Collections.emptyList();
		}

		@Override
		public Iterable<T> values() {
			return Collections.emptyList();
		}

		@Override
		public QuickMap<K, T> unmodifiable() {
			return this;
		}

		@Override
		public void release() {}

		@Override
		public String toString() {
			return "{}";
		}
	}

	/**
	 * A {@link Set} backed by a {@link QuickSet} whose values are ordered in a custom way
	 * 
	 * @param <E> The type of values in the set
	 */
	public static final class CustomOrderedQuickSet<E> extends AbstractSet<E> {
		final QuickSet<E> theSet;
		final int theSize;
		final int[] theCustomOrder;
		final int[] theReverseCustomOrder;

		/**
		 * @param set The {@link QuickSet} of values
		 * @param order A set containing all values in <code>set</code> and whose order will be followed when iterating over values in this
		 *        set
		 */
		public CustomOrderedQuickSet(QuickSet<E> set, Set<E> order) {
			theSet = set;
			theCustomOrder = new int[set.size()];
			theReverseCustomOrder = new int[set.size()];
			Arrays.fill(theReverseCustomOrder, -1);
			int i = 0;
			for (E s : order) {
				int keyIndex = set.indexOf(s);
				theCustomOrder[i] = keyIndex;
				theReverseCustomOrder[keyIndex] = i;
				i++;
			}
			theSize = i;
		}

		/** @return The QuickSet backing this set */
		public QuickSet<E> getQuickSet() {
			return theSet;
		}

		@Override
		public int size() {
			return theSize;
		}

		@Override
		public boolean isEmpty() {
			return theSize == 0;
		}

		@Override
		public boolean contains(Object o) {
			if (!(o instanceof String)) {
				return false;
			}
			int keyIndex = theSet.indexOfTolerant(o);
			if (keyIndex < 0) {
				return false;
			}
			return theReverseCustomOrder[keyIndex] >= 0;
		}

		@Override
		public Iterator<E> iterator() {
			return new COSSSSIterator();
		}

		@Override
		public Object[] toArray() {
			Object[] array = new String[theSize];
			for (int i = 0; i < theSize; i++) {
				array[i] = theSet.get(theCustomOrder[i]);
			}
			return array;
		}

		@Override
		public <T> T[] toArray(T[] a) {
			if (a.length < theSize) {
				if (a instanceof String[]) {
					a = (T[]) new String[theSize];
				} else {
					a = (T[]) new Object[theSize];
				}
			}
			for (int i = 0; i < theSize; i++) {
				a[i] = (T) theSet.get(theCustomOrder[i]);
			}
			return a;
		}

		private class COSSSSIterator implements Iterator<E> {
			private int index;

			@Override
			public boolean hasNext() {
				return index < theSize;
			}

			@Override
			public E next() {
				if (index == theSize) {
					throw new NoSuchElementException();
				}
				Object s = theSet.get(theCustomOrder[index]);
				index++;
				return (E) s;
			}
		}
	}

	/**
	 * A {@link Map} backed by a {@link QuickSet.QuickMap} whose {@link Map#keySet()} is a {@link QuickSet.CustomOrderedQuickSet}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	public static final class CustomOrderedQuickMap<K, V> extends AbstractMap<K, V> {
		final QuickMap<K, V> theMap;
		final CustomOrderedQuickSet<K> theKeySet;

		/**
		 * @param map The quick map to back this map
		 * @param order The custom order for the key set
		 */
		public CustomOrderedQuickMap(QuickMap<K, V> map, Set<K> order) {
			theMap = map;
			if (order instanceof CustomOrderedQuickSet) {
				theKeySet = (CustomOrderedQuickSet<K>) order;
			} else {
				theKeySet = new CustomOrderedQuickSet<>(theMap.keySet(), order);
			}
		}

		/** @return The QuickMap backing this map */
		public QuickMap<K, V> getMap() {
			return theMap;
		}

		@Override
		public int size() {
			return theKeySet.theSize;
		}

		@Override
		public boolean isEmpty() {
			return theKeySet.isEmpty();
		}

		/**
		 * @param index The index in this map of the value to get
		 * @return The value for the key at the given index in this map's key set
		 */
		public V getAt(int index) {
			return theMap.get(theKeySet.theReverseCustomOrder[index]);
		}

		/**
		 * @param index The index in this map of the value to set
		 * @param value The value for the key at the given index in this map's key set
		 * @return The previously set value for the key
		 */
		public V putAt(int index, V value) {
			return theMap.put(theKeySet.theReverseCustomOrder[index], value);
		}

		@Override
		public V get(Object key) {
			if (!(key instanceof String)) {
				return null;
			}
			int index = theMap.keySet().indexOfTolerant(key);
			if (index < 0 || theKeySet.theReverseCustomOrder[index] < 0) {
				return null;
			}
			return theMap.get(index);
		}

		@Override
		public V put(K key, V value) {
			int index = theMap.keyIndexTolerant(key);
			if (index < 0 || theKeySet.theReverseCustomOrder[index] < 0) {
				return null;
			}
			return theMap.put(index, value);
		}

		@Override
		public V remove(Object key) {
			int index = theMap.keyIndexTolerant(key);
			if (index < 0)
				return null;
			return theMap.put(index, null);
		}

		@Override
		public void clear() {
			for (int i = 0; i < theKeySet.theSize; i++) {
				theMap.put(theKeySet.theCustomOrder[i], null);
			}
		}

		@Override
		public CustomOrderedQuickSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public Set<Map.Entry<K, V>> entrySet() {
			return new EntrySet();
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Map)) {
				return false;
			}
			Map<?, ?> other = (Map<?, ?>) o;
			if (theKeySet.theSize != other.size()) {
				return false;
			}
			if (other instanceof CustomOrderedQuickMap) {
				CustomOrderedQuickMap<?, ?> otherSSMap = (CustomOrderedQuickMap<?, ?>) other;
				if (theMap.keySet().equals(otherSSMap.theMap.keySet())
						&& Arrays.equals(theKeySet.theCustomOrder, otherSSMap.theKeySet.theCustomOrder)) {
					if (theMap == otherSSMap.theMap) {
						return true;
					}
					// Can do this by index instead of looking strings up
					for (int i = 0; i < theKeySet.theSize; i++) {
						int keyIndex = theKeySet.theCustomOrder[i];
						if (!Objects.equals(theMap.get(keyIndex), otherSSMap.theMap.get(keyIndex))) {
							return false;
						}
					}
					return true;
				}
			}
			for (int i = 0; i < theKeySet.theSize; i++) {
				int keyIndex = theKeySet.theCustomOrder[i];
				if (!Objects.equals(theMap.get(keyIndex), other.get(theMap.keySet().get(keyIndex)))) {
					return false;
				}
			}
			return true;
		}

		private class EntrySet extends AbstractSet<Map.Entry<K, V>> {
			@Override
			public int size() {
				return theKeySet.theSize;
			}

			@Override
			public boolean isEmpty() {
				return theKeySet.isEmpty();
			}

			@Override
			public boolean contains(Object o) {
				return CustomOrderedQuickMap.this.containsKey(o);
			}

			@Override
			public Iterator<Map.Entry<K, V>> iterator() {
				return new EntryIterator();
			}
		}

		private class EntryIterator implements Iterator<Map.Entry<K, V>> {
			private int index;

			@Override
			public boolean hasNext() {
				return index < theKeySet.theSize;
			}

			@Override
			public Map.Entry<K, V> next() {
				if (index == theKeySet.theSize) {
					throw new NoSuchElementException();
				}
				int keyIndex = theKeySet.theCustomOrder[index];
				index++;
				return new Entry(keyIndex);
			}
		}

		private class Entry implements Map.Entry<K, V> {
			private final int index;

			Entry(int index) {
				this.index = index;
			}

			@Override
			public K getKey() {
				return theMap.keySet().get(index);
			}

			@Override
			public V getValue() {
				return theMap.get(index);
			}

			@Override
			public V setValue(V value) {
				return theMap.put(index, value);
			}
		}
	}

	static final class JavaSet<E> extends AbstractSet<E> {
		private final QuickSet<E> theSet;

		public JavaSet(QuickSet<E> set) {
			theSet = set;
		}

		@Override
		public Iterator<E> iterator() {
			return theSet.iterator();
		}

		@Override
		public int size() {
			return theSet.size();
		}
	}

	static final class ParamMapAsMap<K, V> extends AbstractMap<K, V> {
		private final QuickMap<K, V> theMap;

		ParamMapAsMap(QuickMap<K, V> map) {
			theMap = map;
		}

		@Override
		public int size() {
			return theMap.keySet().size();
		}

		@Override
		public boolean isEmpty() {
			return theMap.keySet().isEmpty();
		}

		@Override
		public boolean containsKey(Object key) {
			return theMap.keySet().contains(key);
		}

		@Override
		public boolean containsValue(Object value) {
			for (int i = 0; i < theMap.keySet().size(); i++) {
				if (Objects.equals(theMap.get(i), value)) {
					return true;
				}
			}
			return false;
		}

		@Override
		public V get(Object key) {
			if (!(key instanceof String)) {
				return null;
			}
			int index;
			try {
				index = theMap.keySet().indexOf((K) key);
			} catch (ClassCastException e) {
				return null;
			}
			if (index < 0) {
				return null;
			}
			return theMap.get(index);
		}

		@Override
		public V put(K key, V value) {
			return theMap.put(key, value);
		}

		@Override
		public V remove(Object key) {
			if (!(key instanceof String)) {
				return null;
			}
			int index;
			try {
				index = theMap.keySet().indexOf((K) key);
			} catch (ClassCastException e) {
				return null;
			}
			if (index < 0) {
				return null;
			}
			return theMap.put(index, null);
		}

		@Override
		public void clear() {
			theMap.clear();
		}

		@Override
		public Set<K> keySet() {
			return theMap.keySet();
		}

		@Override
		public Set<Map.Entry<K, V>> entrySet() {
			return new EntrySet();
		}

		private class EntrySet extends AbstractSet<Map.Entry<K, V>> {
			@Override
			public Iterator<Map.Entry<K, V>> iterator() {
				return new SSSEntryIterator();
			}

			@Override
			public int size() {
				return theMap.keySet().size();
			}
		}

		private class SSSEntryIterator implements Iterator<Map.Entry<K, V>> {
			private int index;

			@Override
			public boolean hasNext() {
				return index < theMap.keySet().size();
			}

			@Override
			public Map.Entry<K, V> next() {
				SSSEntry entry = new SSSEntry(index);
				index++;
				return entry;
			}
		}

		private class SSSEntry implements Map.Entry<K, V> {
			private final int index;

			SSSEntry(int index) {
				this.index = index;
			}

			@Override
			public K getKey() {
				return theMap.keySet().get(index);
			}

			@Override
			public V getValue() {
				return theMap.get(index);
			}

			@Override
			public V setValue(V value) {
				return theMap.put(index, value);
			}

			@Override
			public int hashCode() {
				return getKey().hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof Map.Entry && getKey().equals(((Map.Entry<?, ?>) obj).getKey());
			}

			@Override
			public String toString() {
				return getKey() + "=" + getValue();
			}
		}
	}

	private static final class ParamMapValueIterable<V> implements Iterable<V> {
		private final Object[] theValues;
		private final IntFunction<V> theValueFunction;

		ParamMapValueIterable(Object[] values, IntFunction<V> valueFunction) {
			theValues = values;
			theValueFunction = valueFunction;
		}

		@Override
		public Iterator<V> iterator() {
			return new ParamMapValueIterator<>(theValues, theValueFunction, false);
		}
	}

	private static final class ParamMapValueList<V> extends AbstractList<V> {
		private final Object[] theValues;
		private final IntFunction<V> theValueFunction;

		ParamMapValueList(Object[] values, IntFunction<V> valueFunction) {
			theValues = values;
			theValueFunction = valueFunction;
		}

		@Override
		public V get(int index) {
			if (theValues[index] == null && theValueFunction != null)
				theValues[index] = theValueFunction.apply(index);
			return (V) theValues[index];
		}

		@Override
		public int size() {
			return theValues.length;
		}
	}

	private static final class ParamMapValueIterator<V> implements Iterator<V> {
		private final Object[] theValues;
		private final IntFunction<V> theValueFunction;
		private final boolean allValues;
		private int index;
		private boolean hasChecked;

		ParamMapValueIterator(Object[] values, IntFunction<V> valueFunction, boolean allValues) {
			theValues = values;
			theValueFunction = valueFunction;
			this.allValues = allValues;

			if (!allValues) {
				while (index < theValues.length && get() == null) {
					hasChecked = false;
					index++;
				}
			}
		}

		private V get() {
			if (theValueFunction != null && !hasChecked && theValues[index] == null) {
				hasChecked = true;
				theValues[index] = theValueFunction.apply(index);
			}
			return (V) theValues[index];
		}

		@Override
		public boolean hasNext() {
			return index < theValues.length;
		}

		@Override
		public V next() {
			V value = get();
			do {
				hasChecked = false;
				index++;
			} while (!allValues && index < theValues.length && get() == null);
			return value;
		}
	}

	static final class QuickMapImpl<K, V> implements QuickMap<K, V> {
		private final QuickSet<K> theKeys;
		private Object[] theValues;
		private IntFunction<V> theValueFunction;
		private UnmodifiableQuickMap<K, V> unmodifiable;

		private int hashCode;

		QuickMapImpl(QuickSet<K> keys, IntFunction<V> valueProducer) {
			theKeys = keys;
			theValueFunction = valueProducer;

			hashCode = -1;
		}

		void init(IntFunction<V> valueProducer) {
			theValueFunction = valueProducer;
			if (theValues != null)
				Arrays.fill(theValues, null);
		}

		@Override
		public QuickSet<K> keySet() {
			return theKeys;
		}

		@Override
		public int valueCount() {
			if (theValues == null)
				return 0;
			int sz = 0;
			for (int i = 0; i < theValues.length; i++) {
				if (theValues[i] != null) {
					sz++;
				}
			}
			return sz;
		}

		@Override
		public V get(int index) {
			if (theValues == null) {
				if (theValueFunction == null) {
					theKeys.get(index); // Let the key set throw an out of bounds exception if needed
					return null;
				} else
					theValues = new Object[theKeys.size()];
			}
			V value = (V) theValues[index];
			if (value == null && theValueFunction != null) {
				theValues[index] = value = theValueFunction.apply(index);
				if (value != null) {
					hashCode = -1;
				}
			}
			return value;
		}

		@Override
		public V put(int index, V value) {
			if (theValues == null)
				theValues = new Object[theKeys.size()];
			V old = (V) theValues[index];
			theValues[index] = value;
			hashCode = -1;
			return old;
		}

		@Override
		public V computeIfAbsent(int index, Function<? super K, ? extends V> valueProducer) {
			if (theValues == null)
				theValues = new Object[theKeys.size()];
			V value = (V) theValues[index];
			if (value == null) {
				theValues[index] = value = valueProducer.apply(theKeys.get(index));
				if (value != null) {
					hashCode = -1;
				}
			}
			return value;
		}

		@Override
		public void clear() {
			hashCode = -1;
			if (theValues != null)
				Arrays.fill(theValues, null);
		}

		@Override
		public List<V> allValues() {
			if (theValues == null)
				theValues = new Object[theKeys.size()];
			return new ParamMapValueList<>(theValues, theValueFunction);
		}

		@Override
		public Iterable<V> values() {
			if (theValues == null)
				return Collections.emptyList();
			return new ParamMapValueIterable<>(theValues, theValueFunction);
		}

		@Override
		public QuickMap<K, V> unmodifiable() {
			if (unmodifiable == null) {
				unmodifiable = new UnmodifiableQuickMap<>(this);
			}
			return unmodifiable;
		}

		@Override
		public void release() {
			theKeys.released(this);
		}

		@Override
		public int hashCode() {
			if (hashCode == -1) {
				int hc = theKeys.hashCode();
				if (theValues != null) {
					for (int i = 0; i < theValues.length; i++) {
						hc = hc * 31 + Objects.hashCode(get(i));
					}
				}
				hashCode = hc;
			}
			return hashCode;
		}

		@Override
		public boolean equals(Object obj) {
			if(this==obj) {
				return true;
			} else if (!(obj instanceof QuickMap)) {
				return false;
			}
			QuickMap<?, ?> other = (QuickMap<?, ?>) obj;
			if (!theKeys.equals(other.keySet())) {
				return false;
			}
			if (theValues == null)
				theValues = new Object[theKeys.size()];
			for (int i = 0; i < theValues.length; i++) {
				if (!Objects.equals(get(i), other.get(i))) {
					return false;
				}
			}
			return true;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append('{');
			boolean first = true;
			if (theValues == null)
				theValues = new Object[theKeys.size()];
			for (int i = 0; i < theValues.length; i++) {
				if (!first) {
					str.append(", ");
				}
				first = false;
				str.append(theKeys.get(i)).append('=');
				if (theValues[i] == null && theValueFunction != null) {
					str.append('?');
				} else {
					str.append(theValues[i]);
				}
			}
			str.append('}');
			return str.toString();
		}
	}

	static final class DynamicQuickMapImpl<K, V> implements QuickMap<K, V> {
		private final QuickSet<K> theKeys;
		private final IntFunction<? extends V> theValues;
		private UnmodifiableQuickMap<K, V> unmodifiable;

		DynamicQuickMapImpl(QuickSet<K> keySet, IntFunction<? extends V> values) {
			theKeys = keySet;
			theValues = values;
		}

		@Override
		public QuickSet<K> keySet() {
			return theKeys;
		}

		@Override
		public int valueCount() {
			int vc = 0;
			for (int i = 0; i < theKeys.size(); i++)
				if (theValues.apply(i) != null)
					vc++;
			return vc;
		}

		@Override
		public V get(int index) {
			if (index < 0 || index >= theKeys.size())
				throw new IndexOutOfBoundsException(index + " of " + theKeys.size());
			return theValues.apply(index);
		}

		@Override
		public V put(int index, V value) {
			throw new UnsupportedOperationException("This view is not modifiable");
		}

		@Override
		public V computeIfAbsent(int index, Function<? super K, ? extends V> valueProducer) {
			return get(index);
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException("This view is not modifiable");
		}

		@Override
		public List<V> allValues() {
			return new AbstractList<V>() {
				@Override
				public V get(int index) {
					return theValues.apply(index);
				}

				@Override
				public int size() {
					return theKeys.size();
				}
			};
		}

		@Override
		public Iterable<V> values() {
			return new Iterable<V>() {
				@Override
				public Iterator<V> iterator() {
					return new Iterator<V>() {
						private int theIndex;
						private boolean hasNext;
						private V theNext;

						@Override
						public boolean hasNext() {
							while (!hasNext && theIndex < theKeys.size()) {
								theNext = theValues.apply(theIndex++);
								hasNext = theNext != null;
							}
							return hasNext;
						}

						@Override
						public V next() {
							if (!hasNext())
								throw new NoSuchElementException();
							hasNext = false;
							V value = theNext;
							theNext = null;
							return value;
						}
					};
				}
			};
		}

		@Override
		public QuickMap<K, V> unmodifiable() {
			if (unmodifiable == null) {
				unmodifiable = new UnmodifiableQuickMap<>(this);
			}
			return unmodifiable;
		}

		@Override
		public void release() {}

		@Override
		public int hashCode() {
			int hc = theKeys.hashCode();
			if (theValues != null) {
				for (int i = 0; i < theKeys.size(); i++) {
					hc = hc * 31 + Objects.hashCode(get(i));
				}
			}
			return hc;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			} else if (!(obj instanceof QuickMap)) {
				return false;
			}
			QuickMap<?, ?> other = (QuickMap<?, ?>) obj;
			if (!theKeys.equals(other.keySet())) {
				return false;
			}
			for (int i = 0; i < theKeys.size(); i++) {
				if (!Objects.equals(get(i), other.get(i))) {
					return false;
				}
			}
			return true;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append('{');
			boolean first = true;
			for (int i = 0; i < theKeys.size(); i++) {
				if (!first) {
					str.append(", ");
				}
				first = false;
				str.append(theKeys.get(i)).append('=');
				str.append(get(i));
			}
			str.append('}');
			return str.toString();
		}
	}

	static final class UnmodifiableQuickMap<K, V> implements QuickMap<K, V> {
		private final QuickMap<K, V> theWrapped;

		UnmodifiableQuickMap(QuickMap<K, V> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public QuickSet<K> keySet() {
			return theWrapped.keySet();
		}

		@Override
		public int valueCount() {
			return theWrapped.valueCount();
		}

		@Override
		public V get(int index) {
			return theWrapped.get(index);
		}

		@Override
		public V put(int index, V value) {
			throw new UnsupportedOperationException("This map supplies its own values");
		}

		@Override
		public V computeIfAbsent(int index, Function<? super K, ? extends V> valueProducer) {
			throw new UnsupportedOperationException("This map supplies its own values");
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException("This map supplies its own values");
		}

		@Override
		public List<V> allValues() {
			return Collections.unmodifiableList(theWrapped.allValues());
		}

		@Override
		public Iterable<V> values() {
			return theWrapped.values();
		}

		// Non-modifying default methods overridden for performance

		@Override
		public int keyIndex(K key) {
			return theWrapped.keyIndex(key);
		}

		@Override
		public V get(K key) {
			return theWrapped.get(key);
		}

		@Override
		public QuickMap<K, V> unmodifiable() {
			return this;
		}

		@Override
		public void release() {
			return; // Can't release the wrapped map through an unmodifiable view
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theWrapped.equals(obj);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}
}

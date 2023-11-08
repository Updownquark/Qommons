package org.qommons.collect;

import java.util.Comparator;
import java.util.NavigableSet;

/**
 * A multi-map whose {@link #keySet()} is {@link NavigableSet sorted}
 * 
 * @param <K> The key-type of the map
 * @param <V> The value-type of the map
 */
public interface SortedMultiMap<K, V> extends MultiMap<K, V> {
	@Override
	NavigableSet<K> keySet();

	/** @return The comparator used to sort this map's keys */
	default Comparator<? super K> comparator() {
		return keySet().comparator();
	}

	@Override
	NavigableSet<? extends MultiEntry<K, V>> entrySet();

	/**
	 * @param key The key to search for
	 * @return The entry in this map with the highest key lower than <code>key</code>
	 */
	MultiEntry<K, V> lowerEntry(K key);
	/**
	 * @param key The key to search for
	 * @return The entry in this map with the highest key lower than or equivalent to <code>key</code>
	 */
	MultiEntry<K, V> floorEntry(K key);
	/**
	 * @param key The key to search for
	 * @return The entry in this map with the lowest key higher than or equivalent to <code>key</code>
	 */
	MultiEntry<K, V> ceilingEntry(K key);
	/**
	 * @param key The key to search for
	 * @return The entry in this map with the lowest key higher than <code>key</code>
	 */
	MultiEntry<K, V> higherEntry(K key);
	/** @return The entry in this map with the lowest key */
	MultiEntry<K, V> firstEntry();
	/** @return The entry in this map with the highest key */
	MultiEntry<K, V> lastEntry();

	/**
	 * @param low The minimum key to include
	 * @param lowIncluded Whether entries whose key is equivalent to the given minimum key should be included
	 * @param high The maximum key to include
	 * @param highIncluded Whether entries whose key is equivalent to the given maximum key should be included
	 * @return A {@link SortedMultiMap} backed by this map whose key set is equivalent to
	 *         {@link NavigableSet#subSet(Object, boolean, Object, boolean) this.keySet().subSet(low, lowIncluded, high, highIncluded)}
	 */
	SortedMultiMap<K, V> subMap(K low, boolean lowIncluded, K high, boolean highIncluded);
	/**
	 * Equivalent to {@link #subMap(Object, boolean, Object, boolean) subMap(low, true, high, false)}
	 * 
	 * @param low The minimum key to include
	 * @param high The maximum key to include
	 * @return A {@link SortedMultiMap} backed by this map whose key set is equivalent to {@link NavigableSet#subSet(Object, Object)
	 *         this.keySet().subSet(low, high)}
	 */
	default SortedMultiMap<K, V> subMap(K low, K high) {
		return subMap(low, true, high, false);
	}
	/**
	 * @param high The maximum key to include
	 * @param highIncluded Whether entries whose key is equivalent to the given maximum key should be included
	 * @return A {@link SortedMultiMap} backed by this map whose key set is equivalent to {@link NavigableSet#headSet(Object, boolean)
	 *         this.keySet().headSet(high, highIncluded)}
	 */
	SortedMultiMap<K, V> headMap(K high, boolean highIncluded);
	/**
	 * Equivalent to {@link #headMap(Object, boolean) headMap(high, false)}
	 * 
	 * @param high The maximum key to include
	 * @return A {@link SortedMultiMap} backed by this map whose key set is equivalent to {@link NavigableSet#headSet(Object)
	 *         this.keySet().headSet(high)}
	 */
	default SortedMultiMap<K, V> headMap(K high) {
		return headMap(high, false);
	}
	/**
	 * @param low The minimum key to include
	 * @param lowIncluded Whether entries whose key is equivalent to the given minimum key should be included
	 * @return A {@link SortedMultiMap} backed by this map whose key set is equivalent to {@link NavigableSet#tailSet(Object, boolean)
	 *         this.keySet().tailSet(low, lowIncluded)}
	 */
	SortedMultiMap<K, V> tailMap(K low, boolean lowIncluded);
	/**
	 * Equivalent to {@link #tailMap(Object, boolean) tailMap(low, true)}
	 * 
	 * @param low The minimum key to include
	 * @return A {@link SortedMultiMap} backed by this map whose key set is equivalent to {@link NavigableSet#tailSet(Object)
	 *         this.keySet().tailSet(low)}
	 */
	default SortedMultiMap<K, V> tailMap(K low) {
		return tailMap(low, true);
	}

	/** @return This map, with its {@link #keySet()} and {@link MultiMap.MultiEntry#getValues() values} reversed */
	SortedMultiMap<K, V> reverse();
}

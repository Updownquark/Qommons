package org.qommons.collect;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * A map that can store more than one value per key. The behavior of the collections associated with the keys are implementation-specific.
 * For example, some implementations may accept duplicate key-value pairs, while others may enforce uniqueness.
 *
 * @param <K> The key type for the map
 * @param <V> The value type for the map
 */
public interface MultiMap<K, V> {
	/**
	 * A key-value collection entry in a multi-map
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	interface MultiEntry<K, V> {
		/** @return The key associated with this entry's values */
		K getKey();

		/** @return All values associated with this entry's key */
		Collection<V> getValues();
	}

	/** @return The keys that have least one value in this map */
	Set<K> keySet();

	/** @return All key-value collection entries in this multi-map */
	Set<? extends MultiEntry<K, V>> entrySet();

	/** @return All key-value pairs in this multi-map */
	Collection<? extends Map.Entry<K, V>> singleEntries();

	/**
	 * @param key The key to get values for. Though this parameter's type is the key type of the map, this method will not throw exceptions
	 *        if the key is not actually an instance of that type. An empty collection will be returned.
	 * @return The collection of values stored for the given key in this map. Never null.
	 */
	Collection<V> get(K key);

	/** @return The number of values in this multi-map */
	int valueSize();

	/** @return A collection of all values associated with any key in this map */
	Collection<V> values();

	/**
	 * Adds a value for the given key
	 *
	 * @param key The key to add the value for
	 * @param value The value to add for the key
	 * @return Whether this map was changed as a result of the call
	 */
	boolean add(K key, V value);

	/**
	 * Adds a number of values for a key
	 *
	 * @param key The key to add the values for
	 * @param values The values to add for the key
	 * @return Whether this map was changed as a result of the call
	 */
	boolean addAll(K key, Collection<? extends V> values);

	/**
	 * @param values The map whose key-value pairs to add into this map
	 * @return Whether any entries were added to this map
	 */
	boolean putAll(Map<? extends K, ? extends V> values);

	/**
	 * @param values The multi-map whose key-value pairs to add into this map
	 * @return Whether any entries were added to this map
	 */
	boolean putAll(MultiMap<? extends K, ? extends V> values);

	/**
	 * Removes a value associated with a key
	 *
	 * @param key The key to remove the value for
	 * @param value The value to remove
	 * @return Whether this map was changed as a result of the call
	 */
	boolean remove(K key, Object value);

	/**
	 * Removes all values stored by a given key
	 *
	 * @param key The key to remove all the values of
	 * @return Whether this map was changed as a result of the call
	 */
	boolean removeAll(K key);

	/**
	 * Removes all keys and values in this multi-map
	 * 
	 * @return Whether any keys or values were removed
	 */
	boolean clear();
}

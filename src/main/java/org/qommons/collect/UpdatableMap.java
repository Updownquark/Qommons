package org.qommons.collect;

import java.util.Map;

/**
 * A {@link Map} with an additional {@link #update(Object)} method.In addition to other potential implementation-specific functionality, the
 * update method of an updatable map can be used to notify the map that a key has changed in a way that may have affected its storage
 * mechanism in the map. For example, its hash code may have changed, or one of the fields by which it is sorted.
 * </p>
 * 
 * <p>
 * An UpdatableMap must be able to find and re-store entries after their keys' storage attributes have changed, e.g. by using an
 * {@link java.util.IdentityHashMap}
 * </p>
 * 
 * @param <K> The key-type of the map
 * @param <V> The value-type of the map
 * 
 * @see UpdatableSet
 */
public interface UpdatableMap<K, V> extends Map<K, V> {
	/**
	 * @param key The key (same identical object as the one in the collection) that may have changed
	 * @return Whether the key was found in the collection
	 */
	boolean update(K key);

	@Override
	UpdatableSet<K> keySet();
}

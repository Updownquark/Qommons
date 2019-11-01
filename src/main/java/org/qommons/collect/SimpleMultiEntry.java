package org.qommons.collect;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

/**
 * A simple multi-entry
 * 
 * @param <K> The key-type of the entry
 * @param <V> The value-type of the entry
 */
public class SimpleMultiEntry<K, V> implements MultiMap.MultiEntry<K, V> {
	private final K theKey;
	private Collection<V> theValues;

	/**
	 * Creates an immutable entry
	 * 
	 * @param key The key for the entry
	 * @param mutableValues Whether {@link #getValues()} should be mutable
	 */
	public SimpleMultiEntry(K key, boolean mutableValues) {
		this(key, mutableValues ? new LinkedList<>() : Collections.emptyList());
	}

	/**
	 * Creates an entry
	 * 
	 * @param key The key for the entry
	 * @param values The values for the entry
	 */
	public SimpleMultiEntry(K key, Collection<V> values) {
		theKey = key;
		theValues = values;
	}

	@Override
	public K getKey() {
		return theKey;
	}

	@Override
	public Collection<V> getValues() {
		return theValues;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(theKey);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Map.Entry && Objects.equals(((Map.Entry<?, ?>) o).getKey(), theKey);
	}

	@Override
	public String toString() {
		return new StringBuilder().append(theKey).append('=').append(theValues).toString();
	}
}

package org.qommons;

import java.util.Map;

/**
 * A simple entry with either a settable or an immutable value
 * 
 * @param <K> The key-type of the entry
 * @param <V> The value-type of the entry
 */
public class SimpleMapEntry<K, V> implements Map.Entry<K, V> {
	private final K theKey;
	private V theValue;

	private boolean isValueMutable;

	/**
	 * Creates an immutable entry
	 * 
	 * @param key The key for the entry
	 * @param value The value for the entry
	 */
	public SimpleMapEntry(K key, V value) {
		this(key, value, false);
	}

	/**
	 * Creates an entry
	 * 
	 * @param key The key for the entry
	 * @param value The value for the entry
	 * @param mutableValue Whether to allow {@link #setValue(Object)} to change this entry's value
	 */
	public SimpleMapEntry(K key, V value, boolean mutableValue) {
		theKey = key;
		theValue = value;
		isValueMutable = mutableValue;
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
		if(isValueMutable) {
			V ret = theValue;
			theValue = value;
			return ret;
		} else {
			throw new UnsupportedOperationException();
		}
	}
}

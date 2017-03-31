package org.qommons;

/**
 * Provides hash codes
 * 
 * @param <T> The type of values that this hasher can hash
 */
public interface Hasher<T> {
	/**
	 * @param <V> The sub-type of value to hash
	 * @param value The value to hash
	 * @return The hash code for the value
	 */
	<V extends T> int hash(V value);
}
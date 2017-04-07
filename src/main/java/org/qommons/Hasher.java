package org.qommons;

import java.util.Objects;

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

	/**
	 * @param <T> The type of hasher to create
	 * @return A hasher that uses Object#hashCode (allowing for nulls (0))
	 */
	public static <T> Hasher<T> object() {
		return Objects::hashCode;
	};

	/**
	 * @param <T> The type of hasher to create
	 * @return A hasher that uses {@link System#identityHashCode(Object)})
	 */
	public static <T> Hasher<T> id() {
		return System::identityHashCode;
	};
}
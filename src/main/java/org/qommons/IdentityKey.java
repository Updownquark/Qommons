package org.qommons;

/**
 * A wrapper that allows values to be added to sets or maps by identity instead of by value
 * 
 * @param <T> The type of the value to wrap
 */
public class IdentityKey<T> {
	/** The wrapped value */
	public final T value;
	private final boolean useIdHash;

	/** @param val The value to wrap */
	public IdentityKey(T val) {
		this(val, true);
	}

	public IdentityKey(T val, boolean useIdHash) {
		value = val;
		this.useIdHash = useIdHash;
	}

	@Override
	public int hashCode() {
		if (useIdHash)
			return System.identityHashCode(value);
		else
			return value == null ? 0 : value.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof IdentityKey && ((IdentityKey<?>) obj).value == value;
	}

	@Override
	public String toString() {
		return value.toString();
	}
}

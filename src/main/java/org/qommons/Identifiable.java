package org.qommons;

/**
 * <p>
 * An object provides visibility into its identity. Objects with the same identity are guaranteed to implement the same interface(s) and
 * provide the same value(s) from it/them.
 * </p>
 * 
 * <p>
 * The identity of an object should also implement {@link Object#toString()} to provide a human-readable representation of where its value
 * comes from.
 * </p>
 */
public interface Identifiable {
	/** @return A representation of this object's identity */
	Object getIdentity();
}

package org.qommons;

import java.util.Objects;

/** A simple binary predicate testing the equivalence of two objects in some context */
public interface Equalizer {
	/**
	 * @param o1 The first object to test
	 * @param o2 The second object to test
	 * @return Whether the two objects are equivalent in this context
	 */
	boolean equals(Object o1, Object o2);

	/**
	 * @param <V> The type of the value
	 * @param value The value to make a node for
	 * @param hashCode The hash code for the value
	 * @return An equalizer node for the given value using this equalizer
	 */
	public default <V> EqualizerNode<V> nodeFor(V value, int hashCode) {
		return new EqualizerNode<>(this, value, hashCode);
	}

	/** An equalizer that uses {@link Object#equals(Object)} (allowing for nulls) */
	public static final Equalizer object = Objects::equals;

	/** An equalizer that uses == */
	public static final Equalizer id = (o1, o2) -> o1 == o2;

	/**
	 * A node that encapsulates a value and uses an {@link Equalizer} for its {@link #equals(Object)} method
	 * 
	 * @param <V> The type of value stored in the node
	 */
	public static class EqualizerNode<V> {
		private final Equalizer theEqualizer;
		private final V theValue;
		private final int theHashCode;

		/**
		 * @param equalizer The equalizer for equals testing
		 * @param value The value to test
		 * @param hashCode The hash code for the value
		 */
		public EqualizerNode(Equalizer equalizer, V value, int hashCode) {
			theEqualizer = equalizer;
			theValue = value;
			theHashCode = hashCode;
		}

		/**
		 * @deprecated This constructor uses {@link Objects#hashCode(Object)} to generate a hash code for the object. This may be
		 *             inconsistent with the {@link Equalizer}'s {@link Equalizer#equals(Object, Object) equals} method, resulting in this
		 *             node's {@link #hashCode()} contract being broken. Use {@link EqualizerNode#EqualizerNode(Equalizer, Object, int)}
		 *             instead.
		 * @param equalizer The equalizer for equals testing
		 * @param value The value to test
		 */
		@Deprecated
		public EqualizerNode(Equalizer equalizer, V value) {
			this(equalizer, value, Objects.hashCode(value));
		}

		/** @return The value in this node */
		public V get() {
			return theValue;
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof EqualizerNode)
				return theEqualizer.equals(theValue, ((EqualizerNode<?>) o).get());
			else
				return theEqualizer.equals(theValue, o);
		}

		@Override
		public int hashCode() {
			return theHashCode;
		}
	}
}

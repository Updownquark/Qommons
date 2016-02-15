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
	 * A node that encapsulates a value and uses an {@link Equalizer} for its {@link #equals(Object)} method
	 * 
	 * @param <V> The type of value stored in the node
	 */
	public static class EqualizerNode<V> {
		private final Equalizer theEqualizer;
		private final V theValue;

		/**
		 * @param equalizer The equalizer for equals testing
		 * @param value The value to test
		 */
		public EqualizerNode(Equalizer equalizer, V value) {
			theEqualizer = equalizer;
			theValue = value;
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
			return Objects.hashCode(theValue);
		}
	}
}

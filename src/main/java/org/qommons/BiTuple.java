package org.qommons;

import java.util.Objects;

/**
 * A tuple with two typed values
 *
 * @param <V1> The type of the first value
 * @param <V2> The type of the second value
 */
public class BiTuple<V1, V2> {
	private final V1 theValue1;
	private final V2 theValue2;

	private int hashCode;

	/**
	 * @param v1 The first value
	 * @param v2 The second value
	 */
	public BiTuple(V1 v1, V2 v2) {
		theValue1 = v1;
		theValue2 = v2;

		hashCode = -1;
	}

	/** @return The first Value */
	public V1 getValue1() {
		return theValue1;
	}

	/** @return The second value */
	public V2 getValue2() {
		return theValue2;
	}

	/** @return Whether this tuple has at least one non-null value */
	public boolean hasValue() {
		return theValue1 != null || theValue2 != null;
	}

	/** @return Whether this tuple has both non-null values */
	public boolean has2Values() {
		return theValue1 != null && theValue2 != null;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof BiTuple))
			return false;
		BiTuple<?, ?> tuple = (BiTuple<?, ?>) o;
		// Don't compile the hashCode, but if we already know it, we can use it
		if (hashCode != -1 && tuple.hashCode != -1 && hashCode != tuple.hashCode)
			return false;
		return Objects.equals(theValue1, tuple.theValue1) && Objects.equals(theValue2, tuple.theValue2);
	}

	@Override
	public int hashCode() {
		if (hashCode == -1) {
			hashCode = Objects.hashCode(theValue1) + Objects.hashCode(theValue2);
		}
		return hashCode;
	}

	@Override
	public String toString() {
		return new StringBuilder("[").append(theValue1).append(", ").append(theValue2).append(']').toString();
	}
}

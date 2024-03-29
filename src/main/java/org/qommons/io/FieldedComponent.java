package org.qommons.io;

/**
 * A component in a {@link FieldedAdjustable}
 * 
 * @param <F> The field type enum
 * @param <V> The value type
 */
public abstract class FieldedComponent<F extends Enum<F>, V extends Comparable<V>> implements AdjustableComponent {
	private final int theStart;
	private final int theEnd;
	private final F theField;
	private final V theValue;

	/**
	 * @param start The start position of this component
	 * @param end The end position of this component (exclusive)
	 * @param field The field type of this component
	 * @param value The value of this component
	 */
	public FieldedComponent(int start, int end, F field, V value) {
		theStart = start;
		theEnd = end;
		theField = field;
		theValue = value;
	}

	@Override
	public int getStart() {
		return theStart;
	}

	@Override
	public int getEnd() {
		return theEnd;
	}

	/** @return This component's field type */
	public F getField() {
		return theField;
	}

	/** @return This component's value */
	public V getValue() {
		return theValue;
	}

	@Override
	public abstract String toString();
}

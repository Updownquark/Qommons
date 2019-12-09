package org.qommons.io;

public abstract class FieldedComponent<F extends Enum<F>> implements AdjustableComponent {
	private final int theStart;
	private final int theEnd;
	private final F theField;
	private final int theValue;

	public FieldedComponent(int start, int end, F field, int value) {
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

	public F getField() {
		return theField;
	}

	public int getValue() {
		return theValue;
	}

	@Override
	public abstract String toString();
}

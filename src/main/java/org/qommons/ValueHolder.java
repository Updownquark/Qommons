package org.qommons;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ValueHolder<T> implements Consumer<T>, Supplier<T> {
	private boolean isSet;
	private T theValue;

	public ValueHolder() {}

	public ValueHolder(T value) {
		theValue = value;
	}

	public boolean isPresent() {
		return isSet;
	}

	@Override
	public T get() {
		return theValue;
	}

	@Override
	public void accept(T t) {
		isSet = true;
		theValue = t;
	}

	public void clear() {
		isSet = false;
		theValue = null;
	}

	@Override
	public String toString() {
		if (!isSet)
			return "(empty)";
		else
			return String.valueOf(theValue);
	}
}

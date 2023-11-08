package org.qommons;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Simple, mutable container for holding a typed value
 * 
 * @param <T> The type of the value
 */
public class ValueHolder<T> implements Consumer<T>, Supplier<T> {
	private boolean isSet;
	private T theValue;

	/** Creates an empty holder */
	public ValueHolder() {}

	/** @param value The initial value for the holder */
	public ValueHolder(T value) {
		theValue = value;
		isSet = true;
	}

	/**
	 * @return Whether an {@link ValueHolder#ValueHolder(Object) initial value} was set or {@link #accept(Object) accept} has been called on
	 *         this holder
	 */
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

	/**
	 * Clears this value, returning previously set value, if any
	 * 
	 * @return The previously set value
	 */
	public T clear() {
		T value = theValue;
		isSet = false;
		theValue = null;
		return value;
	}

	@Override
	public String toString() {
		if (!isSet)
			return "(empty)";
		else
			return String.valueOf(theValue);
	}
}

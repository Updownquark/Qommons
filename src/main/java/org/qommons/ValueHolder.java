package org.qommons;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ValueHolder<T> implements Consumer<T>, Supplier<T> {
	private T theValue;

	public ValueHolder() {}

	public ValueHolder(T value) {
		theValue = value;
	}

	@Override
	public T get() {
		return theValue;
	}

	@Override
	public void accept(T t) {
		theValue = t;
	}
}

package org.qommons.threading;

import java.util.function.Supplier;

/**
 * A simple class supporting the lazy creation of a singleton value using safe double-checked-locking for thread safety and performance
 * 
 * @param <T> The type of value to supply
 */
public final class Singleton<T> implements Supplier<T> {
	private Supplier<? extends T> theCreator;
	private volatile T theValue;

	private Singleton(Supplier<? extends T> creator) {
		theCreator = creator;
	}

	@Override
	public T get() {
		T value = theValue;
		if (value == null) {
			synchronized (this) {
				value = theValue;
				if (value == null) {
					value = theCreator.get();
					theCreator = null;
					theValue = value;
				}
			}
		}
		return value;
	}

	@Override
	public String toString() {
		T value = theValue;
		Supplier<? extends T> creator = theCreator;
		if (value != null)
			return value.toString();
		else if (creator != null)
			return creator.toString();
		else
			return "null";
	}

	/**
	 * @param <T> The type of the value to supply
	 * @param creator The creator for the first time the value is requested
	 * @return The singleton
	 */
	public static <T> Singleton<T> of(Supplier<? extends T> creator) {
		return new Singleton<>(creator);
	}
}

package org.qommons.threading;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A simple class supporting the lazy creation of a singleton value using safe double-checked-locking for thread safety and performance
 * 
 * @param <T> The type of value to supply
 */
public final class Singleton<T> implements Supplier<T> {
	private volatile Supplier<? extends T> theCreator;
	private volatile T theValue;

	private Singleton(Supplier<? extends T> creator) {
		theCreator = creator;
	}

	@Override
	public T get() {
		T value = theValue;
		if (value == null && theCreator != null) {
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

	/**
	 * Performs an operation on this singleton's value if it has been instantiated via a call to {@link #get()}. This does nothing if the
	 * value has not been instantiated.
	 * 
	 * @param operation The operation to perform on the value
	 * @return Whether the value was instantiated, and therefore whether the operation was performed on it
	 */
	public boolean doIfPresent(Consumer<? super T> operation) {
		T value = theValue;
		boolean created;
		if (value == null && theCreator != null) {
			synchronized (this) {
				value = theValue;
				created = theCreator == null;
			}
		} else
			created = true;
		if (created)
			operation.accept(value);
		return created;
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

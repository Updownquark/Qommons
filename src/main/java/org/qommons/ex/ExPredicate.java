package org.qommons.ex;

import java.util.function.Predicate;

/**
 * A {@link Predicate} look-alike that can throw a checked exception
 * 
 * @param <T> The type to test
 * @param <E> The exception type
 */
public interface ExPredicate<T, E extends Throwable> {
	/**
	 * @param value The value to test
	 * @return Whether the value passes this test
	 * @throws E An exception
	 */
	boolean test(T value) throws E;

	/** @return A {@link Predicate} that calls this predicate, wrapping any checked exceptions with {@link CheckedExceptionWrapper} */
	default Predicate<T> unsafe() {
		return value -> {
			try {
				return ExPredicate.this.test(value);
			} catch (RuntimeException | Error e) {
				throw e;
			} catch (Throwable e) {
				throw new CheckedExceptionWrapper(e);
			}
		};
	}

	/**
	 * @param <T> The type to test
	 * @param <E> The exception type
	 * @param p The predicate to wrap
	 * @return An {@link ExPredicate} that calls the given predicate and never throws any checked exceptions
	 */
	static <T, E extends Throwable> ExPredicate<T, E> wrap(Predicate<T> p) {
		if (p == null)
			return null;
		return value -> p.test(value);
	}
}

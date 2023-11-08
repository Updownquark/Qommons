package org.qommons.ex;

import java.util.function.Supplier;

/**
 * A {@link Supplier} look-alike that can throw a checked exception
 * 
 * @param <T> The type to supply
 * @param <E> The exception type
 */
public interface ExSupplier<T, E extends Throwable> {
	/**
	 * @return The supplied value
	 * @throws E An exception
	 */
	T get() throws E;

	/** @return A {@link Supplier} that calls this supplier, wrapping any checked exceptions with {@link CheckedExceptionWrapper} */
	default Supplier<T> unsafe() {
		return () -> {
			try {
				return ExSupplier.this.get();
			} catch (RuntimeException | Error e) {
				throw e;
			} catch (Throwable e) {
				throw new CheckedExceptionWrapper(e);
			}
		};
	}

	/**
	 * @param <T> The type to supply
	 * @param <E> The exception type
	 * @param s The supplier to wrap
	 * @return An {@link ExSupplier} that calls the given supplier and never throws any checked exceptions
	 */
	static <T, E extends Throwable> ExSupplier<T, E> of(Supplier<T> s) {
		if (s == null)
			return null;
		return () -> s.get();
	}
}

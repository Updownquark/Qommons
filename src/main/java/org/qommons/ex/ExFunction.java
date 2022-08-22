package org.qommons.ex;

import java.util.function.Function;

import org.qommons.LambdaUtils;

/**
 * A {@link Function} look-alike that can throw a checked exception
 * 
 * @param <F> The argument type
 * @param <T> The return type
 * @param <E> The throwable type
 */
@FunctionalInterface
public interface ExFunction<F, T, E extends Throwable> {
	/** The identity function */
	public static final ExFunction<Object, Object, RuntimeException> IDENTITY = v -> v;

	/**
	 * @param value The argument
	 * @return The return value
	 * @throws E An exception
	 */
	T apply(F value) throws E;

	/** @return A {@link Function} that calls this function, wrapping any thrown checked exception with a {@link CheckedExceptionWrapper} */
	default Function<F, T> unsafe() {
		return value -> {
			try {
				return ExFunction.this.apply(value);
			} catch (RuntimeException | Error e) {
				throw e;
			} catch(Throwable e) {
				throw new CheckedExceptionWrapper(e);
			}
		};
	}

	/**
	 * @param <F> The argument type
	 * @param <T> The return type
	 * @param <E> The throwable type
	 * @param f The function to wrap
	 * @return An {@link ExFunction} that calls the given function and does not actually throw any checked exceptions
	 */
	static <F, T, E extends Throwable> ExFunction<F, T, E> of(Function<F, T> f) {
		if (f == null)
			return null;
		if (LambdaUtils.getIdentifier(f) == LambdaUtils.IDENTITY)
			return (ExFunction<F, T, E>) IDENTITY;
		return value -> f.apply(value);
	}

	/**
	 * @param <F> The type of the value to accept and return
	 * @return The identity function
	 */
	static <F> ExFunction<F, F, RuntimeException> identity() {
		return (ExFunction<F, F, RuntimeException>) IDENTITY;
	}
}

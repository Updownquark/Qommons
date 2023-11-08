package org.qommons.ex;

import java.util.function.BiFunction;

/**
 * A {@link BiFunction} look-alike that is capable of throwing a checked exception
 * 
 * @param <T> The first argument type
 * @param <U> The second argument type
 * @param <R> The return type
 * @param <E> The throwable type
 */
@FunctionalInterface
public interface ExBiFunction<T, U, R, E extends Throwable> {
	/**
	 * @param t The first argument
	 * @param u The second argument
	 * @return The return value
	 * @throws E An exception
	 */
	R apply(T t, U u) throws E;

	/**
	 * @return A {@link BiFunction} that calls this bi function, wrapping any thrown checked exception with a
	 *         {@link CheckedExceptionWrapper}
	 */
	default BiFunction<T, U, R> unsafe() {
		return (arg1, arg2) -> {
			try {
				return ExBiFunction.this.apply(arg1, arg2);
			} catch (RuntimeException | Error e) {
				throw e;
			} catch (Throwable e) {
				throw new CheckedExceptionWrapper(e);
			}
		};
	}

	/**
	 * @param <T> The first argument type
	 * @param <U> The second argument type
	 * @param <R> The return type
	 * @param <E> The throwable type
	 * @param f The function to wrap
	 * @return an {@link ExBiFunction} that calls the given function and never actually throws a checked exception
	 */
	static <T, U, R, E extends Throwable> ExBiFunction<T, U, R, E> of(BiFunction<T, U, R> f) {
		return (arg1, arg2) -> f.apply(arg1, arg2);
	}
}

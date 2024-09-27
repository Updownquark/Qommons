package org.qommons.ex;

import org.qommons.TriFunction;

/**
 * A {@link TriFunction} look-alike that is capable of throwing a checked exception
 * 
 * @param <T> The first argument type
 * @param <U> The second argument type
 * @param <V> The third argument type
 * @param <R> The return type
 * @param <E> The throwable type
 */
@FunctionalInterface
public interface ExTriFunction<T, U, V, R, E extends Throwable> {
	/**
	 * @param t The first argument
	 * @param u The second argument
	 * @param v The third argument
	 * @return The return value
	 * @throws E An exception
	 */
	R apply(T t, U u, V v) throws E;

	/**
	 * @return A {@link TriFunction} that calls this tri function, wrapping any thrown checked exception with a
	 *         {@link CheckedExceptionWrapper}
	 */
	default TriFunction<T, U, V, R> unsafe() {
		return (arg1, arg2, arg3) -> {
			try {
				return ExTriFunction.this.apply(arg1, arg2, arg3);
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
	 * @param <V> The third argument type
	 * @param <R> The return type
	 * @param <E> The throwable type
	 * @param f The function to wrap
	 * @return an {@link ExTriFunction} that calls the given function and never actually throws a checked exception
	 */
	static <T, U, V, R, E extends Throwable> ExTriFunction<T, U, V, R, E> of(TriFunction<T, U, V, R> f) {
		return (arg1, arg2, arg3) -> f.apply(arg1, arg2, arg3);
	}
}

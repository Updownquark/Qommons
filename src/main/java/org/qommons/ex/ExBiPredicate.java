package org.qommons.ex;

import java.util.function.BiPredicate;

/**
 * A {@link BiPredicate} look-alike that is capable of throwing a checked exception
 * 
 * @param <T> The first argument type
 * @param <U> The second argument type
 * @param <E> The throwable type
 */
@FunctionalInterface
public interface ExBiPredicate<T, U, E extends Throwable> {
	/**
	 * @param t The first argument
	 * @param u The second argument
	 * @return Whether the test passes for the given values
	 * @throws E An exception
	 */
	boolean test(T t, U u) throws E;

	/**
	 * @return A {@link BiPredicate} that calls this bi predicate, wrapping any thrown checked exception with a
	 *         {@link CheckedExceptionWrapper}
	 */
	default BiPredicate<T, U> unsafe() {
		return (arg1, arg2) -> {
			try {
				return ExBiPredicate.this.test(arg1, arg2);
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
	 * @param <E> The throwable type
	 * @param f The function to wrap
	 * @return an {@link ExBiPredicate} that calls the given function and never actually throws a checked exception
	 */
	static <T, U, E extends Throwable> ExBiPredicate<T, U, E> of(BiPredicate<T, U> f) {
		return (arg1, arg2) -> f.test(arg1, arg2);
	}
}

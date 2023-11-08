package org.qommons.ex;

import java.util.function.BiConsumer;

/**
 * A {@link BiConsumer} look-alike that is capable of throwing a checked exception
 * 
 * @param <T> The first argument type
 * @param <U> The second argument type
 * @param <E> The throwable type
 */
@FunctionalInterface
public interface ExBiConsumer<T, U, E extends Throwable> {
	/**
	 * @param t The first argument
	 * @param u The second argument
	 * @throws E An exception
	 */
	void accept(T t, U u) throws E;

	/**
	 * @return A {@link BiConsumer} that calls this bi consumer, wrapping any thrown checked exception with a
	 *         {@link CheckedExceptionWrapper}
	 */
	default BiConsumer<T, U> unsafe() {
		return (arg1, arg2) -> {
			try {
				ExBiConsumer.this.accept(arg1, arg2);
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
	 * @return an {@link ExBiConsumer} that calls the given function and never actually throws a checked exception
	 */
	static <T, U, E extends Throwable> ExBiConsumer<T, U, E> of(BiConsumer<T, U> f) {
		return (arg1, arg2) -> f.accept(arg1, arg2);
	}
}

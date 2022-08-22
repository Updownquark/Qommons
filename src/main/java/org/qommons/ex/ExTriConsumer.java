package org.qommons.ex;

import java.util.function.BiConsumer;

import org.qommons.TriConsumer;

/**
 * A {@link BiConsumer} look-alike that is capable of throwing a checked exception
 * 
 * @param <T> The first argument type
 * @param <U> The second argument type
 * @param <V> The third argument type
 * @param <E> The throwable type
 */
@FunctionalInterface
public interface ExTriConsumer<T, U, V, E extends Throwable> {
	/**
	 * @param t The first argument
	 * @param u The second argument
	 * @param v The third argument
	 * @throws E An exception
	 */
	void accept(T t, U u, V v) throws E;

	/**
	 * @return A {@link TriConsumer} that calls this tri consumer, wrapping any thrown checked exception with a
	 *         {@link CheckedExceptionWrapper}
	 */
	default TriConsumer<T, U, V> unsafe() {
		return (arg1, arg2, arg3) -> {
			try {
				ExTriConsumer.this.accept(arg1, arg2, arg3);
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
	 * @param <E> The throwable type
	 * @param f The function to wrap
	 * @return an {@link ExTriConsumer} that calls the given function and never actually throws a checked exception
	 */
	static <T, U, V, E extends Throwable> ExTriConsumer<T, U, V, E> of(TriConsumer<T, U, V> f) {
		return (arg1, arg2, arg3) -> f.accept(arg1, arg2, arg3);
	}
}

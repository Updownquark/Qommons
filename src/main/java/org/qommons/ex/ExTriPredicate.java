package org.qommons.ex;

import java.util.function.BiPredicate;

import org.qommons.fn.FunctionUtils;
import org.qommons.fn.TriPredicate;

/**
 * A {@link BiPredicate} look-alike that is capable of throwing a checked exception
 * 
 * @param <T> The first argument type
 * @param <U> The second argument type
 * @param <V> The third argument type
 * @param <X> The throwable type
 */
@FunctionalInterface
public interface ExTriPredicate<T, U, V, X extends Throwable> {
	/**
	 * @param t The first argument
	 * @param u The second argument
	 * @param v The third argument
	 * @return Whether the test passes for the given values
	 * @throws X An exception
	 */
	boolean test(T t, U u, V v) throws X;

	/**
	 * @return A {@link BiPredicate} that calls this tri predicate, wrapping any thrown checked exception with a
	 *         {@link CheckedExceptionWrapper}
	 */
	default TriPredicate<T, U, V> unsafe() {
		return (arg1, arg2, arg3) -> {
			try {
				return ExTriPredicate.this.test(arg1, arg2, arg3);
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
	 * @param <X> The throwable type
	 * @param f The function to wrap
	 * @return an {@link ExTriPredicate} that calls the given function and never actually throws a checked exception
	 */
	static <T, U, V, X extends Throwable> ExTriPredicate<T, U, V, X> of(TriPredicate<T, U, V> f) {
		if (f == null)
			return null;
		return FunctionUtils.printableExTriPred(f::test, f::toString, f);
	}
}

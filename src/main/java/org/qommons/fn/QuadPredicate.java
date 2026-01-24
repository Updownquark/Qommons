package org.qommons.fn;

import java.util.function.Supplier;

/**
 * A predicate that operates on 4 arguments
 * 
 * @param <A1> The first argument type
 * @param <A2> The second argument type
 * @param <A3> The third argument type
 * @param <A4> The fourth argument type
 */
@FunctionalInterface
public interface QuadPredicate<A1, A2, A3, A4> {
	/**
	 * @param arg1 The first argument
	 * @param arg2 The second argument
	 * @param arg3 The third argument
	 * @param arg4 The fourth argument
	 * @return The result of the test
	 */
	boolean test(A1 arg1, A2 arg2, A3 arg3, A4 arg4);

	/**
	 * @param <A1> The type of the first argument to the quaternary predicate
	 * @param <A2> The type of the second argument to the quaternary predicate
	 * @param <A3> The type of the third argument to the quaternary predicate
	 * @param <A4> The type of the fourth argument to the quaternary predicate
	 * @param function The implementation
	 * @param print The string for the wrapped predicate's {@link #toString()}
	 * @param identity The identity for the predicate's {@link #hashCode()} and {@link #equals(Object)} methods
	 * @return The wrapped, printable quaternary predicate
	 */
	public static <A1, A2, A3, A4> QuadPredicate<A1, A2, A3, A4> printable(
		QuadPredicate<? super A1, ? super A2, ? super A3, ? super A4> function, String print, Object identity) {
		return printable(function, () -> print, identity);
	}

	/**
	 * @param <A1> The type of the first argument to the quaternary predicate
	 * @param <A2> The type of the second argument to the quaternary predicate
	 * @param <A3> The type of the third argument to the quaternary predicate
	 * @param <A4> The type of the fourth argument to the quaternary predicate
	 * @param function The implementation
	 * @param print Supplies the string for the wrapped predicate's {@link #toString()}
	 * @param identity The identity for the predicate's {@link #hashCode()} and {@link #equals(Object)} methods
	 * @return The wrapped, printable quaternary predicate
	 */
	public static <A1, A2, A3, A4> QuadPredicate<A1, A2, A3, A4> printable(
		QuadPredicate<? super A1, ? super A2, ? super A3, ? super A4> function, Supplier<String> print, Object identity) {
		return new PrintableQuadPredicate<>(function, print, identity);
	}

	/**
	 * Implements {@link QuadPredicate#printable(QuadPredicate, String, Object)}
	 * 
	 * @param <A1> The first argument type
	 * @param <A2> The second argument type
	 * @param <A3> The third argument type
	 * @param <A4> The fourth argument type
	 */
	class PrintableQuadPredicate<A1, A2, A3, A4>
		extends FunctionUtils.PrintableLambda<QuadPredicate<? super A1, ? super A2, ? super A3, ? super A4>>
		implements QuadPredicate<A1, A2, A3, A4> {
		public PrintableQuadPredicate(QuadPredicate<? super A1, ? super A2, ? super A3, ? super A4> lambda, Supplier<String> print,
			Object identifier) {
			super(lambda, print, identifier);
		}

		@Override
		public boolean isTrivial() {
			return false;
		}

		@Override
		public boolean test(A1 arg1, A2 arg2, A3 arg3, A4 arg4) {
			return getLambda().test(arg1, arg2, arg3, arg4);
		}
	}
}

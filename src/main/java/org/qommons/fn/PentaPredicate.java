package org.qommons.fn;

import java.util.function.Supplier;

/**
 * A predicate that operates on 5 arguments
 * 
 * @param <A1> The first argument type
 * @param <A2> The second argument type
 * @param <A3> The third argument type
 * @param <A4> The fourth argument type
 * @param <A5> The fifth argument type
 */
@FunctionalInterface
public interface PentaPredicate<A1, A2, A3, A4, A5> {
	/**
	 * @param arg1 The first argument
	 * @param arg2 The second argument
	 * @param arg3 The third argument
	 * @param arg4 The fourth argument
	 * @param arg5 The fifth argument
	 * @return The result of the test
	 */
	boolean test(A1 arg1, A2 arg2, A3 arg3, A4 arg4, A5 arg5);

	/**
	 * @param <A1> The type of the first argument to the 5-argument predicate
	 * @param <A2> The type of the second argument to the 5-argument predicate
	 * @param <A3> The type of the third argument to the 5-argument predicate
	 * @param <A4> The type of the fourth argument to the 5-argument predicate
	 * @param <A5> The type of the fifth argument to the 5-argument predicate
	 * @param function The implementation
	 * @param print The string for the wrapped predicate's {@link #toString()}
	 * @param identity The identity for the predicate's {@link #hashCode()} and {@link #equals(Object)} methods
	 * @return The wrapped, printable 5-argument predicate
	 */
	public static <A1, A2, A3, A4, A5> PentaPredicate<A1, A2, A3, A4, A5> printable(
		PentaPredicate<? super A1, ? super A2, ? super A3, ? super A4, ? super A5> function, String print, Object identity) {
		return printable(function, () -> print, identity);
	}

	/**
	 * @param <A1> The type of the first argument to the 5-argument predicate
	 * @param <A2> The type of the second argument to the 5-argument predicate
	 * @param <A3> The type of the third argument to the 5-argument predicate
	 * @param <A4> The type of the fourth argument to the 5-argument predicate
	 * @param <A5> The type of the fifth argument to the 5-argument predicate
	 * @param function The implementation
	 * @param print Supplies the string for the wrapped predicate's {@link #toString()}
	 * @param identity The identity for the predicate's {@link #hashCode()} and {@link #equals(Object)} methods
	 * @return The wrapped, printable 5-argument predicate
	 */
	public static <A1, A2, A3, A4, A5> PentaPredicate<A1, A2, A3, A4, A5> printable(
		PentaPredicate<? super A1, ? super A2, ? super A3, ? super A4, ? super A5> function, Supplier<String> print, Object identity) {
		return new PrintablePentaPredicate<>(function, print, identity);
	}

	/**
	 * Implements {@link PentaPredicate#printable(PentaPredicate, String, Object)}
	 * 
	 * @param <A1> The first argument type
	 * @param <A2> The second argument type
	 * @param <A3> The third argument type
	 * @param <A4> The fourth argument type
	 * @param <A5> The fifth argument type
	 */
	class PrintablePentaPredicate<A1, A2, A3, A4, A5>
		extends FunctionUtils.PrintableLambda<PentaPredicate<? super A1, ? super A2, ? super A3, ? super A4, ? super A5>>
		implements PentaPredicate<A1, A2, A3, A4, A5> {
		public PrintablePentaPredicate(PentaPredicate<? super A1, ? super A2, ? super A3, ? super A4, ? super A5> lambda,
			Supplier<String> print, Object identifier) {
			super(lambda, print, identifier);
		}

		@Override
		public boolean isTrivial() {
			return false;
		}

		@Override
		public boolean test(A1 arg1, A2 arg2, A3 arg3, A4 arg4, A5 arg5) {
			return getLambda().test(arg1, arg2, arg3, arg4, arg5);
		}
	}
}

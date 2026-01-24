package org.qommons.fn;

import java.util.function.Supplier;

/**
 * A predicate that operates on 9 arguments
 * 
 * @param <A1> The first argument type
 * @param <A2> The second argument type
 * @param <A3> The third argument type
 * @param <A4> The fourth argument type
 * @param <A5> The fifth argument type
 * @param <A6> The sixth argument type
 * @param <A7> The seventh argument type
 * @param <A8> The eighth argument type
 * @param <A9> The ninth argument type
 */
@FunctionalInterface
public interface NonaPredicate<A1, A2, A3, A4, A5, A6, A7, A8, A9> {
	/**
	 * @param arg1 The first argument
	 * @param arg2 The second argument
	 * @param arg3 The third argument
	 * @param arg4 The fourth argument
	 * @param arg5 The fifth argument
	 * @param arg6 The sixth argument
	 * @param arg7 The seventh argument
	 * @param arg8 The eighth argument
	 * @param arg9 The ninth argument
	 * @return The result of the test
	 */
	boolean test(A1 arg1, A2 arg2, A3 arg3, A4 arg4, A5 arg5, A6 arg6, A7 arg7, A8 arg8, A9 arg9);

	/**
	 * @param <A1> The type of the first argument to the predicate
	 * @param <A2> The type of the second argument to the predicate
	 * @param <A3> The type of the third argument to the predicate
	 * @param <A4> The type of the fourth argument to the predicate
	 * @param <A5> The type of the fifth argument to the predicate
	 * @param <A6> The type of the sixth argument to the predicate
	 * @param <A7> The type of the seventh argument to the predicate
	 * @param <A8> The type of the eighth argument to the predicate
	 * @param <A9> The type of the ninth argument to the predicate
	 * @param function The implementation
	 * @param print The string for the wrapped predicate's {@link #toString()}
	 * @param identity The identity for the predicate's {@link #hashCode()} and {@link #equals(Object)} methods
	 * @return The wrapped, printable predicate
	 */
	public static <A1, A2, A3, A4, A5, A6, A7, A8, A9> NonaPredicate<A1, A2, A3, A4, A5, A6, A7, A8, A9> printable(
		NonaPredicate<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? super A6, ? super A7, ? super A8, ? super A9> function,
		String print, Object identity) {
		return printable(function, () -> print, identity);
	}

	/**
	 * @param <A1> The type of the first argument to the predicate
	 * @param <A2> The type of the second argument to the predicate
	 * @param <A3> The type of the third argument to the predicate
	 * @param <A4> The type of the fourth argument to the predicate
	 * @param <A5> The type of the fifth argument to the predicate
	 * @param <A6> The type of the sixth argument to the predicate
	 * @param <A7> The type of the seventh argument to the predicate
	 * @param <A8> The type of the eighth argument to the predicate
	 * @param <A9> The type of the ninth argument to the predicate
	 * @param function The implementation
	 * @param print Supplies the string for the wrapped predicate's {@link #toString()}
	 * @param identity The identity for the predicate's {@link #hashCode()} and {@link #equals(Object)} methods
	 * @return The wrapped, printable predicate
	 */
	public static <A1, A2, A3, A4, A5, A6, A7, A8, A9> NonaPredicate<A1, A2, A3, A4, A5, A6, A7, A8, A9> printable(
		NonaPredicate<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? super A6, ? super A7, ? super A8, ? super A9> function,
		Supplier<String> print, Object identity) {
		return new PrintableNonaPredicate<>(function, print, identity);
	}

	/**
	 * Implements {@link NonaPredicate#printable(NonaPredicate, String, Object)}
	 * 
	 * @param <A1> The first argument type
	 * @param <A2> The second argument type
	 * @param <A3> The third argument type
	 * @param <A4> The fourth argument type
	 * @param <A5> The fifth argument type
	 * @param <A6> The sixth argument type
	 * @param <A7> The seventh argument type
	 * @param <A8> The eighth argument type
	 * @param <A9> The ninth argument type
	 */
	class PrintableNonaPredicate<A1, A2, A3, A4, A5, A6, A7, A8, A9> extends
		FunctionUtils.PrintableLambda<NonaPredicate<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? super A6, ? super A7, ? super A8, ? super A9>>
		implements NonaPredicate<A1, A2, A3, A4, A5, A6, A7, A8, A9> {
		public PrintableNonaPredicate(
			NonaPredicate<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? super A6, ? super A7, ? super A8, ? super A9> lambda,
			Supplier<String> print, Object identifier) {
			super(lambda, print, identifier);
		}

		@Override
		public boolean isTrivial() {
			return false;
		}

		@Override
		public boolean test(A1 arg1, A2 arg2, A3 arg3, A4 arg4, A5 arg5, A6 arg6, A7 arg7, A8 arg8, A9 arg9) {
			return getLambda().test(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
		}
	}
}

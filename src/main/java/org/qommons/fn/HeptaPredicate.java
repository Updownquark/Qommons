package org.qommons.fn;

import java.util.function.Supplier;

/**
 * A predicate that operates on 7 arguments
 * 
 * @param <A1> The first argument type
 * @param <A2> The second argument type
 * @param <A3> The third argument type
 * @param <A4> The fourth argument type
 * @param <A5> The fifth argument type
 * @param <A6> The sixth argument type
 * @param <A7> The seventh argument type
 */
@FunctionalInterface
public interface HeptaPredicate<A1, A2, A3, A4, A5, A6, A7> {
	/**
	 * @param arg1 The first argument
	 * @param arg2 The second argument
	 * @param arg3 The third argument
	 * @param arg4 The fourth argument
	 * @param arg5 The fifth argument
	 * @param arg6 The sixth argument
	 * @param arg7 The seventh argument
	 * @return The result of the test
	 */
	boolean test(A1 arg1, A2 arg2, A3 arg3, A4 arg4, A5 arg5, A6 arg6, A7 arg7);

	/**
	 * @param <A1> The type of the first argument to the predicate
	 * @param <A2> The type of the second argument to the predicate
	 * @param <A3> The type of the third argument to the predicate
	 * @param <A4> The type of the fourth argument to the predicate
	 * @param <A5> The type of the fifth argument to the predicate
	 * @param <A6> The type of the sixth argument to the predicate
	 * @param <A7> The type of the seventh argument to the predicate
	 * @param function The implementation
	 * @param print The string for the wrapped predicate's {@link #toString()}
	 * @param identity The identity for the predicate's {@link #hashCode()} and {@link #equals(Object)} methods
	 * @return The wrapped, printable predicate
	 */
	public static <A1, A2, A3, A4, A5, A6, A7> HeptaPredicate<A1, A2, A3, A4, A5, A6, A7> printable(
		HeptaPredicate<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? super A6, ? super A7> function, String print,
		Object identity) {
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
	 * @param function The implementation
	 * @param print Supplies the string for the wrapped predicate's {@link #toString()}
	 * @param identity The identity for the predicate's {@link #hashCode()} and {@link #equals(Object)} methods
	 * @return The wrapped, printable predicate
	 */
	public static <A1, A2, A3, A4, A5, A6, A7> HeptaPredicate<A1, A2, A3, A4, A5, A6, A7> printable(
		HeptaPredicate<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? super A6, ? super A7> function, Supplier<String> print,
		Object identity) {
		return new PrintableHeptaPredicate<>(function, print, identity);
	}

	/**
	 * Implements {@link HexaPredicate#printable(HexaPredicate, String, Object)}
	 * 
	 * @param <A1> The first argument type
	 * @param <A2> The second argument type
	 * @param <A3> The third argument type
	 * @param <A4> The fourth argument type
	 * @param <A5> The fifth argument type
	 * @param <A6> The sixth argument type
	 * @param <A7> The seventh argument type
	 */
	class PrintableHeptaPredicate<A1, A2, A3, A4, A5, A6, A7> extends
		FunctionUtils.PrintableLambda<HeptaPredicate<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? super A6, ? super A7>>
		implements HeptaPredicate<A1, A2, A3, A4, A5, A6, A7> {
		public PrintableHeptaPredicate(
			HeptaPredicate<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? super A6, ? super A7> lambda,
			Supplier<String> print, Object identifier) {
			super(lambda, print, identifier);
		}

		@Override
		public boolean isTrivial() {
			return false;
		}

		@Override
		public boolean test(A1 arg1, A2 arg2, A3 arg3, A4 arg4, A5 arg5, A6 arg6, A7 arg7) {
			return getLambda().test(arg1, arg2, arg3, arg4, arg5, arg6, arg7);
		}
	}
}

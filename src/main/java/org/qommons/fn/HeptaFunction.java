package org.qommons.fn;

import java.util.function.Supplier;

/**
 * A function that operates on 7 arguments
 * 
 * @param <A1> The first argument type
 * @param <A2> The second argument type
 * @param <A3> The third argument type
 * @param <A4> The fourth argument type
 * @param <A5> The fifth argument type
 * @param <A6> The sixth argument type
 * @param <A7> The seventh argument type
 * @param <R> The return type of the function
 */
@FunctionalInterface
public interface HeptaFunction<A1, A2, A3, A4, A5, A6, A7, R> {
	/**
	 * @param arg1 The first argument
	 * @param arg2 The second argument
	 * @param arg3 The third argument
	 * @param arg4 The fourth argument
	 * @param arg5 The fifth argument
	 * @param arg6 The sixth argument
	 * @param arg7 The seventh argument
	 * @return The result of the function call
	 */
	R apply(A1 arg1, A2 arg2, A3 arg3, A4 arg4, A5 arg5, A6 arg6, A7 arg7);

	/**
	 * @param <A1> The type of the first argument to the function
	 * @param <A2> The type of the second argument to the function
	 * @param <A3> The type of the third argument to the function
	 * @param <A4> The type of the fourth argument to the function
	 * @param <A5> The type of the fifth argument to the function
	 * @param <A6> The type of the sixth argument to the function
	 * @param <A7> The type of the seventh argument to the function
	 * @param <R> The return type of the function
	 * @param function The implementation
	 * @param print The string for the wrapped function's {@link #toString()}
	 * @param identity The identity for the function's {@link #hashCode()} and {@link #equals(Object)} methods
	 * @return The wrapped, printable function
	 */
	public static <A1, A2, A3, A4, A5, A6, A7, R> HeptaFunction<A1, A2, A3, A4, A5, A6, A7, R> printable(
		HeptaFunction<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? super A6, ? super A7, ? extends R> function,
		String print, Object identity) {
		return printable(function, () -> print, identity);
	}

	/**
	 * @param <A1> The type of the first argument to the function
	 * @param <A2> The type of the second argument to the function
	 * @param <A3> The type of the third argument to the function
	 * @param <A4> The type of the fourth argument to the function
	 * @param <A5> The type of the fifth argument to the function
	 * @param <A6> The type of the sixth argument to the function
	 * @param <A7> The type of the seventh argument to the function
	 * @param <R> The return type of the function
	 * @param function The implementation
	 * @param print Supplies the string for the wrapped function's {@link #toString()}
	 * @param identity The identity for the function's {@link #hashCode()} and {@link #equals(Object)} methods
	 * @return The wrapped, printable function
	 */
	public static <A1, A2, A3, A4, A5, A6, A7, R> HeptaFunction<A1, A2, A3, A4, A5, A6, A7, R> printable(
		HeptaFunction<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? super A6, ? super A7, ? extends R> function,
		Supplier<String> print, Object identity) {
		return new PrintableHeptaFn<>(function, print, identity);
	}

	/**
	 * Implements {@link HeptaFunction#printable(HeptaFunction, String, Object)}
	 * 
	 * @param <A1> The first argument type
	 * @param <A2> The second argument type
	 * @param <A3> The third argument type
	 * @param <A4> The fourth argument type
	 * @param <A5> The fifth argument type
	 * @param <A6> The sixth argument type
	 * @param <A7> The seventh argument type
	 * @param <R> The return type of the function
	 */
	class PrintableHeptaFn<A1, A2, A3, A4, A5, A6, A7, R> extends
		FunctionUtils.PrintableLambda<HeptaFunction<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? super A6, ? super A7, ? extends R>>
		implements HeptaFunction<A1, A2, A3, A4, A5, A6, A7, R> {
		public PrintableHeptaFn(
			HeptaFunction<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? super A6, ? super A7, ? extends R> lambda,
			Supplier<String> print, Object identifier) {
			super(lambda, print, identifier);
		}

		@Override
		public boolean isTrivial() {
			return false;
		}

		@Override
		public R apply(A1 arg1, A2 arg2, A3 arg3, A4 arg4, A5 arg5, A6 arg6, A7 arg7) {
			return getLambda().apply(arg1, arg2, arg3, arg4, arg5, arg6, arg7);
		}
	}
}

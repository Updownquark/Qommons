package org.qommons.fn;

import java.util.function.Supplier;

/**
 * A function that operates on 9 arguments
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
 * @param <R> The return type of the function
 */
@FunctionalInterface
public interface NonaFunction<A1, A2, A3, A4, A5, A6, A7, A8, A9, R> {
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
	 * @return The result of the function call
	 */
	R apply(A1 arg1, A2 arg2, A3 arg3, A4 arg4, A5 arg5, A6 arg6, A7 arg7, A8 arg8, A9 arg9);

	/**
	 * @param <A1> The type of the first argument to the function
	 * @param <A2> The type of the second argument to the function
	 * @param <A3> The type of the third argument to the function
	 * @param <A4> The type of the fourth argument to the function
	 * @param <A5> The type of the fifth argument to the function
	 * @param <A6> The type of the sixth argument to the function
	 * @param <A7> The type of the seventh argument to the function
	 * @param <A8> The type of the eighth argument to the function
	 * @param <A9> The type of the ninth argument to the function
	 * @param <R> The return type of the function
	 * @param function The implementation
	 * @param print The string for the wrapped function's {@link #toString()}
	 * @param identity The identity for the function's {@link #hashCode()} and {@link #equals(Object)} methods
	 * @return The wrapped, printable function
	 */
	public static <A1, A2, A3, A4, A5, A6, A7, A8, A9, R> NonaFunction<A1, A2, A3, A4, A5, A6, A7, A8, A9, R> printable(
		NonaFunction<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? super A6, ? super A7, ? super A8, ? super A9, ? extends R> function,
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
	 * @param <A8> The type of the eighth argument to the function
	 * @param <A9> The type of the ninth argument to the function
	 * @param <R> The return type of the function
	 * @param function The implementation
	 * @param print Supplies the string for the wrapped function's {@link #toString()}
	 * @param identity The identity for the function's {@link #hashCode()} and {@link #equals(Object)} methods
	 * @return The wrapped, printable function
	 */
	public static <A1, A2, A3, A4, A5, A6, A7, A8, A9, R> NonaFunction<A1, A2, A3, A4, A5, A6, A7, A8, A9, R> printable(
		NonaFunction<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? super A6, ? super A7, ? super A8, ? super A9, ? extends R> function,
		Supplier<String> print, Object identity) {
		return new PrintableNonaFn<>(function, print, identity);
	}

	/**
	 * Implements {@link NonaFunction#printable(NonaFunction, String, Object)}
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
	 * @param <R> The return type of the function
	 */
	class PrintableNonaFn<A1, A2, A3, A4, A5, A6, A7, A8, A9, R> extends
		FunctionUtils.PrintableLambda<NonaFunction<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? super A6, ? super A7, ? super A8, ? super A9, ? extends R>>
		implements NonaFunction<A1, A2, A3, A4, A5, A6, A7, A8, A9, R> {
		public PrintableNonaFn(
			NonaFunction<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? super A6, ? super A7, ? super A8, ? super A9, ? extends R> lambda,
			Supplier<String> print, Object identifier) {
			super(lambda, print, identifier);
		}

		@Override
		public boolean isTrivial() {
			return false;
		}

		@Override
		public R apply(A1 arg1, A2 arg2, A3 arg3, A4 arg4, A5 arg5, A6 arg6, A7 arg7, A8 arg8, A9 arg9) {
			return getLambda().apply(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
		}
	}
}

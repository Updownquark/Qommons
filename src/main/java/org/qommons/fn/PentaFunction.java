package org.qommons.fn;

import java.util.function.Supplier;

/**
 * A function that operates on 5 arguments
 * 
 * @param <A1> The first argument type
 * @param <A2> The second argument type
 * @param <A3> The third argument type
 * @param <A4> The fourth argument type
 * @param <A5> The fifth argument type
 * @param <R> The return type of the function
 */
@FunctionalInterface
public interface PentaFunction<A1, A2, A3, A4, A5, R> {
	/**
	 * @param arg1 The first argument
	 * @param arg2 The second argument
	 * @param arg3 The third argument
	 * @param arg4 The fourth argument
	 * @param arg5 The fifth argument
	 * @return The result of the function call
	 */
	R apply(A1 arg1, A2 arg2, A3 arg3, A4 arg4, A5 arg5);

	/**
	 * @param <A1> The type of the first argument to the 5-argument function
	 * @param <A2> The type of the second argument to the 5-argument function
	 * @param <A3> The type of the third argument to the 5-argument function
	 * @param <A4> The type of the fourth argument to the 5-argument function
	 * @param <A5> The type of the fifth argument to the 5-argument function
	 * @param <R> The return type of the function
	 * @param function The implementation
	 * @param print The string for the wrapped function's {@link #toString()}
	 * @param identity The identity for the function's {@link #hashCode()} and {@link #equals(Object)} methods
	 * @return The wrapped, printable 5-argument function
	 */
	public static <A1, A2, A3, A4, A5, R> PentaFunction<A1, A2, A3, A4, A5, R> printable(
		PentaFunction<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? extends R> function, String print, Object identity) {
		return printable(function, () -> print, identity);
	}

	/**
	 * @param <A1> The type of the first argument to the 5-argument function
	 * @param <A2> The type of the second argument to the 5-argument function
	 * @param <A3> The type of the third argument to the 5-argument function
	 * @param <A4> The type of the fourth argument to the 5-argument function
	 * @param <A5> The type of the fifth argument to the 5-argument function
	 * @param <R> The return type of the function
	 * @param function The implementation
	 * @param print Supplies the string for the wrapped function's {@link #toString()}
	 * @param identity The identity for the function's {@link #hashCode()} and {@link #equals(Object)} methods
	 * @return The wrapped, printable 5-argument function
	 */
	public static <A1, A2, A3, A4, A5, R> PentaFunction<A1, A2, A3, A4, A5, R> printable(
		PentaFunction<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? extends R> function, Supplier<String> print,
		Object identity) {
		return new PrintablePentaFn<>(function, print, identity);
	}

	/**
	 * Implements {@link PentaFunction#printable(PentaFunction, String, Object)}
	 * 
	 * @param <A1> The first argument type
	 * @param <A2> The second argument type
	 * @param <A3> The third argument type
	 * @param <A4> The fourth argument type
	 * @param <A5> The fifth argument type
	 * @param <R> The return type of the function
	 */
	class PrintablePentaFn<A1, A2, A3, A4, A5, R>
		extends FunctionUtils.PrintableLambda<PentaFunction<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? extends R>>
		implements PentaFunction<A1, A2, A3, A4, A5, R> {
		public PrintablePentaFn(PentaFunction<? super A1, ? super A2, ? super A3, ? super A4, ? super A5, ? extends R> lambda,
			Supplier<String> print, Object identifier) {
			super(lambda, print, identifier);
		}

		@Override
		public boolean isTrivial() {
			return false;
		}

		@Override
		public R apply(A1 arg1, A2 arg2, A3 arg3, A4 arg4, A5 arg5) {
			return getLambda().apply(arg1, arg2, arg3, arg4, arg5);
		}
	}
}

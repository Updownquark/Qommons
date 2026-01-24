package org.qommons.fn;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A function that operates on 4 arguments
 * 
 * @param <A1> The first argument type
 * @param <A2> The second argument type
 * @param <A3> The third argument type
 * @param <A4> The fourth argument type
 * @param <R> The return type of the function
 */
@FunctionalInterface
public interface QuadFunction<A1, A2, A3, A4, R> {
	/**
	 * @param arg1 The first argument
	 * @param arg2 The second argument
	 * @param arg3 The third argument
	 * @param arg4 The fourth argument
	 * @return The product of the function call
	 */
	R apply(A1 arg1, A2 arg2, A3 arg3, A4 arg4);

	/**
	 * @param arg1 The first argument to this function
	 * @return A ternary function that calls this quarternary function with a constant first argument
	 */
	default TriFunction<A2, A3, A4, R> curry1(A1 arg1) {
		return new QuadFnCurry1<>(this, arg1);
	}

	/**
	 * @param arg2 The first argument to this function
	 * @return A ternary function that calls this quaternary function with a constant second argument
	 */
	default TriFunction<A1, A3, A4, R> curry2(A2 arg2) {
		return new QuadFnCurry2<>(this, arg2);
	}

	/**
	 * @param arg3 The third argument to this function
	 * @return A ternary function that calls this quaternary function with a constant third argument
	 */
	default TriFunction<A1, A2, A4, R> curry3(A3 arg3) {
		return new QuadFnCurry3<>(this, arg3);
	}

	/**
	 * @param arg4 The fourth argument to this function
	 * @return A ternary function that calls this quaternary function with a constant fourth argument
	 */
	default TriFunction<A1, A2, A3, R> curry4(A4 arg4) {
		return new QuadFnCurry4<>(this, arg4);
	}

	/**
	 * @param <X> The return type of the transformed function
	 * @param tx The transform function for results of this function
	 * @return A quad-function with the same arguments as this, but which transforms its end result with the given function
	 */
	default <X> QuadFunction<A1, A2, A3, A4, X> andThen(Function<? super R, ? extends X> tx) {
		return new AndThenQuadFn<>(this, tx);
	}

	/**
	 * @param <A1> The type of the first argument to the quaternary function
	 * @param <A2> The type of the second argument to the quaternary function
	 * @param <A3> The type of the third argument to the quaternary function
	 * @param <A4> The type of the fourth argument to the quaternary function
	 * @param <R> The return type of the function
	 * @param function The implementation
	 * @param print The string for the wrapped function's {@link #toString()}
	 * @param identity The identity for the function's {@link #hashCode()} and {@link #equals(Object)} methods
	 * @return The wrapped, printable quaternary function
	 */
	public static <A1, A2, A3, A4, R> QuadFunction<A1, A2, A3, A4, R> printable(
		QuadFunction<? super A1, ? super A2, ? super A3, ? super A4, ? extends R> function, String print, Object identity) {
		return printable(function, () -> print, identity);
	}

	/**
	 * @param <A1> The type of the first argument to the quaternary function
	 * @param <A2> The type of the second argument to the quaternary function
	 * @param <A3> The type of the third argument to the quaternary function
	 * @param <A4> The type of the fourth argument to the quaternary function
	 * @param <R> The return type of the function
	 * @param function The implementation
	 * @param print Supplies the string for the wrapped function's {@link #toString()}
	 * @param identity The identity for the function's {@link #hashCode()} and {@link #equals(Object)} methods
	 * @return The wrapped, printable quaternary function
	 */
	public static <A1, A2, A3, A4, R> QuadFunction<A1, A2, A3, A4, R> printable(
		QuadFunction<? super A1, ? super A2, ? super A3, ? super A4, ? extends R> function, Supplier<String> print, Object identity) {
		return new PrintableQuadFn<>(function, print, identity);
	}

	/**
	 * Implements {@link QuadFunction#curry1(Object)}
	 * 
	 * @param <A1> The type of the first argument to the quaternary function
	 * @param <A2> The type of the second argument to the quaternary function
	 * @param <A3> The type of the third argument to the quaternary function
	 * @param <A4> The type of the fourth argument to the quaternary function
	 * @param <R> The return type of the function
	 */
	class QuadFnCurry1<A1, A2, A3, A4, R> implements TriFunction<A2, A3, A4, R> {
		private final QuadFunction<A1, A2, A3, A4, R> theSource;
		private final A1 theArg1;

		QuadFnCurry1(QuadFunction<A1, A2, A3, A4, R> source, A1 arg1) {
			theSource = source;
			theArg1 = arg1;
		}

		protected QuadFunction<A1, A2, A3, A4, R> getSource() {
			return theSource;
		}

		protected A1 getArg1() {
			return theArg1;
		}

		@Override
		public R apply(A2 arg1, A3 arg2, A4 arg3) {
			return theSource.apply(theArg1, arg1, arg2, arg3);
		}

		@Override
		public BetterBiFunction<A3, A4, R> curry1(A2 arg1) {
			return new QuadFnCurry12<>(theSource, theArg1, arg1);
		}

		@Override
		public BetterBiFunction<A2, A4, R> curry2(A3 arg2) {
			return new QuadFnCurry13<>(theSource, theArg1, arg2);
		}

		@Override
		public BetterBiFunction<A2, A3, R> curry3(A4 arg3) {
			return new QuadFnCurry14<>(theSource, theArg1, arg3);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 1, theArg1);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof QuadFnCurry1))
				return false;
			QuadFnCurry1<?, ?, ?, ?, ?> other = (QuadFnCurry1<?, ?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource()) && Objects.equals(theArg1, other.getArg1());
		}

		@Override
		public String toString() {
			return theSource + ".curry1(" + theArg1 + ")";
		}
	}

	/**
	 * Implements {@link QuadFunction#curry2(Object)}
	 * 
	 * @param <A1> The type of the first argument to the quaternary function
	 * @param <A2> The type of the second argument to the quaternary function
	 * @param <A3> The type of the third argument to the quaternary function
	 * @param <A4> The type of the fourth argument to the quaternary function
	 * @param <R> The return type of the function
	 */
	class QuadFnCurry2<A1, A2, A3, A4, R> implements TriFunction<A1, A3, A4, R> {
		private final QuadFunction<A1, A2, A3, A4, R> theSource;
		private final A2 theArg2;

		QuadFnCurry2(QuadFunction<A1, A2, A3, A4, R> source, A2 arg2) {
			theSource = source;
			theArg2 = arg2;
		}

		protected QuadFunction<A1, A2, A3, A4, R> getSource() {
			return theSource;
		}

		protected A2 getArg2() {
			return theArg2;
		}

		@Override
		public R apply(A1 arg1, A3 arg2, A4 arg3) {
			return theSource.apply(arg1, theArg2, arg2, arg3);
		}

		@Override
		public BetterBiFunction<A3, A4, R> curry1(A1 arg1) {
			return new QuadFnCurry12<>(theSource, arg1, theArg2);
		}

		@Override
		public BetterBiFunction<A1, A4, R> curry2(A3 arg2) {
			return new QuadFnCurry23<>(theSource, theArg2, arg2);
		}

		@Override
		public BetterBiFunction<A1, A3, R> curry3(A4 arg3) {
			return new QuadFnCurry24<>(theSource, theArg2, arg3);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 1, theArg2);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof QuadFnCurry2))
				return false;
			QuadFnCurry2<?, ?, ?, ?, ?> other = (QuadFnCurry2<?, ?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource()) && Objects.equals(theArg2, other.getArg2());
		}

		@Override
		public String toString() {
			return theSource + ".curry2(" + theArg2 + ")";
		}
	}

	/**
	 * Implements {@link QuadFunction#curry3(Object)}
	 * 
	 * @param <A1> The type of the first argument to the quaternary function
	 * @param <A2> The type of the second argument to the quaternary function
	 * @param <A3> The type of the third argument to the quaternary function
	 * @param <A4> The type of the fourth argument to the quaternary function
	 * @param <R> The return type of the function
	 */
	class QuadFnCurry3<A1, A2, A3, A4, R> implements TriFunction<A1, A2, A4, R> {
		private final QuadFunction<A1, A2, A3, A4, R> theSource;
		private final A3 theArg3;

		QuadFnCurry3(QuadFunction<A1, A2, A3, A4, R> source, A3 arg3) {
			theSource = source;
			theArg3 = arg3;
		}

		protected QuadFunction<A1, A2, A3, A4, R> getSource() {
			return theSource;
		}

		protected A3 getArg3() {
			return theArg3;
		}

		@Override
		public R apply(A1 arg1, A2 arg2, A4 arg3) {
			return theSource.apply(arg1, arg2, theArg3, arg3);
		}

		@Override
		public BetterBiFunction<A2, A4, R> curry1(A1 arg1) {
			return new QuadFnCurry13<>(theSource, arg1, theArg3);
		}

		@Override
		public BetterBiFunction<A1, A4, R> curry2(A2 arg2) {
			return new QuadFnCurry23<>(theSource, arg2, theArg3);
		}

		@Override
		public BetterBiFunction<A1, A2, R> curry3(A4 arg3) {
			return new QuadFnCurry34<>(theSource, theArg3, arg3);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 1, theArg3);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof QuadFnCurry3))
				return false;
			QuadFnCurry3<?, ?, ?, ?, ?> other = (QuadFnCurry3<?, ?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource()) && Objects.equals(theArg3, other.getArg3());
		}

		@Override
		public String toString() {
			return theSource + ".curry3(" + theArg3 + ")";
		}
	}

	/**
	 * Implements {@link QuadFunction#curry3(Object)}
	 * 
	 * @param <A1> The type of the first argument to the quaternary function
	 * @param <A2> The type of the second argument to the quaternary function
	 * @param <A3> The type of the third argument to the quaternary function
	 * @param <A4> The type of the fourth argument to the quaternary function
	 * @param <R> The return type of the function
	 */
	class QuadFnCurry4<A1, A2, A3, A4, R> implements TriFunction<A1, A2, A3, R> {
		private final QuadFunction<A1, A2, A3, A4, R> theSource;
		private final A4 theArg4;

		QuadFnCurry4(QuadFunction<A1, A2, A3, A4, R> source, A4 arg4) {
			theSource = source;
			theArg4 = arg4;
		}

		protected QuadFunction<A1, A2, A3, A4, R> getSource() {
			return theSource;
		}

		protected A4 getArg4() {
			return theArg4;
		}

		@Override
		public R apply(A1 arg1, A2 arg2, A3 arg3) {
			return theSource.apply(arg1, arg2, arg3, theArg4);
		}

		@Override
		public BetterBiFunction<A2, A3, R> curry1(A1 arg1) {
			return new QuadFnCurry14<>(theSource, arg1, theArg4);
		}

		@Override
		public BetterBiFunction<A1, A3, R> curry2(A2 arg2) {
			return new QuadFnCurry24<>(theSource, arg2, theArg4);
		}

		@Override
		public BetterBiFunction<A1, A2, R> curry3(A3 arg3) {
			return new QuadFnCurry34<>(theSource, arg3, theArg4);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 1, theArg4);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof QuadFnCurry4))
				return false;
			QuadFnCurry4<?, ?, ?, ?, ?> other = (QuadFnCurry4<?, ?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource()) && Objects.equals(theArg4, other.getArg4());
		}

		@Override
		public String toString() {
			return theSource + ".curry4(" + theArg4 + ")";
		}
	}

	/**
	 * A quaternary function with its first two arguments constant
	 * 
	 * @param <A1> The type of the first argument to the quaternary function
	 * @param <A2> The type of the second argument to the quaternary function
	 * @param <A3> The type of the third argument to the quaternary function
	 * @param <A4> The type of the fourth argument to the quaternary function
	 * @param <R> The return type of the function
	 */
	class QuadFnCurry12<A1, A2, A3, A4, R> implements BetterBiFunction<A3, A4, R> {
		private final QuadFunction<A1, A2, A3, A4, R> theSource;
		private final A1 theArg1;
		private final A2 theArg2;

		QuadFnCurry12(QuadFunction<A1, A2, A3, A4, R> source, A1 arg1, A2 arg2) {
			theSource = source;
			theArg1 = arg1;
			theArg2 = arg2;
		}

		protected QuadFunction<A1, A2, A3, A4, R> getSource() {
			return theSource;
		}

		protected A1 getArg1() {
			return theArg1;
		}

		protected A2 getArg2() {
			return theArg2;
		}

		@Override
		public R apply(A3 arg3, A4 arg4) {
			return theSource.apply(theArg1, theArg2, arg3, arg4);
		}

		@Override
		public BetterFunction<A4, R> curry1(A3 arg1) {
			return new QuadFnCurry123<>(theSource, theArg1, theArg2, arg1);
		}

		@Override
		public BetterFunction<A3, R> curry2(A4 arg2) {
			return new QuadFnCurry124<>(theSource, theArg1, theArg2, arg2);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 1, theArg1, theArg2);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof QuadFnCurry12))
				return false;
			QuadFnCurry12<?, ?, ?, ?, ?> other = (QuadFnCurry12<?, ?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource())//
				&& Objects.equals(theArg1, other.getArg1()) && Objects.equals(theArg2, other.getArg2());
		}

		@Override
		public String toString() {
			return theSource + ".curry12(" + theArg1 + ", " + theArg2 + ")";
		}
	}

	/**
	 * A quaternary function with its first and third arguments constant
	 * 
	 * @param <A1> The type of the first argument to the quaternary function
	 * @param <A2> The type of the second argument to the quaternary function
	 * @param <A3> The type of the third argument to the quaternary function
	 * @param <A4> The type of the fourth argument to the quaternary function
	 * @param <R> The return type of the function
	 */
	class QuadFnCurry13<A1, A2, A3, A4, R> implements BetterBiFunction<A2, A4, R> {
		private final QuadFunction<A1, A2, A3, A4, R> theSource;
		private final A1 theArg1;
		private final A3 theArg3;

		QuadFnCurry13(QuadFunction<A1, A2, A3, A4, R> source, A1 arg1, A3 arg3) {
			theSource = source;
			theArg1 = arg1;
			theArg3 = arg3;
		}

		protected QuadFunction<A1, A2, A3, A4, R> getSource() {
			return theSource;
		}

		protected A1 getArg1() {
			return theArg1;
		}

		protected A3 getArg3() {
			return theArg3;
		}

		@Override
		public R apply(A2 arg2, A4 arg4) {
			return theSource.apply(theArg1, arg2, theArg3, arg4);
		}

		@Override
		public BetterFunction<A4, R> curry1(A2 arg1) {
			return new QuadFnCurry123<>(theSource, theArg1, arg1, theArg3);
		}

		@Override
		public BetterFunction<A2, R> curry2(A4 arg2) {
			return new QuadFnCurry134<>(theSource, theArg1, theArg3, arg2);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 1, theArg1, theArg3);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof QuadFnCurry13))
				return false;
			QuadFnCurry13<?, ?, ?, ?, ?> other = (QuadFnCurry13<?, ?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource())//
				&& Objects.equals(theArg1, other.getArg1()) && Objects.equals(theArg3, other.getArg3());
		}

		@Override
		public String toString() {
			return theSource + ".curry13(" + theArg1 + ", " + theArg3 + ")";
		}
	}

	/**
	 * A quaternary function with its first and fourth arguments constant
	 * 
	 * @param <A1> The type of the first argument to the quaternary function
	 * @param <A2> The type of the second argument to the quaternary function
	 * @param <A3> The type of the third argument to the quaternary function
	 * @param <A4> The type of the fourth argument to the quaternary function
	 * @param <R> The return type of the function
	 */
	class QuadFnCurry14<A1, A2, A3, A4, R> implements BetterBiFunction<A2, A3, R> {
		private final QuadFunction<A1, A2, A3, A4, R> theSource;
		private final A1 theArg1;
		private final A4 theArg4;

		QuadFnCurry14(QuadFunction<A1, A2, A3, A4, R> source, A1 arg1, A4 arg4) {
			theSource = source;
			theArg1 = arg1;
			theArg4 = arg4;
		}

		protected QuadFunction<A1, A2, A3, A4, R> getSource() {
			return theSource;
		}

		protected A1 getArg1() {
			return theArg1;
		}

		protected A4 getArg4() {
			return theArg4;
		}

		@Override
		public R apply(A2 arg2, A3 arg3) {
			return theSource.apply(theArg1, arg2, arg3, theArg4);
		}

		@Override
		public BetterFunction<A3, R> curry1(A2 arg1) {
			return new QuadFnCurry124<>(theSource, theArg1, arg1, theArg4);
		}

		@Override
		public BetterFunction<A2, R> curry2(A3 arg2) {
			return new QuadFnCurry134<>(theSource, theArg1, arg2, theArg4);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 1, theArg1, theArg4);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof QuadFnCurry14))
				return false;
			QuadFnCurry14<?, ?, ?, ?, ?> other = (QuadFnCurry14<?, ?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource())//
				&& Objects.equals(theArg1, other.getArg1()) && Objects.equals(theArg4, other.getArg4());
		}

		@Override
		public String toString() {
			return theSource + ".curry14(" + theArg1 + ", " + theArg4 + ")";
		}
	}

	/**
	 * A quaternary function with its first and fourth arguments constant
	 * 
	 * @param <A1> The type of the first argument to the quaternary function
	 * @param <A2> The type of the second argument to the quaternary function
	 * @param <A3> The type of the third argument to the quaternary function
	 * @param <A4> The type of the fourth argument to the quaternary function
	 * @param <R> The return type of the function
	 */
	class QuadFnCurry23<A1, A2, A3, A4, R> implements BetterBiFunction<A1, A4, R> {
		private final QuadFunction<A1, A2, A3, A4, R> theSource;
		private final A2 theArg2;
		private final A3 theArg3;

		QuadFnCurry23(QuadFunction<A1, A2, A3, A4, R> source, A2 arg2, A3 arg3) {
			theSource = source;
			theArg2 = arg2;
			theArg3 = arg3;
		}

		protected QuadFunction<A1, A2, A3, A4, R> getSource() {
			return theSource;
		}

		protected A2 getArg2() {
			return theArg2;
		}

		protected A3 getArg3() {
			return theArg3;
		}

		@Override
		public R apply(A1 arg1, A4 arg4) {
			return theSource.apply(arg1, theArg2, theArg3, arg4);
		}

		@Override
		public BetterFunction<A4, R> curry1(A1 arg1) {
			return new QuadFnCurry123<>(theSource, arg1, theArg2, theArg3);
		}

		@Override
		public BetterFunction<A1, R> curry2(A4 arg2) {
			return new QuadFnCurry234<>(theSource, theArg2, theArg3, arg2);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 1, theArg2, theArg3);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof QuadFnCurry23))
				return false;
			QuadFnCurry23<?, ?, ?, ?, ?> other = (QuadFnCurry23<?, ?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource())//
				&& Objects.equals(theArg2, other.theArg2) && Objects.equals(theArg3, other.theArg3);
		}

		@Override
		public String toString() {
			return theSource + ".curry23(" + theArg2 + ", " + theArg3 + ")";
		}
	}

	/**
	 * A quaternary function with its first and fourth arguments constant
	 * 
	 * @param <A1> The type of the first argument to the quaternary function
	 * @param <A2> The type of the second argument to the quaternary function
	 * @param <A3> The type of the third argument to the quaternary function
	 * @param <A4> The type of the fourth argument to the quaternary function
	 * @param <R> The return type of the function
	 */
	class QuadFnCurry24<A1, A2, A3, A4, R> implements BetterBiFunction<A1, A3, R> {
		private final QuadFunction<A1, A2, A3, A4, R> theSource;
		private final A2 theArg2;
		private final A4 theArg4;

		QuadFnCurry24(QuadFunction<A1, A2, A3, A4, R> source, A2 arg2, A4 arg4) {
			theSource = source;
			theArg2 = arg2;
			theArg4 = arg4;
		}

		protected QuadFunction<A1, A2, A3, A4, R> getSource() {
			return theSource;
		}

		protected A2 getArg2() {
			return theArg2;
		}

		protected A4 getArg4() {
			return theArg4;
		}

		@Override
		public R apply(A1 arg1, A3 arg3) {
			return theSource.apply(arg1, theArg2, arg3, theArg4);
		}

		@Override
		public BetterFunction<A3, R> curry1(A1 arg1) {
			return new QuadFnCurry124<>(theSource, arg1, theArg2, theArg4);
		}

		@Override
		public BetterFunction<A1, R> curry2(A3 arg2) {
			return new QuadFnCurry234<>(theSource, theArg2, arg2, theArg4);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 1, theArg2, theArg4);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof QuadFnCurry24))
				return false;
			QuadFnCurry24<?, ?, ?, ?, ?> other = (QuadFnCurry24<?, ?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource())//
				&& Objects.equals(theArg2, other.theArg2) && Objects.equals(theArg4, other.theArg4);
		}

		@Override
		public String toString() {
			return theSource + ".curry24(" + theArg2 + ", " + theArg4 + ")";
		}
	}

	/**
	 * A quaternary function with its first and fourth arguments constant
	 * 
	 * @param <A1> The type of the first argument to the quaternary function
	 * @param <A2> The type of the second argument to the quaternary function
	 * @param <A3> The type of the third argument to the quaternary function
	 * @param <A4> The type of the fourth argument to the quaternary function
	 * @param <R> The return type of the function
	 */
	class QuadFnCurry34<A1, A2, A3, A4, R> implements BetterBiFunction<A1, A2, R> {
		private final QuadFunction<A1, A2, A3, A4, R> theSource;
		private final A3 theArg3;
		private final A4 theArg4;

		QuadFnCurry34(QuadFunction<A1, A2, A3, A4, R> source, A3 arg3, A4 arg4) {
			theSource = source;
			theArg3 = arg3;
			theArg4 = arg4;
		}

		protected QuadFunction<A1, A2, A3, A4, R> getSource() {
			return theSource;
		}

		protected A3 getArg3() {
			return theArg3;
		}

		protected A4 getArg4() {
			return theArg4;
		}

		@Override
		public R apply(A1 arg1, A2 arg2) {
			return theSource.apply(arg1, arg2, theArg3, theArg4);
		}

		@Override
		public BetterFunction<A2, R> curry1(A1 arg1) {
			return new QuadFnCurry134<>(theSource, arg1, theArg3, theArg4);
		}

		@Override
		public BetterFunction<A1, R> curry2(A2 arg2) {
			return new QuadFnCurry234<>(theSource, arg2, theArg3, theArg4);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 1, theArg3, theArg4);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof QuadFnCurry24))
				return false;
			QuadFnCurry34<?, ?, ?, ?, ?> other = (QuadFnCurry34<?, ?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource())//
				&& Objects.equals(theArg3, other.theArg3) && Objects.equals(theArg4, other.theArg4);
		}

		@Override
		public String toString() {
			return theSource + ".curry24(" + theArg3 + ", " + theArg4 + ")";
		}
	}

	/**
	 * A quaternary function with its first 3 arguments constant
	 * 
	 * @param <A1> The type of the first argument to the quaternary function
	 * @param <A2> The type of the second argument to the quaternary function
	 * @param <A3> The type of the third argument to the quaternary function
	 * @param <A4> The type of the fourth argument to the quaternary function
	 * @param <R> The return type of the function
	 */
	class QuadFnCurry123<A1, A2, A3, A4, R> implements BetterFunction<A4, R> {
		private final QuadFunction<A1, A2, A3, A4, R> theSource;
		private final A1 theArg1;
		private final A2 theArg2;
		private final A3 theArg3;

		QuadFnCurry123(QuadFunction<A1, A2, A3, A4, R> source, A1 arg1, A2 arg2, A3 arg3) {
			theSource = source;
			theArg1 = arg1;
			theArg2 = arg2;
			theArg3 = arg3;
		}

		protected QuadFunction<A1, A2, A3, A4, R> getSource() {
			return theSource;
		}

		protected A1 getArg1() {
			return theArg1;
		}

		protected A2 getArg2() {
			return theArg2;
		}

		protected A3 getArg3() {
			return theArg3;
		}

		@Override
		public R apply(A4 arg4) {
			return theSource.apply(theArg1, theArg2, theArg3, arg4);
		}

		@Override
		public Supplier<R> curry(A4 arg) {
			return new QuadFnCurryAll<>(theSource, theArg1, theArg2, theArg3, arg);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 1, theArg1, theArg2, theArg3);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof QuadFnCurry123))
				return false;
			QuadFnCurry123<?, ?, ?, ?, ?> other = (QuadFnCurry123<?, ?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource())//
				&& Objects.equals(theArg1, other.theArg1) && Objects.equals(theArg2, other.theArg2)
				&& Objects.equals(theArg3, other.theArg3);
		}

		@Override
		public String toString() {
			return theSource + ".curry123(" + theArg1 + ", " + theArg2 + ", " + theArg3 + ")";
		}
	}

	/**
	 * A quaternary function with its first, second, and fourth arguments constant
	 * 
	 * @param <A1> The type of the first argument to the quaternary function
	 * @param <A2> The type of the second argument to the quaternary function
	 * @param <A3> The type of the third argument to the quaternary function
	 * @param <A4> The type of the fourth argument to the quaternary function
	 * @param <R> The return type of the function
	 */
	class QuadFnCurry124<A1, A2, A3, A4, R> implements BetterFunction<A3, R> {
		private final QuadFunction<A1, A2, A3, A4, R> theSource;
		private final A1 theArg1;
		private final A2 theArg2;
		private final A4 theArg4;

		QuadFnCurry124(QuadFunction<A1, A2, A3, A4, R> source, A1 arg1, A2 arg2, A4 arg4) {
			theSource = source;
			theArg1 = arg1;
			theArg2 = arg2;
			theArg4 = arg4;
		}

		protected QuadFunction<A1, A2, A3, A4, R> getSource() {
			return theSource;
		}

		protected A1 getArg1() {
			return theArg1;
		}

		protected A2 getArg2() {
			return theArg2;
		}

		protected A4 getArg4() {
			return theArg4;
		}

		@Override
		public R apply(A3 arg3) {
			return theSource.apply(theArg1, theArg2, arg3, theArg4);
		}

		@Override
		public Supplier<R> curry(A3 arg) {
			return new QuadFnCurryAll<>(theSource, theArg1, theArg2, arg, theArg4);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 1, theArg1, theArg2, theArg4);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof QuadFnCurry124))
				return false;
			QuadFnCurry124<?, ?, ?, ?, ?> other = (QuadFnCurry124<?, ?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource())//
				&& Objects.equals(theArg1, other.theArg1) && Objects.equals(theArg2, other.theArg2)
				&& Objects.equals(theArg4, other.theArg4);
		}

		@Override
		public String toString() {
			return theSource + ".curry124(" + theArg1 + ", " + theArg2 + ", " + theArg4 + ")";
		}
	}

	/**
	 * A quaternary function with its first, third, and fourth arguments constant
	 * 
	 * @param <A1> The type of the first argument to the quaternary function
	 * @param <A2> The type of the second argument to the quaternary function
	 * @param <A3> The type of the third argument to the quaternary function
	 * @param <A4> The type of the fourth argument to the quaternary function
	 * @param <R> The return type of the function
	 */
	class QuadFnCurry134<A1, A2, A3, A4, R> implements BetterFunction<A2, R> {
		private final QuadFunction<A1, A2, A3, A4, R> theSource;
		private final A1 theArg1;
		private final A3 theArg3;
		private final A4 theArg4;

		QuadFnCurry134(QuadFunction<A1, A2, A3, A4, R> source, A1 arg1, A3 arg3, A4 arg4) {
			theSource = source;
			theArg1 = arg1;
			theArg3 = arg3;
			theArg4 = arg4;
		}

		protected QuadFunction<A1, A2, A3, A4, R> getSource() {
			return theSource;
		}

		protected A1 getArg1() {
			return theArg1;
		}

		protected A3 getArg3() {
			return theArg3;
		}

		protected A4 getArg4() {
			return theArg4;
		}

		@Override
		public R apply(A2 arg2) {
			return theSource.apply(theArg1, arg2, theArg3, theArg4);
		}

		@Override
		public Supplier<R> curry(A2 arg2) {
			return new QuadFnCurryAll<>(theSource, theArg1, arg2, theArg3, theArg4);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 1, theArg1, theArg3, theArg4);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof QuadFnCurry134))
				return false;
			QuadFnCurry134<?, ?, ?, ?, ?> other = (QuadFnCurry134<?, ?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource())//
				&& Objects.equals(theArg1, other.theArg1) && Objects.equals(theArg3, other.theArg3)
				&& Objects.equals(theArg4, other.theArg4);
		}

		@Override
		public String toString() {
			return theSource + ".curry134(" + theArg1 + ", " + theArg3 + ", " + theArg4 + ")";
		}
	}

	/**
	 * A quaternary function with its second, third, and fourth arguments constant
	 * 
	 * @param <A1> The type of the first argument to the quaternary function
	 * @param <A2> The type of the second argument to the quaternary function
	 * @param <A3> The type of the third argument to the quaternary function
	 * @param <A4> The type of the fourth argument to the quaternary function
	 * @param <R> The return type of the function
	 */
	class QuadFnCurry234<A1, A2, A3, A4, R> implements BetterFunction<A1, R> {
		private final QuadFunction<A1, A2, A3, A4, R> theSource;
		private final A2 theArg2;
		private final A3 theArg3;
		private final A4 theArg4;

		QuadFnCurry234(QuadFunction<A1, A2, A3, A4, R> source, A2 arg2, A3 arg3, A4 arg4) {
			theSource = source;
			theArg2 = arg2;
			theArg3 = arg3;
			theArg4 = arg4;
		}

		protected QuadFunction<A1, A2, A3, A4, R> getSource() {
			return theSource;
		}

		protected A2 getArg2() {
			return theArg2;
		}

		protected A3 getArg3() {
			return theArg3;
		}

		protected A4 getArg4() {
			return theArg4;
		}

		@Override
		public R apply(A1 arg1) {
			return theSource.apply(arg1, theArg2, theArg3, theArg4);
		}

		@Override
		public Supplier<R> curry(A1 arg1) {
			return new QuadFnCurryAll<>(theSource, arg1, theArg2, theArg3, theArg4);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 1, theArg2, theArg3, theArg4);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof QuadFnCurry234))
				return false;
			QuadFnCurry234<?, ?, ?, ?, ?> other = (QuadFnCurry234<?, ?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource())//
				&& Objects.equals(theArg2, other.theArg2) && Objects.equals(theArg3, other.theArg3)
				&& Objects.equals(theArg4, other.theArg4);
		}

		@Override
		public String toString() {
			return theSource + ".curry234(" + theArg2 + ", " + theArg3 + ", " + theArg4 + ")";
		}
	}

	/**
	 * A quaternary function with all arguments constant
	 * 
	 * @param <A1> The type of the first argument to the quaternary function
	 * @param <A2> The type of the second argument to the quaternary function
	 * @param <A3> The type of the third argument to the quaternary function
	 * @param <A4> The type of the fourth argument to the quaternary function
	 * @param <R> The return type of the function
	 */
	class QuadFnCurryAll<A1, A2, A3, A4, R> implements Supplier<R> {
		private final QuadFunction<A1, A2, A3, A4, R> theSource;
		private final A1 theArg1;
		private final A2 theArg2;
		private final A3 theArg3;
		private final A4 theArg4;

		QuadFnCurryAll(QuadFunction<A1, A2, A3, A4, R> source, A1 arg1, A2 arg2, A3 arg3, A4 arg4) {
			theSource = source;
			theArg1 = arg1;
			theArg2 = arg2;
			theArg3 = arg3;
			theArg4 = arg4;
		}

		@Override
		public R get() {
			return theSource.apply(theArg1, theArg2, theArg3, theArg4);
		}

		protected QuadFunction<A1, A2, A3, A4, R> getSource() {
			return theSource;
		}

		protected A1 getArg1() {
			return theArg1;
		}

		protected A2 getArg2() {
			return theArg2;
		}

		protected A3 getArg3() {
			return theArg3;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, theArg1, theArg2, theArg3, theArg4);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof QuadFnCurryAll))
				return false;
			QuadFnCurryAll<?, ?, ?, ?, ?> other = (QuadFnCurryAll<?, ?, ?, ?, ?>) obj;
			return theSource.equals(other.theSource) //
				&& Objects.equals(theArg1, other.theArg1) && Objects.equals(theArg2, other.theArg2)
				&& Objects.equals(theArg3, other.theArg3) && Objects.equals(theArg4, other.theArg4);
		}

		@Override
		public String toString() {
			return theSource + ".curryAll(" + theArg1 + ", " + theArg2 + ", " + theArg3 + ", " + theArg4 + ")";
		}
	}

	/**
	 * Implements {@link QuadFunction#andThen(Function)}
	 * 
	 * @param <A1> The first argument type
	 * @param <A2> The second argument type
	 * @param <A3> The third argument type
	 * @param <A4> The third argument type
	 * @param <R> The return type of the source function
	 * @param <X> The return type of the transformed function
	 */
	class AndThenQuadFn<A1, A2, A3, A4, R, X> implements QuadFunction<A1, A2, A3, A4, X> {
		private final QuadFunction<A1, A2, A3, A4, R> theSource;
		private final Function<? super R, ? extends X> theTransform;

		/**
		 * @param source The quaternary function to transform
		 * @param transform The transformation for the result of the source function
		 */
		public AndThenQuadFn(QuadFunction<A1, A2, A3, A4, R> source, Function<? super R, ? extends X> transform) {
			theSource = source;
			theTransform = transform;
		}

		@Override
		public X apply(A1 arg1, A2 arg2, A3 arg3, A4 arg4) {
			R sourceRes = theSource.apply(arg1, arg2, arg3, arg4);
			return theTransform.apply(sourceRes);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, theTransform);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			if (!(obj instanceof AndThenQuadFn))
				return false;
			return theSource.equals(((AndThenQuadFn<?, ?, ?, ?, ?, ?>) obj).theSource)
				&& theTransform.equals(((AndThenQuadFn<?, ?, ?, ?, ?, ?>) obj).theTransform);
		}

		@Override
		public String toString() {
			return theSource + ".andThen(" + theTransform + ")";
		}
	}

	/**
	 * Implements {@link QuadFunction#printable(QuadFunction, String, Object)}
	 * 
	 * @param <A1> The first argument type
	 * @param <A2> The second argument type
	 * @param <A3> The third argument type
	 * @param <A4> The fourth argument type
	 * @param <R> The return type of the function
	 */
	class PrintableQuadFn<A1, A2, A3, A4, R>
		extends FunctionUtils.PrintableLambda<QuadFunction<? super A1, ? super A2, ? super A3, ? super A4, ? extends R>>
		implements QuadFunction<A1, A2, A3, A4, R> {
		public PrintableQuadFn(QuadFunction<? super A1, ? super A2, ? super A3, ? super A4, ? extends R> lambda, Supplier<String> print,
			Object identifier) {
			super(lambda, print, identifier);
		}

		@Override
		public boolean isTrivial() {
			return false;
		}

		@Override
		public R apply(A1 arg1, A2 arg2, A3 arg3, A4 arg4) {
			return getLambda().apply(arg1, arg2, arg3, arg4);
		}
	}
}

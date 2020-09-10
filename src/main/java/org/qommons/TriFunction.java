package org.qommons;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * A function that operates on 3 arguments
 * 
 * @param <T> The first argument type
 * @param <U> The second argument type
 * @param <V> The third argument type
 * @param <R> The return type of the function
 */
@FunctionalInterface
public interface TriFunction<T, U, V, R> {
	/**
	 * @param arg1 The first argument
	 * @param arg2 The second argument
	 * @param arg3 The third argument
	 * @return The product of the function call
	 */
	R apply(T arg1, U arg2, V arg3);

	/**
	 * @param arg1 The first argument to this function
	 * @return A binary function that calls this ternary function with a constant first argument
	 */
	default BiFunction<U, V, R> curry1(T arg1) {
		return new TriFnCurry1<>(this, arg1);
	}

	/**
	 * @param arg2 The second argument to this function
	 * @return A binary function that calls this ternary function with a constant second argument
	 */
	default BiFunction<T, V, R> curry2(U arg2) {
		return new TriFnCurry2<>(this, arg2);
	}

	/**
	 * @param arg3 The third argument to this function
	 * @return A binary function that calls this ternary function with a constant third argument
	 */
	default BiFunction<T, U, R> curry3(V arg3) {
		return new TriFnCurry3<>(this, arg3);
	}

	/**
	 * Implements {@link TriFunction#curry1(Object)}
	 * 
	 * @param <T> The type of the first argument to the ternary function
	 * @param <U> The type of the second argument to the ternary function
	 * @param <V> The type of the second argument to the ternary function
	 * @param <R> The return type of the function
	 */
	class TriFnCurry1<T, U, V, R> implements BiFunction<U, V, R> {
		private final TriFunction<T, U, V, R> theSource;
		private final T theArg1;

		TriFnCurry1(TriFunction<T, U, V, R> source, T arg1) {
			theSource = source;
			theArg1 = arg1;
		}

		@Override
		public R apply(U arg2, V arg3) {
			return theSource.apply(theArg1, arg2, arg3);
		}

		TriFunction<T, U, V, R> getSource() {
			return theSource;
		}

		T getArg1() {
			return theArg1;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 1, theArg1);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof TriFnCurry1))
				return false;
			TriFnCurry1<?, ?, ?, ?> other = (TriFnCurry1<?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource()) && theArg1.equals(other.getArg1());
		}

		@Override
		public String toString() {
			return theSource + ".curry1(" + theArg1 + ")";
		}
	}

	/**
	 * Implements {@link TriFunction#curry2(Object)}
	 * 
	 * @param <T> The type of the first argument to the ternary function
	 * @param <U> The type of the second argument to the ternary function
	 * @param <V> The type of the second argument to the ternary function
	 * @param <R> The return type of the function
	 */
	class TriFnCurry2<T, U, V, R> implements BiFunction<T, V, R> {
		private final TriFunction<T, U, V, R> theSource;
		private final U theArg2;

		TriFnCurry2(TriFunction<T, U, V, R> source, U arg2) {
			theSource = source;
			theArg2 = arg2;
		}

		@Override
		public R apply(T arg1, V arg3) {
			return theSource.apply(arg1, theArg2, arg3);
		}

		TriFunction<T, U, V, R> getSource() {
			return theSource;
		}

		U getArg2() {
			return theArg2;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 2, theArg2);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof TriFnCurry2))
				return false;
			TriFnCurry2<?, ?, ?, ?> other = (TriFnCurry2<?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource()) && theArg2.equals(other.getArg2());
		}

		@Override
		public String toString() {
			return theSource + ".curry2(" + theArg2 + ")";
		}
	}

	/**
	 * Implements {@link TriFunction#curry3(Object)}
	 * 
	 * @param <T> The type of the first argument to the ternary function
	 * @param <U> The type of the second argument to the ternary function
	 * @param <V> The type of the second argument to the ternary function
	 * @param <R> The return type of the function
	 */
	class TriFnCurry3<T, U, V, R> implements BiFunction<T, U, R> {
		private final TriFunction<T, U, V, R> theSource;
		private final V theArg3;

		TriFnCurry3(TriFunction<T, U, V, R> source, V arg3) {
			theSource = source;
			theArg3 = arg3;
		}

		@Override
		public R apply(T arg1, U arg2) {
			return theSource.apply(arg1, arg2, theArg3);
		}

		TriFunction<T, U, V, R> getSource() {
			return theSource;
		}

		V getArg3() {
			return theArg3;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 3, theArg3);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof TriFnCurry3))
				return false;
			TriFnCurry3<?, ?, ?, ?> other = (TriFnCurry3<?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource()) && theArg3.equals(other.getArg3());
		}

		@Override
		public String toString() {
			return theSource + ".curry3(" + theArg3 + ")";
		}
	}
}

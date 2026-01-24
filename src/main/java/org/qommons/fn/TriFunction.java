package org.qommons.fn;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

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
	default BetterBiFunction<U, V, R> curry1(T arg1) {
		return new TriFnCurry1<>(this, arg1);
	}

	/**
	 * @param arg2 The second argument to this function
	 * @return A binary function that calls this ternary function with a constant second argument
	 */
	default BetterBiFunction<T, V, R> curry2(U arg2) {
		return new TriFnCurry2<>(this, arg2);
	}

	/**
	 * @param arg3 The third argument to this function
	 * @return A binary function that calls this ternary function with a constant third argument
	 */
	default BetterBiFunction<T, U, R> curry3(V arg3) {
		return new TriFnCurry3<>(this, arg3);
	}

	/**
	 * @param <X> The return type of the transformed function
	 * @param tx The transform function for results of this function
	 * @return A tri-function with the same arguments as this, but which transforms its end result with the given function
	 */
	default <X> TriFunction<T, U, V, X> andThen(Function<? super R, ? extends X> tx) {
		return new AndThenTriFn<>(this, tx);
	}

	/**
	 * Implements {@link TriFunction#curry1(Object)}
	 * 
	 * @param <T> The type of the first argument to the ternary function
	 * @param <U> The type of the second argument to the ternary function
	 * @param <V> The type of the second argument to the ternary function
	 * @param <R> The return type of the function
	 */
	class TriFnCurry1<T, U, V, R> implements BetterBiFunction<U, V, R> {
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

		@Override
		public BetterFunction<V, R> curry1(U arg1) {
			return new TriFnCurry12<>(theSource, theArg1, arg1);
		}

		@Override
		public BetterFunction<U, R> curry2(V arg2) {
			return new TriFnCurry13<>(theSource, theArg1, arg2);
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
			return getSource().equals(other.getSource()) && Objects.equals(theArg1, other.getArg1());
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
	class TriFnCurry2<T, U, V, R> implements BetterBiFunction<T, V, R> {
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

		@Override
		public BetterFunction<V, R> curry1(T arg1) {
			return new TriFnCurry12<>(theSource, arg1, theArg2);
		}

		@Override
		public BetterFunction<T, R> curry2(V arg2) {
			return new TriFnCurry23<>(theSource, theArg2, arg2);
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
			return getSource().equals(other.getSource()) && Objects.equals(theArg2, other.getArg2());
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
	class TriFnCurry3<T, U, V, R> implements BetterBiFunction<T, U, R> {
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

		@Override
		public BetterFunction<U, R> curry1(T arg1) {
			return new TriFnCurry13<>(theSource, arg1, theArg3);
		}

		@Override
		public BetterFunction<T, R> curry2(U arg2) {
			return new TriFnCurry23<>(theSource, arg2, theArg3);
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
			return getSource().equals(other.getSource()) && Objects.equals(theArg3, other.getArg3());
		}

		@Override
		public String toString() {
			return theSource + ".curry3(" + theArg3 + ")";
		}
	}

	/**
	 * A ternary function with its first two arguments constant
	 * 
	 * @param <T> The type of the first argument to the ternary function
	 * @param <U> The type of the second argument to the ternary function
	 * @param <V> The type of the second argument to the ternary function
	 * @param <R> The return type of the function
	 */
	class TriFnCurry12<T, U, V, R> implements BetterFunction<V, R> {
		private final TriFunction<T, U, V, R> theSource;
		private final T theArg1;
		private final U theArg2;

		TriFnCurry12(TriFunction<T, U, V, R> source, T arg1, U arg2) {
			theSource = source;
			theArg1 = arg1;
			theArg2 = arg2;
		}

		@Override
		public R apply(V arg3) {
			return theSource.apply(theArg1, theArg2, arg3);
		}

		@Override
		public Supplier<R> curry(V arg) {
			return new TriFnCurryAll<>(theSource, theArg1, theArg2, arg);
		}

		TriFunction<T, U, V, R> getSource() {
			return theSource;
		}

		T getArg1() {
			return theArg1;
		}

		U getArg2() {
			return theArg2;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, theArg1, theArg2);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof TriFnCurry12))
				return false;
			TriFnCurry12<?, ?, ?, ?> other = (TriFnCurry12<?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource())//
				&& Objects.equals(theArg1, other.theArg1) && Objects.equals(theArg2, other.theArg2);
		}

		@Override
		public String toString() {
			return theSource + ".curry12(" + theArg1 + ", " + theArg2 + ")";
		}
	}

	/**
	 * A ternary function with its first and third argument constant
	 * 
	 * @param <T> The type of the first argument to the ternary function
	 * @param <U> The type of the second argument to the ternary function
	 * @param <V> The type of the second argument to the ternary function
	 * @param <R> The return type of the function
	 */
	class TriFnCurry13<T, U, V, R> implements BetterFunction<U, R> {
		private final TriFunction<T, U, V, R> theSource;
		private final T theArg1;
		private final V theArg3;

		TriFnCurry13(TriFunction<T, U, V, R> source, T arg1, V arg3) {
			theSource = source;
			theArg1 = arg1;
			theArg3 = arg3;
		}

		@Override
		public R apply(U arg2) {
			return theSource.apply(theArg1, arg2, theArg3);
		}

		@Override
		public Supplier<R> curry(U arg) {
			return new TriFnCurryAll<>(theSource, theArg1, arg, theArg3);
		}

		TriFunction<T, U, V, R> getSource() {
			return theSource;
		}

		T getArg1() {
			return theArg1;
		}

		V getArg3() {
			return theArg3;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, theArg1, theArg3);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof TriFnCurry13))
				return false;
			TriFnCurry13<?, ?, ?, ?> other = (TriFnCurry13<?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource())//
				&& Objects.equals(theArg1, other.theArg1) && Objects.equals(theArg3, other.theArg3);
		}

		@Override
		public String toString() {
			return theSource + ".curry13(" + theArg1 + ", " + theArg3 + ")";
		}
	}

	/**
	 * A ternary function with its second and third argument constant
	 * 
	 * @param <T> The type of the first argument to the ternary function
	 * @param <U> The type of the second argument to the ternary function
	 * @param <V> The type of the second argument to the ternary function
	 * @param <R> The return type of the function
	 */
	class TriFnCurry23<T, U, V, R> implements BetterFunction<T, R> {
		private final TriFunction<T, U, V, R> theSource;
		private final U theArg2;
		private final V theArg3;

		TriFnCurry23(TriFunction<T, U, V, R> source, U arg2, V arg3) {
			theSource = source;
			theArg2 = arg2;
			theArg3 = arg3;
		}

		@Override
		public R apply(T arg1) {
			return theSource.apply(arg1, theArg2, theArg3);
		}

		@Override
		public Supplier<R> curry(T arg) {
			return new TriFnCurryAll<>(theSource, arg, theArg2, theArg3);
		}

		TriFunction<T, U, V, R> getSource() {
			return theSource;
		}

		U getArg2() {
			return theArg2;
		}

		V getArg3() {
			return theArg3;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 6, theArg2, theArg3);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof TriFnCurry23))
				return false;
			TriFnCurry23<?, ?, ?, ?> other = (TriFnCurry23<?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource())//
				&& Objects.equals(theArg2, other.getArg2()) && Objects.equals(theArg3, other.getArg3());
		}

		@Override
		public String toString() {
			return theSource + ".curry23(" + theArg2 + ", " + theArg3 + ")";
		}
	}

	/**
	 * A ternary function with all arguments constant
	 * 
	 * @param <T> The type of the first argument to the ternary function
	 * @param <U> The type of the second argument to the ternary function
	 * @param <V> The type of the third argument to the ternary function
	 * @param <R> The return type of the function
	 */
	class TriFnCurryAll<T, U, V, R> implements Supplier<R> {
		private final TriFunction<T, U, V, R> theSource;
		private final T theArg1;
		private final U theArg2;
		private final V theArg3;

		TriFnCurryAll(TriFunction<T, U, V, R> source, T arg1, U arg2, V arg3) {
			theSource = source;
			theArg1 = arg1;
			theArg2 = arg2;
			theArg3 = arg3;
		}

		@Override
		public R get() {
			return theSource.apply(theArg1, theArg2, theArg3);
		}

		protected TriFunction<T, U, V, R> getSource() {
			return theSource;
		}

		protected T getArg1() {
			return theArg1;
		}

		protected U getArg2() {
			return theArg2;
		}

		protected V getArg3() {
			return theArg3;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, theArg1, theArg2, theArg3);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof TriFnCurryAll))
				return false;
			TriFnCurryAll<?, ?, ?, ?> other = (TriFnCurryAll<?, ?, ?, ?>) obj;
			return getSource().equals(other.getSource()) //
				&& Objects.equals(theArg1, other.theArg2) && Objects.equals(theArg2, other.theArg2)
				&& Objects.equals(theArg3, other.theArg3);
		}

		@Override
		public String toString() {
			return theSource + ".curryAll(" + theArg1 + ", " + theArg2 + ", " + theArg3 + ")";
		}
	}

	/**
	 * Implements {@link TriFunction#andThen(Function)}
	 * 
	 * @param <T> The first argument type
	 * @param <U> The second argument type
	 * @param <V> The third argument type
	 * @param <R> The return type of the source function
	 * @param <X> The return type of the transformed function
	 */
	class AndThenTriFn<T, U, V, R, X> implements TriFunction<T, U, V, X> {
		private final TriFunction<T, U, V, R> theSource;
		private final Function<? super R, ? extends X> theTransform;

		/**
		 * @param source The tri-function to transform
		 * @param transform The transformation for the result of the source function
		 */
		public AndThenTriFn(TriFunction<T, U, V, R> source, Function<? super R, ? extends X> transform) {
			theSource = source;
			theTransform = transform;
		}

		@Override
		public X apply(T arg1, U arg2, V arg3) {
			R sourceRes = theSource.apply(arg1, arg2, arg3);
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
			if (!(obj instanceof AndThenTriFn))
				return false;
			return theSource.equals(((AndThenTriFn<?, ?, ?, ?, ?>) obj).theSource)
				&& theTransform.equals(((AndThenTriFn<?, ?, ?, ?, ?>) obj).theTransform);
		}

		@Override
		public String toString() {
			return theSource + ".andThen(" + theTransform + ")";
		}
	}
}

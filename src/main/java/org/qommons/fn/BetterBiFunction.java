package org.qommons.fn;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An extension of {@link BiFunction} that provides curry functions
 * 
 * @param <A1> The first argument type
 * @param <A2> The second argument type
 * @param <R> The return type of the function
 */
public interface BetterBiFunction<A1, A2, R> extends BiFunction<A1, A2, R> {
	/**
	 * @param arg1 The first argument for this function
	 * @return A unary function that calls this binary function with the given constant first argument
	 */
	default BetterFunction<A2, R> curry1(A1 arg1) {
		return new BiFnCurry1<>(this, arg1);
	}

	/**
	 * @param arg2 The second argument for this function
	 * @return A unary function that calls this binary function with the given constant second argument
	 */
	default BetterFunction<A1, R> curry2(A2 arg2) {
		return new BiFnCurry2<>(this, arg2);
	}

	@Override
	default <X> BetterBiFunction<A1, A2, X> andThen(Function<? super R, ? extends X> transform) {
		return new AndThenBiFn<>(this, transform);
	}

	/**
	 * Implements {@link BetterBiFunction#curry1(Object)}
	 * 
	 * @param <A1> The first argument type of the binary function
	 * @param <A2> The second argument type of the binary function
	 * @param <R> The return type of the function
	 */
	class BiFnCurry1<A1, A2, R> implements BetterFunction<A2, R> {
		private final BiFunction<? super A1, ? super A2, ? extends R> theSource;
		private final A1 theArg1;

		public BiFnCurry1(BiFunction<? super A1, ? super A2, ? extends R> source, A1 arg1) {
			theSource = source;
			theArg1 = arg1;
		}

		protected BiFunction<? super A1, ? super A2, ? extends R> getSource() {
			return theSource;
		}

		protected A1 getArg1() {
			return theArg1;
		}

		@Override
		public R apply(A2 t) {
			return theSource.apply(theArg1, t);
		}

		@Override
		public Supplier<R> curry(A2 arg2) {
			return new BiFnCurryAll<>(theSource, theArg1, arg2);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, theArg1);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof BiFnCurry1))
				return false;
			BiFnCurry1<?, ?, ?> other = (BiFnCurry1<?, ?, ?>) obj;
			return theSource.equals(other.theSource) && Objects.equals(theArg1, other.theArg1);
		}

		@Override
		public String toString() {
			return theSource + ".curry1(" + theArg1 + ")";
		}
	}

	/**
	 * Implements {@link BetterBiFunction#curry2(Object)}
	 * 
	 * @param <A1> The first argument type of the binary function
	 * @param <A2> The second argument type of the binary function
	 * @param <R> The return type of the function
	 */
	class BiFnCurry2<A1, A2, R> implements BetterFunction<A1, R> {
		private final BiFunction<? super A1, ? super A2, ? extends R> theSource;
		private final A2 theArg2;

		public BiFnCurry2(BiFunction<? super A1, ? super A2, ? extends R> source, A2 arg2) {
			theSource = source;
			theArg2 = arg2;
		}

		protected BiFunction<? super A1, ? super A2, ? extends R> getSource() {
			return theSource;
		}

		protected A2 getArg2() {
			return theArg2;
		}

		@Override
		public R apply(A1 t) {
			return theSource.apply(t, theArg2);
		}

		@Override
		public Supplier<R> curry(A1 arg1) {
			return new BiFnCurryAll<>(theSource, arg1, theArg2);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, theArg2);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof BiFnCurry2))
				return false;
			BiFnCurry2<?, ?, ?> other = (BiFnCurry2<?, ?, ?>) obj;
			return theSource.equals(other.theSource) && Objects.equals(theArg2, other.theArg2);
		}

		@Override
		public String toString() {
			return theSource + ".curry2(" + theArg2 + ")";
		}
	}

	/**
	 * A binary function with both its arguments constant
	 * 
	 * @param <A1> The first argument type of the binary function
	 * @param <A2> The second argument type of the binary function
	 * @param <R> The return type of the function
	 */
	class BiFnCurryAll<A1, A2, R> implements Supplier<R> {
		private final BiFunction<? super A1, ? super A2, ? extends R> theSource;
		private final A1 theArg1;
		private final A2 theArg2;

		public BiFnCurryAll(BiFunction<? super A1, ? super A2, ? extends R> source, A1 arg1, A2 arg2) {
			theSource = source;
			theArg1 = arg1;
			theArg2 = arg2;
		}

		protected BiFunction<? super A1, ? super A2, ? extends R> getSource() {
			return theSource;
		}

		protected A1 getArg1() {
			return theArg1;
		}

		protected A2 getArg2() {
			return theArg2;
		}

		@Override
		public R get() {
			return theSource.apply(theArg1, theArg2);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, theArg1, theArg2);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof BiFnCurryAll))
				return false;
			BiFnCurryAll<?, ?, ?> other = (BiFnCurryAll<?, ?, ?>) obj;
			return theSource.equals(other.theSource) && Objects.equals(theArg1, other.theArg1) && Objects.equals(theArg2, other.theArg2);
		}

		@Override
		public String toString() {
			return theSource + ".curryAll(" + theArg1 + ", " + theArg2 + ")";
		}
	}

	/**
	 * Implements {@link BetterBiFunction#andThen(Function)}
	 * 
	 * @param <A1> The first argument type
	 * @param <A2> The second argument type
	 * @param <R> The return type of the source function
	 * @param <X> The return type of the transformed function
	 */
	class AndThenBiFn<A1, A2, R, X> implements BetterBiFunction<A1, A2, X> {
		private final BiFunction<? super A1, ? super A2, ? extends R> theSource;
		private final Function<? super R, ? extends X> theTransform;

		public AndThenBiFn(BiFunction<? super A1, ? super A2, ? extends R> source, Function<? super R, ? extends X> transform) {
			theSource = source;
			theTransform = transform;
		}

		@Override
		public X apply(A1 t, A2 u) {
			R intermediate = theSource.apply(t, u);
			return theTransform.apply(intermediate);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, theTransform);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof AndThenBiFn))
				return false;
			AndThenBiFn<?, ?, ?, ?> other = (AndThenBiFn<?, ?, ?, ?>) obj;
			return theSource.equals(other.theSource) && theTransform.equals(other.theTransform);
		}

		@Override
		public String toString() {
			return theSource + ".andThen(" + theTransform + ")";
		}
	}
}

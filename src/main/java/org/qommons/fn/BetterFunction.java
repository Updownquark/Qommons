package org.qommons.fn;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Extension of {@link Function} that provides a curry function
 * 
 * @param <A> The type of the argument to the function
 * @param <R> The return type of the function
 */
public interface BetterFunction<A, R> extends Function<A, R> {
	/**
	 * @param arg The argument for this function
	 * @return A supplier that calls this function with the given constant argument
	 */
	default Supplier<R> curry(A arg) {
		return new CurriedFn<>(this, arg);
	}

	@Override
	default <X> BetterFunction<A, X> andThen(Function<? super R, ? extends X> transform) {
		return new AndThenFn<>(this, transform);
	}

	/**
	 * Implements {@link BetterFunction#curry(Object)}
	 * 
	 * @param <A> The function's argument type
	 * @param <R> The return type of the function
	 */
	class CurriedFn<A, R> implements Supplier<R> {
		private final Function<A, R> theSource;
		private final A theArg;

		public CurriedFn(Function<A, R> source, A arg) {
			theSource = source;
			theArg = arg;
		}

		@Override
		public R get() {
			return theSource.apply(theArg);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, theArg);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof CurriedFn))
				return false;
			CurriedFn<?, ?> other = (CurriedFn<?, ?>) obj;
			return theSource.equals(other.theSource) && Objects.equals(theArg, other.theArg);
		}

		@Override
		public String toString() {
			return theSource + ".curry(" + theArg + ")";
		}
	}

	/**
	 * Implements {@link BetterFunction#andThen(Function)}
	 * 
	 * @param <A> The argument type
	 * @param <R> The return type of the source function
	 * @param <X> The return type of the transformed function
	 */
	class AndThenFn<A, R, X> implements BetterFunction<A, X> {
		private final Function<? super A, ? extends R> theSource;
		private final Function<? super R, ? extends X> theTransform;

		public AndThenFn(Function<? super A, ? extends R> source, Function<? super R, ? extends X> transform) {
			theSource = source;
			theTransform = transform;
		}

		@Override
		public X apply(A t) {
			R intermediate = theSource.apply(t);
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
			else if (!(obj instanceof AndThenFn))
				return false;
			AndThenFn<?, ?, ?> other = (AndThenFn<?, ?, ?>) obj;
			return theSource.equals(other.theSource) && theTransform.equals(other.theTransform);
		}

		@Override
		public String toString() {
			return theSource + ".andThen(" + theTransform + ")";
		}
	}
}

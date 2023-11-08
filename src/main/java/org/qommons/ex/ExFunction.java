package org.qommons.ex;

import java.util.Objects;
import java.util.function.Function;

import org.qommons.LambdaUtils;

/**
 * A {@link Function} look-alike that can throw a checked exception
 * 
 * @param <F> The argument type
 * @param <T> The return type
 * @param <E> The throwable type
 */
@FunctionalInterface
public interface ExFunction<F, T, E extends Throwable> {
	/** The identity function */
	public static final ExFunction<Object, Object, RuntimeException> IDENTITY = v -> v;

	/**
	 * @param value The argument
	 * @return The return value
	 * @throws E An exception
	 */
	T apply(F value) throws E;

	/** @return A {@link Function} that calls this function, wrapping any thrown checked exception with a {@link CheckedExceptionWrapper} */
	default Function<F, T> unsafe() {
		return value -> {
			try {
				return ExFunction.this.apply(value);
			} catch (RuntimeException | Error e) {
				throw e;
			} catch(Throwable e) {
				throw new CheckedExceptionWrapper(e);
			}
		};
	}

	default <X> ExFunction<F, X, E> andThen(ExFunction<? super T, X, ? extends E> next) {
		return new CombinedExFn<>(this, next);
	}

	/**
	 * @param <F> The argument type
	 * @param <T> The return type
	 * @param <E> The throwable type
	 * @param f The function to wrap
	 * @return An {@link ExFunction} that calls the given function and does not actually throw any checked exceptions
	 */
	static <F, T, E extends Throwable> ExFunction<F, T, E> of(Function<F, T> f) {
		if (f == null)
			return null;
		if (LambdaUtils.getIdentifier(f) == LambdaUtils.IDENTITY)
			return (ExFunction<F, T, E>) IDENTITY;
		return value -> f.apply(value);
	}

	/**
	 * @param <F> The type of the value to accept and return
	 * @return The identity function
	 */
	static <F> ExFunction<F, F, RuntimeException> identity() {
		return (ExFunction<F, F, RuntimeException>) IDENTITY;
	}

	static class CombinedExFn<S, I, T, E extends Throwable> implements ExFunction<S, T, E> {
		private final ExFunction<S, I, ? extends E> theFirst;
		private final ExFunction<? super I, T, ? extends E> theSecond;

		public CombinedExFn(ExFunction<S, I, ? extends E> first, ExFunction<? super I, T, ? extends E> second) {
			theFirst = first;
			theSecond = second;
		}

		@Override
		public T apply(S value) throws E {
			I intermediate = theFirst.apply(value);
			return theSecond.apply(intermediate);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theFirst, theSecond);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof CombinedExFn))
				return false;
			CombinedExFn<?, ?, ?, ?> other = (CombinedExFn<?, ?, ?, ?>) obj;
			return theFirst.equals(other.theFirst) && theSecond.equals(other.theSecond);
		}

		@Override
		public String toString() {
			return theFirst + "->" + theSecond;
		}
	}
}

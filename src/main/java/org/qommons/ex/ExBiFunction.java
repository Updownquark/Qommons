package org.qommons.ex;

import java.util.function.BiFunction;

/**
 * A {@link BiFunction} look-alike that is capable of throwing a checked exception
 * 
 * @param <T> The first argument type
 * @param <U> The second argument type
 * @param <R> The return type
 * @param <E> The throwable type
 */
@FunctionalInterface
public interface ExBiFunction<T, U, R, E extends Throwable> {
	/**
	 * @param t The first argument
	 * @param u The second argument
	 * @return The return value
	 * @throws E An exception
	 */
	R apply(T t, U u) throws E;

	/**
	 * @return A {@link BiFunction} that calls this bi function, wrapping any thrown checked exception with a
	 *         {@link CheckedExceptionWrapper}
	 */
	default BiFunction<T, U, R> unsafe() {
		return (arg1, arg2) -> {
			try {
				return ExBiFunction.this.apply(arg1, arg2);
			} catch (RuntimeException | Error e) {
				throw e;
			} catch (Throwable e) {
				throw new CheckedExceptionWrapper(e);
			}
		};
	}

	/**
	 * @param <T> The first argument type
	 * @param <U> The second argument type
	 * @param <R> The return type
	 * @param <E> The throwable type
	 * @param f The function to wrap
	 * @return an {@link ExBiFunction} that calls the given function and never actually throws a checked exception
	 */
	static <T, U, R, E extends Throwable> ExBiFunction<T, U, R, E> of(BiFunction<T, U, R> f) {
		return (arg1, arg2) -> f.apply(arg1, arg2);
	}

	static <T, U, R, E extends Throwable> ExBiFunction<T, U, R, E> ofF11(ExFunction<T, R, E> singleFn) {
		return new UnaryToBiFn1<>(singleFn);
	}

	static <T, U, R, E extends Throwable> ExBiFunction<T, U, R, E> ofF12(ExFunction<U, R, E> singleFn) {
		return new UnaryToBiFn2<>(singleFn);
	}

	class UnaryToBiFn1<T, U, R, E extends Throwable> implements ExBiFunction<T, U, R, E> {
		private final ExFunction<T, R, E> theBacking;

		public UnaryToBiFn1(ExFunction<T, R, E> backing) {
			theBacking = backing;
		}

		@Override
		public R apply(T t, U u) throws E {
			return theBacking.apply(t);
		}

		@Override
		public int hashCode() {
			return theBacking.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else
				return obj instanceof UnaryToBiFn1 && theBacking.equals(((UnaryToBiFn1<?, ?, ?, ?>) obj).theBacking);
		}

		@Override
		public String toString() {
			return theBacking.toString();
		}
	}

	class UnaryToBiFn2<T, U, R, E extends Throwable> implements ExBiFunction<T, U, R, E> {
		private final ExFunction<U, R, E> theBacking;

		public UnaryToBiFn2(ExFunction<U, R, E> backing) {
			theBacking = backing;
		}

		@Override
		public R apply(T t, U u) throws E {
			return theBacking.apply(u);
		}

		@Override
		public int hashCode() {
			return theBacking.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else
				return obj instanceof UnaryToBiFn1 && theBacking.equals(((UnaryToBiFn1<?, ?, ?, ?>) obj).theBacking);
		}

		@Override
		public String toString() {
			return theBacking.toString();
		}
	}
}

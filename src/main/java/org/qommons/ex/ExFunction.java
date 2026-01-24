package org.qommons.ex;

import java.util.Objects;
import java.util.function.Function;

import org.qommons.fn.BetterFunction;
import org.qommons.fn.FunctionUtils;

/**
 * A {@link Function} look-alike that can throw a checked exception
 * 
 * @param <T> The argument type
 * @param <R> The return type
 * @param <X> The throwable type
 */
@FunctionalInterface
public interface ExFunction<T, R, X extends Throwable> {
	/** The identity function */
	public static final ExFunction<Object, Object, RuntimeException> IDENTITY = v -> v;

	/**
	 * @param value The argument
	 * @return The return value
	 * @throws X An exception
	 */
	R apply(T value) throws X;

	/** @return A {@link Function} that calls this function, wrapping any thrown checked exception with a {@link CheckedExceptionWrapper} */
	default BetterFunction<T, R> unsafe() {
		return new Unsafe<>(this);
	}

	/**
	 * @param <U> The target type of the new function
	 * @param next The function to apply after this one
	 * @return A function that is the result of this function applied to a value, followed by <code>next</code>
	 */
	default <U> ExFunction<T, U, X> andThen(ExFunction<? super R, U, ? extends X> next) {
		return new CombinedExFn<>(this, next);
	}

	/**
	 * @param arg The argument to pass to the function
	 * @return This function as a supplier with the given constant argument
	 */
	default ExSupplier<R, X> curry(T arg) {
		return curry(FunctionUtils.constantExSupplier(arg));
	}

	/**
	 * @param arg Creates the argument to pass to the function
	 * @return This function as a supplier with the given argument supplier
	 */
	default ExSupplier<R, X> curry(ExSupplier<? extends T, ? extends X> arg) {
		return new ArgCurried<>(this, arg);
	}

	/**
	 * @param test The test for this function's return value
	 * @return This function as a predicate with the given test
	 */
	default ExPredicate<T, X> asPredicate(ExPredicate<? super R, ? extends X> test) {
		return new AsPredicate<>(this, test);
	}

	/**
	 * @param consume An optional consumer for this function's return value
	 * @return This function as a consumer
	 */
	default ExConsumer<T, X> asConsumer(ExConsumer<? super R, ? extends X> consume) {
		return new AsConsumer<>(this, consume);
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
		return FunctionUtils.printableExFn(f::apply, f::toString, f);
	}

	/**
	 * @param <F> The type of the value to accept and return
	 * @return The identity function
	 */
	static <F> ExFunction<F, F, RuntimeException> identity() {
		return (ExFunction<F, F, RuntimeException>) IDENTITY;
	}

	/**
	 * Implements {@link ExFunction#unsafe()}
	 * 
	 * @param <T> The argument type
	 * @param <R> The return type
	 * @param <X> The throwable type
	 */
	class Unsafe<T, R, X extends Throwable> implements BetterFunction<T, R> {
		private ExFunction<T, R, X> theFunction;

		public Unsafe(ExFunction<T, R, X> function) {
			theFunction = function;
		}

		@Override
		public R apply(T value) {
			try {
				return theFunction.apply(value);
			} catch (RuntimeException | Error e) {
				throw e;
			} catch (Throwable e) {
				throw new CheckedExceptionWrapper(e);
			}
		}

		@Override
		public int hashCode() {
			return theFunction.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else
				return obj instanceof Unsafe && theFunction.equals(((Unsafe<?, ?, ?>) obj).theFunction);
		}

		@Override
		public String toString() {
			return theFunction.toString();
		}
	}

	/**
	 * Implements {@link ExFunction#andThen(ExFunction)}
	 * 
	 * @param <S> The source type of the primary function
	 * @param <I> The target type of the primary function
	 * @param <T> The target type of the secondary function
	 * @param <E> The exception type thrown by this function
	 */
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

	/**
	 * Implements {@link ExFunction#curry(ExSupplier)}
	 * 
	 * @param <T> The type of the function argument
	 * @param <R> The return type of the function
	 * @param <X> The type of exception throwable by the function
	 */
	class ArgCurried<T, R, X extends Throwable> implements ExSupplier<R, X> {
		private final ExFunction<T, R, X> theFunction;
		private final ExSupplier<? extends T, ? extends X> theArg;

		public ArgCurried(ExFunction<T, R, X> function, ExSupplier<? extends T, ? extends X> arg) {
			theFunction = function;
			theArg = arg;
		}

		@Override
		public R get() throws X {
			T arg = theArg.get();
			return theFunction.apply(arg);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theFunction, theArg);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof ArgCurried))
				return false;
			ArgCurried<?, ?, ?> other = (ArgCurried<?, ?, ?>) obj;
			return theFunction.equals(other.theFunction) && theArg.equals(other.theArg);
		}

		@Override
		public String toString() {
			return theFunction + ".curry(" + theArg + ")";
		}
	}

	/**
	 * Implements {@link ExFunction#asPredicate(ExPredicate)}
	 * 
	 * @param <T> The type of the function argument
	 * @param <R> The return type of the function
	 * @param <X> The type of exception throwable by the function
	 */
	class AsPredicate<T, R, X extends Throwable> implements ExPredicate<T, X> {
		private final ExFunction<T, R, X> theFunction;
		private final ExPredicate<? super R, ? extends X> theTest;

		public AsPredicate(ExFunction<T, R, X> function, ExPredicate<? super R, ? extends X> test) {
			theFunction = function;
			theTest = test;
		}

		@Override
		public boolean test(T arg) throws X {
			R value = theFunction.apply(arg);
			return theTest.test(value);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theFunction, theTest);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof AsPredicate))
				return false;
			AsPredicate<?, ?, ?> other = (AsPredicate<?, ?, ?>) obj;
			return theFunction.equals(other.theFunction) && theTest.equals(other.theTest);
		}

		@Override
		public String toString() {
			return theFunction + ".if(" + theTest + ")";
		}
	}

	/**
	 * Implements {@link ExFunction#asConsumer(ExConsumer)}
	 * 
	 * @param <T> The type of the function argument
	 * @param <R> The return type of the function
	 * @param <X> The type of exception throwable by the function
	 */
	class AsConsumer<T, R, X extends Throwable> implements ExConsumer<T, X> {
		private final ExFunction<T, R, X> theFunction;
		private final ExConsumer<? super R, ? extends X> theConsumer;

		public AsConsumer(ExFunction<T, R, X> function, ExConsumer<? super R, ? extends X> consumer) {
			theFunction = function;
			if (consumer != null)
				theConsumer = consumer;
			else
				theConsumer = FunctionUtils.exConsumeDoNothing();
		}

		@Override
		public void accept(T arg) throws X {
			R value = theFunction.apply(arg);
			theConsumer.accept(value);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theFunction, theConsumer);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof AsPredicate))
				return false;
			AsConsumer<?, ?, ?> other = (AsConsumer<?, ?, ?>) obj;
			return theFunction.equals(other.theFunction) && theConsumer.equals(other.theConsumer);
		}

		@Override
		public String toString() {
			return theFunction + ".consume(" + theConsumer + ")";
		}
	}
}

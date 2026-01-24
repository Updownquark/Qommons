package org.qommons.ex;

import java.util.Objects;
import java.util.function.BiFunction;

import org.qommons.fn.BetterBiFunction;
import org.qommons.fn.FunctionUtils;

/**
 * A {@link BiFunction} look-alike that is capable of throwing a checked exception
 * 
 * @param <T> The first argument type
 * @param <U> The second argument type
 * @param <R> The return type
 * @param <X> The throwable type
 */
@FunctionalInterface
public interface ExBiFunction<T, U, R, X extends Throwable> {
	/**
	 * @param t The first argument
	 * @param u The second argument
	 * @return The return value
	 * @throws X An exception
	 */
	R apply(T t, U u) throws X;

	/**
	 * @return A {@link BiFunction} that calls this bi function, wrapping any thrown checked exception with a
	 *         {@link CheckedExceptionWrapper}
	 */
	default BetterBiFunction<T, U, R> unsafe() {
		return new Unsafe<>(this);
	}

	/**
	 * @param arg1 The argument to pass to this function as the first argument whenever the new function is called
	 * @return The curried unary function
	 */
	default ExFunction<U, R, X> curry1(T arg1) {
		return curry1(FunctionUtils.constantExSupplier(arg1));
	}

	/**
	 * @param arg1 Creator for the argument to pass to this function as the first argument whenever the new function is called
	 * @return The curried unary function
	 */
	default ExFunction<U, R, X> curry1(ExSupplier<? extends T, ? extends X> arg1) {
		return new Arg1Curried<>(this, arg1);
	}

	/**
	 * @param arg2 The argument to pass to this function as the second argument whenever the new function is called
	 * @return The curried unary function
	 */
	default ExFunction<T, R, X> curry2(U arg2) {
		return curry2(FunctionUtils.constantExSupplier(arg2));
	}

	/**
	 * @param arg2 Creator for the argument to pass to this function as the second argument whenever the new function is called
	 * @return The curried unary function
	 */
	default ExFunction<T, R, X> curry2(ExSupplier<? extends U, ? extends X> arg2) {
		return new Arg2Curried<>(this, arg2);
	}

	/**
	 * @param test The test for this function's return value
	 * @return This function as a predicate with the given test
	 */
	default ExBiPredicate<T, U, X> asPredicate(ExPredicate<? super R, ? extends X> test) {
		return new AsPredicate<>(this, test);
	}

	/**
	 * @param consumer An optional consumer to do something with this function's return value
	 * @return This function as a consumer
	 */
	default ExBiConsumer<T, U, X> asConsumer(ExConsumer<? super R, ? extends X> consumer) {
		return new AsConsumer<>(this, consumer);
	}

	/**
	 * @param <T> The first argument type
	 * @param <U> The second argument type
	 * @param <R> The return type
	 * @param <X> The throwable type
	 * @param f The function to wrap
	 * @return an {@link ExBiFunction} that calls the given function and never actually throws a checked exception
	 */
	static <T, U, R, X extends Throwable> ExBiFunction<T, U, R, X> of(BiFunction<T, U, R> f) {
		return f::apply;
	}

	/**
	 * Makes a binary function of a unary one, passing the first argument to the unary function
	 * 
	 * @param <T> The type of the first argument to the new binary function (passed to the argument function)
	 * @param <U> The type of the second argument to the new binary function (unused)
	 * @param <R> The return type of the function
	 * @param <X> The type of exception thrown by the function
	 * @param singleFn The function to call with the first argument to the new binary function
	 * @return The binary function
	 */
	static <T, U, R, X extends Throwable> ExBiFunction<T, U, R, X> ofF11(ExFunction<T, R, X> singleFn) {
		return new UnaryToBiFn1<>(singleFn);
	}

	/**
	 * Makes a binary function of a unary one, passing the second argument to the unary function
	 * 
	 * @param <T> The type of the first argument to the new binary function (unused)
	 * @param <U> The type of the second argument to the new binary function (passed to the argument function)
	 * @param <R> The return type of the function
	 * @param <X> The type of exception thrown by the function
	 * @param singleFn The function to call with the second argument to the new binary function
	 * @return The binary function
	 */
	static <T, U, R, X extends Throwable> ExBiFunction<T, U, R, X> ofF12(ExFunction<U, R, X> singleFn) {
		return new UnaryToBiFn2<>(singleFn);
	}

	/**
	 * Implements {@link ExBiFunction#unsafe()}
	 * 
	 * @param <T> The type of the first argument to the new binary function (unused)
	 * @param <U> The type of the second argument to the new binary function (passed to the argument function)
	 * @param <R> The return type of the function
	 * @param <X> The type of exception thrown by the exception-enabled function
	 */
	class Unsafe<T, U, R, X extends Throwable> implements BetterBiFunction<T, U, R> {
		private final ExBiFunction<T, U, R, X> theFunction;

		public Unsafe(ExBiFunction<T, U, R, X> function) {
			theFunction = function;
		}

		@Override
		public R apply(T arg1, U arg2) {
			try {
				return theFunction.apply(arg1, arg2);
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
			else if (obj instanceof Unsafe)
				return theFunction.equals(((Unsafe<?, ?, ?, ?>) obj).theFunction);
			else
				return false;
		}

		@Override
		public String toString() {
			return theFunction.toString();
		}
	}

	/**
	 * Implements {@link ExBiFunction#ofF11(ExFunction)}
	 * 
	 * @param <T> The type of the first argument to the new binary function (passed to the argument function)
	 * @param <U> The type of the second argument to the new binary function (unused)
	 * @param <R> The return type of the function
	 * @param <X> The type of exception thrown by the function
	 */
	class UnaryToBiFn1<T, U, R, X extends Throwable> implements ExBiFunction<T, U, R, X> {
		private final ExFunction<T, R, X> theBacking;

		public UnaryToBiFn1(ExFunction<T, R, X> backing) {
			theBacking = backing;
		}

		@Override
		public R apply(T t, U u) throws X {
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

	/**
	 * Implements {@link ExBiFunction#ofF12(ExFunction)}
	 * 
	 * @param <T> The type of the first argument to the new binary function (unused)
	 * @param <U> The type of the second argument to the new binary function (passed to the argument function)
	 * @param <R> The return type of the function
	 * @param <X> The type of exception thrown by the function
	 */
	class UnaryToBiFn2<T, U, R, X extends Throwable> implements ExBiFunction<T, U, R, X> {
		private final ExFunction<U, R, X> theBacking;

		public UnaryToBiFn2(ExFunction<U, R, X> backing) {
			theBacking = backing;
		}

		@Override
		public R apply(T t, U u) throws X {
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

	/**
	 * Implements {@link ExBiFunction#curry1(ExSupplier)}
	 * 
	 * @param <T> The first argument type of the binary function (supplied by the curried supplier)
	 * @param <U> The second argument type of the binary function (passed through from the argument to the function)
	 * @param <R> The return type of the function
	 * @param <X> The exception type thrown by the function
	 */
	class Arg1Curried<T, U, R, X extends Throwable> implements ExFunction<U, R, X> {
		private final ExBiFunction<T, U, R, X> theFunction;
		private final ExSupplier<? extends T, ? extends X> theArg1;

		public Arg1Curried(ExBiFunction<T, U, R, X> function, ExSupplier<? extends T, ? extends X> arg1) {
			theFunction = function;
			theArg1 = arg1;
		}

		@Override
		public R apply(U value) throws X {
			T arg1 = theArg1.get();
			return theFunction.apply(arg1, value);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theFunction, theArg1);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof Arg1Curried))
				return false;
			Arg1Curried<?, ?, ?, ?> other = (Arg1Curried<?, ?, ?, ?>) obj;
			return theFunction.equals(other.theFunction) && theArg1.equals(other.theArg1);
		}

		@Override
		public String toString() {
			return theFunction + ".curry1(" + theArg1 + ")";
		}
	}

	/**
	 * Implements {@link ExBiFunction#curry2(ExSupplier)}
	 * 
	 * @param <T> The first argument type of the binary function (passed through from the argument to the function)
	 * @param <U> The second argument type of the binary function (supplied by the curried supplier)
	 * @param <R> The return type of the function
	 * @param <X> The exception type thrown by the function
	 */
	class Arg2Curried<T, U, R, X extends Throwable> implements ExFunction<T, R, X> {
		private final ExBiFunction<T, U, R, X> theFunction;
		private final ExSupplier<? extends U, ? extends X> theArg2;

		public Arg2Curried(ExBiFunction<T, U, R, X> function, ExSupplier<? extends U, ? extends X> arg12) {
			theFunction = function;
			theArg2 = arg12;
		}

		@Override
		public R apply(T value) throws X {
			U arg2 = theArg2.get();
			return theFunction.apply(value, arg2);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theFunction, theArg2);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof Arg2Curried))
				return false;
			Arg2Curried<?, ?, ?, ?> other = (Arg2Curried<?, ?, ?, ?>) obj;
			return theFunction.equals(other.theFunction) && theArg2.equals(other.theArg2);
		}

		@Override
		public String toString() {
			return theFunction + ".curry2(" + theArg2 + ")";
		}
	}

	/**
	 * Implements {@link ExBiFunction#asPredicate(ExPredicate)}
	 * 
	 * @param <T> The type of the first argument the the predicate/function
	 * @param <U> The type of the second argument to the predicate/function
	 * @param <R> The type of value returned by the function
	 * @param <X> The type of exception throwable by the predicate
	 */
	class AsPredicate<T, U, R, X extends Throwable> implements ExBiPredicate<T, U, X> {
		private final ExBiFunction<T, U, R, X> theFunction;
		private final ExPredicate<? super R, ? extends X> theTest;

		public AsPredicate(ExBiFunction<T, U, R, X> function, ExPredicate<? super R, ? extends X> test) {
			theFunction = function;
			theTest = test;
		}

		@Override
		public boolean test(T t, U u) throws X {
			R value = theFunction.apply(t, u);
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
			AsPredicate<?, ?, ?, ?> other = (AsPredicate<?, ?, ?, ?>) obj;
			return theFunction.equals(other.theFunction) && theTest.equals(other.theTest);
		}

		@Override
		public String toString() {
			return theFunction + ".if(" + theTest + ")";
		}
	}

	/**
	 * Implements {@link ExBiFunction#asConsumer(ExConsumer)}
	 * 
	 * @param <T> The type of the first argument the the consumer/function
	 * @param <U> The type of the second argument to the consumer/function
	 * @param <R> The type of value returned by the function
	 * @param <X> The type of exception throwable by the consumer
	 */
	class AsConsumer<T, U, R, X extends Throwable> implements ExBiConsumer<T, U, X> {
		private final ExBiFunction<T, U, R, X> theFunction;
		private final ExConsumer<? super R, ? extends X> theConsumer;

		public AsConsumer(ExBiFunction<T, U, R, X> function, ExConsumer<? super R, ? extends X> consumer) {
			theFunction = function;
			if (consumer != null)
				theConsumer = consumer;
			else
				theConsumer = FunctionUtils.exConsumeDoNothing();
		}

		@Override
		public void accept(T t, U u) throws X {
			R value = theFunction.apply(t, u);
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
			AsConsumer<?, ?, ?, ?> other = (AsConsumer<?, ?, ?, ?>) obj;
			return theFunction.equals(other.theFunction) && theConsumer.equals(other.theConsumer);
		}

		@Override
		public String toString() {
			return theFunction + ".consume(" + theConsumer + ")";
		}
	}
}

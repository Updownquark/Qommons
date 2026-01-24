package org.qommons.ex;

import java.util.Objects;

import org.qommons.fn.FunctionUtils;
import org.qommons.fn.TriFunction;

/**
 * A {@link TriFunction} look-alike that is capable of throwing a checked exception
 * 
 * @param <T> The first argument type
 * @param <U> The second argument type
 * @param <V> The third argument type
 * @param <R> The return type
 * @param <X> The throwable type
 */
@FunctionalInterface
public interface ExTriFunction<T, U, V, R, X extends Throwable> {
	/**
	 * @param t The first argument
	 * @param u The second argument
	 * @param v The third argument
	 * @return The return value
	 * @throws X An exception
	 */
	R apply(T t, U u, V v) throws X;

	/**
	 * @return A {@link TriFunction} that calls this tri function, wrapping any thrown checked exception with a
	 *         {@link CheckedExceptionWrapper}
	 */
	default TriFunction<T, U, V, R> unsafe() {
		return new Unsafe<>(this);
	}

	/**
	 * @param arg1 The argument to pass to this function as the first argument whenever the new function is called
	 * @return The curried unary function
	 */
	default ExBiFunction<U, V, R, X> curry1(T arg1) {
		return curry1(FunctionUtils.constantExSupplier(arg1));
	}

	/**
	 * @param arg1 Creator for the argument to pass to this function as the first argument whenever the new function is called
	 * @return The curried unary function
	 */
	default ExBiFunction<U, V, R, X> curry1(ExSupplier<? extends T, ? extends X> arg1) {
		return new Arg1Curried<>(this, arg1);
	}

	/**
	 * @param arg2 The argument to pass to this function as the second argument whenever the new function is called
	 * @return The curried unary function
	 */
	default ExBiFunction<T, V, R, X> curry2(U arg2) {
		return curry2(FunctionUtils.constantExSupplier(arg2));
	}

	/**
	 * @param arg2 Creator for the argument to pass to this function as the second argument whenever the new function is called
	 * @return The curried unary function
	 */
	default ExBiFunction<T, V, R, X> curry2(ExSupplier<? extends U, ? extends X> arg2) {
		return new Arg2Curried<>(this, arg2);
	}

	/**
	 * @param arg2 The argument to pass to this function as the second argument whenever the new function is called
	 * @return The curried unary function
	 */
	default ExBiFunction<T, U, R, X> curry3(V arg2) {
		return curry3(FunctionUtils.constantExSupplier(arg2));
	}

	/**
	 * @param arg2 Creator for the argument to pass to this function as the second argument whenever the new function is called
	 * @return The curried unary function
	 */
	default ExBiFunction<T, U, R, X> curry3(ExSupplier<? extends V, ? extends X> arg2) {
		return new Arg3Curried<>(this, arg2);
	}

	/**
	 * @param test The test for this function's return value
	 * @return This function as a predicate with the given test
	 */
	default ExTriPredicate<T, U, V, X> asPredicate(ExPredicate<? super R, ? extends X> test) {
		return new AsPredicate<>(this, test);
	}

	/**
	 * @param consumer An optional consumer to do something with this function's return value
	 * @return This function as a consumer
	 */
	default ExTriConsumer<T, U, V, X> asConsumer(ExConsumer<? super R, ? extends X> consumer) {
		return new AsConsumer<>(this, consumer);
	}

	/**
	 * @param <T> The first argument type
	 * @param <U> The second argument type
	 * @param <V> The third argument type
	 * @param <R> The return type
	 * @param <X> The throwable type
	 * @param f The function to wrap
	 * @return an {@link ExTriFunction} that calls the given function and never actually throws a checked exception
	 */
	static <T, U, V, R, X extends Throwable> ExTriFunction<T, U, V, R, X> of(TriFunction<T, U, V, R> f) {
		if (f == null)
			return null;
		return FunctionUtils.printableExTriFn(f::apply, f::toString, f);
	}

	/**
	 * Implements {@link ExTriFunction#unsafe()}
	 * 
	 * @param <T> The first argument type
	 * @param <U> The second argument type
	 * @param <V> The third argument type
	 * @param <R> The return type
	 * @param <X> The throwable type
	 */
	class Unsafe<T, U, V, R, X extends Throwable> implements TriFunction<T, U, V, R> {
		private final ExTriFunction<T, U, V, R, X> theFunction;

		public Unsafe(ExTriFunction<T, U, V, R, X> function) {
			theFunction = function;
		}

		@Override
		public R apply(T t, U u, V v) {
			try {
				return theFunction.apply(t, u, v);
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
				return obj instanceof Unsafe && theFunction.equals(((Unsafe<?, ?, ?, ?, ?>) obj).theFunction);
		}

		@Override
		public String toString() {
			return theFunction.toString();
		}
	}

	/**
	 * Implements {@link ExTriFunction#curry1(ExSupplier)}
	 * 
	 * @param <T> The first argument type of the ternary function (supplied by the curried supplier)
	 * @param <U> The second argument type of the ternary function (passed through from the argument to the function)
	 * @param <V> The third argument type of the ternary function (passed through from the argument to the function)
	 * @param <R> The return type of the function
	 * @param <X> The exception type thrown by the function
	 */
	class Arg1Curried<T, U, V, R, X extends Throwable> implements ExBiFunction<U, V, R, X> {
		private final ExTriFunction<T, U, V, R, X> theFunction;
		private final ExSupplier<? extends T, ? extends X> theArg1;

		public Arg1Curried(ExTriFunction<T, U, V, R, X> function, ExSupplier<? extends T, ? extends X> arg1) {
			theFunction = function;
			theArg1 = arg1;
		}

		@Override
		public R apply(U arg2, V arg3) throws X {
			T arg1 = theArg1.get();
			return theFunction.apply(arg1, arg2, arg3);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theFunction, theArg1);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof ExTriFunction.Arg1Curried))
				return false;
			ExTriFunction.Arg1Curried<?, ?, ?, ?, ?> other = (ExTriFunction.Arg1Curried<?, ?, ?, ?, ?>) obj;
			return theFunction.equals(other.theFunction) && theArg1.equals(other.theArg1);
		}

		@Override
		public String toString() {
			return theFunction + ".curry1(" + theArg1 + ")";
		}
	}

	/**
	 * Implements {@link ExTriFunction#curry2(ExSupplier)}
	 * 
	 * @param <T> The first argument type of the binary function (passed through from the argument to the function)
	 * @param <U> The second argument type of the binary function (supplied by the curried supplier)
	 * @param <V> The third argument type of the binary function (passed through from the argument to the function)
	 * @param <R> The return type of the function
	 * @param <X> The exception type thrown by the function
	 */
	class Arg2Curried<T, U, V, R, X extends Throwable> implements ExBiFunction<T, V, R, X> {
		private final ExTriFunction<T, U, V, R, X> theFunction;
		private final ExSupplier<? extends U, ? extends X> theArg2;

		public Arg2Curried(ExTriFunction<T, U, V, R, X> function, ExSupplier<? extends U, ? extends X> arg12) {
			theFunction = function;
			theArg2 = arg12;
		}

		@Override
		public R apply(T arg1, V arg3) throws X {
			U arg2 = theArg2.get();
			return theFunction.apply(arg1, arg2, arg3);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theFunction, theArg2);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof ExTriFunction.Arg2Curried))
				return false;
			ExTriFunction.Arg2Curried<?, ?, ?, ?, ?> other = (ExTriFunction.Arg2Curried<?, ?, ?, ?, ?>) obj;
			return theFunction.equals(other.theFunction) && theArg2.equals(other.theArg2);
		}

		@Override
		public String toString() {
			return theFunction + ".curry2(" + theArg2 + ")";
		}
	}

	/**
	 * Implements {@link ExTriFunction#curry2(ExSupplier)}
	 * 
	 * @param <T> The first argument type of the binary function (passed through from the argument to the function)
	 * @param <U> The second argument type of the binary function (passed through from the argument to the function)
	 * @param <V> The third argument type of the binary function (supplied by the curried supplier)
	 * @param <R> The return type of the function
	 * @param <X> The exception type thrown by the function
	 */
	class Arg3Curried<T, U, V, R, X extends Throwable> implements ExBiFunction<T, U, R, X> {
		private final ExTriFunction<T, U, V, R, X> theFunction;
		private final ExSupplier<? extends V, ? extends X> theArg3;

		public Arg3Curried(ExTriFunction<T, U, V, R, X> function, ExSupplier<? extends V, ? extends X> arg12) {
			theFunction = function;
			theArg3 = arg12;
		}

		@Override
		public R apply(T arg1, U arg2) throws X {
			V arg3 = theArg3.get();
			return theFunction.apply(arg1, arg2, arg3);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theFunction, theArg3);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof Arg3Curried))
				return false;
			Arg3Curried<?, ?, ?, ?, ?> other = (Arg3Curried<?, ?, ?, ?, ?>) obj;
			return theFunction.equals(other.theFunction) && theArg3.equals(other.theArg3);
		}

		@Override
		public String toString() {
			return theFunction + ".curry2(" + theArg3 + ")";
		}
	}

	/**
	 * Implements {@link ExTriFunction#asPredicate(ExPredicate)}
	 * 
	 * @param <T> The type of the first argument the the predicate/function
	 * @param <U> The type of the second argument to the predicate/function
	 * @param <V> The type of the third argument to the predicate/function
	 * @param <R> The type of value returned by the function
	 * @param <X> The type of exception throwable by the predicate
	 */
	class AsPredicate<T, U, V, R, X extends Throwable> implements ExTriPredicate<T, U, V, X> {
		private final ExTriFunction<T, U, V, R, X> theFunction;
		private final ExPredicate<? super R, ? extends X> theTest;

		public AsPredicate(ExTriFunction<T, U, V, R, X> function, ExPredicate<? super R, ? extends X> test) {
			theFunction = function;
			theTest = test;
		}

		@Override
		public boolean test(T t, U u, V v) throws X {
			R value = theFunction.apply(t, u, v);
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
			AsPredicate<?, ?, ?, ?, ?> other = (AsPredicate<?, ?, ?, ?, ?>) obj;
			return theFunction.equals(other.theFunction) && theTest.equals(other.theTest);
		}

		@Override
		public String toString() {
			return theFunction + ".if(" + theTest + ")";
		}
	}

	/**
	 * Implements {@link ExTriFunction#asConsumer(ExConsumer)}
	 * 
	 * @param <T> The type of the first argument the the consumer/function
	 * @param <U> The type of the second argument to the consumer/function
	 * @param <V> The type of the third argument to the consumer/function
	 * @param <R> The type of value returned by the function
	 * @param <X> The type of exception throwable by the consumer
	 */
	class AsConsumer<T, U, V, R, X extends Throwable> implements ExTriConsumer<T, U, V, X> {
		private final ExTriFunction<T, U, V, R, X> theFunction;
		private final ExConsumer<? super R, ? extends X> theConsumer;

		public AsConsumer(ExTriFunction<T, U, V, R, X> function, ExConsumer<? super R, ? extends X> consumer) {
			theFunction = function;
			if (consumer != null)
				theConsumer = consumer;
			else
				theConsumer = FunctionUtils.exConsumeDoNothing();
		}

		@Override
		public void accept(T t, U u, V v) throws X {
			R value = theFunction.apply(t, u, v);
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
			AsConsumer<?, ?, ?, ?, ?> other = (AsConsumer<?, ?, ?, ?, ?>) obj;
			return theFunction.equals(other.theFunction) && theConsumer.equals(other.theConsumer);
		}

		@Override
		public String toString() {
			return theFunction + ".consume(" + theConsumer + ")";
		}
	}
}

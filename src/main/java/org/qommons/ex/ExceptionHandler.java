package org.qommons.ex;

import java.util.function.Supplier;

/**
 * <p>
 * An exception is often the most intuitive API feature for indicating a particular situation. Sometimes it even makes sense to advertise an
 * exception that may be thrown under semi-normal circumstances when it doesn't make sense for the natural result of an operation to be able
 * to represent that circumstance.
 * <p>
 * <p>
 * This API is for handling exceptions when it may be expected that the caller will expect them and want to handle them more efficiently
 * than throwing them and catching them.
 * </p>
 */
public interface ExceptionHandler {
	/**
	 * @return Whether this handler creates the exceptions it {@link Single#handle1(Supplier) handles}, either to throw them or to return
	 *         them via a {@link Single#get1() get} method. A non-instantiating one only reports that it handled one via a
	 *         {@link Single#hasException() hasException} method.
	 */
	boolean isInstantiating();

	/**
	 * Simply asserts that this handler currently {@link #hasException() has} no exception. For use prior to passing to a method that may
	 * call a handle method, since exception handler's cannot handle multiple exceptions.
	 * 
	 * @return This handler
	 */
	default ExceptionHandler use() {
		if (hasException())
			throw new IllegalStateException("This handler has exceptions that have not been cleared");
		return this;
	}

	/** @return Whether this handler is holding any exceptions */
	boolean hasException();

	/**
	 * Clears any exceptions this handler may be holding
	 * 
	 * @return This handler
	 */
	ExceptionHandler clear();

	/**
	 * @param fill Whether this handler should fill in the stack traces of any exceptions it receives for holding. This operation is slow,
	 *        so this setting is false by default.
	 * @return This handler
	 */
	ExceptionHandler fillStackTrace(boolean fill);

	/**
	 * @param <X> The type of the exception
	 * @return A handler that just throws an exception passed to it
	 */
	public static <X extends Throwable> Single<X, X> thrower() {
		return (Single<X, X>) Impl.THROWER;
	}

	/**
	 * @param <X1> The first type of exception
	 * @param <X2> The second type of exception
	 * @return A handler that just throws any exception passed to it
	 */
	public static <X1 extends Throwable, X2 extends Throwable> Double<X1, X2, X1, X2> thrower2() {
		return ExceptionHandler.<X1> thrower().stack(//
			ExceptionHandler.<X2> thrower());
	}

	/**
	 * @param <X> The type of the exception
	 * @return A handler that captures and holds an exception passed to it
	 */
	public static <X extends Throwable> Single<X, NeverThrown> holder() {
		return new Impl.Holder<>();
	}

	/**
	 * @param <X1> The first type of exception
	 * @param <X2> The second type of exception
	 * @return A handler that captures and holds any exception passed to it
	 */
	public static <X1 extends Throwable, X2 extends Throwable> Double<X1, X2, NeverThrown, NeverThrown> holder2() {
		return ExceptionHandler.<X1> holder().stack(//
			ExceptionHandler.<X2> holder());
	}

	/**
	 * @param <X> The type of the exception
	 * @return A handler that only captures that an exception was thrown without instantiating it
	 */
	public static <X extends Throwable> Single<X, NeverThrown> placeHolder() {
		return new Impl.PlaceHolder<>();
	}

	/**
	 * @param <X1> The first type of exception
	 * @param <X2> The second type of exception
	 * @return A handler that only captures that an exception was thrown without instantiating it
	 */
	public static <X1 extends Throwable, X2 extends Throwable> Double<X1, X2, NeverThrown, NeverThrown> placeHolder2() {
		return ExceptionHandler.<X1> placeHolder().stack(//
			ExceptionHandler.<X2> placeHolder());
	}

	/**
	 * @param <X> The type of the exception
	 * @param instantiating Whether to create an instantiating exception holder (one that creates the exceptions it
	 *        {@link Single#handle1(Supplier) handles}) and returns them via {@link Single#get1()}, or a non-instantiating one, that only
	 *        reports that it handled one via {@link Single#hasException1()}
	 * @return A handler that only captures that an exception was thrown without instantiating it
	 */
	public static <X extends Throwable> Single<X, NeverThrown> holder(boolean instantiating) {
		if (instantiating)
			return holder();
		else
			return placeHolder();
	}

	/**
	 * @param <X1> The first type of exception
	 * @param <X2> The second type of exception
	 * @param instantiating Whether to create an instantiating exception holder (one that creates the exceptions it
	 *        {@link Single#handle1(Supplier) handles}) and returns them via {@link Single#get1()}, or a non-instantiating one, that only
	 *        reports that it handled one via {@link Single#hasException1()}
	 * @return A handler that only captures that an exception was thrown without instantiating it
	 */
	public static <X1 extends Throwable, X2 extends Throwable> Double<X1, X2, NeverThrown, NeverThrown> holder2(boolean instantiating) {
		return ExceptionHandler.<X1> holder(instantiating).stack(//
			ExceptionHandler.<X2> holder(instantiating));
	}

	/**
	 * A handler for a single exception type
	 * 
	 * @param <X> The type of exception that this handler handles
	 * @param <T> The type of exception that this handler throws
	 */
	public interface Single<X extends Throwable, T extends Throwable> extends ExceptionHandler {
		@Override
		default Single<X, T> use() {
			ExceptionHandler.super.use();
			return this;
		}

		/**
		 * @param exception The producer for an exception to handle in this handler's first spot
		 * @throws T The exception thrown by this handler if so configured
		 */
		public void handle1(Supplier<? extends X> exception) throws T;

		/**
		 * @param <X2> The second type of exception to handle
		 * @param <T2> The type of exception thrown when handling the second handled exception type
		 * @param other The exception handler for the second exception type
		 * @return An exception handler capable of handling this handler's type and the other's
		 */
		<X2 extends Throwable, T2 extends Throwable> Double<X, X2, T, T2> stack(Single<X2, T2> other);

		/**
		 * @param <X2> The second type of exception to handle
		 * @param <X3> The third type of exception to handle
		 * @param <T2> The type of exception thrown when handling the second handled exception type
		 * @param <T3> The type of exception thrown when handling the third handled exception type
		 * @param other The exception handler for the second and third exception types
		 * @return An exception handler capable of handling this handler's type and both of the other's
		 */
		<X2 extends Throwable, X3 extends Throwable, T2 extends Throwable, T3 extends Throwable> Triple<X, X2, X3, T, T2, T3> stack(
			Double<X2, X3, T2, T3> other);

		/** @return An exception handler that only handles this handler's first type */
		Single<X, T> oneOnly();

		@Override
		default boolean hasException() {
			return hasException1();
		}

		/**
		 * @return Whether this handler's {@link #handle1(Supplier)} method has been called since instantiation or its {@link #clear1()}
		 *         method
		 */
		boolean hasException1();

		/**
		 * @return The exception held in this handler's first place, or null if:
		 *         <ul>
		 *         <li>This handler does not hold its exceptions</li>
		 *         <li>No exception has been {@link #handle1(Supplier) handled} by it</li>
		 *         <li>The exception has been cleared (see {@link #clear()} and {@link #clear1()})</li>
		 *         </ul>
		 */
		X get1();

		/**
		 * Clears any exception held in this handler's first place
		 * 
		 * @return This handler
		 */
		Single<X, T> clear1();

		@Override
		Single<X, T> clear();

		/**
		 * @param fill Whether this handler should fill in the stack traces of any exceptions it receives for holding in its first place.
		 *        This operation is slow, so this setting is false by default.
		 * @return This handler
		 */
		Single<X, T> fillStackTrace1(boolean fill);

		@Override
		Single<X, T> fillStackTrace(boolean fill);
	}

	/**
	 * A handler for 2 exception types
	 * 
	 * @param <X1> The first type of exception that this handler handles
	 * @param <X2> The second type of exception that this handler handles
	 * @param <T1> The first type of exception that this handler throws
	 * @param <T2> The second type of exception that this handler throws
	 */
	public interface Double<X1 extends Throwable, X2 extends Throwable, T1 extends Throwable, T2 extends Throwable> extends Single<X1, T1> {
		@Override
		default Double<X1, X2, T1, T2> use() {
			Single.super.use();
			return this;
		}

		/**
		 * @param exception The producer for an exception to handle in this handler's second spot
		 * @throws T2 The exception thrown by this handler if so configured
		 */
		public void handle2(Supplier<? extends X2> exception) throws T2;

		/** @return An exception handler that only handles this handler's second type */
		Single<X2, T2> twoOnly();

		@Override
		<X3 extends Throwable, T3 extends Throwable> Triple<X1, X3, X2, T1, T3, T2> stack(Single<X3, T3> other);

		/**
		 * @return The exception held in this handler's second place, or null if:
		 *         <ul>
		 *         <li>This handler does not hold its exceptions</li>
		 *         <li>No exception has been {@link #handle2(Supplier) handled} by it</li>
		 *         <li>The exception has been cleared (see {@link #clear()} and {@link #clear2()})</li>
		 *         </ul>
		 */
		X2 get2();

		@Override
		default boolean hasException() {
			return hasException1() || hasException2();
		}

		/**
		 * @return Whether this handler's {@link #handle2(Supplier)} method has been called since instantiation or its {@link #clear2()}
		 *         method
		 */
		boolean hasException2();

		@Override
		Double<X1, X2, T1, T2> clear1();

		/**
		 * Clears any exception held in this handler's second place
		 * 
		 * @return This handler
		 */
		Double<X1, X2, T1, T2> clear2();

		@Override
		Double<X1, X2, T1, T2> clear();

		@Override
		Double<X1, X2, T1, T2> fillStackTrace1(boolean fill);

		/**
		 * @param fill Whether this handler should fill in the stack traces of any exceptions it receives for holding in its second place.
		 *        This operation is slow, so this setting is false by default.
		 * @return This handler
		 */
		Double<X1, X2, T1, T2> fillStackTrace2(boolean fill);

		@Override
		Double<X1, X2, T1, T2> fillStackTrace(boolean fill);
	}

	/**
	 * A handler for 3 exception types
	 * 
	 * @param <X1> The first type of exception that this handler handles
	 * @param <X2> The second type of exception that this handler handles
	 * @param <X3> The third type of exception that this handler handles
	 * @param <T1> The first type of exception that this handler throws
	 * @param <T2> The second type of exception that this handler throws
	 * @param <T3> The third type of exception that this handler throws
	 */
	public interface Triple<X1 extends Throwable, X2 extends Throwable, X3 extends Throwable, T1 extends Throwable, T2 extends Throwable, T3 extends Throwable>
		extends Double<X1, X2, T1, T2> {
		@Override
		default Triple<X1, X2, X3, T1, T2, T3> use() {
			Double.super.use();
			return this;
		}
		/**
		 * @param exception The producer for an exception to handle in this handler's third spot
		 * @throws T3 The exception thrown by this handler if so configured
		 */
		public void handle3(Supplier<? extends X3> exception) throws T3;

		/** @return An exception handler that only handles this handler's third type */
		Single<X3, T3> threeOnly();

		/**
		 * @return The exception held in this handler's third place, or null if:
		 *         <ul>
		 *         <li>This handler does not hold its exceptions</li>
		 *         <li>No exception has been {@link #handle3(Supplier) handled} by it</li>
		 *         <li>The exception has been cleared (see {@link #clear()} and {@link #clear3()})</li>
		 *         </ul>
		 */
		X3 get3();

		@Override
		default boolean hasException() {
			return hasException1() || hasException2() || hasException3();
		}

		/**
		 * @return Whether this handler's {@link #handle3(Supplier)} method has been called since instantiation or its {@link #clear3()}
		 *         method
		 */
		boolean hasException3();

		@Override
		Triple<X1, X2, X3, T1, T2, T3> clear1();

		@Override
		Triple<X1, X2, X3, T1, T2, T3> clear2();

		/**
		 * Clears any exception held in this handler's third place
		 * 
		 * @return This handler
		 */
		Triple<X1, X2, X3, T1, T2, T3> clear3();

		@Override
		Triple<X1, X2, X3, T1, T2, T3> clear();

		@Override
		Triple<X1, X2, X3, T1, T2, T3> fillStackTrace1(boolean fill);

		@Override
		Triple<X1, X2, X3, T1, T2, T3> fillStackTrace2(boolean fill);

		/**
		 * @param fill Whether this handler should fill in the stack traces of any exceptions it receives for holding in its third place.
		 *        This operation is slow, so this setting is false by default.
		 * @return This handler
		 */
		Triple<X1, X2, X3, T1, T2, T3> fillStackTrace3(boolean fill);

		@Override
		Triple<X1, X2, X3, T1, T2, T3> fillStackTrace(boolean fill);
	}

	/** Implementation details for this class */
	static class Impl {
		static final Thrower<?> THROWER = new Thrower<>();

		private static abstract class SingleImpl<X extends Throwable, T extends Throwable> implements Single<X, T> {
			@Override
			public <X2 extends Throwable, T2 extends Throwable> Double<X, X2, T, T2> stack(Single<X2, T2> other) {
				return new DoubleImpl<>(oneOnly(), other.oneOnly());
			}

			@Override
			public <X2 extends Throwable, X3 extends Throwable, T2 extends Throwable, T3 extends Throwable> Triple<X, X2, X3, T, T2, T3> stack(
				Double<X2, X3, T2, T3> other) {
				return new TripleImpl<>(oneOnly(), other.oneOnly(), other.twoOnly());
			}

			@Override
			public Single<X, T> oneOnly() {
				return this;
			}

			@Override
			public Single<X, T> clear() {
				clear1();
				return this;
			}

			@Override
			public Single<X, T> fillStackTrace(boolean fill) {
				fillStackTrace1(fill);
				return this;
			}
		}

		private static class Thrower<X extends Throwable> extends SingleImpl<X, X> {
			@Override
			public boolean isInstantiating() {
				return true;
			}

			@Override
			public void handle1(Supplier<? extends X> exception) throws X {
				throw exception.get();
			}

			@Override
			public boolean hasException1() {
				return false;
			}

			@Override
			public X get1() {
				return null;
			}

			@Override
			public Single<X, X> clear1() {
				return this;
			}

			@Override
			public Single<X, X> fillStackTrace1(boolean fill) {
				return this;
			}
		}

		private static class Holder<X extends Throwable> extends SingleImpl<X, NeverThrown> {
			private X theException;
			private boolean isFillStackTrace;

			@Override
			public boolean isInstantiating() {
				return true;
			}

			@Override
			public void handle1(Supplier<? extends X> exception) {
				if (exception == null)
					throw new NullPointerException("Cannot handle a null exception");
				if (theException != null)
					throw multiExceptionError(exception.get(), theException);
				theException = exception.get();
				if (isFillStackTrace)
					theException.fillInStackTrace();
			}

			@Override
			public boolean hasException1() {
				return theException != null;
			}

			@Override
			public X get1() {
				return theException;
			}

			@Override
			public Single<X, NeverThrown> clear1() {
				theException = null;
				return this;
			}

			@Override
			public Single<X, NeverThrown> fillStackTrace1(boolean fill) {
				isFillStackTrace = fill;
				return this;
			}
		}

		private static class PlaceHolder<X extends Throwable> extends SingleImpl<X, NeverThrown> {
			private boolean hasException;

			@Override
			public boolean isInstantiating() {
				return false;
			}

			@Override
			public void handle1(Supplier<? extends X> exception) throws NeverThrown {
				if (hasException)
					throw multiExceptionError(exception.get());
				hasException = true;
			}

			@Override
			public boolean hasException1() {
				return hasException;
			}

			@Override
			public X get1() {
				return null;
			}

			@Override
			public Single<X, NeverThrown> clear1() {
				hasException = false;
				return this;
			}

			@Override
			public Single<X, NeverThrown> fillStackTrace1(boolean fill) {
				return this;
			}
		}

		private static IllegalStateException multiExceptionError(Throwable first, Throwable... more) {
			IllegalStateException ise = new IllegalStateException("Cannot handle multiple exceptions", first);
			for (Throwable other : more) {
				if (other != null)
					ise.addSuppressed(other);
			}
			return ise;
		}

		private static class DoubleImpl<X1 extends Throwable, X2 extends Throwable, T1 extends Throwable, T2 extends Throwable>
			implements Double<X1, X2, T1, T2> {
			protected final Single<X1, T1> theFirst;
			protected final Single<X2, T2> theSecond;

			DoubleImpl(Single<X1, T1> first, Single<X2, T2> second) {
				theFirst = first;
				theSecond = second;
			}

			@Override
			public boolean isInstantiating() {
				return theFirst.isInstantiating() || theSecond.isInstantiating();
			}

			@Override
			public void handle1(Supplier<? extends X1> exception) throws T1 {
				if (theSecond.hasException())
					throw multiExceptionError(exception.get(), theFirst.get1(), theSecond.get1());
				theFirst.handle1(exception);
			}

			@Override
			public void handle2(Supplier<? extends X2> exception) throws T2 {
				if (theFirst.hasException())
					throw multiExceptionError(exception.get(), theFirst.get1(), theSecond.get1());
				theSecond.handle1(exception);
			}

			@Override
			public Single<X1, T1> oneOnly() {
				return theFirst;
			}

			@Override
			public Single<X2, T2> twoOnly() {
				return theSecond;
			}

			@Override
			public <X3 extends Throwable, X4 extends Throwable, T3 extends Throwable, T4 extends Throwable> Triple<X1, X3, X4, T1, T3, T4> stack(
				Double<X3, X4, T3, T4> other) {
				return new TripleImpl<>(oneOnly(), other.oneOnly(), other.twoOnly());
			}

			@Override
			public <X3 extends Throwable, T3 extends Throwable> Triple<X1, X3, X2, T1, T3, T2> stack(Single<X3, T3> other) {
				return new TripleImpl<>(oneOnly(), other, twoOnly());
			}

			@Override
			public boolean hasException1() {
				return theFirst.hasException1();
			}

			@Override
			public boolean hasException2() {
				return theSecond.hasException1();
			}

			@Override
			public X1 get1() {
				return theFirst.get1();
			}

			@Override
			public X2 get2() {
				return theSecond.get1();
			}

			@Override
			public Double<X1, X2, T1, T2> clear1() {
				theFirst.clear1();
				return this;
			}

			@Override
			public Double<X1, X2, T1, T2> clear2() {
				theSecond.clear1();
				return this;
			}

			@Override
			public Double<X1, X2, T1, T2> clear() {
				theFirst.clear1();
				theSecond.clear1();
				return this;
			}

			@Override
			public Double<X1, X2, T1, T2> fillStackTrace1(boolean fill) {
				theFirst.fillStackTrace1(fill);
				return this;
			}

			@Override
			public Double<X1, X2, T1, T2> fillStackTrace2(boolean fill) {
				theSecond.fillStackTrace1(fill);
				return this;
			}

			@Override
			public Double<X1, X2, T1, T2> fillStackTrace(boolean fill) {
				theFirst.fillStackTrace1(fill);
				theSecond.fillStackTrace1(fill);
				return this;
			}
		}

		private static class TripleImpl<X1 extends Throwable, X2 extends Throwable, X3 extends Throwable, T1 extends Throwable, T2 extends Throwable, T3 extends Throwable>
			extends DoubleImpl<X1, X2, T1, T2> implements Triple<X1, X2, X3, T1, T2, T3> {
			protected final Single<X3, T3> theThird;

			TripleImpl(Single<X1, T1> first, Single<X2, T2> second, Single<X3, T3> third) {
				super(first, second);
				theThird = third;
			}

			@Override
			public boolean isInstantiating() {
				return super.isInstantiating() || theThird.isInstantiating();
			}

			@Override
			public void handle1(Supplier<? extends X1> exception) throws T1 {
				if (theThird.get1() != null)
					throw multiExceptionError(exception.get(), theFirst.get1(), theSecond.get1(), theThird.get1());
				super.handle1(exception);
			}

			@Override
			public void handle2(Supplier<? extends X2> exception) throws T2 {
				if (theThird.get1() != null)
					throw multiExceptionError(exception.get(), theFirst.get1(), theSecond.get1(), theThird.get1());
				super.handle2(exception);
			}

			@Override
			public void handle3(Supplier<? extends X3> exception) throws T3 {
				if (super.hasException())
					throw multiExceptionError(exception.get(), theFirst.get1(), theSecond.get1(), theThird.get1());
				theThird.handle1(exception);
			}

			@Override
			public Single<X3, T3> threeOnly() {
				return theThird;
			}

			@Override
			public X3 get3() {
				return theThird.get1();
			}

			@Override
			public boolean hasException3() {
				return theThird.hasException1();
			}

			@Override
			public Triple<X1, X2, X3, T1, T2, T3> fillStackTrace1(boolean fill) {
				super.fillStackTrace1(fill);
				return this;
			}

			@Override
			public Triple<X1, X2, X3, T1, T2, T3> fillStackTrace2(boolean fill) {
				super.fillStackTrace2(fill);
				return this;
			}

			@Override
			public Triple<X1, X2, X3, T1, T2, T3> fillStackTrace3(boolean fill) {
				theThird.fillStackTrace1(fill);
				return this;
			}

			@Override
			public Triple<X1, X2, X3, T1, T2, T3> fillStackTrace(boolean fill) {
				super.fillStackTrace(fill);
				theThird.fillStackTrace1(fill);
				return this;
			}

			@Override
			public Triple<X1, X2, X3, T1, T2, T3> clear1() {
				super.clear1();
				return this;
			}

			@Override
			public Triple<X1, X2, X3, T1, T2, T3> clear2() {
				super.clear2();
				return this;
			}

			@Override
			public Triple<X1, X2, X3, T1, T2, T3> clear3() {
				theThird.clear1();
				return this;
			}

			@Override
			public Triple<X1, X2, X3, T1, T2, T3> clear() {
				super.clear();
				theThird.clear1();
				return this;
			}
		}
	}
}

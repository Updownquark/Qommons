package org.qommons.ex;

public interface ExceptionHandler {
	boolean hasException();

	ExceptionHandler clear();

	ExceptionHandler fillStackTrace(boolean fill);

	public static <X extends Throwable> Single<X, X> thrower() {
		return (Single<X, X>) Impl.THROWER;
	}

	public static <X1 extends Throwable, X2 extends Throwable> Double<X1, X2, X1, X2> thrower2() {
		return ExceptionHandler.<X1> thrower().stack(//
			ExceptionHandler.<X2> thrower());
	}

	public static <X extends Throwable> Single<X, NeverThrown> holder() {
		return new Impl.Holder<>();
	}

	public static <X1 extends Throwable, X2 extends Throwable> Double<X1, X2, NeverThrown, NeverThrown> holder2() {
		return ExceptionHandler.<X1> holder().stack(//
			ExceptionHandler.<X2> holder());
	}

	public interface Single<X extends Throwable, T extends Throwable> extends ExceptionHandler {
		public void handle1(X exception) throws T;

		<X2 extends Throwable, T2 extends Throwable> Double<X, X2, T, T2> stack(Single<X2, T2> other);

		<X2 extends Throwable, X3 extends Throwable, T2 extends Throwable, T3 extends Throwable> Triple<X, X2, X3, T, T2, T3> stack(
			Double<X2, X3, T2, T3> other);

		Single<X, T> oneOnly();

		@Override
		boolean hasException();

		X get1();

		Single<X, T> clear1();

		@Override
		Single<X, T> clear();

		Single<X, T> fillStackTrace1(boolean fill);

		@Override
		Single<X, T> fillStackTrace(boolean fill);
	}

	public interface Double<X1 extends Throwable, X2 extends Throwable, T1 extends Throwable, T2 extends Throwable> extends Single<X1, T1> {
		public void handle2(X2 exception) throws T2;

		Single<X2, T2> twoOnly();

		@Override
		<X3 extends Throwable, T3 extends Throwable> Triple<X1, X3, X2, T1, T3, T2> stack(Single<X3, T3> other);

		X2 get2();

		@Override
		Double<X1, X2, T1, T2> clear1();

		Double<X1, X2, T1, T2> clear2();

		@Override
		Double<X1, X2, T1, T2> clear();

		@Override
		Double<X1, X2, T1, T2> fillStackTrace1(boolean fill);

		Double<X1, X2, T1, T2> fillStackTrace2(boolean fill);

		@Override
		Double<X1, X2, T1, T2> fillStackTrace(boolean fill);
	}

	public interface Triple<X1 extends Throwable, X2 extends Throwable, X3 extends Throwable, T1 extends Throwable, T2 extends Throwable, T3 extends Throwable>
		extends Double<X1, X2, T1, T2> {
		public void handle3(X3 exception) throws T3;

		Single<X3, T3> threeOnly();

		@Override
		Triple<X1, X2, X3, T1, T2, T3> clear1();

		@Override
		Triple<X1, X2, X3, T1, T2, T3> clear2();

		Triple<X1, X2, X3, T1, T2, T3> clear3();

		@Override
		Triple<X1, X2, X3, T1, T2, T3> clear();

		@Override
		Triple<X1, X2, X3, T1, T2, T3> fillStackTrace1(boolean fill);

		@Override
		Triple<X1, X2, X3, T1, T2, T3> fillStackTrace2(boolean fill);

		Triple<X1, X2, X3, T1, T2, T3> fillStackTrace3(boolean fill);

		@Override
		Triple<X1, X2, X3, T1, T2, T3> fillStackTrace(boolean fill);
	}

	static class Impl {
		static final Thrower<?> THROWER = new Thrower<>();

		private static class Thrower<X extends Throwable> implements Single<X, X> {
			@Override
			public void handle1(X exception) throws X {
				throw exception;
			}

			@Override
			public <X2 extends Throwable, T2 extends Throwable> Double<X, X2, X, T2> stack(Single<X2, T2> other) {
				return new DoubleImpl<>(oneOnly(), other.oneOnly());
			}

			@Override
			public <X2 extends Throwable, X3 extends Throwable, T2 extends Throwable, T3 extends Throwable> Triple<X, X2, X3, X, T2, T3> stack(
				Double<X2, X3, T2, T3> other) {
				return new TripleImpl<>(oneOnly(), other.oneOnly(), other.twoOnly());
			}

			@Override
			public Single<X, X> oneOnly() {
				return this;
			}

			@Override
			public boolean hasException() {
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
			public Single<X, X> clear() {
				return this;
			}

			@Override
			public Single<X, X> fillStackTrace1(boolean fill) {
				return this;
			}

			@Override
			public Single<X, X> fillStackTrace(boolean fill) {
				return this;
			}
		}

		private static class Holder<X extends Throwable> implements Single<X, NeverThrown> {
			private X theException;
			private boolean isFillStackTrace;

			@Override
			public void handle1(X exception) {
				if (exception == null)
					throw new NullPointerException("Cannot handle a null exception");
				if (theException != null)
					throw multiExceptionError(exception, theException);
				theException = exception;
				if (isFillStackTrace)
					exception.fillInStackTrace();
			}

			@Override
			public Single<X, NeverThrown> oneOnly() {
				return this;
			}

			@Override
			public <X2 extends Throwable, T2 extends Throwable> Double<X, X2, NeverThrown, T2> stack(Single<X2, T2> other) {
				return new DoubleImpl<>(this, other.oneOnly());
			}

			@Override
			public <X2 extends Throwable, X3 extends Throwable, T2 extends Throwable, T3 extends Throwable> Triple<X, X2, X3, NeverThrown, T2, T3> stack(
				Double<X2, X3, T2, T3> other) {
				return new TripleImpl<>(oneOnly(), other.oneOnly(), other.twoOnly());
			}

			@Override
			public boolean hasException() {
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
			public Single<X, NeverThrown> clear() {
				return clear1();
			}

			@Override
			public Single<X, NeverThrown> fillStackTrace1(boolean fill) {
				isFillStackTrace = fill;
				return this;
			}

			@Override
			public Single<X, NeverThrown> fillStackTrace(boolean fill) {
				return fillStackTrace1(fill);
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
			public void handle1(X1 exception) throws T1 {
				if (theSecond.get1() != null)
					throw multiExceptionError(exception, theFirst.get1(), theSecond.get1());
				theFirst.handle1(exception);
			}

			@Override
			public void handle2(X2 exception) throws T2 {
				if (theFirst.get1() != null)
					throw multiExceptionError(exception, theFirst.get1(), theSecond.get1());
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
			public boolean hasException() {
				return theFirst.get1() != null || theSecond.get1() != null;
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
			public void handle1(X1 exception) throws T1 {
				if (theThird.get1() != null)
					throw multiExceptionError(exception, theFirst.get1(), theSecond.get1(), theThird.get1());
				super.handle1(exception);
			}

			@Override
			public void handle2(X2 exception) throws T2 {
				if (theThird.get1() != null)
					throw multiExceptionError(exception, theFirst.get1(), theSecond.get1(), theThird.get1());
				super.handle2(exception);
			}

			@Override
			public void handle3(X3 exception) throws T3 {
				if (super.hasException())
					throw multiExceptionError(exception, theFirst.get1(), theSecond.get1(), theThird.get1());
				theThird.handle1(exception);
			}

			@Override
			public Single<X3, T3> threeOnly() {
				return theThird;
			}

			@Override
			public boolean hasException() {
				return super.hasException() && theThird.get1() != null;
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

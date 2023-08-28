package org.qommons.ex;

public class ExceptionHandler {
	private ExceptionHandler() {
	}

	public static <X extends Throwable> Single<X, X> get1() {
		return (Single<X, X>) THROWER1;
	}

	public static <X1 extends Throwable, X2 extends Throwable> Double<X1, X2, X1, X2> get2() {
		return (Double<X1, X2, X1, X2>) THROWER2;
	}

	public static <X1 extends Throwable, X2 extends Throwable, X3 extends Throwable> Triple<X1, X2, X3, X1, X2, X3> get3() {
		return (Triple<X1, X2, X3, X1, X2, X3>) THROWER3;
	}

	private static final Thrower1<?> THROWER1 = new Thrower1<>();
	private static final Thrower2<?, ?> THROWER2 = new Thrower2<>();
	private static final Thrower3<?, ?, ?> THROWER3 = new Thrower3<>();

	public interface Single<X extends Throwable, T extends Throwable> {
		public void handle1(X exception) throws T;

		Container0<X> hold1();
	}

	public interface Double<X1 extends Throwable, X2 extends Throwable, T1 extends Throwable, T2 extends Throwable> extends Single<X1, T1> {
		public void handle2(X2 exception) throws T2;

		@Override
		Container0X<X1, X2, T2> hold1();

		ContainerX0<X1, X2, T1> hold2();
	}

	public interface Triple<X1 extends Throwable, X2 extends Throwable, X3 extends Throwable, T1 extends Throwable, T2 extends Throwable, T3 extends Throwable>
		extends Double<X1, X2, T1, T2> {
		public void handle3(X3 exception) throws T3;

		@Override
		Container0XX<X1, X2, X3, T2, T3> hold1();

		@Override
		ContainerX0X<X1, X2, X3, T1, T3> hold2();

		ContainerXX0<X1, X2, X3, T1, T2> hold3();
	}

	public interface ExceptionContainer {
		boolean hasException();

		ExceptionContainer clear();

		ExceptionContainer fillStackTrace(boolean fill);
	}

	public interface Container0<X extends Throwable> extends Single<X, NeverThrown>, ExceptionContainer {
		@Override
		default boolean hasException() {
			return get1() != null;
		}

		@Override
		Container0<X> fillStackTrace(boolean fill);

		X get1();

		Container0<X> clear1();

		@Override
		Container0<X> clear();

		default Container0<X> throwIfHas1() throws X {
			X x = get1();
			if (x != null)
				throw x;
			return this;
		}

		default void throw1() throws X, IllegalStateException {
			X x = get1();
			if (x != null)
				throw x;
			else
				throw new IllegalStateException("No exception occurred");
		}

		@Override
		default Container0<X> hold1() {
			return this;
		}
	}

	public interface Container0X<X1 extends Throwable, X2 extends Throwable, T2 extends Throwable>
		extends Container0<X1>, Double<X1, X2, NeverThrown, T2> {
		@Override
		Container0X<X1, X2, T2> fillStackTrace(boolean fill);

		@Override
		Container0X<X1, X2, T2> clear1();

		@Override
		Container0X<X1, X2, T2> clear();

		@Override
		default Container0X<X1, X2, T2> throwIfHas1() throws X1 {
			Container0.super.throwIfHas1();
			return this;
		}

		@Override
		default Container0X<X1, X2, T2> hold1() {
			return this;
		}

		@Override
		Container00<X1, X2> hold2();
	}

	public interface ContainerX0<X1 extends Throwable, X2 extends Throwable, T1 extends Throwable>
		extends Double<X1, X2, T1, NeverThrown>, ExceptionContainer {
		@Override
		default boolean hasException() {
			return get2() != null;
		}

		@Override
		ContainerX0<X1, X2, T1> fillStackTrace(boolean fill);

		X2 get2();

		ContainerX0<X1, X2, T1> clear2();

		@Override
		ContainerX0<X1, X2, T1> clear();

		default ContainerX0<X1, X2, T1> throwIfHas2() throws X2 {
			X2 x = get2();
			if (x != null)
				throw x;
			return this;
		}

		default void throw2() throws X2, IllegalStateException {
			X2 x = get2();
			if (x != null)
				throw x;
			else
				throw new IllegalStateException("No exception occurred");
		}

		@Override
		Container00<X1, X2> hold1();

		@Override
		default ContainerX0<X1, X2, T1> hold2() {
			return this;
		}
	}

	public interface Container00<X1 extends Throwable, X2 extends Throwable>
		extends ContainerX0<X1, X2, NeverThrown>, Container0X<X1, X2, NeverThrown> {
		@Override
		default boolean hasException() {
			return get1() != null || get2() != null;
		}

		@Override
		Container00<X1, X2> fillStackTrace(boolean fill);

		@Override
		Container00<X1, X2> clear1();

		@Override
		Container00<X1, X2> clear();

		@Override
		default Container00<X1, X2> throwIfHas1() throws X1 {
			Container0X.super.throwIfHas1();
			return this;
		}

		@Override
		default Container00<X1, X2> throwIfHas2() throws X2 {
			ContainerX0.super.throwIfHas2();
			return this;
		}

		default Container00<X1, X2> throwIfHas12() throws X1, X2 {
			throwIfHas1();
			throwIfHas2();
			return this;
		}

		@Override
		default Container00<X1, X2> hold1() {
			return this;
		}

		@Override
		default Container00<X1, X2> hold2() {
			return this;
		}
	}

	public interface Container0XX<X1 extends Throwable, X2 extends Throwable, X3 extends Throwable, T2 extends Throwable, T3 extends Throwable>
		extends Container0X<X1, X2, T2>, Triple<X1, X2, X3, NeverThrown, T2, T3> {
		@Override
		Container0XX<X1, X2, X3, T2, T3> fillStackTrace(boolean fill);

		@Override
		Container0XX<X1, X2, X3, T2, T3> clear1();

		@Override
		Container0XX<X1, X2, X3, T2, T3> clear();

		@Override
		default Container0XX<X1, X2, X3, T2, T3> throwIfHas1() throws X1 {
			Container0X.super.throwIfHas1();
			return this;
		}

		@Override
		default Container0XX<X1, X2, X3, T2, T3> hold1() {
			return this;
		}

		@Override
		Container00X<X1, X2, X3, T3> hold2();

		@Override
		Container0X0<X1, X2, X3, T2> hold3();
	}

	public interface ContainerX0X<X1 extends Throwable, X2 extends Throwable, X3 extends Throwable, T1 extends Throwable, T3 extends Throwable>
		extends ContainerX0<X1, X2, T1>, Triple<X1, X2, X3, T1, NeverThrown, T3> {
		@Override
		ContainerX0X<X1, X2, X3, T1, T3> fillStackTrace(boolean fill);

		@Override
		ContainerX0X<X1, X2, X3, T1, T3> clear2();

		@Override
		ContainerX0X<X1, X2, X3, T1, T3> clear();

		@Override
		default ContainerX0X<X1, X2, X3, T1, T3> throwIfHas2() throws X2 {
			ContainerX0.super.throwIfHas2();
			return this;
		}

		@Override
		Container00X<X1, X2, X3, T3> hold1();

		@Override
		default ContainerX0X<X1, X2, X3, T1, T3> hold2() {
			return this;
		}

		@Override
		ContainerX00<X1, X2, X3, T1> hold3();
	}

	public interface ContainerXX0<X1 extends Throwable, X2 extends Throwable, X3 extends Throwable, T1 extends Throwable, T2 extends Throwable>
		extends Triple<X1, X2, X3, T1, T2, NeverThrown>, ExceptionContainer {
		@Override
		default boolean hasException() {
			return get3() != null;
		}

		@Override
		ContainerXX0<X1, X2, X3, T1, T2> fillStackTrace(boolean fill);

		X3 get3();

		ContainerXX0<X1, X2, X3, T1, T2> clear3();

		@Override
		ContainerXX0<X1, X2, X3, T1, T2> clear();

		default ContainerXX0<X1, X2, X3, T1, T2> throwIfHas3() throws X3 {
			X3 x = get3();
			if (x != null)
				throw x;
			return this;
		}

		default void throw3() throws X3, IllegalStateException {
			X3 x = get3();
			if (x != null)
				throw x;
			else
				throw new IllegalStateException("No exception occurred");
		}

		@Override
		Container0X0<X1, X2, X3, T2> hold1();

		@Override
		ContainerX00<X1, X2, X3, T1> hold2();

		@Override
		default ContainerXX0<X1, X2, X3, T1, T2> hold3() {
			return this;
		}
	}

	public interface Container00X<X1 extends Throwable, X2 extends Throwable, X3 extends Throwable, T3 extends Throwable>
		extends Container00<X1, X2>, Container0XX<X1, X2, X3, NeverThrown, T3>, ContainerX0X<X1, X2, X3, NeverThrown, T3> {
		@Override
		Container00X<X1, X2, X3, T3> fillStackTrace(boolean fill);

		@Override
		Container00X<X1, X2, X3, T3> clear1();

		@Override
		Container00X<X1, X2, X3, T3> clear2();

		@Override
		Container00X<X1, X2, X3, T3> clear();

		@Override
		default Container00X<X1, X2, X3, T3> throwIfHas1() throws X1 {
			Container00.super.throwIfHas1();
			return this;
		}

		@Override
		default Container00X<X1, X2, X3, T3> throwIfHas2() throws X2 {
			Container00.super.throwIfHas2();
			return this;
		}

		@Override
		default Container00X<X1, X2, X3, T3> throwIfHas12() throws X1, X2 {
			Container00.super.throwIfHas12();
			return this;
		}

		@Override
		default Container00X<X1, X2, X3, T3> hold1() {
			return this;
		}

		@Override
		default Container00X<X1, X2, X3, T3> hold2() {
			return this;
		}

		@Override
		Container000<X1, X2, X3> hold3();
	}

	public interface Container0X0<X1 extends Throwable, X2 extends Throwable, X3 extends Throwable, T2 extends Throwable>
		extends Container0X<X1, X2, T2>, Container0XX<X1, X2, X3, T2, NeverThrown>, ContainerXX0<X1, X2, X3, NeverThrown, T2> {
		@Override
		default boolean hasException() {
			return get1() != null || get3() != null;
		}

		@Override
		Container0X0<X1, X2, X3, T2> fillStackTrace(boolean fill);

		@Override
		Container0X0<X1, X2, X3, T2> clear1();

		@Override
		Container0X0<X1, X2, X3, T2> clear3();

		@Override
		Container0X0<X1, X2, X3, T2> clear();

		@Override
		default Container0X0<X1, X2, X3, T2> throwIfHas1() throws X1 {
			Container0XX.super.throwIfHas1();
			return this;
		}

		@Override
		default Container0X0<X1, X2, X3, T2> throwIfHas3() throws X3 {
			ContainerXX0.super.throwIfHas3();
			return this;
		}

		default Container0X0<X1, X2, X3, T2> throwIfHas13() throws X1, X3 {
			throwIfHas1();
			throwIfHas3();
			return this;
		}

		@Override
		default Container0X0<X1, X2, X3, T2> hold1() {
			return this;
		}

		@Override
		Container000<X1, X2, X3> hold2();

		@Override
		default Container0X0<X1, X2, X3, T2> hold3() {
			return this;
		}
	}

	public interface ContainerX00<X1 extends Throwable, X2 extends Throwable, X3 extends Throwable, T1 extends Throwable>
		extends ContainerXX0<X1, X2, X3, T1, NeverThrown>, ContainerX0X<X1, X2, X3, T1, NeverThrown> {
		@Override
		default boolean hasException() {
			return get2() != null || get3() != null;
		}

		@Override
		ContainerX00<X1, X2, X3, T1> fillStackTrace(boolean fill);

		@Override
		ContainerX00<X1, X2, X3, T1> clear2();

		@Override
		ContainerX00<X1, X2, X3, T1> clear3();

		@Override
		ContainerX00<X1, X2, X3, T1> clear();

		@Override
		default ContainerX00<X1, X2, X3, T1> throwIfHas2() throws X2 {
			ContainerX0X.super.throwIfHas2();
			return this;
		}

		@Override
		default ContainerX00<X1, X2, X3, T1> throwIfHas3() throws X3 {
			ContainerXX0.super.throwIfHas3();
			return this;
		}

		default ContainerX00<X1, X2, X3, T1> throwIfHas23() throws X2, X3 {
			throwIfHas2();
			throwIfHas3();
			return this;
		}

		@Override
		Container000<X1, X2, X3> hold1();

		@Override
		default ContainerX00<X1, X2, X3, T1> hold2() {
			return this;
		}

		@Override
		default ContainerX00<X1, X2, X3, T1> hold3() {
			return this;
		}
	}

	public interface Container000<X1 extends Throwable, X2 extends Throwable, X3 extends Throwable>
		extends Container00X<X1, X2, X3, NeverThrown>, Container0X0<X1, X2, X3, NeverThrown>, ContainerX00<X1, X2, X3, NeverThrown> {
		@Override
		default boolean hasException() {
			return get1() != null || get2() != null || get3() != null;
		}

		@Override
		Container000<X1, X2, X3> fillStackTrace(boolean fill);

		@Override
		Container000<X1, X2, X3> clear1();

		@Override
		Container000<X1, X2, X3> clear2();

		@Override
		Container000<X1, X2, X3> clear3();

		@Override
		Container000<X1, X2, X3> clear();

		@Override
		default Container000<X1, X2, X3> throwIfHas3() throws X3 {
			Container0X0.super.throwIfHas3();
			return this;
		}

		@Override
		default Container000<X1, X2, X3> throwIfHas13() throws X1, X3 {
			Container0X0.super.throwIfHas13();
			return this;
		}

		@Override
		default Container000<X1, X2, X3> throwIfHas1() throws X1 {
			Container00X.super.throwIfHas1();
			return this;
		}

		@Override
		default Container000<X1, X2, X3> throwIfHas2() throws X2 {
			Container00X.super.throwIfHas2();
			return this;
		}

		@Override
		default Container000<X1, X2, X3> throwIfHas12() throws X1, X2 {
			Container00X.super.throwIfHas12();
			return this;
		}

		default Container000<X1, X2, X3> throwIfHas123() throws X1, X2, X3 {
			throwIfHas1();
			throwIfHas12();
			throwIfHas123();
			return this;
		}

		@Override
		default Container000<X1, X2, X3> hold1() {
			return this;
		}

		@Override
		default Container000<X1, X2, X3> hold2() {
			return this;
		}

		@Override
		default Container000<X1, X2, X3> hold3() {
			return this;
		}
	}

	private static class Thrower1<X extends Throwable> implements Single<X, X> {
		@Override
		public void handle1(X exception) throws X {
			throw exception;
		}

		@Override
		public Container0<X> hold1() {
			return new Container0Impl<>(1);
		}
	}

	static class Thrower2<X1 extends Throwable, X2 extends Throwable> extends Thrower1<X1> implements Double<X1, X2, X1, X2> {
		@Override
		public void handle2(X2 exception) throws X2 {
			throw exception;
		}

		@Override
		public Container0X<X1, X2, X2> hold1() {
			return new Container0XImpl<>(2);
		}

		@Override
		public ContainerX0<X1, X2, X1> hold2() {
			return new ContainerX0Impl<>(2);
		}
	}

	static class Thrower3<X1 extends Throwable, X2 extends Throwable, X3 extends Throwable> extends Thrower2<X1, X2>
		implements Triple<X1, X2, X3, X1, X2, X3> {
		@Override
		public void handle3(X3 exception) throws X3 {
			throw exception;
		}

		@Override
		public Container0XX<X1, X2, X3, X2, X3> hold1() {
			return new Container0XXImpl<>(1);
		}

		@Override
		public ContainerX0X<X1, X2, X3, X1, X3> hold2() {
			return new ContainerX0XImpl<>(1);
		}

		@Override
		public ContainerXX0<X1, X2, X3, X1, X2> hold3() {
			return new ContainerXX0Impl<>(1);
		}
	}

	private static class AbstractContainer implements ExceptionContainer {
		private final Throwable[] theExceptions;
		private boolean isFillStackTrace;

		AbstractContainer(int size) {
			theExceptions = new Throwable[size];
		}

		@Override
		public boolean hasException() {
			for (Throwable ex : theExceptions) {
				if (ex != null)
					return true;
			}
			return false;
		}

		@Override
		public ExceptionContainer clear() {
			for (int i = 0; i < theExceptions.length; i++)
				theExceptions[i] = null;
			return this;
		}

		@Override
		public ExceptionContainer fillStackTrace(boolean fill) {
			isFillStackTrace = fill;
			return this;
		}

		void handle(int index, Throwable exception) {
			if (exception == null)
				throw new NullPointerException("Cannot handle a null exception");
			if (hasException()) {
				IllegalStateException ise = new IllegalStateException("Cannot handle multiple exceptions", exception);
				for (Throwable e : theExceptions) {
					if (e != null)
						ise.addSuppressed(e);
				}
				throw ise;
			}
			theExceptions[index] = exception;
			if (isFillStackTrace)
				exception.fillInStackTrace();
		}

		Throwable get(int index) {
			return theExceptions[index];
		}

		void clear(int index) {
			theExceptions[index] = null;
		}
	}

	private static class Container0Impl<X extends Throwable> extends AbstractContainer implements Container0<X> {
		Container0Impl(int size) {
			super(size);
		}

		@Override
		public Container0Impl<X> fillStackTrace(boolean fill) {
			super.fillStackTrace(fill);
			return this;
		}

		@Override
		public void handle1(X exception) {
			super.handle(0, exception);
		}

		@Override
		public X get1() {
			return (X) super.get(0);
		}

		@Override
		public Container0<X> clear1() {
			super.clear(0);
			return this;
		}

		@Override
		public Container0<X> clear() {
			super.clear();
			return this;
		}
	}

	private static class Container0XImpl<X1 extends Throwable, X2 extends Throwable> extends Container0Impl<X1>
		implements Container0X<X1, X2, X2> {

		Container0XImpl(int size) {
			super(size);
		}

		@Override
		public void handle2(X2 exception) throws X2 {
			throw exception;
		}

		@Override
		public Container0XImpl<X1, X2> fillStackTrace(boolean fill) {
			super.fillStackTrace(fill);
			return this;
		}

		@Override
		public Container0XImpl<X1, X2> clear1() {
			super.clear1();
			return this;
		}

		@Override
		public Container0X<X1, X2, X2> clear() {
			super.clear();
			return this;
		}

		@Override
		public Container00<X1, X2> hold2() {
			return new Container00Impl<>(2);
		}
	}

	private static class ContainerX0Impl<X1 extends Throwable, X2 extends Throwable> extends AbstractContainer
		implements ContainerX0<X1, X2, X1> {
		ContainerX0Impl(int size) {
			super(size);
		}

		@Override
		public void handle1(X1 exception) throws X1 {
			throw exception;
		}

		@Override
		public void handle2(X2 exception) throws NeverThrown {
			super.handle(0, exception);
		}

		@Override
		public X2 get2() {
			return (X2) super.get(0);
		}

		@Override
		public ContainerX0Impl<X1, X2> fillStackTrace(boolean fill) {
			super.fillStackTrace(fill);
			return this;
		}

		@Override
		public ContainerX0<X1, X2, X1> clear2() {
			super.clear(0);
			return this;
		}

		@Override
		public ContainerX0<X1, X2, X1> clear() {
			super.clear();
			return this;
		}

		@Override
		public Container00<X1, X2> hold1() {
			return new Container00Impl<>(2);
		}
	}

	private static class Container00Impl<X1 extends Throwable, X2 extends Throwable> extends Container0Impl<X1>
		implements Container00<X1, X2> {
		Container00Impl(int size) {
			super(size);
		}

		@Override
		public X2 get2() {
			return (X2) super.get(1);
		}

		@Override
		public Container00Impl<X1, X2> fillStackTrace(boolean fill) {
			super.fillStackTrace(fill);
			return this;
		}

		@Override
		public Container00<X1, X2> clear1() {
			super.clear1();
			return this;
		}

		@Override
		public ContainerX0<X1, X2, NeverThrown> clear2() {
			super.clear(1);
			return this;
		}

		@Override
		public Container00<X1, X2> clear() {
			super.clear();
			return this;
		}

		@Override
		public void handle2(X2 exception) throws NeverThrown {
			super.handle(1, exception);
		}
	}

	public static class Container0XXImpl<X1 extends Throwable, X2 extends Throwable, X3 extends Throwable> extends Container0XImpl<X1, X2>
		implements Container0XX<X1, X2, X3, X2, X3> {
		Container0XXImpl(int size) {
			super(size);
		}

		@Override
		public void handle3(X3 exception) throws X3 {
			throw exception;
		}

		@Override
		public Container0XXImpl<X1, X2, X3> fillStackTrace(boolean fill) {
			super.fillStackTrace(fill);
			return this;
		}

		@Override
		public Container0XXImpl<X1, X2, X3> clear1() {
			super.clear1();
			return this;
		}

		@Override
		public Container0XX<X1, X2, X3, X2, X3> clear() {
			super.clear();
			return this;
		}

		@Override
		public Container00X<X1, X2, X3, X3> hold2() {
			return new Container00XImpl<>(2);
		}

		@Override
		public Container0X0<X1, X2, X3, X2> hold3() {
			return new Container0X0Impl<>(2);
		}
	}

	public static class ContainerX0XImpl<X1 extends Throwable, X2 extends Throwable, X3 extends Throwable> extends ContainerX0Impl<X1, X2>
		implements ContainerX0X<X1, X2, X3, X1, X3> {
		ContainerX0XImpl(int size) {
			super(size);
		}

		@Override
		public void handle3(X3 exception) throws X3 {
			throw exception;
		}

		@Override
		public ContainerX0XImpl<X1, X2, X3> fillStackTrace(boolean fill) {
			super.fillStackTrace(fill);
			return this;
		}

		@Override
		public ContainerX0XImpl<X1, X2, X3> clear2() {
			super.clear2();
			return this;
		}

		@Override
		public ContainerX0X<X1, X2, X3, X1, X3> clear() {
			super.clear();
			return this;
		}

		@Override
		public Container00X<X1, X2, X3, X3> hold1() {
			return new Container00XImpl<>(2);
		}

		@Override
		public ContainerX00<X1, X2, X3, X1> hold3() {
			return new ContainerX00Impl<>(2);
		}
	}

	public static class ContainerXX0Impl<X1 extends Throwable, X2 extends Throwable, X3 extends Throwable> extends AbstractContainer
		implements ContainerXX0<X1, X2, X3, X1, X2> {
		ContainerXX0Impl(int size) {
			super(size);
		}

		@Override
		public void handle1(X1 exception) throws X1 {
			throw exception;
		}

		@Override
		public void handle2(X2 exception) throws X2 {
			throw exception;
		}

		@Override
		public void handle3(X3 exception) {
			super.handle(0, exception);
		}

		@Override
		public ContainerXX0Impl<X1, X2, X3> fillStackTrace(boolean fill) {
			super.fillStackTrace(fill);
			return this;
		}

		@Override
		public X3 get3() {
			return (X3) super.get(0);
		}

		@Override
		public ContainerXX0<X1, X2, X3, X1, X2> clear3() {
			super.clear(0);
			return this;
		}

		@Override
		public ContainerXX0<X1, X2, X3, X1, X2> clear() {
			super.clear();
			return this;
		}

		@Override
		public Container0X0<X1, X2, X3, X2> hold1() {
			return new Container0X0Impl<>(2);
		}

		@Override
		public ContainerX00<X1, X2, X3, X1> hold2() {
			return new ContainerX00Impl<>(2);
		}
	}

	public static class Container00XImpl<X1 extends Throwable, X2 extends Throwable, X3 extends Throwable> extends Container00Impl<X1, X2>
		implements Container00X<X1, X2, X3, X3> {
		Container00XImpl(int size) {
			super(size);
		}

		@Override
		public void handle3(X3 exception) throws X3 {
			throw exception;
		}

		@Override
		public Container00XImpl<X1, X2, X3> fillStackTrace(boolean fill) {
			super.fillStackTrace(fill);
			return this;
		}

		@Override
		public Container00XImpl<X1, X2, X3> clear1() {
			super.clear1();
			return this;
		}

		@Override
		public Container00XImpl<X1, X2, X3> clear2() {
			super.clear2();
			return this;
		}

		@Override
		public Container00X<X1, X2, X3, X3> clear() {
			super.clear();
			return this;
		}

		@Override
		public Container000<X1, X2, X3> hold3() {
			return new Container000Impl<>(3);
		}
	}

	public static class Container0X0Impl<X1 extends Throwable, X2 extends Throwable, X3 extends Throwable> extends Container0XImpl<X1, X2>
		implements Container0X0<X1, X2, X3, X2> {
		Container0X0Impl(int size) {
			super(size);
		}

		@Override
		public X3 get3() {
			return (X3) super.get(1);
		}

		@Override
		public void handle3(X3 exception) throws NeverThrown {
			super.handle(1, exception);
		}

		@Override
		public Container0X0Impl<X1, X2, X3> fillStackTrace(boolean fill) {
			super.fillStackTrace(fill);
			return this;
		}

		@Override
		public Container0X0Impl<X1, X2, X3> clear1() {
			super.clear1();
			return this;
		}

		@Override
		public Container0X0<X1, X2, X3, X2> clear3() {
			super.clear(1);
			return this;
		}

		@Override
		public Container0X0<X1, X2, X3, X2> clear() {
			super.clear();
			return this;
		}

		@Override
		public Container000<X1, X2, X3> hold2() {
			return new Container000Impl<>(3);
		}
	}

	public static class ContainerX00Impl<X1 extends Throwable, X2 extends Throwable, X3 extends Throwable> extends ContainerX0Impl<X1, X2>
		implements ContainerX00<X1, X2, X3, X1> {
		ContainerX00Impl(int size) {
			super(size);
		}

		@Override
		public X3 get3() {
			return (X3) super.get(1);
		}

		@Override
		public void handle3(X3 exception) throws NeverThrown {
			super.handle(1, exception);
		}

		@Override
		public ContainerX00Impl<X1, X2, X3> fillStackTrace(boolean fill) {
			super.fillStackTrace(fill);
			return this;
		}

		@Override
		public ContainerX00Impl<X1, X2, X3> clear2() {
			super.clear2();
			return this;
		}

		@Override
		public ContainerX00<X1, X2, X3, X1> clear3() {
			super.clear(1);
			return this;
		}

		@Override
		public ContainerX00<X1, X2, X3, X1> clear() {
			super.clear();
			return this;
		}

		@Override
		public Container000<X1, X2, X3> hold1() {
			return new Container000Impl<>(3);
		}
	}

	public static class Container000Impl<X1 extends Throwable, X2 extends Throwable, X3 extends Throwable> extends Container00Impl<X1, X2>
		implements Container000<X1, X2, X3> {
		Container000Impl(int size) {
			super(size);
		}

		@Override
		public X3 get3() {
			return (X3) super.get(2);
		}

		@Override
		public void handle3(X3 exception) throws NeverThrown {
			super.handle(2, exception);
		}

		@Override
		public Container000Impl<X1, X2, X3> fillStackTrace(boolean fill) {
			super.fillStackTrace(fill);
			return this;
		}

		@Override
		public Container000Impl<X1, X2, X3> clear1() {
			super.clear1();
			return this;
		}

		@Override
		public Container000Impl<X1, X2, X3> clear2() {
			super.clear1();
			return this;
		}

		@Override
		public Container000<X1, X2, X3> clear3() {
			super.clear(2);
			return this;
		}

		@Override
		public Container000<X1, X2, X3> clear() {
			super.clear();
			return this;
		}
	}
}

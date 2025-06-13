package org.qommons.ex;

import java.util.Objects;
import java.util.function.Consumer;

import org.qommons.LambdaUtils;

/**
 * A consumer that may throw an exception
 * 
 * @param <T> The type of value to consume
 * @param <X> The type of exception to throw
 */
public interface ExConsumer<T, X extends Throwable> {
	/** Does nothing */
	public static final ExConsumer<Object, NeverThrown> DO_NOTHING = new ExConsumer<Object, NeverThrown>() {
		@Override
		public void accept(Object value) {
		}

		@Override
		public String toString() {
			return "DoNothing";
		}
	};

	/**
	 * @param <T> The type to consume
	 * @param <X> The type to (not) throw
	 * @return A consumer that does nothing
	 */
	public static <T, X extends Throwable> ExConsumer<T, X> doNothing() {
		return (ExConsumer<T, X>) DO_NOTHING;
	}

	/**
	 * @param value The value to consume
	 * @throws X If an error occurs
	 */
	void accept(T value) throws X;

	/**
	 * @param arg The argument for the consumer
	 * @return A runnable that always supplies the given value for the argument to this consumer
	 */
	default ExRunnable<X> curry(T arg) {
		return curry(LambdaUtils.constantExSupplier(arg));
	}

	/**
	 * @param arg Provides the argument for the consumer
	 * @return A runnable that supplies the value returned from the given supplier for the argument to this consumer
	 */
	default ExRunnable<X> curry(ExSupplier<? extends T, ? extends X> arg) {
		return new ArgCurried<>(this, arg);
	}

	/** @return A {@link Consumer} that calls this consumer, wrapping any checked exceptions with {@link CheckedExceptionWrapper} */
	default Consumer<T> unsafe() {
		return value -> {
			try {
				ExConsumer.this.accept(value);
			} catch (RuntimeException | Error e) {
				throw e;
			} catch (Throwable e) {
				throw new CheckedExceptionWrapper(e);
			}
		};
	}

	/**
	 * @param <T> The type to accept
	 * @param <E> The exception type
	 * @param s The consumer to wrap
	 * @return An {@link ExConsumer} that calls the given consumer and never throws any checked exceptions
	 */
	static <T, E extends Throwable> ExConsumer<T, E> wrap(Consumer<T> s) {
		if (s == null)
			return null;
		return LambdaUtils.printableExConsumer(s::accept, s::toString, s);
	}

	/**
	 * Implements {@link ExFunction#unsafe()}
	 * 
	 * @param <T> The argument type
	 * @param <X> The throwable type
	 */
	class Unsafe<T, X extends Throwable> implements Consumer<T> {
		private ExConsumer<T, X> theFunction;

		public Unsafe(ExConsumer<T, X> function) {
			theFunction = function;
		}

		@Override
		public void accept(T value) {
			try {
				theFunction.accept(value);
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
				return obj instanceof Unsafe && theFunction.equals(((Unsafe<?, ?>) obj).theFunction);
		}

		@Override
		public String toString() {
			return theFunction.toString();
		}
	}

	/**
	 * Implements {@link ExConsumer#curry(ExSupplier)}
	 * 
	 * @param <T> The type of the consumer argument
	 * @param <X> The type of exception throwable by the consumer
	 */
	class ArgCurried<T, X extends Throwable> implements ExRunnable<X> {
		private final ExConsumer<T, X> theFunction;
		private final ExSupplier<? extends T, ? extends X> theArg;

		public ArgCurried(ExConsumer<T, X> function, ExSupplier<? extends T, ? extends X> arg) {
			theFunction = function;
			theArg = arg;
		}

		@Override
		public void run() throws X {
			T arg = theArg.get();
			theFunction.accept(arg);
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
			ArgCurried<?, ?> other = (ArgCurried<?, ?>) obj;
			return theFunction.equals(other.theFunction) && theArg.equals(other.theArg);
		}

		@Override
		public String toString() {
			return theFunction + ".curry(" + theArg + ")";
		}
	}
}

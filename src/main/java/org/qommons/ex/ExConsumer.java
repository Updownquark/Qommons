package org.qommons.ex;

import java.util.function.Consumer;

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
		return value -> s.accept(value);
	}
}

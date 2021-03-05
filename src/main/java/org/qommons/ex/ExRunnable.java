package org.qommons.ex;

/**
 * A runnable that may throw an exception
 * 
 * @param <X> The type of exception to throw
 */
public interface ExRunnable<X extends Throwable> {
	/**
	 * Performs an action
	 * @throws X If an error occurs
	 */
	void run() throws X;

	/** @return A {@link Runnable} that calls this runnable, wrapping any checked exceptions with {@link CheckedExceptionWrapper} */
	default Runnable unsafe() {
		return () -> {
			try {
				ExRunnable.this.run();
			} catch (RuntimeException | Error e) {
				throw e;
			} catch (Throwable e) {
				throw new CheckedExceptionWrapper(e);
			}
		};
	}

	/**
	 * @param <E> The exception type
	 * @param s The runnable to wrap
	 * @return An {@link ExRunnable} that calls the given runnable and never throws any checked exceptions
	 */
	static <E extends Throwable> ExRunnable<E> wrap(Runnable s) {
		return () -> s.run();
	}
}

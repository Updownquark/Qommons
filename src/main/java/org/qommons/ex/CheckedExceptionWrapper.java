package org.qommons.ex;

/** A RuntimeException wrapping a checked {@link Throwable} */
public class CheckedExceptionWrapper extends RuntimeException {
	/** @param ex The checked exception to wrap */
	public CheckedExceptionWrapper(Throwable ex) {
		super(ex);
	}

	/**
	 * @param <X> The type of the cause to get
	 * @param ex The checked exception wrapper to get the cause of
	 * @param exType The type of the type of the cause to get
	 * @return The cause of the checked wrapper if it is of the given type
	 * @throws CheckedExceptionWrapper The exception wrapper if the cause is not of the given type
	 */
	public static <X extends Throwable> X getThrowable(CheckedExceptionWrapper ex, Class<X> exType) throws CheckedExceptionWrapper {
		if (exType.isInstance(ex.getCause()))
			return (X) ex.getCause();
		else
			throw ex;
	}
}

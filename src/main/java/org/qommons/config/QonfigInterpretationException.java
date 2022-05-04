package org.qommons.config;

/** An exception as the result of errors during interpretation of a Qonfig document */
public class QonfigInterpretationException extends Exception {
	/**
	 * @param message The message for the exception
	 * @param cause The cause of the exception
	 */
	public QonfigInterpretationException(String message, Throwable cause) {
		super(message, cause);
	}

	/** @param message The message for the exception */
	public QonfigInterpretationException(String message) {
		super(message);
	}

	/** @param cause The cause of the exception */
	public QonfigInterpretationException(Throwable cause) {
		super(cause);
	}
}
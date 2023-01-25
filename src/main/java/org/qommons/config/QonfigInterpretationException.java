package org.qommons.config;

/** An exception as the result of errors during interpretation of a Qonfig document */
public class QonfigInterpretationException extends QonfigException {
	/** @see QonfigException#QonfigException(String, Throwable, QonfigFilePosition, int) */
	public QonfigInterpretationException(String message, Throwable cause, QonfigFilePosition position, int errorLength) {
		super(message, cause, position, errorLength);
	}

	/** @see QonfigException#QonfigException(String, QonfigFilePosition, int) */
	public QonfigInterpretationException(String message, QonfigFilePosition position, int errorLength) {
		super(message, position, errorLength);
	}

	/** @see QonfigException#QonfigException(Throwable, QonfigFilePosition, int) */
	public QonfigInterpretationException(Throwable cause, QonfigFilePosition position, int errorLength) {
		super(cause, position, errorLength);
	}
}
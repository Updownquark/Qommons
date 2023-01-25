package org.qommons.config;

public class QonfigEvaluationException extends QonfigException {
	/** @see QonfigException#QonfigException(String, Throwable, QonfigFilePosition, int) */
	public QonfigEvaluationException(String message, Throwable cause, QonfigFilePosition position, int errorLength) {
		super(message, cause, position, errorLength);
	}

	/** @see QonfigException#QonfigException(String, QonfigFilePosition, int) */
	public QonfigEvaluationException(String message, QonfigFilePosition position, int errorLength) {
		super(message, position, errorLength);
	}

	/** @see QonfigException#QonfigException(Throwable, QonfigFilePosition, int) */
	public QonfigEvaluationException(Throwable cause, QonfigFilePosition position, int errorLength) {
		super(cause, position, errorLength);
	}
}

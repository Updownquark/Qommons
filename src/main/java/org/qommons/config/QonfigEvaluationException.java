package org.qommons.config;

import org.qommons.io.LocatedFilePosition;

/** Thrown from Qonfig {@link QonfigInterpreterCore interpretation} if an error occurs parsing a structure from its {@link QonfigElement} */
public class QonfigEvaluationException extends QonfigException {
	/** @see QonfigException#QonfigException(String, LocatedFilePosition, int, Throwable) */
	public QonfigEvaluationException(String message, Throwable cause, LocatedFilePosition position, int errorLength) {
		super(message, position, errorLength, cause);
	}

	/** @see QonfigException#QonfigException(String, LocatedFilePosition, int) */
	public QonfigEvaluationException(String message, LocatedFilePosition position, int errorLength) {
		super(message, position, errorLength);
	}

	/** @see QonfigException#QonfigException(LocatedFilePosition, int, Throwable) */
	public QonfigEvaluationException(Throwable cause, LocatedFilePosition position, int errorLength) {
		super(position, errorLength, cause);
	}
}

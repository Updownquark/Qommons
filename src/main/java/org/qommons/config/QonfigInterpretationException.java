package org.qommons.config;

import org.qommons.io.LocatedFilePosition;

/** An exception as the result of errors during interpretation of a Qonfig document */
public class QonfigInterpretationException extends QonfigException {
	/** @see QonfigException#QonfigException(String, LocatedFilePosition, int, Throwable) */
	public QonfigInterpretationException(String message, LocatedFilePosition position, int errorLength, Throwable cause) {
		super(message, position, errorLength, cause);
	}

	/** @see QonfigException#QonfigException(String, LocatedFilePosition, int) */
	public QonfigInterpretationException(String message, LocatedFilePosition position, int errorLength) {
		super(message, position, errorLength);
	}

	/** @see QonfigException#QonfigException(LocatedFilePosition, int, Throwable) */
	public QonfigInterpretationException(LocatedFilePosition position, int errorLength, Throwable cause) {
		super(position, errorLength, cause);
	}
}
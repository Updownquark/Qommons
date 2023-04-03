package org.qommons.config;

import org.qommons.io.LocatedFilePosition;
import org.qommons.io.TextParseException;

public abstract class QonfigException extends TextParseException {
	private final int theErrorLength;

	/**
	 * @param message The message for the exception
	 * @param cause The cause of the exception
	 */
	public QonfigException(String message, LocatedFilePosition position, int errorLength, Throwable cause) {
		super(message, position, cause);
		theErrorLength = errorLength;
	}

	/** @param message The message for the exception */
	public QonfigException(String message, LocatedFilePosition position, int errorLength) {
		super(message, position);
		theErrorLength = errorLength;
	}

	/** @param cause The cause of the exception */
	public QonfigException(LocatedFilePosition position, int errorLength, Throwable cause) {
		super(cause.getMessage(), position, cause);
		theErrorLength = errorLength;
	}

	@Override
	public LocatedFilePosition getPosition() {
		return (LocatedFilePosition) super.getPosition();
	}

	public int getErrorLength() {
		return theErrorLength;
	}
}

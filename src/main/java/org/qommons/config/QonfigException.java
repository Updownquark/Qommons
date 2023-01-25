package org.qommons.config;

public abstract class QonfigException extends Exception {
	private final QonfigFilePosition thePosition;
	private final int theErrorLength;

	/**
	 * @param message The message for the exception
	 * @param cause The cause of the exception
	 */
	public QonfigException(String message, Throwable cause, QonfigFilePosition position, int errorLength) {
		super(position == null ? message : position + ": " + message, cause);
		thePosition = position;
		theErrorLength = errorLength;
	}

	/** @param message The message for the exception */
	public QonfigException(String message, QonfigFilePosition position, int errorLength) {
		super(position == null ? message : position + ": " + message);
		thePosition = position;
		theErrorLength = errorLength;
	}

	/** @param cause The cause of the exception */
	public QonfigException(Throwable cause, QonfigFilePosition position, int errorLength) {
		super(cause);
		thePosition = position;
		theErrorLength = errorLength;
	}

	public QonfigFilePosition getPosition() {
		return thePosition;
	}

	public int getErrorLength() {
		return theErrorLength;
	}
}

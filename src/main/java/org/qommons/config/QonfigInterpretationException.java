package org.qommons.config;

import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;

/** An exception as the result of errors during interpretation of a Qonfig document */
public class QonfigInterpretationException extends QonfigException {
	/**
	 * @param message The message for the exception
	 * @param position The position in the file where the exception occurred
	 * @param errorLength The length of the text where the exception occurred
	 * @param cause The cause of the exception
	 * @see QonfigException#QonfigException(String, LocatedFilePosition, int, Throwable)
	 */
	public QonfigInterpretationException(String message, LocatedFilePosition position, int errorLength, Throwable cause) {
		super(message, position, errorLength, cause);
	}

	/**
	 * @param message The message for the exception
	 * @param position The position in the file where the exception occurred
	 * @param errorLength The length of the text where the exception occurred
	 * @see QonfigException#QonfigException(String, LocatedFilePosition, int)
	 */
	public QonfigInterpretationException(String message, LocatedFilePosition position, int errorLength) {
		super(message, position, errorLength);
	}

	/**
	 * @param cause The cause of the exception
	 * @param position The position in the file where the exception occurred
	 * @param errorLength The length of the text where the exception occurred
	 * @see QonfigException#QonfigException(LocatedFilePosition, int, Throwable)
	 */
	public QonfigInterpretationException(LocatedFilePosition position, int errorLength, Throwable cause) {
		super(position, errorLength, cause);
	}

	/**
	 * @param message The message describing the cause of the exception
	 * @param content The source location where the error occurred
	 */
	public QonfigInterpretationException(String message, LocatedPositionedContent content) {
		this(message, content.getPosition(0), content.length());
	}

	/**
	 * @param message The message describing the cause of the exception
	 * @param content The source location where the error occurred
	 * @param cause The throwable cause of the exception
	 */
	public QonfigInterpretationException(String message, LocatedPositionedContent content, Throwable cause) {
		this(message, content.getPosition(0), content.length(), cause);
	}
}
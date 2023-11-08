package org.qommons.io;

import java.text.ParseException;

/** A ParseException for multi-line character sequences */
public class TextParseException extends ParseException {
	private final FilePosition thePosition;

	/**
	 * @param s The message for the exception
	 * @param errorOffset The character offset of the error in the sequence
	 * @param lineNumber The line number of the error in the sequence, offset from zero
	 * @param columnNumber The character number of the error in the line, offset from zero
	 */
	public TextParseException(String s, int errorOffset, int lineNumber, int columnNumber) {
		super(s, errorOffset);
		thePosition = new FilePosition(errorOffset, lineNumber, columnNumber);
	}

	/**
	 * @param s The message for the exception
	 * @param errorOffset The character offset of the error in the sequence
	 * @param lineNumber The line number of the error in the sequence, offset from zero
	 * @param columnNumber The character number of the error in the line, offset from zero
	 * @param cause The cause of the exception
	 */
	public TextParseException(String s, int errorOffset, int lineNumber, int columnNumber, Throwable cause) {
		super(s, errorOffset);
		initCause(cause);
		thePosition = new FilePosition(errorOffset, lineNumber, columnNumber);
	}

	/**
	 * @param s The message for the exception
	 * @param position The position in the sequence
	 */
	public TextParseException(String s, FilePosition position) {
		super(s, position == null ? 0 : position.getPosition());
		thePosition = position;
	}

	/**
	 * @param s The message for the exception
	 * @param position The position in the sequence
	 * @param cause The cause of the exception
	 */
	public TextParseException(String s, FilePosition position, Throwable cause) {
		super(s, position == null ? 0 : position.getPosition());
		initCause(cause);
		thePosition = position;
	}

	/**
	 * @param s The message for the exception
	 * @param cause The cause of the exception
	 */
	public TextParseException(String s, TextParseException cause) {
		super(s, cause.getErrorOffset());
		initCause(cause);
		thePosition = cause.getPosition();
	}

	/** @return The position of the source of the error in the file */
	public FilePosition getPosition() {
		return thePosition;
	}

	/** @return The line number of the error in the sequence, offset from zero */
	public int getLineNumber() {
		return thePosition.getLineNumber();
	}

	/** @return The character number of the error in the line, offset from zero */
	public int getColumnNumber() {
		return thePosition.getCharNumber();
	}

	@Override
	public String toString() {
		if (thePosition != null)
			return new StringBuilder().append(thePosition).append(":\n").append(super.toString()).toString();
		else
			return super.toString();
	}
}
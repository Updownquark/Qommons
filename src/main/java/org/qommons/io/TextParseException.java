package org.qommons.io;

import java.text.ParseException;

/** A ParseException for multi-line character sequences */
public class TextParseException extends ParseException {
	private final int theLineNumber;
	private final int theColumnNumber;

	/**
	 * @param s The message for the exception
	 * @param errorOffset The character offset of the error in the sequence
	 * @param lineNumber The line number of the error in the sequence, offset from zero
	 * @param columnNumber The character number of the error in the line, offset from zero
	 */
	public TextParseException(String s, int errorOffset, int lineNumber, int columnNumber) {
		super(s, errorOffset);
		theLineNumber = lineNumber;
		theColumnNumber = columnNumber;
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
		theLineNumber = lineNumber;
		theColumnNumber = columnNumber;
	}

	/**
	 * @param s The message for the exception
	 * @param cause The cause of the exception
	 */
	public TextParseException(String s, TextParseException cause) {
		super(s, cause.getErrorOffset());
		initCause(cause);
		theLineNumber = cause.getLineNumber();
		theColumnNumber = cause.getColumnNumber();
	}

	/** @return The line number of the error in the sequence, offset from zero */
	public int getLineNumber() {
		return theLineNumber;
	}

	/** @return The character number of the error in the line, offset from zero */
	public int getColumnNumber() {
		return theColumnNumber;
	}

	@Override
	public String toString() {
		return new StringBuilder("Line ").append(theLineNumber + 1).append(" Col ").append(theColumnNumber + 1).append(": ")
			.append(getMessage()).toString();
	}
}
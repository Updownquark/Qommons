package org.qommons.io;

import java.text.ParseException;

/** A ParseException for multi-line character sequences */
public class TextParseException extends ParseException {
	private final int theLineNumber;
	private final int theColumnNumber;

	/**
	 * @param s The message for the exception
	 * @param errorOffset The character offset of the error in the sequence
	 * @param lineNumber The line number of the error in the sequence
	 * @param columnNumber The character number of the error in the line
	 */
	public TextParseException(String s, int errorOffset, int lineNumber, int columnNumber) {
		super(s, errorOffset);
		theLineNumber = lineNumber;
		theColumnNumber = columnNumber;
	}

	/** @return The line number of the error in the sequence */
	public int getLineNumber() {
		return theLineNumber;
	}

	/** @return The character number of the error in the line */
	public int getColumnNumber() {
		return theColumnNumber;
	}

	@Override
	public String toString() {
		return new StringBuilder("Line ").append(theLineNumber + 1).append(" Col ").append(theColumnNumber + 1).append(": ")
			.append(getMessage()).toString();
	}
}
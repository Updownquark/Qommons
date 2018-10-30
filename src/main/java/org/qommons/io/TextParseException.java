package org.qommons.io;

import java.text.ParseException;

public class TextParseException extends ParseException {
	private final int theLineNumber;
	private final int theColumnNumber;

	public TextParseException(String s, int errorOffset, int lineNumber, int columnNumber) {
		super(s, errorOffset);
		theLineNumber = lineNumber;
		theColumnNumber = columnNumber;
	}

	public int getLineNumber() {
		return theLineNumber;
	}

	public int getColumnNumber() {
		return theColumnNumber;
	}

	@Override
	public String toString() {
		return new StringBuilder("Line ").append(theLineNumber + 1).append(" Col ").append(theColumnNumber + 1).append(": ")
			.append(getMessage()).toString();
	}
}
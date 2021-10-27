package org.qommons.config;

import java.io.PrintStream;

/** Represents a single error or warning signaling something wrong with a Qonfig toolkit or document */
public class QonfigParseIssue {
	private final ElementPath thePath;
	private final String theMessage;
	private final Throwable theCause;

	/**
	 * @param path The path detailing where the error is located
	 * @param message The error/warning message
	 * @param cause The cause of the error or warning, if any
	 */
	public QonfigParseIssue(ElementPath path, String message, Throwable cause) {
		thePath = path;
		theMessage = message;
		theCause = cause;
	}

	/** @return The path detailing where the error is located */
	public ElementPath getPath() {
		return thePath;
	}

	/** @return The error/warning message */
	public String getMessage() {
		return theMessage;
	}

	/** @return The cause of the error or warning, if any */
	public Throwable getCause() {
		return theCause;
	}

	/** @param stream The stream to print this issue to */
	public void print(PrintStream stream) {
		stream.print(thePath.toString());
		stream.print(": ");
		stream.println(theMessage);
		if (theCause != null)
			theCause.printStackTrace(stream);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append(thePath.toString());
		str.append(": ");
		str.append(theMessage);
		if (theCause != null)
			str.append(": ").append(theCause);
		return str.toString();
	}
}

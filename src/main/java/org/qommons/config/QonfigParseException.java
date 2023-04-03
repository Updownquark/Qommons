package org.qommons.config;

import java.util.List;

/** Thrown by {@link QonfigParser}s if a toolkit or document cannot be parsed from XML */
public class QonfigParseException extends Exception {
	private final String theDocumentLocation;
	private final List<QonfigParseIssue> theIssues;

	public QonfigParseException(String documentLocation, String message, List<QonfigParseIssue> issues) {
		super(message + createMessage(documentLocation, issues));
		theDocumentLocation = documentLocation;
		theIssues = issues;
	}

	public QonfigParseException(String documentLocation, String message, List<QonfigParseIssue> issues, Throwable cause) {
		super(message + createMessage(documentLocation, issues), cause);
		theDocumentLocation = documentLocation;
		theIssues = issues;
	}

	/** @return The document that has the errors (e.g. a URL or a file path) */
	public String getDocumentLocation() {
		return theDocumentLocation;
	}

	/** @return The issues that this session is for */
	public List<QonfigParseIssue> getIssues() {
		return theIssues;
	}

	/**
	 * @param documentLocation A string describing the document that has the errors, e.g. a URL or file path
	 * @param message The root message for the exception
	 * @param issues The issues that the exception is for
	 * @return The exception to throw
	 */
	public static QonfigParseException forIssues(String documentLocation, String message, List<QonfigParseIssue> issues) {
		Throwable cause = null;
		for (QonfigParseIssue issue : issues) {
			if (issue.getCause() != null) {
				if (cause == null)
					cause = issue.getCause();
				else {
					cause = null;
					break;
				}
			}
		}
		return cause == null ? new QonfigParseException(documentLocation, message, issues)
			: new QonfigParseException(documentLocation, message, issues, cause);
	}

	private static String createMessage(String documentLocation, List<QonfigParseIssue> issues) {
		StringBuilder str = new StringBuilder(documentLocation);
		for (QonfigParseIssue issue : issues)
			str.append('\n').append(issue);
		return str.toString();
	}
}

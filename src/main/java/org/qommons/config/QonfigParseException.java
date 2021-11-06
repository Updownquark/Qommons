package org.qommons.config;

import java.util.List;

/** Thrown by {@link QonfigParser}s if a toolkit or document cannot be parsed */
public class QonfigParseException extends Exception {
	private final List<QonfigParseIssue> theIssues;

	private QonfigParseException(String message, List<QonfigParseIssue> issues) {
		super(message + createMessage(issues));
		theIssues = issues;
	}

	private QonfigParseException(String message, List<QonfigParseIssue> issues, Throwable cause) {
		super(message + createMessage(issues), cause);
		theIssues = issues;
	}

	/** @return The issues that this session is for */
	public List<QonfigParseIssue> getIssues() {
		return theIssues;
	}

	/**
	 * @param message The root message for the exception
	 * @param issues The issues that the exception is for
	 * @return The exception to throw
	 */
	public static QonfigParseException forIssues(String message, List<QonfigParseIssue> issues) {
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
		return cause == null ? new QonfigParseException(message, issues) : new QonfigParseException(message, issues, cause);
	}

	private static String createMessage(List<QonfigParseIssue> issues) {
		StringBuilder str = new StringBuilder();
		for (QonfigParseIssue issue : issues)
			str.append('\n').append(issue);
		return str.toString();
	}
}

package org.qommons.config;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedFilePosition;

/** Thrown by {@link QonfigParser}s if a toolkit or document cannot be parsed from XML */
public class QonfigParseException extends Exception {
	private final List<ErrorReporting.Issue> theIssues;

	/**
	 * @param message The message for the dev
	 * @param issues The list of issues composing this exception
	 */
	public QonfigParseException(String message, List<ErrorReporting.Issue> issues) {
		super(message + createMessage(issues));
		theIssues = issues;
	}

	/**
	 * @param message The message for the dev
	 * @param issues The list of issues composing this exception
	 * @param cause The cause of this exception
	 */
	public QonfigParseException(String message, List<ErrorReporting.Issue> issues, Throwable cause) {
		super(message + createMessage(issues), cause);
		theIssues = issues;
	}

	/** @return The issues that this session is for */
	public List<ErrorReporting.Issue> getIssues() {
		return theIssues;
	}

	@Override
	public void printStackTrace(PrintStream s) {
		for (ErrorReporting.Issue issue : theIssues)
			issue.printStackTrace(s);
	}

	@Override
	public void printStackTrace(PrintWriter s) {
		for (ErrorReporting.Issue issue : theIssues)
			issue.printStackTrace(s);
	}

	/**
	 * @param message The root message for the exception
	 * @param issues The issues that the exception is for
	 * @return The exception to throw
	 */
	public static QonfigParseException forIssues(String message, List<ErrorReporting.Issue> issues) {
		Throwable cause = null;
		for (ErrorReporting.Issue issue : issues) {
			if (issue.cause != null) {
				if (cause == null)
					cause = issue.cause;
				else {
					cause = null;
					break;
				}
			}
		}
		return cause == null ? new QonfigParseException(message, issues) : new QonfigParseException(message, issues, cause);
	}

	/**
	 * 
	 * @param position The position in the file where the exception occurred
	 * @param message The message for the dev
	 * @param cause The cause of the exception--may be null
	 * @return A {@link QonfigParseException} to throw
	 */
	public static QonfigParseException createSimple(LocatedFilePosition position, String message, Throwable cause) {
		List<ErrorReporting.Issue> issues = Arrays
			.asList(new ErrorReporting.Issue(position,
				ErrorReporting.IssueSeverity.ERROR, message, Thread.currentThread().getStackTrace()[0], cause));
		if (cause != null)
			return new QonfigParseException(message, issues, cause);
		else
			return new QonfigParseException(message, issues);
	}

	private static String createMessage(List<ErrorReporting.Issue> issues) {
		StringBuilder str = new StringBuilder();
		for (ErrorReporting.Issue issue : issues)
			str.append('\n').append(issue);
		return str.toString();
	}
}

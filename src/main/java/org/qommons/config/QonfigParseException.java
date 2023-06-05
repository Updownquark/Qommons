package org.qommons.config;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedFilePosition;

/** Thrown by {@link QonfigParser}s if a toolkit or document cannot be parsed from XML */
public class QonfigParseException extends Exception {
	private final List<ErrorReporting.Issue> theIssues;

	private QonfigParseException(String message, List<ErrorReporting.Issue> issues) {
		super(message + createMessage(issues));
		theIssues = issues;
	}

	private QonfigParseException(String message, List<ErrorReporting.Issue> issues, Throwable cause) {
		super(message + createMessage(issues), cause);
		theIssues = issues;
	}

	private QonfigParseException(ErrorReporting.Issue issue) {
		super(issue.toString());
		if (issue.cause != null)
			initCause(issue.cause);
		theIssues = Collections.singletonList(issue);
		if (issue.codeLocation != null)
			setStackTrace(new StackTraceElement[] { issue.codeLocation });
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
		if (issues.size() == 1)
			return new QonfigParseException(issues.get(0));
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
		QonfigParseException ex = cause == null ? new QonfigParseException(message, issues)
			: new QonfigParseException(message, issues, cause);
		for (ErrorReporting.Issue issue : issues)
			ex.addSuppressed(new Issue(issue));
		return ex;
	}

	/**
	 * 
	 * @param position The position in the file where the exception occurred
	 * @param message The message for the dev
	 * @param cause The cause of the exception--may be null
	 * @return A {@link QonfigParseException} to throw
	 */
	public static QonfigParseException createSimple(LocatedFilePosition position, String message, Throwable cause) {
		return new QonfigParseException(new ErrorReporting.Issue(position,
				ErrorReporting.IssueSeverity.ERROR, message, Thread.currentThread().getStackTrace()[0], cause));
	}

	private static String createMessage(List<ErrorReporting.Issue> issues) {
		StringBuilder str = new StringBuilder();
		for (ErrorReporting.Issue issue : issues)
			str.append('\n').append(issue);
		return str.toString();
	}

	private static class Issue extends Exception {
		Issue(ErrorReporting.Issue issue) {
			super(issue.toString());
			if (issue.codeLocation != null)
				setStackTrace(new StackTraceElement[] { issue.codeLocation });
			else
				setStackTrace(new StackTraceElement[0]);
		}
	}
}

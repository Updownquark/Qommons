package org.qommons.config;

import java.util.List;

/** Thrown by {@link QonfigParser}s if a toolkit or document cannot be parsed */
public class QonfigParseException extends Exception {
	private final List<QonfigParseIssue> theIssues;

	/** @param issues The issues that this exception is for */
	public QonfigParseException(List<QonfigParseIssue> issues) {
		super(createMessage(issues));
		theIssues = issues;
	}

	/** @return The issues that this session is for */
	public List<QonfigParseIssue> getIssues() {
		return theIssues;
	}

	private static String createMessage(List<QonfigParseIssue> issues) {
		StringBuilder str = new StringBuilder();
		for (QonfigParseIssue issue : issues)
			str.append('\n').append(issue);
		return str.toString();
	}
}

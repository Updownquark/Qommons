package org.qommons.config;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.qommons.io.ContentPosition;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedContentPosition;

/** A structure to pass around during Qonfig parsing to report errors and warnings */
public class QonfigParseSession implements ErrorReporting {
	private final QonfigParseSession theParent;
	private final LocatedContentPosition theFrame;
	private final QonfigToolkit theToolkit;
	private final List<Issue> theErrors;
	private final List<Issue> theWarnings;

	private QonfigParseSession(QonfigParseSession parent, LocatedContentPosition frame, QonfigToolkit toolkit, List<Issue> warnings,
		List<Issue> errors) {
		theParent = parent;
		theFrame = frame;
		theToolkit = toolkit;
		theWarnings = warnings;
		theErrors = errors;
	}

	@Override
	public QonfigParseSession getParent() {
		return theParent;
	}

	@Override
	public LocatedContentPosition getFrame() {
		return theFrame;
	}

	/** @return The toolkit that is being parsed, or for which a document is being parsed */
	public QonfigToolkit getToolkit() {
		return theToolkit;
	}

	@Override
	public QonfigParseSession at(ContentPosition position) {
		return (QonfigParseSession) ErrorReporting.super.at(position);
	}

	@Override
	public QonfigParseSession at(LocatedContentPosition position) {
		return new QonfigParseSession(this, position, theToolkit, theWarnings, theErrors);
	}

	@Override
	public StackTraceElement getLocation() {
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		if (stack == null)
			return null;
		int i;
		for (i = 1; i < stack.length && (//
		stack[i].getClassName().equals(QonfigParseSession.class.getName())//
			|| stack[i].getClassName().equals(QonfigInterpreterCore.class.getName())//
			|| stack[i].getClassName().equals(QonfigInterpreterCore.CoreSession.class.getName())//
			|| stack[i].getClassName().equals(AbstractQIS.class.getName())//
			|| stack[i].getClassName().equals(ErrorReporting.class.getName())//
		); i++) {//
		}
		return i < stack.length ? stack[i] : null;
	}

	@Override
	public QonfigParseSession report(Issue issue) {
		if (issue.severity == IssueSeverity.ERROR)
			theErrors.add(issue);
		else
			theWarnings.add(issue);
		return this;
	}


	/** @return All warnings logged against this session or any of its {@link #at(ContentPosition) children} */
	public List<Issue> getWarnings() {
		return theWarnings;
	}

	/** @return All errors logged against this session or any of its {@link #at(ContentPosition) children} */
	public List<Issue> getErrors() {
		return theErrors;
	}

	/**
	 * @param message The root message for the exception, if any is thrown
	 * @return This session
	 * @throws QonfigParseException If any errors have been logged against this session or any of its {@link #at(ContentPosition) children}
	 */
	public QonfigParseSession throwErrors(String message) throws QonfigParseException {
		if (theErrors.isEmpty())
			return this;
		throw createException(message);
	}

	/**
	 * @param message The root message for the exception, if any is thrown
	 * @return An exception for errors that have logged against this session or any of its {@link #at(ContentPosition) children}
	 */
	public QonfigParseException createException(String message) {
		return QonfigParseException.forIssues(message, theErrors);
	}

	/**
	 * @param stream The stream to print the warnings to
	 * @param message The root message to print if there are any warnings
	 * @return Whether any warnings had been logged against this session or any of its {@link #at(ContentPosition) children}
	 */
	public boolean printWarnings(PrintStream stream, String message) {
		if (!theWarnings.isEmpty()) {
			stream.print(message);
			stream.print(" WARNING:\n");
		}
		for (Issue issue : theWarnings) {
			stream.print("WARNING: ");
			issue.printStackTrace(stream);
		}
		return !theWarnings.isEmpty();
	}

	@Override
	public String toString() {
		return theToolkit + ":" + theFrame;
	}

	/**
	 * Creates a root session
	 * 
	 * @param toolkit The toolkit for the session
	 * @param position The file position of the root element
	 * @return The new parse session
	 */
	public static QonfigParseSession forRoot(QonfigToolkit toolkit, ContentPosition position) {
		return new QonfigParseSession(null,
			LocatedContentPosition.of(toolkit.getLocation() == null ? toolkit.getName() : toolkit.getLocation().toString(), position),
			toolkit, new ArrayList<>(), new ArrayList<>());
	}
}

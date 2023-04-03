package org.qommons.config;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.qommons.io.FilePosition;

/** A structure to pass around during Qonfig parsing to report errors and warnings */
public class QonfigParseSession implements ErrorReporting {
	private final QonfigToolkit theToolkit;
	private final ElementPath thePath;
	private final List<QonfigParseIssue> theWarnings;
	private final List<QonfigParseIssue> theErrors;

	private QonfigParseSession(QonfigToolkit toolkit, ElementPath path, List<QonfigParseIssue> warnings, List<QonfigParseIssue> errors) {
		theToolkit = toolkit;
		thePath = path;
		theWarnings = warnings;
		theErrors = errors;
	}

	/** @return The toolkit that is being parsed, or for which a document is being parsed */
	public QonfigToolkit getToolkit() {
		return theToolkit;
	}

	@Override
	public QonfigParseSession forChild(String childName, FilePosition position) {
		return new QonfigParseSession(theToolkit, thePath.forChild(childName, position), theWarnings, theErrors);
	}

	private static StackTraceElement getLocation() {
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		if (stack == null)
			return null;
		int i;
		for (i = 1; i < stack.length && (//
		stack[i].getClassName().equals(QonfigParseSession.class.getName())//
			|| stack[i].getClassName().equals(QonfigInterpreterCore.class.getName())//
			|| stack[i].getClassName().equals(QonfigInterpreterCore.CoreSession.class.getName())//
			|| stack[i].getClassName().equals(AbstractQIS.class.getName())//
		); i++) {//
		}
		return i < stack.length ? stack[i] : null;
	}

	@Override
	public QonfigParseSession warn(String message, Throwable cause) {
		theWarnings.add(new QonfigParseIssue(thePath, message, getLocation(), cause));
		return this;
	}

	@Override
	public QonfigParseSession error(String message, Throwable cause) {
		theErrors.add(new QonfigParseIssue(thePath, message, getLocation(), cause));
		return this;
	}

	/** @return This session's element path */
	public ElementPath getPath() {
		return thePath;
	}

	/** @return All warnings logged against this session or any of its {@link #forChild(String, FilePosition) children} */
	public List<QonfigParseIssue> getWarnings() {
		return theWarnings;
	}

	/** @return All errors logged against this session or any of its {@link #forChild(String, FilePosition) children} */
	public List<QonfigParseIssue> getErrors() {
		return theErrors;
	}

	/**
	 * @param message The root message for the exception, if any is thrown
	 * @return This session
	 * @throws QonfigParseException If any errors have been logged against this session or any of its {@link #forChild(String, FilePosition)
	 *         children}
	 */
	public QonfigParseSession throwErrors(String message) throws QonfigParseException {
		if (theErrors.isEmpty())
			return this;
		throw createException(message);
	}

	/**
	 * @param message The root message for the exception, if any is thrown
	 * @return An exception for errors that have logged against this session or any of its {@link #forChild(String, FilePosition) children}
	 */
	public QonfigParseException createException(String message) {
		return QonfigParseException.forIssues(//
			theToolkit.getLocation() == null ? theToolkit.getName() : theToolkit.getLocation().toString(), message, theErrors);
	}

	/**
	 * @param stream The stream to print the warnings to
	 * @param message The root message to print if there are any warnings
	 * @return Whether any warnings had been logged against this session or any of its {@link #forChild(String, FilePosition) children}
	 */
	public boolean printWarnings(PrintStream stream, String message) {
		if (!theWarnings.isEmpty()) {
			stream.print(message);
			stream.print(" WARNING:\n");
		}
		for (QonfigParseIssue issue : theWarnings) {
			stream.print("WARNING: ");
			issue.print(stream);
		}
		return !theWarnings.isEmpty();
	}

	@Override
	public String toString() {
		return theToolkit + ":" + thePath;
	}

	/**
	 * Creates a root session
	 * 
	 * @param rootName The root name for the session
	 * @param toolkit The toolkit for the session
	 * @param position The file position of the root element
	 * @return The new parse session
	 */
	public static QonfigParseSession forRoot(String rootName, QonfigToolkit toolkit, FilePosition position) {
		return new QonfigParseSession(toolkit,
			ElementPath.forRoot(toolkit.getLocation() == null ? toolkit.getName() : toolkit.getLocation().toString(), rootName, position),
			new ArrayList<>(), new ArrayList<>());
	}
}

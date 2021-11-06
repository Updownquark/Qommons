package org.qommons.config;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/** A structure to pass around during Qonfig parsing to report errors and warnings */
public class QonfigParseSession {
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

	/**
	 * @param childName The name of the child to create the session for
	 * @param identifier An identifier for the child (in case multiple children with the same name exist in the parent)
	 * @return A session for the child
	 */
	public QonfigParseSession forChild(String childName, Object identifier) {
		return new QonfigParseSession(theToolkit, thePath.forChild(childName, identifier), theWarnings, theErrors);
	}

	/**
	 * @param message The warning message to log
	 * @return This session
	 */
	public QonfigParseSession withWarning(String message) {
		return withWarning(message, null);
	}

	/**
	 * @param message The warning message to log
	 * @param cause The cause of the warning, if any
	 * @return This session
	 */
	public QonfigParseSession withWarning(String message, Throwable cause) {
		theWarnings.add(new QonfigParseIssue(thePath, message, cause));
		return this;
	}

	/**
	 * @param message The error message to log
	 * @return This session
	 */
	public QonfigParseSession withError(String message) {
		return withError(message, null);
	}

	/**
	 * @param message The error message to log
	 * @param cause The cause of the error, if any
	 * @return This session
	 */
	public QonfigParseSession withError(String message, Throwable cause) {
		theErrors.add(new QonfigParseIssue(thePath, message, cause));
		return this;
	}

	/** @return All warnings logged against this session or any of its {@link #forChild(String, Object) children} */
	public List<QonfigParseIssue> getWarnings() {
		return theWarnings;
	}

	/** @return All errors logged against this session or any of its {@link #forChild(String, Object) children} */
	public List<QonfigParseIssue> getErrors() {
		return theErrors;
	}

	/**
	 * @param message The root message for the exception, if any is thrown
	 * @return This session
	 * @throws QonfigParseException If any errors have been logged against this session or any of its {@link #forChild(String, Object)
	 *         children}
	 */
	public QonfigParseSession throwErrors(String message) throws QonfigParseException {
		if (theErrors.isEmpty())
			return this;
		throw QonfigParseException.forIssues(message, theErrors);
	}

	/**
	 * @param stream The stream to print the warnings to
	 * @param message The root message to print if there are any warnings
	 * @return Whether any warnings had been logged against this session or any of its {@link #forChild(String, Object) children}
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

	/**
	 * Creates a root session
	 * 
	 * @param rootName The root name for the session
	 * @param toolkit The toolkit for the session
	 * @return The new parse session
	 */
	public static QonfigParseSession forRoot(String rootName, QonfigToolkit toolkit) {
		return new QonfigParseSession(toolkit, ElementPath.forRoot(rootName), new ArrayList<>(), new ArrayList<>());
	}
}

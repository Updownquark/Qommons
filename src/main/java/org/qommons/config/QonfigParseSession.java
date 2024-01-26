package org.qommons.config;

import java.util.ArrayList;
import java.util.List;

import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedPositionedContent;
import org.qommons.io.PositionedContent;

/** A structure to pass around during Qonfig parsing to report errors and warnings */
public class QonfigParseSession extends ErrorReporting.Default {
	private final QonfigToolkit theToolkit;
	private final List<Issue> theErrors;
	private final boolean isPartial;

	private QonfigParseSession(boolean partial, LocatedPositionedContent frame, QonfigToolkit toolkit, List<Issue> errors) {
		super(frame);
		theToolkit = toolkit;
		theErrors = errors;
		isPartial = partial;
	}

	private QonfigParseSession(QonfigParseSession parent, LocatedPositionedContent frame) {
		super(parent, frame);
		theToolkit = parent.theToolkit;
		theErrors = parent.theErrors;
		isPartial = parent.isPartial;
	}

	/** @return The toolkit that is being parsed, or for which a document is being parsed */
	public QonfigToolkit getToolkit() {
		return theToolkit;
	}

	/** @return Whether this session is to parse a partial element */
	public boolean isPartial() {
		return isPartial;
	}

	@Override
	public QonfigParseSession at(PositionedContent position) {
		return (QonfigParseSession) super.at(position);
	}

	@Override
	public QonfigParseSession at(LocatedPositionedContent position) {
		return new QonfigParseSession(this, position);
	}

	@Override
	public QonfigParseSession report(Issue issue) {
		if (issue.severity == IssueSeverity.ERROR)
			theErrors.add(issue);
		else
			super.report(issue);
		return this;
	}

	/** @return All errors logged against this session or any of its {@link #at(PositionedContent) children} */
	public List<Issue> getErrors() {
		return theErrors;
	}

	/**
	 * @param message The root message for the exception, if any is thrown
	 * @return This session
	 * @throws QonfigParseException If any errors have been logged against this session or any of its {@link #at(PositionedContent)
	 *         children}
	 */
	public QonfigParseSession throwErrors(String message) throws QonfigParseException {
		if (theErrors.isEmpty())
			return this;
		throw createException(message);
	}

	/**
	 * @param message The root message for the exception, if any is thrown
	 * @return An exception for errors that have logged against this session or any of its {@link #at(PositionedContent) children}
	 */
	public QonfigParseException createException(String message) {
		return QonfigParseException.forIssues(message, theErrors);
	}

	@Override
	public String toString() {
		return theToolkit + ":" + getFileLocation();
	}

	/**
	 * Creates a root session
	 * 
	 * @param partial Whether this session is to parse a partial element
	 * @param toolkit The toolkit for the session
	 * @param position The file position of the root element
	 * @return The new parse session
	 */
	public static QonfigParseSession forRoot(boolean partial, QonfigToolkit toolkit, PositionedContent position) {
		return new QonfigParseSession(partial,
			LocatedPositionedContent.of(toolkit.getLocation() == null ? toolkit.getName() : toolkit.getLocation().toString(), position),
			toolkit, new ArrayList<>());
	}
}

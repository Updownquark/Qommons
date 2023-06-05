package org.qommons.config;

import java.util.ArrayList;
import java.util.List;

import org.qommons.io.PositionedContent;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedPositionedContent;

/** A structure to pass around during Qonfig parsing to report errors and warnings */
public class QonfigParseSession extends ErrorReporting.Default {
	private final LocatedPositionedContent theFileLocation;
	private final QonfigToolkit theToolkit;
	private final List<Issue> theErrors;

	private QonfigParseSession(LocatedPositionedContent frame, QonfigToolkit toolkit, List<Issue> errors) {
		super(frame);
		theFileLocation = frame;
		theToolkit = toolkit;
		theErrors = errors;
	}

	@Override
	public LocatedPositionedContent getFileLocation() {
		return theFileLocation;
	}

	/** @return The toolkit that is being parsed, or for which a document is being parsed */
	public QonfigToolkit getToolkit() {
		return theToolkit;
	}

	@Override
	public QonfigParseSession at(PositionedContent position) {
		return (QonfigParseSession) super.at(position);
	}

	@Override
	public QonfigParseSession at(LocatedPositionedContent position) {
		return new QonfigParseSession(position, theToolkit, theErrors);
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
	 * @throws QonfigParseException If any errors have been logged against this session or any of its {@link #at(PositionedContent) children}
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
		return theToolkit + ":" + theFileLocation;
	}

	/**
	 * Creates a root session
	 * 
	 * @param toolkit The toolkit for the session
	 * @param position The file position of the root element
	 * @return The new parse session
	 */
	public static QonfigParseSession forRoot(QonfigToolkit toolkit, PositionedContent position) {
		return new QonfigParseSession(
			LocatedPositionedContent.of(toolkit.getLocation() == null ? toolkit.getName() : toolkit.getLocation().toString(), position),
			toolkit, new ArrayList<>());
	}
}

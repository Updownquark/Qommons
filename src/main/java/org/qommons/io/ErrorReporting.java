package org.qommons.io;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/** An interface to allow errors and warnings to be reported in a traceable way without terminating execution with an exception */
public interface ErrorReporting {
	public static class Default implements ErrorReporting {
		private final Default theParent;
		private final LocatedContentPosition theFrame;

		public Default(Default parent, LocatedContentPosition frame) {
			theParent = parent;
			theFrame = frame;
		}

		@Override
		public ErrorReporting getParent() {
			return theParent;
		}

		@Override
		public LocatedContentPosition getFrame() {
			return theFrame;
		}

		@Override
		public ErrorReporting report(Issue issue) {
			issue.printStackTrace(issue.severity == IssueSeverity.INFO ? System.out : System.err);
			return this;
		}

		@Override
		public ErrorReporting at(LocatedContentPosition position) {
			return new Default(this, position);
		}

		@Override
		public String toString() {
			return theFrame.toString();
		}
	}

	public enum IssueSeverity {
		ERROR, WARN, INFO
	}

	public class Issue {
		private final ErrorReporting theFrame;
		public final IssueSeverity severity;
		public final String message;
		public final StackTraceElement codeLocation;
		public final Throwable cause;

		public Issue(ErrorReporting frame, IssueSeverity severity, String message, StackTraceElement codeLocation, Throwable cause) {
			theFrame = frame;
			this.severity = severity;
			this.message = message;
			this.codeLocation = codeLocation;
			this.cause = cause;
		}

		public ErrorReporting getFrame() {
			return theFrame;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append(severity);
			if (message != null) {
				str.append(": ").append(message);
				if (cause != null)
					str.append("<-").append(cause);
			} else if (cause != null)
				str.append(": ").append(cause);
			return str.toString();
		}

		public void printStackTrace(PrintWriter w) {
			w.append(severity.toString());
			if (message != null) {
				w.append(": ");
				w.append(message);
			}
			w.append("\n\t at ");
			w.append(codeLocation.toString());
			String file = null;
			for (LocatedFilePosition frame : theFrame.getStack()) {
				w.append("\n\t at ");
				if (!frame.getFileLocation().equals(file)) {
					w.append(frame.toString());
					file = frame.getFileLocation();
				} else
					w.append(frame.printPosition());
			}
			if (cause != null) {
				w.append("\nCaused by: ");
				cause.printStackTrace(w);
			}
		}

		public void printStackTrace(PrintStream w) {
			w.append(severity.toString());
			if (message != null) {
				w.append(": ");
				w.append(message);
			}
			w.append("\n\t at ");
			w.append(codeLocation.toString());
			String file = null;
			for (LocatedFilePosition frame : theFrame.getStack()) {
				w.append("\n\t at ");
				if (!frame.getFileLocation().equals(file)) {
					w.append(frame.toString());
					file = frame.getFileLocation();
				} else
					w.append(frame.printPosition());
			}
			if (cause != null) {
				w.append("\nCaused by: ");
				cause.printStackTrace(w);
			}
		}
	}

	/**
	 * @param message The warning message to log
	 * @return This session
	 */
	default ErrorReporting info(String message) {
		return info(message, null);
	}

	/**
	 * @param message The warning message to log
	 * @param cause The cause of the warning, if any
	 * @return This session
	 */
	default ErrorReporting info(String message, Throwable cause) {
		return report(new Issue(this, IssueSeverity.INFO, message, getLocation(), cause));
	}

	/**
	 * @param message The warning message to log
	 * @return This session
	 */
	default ErrorReporting warn(String message) {
		return warn(message, null);
	}

	/**
	 * @param message The warning message to log
	 * @param cause The cause of the warning, if any
	 * @return This session
	 */
	default ErrorReporting warn(String message, Throwable cause) {
		return report(new Issue(this, IssueSeverity.WARN, message, getLocation(), cause));
	}

	/**
	 * @param message The error message to log
	 * @return This session
	 */
	default ErrorReporting error(String message) {
		return error(message, null);
	}

	/**
	 * @param message The error message to log
	 * @param cause The cause of the error, if any
	 * @return This session
	 */
	default ErrorReporting error(String message, Throwable cause) {
		return report(new Issue(this, IssueSeverity.ERROR, message, getLocation(), cause));
	}

	ErrorReporting report(Issue issue);

	LocatedContentPosition getFrame();

	ErrorReporting getParent();

	default StackTraceElement getLocation() {
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		if (stack == null)
			return null;
		int i;
		for (i = 1; i < stack.length && stack[i].getClassName().equals(ErrorReporting.class.getName()); i++) {//
		}
		return i < stack.length ? stack[i] : null;
	}

	default List<LocatedFilePosition> getStack() {
		return getStack(new ArrayList<>());
	}

	default List<LocatedFilePosition> getStack(List<LocatedFilePosition> stack) {
		if (getFrame() != null)
			stack.add(getFrame().getPosition(0));
		if (getParent() != null)
			getParent().getStack(stack);
		return stack;
	}

	/**
	 * @param position The file position of the element this reporting is for
	 * @return A reporting for the child
	 */
	default ErrorReporting at(ContentPosition position) {
		if (position instanceof LocatedContentPosition)
			return at((LocatedContentPosition) position);
		else
			return at(LocatedContentPosition.of(getFrame().getFileLocation(), position));
	}

	default ErrorReporting at(int positionOffset) {
		if (positionOffset == 0)
			return this;
		return at(getFrame().subSequence(positionOffset));
	}

	/**
	 * @param position The file position of the element this reporting is for
	 * @return A reporting for the child
	 */
	ErrorReporting at(LocatedContentPosition position);
}

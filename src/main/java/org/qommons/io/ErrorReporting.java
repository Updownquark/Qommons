package org.qommons.io;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.LinkedHashSet;
import java.util.Set;

/** An interface to allow errors and warnings to be reported in a traceable way without terminating execution with an exception */
public interface ErrorReporting {
	/** Default {@link ErrorReporting} implementation */
	public static class Default implements ErrorReporting {
		private final LocatedContentPosition theFrame;
		private final Set<String> theIgnoredClasses;

		/** @param frame The position of this reporting */
		public Default(LocatedContentPosition frame) {
			theFrame = frame;
			theIgnoredClasses = new LinkedHashSet<>();
			theIgnoredClasses.add(ErrorReporting.class.getName());
			theIgnoredClasses.add(Default.class.getName());
		}

		private Default(LocatedContentPosition frame, Set<String> ignoredClasses) {
			theFrame = frame;
			theIgnoredClasses = ignoredClasses;
		}

		@Override
		public LocatedContentPosition getFileLocation() {
			return theFrame;
		}

		@Override
		public ErrorReporting report(Issue issue) {
			issue.printStackTrace(issue.severity == IssueSeverity.INFO ? System.out : System.err);
			return this;
		}

		@Override
		public ErrorReporting at(LocatedContentPosition position) {
			return new Default(position, theIgnoredClasses);
		}

		@Override
		public void ignoreClass(String className) {
			theIgnoredClasses.add(className);
		}

		@Override
		public StackTraceElement getCodeLocation() {
			return Impl.getCodeLocation(theIgnoredClasses);
		}

		@Override
		public String toString() {
			return theFrame.toString();
		}
	}

	/** Severity of a reported issue in an {@link ErrorReporting} instance */
	public enum IssueSeverity {
		/** An information message which should not have any adverse effect */
		INFO,
		/**
		 * An error that may cause the program to behave differently than intended, but which will not likely keep the program from being
		 * useful
		 */
		WARN,
		/** An error that is likely to cause the program to fail its objectives in significant ways */
		ERROR
	}

	/** A reported issue in an {@link ErrorReporting} */
	public class Issue {
		/** The file location at which this issue was reported */
		public final LocatedFilePosition fileLocation;
		/** The severity of this issue */
		public final IssueSeverity severity;
		/** The message for this issue */
		public final String message;
		/** The location in the code where this issue was reported from */
		public final StackTraceElement codeLocation;
		/** The exception that caused this issue */
		public final Throwable cause;

		/**
		 * @param fileLocation The file location at which this issue was reported
		 * @param severity The severity of this issue
		 * @param message The message for this issue
		 * @param codeLocation The location in the code where this issue was reported from
		 * @param cause The exception that caused this issue
		 */
		public Issue(LocatedFilePosition fileLocation, IssueSeverity severity, String message, StackTraceElement codeLocation,
			Throwable cause) {
			this.fileLocation = fileLocation;
			this.severity = severity;
			this.message = message;
			this.codeLocation = codeLocation;
			this.cause = cause;
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

		/**
		 * Prints a human-readable representation of this issue to the given printer
		 * 
		 * @param w The printer to print this issue to
		 */
		public void printStackTrace(PrintWriter w) {
			w.append(severity.toString());
			if (message != null) {
				w.append(": ");
				w.append(message);
			}
			if (codeLocation != null)
				w.append("\n\t at ").append(codeLocation.toString());
			if (fileLocation != null)
				w.append("\n\t at ").append(fileLocation.toString());
			if (cause != null) {
				w.append("\nCaused by: ");
				cause.printStackTrace(w);
			}
		}

		/**
		 * Prints a human-readable representation of this issue to the given printer
		 * 
		 * @param w The printer to print this issue to
		 */
		public void printStackTrace(PrintStream w) {
			w.append(severity.toString());
			if (message != null) {
				w.append(": ");
				w.append(message);
			}
			if (codeLocation != null)
				w.append("\n\t at ").append(codeLocation.toString());
			if (fileLocation != null)
				w.append("\n\t at ").append(fileLocation.toString());
			if (cause != null) {
				w.append("\nCaused by: ");
				cause.printStackTrace(w);
			}
		}
	}

	/**
	 * @param message The warning message to log
	 * @return This error reporting instance
	 */
	default ErrorReporting info(String message) {
		return info(message, null);
	}

	/**
	 * @param message The warning message to log
	 * @param cause The cause of the warning, if any
	 * @return This error reporting instance
	 */
	default ErrorReporting info(String message, Throwable cause) {
		return report(new Issue(getFileLocation().getPosition(0), IssueSeverity.INFO, message, getCodeLocation(), cause));
	}

	/**
	 * @param message The warning message to log
	 * @return This error reporting instance
	 */
	default ErrorReporting warn(String message) {
		return warn(message, null);
	}

	/**
	 * @param message The warning message to log
	 * @param cause The cause of the warning, if any
	 * @return This error reporting instance
	 */
	default ErrorReporting warn(String message, Throwable cause) {
		return report(new Issue(getFileLocation().getPosition(0), IssueSeverity.WARN, message, getCodeLocation(), cause));
	}

	/**
	 * @param message The error message to log
	 * @return This error reporting instance
	 */
	default ErrorReporting error(String message) {
		return error(message, null);
	}

	/**
	 * @param message The error message to log
	 * @param cause The cause of the error, if any
	 * @return This error reporting instance
	 */
	default ErrorReporting error(String message, Throwable cause) {
		return report(new Issue(getFileLocation().getPosition(0), IssueSeverity.ERROR, message, getCodeLocation(), cause));
	}

	/**
	 * @param issue The issue to report
	 * @return This error reporting instance
	 */
	ErrorReporting report(Issue issue);

	/** @return The position of the content that this error reporting is for */
	LocatedContentPosition getFileLocation();

	void ignoreClass(String className);

	/** @return The code location to use for a new issue */
	StackTraceElement getCodeLocation();

	/**
	 * @param position The file position of the element this reporting is for
	 * @return A reporting for the child
	 */
	default ErrorReporting at(ContentPosition position) {
		if (position instanceof LocatedContentPosition)
			return at((LocatedContentPosition) position);
		else
			return at(LocatedContentPosition.of(getFileLocation().getFileLocation(), position));
	}

	/**
	 * @param positionOffset The offset in this reporting's content
	 * @return A new error reporting instance that reports for content offset to the given position
	 */
	default ErrorReporting at(int positionOffset) {
		if (positionOffset == 0)
			return this;
		return at(getFileLocation().subSequence(positionOffset));
	}

	/**
	 * @param position The file position of the element this reporting is for
	 * @return A reporting for the child
	 */
	ErrorReporting at(LocatedContentPosition position);

	class Impl {
		private Impl() {
		}

		static StackTraceElement getCodeLocation(Set<String> ignoreClasses) {
			StackTraceElement[] stack = Thread.currentThread().getStackTrace();
			if (stack == null)
				return null;
			int i;
			for (i = 1; i < stack.length && ignoreClasses.contains(stack[i].getClassName()); i++) {//
			}
			return i < stack.length ? stack[i] : null;
		}
	}
}

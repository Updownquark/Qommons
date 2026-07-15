package org.qommons.io;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.qommons.Named;

/** An interface to allow errors and warnings to be reported in a traceable way without terminating execution with an exception */
public interface ErrorReporting {
	/** Default {@link ErrorReporting} implementation */
	public static class Default implements ErrorReporting {
		private final LocatedPositionedContent theFrame;
		private final Set<String> theIgnoredClasses;

		/** @param frame The position of this reporting */
		public Default(LocatedPositionedContent frame) {
			theFrame = frame;
			theIgnoredClasses = new LinkedHashSet<>();
			theIgnoredClasses.add(ErrorReporting.class.getName());
			theIgnoredClasses.add(ErrorReporting.Impl.class.getName());
			theIgnoredClasses.add(Default.class.getName());
		}

		/**
		 * @param source The error reporting parent
		 * @param frame The source location for this reporting
		 */
		protected Default(Default source, LocatedPositionedContent frame) {
			theFrame = frame;
			theIgnoredClasses = source.theIgnoredClasses;
		}

		@Override
		public LocatedPositionedContent getFileLocation() {
			return theFrame;
		}

		@Override
		public boolean isOfInterest(IssueSeverity severity) {
			return true;
		}

		@Override
		public ErrorReporting report(Issue issue) {
			issue.printStackTrace(issue.severity.compareTo(IssueSeverity.INFO) <= 0 ? System.out : System.err);
			return this;
		}

		@Override
		public ErrorReporting at(LocatedPositionedContent position) {
			return new Default(this, position);
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
			return theFrame.getPosition(0).toShortString() + "@" + theFrame.toLocationString();
		}
	}

	/** Severity of a reported issue in an {@link ErrorReporting} instance */
	public enum IssueSeverity {
		/** An information message which should not */
		DEBUG,
		/** An information message which should not have any adverse effect */
		INFO,
		/**
		 * An error that may cause the program to behave differently than intended, but which will not likely keep the program from being
		 * useful
		 */
		WARN,
		/** An error that is likely to cause the program to fail its objectives in significant ways */
		ERROR,
		/**
		 * An error that is likely to cause the program to fail its objectives not only for the operation that experienced the error, but
		 * for a class of functionality for the rest of the lifetime of the program
		 */
		FATAL
	}

	public static class IssueProperty<T> implements Named {
		private final String theName;

		public IssueProperty(String name) {
			theName = name;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public String toString() {
			return theName;
		}
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
		private Map<IssueProperty<?>, Object> theProperties;

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

		/**
		 * Gets an immutable property of this log issue
		 * 
		 * @param <T> The type of the property
		 * @param key The key of the property to get
		 * @param defaultValue Creates the property value for this issue if it has not been populated. This may be null to get the current
		 *        property value without potentially populating it.
		 * @return The property value
		 */
		public <T> T getProperty(IssueProperty<T> key, Supplier<? extends T> defaultValue) {
			if (defaultValue == null)
				return theProperties == null ? null : (T) theProperties.get(key);
			if (theProperties == null)
				theProperties = new HashMap<>();
			return (T) theProperties.computeIfAbsent(key, __ -> defaultValue.get());
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			if (fileLocation != null)
				str.append(fileLocation.toShortString()).append(' ');
			str.append(severity).append(": ");
			if (message != null) {
				str.append(message);
				if (cause != null)
					str.append("<-").append(cause);
			} else if (cause != null)
				str.append(cause);
			return str.toString();
		}

		/**
		 * Prints a human-readable representation of this issue to the given printer
		 * 
		 * @param w The printer to print this issue to
		 */
		public void printStackTrace(PrintWriter w) {
			if (fileLocation != null)
				w.append(fileLocation.toShortString()).append(": ");
			w.append(severity.toString());
			if (message != null) {
				w.append(' ').append(message);
			}
			if (fileLocation != null)
				w.append("\n\t at ").append(fileLocation.toString());
			if (codeLocation != null)
				w.append("\n\t at ").append(codeLocation.toString());
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
			if (fileLocation != null)
				w.append(fileLocation.toShortString()).append(": ");
			w.append(severity.toString());
			if (message != null) {
				w.append(' ').append(message);
			}
			if (severity != IssueSeverity.INFO) {
				if (fileLocation != null)
					w.append("\n\t at ").append(fileLocation.toString());
				if (codeLocation != null)
					w.append("\n\t at ").append(codeLocation.toString());
			}
			w.append('\n');
			if (cause != null) {
				w.append("Caused by: ");
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
		if (!isOfInterest(IssueSeverity.INFO))
			return this;
		return report(new Issue(getFileLocation() == null ? null : getFileLocation().getPosition(0), IssueSeverity.INFO, message,
			getCodeLocation(), cause));
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
		if (!isOfInterest(IssueSeverity.WARN))
			return this;
		return report(new Issue(getFileLocation() == null ? null : getFileLocation().getPosition(0), IssueSeverity.WARN, message,
			getCodeLocation(), cause));
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
		if (!isOfInterest(IssueSeverity.ERROR))
			return this;
		return report(new Issue(getFileLocation() == null ? null : getFileLocation().getPosition(0), IssueSeverity.ERROR, message,
			getCodeLocation(), cause));
	}

	boolean isOfInterest(IssueSeverity severity);

	/**
	 * @param issue The issue to report
	 * @return This error reporting instance
	 */
	ErrorReporting report(Issue issue);

	/** @return The position of the content that this error reporting is for */
	LocatedPositionedContent getFileLocation();

	/** @return The file position at the beginning of this reporting's content */
	default LocatedFilePosition getPosition() {
		LocatedPositionedContent content = getFileLocation();
		if (content == null)
			return null;
		return content.getPosition(0);
	}

	/**
	 * Adds a class to the list of classes that this reporting instance's {@link #getCodeLocation()} method will use to get the the calling
	 * code location
	 * 
	 * @param className The class name to ignore in stack traces
	 */
	void ignoreClass(String className);

	/** @return The code location to use for a new issue */
	StackTraceElement getCodeLocation();

	/**
	 * @param position The file position of the element this reporting is for
	 * @return A reporting for the child
	 */
	default ErrorReporting at(PositionedContent position) {
		if (position instanceof LocatedPositionedContent)
			return at((LocatedPositionedContent) position);
		else if (getFileLocation() != null)
			return at(LocatedPositionedContent.of(getFileLocation().getFileLocation(), position));
		else
			return at((LocatedPositionedContent) null);
	}

	/**
	 * @param positionOffset The offset in this reporting's content
	 * @return A new error reporting instance that reports for content offset to the given position
	 */
	default ErrorReporting at(int positionOffset) {
		if (positionOffset == 0)
			return this;
		return at(getFileLocation() == null ? null : getFileLocation().subSequence(positionOffset));
	}

	/**
	 * @param position The file position of the element this reporting is for
	 * @return A reporting for the child
	 */
	ErrorReporting at(LocatedPositionedContent position);

	/** Implementation details in this class */
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

package org.qommons.config;

import org.qommons.io.FilePosition;

public interface ErrorReporting {
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
	ErrorReporting warn(String message, Throwable cause);

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
	ErrorReporting error(String message, Throwable cause);

	/**
	 * @param childName The name of the child to create the reporting for
	 * @param position The file position of the element this reporting is for
	 * @return A reporting for the child
	 */
	ErrorReporting forChild(String childName, FilePosition position);
}

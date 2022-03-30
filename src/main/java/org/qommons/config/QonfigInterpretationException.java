package org.qommons.config;

public class QonfigInterpretationException extends Exception {
	public QonfigInterpretationException(String message, Throwable cause) {
		super(message, cause);
	}

	public QonfigInterpretationException(String message) {
		super(message);
	}

	public QonfigInterpretationException(Throwable cause) {
		super(cause);
	}
}
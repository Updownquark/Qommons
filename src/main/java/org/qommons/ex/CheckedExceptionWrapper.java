package org.qommons.ex;

/** A RuntimeException wrapping a checked {@link Throwable} */
public class CheckedExceptionWrapper extends RuntimeException {
	/** @param ex The checked exception to wrap */
	public CheckedExceptionWrapper(Throwable ex) {
		super(ex);
	}
}

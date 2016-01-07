package org.qommons.ex;

public class CheckedExceptionWrapper extends RuntimeException {
	public CheckedExceptionWrapper(Throwable ex) {
		super(ex);
	}
}

package org.qommons.collect;

/** Thrown when a method is called by a listener which was invoked by that same method and reentrant notification is not allowed */
public class ReentrantNotificationException extends IllegalStateException {
	/** @param message The message for the exception */
	public ReentrantNotificationException(String message) {
		super(message);
	}
}
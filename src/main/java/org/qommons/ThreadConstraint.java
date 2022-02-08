package org.qommons;

import java.awt.EventQueue;

/**
 * <p>
 * Represents a constraint on the threads events may be fired from.
 * </p>
 * <p>
 * This should NEVER be implemented ad-hoc, but rather all instances of this interface should be static constants.
 * </p>
 */
public interface ThreadConstraint {
	/** Thread "constraint" for eventables that can fire events on any thread */
	public static final ThreadConstraint ANY = new ThreadConstraint() {
		@Override
		public boolean isEventThread() {
			return true;
		}

		@Override
		public void invokeLater(Runnable task) {
			task.run();
		}

		@Override
		public String toString() {
			return "Unconstrained";
		}
	};
	/** Thread constraint for eventables that can never fire events */
	public static final ThreadConstraint NONE = new ThreadConstraint() {
		@Override
		public boolean isEventThread() {
			return true; // Don't throw exceptions when locking--the underlying data structure should throw the exception on modification
		}

		@Override
		public void invokeLater(Runnable task) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String toString() {
			return "Not ThreadConstrained";
		}
	};
	/** Thread constraint for the AWT {@link EventQueue} thread */
	public static final ThreadConstraint EDT = new ThreadConstraint() {
		@Override
		public boolean isEventThread() {
			return EventQueue.isDispatchThread();
		}

		@Override
		public void invokeLater(Runnable task) {
			EventQueue.invokeLater(task);
		}

		@Override
		public String toString() {
			return "AWT Event Queue Thread";
		}
	};

	/** @return Whether events might be fired on the current thread */
	boolean isEventThread();

	/** @param task The task to execute on an acceptable event thread */
	void invokeLater(Runnable task);
}

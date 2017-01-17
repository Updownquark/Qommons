package org.qommons;

import java.util.function.Function;

/** An event or something that may have a cause */
public interface Causable {
	/** @return The cause of this event or thing--typically another event or null */
	Object getCause();

	/**
	 * @param action The action to run when this event or thing finishes
	 * @return This causable
	 */
	Causable onFinish(Runnable action);

	/**
	 * @param action The action to run when the root causable of this causable finishes
	 * @return This causable
	 */
	Causable onRootFinish(Runnable action);

	/** Runs the finish actions of this causable */
	void finish();

	/** @return The thing that caused the chain of events that led to this event or thing */
	default Object getRootCause() {
		Causable root = this;
		Object cause = getCause();
		while (cause != null) {
			root = (Causable) cause;
			cause = root.getCause();
		}
		return root;
	}

	/**
	 * Applies a function to each cause in the chain of events that led to this event and returns the first non-null value. Allows a quick
	 * search through the chain of events
	 * 
	 * @param <T> The type of the value to get out
	 * @param test The test to use to search through the causes
	 * @return The first non-null results of the test on the chain of events
	 */
	default <T> T getCauseLike(Function<Object, T> test) {
		T value = test.apply(this);
		if (value != null)
			return value;
		Causable root = this;
		Object cause = getCause();
		if (cause != null) {
			value = test.apply(cause);
			if (value != null)
				return value;
		}
		while (cause instanceof Causable && value == null) {
			root = (Causable) cause;
			cause = root.getCause();
			value = test.apply(cause);
		}
		return value;
	}
}

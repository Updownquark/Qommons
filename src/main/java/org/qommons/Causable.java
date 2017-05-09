package org.qommons;

import java.util.Map;
import java.util.function.Function;

/** An event or something that may have a cause */
public interface Causable {
	/** An action to be fired when a causable finishes */
	interface TerminalAction {
		/**
		 * @param cause The causable that has finished
		 * @param values The values added to the causable for this action
		 */
		void finished(Causable cause, Map<Object, Object> values);
	}

	/** @return The cause of this event or thing--typically another event or null */
	Object getCause();

	/** @return The thing that caused the chain of events that led to this event or thing */
	Causable getRootCausable();

	/** @return The root cause of this causable (the root may or may not be causable itself) */
	default Object getRootCause() {
		Causable root = getRootCausable();
		if (root.getCause() != null)
			return root.getCause();
		else
			return root;
	}

	/**
	 * @param key The key to add the action for. An action will only be added once to a causable for a given key.
	 * @param action The action to run when this event or thing finishes
	 * @return A map of key-values that may be modified to keep track of information from multiple sub-causes of this cause
	 */
	Map<Object, Object> onFinish(Object key, TerminalAction action);

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

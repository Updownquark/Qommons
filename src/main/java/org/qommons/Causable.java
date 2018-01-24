package org.qommons;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** An event or something that may have a cause */
public abstract class Causable {
	/** An action to be fired when a causable finishes */
	@FunctionalInterface
	public static interface TerminalAction {
		/**
		 * @param cause The causable that has finished
		 * @param values The values added to the causable for this action
		 */
		void finished(Causable cause, Map<Object, Object> values);
	}

	public static class CausableKey {
		private final Map<Object, Object> theValues;
		private final AtomicInteger theCauseCount;
		private final TerminalAction theAction;

		private CausableKey(TerminalAction action) {
			theValues = new LinkedHashMap<>();
			theCauseCount = new AtomicInteger();
			theAction = action;
		}

		/** @return This cause key's current value data, unmodifiable */
		public Map<Object, Object> getData() {
			return Collections.unmodifiableMap(theValues);
		}

		Transaction use(Causable cause) {
			theCauseCount.getAndIncrement();
			boolean[] closed = new boolean[1];
			return () -> {
				if (closed[0])
					return;
				closed[0] = true;
				int remaining = theCauseCount.decrementAndGet();
				if (remaining == 0) {
					theAction.finished(cause, theValues);
					theValues.clear();
				}
			};
		}
	}

	private static class SimpleCause extends Causable {
		public SimpleCause() {
			this(null);
		}

		public SimpleCause(Object cause) {
			super(cause);
		}
	}

	public static CausableKey key(TerminalAction action) {
		return new CausableKey(action);
	}

	public static Causable simpleCause(Object cause) {
		return new SimpleCause(cause);
	}

	private final Object theCause;
	private final Causable theRootCausable;
	private IdentityHashMap<CausableKey, Transaction> theUsedKeys;
	private boolean isStarted;
	private boolean isFinished;

	/** @param cause The cause of this causable */
	public Causable(Object cause) {
		theCause = cause;
		if (cause == null)
			theRootCausable = this;
		else if (cause instanceof Causable)
			theRootCausable = ((Causable) cause).theRootCausable;
		else
			theRootCausable = this;
	}

	/** @return The cause of this event or thing--typically another event or null */
	public Object getCause() {
		return theCause;
	}

	/** @return The thing that caused the chain of events that led to this event or thing */
	public Causable getRootCausable() {
		return theRootCausable;
	}

	/** @return The root cause of this causable (the root may or may not be causable itself) */
	public Object getRootCause() {
		Causable root = getRootCausable();
		if (root.getCause() != null)
			return root.getCause();
		else
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
	public <T> T getCauseLike(Function<Object, T> test) {
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

	/**
	 * @param key The key to add the action for. An action will only be added once to a causable for a given key.
	 * @return A map of key-values that may be modified to keep track of information from multiple sub-causes of this cause
	 */
	public Map<Object, Object> onFinish(CausableKey key) {
		if (!isStarted)
			throw new IllegalStateException("Not started!  Use Causable.use(Causable)");
		if (theUsedKeys == null)
			theUsedKeys = new IdentityHashMap<>();
		theUsedKeys.computeIfAbsent(key, k -> k.use(this));
		return key.theValues;
	}


	private void finish() {
		if (!isStarted)
			throw new IllegalStateException("Not started!  Use Causable.use(Causable)");
		if (isFinished)
			throw new IllegalStateException("A cause may only be finished once");
		isFinished = true;
		// The finish actions may use this causable as a cause for events they fire.
		// These events may trigger onRootFinish calls, which add more actions to this causable
		// Though this cycle is allowed, care must be taken by callers to ensure it does not become infinite
		Map<CausableKey, Transaction> keys = theUsedKeys;
		while (keys != null) {
			theUsedKeys = null;
			for (Transaction t : keys.values())
				t.close();
			keys = theUsedKeys;
		}
	}

	/**
	 * @param cause The cause to use
	 * @return A transaction whose {@link Transaction#close()} method finishes the cause
	 */
	public static Transaction use(Object cause) {
		if (!(cause instanceof Causable))
			return Transaction.NONE;
		Causable c = (Causable) cause;
		if (c.isStarted)
			throw new IllegalStateException("This causable is already being (or has been) used");
		c.isStarted = true;
		return c::finish;
	}
}

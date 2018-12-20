package org.qommons;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * An event or something that may have a cause.
 * 
 * Each causable may itself have a cause, so cause chains can be created.
 * 
 * Causables have the ability to allow code to keep track of the effects of a cause, then perform an action when they finish that is the
 * cumulative result of the cause.
 * 
 * The typical use case is to grab the root causable and call {@link #onFinish(CausableKey)} with a key. The map returned is updated with
 * some effects of the cause. Then the terminal action of the key is called when the root cause finishes and the data in the map used to
 * cause the correct effects.
 */
public abstract class Causable {
	/** An action to be fired when a causable finishes */
	@FunctionalInterface
	public interface TerminalAction {
		/**
		 * @param cause The causable that has finished
		 * @param values The values added to the causable for this action
		 */
		void finished(Causable cause, Map<Object, Object> values);
	}

	/**
	 * This interface is so causability can be traced back to causes that spawn Causables across threads, or for other reasons where it is
	 * desired that a cause-effect relationship be traced without affecting the {@link Causable#getRootCausable() root cause}.
	 */
	public interface ChainBreak {
		/** @return The wrapped cause */
		Object getCause();
	}

	/**
	 * <p>
	 * A CausableKey to use with {@link Causable#onFinish(CausableKey)}.
	 * </p>
	 * <p>
	 * Use {@link Causable#key(TerminalAction)} or {@link Causable#key(TerminalAction, TerminalAction)} to create one.
	 * </p>
	 */
	public static final class CausableKey {
		private final Map<Object, Object> theValues;
		private final AtomicInteger theCauseCount;
		private final TerminalAction theAction;
		private final TerminalAction theAfterAction;

		private CausableKey(TerminalAction action, TerminalAction afterAction) {
			theValues = new LinkedHashMap<>();
			theCauseCount = new AtomicInteger();
			theAction = action;
			theAfterAction = afterAction;
		}

		/** @return This cause key's current value data, unmodifiable */
		public Map<Object, Object> getData() {
			return Collections.unmodifiableMap(theValues);
		}

		Supplier<Transaction> use(Causable cause) {
			theCauseCount.getAndIncrement();
			boolean[] closed = new boolean[1];
			return () -> {
				if (closed[0])
					return null;
				closed[0] = true;
				int remaining = theCauseCount.decrementAndGet();
				if (remaining > 0)
					return null;
				if (theAction != null)
					theAction.finished(cause, theValues);
				if (theAfterAction == null) {
					theValues.clear();
					return null;
				} else {
					return () -> {
						theAfterAction.finished(cause, theValues);
						theValues.clear();
					};
				}
			};
		}
	}

	private static class SimpleCause extends Causable {
		SimpleCause(Object cause) {
			super(cause);
		}
	}

	/**
	 * Creates a CausableKey to use with {@link Causable#onFinish(CausableKey)}. The key is not re-usable or thread-safe; a new one for each
	 * use.
	 * 
	 * @param action The action for the key to perform on its accumulated data when the cause(s) it is registered for finish.
	 * @return The cause key to use to perform actions from Causables
	 */
	public static CausableKey key(TerminalAction action) {
		return key(action, null);
	}

	/**
	 * Creates a CausableKey to use with {@link Causable#onFinish(CausableKey)}. The key is not re-usable or thread-safe; a new one for each
	 * use.
	 * 
	 * @param action The action for the key to perform on its accumulated data when the cause(s) it is registered for finish.
	 * @param afterAction The action to perform after all other terminal actions (except afterActions from keys that are registered after
	 *        this one
	 * @return The cause key to use to perform actions from Causables
	 */
	public static CausableKey key(TerminalAction action, TerminalAction afterAction) {
		return new CausableKey(action, afterAction);
	}

	/**
	 * @param cause The cause of the causable
	 * @return A simple Causable
	 */
	public static Causable simpleCause(Object cause) {
		return new SimpleCause(cause);
	}

	private final Object theCause;
	private final Causable theRootCausable;
	private LinkedHashMap<IdentityKey<CausableKey>, Supplier<Transaction>> theKeys;
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
	 * Finds a cause to this event's cause chain that passes the given test
	 * 
	 * @param test The test to apply
	 * @return The most immediate cause of this causable that passes the given test, or null if none exists
	 */
	public Object hasCauseLike(Predicate<Object> test) {
		return getCauseLike(c -> test.test(c) ? c : null);
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
		while ((cause instanceof Causable || cause instanceof ChainBreak) && value == null) {
			if (cause instanceof Causable) {
				root = (Causable) cause;
				cause = root.getCause();
			} else
				cause = ((ChainBreak) cause).getCause();
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
		if (theKeys == null)
			theKeys = new LinkedHashMap<>();
		theKeys.computeIfAbsent(new IdentityKey<>(key), k -> k.value.use(this));
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
		if (theKeys != null) {
			LinkedList<Transaction> postActions = null;
			while (!theKeys.isEmpty()) {
				Iterator<Supplier<Transaction>> keyActionIter = theKeys.values().iterator();
				Supplier<Transaction> keyAction = keyActionIter.next();
				keyActionIter.remove();
				Transaction postAction = keyAction.get();
				if (postAction != null) {
					if (postActions == null)
						postActions = new LinkedList<>();
					postActions.addFirst(postAction);
				}
			}
			if (postActions != null)
				for (Transaction key : postActions)
					key.close();
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

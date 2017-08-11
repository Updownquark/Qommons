package org.qommons;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/** An efficient abstract implementation of Causable */
public abstract class AbstractCausable implements Causable {
	private final Object theCause;
	private final Causable theRootCausable;
	private IdentityHashMap<Object, TerminalActionHolder> theActions;
	private boolean isFinished;

	/** @param cause The cause of this causable */
	public AbstractCausable(Object cause) {
		theCause = wrapCausable(cause);
		theRootCausable = theCause == null ? this : ((Causable) theCause).getRootCausable();
	}

	private AbstractCausable(Object cause, Void root) {
		theCause = cause;
		theRootCausable = this;
	}

	@Override
	public Object getCause() {
		return theCause;
	}

	@Override
	public Causable getRootCausable() {
		return theRootCausable;
	}

	@Override
	public Map<Object, Object> onFinish(Object key, TerminalAction action) {
		if (theActions == null)
			theActions = new IdentityHashMap<>();
		return theActions.computeIfAbsent(key, k -> new TerminalActionHolder(action));
	}

	private void finish() {
		if (isFinished)
			throw new IllegalStateException("A cause may only be finished once");
		isFinished = true;
		// The finish actions may use this causable as a cause for events they fire.
		// These events may trigger onRootFinish calls, which add more actions to this causable
		// Though this cycle is allowed, care must be taken by callers to ensure it does not become infinite
		while (theActions != null) {
			Collection<TerminalActionHolder> actions = theActions.values();
			theActions = null;
			for (TerminalActionHolder action : actions)
				action.execute(this);
		}
	}

	/**
	 * Fires a causable event (or thing) and then finishes it. A cause may only be finished once.
	 * 
	 * @param <T> The type of the action's result
	 * @param <C> The sub-type of causable to fire
	 * @param event The event (causable) to fire and finish
	 * @param action The action to perform with the cause
	 * @return The result of the operation
	 */
	public static <C extends AbstractCausable, T> T doWithF(C event, Function<? super C, T> action) {
		T ret;
		try {
			ret = action.apply(event);
		} finally {
			((AbstractCausable) event).finish();
		}
		return ret;
	}

	public static <C extends AbstractCausable> C doWith(C event, Consumer<? super C> action) {
		try {
			action.accept(event);
		} finally {
			((AbstractCausable) event).finish();
		}
		return event;
	}

	private static Causable wrapCausable(Object cause) {
		if (cause == null)
			return null;
		else if (cause instanceof Causable)
			return (Causable) cause;
		else
			return new CausableWrapper(cause);
	}

	private static final class CausableWrapper extends AbstractCausable {
		CausableWrapper(Object cause) {
			super(cause, null);
		}
	}

	private static final class TerminalActionHolder implements Map<Object, Object> {
		private final TerminalAction theAction;
		private Map<Object, Object> theValues;

		TerminalActionHolder(TerminalAction action) {
			theAction = action;
		}

		void execute(Causable cause) {
			theAction.finished(cause, this);
		}

		private void init() {
			if (theValues == null)
				theValues = new LinkedHashMap<>();
		}

		@Override
		public int size() {
			init();
			return theValues.size();
		}

		@Override
		public boolean isEmpty() {
			init();
			return theValues.isEmpty();
		}

		@Override
		public boolean containsKey(Object key) {
			init();
			return theValues.containsKey(key);
		}

		@Override
		public boolean containsValue(Object value) {
			init();
			return theValues.containsValue(value);
		}

		@Override
		public Object get(Object key) {
			init();
			return theValues.get(key);
		}

		@Override
		public Object put(Object key, Object value) {
			init();
			return theValues.put(key, value);
		}

		@Override
		public Object remove(Object key) {
			init();
			return theValues.remove(key);
		}

		@Override
		public void putAll(Map<? extends Object, ? extends Object> m) {
			init();
			theValues.putAll(m);
		}

		@Override
		public void clear() {
			init();
			theValues.clear();
		}

		@Override
		public Set<Object> keySet() {
			init();
			return theValues.keySet();
		}

		@Override
		public Collection<Object> values() {
			init();
			return theValues.values();
		}

		@Override
		public Set<Entry<Object, Object>> entrySet() {
			init();
			return theValues.entrySet();
		}
	}
}

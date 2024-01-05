package org.qommons;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

import org.qommons.collect.BetterList;
import org.qommons.collect.CircularArrayList;

/**
 * <p>
 * An event or something that may have a cause.
 * </p>
 * 
 * <p>
 * Each causable may itself have a cause (which itself may or may not be a Causable), so cause chains can be created.
 * </p>
 * 
 * <p>
 * Causables allow code to keep track of the root cause of an action and its effects, then perform an action when the root cause of the
 * action finishes. Code can thus delay its effects, potentially achieving great performance gains.
 * </p>
 * 
 * <p>
 * The typical use case is to grab the root causable and call {@link #onFinish(CausableKey)} with a key. The map returned may be updated
 * with the effects of the current cause. Then the terminal action of the key is called when the root cause finishes and the data in the map
 * may be used used to cause the correct cumulative effects.
 * </p>
 * 
 * <p>
 * Unless explicitly supported by an implementation, a Causable may only be used on a single thread. To track causality across threads, a
 * {@link ChainBreak} may be used. Use {@link #broken(Object...)} to create a ChainBreak object that may be used as the cause of a Causable.
 * It will be included in the Causable and be available from {@link #getCauseLike(Function)}, but it will not be in the Causable chain.
 * </p>
 * 
 * <p>
 * A Causable may be created using {@link Causable#simpleCause(Object [])}. {@link #use()} must then be called before the Causable is used
 * in the wild (before its {@link #onFinish(CausableKey)} method may be called) and the {@link Transaction} returned from {@link #use()}
 * must be closed to fire off all the registered finish actions.
 * </p>
 * 
 * <p>
 * Alternatively, {@link #cause(Object...)} will return a {@link CausableInUse} which also implements {@link Transaction}. Thus the Causable
 * itself may be used as a resource in a try statement.
 * </p>
 */
public interface Causable extends CausalLock.Cause {
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
		BetterList<Object> getCauses();
	}

	/** A {@link Causable} that is already in use and can be {@link #close() closed} */
	public interface CausableInUse extends Causable, Transaction {
	}

	/**
	 * <p>
	 * A key to use with {@link Causable#onFinish(CausableKey)} to keep track of the effects of a cause or set of causes and effect them
	 * when the cause chain finishes.
	 * </p>
	 * <p>
	 * Use {@link Causable#key(TerminalAction)} to create a key
	 * </p>
	 */
	public static final class CausableKey {
		private final TerminalAction theAction;
		private final TerminalAction theAfterAction;

		private CausableKey(TerminalAction action, TerminalAction afterAction) {
			theAction = action;
			theAfterAction = afterAction;
		}

		Transaction execute(Causable cause, Map<Object, Object> data) {
			if (theAction != null)
				theAction.finished(cause, data);
			if (theAfterAction != null) {
				return () -> theAfterAction.finished(cause, data);
			} else
				return null;
		}

		boolean hasPrimaryAction() {
			return theAction != null;
		}
	}

	/** The effect of a {@link CausableKey} in a {@link Causable} */
	public static class Effect extends AbstractMap<Object, Object> {
		private final CausableKey theKey;
		private Map<Object, Object> theData;
		private boolean isExecuted;

		/** @param key The key this effect is for */
		public Effect(CausableKey key) {
			theKey = key;
		}

		/** @return The key this effect is for */
		public CausableKey getKey() {
			return theKey;
		}

		/**
		 * Executes the {@link #getKey() key}'s primary action against the cause
		 * 
		 * @param cause The cause to execute the key against
		 * @return A transaction that execute's the key's secondary action against the cause, or null if the key has no secondary action
		 */
		public Transaction execute(Causable cause) {
			isExecuted = true;
			return theKey.execute(cause, this);
		}

		/** @return Whether this effect has been {@link #execute(Causable) executed} */
		public boolean isExecuted() {
			return isExecuted;
		}

		/** @return A new map to hold data for this effect */
		protected Map<Object, Object> createData() {
			return new LinkedHashMap<>();
		}

		/** Initializes this effect's data map if has not yet been */
		protected void initData() {
			if (theData == null)
				theData = createData();
		}

		@Override
		public Set<Map.Entry<Object, Object>> entrySet() {
			if (theData == null)
				return Collections.emptySet();
			else
				return theData.entrySet();
		}

		@Override
		public boolean containsKey(Object key) {
			return theData != null && theData.containsKey(key);
		}

		@Override
		public Object put(Object key, Object value) {
			initData();
			return theData.put(key, value);
		}

		@Override
		public void putAll(Map<? extends Object, ? extends Object> m) {
			if (m.isEmpty())
				return;
			initData();
			theData.putAll(m);
		}

		@Override
		public void clear() {
			if (theData != null)
				theData.clear();
		}
	}

	/** An abstract implementation of Causable */
	public static class AbstractCausable implements Causable {
		private final BetterList<Object> theCauses;
		private final Causable theRootCausable;
		private LinkedHashMap<CausableKey, Effect> theKeys;
		private boolean isStarted;
		private boolean isFinished;
		private boolean isTerminated;

		/** @param causes The causes of this causable */
		public AbstractCausable(Object... causes) {
			// There are prettier ways to do this, but this is a hot spot, so we need to save as many cycles as possible
			if (causes == null) {
				theRootCausable = this;
				theCauses = BetterList.EMPTY;
			} else {
				int size = causes.length;
				Causable root = null;
				for (Object cause : causes) {
					if (cause == null)
						size--;
					else if (root == null && cause instanceof Causable && !(cause instanceof ChainBreak)) {
						if (((Causable) cause).isTerminated())
							throw new IllegalStateException("Cannot use a finished Causable as a cause");
						root = ((Causable) cause).getRootCausable();
						if (root.isTerminated())
							throw new IllegalStateException("Cannot use a finished Causable as a cause");
					}
				}
				theRootCausable = root != null ? root : this;
				if (size == causes.length)
					theCauses = BetterList.of(causes);
				else {
					Object[] notNullCauses = new Object[size];
					int i = 0;
					for (Object cause : causes) {
						if (cause != null)
							notNullCauses[i++] = cause;
					}
					theCauses = BetterList.of(notNullCauses);
				}
			}
		}

		/** @param causes The causes of this causable */
		public AbstractCausable(Collection<?> causes) {
			this(causes.toArray());
		}

		@Override
		public BetterList<Object> getCauses() {
			return theCauses;
		}

		@Override
		public Causable getRootCausable() {
			return theRootCausable;
		}

		@Override
		public Map<Object, Object> onFinish(CausableKey key) {
			if (!isStarted)
				throw new IllegalStateException("Not started!  Use Causable.use(Causable)");
			else if (isTerminated)
				throw new IllegalStateException("This cause has already terminated");
			if (theKeys == null)
				theKeys = new LinkedHashMap<>();
			return theKeys.computeIfAbsent(key, Effect::new);
		}

		@Override
		public boolean isFinished() {
			return isFinished;
		}

		@Override
		public boolean isTerminated() {
			return isTerminated;
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
			try {
				Causable.terminateFull(theKeys, this);
			} finally {
				isTerminated = true;
			}
		}

		@Override
		public Transaction use() {
			if (isStarted)
				throw new IllegalStateException("This causable is already being (or has been) used");
			isStarted = true;
			return this::finish;
		}

		private static class SimpleCause extends AbstractCausable {
			SimpleCause(Object... causes) {
				super(causes);
			}
		}
	}

	/** Simple {@link ChainBreak} implementation */
	public static class SimpleChainBreak implements ChainBreak {
		private final BetterList<Object> theCauses;

		/** @param causes The causes for this chain break */
		public SimpleChainBreak(Object... causes) {
			theCauses = QommonsUtils.filterMap(Arrays.asList(causes), v -> v != null, null);
		}

		@Override
		public BetterList<Object> getCauses() {
			return theCauses;
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
	 * Runs the termination sequence against a set of effects for a cause
	 * 
	 * @param keys The key->effect map to execute
	 * @param cause The cause to execute the effects against
	 */
	public static void terminateFull(Map<CausableKey, Effect> keys, Causable cause) {
		if (keys == null)
			return;
		while (!keys.isEmpty())
			terminate(keys.values(), cause);
	}

	/**
	 * Runs the termination sequence against a set of effects for a cause
	 * 
	 * @param effects The effects to execute
	 * @param cause The cause to execute the effects against
	 */
	public static void terminate(Collection<Effect> effects, Causable cause) {
		LinkedList<Transaction> postActions = null;
		int onlyPostActions = 0;
		while (effects.size() > onlyPostActions) {
			onlyPostActions = 0;
			int expectedSize = effects.size();
			Iterator<Effect> keyIter = effects.iterator();
			while (effects.size() == expectedSize && keyIter.hasNext()) {
				Effect effect = keyIter.next();
				if (!effect.getKey().hasPrimaryAction()) {
					onlyPostActions++;
					continue;
				}
				keyIter.remove();
				expectedSize--;
				Transaction postAction = effect.execute(cause);
				if (postAction != null) {
					if (postActions == null)
						postActions = new LinkedList<>();
					postActions.addFirst(postAction);
				}
			}
		}
		while (!effects.isEmpty()) {
			Iterator<Effect> keyIter = effects.iterator();
			int expectedSize = effects.size();
			while (effects.size() == expectedSize && keyIter.hasNext()) {
				Effect effect = keyIter.next();
				keyIter.remove();
				expectedSize--;
				Transaction postAction = effect.execute(cause);
				if (postAction != null)
					postAction.close();
			}
		}
		if (postActions != null) {
			for (Transaction key : postActions)
				key.close();
			postActions.clear();
		}
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
	 * @param causes The causes of the causable
	 * @return A simple Causable
	 */
	public static Causable simpleCause(Object... causes) {
		return new AbstractCausable.SimpleCause(causes);
	}

	/**
	 * @param causes The causes of the causable
	 * @return A simple Causable
	 */
	public static Causable simpleCause(Collection<?> causes) {
		return new AbstractCausable.SimpleCause(causes.toArray());
	}

	/**
	 * Used to break the chain for {@link #getRootCausable()}
	 * 
	 * @param causes The causes of the causable
	 * @return A simple broken-chain causable
	 * @see ChainBreak
	 */
	public static ChainBreak broken(Object... causes) {
		return new SimpleChainBreak(causes);
	}
	
	/** @return The causes of this event or thing--typically another event or null */
	BetterList<Object> getCauses();

	/** @return The thing that caused the chain of events that led to this event or thing */
	Causable getRootCausable();

	/** @return The root cause of this causable (the root may or may not be causable itself) */
	default Object getRootCause() {
		Object cause = getRootCausable();
		while (true) {
			if (cause instanceof ChainBreak && !((ChainBreak) cause).getCauses().isEmpty())
				cause = ((ChainBreak) cause).getCauses().peekFirst();
			else if (cause instanceof Causable) {
				Causable newCause = ((Causable) cause).getRootCausable();
				if (newCause == cause)
					break;
			}
		}
		return cause;
	}

	/**
	 * Finds a cause to this event's cause chain that passes the given test
	 * 
	 * @param test The test to apply
	 * @return The most immediate cause of this causable that passes the given test, or null if none exists
	 */
	default Object hasCauseLike(Predicate<Object> test) {
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
	default <T> T getCauseLike(Function<Object, T> test) {
		T value = test.apply(this);
		if (value != null)
			return value;
		CircularArrayList<Object> causes = CircularArrayList.build().build().withAll(getCauses());
		while (!causes.isEmpty()) {
			Object cause = causes.removeFirst();
			value = test.apply(cause);
			if (value != null)
				return value;
			if (cause instanceof Collection)
				causes.addAll(0, (Collection<?>) cause);
			else if (cause instanceof Causable)
				causes.addAll(((Causable) cause).getCauses());
			else if (cause instanceof ChainBreak)
				causes.addAll(((ChainBreak) cause).getCauses());
		}
		return value;
	}

	/**
	 * @param key The key to add the action for. An action will only be added once to a causable for a given key.
	 * @return A map of key-values that may be modified to keep track of information from multiple sub-causes of this cause
	 */
	Map<Object, Object> onFinish(CausableKey key);

	/** @return Whether this causable has finished or is finishing */
	boolean isFinished();

	/** @return Whether this causable has completely finished being fired */
	boolean isTerminated();

	/**
	 * Begins use of this cause. This method may only be called once.
	 * 
	 * @return A transaction whose {@link Transaction#close()} method finishes this cause
	 */
	Transaction use();

	/**
	 * Same as {@link #use()}, but may be called with null or non-Causable values, in which case {@value Transaction#NONE} will be returned
	 * 
	 * @param cause The cause to use
	 * @return A transaction whose {@link Transaction#close()} method finishes the cause
	 */
	static Transaction use(Object cause) {
		if (cause instanceof Causable)
			return ((Causable) cause).use();
		else
			return Transaction.NONE;
	}

	/**
	 * A nice little method that allows causes to be created as a resource in a try-resources statement for brevity.
	 * 
	 * @param causes The causes for the new causable
	 * @return A {@link CausableInUse}, a cause that is already in use and implements {@link Transaction} so it can be closed
	 */
	static CausableInUse cause(Object... causes) {
		return Impl.cause(causes);
	}

	/**
	 * @param causes The causes to wrap
	 * @return A simple cause, wrapping the given causes
	 */
	static Causable simpleDelegate(Object... causes) {
		return new SimpleDelegate(causes);
	}

	/** Implementation details for static methods of this class */
	class Impl {
		private static class CauseInUseImpl extends AbstractCausable implements CausableInUse {
			private final Transaction theInUseT;
			private final AtomicInteger theDepth;

			CauseInUseImpl(Collection<?> causes) {
				super(causes);
				theInUseT = super.use();
				theDepth = new AtomicInteger();
			}

			CauseInUseImpl descend() {
				theDepth.incrementAndGet();
				return this;
			}

			@Override
			public void close() {
				if (theDepth.decrementAndGet() == 0) {
					CAUSES.remove(getCauses());
					theInUseT.close();
				}
			}
		}

		private static class SimpleCauseInUse extends AbstractCausable implements CausableInUse {
			private final Transaction theInUseT;

			public SimpleCauseInUse() {
				super();
				theInUseT = super.use();
			}

			@Override
			public void close() {
				theInUseT.close();
			}
		}

		private static final ConcurrentHashMap<Object, CauseInUseImpl> CAUSES = new ConcurrentHashMap<>();

		private Impl() {
		}

		static CausableInUse cause(Object... causes) {
			List<Object> nnCauses = BetterList.of(causes).quickFilter(c -> c != null);
			if (nnCauses.isEmpty())
				return new SimpleCauseInUse();
			else
				return CAUSES.computeIfAbsent(nnCauses, __ -> new CauseInUseImpl(nnCauses)).descend();
		}
	}

	/** A simple cause that wraps another */
	class SimpleDelegate implements Causable {
		private final Causable theDelegate;
		private final BetterList<Object> theCauses;

		SimpleDelegate(Object... causes) {
			if (causes == null || causes.length == 0 || !(causes[0] instanceof Causable))
				throw new IllegalArgumentException("Delegation must have a causable to delegate to");
			for (Object cause : causes) {
				if (cause instanceof Causable && !(cause instanceof ChainBreak)) {
					if (((Causable) cause).isTerminated())
						throw new IllegalStateException("Cannot use a finished Causable as a cause");
					if (((Causable) cause).getRootCausable().isTerminated())
						throw new IllegalStateException("Cannot use a finished Causable as a cause");
					break;
				}
			}
			theDelegate = (Causable) causes[0];
			theCauses = BetterList.of(causes);
		}

		@Override
		public BetterList<Object> getCauses() {
			return theCauses;
		}

		@Override
		public Causable getRootCausable() {
			return theDelegate.getRootCausable();
		}

		@Override
		public Map<Object, Object> onFinish(CausableKey key) {
			return theDelegate.onFinish(key);
		}

		@Override
		public boolean isFinished() {
			return theDelegate.isFinished();
		}

		@Override
		public boolean isTerminated() {
			return theDelegate.isTerminated();
		}

		@Override
		public Transaction use() {
			return Transaction.NONE;
		}
	}
}

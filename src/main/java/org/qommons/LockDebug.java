package org.qommons;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;

import org.qommons.collect.BetterHashMap;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MapEntryHandle;
import org.qommons.tree.BetterTreeList;

/**
 * <p>
 * Allows more visibility into Qommons lock types to debug deadlocks and other issues.
 * </p>
 * <p>
 * This class's functionality is off by default for performance (it is quite heavy and slow), but can be enabled either by calling
 * {@link LockDebug#setDebugging(boolean)} or by setting the "org.qommons.LockDebug" system property to true before this class is
 * initialized.
 * </p>
 */
public class LockDebug {
	private static boolean DEBUGGING;

	static {
		setDebugging("true".equals(System.getProperty(LockDebug.class.getName())));
	}

	/** @return Whether this class is tracking locks */
	public static boolean isDebugging() {
		return DEBUGGING;
	}

	/** @param debugging Whether to track locks */
	public static void setDebugging(boolean debugging) {
		DEBUGGING = debugging;
	}

	/**
	 * @param lock The lock to lock
	 * @param debugInfo Extra debug information to attach to the lock
	 * @param exclusive Whether the lock will be an exclusive one or not
	 * @param justTry Whether the lock attempt is just a try which may return a null transaction if it fails instead of locking or throwing
	 * @param transaction Performs the lock
	 * @return The transaction to wrap the supplied one
	 */
	public static Transaction debug(Object lock, Object debugInfo, boolean exclusive, boolean justTry, Supplier<Transaction> transaction) {
		if (!DEBUGGING)
			return transaction.get();
		return theLocks.computeIfAbsent(lock, lck -> new LockInfo(lck)).lock(debugInfo, exclusive, justTry, transaction);
	}

	private static final ConcurrentHashMap<Object, LockInfo> theLocks = new ConcurrentHashMap<>();
	private static final ThreadLocal<LockStack> theStacks = ThreadLocal.withInitial(LockStack::new);

	/** Information about a particular lockable object */
	public static class LockInfo {
		final Object theLock;
		@SuppressWarnings("unused")
		private LockStack theExclusiveHolder;
		private final Map<LockStack, int[]> theNonExclusiveHolders;
		private final ListenerList<LockHold> theExclusiveHolds;
		private final ListenerList<LockHold> theNonExclusiveHolds;

		LockInfo(Object lock) {
			theLock = lock;
			theNonExclusiveHolders = new ConcurrentHashMap<>();
			theExclusiveHolds = ListenerList.build().build();
			theNonExclusiveHolds = ListenerList.build().build();
		}

		Transaction lock(Object debugInfo, boolean exclusive, boolean justTry, Supplier<Transaction> transaction) {
			LockHold newHold;
			synchronized (LockDebug.class) {
				LockStack stack = theStacks.get();
				if (!justTry) {
					List<LockHold> cycle = detectCycle(stack, exclusive);
					if (cycle != null) {
						System.err.println("Imminent deadlock detected!  " + cycle);
						BreakpointHere.breakpoint();
					}
				}
				newHold = stack.lock(this, debugInfo, exclusive);
				newHold.theLockRef = (exclusive ? theExclusiveHolds : theNonExclusiveHolds).add(newHold, false);
				if (exclusive)
					theExclusiveHolder = stack;
				else
					theNonExclusiveHolders.computeIfAbsent(stack, __ -> new int[1])[0]++;
			}
			return newHold.lock(transaction);
		}

		void remove(LockHold hold) {
			if (hold.isExclusive) {
				if (theExclusiveHolds.isEmpty())
					theExclusiveHolder = null;
			} else {
				theNonExclusiveHolders.compute(hold.theStack, (stack, count) -> {
					if (count[0] == 1)
						return null;
					count[0]--;
					return count;
				});
			}
		}

		private List<LockHold> detectCycle(LockStack stack, boolean exclusive) {
			if (exclusive && theNonExclusiveHolders.containsKey(stack) && !(theLock instanceof StampedLock))
				return listOf(null); // Just debugging
			// if(excl!=null && excl!=stack)
			// theExclusiveHolds.forEach(hold->{
			// if(hold.theStack!=theStack
			// });
			// List<LockHold>[] cycle = new List[1];
		
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String toString() {
			return theLock.toString();
		}
	}

	/** Represents a lock that is either already held or is being attempted */
	public static class LockHold implements Transaction {
		final LockInfo theLock;
		private final Object theDebugInfo;
		final boolean isExclusive;
		final LockStack theStack;
		private final ElementId theStackElement;
		private final ElementId theStackHoldEntry;
		private final ElementId theStackHoldListElement;
		Runnable theLockRef;
		Transaction theTransaction;
		@SuppressWarnings("unused")
		private final StackTraceElement[] theStackTrace;

		LockHold(LockInfo lock, Object debugInfo, boolean exclusive, LockStack stack, ElementId stackElement, ElementId stackHoldEntry,
			ElementId stackHoldListElement) {
			theLock = lock;
			theDebugInfo = debugInfo;
			isExclusive = exclusive;
			theStack = stack;
			theStackElement = stackElement;
			theStackHoldEntry = stackHoldEntry;
			theStackHoldListElement = stackHoldListElement;
			theStackTrace = theStack.theThread.getStackTrace();
		}

		Transaction lock(Supplier<Transaction> transaction) {
			Transaction trans = transaction.get();
			theTransaction = trans;
			if (trans == null || trans == Transaction.NONE) {
				remove();
				return trans;
			} else
				return this;
		}

		@Override
		public void close() {
			synchronized (LockDebug.class) {
				if (!theStackElement.isPresent()) {
					System.err.println("Duplicate close calls for " + this);
					return;
				}
				remove();
				theTransaction.close();
			}
		}

		private void remove() {
			if (!theStack.theStack.getTerminalElement(false).getElementId().equals(theStackElement))
				throw new IllegalStateException("Debug implementation error");
			theLockRef.run();
			theLock.remove(this);
			BetterList<LockHold> heldLocks = theStack.theHeldLocks.getEntryById(theStackHoldEntry).getValue();
			if (heldLocks.size() == 1)
				theStack.theHeldLocks.mutableEntry(theStackHoldEntry).remove();
			else
				heldLocks.mutableElement(theStackHoldListElement).remove();
			theStack.theStack.removeLast();
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append(theLock.theLock).append('(').append(isExclusive ? "x" : "o").append(") by ");
			if (theDebugInfo instanceof Identifiable)
				str.append(((Identifiable) theDebugInfo).getIdentity());
			else
				str.append(theDebugInfo);
			str.append(" on ").append(theStack);
			if (theTransaction == null)
				str.append("...");
			return str.toString();
		}
	}

	/** Contains information about locks held by a particular thread */
	public static class LockStack {
		final Thread theThread;
		final BetterList<LockHold> theStack;
		final BetterMap<LockInfo, BetterList<LockHold>> theHeldLocks;

		LockStack() {
			theThread = Thread.currentThread();
			theStack = BetterTreeList.<LockHold> build().build();
			theHeldLocks = BetterHashMap.build().buildMap();
		}

		LockHold lock(LockInfo lock, Object debugInfo, boolean exclusive) {
			ElementId stackEl = theStack.addElement(null, false).getElementId();
			MapEntryHandle<LockInfo, BetterList<LockHold>> stackHoldEntry = theHeldLocks.getOrPutEntry(lock,
				__ -> BetterTreeList.<LockHold> build().build(), null, null, false, null);
			ElementId holdListEl = stackHoldEntry.get().addElement(null, false).getElementId();
			LockHold hold = new LockHold(lock, debugInfo, exclusive, this, stackEl, stackHoldEntry.getElementId(), holdListEl);
			theStack.mutableElement(stackEl).set(hold);
			stackHoldEntry.get().mutableElement(holdListEl).set(hold);
			return hold;
		}

		LinkedList<LockHold> findCycleTo(LockInfo lock, boolean exclusive) {
			BetterList<LockHold> held = theHeldLocks.get(lock);
			if (held == null)
				return null;
			for (LockHold hold : held)
				if (!exclusive || hold.isExclusive)
					return listOf(hold);
			return null;
		}

		@Override
		public String toString() {
			return theThread.getName() + '(' + theThread.getId() + ')';
		}
	}

	static LinkedList<LockHold> listOf(LockHold hold) {
		LinkedList<LockHold> list = new LinkedList<>();
		list.add(hold);
		return list;
	}
}

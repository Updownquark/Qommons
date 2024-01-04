package org.qommons;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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
		public boolean supportsInvoke() {
			return true;
		}

		@Override
		public void invoke(Runnable task) {
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
		public boolean supportsInvoke() {
			return false;
		}

		@Override
		public void invoke(Runnable task) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String toString() {
			return "Uneventable";
		}
	};
	/** Thread constraint for the AWT {@link EventQueue} thread */
	public static final ThreadConstraint EDT = new CachedThreadConstraint() {
		@Override
		public boolean isEventThread() {
			return EventQueue.isDispatchThread();
		}

		@Override
		public boolean supportsInvoke() {
			return true;
		}

		@Override
		protected void reallyInvokeLater(Runnable task) {
			EventQueue.invokeLater(task);
		}

		@Override
		public String toString() {
			return "AWT Event Queue Thread";
		}
	};

	/** @return Whether events might be fired on the current thread */
	boolean isEventThread();

	/**
	 * @return Whether this thread constraint supports the {@link #invoke(Runnable)} method. A thread constraint can serve as merely an
	 *         indicator, without supporting some operations that require invocation. Invocation may be unsupported, for example, by
	 *         {@link #dedicated(Thread, Consumer) dedicated} constraints.
	 */
	boolean supportsInvoke();

	/**
	 * Performs the task on an acceptable thread. If the current thread is acceptable, the task will be executed inline. Otherwise, the task
	 * will be queued to be executed on an acceptable thread and this method will return immediately, likely before the task is executed.
	 * 
	 * @param task The task to execute
	 */
	void invoke(Runnable task);

	/**
	 * @param thread The thread that updates will be executed on
	 * @param invoke Supports {@link ThreadConstraint#invoke(Runnable)}, or null if invocation is not to be supported.
	 * @return A {@link ThreadConstraint} that prevents modification off of the given thread.
	 */
	public static DedicatedThreaded dedicated(Thread thread, Consumer<Runnable> invoke) {
		return new DedicatedThreaded(thread, invoke);
	}

	/**
	 * @param constraints The thread constraints to determine whether a thread is an {@link #isEventThread() event thread}.
	 * @return A constraint that supports modification to any of the given constraints' event threads, and uses the first
	 *         invocation-supporting constraint to support invocation (if any).
	 */
	public static ThreadConstraint union(ThreadConstraint... constraints) {
		List<ThreadConstraint> cs = new ArrayList<>(constraints.length - 1);
		for (ThreadConstraint c : constraints) {
			if (c == ThreadConstraint.NONE)
				continue;
			else if (c == ThreadConstraint.ANY)
				return c;
			boolean found = false;
			for (int i = 0; i < cs.size(); i++) {
				if (cs.get(i).equals(c)) {
					found = true;
					break;
				}
			}
			if (!found)
				cs.add(c);
		}
		if (cs.isEmpty())
			return ThreadConstraint.NONE;
		if (cs.size() == 1)
			return cs.get(0);
		return new UnionConstraint(QommonsUtils.unmodifiableCopy(cs));
	}

	/**
	 * A partial implementation of ThreadConstraint that implements caching. This can be handy when calls to an
	 * {@link ThreadConstraint#invoke(Runnable)} method would otherwise be quite slow, which turns out to be the case for
	 * {@link EventQueue#invokeLater(Runnable)}.
	 */
	static abstract class CachedThreadConstraint implements ThreadConstraint {
		private static final int EMPTY_QUEUE_RETRIES = 10;
		private final ConcurrentLinkedQueue<Runnable> theEventCache = new ConcurrentLinkedQueue<>();
		private final AtomicInteger theQueuedRuns = new AtomicInteger();
		private int theEmptyRuns;

		protected abstract void reallyInvokeLater(Runnable task);

		@Override
		public void invoke(Runnable task) {
			// If the queue is not empty, we need to add the task to the queue instead of running it inline to avoid ordering problems
			if (theQueuedRuns.get() > 0 || !theEventCache.isEmpty() || !isEventThread()) {
				theEventCache.add(task);
				if (theQueuedRuns.compareAndSet(0, 1))
					reallyInvokeLater(this::flushCachedEvents);
			} else
				task.run();
		}

		private void flushCachedEvents() {
			theQueuedRuns.getAndDecrement();
			int emptyRuns;
			if (runCache())
				theEmptyRuns = emptyRuns = 0;
			else
				emptyRuns = ++theEmptyRuns;
			if (emptyRuns < EMPTY_QUEUE_RETRIES) {
				theQueuedRuns.getAndIncrement();
				reallyInvokeLater(this::flushCachedEvents);
			}
		}

		private boolean runCache() {
			Runnable task = theEventCache.poll();
			if (task == null)
				return false;
			do {
				try {
					task.run();
				} catch (RuntimeException e) {
					e.printStackTrace();
				}
				task = theEventCache.poll();
			} while (task != null);
			return true;
		}
	}

	/** Implements {@link ThreadConstraint#dedicated(Thread, Consumer)} */
	static class DedicatedThreaded implements ThreadConstraint {
		private final Thread theThread;
		private final Consumer<Runnable> theInvoke;

		DedicatedThreaded(Thread thread, Consumer<Runnable> invoke) {
			theThread = thread;
			theInvoke = invoke;
		}

		@Override
		public boolean isEventThread() {
			return Thread.currentThread() == theThread;
		}

		@Override
		public boolean supportsInvoke() {
			return theInvoke != null;
		}

		@Override
		public void invoke(Runnable task) {
			if (theInvoke == null)
				throw new UnsupportedOperationException();
			theInvoke.accept(task);
		}

		@Override
		public int hashCode() {
			return theThread.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof DedicatedThreaded && theThread.equals(((DedicatedThreaded) obj).theThread);
		}

		@Override
		public String toString() {
			return "Dedicated@" + theThread.getName();
		}
	}

	/** Implements {@link ThreadConstraint#union(ThreadConstraint...)} */
	static class UnionConstraint implements ThreadConstraint {
		private final List<ThreadConstraint> theConstraints;
		private final ThreadConstraint theInvocationSupport;

		UnionConstraint(List<ThreadConstraint> constraints) {
			theConstraints = constraints;
			ThreadConstraint invocationSupport = null;
			for (ThreadConstraint tc : theConstraints) {
				if (tc.supportsInvoke()) {
					invocationSupport = tc;
					break;
				}
			}
			theInvocationSupport = invocationSupport;
		}

		@Override
		public boolean isEventThread() {
			for (ThreadConstraint constraint : theConstraints) {
				if (constraint.isEventThread())
					return true;
			}
			return false;
		}

		@Override
		public boolean supportsInvoke() {
			// We already checked that it supports invocation, but I dunno, maybe there's a reason this could be dynamic
			return theInvocationSupport != null && theInvocationSupport.supportsInvoke();
		}

		@Override
		public void invoke(Runnable task) {
			theConstraints.get(0).invoke(task);
		}

		@Override
		public int hashCode() {
			return theConstraints.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof UnionConstraint))
				return false;
			return theConstraints.equals(((UnionConstraint) obj).theConstraints);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder("union(");
			for (int i = 0; i < theConstraints.size(); i++) {
				if (i > 0)
					str.append(',');
				str.append(theConstraints.get(i));
			}
			return str.append(')').toString();
		}
	}
}

package org.qommons;

import java.awt.EventQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

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
		public void invoke(Runnable task) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String toString() {
			return "Not ThreadConstrained";
		}
	};
	/** Thread constraint for the AWT {@link EventQueue} thread */
	public static final ThreadConstraint EDT = new CachedThreadConstraint() {
		@Override
		public boolean isEventThread() {
			return EventQueue.isDispatchThread();
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
	 * Performs the task on an acceptable thread. If the current thread is acceptable, the task will be executed inline. Otherwise, the task
	 * will be queued to be executed on an acceptable thread and this method will return immediately, likely before the task is executed.
	 * 
	 * @param task The task to execute
	 */
	void invoke(Runnable task);

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
			if (!isEventThread()) {
				theEventCache.add(task);
				if (theQueuedRuns.compareAndSet(0, 1))
					reallyInvokeLater(this::emptyEdtEvents);
			} else {
				runCache();
				task.run();
			}
		}

		private void emptyEdtEvents() {
			theQueuedRuns.getAndDecrement();
			int emptyRuns;
			if (runCache())
				theEmptyRuns = emptyRuns = 0;
			else
				emptyRuns = ++theEmptyRuns;
			if (emptyRuns < EMPTY_QUEUE_RETRIES) {
				theQueuedRuns.getAndIncrement();
				reallyInvokeLater(this::emptyEdtEvents);
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
}

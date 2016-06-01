/*
 * ThreadPoolWorker.java Created Jun 13, 2008 by Andrew Butler, PSL
 */
package org.qommons;

import org.apache.log4j.Logger;

/**
 * A fairly simple but safe thread pool. The main method, {@link #run(Runnable, Worker.ErrorListener)}, runs a user's task in its own
 * thread.<br />
 * The number of threads in a ThreadPool grows and shrinks quadratically.
 */
public class ThreadPoolWorker implements Worker {
	private static class TaskQueueObject {
		final Runnable task;

		final Worker.ErrorListener listener;

		TaskQueueObject(Runnable aTask, Worker.ErrorListener aListener) {
			task = aTask;
			listener = aListener;
		}
	}

	static Logger log = Logger.getLogger(ThreadPoolWorker.class);

	private final String theName;

	private int theMaxThreadCount;

	volatile int theThreadCounter;

	private final java.util.concurrent.locks.ReentrantLock theLock;

	private final java.util.List<ReusableThread> theAvailableThreads;

	private final java.util.List<ReusableThread> theInUseThreads;

	private final java.util.List<TaskQueueObject> theTaskQueue;

	private int thePriority;

	private boolean isClosed;

	/**
	 * A subtype of thread that takes up <i>very</i> little CPU time while resting, but may execute a given task instantaneously. Groups of
	 * this type are managed by the {@link ThreadPoolWorker} class.
	 */
	private class ReusableThread extends Thread {
		private volatile boolean isAlive;

		private volatile Runnable theTask;

		private volatile Worker.ErrorListener theListener;

		ReusableThread() {
			isAlive = true;
			theThreadCounter++;
			setName(ThreadPoolWorker.this.getName() + " #" + theThreadCounter);
			setPriority(getPriority());
		}

		@Override
		public void run() {
			while (isAlive) {
				if (theTask != null) {
					try {
						try {
							theTask.run();
						} catch (Error e) {
							theListener.error(e);
						} catch (RuntimeException e) {
							theListener.runtime(e);
						} catch (Throwable e) {
							log.error("Should be impossible to get here", e);
						}
					} catch (Throwable e) {
						log.error("Error listener threw exception: ", e);
					} finally {
						theTask = null;
						release(this);
					}
					if (!isAlive)
						break;
				}
				if (!isAlive)
					break;
				try {
					Thread.sleep(24L * 60 * 60 * 1000);
				} catch (InterruptedException e) {
					// Do nothing--this is normal for waking up
				}
			}
		}

		/**
		 * Runs the given task
		 * 
		 * @param task The task to be run
		 * @param listener The listener to notify in case the task throws a Throwable
		 */
		void run(Runnable task, Worker.ErrorListener listener) {
			if (theTask != null)
				throw new IllegalStateException("This worker thread is already running a task");
			theListener = listener;
			theTask = task;
			if (isAlive())
				interrupt();
		}

		/**
		 * Kills this thread as soon as its current task is finished, or immediately if this thread is not currently busy
		 * 
		 * @param active Whether the thread is currently executing a task or not
		 */
		void kill(boolean active) {
			isAlive = false;
			if (!active)
				interrupt();
		}

		/** @return Whether the thread is running a task or available to run one */
		boolean isActive() {
			return isAlive;
		}
	}

	/**
	 * Creates a ThreadPool with a default maximum thread count (the number of available processors times 2. This improves performance
	 * during I/O intensive operations.).
	 * 
	 * @param name The name for this worker
	 */
	public ThreadPoolWorker(String name) {
		this(name, Runtime.getRuntime().availableProcessors() * 2);
	}

	/**
	 * Creates a ThreadPool with a specified maximum thread count
	 * 
	 * @param name The name for this worker
	 * @param threads The max thread count. See {@link #setMaxThreadCount(int)}
	 */
	public ThreadPoolWorker(String name, int threads) {
		theName = name;
		theLock = new java.util.concurrent.locks.ReentrantLock();
		theAvailableThreads = new java.util.ArrayList<ReusableThread>();
		theInUseThreads = new java.util.ArrayList<ReusableThread>();
		theTaskQueue = new java.util.LinkedList<TaskQueueObject>();
		thePriority = Thread.NORM_PRIORITY;
		setMaxThreadCount(threads);
	}

	/**
	 * Gets and executes a task in a thread from the pool or creates a new thread if none is available and the maximum thread count for the
	 * pool is not met. If the max thread count is met, the task will be queued up to run when a currently-executing thread finishes its
	 * task and is released.
	 * 
	 * @see Worker#run(Runnable, Worker.ErrorListener)
	 */
	@Override
	public void run(Runnable task, Worker.ErrorListener listener) {
		if (isClosed)
			throw new IllegalStateException("This worker is closed--no new tasks will be accepted");
		theLock.lock();
		try {
			if (theAvailableThreads.size() == 0)
				adjustThreadCount();
			if (theAvailableThreads.size() == 0)
				theTaskQueue.add(new TaskQueueObject(task, listener));
			else {
				ReusableThread thread = theAvailableThreads.remove(theAvailableThreads.size() - 1);
				theInUseThreads.add(thread);
				thread.run(task, listener);
				if (!thread.isAlive())
					thread.start();
			}
		} finally {
			theLock.unlock();
		}
	}

	/**
	 * Releases a thread back to the thread pool after it has performed its task
	 * 
	 * @param thread The thread that has performed its task and may be pooled again and reused
	 */
	void release(ReusableThread thread) {
		theLock.lock();
		try {
			if (thread.isActive() && theTaskQueue.size() > 0) {
				TaskQueueObject task = theTaskQueue.remove(0);
				thread.run(task.task, task.listener);
			} else {
				theInUseThreads.remove(thread);
				if (isClosed)
					thread.kill(true);
				else {
					if (thread.isActive())
						theAvailableThreads.add(thread);
					adjustThreadCount();
				}
			}
		} finally {
			theLock.unlock();
		}
	}

	/** @return This worker's name */
	public String getName() {
		return theName;
	}

	/** @return The priority at which threads under this worker run */
	public int getPriority() {
		return thePriority;
	}

	/** @param priority The priority at which threads under this worker should run */
	public void setPriority(int priority) {
		thePriority = priority;
		for (Thread t : theAvailableThreads)
			t.setPriority(priority);
		for (Thread t : theInUseThreads)
			t.setPriority(priority);
	}

	/** @return Whether this thread pool has been marked as closed */
	public boolean isClosed() {
		return isClosed;
	}

	/**
	 * Shrinks the thread pool's size to zero. Calling this method does not kill any currently executing tasks, and tasks that are queued up
	 * will be executed in due time. No new tasks will be accepted.
	 */
	@Override
	public void close() {
		isClosed = true;
		theLock.lock();
		try {
			java.util.Iterator<ReusableThread> iter;
			iter = theAvailableThreads.iterator();
			while (iter.hasNext()) {
				iter.next().kill(false);
				iter.remove();
			}
		} finally {
			theLock.unlock();
		}
	}

	/**
	 * Shrinks the thread pool's size to zero. Calling this method does not kill any currently executing tasks, but it will cause tasks that
	 * are queued up to never be executed (a situation that only arises when the number of tasks needing to be executed exceeds this pool's
	 * maximum thread count).
	 */
	@Override
	public void closeNow() {
		theLock.lock();
		try {
			theTaskQueue.clear();
			java.util.Iterator<ReusableThread> iter;
			iter = theInUseThreads.iterator();
			while (iter.hasNext())
				iter.next().kill(true);
			iter = theAvailableThreads.iterator();
			while (iter.hasNext()) {
				iter.next().kill(false);
				iter.remove();
			}
		} finally {
			theLock.unlock();
		}
	}

	@Override
	protected void finalize() {
		close();
	}

	/** @return The total number of threads managed by this thread pool */
	public int getThreadCount() {
		return theAvailableThreads.size() + theInUseThreads.size();
	}

	/** @return The number of threads in this thread pool available for new tasks */
	public int getAvailableThreadCount() {
		return theAvailableThreads.size();
	}

	/** @return The number of threads in this thread pool currently executing tasks */
	public int getInUseThreadCount() {
		return theInUseThreads.size();
	}

	/**
	 * @return The number of tasks that have been queued in this worker but are not currently being executed
	 */
	public int getQueuedTaskCount() {
		return theTaskQueue.size();
	}

	private void adjustThreadCount() {
		theLock.lock();
		try {
			int newTC = getNewThreadCount();
			int total = getThreadCount();
			if (newTC < total) { // Need to kill some threads
				int killCount = total - newTC;
				for (; theAvailableThreads.size() > 0 && killCount > 0; killCount--)
					theAvailableThreads.remove(theAvailableThreads.size() - 1).kill(false);
				for (int i = theInUseThreads.size() - 1; i >= 0 && killCount > 0; i--, killCount--)
					theInUseThreads.get(i).kill(true);
			} else if (newTC > total) {
				int spawnCount = newTC - total;
				for (int t = 0; t < spawnCount; t++)
					theAvailableThreads.add(0, new ReusableThread());
			}
		} finally {
			theLock.unlock();
		}
	}

	private int getNewThreadCount() {
		int used = getInUseThreadCount();
		int total = getThreadCount();
		int ret;
		if (used == total) {
			for (ret = 1; ret * ret <= total; ret++)
				;
			ret = ret * ret;
		} else {
			int ceilUsedSqrt;
			for (ceilUsedSqrt = 1; ceilUsedSqrt * ceilUsedSqrt < used; ceilUsedSqrt++)
				;
			int floorTotalSqrt;
			for (floorTotalSqrt = 1; floorTotalSqrt * floorTotalSqrt <= total; floorTotalSqrt++)
				;
			floorTotalSqrt--;
			if (ceilUsedSqrt < floorTotalSqrt - 1)
				ret = (ceilUsedSqrt + 1) * (ceilUsedSqrt + 1);
			else
				ret = total;
		}
		if (ret > theMaxThreadCount)
			ret = theMaxThreadCount;
		return ret;
	}

	/**
	 * @return The maximum number of threads this ThreadPool will spawn before rejecting thread requests. The default maximum thread count
	 *         is 100.
	 */
	@Override
	public int getMaxThreadCount() {
		return theMaxThreadCount;
	}

	/**
	 * Sets the maximum thread count of this pool. If the given value is positive, the value will be used literally. If 0 is given, the max
	 * thread count will be set to the current number of available processors available. If a negative value is given, the max thread count
	 * will be set to the current available processor count minus the absolute value of the argument. If the processor-adjusted thread count
	 * is <=0, then a max thread count of 1 will be used.
	 * 
	 * @param maxTC The maximum number of threads this ThreadPool should spawn before rejecting thread requests. If <code>maxTC</code> is
	 *        smaller than the number of threads currently in use, no thread requests will be granted until that number is one less than
	 *        <code>maxTC</code>.
	 */
	public void setMaxThreadCount(int maxTC) {
		if (maxTC <= 0)
			maxTC += Runtime.getRuntime().availableProcessors();
		if (maxTC <= 0)
			maxTC = 1;
		theLock.lock();
		try {
			theMaxThreadCount = maxTC;
			if (getThreadCount() > theMaxThreadCount)
				adjustThreadCount();
		} finally {
			theLock.unlock();
		}
	}
}

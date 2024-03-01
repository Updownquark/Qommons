package org.qommons.threading;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * <p>
 * A small framework to execute tasks on multiple threads, with lots of options for thread maintenance.
 * </p>
 * 
 * <p>
 * By default, this class keeps an unlimited queue of tasks to execute and employs a number of worker threads up to the number of available
 * processors on the system minus one to execute the tasks.
 * </p>
 * <p>
 * No threads are spawned on initialization. New worker threads are spawned as tasks are queued, up to the maximum number. When the queue is
 * empty, worker threads will kill themselves after a tenth of a second.
 * </p>
 * 
 * <p>
 * All these numbers are configurable. The executor can also be configured to drop tasks if the queue gets too large.
 * </p>
 * 
 * <h2>Justification</h2>
 * <p>
 * Although Java has many execution utilities that appear to do the same job as this class, this class has 2 advantages over them:
 * <ol>
 * <li>The {@link #waitWhileActive(long)} method provides the ability to be notified when the executor has finished its tasks without being
 * shut down.</li>
 * <li>Performance. This class is much faster than Java's executor. Around 3x faster, according to my test (see ElasticExecutorTest in the
 * testing sources).</li>
 * </ol>
 * This class does not provide visibility into the execution status of each executed task. This could be added, but it can easily be done by
 * the tasks themselves.
 * </p>
 * 
 * <p>
 * Note that this executor has no shutdown method. If the worker's {@link #getMinThreadCount()} is zero, this is typically not important
 * since worker threads will kill themselves after the configured lifetime. If the min thread count is not zero or you want all workers
 * killed immediately, set both the min thread count and the used thread lifetime to zero. Those calls may return before all workers are
 * killed, but they will be killed quickly. The {@link #getThreadCount()} method gives visibility into the total number of worker threads
 * alive.
 * </p>
 * 
 * @param <T> The type of task to execute
 */
public class ElasticExecutor<T> {
	/** The maximum possible {@link #getMaxQueueSize() maximum queue size} allowed for this class */
	public static final int MAX_POSSIBLE_QUEUE_SIZE = 1_000_000_000;

	/**
	 * Executes tasks on a single thread for an {@link ElasticExecutor}. The {@link AutoCloseable#close()} method will be called when this
	 * class is no longer needed.
	 * 
	 * @param <T> The type of task to execute
	 */
	public interface TaskExecutor<T> extends AutoCloseable {
		/**
		 * Executes a task
		 * 
		 * @param task The task to execute
		 */
		void execute(T task);

		@Override
		default void close() throws Exception {}
	}

	/** An interface to facilitate custom handling of threads for an {@link ElasticExecutor} */
	public interface Runner {
		/**
		 * @param task The runnable to execute in a separate thread
		 * @param name The (suggested) name of the thread to execute it in
		 */
		void execute(Runnable task, String name);
	}

	private String theName;
	private final Supplier<? extends TaskExecutor<? super T>> theGuts;
	private volatile int theMinThreadCount;
	private volatile int theMaxThreadCount;
	private volatile int theMaxQueueSize;
	private int theUnusedThreadLifetime;

	private final ConcurrentLinkedQueue<T> theQueue;
	private final AtomicInteger theQueueSize;
	private volatile Runner theRunner;
	private final AtomicInteger theThreadCount;
	private final AtomicInteger theActiveWorkers;
	// This next field is duplicate information that could be derived from the above 2, but that wouldn't be atomic
	private final AtomicInteger theWaitingWorkers;
	private volatile ConcurrentLinkedQueue<TaskExecutor<? super T>> theCachedWorkers;
	private final AtomicReference<byte[]> theNextWorkerId;

	private final Object theLock;

	/**
	 * Creates an executor
	 * 
	 * @param name The name of the executor (typically used to name the threads it spawns)
	 * @param taskExecutor Supplies task executors, each of which will be used to execute a single task at a time
	 */
	public ElasticExecutor(String name, Supplier<? extends TaskExecutor<? super T>> taskExecutor) {
		theName = name;
		theGuts = taskExecutor;
		theMinThreadCount = 0;
		theMaxThreadCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
		theMaxQueueSize = Integer.MAX_VALUE;
		theUnusedThreadLifetime = 100;

		theQueue = new ConcurrentLinkedQueue<>();
		theQueueSize = new AtomicInteger();
		theRunner = new DefaultRunner();
		theThreadCount = new AtomicInteger();
		theActiveWorkers = new AtomicInteger();
		theWaitingWorkers = new AtomicInteger();

		theNextWorkerId = new AtomicReference<>(new byte[] { 0 });

		theLock = new Object();
	}

	// Configuration methods

	/**
	 * @param runner The runner to use to execute task threads in this executor. This method may be used to use this class with a thread
	 *        pool or to customize the threads that are spawned by this class, for example.
	 * @return This executor
	 */
	public ElasticExecutor<T> setRunner(Runner runner) {
		if (runner == null)
			throw new NullPointerException("Runner cannot be null");
		theRunner = runner;
		return this;
	}

	/** @return The number of threads that will be maintained by this executor even when there are no tasks being executed */
	public int getMinThreadCount() {
		return theMinThreadCount;
	}

	/** @return The maximum number of threads that this class will utilize at once */
	public int getMaxThreadCount() {
		return theMaxThreadCount;
	}

	/**
	 * Sets the minimum and maximum thread counts simultaneously.
	 * 
	 * @param minThreadCount The number of threads to maintain even when no tasks are being executed. Setting this value will not cause
	 *        threads to be created--it will only keep them from being released after they are spawned.
	 * @param maxThreadCount The maximum number of threads that this class will utilize at once. Setting this value will cause threads in
	 *        excess of this amount to be released after their current task is finished.
	 * @return This executor
	 */
	public ElasticExecutor<T> setThreadRange(int minThreadCount, int maxThreadCount) {
		if (minThreadCount < 0)
			throw new IllegalArgumentException("Minimum thread count cannot be less than zero: " + minThreadCount);
		else if (minThreadCount > maxThreadCount)
			throw new IllegalArgumentException(
				"Minimum thread count cannot be greater than maximum thread count: " + minThreadCount + "..." + maxThreadCount);
		if (minThreadCount < theMinThreadCount) {
			theMinThreadCount = minThreadCount;
			theMaxThreadCount = maxThreadCount;
			// Notify worker threads that may be waiting a long time under the assumption that they should never die
			synchronized (theLock) {
				theLock.notifyAll();
			}
		} else {
			theMaxThreadCount = maxThreadCount;
			theMinThreadCount = minThreadCount;
		}
		return this;
	}

	/**
	 * @param minThreadCount The number of threads to maintain even when no tasks are being executed. Setting this value will not cause
	 *        threads to be created--it will only keep them from being released after they are spawned.
	 * @return This executor
	 */
	public ElasticExecutor<T> setMinThreadCount(int minThreadCount) {
		if (minThreadCount < 0)
			throw new IllegalArgumentException("Minimum thread count cannot be less than zero: " + minThreadCount);
		else if (minThreadCount > theMaxThreadCount)
			throw new IllegalArgumentException(
				"Minimum thread count cannot be greater than maximum thread count: " + minThreadCount + "..." + theMaxThreadCount);
		theMinThreadCount = minThreadCount;
		return this;
	}

	/**
	 * @param maxThreadCount The maximum number of threads that this class will utilize at once. Setting this value will cause threads in
	 *        excess of this amount to be released after their current task is finished.
	 * @return This executor
	 */
	public ElasticExecutor<T> setMaxThreadCount(int maxThreadCount) {
		if (maxThreadCount <= 0)
			throw new IllegalArgumentException("Maximum thread count must be at least 1");
		else if (theMinThreadCount > maxThreadCount)
			throw new IllegalArgumentException(
				"Maximum thread count cannot be less than minimum thread count: " + theMinThreadCount + "..." + maxThreadCount);
		theMaxThreadCount = maxThreadCount;
		return this;
	}

	/**
	 * @return The maximum {@link #getQueueSize() queue size} allowed for this executor before tasks are {@link #execute(Object) rejected}.
	 */
	public int getMaxQueueSize() {
		return theMaxQueueSize;
	}

	/**
	 * @param maxQueueSize The maximum {@link #getQueueSize() queue size} allowed for this executor before tasks are {@link #execute(Object)
	 *        rejected}.
	 * @return This executor
	 */
	public ElasticExecutor<T> setMaxQueueSize(int maxQueueSize) {
		if (maxQueueSize < 0)
			throw new IllegalArgumentException("Maximum queue size cannot be less than zero: " + maxQueueSize);
		else if (maxQueueSize > MAX_POSSIBLE_QUEUE_SIZE)
			throw new IllegalArgumentException("Maximum queue size cannot exceed " + MAX_POSSIBLE_QUEUE_SIZE + ": " + maxQueueSize);
		theMaxQueueSize = maxQueueSize;
		return this;
	}

	/**
	 * @param lifetime The lifetime of threads beyond the {@link #getMinThreadCount() minimum thread count} that have nothing to do
	 * @return This executor
	 */
	public ElasticExecutor<T> setUsedThreadLifetime(int lifetime) {
		if (lifetime < 0)
			throw new IllegalArgumentException("Used thread lifetime must not be negative");
		boolean lowerLifetime = lifetime < theUnusedThreadLifetime;
		theUnusedThreadLifetime = lifetime;
		if (lowerLifetime) {
			// Notify worker threads that may be waiting a longer than they should be now
			synchronized (theLock) {
				theLock.notifyAll();
			}
		}
		return this;
	}

	/**
	 * @param cacheWorkers Whether this executor should, when threads are released due to being no longer needed, cache workers created by
	 *        its task executor for re-use when more threads are needed later. If false and this executor currently has workers cached, they
	 *        will be {@link TaskExecutor#close() closed}.
	 * @return This executor
	 */
	public synchronized ElasticExecutor<T> cacheWorkers(boolean cacheWorkers) {
		if ((theCachedWorkers != null) != cacheWorkers) {
			if (cacheWorkers)
				theCachedWorkers = new ConcurrentLinkedQueue<>();
			else {
				TaskExecutor<? super T> worker = theCachedWorkers.poll();
				while (worker != null) {
					try {
						worker.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
					worker = theCachedWorkers.poll();
				}
				theCachedWorkers = null;
			}
		}
		return this;
	}

	// Status methods

	/** @return The current size of the queue of tasks waiting to begin execution */
	public int getQueueSize() {
		return theQueue.size();
	}

	/** @return The current number of threads being used to execute tasks */
	public int getThreadCount() {
		return theThreadCount.get();
	}

	/** @return The number of threads actively working on tasks for this executor */
	public int getActiveThreads() {
		return theActiveWorkers.get();
	}

	/**
	 * @return True if this executor has been given any tasks to {@link #execute(Object) execute} that have not finished. False if all tasks
	 *         have finished.
	 */
	public boolean isActive() {
		return theActiveWorkers.get() != 0 || theQueueSize.get() != 0;
	}

	// Action methods

	/**
	 * Executes a task
	 * 
	 * @param task The task to execute
	 * @return Whether the task was successfully queued or was rejected (due to {@link #getMaxQueueSize() max queue size})
	 */
	public boolean execute(T task) {
		int maxSize = theMaxQueueSize;
		int preQueueSize = theQueueSize.getAndUpdate(size -> incrementQueueSize(size, maxSize));
		if (preQueueSize == maxSize)
			return false;
		theQueue.add(task);
		if (theWaitingWorkers.get() != 0) {
			synchronized (theLock) {
				theLock.notify();
			}
		} else {
			int maxTC = theMaxThreadCount;
			int preTC = theThreadCount.getAndUpdate(tc -> {
				if (tc >= maxTC)
					return tc;
				return tc + 1;
			});
			if (preTC < maxTC) {
				if (!startThread()) {
					theThreadCount.getAndDecrement();
					if (preTC == 0)
						throw new IllegalStateException("Could not start first worker thread--task executor returned null");
				}
			}
		}
		return true;
	}

	private static int incrementQueueSize(int currentSize, int maxSize) {
		if (currentSize == maxSize)
			return currentSize;
		return currentSize + 1;
	}

	/**
	 * Causes this thread to block until this executor has finished all its tasks, or until the given timeout expires
	 * 
	 * @param timeout The maximum amount of time to wait for the queue to empty, or &lt;=0 to wait forever
	 * @return True if the method exits because the queue is empty; false if it exits due to the timeout parameter
	 */
	public boolean waitWhileActive(long timeout) {
		if (!isActive())
			return true;
		synchronized (this) {
			if (!isActive())
				return true;
			long endTime = timeout <= 0 ? 0 : System.currentTimeMillis() + timeout;
			while (true) {
				long sleepTime;
				if (timeout > 0) {
					long now = System.currentTimeMillis();
					if (now >= endTime)
						return false;
					else
						sleepTime = Math.min(endTime - now, 10);
				} else
					sleepTime = 0;
				try {
					wait(sleepTime);
				} catch (InterruptedException e) {
					// Just wake up normally
				}
				if (!isActive())
					return true;
			}
		}
	}

	/**
	 * <p>
	 * Clears all <i>waiting</i> tasks in the execution queue, calling the parameter's {@link Consumer#accept(Object) accept} method for
	 * each item in the queue that will no longer be {@link TaskExecutor#execute(Object) executed} as a result of this call. This action
	 * runs on the thread it is called from. If a task is {@link #execute(Object) scheduled} to be executed (either by the
	 * <code>onEachCleared</code> action or from another thread) while this call is running, such tasks may or may not be cleared by this
	 * call.
	 * </p>
	 * <p>
	 * This method does not attempt to stop the execution of tasks that are currently being executed or are just about to be executed. Such
	 * functionality must be implemented by the {@link TaskExecutor#execute(Object) execute} method of the implementation.
	 * </p>
	 * 
	 * @param onEachCleared An action to perform on each cleared task. May be null.
	 * @return The number of tasks that were {@link #execute(Object) scheduled} to be {@link TaskExecutor#execute(Object) executed} in this
	 *         executor that will not be executed as a result of this call
	 */
	public int clear(Consumer<? super T> onEachCleared) {
		int cleared = 0;
		T task = theQueue.poll();
		while (task != null) {
			cleared++;
			theQueueSize.decrementAndGet();
			if (onEachCleared != null)
				onEachCleared.accept(task);
			task = theQueue.poll();
		}
		return cleared;
	}

	private boolean startThread() {
		TaskExecutor<? super T> taskExecutor = null;
		ConcurrentLinkedQueue<TaskExecutor<? super T>> cache = theCachedWorkers;
		if (cache != null)
			taskExecutor = cache.poll();
		if (taskExecutor == null)
			taskExecutor = theGuts.get();
		if (taskExecutor == null) {
			theThreadCount.getAndDecrement();
			return false;
		} else {
			new ElasticExecutorWorker(getNextWorkerId(), taskExecutor).start();
			return true;
		}
	}
	private static final char[] WORKER_ID_CHAR_SEQ;
	static {
		String workerCharSeq = "1234567890";
		WORKER_ID_CHAR_SEQ = workerCharSeq.toCharArray();
	}

	private String getNextWorkerId() {
		byte[] workerId = theNextWorkerId.getAndUpdate(ElasticExecutor::incrementWorkerId);
		char[] workerIdChars = new char[workerId.length];
		for (int i = 0; i < workerId.length; i++)
			workerIdChars[i] = WORKER_ID_CHAR_SEQ[workerId[i]];
		return new String(workerIdChars);
	}

	static byte[] incrementWorkerId(byte[] previous) {
		int idx = previous.length - 1;
		int dig = previous[idx] + 1;
		while (dig == WORKER_ID_CHAR_SEQ.length) {
			idx--;
			if (idx < 0)
				break;
			dig = previous[idx];
		}
		byte[] newId;
		if (idx < 0)
			newId = new byte[previous.length + 1];
		else {
			newId = new byte[previous.length];
			if (idx > 0)
				System.arraycopy(previous, 0, newId, 0, idx);
			newId[idx] = (byte) dig;
			if (idx < previous.length - 1)
				Arrays.fill(newId, idx + 1, newId.length, (byte) 0);
		}
		return newId;
	}

	synchronized void notifyWaitingThreads() {
		notifyAll();
	}

	private class ElasticExecutorWorker implements Runnable {
		private final String theWorkerId;
		private final TaskExecutor<? super T> theTaskExecutor;

		ElasticExecutorWorker(String id, TaskExecutor<? super T> taskExecutor) {
			theWorkerId = id;
			theTaskExecutor = taskExecutor;
		}

		void start() {
			theRunner.execute(this, theName + " " + theWorkerId);
		}

		@Override
		public void run() {
			long now = System.currentTimeMillis();
			long lastUsed = now;
			T task = theQueue.poll();
			boolean die = false;
			boolean active = true; // Assume we'll be active since we've just been spawned
			theActiveWorkers.getAndIncrement();
			while (!die) {
				if (task != null) {
					if (!active) {
						active = true;
						theActiveWorkers.getAndIncrement();
						theWaitingWorkers.getAndDecrement();
					}
					do {
						theQueueSize.decrementAndGet();
						try {
							theTaskExecutor.execute(task);
						} catch (RuntimeException | Error e) {
							e.printStackTrace();
						}
						now = System.currentTimeMillis();
						lastUsed = now;
						task = theQueue.poll();
					} while (task != null);
				} else {
					if (active) {
						active = false;
						if (0 == theActiveWorkers.decrementAndGet()) {
							notifyWaitingThreads();
						}
						theWaitingWorkers.getAndIncrement();
					}
					synchronized (theLock) {
						try {
							theLock.wait(theUnusedThreadLifetime);
						} catch (InterruptedException e) {}
					}
					now = System.currentTimeMillis();
					task = theQueue.poll();
					if (task == null && (now - lastUsed) >= theUnusedThreadLifetime) {
						// No tasks available, see if we should die now
						int minTC = theMinThreadCount;
						int preTC = theThreadCount.getAndUpdate(tc -> {
							if (tc <= minTC)
								return tc;
							return tc - 1;
						});
						if (preTC > minTC)
							die = true;
					}
				}
			}
			theWaitingWorkers.getAndDecrement();
			ConcurrentLinkedQueue<TaskExecutor<? super T>> cache = theCachedWorkers;
			if (cache != null)
				cache.add(theTaskExecutor);
			else {
				try {
					theTaskExecutor.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	private static class DefaultRunner implements Runner {
		@Override
		public void execute(Runnable task, String name) {
			Thread thread = new Thread(task, name);
			thread.setDaemon(true);
			thread.start();
		}
	}
}

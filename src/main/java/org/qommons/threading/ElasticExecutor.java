package org.qommons.threading;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

import org.qommons.Named;
import org.qommons.collect.BetterBitSet;

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
 * <li>The {@link #waitWhileActive(int, long)} method provides the ability to be notified when the executor has finished its tasks without
 * being shut down.</li>
 * <li>Performance. This class is much faster than Java's executor, especially with lots of quick tasks. Up to 10x faster, measured by the
 * amount of time between beginning to queue the tasks and all tasks finishing, according to my test (see ElasticExecutorTest in the testing
 * sources). The time it takes to enqueue tasks (the {@link #execute(Object) execute} method) is much slower, though.</li>
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
public class ElasticExecutor<T> implements Named {
	/** The maximum possible {@link #getMaxQueueSize() maximum queue size} allowed for this class */
	public static final int MAX_POSSIBLE_QUEUE_SIZE = 1_000_000_000;

	/**
	 * The default {@link #isTrackingProcessorCount() processor tracking} operation for executors--keeps the executor's
	 * {@link #getMaxThreadCount() maximum thread count} at the number of available processors minus one
	 */
	public static final IntUnaryOperator DEFAULT_PROCESSOR_TRACKING = p -> p - 1;

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
		default void close() throws Exception {
		}
	}

	/** An interface to facilitate custom handling of threads for an {@link ElasticExecutor} */
	public interface Runner {
		/**
		 * @param task The runnable to execute in a separate thread
		 * @param name The (suggested) name of the thread to execute it in
		 */
		void execute(Runnable task, String name);
	}

	private final String theName;
	private final Supplier<? extends TaskExecutor<? super T>> theGuts;
	private volatile int theMinWorkerCount;
	private volatile int theMaxWorkerCount;
	private volatile IntUnaryOperator isTrackingProcessorCount;
	private volatile int theMaxQueueSize;
	private volatile int theUnusedWorkerLifetime;

	private final ConcurrentLinkedQueue<T> theTaskQueue;
	private volatile Runner theRunner;
	private final AtomicInteger theUnfinishedTaskCount;
	private final BetterBitSet theActiveWorkers;
	private volatile int theActiveWorkerCount;
	private final BetterBitSet theWaitingWorkers;
	private volatile ConcurrentLinkedQueue<TaskExecutor<? super T>> theCachedWorkers;

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
		theMinWorkerCount = 0;
		isTrackingProcessorCount = DEFAULT_PROCESSOR_TRACKING;
		int processors = Runtime.getRuntime().availableProcessors();
		theMaxWorkerCount = Math.max(1, isTrackingProcessorCount.applyAsInt(processors));
		theMaxQueueSize = 0;
		theUnusedWorkerLifetime = 100;

		theTaskQueue = new ConcurrentLinkedQueue<>();
		theRunner = new DefaultRunner(new ThreadGroup(name));
		theUnfinishedTaskCount = new AtomicInteger();

		theActiveWorkers = new BetterBitSet();
		theWaitingWorkers = new BetterBitSet();

		theLock = new Object();

		QommonsTimer.startProcessorTracking(this);
	}

	// Configuration methods

	@Override
	public String getName() {
		return theName;
	}

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
		return theMinWorkerCount;
	}

	/** @return The maximum number of threads that this class will utilize at once */
	public int getMaxThreadCount() {
		return theMaxWorkerCount;
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
		isTrackingProcessorCount = null;
		if (minThreadCount < theMinWorkerCount) {
			theMinWorkerCount = minThreadCount;
			theMaxWorkerCount = maxThreadCount;
			// Notify worker threads that may be waiting a long time under the assumption that they should never die
			synchronized (theLock) {
				theLock.notifyAll();
			}
		} else {
			theMaxWorkerCount = maxThreadCount;
			theMinWorkerCount = minThreadCount;
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
		else if (minThreadCount > theMaxWorkerCount)
			throw new IllegalArgumentException(
				"Minimum thread count cannot be greater than maximum thread count: " + minThreadCount + "..." + theMaxWorkerCount);
		theMinWorkerCount = minThreadCount;
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
		else if (theMinWorkerCount > maxThreadCount)
			throw new IllegalArgumentException(
				"Maximum thread count cannot be less than minimum thread count: " + theMinWorkerCount + "..." + maxThreadCount);
		isTrackingProcessorCount = null;
		theMaxWorkerCount = maxThreadCount;
		return this;
	}

	/**
	 * <p>
	 * A function to determine this executor's {@link #getMaxThreadCount() maximum thread count} using this Java {@link Runtime runtime}'s
	 * {@link Runtime#availableProcessors() available processor} count.
	 * </p>
	 * <p>
	 * While this operator is non-null, a task will execute periodically to poll this value and update the max thread count if/when it
	 * changes.
	 * </p>
	 * <p>
	 * Note that if an executor is created near the very beginning of a Java process's lifetime, the available processor count may not have
	 * yet polled the hardware for its available CPU count, in which case the runtime method will return 1. Leaving or setting this value
	 * non-null will allow this executor to run multiple threads when the processor count is updated.
	 * </p>
	 * <p>
	 * If this operator ever returns a value less than the maximum of one and this executor's {@link #getMinThreadCount()}, that value will
	 * be silently substituted.
	 * </p>
	 * <p>
	 * The default value ({@link #DEFAULT_PROCESSOR_TRACKING}) is the available processor count minus one.
	 * </p>
	 * 
	 * @return Whether this processor's max thread count tracks the available processor count
	 */
	public IntUnaryOperator isTrackingProcessorCount() {
		return isTrackingProcessorCount;
	}

	/**
	 * @param tracking A function to produce a {@link #getMaxThreadCount() maximum thread count} for this executor by this {@link Runtime
	 *        runtime}'s {@link Runtime#availableProcessors() available processor} count, or null if this executor's max thread count should
	 *        not track the processor count.
	 * @return This executor
	 * @see #isTrackingProcessorCount()
	 */
	public ElasticExecutor<T> setTrackingProcessorCount(IntUnaryOperator tracking) {
		isTrackingProcessorCount = tracking;
		trackProcessorCount();
		return this;
	}

	/**
	 * Adjusts this executor's {@link #getMaxThreadCount() maximum thread count} using the {@link #isTrackingProcessorCount() processor
	 * tracking} operator and this Java {@link Runtime runtime}'s {@link Runtime#availableProcessors() available processor} count.
	 * 
	 * @return Whether this executor's max thread count changed as a result
	 */
	public boolean trackProcessorCount() {
		IntUnaryOperator tracking = isTrackingProcessorCount;
		if (tracking == null)
			return false;
		int minCount = theMinWorkerCount;
		int maxCount = theMaxWorkerCount;
		int processors = Runtime.getRuntime().availableProcessors();
		int newMaxWorkers = tracking.applyAsInt(processors);
		if (newMaxWorkers < minCount)
			newMaxWorkers = minCount;
		else if (newMaxWorkers < 1)
			newMaxWorkers = 1;
		if (maxCount == newMaxWorkers)
			return false;
		theMaxWorkerCount = newMaxWorkers;
		return true;
	}

	/**
	 * @return The maximum {@link #getQueueSize() queue size} allowed for this executor before tasks are {@link #execute(Object) rejected}.
	 */
	public int getMaxQueueSize() {
		return theMaxQueueSize;
	}

	/**
	 * @param maxQueueSize The maximum {@link #getQueueSize() queue size} allowed for this executor before tasks are {@link #execute(Object)
	 *        rejected}. A value of zero means no limit
	 * @return This executor
	 */
	public ElasticExecutor<T> setMaxQueueSize(int maxQueueSize) {
		if (maxQueueSize > 0 && maxQueueSize < 10)
			throw new IllegalArgumentException("Maximum queue size must be at least 10: " + maxQueueSize);
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
		boolean lowerLifetime = lifetime < theUnusedWorkerLifetime;
		theUnusedWorkerLifetime = lifetime;
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
		return theTaskQueue.size();
	}

	/** @return The current number of threads being used to execute tasks */
	public int getThreadCount() {
		return theActiveWorkerCount;
	}

	/** @return The number of threads actively working on tasks for this executor */
	public int getActiveThreads() {
		return Math.max(0, getThreadCount() - theWaitingWorkers.cardinality());
	}

	/**
	 * @return True if this executor has been given any tasks to {@link #execute(Object) execute} that have not finished. False if all tasks
	 *         have finished.
	 */
	public boolean isActive() {
		return theUnfinishedTaskCount.get() != 0;
	}

	/**
	 * @return The number of tasks that have been successfully scheduled for {@link #execute(Object) execution} but which have not finished
	 *         executing and have not been {@link #clear(Consumer) cleared}
	 */
	public int getUnfinishedTasks() {
		return theUnfinishedTaskCount.get();
	}

	// Action methods

	/**
	 * Executes a task
	 * 
	 * @param task The task to execute
	 * @return Whether the task was successfully queued or was rejected (due to {@link #getMaxQueueSize() max queue size})
	 */
	public boolean execute(T task) {
		if (task == null)
			throw new NullPointerException("Null tasks are not allowed");
		int maxSize = theMaxQueueSize;
		if (maxSize == 0) {
			theUnfinishedTaskCount.incrementAndGet();
		} else {
			int preQueueSize = theUnfinishedTaskCount.getAndUpdate(count -> incrementQueueSize(count, maxSize));
			if (preQueueSize >= maxSize)
				return false;
		}
		theTaskQueue.add(task);
		int startWorker = -1;
		int maxTC = theMaxWorkerCount;
		while (!theWaitingWorkers.isEmpty() || theActiveWorkerCount < maxTC) {
			synchronized (theLock) {
				if (!theWaitingWorkers.isEmpty()) {
					theLock.notifyAll();
					break;
				} else {
					startWorker = theActiveWorkers.nextClearBit(0);
					if (startWorker < maxTC) {
						theActiveWorkerCount++;
						theActiveWorkers.set(startWorker);
						break;
					} else
						startWorker = -1;
				}
			}
		}
		if (startWorker >= 0)
			startWorker(startWorker);
		return true;
	}

	/**
	 * Causes this thread to block until this executor has finished all but at most <code>maxUnfinished</code> of its tasks, or until the
	 * given timeout expires
	 * 
	 * @param maxUnfinished The maximum number of unfinished tasks that may remain before this method exits with a true result. This
	 *        parameter is useful, for example, when called from an executor itself, when it wants to know if it's the only remaining
	 *        executor left.
	 * @param timeout The maximum amount of time to wait for the queue to empty, or &lt;=0 to wait forever
	 * @return True if the method exits because the queue is empty; false if it exits due to the timeout parameter
	 */
	public boolean waitWhileActive(int maxUnfinished, long timeout) {
		if (theUnfinishedTaskCount.get() <= maxUnfinished)
			return true;
		long endTime = timeout <= 0 ? 0 : System.currentTimeMillis() + timeout;
		if (maxUnfinished >= getActiveThreads() && !theTaskQueue.isEmpty()) {
			// If X tasks are waiting for all but themselves to be completed,
			// but there are max <=X workers total, this would otherwise result in deadlock
			try (TaskExecutor<? super T> executor = theGuts.get()) {
				while (theUnfinishedTaskCount.get() > maxUnfinished) {
					T task = pollTask();
					if (task != null) {
						try {
							executor.execute(task);
						} catch (Throwable e) {
							System.err.println("Error executing " + task);
							e.printStackTrace();
						}
						taskFinished();
					}
					if (timeout > 0 && System.currentTimeMillis() >= endTime)
						break;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		synchronized (this) {
			while (theUnfinishedTaskCount.get() > maxUnfinished) {
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
			}
			return true;
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
		T task = theTaskQueue.poll();
		while (task != null) {
			cleared++;
			if (onEachCleared != null) {
				try {
					onEachCleared.accept(task);
				} finally {
					taskFinished();
				}
			} else
				taskFinished();

			task = theTaskQueue.poll();
		}
		return cleared;
	}

	// Internal methods. Some of these are package-private to avoid the overhead of synthetic methods.

	private void startWorker(int id) {
		TaskExecutor<? super T> taskExecutor = null;
		ConcurrentLinkedQueue<TaskExecutor<? super T>> cache = theCachedWorkers;
		if (cache != null)
			taskExecutor = cache.poll();
		theRunner.execute(new Worker(id, taskExecutor), theName + ":" + (id + 1));
	}

	private static int incrementQueueSize(int currentSize, int maxSize) {
		if (currentSize >= maxSize)
			return currentSize;
		return currentSize + 1;
	}

	void taskFinished() {
		if (0 == theUnfinishedTaskCount.decrementAndGet()) {
			synchronized (this) {
				notifyAll();
			}
		}
	}

	T pollTask() {
		return theTaskQueue.poll();
	}

	T waitForTask(Worker worker) {
		T task;
		long waitStart = System.currentTimeMillis();
		long now = waitStart;
		do {
			int lifetime = theUnusedWorkerLifetime;
			long waitUntil = waitStart + lifetime;
			synchronized (theLock) {
				if (worker.id >= theMaxWorkerCount) {
					kill(worker);
					return null;
				}
				task = theTaskQueue.poll();
				boolean expired = false;
				if (task == null && lifetime > 0) {
					if (worker.id >= theMaxWorkerCount) {
						kill(worker);
						return null;
					}
					theWaitingWorkers.set(worker.id);
					try {
						while (!expired) {
							try {
								theLock.wait(waitUntil - now);
							} catch (InterruptedException e) {
							}
							// Only wake up if we're the highest-priority worker or if we're expired
							if (theWaitingWorkers.previousSetBit(worker.id - 1) < 0) {
								break;
							} else if (worker.id >= theMinWorkerCount) {
								now = System.currentTimeMillis();
								lifetime = theUnusedWorkerLifetime;
								waitUntil = waitStart + lifetime;
								expired = shouldDie(worker.id, now >= waitUntil);
							}
						}
					} finally {
						theWaitingWorkers.clear(worker.id);
					}
				}
				if (task == null)
					task = pollTask();
				if (task != null)
					break;
				else {
					if (!expired)
						now = System.currentTimeMillis();
					if (expired || now >= waitUntil) {
						// No tasks available, see if we should die now
						int minTC = theMinWorkerCount;
						if (worker.id >= minTC)
							kill(worker);
					}
				}
			}
		} while (!worker.isDead);

		return task;
	}

	private void kill(Worker worker) {
		worker.isDead = true;
		theActiveWorkers.clear(worker.id);
		theActiveWorkerCount--;
	}

	private boolean shouldDie(int workerId, boolean timeExpired) {
		if (workerId > theMaxWorkerCount)
			return true;
		else
			return timeExpired;
	}

	class Worker implements Runnable {
		final int id;
		private TaskExecutor<? super T> theTaskExecutor;
		boolean isDead;

		Worker(int id, TaskExecutor<? super T> taskExecutor) {
			this.id = id;
			theTaskExecutor = taskExecutor;
		}

		@Override
		public void run() {
			if (theTaskExecutor == null)
				theTaskExecutor = theGuts.get();
			T task = pollTask();
			do {
				while (task != null) {
					try {
						theTaskExecutor.execute(task);
					} catch (Throwable e) {
						e.printStackTrace();
					}
					taskFinished();
					task = pollTask();
				}

				task = waitForTask(this);
			} while (!isDead);

			if (task != null) // Should never happen, but good to know if it does
				System.err.println("Dying with unfinished task !!! " + task);

			// Re-cache or destroy the executor
			ConcurrentLinkedQueue<TaskExecutor<? super T>> cache = theCachedWorkers;
			if (cache != null)
				cache.add(theTaskExecutor);
			else {
				try {
					theTaskExecutor.close();
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(theName).append(':').append(id + 1);
			if (isDead)
				str.append("(x)");
			return str.toString();
		}
	}

	private static class DefaultRunner implements Runner {
		private final ThreadGroup theThreadGroup;

		public DefaultRunner(ThreadGroup threadGroup) {
			theThreadGroup = threadGroup;
		}

		@Override
		public void execute(Runnable task, String name) {
			Thread thread = new Thread(theThreadGroup, task, name);
			thread.setDaemon(true);
			thread.start();
		}
	}
}

package org.qommons;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * A small framework to execute tasks on multiple threads, with lots of options for thread maintenance
 * 
 * @param <T> The type of task to execute
 */
public class ElasticExecutor<T> {
	/** The maximum possible {@link #getMaxQueueSize() maximum queue size} allowed for this class */
	public static final int MAX_POSSIBLE_QUEUE_SIZE = 1_000_000_000;

	/**
	 * Executes tasks on a single thread for an {@link ElasticExecutor}
	 * 
	 * @param <T> The type of task to execute
	 */
	public interface TaskExecutor<T> {
		/**
		 * Executes a task
		 * 
		 * @param task The task to execute
		 * @return The result of the task (currently unused)
		 */
		int execute(T task);
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
	private volatile int thePreferredQueueSize;
	private int theUnusedThreadLifetime;

	private final ConcurrentLinkedQueue<T> theQueue;
	private final AtomicInteger theQueueSize;
	private volatile Runner theRunner;
	private final AtomicInteger theThreadCount;
	private volatile ConcurrentLinkedQueue<TaskExecutor<? super T>> theCachedWorkers;

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
		theMaxQueueSize = 1000;
		thePreferredQueueSize = 10;
		theUnusedThreadLifetime = 100;

		theQueue = new ConcurrentLinkedQueue<>();
		theQueueSize = new AtomicInteger();
		theRunner = new DefaultRunner();
		theThreadCount = new AtomicInteger();
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
		if (theMinThreadCount > maxThreadCount)
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
	 * @return The {@link #getQueueSize() queue size} above which new threads will be spawned to help (if permitted by
	 *         {@link #getMaxThreadCount()})
	 */
	public int getPreferredQueueSize() {
		return thePreferredQueueSize;
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
	 * @param prefQueueSize The {@link #getQueueSize() queue size} above which new threads will be spawned to help (if permitted by
	 *        {@link #getMaxThreadCount()})
	 * @return This executor
	 */
	public ElasticExecutor<T> setPreferredQueueSize(int prefQueueSize) {
		if (prefQueueSize < 2)
			throw new IllegalArgumentException("Preferred queue size cannot be less than two: " + prefQueueSize);
		else if (prefQueueSize > MAX_POSSIBLE_QUEUE_SIZE)
			throw new IllegalArgumentException("Preferred queue size cannot exceed " + MAX_POSSIBLE_QUEUE_SIZE + ": " + prefQueueSize);
		thePreferredQueueSize = prefQueueSize;
		return this;
	}

	/**
	 * @param lifetime The lifetime of threads beyond the {@link #getMinThreadCount() minimum thread count} that have nothing to do
	 * @return This executor
	 */
	public ElasticExecutor<T> setUsedThreadLifetime(int lifetime) {
		if (lifetime < 0)
			throw new IllegalArgumentException("Used thread lifetime must not be negative");
		theUnusedThreadLifetime = lifetime;
		return this;
	}

	/**
	 * @param cacheWorkers Whether this executor should, when threads are released due to being no longer needed, cache workers created by
	 *        its task executor for re-use when more threads are needed later
	 * @return This executor
	 */
	public synchronized ElasticExecutor<T> cacheWorkers(boolean cacheWorkers) {
		if ((theCachedWorkers != null) != cacheWorkers) {
			if (cacheWorkers)
				theCachedWorkers = new ConcurrentLinkedQueue<>();
			else
				theCachedWorkers = null;
		}
		return this;
	}

	/**
	 * Executes a task
	 * 
	 * @param task The task to execute
	 * @return Whether the task was successfully queued or was rejected (due to {@link #getMaxQueueSize() max queue size})
	 */
	public boolean execute(T task) {
		int newQueueSize = theQueueSize.incrementAndGet();
		if (newQueueSize >= theMaxQueueSize) {
			theQueueSize.decrementAndGet();
			return false;
		}
		theQueue.add(task);
		if (newQueueSize == 1) {
			if (theThreadCount.incrementAndGet() == 1) {// Start the first thread
				if (!startThread(1))
					throw new IllegalStateException("Could not start first worker thread--task executor returned null");
			} else
				theThreadCount.decrementAndGet();
		}
		return true;
	}

	/** @return The current size of the queue of tasks waiting to begin execution */
	public int getQueueSize() {
		return theQueueSize.get();
	}

	/** @return The current number of threads being used to execute tasks */
	public int getThreadCount() {
		return theThreadCount.get();
	}

	private boolean startThread(int threadNumber) {
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
			new ElasticExecutorWorker(threadNumber, taskExecutor).start();
			return true;
		}
	}

	private class ElasticExecutorWorker implements Runnable {
		private final int theId;
		private final TaskExecutor<? super T> theTaskExecutor;

		ElasticExecutorWorker(int id, TaskExecutor<? super T> taskExecutor) {
			theId = id;
			theTaskExecutor = taskExecutor;
		}

		void start() {
			theRunner.execute(this, theName);
		}

		@Override
		public void run() {
			long now = System.currentTimeMillis();
			long lastUsed = now;
			while (true) {
				T task = theQueue.poll();
				if (task != null) {
					while (task != null) {
						if (theQueueSize.decrementAndGet() >= thePreferredQueueSize) {
							// Think about allocating a new writer
							int newId = theThreadCount.incrementAndGet();
							if (newId <= theMaxThreadCount) {
								startThread(newId);
							} else {
								theThreadCount.decrementAndGet();
							}
						}
						@SuppressWarnings("unused")
						int result;
						try {
							result = theTaskExecutor.execute(task);
						} catch (RuntimeException | Error e) {
							e.printStackTrace();
							result = -1;
						}
						// TODO Perhaps accumulate the result for reporting somehow?
						task = theQueue.poll();
						now = System.currentTimeMillis();
						lastUsed = now;
					}
				} else {
					try {
						Thread.sleep(5);
					} catch (InterruptedException e) {}
					now = System.currentTimeMillis();
				}
				if (theId <= theMinThreadCount) {
					// We stay alive forever
				} else if (theId > theMaxThreadCount)
					break;
				else if ((now - lastUsed) >= theUnusedThreadLifetime) {
					ConcurrentLinkedQueue<TaskExecutor<? super T>> cache = theCachedWorkers;
					if (cache != null)
						cache.add(theTaskExecutor);
					break;
				}
			}
			theThreadCount.decrementAndGet();
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

package org.qommons.threading;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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
	private volatile int thePreferredQueueSize;
	private int theUnusedThreadLifetime;

	private final ConcurrentLinkedQueue<T> theQueue;
	private final AtomicInteger theQueueSize;
	private volatile Runner theRunner;
	private final AtomicInteger theThreadCount;
	private final AtomicInteger theActiveThreads;
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
		theMinThreadCount = 0;
		theMaxThreadCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
		theMaxQueueSize = 1000;
		thePreferredQueueSize = 10;
		theUnusedThreadLifetime = 100;

		theQueue = new ConcurrentLinkedQueue<>();
		theQueueSize = new AtomicInteger();
		theRunner = new DefaultRunner();
		theThreadCount = new AtomicInteger();
		theActiveThreads = new AtomicInteger();

		theLock = new Object();
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
		if (prefQueueSize > MAX_POSSIBLE_QUEUE_SIZE)
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

	/**
	 * Executes a task
	 * 
	 * @param task The task to execute
	 * @return Whether the task was successfully queued or was rejected (due to {@link #getMaxQueueSize() max queue size})
	 */
	public boolean execute(T task) {
		int newQueueSize = theQueueSize.incrementAndGet();
		if (newQueueSize > theMaxQueueSize) {
			theQueueSize.decrementAndGet();
			return false;
		}
		theQueue.add(task);
		synchronized (theLock) {
			theLock.notify();
		}
		newQueueSize = theQueueSize.get();
		if (newQueueSize == 1) {
			if (theThreadCount.incrementAndGet() == 1) {// Start the first thread
				if (!startThread(1))
					throw new IllegalStateException("Could not start first worker thread--task executor returned null");
			} else
				theThreadCount.decrementAndGet();
		} else if (newQueueSize > thePreferredQueueSize && getActiveThreads() == getThreadCount()) {
			int newId = theThreadCount.incrementAndGet();
			if (newId <= theMaxThreadCount)
				startThread(newId);
			else
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

	/** @return The number of threads actively working on tasks for this executor */
	public int getActiveThreads() {
		return theActiveThreads.get();
	}

	/**
	 * Causes this thread to block until this executor has finished all its tasks, or until the given timeout expires
	 * 
	 * @param timeout The maximum amount of time to wait for the queue to empty, or &lt;=0 to wait forever
	 * @return True if the method exits because the queue is empty; false if it exits due to the timeout parameter
	 */
	public boolean waitWhileActive(long timeout) {
		long endTime = timeout <= 0 ? 0 : System.currentTimeMillis() + timeout;
		while (theActiveThreads.get() > 0 || theQueueSize.get() > 0) {
			long sleepTime;
			if (timeout > 0) {
				long now = System.currentTimeMillis();
				if (now >= endTime)
					return false;
				else
					sleepTime = Math.min(endTime - now, 10);
			} else
				sleepTime = 10;
			try {
				Thread.sleep(sleepTime);
			} catch (InterruptedException e) {
				// Just wake up normally
			}
		}
		return true;
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
			T task = theQueue.poll();
			boolean die = false;
			while (!die) {
				if (task != null) {
					theActiveThreads.getAndIncrement();
					do {
						if (theQueueSize.decrementAndGet() > thePreferredQueueSize) {
							// Think about allocating a new writer
							int newId = theThreadCount.incrementAndGet();
							if (newId <= theMaxThreadCount) {
								startThread(newId);
							} else {
								theThreadCount.decrementAndGet();
							}
						}
						try {
							theTaskExecutor.execute(task);
						} catch (RuntimeException | Error e) {
							e.printStackTrace();
						}
						task = theQueue.poll();
						now = System.currentTimeMillis();
						lastUsed = now;
					} while (task != null);
					theActiveThreads.decrementAndGet();
				} else {
					synchronized (theLock) {
						try {
							theLock.wait(theUnusedThreadLifetime);
						} catch (InterruptedException e) {}
					}
					now = System.currentTimeMillis();
					task = theQueue.poll();
				}
				if (task != null)
					die = false;
				else if (theId <= theMinThreadCount)
					die = false;// We stay alive forever
				else if (theId > theMaxThreadCount)
					die = true;
				else if ((now - lastUsed) >= theUnusedThreadLifetime)
					die = true;
				else
					die = false;
				if (die) {
					if (theThreadCount.decrementAndGet() == 0 && theQueueSize.get() > 0) {
						// Make sure we don't orphan any just-queued tasks
						if (theThreadCount.incrementAndGet() > theMaxThreadCount)
							theThreadCount.decrementAndGet();
						else {
							die = false;
							now = System.currentTimeMillis();
						}
					}
				}
			}
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

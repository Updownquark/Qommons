/*
 * Worker.java Created Jun 13, 2008 by Andrew Butler, PSL
 */
package org.qommons;

/** Simply performs generic jobs in the background */
public interface Worker
{
	/**
	 * Runs a task in the background
	 * 
	 * @param r The task to perform
	 * @param listener The listener to notify in case the task throws a Throwable
	 */
	void run(Runnable r, ErrorListener listener);

	/** @return The maximum number of tasks that this worker will execute simulaneously */
	int getMaxThreadCount();

	/** Releases all of this worker's resources after all tasks have finished */
	void close();

	/**
	 * Releases all of this worker's resources immediately. This method will allow currently
	 * executing tasks to complete; but it will clear out all tasks that have been queued but are
	 * not yet being executed.
	 */
	void closeNow();

	/** An interface to listen for errors in a background task */
	interface ErrorListener
	{
		/**
		 * Called when an error occurs in the background task
		 * 
		 * @param error The error that occurred
		 */
		void error(Error error);

		/**
		 * Called when a RuntimeException occurs in the background task
		 * 
		 * @param ex The exception that occurred
		 */
		void runtime(RuntimeException ex);
	}
}

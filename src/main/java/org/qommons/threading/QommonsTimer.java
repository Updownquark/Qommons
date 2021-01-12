package org.qommons.threading;

import java.awt.EventQueue;
import java.time.Duration;
import java.time.Instant;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.ArgumentParsing;
import org.qommons.ArgumentParsing.Argument;
import org.qommons.ArgumentParsing.ArgumentParser;
import org.qommons.ArgumentParsing.Arguments;
import org.qommons.ArgumentParsing.ValuedArgument;
import org.qommons.QommonsUtils;
import org.qommons.TimeUtils;
import org.qommons.collect.ListenerList;

/** A timer class that allows very flexible scheduling of tasks without needing to create a thread per task */
public class QommonsTimer {
	private static final QommonsTimer COMMON_INSTANCE = new QommonsTimer(new SystemClock(), r -> {
		Thread t = new Thread(r, "Qommon Timer");
		t.start();
	}, new ElasticExecutor<>("Qommon Timer Offloader", () -> Runnable::run)//
		.setPreferredQueueSize(0)//
		.setUsedThreadLifetime(2000)//
		.setMaxQueueSize(1_000_000_000)// No offload rejection if we can help it
	::execute);

	/** @return A common timer that uses the system clock */
	public static QommonsTimer getCommonInstance() {
		return COMMON_INSTANCE;
	}

	/**
	 * A simple clock interface to drive a timer. Use of this interface allows timers to be created for simulated time, in-game time, or any
	 * other time scheme.
	 */
	public interface TimerClock {
		/** @return The current time */
		Instant now();

		/**
		 * @param sleepTime The amount of time to wait for
		 * @throws InterruptedException If the sleeping thread is {@link Thread#interrupt() interrupted}
		 */
		void sleep(Duration sleepTime) throws InterruptedException;
	}

	/** The default clock implementation based on system time */
	public static class SystemClock implements TimerClock {
		@Override
		public Instant now() {
			long millis = System.currentTimeMillis();
			int nanos = (int) (System.nanoTime() % 1_000_000);
			if (nanos < 0)
				nanos += 1_000_000;
			nanos += (millis % 1000) * 1_000_000L;
			return Instant.ofEpochSecond(millis / 1000, nanos);
		}

		@Override
		public void sleep(Duration sleepTime) throws InterruptedException {
			if (sleepTime.getSeconds() != 0 || sleepTime.getNano() >= 5_000_000)
				Thread.sleep(sleepTime.toMillis());
			else
				Thread.sleep(sleepTime.getNano() / 1_000_000, sleepTime.getNano() % 1_000_000);
		}
	}

	/** Different threading schemes that tasks can use to run */
	public enum TaskThreading {
		/**
		 * Specifies that a task should be run on the main timer thread. This scheme is only appropriate for very short tasks. Long-running
		 * tasks run on the timer thread risk delaying the execution other tasks.
		 */
		Timer,
		/**
		 * Specifies that a task should be run on the AWT/Swing Event Dispatch Thread (EDT)
		 */
		EDT,
		/**
		 * Specifies that a task may be run on any thread. This is the default. Tasks of this type will be offloaded to a separate thread
		 * pool to prevent any possibility of interfering with the scheduling or execution of other tasks.
		 */
		Any;
	}

	/** A handle on a scheduled task that allows for control of the task's execution */
	public class TaskHandle {
		private final Runnable theTask;
		private final AtomicBoolean isActive;
		private Runnable theRemove;

		private volatile Duration theFrequency;
		private volatile boolean isConsistent;

		private volatile Instant thePreviousRun;
		private volatile Instant theNextRun;

		private volatile long theRemainingExecCount;
		private volatile Instant theLastRun;
		private volatile boolean shouldRunAfterLast;

		private volatile boolean isExecuting;
		private volatile boolean isWaiting;
		private volatile long theExecCount;
		private volatile TaskThreading theThreading;
		private volatile boolean didOffloadFail;
		final Runnable offloadTask;

		TaskHandle(Runnable task, Duration frequency, boolean consistent) {
			theTask = task;
			isActive = new AtomicBoolean();
			theThreading = TaskThreading.Any;
			setFrequency(frequency, consistent);
			offloadTask = new Runnable() {
				@Override
				public void run() {
					execute();
				}

				@Override
				public String toString() {
					return TaskHandle.this.toString();
				}
			};
		}

		/** @return The timer that created this task */
		public QommonsTimer getTimer() {
			return QommonsTimer.this;
		}

		/** @return Whether the task is currently executing */
		public boolean isExecuting() {
			return isExecuting;
		}

		/** @return True if the timer has determined that this task's execution conditions are met and scheduled it for execution */
		public boolean isWaitingOrExecuting() {
			return isWaiting;
		}

		/** @return Whether this task is currently scheduled for one-time or periodic execution */
		public boolean isActive() {
			return isActive.get();
		}

		/** @return The time between now and when this task will next be run */
		public Duration getTimeUntilNextRun() {
			if (!isActive.get())
				return null;
			Instant nextRun = theNextRun;
			return nextRun == null ? null : TimeUtils.between(theClock.now(), nextRun);
		}

		/** @return The number of times this task has run since being created (or {@link #resetExecutionCount() reset}) */
		public long getExecutionCount() {
			return theExecCount;
		}

		/**
		 * @return The number of times this task has left to be executed before shutting itself down (or &lt;=0 if this task has no
		 *         remaining counter)
		 */
		public long getRemainingExecutionCount() {
			return theRemainingExecCount;
		}

		/** @return The time after which this task will shut itself down */
		public Instant getLastRun() {
			return theLastRun;
		}

		/** @return The last time this task was executed */
		public Instant getPreviousExecution() {
			return thePreviousRun;
		}

		/** @return The frequency at which this task executes */
		public Duration getFrequency() {
			return theFrequency;
		}

		/** @return The thread type that this task will be run on */
		public TaskThreading getThreading() {
			return theThreading;
		}

		/**
		 * Causes this task to execute as quickly as the scheduler can get to it.
		 * 
		 * If this task is not currently active, it will activate itself with a {@link #times(long) remaining execution count} of 1.
		 * 
		 * @return This task
		 */
		public TaskHandle runImmediately() {
			return runNextAt(Instant.MIN);
		}

		/**
		 * Sets the next execution time for this task. This time will be respected regardless of its {@link #getFrequency() frequency}.
		 * 
		 * If this task is not currently active, it will activate itself with a {@link #times(long) remaining execution count} of 1.
		 * 
		 * @param time The next scheduled execution time for this task
		 * @return This task
		 */
		public TaskHandle runNextAt(Instant time) {
			Instant nextRun = theNextRun;
			theNextRun = time;
			if (!isActive()) {
				theRemainingExecCount = 1;
				setActive(true);
			} else if (nextRun == null || time.compareTo(nextRun) < 0)
				interruptScheduler();
			return this;
		}

		/**
		 * Sets the next execution time for this task (relative to {@link TimerClock#now() now}).
		 * 
		 * If this task is not currently active, it will activate itself with a {@link #times(long) remaining execution count} of 1.
		 * 
		 * @param interval The duration until the next scheduled execution time for this task
		 * @return This task
		 */
		public TaskHandle runNextIn(Duration interval) {
			if (interval == null || interval.isNegative() || interval.isZero())
				return runImmediately();
			else
				return runNextAt(theClock.now().plus(interval));
		}

		/**
		 * Controls how often this periodic task executes
		 * 
		 * @param frequency The frequency at which to execute the task
		 * @param consistent Whether the task should be run on a strict interval. If true, an attempt will be made to start the task at the
		 *        beginning of the interval so that an execution happens precisely as often as described. If one execution is delayed, the
		 *        next may happen slightly earlier to make up the time and restore the rhythm to the interval. If false, the task will run
		 *        no more often than the given interval, i.e. the duration between the end of one execution and the beginning of another
		 *        will be the given frequency interval or slightly longer.
		 * @return This task handle
		 */
		public TaskHandle setFrequency(Duration frequency, boolean consistent) {
			theFrequency = frequency;
			isConsistent = consistent;
			if (frequency != null && isActive.get()) {
				Instant nextRun2 = theClock.now().plus(frequency);
				Instant nextRun = theNextRun;
				if (nextRun == null || nextRun2.compareTo(nextRun) < 0)
					theNextRun = nextRun2;
			}
			return this;
		}

		/**
		 * Causes this task to {@link #setActive(boolean) deactivate} itself after a certain number of executions
		 * 
		 * @param times The number of times to execute this task before shutting down. If &lt;=0, this task will run indefinitely.
		 * @return This task
		 */
		public TaskHandle times(long times) {
			theRemainingExecCount = times;
			return this;
		}

		/**
		 * Controls when this task will shut itself down
		 * 
		 * @param until The time at which this task will {@link #setActive(boolean) deactivate} itself
		 * @param runAfterLast Whether to run the task one last time at or after the given moment
		 * @return This task
		 */
		public TaskHandle until(Instant until, boolean runAfterLast) {
			theLastRun = until;
			this.shouldRunAfterLast = runAfterLast;
			return this;
		}

		/**
		 * Controls when this task will shut itself down
		 * 
		 * @param duration The duration for which this task will be {@link #setActive(boolean) active}
		 * @param runAfterEnd Whether to run the task one last time at or after the given moment
		 * @return This task
		 */
		public TaskHandle endIn(Duration duration, boolean runAfterEnd) {
			return until(theClock.now().plus(duration), runAfterEnd);
		}

		/**
		 * @param threading The threading scheme for this task
		 * @return This task
		 */
		public TaskHandle withThreading(TaskThreading threading) {
			if (threading == null)
				throw new NullPointerException();
			theThreading = threading;
			return this;
		}

		/**
		 * Specifies that this task should be run on the main timer thread. This scheme is only appropriate for very short tasks.
		 * Long-running tasks run on the timer thread risk delaying the execution other tasks.
		 * 
		 * @return This handle
		 * @see TaskThreading#Timer
		 */
		public TaskHandle onTimer() {
			return withThreading(TaskThreading.Timer);
		}

		/**
		 * Specifies that this task should be run on the AWT/Swing Event Dispatch Thread (EDT)
		 * 
		 * @return This handle
		 * @see TaskThreading#EDT
		 */
		public TaskHandle onEDT() {
			return withThreading(TaskThreading.EDT);
		}

		/**
		 * Specifies that this task may be run on any thread. This is the default. Tasks of this type will be offloaded to a separate thread
		 * pool to prevent any possibility of interfering with the scheduling or execution of other tasks.
		 * 
		 * @return This handle
		 * @see TaskThreading#Any
		 */
		public TaskHandle onAnyThread() {
			return withThreading(TaskThreading.Any);
		}

		/**
		 * Sets this task's {@link #getExecutionCount() execution count} to zero
		 * 
		 * @return This task
		 */
		public TaskHandle resetExecutionCount() {
			theExecCount = 0;
			return this;
		}

		/**
		 * Causes this task to begin or cease executing
		 * 
		 * @param active Whether the task should execut or not
		 * @return This tak handle
		 */
		public TaskHandle setActive(boolean active) {
			if (isActive.compareAndSet(!active, active)) {
				synchronized (this) {
					if (isActive.get() != active)
						return this;
					if (active) {
						theRemove = schedule(this);
					} else {
						theRemove.run();
						theRemove = null;
					}
				}
			}
			return this;
		}

		@Override
		public String toString() {
			return theTask.toString();
		}

		boolean shouldExecute(Instant now, Instant[] minNextRun) {
			if (!isActive.get())
				return false;
			else if (didOffloadFail) {
				didOffloadFail = false;
				return true;
			} else if (isWaiting) {
				minNextRun[0] = now.plus(Duration.ofMillis(10));
				return false;
			}
			Instant nextRun = theNextRun;
			if (nextRun == null)
				nextRun = now;
			boolean execute = now.compareTo(nextRun) >= 0;
			Instant lastRun = theLastRun;
			boolean terminate = false;
			if (lastRun != null && !shouldRunAfterLast && now.compareTo(lastRun) > 0) {
				execute = false;
				terminate = true;
				nextRun = null;
			} else if (execute) {
				if (isConsistent) {
					Duration freq = theFrequency;
					nextRun = nextRun.plus(freq);
					if (now.compareTo(nextRun) > 0) {
						// Not keeping up; skip a run or two
						long mult = TimeUtils.divide(TimeUtils.between(nextRun, now), freq);
						nextRun = nextRun.plus(freq.multipliedBy(mult));
						// Probably need to skip one more due to flooring
						if (now.compareTo(nextRun) > 0)
							nextRun = nextRun.plus(freq);
					}
					theNextRun = nextRun;
				} else
					theNextRun = nextRun = null;
				isWaiting = true;
			}
			if (nextRun != null) {
				if (minNextRun[0] == null || nextRun.compareTo(minNextRun[0]) < 0)
					minNextRun[0] = nextRun;
			} else if (terminate)
				setActive(false);
			return execute;
		}

		void offloadFailed() {
			didOffloadFail = true;
		}

		void execute() {
			if (isActive.get()) {
				isExecuting = true;
				try {
					theExecCount++;
					thePreviousRun = theClock.now();
					Instant nextRun;
					boolean terminate = false;
					boolean interrupt = false;
					if (theNextRun == null && theFrequency != null) {
						interrupt = true;
						nextRun = thePreviousRun.plus(theFrequency);
					} else
						nextRun = theNextRun;
					Instant lastRun = theLastRun;
					if (nextRun != null && lastRun != null) {
						if (thePreviousRun.compareTo(lastRun) >= 0)
							terminate = true;
						else if (!shouldRunAfterLast && nextRun.compareTo(lastRun) > 0)
							terminate = true;
						if (terminate)
							theLastRun = null;
					}
					long rem = theRemainingExecCount;
					if (rem > 0) {
						rem--;
						theRemainingExecCount = rem;
						if (rem == 0) {
							terminate = true;
							nextRun = null;
						}
					}
					theNextRun = nextRun;
					if (terminate) {
						setActive(false);
						interrupt = false;
					}
					try {
						theTask.run();
					} catch (RuntimeException | Error e) {
						e.printStackTrace();
					}
					if (interrupt)
						interruptScheduler();
				} finally {
					isExecuting = false;
				}
			}
			isWaiting = false;
		}
	}

	final TimerClock theClock;
	private final Consumer<Runnable> theMainRunner;
	final Function<Runnable, Boolean> theAccessoryRunner;
	private final ListenerList<TaskHandle> theTaskQueue;
	private volatile boolean isRunning;
	private volatile Thread theSchedulerThread;
	private final AtomicBoolean isSleeping;
	private final ConcurrentHashMap<Object, TaskHandle> theInactityTasks;

	/**
	 * @param clock The clock implementation to use for scheduling
	 * @param mainRunner Runs the scheduler for this timer
	 * @param accessoryRunner Runs offloaded tasks for this timer (see {@link TaskHandle#onAnyThread()}). The return boolean should be true
	 *        if the task was queued, or false if it wasn't (e.g. due to queue size)
	 */
	public QommonsTimer(TimerClock clock, Consumer<Runnable> mainRunner, Function<Runnable, Boolean> accessoryRunner) {
		theClock = clock;
		theMainRunner = mainRunner;
		theAccessoryRunner = accessoryRunner;
		theInactityTasks = new ConcurrentHashMap<>();
		theTaskQueue = ListenerList.build().allowReentrant().withFastSize(false).withInUse(inUse -> {
			if (inUse) {
				synchronized (this) {
					if (!isRunning)
						start();
				}
			} else
				interruptScheduler();
		}).build();
		isSleeping = new AtomicBoolean();
	}

	/** @return The clock driving this timer */
	public TimerClock getClock() {
		return theClock;
	}

	/**
	 * Begins periodic execution of a new task
	 * 
	 * @param task The task to execute periodically
	 * @param frequency The frequency at which to execute the task
	 * @param consistent Whether the task should be run on a strict interval. If true, an attempt will be made to start the task at the
	 *        beginning of the interval so that an execution happens precisely as often as described. If one execution is delayed, the next
	 *        may happen slightly earlier to make up the time and restore the rhythm to the interval. If false, the task will run no more
	 *        often than the given interval, i.e. the duration between the end of one execution and the beginning of another will be the
	 *        given frequency interval or slightly longer.
	 * @return The task handle to use to control or stop the task's execution
	 */
	public TaskHandle execute(Runnable task, Duration frequency, boolean consistent) {
		return execute(task, frequency, frequency, consistent);
	}

	/**
	 * Begins periodic execution of a new task
	 * 
	 * @param task The task to execute periodically
	 * @param initialDelay The amount of time before the first execution of the task
	 * @param frequency The frequency at which to execute the task
	 * @param consistent Whether the task should be run on a strict interval. If true, an attempt will be made to start the task at the
	 *        beginning of the interval so that an execution happens precisely as often as described. If one execution is delayed, the next
	 *        may happen slightly earlier to make up the time and restore the rhythm to the interval. If false, the task will run no more
	 *        often than the given interval, i.e. the duration between the end of one execution and the beginning of another will be the
	 *        given frequency interval or slightly longer.
	 * @return The task handle to use to control or stop the task's execution
	 */
	public TaskHandle execute(Runnable task, Duration initialDelay, Duration frequency, boolean consistent) {
		return build(task, frequency, consistent).runNextIn(initialDelay).setActive(true);
	}

	/**
	 * Creates a handle for a task, relying on the caller to begin its execution with {@link TaskHandle#setActive(boolean) setActive(true)}
	 * 
	 * @param task The task to execute periodically
	 * @param frequency The frequency at which to execute the task
	 * @param consistent Whether the task should be run on a strict interval. If true, an attempt will be made to start the task at the
	 *        beginning of the interval so that an execution happens precisely as often as described. If one execution is delayed, the next
	 *        may happen slightly earlier to make up the time and restore the rhythm to the interval. If false, the task will run no more
	 *        often than the given interval, i.e. the duration between the end of one execution and the beginning of another will be the
	 *        given frequency interval or slightly longer.
	 * @return The task handle to use to control or stop the task's execution
	 */
	public TaskHandle build(Runnable task, Duration frequency, boolean consistent) {
		return new TaskHandle(task, frequency, consistent);
	}

	/**
	 * Sets a timer after which a task will be executed if this method is not called again with the same key. This method is useful for
	 * ensuring that dynamically-created resources are closed when they may be re-used multiple times, without requiring that they be
	 * re-allocated every time they are needed.
	 * 
	 * @param taskKey The key representing the task
	 * @param task The task to run after the inactivity period has elapsed
	 * @param inactiveTime The inactivity period after which to run the task if this method has not been called again with the same key
	 * @return The task handle for the task
	 */
	public TaskHandle doAfterInactivity(Object taskKey, Runnable task, long inactiveTime) {
		return doAfterInactivity(taskKey, task, Duration.ofMillis(inactiveTime));
	}

	/**
	 * <p>
	 * Performs a task after this method has not been called (with the same key) after a certain time period.
	 * </p>
	 * <p>
	 * This method is good for performing cleanup tasks after a resources has not been used for a while. This eliminates the need to close
	 * and re-create the resource each time it is needed, while ensuring that the resource is cleaned up eventually.
	 * </p>
	 * 
	 * @param taskKey A unique (by {@link Object#equals(Object)}) key used to prolong the task's execution as long as this method keeps
	 *        being called periodically (faster than <code>inactiveTime</code>).
	 * @param task The task to execute
	 * @param inactiveTime The inactive time after which to perform the task
	 * @return The task handle for the task
	 */
	public TaskHandle doAfterInactivity(Object taskKey, Runnable task, Duration inactiveTime) {
		return theInactityTasks.compute(taskKey, (k, existing) -> {
			if (existing == null) {
				TaskHandle[] handle = new TaskHandle[1];
				return handle[0] = build(() -> {
					try {
						task.run();
					} finally {
						if (!handle[0].isActive())
							theInactityTasks.remove(taskKey);
					}
				}, Duration.ofSeconds(1_000_000_000), false).runNextIn(inactiveTime).times(1).setActive(true);
			} else {
				existing.times(1).runNextIn(inactiveTime).setActive(true);
				return existing;
			}
		});
	}

	/**
	 * Executes a task one time (unless the task handle is modified to execute more) after an optional delay
	 * 
	 * @param task The task to execute
	 * @param delay The delay to wait before executing the task (may be null to execute immediately)
	 * @return The handle for the task
	 */
	public TaskHandle offload(Runnable task, Duration delay) {
		TaskHandle handle = build(task, null, false).times(1);
		if (delay != null)
			handle.runNextIn(delay);
		else
			handle.runImmediately();
		return handle;
	}

	/**
	 * Executes a task one time in a different thread as soon as possible
	 * 
	 * @param task The task to execute
	 */
	public void offload(Runnable task) {
		while (!theAccessoryRunner.apply(task)) {
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {}
		}
	}

	Runnable schedule(TaskHandle task) {
		return theTaskQueue.add(task, false);
	}

	/** @return Whether there are any tasks scheduled in this timer */
	public boolean isExecuting() {
		return !theTaskQueue.isEmpty();
	}

	private void start() {
		isRunning = true;
		theMainRunner.accept(this::execute);
	}

	void interruptScheduler() {
		if (!isSleeping.compareAndSet(false, true)) {
			Thread schedulerThread = theSchedulerThread;
			if (schedulerThread != null)
				schedulerThread.interrupt();
		}
	}

	void execute() {
		Instant[] minNextRun = new Instant[1];
		Instant now = theClock.now();
		theSchedulerThread = Thread.currentThread();
		isSleeping.set(false);
		while (true) {
			minNextRun[0] = null;
			Instant fNow = now;
			theTaskQueue.forEach(handle -> {
				if (handle.shouldExecute(fNow, minNextRun)) {
					switch (handle.getThreading()) {
					case Timer:
						handle.execute();
						break;
					case EDT:
						EventQueue.invokeLater(handle.offloadTask);
						break;
					case Any:
						if (!theAccessoryRunner.apply(handle::execute)) {
							handle.offloadFailed();
							minNextRun[0] = Instant.MIN;
						}
						break;
					}
				}
			});
			now = theClock.now();
			if (minNextRun[0] != null && minNextRun[0].compareTo(now) > 0) {
				Duration sleepTime = TimeUtils.between(now, minNextRun[0]);
				if (isSleeping.compareAndSet(false, true)) {
					try {
						theClock.sleep(sleepTime);
					} catch (InterruptedException e) {
					}
					now = theClock.now();
				}
			}
			isSleeping.set(false);
			if (theTaskQueue.isEmpty()) {
				synchronized (this) {
					if (theTaskQueue.isEmpty()) {
						theSchedulerThread = null;
						isRunning = false;
						break;
					}
				}
			}
		}
	}

	/**
	 * A test that is driven by the user (pressing enter in the console) to trigger different timer modes
	 * 
	 * @param args Command-line arguments, ignored
	 */
	public static void main(String[] args) {
		System.out.println("Init 3 sec delay, 1 sec frequency");
		long[] prev = new long[] { System.currentTimeMillis() };
		TaskHandle handle = getCommonInstance().execute(() -> {
			long now = System.currentTimeMillis();
			System.out.println(QommonsUtils.printTimeLength(now - prev[0]) + " on " + Thread.currentThread().getName());
			prev[0] = now;
		}, Duration.ofSeconds(3), Duration.ofSeconds(1), true);
		prev[0] = System.currentTimeMillis();

		try (Scanner scanner = new Scanner(System.in)) {
			scanner.nextLine();
			System.out.println("Delaying 3 seconds");
			prev[0] = System.currentTimeMillis();
			handle.runNextIn(Duration.ofSeconds(3));

			scanner.nextLine();
			System.out.println("Set frequency to 2 seconds");
			handle.setFrequency(Duration.ofSeconds(2), false);

			ArgumentParser argParser = ArgumentParsing.create().forPattern("(.+)=(.+)")//
				.booleanArg("active")//
				.durationArg("end")//
				.durationArg("next")//
				.durationArg("freq")//
				.intArg("times").between(0, 100)//
				.getParser().forDefaultFlagPattern()//
				.flagArg("now")//
				.flagArg("nextRun")//
				.flagArg("i")//
				.getParser();

			while (true) {
				String line = scanner.nextLine();
				if (line.length() == 0) {
					System.err.println(argParser);
					continue;
				}
				Arguments lineArgs;
				try {
					lineArgs = argParser.parse(line.split(" "));
				} catch (IllegalArgumentException e) {
					System.err.println(e.getMessage());
					continue;
				}
				for(Argument arg : lineArgs.getArguments()){
					switch(arg.getName()){
					case "i":
						getCommonInstance().doAfterInactivity("test", () -> System.out.println("inactive"), 2000);
						break;
					case "active":
						prev[0] = System.currentTimeMillis();
						handle.setActive(((ValuedArgument<Boolean>) arg).getValue());
						break;
					case "end":
						handle.endIn(((ValuedArgument<Duration>) arg).getValue(), true);
						break;
					case "next":
						prev[0] = System.currentTimeMillis();
						handle.runNextIn(((ValuedArgument<Duration>) arg).getValue());
						break;
					case "freq":
						handle.setFrequency(((ValuedArgument<Duration>) arg).getValue(), true);
						break;
					case "times":
						handle.times(((ValuedArgument<Number>) arg).getValue().intValue());
						break;
					case "now":
						prev[0] = System.currentTimeMillis();
						handle.runImmediately();
						break;
					case "nextRun":
						System.out.println(QommonsUtils.printDuration(handle.getTimeUntilNextRun(), false));
						break;
					default:
						System.err.println("Unrecognized arg: "+arg.getName());
					}
				}
			}
		}
	}
}

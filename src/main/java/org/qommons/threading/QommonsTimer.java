package org.qommons.threading;

import java.awt.EventQueue;
import java.time.Duration;
import java.time.Instant;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.QommonsUtils;
import org.qommons.TimeUtils;
import org.qommons.collect.ListenerList;

/** A timer class that allows very flexible scheduling of tasks without needing to create a thread per task */
public class QommonsTimer {
	private static final QommonsTimer COMMON_INSTANCE = new QommonsTimer(new SystemClock(), r -> {
		Thread t = new Thread(r, "Qommon Timer");
		t.start();
	}, new ElasticExecutor<>("Qommon Timer Offloader", () -> Runnable::run).setUsedThreadLifetime(2000)::execute);

	/** @return A common timer that uses the system clock */
	public static QommonsTimer getCommonInstance() {
		return COMMON_INSTANCE;
	}

	public interface TimerClock {
		Instant now();
	}

	public static class SystemClock implements TimerClock {
		@Override
		public Instant now() {
			long millis = System.currentTimeMillis();
			long nanos = System.nanoTime() % 1000000000;
				if (nanos < 0)
					nanos += 1000000000;
				nanos += millis * 1000000L;
			return Instant.ofEpochSecond(millis, nanos);
		}
	}

	public enum TaskThreading {
		Timer, EDT, Any;
	}

	/** A handle on a scheduled task that allows */
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
		private volatile boolean runAfterLast;

		private volatile boolean isExecuting;
		private volatile boolean isWaiting;
		private volatile long theExecCount;
		private volatile TaskThreading theThreading;

		TaskHandle(Runnable task) {
			theTask = task;
			isActive = new AtomicBoolean();
			theThreading = TaskThreading.Any;
			theRemainingExecCount = -1;
		}

		public QommonsTimer getTimer() {
			return QommonsTimer.this;
		}

		public long getExecCount() {
			return theExecCount;
		}

		public boolean isExecuting() {
			return isExecuting;
		}

		public boolean isWaitingOrExecuting() {
			return isWaiting;
		}

		public boolean isActive() {
			return isActive.get();
		}

		public Duration getTimeUntilNextRun() {
			if (!isActive.get())
				return null;
			Instant nextRun = theNextRun;
			return nextRun == null ? null : TimeUtils.between(theClock.now(), nextRun);
		}

		public long getExecutionCount() {
			return theExecCount;
		}

		public long getRemainingExecutionCount() {
			return theRemainingExecCount;
		}

		public Instant getLastRun() {
			return theLastRun;
		}

		public Instant getPreviousExecution() {
			return thePreviousRun;
		}

		public Duration getFrequency() {
			return theFrequency;
		}

		/** @return The thread type that this task will be run on */
		public TaskThreading getThreading() {
			return theThreading;
		}

		public TaskHandle runImmediately() {
			return runNextAt(theClock.now());
		}

		public TaskHandle runNextAt(Instant time) {
			theNextRun = time;
			return this;
		}

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
			if (frequency != null && isActive.get()) {
				Instant nextRun2 = theClock.now().plus(frequency);
				Instant nextRun = theNextRun;
				if (nextRun == null || nextRun2.compareTo(nextRun) < 0)
					theNextRun = nextRun2;
			}
			return this;
		}

		public TaskHandle times(long times) {
			theRemainingExecCount = times;
			return null;
		}

		/**
		 * Controls when this task will shut itself down
		 * 
		 * @param until
		 * @param runAfterLast
		 * @return
		 */
		public TaskHandle until(Instant until, boolean runAfterLast) {
			theLastRun = until;
			this.runAfterLast = runAfterLast;
			return this;
		}

		public TaskHandle withThreading(TaskThreading threading) {
			if (threading == null)
				throw new NullPointerException();
			theThreading = threading;
			return this;
		}

		/**
		 * Specifies that this task should be run on the main timer thread. This scheme is only appropriate for very short tasks.
		 * Long-running tasks * run on the timer thread risk delaying the execution other tasks.
		 * 
		 * @return This handle
		 */
		public TaskHandle onTimer() {
			return withThreading(TaskThreading.Timer);
		}

		/**
		 * Specifies that this task should be run on the AWT/Swing Event Dispatch Thread (EDT)
		 * 
		 * @return This handle
		 */
		public TaskHandle onEDT() {
			return withThreading(TaskThreading.EDT);
		}

		/**
		 * Specifies that this task may be run on any thread. This is the default. Tasks of this type will be offloaded to a separate thread
		 * pool to prevent any possibility of interfering with the scheduling or execution of other tasks.
		 * 
		 * @return This handle
		 */
		public TaskHandle onAnyThread() {
			return withThreading(TaskThreading.Any);
		}

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
					if (active)
						theRemove = schedule(this);
					else {
						theRemove.run();
						theRemove = null;
					}
				}
			}
			return this;
		}

		boolean shouldExecute(Instant now, Instant[] minNextRun) {
			if (!isActive.get())
				return false;
			else if (isWaiting) {
				minNextRun[0] = Instant.MIN;
				return false;
			}
			Instant nextRun = theNextRun;
			boolean execute = now.compareTo(nextRun) >= 0;
			Instant lastRun = theLastRun;
			if (lastRun != null && !runAfterLast && now.compareTo(lastRun) > 0) {
				execute = false;
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
				} else
					nextRun = null;
				long rem = theRemainingExecCount;
				if (rem > 0) {
					rem--;
					theRemainingExecCount = rem;
					if (rem == 0)
						nextRun = null;
				}
				isWaiting = true;
			}
			if (nextRun != null) {
				if (minNextRun[0] == null || nextRun.compareTo(minNextRun[0]) < 0)
					minNextRun[0] = nextRun;
			} else
				setActive(false);
			return execute;
		}

		void execute() {
			if (isActive.get()) {
				isExecuting = true;
				try {
					thePreviousRun = theClock.now();
					theTask.run();
				} catch (RuntimeException | Error e) {
					e.printStackTrace();
				}
				theExecCount++;
				Instant nextRun;
				if (theRemainingExecCount == 0)
					nextRun = null;
				else if (theNextRun == null && theFrequency != null)
					nextRun = thePreviousRun.plus(theFrequency);
				else
					nextRun = theNextRun;
				Instant lastRun = theLastRun;
				if (nextRun != null && lastRun != null) {
					if (thePreviousRun.compareTo(lastRun) >= 0)
						nextRun = null;
					else if (!runAfterLast && nextRun.compareTo(lastRun) > 0)
						nextRun = null;
				}
				theNextRun = nextRun;
				if (nextRun != null)
					interruptScheduler();
				else
					setActive(false);
				isExecuting = false;
			}
			isWaiting = false;
		}
	}

	final TimerClock theClock;
	private final Consumer<Runnable> theMainRunner;
	final Function<Runnable, Boolean> theAccessoryRunner;
	private final ListenerList<TaskHandle> theTaskQueue;
	private volatile boolean shouldRun;
	private volatile boolean isRunning;
	private volatile Thread theSchedulerThread;
	private final AtomicBoolean isSleeping;

	public QommonsTimer(TimerClock clock, Consumer<Runnable> mainRunner, Function<Runnable, Boolean> accessoryRunner) {
		theClock = clock;
		theMainRunner = mainRunner;
		theAccessoryRunner = accessoryRunner;
		theTaskQueue = ListenerList.build().allowReentrant().withFastSize(false).withInUse(inUse -> {
			shouldRun = inUse;
			if (inUse)
				start();
		}).build();
		isSleeping = new AtomicBoolean();
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
		return build(task).setFrequency(frequency, consistent).runNextIn(frequency).setActive(true);
	}

	/**
	 * Creates a handle for a task, relying on the caller to begin its execution with {@link TaskHandle#setActive(boolean) setActive(true)}
	 * 
	 * @param task The task to execute periodically
	 * @return The task handle to use to control or stop the task's execution
	 */
	public TaskHandle build(Runnable task) {
		return new TaskHandle(task);
	}

	Runnable schedule(TaskHandle task) {
		return theTaskQueue.add(task, false);
	}

	/** @return Whether there are any tasks scheduled in this timer */
	public boolean isExecuting() {
		return !theTaskQueue.isEmpty();
	}

	private void start() {
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
		while (isRunning) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {}
		}
		Instant[] minNextRun = new Instant[1];
		Instant now = theClock.now();
		theSchedulerThread = Thread.currentThread();
		isSleeping.set(false);
		while (shouldRun) {
			minNextRun[0] = null;
			Instant fNow = now;
			theTaskQueue.forEach(handle -> {
				if (handle.shouldExecute(fNow, minNextRun)) {
					switch (handle.getThreading()) {
					case Timer:
						handle.execute();
						break;
					case EDT:
						EventQueue.invokeLater(handle::execute);
						break;
					case Any:
						if (!theAccessoryRunner.apply(handle::execute))
							minNextRun[0] = Instant.MIN;
						break;
					}
				}
			});
			now = theClock.now();
			if (minNextRun[0] != null && minNextRun[0].compareTo(now) > 0) {
				Duration sleepTime = TimeUtils.between(now, minNextRun[0]);
				if (isSleeping.compareAndSet(false, true)) {
					try {
						if (sleepTime.getSeconds() != 0 || sleepTime.getNano() >= 5_000_000)
							Thread.sleep(sleepTime.toMillis());
						else
							Thread.sleep(sleepTime.getNano() / 1_000_000, sleepTime.getNano() % 1_000_000);
					} catch (InterruptedException e) {
					}
				}
			}
			isSleeping.set(false);
		}
		theSchedulerThread = null;
		isRunning = false;
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

			while (true) {
				String line = scanner.nextLine();
				if ("stop".equals(line)) {
					handle.setActive(false);
					break;
				}
				TaskThreading nextThreading = nextThreading(handle.getThreading());
				System.out.println("Run immediately, threading=" + nextThreading);
				handle.setFrequency(Duration.ofSeconds(1), true).withThreading(nextThreading);
				prev[0] = System.currentTimeMillis();
				handle.runImmediately();
			}
			scanner.nextLine();
		}
	}

	private static TaskThreading nextThreading(TaskThreading threading) {
		int idx = threading.ordinal() + 1;
		return TaskThreading.values()[idx < TaskThreading.values().length ? idx : 0];
	}
}

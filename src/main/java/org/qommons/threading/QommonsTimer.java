package org.qommons.threading;

import java.awt.EventQueue;
import java.time.Duration;
import java.time.Instant;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.QommonsUtils;
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
		Time now(boolean nanoPrecision);
	}

	public static class SystemClock implements TimerClock {
		@Override
		public Time now(boolean nanoPrecision) {
			long millis = System.currentTimeMillis();
			long nanos;
			if (nanoPrecision) {
				nanos = System.nanoTime() % 1000000000;
				if (nanos < 0)
					nanos += 1000000000;
				nanos += millis * 1000000L;
			} else {
				nanos = 0;
			}
			return new Time(millis, nanos);
		}
	}

	public static class Time implements Comparable<Time> {
		public static Time ZERO = new Time(0, 0);
		public static Time FOREVER = new Time(Long.MAX_VALUE, 0);

		public final long millis;
		public final long nanos;

		public Time(long millis, long nanos) {
			this.millis = millis;
			this.nanos = nanos;
		}

		@Override
		public int compareTo(Time time) {
			int comp = Long.compare(millis, time.millis);
			if (comp == 0) {
				long nanoDiff = (nanos - time.nanos);
				comp = nanoDiff < 0 ? -1 : (nanoDiff > 0 ? 1 : 0);
			}
			return comp;
		}

		public Time plus(Time other) {
			return new Time(millis + other.millis, nanos + other.nanos);
		}

		public Time minus(Time other) {
			if (millis != 0) {
				if (other.millis != 0)
					return new Time(millis - other.millis, 0);
				else
					return new Time(0, millis * 1000000 - other.nanos);
			} else if (other.millis != 0)
				return new Time(0, nanos - other.millis * 1000000);
			else
				return new Time(0, nanos - other.nanos);
		}

		public Time multipliedBy(long mult) {
			return new Time(millis * mult, nanos * mult);
		}

		public long dividedBy(Time time) {
			if (millis != 0) {
				if (time.millis != 0)
					return millis / time.millis;
				else
					return millis * 1000000 / time.nanos;
			} else if (time.millis != 0)
				return nanos / (time.millis * 1000000);
			else
				return nanos / time.nanos;
		}

		public static Time of(Instant time, boolean nanoPrecision) {
			if (!nanoPrecision)
				return new Time(time.toEpochMilli(), 0);
			long secs = time.getEpochSecond();
			int nanos = time.getNano();
			return new Time(secs * 1000 + nanos / 1000, secs * 1000000000 + nanos);
		}

		public static Time of(Duration time, boolean nanoPrecision) {
			if (!nanoPrecision)
				return new Time(time.toMillis(), 0);
			long secs = time.getSeconds();
			int nanos = time.getNano();
			return new Time(secs * 1000 + nanos / 1000, secs * 1000000000 + nanos);
		}

		@Override
		public String toString() {
			return millis + ":" + nanos;
		}
	}

	public enum TaskThreading {
		Timer, EDT, Any;
	}

	/** A handle on a scheduled task that allows */
	public class TaskHandle {
		private final Runnable theTask;
		private volatile boolean isScheduled;
		private Runnable theRemove;

		private Duration theFrequency;
		private volatile Time theFrequencyTime;
		private volatile boolean isConsistent;

		private volatile Time theLastRun;
		private volatile Time theNextRun;

		private volatile boolean isExecuting;
		private volatile boolean isWaiting;
		private volatile long theExecCount;
		private volatile TaskThreading theThreading;

		TaskHandle(Runnable task, Duration initDelay, Duration frequency, boolean consistent) {
			theTask = task;
			isScheduled = true;
			theThreading = TaskThreading.Any;
			runNextIn(frequency); // Gotta set this first because setFrequency uses it
			setFrequency(frequency, consistent);
			runNextIn(initDelay);
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

		public boolean isScheduled() {
			return isScheduled;
		}

		public Duration getTimeUntilNextRun() {
			if (!isScheduled)
				return null;
			Time nextRun = theNextRun;
			boolean nanoPrecision = nextRun.nanos != 0;
			Time now = theClock.now(nanoPrecision);
			long millisDiff = now.millis - nextRun.millis;
			if (millisDiff < 0)
				return Duration.ZERO;
			else if (!nanoPrecision || millisDiff >= 10)
				return Duration.ofMillis(millisDiff);
			long nanoDiff = now.nanos - nextRun.nanos;
			if (nanoDiff < 0)
				return Duration.ZERO;
			return Duration.ofNanos(nanoDiff);
		}

		public Duration getFrequency() {
			return theFrequency;
		}

		/** @return The thread type that this task will be run on */
		public TaskThreading getThreading() {
			return theThreading;
		}

		public TaskHandle runImmediately() {
			return runNextAt(theClock.now(true));
		}

		public TaskHandle runNextAt(Instant time, boolean nanoPrecision) {
			return runNextAt(Time.of(time, nanoPrecision));
		}

		public TaskHandle runNextIn(Duration interval) {
			if (interval.isNegative() || interval.isZero())
				return runImmediately();
			else {
				boolean nanoPrecision = interval.getSeconds() == 0 && interval.getNano() < 5_000_000;
				return runNextAt(theClock.now(nanoPrecision).plus(Time.of(interval, nanoPrecision)));
			}
		}

		private TaskHandle runNextAt(Time time) {
			if (!isScheduled)
				throw new IllegalStateException("This task has been stopped");
			theNextRun = time;
			interruptScheduler();
			return this;
		}

		public TaskHandle setFrequency(Duration frequency, boolean consistent) {
			if (!isScheduled)
				throw new IllegalStateException("This task has been stopped");
			theFrequency = frequency;
			boolean nanoPrecision = frequency.getSeconds() == 0 && frequency.getNano() < 5_000_000;
			if (nanoPrecision) {
				theFrequencyTime = new Time(0, frequency.toNanos());
			} else
				theFrequencyTime = new Time(frequency.toMillis(), 0);
			isConsistent = consistent;

			Time nextRun = theNextRun;
			if (nextRun != Time.FOREVER) {
				Time now = theClock.now(nanoPrecision);
				Time nextRun2 = now.plus(theFrequencyTime);
				if (nextRun2.compareTo(nextRun) < 0) {
					theNextRun = nextRun2;
					interruptScheduler();
				}
			}
			return this;
		}

		public TaskHandle withThreading(TaskThreading threading) {
			if (!isScheduled)
				throw new IllegalStateException("This task has been stopped");
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

		/**
		 * Permanently removes this task from the schedule. It cannot be added again, but must be re-scheduled with a different handle. This
		 * call does not stop the current execution if the task is currently running.
		 */
		public void stop() {
			if (isScheduled) {
				isScheduled = false;
				theRemove.run();
			}
		}

		boolean shouldExecute(Time now, Time[] minNextRun) {
			if (!isScheduled)
				return false;
			else if (isWaiting) {
				minNextRun[0] = Time.ZERO;
				return false;
			}
			boolean execute = now.compareTo(theNextRun) >= 0;
			if (execute) {
				if (isConsistent) {
					theNextRun = theNextRun.plus(theFrequencyTime);
					if (now.compareTo(theNextRun) > 0) {
						// Not keeping up; skip a run or two
						long mult = now.minus(theNextRun).dividedBy(theFrequencyTime);
						theNextRun = theNextRun.plus(theFrequencyTime.multipliedBy(mult));
						// Probably need to skip one more due to flooring
						if (now.compareTo(theNextRun) > 0)
							theNextRun = theNextRun.plus(theFrequencyTime);
					}
				} else
					theNextRun = Time.FOREVER;
				isWaiting = true;
			}
			if (minNextRun[0] == null || theNextRun.compareTo(minNextRun[0]) < 0)
				minNextRun[0] = theNextRun;
			return execute;
		}

		void execute() {
			if (isScheduled) {
				isExecuting = true;
				try {
					theLastRun = theClock.now(theFrequencyTime.nanos != 0);
					theTask.run();
				} catch (RuntimeException | Error e) {
					e.printStackTrace();
				}
				theExecCount++;
				if (theNextRun == Time.FOREVER) {
					theNextRun = theLastRun.plus(theFrequencyTime);
					interruptScheduler();
				}
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
		TaskHandle handle = new TaskHandle(task, initialDelay, frequency, consistent);
		handle.theRemove = theTaskQueue.add(handle, false);
		return handle;
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
		Time[] minNextRun = new Time[1];
		Time now = theClock.now(true);
		theSchedulerThread = Thread.currentThread();
		isSleeping.set(false);
		while (shouldRun) {
			minNextRun[0] = null;
			Time fNow = now;
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
							minNextRun[0] = Time.ZERO;
						break;
					}
				}
			});
			now = theClock.now(true);
			if (minNextRun[0] != null && minNextRun[0].compareTo(now) > 0) {
				Time sleepTime = minNextRun[0].minus(now);
				if (isSleeping.compareAndSet(false, true)) {
					try {
						if (sleepTime.millis >= 5)
							Thread.sleep(sleepTime.millis);
						else
							Thread.sleep(0, (int) sleepTime.nanos);
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
					handle.stop();
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

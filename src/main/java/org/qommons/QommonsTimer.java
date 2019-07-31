package org.qommons;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.collect.ListenerList;

// Untested!
public class QommonsTimer {
	private static final QommonsTimer COMMON_INSTANCE = new QommonsTimer(new SystemClock(), r -> {
		Thread t = new Thread(r, "Qommon Timer");
		t.start();
	}, new ElasticExecutor<>("Qommon Timer Offloader", () -> Runnable::run)::execute);

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
	}

	public class TaskHandle {
		private final Runnable theTask;
		private volatile boolean isScheduled;
		private Runnable theRemove;

		private Duration theFrequency;
		private volatile Time theFrequencyTime;
		private volatile boolean isConsistent;

		private volatile Time theNextRun;

		private volatile boolean isExecuting;
		private volatile boolean isWaiting;
		private volatile long theExecCount;
		private volatile boolean isOffloaded;

		TaskHandle(Runnable task, Duration initDelay, Duration frequency, boolean consistent) {
			theTask = task;
			isScheduled = true;
			runNextIn(frequency);
			setFrequency(frequency, consistent);
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

		public TaskHandle runImmediately() {
			theNextRun = Time.ZERO;
			return this;
		}

		public TaskHandle runNextAt(Instant time, boolean nanoPrecision) {
			theNextRun = Time.of(time, nanoPrecision);
			return this;
		}

		public TaskHandle runNextIn(Duration interval) {
			if (interval.isNegative() || interval.isZero())
				return runImmediately();
			else
				return runNextAt(Instant.now().plus(interval), interval.getSeconds() < 5);
		}

		public Duration getTimeUntilNextRun() {
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

		public TaskHandle setFrequency(Duration frequency, boolean consistent) {
			theFrequency = frequency;
			boolean nanoPrecision = frequency.getSeconds() == 0 && frequency.getNano() < 5_000_000;
			if (nanoPrecision) {
				theFrequencyTime = new Time(0, frequency.toNanos());
			} else
				theFrequencyTime = new Time(frequency.toMillis(), 0);
			isConsistent = consistent;

			Time nextRun = theNextRun;
			Time now = theClock.now(nanoPrecision);
			Time nextRun2 = now.plus(theFrequencyTime);
			if (nextRun2.compareTo(nextRun) < 0)
				theNextRun = nextRun2;
			return this;
		}

		public boolean isOffloaded() {
			return isOffloaded;
		}

		public TaskHandle offload(boolean offloaded) {
			isOffloaded = offloaded;
			return this;
		}

		public void stop() {
			if (isScheduled) {
				isScheduled = false;
				theRemove.run();
			}
		}

		public QommonsTimer getTimer() {
			return QommonsTimer.this;
		}

		boolean shouldExecute(Time now, Time[] minNextRun) {
			boolean execute = now.compareTo(theNextRun) >= 0;
			if (execute && isWaiting) {
				minNextRun[0] = Time.ZERO;
				return false;
			}
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
					theNextRun = now.plus(theFrequencyTime);
				isWaiting = true;
			}
			if (minNextRun[0] == null || theNextRun.compareTo(minNextRun[0]) < 0)
				minNextRun[0] = theNextRun;
			return execute;
		}

		void execute() {
			isExecuting = true;
			try {
				theTask.run();
			} catch (RuntimeException | Error e) {
				e.printStackTrace();
			}
			theExecCount++;
			isExecuting = false;
			isWaiting = false;
		}
	}

	final TimerClock theClock;
	private final Consumer<Runnable> theMainRunner;
	final Function<Runnable, Boolean> theAccessoryRunner;
	private final ListenerList<TaskHandle> theTaskQueue;
	private volatile boolean shouldRun;
	private volatile boolean isRunning;

	public QommonsTimer(TimerClock clock, Consumer<Runnable> mainRunner, Function<Runnable, Boolean> accessoryRunner) {
		theClock = clock;
		theMainRunner = mainRunner;
		theAccessoryRunner = accessoryRunner;
		theTaskQueue = ListenerList.build().allowReentrant().withFastSize(false).withInUse(inUse -> {
			shouldRun = inUse;
			if (inUse)
				start();
		}).build();
	}

	public TaskHandle execute(Runnable task, Duration frequency, boolean consistent) {
		return execute(task, frequency, frequency, consistent);
	}

	public TaskHandle execute(Runnable task, Duration initialDelay, Duration frequency, boolean consistent) {
		TaskHandle handle = new TaskHandle(task, initialDelay, frequency, consistent);
		handle.theRemove = theTaskQueue.add(handle, false);
		return handle;
	}

	public boolean isExecuting() {
		return isRunning || shouldRun;
	}

	private void start() {
		theMainRunner.accept(this::execute);
	}

	void execute() {
		while (isRunning) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {}
		}
		Time[] minNextRun = new Time[1];
		Time now = theClock.now(true);
		while (shouldRun) {
			minNextRun[0] = null;
			Time fNow = now;
			theTaskQueue.forEach(handle -> {
				if (handle.shouldExecute(fNow, minNextRun)) {
					if (handle.isOffloaded())
						if (!theAccessoryRunner.apply(handle::execute))
							minNextRun[0] = Time.ZERO;
					else
						handle.execute();
				}
			});
			now = theClock.now(true);
			if (minNextRun[0] != null && minNextRun[0].compareTo(now) > 0) {
				Time sleepTime = minNextRun[0].minus(now);
				try {
					if (sleepTime.millis >= 5)
						Thread.sleep(sleepTime.millis);
					else
						Thread.sleep(0, (int) sleepTime.nanos);
				} catch (InterruptedException e) {}
			}
		}
		isRunning = false;
	}
}

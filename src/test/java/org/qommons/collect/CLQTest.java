package org.qommons.collect;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.BreakpointHere;
import org.qommons.TestHelper;

/**
 * I wrote this to compare performance of {@link ConcurrentLinkedQueue} against {@link ListenerList}
 * 
 * @see ListenerListTest
 */
public class CLQTest {
	@Test
	public void threadTest() {
		TestHelper.Testing testing = TestHelper.createTester(ThreadTester.class)//
			.withRandomCases(5).withFailurePersistence(false).withMaxFailures(1).withMaxTotalDuration(Duration.ofMinutes(1));
		System.out.println(testing.execute());
	}

	/**
	 * <p>
	 * This tester spawns several threads that each execute several million actions of various types against a {@link ListenerList}
	 * concurrently. Occasionally, all threads will stop and wait for a check operation which ensures that the list contains all the values
	 * which have been added to it and have not been removed.
	 * </p>
	 * 
	 * <p>
	 * This class is designed to ensure that {@link ListenerList} is immune to deadlocks and data loss.
	 * </p>
	 */
	public static class ThreadTester implements TestHelper.Testable {
		private static final int THREAD_COUNT = 20;
		private static final int TEST_ACTIONS = 100_000;

		private final ConcurrentLinkedQueue<Integer> list = new ConcurrentLinkedQueue<>();
		private final ConcurrentHashMap<Integer, Integer> copy = new ConcurrentHashMap<>();
		private final AtomicInteger size = new AtomicInteger();

		private final int[] threadOperations = new int[THREAD_COUNT];
		private final boolean[] threadsPaused = new boolean[THREAD_COUNT];
		private final AtomicBoolean isChecking = new AtomicBoolean();
		private final AtomicLong operations = new AtomicLong();
		private final AtomicInteger checks = new AtomicInteger();

		private volatile Throwable error;

		@Override
		public void accept(TestHelper helper) {
			boolean[] threadsComplete = new boolean[THREAD_COUNT];
			Thread[] threads = new Thread[THREAD_COUNT];
			TestHelper[] forks = new TestHelper[THREAD_COUNT];
			for (int i = 0; i < THREAD_COUNT; i++) {
				TestHelper fork = helper.fork();
				forks[i] = fork;
				int threadIndex = i;
				threads[i] = new Thread(() -> {
					try {
						testThreadExec(fork, threadIndex);
					} catch (RuntimeException | Error e) {
						error = e;
					} finally {
						threadsPaused[threadIndex] = true;
						threadsComplete[threadIndex] = true;
					}
				}, "ListenerList Tester " + i);
			}
			for (int i = 0; i < THREAD_COUNT; i++) {
				threads[i].start();
			}

			// Now wait for it all to finish
			long[] lastDiff = new long[] { System.currentTimeMillis() };
			int[] threadOpsCopy = new int[THREAD_COUNT];
			long totalExpected = THREAD_COUNT * 1L * TEST_ACTIONS;
			int[] lastProgress = new int[1];
			waitForAll(threadsComplete, true, () -> {
				long now = System.currentTimeMillis();
				boolean diff = false;
				for (int i = 0; i < THREAD_COUNT; i++) {
					if (threadOperations[i] != threadOpsCopy[i]) {
						diff = true;
						threadOpsCopy[i] = threadOperations[i];
					}
				}
				if (diff)
					lastDiff[0] = now;
				else if (now - lastDiff[0] >= 1000) {
					if (error != null)
						return;
					System.out.println("\nDeadlock (or breakpoint) detected");
					if (!BreakpointHere.breakpoint()) {
						for (int i = 0; i < THREAD_COUNT; i++) {
							Exception e = new Exception("Stack Trace");
							e.setStackTrace(threads[i].getStackTrace());
							e.printStackTrace();
						}
						System.exit(0);
					}
				}
				int progress = (int) Math.round((operations.get() + checks.get()) * 100.0 / totalExpected);
				if (progress > lastProgress[0]) {
					while (progress > lastProgress[0]) {
						lastProgress[0]++;
						if (lastProgress[0] % 10 == 0)
							System.out.print(lastProgress[0] + "%");
						else
							System.out.print('.');
					}
					System.out.flush();
				}
			});

			if (error instanceof RuntimeException) {
				throw (RuntimeException) error;
			} else if (error != null)
				throw (Error) error;
			System.out.println("\nSuccess with " + operations + " operations and " + checks + " checks");
		}

		private static void waitForAll(boolean[] threadStates, boolean requiredState, Runnable whileWaiting) {
			boolean allThere;
			do {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {}

				allThere = true;
				for (int i = 0; allThere && i < THREAD_COUNT; i++) {
					if (threadStates[i] != requiredState)
						allThere = false;
				}
				if (!allThere && whileWaiting != null)
					whileWaiting.run();
			} while (!allThere);
		}

		private void testThreadExec(TestHelper fork, int threadIndex) {
			for (int i = 0; i < TEST_ACTIONS && error == null; i++) {
				fork.createAction()//
					.or(10, () -> add(fork, threadIndex))//
					.or(2, () -> removeRandom(fork, threadIndex))//
					.or(2, () -> poll(fork, threadIndex))//
					.or(2, () -> iterate(fork, threadIndex))//
					.or(.001, () -> check(fork, threadIndex))//
					.execute(null);
				threadOperations[threadIndex]++;
			}
		}

		private void add(TestHelper fork, int threadIndex) {
			waitForCheck(threadIndex);
			Integer value = fork.getAnyInt();
			list.add(value);
			setRandom(value, fork);
		}

		private void removeRandom(TestHelper fork, int threadIndex) {
			waitForCheck(threadIndex);
			setRandom(null, fork);
		}

		private void poll(TestHelper fork, int threadIndex) {
			waitForCheck(threadIndex);
			Integer removed = list.poll();
			if (removed != null)
				valueRemoved(removed);
			operations.getAndIncrement();
		}

		private void iterate(TestHelper fork, int threadIndex) {
			waitForCheck(threadIndex);
			list.forEach(integer -> {});
			operations.getAndIncrement();
		}

		private void waitForCheck(int threadIndex) {
			while (isChecking.get()) {
				threadsPaused[threadIndex] = true;
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {}
			}
			threadsPaused[threadIndex] = false;
		}

		private void setRandom(Integer value, TestHelper fork) {
			boolean removeOne;
			if (value != null) {
				valueAdded(value);
				removeOne = fork.getBoolean(size.get() * 1.0 / 1000);
			} else
				removeOne = true;
			if (removeOne) {
				value = list.poll();
				if (value != null)
					valueRemoved(value);
			}

			operations.getAndIncrement();
		}

		private void valueAdded(Integer value) {
			copy.compute(value, (v, count) -> {
				if (count == null)
					return 1;
				else if (count.intValue() == -1)
					return null;
				else
					return count + 1;
			});
			size.getAndIncrement();
		}

		private void valueRemoved(Integer value) {
			copy.compute(value, (v, count) -> {
				if (count == null)
					return -1;
				else if (count.intValue() == 1)
					return null;
				else
					return count - 1;
			});
			size.getAndDecrement();
		}

		private void check(TestHelper fork, int threadIndex) {
			threadsPaused[threadIndex] = true;
			while (!isChecking.compareAndSet(false, true)) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {}
			}
			try {
				waitForAll(threadsPaused, true, null);

				HashMap<Integer, Integer> copyCopy = new HashMap<>(copy);
				int[] found = new int[1];
				list.forEach(v -> {
					found[0]++;
					Integer count = copyCopy.get(v);
					Assert.assertNotNull(count);
					Assert.assertTrue(count.intValue() > 0);
					if (count.intValue() == 1)
						copyCopy.remove(v);
					else
						copyCopy.put(v, count - 1);
				});
				int sz = size.get();
				if (sz != list.size())
					Assert.assertEquals(sz, list.size());
				if (sz != found[0])
					Assert.assertEquals(sz, found[0]);
				Assert.assertTrue(copyCopy.isEmpty());
				checks.getAndIncrement();
				threadsPaused[threadIndex] = false;
			} finally {
				isChecking.set(false);
			}
		}
	}
}

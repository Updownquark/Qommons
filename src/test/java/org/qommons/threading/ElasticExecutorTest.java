package org.qommons.threading;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.QommonsUtils;

/** Tests the {@link ElasticExecutor} */
public class ElasticExecutorTest {
	/** Test the executor's integrity */
	@Test
	public void testElasticExecutor() {
		test(new QommonsExecutor());
	}

	/**
	 * <p>
	 * A parallel test against Java's {@link ThreadPoolExecutor}, whose purpose is to compare the performance of {@link ElasticExecutor}
	 * against Java's native utilities.
	 * </p>
	 * <p>
	 * This method's @{@link Test} annotation is commented out because java's executor is so slow compared to {@link ElasticExecutor}. I
	 * tuned the test so that it runs for about a quarter second on my system with {@link ElasticExecutor}, but it takes java's executor
	 * more than a second.
	 * </p>
	 */
	// @Test
	public void testJavaExecutor() {
		test(new JavaExecutor());
	}

	private static void test(TestExecutor executor) {
		final AtomicLong value = new AtomicLong();
		final AtomicInteger count = new AtomicInteger();
		Random random = new Random();
		int[] ops = new int[2_000];
		long asyncTime = 0, queueTime = 0;
		int outerOps = 500;
		int progressPrint = outerOps / 10;
		int dotPrint = progressPrint / 10;
		for (int outer = 0; outer < outerOps; outer++) {
			long expected = 0;
			for (int i = 0; i < ops.length; i++) {
				ops[i] = random.nextInt(1000) - 500;
				expected += ops[i];
			}

			value.set(0);
			count.set(0);

			long start = System.currentTimeMillis();
			// Now do it asynchronously
			for (int i = 0; i < ops.length; i++) {
				int index = i;
				executor.execute(() -> {
					value.addAndGet(ops[index]);
					count.getAndIncrement();
				});
			}
			long queueEnd = System.currentTimeMillis();
			queueTime += (queueEnd - start);
			long end = executor.waitWhileActive(ops.length, expected, count, value, start);
			end = System.currentTimeMillis();
			asyncTime += end - start;
			if (count.get() != ops.length)
				Assert.assertEquals(ops.length, count.get());
			if (expected != value.get())
				Assert.assertEquals(expected, value.get());

			if (outer % progressPrint == progressPrint - 1)
				System.out.print((outer / progressPrint + 1) + "%");
			else if (outer % dotPrint == 0)
				System.out.print('.');
		}
		System.out.println("\nAsync execution " + QommonsUtils.printTimeLength(asyncTime) + ", " + QommonsUtils.printTimeLength(queueTime)
			+ " queue time");
	}

	interface TestExecutor {
		void execute(Runnable task);

		long waitWhileActive(int expectedCount, long expectedValue, AtomicInteger count, AtomicLong value, long start);
	}

	static class QommonsExecutor implements TestExecutor {
		private final ElasticExecutor<Runnable> executor = new ElasticExecutor<>("Executor Test", () -> Runnable::run)//
			.setMaxThreadCount(4)//
			.setUsedThreadLifetime(1000);

		@Override
		public void execute(Runnable task) {
			executor.execute(task);
		}

		@Override
		public long waitWhileActive(int expectedCount, long expectedValue, AtomicInteger count, AtomicLong value, long start) {
			boolean finished = executor.waitWhileActive(1000);
			long now = System.currentTimeMillis();
			if (now - start > 250) {
				int countNow = count.get();
				String countCorrect = (expectedCount == countNow) ? "correct" : (countNow + " of " + expectedCount);
				String valueCorrect = (expectedValue == value.get()) ? "correct" : "incorrect";
				if (!finished)
					throw new AssertionError("Executor seems deadlocked (count " + count.get() + ", value " + valueCorrect + ")");
				else
					throw new AssertionError("Executor took too long (>" + QommonsUtils.printTimeLength(System.currentTimeMillis() - start)
						+ ", count " + countCorrect + ", value " + valueCorrect + ")");
			}
			return now;
		}
	}

	static class JavaExecutor implements TestExecutor {
		private final ExecutorService executor = new ThreadPoolExecutor(4, 4, 100, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

		@Override
		public void execute(Runnable task) {
			executor.execute(task);
		}

		@Override
		public long waitWhileActive(int expectedCount, long expectedValue, AtomicInteger count, AtomicLong value, long start) {
			// Java's executor does not have such a method. We just need to watch the operation count directly.
			while (count.get() != expectedCount) {
				try {
					Thread.sleep(2);
				} catch (InterruptedException e) {
				}
			}
			return System.currentTimeMillis();
		}
	}
}

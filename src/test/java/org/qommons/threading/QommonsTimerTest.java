package org.qommons.threading;

import java.time.Duration;

import org.junit.Assert;
import org.junit.Test;

/** Tests {@link QommonsTimer} */
public class QommonsTimerTest {
	/** Tests {@link QommonsTimer#doAfterInactivity(Object, Runnable, Duration)} */
	@Test
	public void testInactivity() {
		QommonsTimer timer = QommonsTimer.getCommonInstance();
		boolean[] b = new boolean[1];
		String key = "key";
		Runnable task = () -> {
			b[0] = false;
		};
		Duration d = Duration.ofMillis(250);

		System.out.println("First");
		b[0] = true;
		timer.doAfterInactivity(key, task, d);
		Assert.assertTrue(b[0]);
		wait(200);
		System.out.println("Checking");
		Assert.assertTrue(b[0]);
		wait(100);
		System.out.println("Test");
		Assert.assertFalse(b[0]);

		wait(300);

		System.out.println("Second");
		b[0] = true;
		timer.doAfterInactivity(key, task, d);
		Assert.assertTrue(b[0]);
		wait(200);
		System.out.println("Checking");
		Assert.assertTrue(b[0]);
		wait(100);
		System.out.println("Test");
		Assert.assertFalse(b[0]);
	}

	private static void wait(int millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			throw new IllegalStateException(e);
		}
	}
}

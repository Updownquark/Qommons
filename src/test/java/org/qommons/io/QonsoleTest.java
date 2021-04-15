package org.qommons.io;

import java.io.IOException;
import java.io.Reader;
import java.time.Duration;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.TestHelper;
import org.qommons.TestHelper.Testable;

/** Tests {@link Qonsole} */
public class QonsoleTest {
	/** Random {@link Qonsole} test */
	@SuppressWarnings("static-method")
	@Test
	public void testQonsole() {
		TestHelper.createTester(QTestable.class).revisitKnownFailures(true).withDebug(true).withFailurePersistence(true)
			.withMaxCaseDuration(Duration.ofSeconds(10)).withRandomCases(50).execute().throwErrorIfFailed();
	}

	static class QTestable implements Testable {
		@Override
		public void accept(TestHelper helper) {
			BufferedReaderWriter rw = new BufferedReaderWriter();
			int[] testValues = new int[2];
			try (Reader reader = rw.read();
				Qonsole qonsole = new Qonsole("Test", reader, ":", () -> false)//
				.addPlugin("test0", content -> {
					int line = indexOf(content, '\n');
					String firstLine = line < 0 ? content.toString() : content.subSequence(0, line).toString();
					switch (firstLine) {
					case "add":
						if (line > 0) {
							testValues[0] += Integer.parseInt(content.subSequence(line + 1, content.length()).toString());
							return true;
						} else
							return false;
					case "sub":
						if (line > 0) {
							testValues[0] -= Integer.parseInt(content.subSequence(line + 1, content.length()).toString());
							return true;
						} else
							return false;
					default:
						throw new IllegalArgumentException("Unrecognized command: " + content);
					}
				}).addPlugin("test1", content -> {
					int line = indexOf(content, '\n');
					String firstLine = line < 0 ? content.toString() : content.subSequence(0, line).toString();
					switch (firstLine) {
					case "add":
						if (line > 0) {
							testValues[1] += Integer.parseInt(content.subSequence(line + 1, content.length()).toString());
							return true;
						} else
							return false;
					case "sub":
						if (line > 0) {
							testValues[1] -= Integer.parseInt(content.subSequence(line + 1, content.length()).toString());
							return true;
						} else
							return false;
					default:
						throw new IllegalArgumentException("Unrecognized command: " + content);
					}
					})) {
				int[] expectedValues = new int[testValues.length];

				for (int i = 0; i < 50; i++) {
					int whichValue = helper.getBoolean() ? 0 : 1;
					boolean add = helper.getBoolean();
					int value = helper.getAnyInt();
					try {
						rw.write().append(//
							"test" + whichValue + ":" + (add ? "add" : "sub") + "\n");
						rw.write().append(String.valueOf(value) + "\n");
					} catch (IOException e) {
						Assert.assertFalse(true);
					}
					expectedValues[whichValue] += (add ? value : -value);
					try {
						Thread.sleep(2);
					} catch (InterruptedException e) {
					}
					Assert.assertArrayEquals(expectedValues, testValues);
				}
				System.out.print("");
			} catch (IOException e) {
				Assert.assertFalse(true);
			}
		}
	}

	static int indexOf(CharSequence seq, char ch) {
		for (int i = 0; i < seq.length(); i++) {
			if (seq.charAt(i) == ch)
				return i;
		}
		return -1;
	}
}

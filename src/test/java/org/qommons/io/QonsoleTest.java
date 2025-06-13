package org.qommons.io;

import java.io.IOException;
import java.io.Reader;
import java.time.Duration;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.testing.TestHelper;
import org.qommons.testing.TestHelper.Testable;

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
			CircularCharBuffer rw = new CircularCharBuffer(-1);
			int[] testValues = new int[2];
			try (Reader reader = rw.asDeletingReader(false); // Keep the Qonsole alive
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

				// The Qonsole is reading things off of a different thread, so we need to add to the buffer all at once
				StringBuilder str = new StringBuilder();
				for (int i = 0; i < 50; i++) {
					int whichValue = helper.getBoolean() ? 0 : 1;
					boolean add = helper.getBoolean();
					int value;
					do {
						value = helper.getAnyInt();
					} while (value == 0);

					str.append("test").append(whichValue).append(':').append(add ? "add" : "sub").append('\n');
					str.append(value).append('\n');
					int preValue = testValues[whichValue];
					rw.append(str);
					expectedValues[whichValue] += (add ? value : -value);
					/* Give Qonsole a bit to read, parse, and act
					 * The GC (I guess) sometimes causes hitches, so most of the time this only takes 1 sleep,
					 * but other times it takes 30 or more
					 */
					int tries;
					for (tries = 0; tries < 100 && testValues[whichValue] == preValue; tries++) {
						try {
							Thread.sleep(2);
						} catch (InterruptedException e) {
						}
					}
					// System.out.println("Tries=" + tries);
					Assert.assertArrayEquals(expectedValues, testValues);
					str.setLength(0);
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

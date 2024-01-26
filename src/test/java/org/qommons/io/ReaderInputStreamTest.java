package org.qommons.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Duration;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.testing.TestHelper;
import org.qommons.testing.TestHelper.Testable;

/** Tests {@link ReaderInputStream} */
public class ReaderInputStreamTest {
	/** Random {@link ReaderInputStream} test */
	@SuppressWarnings("static-method")
	@Test
	public void testReaderInputStream() {
		TestHelper.createTester(RISTestable.class).revisitKnownFailures(true).withDebug(true).withFailurePersistence(true)
			.withMaxCaseDuration(Duration.ofSeconds(1)).withRandomCases(100).execute();
	}

	static class RISTestable implements Testable {
		@SuppressWarnings("null")
		@Override
		public void accept(TestHelper helper) {
			String chars = helper.getAlphaNumericString(1000, 10000);
			int charSetIdx = helper.getInt(0, Charset.availableCharsets().size());
			Charset charSet = null;
			while (true) {
				for (Charset cs : Charset.availableCharsets().values()) {
					if (charSetIdx == 0) {
						charSet = cs;
						break;
					}
					charSetIdx--;
				}
				if (!charSet.canEncode() || !charSet.newEncoder().canEncode(chars))
					continue;
				break;
			}
			ByteBuffer byteBuffer = charSet.encode(chars);
			byte[] bytes = byteBuffer.array();
			ByteArrayInputStream in = new ByteArrayInputStream(bytes);
			try (InputStreamReader streamReader = new InputStreamReader(in, charSet); //
				ReaderInputStream readerStream = new ReaderInputStream(streamReader, charSet.newEncoder())) {
				byte[] result = new byte[bytes.length];
				int read;
				try {
					read = readerStream.read(result);
				} catch (IOException e) {
					Assert.assertFalse(true);
					return;
				}
				Assert.assertEquals(bytes.length, read);
				Assert.assertArrayEquals(bytes, result);
			} catch (IOException e) {
				Assert.assertFalse(true);
			}
		}
	}
}

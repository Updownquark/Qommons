package org.qommons;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.StringUtils.BinaryDataEncoder;

/** Tests for some {@link StringUtils} functionality */
public class StringUtilsTest {
	private static final String TEST_TEXT_1 = "Man is distinguished, not only by his reason, but by this singular passion from other animals,"
		+ " which is a lust of the mind, that by a perseverance of delight in the continued and indefatigable generation of knowledge,"
		+ " exceeds the short vehemence of any carnal pleasure.";

	private static final String TEST_TEXT_2 = "Lorem ipsum dolor sit amet, consectetur adipiscing elit,"
		+ " sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam,"
		+ " quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat."
		+ " Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur."
		+ " Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";

	private static final List<String> TEST_TEXTS = QommonsUtils.unmodifiableCopy(TEST_TEXT_1, TEST_TEXT_2);

	/** Tests {@link StringUtils#encodeHex()} */
	@Test
	public void testHexEncoding() {
		testEncoding(StringUtils.encodeHex());
	}

	/** Tests {@link StringUtils#encodeBase64()} */
	@Test
	public void testBase64Encoding() {
		testEncoding(StringUtils.encodeBase64());
	}

	private static void testEncoding(BinaryDataEncoder encoder) {
		for (String text : TEST_TEXTS)
			testEncoding(text, encoder);
	}

	private static void testEncoding(String text, BinaryDataEncoder encoder) {
		try {
			String encoded = encoder.format(text.getBytes("UTF-8"));
			String decoded = new String(encoder.parse(encoded), "UTF-8");
			Assert.assertEquals(text, decoded);

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (InputStream reader = encoder.parseAsStream(encoded)) {
				int read = reader.read();
				while (read >= 0) {
					bytes.write(read);
					read = reader.read();
				}
			} catch (IOException e) {
				throw new IllegalStateException("Should not happen", e);
			}
			decoded = new String(bytes.toByteArray(), "UTF-8");
			Assert.assertEquals(text, decoded);
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Tests {@link StringUtils#toRomanNumeral(int)} */
	@Test
	public void testRomanNumerals() {
		Assert.assertEquals("I", StringUtils.toRomanNumeral(1));
		Assert.assertEquals("IV", StringUtils.toRomanNumeral(4));
		Assert.assertEquals("VIII", StringUtils.toRomanNumeral(8));
		Assert.assertEquals("IX", StringUtils.toRomanNumeral(9));
		Assert.assertEquals("DCCCXLVII", StringUtils.toRomanNumeral(847));
		Assert.assertEquals("MCMLXXXVII", StringUtils.toRomanNumeral(1987));
		Assert.assertEquals("MMXXIV", StringUtils.toRomanNumeral(2024));
		Assert.assertEquals("DLXXIII", StringUtils.toRomanNumeral(573));
		Assert.assertEquals("CMXLII", StringUtils.toRomanNumeral(942));
		Assert.assertEquals("DCCXXXVI", StringUtils.toRomanNumeral(736));
	}
}

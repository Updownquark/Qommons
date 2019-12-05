package org.qommons;

import java.io.UnsupportedEncodingException;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.StringUtils.BinaryDataEncoder;

/** Tests for some {@link StringUtils} functionality */
public class StringUtilsTest {
	private String theText = "Man is distinguished, not only by his reason, but by this singular passion from other animals,"
		+ " which is a lust of the mind, that by a perseverance of delight in the continued and indefatigable generation of knowledge,"
		+ " exceeds the short vehemence of any carnal pleasure.";

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

	private void testEncoding(BinaryDataEncoder encoder) {
		try {
			String encoded = encoder.format(theText.getBytes("UTF-8"));
			String testText = new String(encoder.parse(encoded), "UTF-8");
			Assert.assertEquals(theText, testText);
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}
}

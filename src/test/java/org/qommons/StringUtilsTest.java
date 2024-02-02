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

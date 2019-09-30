package org.qommons;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.io.CsvParser;
import org.qommons.io.TextParseException;

public class CsvParserTest {
	@Test
	public void testSimple() throws TextParseException, IOException {
		String csv = "This is a test, a very, very simple test\n"//
			+ "It's so marvelously, ridiculously simple, but it does have multiple lines";
		String[] columns = new String[3];
		CsvParser parser = new CsvParser(new StringReader(csv), ',');

		Assert.assertTrue("No line", parser.parseNextLine(columns));
		Assert.assertEquals("This is a test", columns[0]);
		Assert.assertEquals(" a very", columns[1]);
		Assert.assertEquals(" very simple test", columns[2]);

		Assert.assertTrue("No line", parser.parseNextLine(columns));
		Assert.assertEquals("It's so marvelously", columns[0]);
		Assert.assertEquals(" ridiculously simple", columns[1]);
		Assert.assertEquals(" but it does have multiple lines", columns[2]);

		Assert.assertFalse("Should not have more content", parser.parseNextLine(columns));
	}

	@Test
	public void testValidQuotes() throws TextParseException, IOException {
		String csv = "This is a test, a somewhat more complicated test\n"//
			+ "It uses well-placed quotes,\" and by \"\"well-placed\"\",\n I mean it should parse without exception.\"\n"//
			+ "This line also uses quotes,\" but it doesn't have a comma (,) or a newline character.\"\n"//
			+ "If it doesn't parse, the " + CsvParser.class.getSimpleName() + " class is broken.";
		String[] columns = new String[2];
		CsvParser parser = new CsvParser(new StringReader(csv), ',');

		Assert.assertTrue("No line", parser.parseNextLine(columns));
		Assert.assertEquals("This is a test", columns[0]);
		Assert.assertEquals(" a somewhat more complicated test", columns[1]);

		Assert.assertTrue("No line", parser.parseNextLine(columns));
		Assert.assertEquals("It uses well-placed quotes", columns[0]);
		Assert.assertEquals(" and by \"well-placed\",\n I mean it should parse without exception.", columns[1]);

		Assert.assertTrue("No line", parser.parseNextLine(columns));
		Assert.assertEquals("This line also uses quotes", columns[0]);
		Assert.assertEquals(" but it doesn't have a comma (,) or a newline character.", columns[1]);

		Assert.assertTrue("No line", parser.parseNextLine(columns));
		Assert.assertEquals("If it doesn't parse", columns[0]);
		Assert.assertEquals(" the " + CsvParser.class.getSimpleName() + " class is broken.", columns[1]);

		Assert.assertFalse("Should not have more content", parser.parseNextLine(columns));
	}

	@Test
	public void testErrorCases() throws TextParseException, IOException {
		String csv = "This is a test that is designed to fail, specifically by throwing exceptions."//
			+ "The first error is that this csv file should have been separated by a newline char, but isn't";
		String[] columns = new String[2];
		CsvParser parser = new CsvParser(new StringReader(csv), ',');

		try {
			parser.parseNextLine(columns);
			Assert.assertTrue("Should have thrown an exception", false);
		} catch (TextParseException e) {}

		csv = "This is a similar failure-bound test,\" differing only by quotes.\""//
			+ "The first error is that this csv file should have been separated by a newline char, but isn't";
		parser = new CsvParser(new StringReader(csv), ',');
		try {
			parser.parseNextLine(columns);
			Assert.assertTrue("Should have thrown an exception", false);
		} catch (TextParseException e) {}
	}

	@Test
	public void testBlankLines() throws TextParseException, IOException {
		String csv = "This is a test that uses blank, empty lines.\n"//
			+ "\n"//
			+ "It should ignore both empty lines; the one between the content-filled lines, and the terminal one.";
		String[] columns = new String[2];
		CsvParser parser = new CsvParser(new StringReader(csv), ',');

		Assert.assertTrue("No line", parser.parseNextLine(columns));
		Assert.assertEquals("This is a test that uses blank", columns[0]);
		Assert.assertEquals(" empty lines.", columns[1]);

		Assert.assertTrue("No line", parser.parseNextLine(columns));
		Assert.assertEquals("It should ignore both empty lines; the one between the content-filled lines", columns[0]);
		Assert.assertEquals(" and the terminal one.", columns[1]);

		Assert.assertFalse("Empty line parsed as content", parser.parseNextLine(columns));
	}
}

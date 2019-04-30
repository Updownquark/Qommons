package org.qommons.io;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple CSV parser. Attempts to be as tolerant and flexible as possible, while adhering closely to the RFC 4180 standard. Some
 * differences from the standard include:
 * <ul>
 * <li>The delimiter character is configurable</li>
 * <li>Carriage returns (\r, ASCII code 13) are not required at the end of lines. Newline characters (\n, ASCII code 10) are required.</li>
 * <li>Blank lines are allowed and ignored anywhere in the file (the {@link #getPassedBlankLines()} method may be used to detect when this
 * happens) except within quoted columns, in which case they appear as part of the column value</li>
 * <li>Un-quoted columns may include any character that is not the delimiter or the newline character</li>
 * <li>Quoted columns may include any character at all (double-quote characters (") must, of course, be escaped by using a double
 * double-quote (""))</li>
 * <li>Depending on how it is called, the parser may or may not expect the same number of columns per line. The {@link #parseNextLine()}
 * method parses a line regardless of the number of columns and returns them. The {@link #parseNextLine(String[])} method takes an array of
 * columns and populates them with the columns found on the current line. If the number of columns found differs from the length of the
 * input array, an exception is thrown.</li>
 * </ul>
 * <p>
 * As per the standard, double double-quotes ("") within quoted columns are interpreted as a single double-quote character ("). But within
 * un-quoted columns, double-quote characters are not special, so double double-quotes would be interpreted as-is ("").
 * </p>
 * <p>
 * This class does not deal specifically with a header, which may be parsed the same as any other line.
 * </p>
 */
public class CsvParser {
	final Reader theReader;
	final char theDelimiter;
	int theTabColumnOffset;
	private final CsvParseState theParseState;
	private int thePassedBlankLines;
	private int theLastLineNumber;
	private int theLastLineOffset;

	/**
	 * @param reader The reader to parse CSV data from
	 * @param delimiter The delimiter character for the CSV file
	 */
	public CsvParser(Reader reader, char delimiter) {
		theReader = reader;
		theDelimiter = delimiter;
		theTabColumnOffset = 1;
		theParseState = new CsvParseState();
	}

	/** @return The delimiter character used to parse CSV */
	public char getDelimiter() {
		return theDelimiter;
	}

	/**
	 * @param tabOffset The number of places to count for tab offsets (for the {@link TextParseException#getColumnNumber() column numbers}
	 *        in thrown exceptions)
	 * @return This parser
	 */
	public CsvParser setTabColumnOffset(int tabOffset) {
		theTabColumnOffset = tabOffset;
		return this;
	}

	/**
	 * Parses all columns in the next line
	 * 
	 * @return The parsed column values for the line, or null if there is no more content in the file
	 * @throws IOException If the reader throws an exception
	 * @throws TextParseException If there is an error on the next line the CSV file
	 */
	public String[] parseNextLine() throws IOException, TextParseException {
		thePassedBlankLines = 0;
		List<String> columns = new ArrayList<>();
		String value = theParseState.parseColumn();
		while (value.length() == 0 && theParseState.getLastTerminal() == CsvValueTerminal.LINE_END) {
			thePassedBlankLines++;
			value = theParseState.parseColumn(); // Move past any blank lines
		}
		theLastLineNumber = theParseState.theLineNumber;
		theLastLineOffset = theParseState.theOffset;
		if (value.length() == 0 && theParseState.getLastTerminal() == CsvValueTerminal.FILE_END)
			return null;

		columns.add(value);
		do {
			columns.add(theParseState.parseColumn());
		} while (theParseState.getLastTerminal() == CsvValueTerminal.COLUMN_END);
		return columns.toArray(new String[columns.size()]);
	}

	/**
	 * Parses the next line in the file, asserting that the number of columns is equal to the length of the <code>columns</code> array
	 * 
	 * @param columns The columns array to populate with the parsed column values from the next line of the file
	 * @return True if content was parsed, or false if there is no more content in the file
	 * @throws IOException If the reader throws an exception
	 * @throws TextParseException If there is an error on the next line of the CSV file or the number of columns on the line is different
	 *         than <code>columns.length</code>
	 */
	public boolean parseNextLine(String[] columns) throws IOException, TextParseException {
		thePassedBlankLines = 0;
		String value = theParseState.parseColumn();
		while (value.length() == 0 && theParseState.getLastTerminal() == CsvValueTerminal.LINE_END) {
			thePassedBlankLines++;
			value = theParseState.parseColumn(); // Move past any blank lines
		}
		theLastLineNumber = theParseState.theLineNumber;
		theLastLineOffset = theParseState.theOffset;
		if (value.length() == 0 && theParseState.getLastTerminal() == CsvValueTerminal.FILE_END)
			return false;

		columns[0] = value;
		int c = 1;
		do {
			columns[c++] = theParseState.parseColumn();
		} while (c < columns.length && theParseState.getLastTerminal() == CsvValueTerminal.COLUMN_END);
		if (c < columns.length)
			theParseState.throwParseException(columns.length + " columns expected, but only " + c + " encountered");
		else if (theParseState.getLastTerminal() == CsvValueTerminal.COLUMN_END)
			theParseState.throwParseException("More than the expected " + columns.length + " columns encountered");
		return true;
	}

	/** @return The number of blank lines that were ignored prior to the most recently-parsed line of the file */
	public int getPassedBlankLines() {
		return thePassedBlankLines;
	}

	/** @return The line number that was most recently parsed */
	public int getLastLineNumber() {
		return theLastLineNumber;
	}

	/** @return The overall character offset of the start of the line that was most recently parsed */
	public int getLastLineOffset() {
		return theLastLineOffset;
	}

	/** @return The line number of the line about to be parsed (or the last line parsed if there are no more lines) */
	public int getCurrentLineNumber() {
		return theParseState.theLineNumber;
	}

	/**
	 * @return The overall character offset of the start of the line about to be parsed (or of the end of the file if there are no more
	 *         lines)
	 */
	public int getCurrentOffset() {
		return theParseState.theOffset;
	}

	static enum CsvValueTerminal {
		COLUMN_END, LINE_END, FILE_END;
	}

	class CsvParseState {
		private final StringBuilder theValue;
		private int[] isQuoted;
		private int theOffset;
		private int theLineNumber;
		private int theColumnNumber; // Char columns, not CSV columns here
		private CsvValueTerminal theLastTerminal;

		CsvParseState() {
			theValue = new StringBuilder();
		}

		<T> T throwParseException(String message) throws TextParseException {
			throw new TextParseException(message, theOffset, theLineNumber, theColumnNumber);
		}

		CsvValueTerminal getLastTerminal() {
			return theLastTerminal;
		}

		String parseColumn() throws IOException, TextParseException {
			isQuoted = null;
			theLastTerminal = null;
			int c = readContentChar(true);
			while (c >= 0) {
				theValue.append((char) c);
				c = readContentChar(false);
			}
			String column = theValue.toString();
			theValue.setLength(0);
			return column;
		}

		private int readContentChar(boolean columnStart) throws IOException, TextParseException {
			int c = readStreamChar();
			return interpretStreamChar(c, columnStart);
		}

		private int interpretStreamChar(int c, boolean columnStart) throws IOException, TextParseException {
			theOffset++;
			if (c == '\n') {
				theLineNumber++;
				theColumnNumber = 0;
			} else
				theColumnNumber++;

			if (c == '"') {
				if (columnStart) { // Begin quote
					isQuoted = new int[] { theOffset, theLineNumber, theColumnNumber };
					return readContentChar(false);
				} else if (isQuoted != null) {
					c = readStreamChar();
					if (c == '"') { // Double double-quotes within a quoted column is an escaped double-quote
						theColumnNumber++;
						return c;
					} else { // End quote
						isQuoted = null;
						c = interpretStreamChar(c, false);
						if (c >= 0)
							return throwParseException(
								"Unexpected content after end quote: '" + (char) c + "'. End quotes must terminate the column.");
						return c;
					}
				} else
					return c;
			} else if (c < 0) { // File end
				if (isQuoted != null)
					throw new TextParseException("Unmatched quote", isQuoted[0], isQuoted[1], isQuoted[2]);
				theLastTerminal = CsvValueTerminal.FILE_END;
				return -1;
			} else if (c == '\t' && theTabColumnOffset != 1) { // Tab
				int mod = (theColumnNumber - 1) % theTabColumnOffset;
				theColumnNumber += (theTabColumnOffset - mod);
			} else if (isQuoted != null) {
				// Inside a quote, so other would-be terminals will just be content like anything else
			} else if (c == '\n' || c == '\r') { // Line end
				theLastTerminal = CsvValueTerminal.LINE_END;
				return -1;
			} else if (c == theDelimiter) { // Column end
				theLastTerminal = CsvValueTerminal.COLUMN_END;
				return -1;
			}
			return c;
		}

		private int readStreamChar() throws IOException {
			int c = theReader.read();
			if (isQuoted == null) {
				while (c == '\r') // Ignore stupid DOS CR characters except in quotes
					c = theReader.read();
			}
			return c;
		}

		@Override
		public String toString() {
			return theValue.toString();
		}
	}
}

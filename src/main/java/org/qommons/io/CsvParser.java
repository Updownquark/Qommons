package org.qommons.io;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple CSV parser. Attempts to be as tolerant as possible within the RFC 4180, tolerating quoted values containing any characters
 * (except unescaped double-quotes), doubled double-quotes (escaped double quote character), and backslash-escaped quotes.
 */
public class CsvParser {
	final Reader theReader;
	final char theDelimiter;
	int theTabColumnOffset;
	private final CsvParseState theParseState;

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
	 * Parses all columns in the current line
	 * 
	 * @return The parsed column values
	 * @throws IOException If the reader throws an exception
	 * @throws TextParseException If there is an error in the CSV file
	 */
	public String[] parseNextLine() throws IOException, TextParseException {
		List<String> columns = new ArrayList<>();
		String value = theParseState.parseColumn();
		while (theParseState.getLastTerminal() == CsvValueTerminal.LINE_END)
			value = theParseState.parseColumn(); // Move past any initial blank lines
		if (theParseState.getLastTerminal() == CsvValueTerminal.FILE_END)
			return null;

		columns.add(value);
		do {
			columns.add(theParseState.parseColumn());
		} while (theParseState.getLastTerminal() == CsvValueTerminal.COLUMN_END);
		return columns.toArray(new String[columns.size()]);
	}

	/**
	 * Parses the current line in the file, assuming the number of columns is equal to the length of the <code>columns</code> array
	 * 
	 * @param columns The columns array to populate with the parsed column values
	 * @return The parsed column values
	 * @throws IOException If the reader throws an exception
	 * @throws TextParseException If there is an error in the CSV file
	 */
	public boolean parseNextLine(String[] columns) throws IOException, TextParseException {
		String value = theParseState.parseColumn();
		while (theParseState.getLastTerminal() == CsvValueTerminal.LINE_END)
			value = theParseState.parseColumn(); // Move past any initial blank lines
		if (theParseState.getLastTerminal() == CsvValueTerminal.FILE_END)
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

	static enum CsvValueTerminal {
		COLUMN_END, LINE_END, FILE_END;
	}

	class CsvParseState {
		private final StringBuilder theValue;

		private int[] isQuoted;
		private char theHoldover;
		private int theOffset;
		private int theLineNumber;
		private int theColumnNumber; // Char columns, not CSV columns here
		private CsvValueTerminal theLastTerminal;

		CsvParseState() {
			theValue = new StringBuilder();
		}

		CsvValueTerminal getLastTerminal() {
			return theLastTerminal;
		}

		String parseColumn() throws IOException, TextParseException {
			if (beginColumn()) {
				boolean doNothing = true;
				while (doNothing && readNextChar()) {
					doNothing = true; // Just here so one can step through each character as it's parsed
				}
			}
			if (theValue.length() == 0 && theLastTerminal == CsvValueTerminal.FILE_END)
				return null;
			String column = theValue.toString();
			theValue.setLength(0);
			return column;
		}

		<T> T throwParseException(String message) throws TextParseException {
			throw new TextParseException(message, theOffset, theLineNumber, theColumnNumber);
		}

		private boolean beginColumn() throws IOException, TextParseException {
			theLastTerminal = null;
			isQuoted = null;
			int c = read();
			if (isLineTerminal(c)) {
				return false;
			} else if (c == '"') {
				int[] quotePosition = new int[] { theOffset, theLineNumber, theColumnNumber };
				int moreQuotes = countConsecutiveQuotes();
				for (int i = 0; i < moreQuotes; i += 2)
					theValue.append('"');
				if (moreQuotes % 2 == 0)
					isQuoted = quotePosition;
				return theLastTerminal == null;
			} else if (c == theDelimiter) {
				theLastTerminal = CsvValueTerminal.COLUMN_END;
				return false;
			} else
				theValue.append((char) c);
			return true;
		}

		private boolean readNextChar() throws IOException, TextParseException {
			int c = read();
			if (c == '"') {
				int moreQuotes = countConsecutiveQuotes();
				boolean endQuote = moreQuotes % 2 == 0;
				if (endQuote) {
					if (isQuoted != null) {
						// End of the quoted value. Make sure the next character is the delimiter (or a value terminal).
						c = read();
						if (c == theDelimiter) {
							theLastTerminal = CsvValueTerminal.COLUMN_END;
							return false;
						} else if (isLineTerminal(c))
							return false;
						else
							return throwParseException("Unexpected content after end quote");
					} else
						return throwParseException("Bad placement of quote character");
				}
				for (int i = 0; i < moreQuotes; i += 2)
					theValue.append('"');
			} else if (isQuoted != null) {
				if (c < 0)
					throw new TextParseException("Unmatched quote character", isQuoted[0], isQuoted[1], isQuoted[2]);
				theValue.append((char) c);
				return true;
			} else if (isLineTerminal(c)) {
				return false;
			} else if (c == theDelimiter) {
				theLastTerminal = CsvValueTerminal.COLUMN_END;
				return false;
			} else
				theValue.append((char) c);
			return true;
		}

		private int read() throws IOException {
			if (theHoldover > 0) {
				char holdover = theHoldover;
				theHoldover = 0;
				return holdover;
			}
			theOffset++;
			int c = theReader.read();
			if (c < 0)
				theLastTerminal = CsvValueTerminal.FILE_END;
			else if (c == '\n' || c == '\r') {
				theLineNumber++;
				theColumnNumber = 0;
				// Parse out DOS-style line endings
				int c1 = theReader.read();
				if (c1 < 0)
					theLastTerminal = CsvValueTerminal.FILE_END;
				else if (isQuoted == null && c1 != c && (c1 == '\n' || c1 == '\r')) {
					// Only swallow the extra newline character if we're not quoted
				} else {
					// c1 is a character in the next line
					theHoldover = (char) c1;
				}
				if (isQuoted == null)
					theLastTerminal = CsvValueTerminal.LINE_END;
			} else if (c == '\t' && theTabColumnOffset != 1) {
				int mod = theColumnNumber % theTabColumnOffset;
				theColumnNumber += (theTabColumnOffset - mod);
			} else {
				theColumnNumber++;
			}
			return c;
		}

		private int countConsecutiveQuotes() throws IOException {
			int consecutiveQuotes = 0;
			int c = read();
			while (c == '"') {
				consecutiveQuotes++;
				c = read();
			}
			if (!isLineTerminal(c))
				theHoldover = (char) c;
			return consecutiveQuotes;
		}
	}

	private static boolean isLineTerminal(int ch) {
		return ch < 0 || ch == '\n' || ch == '\r';
	}
}

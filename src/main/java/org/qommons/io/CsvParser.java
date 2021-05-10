package org.qommons.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import org.qommons.ArgumentParsing2;
import org.qommons.IntList;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;

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
	private int theEntryNumber;
	private int theLastLineNumber;
	private long theLastLineOffset;
	private final IntList theLastLineColumnOffsets;

	private CharsetEncoder theCharSet;
	private StringBuilder theCurrentLine;
	private long theLastLineByteOffset;
	private long theCurrentByteOffset;

	/**
	 * @param reader The reader to parse CSV data from
	 * @param delimiter The delimiter character for the CSV file
	 */
	public CsvParser(Reader reader, char delimiter) {
		theReader = reader;
		theDelimiter = delimiter;
		theTabColumnOffset = 1;
		theEntryNumber = -1;
		theParseState = new CsvParseState();
		theLastLineColumnOffsets = new IntList();
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
		if (theDelimiter == '\t' && tabOffset != 1)
			throw new IllegalStateException("tab offset cannot be used with tab-delimited files");
		theTabColumnOffset = tabOffset;
		return this;
	}

	/**
	 * This class can account not only for characters, but also for bytes read from the reader. This may be a bit inefficient, as this class
	 * may have to to re-encode character data into bytes that have already been decoded by the reader.
	 * 
	 * @param charSet The character set to use to keep track of bytes read and parsed by this parser
	 * @return This parser
	 * @see #getLastLineByteOffset()
	 * @see #getCurrentByteOffset()
	 */
	public CsvParser withCharset(Charset charSet) {
		if (theEntryNumber > 0 || thePassedBlankLines > 0)
			throw new IllegalStateException("Cannot start accounting for bytes after reading data");
		theCharSet = charSet.newEncoder();
		theCurrentLine = new StringBuilder();
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
		List<String> columns = new LinkedList<>();
		if (!parseNextLine(columns::add))
			return null;
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
		class ArrayColumnConsumer implements ColumnAccepter {
			int index = 0;

			@Override
			public void accept(String column) throws TextParseException {
				columns[index] = column;
				if (index == columns.length - 1 && theParseState.getLastTerminal() == CsvValueTerminal.COLUMN_END)
					theParseState.throwParseException("More than the expected " + columns.length + " columns encountered");
				index++;
			}
		}
		ArrayColumnConsumer onColumn = new ArrayColumnConsumer();
		parseNextLine(onColumn);
		if (onColumn.index == 0)
			return false;
		else if (onColumn.index < columns.length)
			theParseState.throwParseException(columns.length + " columns expected, but only " + onColumn.index + " encountered");
		return true;
	}

	private interface ColumnAccepter {
		void accept(String column) throws TextParseException;
	}

	private boolean parseNextLine(ColumnAccepter onColumn) throws IOException, TextParseException {
		thePassedBlankLines = 0;
		theLastLineColumnOffsets.clear();
		theLastLineNumber = theParseState.theLineNumber;
		theLastLineOffset = theParseState.theOffset;
		theLastLineByteOffset = theCurrentByteOffset;
		try {
			String value = theParseState.parseColumn();
			while (value.length() == 0 && theParseState.getLastTerminal() == CsvValueTerminal.LINE_END) {
				thePassedBlankLines++;
				theLastLineNumber = theParseState.theLineNumber;
				theLastLineOffset = theParseState.theOffset;
				value = theParseState.parseColumn(); // Move past any blank lines
			}
			switch (theParseState.getLastTerminal()) {
			case COLUMN_END:
				break;
			case LINE_END:
				onColumn.accept(value);
				return true;
			case FILE_END:
				if (value.length() > 0) {
					onColumn.accept(value);
					return true;
				} else
					return false;
			}

			onColumn.accept(value);
			theLastLineColumnOffsets.add((int) (theParseState.getValueOffset() - theParseState.theOffset));
			do {
				onColumn.accept(theParseState.parseColumn());
				theLastLineColumnOffsets.add((int) (theParseState.getValueOffset() - theParseState.theOffset));
			} while (theParseState.getLastTerminal() == CsvValueTerminal.COLUMN_END);
			theEntryNumber++;
			return true;
		} finally {
			if (theCharSet != null) {
				int length = theCharSet.encode(CharBuffer.wrap(theCurrentLine)).limit();
				theCurrentByteOffset += length;
			}
		}
	}

	/** @return The number of blank lines that were ignored prior to the most recently-parsed line of the file */
	public int getPassedBlankLines() {
		return thePassedBlankLines;
	}

	/**
	 * @return The number of rows (not the same as lines, since newlines within quotes can be part of a CSV column) that have been parsed by
	 *         this parser, including the current row, or -1 if no entries have been parsed
	 */
	public int getEntryNumber() {
		return theEntryNumber;
	}

	/** @return The line number that was most recently parsed */
	public int getLastLineNumber() {
		return theLastLineNumber;
	}

	/** @return The overall character offset of the start of the line that was most recently parsed */
	public long getLastLineOffset() {
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
	public long getCurrentOffset() {
		return theParseState.theOffset;
	}

	/**
	 * Gets the number of bytes parsed by this class up to the last line. This feature is only enabled if the {@link #withCharset(Charset)
	 * char set} is set.
	 * 
	 * @return The number of bytes read and parsed by this class at the beginning of the most recently-parsed line, or -1 if the char set
	 *         was not given
	 */
	public long getLastLineByteOffset() {
		if (theCharSet == null)
			return -1;
		return theLastLineByteOffset;
	}

	/**
	 * Gets the number of bytes parsed by this class so far. This feature is only enabled if the {@link #withCharset(Charset) char set} is
	 * set.
	 * 
	 * @return The number of bytes read and parsed by this class so far, or -1 if the char set was not given
	 */
	public long getCurrentByteOffset() {
		if (theCharSet == null)
			return -1;
		return theCurrentByteOffset;
	}

	/**
	 * @param columnIndex The index of the column in the last line parsed
	 * @return The offset of the specified column of the previous line from the beginning of the file
	 */
	public int getColumnOffset(int columnIndex) {
		return theLastLineColumnOffsets.get(columnIndex);
	}

	/**
	 * Throws a {@link TextParseException} pointing to the given column of the previously-parsed line in the file. Never called internally,
	 * this method is useful for when the actual CSV was correctly parsed, but a column's data is malformed for the use of the calling
	 * application.
	 * 
	 * @param columnIndex The index of the column with bad data
	 * @param errorOffset The offset of the error within the column text
	 * @param message The message for the exception
	 * @throws TextParseException always
	 */
	public void throwParseException(int columnIndex, int errorOffset, String message) throws TextParseException {
		throwParseException(columnIndex, errorOffset, message);
		int colOffset = getColumnOffset(columnIndex);
		throw new TextParseException(message, colOffset + errorOffset, theLastLineNumber, columnIndex);
	}

	/**
	 * Throws a {@link TextParseException} pointing to the given column of the previously-parsed line in the file. Never called internally,
	 * this method is useful for when the actual CSV was correctly parsed, but a column's data is malformed for the use of the calling
	 * application.
	 * 
	 * @param columnIndex The index of the column with bad data
	 * @param errorOffset The offset of the error within the column text
	 * @param message The message for the exception
	 * @param cause The cause of the error
	 * @throws TextParseException always
	 */
	public void throwParseException(int columnIndex, int errorOffset, String message, Throwable cause) throws TextParseException {
		int colOffset = getColumnOffset(columnIndex);
		throw new TextParseException(message, colOffset + errorOffset, theLastLineNumber, columnIndex, cause);
	}

	static enum CsvValueTerminal {
		COLUMN_END, LINE_END, FILE_END;
	}

	static class QuoteStart {
		final long theOffset;
		final int theLineNumber;
		final int theColumnNumber;

		QuoteStart(long offset, int lineNumber, int columnNumber) {
			theOffset = offset;
			theLineNumber = lineNumber;
			theColumnNumber = columnNumber;
		}
	}

	class CsvParseState {
		private final StringBuilder theValue;
		private QuoteStart isQuoted;
		private long theValueOffset;
		private long theOffset;
		private int theLineNumber;
		private int theColumnNumber; // Char columns, not CSV columns here
		private CsvValueTerminal theLastTerminal;

		CsvParseState() {
			theValue = new StringBuilder();
		}

		<T> T throwParseException(String message) throws TextParseException {
			throw new TextParseException(message, (int) theOffset, theLineNumber, theColumnNumber);
		}

		CsvValueTerminal getLastTerminal() {
			return theLastTerminal;
		}

		long getValueOffset() {
			return theValueOffset;
		}

		String parseColumn() throws IOException, TextParseException {
			isQuoted = null;
			theLastTerminal = null;
			theValueOffset = theOffset;
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
					isQuoted = new QuoteStart(theOffset, theLineNumber, theColumnNumber);
					theValueOffset++;
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
					throw new TextParseException("Unmatched quote", (int) isQuoted.theOffset, isQuoted.theLineNumber,
						isQuoted.theColumnNumber);
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
			if (c >= 0 && theCurrentLine != null)
				theCurrentLine.append((char) c);
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

	/**
	 * @param string The string to format to CSV
	 * @param delimiter The delimiter of the format
	 * @return The CSV-formatted cell value
	 */
	public static String toCsv(String string, char delimiter) {
		if (string.indexOf(delimiter) < 0)
			return string;
		StringBuilder str = new StringBuilder().append('"');
		for (int c = 0; c < string.length(); c++) {
			if (string.charAt(c) == '"')
				str.append("\"\"");
			else
				str.append(string.charAt(c));
		}
		str.append('"');
		return str.toString();
	}

	/**
	 * <p>
	 * A simple program that reads in a CSV file (the "--src=&lt;file>" argument) and prints it to another file (the "--target=&lt;file>"
	 * argument).
	 * </p>
	 * 
	 * <p>
	 * The non-trivial part is that this program has the capability to filter columns and rows out of the ouput. Columns can be filtered
	 * using the "--include=header1,header2..." or "--exclude=header1,header2..." arguments. Rows can be filtered using one or more
	 * "--filter=column=value" arguments.
	 * </p>
	 * <p>
	 * The delimiter character can be overridden (default is ',') using "--delimiter=?"
	 * </p>
	 * 
	 * @param args Command line arguments determining the location of the source and target files, the filtering, and the delimiter.
	 */
	public static void main(String[] args) {
		ArgumentParsing2.Arguments parsedArgs = ArgumentParsing2.build().forValuePattern(a -> {
			a.addBetterFileArgument("src", f -> f.required().directory(false).mustExist(true))//
				.addBetterFileArgument("target", f -> f.required().directory(false).create(true))//
				.addPatternArgument("filter", "(?<column>.+)=(?<value>.*)", a2 -> a2.times(0, Integer.MAX_VALUE))//
				.addStringArgument("delimiter", a2 -> a2.defaultValue(","));
		}).forMultiValuePattern(a -> {
			a.addStringArgument("include", a2 -> a2.optional())//
				.addStringArgument("exclude", a2 -> a2.optional().when("include", String.class, b -> b.specified().forbidden()))//
			;
		}).build().parse(args);

		String delimiter = parsedArgs.get("delimiter", String.class);
		Map<String, String> filters = new LinkedHashMap<>();
		for (Matcher m : parsedArgs.getAll("filter", Matcher.class)) {
			filters.put(m.group("column"), m.group("value"));
		}
		if (delimiter.length() != 1)
			throw new IllegalArgumentException("Delimiter must be a single character");
		List<? extends String> include = parsedArgs.getAll("include", String.class);
		Set<String> exclude = new HashSet<>(parsedArgs.getAll("exclude", String.class));
		BetterFile src = parsedArgs.get("src", BetterFile.class);
		long length = src.length();
		int progress = 0;
		try (CountingInputStream stream = new CountingInputStream(src.read()); //
			Reader in = new InputStreamReader(stream); //
			Writer out = new BufferedWriter(new OutputStreamWriter(parsedArgs.get("target", BetterFile.class).write()))) {
			CsvParser parser = new CsvParser(in, delimiter.charAt(0));
			String[] header = parser.parseNextLine();
			for (String f : filters.keySet()) {
				boolean found = false;
				for (String h : header) {
					if (h.equals(f)) {
						found = true;
						break;
					}
				}
				if (!found)
					throw new IllegalArgumentException("No such header found for filter: " + f);
			}
			for (String i : include) {
				boolean found = false;
				for (String h : header) {
					if (h.equals(i)) {
						found = true;
						break;
					}
				}
				if (!found)
					throw new IllegalArgumentException("No such header found for include: " + i);
			}
			for (String i : exclude) {
				boolean found = false;
				for (String h : header) {
					if (h.equals(i)) {
						found = true;
						break;
					}
				}
				if (!found)
					throw new IllegalArgumentException("No such header found for exclude: " + i);
			}
			QuickMap<String, Integer> columns = QuickSet.of(header).createMap();
			for (int i = 0; i < header.length; i++)
				columns.put(header[i], i);
			if (!include.isEmpty()) {
				boolean first = true;
				for (String column : include) {
					if (first)
						first = false;
					else
						out.append(delimiter.charAt(0));
					out.append(column);
				}
			} else {
				boolean first = true;
				for (String column : header) {
					if (exclude.contains(column))
						continue;
					if (first)
						first = false;
					else
						out.append(delimiter.charAt(0));
					out.append(column);
				}
			}
			out.append('\n');
			String[] line = new String[header.length];
			int total = 0, kept = 0;
			while (parser.parseNextLine(line)) {
				total++;
				boolean filterMatch = true;
				for (Map.Entry<String, String> filter : filters.entrySet()) {
					if (!filter.getValue().equalsIgnoreCase(line[columns.get(filter.getKey())].trim())) {
						filterMatch = false;
						break;
					}
				}
				if (!filterMatch)
					continue;
				kept++;
				if (!include.isEmpty()) {
					boolean first = true;
					for (String column : include) {
						if (first)
							first = false;
						else
							out.append(delimiter.charAt(0));
						out.append(CsvParser.toCsv(line[columns.get(column)], delimiter.charAt(0)));
					}
				} else {
					boolean first = true;
					for (int h = 0; h < header.length; h++) {
						if (exclude.contains(header[h]))
							continue;
						if (first)
							first = false;
						else
							out.append(delimiter.charAt(0));
						out.append(CsvParser.toCsv(line[h], delimiter.charAt(0)));
					}
				}
				out.append('\n');
				int newProgress = (int) (stream.getPosition() * 100.0 / length);
				if (newProgress > progress && newProgress != 100) {
					progress = newProgress;
					if (progress % 10 == 0) {
						System.out.print(progress);
						System.out.print('%');
					} else
						System.out.print('.');
					System.out.flush();
				}
			}
			System.out.println();
			int percent = (int) Math.round(kept * 1000.0 / total);
			System.out.println(kept + " of " + total + " lines (" + (percent / 10) + "." + (percent % 10) + "% copied");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (TextParseException e) {
			e.printStackTrace();
		}
	}
}

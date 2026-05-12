package org.qommons.io;

import java.io.*;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.*;
import java.util.regex.Matcher;

import org.qommons.ArgumentParsing;
import org.qommons.LongList;
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
 * <p>
 * This class does not provide for validation of the column content (beyond valid CSV) or parsing of column values into structures. It is an
 * easy-to-use, single-purpose utility for parsing CSV files, upon which other higher-level APIs may be developed.
 * </p>
 * <p>
 * When errors are found in CSV, this class throws {@link TextParseException}s, which provide the {@link TextParseException#getErrorOffset()
 * absolute position offset}, {@link TextParseException#getLineNumber() line number}, and {@link TextParseException#getColumnNumber() column
 * number} of the location in the file where the error was observed.
 * </p>
 */
public class CsvParser implements TabularFileParser {
	final Reader theReader;
	final char theDelimiter;
	private final long theFileLength;
	int theTabColumnOffset;
	private final CsvParseState theParseState;
	private int thePassedBlankLines;
	private int theEntryNumber;
	private int theLastLineNumber;
	private long theLastLineOffset;
	private final LongList theLastLineColumnOffsets;

	private CharsetEncoder theCharSet;
	private StringBuilder theCurrentLine;
	private long theLastLineByteOffset;
	private long theCurrentByteOffset;

	/**
	 * @param reader The reader to parse CSV data from
	 * @param delimiter The delimiter character for the CSV file
	 * @param fileLength The number of characters in the file. This is only need for the {@link #getFileLength()} method and may be -1 or
	 *        anything else if that method will not be used
	 */
	public CsvParser(Reader reader, char delimiter, long fileLength) {
		theReader = reader;
		theDelimiter = delimiter;
		theFileLength = fileLength;
		theTabColumnOffset = 1;
		theEntryNumber = -1;
		theParseState = new CsvParseState();
		theLastLineColumnOffsets = new LongList();
		theCurrentLine = new StringBuilder();
	}

	/** @return The delimiter character used to parse CSV */
	public char getDelimiter() {
		return theDelimiter;
	}

	@Override
	public long getFileLength() {
		return theFileLength;
	}

	@Override
	public long getParseProgress() {
		return theCurrentByteOffset;
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
		return this;
	}

	@Override
	public String[] parseNextLine() throws IOException, TextParseException {
		List<String> columns = new LinkedList<>();
		if (!parseNextLine(columns::add))
			return null;
		return columns.toArray(new String[columns.size()]);
	}

	@Override
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
			theLastLineColumnOffsets.add(theParseState.getValueOffset() - theLastLineOffset);
			do {
				onColumn.accept(theParseState.parseColumn());
				theLastLineColumnOffsets.add(theParseState.getValueOffset() - theLastLineOffset);
			} while (theParseState.getLastTerminal() == CsvValueTerminal.COLUMN_END);
			theEntryNumber++;
			return true;
		} finally {
			if (theCharSet != null) {
				int length = theCharSet.encode(CharBuffer.wrap(theCurrentLine)).limit();
				theCurrentByteOffset += length;
			} else
				theCurrentByteOffset += theCurrentLine.length();
			theCurrentLine.setLength(0);
		}
	}

	@Override
	public int getPassedBlankLines() {
		return thePassedBlankLines;
	}

	@Override
	public int getEntryNumber() {
		return theEntryNumber;
	}

	@Override
	public int getLastLineNumber() {
		return theLastLineNumber;
	}

	@Override
	public long getLastLineOffset() {
		return theLastLineOffset;
	}

	@Override
	public int getCurrentLineNumber() {
		return theParseState.theLineNumber;
	}

	@Override
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

	@Override
	public long getColumnOffset(int columnIndex) {
		return theLastLineColumnOffsets.get(columnIndex);
	}

	@Override
	public void close() throws IOException {
		theReader.close();
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
		private boolean isAtColumnStart;
		private CsvValueTerminal theLastTerminal;

		CsvParseState() {
			theValue = new StringBuilder();
		}

		void goToLineEnd() {
			try {
				while (theColumnNumber != 0) {
					try {
						if (isAtColumnStart)
							parseColumn();
						else if (readContentChar() < 0)
							return;
					} catch (TextParseException e) {
					}
				}
			} catch (IOException e) {
			}
		}

		<T> T throwParseException(String message) throws TextParseException {
			goToLineEnd();
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
			isAtColumnStart = true;
			int c = readContentChar();
			while (c >= 0) {
				theValue.append((char) c);
				c = readContentChar();
			}
			String column = theValue.toString();
			theValue.setLength(0);
			return column;
		}

		private int readContentChar() throws IOException, TextParseException {
			int c = readStreamChar();
			return interpretStreamChar(c);
		}

		private int interpretStreamChar(int c) throws IOException, TextParseException {
			boolean atColumnStart = isAtColumnStart;
			if (atColumnStart)
				isAtColumnStart = false;
			theOffset++;
			if (c == '\n') {
				theLineNumber++;
				theColumnNumber = 0;
			} else
				theColumnNumber++;

			if (c == '"') {
				if (atColumnStart) { // Begin quote
					isQuoted = new QuoteStart(theOffset, theLineNumber, theColumnNumber);
					theValueOffset++;
					return readContentChar();
				} else if (isQuoted != null) {
					c = readStreamChar();
					if (c == '"') { // Double double-quotes within a quoted column is an escaped double-quote
						theColumnNumber++;
						return c;
					} else { // End quote
						isQuoted = null;
						c = interpretStreamChar(c);
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
	 * <p>
	 * The only method in this class for <b>outputting</b> CSV, this method accepts any string and returns a string that, when encountered
	 * in a CSV file, this class would parse as a single column value equivalent to the given string.
	 * </p>
	 * <p>
	 * CSV is such a simple format that this method is the only functionality complicated enough to justify a utility method.
	 * </p>
	 * 
	 * @param string The string to format to CSV
	 * @param delimiter The delimiter of the format
	 * @return The CSV-formatted cell value
	 */
	public static String toCsv(String string, char delimiter) {
		boolean simple = true;
		for (int c = 0; simple && c < string.length(); c++) {
			char ch = string.charAt(c);
			if (ch == delimiter || ch == '\n' || ch == '"')
				simple = false;
		}
		if (simple)
			return string;
		StringBuilder str = new StringBuilder(string.length() + 10);
		str.append('"');
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
	 * Escapes a CSV column value in a StringBuilder
	 * 
	 * @param str The StringBuilder containing the column value
	 * @param start The start position of the CSV value
	 * @param end The end position of the CSV value
	 * @param delimiters Delimiters that must be escaped
	 * @return The end position of the now-escaped CSV column value in the sequence
	 */
	public static int escapeCsv(StringBuilder str, int start, int end, char... delimiters) {
		if (start >= end)
			return end;
		Arrays.sort(delimiters);
		// Quote characters in the body of the value do not need to be escaped,
		// but an initial quote character would signal to the parser that the CSV value is escaped, which would cause problems.
		boolean simple = str.charAt(0) != '"';
		for (int c = start; simple && c < end; c++) {
			char ch = str.charAt(c);
			if (Arrays.binarySearch(delimiters, ch) >= 0 || ch == '\n')
				simple = false;
		}
		if (simple)
			return end;
		str.insert(start, '"');
		end++;
		for (int c = start + 1; c < end; c++) {
			char ch = str.charAt(c);
			if (ch == '"') {
				c++;
				str.insert(c, '"');
			}
		}
		str.insert(end, '"');
		return end + 1;
	}

	/** A result from the {@link CsvParser#fromCsv(CharSequence, int, int, int, char...)} method */
	public static class ParsedCsvValue {
		/** The parsed CSV value */
		public final CharSequence parsed;
		/**
		 * The end of the CSV value in the source sequence. This is either the length of the sequence (the value was the last in the
		 * sequence), or the index in the sequence of the delimiter that terminated the value.
		 */
		public final int sourceEnd;
		/** The line number of the end of the CSV value */
		public final int endLine;
		/** The column number of the end of the CSV value */
		public final int endColumn;

		/**
		 * @param parsed The parsed CSV value
		 * @param sourceEnd The end of the CSV value in the source sequence. This is either the length of the sequence (the value was the
		 *        last in the sequence), or the index in the sequence of the delimiter that terminated the value.
		 * @param endLine The line number of the end of the CSV value
		 * @param endColumn The column number of the end of the CSV value
		 */
		public ParsedCsvValue(CharSequence parsed, int sourceEnd, int endLine, int endColumn) {
			this.parsed = parsed;
			this.sourceEnd = sourceEnd;
			this.endLine = endLine;
			this.endColumn = endColumn;
		}
	}

	/**
	 * Parses a CSV column value from a sequence
	 * 
	 * @param text The sequence containing the CSV value
	 * @param start The starting index of the CSV value in the sequence
	 * @param startLine The line number at the value start position
	 * @param startCol The column number at the value start position
	 * @param delimiters The delimiters that may terminate the value
	 * @return A structure containing the parsed value and information about its positioning
	 * @throws TextParseException If the CSV value is escaped (begins with a '"'), but there is no terminating quotation mark
	 */
	public static ParsedCsvValue fromCsv(CharSequence text, int start, int startLine, int startCol, char... delimiters)
		throws TextParseException {
		Arrays.sort(delimiters);
		int line = startLine, col = startCol;
		if (start >= text.length() || text.charAt(start) != '"') {
			for (int i = start; i < text.length(); i++) {
				char ch = text.charAt(i);
				if (Arrays.binarySearch(delimiters, ch) >= 0)
					return new ParsedCsvValue(text.subSequence(start, i), i, line, col);
				else if (ch == '\n') {
					line++;
					col = 0;
				} else
					col++;
			}
			return new ParsedCsvValue(start == 0 ? text : text.subSequence(start, text.length()), text.length(), line, col);
		}
		StringBuilder unescaped = new StringBuilder();
		for (int i = start + 1; i < text.length(); i++) {
			char ch = text.charAt(i);
			col++;
			if (ch == '"') {
				int next = i + 1;
				if (next < text.length() && text.charAt(next) == '"') {
					i++;
					col++;
				} else
					return new ParsedCsvValue(unescaped, i + 1, line, col);
			} else {
				line++;
				col = 0;
			}
			unescaped.append(ch);
		}
		throw new TextParseException("Unmatched quote", start, startLine, startCol);
	}

	/**
	 * <p>
	 * A simple program that reads in a CSV file (the "--src=&lt;file>" argument) and prints it to another file (the "--target=&lt;file>"
	 * argument).
	 * </p>
	 * 
	 * <p>
	 * The non-trivial part is that this program has the capability to filter columns and rows out of the output. Columns can be filtered
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
		ArgumentParsing.Arguments parsedArgs = ArgumentParsing.build().forValuePattern(a -> {
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
			Writer out = new BufferedWriter(new OutputStreamWriter(parsedArgs.get("target", BetterFile.class).write()));
			CsvParser parser = new CsvParser(in, delimiter.charAt(0), src.length())) {
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
			int percent = total == 0 ? 1000 : (int) Math.round(kept * 1000.0 / total);
			System.out.println(kept + " of " + total + " lines (" + (percent / 10) + "." + (percent % 10) + "% copied");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (TextParseException e) {
			e.printStackTrace();
		}
	}
}

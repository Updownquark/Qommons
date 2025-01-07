package org.qommons.io;

import java.io.*;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.ParseException;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.qommons.*;
import org.qommons.collect.BetterList;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.ex.ExFunction;

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

	/**
	 * @return A builder for a structure ({@link TypedLineParser}) that facilitates the parsing of lines from a CSV file with a header into
	 *         typed structures
	 * @throws IllegalStateException If this method is called after parsing has begun
	 * @throws IOException If the CSV file's header could not be read
	 * @throws TextParseException If the CSV file is empty--without a header
	 */
	public TypedLineParser0 parseTyped() throws IllegalStateException, IOException, TextParseException {
		if (theLastLineNumber != 0)
			throw new IllegalStateException("This method may only be called at the beginning of a file");
		String[] header = parseNextLine();
		if (header == null || header.length == 0) {
			throwParseException(0, 0, "No CSV header line");
			throw new IllegalStateException("Shouldn't happen");
		}
		return new TypedLineParser0(header, new String[header.length], true, false);
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
			theLastLineColumnOffsets.add((int) (theParseState.getValueOffset() - theLastLineOffset));
			do {
				onColumn.accept(theParseState.parseColumn());
				theLastLineColumnOffsets.add((int) (theParseState.getValueOffset() - theLastLineOffset));
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
		int colOffset = getColumnOffset(columnIndex);
		throw new TextParseException(message, (int) theLastLineOffset + colOffset + errorOffset, theLastLineNumber,
			colOffset + errorOffset);
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

	/** A line produced by a {@link TypedLineParser}, containing structures parsed from the column values of a single CSV line */
	public interface TypedLine {
		/** @return The number of columns available in this line (some may be absent) */
		int getColumnCount();

		/**
		 * @param index The index of the column to get. This index corresponds to the order in which columns were
		 *        {@link TypedLineParser#with(Pattern, boolean, ExFunction) added} to the parser, not their order in the file
		 * @return The value parsed from the given column in this line. Null if the column was absent in the file (and optional) or if the
		 *         parser returned null.
		 */
		Object get(int index);

		/**
		 * A strongly-typed version of {@link #get(int)}
		 * 
		 * @param <T> The type of the column value
		 * @param index The index of the column to get (according to the order the column was
		 *        {@link TypedLineParser#with(Pattern, boolean, ExFunction) added} to the parser, not the order in the file)
		 * @param type The type of the column value
		 * @return The value parsed from the given column in this line, or null if the column was not present in the file or its parser
		 *         returned null
		 * @throws ClassCastException If the column was present but the parsed value was not of the given type
		 */
		default <T> T get(int index, Class<T> type) throws ClassCastException {
			return get(index, type, null);
		}

		/**
		 * A strongly-typed version of {@link #get(int)}
		 * 
		 * @param <T> The type of the column value
		 * @param index The index of the column to get (according to the order the column was
		 *        {@link TypedLineParser#with(Pattern, boolean, ExFunction) added} to the parser, not the order in the file)
		 * @param type The type of the column value
		 * @param defaultValue The value to return if the column was missing from the file or if its parser returned null
		 * @return The value parsed from the given column in this line, or <code>defaultValue</code> if the column was not present in the
		 *         file or its parser returned null
		 * @throws ClassCastException If the column was present but the parsed value was not of the given type
		 */
		default <T> T get(int index, Class<T> type, T defaultValue) throws ClassCastException {
			Object value = get(index);
			if (value == null)
				return defaultValue;
			else if (type.isInstance(value))
				return (T) value;
			else if (type.isPrimitive()) {
				Class<T> wrapper = QommonsUtils.wrap(type);
				if (wrapper != type && wrapper.isInstance(value))
					return (T) value;
			}
			throw new ClassCastException("Value [" + index + "] is of type " + value.getClass().getName() + ", not " + type.getName());
		}
	}

	/**
	 * A typed line with a single configured column
	 * 
	 * @param <T> The type of the column
	 */
	public static class SingleTypedLine<T> implements TypedLine, Supplier<T> {
		private static final SingleTypedLine<?> NULL = new SingleTypedLine<>(null);

		/**
		 * @param <T> The type for the line
		 * @return A {@link SingleTypedLine} with a null value
		 */
		public static <T> SingleTypedLine<T> NULL() {
			return (SingleTypedLine<T>) NULL;
		}

		/**
		 * @param <T> The type for the line
		 * @param value The value for the line
		 * @return A {@link SingleTypedLine} with the given value
		 */
		public static <T> SingleTypedLine<T> of(T value) {
			return value == null ? (SingleTypedLine<T>) NULL : new SingleTypedLine<>(value);
		}

		private final T theValue;

		private SingleTypedLine(T value) {
			theValue = value;
		}

		@Override
		public int getColumnCount() {
			return 1;
		}

		@Override
		public T get() {
			return theValue;
		}

		@Override
		public T get(int index) {
			if (index != 0)
				throw new IndexOutOfBoundsException(index + " of 1");
			return get();
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theValue);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof SingleTypedLine))
				return false;
			else
				return Objects.equals(theValue, ((SingleTypedLine<?>) obj).theValue);
		}

		@Override
		public String toString() {
			return String.valueOf(theValue);
		}
	}

	/**
	 * A typed line with 2 configured columns
	 * 
	 * @param <T> The type of the first column
	 * @param <U> The type of the second column
	 */
	public static class DoubleTypedLine<T, U> extends BiTuple<T, U> implements TypedLine {
		/**
		 * @param v1 The parsed value of the first column
		 * @param v2 The parsed value of the second column
		 */
		public DoubleTypedLine(T v1, U v2) {
			super(v1, v2);
		}

		@Override
		public int getColumnCount() {
			return 2;
		}

		@Override
		public Object get(int index) {
			switch (index) {
			case 0:
				return getValue1();
			case 1:
				return getValue2();
			default:
				throw new IndexOutOfBoundsException(index + " of 2");
			}
		}
	}

	/**
	 * A typed line with 3 configured columns
	 * 
	 * @param <T> The type of the first column
	 * @param <U> The type of the second column
	 * @param <V> The type of the third column
	 */
	public static class TripleTypedLine<T, U, V> extends TriTuple<T, U, V> implements TypedLine {
		/**
		 * @param v1 The parsed value of the first column
		 * @param v2 The parsed value of the second column
		 * @param v3 The parsed value of the third column
		 */
		public TripleTypedLine(T v1, U v2, V v3) {
			super(v1, v2, v3);
		}

		@Override
		public int getColumnCount() {
			return 3;
		}

		@Override
		public Object get(int index) {
			switch (index) {
			case 0:
				return getValue1();
			case 1:
				return getValue2();
			case 2:
				return getValue3();
			default:
				throw new IndexOutOfBoundsException(index + " of 3");
			}
		}
	}

	/**
	 * A typed line with 4 or more configured columns
	 * 
	 * @param <T> The type of the first column
	 * @param <U> The type of the second column
	 * @param <V> The type of the third column
	 */
	public static class NTypedLine<T, U, V> implements TypedLine {
		private final Object[] theValues;

		/** @param values The parsed column values */
		public NTypedLine(Object[] values) {
			theValues = values;
		}

		@Override
		public int getColumnCount() {
			return theValues.length;
		}

		@Override
		public Object get(int index) {
			if (index < 0 || index >= theValues.length)
				throw new IndexOutOfBoundsException(index + " of " + theValues.length);
			return theValues[index];
		}

		/** @return The first column value */
		public T getValue1() {
			return (T) theValues[0];
		}

		/** @return The second column value */
		public U getValue2() {
			return (U) theValues[1];
		}

		/** @return The third column value */
		public V getValue3() {
			return (V) theValues[2];
		}

		/**
		 * @param extraIdx The index of the column <b>beyond the third value</b>
		 * @return The value of the extra column
		 */
		public Object getExtra(int extraIdx) {
			return theValues[extraIdx - 3];
		}

		/**
		 * @param <X> The type of the column value
		 * @param extraIdx The index of the column <b>beyond the third value</b>
		 * @param type The type of the column value
		 * @return The value of the extra column
		 * @throws ClassCastException If the column was present but the parsed value was not of the given type
		 */
		public <X> X getExtra(int extraIdx, Class<X> type) throws ClassCastException {
			return get(extraIdx - 3, type);
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(theValues);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof NTypedLine))
				return false;
			else
				return ArrayUtils.equals(theValues, ((NTypedLine<?, ?, ?>) obj).theValues);
		}

		@Override
		public String toString() {
			return Arrays.toString(theValues);
		}
	}

	/**
	 * A structure that facilitates the parsing of lines from a CSV file with a header into typed structures
	 * 
	 * @param <L> The sub-type of typed line this parser produces
	 */
	public interface TypedLineParser<L extends TypedLine> {
		/** @return The CSV parser that this typed line parser was created by */
		CsvParser getCsvParser();

		/** @return The header parsed from the file */
		List<String> getHeader();

		/**
		 * @param index The index of the column to check (according to the order in which it was
		 *        {@link TypedLineParser#with(Pattern, boolean, ExFunction) added} to the parser, not the order in the file)
		 * @return Whether the column was found in the CSV file header. Always true for non-optional columns.
		 */
		default boolean hasColumn(int index) {
			return translateColumn(index) >= 0;
		}

		/**
		 * @param index The index of the column in this line parser according to the order in which it was
		 *        {@link TypedLineParser#with(Pattern, boolean, ExFunction) added} to the parser
		 * @return The index of the column as found in the file header
		 */
		int translateColumn(int index);

		/**
		 * @return The next line in the file, or null if the CSV file has no more content lines
		 * @throws IOException If the file could not be read
		 * @throws TextParseException If the CSV line contains a syntax error, or if any of the parsers for the typed columns throws a
		 *         {@link ParseException}
		 */
		L parseNextLine() throws IOException, TextParseException;

		/**
		 * Creates a parser that parses all of this parser's columns as well as another
		 * 
		 * @param <T> The type of the column to parse
		 * @param column The name of the column
		 * @param optional Whether the column may be absent (all values for the column will be null)
		 * @param parser The parser to parse the column value
		 * @param otherPossibilities Other possible names for the column
		 * @return A new parser that can parse lines containing the given column
		 * @throws TextParseException If <code>optional</code> is false and the column is not present in the file header
		 */
		<T> AbstractTypedLineParser with(String column, boolean optional, ExFunction<String, ? extends T, ParseException> parser,
			String... otherPossibilities) throws TextParseException;

		/**
		 * Creates a parser that parses all of this parser's columns as well as another
		 * 
		 * @param <T> The type of the column to parse
		 * @param column A pattern to match the name of the target column
		 * @param optional Whether the column may be absent (all values for the column will be null)
		 * @param parser The parser to parse the column value
		 * @return A new parser that can parse lines containing the given column
		 * @throws TextParseException If <code>optional</code> is false and the column is not present in the file header
		 */
		<T> AbstractTypedLineParser with(Pattern column, boolean optional, ExFunction<String, ? extends T, ParseException> parser)
			throws TextParseException;
	}

	/** Abstract class with utilities useful to {@link TypedLineParser}s */
	public abstract class AbstractTypedLineParser {
		/** The column names parsed from the CSV header */
		protected final String[] theHeader;
		/** The most recently parsed CSV line (for efficiency) */
		protected final String[] theLine;
		/** Whether letter case is ignored when matching column names for {@link #with(String, boolean, ExFunction, String...)} */
		protected final boolean isIgnoreCase;
		/** Whether space characters are ignored when matching column names for {@link #with(String, boolean, ExFunction, String...)} */
		protected final boolean isIgnoreSpace;

		AbstractTypedLineParser(String[] header, String[] line, boolean isIgnoreCase, boolean ignoreSpace) {
			theHeader = header;
			theLine = line;
			this.isIgnoreCase = isIgnoreCase;
			this.isIgnoreSpace = ignoreSpace;
		}

		/** @return The CSV parser that this typed line parser was created by */
		public CsvParser getCsvParser() {
			return CsvParser.this;
		}

		/**
		 * @param columnName The column name--either the name to look for or a column name found in the file header
		 * @return The reduced column name to match against
		 */
		protected String reduce(String columnName) {
			if (isIgnoreSpace)
				columnName = columnName.replace(" ", "");
			if (isIgnoreCase)
				columnName = columnName.toLowerCase();
			return columnName;
		}

		int findColumn(String column, String[] otherPossibilities, boolean optional) throws TextParseException {
			column = reduce(column);
			for (int c = 0; c < otherPossibilities.length; c++)
				otherPossibilities[c] = reduce(otherPossibilities[c]);
			for (int c = 0; c < theHeader.length; c++) {
				boolean match;
				String h = reduce(theHeader[c]);
				match = h.equals(column);
				for (String other : otherPossibilities) {
					if (match)
						break;
					match = h.equals(other);
				}
				if (match)
					return c;
			}
			if (optional)
				return -1;
			else
				throw new TextParseException("No such column found: " + column, 0, 0, 0);
		}

		int findColumn(Pattern column, boolean optional) throws TextParseException {
			// First try without reducing
			for (int c = 0; c < theHeader.length; c++) {
				if (column.matcher(theHeader[c]).matches())
					return c;
			}
			for (int c = 0; c < theHeader.length; c++) {
				if (column.matcher(reduce(theHeader[c])).matches())
					return c;
			}
			if (optional)
				return -1;
			else
				throw new TextParseException("No column found matching '" + column.pattern() + "'", 0, 0, 0);
		}

		/** @return The header parsed from the file */
		public List<String> getHeader() {
			return BetterList.of(theHeader);
		}

		/**
		 * Implements {@link TypedLineParserN#with(String, boolean, ExFunction, String...)}
		 * 
		 * @param <T> The type of the column to parse
		 * @param column The name of the column
		 * @param optional Whether the column may be absent (all values for the column will be null)
		 * @param parser The parser to parse the column value
		 * @param otherPossibilities Other possible names for the column
		 * @return A new parser that can parse lines containing the given column
		 * @throws TextParseException If <code>optional</code> is false and the column is not present in the file header
		 */
		protected <T> AbstractTypedLineParser with(String column, boolean optional, ExFunction<String, ? extends T, ParseException> parser,
			String... otherPossibilities) throws TextParseException {
			return with(findColumn(column, otherPossibilities, optional), parser);
		}

		/**
		 * Implements {@link TypedLineParserN#with(Pattern, boolean, ExFunction)}
		 * 
		 * @param <T> The type of the column to parse
		 * @param column A pattern to match the name of the target column
		 * @param optional Whether the column may be absent (all values for the column will be null)
		 * @param parser The parser to parse the column value
		 * @return A new parser that can parse lines containing the given column
		 * @throws TextParseException If <code>optional</code> is false and the column is not present in the file header
		 */
		protected <T> AbstractTypedLineParser with(Pattern column, boolean optional, ExFunction<String, ? extends T, ParseException> parser)
			throws TextParseException {
			return with(findColumn(column, optional), parser);
		}

		/**
		 * Implementation for the {@link #with(String, boolean, ExFunction, String...)} methods
		 * 
		 * @param <T> The type of the column to parse
		 * @param columnIndex The index of the column (or -1 if the column was not present and optional)
		 * @param parser The parser to parse the column value
		 * @return A new parser that can parse lines containing the given column
		 */
		protected abstract <T> AbstractTypedLineParser with(int columnIndex, ExFunction<String, ? extends T, ParseException> parser);
	}

	/** A builder for {@link TypedLineParser}s */
	public class TypedLineParser0 extends AbstractTypedLineParser {
		TypedLineParser0(String[] header, String[] line, boolean isIgnoreCase, boolean isIgnoreSpace) {
			super(header, line, isIgnoreCase, isIgnoreSpace);
		}

		/**
		 * @param ignoreCase Whether the parser should ignore case when looking for columns in the CSV header
		 * @return A new parser with the given setting for respecting column name case
		 */
		public TypedLineParser0 ignoreCase(boolean ignoreCase) {
			return new TypedLineParser0(theHeader, theLine, ignoreCase, isIgnoreSpace);
		}

		/**
		 * @param ignoreSpace Whether the parser should ignore spaces when looking for columns in the CSV header
		 * @return A new parser with the given setting for respecting spaces in column names
		 */
		public TypedLineParser0 ignoreSpace(boolean ignoreSpace) {
			return new TypedLineParser0(theHeader, theLine, isIgnoreCase, ignoreSpace);
		}

		@Override
		public <T> TypedLineParser1<T> with(String column, boolean optional, ExFunction<String, ? extends T, ParseException> parser,
			String... otherPossibilities) throws TextParseException {
			return (TypedLineParser1<T>) super.with(column, optional, parser, otherPossibilities);
		}

		@Override
		public <T> TypedLineParser1<T> with(Pattern column, boolean optional, ExFunction<String, ? extends T, ParseException> parser)
			throws TextParseException {
			return (TypedLineParser1<T>) super.with(column, optional, parser);
		}

		@Override
		protected <T> TypedLineParser1<T> with(int columnIndex, ExFunction<String, ? extends T, ParseException> parser) {
			return new TypedLineParser1<>(theHeader, theLine, isIgnoreCase, isIgnoreSpace, columnIndex, parser);
		}
	}

	/**
	 * A {@link TypedLineParser} for a single typed column
	 * 
	 * @param <T> The type of the column
	 */
	public class TypedLineParser1<T> extends AbstractTypedLineParser implements TypedLineParser<SingleTypedLine<T>> {
		private final int theColumnIndex;
		private final ExFunction<String, ? extends T, ParseException> theParser;

		TypedLineParser1(String[] header, String[] line, boolean ignoreCase, boolean ignoreSpace, int columnIndex,
			ExFunction<String, ? extends T, ParseException> parser) {
			super(header, line, ignoreCase, ignoreSpace);
			theColumnIndex = columnIndex;
			theParser = parser;
		}

		@Override
		public int translateColumn(int index) {
			if (index == 0)
				return theColumnIndex;
			else
				throw new IndexOutOfBoundsException(index + " of 1");
		}

		@Override
		public SingleTypedLine<T> parseNextLine() throws IOException, TextParseException {
			if (!CsvParser.this.parseNextLine(theLine))
				return null;
			if (theColumnIndex < 0)
				return SingleTypedLine.NULL();
			String text = theLine[theColumnIndex];
			if (text.isEmpty())
				return SingleTypedLine.NULL();
			try {
				return new SingleTypedLine<>(theParser.apply(theLine[theColumnIndex]));
			} catch (ParseException e) {
				throwParseException(theColumnIndex, e.getErrorOffset(), e.getMessage());
				return null;
			} catch (RuntimeException e) {
				throwParseException(theColumnIndex, 0, e.toString());
				return null;
			}
		}

		@Override
		public <U> TypedLineParser2<T, U> with(String column, boolean optional, ExFunction<String, ? extends U, ParseException> parser,
			String... otherPossibilities) throws TextParseException {
			return (TypedLineParser2<T, U>) super.with(column, optional, parser, otherPossibilities);
		}

		@Override
		public <U> TypedLineParser2<T, U> with(Pattern column, boolean optional, ExFunction<String, ? extends U, ParseException> parser)
			throws TextParseException {
			return (TypedLineParser2<T, U>) super.with(column, optional, parser);
		}

		@Override
		protected <U> TypedLineParser2<T, U> with(int columnIndex, ExFunction<String, ? extends U, ParseException> parser) {
			return new TypedLineParser2<>(theHeader, theLine, isIgnoreCase, isIgnoreSpace, theColumnIndex, columnIndex, theParser, parser);
		}
	}

	/**
	 * A {@link TypedLineParser} for 2 typed columns
	 * 
	 * @param <T> The type of the first column
	 * @param <U> The type of the second column
	 */
	public class TypedLineParser2<T, U> extends AbstractTypedLineParser implements TypedLineParser<DoubleTypedLine<T, U>> {
		private final int theColumnIndex1;
		private final int theColumnIndex2;
		private final ExFunction<String, ? extends T, ParseException> theParser1;
		private final ExFunction<String, ? extends U, ParseException> theParser2;

		TypedLineParser2(String[] header, String[] line, boolean ignoreCase, boolean ignoreSpace, int columnIndex1, int columnIndex2,
			ExFunction<String, ? extends T, ParseException> parser1, ExFunction<String, ? extends U, ParseException> parser2) {
			super(header, line, ignoreCase, ignoreSpace);
			theColumnIndex1 = columnIndex1;
			theColumnIndex2 = columnIndex2;
			theParser1 = parser1;
			theParser2 = parser2;
		}

		@Override
		public int translateColumn(int index) {
			switch (index) {
			case 0:
				return theColumnIndex1;
			case 1:
				return theColumnIndex2;
			default:
				throw new IndexOutOfBoundsException(index + " of 2");
			}
		}

		@Override
		public DoubleTypedLine<T, U> parseNextLine() throws IOException, TextParseException {
			if (!CsvParser.this.parseNextLine(theLine))
				return null;
			String text;
			T value1;
			text = theLine[theColumnIndex1];
			try {
				value1 = text.isEmpty() ? null : theParser1.apply(text);
			} catch (ParseException e) {
				throwParseException(theColumnIndex1, e.getErrorOffset(), e.getMessage());
				return null;
			} catch (RuntimeException e) {
				throwParseException(theColumnIndex1, 0, e.toString());
				return null;
			}
			U value2;
			text = theLine[theColumnIndex2];
			try {
				value2 = text.isEmpty() ? null : theParser2.apply(text);
			} catch (ParseException e) {
				throwParseException(theColumnIndex2, e.getErrorOffset(), e.getMessage());
				return null;
			} catch (RuntimeException e) {
				throwParseException(theColumnIndex2, 0, e.toString());
				return null;
			}
			return new DoubleTypedLine<>(value1, value2);
		}

		@Override
		public <V> TypedLineParser3<T, U, V> with(String column, boolean optional, ExFunction<String, ? extends V, ParseException> parser,
			String... otherPossibilities) throws TextParseException {
			return (TypedLineParser3<T, U, V>) super.with(column, optional, parser, otherPossibilities);
		}

		@Override
		public <V> TypedLineParser3<T, U, V> with(Pattern column, boolean optional, ExFunction<String, ? extends V, ParseException> parser)
			throws TextParseException {
			return (TypedLineParser3<T, U, V>) super.with(column, optional, parser);
		}

		@Override
		protected <V> TypedLineParser3<T, U, V> with(int columnIndex, ExFunction<String, ? extends V, ParseException> parser) {
			return new TypedLineParser3<>(theHeader, theLine, isIgnoreCase, isIgnoreSpace, //
				theColumnIndex1, theColumnIndex2, columnIndex, //
				theParser1, theParser2, parser);
		}
	}

	/**
	 * A {@link TypedLineParser} for 3 typed columns
	 * 
	 * @param <T> The type of the first column
	 * @param <U> The type of the second column
	 * @param <V> The type of the third column
	 */
	public class TypedLineParser3<T, U, V> extends AbstractTypedLineParser implements TypedLineParser<TripleTypedLine<T, U, V>> {
		private final int theColumnIndex1;
		private final int theColumnIndex2;
		private final int theColumnIndex3;
		private final ExFunction<String, ? extends T, ParseException> theParser1;
		private final ExFunction<String, ? extends U, ParseException> theParser2;
		private final ExFunction<String, ? extends V, ParseException> theParser3;

		TypedLineParser3(String[] header, String[] line, boolean ignoreCase, boolean ignoreSpace, int columnIndex1, int columnIndex2,
			int columnIndex3,
			ExFunction<String, ? extends T, ParseException> parser1, ExFunction<String, ? extends U, ParseException> parser2,
			ExFunction<String, ? extends V, ParseException> parser3) {
			super(header, line, ignoreCase, ignoreSpace);
			theColumnIndex1 = columnIndex1;
			theColumnIndex2 = columnIndex2;
			theColumnIndex3 = columnIndex3;
			theParser1 = parser1;
			theParser2 = parser2;
			theParser3 = parser3;
		}

		@Override
		public int translateColumn(int index) {
			switch (index) {
			case 0:
				return theColumnIndex1;
			case 1:
				return theColumnIndex2;
			case 3:
				return theColumnIndex3;
			default:
				throw new IndexOutOfBoundsException(index + " of 3");
			}
		}

		@Override
		public TripleTypedLine<T, U, V> parseNextLine() throws IOException, TextParseException {
			if (!CsvParser.this.parseNextLine(theLine))
				return null;
			String text;
			T value1;
			text = theLine[theColumnIndex1];
			try {
				value1 = text.isEmpty() ? null : theParser1.apply(text);
			} catch (ParseException e) {
				throwParseException(theColumnIndex1, e.getErrorOffset(), e.getMessage());
				return null;
			} catch (RuntimeException e) {
				throwParseException(theColumnIndex1, 0, e.toString());
				return null;
			}
			U value2;
			text = theLine[theColumnIndex2];
			try {
				value2 = text.isEmpty() ? null : theParser2.apply(text);
			} catch (ParseException e) {
				throwParseException(theColumnIndex2, e.getErrorOffset(), e.getMessage());
				return null;
			} catch (RuntimeException e) {
				throwParseException(theColumnIndex2, 0, e.toString());
				return null;
			}
			V value3;
			text = theLine[theColumnIndex3];
			try {
				value3 = text.isEmpty() ? null : theParser3.apply(text);
			} catch (ParseException e) {
				throwParseException(theColumnIndex3, e.getErrorOffset(), e.getMessage());
				return null;
			} catch (RuntimeException e) {
				throwParseException(theColumnIndex3, 0, e.toString());
				return null;
			}
			return new TripleTypedLine<>(value1, value2, value3);
		}

		@Override
		public <X> TypedLineParserN<T, U, V> with(String column, boolean optional, ExFunction<String, ? extends X, ParseException> parser,
			String... otherPossibilities) throws TextParseException {
			return (TypedLineParserN<T, U, V>) super.with(column, optional, parser, otherPossibilities);
		}

		@Override
		public <X> TypedLineParserN<T, U, V> with(Pattern column, boolean optional, ExFunction<String, ? extends X, ParseException> parser)
			throws TextParseException {
			return (TypedLineParserN<T, U, V>) super.with(column, optional, parser);
		}

		@Override
		protected <X> TypedLineParserN<T, U, V> with(int columnIndex, ExFunction<String, ? extends X, ParseException> parser) {
			return new TypedLineParserN<T, U, V>(theHeader, theLine, isIgnoreCase, isIgnoreSpace, //
				new int[] { theColumnIndex1, theColumnIndex2, theColumnIndex3, columnIndex }, //
				new ExFunction[] { theParser1, theParser2, theParser3, parser });
		}
	}

	/**
	 * A {@link TypedLineParser} for 4 or more typed columns
	 * 
	 * @param <T> The type of the first column
	 * @param <U> The type of the second column
	 * @param <V> The type of the third column
	 */
	public class TypedLineParserN<T, U, V> extends AbstractTypedLineParser implements TypedLineParser<NTypedLine<T, U, V>> {
		private final int[] theColumnIndices;
		private final ExFunction<String, ?, ParseException>[] theParsers;

		TypedLineParserN(String[] header, String[] line, boolean ignoreCase, boolean ignoreSpace, int[] columnIndices,
			ExFunction<String, ?, ParseException>[] parsers) {
			super(header, line, ignoreCase, ignoreSpace);
			theColumnIndices = columnIndices;
			theParsers = parsers;
		}

		@Override
		public int translateColumn(int index) {
			return theColumnIndices[index];
		}

		@Override
		public NTypedLine<T, U, V> parseNextLine() throws IOException, TextParseException {
			if (!CsvParser.this.parseNextLine(theLine))
				return null;
			Object[] values = new Object[theColumnIndices.length];
			for (int c = 0; c < theColumnIndices.length; c++) {
				if (theColumnIndices[c] < 0)
					continue;
				String text = theLine[theColumnIndices[c]];
				if (text.isEmpty())
					continue;
				try {
					values[c] = theParsers[c].apply(text);
				} catch (ParseException e) {
					throwParseException(c, e.getErrorOffset(), e.getMessage());
				} catch (RuntimeException e) {
					throwParseException(theColumnIndices[c], 0, e.toString());
					return null;
				}
			}
			return new NTypedLine<>(values);
		}

		@Override
		public <X> TypedLineParserN<T, U, V> with(String column, boolean optional, ExFunction<String, ? extends X, ParseException> parser,
			String... otherPossibilities) throws TextParseException {
			return (TypedLineParserN<T, U, V>) super.with(column, optional, parser, otherPossibilities);
		}

		@Override
		public <X> TypedLineParserN<T, U, V> with(Pattern column, boolean optional, ExFunction<String, ? extends X, ParseException> parser)
			throws TextParseException {
			return (TypedLineParserN<T, U, V>) super.with(column, optional, parser);
		}

		@Override
		protected <X> TypedLineParserN<T, U, V> with(int columnIndex, ExFunction<String, ? extends X, ParseException> parser) {
			int[] newColumns = Arrays.copyOf(theColumnIndices, theColumnIndices.length + 1);
			newColumns[theColumnIndices.length] = columnIndex;
			return new TypedLineParserN<>(theHeader, theLine, isIgnoreCase, isIgnoreSpace, //
				newColumns, ArrayUtils.add(theParsers, parser));
		}
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
			if (ch == delimiter || ch == '\n')
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
			int percent = total == 0 ? 1000 : (int) Math.round(kept * 1000.0 / total);
			System.out.println(kept + " of " + total + " lines (" + (percent / 10) + "." + (percent % 10) + "% copied");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (TextParseException e) {
			e.printStackTrace();
		}
	}
}

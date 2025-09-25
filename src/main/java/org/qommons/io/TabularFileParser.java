package org.qommons.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.qommons.ArrayUtils;
import org.qommons.BiTuple;
import org.qommons.QommonsUtils;
import org.qommons.TriTuple;
import org.qommons.collect.BetterList;
import org.qommons.ex.ExFunction;

/**
 * A parser for a tabular file--one with rows and columns that can be parsed line-by-line.  Some characteristics:  
 * <ul>
 * <li>Blank lines are allowed and ignored anywhere in the file (the {@link #getPassedBlankLines()} method may be used to detect when this
 * happens) except within quoted columns, in which case they appear as part of the column value</li>
 * <li>Depending on how it is called, the parser may or may not expect the same number of columns per line. The {@link #parseNextLine()}
 * method parses a line regardless of the number of columns and returns them. The {@link #parseNextLine(String[])} method takes an array of
 * columns and populates them with the columns found on the current line. If the number of columns found differs from the length of the
 * input array, an exception is thrown.</li>
 * </ul>
 * <p>
 * This class does not deal specifically with a header, which may be parsed the same as any other line.
 * </p>
 * <p>
 * This class does not provide for validation of the column content (beyond valid for the file format) or parsing of column values into structures. It is an
 * easy-to-use, single-purpose utility for parsing tabular files, upon which other higher-level APIs may be developed.
 * </p>
 * <p>
 * When errors are found in the file, this class throws {@link TextParseException}s, which provide the {@link TextParseException#getErrorOffset()
 * absolute position offset}, {@link TextParseException#getLineNumber() line number}, and {@link TextParseException#getColumnNumber() column
 * number} of the location in the file where the error was observed.
 * </p>
 */
public interface TabularFileParser extends AutoCloseable {
	/**
	 * Parses all columns in the next line
	 * 
	 * @return The parsed column values for the line, or null if there is no more content in the file
	 * @throws IOException If the reader throws an exception
	 * @throws TextParseException If there is an error on the next line the file
	 */
	String[] parseNextLine() throws IOException, TextParseException;

	/**
	 * Parses the next line in the file, asserting that the number of columns is equal to the length of the <code>columns</code> array
	 * 
	 * @param columns The columns array to populate with the parsed column values from the next line of the file
	 * @return True if content was parsed, or false if there is no more content in the file
	 * @throws IOException If the reader throws an exception
	 * @throws TextParseException If there is an error on the next line of the file or the number of columns on the line is different than
	 *         <code>columns.length</code>
	 */
	boolean parseNextLine(String[] columns) throws IOException, TextParseException;

	/**
	 * @return A builder for a structure ({@link TypedLineParser}) that facilitates the parsing of lines from a file with a header into
	 *         typed structures
	 * @throws IllegalStateException If this method is called after parsing has begun
	 * @throws IOException If the file's header could not be read
	 * @throws TextParseException If the file is empty--without a header
	 */
	default TypedLineParser0 parseTyped() throws IllegalStateException, IOException, TextParseException {
		if (getLastLineNumber() != 0)
			throw new IllegalStateException("This method may only be called at the beginning of a file");
		String[] header = parseNextLine();
		if (header == null || header.length == 0) {
			throwParseException(0, 0, "No header line");
			throw new IllegalStateException("Shouldn't happen");
		}
		return new TypedLineParser0(this, header, new String[header.length], true, false);
	}

	/** @return The number of blank lines that were ignored prior to the most recently-parsed line of the file */
	int getPassedBlankLines();

	/**
	 * @return The number of rows (may not the same as lines if the format allows for multi-line entries) that have been parsed by this
	 *         parser, including the current row, or -1 if no entries have been parsed
	 */
	int getEntryNumber();

	/** @return The line number that was most recently parsed */
	int getLastLineNumber();

	/** @return The overall character offset of the start of the line that was most recently parsed */
	long getLastLineOffset();

	/** @return The line number of the line about to be parsed (or the last line parsed if there are no more lines) */
	int getCurrentLineNumber();

	/**
	 * @return The overall character offset of the start of the line about to be parsed (or of the end of the file if there are no more
	 *         lines)
	 */
	long getCurrentOffset();

	/**
	 * @param columnIndex The index of the column in the last line parsed
	 * @return The offset of the specified column of the previous line from the beginning of the file
	 */
	int getColumnOffset(int columnIndex);

	/**
	 * @param columnIndex The index of the column in the last line parsed
	 * @return The file position of the specified column in the previous line
	 */
	default FilePosition getColumnPosition(int columnIndex) {
		int lineOffset = (int) getLastLineOffset();
		int columnOffset = getColumnOffset(columnIndex);
		return new FilePosition(columnOffset, getLastLineNumber(), columnOffset - lineOffset);
	}

	/**
	 * The "length" of this file in some unit specific to this file. This may be e.g. the size of the file in bytes, or the number of rows
	 * of text in the file, or any other unit the parser can support. This must be consistent with the value returned from
	 * {@link #getParseProgress()}.
	 * 
	 * @return The length of this file, or -1 if this feature is unsupported
	 */
	long getFileLength();

	/**
	 * @return The amount of this file that has been parsed so far in some unit specific to this file. This may be e.g. the number of bytes
	 *         that have been read from the file, or the number of rows of text that have been parsed, or any other unit the parser can
	 *         support. This must be consistent with the value returned from {@link #getFileLength()}.
	 */
	long getParseProgress();

	/**
	 * Throws a {@link TextParseException} pointing to the given column of the previously-parsed line in the file. Never called internally,
	 * this method is useful for when the actual file content was correctly parsed, but a column's data is malformed for the use of the
	 * calling application.
	 * 
	 * @param columnIndex The index of the column with bad data
	 * @param errorOffset The offset of the error within the column text
	 * @param message The message for the exception
	 * @throws TextParseException always
	 */
	default void throwParseException(int columnIndex, int errorOffset, String message) throws TextParseException {
		int colOffset = getColumnOffset(columnIndex);
		throw new TextParseException(message, (int) getLastLineOffset() + colOffset + errorOffset, getLastLineNumber(),
			colOffset + errorOffset);
	}

	/**
	 * Throws a {@link TextParseException} pointing to the given column of the previously-parsed line in the file. Never called internally,
	 * this method is useful for when the actual file content was correctly parsed, but a column's data is malformed for the use of the
	 * calling application.
	 * 
	 * @param columnIndex The index of the column with bad data
	 * @param errorOffset The offset of the error within the column text
	 * @param message The message for the exception
	 * @param cause The cause of the error
	 * @throws TextParseException always
	 */
	default void throwParseException(int columnIndex, int errorOffset, String message, Throwable cause) throws TextParseException {
		int colOffset = getColumnOffset(columnIndex);
		throw new TextParseException(message, colOffset + errorOffset, getLastLineNumber(), columnIndex, cause);
	}

	@Override
	public void close() throws IOException;

	/**
	 * @param fileName The name of the file to test
	 * @return null If this API knows of an implementation supporting files of the given type, or a human-readable reason why the file is
	 *         unsupported
	 */
	static String isFileTypeSupported(String fileName) {
		if (FileUtils.hasExtension(fileName, "xlsx") == null)
			return null;
		else if (FileUtils.hasExtension(fileName, "csv") == null)
			return null;
		else if (FileUtils.hasExtension(fileName, "tsv") == null)
			return null;
		else
			return "Only CSV, TSV, and XLSX files are supported";
	}

	/**
	 * @param file The file to parse
	 * @return A {@link TabularFileParser} for the given file
	 * @throws IOException If the file could not be read
	 * @throws TextParseException If the file could not be opened as a {@link TabularFileParser}
	 * @throws IllegalArgumentException If the file's type is not supported by a known {@link TabularFileParser} implementation
	 */
	@SuppressWarnings("resource")
	static TabularFileParser parse(File file) throws IOException, TextParseException, IllegalArgumentException {
		if (FileUtils.hasExtension(file, "xlsx") == null)
			return new XlsxParser(file, XlsxParser.MultipleSheetHandling.UseFirst);
		else if (FileUtils.hasExtension(file, "csv") == null)
			return new CsvParser(new BufferedReader(new FileReader(file)), ',', file.length());
		else if (FileUtils.hasExtension(file, "tsv") == null)
			return new CsvParser(new BufferedReader(new FileReader(file)), '\t', file.length());
		else
			throw new IllegalArgumentException(isFileTypeSupported(file.getName()));
	}

	/**
	 * @param file The file to parse
	 * @return A {@link TabularFileParser} for the given file
	 * @throws IOException If the file could not be read
	 * @throws TextParseException If the file could not be opened as a {@link TabularFileParser}
	 * @throws IllegalArgumentException If the file's type is not supported by a known {@link TabularFileParser} implementation
	 */
	@SuppressWarnings("resource")
	static TabularFileParser parse(BetterFile file) throws IOException, TextParseException, IllegalArgumentException {
		if (FileUtils.hasExtension(file.getName(), "xlsx") == null)
			return new XlsxParser(file, XlsxParser.MultipleSheetHandling.UseFirst);
		else if (FileUtils.hasExtension(file.getName(), "csv") == null)
			return new CsvParser(new InputStreamReader(file.read()), ',', file.length());
		else if (FileUtils.hasExtension(file.getName(), "tsv") == null)
			return new CsvParser(new InputStreamReader(file.read()), '\t', file.length());
		else
			throw new IllegalArgumentException(isFileTypeSupported(file.getName()));
	}

	/** A line produced by a {@link TypedLineParser}, containing structures parsed from the column values of a single line */
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
	 * A structure that facilitates the parsing of lines from a tabular file with a header into typed structures
	 * 
	 * @param <L> The sub-type of typed line this parser produces
	 */
	public interface TypedLineParser<L extends TypedLine> extends AutoCloseable {
		/** @return The file parser that this typed line parser was created by */
		TabularFileParser getFileParser();

		/** @return The header parsed from the file */
		List<String> getHeader();

		/**
		 * @param index The index of the column to check (according to the order in which it was
		 *        {@link TypedLineParser#with(Pattern, boolean, ExFunction) added} to the parser, not the order in the file)
		 * @return Whether the column was found in the file header. Always true for non-optional columns.
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
		 * @param index The index of the column in this line parser according to the order in which it was
		 *        {@link TypedLineParser#with(Pattern, boolean, ExFunction) added} to the parser
		 * @return The name option which matched the column header--zero if the primary option matched, or the 1+ the index in the
		 *         otherPossibilities var args
		 */
		int getColumnOption(int index);

		/**
		 * @return The next line in the file, or null if the file has no more content lines
		 * @throws IOException If the file could not be read
		 * @throws TextParseException If the line contains a syntax error, or if any of the parsers for the typed columns throws a
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
		<T> TypedLineParser<?> with(String column, boolean optional, ExFunction<String, ? extends T, ParseException> parser,
			String... otherPossibilities) throws TextParseException;

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
		<T> TypedLineParser<?> with2(String column, boolean optional, Function<String, ? extends T> parser,
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
		<T> TypedLineParser<?> with(Pattern column, boolean optional, ExFunction<String, ? extends T, ParseException> parser)
			throws TextParseException;

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
		<T> TypedLineParser<?> with2(Pattern column, boolean optional, Function<String, ? extends T> parser) throws TextParseException;

		@Override
		default void close() throws IOException {
			getFileParser().close();
		}
	}

	/** Abstract class with utilities useful to {@link TypedLineParser}s */
	public abstract class AbstractTypedLineParser {
		private final TabularFileParser theFileParser;
		
		/** The column names parsed from the header */
		protected final String[] theHeader;
		/** The most recently parsed line (for efficiency) */
		protected final String[] theLine;
		/** Whether letter case is ignored when matching column names for {@link #with(String, boolean, ExFunction, String...)} */
		protected final boolean isIgnoreCase;
		/** Whether space characters are ignored when matching column names for {@link #with(String, boolean, ExFunction, String...)} */
		protected final boolean isIgnoreSpace;

		AbstractTypedLineParser(TabularFileParser fileParser, String[] header, String[] line, boolean isIgnoreCase, boolean ignoreSpace) {
			theFileParser=fileParser;
			theHeader = header;
			theLine = line;
			this.isIgnoreCase = isIgnoreCase;
			this.isIgnoreSpace = ignoreSpace;
		}

		/** @return The file parser that this typed line parser was created by */
		public TabularFileParser getFileParser() {
			return theFileParser;
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

		int[] findColumn(String column, String[] otherPossibilities, boolean optional) throws TextParseException {
			column = reduce(column);
			for (int c = 0; c < otherPossibilities.length; c++)
				otherPossibilities[c] = reduce(otherPossibilities[c]);
			for (int c = 0; c < theHeader.length; c++) {
				boolean match;
				String h = reduce(theHeader[c]);
				match = h.equals(column);
				int optionIndex = 0;
				for (String other : otherPossibilities) {
					if (match)
						break;
					optionIndex++;
					match = h.equals(other);
				}
				if (match)
					return new int[] { c, optionIndex };
			}
			if (optional)
				return new int [] {-1, -1};
			else
				throw new TextParseException("No such column found: " + column, 0, 0, 0);
		}

		int[] findColumn(Pattern column, boolean optional) throws TextParseException {
			// First try without reducing
			for (int c = 0; c < theHeader.length; c++) {
				if (column.matcher(theHeader[c]).matches())
					return new int[] {c, 0};
			}
			for (int c = 0; c < theHeader.length; c++) {
				if (column.matcher(reduce(theHeader[c])).matches())
					return new int[] {c, 0};
			}
			if (optional)
				return new int [] {-1, -1};
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
		protected <T> TypedLineParser<?> with(String column, boolean optional, ExFunction<String, ? extends T, ParseException> parser,
			String... otherPossibilities) throws TextParseException {
			return with(findColumn(column, otherPossibilities, optional), parser);
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
		protected <T> TypedLineParser<?> with2(String column, boolean optional, Function<String, ? extends T> parser,
			String... otherPossibilities) throws TextParseException {
			return with(findColumn(column, otherPossibilities, optional), ExFunction.of(parser));
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
		protected <T> TypedLineParser<?> with(Pattern column, boolean optional, ExFunction<String, ? extends T, ParseException> parser)
			throws TextParseException {
			return with(findColumn(column, optional), parser);
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
		protected <T> TypedLineParser<?> with2(Pattern column, boolean optional, Function<String, ? extends T> parser)
			throws TextParseException {
			return with(findColumn(column, optional), ExFunction.of(parser));
		}

		/**
		 * Implementation for the {@link #with(String, boolean, ExFunction, String...)} methods
		 * 
		 * @param <T> The type of the column to parse
		 * @param columnAndOptionIndex A 2-element array containing
		 *        <ol>
		 *        <li>The index of the column (or -1 if the column was not present and optional)</li>
		 *        <li>The index of the option that matched the column name (for matches with multiple options)
		 * @param parser The parser to parse the column value
		 * @return A new parser that can parse lines containing the given column
		 */
		protected abstract <T> TypedLineParser<?> with(int[] columnAndOptionIndex, ExFunction<String, ? extends T, ParseException> parser);
	}

	/** A builder for {@link TypedLineParser}s */
	public class TypedLineParser0 extends AbstractTypedLineParser {
		TypedLineParser0(TabularFileParser fileParser, String [] header, String[] line, boolean isIgnoreCase, boolean isIgnoreSpace) {
			super(fileParser, header, line, isIgnoreCase, isIgnoreSpace);
		}

		/**
		 * @param ignoreCase Whether the parser should ignore case when looking for columns in the header
		 * @return A new parser with the given setting for respecting column name case
		 */
		public TypedLineParser0 ignoreCase(boolean ignoreCase) {
			return new TypedLineParser0(getFileParser(), theHeader, theLine, ignoreCase, isIgnoreSpace);
		}

		/**
		 * @param ignoreSpace Whether the parser should ignore spaces when looking for columns in the header
		 * @return A new parser with the given setting for respecting spaces in column names
		 */
		public TypedLineParser0 ignoreSpace(boolean ignoreSpace) {
			return new TypedLineParser0(getFileParser(), theHeader, theLine, isIgnoreCase, ignoreSpace);
		}

		@Override
		public <T> TypedLineParser1<T> with(String column, boolean optional, ExFunction<String, ? extends T, ParseException> parser,
			String... otherPossibilities) throws TextParseException {
			return (TypedLineParser1<T>) super.with(column, optional, parser, otherPossibilities);
		}

		@Override
		public <T> TypedLineParser1<T> with2(String column, boolean optional, Function<String, ? extends T> parser,
			String... otherPossibilities) throws TextParseException {
			return with(column, optional, ExFunction.of(parser), otherPossibilities);
		}

		@Override
		public <T> TypedLineParser1<T> with(Pattern column, boolean optional, ExFunction<String, ? extends T, ParseException> parser)
			throws TextParseException {
			return (TypedLineParser1<T>) super.with(column, optional, parser);
		}

		@Override
		public <T> TypedLineParser1<T> with2(Pattern column, boolean optional, Function<String, ? extends T> parser)
			throws TextParseException {
			return (TypedLineParser1<T>) super.with(column, optional, ExFunction.of(parser));
		}

		@Override
		protected <T> TypedLineParser1<T> with(int[] columnAndOptionIndex, ExFunction<String, ? extends T, ParseException> parser) {
			return new TypedLineParser1<>(getFileParser(), theHeader, theLine, isIgnoreCase, isIgnoreSpace, columnAndOptionIndex, parser);
		}
	}

	/**
	 * A {@link TypedLineParser} for a single typed column
	 * 
	 * @param <T> The type of the column
	 */
	public class TypedLineParser1<T> extends AbstractTypedLineParser implements TypedLineParser<SingleTypedLine<T>> {
		private final int theColumnIndex;
		private final int theOptionIndex;
		private final ExFunction<String, ? extends T, ParseException> theParser;

		TypedLineParser1(TabularFileParser fileParser, String[] header, String[] line, boolean ignoreCase, boolean ignoreSpace,
			int[] columnAndOptionIndex,
			ExFunction<String, ? extends T, ParseException> parser) {
			super(fileParser, header, line, ignoreCase, ignoreSpace);
			theColumnIndex = columnAndOptionIndex[0];
			theOptionIndex = columnAndOptionIndex[1];
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
		public int getColumnOption(int index) {
			if (index == 0)
				return theOptionIndex;
			else
				throw new IndexOutOfBoundsException(index + " of 1");
		}

		@Override
		public SingleTypedLine<T> parseNextLine() throws IOException, TextParseException {
			if (!getFileParser().parseNextLine(theLine))
				return null;
			if (theColumnIndex < 0)
				return SingleTypedLine.NULL();
			String text = theLine[theColumnIndex];
			if (text.isEmpty())
				return SingleTypedLine.NULL();
			try {
				return new SingleTypedLine<>(theParser.apply(theLine[theColumnIndex]));
			} catch (ParseException e) {
				getFileParser(). throwParseException(theColumnIndex, e.getErrorOffset(), e.getMessage());
				return null;
			} catch (RuntimeException e) {
				getFileParser(). throwParseException(theColumnIndex, 0, e.toString());
				return null;
			}
		}

		@Override
		public <U> TypedLineParser2<T, U> with(String column, boolean optional, ExFunction<String, ? extends U, ParseException> parser,
			String... otherPossibilities) throws TextParseException {
			return (TypedLineParser2<T, U>) super.with(column, optional, parser, otherPossibilities);
		}

		@Override
		public <U> TypedLineParser2<T, U> with2(String column, boolean optional, Function<String, ? extends U> parser,
			String... otherPossibilities) throws TextParseException {
			return (TypedLineParser2<T, U>) super.with(column, optional, ExFunction.of(parser), otherPossibilities);
		}

		@Override
		public <U> TypedLineParser2<T, U> with(Pattern column, boolean optional, ExFunction<String, ? extends U, ParseException> parser)
			throws TextParseException {
			return (TypedLineParser2<T, U>) super.with(column, optional, parser);
		}

		@Override
		public <U> TypedLineParser2<T, U> with2(Pattern column, boolean optional, Function<String, ? extends U> parser)
			throws TextParseException {
			return (TypedLineParser2<T, U>) super.with(column, optional, ExFunction.of(parser));
		}

		@Override
		protected <U> TypedLineParser2<T, U> with(int[] columnAndOptionIndex, ExFunction<String, ? extends U, ParseException> parser) {
			return new TypedLineParser2<>(getFileParser(), theHeader, theLine, isIgnoreCase, isIgnoreSpace, theColumnIndex, theOptionIndex,
				columnAndOptionIndex, theParser, parser);
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
		private final int theOptionIndex1;
		private final int theColumnIndex2;
		private final int theOptionIndex2;
		private final ExFunction<String, ? extends T, ParseException> theParser1;
		private final ExFunction<String, ? extends U, ParseException> theParser2;

		TypedLineParser2(TabularFileParser fileParser, String[] header, String[] line, boolean ignoreCase, boolean ignoreSpace,
			int columnIndex1, int optionIndex1, int[] columnAndOptionIndex2,
			ExFunction<String, ? extends T, ParseException> parser1, ExFunction<String, ? extends U, ParseException> parser2) {
			super(fileParser, header, line, ignoreCase, ignoreSpace);
			theColumnIndex1 = columnIndex1;
			theOptionIndex1 = optionIndex1;
			theColumnIndex2 = columnAndOptionIndex2[0];
			theOptionIndex2 = columnAndOptionIndex2[1];
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
		public int getColumnOption(int index) {
			switch (index) {
			case 0:
				return theOptionIndex1;
			case 1:
				return theOptionIndex2;
			default:
				throw new IndexOutOfBoundsException(index + " of 2");
			}
		}

		@Override
		public DoubleTypedLine<T, U> parseNextLine() throws IOException, TextParseException {
			if (!getFileParser().parseNextLine(theLine))
				return null;
			String text;
			T value1;
			text = theLine[theColumnIndex1];
			try {
				value1 = text.isEmpty() ? null : theParser1.apply(text);
			} catch (ParseException e) {
				getFileParser(). throwParseException(theColumnIndex1, e.getErrorOffset(), e.getMessage());
				return null;
			} catch (RuntimeException e) {
				getFileParser(). throwParseException(theColumnIndex1, 0, e.toString());
				return null;
			}
			U value2;
			text = theLine[theColumnIndex2];
			try {
				value2 = text.isEmpty() ? null : theParser2.apply(text);
			} catch (ParseException e) {
				getFileParser(). throwParseException(theColumnIndex2, e.getErrorOffset(), e.getMessage());
				return null;
			} catch (RuntimeException e) {
				getFileParser(). throwParseException(theColumnIndex2, 0, e.toString());
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
		public <V> TypedLineParser3<T, U, V> with2(String column, boolean optional, Function<String, ? extends V> parser,
			String... otherPossibilities) throws TextParseException {
			return (TypedLineParser3<T, U, V>) super.with(column, optional, ExFunction.of(parser), otherPossibilities);
		}

		@Override
		public <V> TypedLineParser3<T, U, V> with(Pattern column, boolean optional, ExFunction<String, ? extends V, ParseException> parser)
			throws TextParseException {
			return (TypedLineParser3<T, U, V>) super.with(column, optional, parser);
		}

		@Override
		public <V> TypedLineParser3<T, U, V> with2(Pattern column, boolean optional, Function<String, ? extends V> parser)
			throws TextParseException {
			return (TypedLineParser3<T, U, V>) super.with(column, optional, ExFunction.of(parser));
		}

		@Override
		protected <V> TypedLineParser3<T, U, V> with(int[] columnAndOptionIndex, ExFunction<String, ? extends V, ParseException> parser) {
			return new TypedLineParser3<>(getFileParser(), theHeader, theLine, isIgnoreCase, isIgnoreSpace, //
				theColumnIndex1, theOptionIndex1, theColumnIndex2, theOptionIndex2, columnAndOptionIndex, //
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
		private final int theOptionIndex1;
		private final int theColumnIndex2;
		private final int theOptionIndex2;
		private final int theColumnIndex3;
		private final int theOptionIndex3;
		private final ExFunction<String, ? extends T, ParseException> theParser1;
		private final ExFunction<String, ? extends U, ParseException> theParser2;
		private final ExFunction<String, ? extends V, ParseException> theParser3;

		TypedLineParser3(TabularFileParser fileParser, String[] header, String[] line, boolean ignoreCase, boolean ignoreSpace,
			int columnIndex1, int optionIndex1, int columnIndex2, int optionIndex2, int[] columnAndOptionIndex3,
			ExFunction<String, ? extends T, ParseException> parser1, ExFunction<String, ? extends U, ParseException> parser2,
			ExFunction<String, ? extends V, ParseException> parser3) {
			super(fileParser, header, line, ignoreCase, ignoreSpace);
			theColumnIndex1 = columnIndex1;
			theOptionIndex1 = optionIndex1;
			theColumnIndex2 = columnIndex2;
			theOptionIndex2 = optionIndex2;
			theColumnIndex3 = columnAndOptionIndex3[0];
			theOptionIndex3 = columnAndOptionIndex3[1];
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
		public int getColumnOption(int index) {
			switch (index) {
			case 0:
				return theOptionIndex1;
			case 1:
				return theOptionIndex2;
			case 3:
				return theOptionIndex3;
			default:
				throw new IndexOutOfBoundsException(index + " of 3");
			}
		}

		@Override
		public TripleTypedLine<T, U, V> parseNextLine() throws IOException, TextParseException {
			if (!getFileParser().parseNextLine(theLine))
				return null;
			String text;
			T value1;
			text = theLine[theColumnIndex1];
			try {
				value1 = text.isEmpty() ? null : theParser1.apply(text);
			} catch (ParseException e) {
				getFileParser().throwParseException(theColumnIndex1, e.getErrorOffset(), e.getMessage());
				return null;
			} catch (RuntimeException e) {
				getFileParser().throwParseException(theColumnIndex1, 0, e.toString());
				return null;
			}
			U value2;
			text = theLine[theColumnIndex2];
			try {
				value2 = text.isEmpty() ? null : theParser2.apply(text);
			} catch (ParseException e) {
				getFileParser().throwParseException(theColumnIndex2, e.getErrorOffset(), e.getMessage());
				return null;
			} catch (RuntimeException e) {
				getFileParser().throwParseException(theColumnIndex2, 0, e.toString());
				return null;
			}
			V value3;
			text = theLine[theColumnIndex3];
			try {
				value3 = text.isEmpty() ? null : theParser3.apply(text);
			} catch (ParseException e) {
				getFileParser().throwParseException(theColumnIndex3, e.getErrorOffset(), e.getMessage());
				return null;
			} catch (RuntimeException e) {
				getFileParser().throwParseException(theColumnIndex3, 0, e.toString());
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
		public <X> TypedLineParserN<T, U, V> with2(String column, boolean optional, Function<String, ? extends X> parser,
			String... otherPossibilities) throws TextParseException {
			return (TypedLineParserN<T, U, V>) super.with(column, optional, ExFunction.of(parser), otherPossibilities);
		}

		@Override
		public <X> TypedLineParserN<T, U, V> with(Pattern column, boolean optional, ExFunction<String, ? extends X, ParseException> parser)
			throws TextParseException {
			return (TypedLineParserN<T, U, V>) super.with(column, optional, parser);
		}

		@Override
		public <X> TypedLineParserN<T, U, V> with2(Pattern column, boolean optional, Function<String, ? extends X> parser)
			throws TextParseException {
			return (TypedLineParserN<T, U, V>) super.with(column, optional, ExFunction.of(parser));
		}

		@Override
		protected <X> TypedLineParserN<T, U, V> with(int[] columnAndOptionIndex, ExFunction<String, ? extends X, ParseException> parser) {
			return new TypedLineParserN<T, U, V>(getFileParser(), theHeader, theLine, isIgnoreCase, isIgnoreSpace, //
				new int[] { theColumnIndex1, theColumnIndex2, theColumnIndex3, columnAndOptionIndex[0] }, //
				new int[] { theOptionIndex1, theOptionIndex2, theOptionIndex3, columnAndOptionIndex[1] }, //
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
		private final int[] theOptionIndices;
		private final ExFunction<String, ?, ParseException>[] theParsers;

		TypedLineParserN(TabularFileParser fileParser, String[] header, String[] line, boolean ignoreCase, boolean ignoreSpace,
			int[] columnIndices, int[] optionIndices,
			ExFunction<String, ?, ParseException>[] parsers) {
			super(fileParser, header, line, ignoreCase, ignoreSpace);
			theColumnIndices = columnIndices;
			theOptionIndices = optionIndices;
			theParsers = parsers;
		}

		@Override
		public int translateColumn(int index) {
			return theColumnIndices[index];
		}

		@Override
		public int getColumnOption(int index) {
			return theOptionIndices[index];
		}

		@Override
		public NTypedLine<T, U, V> parseNextLine() throws IOException, TextParseException {
			if (!getFileParser().parseNextLine(theLine))
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
					getFileParser().throwParseException(c, e.getErrorOffset(), e.getMessage());
				} catch (RuntimeException e) {
					getFileParser().throwParseException(theColumnIndices[c], 0, e.toString());
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
		public <X> TypedLineParserN<T, U, V> with2(String column, boolean optional, Function<String, ? extends X> parser,
			String... otherPossibilities) throws TextParseException {
			return (TypedLineParserN<T, U, V>) super.with(column, optional, ExFunction.of(parser), otherPossibilities);
		}

		@Override
		public <X> TypedLineParserN<T, U, V> with(Pattern column, boolean optional, ExFunction<String, ? extends X, ParseException> parser)
			throws TextParseException {
			return (TypedLineParserN<T, U, V>) super.with(column, optional, parser);
		}

		@Override
		public <X> TypedLineParserN<T, U, V> with2(Pattern column, boolean optional, Function<String, ? extends X> parser)
			throws TextParseException {
			return (TypedLineParserN<T, U, V>) super.with(column, optional, ExFunction.of(parser));
		}

		@Override
		protected <X> TypedLineParserN<T, U, V> with(int[] columnAndOptionIndex, ExFunction<String, ? extends X, ParseException> parser) {
			int[] newColumns = Arrays.copyOf(theColumnIndices, theColumnIndices.length + 1);
			int[] newOptions = Arrays.copyOf(theOptionIndices, theOptionIndices.length + 1);
			newColumns[theColumnIndices.length] = columnAndOptionIndex[0];
			newOptions[theOptionIndices.length] = columnAndOptionIndex[1];
			return new TypedLineParserN<>(getFileParser(), theHeader, theLine, isIgnoreCase, isIgnoreSpace, //
				newColumns, newOptions, ArrayUtils.add(theParsers, parser));
		}
	}
}

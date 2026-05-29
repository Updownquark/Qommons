package org.qommons.io;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.qommons.LongList;

/**
 * A {@link TabularFileParser} in which values are separated by any number of spaces. This format is nice because the spacing can be
 * arranged such that the columns line up, making the format more human-readable.
 */
public class SpacedTabularFormat implements TabularFileParser {
	private final Reader theReader;
	private final long theFileLength;
	private int theCurrentLine;
	private int theLastLine;
	private long theLastLineOffset;
	private long theCurrentOffset;
	private int theRowCount;
	private final LongList theColumnOffsets;

	/**
	 * @param reader The reader to retrieve the data from
	 * @param fileLength The length of the file (may be -1, only needed for {@link #getFileLength()} and {@link #getParseProgress()})
	 */
	public SpacedTabularFormat(Reader reader, long fileLength) {
		theReader = reader;
		theLastLine = -1;
		theFileLength = fileLength;
		theColumnOffsets = new LongList();
	}

	@SuppressWarnings("null")
	@Override
	public String[] parseNextLineVC(String[] columns) throws IOException, TextParseException {
		StringBuilder column = new StringBuilder();
		int index = 0;
		List<String> columnsList = columns == null ? new ArrayList<>() : null;
		int ch = read();
		boolean moreContent = true;
		while (moreContent) {
			switch (ch) {
			case -1:
				moreContent = false;
				break;
			case '\n':
				if (index > 0)
					moreContent = false;
				break;
			default:
				if (ch <= ' ') {
					if (column.length() > 0) {
						if (columnsList != null)
							columnsList.add(column.toString());
						else if (index < columns.length)
							columns[index++] = column.toString();
						else {
							columnsList = new ArrayList<>(Math.max(10, columns.length * 2));
							for (String c : columns)
								columnsList.add(c);
							columnsList.add(column.toString());
						}
						column.setLength(0);
					}
				} else if (column.length() > 0) {
					column.append((char) ch);
				} else {
					if (index == 0) { // First content
						theLastLine = theCurrentLine;
						theLastLineOffset = theCurrentOffset;
						theRowCount++;
						theColumnOffsets.clear();
					}
					if (column.length() == 0)
						theColumnOffsets.add(theCurrentOffset);
					column.append((char) ch);
				}
			}
			if (moreContent)
				ch = read();
		}
		if (column.length() > 0) {
			if (columnsList != null)
				columnsList.add(column.toString());
			else if (index < columns.length)
				columns[index++] = column.toString();
			else {
				columnsList = new ArrayList<>(Math.max(10, columns.length * 2));
				for (String c : columns)
					columnsList.add(c);
				columnsList.add(column.toString());
			}
		}
		if (columnsList != null)
			return columnsList.toArray(new String[columnsList.size()]);
		else if (index > 0) {
			if (index < columns.length)
				Arrays.fill(columns, index, columns.length, null);
			return columns;
		} else
			return null;
	}

	@Override
	public boolean parseNextLine(String[] columns) throws IOException, TextParseException {
		StringBuilder column = new StringBuilder();
		int c = 0;
		theLastLineOffset = theCurrentOffset;
		theColumnOffsets.clear();
		int ch = read();
		boolean moreContent = true;
		while (moreContent) {
			switch (ch) {
			case -1:
				moreContent = false;
				break;
			case '\n':
				if (c > 0)
					moreContent = false;
				break;
			default:
				if (ch <= ' ') {
					if (column.length() > 0) {
						columns[c++] = column.toString();
						column.setLength(0);
					}
				} else if (column.length() > 0) {
					column.append((char) ch);
				} else if (c < columns.length) {
					if (c == 0) { // First content
						theLastLine = theCurrentLine;
						theLastLineOffset = theCurrentOffset;
						theRowCount++;
						theColumnOffsets.clear();
					}
					if (column.length() == 0)
						theColumnOffsets.add(theCurrentOffset);
					column.append((char) ch);
				} else
					throw new TextParseException(columns.length + " columns expected, but more encountered", (int) theCurrentOffset,
						theCurrentLine, c);
			}
			if (moreContent)
				ch = read();
		}
		if (column.length() > 0)
			columns[c++] = column.toString();
		if (c > 0 && c < columns.length)
			throw new TextParseException(columns.length + " columns expected, but only encountered " + c, (int) theCurrentOffset,
				theCurrentLine, c);
		return c > 0;
	}

	@Override
	public int getPassedBlankLines() {
		return theCurrentLine - theLastLine - 1;
	}

	@Override
	public int getEntryNumber() {
		return theRowCount;
	}

	@Override
	public int getLastLineNumber() {
		return theLastLine;
	}

	@Override
	public long getLastLineOffset() {
		return theLastLineOffset;
	}

	@Override
	public int getCurrentLineNumber() {
		return theCurrentLine;
	}

	@Override
	public long getCurrentOffset() {
		return theCurrentOffset;
	}

	@Override
	public long getColumnOffset(int columnIndex) {
		return theColumnOffsets.get(columnIndex);
	}

	@Override
	public long getFileLength() {
		return theFileLength;
	}

	@Override
	public long getParseProgress() {
		return theCurrentOffset;
	}

	@Override
	public void close() throws IOException {
		theReader.close();
	}

	private int read() throws IOException {
		int ch = theReader.read();
		theCurrentOffset++;
		if (ch == '\n')
			theCurrentLine++;
		return ch;
	}
}

package org.qommons.io;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.qommons.LongList;

public class SpacedTabularFormat implements TabularFileParser {
	private final Reader theReader;
	private final long theFileLength;
	private int theCurrentLine;
	private int theLastLine;
	private long theLastLineOffset;
	private long theCurrentOffset;
	private int theRowCount;
	private final LongList theColumnOffsets;

	public SpacedTabularFormat(Reader reader, long fileLength) {
		theReader = reader;
		theLastLine = -1;
		theFileLength = fileLength;
		theColumnOffsets = new LongList();
	}

	@Override
	public String[] parseNextLine() throws IOException, TextParseException {
		StringBuilder column = new StringBuilder();
		List<String> columns = new ArrayList<>();
		int ch = read();
		boolean moreContent = true;
		while (moreContent) {
			switch (ch) {
			case -1:
				moreContent = false;
				break;
			case '\n':
				if (!columns.isEmpty())
					moreContent = false;
				break;
			default:
				if (ch <= ' ') {
					if (column.length() > 0) {
						columns.add(column.toString());
						column.setLength(0);
					}
				} else if (column.length() > 0) {
					column.append((char) ch);
				} else {
					if (columns.isEmpty()) { // First content
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
		if (column.length() > 0)
			columns.add(column.toString());
		if (!columns.isEmpty())
			return columns.toArray(new String[columns.size()]);
		else
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

package org.qommons.io;

/** The position of a single character in a text file */
public class FilePosition {
	public static final FilePosition START = new FilePosition(0, 0, 0);

	private final int thePosition;
	private final int theLineNumber;
	private final int theCharNumber;

	/**
	 * @param position The absolute character position in the file
	 * @param lineNumber The line number in the file, indexed from zero
	 * @param charNumber The character number (within the line) in the file, indexed from zero
	 */
	public FilePosition(int position, int lineNumber, int charNumber) {
		thePosition = position;
		theLineNumber = lineNumber;
		theCharNumber = charNumber;
	}

	/** @return The absolute character position in the file */
	public int getPosition() {
		return thePosition;
	}

	/** @return The line number in the file, indexed from zero */
	public int getLineNumber() {
		return theLineNumber;
	}

	/** @return The character number (within the line) in the file, indexed from zero */
	public int getCharNumber() {
		return theCharNumber;
	}

	@Override
	public int hashCode() {
		return thePosition;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof FilePosition && thePosition == ((FilePosition) obj).thePosition;
	}

	@Override
	public String toString() {
		return new StringBuilder("L").append(theLineNumber + 1).append(",C").append(theCharNumber + 1).toString();
	}
}
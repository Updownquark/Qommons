package org.qommons.io;

/** A file position that also specifies the location of the document */
public class LocatedFilePosition extends FilePosition {
	private final String theFileLocation;

	/**
	 * @param fileLocation The location of the document
	 * @param position the location of this position within the document
	 */
	public LocatedFilePosition(String fileLocation, FilePosition position) {
		super(position.getPosition(), position.getLineNumber(), position.getCharNumber());
		theFileLocation = fileLocation;
	}

	/**
	 * @param fileLocation The location of the document
	 * @param position The absolute position within the document
	 * @param lineNumber The line number within the document
	 * @param charNumber The character number within the line
	 */
	public LocatedFilePosition(String fileLocation, int position, int lineNumber, int charNumber) {
		super(position, lineNumber, charNumber);
		theFileLocation = fileLocation;
	}

	/** @return The location of the document */
	public String getFileLocation() {
		return theFileLocation;
	}

	@Override
	public String toString() {
		if (theFileLocation == null)
			return super.toString();
		else
			return theFileLocation + "@" + super.toString();
	}
}

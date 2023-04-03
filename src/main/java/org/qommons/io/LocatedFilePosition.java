package org.qommons.io;

public class LocatedFilePosition extends FilePosition {
	private final String theFileLocation;

	public LocatedFilePosition(String fileLocation, FilePosition position) {
		super(position.getPosition(), position.getLineNumber(), position.getCharNumber());
		theFileLocation = fileLocation;
	}

	public LocatedFilePosition(String fileLocation, int position, int lineNumber, int charNumber) {
		super(position, lineNumber, charNumber);
		theFileLocation = fileLocation;
	}

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

package org.qommons.config;

import org.qommons.io.SimpleXMLParser.FilePosition;

public class QonfigFilePosition extends FilePosition {
	private final String theFileLocation;

	public QonfigFilePosition(String fileLocation, FilePosition position) {
		super(position.getPosition(), position.getLineNumber(), position.getCharNumber());
		theFileLocation = fileLocation;
	}
}

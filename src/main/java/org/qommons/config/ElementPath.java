package org.qommons.config;

import org.qommons.io.FilePosition;
import org.qommons.io.LocatedFilePosition;

/** Represents a path through XML structure for identifying the location of errors, etc. */
public class ElementPath implements FileSourced {
	private final ElementPath theParent;
	private final String theFile;
	private final String theElementName;
	private final FilePosition thePosition;

	private ElementPath(ElementPath parent, String file, String elementName, FilePosition position) {
		theParent = parent;
		theFile = file;
		theElementName = elementName;
		thePosition = position;
	}

	/**
	 * @param childName The name of the child to create the path for
	 * @param position The position in the file where the element was defined
	 * @return An element path for the child
	 */
	public ElementPath forChild(String childName, FilePosition position) {
		return new ElementPath(this, theFile, childName, position);
	}

	/** @return This path's parent */
	public ElementPath getParent() {
		return theParent;
	}

	/** @return The name of this path's element */
	public String getElementName() {
		return theElementName;
	}

	@Override
	public LocatedFilePosition getFilePosition() {
		return new LocatedFilePosition(theFile, thePosition);
	}

	@Override
	public String toString() {
		return getFilePosition().toString();
	}

	/**
	 * @param str The string builder to append this path to
	 * @return The given string builder
	 */
	public StringBuilder toString(StringBuilder str) {
		if (theParent != null) {
			theParent.toString(str).append('/').append(theElementName);
		} else
			str.append(theElementName);
		return str;
	}

	/**
	 * Creates a path for the root element
	 * 
	 * @param fileLocation The location of the file containing the element
	 * @param rootName The name of the root element
	 * @param position The position in the file where the element was defined
	 * @return The root path
	 */
	public static ElementPath forRoot(String fileLocation, String rootName, FilePosition position) {
		return new ElementPath(null, fileLocation, rootName, position);
	}
}

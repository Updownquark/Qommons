package org.qommons.config;

/** Represents a path through XML structure for identifying the location of errors, etc. */
public class ElementPath {
	private final ElementPath theParent;
	private final String theElementName;
	private final Object theIdentifier;
	private final int theLineNumber;

	private ElementPath(ElementPath parent, String elementName, Object identifier, int lineNumber) {
		theParent = parent;
		theElementName = elementName;
		theIdentifier = identifier;
		theLineNumber = lineNumber;
	}

	/**
	 * @param childName The name of the child to create the path for
	 * @param identifier An identifier for the child (in case multiple children with the same name exist in the parent)
	 * @param lineNumber The line number in the file where the element was defined
	 * @return An element path for the child
	 */
	public ElementPath forChild(String childName, Object identifier, int lineNumber) {
		return new ElementPath(this, childName, identifier, lineNumber);
	}

	/** @return This path's parent */
	public ElementPath getParent() {
		return theParent;
	}

	/** @return The name of this path's element */
	public String getElementName() {
		return theElementName;
	}

	/** @return The identifier identifying this path's element among its siblings */
	public Object getIdentifier() {
		return theIdentifier;
	}

	/** @return The line number where this path's element was defined */
	public int getLineNumber() {
		return theLineNumber;
	}

	@Override
	public String toString() {
		return toString(new StringBuilder()).toString();
	}

	/**
	 * @param str The string builder to append this path to
	 * @return The given string builder
	 */
	public StringBuilder toString(StringBuilder str) {
		if (theParent != null) {
			theParent.toString(str).append('/').append(theElementName);
			if (theIdentifier != null)
				str.append('[').append(theIdentifier).append(']');
		} else
			str.append(theElementName);
		return str;
	}

	/**
	 * Creates a path for the root element
	 * 
	 * @param rootName The name of the root element
	 * @param lineNumber The line number in the file where the element was defined
	 * @return The root path
	 */
	public static ElementPath forRoot(String rootName, int lineNumber) {
		return new ElementPath(null, rootName, 0, lineNumber);
	}
}

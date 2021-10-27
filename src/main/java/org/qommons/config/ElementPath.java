package org.qommons.config;

/** Represents a path through XML structure for identifying the location of errors, etc. */
public class ElementPath {
	private final ElementPath theParent;
	private final String theElementName;
	private final Object theIdentifier;

	private ElementPath(ElementPath parent, String elementName, Object identifier) {
		theParent = parent;
		theElementName = elementName;
		theIdentifier = identifier;
	}

	/**
	 * @param childName The name of the child to create the path for
	 * @param identifier An identifier for the child (in case multiple children with the same name exist in the parent)
	 * @return An element path for the child
	 */
	public ElementPath forChild(String childName, Object identifier) {
		return new ElementPath(this, childName, identifier);
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
	 * @return The root path
	 */
	public static ElementPath forRoot(String rootName) {
		return new ElementPath(null, rootName, 0);
	}
}

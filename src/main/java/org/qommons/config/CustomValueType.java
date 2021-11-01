package org.qommons.config;

import org.qommons.Named;

/** Allows injection of custom value types into toolkits */
public interface CustomValueType extends Named {
	/**
	 * Parses a value
	 * 
	 * @param value The text to parse
	 * @param tk The toolkit that the document belongs to
	 * @param session The session to report errors
	 * @return The parsed value
	 */
	Object parse(String value, QonfigToolkit tk, QonfigParseSession session);

	/**
	 * @param value The value to test
	 * @return Whether the given value is an instance of this type
	 */
	boolean isInstance(Object value);
}

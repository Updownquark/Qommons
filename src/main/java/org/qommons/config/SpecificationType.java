package org.qommons.config;

/** Defines whether a user can or must specify an attribute or element text value */
public enum SpecificationType {
	/** The user is not allowed to specify the value--the value specified by the type will be used */
	Forbidden,
	/** A default is provided, the user may override it */
	Optional,
	/** The user must specify the value */
	Required;

	/**
	 * @param attrValue The attribute text for the specification type
	 * @param session The session for error reporting
	 * @return The parsed specification type
	 */
	public static SpecificationType fromAttributeValue(String attrValue, QonfigParseSession session) {
		if (attrValue == null)
			return null;
		for (SpecificationType spec : SpecificationType.values())
			if (spec.name().toLowerCase().equals(attrValue))
				return spec;
		session.withError("Illegal specify value: " + attrValue);
		return null;
	}
}

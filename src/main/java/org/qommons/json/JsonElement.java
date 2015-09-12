/*
 * JsonElement.java Created Dec 21, 2009 by Andrew Butler, PSL
 */
package org.qommons.json;

/**
 * An element of a data structure that can validate a piece of JSON
 */
public interface JsonElement
{
	/**
	 * Configures the element for parsing
	 * 
	 * @param parser The parser that generated this element
	 * @param parent The parent for this element
	 * @param elementName The name for this element
	 * @param schemaEl The schema element that this element is based on
	 */
	void configure(JsonSchemaParser parser, JsonElement parent, String elementName,
		org.json.simple.JSONObject schemaEl);

	/**
	 * @return The parser that generated this element
	 */
	JsonSchemaParser getParser();

	/**
	 * @return This element's parent
	 */
	JsonElement getParent();

	/**
	 * @return This element's name
	 */
	String getName();

	/**
	 * @param jsonValue The value to validate
	 * @return The amount of this schema that the element matches (1=perfect match)
	 */
	float doesValidate(Object jsonValue);

	/**
	 * @param jsonValue The value to validate
	 * @return True if the element has been absolutely validated, false if it may require further
	 *         validation by a subclass
	 * @throws JsonSchemaException If the element is invalid
	 */
	boolean validate(Object jsonValue) throws JsonSchemaException;

	/**
	 * @return A string describing the location of this element within the json structure.
	 */
	String getPathString();
}

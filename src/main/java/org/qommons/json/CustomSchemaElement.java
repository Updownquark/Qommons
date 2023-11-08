/*
 * CustomSchemaElement.java Created Dec 21, 2009 by Andrew Butler, PSL
 */
package org.qommons.json;

/** An element that validates by a custom schema */
public class CustomSchemaElement extends DefaultJsonElement {
	private String theSchemaName;

	private String theSchemaLocation;

	private JsonElement theSchemaEl;

	/**
	 * Creates a custom schema element
	 * 
	 * @param schemaName The name of the custom schema
	 * @param schemaLocation The location of the schema
	 */
	public CustomSchemaElement(String schemaName, String schemaLocation) {
		theSchemaName = schemaName;
		theSchemaLocation = schemaLocation;
	}

	/** @return The name of the custom schema */
	public String getSchemaName() {
		return theSchemaName;
	}

	/** @return The location of the custom schema definition */
	public String getSchemaLocation() {
		return theSchemaLocation;
	}

	@Override
	public void configure(JsonSchemaParser parser, JsonElement parent, String name, JsonObject schemaEl) {
		super.configure(parser, parent, name, schemaEl);
	}

	/** @return The schema element that this custom schema points to and which will be validated against */
	public JsonElement getSchemaElement() {
		return theSchemaEl;
	}

	@Override
	public float doesValidate(Object jsonValue) {
		float ret = super.doesValidate(jsonValue);
		if (ret < 1)
			return ret;
		if (jsonValue == null)
			return 1;
		if (!(jsonValue instanceof JsonObject))
			return 0;
		if (theSchemaEl == null) {
			try {
				load();
			} catch (JsonSchemaException e) {
				throw new IllegalStateException("Could not parse schema for element " + this, e);
			}
		}
		return theSchemaEl.doesValidate(jsonValue);
	}

	@Override
	public boolean validate(Object jsonValue) throws JsonSchemaException {
		if (super.validate(jsonValue))
			return true;
		// if(!(jsonValue instanceof JsonObject))
		// throw new JsonSchemaException("Element must be a set", this, jsonValue);
		if (theSchemaEl == null)
			load();
		return theSchemaEl.validate(jsonValue);
	}

	/**
	 * Loads this custom schema
	 * 
	 * @throws JsonSchemaException If this element's schema cannot be parsed
	 */
	protected void load() throws JsonSchemaException {
		try {
			theSchemaEl = getParser().getExternalSchema(theSchemaName, theSchemaLocation, this);
		} catch (RuntimeException e) {
			throw new JsonSchemaException(e.getMessage(), this, null, e);
		}
	}
}

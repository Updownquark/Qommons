/*
 * ConstantElement.java Created Dec 21, 2009 by Andrew Butler, PSL
 */
package org.qommons.json;

import org.json.simple.JSONObject;

/** Represents an element that must have a constant value */
public class ConstantElement implements JsonElement
{
	private JsonSchemaParser theParser;

	private JsonElement theParent;

	private String theName;

	private Object theValue;

	/** @param value The value for this constant */
	public ConstantElement(Object value)
	{
		theValue = value;
	}

	public void configure(JsonSchemaParser parser, JsonElement parent, String elementName,
		JSONObject schemaEl)
	{
		theParser = parser;
		theParent = parent;
		theName = elementName;
	}

	/** @return The value for this constant */
	public Object getValue()
	{
		return theValue;
	}

	public JsonSchemaParser getParser()
	{
		return theParser;
	}

	public JsonElement getParent()
	{
		return theParent;
	}

	public String getName()
	{
		return theName;
	}

	public float doesValidate(Object jsonValue)
	{
		if(theValue == null)
			return jsonValue == null ? 1 : 0;
		if(jsonValue instanceof Number && theValue instanceof Number)
		{
			if(theValue instanceof Integer || theValue instanceof Long)
			{
				if(jsonValue instanceof Integer || jsonValue instanceof Long)
				{
					if(((Number) theValue).longValue() == ((Number) jsonValue).longValue())
						return 1;
					return 0.5f;
				}
				else if(jsonValue instanceof Float || jsonValue instanceof Double)
				{
					if(((Number) theValue).doubleValue() == ((Number) jsonValue).doubleValue())
						return 1;
					return 0.5f;
				}
				else if(theValue.equals(jsonValue))
					return 1;
				else
					return 0.25f;
			}
		}
		if(theValue.equals(jsonValue))
			return 1;
		if(theValue.getClass().isAssignableFrom(jsonValue.getClass()))
			return 0.5f;
		return 0;
	}

	public boolean validate(Object jsonValue) throws JsonSchemaException
	{
		if(theValue == null ? jsonValue == null : theValue.equals(jsonValue))
			return true;
		throw new JsonSchemaException(theValue + " expected, but value is " + jsonValue, this,
			jsonValue);
	}

	public String getPathString()
	{
		StringBuilder ret = new StringBuilder();
		ret.append(toString());
		JsonElement parent = theParent;
		while(parent != null)
		{
			ret.insert(0, parent.toString() + "/");
			parent = parent.getParent();
		}
		return ret.toString();
	}

	@Override
	public String toString()
	{
		return getName() + "("
			+ getClass().getName().substring(getClass().getName().lastIndexOf('.') + 1) + ")";
	}
}

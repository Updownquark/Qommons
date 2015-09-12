/*
 * EnumElement.java Created Dec 21, 2009 by Andrew Butler, PSL
 */
package org.qommons.json;

import org.json.simple.JSONObject;

/** Validates an element that could take one of several types of values */
public class EnumElement extends ContainerJsonElement
{
	@Override
	public void configure(JsonSchemaParser parser, JsonElement parent, String name,
		JSONObject schemaEl)
	{
		super.configure(parser, parent, name, schemaEl);
		if(getChildren().length == 0)
			throw new IllegalArgumentException("A oneOf element must have at least one option");
	}

	@Override
	public float doesValidate(Object jsonValue)
	{
		float ret = super.doesValidate(jsonValue);
		if(ret < 1)
			return ret;
		if(jsonValue == null)
			return 1;
		ret = 0;
		for(JsonElement el : getChildren())
		{
			float elVal = el.doesValidate(jsonValue);
			if(elVal == 1)
				return 1;
			if(elVal > ret)
				ret = elVal;
		}
		return ret;
	}

	@Override
	public boolean validate(Object jsonValue) throws JsonSchemaException
	{
		if(super.validate(jsonValue))
			return true;
		if(jsonValue == null)
			return true;
		float ret = 0;
		JsonElement closeChild = null;
		for(JsonElement el : getChildren())
		{
			float elVal = el.doesValidate(jsonValue);
			if(elVal == 1)
				return true;
			if(elVal > ret)
			{
				ret = elVal;
				closeChild = el;
			}
		}
		if(closeChild == null)
			throw new JsonSchemaException("Element does not match any schema option: " + jsonValue,
				this, jsonValue);
		// Doesn't match any options. Validate the closest one so we can debug it.
		return closeChild.validate(jsonValue);
	}
}

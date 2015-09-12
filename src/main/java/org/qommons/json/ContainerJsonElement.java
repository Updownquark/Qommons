/*
 * ContainerJsonElement.java Created Dec 21, 2009 by Andrew Butler, PSL
 */
package org.qommons.json;

import org.json.simple.JSONObject;

/**
 * Eases parsing for elements that have children in a "values" array
 */
public abstract class ContainerJsonElement extends DefaultJsonElement
{
	private JsonElement [] theChildren;

	@Override
	public void configure(JsonSchemaParser parser, JsonElement parent, String name,
		JSONObject schemaEl)
	{
		super.configure(parser, parent, name, schemaEl);
		org.json.simple.JSONArray values = (org.json.simple.JSONArray) schemaEl.get("values");
		theChildren = new JsonElement [values.size()];
		for(int i = 0; i < theChildren.length; i++)
			theChildren[i] = parser.parseSchema(this, "[" + i + "]", values.get(i));
	}

	/**
	 * @return This element's children
	 */
	public JsonElement [] getChildren()
	{
		return theChildren;
	}
}

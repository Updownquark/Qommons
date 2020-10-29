/*
 * ContainerJsonElement.java Created Dec 21, 2009 by Andrew Butler, PSL
 */
package org.qommons.json;

import java.util.List;

/** Eases parsing for elements that have children in a "values" array */
public abstract class ContainerJsonElement extends DefaultJsonElement {
	private JsonElement[] theChildren;

	@Override
	public void configure(JsonSchemaParser parser, JsonElement parent, String name, JsonObject schemaEl) {
		super.configure(parser, parent, name, schemaEl);
		List<?> values = (List<?>) schemaEl.get("values");
		theChildren = new JsonElement[values.size()];
		for (int i = 0; i < theChildren.length; i++)
			theChildren[i] = parser.parseSchema(this, "[" + i + "]", values.get(i));
	}

	/** @return This element's children */
	public JsonElement[] getChildren() {
		return theChildren;
	}
}

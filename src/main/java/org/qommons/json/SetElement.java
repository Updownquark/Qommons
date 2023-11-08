/*
 * SetElement.java Created Dec 21, 2009 by Andrew Butler, PSL
 */
package org.qommons.json;

import java.util.List;

/** Validates an element that is a set of other elements */
public class SetElement extends DefaultJsonElement {
	private JsonElement theChild;

	private int theMinSize;

	private int theMaxSize;

	@Override
	public void configure(JsonSchemaParser parser, JsonElement parent, String name, JsonObject schemaEl) {
		super.configure(parser, parent, name, schemaEl);
		theChild = parser.parseSchema(this, "[element]", schemaEl.get("values"));
		theMinSize = -1;
		theMaxSize = -1;
		JsonObject constraints = getConstraints();
		if (constraints != null) {
			if (constraints.get("min") != null)
				theMinSize = ((Number) constraints.get("min")).intValue();
			if (constraints.get("max") != null)
				theMaxSize = ((Number) constraints.get("max")).intValue();
		}
	}

	@Override
	public float doesValidate(Object jsonValue) {
		float ret = super.doesValidate(jsonValue);
		if (ret < 1)
			return ret;
		if (jsonValue == null)
			return 1;
		if (!(jsonValue instanceof List))
			return 0;
		List<Object> set = (List<Object>) jsonValue;
		for (Object el : set) {
			ret = theChild.doesValidate(el);
			if (ret < 1)
				return ret;
		}
		return 1;
	}

	@Override
	public boolean validate(Object jsonValue) throws JsonSchemaException {
		if (super.validate(jsonValue))
			return true;
		if (!(jsonValue instanceof List))
			throw new JsonSchemaException("Element must be a set", this, jsonValue);
		List<Object> set = (List<Object>) jsonValue;
		if (theMinSize >= 0 && set.size() < theMinSize)
			throw new JsonSchemaException("Set must have at least " + theMinSize + " elements", this, jsonValue);
		if (theMaxSize >= 0 && set.size() > theMaxSize)
			throw new JsonSchemaException("Set must have at most " + theMaxSize + " elements", this, jsonValue);
		for (Object el : set)
			theChild.validate(el);
		return true;
	}
}

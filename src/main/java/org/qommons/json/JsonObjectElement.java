/* JsonObjectElement.java Created Dec 21, 2009 by Andrew Butler, PSL */
package org.qommons.json;

import java.util.Map;

/** Represents an element that must be a json object with certain fields */
public class JsonObjectElement extends DefaultJsonElement {
	private static enum OnExtraEl {
		IGNORE, WARN, ERROR;

		static OnExtraEl byName(String name) {
			if(name == null)
				return WARN;
			for(OnExtraEl el : values())
				if(el.name().equalsIgnoreCase(name))
					return el;
			throw new IllegalArgumentException("Illegal extra element value: " + name);
		}
	}

	private Map<String, JsonElement> theChildren;

	private OnExtraEl theExtraEl;

	private CustomSchemaElement theInheritance;

	@Override
	public void configure(JsonSchemaParser parser, JsonElement parent, String name, JsonObject schemaEl) {
		super.configure(parser, parent, name, schemaEl);
		theChildren = new java.util.HashMap<>();
		JsonObject constraints = getConstraints();
		String allowExtras = null;
		if(constraints != null)
			allowExtras = (String) constraints.get("allowExtras");
		if(allowExtras != null)
			theExtraEl = OnExtraEl.byName((String) constraints.get("allowExtras"));
		else { // If allowExtras is not specified, get it from the parent
			JsonElement element = getParent();
			while(element != null && !(element instanceof JsonObjectElement))
				element = element.getParent();
			if(element != null)
				theExtraEl = ((JsonObjectElement) element).theExtraEl;
			else
				// If no parent, use WARN as default
				theExtraEl = OnExtraEl.WARN;
		}
		if(schemaEl.get("inheritSchema") != null) {
			theInheritance = getParser().createElementForType((String) schemaEl.get("inheritSchema"));
			theInheritance.configure(parser, this, "inheritSchema", null);
			schemaEl.remove("inheritSchema");
			if(!(theInheritance.getSchemaElement() instanceof JsonObjectElement))
				throw new IllegalStateException("The inheritsSchema attribute must point to a valid JSON object schema");
			for(java.util.Map.Entry<String, JsonElement> entry : ((JsonObjectElement) theInheritance.getSchemaElement()).theChildren
				.entrySet())
				theChildren.put(entry.getKey(), entry.getValue());
		}
		for(Map.Entry<String, Object> entry : ((Map<String, Object>) schemaEl).entrySet())
			theChildren.put(entry.getKey(), parser.parseSchema(this, entry.getKey(), entry.getValue()));
	}

	/** @return The names of all children of this JsonObjectElement */
	public String [] getChildNames() {
		return theChildren.keySet().toArray(new String[0]);
	}

	/**
	 * @param name The name of the child to get
	 * @return This JsonObjectElement's child of the given name
	 */
	public JsonElement getChild(String name) {
		return theChildren.get(name);
	}

	@Override
	public float doesValidate(Object jsonValue) {
		float ret = super.doesValidate(jsonValue);
		if(ret < 1)
			return ret;
		if(jsonValue == null)
			return 1;
		if (!(jsonValue instanceof JsonObject))
			return 0;
		int total = 0;
		float matched = 0;
		JsonObject json = (JsonObject) jsonValue;
		for(Map.Entry<String, JsonElement> entry : theChildren.entrySet()) {
			total++;
			matched += entry.getValue().doesValidate(json.get(entry.getKey()));
		}
		if(theExtraEl == OnExtraEl.ERROR)
			for(Map.Entry<String, Object> entry : ((Map<String, Object>) jsonValue).entrySet()) {
				if(theChildren.get(entry.getKey()) == null)
					total++;
			}
		return matched / total;
	}

	@Override
	public boolean validate(Object jsonValue) throws JsonSchemaException {
		if(super.validate(jsonValue))
			return true;
		if(jsonValue == null)
			return true;
		if (!(jsonValue instanceof JsonObject))
			throw new JsonSchemaException("Element must be a JSON object", this, jsonValue);
		JsonObject json = (JsonObject) jsonValue;
		for(Map.Entry<String, JsonElement> entry : theChildren.entrySet())
			entry.getValue().validate(json.get(entry.getKey()));
		for(Map.Entry<String, Object> entry : ((Map<String, Object>) jsonValue).entrySet()) {
			if(theChildren.get(entry.getKey()) == null) {
				switch (theExtraEl) {
				case ERROR:
					throw new JsonSchemaException("Extra element " + entry.getKey() + " in JSON object", this, jsonValue);
				case WARN:
					JsonSchemaParser.log.warn("Extra element " + entry.getKey() + " in JSON object " + getPathString());
					break;
				case IGNORE:
					break;
				}
			}
		}
		return true;
	}
}

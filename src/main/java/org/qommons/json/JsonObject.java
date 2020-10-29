package org.qommons.json;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/** Represents a JSON object */
public class JsonObject {
	private final Map<String, Object> theValues;

	/** Creates the object */
	public JsonObject() {
		theValues = new LinkedHashMap<>();
	}

	/**
	 * @param key The key to get the value of
	 * @return The value stored in this object by the given key
	 */
	public Object get(String key) {
		return theValues.get(key);
	}

	/**
	 * @param key The key to store the value under
	 * @param value The value to store
	 * @return This object
	 */
	public JsonObject with(String key, Object value) {
		if (value == null) {//
		} else if (value instanceof Boolean) {//
		} else if (value instanceof Number) {//
		} else if (value instanceof String) {//
		} else if (value instanceof JsonObject) {//
		} else if (value instanceof List) {//
		} else
			throw new IllegalArgumentException("Unrecognized JSON value type: " + value.getClass().getName());
		theValues.put(key, value);
		return this;
	}

	/**
	 * @param key The key to remove in this object
	 * @return This object
	 */
	public JsonObject remove(String key) {
		theValues.remove(key);
		return this;
	}

	/** @return All keys stored in this object */
	public Set<String> keySet() {
		return theValues.keySet();
	}

	/** @return All key-value entries in this object */
	public Collection<Entry<String, Object>> entrySet() {
		return theValues.entrySet();
	}

	@Override
	public int hashCode() {
		return theValues.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof JsonObject && theValues.equals(((JsonObject) obj).theValues);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append('{');
		boolean first = true;
		for (Map.Entry<String, Object> entry : theValues.entrySet()) {
			if (first)
				first = false;
			else
				str.append(',');
			str.append('"').append(entry.getKey()).append("\":");
			if (entry.getValue() instanceof String)
				str.append('"').append(entry.getValue()).append('"');
			else
				str.append(entry.getValue());
		}
		str.append('}');
		return str.toString();
	}
}

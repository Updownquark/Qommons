package org.qommons.json;

import java.util.*;
import java.util.Map.Entry;

import org.qommons.collect.MutableCollectionElement.StdMsg;

/** Represents a JSON object */
public class JsonObject {
	/**
	 * A placeholder key in an object for a property whose value is <code>null</code>. This is semantically different than a missing key.
	 */
	private static final Object NULL = new Object() {
		@Override
		public String toString() {
			return "null";
		}
	};

	private final Map<String, Object> theValues;

	/** Creates the object */
	public JsonObject() {
		theValues = new LinkedHashMap<>();
	}

	/**
	 * @param key The key to check for
	 * @return Whether the given key was defined for this object (including if its value was the literal <code>null</code>)
	 */
	public boolean hasProperty(String key) {
		return theValues.containsKey(key);
	}

	/**
	 * @param key The key to get the value of
	 * @return The value stored in this object by the given key
	 * @throws IllegalArgumentException If no property with the given key was installed in this object
	 */
	public Object get(String key) throws IllegalArgumentException {
		Object value = theValues.get(key);
		if (value == null)
			throw new IllegalArgumentException("No such property used in this JSON object: \"" + key + "\"");
		else if (value == NULL)
			value = null;
		return value;
	}

	/**
	 * @param key The key to get the value of
	 * @param defaultValue The value to return if no value is stored under the given key in this object
	 * @return The value stored in this object by the given key, or the given default if the key is not used in this object
	 */
	public Object getOrDefault(String key, Object defaultValue) {
		Object value = theValues.getOrDefault(key, defaultValue);
		if (value == NULL)
			value = null;
		return value;
	}

	/**
	 * @param key The key to store the value under
	 * @param value The value to store
	 * @return This object
	 */
	public JsonObject with(String key, Object value) {
		if (value == null) {//
			value = NULL;
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
	public Collection<Map.Entry<String, Object>> entrySet() {
		return new EntrySet(theValues.entrySet());
	}

	/** @return The map backing this object's values */
	public Map<String, Object> getMap() {
		return Collections.unmodifiableMap(theValues);
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

	static class EntrySet extends AbstractCollection<Map.Entry<String, Object>> {
		private final Collection<Map.Entry<String, Object>> theMapEntrySet;

		EntrySet(Collection<Entry<String, Object>> mapEntrySet) {
			theMapEntrySet = mapEntrySet;
		}

		@Override
		public Iterator<Map.Entry<String, Object>> iterator() {
			return new EntryIterator(theMapEntrySet.iterator());
		}

		@Override
		public int size() {
			return theMapEntrySet.size();
		}
	}

	static class EntryIterator implements Iterator<Map.Entry<String, Object>> {
		private final Iterator<Map.Entry<String, Object>> theMapIterator;

		EntryIterator(Iterator<Entry<String, Object>> mapIterator) {
			theMapIterator = mapIterator;
		}

		@Override
		public boolean hasNext() {
			return theMapIterator.hasNext();
		}

		@Override
		public Entry<String, Object> next() {
			return new WrappedEntry(theMapIterator.next());
		}
	}

	static class WrappedEntry implements Map.Entry<String, Object> {
		private final Map.Entry<String, Object> theMapEntry;

		WrappedEntry(Map.Entry<String, Object> mapEntry) {
			theMapEntry = mapEntry;
		}

		@Override
		public String getKey() {
			return theMapEntry.getKey();
		}

		@Override
		public Object getValue() {
			Object value = theMapEntry.getValue();
			if (value == NULL)
				return null;
			return value;
		}

		@Override
		public Object setValue(Object value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String toString() {
			return theMapEntry.toString();
		}
	}
}

/* SerialJsonCompiler.java Created Aug 20, 2010 by Andrew Butler, PSL */
package org.qommons.json;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/** An implementation of serial writer that compiles a DOM-like JSON structure serially */
public class SerialJsonCompiler implements JsonSerialWriter {
	private java.util.ArrayList<Object> thePath;

	private String thePropertyName;

	private Object theLastProduced;

	/** Creates a serial JSON compiler */
	public SerialJsonCompiler() {
		thePath = new java.util.ArrayList<>();
	}

	@Override
	public JsonSerialWriter startObject() {
		push(new JSONObject());
		return this;
	}

	@Override
	public JsonSerialWriter startProperty(String name) {
		if(thePropertyName != null)
			throw new IllegalStateException("Property name already set--need value for property");
		thePropertyName = name;
		return this;
	}

	@Override
	public JsonSerialWriter endObject() {
		if(!(top() instanceof JSONObject))
			throw new IllegalStateException("JSON object not started");
		pop();
		return this;
	}

	@Override
	public JsonSerialWriter startArray() {
		push(new JSONArray());
		return this;
	}

	@Override
	public JsonSerialWriter endArray() {
		if(!(top() instanceof JSONArray))
			throw new IllegalStateException("JSON array not started");
		pop();
		return this;
	}

	@Override
	public JsonSerialWriter writeString(String value) {
		push(value);
		return this;
	}

	@Override
	public JsonSerialWriter writeNumber(Number value) {
		push(value);
		return this;
	}

	@Override
	public JsonSerialWriter writeBoolean(boolean value) {
		push(Boolean.valueOf(value));
		return this;
	}

	@Override
	public JsonSerialWriter writeNull() {
		push(null);
		return this;
	}

	/** Closes content currently compiling with this compiler */
	public void close() {
		while(!thePath.isEmpty())
			pop();
	}

	/**
	 * @return The depth of the content being produced (in JSON objects and arrays only)
	 */
	public int getDepth() {
		return thePath.size();
	}

	/**
	 * @return The JSON object or array that is currently being compiled
	 */
	public Object top() {
		int size = thePath.size();
		if(size == 0)
			return null;
		return thePath.get(size - 1);
	}

	/**
	 * @param depth The depth from the currently producing item to get
	 * @return The content being produced at the given depth from the currently active item
	 */
	public Object fromTop(int depth) {
		int size = thePath.size();
		if(depth >= size)
			return null;
		return thePath.get(size - depth - 1);
	}

	/**
	 * @param depth The depth from the root to get the content at
	 * @return The content being produced at the given depth from the root item
	 */
	public Object fromBottom(int depth) {
		if(thePath.size() <= depth)
			return null;
		return thePath.get(depth);
	}

	/**
	 * @return The item that was most recently completed--may be any JSON type (object, array, string, number, boolean, null)
	 */
	public Object getLastProduced() {
		return theLastProduced;
	}

	private void push(Object obj) {
		Object top = top();
		if(top instanceof JSONObject) {
			if(thePropertyName == null)
				throw new IllegalStateException("Property name needed");
			((JSONObject) top).put(thePropertyName, obj);
			thePropertyName = null;
		} else if(top instanceof JSONArray)
			((JSONArray) top).add(obj);
		if(obj instanceof JSONObject || obj instanceof JSONArray)
			thePath.add(obj);
		else
			theLastProduced = obj;
	}

	private void pop() {
		theLastProduced = thePath.remove(thePath.size() - 1);
	}
}

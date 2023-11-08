/*
 * JsonSerialWriter.java Created Jul 29, 2010 by Andrew Butler, PSL
 */
package org.qommons.json;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Creates JSON-formatted data in a serial method. This allows a stream to be written more
 * efficiently than compiling large JSON documents and then serializing them to a string to write.
 */
public interface JsonSerialWriter
{
	/**
	 * Starts a JSON object
	 * 
	 * @return This writer, for chaining
	 * @throws IOException If an error occurs creating the data
	 */
	public JsonSerialWriter startObject() throws IOException;

	/**
	 * Starts a property within an object
	 * 
	 * @param name The name of the property
	 * @return This writer, for chaining
	 * @throws IOException If an error occurs creating the data
	 */
	public JsonSerialWriter startProperty(String name) throws IOException;

	/**
	 * Ends an object
	 * 
	 * @return This writer, for chaining
	 * @throws IOException If an error occurs creating the data
	 */
	public JsonSerialWriter endObject() throws IOException;

	/**
	 * Starts a JSON array (writes the '[')
	 * 
	 * @return This writer, for chaining
	 * @throws IOException If an error occurs creating the data
	 */
	public JsonSerialWriter startArray() throws IOException;

	/**
	 * Ends an array
	 * 
	 * @return This writer, for chaining
	 * @throws IOException If an error occurs creating the data
	 */
	public JsonSerialWriter endArray() throws IOException;

	/**
	 * Writes a string value
	 * 
	 * @param value The string to write
	 * @return This writer, for chaining
	 * @throws IOException If an error occurs creating the data
	 */
	public JsonSerialWriter writeString(String value) throws IOException;

	/**
	 * Writes a number value
	 * 
	 * @param value The number to write
	 * @return This writer, for chaining
	 * @throws IOException If an error occurs creating the data
	 */
	public JsonSerialWriter writeNumber(Number value) throws IOException;

	/**
	 * Writes a boolean value
	 * 
	 * @param value The boolean to write
	 * @return This writer, for chaining
	 * @throws IOException If an error occurs creating the data
	 */
	public JsonSerialWriter writeBoolean(boolean value) throws IOException;

	/**
	 * Writes a null value
	 * 
	 * @return This writer, for chaining
	 * @throws IOException If an error occurs creating the data
	 */
	public JsonSerialWriter writeNull() throws IOException;

	/**
	 * Writes a JSON-typed something to the stream
	 * 
	 * @param thing The JSON thing to write
	 * @return This writer
	 * @throws IOException If an error occurred writing the thing to the stream
	 * @throws IllegalArgumentException If the thing is not a recognized JSON-typed something
	 */
	default JsonSerialWriter writeThing(Object thing) throws IOException, IllegalArgumentException {
		if (thing instanceof JsonObject)
			writeObject((JsonObject) thing);
		else if (thing instanceof List)
			writeArray((List<?>) thing);
		else if (thing instanceof String)
			writeString((String) thing);
		else if (thing instanceof Number)
			writeNumber((Number) thing);
		else if (thing instanceof Boolean)
			writeBoolean((Boolean) thing);
		else if (thing == null)
			writeNull();
		else
			throw new IllegalArgumentException("Unrecognized JSON thing to write: " + thing.getClass().getName());
		return this;
	}

	/**
	 * Writes a JSON object something to the stream
	 * 
	 * @param object The JSON object to write
	 * @return This writer
	 * @throws IOException If an error occurred writing the object to the stream
	 * @throws IllegalArgumentException If the object contains any properties that are not recognized JSON-typed somethings
	 */
	default JsonSerialWriter writeObject(JsonObject object) throws IOException, IllegalArgumentException {
		if (object == null) { // That's ok, I guess
			writeNull();
			return this;
		}
		startObject();
		for (Map.Entry<String, Object> property : object.entrySet()) {
			startProperty(property.getKey());
			writeThing(property.getValue());
		}
		endObject();
		return this;
	}

	/**
	 * Writes a JSON array something to the stream
	 * 
	 * @param array The JSON array to write
	 * @return This writer
	 * @throws IOException If an error occurred writing the array to the stream
	 * @throws IllegalArgumentException If the array contains any elements that are not recognized JSON-typed somethings
	 */
	default JsonSerialWriter writeArray(List<?> array) throws IOException, IllegalArgumentException {
		if (array == null) { // That's ok, I guess
			writeNull();
			return this;
		}
		startArray();
		for (Object element : array)
			writeThing(element);
		endArray();
		return this;
	}
}

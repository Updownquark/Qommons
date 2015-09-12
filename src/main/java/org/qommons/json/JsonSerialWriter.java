/*
 * JsonSerialWriter.java Created Jul 29, 2010 by Andrew Butler, PSL
 */
package org.qommons.json;

import java.io.IOException;

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
}

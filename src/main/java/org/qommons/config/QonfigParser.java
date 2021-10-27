package org.qommons.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/** Parses {@link QonfigToolkit}s and {@link QonfigDocument}s from binary or text content */
public interface QonfigParser {
	/**
	 * Installs pre-created toolkit instances to use instead of retrieving and parsing the references
	 * 
	 * @param toolkits The toolkits to use
	 * @return This parser
	 */
	DefaultQonfigParser withToolkit(QonfigToolkit... toolkits);

	/**
	 * @param location The location of the toolkit to parse
	 * @param content The stream content to parse
	 * @return The parsed toolkit
	 * @throws IOException If the stream cannot be read
	 * @throws QonfigParseException If the toolkit cannot be parsed from the stream
	 */
	QonfigToolkit parseToolkit(URL location, InputStream content) throws IOException, QonfigParseException;

	/**
	 * @param location The location of the document to parse
	 * @param content The stream content to parse
	 * @return The parsed document
	 * @throws IOException If the stream cannot be read
	 * @throws QonfigParseException If the document cannot be parsed from the stream
	 */
	QonfigDocument parseDocument(String location, InputStream content) throws IOException, QonfigParseException;
}

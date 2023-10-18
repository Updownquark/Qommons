package org.qommons.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.qommons.io.TextParseException;

/** Parses {@link QonfigToolkit}s and {@link QonfigDocument}s from binary or text content */
public interface QonfigParser {
	/**
	 * Installs pre-created toolkit instances to use instead of retrieving and parsing the references
	 * 
	 * @param toolkits The toolkits to use
	 * @return This parser
	 */
	QonfigParser withToolkit(QonfigToolkit... toolkits);

	QonfigParser withPromiseFulfillment(QonfigPromiseFulfillment stitcher);

	/**
	 * @param location The location of the toolkit to parse
	 * @param content The stream content to parse
	 * @param customValueTypes Custom value types to be used by the toolkit
	 * @return The parsed toolkit
	 * @throws IOException If the stream cannot be read
	 * @throws TextParseException If the document structure itself cannot be parsed
	 * @throws QonfigParseException If the toolkit cannot be parsed from the stream
	 */
	QonfigToolkit parseToolkit(URL location, InputStream content, CustomValueType... customValueTypes)
		throws IOException, TextParseException, QonfigParseException;

	/**
	 * @param partial Whether to create a partially-validated document structure. These are useful as templates for creating full content
	 *        later given external content to fill in gaps.
	 * @param location The location of the document to parse
	 * @param content The stream content to parse
	 * @return The parsed document
	 * @throws IOException If the stream cannot be read
	 * @throws TextParseException If the document structure itself cannot be parsed
	 * @throws QonfigParseException If the document cannot be parsed from the structure
	 */
	QonfigDocument parseDocument(boolean partial, String location, InputStream content)
		throws IOException, TextParseException, QonfigParseException;
}

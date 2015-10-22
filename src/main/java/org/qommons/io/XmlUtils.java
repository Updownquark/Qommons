package org.qommons.io;

import java.io.IOException;
import java.io.Reader;
import java.util.function.Consumer;

import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.SAXHandler;
import org.xml.sax.SAXException;

/** Utilities for XML */
public class XmlUtils {
	/**
	 * Parses an XML document serially, notifying a handler for each top-level child of the root element
	 *
	 * @param reader The XML stream to read
	 * @param handler The handler to accept each element under the root
	 * @throws IOException If an error occurs reading the stream
	 * @throws JDOMException If an error occurs parsing the stream
	 */
	public static void parseTopLevelElements(Reader reader, final Consumer<? super Element> handler) throws IOException, JDOMException {
		final SAXHandler saxHandler = new SAXHandler() {
			@Override
			public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
				boolean hit = getCurrentElement().getParentElement() != null
					&& getCurrentElement().getParentElement().getParentElement() == null;
				if(hit)
					handler.accept(getCurrentElement());
				super.endElement(namespaceURI, localName, qName);
				if(hit) // Clean up to lower memory footprint
					getCurrentElement().removeContent();
			}
		};
		SAXBuilder builder = new SAXBuilder();
		builder.setSAXHandlerFactory(factory -> saxHandler);
		builder.build(reader);
	}
}

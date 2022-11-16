package org.qommons.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Stack;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * <p>
 * Grabbed this code (and augmented it a bit) from the first StackOverflow answer at
 * <a href="https://stackoverflow.com/questions/4915422/get-line-number-from-xml-node-java" />, credit to <b>priomsrb</b>.
 * </p>
 * <p>
 * This reader parses an XML document, encoding text position information in the properties:
 * <ul>
 * <li>{@link #LINE_NUMBER_KEY_NAME}</li>
 * <li>{@link #COLUMN_NUMBER_KEY_NAME}</li>
 * </ul>
 * </p>
 */
public class PositionalXMLReader {
	/**
	 * {@link Element#getUserData(String) User data} key in which the line number will be stored in each {@link Element} parsed by this
	 * class
	 */
	public final static String LINE_NUMBER_KEY_NAME = "lineNumber";
	/**
	 * {@link Element#getUserData(String) User data} key in which the column number will be stored in each {@link Element} parsed by this
	 * class
	 */
	public final static String COLUMN_NUMBER_KEY_NAME = "columnNumber";

	/**
	 * @param is The input stream containing the XML data to read
	 * @return The parsed document, containing line and column numbers as {@link Element#getUserData(String) user data}
	 * @throws IOException If the stream could not be read
	 * @throws SAXException If the XML could not be parsed
	 */
	public static Document readXML(final InputStream is) throws IOException, SAXException {
		final Document doc;
		SAXParser parser;
		try {
			final SAXParserFactory factory = SAXParserFactory.newInstance();
			parser = factory.newSAXParser();
			final DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
			final DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
			doc = docBuilder.newDocument();
		} catch (final ParserConfigurationException e) {
			throw new RuntimeException("Can't create SAX parser / DOM builder.", e);
		}

		final Stack<Element> elementStack = new Stack<>();
		final StringBuilder textBuffer = new StringBuilder();
		final DefaultHandler handler = new DefaultHandler() {
			private Locator locator;

			@Override
			public void setDocumentLocator(final Locator locator) {
				this.locator = locator; // Save the locator, so that it can be used later for line tracking when traversing nodes.
			}

			@Override
			public void startElement(final String uri, final String localName, final String qName, final Attributes attributes)
				throws SAXException {
				addTextIfNeeded();
				final Element el = doc.createElement(qName);
				for (int i = 0; i < attributes.getLength(); i++) {
					el.setAttribute(attributes.getQName(i), attributes.getValue(i));
				}
				el.setUserData(LINE_NUMBER_KEY_NAME, this.locator.getLineNumber(), null);
				el.setUserData(COLUMN_NUMBER_KEY_NAME, this.locator.getColumnNumber(), null);
				elementStack.push(el);
			}

			@Override
			public void endElement(final String uri, final String localName, final String qName) {
				addTextIfNeeded();
				final Element closedEl = elementStack.pop();
				if (elementStack.isEmpty()) { // Is this the root element?
					doc.appendChild(closedEl);
				} else {
					final Element parentEl = elementStack.peek();
					parentEl.appendChild(closedEl);
				}
			}

			@Override
			public void characters(final char ch[], final int start, final int length) throws SAXException {
				textBuffer.append(ch, start, length);
			}

			// Outputs text accumulated under the current node
			private void addTextIfNeeded() {
				if (textBuffer.length() > 0) {
					final Element el = elementStack.peek();
					final Node textNode = doc.createTextNode(textBuffer.toString());
					el.appendChild(textNode);
					textBuffer.delete(0, textBuffer.length());
				}
			}
		};
		parser.parse(is, handler);

		return doc;
	}

	/**
	 * @param element The element to get the line number for
	 * @return The line number where the element was defined (begun). If the element was not parsed with this class, -1 will be returned
	 */
	public static int getLineNumber(Element element) {
		Integer lineNumber = (Integer) element.getUserData(LINE_NUMBER_KEY_NAME);
		return lineNumber == null ? -1 : lineNumber.intValue();
	}
}

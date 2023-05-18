package org.qommons.io;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.qommons.IntList;
import org.qommons.Named;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * <p>
 * I have need of an XML parser with more rigorous position handling than Java's native handling is capable of.
 * </p>
 * <p>
 * This class facilitates full tracking of every element, attribute, and value to an exact position with the XML file.
 * </p>
 * <p>
 * <b><font color="red">This class is NOT a full-featured XML parser.</font></b> Several features of XML are not supported or treated
 * differently:
 * <ul>
 * <li>Namespaces are not handled specially, but rather are treated as part of the element/attribute name.</li>
 * <li>This class completely lacks support for the DOCTYPE declaration. The parser will throw exceptions when these are encountered.</li>
 * </ul>
 * </p>
 */
public class SimpleXMLParser {
	/** An element as given by an {@link XmlParseException} */
	public static class LocatedXmlElement implements Named {
		private final LocatedXmlElement theParent;
		private final String theName;
		private final FilePosition thePosition;

		/**
		 * @param parent The parent element
		 * @param name The name of the element
		 * @param position The position of the name of the element in its opening tag in the file
		 */
		public LocatedXmlElement(LocatedXmlElement parent, String name, FilePosition position) {
			theParent = parent;
			theName = name;
			thePosition = position;
		}

		/** @return This element's parent */
		public LocatedXmlElement getParent() {
			return theParent;
		}

		@Override
		public String getName() {
			return theName;
		}

		/** @return The position of the name of the element in its opening tag in the file */
		public FilePosition getPosition() {
			return thePosition;
		}
	}

	/** Thrown from {@link SimpleXMLParser}'s parse methods */
	public static class XmlParseException extends TextParseException {
		private final LocatedXmlElement theElement;

		/**
		 * @param element The element under which the parse error occurred (may be null if the root element had not yet been encountered)
		 * @param message The message indicating the nature of the XML malformation
		 * @param errorOffset The absolute position offset of the character in the file where the error was detected
		 * @param lineNumber The line number in the file where the error was detected (offset from zero)
		 * @param columnNumber The character number (in its line) in the file where the error was detected (offset from zero)
		 */
		public XmlParseException(LocatedXmlElement element, String message, int errorOffset, int lineNumber, int columnNumber) {
			super(message, errorOffset, lineNumber, columnNumber);
			theElement = element;
		}

		/** @return The element under which the parse error occurred. May be null if the root element had not yet been encountered */
		public LocatedXmlElement getElement() {
			return theElement;
		}
	}

	/** Represents the XML declaration which may occur at position zero of an XML file */
	public static class XmlDeclaration {
		private final String theVersion;
		private final Charset theEncoding;
		private final Boolean isStandalone;

		/**
		 * @param version The XML version. This string is not validated.
		 * @param encoding The character set encoding specified in the declaration, or null if none was specified. This class assumes UTF-8
		 *        encoding if not specified.
		 * @param standalone Whether the 'standalone' attribute was specified as 'yes' or 'no', or null if it it was not specified
		 */
		public XmlDeclaration(String version, Charset encoding, Boolean standalone) {
			theVersion = version;
			theEncoding = encoding;
			isStandalone = standalone;
		}

		/** @return The XML version. This string is not validated. */
		public String getVersion() {
			return theVersion;
		}

		/**
		 * @return The character set encoding specified in the declaration, or null if none was specified. This class assumes UTF-8 encoding
		 *         if not specified.
		 */
		public Charset getEncoding() {
			return theEncoding;
		}

		/** @return Whether the 'standalone' attribute was specified as 'yes' or 'no', or null if it it was not specified */
		public Boolean isStandalone() {
			return isStandalone;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder("<?xml version=").append(theVersion);
			if (theEncoding != null)
				str.append(" encoding=").append(theEncoding);
			if (isStandalone != null)
				str.append(" standalone=").append(isStandalone ? "yes" : "no");
			str.append(" ?>");
			return str.toString();
		}
	}

	/** A handler to be notified for each item of content in an XML document */
	public interface ParseHandler {
		/**
		 * Called for the XML declaration at the top of the document. An XML declaration is not required, and this method will not be called
		 * if it is missing.
		 * 
		 * @param declaration The XML declaration in the document
		 */
		default void handleDeclaration(XmlDeclaration declaration) {
		}

		/**
		 * Called when an XML processing instruction is encountered: <code>&lt;?TARGET?></code> or <code>&lt;?TARGET CONTENT?></code>.
		 * 
		 * @param target The target of the instruction
		 * @param targetPosition The position of the target instruction
		 * @param content The content text of the instruction, or null if no content is specified. This will only be null if the terminal
		 *        '?>' IMMEDIATELY follows the TARGET, with no whitespace. If any whitespace exists between the TARGET and the terminal
		 *        '?>', the content will be all of the characters between the TARGET and '?>', minus the first whitespace character.
		 * @param contentPosition The position information of the content of the instruction, or null if no content is specified
		 */
		default void handleProcessingInstruction(String target, FilePosition targetPosition, String content,
			ContentPosition contentPosition) {
		}

		/**
		 * Called when an XML comment is encountered
		 * 
		 * @param comment The comment's content--everything between '&lt;!--' and '-->'
		 * @param position Position information for the content
		 */
		default void handleComment(String comment, ContentPosition position) {
		}

		/**
		 * Called when the declaration of a root or a child element is encountered
		 * 
		 * @param name The name of the element
		 * @param position The position of the element's name in its open tag
		 */
		default void handleElementStart(String name, FilePosition position) {
		}

		/**
		 * Called when an attribute is encountered
		 * 
		 * @param attributeName The name of the attribute
		 * @param namePosition The position of the attribute's name
		 * @param attributeValue The value of the attribute--everything between the quotes
		 * @param valuePosition Position information for the attribute's value
		 */
		default void handleAttributeValue(String attributeName, FilePosition namePosition, String attributeValue,
			ContentPosition valuePosition) {
		}

		/**
		 * <p>
		 * Called when any characters occur between an element's open and close tags which is not an XML structure such as a
		 * {@link #handleElementStart(String, FilePosition) child element}, a {@link #handleCDataContent(String, String, ContentPosition)
		 * CDATA} structure, or a {@link #handleComment(String, ContentPosition) comment}.
		 * </p>
		 * <p>
		 * This method will not be called for an element that is self-closing (e.g. &lt;element />), for empty content (e.g.
		 * &lt;element>&lt;/element>), or between adjacent XML structures (e.g. &lt;parent>&lt;child />&lt;child />&lt;/parent>).
		 * </p>
		 * <p>
		 * This method will be called each time there is non-empty content under an element that is not an XML structure. E.g. for<br />
		 * <code>
		 * &lt;parent><br />
		 * &nbsp;&nbsp;&nbsp;&lt;child /><br />
		 * &nbsp;&nbsp;&nbsp;Some text<br />
		 * &nbsp;&nbsp;&nbsp;&lt;child /><br />
		 * &lt;/parent>
		 * </code><br />
		 * this method will be called 3 times:
		 * <ul>
		 * <li>once for the white space between &lt;parent> and the first &lt;child></li>
		 * <li>once for "Some text" (which will be surrounded by white space)</li>
		 * <li>once for the white space between the last &lt;child> and &lt;/parent></li>
		 * </p>
		 * <p>
		 * This method is not called for white space occurring outside of the document's root element.
		 * </p>
		 * 
		 * @param elementName The name of the element under which the content is occurring
		 * @param elementValue The content text
		 * @param position Position information for the content
		 */
		default void handleElementContent(String elementName, String elementValue, ContentPosition position) {
		}

		/**
		 * Called when a CDATA structure is encountered.
		 * 
		 * @param elementName The name of the element under which the CDATA structure occurred
		 * @param content The content of the CDATA structure--everything between '&lt;!CDATA[[' and ']]>'
		 * @param position Position information for the CDATA content
		 */
		default void handleCDataContent(String elementName, String content, ContentPosition position) {
		}

		/**
		 * Called when an element is closed
		 * 
		 * @param elementName The name of the element being closed
		 * @param position The position of the name of the element in the closing tag (which may be the same as the opening tag if the
		 *        element is self-closing, offset from zero)
		 * @param selfClosing Whether the element was self-closing, as opposed to opened and closed with separate tags
		 */
		default void handleElementEnd(String elementName, FilePosition position, boolean selfClosing) {
		}

		/**
		 * Called if an error occurs reading the input stream
		 * 
		 * @param ioError The error that occurred
		 * @param position The position of the last character that was successfully read
		 */
		default void handleIOError(IOException ioError, FilePosition position) {
		}

		/**
		 * Called if the XML contains a syntax error
		 * 
		 * @param parseError The parse error that occurred
		 */
		default void handleParseError(XmlParseException parseError) {
		}
	}

	/**
	 * A handler that creates {@link Document}s. Each {@link Node} in the document will be populated with any relevant positions.
	 * 
	 * @see #NAME_POSITION_KEY
	 * @see #CONTENT_POSITION_KEY
	 */
	public static class DomCreatorHandler implements ParseHandler {
		/**
		 * {@link Node#getUserData(String) User data} key in which the {@link FilePosition position} of the node's {@link Node#getNodeName()
		 * name} will be stored in each {@link Node} parsed by this class
		 */
		public final static String NAME_POSITION_KEY = "namePosition";
		/**
		 * {@link Node#getUserData(String) User data} key in which the {@link ContentPosition position} of the node's
		 * {@link Node#getNodeValue() value} will be stored in each {@link Node} parsed by this class
		 */
		public final static String CONTENT_POSITION_KEY = "contentPosition";

		private final Document theDocument;
		private final Deque<Element> theStack;

		/** @param document The document object to populate */
		public DomCreatorHandler(Document document) {
			theDocument = document;
			theStack = new ArrayDeque<>();
		}

		/** @return This handler's document */
		public Document getDocument() {
			return theDocument;
		}

		@Override
		public void handleDeclaration(XmlDeclaration declaration) {
			theDocument.setXmlVersion(declaration.getVersion());
			if (declaration.isStandalone() != null)
				theDocument.setXmlStandalone(declaration.isStandalone());
		}

		@Override
		public void handleProcessingInstruction(String target, FilePosition targetPosition, String content,
			ContentPosition contentPosition) {
			Node node = theDocument.createProcessingInstruction(target, content);
			node.setUserData(NAME_POSITION_KEY, targetPosition, null);
			node.setUserData(CONTENT_POSITION_KEY, contentPosition, null);
			if (theStack.isEmpty())
				theDocument.appendChild(node);
			else
				theStack.getLast().appendChild(node);
		}

		@Override
		public void handleComment(String comment, ContentPosition position) {
			Node node = theDocument.createComment(comment);
			node.setUserData(CONTENT_POSITION_KEY, position, null);
			if (theStack.isEmpty())
				theDocument.appendChild(node);
			else
				theStack.getLast().appendChild(node);
		}

		@Override
		public void handleElementStart(String name, FilePosition position) {
			Element node = theDocument.createElement(name);
			node.setUserData(NAME_POSITION_KEY, position, null);
			if (theStack.isEmpty())
				theDocument.appendChild(node);
			else
				theStack.getLast().appendChild(node);
			theStack.add(node);
		}

		@Override
		public void handleAttributeValue(String attributeName, FilePosition namePosition, String attributeValue,
			ContentPosition valuePosition) {
			Attr node = theDocument.createAttribute(attributeName);
			node.setValue(attributeValue);
			node.setUserData(NAME_POSITION_KEY, namePosition, null);
			node.setUserData(CONTENT_POSITION_KEY, valuePosition, null);
			theStack.getLast().setAttributeNode(node);
		}

		@Override
		public void handleElementContent(String elementName, String elementValue, ContentPosition position) {
			Node node = theDocument.createTextNode(elementValue);
			node.setUserData(CONTENT_POSITION_KEY, position, null);
			theStack.getLast().appendChild(node);
		}

		@Override
		public void handleCDataContent(String elementName, String content, ContentPosition position) {
			Node node = theDocument.createCDATASection(content);
			node.setUserData(CONTENT_POSITION_KEY, position, null);
			theStack.getLast().appendChild(node);
		}

		@Override
		public void handleElementEnd(String elementName, FilePosition position, boolean selfClosing) {
			theStack.removeLast();
		}
	}

	private int theTabLength = 4;

	/** @return The number of spaces to interpret tabs as in the character numbers provided by this parser. The default is 4. */
	public int getTabLength() {
		return theTabLength;
	}

	/**
	 * @param tabLength The number of spaces to interpret tabs as in the character numbers provided by this parser
	 * @return This parser
	 */
	public SimpleXMLParser setTabLength(int tabLength) {
		if (tabLength < 0)
			throw new IllegalArgumentException("Tab length must not be less than zero");
		theTabLength = tabLength;
		return this;
	}

	/**
	 * Parses XML from a binary stream. The character encoding may be specified by the XML declaration in the document, or is defaulted to
	 * UTF-8.
	 * 
	 * @param <H> The type of the handler
	 * @param in The input stream to parse XML from
	 * @param handler The handler to be notified of each XML structure in the document
	 * @return The given handler
	 * @throws IOException If an error occurs reading the stream
	 * @throws XmlParseException If an error occurs parsing the XML
	 */
	public <H extends ParseHandler> H parseXml(InputStream in, H handler) throws IOException, XmlParseException {
		if (in == null)
			throw new NullPointerException("Stream cannot be null");
		parseXml(new ParseSession(in), handler);
		return handler;
	}

	/**
	 * Parses XML from a reader. If character encoding is specified by the XML declaration in the document, it will be ignored, other than
	 * being {@link ParseHandler#handleDeclaration(XmlDeclaration) passed} to the handler.
	 * 
	 * @param <H> The type of the handler
	 * @param in The reader to parse XML from
	 * @param handler The handler to be notified of each XML structure in the document
	 * @return The given handler
	 * @throws IOException If an error occurs reading the stream
	 * @throws XmlParseException If an error occurs parsing the XML
	 */
	public <H extends ParseHandler> H parseXml(Reader in, H handler) throws IOException, XmlParseException {
		if (in == null)
			throw new NullPointerException("Reader cannot be null");
		parseXml(new ParseSession(in), handler);
		return handler;
	}

	/**
	 * Parses an XML document from a binary stream. The character encoding may be specified by the XML declaration in the document, or is
	 * defaulted to UTF-8. The document's {@link Node node}s will be populated with any relevant positions.
	 * 
	 * @param in The stream to parse
	 * @return The parsed document
	 * @throws IOException If an error occurs reading the stream
	 * @throws XmlParseException If the XML is malformed
	 * @see #getNamePosition(Node)
	 * @see #getContentPosition(Node)
	 */
	public Document parseDocument(InputStream in) throws IOException, XmlParseException {
		if (in == null)
			throw new NullPointerException("Stream cannot be null");
		return parseXml(//
			new ParseSession(in), new DomCreatorHandler(DOM_BUILDERS.get().createDocument()))//
				.getDocument();
	}

	/**
	 * Parses an XML document from a binary stream. If character encoding is specified by the XML declaration in the document, it will be
	 * ignored. The document's {@link Node node}s will be populated with any relevant positions.
	 * 
	 * @param in The reader to parse
	 * @return The parsed document
	 * @throws IOException If an error occurs reading the stream
	 * @throws XmlParseException If the XML is malformed
	 * @see #getNamePosition(Node)
	 * @see #getContentPosition(Node)
	 */
	public Document parseDocument(Reader in) throws IOException, XmlParseException {
		if (in == null)
			throw new NullPointerException("Reader cannot be null");
		return parseXml(//
			new ParseSession(in), new DomCreatorHandler(DOM_BUILDERS.get().createDocument()))//
				.getDocument();
	}

	/**
	 * Gets the name position stored in a node, if it was parsed using this class and name position is relevant for the node
	 * 
	 * @param node The XML node to get the name position for
	 * @return The name position stored in the node
	 * @see #parseDocument(InputStream)
	 * @see DomCreatorHandler
	 */
	public static FilePosition getNamePosition(Node node) {
		return (FilePosition) node.getUserData(DomCreatorHandler.NAME_POSITION_KEY);
	}

	/**
	 * Gets the content position stored in a node, if it was parsed using this class and content position is relevant for the node
	 * 
	 * @param node The XML node to get the content position for
	 * @return The content position stored in the node
	 * @see #parseDocument(InputStream)
	 * @see DomCreatorHandler
	 */
	public static ContentPosition getContentPosition(Node node) {
		return (ContentPosition) node.getUserData(DomCreatorHandler.CONTENT_POSITION_KEY);
	}

	private static class DomBuilderSet {
		final DocumentBuilderFactory docBuilderFactory;
		DocumentBuilder docBuilder;

		DomBuilderSet() {
			docBuilderFactory = DocumentBuilderFactory.newInstance();
		}

		Document createDocument() throws IllegalStateException {
			if (docBuilder == null) {
				try {
					docBuilder = docBuilderFactory.newDocumentBuilder();
				} catch (ParserConfigurationException e) {
					throw new IllegalStateException("Could not configure DOM builder", e);
				}
			}
			return docBuilder.newDocument();
		}
	}

	private static final ThreadLocal<DomBuilderSet> DOM_BUILDERS = ThreadLocal.withInitial(DomBuilderSet::new);

	private <H extends ParseHandler> H parseXml(ParseSession session, H handler) throws IOException, XmlParseException {
		if (handler == null)
			throw new NullPointerException("Handler cannot be null");
		try {
			_parseXml(session, handler);
		} catch (IOException e) {
			handler.handleIOError(e, session.getFilePosition(false));
			throw e;
		} catch (XmlParseException e) {
			handler.handleParseError(e);
			throw e;
		}
		return handler;
	}

	private void _parseXml(ParseSession session, ParseHandler handler) throws IOException, XmlParseException {
		// First, find the declaration, if it exists, or the start of the root element if not
		char ch = session.skipWS();
		if (ch != '<')
			session.throwException(false, "The first non-whitespace character in an XML document must be '<', not '" + ch + "'");
		// Found the '<'. This is either the XML declaration or the start of the root element if the declaration is missing
		ch = session.nextChar();
		if (ch == '?') { // XML Declaration
			handleXmlDeclaration(session, handler);

			// The declaration is handled.
			// Now we need to move to the root element, where the code below expects, just like if there had been no XML declaration.
			ch = session.skipWS();
			if (ch != '<')
				session.throwException(false, "The first non-whitespace character after the XML declaration must be '<', not '" + ch + "'");
			ch = session.nextChar();
		}

		while (ch == '!' || ch == '?') { // DOCTYPE declaration or comment, or processing instruction
			if (ch == '!') {
				ch = session.nextChar();
				session.mark();
				if (ch == 'D') {// DOCTYPE declaration
					if (!session.expect("OCTYPE"))
						session.throwException(false, "'<!DOCTYPE' expected but not found");
					session.throwException(true, "DOCTYPE declarations are not supported by this parser");
				} else if (ch == '-') { // Comment
					handleComment(session, handler);

					ch = session.skipWS();
					if (ch != '<')
						session.throwException(false, "'<' expected, not '" + ch + "'");
					ch = session.nextChar();
				} else
					session.throwException(false,
						"'<!' here is expected to be followed by 'DOCTYPE' for a DOCTYPE declaration or '--' for a comment");
			} else { // Processing instruction
				handleProcessingInstruction(session, handler);

				ch = session.skipWS();
				if (ch != '<')
					session.throwException(false, "'<' expected, not '" + ch + "'");
				ch = session.nextChar();
			}
		}

		// Now we should be at the name of the root element
		handleElement(session, handler);

		session.setContentComplete(true);
		session.skipWS();
		while (!session.isAtEnd()) {
			session.setContentComplete(false);
			session.mark();
			if (session.currentChar() == '<') {
				if (session.nextChar() == '!') {
					if (session.nextChar() != '-')
						session.throwException(true, "Comment expected");
					handleComment(session, handler);
				} else if (session.currentChar() == '?') // Processing instruction
					handleProcessingInstruction(session, handler);
				else
					session.throwException(true, "Unexpected character sequence in XML after root element");
			} else
				session.throwException(false, "Unexpected character in XML after root element");

			session.setContentComplete(true);
			session.skipWS();
		}
	}

	private static void handleXmlDeclaration(ParseSession session, ParseHandler handler) throws IOException, XmlParseException {
		if (session.getPosition() != 1)
			session.throwException(false, "XML declaration must be at the first position of the first line of the XML document");
		session.mark();
		if (!session.expect("xml"))
			session.throwException(true, "XML Declaration must start with '<?xml'");
		if (!Character.isWhitespace(session.nextChar()))
			session.throwException(false, "Expected whitespace after beginning of XML declaration");

		String versionStr = null;
		Charset encoding = null;
		Boolean standalone = null;
		char ch = session.skipWS();
		session.mark();
		while (ch >= 'a' && ch <= 'z') {
			// New declaration attribute
			String attrName = session.getName();
			boolean version, enc;
			switch (attrName) {
			case "version":
				if (versionStr != null)
					session.throwException(true, "Duplicate 'version' attribute on XML declaration");
				version = true;
				enc = false;
				break;
			case "encoding":
				if (encoding != null)
					session.throwException(true, "Duplicate 'encoding' attribute on XML declaration");
				enc = true;
				version = false;
				break;
			case "standalone":
				if (standalone != null)
					session.throwException(true, "Duplicate 'standalone' attribute on XML declaration");
				version = enc = false;
				break;
			default:
				session.throwException(true, "Only 'version', 'encoding', or 'standalone' attributes are allowed on the XML declaration");
				return;
			}
			session.startAttribute(attrName);
			session.mark();
			String value = session.getTextValue(true, '"').toString();
			if (version) {
				// validate the version number?
				versionStr = value;
			} else if (enc) {
				try {
					encoding = Charset.forName(value);
				} catch (IllegalCharsetNameException e) {
					session.throwException(true, "Illegal character set name: " + value);
				} catch (UnsupportedCharsetException e) {
					session.throwException(true, "Unsupported character set: " + value);
				}
			} else {
				switch (value) {
				case "yes":
					standalone = Boolean.TRUE;
					break;
				case "no":
					standalone = Boolean.FALSE;
					break;
				default:
					session.throwException(true, "standalone must be 'yes' or 'no', not '" + value + "'");
					break;
				}
			}

			ch = session.skipWS();
		}
		session.mark();
		if (ch != '?' || session.nextChar() != '>')
			session.throwException(false, "XML declaration must end with '?>");
		if (versionStr == null)
			session.throwException(false, "XML declaration must include the 'version' attribute");
		handler.handleDeclaration(new XmlDeclaration(versionStr, encoding, standalone));

		session.setEncoding(encoding == null ? Charset.forName("UTF-8") : encoding);
	}

	private static void handleComment(ParseSession session, ParseHandler handler) throws IOException, XmlParseException {
		if (!session.expect("-"))
			session.throwException(false, "'<!-' here should be followed by another '-' for a comment");
		ContentPosition comment = session.getSpecialContent("--");
		if (session.nextChar() != '>')
			session.throwException(true, "'--' is not allowed in comments");
		handler.handleComment(comment.toString(), comment);
	}

	private static void handleProcessingInstruction(ParseSession session, ParseHandler handler) throws IOException, XmlParseException {
		session.nextChar();
		FilePosition pos = session.getFilePosition(false);
		session.mark();
		String target = session.getName();
		if (target.equalsIgnoreCase("xml"))
			session.throwException(true, "Processing instruction cannot be 'xml' with any character case");
		if (!Character.isWhitespace(session.currentChar())) {
			session.mark();
			if (session.currentChar() == '?' && session.nextChar() == '>') // No content
				handler.handleProcessingInstruction(target, pos, null, null);
			else
				session.throwException(true, "Processing instruction target must be followed by '?>' or whitespace");
		} else {
			ContentPosition content = session.getSpecialContent("?>");
			handler.handleProcessingInstruction(target, pos, content.toString(), content);
		}
		session.nextChar();
	}

	private void handleElement(ParseSession session, ParseHandler handler) throws IOException, XmlParseException {
		if (Character.isWhitespace(session.currentChar()))
			session.skipWS();
		FilePosition startPos = session.getFilePosition(false);
		String elementName = session.getName();
		session.openElement(elementName, startPos);
		handler.handleElementStart(elementName, startPos);
		Set<String> attributes = null;
		if (Character.isWhitespace(session.currentChar()))
			session.skipWS();
		while (session.currentChar() != '/' && session.currentChar() != '>') {
			// Attribute
			session.mark();
			FilePosition pos = session.getFilePosition(false);
			String attributeName = session.getName();
			if (attributes == null)
				attributes = new HashSet<>();
			if (!attributes.add(attributeName))
				session.throwException(true, "Multiple '" + attributeName + "' attributes specified on this element");
			session.startAttribute(attributeName);
			ContentPosition value = session.getTextValue(true, '"');
			handler.handleAttributeValue(attributeName, pos, value.toString(), value);
			session.skipWS();
		}
		if (session.currentChar() == '/') {// Self-closing element
			if (session.nextChar() != '>')
				session.throwException(false, "'>' expected");
			handler.handleElementEnd(elementName, startPos, true);
			session.closeElement();
			return;
		}

		session.nextChar();
		while (true) {
			session.mark();
			if (session.currentChar() == '<') { // Comment, CDATA, or child element
				if (session.nextChar() == '!') { // Comment or CDATA
					if (session.nextChar() == '[') { // CDATA
						if (!session.expect("CDATA["))
							session.throwException(true, "Bad CDATA initializer");
						ContentPosition content = session.getSpecialContent("]]>");
						handler.handleCDataContent(elementName, content.toString(), content);
					} else if (session.currentChar() == '-') { // Comment
						handleComment(session, handler);
						session.nextChar(); // Move past the terminal '>'
					} else
						session.throwException(true, "Misplaced '<' or malformed XML construct");
				} else if (session.currentChar() == '?') // Processing instruction
					handleProcessingInstruction(session, handler);
				else if (session.currentChar() == '/') { // Closing element
					session.skipWS();
					session.mark();
					FilePosition pos = session.getFilePosition(false);
					String closingElement = session.getName();
					if (!closingElement.equals(elementName))
						session.throwException(true, "Closing element for '" + elementName + "' expected, not '" + closingElement + "'");
					if (Character.isWhitespace(session.currentChar()))
						session.skipWS();
					if (session.currentChar() != '>')
						session.throwException(false, "'>' expected");
					handler.handleElementEnd(elementName, pos, false);
					session.closeElement();
					return;
				} else {// Child element
					handleElement(session, handler);
					session.nextChar(); // Move past the terminal '>'
				}
			} else { // Element content
				ContentPosition content = session.getTextValue(false, '<');
				handler.handleElementContent(elementName, content.toString(), content);
			}
		}
	}

	static boolean isNameChar(int ch) {
		switch (ch) {
		case '-':
		case '_':
		case '.':
		case ':':
			return true;
		default:
			return Character.isLetter(ch) || Character.isDigit(ch);
		}
	}

	class ParseSession {
		private final InputStream theStream;
		private Reader theReader;
		private char theChar;
		private int thePosition;
		private int theLineNumber;
		private int theCharNumber;
		private boolean isContentComplete;
		private boolean isAtEnd;

		private LocatedXmlElement theElement;
		private int theMarkPosition;
		private int theMarkLineNumber;
		private int theMarkCharNumber;

		private final StringBuilder theBuffer;

		ParseSession(InputStream stream) {
			theStream = stream;
			theBuffer = new StringBuilder();
		}

		ParseSession(Reader reader) {
			theStream = null;
			theReader = reader;
			theBuffer = new StringBuilder();
		}

		ParseSession setContentComplete(boolean complete) {
			isContentComplete = complete;
			return this;
		}

		boolean isAtEnd() {
			return isAtEnd;
		}

		char currentChar() {
			return theChar;
		}

		char nextChar() throws IOException, XmlParseException {
			if (thePosition > 0) {
				switch (theChar) {
				case '\n':
					theLineNumber++;
					theCharNumber = 0;
					break;
				case '\t':
					theCharNumber += theTabLength;
					break;
				default:
					theCharNumber++;
				}
			}
			int ch;
			do {
				if (theReader != null)
					ch = theReader.read();
				else
					ch = theStream.read();
			} while (ch == '\r'); // Trash character
			if (ch < 0) {
				if (!isContentComplete)
					throwException(false, "Unexpected end of XML content");
				isAtEnd = true;
			}
			theChar = (char) ch;
			thePosition++;
			return theChar;
		}

		char skipWS() throws IOException, XmlParseException {
			char ch = nextChar();
			while (Character.isWhitespace(ch)) {
				ch = nextChar();
			}
			return ch;
		}

		void openElement(String name, FilePosition position) {
			theElement = new LocatedXmlElement(theElement, name, position);
		}

		void closeElement() {
			theElement = theElement.getParent();
		}

		boolean expect(String text) throws IOException, XmlParseException {
			for (int c = 0; c < text.length(); c++) {
				if (nextChar() != text.charAt(c))
					return false;
			}
			return true;
		}

		ParseSession mark() {
			theMarkPosition = thePosition;
			theMarkLineNumber = theLineNumber;
			theMarkCharNumber = theCharNumber;
			return this;
		}

		void throwException(boolean atMark, String message) throws XmlParseException {
			int pos = atMark ? theMarkPosition : thePosition;
			int line = atMark ? theMarkLineNumber : theLineNumber;
			int ch = atMark ? theMarkCharNumber : theCharNumber;
			throw new XmlParseException(theElement, message, pos, line, ch);
		}

		void setEncoding(Charset charSet) {
			if (theReader == null)
				theReader = new InputStreamReader(theStream, charSet);
		}

		int getPosition() {
			return thePosition - 1; // This method returns the position of the current character
		}

		FilePosition getFilePosition(boolean atMark) {
			return new FilePosition(//
				atMark ? theMarkPosition : thePosition, //
				atMark ? theMarkLineNumber : theLineNumber, //
				atMark ? theMarkCharNumber : theCharNumber);
		}

		/** Parses an XML element or attribute name from the stream, including the current character */
		String getName() throws IOException, XmlParseException {
			// When we get here, the current character is the first character of the element's name
			if (theChar != '_' && !Character.isLetter(theChar))
				throwException(false, "Names must start with a letter or underscore, not '" + theChar + "'");
			theBuffer.setLength(0);
			theBuffer.append(theChar);
			for (char ch = nextChar(); isNameChar(ch); ch = nextChar())
				theBuffer.append(ch);
			return theBuffer.toString();
		}

		/** Moves past the '="' sequence between an attribute's name and its value */
		void startAttribute(String attributeName) throws IOException, XmlParseException {
			if (Character.isWhitespace(theChar))
				skipWS();
			if (theChar != '=')
				throwException(false, "'=' expected");
			if (skipWS() != '"')
				throwException(false, "'\"' expected");
		}

		private final IntList lineBreaks = new IntList(false, false); // It will be sorted and unique, but the list doesn't need to enforce
		private final List<int[]> insertChars = new ArrayList<>();
		private final IntList lineInsertChars = new IntList(false, false);

		private void insertChars(int position, int count) {
			for (int i = 0; i < count; i++)
				lineInsertChars.add(position);
		}

		/** Parses an attribute value or element text content */
		ContentPosition getTextValue(boolean skipCurrent, char terminator) throws IOException, XmlParseException {
			// When we get here, we're still on the preceding quote defining the start of the value
			char ch = skipCurrent ? nextChar() : currentChar();
			int startPos = thePosition;
			int startLine = theLineNumber;
			int startChar = theCharNumber;
			theBuffer.setLength(0);
			lineBreaks.clear();
			insertChars.clear();
			lineInsertChars.clear();
			for (int pos = 0; ch != terminator; ch = nextChar(), pos++) {
				if (ch == '<')
					throwException(false, "'<' is not a valid character in an attribute value");
				if (ch == '&')
					ch = parseEscapeSequence(pos);
				if (ch == '\n') {
					lineBreaks.add(pos);
					insertChars.add(lineInsertChars.toArray());
					lineInsertChars.clear();
				} else if (ch == '\t')
					insertChars(pos, theTabLength - 1);
				theBuffer.append(ch);
			}
			insertChars.add(lineInsertChars.toArray());
			return createContent(theBuffer.toString(), startPos, startLine, startChar, lineBreaks, insertChars);
		}

		/** Parses special content, e.g. in a comment or CDATA, where only the termination string is not allowed */
		ContentPosition getSpecialContent(String terminator) throws IOException, XmlParseException {
			// When we get here, we're still on the preceding character defining the start of the value
			char ch = nextChar();
			int startPos = thePosition;
			int startLine = theLineNumber;
			int startChar = theCharNumber;
			theBuffer.setLength(0);
			lineBreaks.clear();
			insertChars.clear();
			lineInsertChars.clear();
			int terminatorIdx = 0;
			for (int pos = 0; true; ch = nextChar(), pos++) {
				if (ch == terminator.charAt(terminatorIdx)) {
					if (terminatorIdx == 0)
						mark();
					if (++terminatorIdx == terminator.length())
						break;
					continue;
				} else if (terminatorIdx > 0) {
					theBuffer.append(terminator, 0, terminatorIdx);
					terminatorIdx = 0;
				}
				if (ch == '\n') {
					lineBreaks.add(pos);
					insertChars.add(lineInsertChars.toArray());
					lineInsertChars.clear();
				} else if (ch == '\t')
					insertChars(pos, theTabLength - 1);
				theBuffer.append(ch);
			}
			insertChars.add(lineInsertChars.toArray());
			return createContent(theBuffer.toString(), startPos, startLine, startChar, lineBreaks, insertChars);
		}

		private char parseEscapeSequence(int pos) throws IOException, XmlParseException {
			mark();
			char ch = nextChar();
			switch (ch) {
			case 'a':
				ch = nextChar();
				switch (ch) {
				case 'm':
					// amp
					expectEscapeSequence("p");
					insertChars(pos, 4);
					return '&';
				case 'p':
					// apos
					expectEscapeSequence("os");
					insertChars(pos, 5);
					return '\'';
				default:
					throwException(true, "Unrecognized escape sequence");
					return '!'; // Won't get here
				}
			case 'g':
				// gt
				expectEscapeSequence("t");
				insertChars(pos, 3);
				return '>';
			case 'l':
				// lt
				expectEscapeSequence("t");
				insertChars(pos, 3);
				return '<';
			case 'q':
				// quot
				expectEscapeSequence("uot");
				insertChars(pos, 5);
				return '"';
			case 'x':
				if (nextChar() != '#')
					throwException(false, "'#' expected");

				mark();
				int code = nextHex();
				code = code * 16 + nextHex();
				code = code * 16 + nextHex();
				code = code * 16 + nextHex();

				if (nextChar() != ';')
					throwException(false, "';' expected");
				insertChars(pos, 7);
				return (char) code;
			default:
				throwException(true, "Unrecognized escape sequence");
				return '!'; // Won't get here
			}
		}

		private void expectEscapeSequence(String seq) throws IOException, XmlParseException {
			for (int c = 0; c < seq.length(); c++) {
				if (nextChar() != seq.charAt(c))
					throwException(true, "Unrecognized escape sequence");
			}
			if (nextChar() != ';')
				throwException(false, "';' expected");
		}

		private int nextHex() throws IOException, XmlParseException {
			char ch = nextChar();
			if (ch >= '0' && ch <= '9')
				return ch - '0';
			else if (ch >= 'a' && ch <= 'z')
				return ch - 'a' + 10;
			else if (ch >= 'A' && ch <= 'Z')
				return ch - 'A' + 10;
			else {
				throwException(true, "Expected 4 hexadecimal digits for unicode escape sequence");
				return 0; // Won't get here
			}
		}
	}

	private static int[][] EMPTY_INSERT_CHARS = new int[0][0];

	static ContentPosition createContent(String content, int contentStartPosition, int contentStartLine, int contentStartChar,
		IntList lineBreaks, List<int[]> insertChars) {
		if (lineBreaks.isEmpty() && insertChars.isEmpty())
			return new ContentPosition.Simple(new FilePosition(contentStartPosition, contentStartLine, contentStartChar), content);
		else
			return new PositionedContent(content, contentStartPosition, contentStartLine, contentStartChar, lineBreaks.toArray(),
				insertChars.isEmpty() ? EMPTY_INSERT_CHARS : insertChars.toArray(new int[insertChars.size()][]));
	}

	static class PositionedContent implements ContentPosition {
		final String theContent;
		private final int theContentStartPosition;
		private final int theContentStartLine;
		private final int theContentStartChar;
		private final int[] theLineBreaks;
		private final int[][] theInsertChars;

		PositionedContent(String content, int contentStartPosition, int contentStartLine, int contentStartChar, int[] lineBreaks,
			int[][] insertChars) {
			theContent = content;
			theContentStartPosition = contentStartPosition;
			theContentStartLine = contentStartLine;
			theContentStartChar = contentStartChar;
			theLineBreaks = lineBreaks;
			theInsertChars = insertChars;
		}

		@Override
		public int length() {
			return theContent.length();
		}

		@Override
		public FilePosition getPosition(int index) {
			if (index > theContent.length())
				throw new IndexOutOfBoundsException(index + " of " + theContent.length());
			int line = Arrays.binarySearch(theLineBreaks, index);
			if (line < 0)
				line = -line - 1;
			int lineStart = line == 0 ? 0 : theLineBreaks[line - 1];
			int charPos = index - lineStart;
			int insertIdx = Arrays.binarySearch(theInsertChars[line], index);
			if (insertIdx < 0)
				insertIdx = -insertIdx - 1;
			charPos += insertIdx;
			int pos = theContentStartPosition;
			for (int L = 0; L < theLineBreaks.length; L++)
				pos += theLineBreaks[L] + theInsertChars[L].length;
			pos += charPos;
			return new FilePosition(pos, theContentStartLine + line, theContentStartChar + charPos);
		}

		@Override
		public String toString() {
			return theContent;
		}
	}

	/**
	 * Main method that accepts a single argument--the path to a file to parse. This method parses the given file as XML, printing
	 * information about each structure in the XML, with accompanying position information.
	 * 
	 * @param args Command-line arguments--should be a single path to a file on the system
	 * @throws IOException If an error occurs reading the file
	 * @throws XmlParseException If an error occurs parsing the file as XML
	 */
	public static void main(String... args) throws IOException, XmlParseException {
		try (InputStream in = new BufferedInputStream(new FileInputStream(args[0]))) {
			new SimpleXMLParser().setTabLength(3).parseXml(in, new ParseHandler() {
				int indent = 0;

				private void indent() {
					for (int i = 0; i < indent; i++)
						System.out.print('\t');
				}

				private String printStart(ContentPosition position) {
					return position.getPosition(0).toString();
				}

				private String printContent(String content) {
					return content.replaceAll("\n", "\\\\n").replaceAll("\t", "\\\\t");
				}

				@Override
				public void handleDeclaration(XmlDeclaration declaration) {
					System.out.println("Declaration " + declaration);
				}

				@Override
				public void handleProcessingInstruction(String target, FilePosition targetPosition, String content,
					ContentPosition contentPosition) {
					indent();
					System.out.println("Processing instruction @" + targetPosition + ": " + target + "=" + content);
				}

				@Override
				public void handleComment(String comment, ContentPosition position) {
					indent();
					System.out.println("Comment @" + printStart(position) + ": " + printContent(comment));
				}

				@Override
				public void handleElementStart(String name, FilePosition position) {
					indent();
					System.out.println("Element @" + position + ": " + name);
					indent++;
				}

				@Override
				public void handleAttributeValue(String attributeName, FilePosition namePosition, String attributeValue,
					ContentPosition valuePosition) {
					indent();
					System.out.println("Attribute @" + namePosition + ": " + attributeName + "=" + printContent(attributeValue) + " @"
						+ printStart(valuePosition));
				}

				@Override
				public void handleElementContent(String elementName, String elementValue, ContentPosition position) {
					indent();
					System.out.println("Content @" + printStart(position) + ": " + printContent(elementValue));
				}

				@Override
				public void handleCDataContent(String elementName, String content, ContentPosition position) {
					indent();
					System.out.println("CDATA @" + printStart(position) + ": " + printContent(content));
				}

				@Override
				public void handleElementEnd(String elementName, FilePosition position, boolean selfClosing) {
					indent--;
					indent();
					System.out.println("Close @" + position + ": " + elementName);
				}
			});
		}
	}
}

package org.qommons.io;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;

/** A simple utility for writing XML to a {@link Writer} serially */
public class XmlSerialWriter {
	/** The default version (1.0) that will be written for the document if not explicitly specified */
	public static final String DEFAULT_VERSION = "1.0";
	/** The default encoding (UTF-8) that will be written for the document if not explicitly specified */
	public static final String DEFAULT_ENCODING = "UTF-8";

	/** User-supplied code to configure an XML element */
	public interface XmlChild {
		/**
		 * @param element The element to populate
		 * @throws IOException If an error occurs writing the content
		 */
		void configure(Element element) throws IOException;
	}

	/**
	 * @param writer The writer to populate with XML
	 * @return The document to use to write XML data
	 * @throws IOException If an exception occurs initializing the XML document
	 */
	public static Document createDocument(Writer writer) throws IOException {
		return new Document(writer);
	}

	/** A {@link Document} or an {@link Element} */
	public static abstract class XmlComponent {
		boolean isNewLine;

		abstract Document getDocument();

		abstract void init() throws IOException;
		abstract void closeHeader() throws IOException;
		abstract void indent() throws IOException;

		/** @return -1 for a document, 0 for the root element, etc. */
		public abstract int getDepth();

		void preContent() throws IOException {
			closeHeader();
			if (getDocument().isContentOnSeparateLines() && !isNewLine)
				getDocument().getWriter().write('\n');
			isNewLine = false;
			indent();
		}

		/**
		 * @param comment The text of the comment to write
		 * @return This component
		 * @throws IOException If an exception occurs writing the comment
		 */
		public XmlComponent writeComment(String comment) throws IOException {
			preContent();
			getDocument().getWriter().append("<!--");
			writeXmlContent(getDocument().getWriter(), comment, XmlContentType.COMMENT);
			getDocument().getWriter().append("-->");
			return this;
		}

		XmlComponent addChild(String elementName, XmlChild onChild) throws IOException {
			preContent();
			Element element = new Element(getDocument(), elementName, getDepth() + 1);
			if (onChild != null)
				onChild.configure(element);
			element.close();
			if (getDocument().isContentOnSeparateLines()) {
				getDocument().getWriter().write('\n');
				isNewLine = true;
			}
			return this;
		}
	}

	/** An XML document */
	public static class Document extends XmlComponent {
		private final Writer theWriter;
		private Stage theStage;

		private boolean hasWrittenVersion;
		private boolean hasWrittenEncoding;

		private String theIndent;
		private boolean isContentOnSeparateLines;

		Document(Writer writer) throws IOException {
			theWriter = writer;
			theStage = Stage.HEADER;
			theIndent = "\t";
			isContentOnSeparateLines = true;

			init();
		}

		/** @return The indent String that will be used to indent children in this document */
		public String getIndent() {
			return theIndent;
		}

		/**
		 * @param indent The indent String that will be used to indent children in this document
		 * @return This document
		 */
		public Document setIndent(String indent) {
			theIndent = indent;
			return this;
		}

		/** @return Whether this document will insert a newline character between elements */
		public boolean isContentOnSeparateLines() {
			return isContentOnSeparateLines;
		}

		/**
		 * @param contentOnSeparateLines Whether this document will insert a newline character between elements
		 * @return This document
		 */
		public Document setContentOnSeparateLines(boolean contentOnSeparateLines) {
			isContentOnSeparateLines = contentOnSeparateLines;
			return this;
		}

		Writer getWriter() {
			return theWriter;
		}

		@Override
		Document getDocument() {
			return this;
		}

		@Override
		void indent() throws IOException {}

		@Override
		public int getDepth() {
			return -1;
		}

		@Override
		void init() throws IOException {
			theWriter.append("<?xml");
		}

		/**
		 * @param version The version string to write for this document
		 * @return This document
		 * @throws IOException If an exception occurs writing the data
		 */
		public Document setVersion(String version) throws IOException {
			if (theStage != Stage.HEADER)
				throw new IllegalStateException("Cannot write version except in the header before any content");
			if (hasWrittenVersion)
				throw new IllegalStateException("Version has already been written");
			hasWrittenVersion = true;
			if (version != null) {
				theWriter.write(" version=\"");
				theWriter.write(version);
				theWriter.write('\"');
			}
			return this;
		}

		/**
		 * @param encoding The encoding string to write for this document
		 * @return This document
		 * @throws IOException If an exception occurs writing the data
		 */
		public Document setEncoding(String encoding) throws IOException {
			if (theStage != Stage.HEADER)
				throw new IllegalStateException("Cannot write encoding except in the header before any content");
			if (hasWrittenEncoding)
				throw new IllegalStateException("Encoding has already been written");
			hasWrittenEncoding = true;
			if (encoding != null) {
				theWriter.write(" encoding=\"");
				theWriter.write(encoding);
				theWriter.write('\"');
			}
			return this;
		}

		@Override
		void closeHeader() throws IOException {
			if (theStage != Stage.HEADER)
				return;
			if (!hasWrittenVersion)
				setVersion(DEFAULT_VERSION);
			if (!hasWrittenEncoding)
				setEncoding(DEFAULT_ENCODING);

			theWriter.append(" ?>");
			theStage = Stage.POST_HEADER;
		}

		/**
		 * @param whitespace The whitespace string to write
		 * @return This document
		 * @throws IOException If an exception occurs writing the data
		 */
		public Document writeWhitespace(String whitespace) throws IOException {
			for (int c = 0; c < whitespace.length(); c++)
				if (!Character.isWhitespace(whitespace.charAt(c)))
					throw new IllegalArgumentException("Character " + whitespace.charAt(c) + " (\\u" + //
						Integer.toHexString(whitespace.codePointAt(c)) + ")");
			if (whitespace.length() > 0 && whitespace.charAt(whitespace.length() - 1) == '\n')
				isNewLine = true;
			writeXmlContent(theWriter, whitespace, XmlContentType.CONTENT);
			return this;
		}

		@Override
		public Document writeComment(String comment) throws IOException {
			super.writeComment(comment);
			return this;
		}

		/**
		 * @param rootElementName The name for the root element of this document
		 * @param body The code to configure the root element
		 * @return This document
		 * @throws IOException If an exception occurs writing the data
		 */
		public Document writeRoot(String rootElementName, XmlChild body) throws IOException {
			if (theStage == Stage.POST_CONTENT)
				throw new IllegalStateException("Root element has already been written");
			super.addChild(rootElementName, body);
			return this;
		}

		enum Stage {
			HEADER, POST_HEADER, POST_CONTENT
		}
	}

	/** An XML element */
	public static class Element extends XmlComponent {
		private final Document theDocument;
		private final String theElementName;
		private final int theDepth;

		private boolean isEmpty;
		private boolean isHeaderClosed;
		private boolean isClosed;

		Element(Document doc, String elementName, int depth) throws IOException {
			theDocument = doc;
			theElementName = elementName;
			theDepth = depth;
			isEmpty = true;

			init();
		}

		@Override
		Document getDocument() {
			return theDocument;
		}

		@Override
		void closeHeader() throws IOException {
			if (isHeaderClosed)
				return;
			theDocument.getWriter().write('>');
			isHeaderClosed = true;
			isEmpty = false;
		}

		@Override
		void indent() throws IOException {
			for (int i = 0; i < theDepth; i++)
				theDocument.getWriter().write(theDocument.getIndent());
		}

		@Override
		public int getDepth() {
			return theDepth;
		}

		@Override
		void init() throws IOException {
			indent();
			theDocument.getWriter().write('<');
			writeXmlContent(theDocument.getWriter(), theElementName, XmlContentType.ELEMENT_NAME);
		}

		@Override
		public Element writeComment(String comment) throws IOException {
			super.writeComment(comment);
			return this;
		}

		/**
		 * @param attribute The name of the attribute to write
		 * @param value The value to write for the attribute
		 * @return This element
		 * @throws IOException If an exception occurs writing the data
		 */
		public Element att(String attribute, String value) throws IOException {
			return addAttribute(attribute, value);
		}

		/**
		 * @param attribute The name of the attribute to write
		 * @param value The value to write for the attribute
		 * @return This element
		 * @throws IOException If an exception occurs writing the data
		 */
		public Element addAttribute(String attribute, String value) throws IOException {
			if (isClosed)
				throw new IllegalStateException("This element has already been closed");
			if (isHeaderClosed)
				throw new IllegalStateException("This element's header has already been closed to allow for content");
			theDocument.getWriter().write(' ');
			writeXmlContent(theDocument.getWriter(), attribute, XmlContentType.ATTRIBUTE_NAME);
			theDocument.getWriter().write("=\"");
			writeXmlContent(theDocument.getWriter(), value, XmlContentType.ATTRIBUTE_VALUE);
			theDocument.getWriter().write('\"');
			return this;
		}

		/**
		 * @param childName The name for the child element
		 * @param onChild The code to configure the child element
		 * @return This element
		 * @throws IOException If an exception occurs writing the data
		 */
		public Element child(String childName, XmlChild onChild) throws IOException {
			return addChild(childName, onChild);
		}

		@Override
		public Element addChild(String childName, XmlChild onChild) throws IOException {
			if (isClosed)
				throw new IllegalStateException("This element has already been closed");
			super.addChild(childName, onChild);
			return this;
		}

		/**
		 * @param content Text content for this element
		 * @return This element
		 * @throws IOException If an exception occurs writing the data
		 */
		public Element addContent(String content) throws IOException {
			if (isClosed)
				throw new IllegalStateException("This element has already been closed");
			preContent();
			writeXmlContent(theDocument.getWriter(), content, XmlContentType.CONTENT);
			return this;
		}

		/**
		 * Specifies that this element should not be self-closing (e.g. &lt;element attr="attrValue" /&gt;)
		 * 
		 * @return This element
		 */
		public Element nonEmpty() {
			if (isClosed)
				throw new IllegalStateException("This element has already been closed");
			isEmpty = false;
			return this;
		}

		void close() throws IOException {
			if (isEmpty) {
				theDocument.getWriter().write(" />");
				isHeaderClosed = true;
			} else {
				preContent();
				theDocument.getWriter().write("</");
				writeXmlContent(theDocument.getWriter(), theElementName, XmlContentType.ELEMENT_NAME);
				theDocument.getWriter().write(">");
			}
			isClosed = true;
		}
	}

	enum XmlContentType {
		CONTENT, COMMENT, ATTRIBUTE_NAME, ATTRIBUTE_VALUE, ELEMENT_NAME;
	}

	private static final char[] ILLEGAL_ELEMENT_CHARS;
	private static final char[] ESCAPE_CHARS;
	private static final String[] BAD_CHAR_ESCAPES;
	static {
		ILLEGAL_ELEMENT_CHARS = new char[] { ' ', '\'', '"', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '{', '}', '=', '<', '>', '+',
			'/', ';', '?', '`', '~', '|', ',' };
		Arrays.sort(ILLEGAL_ELEMENT_CHARS);

		ESCAPE_CHARS = new char[] { '<', '&', '"' };
		Arrays.sort(ESCAPE_CHARS);
		BAD_CHAR_ESCAPES = new String[ESCAPE_CHARS.length];
		for (int i = 0; i < ESCAPE_CHARS.length; i++) {
			switch (ESCAPE_CHARS[i]) {
			case '<':
				BAD_CHAR_ESCAPES[i] = "&lt;";
				break;
			case '&':
				BAD_CHAR_ESCAPES[i] = "&amp;";
				break;
			case '"':
				BAD_CHAR_ESCAPES[i] = "&quot;";
				break;
			}
		}
	}

	static void writeXmlContent(Writer writer, String content, XmlContentType type) throws IOException {
		switch (type) {
		case ATTRIBUTE_NAME:
		case ELEMENT_NAME:
			if (content.length() == 0)
				throw new IllegalArgumentException("Empty element/attribute names are illegal");
			char initC = content.charAt(0);
			if (initC == '-' || initC == '.' || (initC >= '0' && initC <= '9'))
				throw new IllegalArgumentException("Element/attribute names may not begin with " + initC);
			break;
		default:
			break;
		}
		switch (type) {
		case ATTRIBUTE_NAME:
		case ELEMENT_NAME:
			for (int c = 0; c < content.length(); c++) {
				if (Arrays.binarySearch(ILLEGAL_ELEMENT_CHARS, content.charAt(c)) >= 0)
					throw new IllegalArgumentException("Element/attribute names may not contain " + content.charAt(c));
			}
			writer.write(content);
			break;
		case ATTRIBUTE_VALUE:
		case CONTENT:
			int start = 0;
			for (int c = 0; c < content.length(); c++) {
				int idx = Arrays.binarySearch(ESCAPE_CHARS, content.charAt(c));
				if (idx >= 0) {
					writer.write(content, start, c);
					writer.write(BAD_CHAR_ESCAPES[idx]);
					start = c;
				}
			}
			if (start < content.length())
				writer.write(content, start, content.length());
			break;
		case COMMENT:
			boolean wasDash = false;
			for (int c = 0; c < content.length(); c++) {
				if (content.charAt(c) == '-') {
					if (wasDash)
						throw new IllegalArgumentException("Comments cannot contain the sequence '--'");
					wasDash = true;
				} else if (wasDash)
					wasDash = false;
			}
			writer.write(content);
			break;
		}
	}
}

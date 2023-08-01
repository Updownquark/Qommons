package org.qommons.io;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.qommons.ArrayUtils;
import org.qommons.Named;
import org.qommons.QommonsUtils;
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
 * <p>
 * For uses that do not require schema validation in the parser, this class is a nice alternative. As a bonus, it is not subject to many
 * vulnerabilities that typical XML parsers are susceptible to due to their ability to pull in files as directed by the XML data.
 * </p>
 * </p>
 */
public class SimpleXMLParser {
	/** The name of the version attribute for the XML declaration */
	public static final String VERSION = "version";
	/** The name of the encoding attribute for the XML declaration */
	public static final String ENCODING = "encoding";
	/** The name of the standalone attribute for the XML declaration */
	public static final String STANDALONE = "standalone";
	/** Constant for the declaration of the beginning of a comment */
	public static final String COMMENT_START = "<!--";
	/** Constant for the declaration of the end of a comment */
	public static final String COMMENT_END = "-->";
	/** Constant for the declaration of the beginning of a CDATA section */
	public static final String CDATA_START = "<![CDATA[";
	/** Constant for the declaration of the end of a CDATA section */
	public static final String CDATA_END = "]]>";
	/** Constant for the declaration of the beginning of a processing instruction */
	public static final String PROCESSING_INSTRUCTION_BEGIN = "<?";
	/** Constant for the declaration of the end of a processing instruction */
	public static final String PROCESSING_INSTRUCTION_END = "?>";
	/** Constant for the declaration of a named entity */
	public static final String NAMED_ENTITY_PREFIX = "&";
	/** Constant for the declaration of a numerically-specified character in decimal notation */
	public static final String DECIMAL_ENTITY_PREFIX = "&#";
	/** Constant for the declaration of a numerically-specified character in hexadecimal notation */
	public static final String HEX_ENTITY_PREFIX = "&#x";
	/** Standard named entities in XML by their representation (e.g. "amp" for "&amp;", specified by "&amp;amp;") */
	public static final Map<String, String> STANDARD_NAMED_ENTITIES = QommonsUtils.<String, String> buildMap(null)//
		.with("quot", "\"")//
		.with("amp", "&")//
		.with("apos", "'")//
		.with("gt", ">")//
		.with("lt", "<")//
		.getUnmodifiable();
	private static final String NLCR = "\n\r";
	private static final String CRNL = "\r\n";
	private static final String TAB = "\t";

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

	/** Super interface for any XML structure passed to a {@link ParseHandler handler} */
	public interface XmlComponent {
		/** @return The character content defining the entire XML structure */
		PositionedContent getContent();
	}

	/** Represents the XML declaration which may occur at position zero of an XML file */
	public static class XmlDeclaration implements XmlComponent {
		private final String theVersion;
		private final Charset theEncoding;
		private final Boolean isStandalone;

		private final int theVersionNameOffset;
		private final int theVersionValueOffset;
		private final int theEncodingNameOffset;
		private final int theEncodingValueOffset;
		private final int theStandaloneNameOffset;
		private final int theStandaloneValueOffset;

		private final PositionedContent theDeclarationContent;

		/**
		 * @param version The XML version. This string is not validated.
		 * @param encoding The character set encoding specified in the declaration, or null if none was specified. This class assumes UTF-8
		 *        encoding if not specified.
		 * @param standalone Whether the 'standalone' attribute was specified as 'yes' or 'no', or null if it it was not specified
		 * @param declarationContent The text content declaring the XML declaration
		 * @param versionNameOffset The position of the start of the "version" attribute name relative to the start of the declaration, or
		 *        -1 if not specified
		 * @param versionValueOffset The position of the start of the "version" attribute value relative to the start of the declaration, or
		 *        -1 if not specified
		 * @param encodingNameOffset The position of the start of the "encoding" attribute name relative to the start of the declaration, or
		 *        -1 if not specified
		 * @param encodingValueOffset The position of the start of the "encoding" attribute value relative to the start of the declaration,
		 *        or -1 if not specified
		 * @param standaloneNameOffset The position of the start of the "standalone" attribute name relative to the start of the
		 *        declaration, or -1 if not specified
		 * @param standaloneValueOffset The position of the start of the "standalone" attribute value relative to the start of the
		 *        declaration, or -1 if not specified
		 */
		public XmlDeclaration(String version, Charset encoding, Boolean standalone, PositionedContent declarationContent, //
			int versionNameOffset, int versionValueOffset, int encodingNameOffset, int encodingValueOffset, int standaloneNameOffset,
			int standaloneValueOffset) {
			theVersion = version;
			theEncoding = encoding;
			isStandalone = standalone;
			theVersionNameOffset = versionNameOffset;
			theVersionValueOffset = versionValueOffset;
			theEncodingNameOffset = encodingNameOffset;
			theEncodingValueOffset = encodingValueOffset;
			theStandaloneNameOffset = standaloneNameOffset;
			theStandaloneValueOffset = standaloneValueOffset;
			theDeclarationContent = declarationContent;
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

		/**
		 * @return The "attributes" specified on this declaration, in order. Each element will be
		 *         <ul>
		 *         <li>{@link SimpleXMLParser#VERSION version}</li>
		 *         <li>{@link SimpleXMLParser#ENCODING encoding}</li>
		 *         <li>or {@link SimpleXMLParser#STANDALONE standalone}</li>
		 *         </ul>
		 */
		public List<String> getAttributes() {
			if (theVersion == null) {
				if (theEncoding == null) {
					if (isStandalone == null)
						return QommonsUtils.unmodifiableCopy();
					else
						return QommonsUtils.unmodifiableCopy(STANDALONE);
				} else if (theEncodingNameOffset < theStandaloneNameOffset)
					return QommonsUtils.unmodifiableCopy(ENCODING, STANDALONE);
				else
					return QommonsUtils.unmodifiableCopy(STANDALONE, ENCODING);
			} else {
				if (theEncoding == null) {
					if (isStandalone == null)
						return QommonsUtils.unmodifiableCopy(VERSION);
					else if (theVersionNameOffset < theStandaloneNameOffset)
						return QommonsUtils.unmodifiableCopy(VERSION, STANDALONE);
					else
						return QommonsUtils.unmodifiableCopy(STANDALONE, VERSION);
				} else if (theVersionNameOffset < theEncodingNameOffset) {
					if (isStandalone == null)
						return QommonsUtils.unmodifiableCopy(VERSION, ENCODING);
					else if (theVersionNameOffset < theStandaloneNameOffset) {
						if (theEncodingNameOffset < theStandaloneNameOffset)
							return QommonsUtils.unmodifiableCopy(VERSION, ENCODING, STANDALONE);
						else
							return QommonsUtils.unmodifiableCopy(VERSION, STANDALONE, ENCODING);
					} else
						return QommonsUtils.unmodifiableCopy(STANDALONE, VERSION, ENCODING);
				} else if (isStandalone == null)
					return QommonsUtils.unmodifiableCopy(ENCODING, VERSION);
				else if (theEncodingNameOffset < theStandaloneNameOffset) {
					if (theVersionNameOffset < theStandaloneNameOffset)
						return QommonsUtils.unmodifiableCopy(ENCODING, VERSION, STANDALONE);
					else
						return QommonsUtils.unmodifiableCopy(ENCODING, STANDALONE, VERSION);
				} else
					return QommonsUtils.unmodifiableCopy(STANDALONE, ENCODING, VERSION);
			}
		}

		/**
		 * @param attribute The "attribute" to get the name position for. Must be
		 *        <ul>
		 *        <li>{@link SimpleXMLParser#VERSION version}</li>
		 *        <li>{@link SimpleXMLParser#ENCODING encoding}</li>
		 *        <li>or {@link SimpleXMLParser#STANDALONE standalone}</li>
		 *        </ul>
		 * @return The positioned "attribute" in this declaration, or null if it was not specified
		 */
		public XmlAttribute getAttribute(String attribute) {
			int start, valueStart, end;
			switch (attribute) {
			case VERSION:
				start = theVersionNameOffset;
				if (start < 0)
					return null;
				valueStart = theVersionValueOffset;
				end = valueStart + theVersion.length() + 1;
				break;
			case ENCODING:
				start = theEncodingNameOffset;
				if (start < 0)
					return null;
				valueStart = theEncodingValueOffset;
				end = valueStart + theEncoding.name().length() + 1;
				break;
			case STANDALONE:
				start = theStandaloneNameOffset;
				if (start < 0)
					return null;
				valueStart = theStandaloneValueOffset;
				end = valueStart + (isStandalone.booleanValue() ? 3 : 2) + 1;
				break;
			default:
				throw new IllegalArgumentException("No such attribute '" + attribute + "' on XML declaration");
			}
			return new XmlAttribute(attribute, valueStart - start, theDeclarationContent.subSequence(start, end));
		}

		/**
		 * @param attribute The "attribute" to get the name position for. Must be
		 *        <ul>
		 *        <li>{@link SimpleXMLParser#VERSION version}</li>
		 *        <li>{@link SimpleXMLParser#ENCODING encoding}</li>
		 *        <li>or {@link SimpleXMLParser#STANDALONE standalone}</li>
		 *        </ul>
		 * @return The position of the name of the given "attribute" in this declaration, or null if it was not specified
		 */
		public FilePosition getAttributeNamePosition(String attribute) {
			int offset;
			switch (attribute) {
			case VERSION:
				offset = theVersionNameOffset;
				break;
			case ENCODING:
				offset = theEncodingNameOffset;
				break;
			case STANDALONE:
				offset = theStandaloneNameOffset;
				break;
			default:
				throw new IllegalArgumentException("No such attribute '" + attribute + "' on XML declaration");
			}
			return offset < 0 ? null : theDeclarationContent.getPosition(offset);
		}

		/**
		 * @param attribute The "attribute" to get the value content for. Must be
		 *        <ul>
		 *        <li>{@link SimpleXMLParser#VERSION version}</li>
		 *        <li>{@link SimpleXMLParser#ENCODING encoding}</li>
		 *        <li>or {@link SimpleXMLParser#STANDALONE standalone}</li>
		 *        </ul>
		 * @return The value content of the given "attribute" in this declaration, or null if it was not specified
		 */
		public PositionedContent getAttributeValue(String attribute) {
			int start, length;
			switch (attribute) {
			case VERSION:
				start = theVersionValueOffset;
				length = start < 0 ? 0 : theVersion.length();
				break;
			case ENCODING:
				start = theEncodingValueOffset;
				length = start < 0 ? 0 : theEncoding.displayName().length();
				break;
			case STANDALONE:
				start = theStandaloneValueOffset;
				length = start < 0 ? 0 : (isStandalone.booleanValue() ? 3 : 2);
				break;
			default:
				throw new IllegalArgumentException("No such attribute '" + attribute + "' on XML declaration");
			}
			return theDeclarationContent.subSequence(start, start + length);
		}

		/**
		 * @param attribute The "attribute" to get the end position for. Must be
		 *        <ul>
		 *        <li>{@link SimpleXMLParser#VERSION version}</li>
		 *        <li>{@link SimpleXMLParser#ENCODING encoding}</li>
		 *        <li>or {@link SimpleXMLParser#STANDALONE standalone}</li>
		 *        </ul>
		 * @return The position of the end quote of the value of the given "attribute" in this declaration, or null if it was not specified
		 */
		public FilePosition getAttributeValueEnd(String attribute) {
			int start, length;
			switch (attribute) {
			case VERSION:
				start = theVersionValueOffset;
				length = start < 0 ? 0 : theVersion.length();
				break;
			case ENCODING:
				start = theEncodingValueOffset;
				length = start < 0 ? 0 : theEncoding.displayName().length();
				break;
			case STANDALONE:
				start = theStandaloneValueOffset;
				length = start < 0 ? 0 : (isStandalone.booleanValue() ? 3 : 2);
				break;
			default:
				throw new IllegalArgumentException("No such attribute '" + attribute + "' on XML declaration");
			}
			return theDeclarationContent.getPosition(start + length + 1);
		}

		/** @return The offset of the name of the {@link SimpleXMLParser#VERSION version} attribute, or -1 if it was not specified */
		public int getVersionNameOffset() {
			return theVersionNameOffset;
		}

		/** @return The offset of the value of the {@link SimpleXMLParser#VERSION version} attribute, or -1 if it was not specified */
		public int getVersionValueOffset() {
			return theVersionValueOffset;
		}

		/** @return The offset of the name of the {@link SimpleXMLParser#ENCODING encoding} attribute, or -1 if it was not specified */
		public int getEncodingNameOffset() {
			return theEncodingNameOffset;
		}

		/** @return The offset of the value of the {@link SimpleXMLParser#ENCODING encoding} attribute, or -1 if it was not specified */
		public int getEncodingValueOffset() {
			return theEncodingValueOffset;
		}

		/** @return The offset of the name of the {@link SimpleXMLParser#STANDALONE standalone} attribute, or -1 if it was not specified */
		public int getStandaloneNameOffset() {
			return theStandaloneNameOffset;
		}

		/** @return The offset of the value of the {@link SimpleXMLParser#STANDALONE standalone} attribute, or -1 if it was not specified */
		public int getStandaloneValueOffset() {
			return theStandaloneValueOffset;
		}

		/** @return The position of the start of the "version" attribute name, if specified */
		public FilePosition getVersionNamePosition() {
			return theDeclarationContent.getPosition(theVersionNameOffset);
		}

		/** @return The position of the start of the "version" attribute value, if specified */
		public FilePosition getVersionValuePosition() {
			return theDeclarationContent.getPosition(theVersionValueOffset);
		}

		/** @return The position of the start of the "encoding" attribute name, if specified */
		public FilePosition getEncodingNamePosition() {
			if (theEncodingNameOffset < 0)
				return null;
			return theDeclarationContent.getPosition(theEncodingNameOffset);
		}

		/** @return The position of the start of the "encoding" attribute value, if specified */
		public FilePosition getEncodingValuePosition() {
			if (theEncodingNameOffset < 0)
				return null;
			return theDeclarationContent.getPosition(theEncodingValueOffset);
		}

		/** @return The position of the start of the "standalone" attribute name, if specified */
		public FilePosition getStandaloneNamePosition() {
			if (theStandaloneNameOffset < 0)
				return null;
			return theDeclarationContent.getPosition(theStandaloneNameOffset);
		}

		/** @return The position of the start of the "standalone" attribute value, if specified */
		public FilePosition getStandaloneValuePosition() {
			if (theStandaloneNameOffset < 0)
				return null;
			return theDeclarationContent.getPosition(theStandaloneValueOffset);
		}

		@Override
		public PositionedContent getContent() {
			return theDeclarationContent;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder("<?xml version=").append(theVersion);
			if (theEncoding != null)
				str.append(" encoding=").append(theEncoding);
			if (isStandalone != null)
				str.append(" standalone=").append(isStandalone.booleanValue() ? "yes" : "no");
			str.append(" ?>");
			return str.toString();
		}
	}

	/**
	 * <p>
	 * An XML processing instruction parsed by a {@link SimpleXMLParser}.
	 * </p>
	 * <p>
	 * A processing instruction is of the form <code>&lt;?TARGET VALUE?></code>, where VALUE is optional
	 */
	public static class XmlProcessingInstruction implements XmlComponent {
		private final String theTargetName;
		private final int theValueOffset;
		private final PositionedContent theContent;

		/**
		 * @param targetName The name of the processing instruction's target
		 * @param contentOffset The offset of the processing instruction's content, or -1 if there was no content
		 * @param content The text content defining the processing instruction, including <code>&lt;?</code> and <code>?></code>
		 */
		public XmlProcessingInstruction(String targetName, int contentOffset, PositionedContent content) {
			theTargetName = targetName;
			theValueOffset = contentOffset;
			theContent = content;
		}

		/** @return The name of the processing instruction's target */
		public String getTargetName() {
			return theTargetName;
		}

		/** @return The offset of the processing instruction's content, or -1 if there was no content */
		public int getValueOffset() {
			return theValueOffset;
		}

		/** @return The text content defining the processing instruction */
		public FilePosition getTargetPosition() {
			return theContent.getPosition(PROCESSING_INSTRUCTION_END.length());
		}

		@Override
		public PositionedContent getContent() {
			return theContent;
		}

		/** @return The text content containing the processing instruction's target */
		public PositionedContent getTargetContent() {
			return theContent.subSequence(PROCESSING_INSTRUCTION_BEGIN.length(),
				PROCESSING_INSTRUCTION_BEGIN.length() + theTargetName.length());
		}

		/** @return The text content containing the processing instruction's value, or null if no value was specified */
		public PositionedContent getValueContent() {
			return theValueOffset < 0 ? null
				: theContent.subSequence(theValueOffset, theContent.length() - PROCESSING_INSTRUCTION_END.length());
		}

		@Override
		public String toString() {
			return theContent.toString();
		}
	}

	/** An XML comment parsed by a {@link SimpleXMLParser} */
	public static class XmlComment implements XmlComponent {
		private final PositionedContent theContent;

		/** @param content The text content defining the comment, including <code>&lt;!--</code> and <code>--></code> */
		public XmlComment(PositionedContent content) {
			theContent = content;
		}

		@Override
		public PositionedContent getContent() {
			return theContent;
		}

		/** @return The text content of the comment, between the opening <code>&lt;!--</code> and the closing <code>--></code> */
		public PositionedContent getValueContent() {
			return theContent.subSequence(COMMENT_START.length(), theContent.length() - COMMENT_END.length());
		}

		@Override
		public String toString() {
			return theContent.toString();
		}
	}

	/** Represents an open or close tag of an XML element being parsed by a {@link SimpleXMLParser} */
	public static class XmlElementTerminal implements XmlComponent, Named {
		private final String theName;
		private final int theNameOffset;
		private final PositionedContent theContent;

		/**
		 * @param elementName The name of the element
		 * @param nameOffset The offset of the name of the element in the open or close tag
		 * @param content The content defining the element's open (everything between and including the initial <code>&lt;</code> and the
		 *        element's name) or close tag (everything between and including <code>&lt;/</code> and <code>></code>
		 */
		public XmlElementTerminal(String elementName, int nameOffset, PositionedContent content) {
			theName = elementName;
			theNameOffset = nameOffset;
			theContent = content;
		}

		@Override
		public String getName() {
			return theName;
		}

		/** @return The offset of the name of the element in the open or close tag */
		public int getNameOffset() {
			return theNameOffset;
		}

		/** @return The position of the beginning of the element's name */
		public FilePosition getNamePosition() {
			if (theNameOffset < 0)
				return null; // Self-closing tag
			return theContent.getPosition(theNameOffset);
		}

		@Override
		public PositionedContent getContent() {
			return theContent;
		}

		/** @return The text content containing the element's name */
		public PositionedContent getNameContent() {
			return theContent.subSequence(theNameOffset);
		}

		@Override
		public String toString() {
			return theContent.toString();
		}
	}

	/** An XML attribute parsed by a {@link SimpleXMLParser} */
	public static class XmlAttribute implements XmlComponent, Named {
		private final String theName;
		private final int theValueStartOffset;
		private final PositionedContent theContent;

		/**
		 * @param attributeName The name of the attribute
		 * @param valueStartOffset The offset of the attribute's value in its declaration
		 * @param content The content defining the attribute, everything between and including the attribute name and the terminal
		 *        <code>"</code>
		 */
		public XmlAttribute(String attributeName, int valueStartOffset, PositionedContent content) {
			theName = attributeName;
			theValueStartOffset = valueStartOffset;
			theContent = content;
		}

		@Override
		public String getName() {
			return theName;
		}

		/** @return The position of the start of the attribute's name */
		public FilePosition getNamePosition() {
			return theContent.getPosition(0);
		}

		/** @return The offset of the attribute's value in its declaration */
		public int getValueStartOffset() {
			return theValueStartOffset;
		}

		@Override
		public PositionedContent getContent() {
			return theContent;
		}

		/** @return The text content containing the value of the attribute */
		public PositionedContent getValueContent() {
			return theContent.subSequence(theValueStartOffset, theContent.length() - 1);
		}

		@Override
		public String toString() {
			return theContent.toString();
		}
	}

	/** An XML CDATA structure parsed by a {@link SimpleXMLParser} */
	public static class XmlCdata implements XmlComponent {
		private final PositionedContent theContent;

		/**
		 * @param content The content defining the CDATA, everything between and including the terminal {@link SimpleXMLParser#CDATA_START
		 *        &lt;![CDATA[} and {@link SimpleXMLParser#CDATA_END ]]>}
		 */
		public XmlCdata(PositionedContent content) {
			theContent = content;
		}

		@Override
		public PositionedContent getContent() {
			return theContent;
		}

		/** @return The text content containing the character data in the CDATA structure */
		public PositionedContent getValueContent() {
			return theContent.subSequence(CDATA_START.length(), theContent.length() - CDATA_END.length());
		}

		@Override
		public String toString() {
			return theContent.toString();
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
		 * @param pi The processing instruction
		 */
		default void handleProcessingInstruction(XmlProcessingInstruction pi) {
		}

		/**
		 * Called when an XML comment is encountered
		 * 
		 * @param comment The comment
		 */
		default void handleComment(XmlComment comment) {
		}

		/**
		 * Called when the declaration of a root or a child element is encountered
		 * 
		 * @param element The element
		 */
		default void handleElementStart(XmlElementTerminal element) {
		}

		/**
		 * Called when an element's opening tag ends without being self-closing
		 * 
		 * @param elementName The name of the element
		 * @param openEnd The content closing the open tag
		 */
		default void handleElementOpen(String elementName, PositionedContent openEnd) {
		}

		/**
		 * Called when an attribute is encountered
		 * 
		 * @param attribute The attribute
		 */
		default void handleAttribute(XmlAttribute attribute) {
		}

		/**
		 * <p>
		 * Called when any characters occur between an element's open and close tags which is not an XML structure such as a
		 * {@link #handleElementStart(XmlElementTerminal) child element}, a {@link #handleCDataContent(String, XmlCdata) CDATA} structure,
		 * or a {@link #handleCDataContent(String, XmlCdata) comment}.
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
		 * @param elementValue The positioned content text
		 */
		default void handleElementContent(String elementName, PositionedContent elementValue) {
		}

		/**
		 * Called when a CDATA structure is encountered.
		 * 
		 * @param elementName The name of the element under which the CDATA structure occurred
		 * @param cdata The CDATA structure
		 */
		default void handleCDataContent(String elementName, XmlCdata cdata) {
		}

		/**
		 * Called when an element is closed
		 * 
		 * @param element The element being closed
		 * @param selfClosing Whether the element was self-closing, as opposed to opened and closed with separate tags
		 */
		default void handleElementEnd(XmlElementTerminal element, boolean selfClosing) {
		}

		/**
		 * Called when white space occurs outside of the positioned content of any XML structure. E.g. white space before and after the root
		 * element but not in a comment or processing instruction, or the white space between attributes in an element opening tag.
		 * 
		 * @param whitespace The positioned white space content
		 */
		default void handleIgnorableWhitespace(PositionedContent whitespace) {
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
		 * {@link Node#getUserData(String) User data} key in which the {@link PositionedContent position} of the node's
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
		public void handleProcessingInstruction(XmlProcessingInstruction pi) {
			String content = pi.getValueOffset() >= 0 ? pi.getValueContent().toString() : null;
			Node node = theDocument.createProcessingInstruction(pi.getTargetName(), content);
			node.setUserData(NAME_POSITION_KEY, pi.getTargetPosition(), null);
			node.setUserData(CONTENT_POSITION_KEY, pi.getValueContent(), null);
			if (theStack.isEmpty())
				theDocument.appendChild(node);
			else
				theStack.getLast().appendChild(node);
		}

		@Override
		public void handleComment(XmlComment comment) {
			Node node = theDocument.createComment(comment.getValueContent().toString());
			node.setUserData(CONTENT_POSITION_KEY, comment.getValueContent(), null);
			if (theStack.isEmpty())
				theDocument.appendChild(node);
			else
				theStack.getLast().appendChild(node);
		}

		@Override
		public void handleElementStart(XmlElementTerminal element) {
			Element node = theDocument.createElement(element.getName());
			node.setUserData(NAME_POSITION_KEY, element.getNamePosition(), null);
			if (theStack.isEmpty())
				theDocument.appendChild(node);
			else
				theStack.getLast().appendChild(node);
			theStack.add(node);
		}

		@Override
		public void handleAttribute(XmlAttribute attribute) {
			Attr node = theDocument.createAttribute(attribute.getName());
			node.setValue(attribute.getValueContent().toString());
			node.setUserData(NAME_POSITION_KEY, attribute.getNamePosition(), null);
			node.setUserData(CONTENT_POSITION_KEY, attribute.getValueContent(), null);
			theStack.getLast().setAttributeNode(node);
		}

		@Override
		public void handleElementContent(String elementName, PositionedContent elementValue) {
			Node node = theDocument.createTextNode(elementValue.toString());
			node.setUserData(CONTENT_POSITION_KEY, elementValue, null);
			theStack.getLast().appendChild(node);
		}

		@Override
		public void handleCDataContent(String elementName, XmlCdata content) {
			Node node = theDocument.createCDATASection(content.getValueContent().toString());
			node.setUserData(CONTENT_POSITION_KEY, content.getValueContent(), null);
			theStack.getLast().appendChild(node);
		}

		@Override
		public void handleElementEnd(XmlElementTerminal element, boolean selfClosing) {
			theStack.removeLast();
		}
	}

	private int theTabLength = 4;
	private final Map<String, String> theNamedEntities = new HashMap<>(STANDARD_NAMED_ENTITIES);

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
	 * Adds a named sequence to be recognized by this parser. E.g. this could be used to recognize HTML entities.
	 * 
	 * @param name The name of the entity to be recognized by the parser (the content between "&" and ";")
	 * @param sequence The sequence to be represented by the entity
	 * @return This parser
	 */
	public SimpleXMLParser withNamedEntity(String name, String sequence) {
		theNamedEntities.put(name, sequence);
		return this;
	}

	/**
	 * Adds a set of named sequences to be recognized by this parser. E.g. this could be used to recognize HTML entities.
	 * 
	 * @param entities A map whose keys are the names of the entities to be recognized by the parser (the content between "&" and ";") and
	 *        whose values are the sequences to be represented by each entity
	 * @return This parser
	 */
	public SimpleXMLParser withNamedEntities(Map<String, String> entities) {
		theNamedEntities.putAll(entities);
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
	 * @see #getPositionContent(Node)
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
	 * @see #getPositionContent(Node)
	 */
	public Document parseDocument(Reader in) throws IOException, XmlParseException {
		if (in == null)
			throw new NullPointerException("Reader cannot be null");
		return parseXml(//
			new ParseSession(in), new DomCreatorHandler(DOM_BUILDERS.get().createDocument()))//
				.getDocument();
	}

	/**
	 * @param in The input stream to read
	 * @return A reader that reads the input stream as XML text. This takes into account the encoding specified in the XML declaration, if
	 *         present. The stream is read as UTF-8 if:
	 *         <ul>
	 *         <li>there is no XML declaration</li>
	 *         <li>the XML declaration does not specify an encoding</li>
	 *         <li>the XML declaration is malformatted</li>
	 *         <li>the file is not XML</li>
	 *         </ul>
	 *         Under none of these conditions does this method throw an exception. An exception is ONLY thrown if the underlying stream
	 *         throws it.
	 * 
	 * @throws IOException If the input stream throws it
	 */
	public Reader readXmlFile(InputStream in) throws IOException {
		CircularCharBuffer buffer = new CircularCharBuffer(-1);
		InputStream wrapped = new InputStream() {
			@Override
			public int read() throws IOException {
				return in.read();
			}

			@Override
			public int read(byte[] b) throws IOException {
				return in.read(b);
			}

			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				return in.read(b, off, len);
			}

			@Override
			public long skip(long n) throws IOException {
				return in.skip(n);
			}

			@Override
			public int available() throws IOException {
				return in.available();
			}

			@Override
			public void close() throws IOException {
				in.close();
			}

			@Override
			public synchronized void mark(int readlimit) {
				in.mark(readlimit);
			}

			@Override
			public synchronized void reset() throws IOException {
				in.reset();
			}

			@Override
			public boolean markSupported() {
				return in.markSupported();
			}
		};
		ParseSession session = new ParseSession(wrapped);
		Charset[] charSet = new Charset[1];
		try {
			// First, find the declaration, if it exists, or the start of the root element if not
			char ch = session.skipWS(null);
			if (ch == '<') {
				// Found the '<'. This is either the XML declaration or the start of the root element if the declaration is missing
				ch = session.nextChar();
				if (ch == '?') // XML Declaration
					handleXmlDeclaration(session, new ParseHandler() {
						@Override
						public void handleDeclaration(XmlDeclaration declaration) {
							buffer.append(declaration.getContent()).append(session.currentChar());
							charSet[0] = declaration.getEncoding();
						}
					});
			}
		} catch (XmlParseException e) {
			// Bad XML, but we don't throw exceptions here, we just assume UTF-8
		}
		if (charSet[0] == null)
			charSet[0] = StandardCharsets.UTF_8;
		Reader bufferReader = buffer.asReader();
		Reader streamReader = new InputStreamReader(in, charSet[0]);
		return new Reader() {
			@Override
			public int read(CharBuffer target) throws IOException {
				int read = bufferReader.read(target);
				if (read < 0)
					read = streamReader.read(target);
				return read;
			}

			@Override
			public int read() throws IOException {
				int read = bufferReader.read();
				if (read < 0)
					read = streamReader.read();
				return read;
			}

			@Override
			public int read(char[] cbuf) throws IOException {
				int read = bufferReader.read(cbuf);
				if (read < 0)
					read = streamReader.read(cbuf);
				return read;
			}

			@Override
			public int read(char[] cbuf, int off, int len) throws IOException {
				int read = bufferReader.read(cbuf, off, len);
				if (read < 0)
					read = streamReader.read(cbuf, off, len);
				return read;
			}

			@Override
			public long skip(long n) throws IOException {
				long skipped = bufferReader.skip(n);
				if (skipped == 0)
					skipped = streamReader.skip(n);
				return skipped;
			}

			@Override
			public boolean ready() throws IOException {
				return bufferReader.ready() || streamReader.ready();
			}

			@Override
			public boolean markSupported() {
				try {
					if (bufferReader.ready())
						return false; // Buffered reader doesn't support mark
				} catch (IOException e) {
					throw new IllegalStateException("Buffered reader threw exception", e);
				}
				return streamReader.markSupported();
			}

			@Override
			public void mark(int readAheadLimit) throws IOException {
				try {
					if (bufferReader.ready())
						throw new IOException("Mark not supported"); // Buffered reader doesn't support mark
				} catch (IOException e) {
					throw new IllegalStateException("Buffered reader threw exception", e);
				}
				streamReader.mark(readAheadLimit);
			}

			@Override
			public void reset() throws IOException {
				try {
					if (bufferReader.ready())
						throw new IOException("Mark not supported"); // Buffered reader doesn't support mark
				} catch (IOException e) {
					throw new IllegalStateException("Buffered reader threw exception", e);
				}
				streamReader.reset();
			}

			@Override
			public void close() throws IOException {
				streamReader.close();
			}
		};
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
	public static PositionedContent getPositionContent(Node node) {
		return (PositionedContent) node.getUserData(DomCreatorHandler.CONTENT_POSITION_KEY);
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

	private static <H extends ParseHandler> H parseXml(ParseSession session, H handler) throws IOException, XmlParseException {
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

	private static void _parseXml(ParseSession session, ParseHandler handler) throws IOException, XmlParseException {
		// First, find the declaration, if it exists, or the start of the root element if not
		char ch = session.skipWS(handler);
		if (ch != '<')
			session.throwException(false, "The first non-whitespace character in an XML document must be '<', not '" + ch + "'");
		// Found the '<'. This is either the XML declaration or the start of the root element if the declaration is missing
		ch = session.nextChar();
		if (ch == '?') { // XML Declaration
			handleXmlDeclaration(session, handler);

			// The declaration is handled.
			// Now we need to move to the root element, where the code below expects, just like if there had been no XML declaration.
			ch = session.skipWS(handler);
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

					ch = session.skipWS(handler);
					if (ch != '<')
						session.throwException(false, "'<' expected, not '" + ch + "'");
					ch = session.nextChar();
				} else
					session.throwException(false,
						"'<!' here is expected to be followed by 'DOCTYPE' for a DOCTYPE declaration or '--' for a comment");
			} else { // Processing instruction
				handleProcessingInstruction(session, handler);

				ch = session.skipWS(handler);
				if (ch != '<')
					session.throwException(false, "'<' expected, not '" + ch + "'");
				ch = session.nextChar();
			}
		}

		// Now we should be at the name of the root element
		handleElement(session, handler);

		session.setContentComplete(true);
		session.skipWS(handler);
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
			session.skipWS(handler);
		}
	}

	private static void handleXmlDeclaration(ParseSession session, ParseHandler handler) throws IOException, XmlParseException {
		int decPos = session.getPosition();
		if (decPos != 1)
			session.throwException(false, "XML declaration must be at the first position of the first line of the XML document");
		session.mark();
		if (!session.expect("xml"))
			session.throwException(true, "XML Declaration must start with '<?xml'");
		if (!Character.isWhitespace(session.nextChar()))
			session.throwException(false, "Expected whitespace after beginning of XML declaration");

		String versionStr = null;
		Charset encoding = null;
		Boolean standalone = null;
		int versionNameOffset = -1, versionValueOffset = -1;
		int encodingNameOffset = -1, encodingValueOffset = -1;
		int standaloneNameOffset = -1, standaloneValueOffset = -1;
		char ch = session.skipWS(null); // White space is part of the XML declaration
		session.mark();
		while (ch >= 'a' && ch <= 'z') {
			// New declaration attribute
			int namePos = session.getPosition();
			String attrName = session.getName();
			boolean version, enc;
			switch (attrName) {
			case VERSION:
				if (versionStr != null)
					session.throwException(true, "Duplicate '" + VERSION + "' attribute on XML declaration");
				version = true;
				enc = false;
				break;
			case ENCODING:
				if (encoding != null)
					session.throwException(true, "Duplicate '" + ENCODING + "' attribute on XML declaration");
				enc = true;
				version = false;
				break;
			case STANDALONE:
				if (standalone != null)
					session.throwException(true, "Duplicate '" + STANDALONE + "' attribute on XML declaration");
				version = enc = false;
				break;
			default:
				session.throwException(true,
					"Only '" + VERSION + "', '" + ENCODING + "', or '" + STANDALONE
						+ "' attributes are allowed on the XML declaration, not '" + attrName + "'");
				return;
			}
			session.startAttribute(attrName);
			int valuePos = session.getPosition();
			session.mark();
			String value = session.parseXmlContent(false, ATTRIBUTE_TERMINATION);
			if (version) {
				// validate the version number?
				versionStr = value;
				versionNameOffset = namePos - decPos + 1;
				versionValueOffset = valuePos - decPos + 1;
			} else if (enc) {
				encodingNameOffset = namePos - decPos + 1;
				encodingValueOffset = valuePos - decPos + 1;
				try {
					encoding = Charset.forName(value);
				} catch (IllegalCharsetNameException e) {
					session.throwException(true, "Illegal character set name: " + value);
				} catch (UnsupportedCharsetException e) {
					session.throwException(true, "Unsupported character set: " + value);
				}
			} else {
				standaloneNameOffset = namePos - decPos + 1;
				standaloneValueOffset = valuePos - decPos + 1;
				switch (value) {
				case "yes":
					standalone = Boolean.TRUE;
					break;
				case "no":
					standalone = Boolean.FALSE;
					break;
				default:
					session.throwException(true, STANDALONE + " must be 'yes' or 'no', not '" + value + "'");
					break;
				}
			}

			ch = session.currentChar();
			if (Character.isWhitespace(ch))
				ch = session.skipWS(null); // White space is part of the XML declaration
		}
		session.mark();
		if (ch != '?' || session.nextChar() != '>')
			session.throwException(false, "XML declaration must end with '?>");
		if (versionStr == null)
			session.throwException(false, "XML declaration must include the '" + VERSION + "' attribute");
		session.nextChar(); // Move past the whole declaration so it's all in the sequence
		handler.handleDeclaration(new XmlDeclaration(versionStr, encoding, standalone, session.dumpSequence(), //
			versionNameOffset, versionValueOffset, encodingNameOffset, encodingValueOffset, standaloneNameOffset, standaloneValueOffset));

		session.setEncoding(encoding == null ? StandardCharsets.UTF_8 : encoding);
	}

	private static void handleComment(ParseSession session, ParseHandler handler) throws IOException, XmlParseException {
		if (!session.expect("-"))
			session.throwException(false, "'<!-' here should be followed by another '-' for a comment");
		session.nextChar();
		session.parseXmlContent(true, COMMENT_TERMINATION);
		if (session.currentChar() != '>')
			session.throwException(true, "'--' is not allowed in comments");
		session.nextChar(); // Get the entire comment into the sequence
		handler.handleComment(new XmlComment(session.dumpSequence()));
	}

	private static void handleProcessingInstruction(ParseSession session, ParseHandler handler) throws IOException, XmlParseException {
		session.nextChar();
		session.mark();
		String target = session.getName();
		if (target.equalsIgnoreCase("xml"))
			session.throwException(true, "Processing instruction cannot be 'xml' with any character case");
		if (!Character.isWhitespace(session.currentChar())) {
			session.mark();
			if (session.currentChar() != '?' || session.nextChar() != '>') // No content
				session.throwException(true, "Processing instruction target must be followed by '?>' or whitespace");
			session.nextChar(); // Include the terminal '>'
			handler.handleProcessingInstruction(new XmlProcessingInstruction(target, -1, session.dumpSequence()));
		} else {
			session.skipWS(null); // Initial white space is part of the processing instruction, but not of the value
			int valuePos = session.getPosition();
			session.parseXmlContent(true, PI_TERMINATION);
			handler.handleProcessingInstruction(
				new XmlProcessingInstruction(target, valuePos - session.getSequenceStartPosition(), session.dumpSequence()));
		}
	}

	private static void handleElement(ParseSession session, ParseHandler handler) throws IOException, XmlParseException {
		if (Character.isWhitespace(session.currentChar()))
			session.skipWS(null); // White space is part of the element start
		int startPos = session.getPosition() - session.getSequenceStartPosition();
		FilePosition namePos = session.getFilePosition(false);
		String elementName = session.getName();
		session.openElement(elementName, namePos);
		handler.handleElementStart(new XmlElementTerminal(elementName, startPos, session.dumpSequence()));
		Set<String> attributes = null;
		if (Character.isWhitespace(session.currentChar()))
			session.skipWS(handler); // White space between element start and attribute or terminal is not part of any component
		while (session.currentChar() != '/' && session.currentChar() != '>') {
			// Attribute
			session.mark();
			String attributeName = session.getName();
			if (attributes == null)
				attributes = new HashSet<>();
			if (!attributes.add(attributeName))
				session.throwException(true, "Multiple '" + attributeName + "' attributes specified on this element");
			session.startAttribute(attributeName);
			int attrValuePos = session.getPosition() - session.getSequenceStartPosition();
			session.parseXmlContent(false, ATTRIBUTE_TERMINATION);
			handler.handleAttribute(new XmlAttribute(attributeName, attrValuePos, session.dumpSequence()));
			if (Character.isWhitespace(session.currentChar()))
				session.skipWS(handler);// White space between attributes or the terminal is not part of any component
		}
		if (session.currentChar() == '/') {// Self-closing element
			if (session.nextChar() != '>')
				session.throwException(false, "'>' expected");
			session.nextChar(); // Include the terminal '>'
			handler.handleElementEnd(new XmlElementTerminal(elementName, -1, session.dumpSequence()), true);
			session.closeElement();
			return;
		}

		session.nextChar(); // Include the '>' in the sequence
		handler.handleElementOpen(elementName, session.dumpSequence());
		while (true) {
			session.mark();
			if (session.currentChar() == '<') { // Comment, CDATA, or child element
				if (session.nextChar() == '!') { // Comment or CDATA
					if (session.nextChar() == '[') { // CDATA
						if (!session.expect("CDATA["))
							session.throwException(true, "Bad CDATA initializer");
						session.nextChar(); // Move to the beginning of the CDATA content
						session.parseXmlContent(true, CDATA_TERMINATION);
						handler.handleCDataContent(elementName, new XmlCdata(session.dumpSequence()));
					} else if (session.currentChar() == '-') { // Comment
						handleComment(session, handler);
					} else
						session.throwException(true, "Misplaced '<' or malformed XML construct");
				} else if (session.currentChar() == '?') // Processing instruction
					handleProcessingInstruction(session, handler);
				else if (session.currentChar() == '/') { // Closing element
					session.skipWS(null); // White space is part of the closing tag
					session.mark();
					int closePos = session.getPosition() - session.getSequenceStartPosition();
					String closingElement = session.getName();
					if (!closingElement.equals(elementName))
						session.throwException(true, "Closing element for '" + elementName + "' expected, not '" + closingElement + "'");
					if (Character.isWhitespace(session.currentChar()))
						session.skipWS(null);// White space is part of the closing tag
					if (session.currentChar() != '>')
						session.throwException(false, "'>' expected");
					session.nextChar(); // Include the terminal '>'
					handler.handleElementEnd(new XmlElementTerminal(elementName, closePos, session.dumpSequence()), false);
					session.closeElement();
					return;
				} else // Child element
					handleElement(session, handler);
			} else { // Element content
				session.parseXmlContent(false, ELEMENT_CONTENT_TERMINATION);
				handler.handleElementContent(elementName, session.dumpSequence());
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

	interface Termination {
		int isTerminator(char ch, int prevT);

		boolean isTerminated(int t);

		boolean isLastTerminatorCharIncluded();
	}

	static class CharTermination implements Termination {
		private final char theTerminator;
		private final boolean isLastIncluded;

		CharTermination(char terminator, boolean lastIncluded) {
			theTerminator = terminator;
			isLastIncluded = lastIncluded;
		}

		@Override
		public int isTerminator(char ch, int prevT) {
			return ch == theTerminator ? 1 : 0;
		}

		@Override
		public boolean isTerminated(int t) {
			return true; // Only called after a match
		}

		@Override
		public boolean isLastTerminatorCharIncluded() {
			return isLastIncluded;
		}
	}

	static class StringTermination implements Termination {
		private final String theTerminator;

		StringTermination(String terminator) {
			theTerminator = terminator;
		}

		@Override
		public int isTerminator(char ch, int prevT) {
			return ch == theTerminator.charAt(prevT) ? prevT + 1 : 0;
		}

		@Override
		public boolean isTerminated(int t) {
			return t == theTerminator.length();
		}

		@Override
		public boolean isLastTerminatorCharIncluded() {
			return true;
		}
	}

	static final Termination ATTRIBUTE_TERMINATION = new CharTermination('"', true);
	static final Termination ELEMENT_CONTENT_TERMINATION = new CharTermination('<', false);
	static final Termination COMMENT_TERMINATION = new StringTermination("--");
	static final Termination PI_TERMINATION = new StringTermination(PROCESSING_INSTRUCTION_END);
	static final Termination CDATA_TERMINATION = new StringTermination(CDATA_END);

	class ParseSession {
		private final InputStream theStream;
		private Reader theReader;
		private char theChar;
		private int theNextChar = -1;
		private int thePosition;
		private int theLineNumber;
		private int theCharNumber;
		private boolean isContentComplete;
		private boolean isAtBeginning = true;
		private boolean isAtEnd;
		private boolean wasCRNL;

		private LocatedXmlElement theElement;
		private int theMarkPosition;
		private int theMarkLineNumber;
		private int theMarkCharNumber;

		private final StringBuilder theNameBuffer = new StringBuilder();
		private final StringBuilder theSequenceBuffer = new StringBuilder();
		private final int[] theSequencePosition = new int[3];
		private final List<LineContent> lines = new ArrayList<>();
		private int lineStart;
		private int linePosition;
		private final List<SpecialCharSequence> lineSpecialSequences = new ArrayList<>();

		private void specialSequence(int length, String chars) {
			lineSpecialSequences.add(new SpecialCharSequence(theSequenceBuffer.length(), length, chars));
		}

		private void newLine() {
			SpecialCharSequence[] seqs;
			if (lineSpecialSequences.isEmpty())
				seqs = EMPTY_SPECIAL_SEQUENCE;
			else {
				seqs = lineSpecialSequences.toArray(new SpecialCharSequence[lineSpecialSequences.size()]);
				lineSpecialSequences.clear();
			}
			lines.add(new LineContent(lineStart, linePosition, seqs));
			lineStart = theSequenceBuffer.length();
			linePosition = thePosition;
		}

		ParseSession(InputStream stream) {
			theStream = stream;
		}

		ParseSession(Reader reader) {
			theStream = null;
			theReader = reader;
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
			int ch;
			if (isAtBeginning) {
				isAtBeginning = false;
				ch = getNextStreamChar();
			} else {
				thePosition++;
				switch (theChar) {
				case '\n':
					if (wasCRNL) {
						wasCRNL = false;
						specialSequence(1, CRNL);
						theSequenceBuffer.append('\n');
						ch = getNextStreamChar();
						newLine();
					} else {
						ch = getNextStreamChar();
						if (ch == '\r') {
							thePosition++;
							specialSequence(1, NLCR);
							ch = getNextStreamChar();
						}
						theSequenceBuffer.append('\n');
						newLine();
					}
					theLineNumber++;
					theCharNumber = 0;
					break;
				case '\t':
					theCharNumber += theTabLength;
					specialSequence(theTabLength, TAB);
					theSequenceBuffer.append('\t');
					ch = getNextStreamChar();
					break;
				default:
					theSequenceBuffer.append(theChar);
					theCharNumber++;
					ch = getNextStreamChar();
				}
			}
			if (ch < 0) {
				if (!isContentComplete)
					throwException(false, "Unexpected end of XML content");
				isAtEnd = true;
				return (char) 0;
			} else if (ch == '\r') {
				ch = getNextStreamChar();
				if (ch == '\n')
					wasCRNL = true;
				else {
					ch = '\r';
					theNextChar = ch;
				}
			}
			theChar = (char) ch;
			return theChar;
		}

		private int getNextStreamChar() throws IOException {
			int ch;
			if (theNextChar >= 0) {
				ch = theNextChar;
				theNextChar = -1;
			} else if (theReader != null)
				ch = theReader.read();
			else
				ch = theStream.read();
			return ch;
		}

		PositionedContent dumpSequence() throws IOException, XmlParseException {
			if (theSequenceBuffer.length() > 0)
				newLine();
			LineContent[] dumped = lines.toArray(new LineContent[lines.size()]);
			lines.clear();
			String content = theSequenceBuffer.toString();
			theSequenceBuffer.setLength(0);
			lineStart = 0;
			FilePosition seqPos = new FilePosition(theSequencePosition[0], theSequencePosition[1], theSequencePosition[2]);
			theSequencePosition[0] = thePosition;
			theSequencePosition[1] = theLineNumber;
			theSequencePosition[2] = theCharNumber;
			return new PositionedContentImpl(content, seqPos, dumped);
		}

		char skipWS(ParseHandler handler) throws IOException, XmlParseException {
			while (Character.isWhitespace(nextChar())) { //
			}
			if (theSequenceBuffer.length() > 0 && handler != null)
				handler.handleIgnorableWhitespace(dumpSequence());
			return theChar;
		}

		String parseXmlContent(boolean permissive, Termination terminator) throws IOException, XmlParseException {
			int preLen = theSequenceBuffer.length();
			char ch = currentChar();
			int t = 0;
			int preTerm = -1;
			for (; true; ch = nextChar()) {
				int preT = t;
				t = terminator.isTerminator(ch, preT);
				if (t > 0) {
					if (preT == 0)
						preTerm = theSequenceBuffer.length();
					if (terminator.isTerminated(t))
						break;
					continue;
				}
				if (!permissive) {
					if (ch == '<')
						throwException(false, "'<' is not a valid character in an attribute value");
					else if (ch == '&')
						parseEscapeSequence();
				}
			}
			if (terminator.isLastTerminatorCharIncluded())
				nextChar(); // Move past the terminator
			return theSequenceBuffer.subSequence(preLen, preTerm).toString();
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
			return thePosition; // This method returns the position of the current character
		}

		int getSequenceStartPosition() {
			return theSequencePosition[0];
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
			theNameBuffer.setLength(0);
			theNameBuffer.append(theChar);
			while (isNameChar(nextChar()))
				theNameBuffer.append(theChar);
			String name = theNameBuffer.toString();
			theNameBuffer.setLength(0);
			return name;
		}

		/** Moves past the '="' sequence between an attribute's name and its value */
		void startAttribute(String attributeName) throws IOException, XmlParseException {
			// White space here is part of the attribute--don't report it as ignorable white space
			if (Character.isWhitespace(theChar))
				skipWS(null);
			if (theChar != '=')
				throwException(false, "'=' expected");
			if (skipWS(null) != '"')
				throwException(false, "'\"' expected");
			nextChar(); // Position ourselves at the beginning of the attribute value
		}

		private final StringBuilder theEntityBuffer = new StringBuilder();

		private void parseEscapeSequence() throws IOException, XmlParseException {
			mark();
			int ch = getNextStreamChar();
			String content;
			if (theChar == '&') {
				if (ch == '#') {
					int code = 0;
					ch = getNextStreamChar();
					if (ch == 'x') { // Hex entity
						mark();
						theEntityBuffer.append(HEX_ENTITY_PREFIX);
						ch = getNextStreamChar();
						int count = 0;
						int hex = hex(ch);
						while (hex >= 0) {
							count++;
							theEntityBuffer.append((char) ch);
							code = code * 16 + hex;
							if (code > Character.MAX_CODE_POINT)
								throwException(true, "Hex entity is too large--no such character");
							ch = getNextStreamChar();
							hex = hex(ch);
						}
						if (count == 0)
							throwException(true, "One or more hexadecimal characters expected");
					} else { // Decimal entity
						mark();
						theEntityBuffer.append(DECIMAL_ENTITY_PREFIX);
						int count = 0;
						while (ch >= '0' && ch <= '9') {
							count++;
							theEntityBuffer.append((char) ch);
							code = code * 10 + ch - '0';
							if (code > Character.MAX_CODE_POINT)
								throwException(true, "Decimal entity is too large--no such character");
							ch = getNextStreamChar();
						}
						if (count == 0)
							throwException(true, "One or more decimal characters expected");
					}
					content = String.valueOf((char) code);
				} else {
					mark();
					theEntityBuffer.append(NAMED_ENTITY_PREFIX);
					int count = 0;
					while (isNameChar(ch)) {
						count++;
						theEntityBuffer.append((char) ch);
						ch = getNextStreamChar();
					}
					if (count == 0)
						throwException(true, "Entity name expected");
					String entityName = theEntityBuffer.substring(NAMED_ENTITY_PREFIX.length());
					content = theNamedEntities.get(entityName);
					if (content == null) {
						throwException(true, "Unrecognized named entity: '" + entityName + "'");
						content = "!"; // Won't get here
					}
				}
			} else if (theChar == '%') {
				throwException(true, "Parsed entities are not supported");
				content = "!"; // Won't get here
			} else {
				throwException(true, "Unrecognized escape sequence");
				content = "!"; // Won't get here
			}
			if (ch != ';') {
				thePosition += theEntityBuffer.length();
				theCharNumber += theEntityBuffer.length();
				throwException(false, "';' expected");
			}
			theEntityBuffer.append(';');
			String seq = theEntityBuffer.toString();
			theEntityBuffer.setLength(0);
			specialSequence(seq.length(), seq);
			if (content.length() > 1)
				theSequenceBuffer.append(content, 0, content.length() - 1);
			theChar = content.charAt(content.length() - 1);
			thePosition += seq.length() - 1;
			theCharNumber += seq.length() - 1;
		}

		private int hex(int ch) {
			if (ch >= '0' && ch <= '9')
				return ch - '0';
			else if (ch >= 'a' && ch <= 'z')
				return ch - 'a' + 10;
			else if (ch >= 'A' && ch <= 'Z')
				return ch - 'A' + 10;
			else
				return -1;
		}

		@Override
		public String toString() {
			return "L" + theLineNumber + "C" + theCharNumber + "'" + theChar + "'";
		}
	}

	static final SpecialCharSequence[] EMPTY_SPECIAL_SEQUENCE = new SpecialCharSequence[0];

	static class PositionedContentImpl implements PositionedContent {
		private final String theContent;
		private final FilePosition theStart;
		private final LineContent[] theLines;

		PositionedContentImpl(String content, FilePosition start, LineContent[] lines) {
			theContent = content;
			theStart = start;
			theLines = lines;
		}

		@Override
		public int length() {
			return theContent.length();
		}

		@Override
		public char charAt(int index) {
			return theContent.charAt(index);
		}

		int getLine(int index) {
			int lineIndex = ArrayUtils.binarySearch(theLines, line -> Integer.compare(index, line.contentIndex));
			if (lineIndex < 0)
				lineIndex = -lineIndex - 2;
			return lineIndex;
		}

		@Override
		public FilePosition getPosition(int index) {
			if (index < 0 || index > theContent.length())
				throw new IndexOutOfBoundsException(index + " of " + theContent.length());
			else if (index == 0)
				return theStart;
			int line = getLine(index);
			int charNumber = line == 0 ? theStart.getCharNumber() : 0;
			int lineNumber = line + theStart.getLineNumber();
			return theLines[line].getPosition(index, lineNumber, charNumber);
		}

		@Override
		public int getSourceLength(int from, int to) {
			if (from < 0 || from > to || to > theContent.length())
				throw new IndexOutOfBoundsException(from + " to " + to + " of " + theContent.length());
			else if (from == to)
				return 0;
			int line = getLine(from);
			if (to == from + 1) // Single character
				return theLines[line].getSourceCharacter(from, theContent).length();
			int total = 0;
			while (line < theLines.length && to > theLines[line].contentIndex) {
				int lineEnd = line + 1 < theLines.length ? theLines[line + 1].contentIndex : to;
				total += theLines[line].getSourceLength(from, Math.min(lineEnd, to));
				line++;
			}
			return total;
		}

		@Override
		public CharSequence getSourceContent(int from, int to) {
			if (from < 0 || from > to || to > theContent.length())
				throw new IndexOutOfBoundsException(from + " to " + to + " of " + theContent.length());
			else if (from == to)
				return "";
			int line = getLine(from);
			if (to == from + 1) // Single character
				return theLines[line].getSourceCharacter(from, theContent);
			StringBuilder str = new StringBuilder();
			while (line < theLines.length && to > theLines[line].contentIndex) {
				int lineEnd = line + 1 < theLines.length ? theLines[line + 1].contentIndex : to;
				theLines[line].getSourceContent(from, Math.min(lineEnd, to), str, theContent);
				line++;
			}
			return str.toString();
		}

		@Override
		public String toString() {
			return theContent;
		}
	}

	static class LineContent {
		final int contentIndex;
		final int position;
		final SpecialCharSequence[] specialSequences;

		LineContent(int contentIndex, int position, SpecialCharSequence[] specialSequences) {
			this.contentIndex = contentIndex;
			this.position = position;
			this.specialSequences = specialSequences;
		}

		int getSeqIndex(int index) {
			return ArrayUtils.binarySearch(specialSequences, seq -> Integer.compare(index, seq.contentIndex));
		}

		FilePosition getPosition(int index, int lineNumber, int charNumber) {
			int seqIndex = getSeqIndex(index);
			if (seqIndex >= 0)
				seqIndex--;
			else
				seqIndex = -seqIndex - 2;
			int charIndex = index - contentIndex;
			int pos = position + charIndex, col = charIndex;
			for (int s = 0; s <= seqIndex; s++) {
				pos += specialSequences[s].sequence.length() - 1;
				col += specialSequences[s].contentLength - 1;
			}
			return new FilePosition(pos, lineNumber, charNumber + col);
		}

		CharSequence getSourceCharacter(int index, String content) {
			int seqIndex = getSeqIndex(index);
			if (seqIndex >= 0)
				return specialSequences[seqIndex].sequence;
			else
				return new SingleCharSequence(content.charAt(index));
		}

		int getSourceLength(int from, int to) {
			if (specialSequences.length == 0)
				return to - from;
			int seqIdx;
			if (from <= contentIndex)
				seqIdx = 0;
			else {
				seqIdx = getSeqIndex(from);
				if (seqIdx < 0)
					seqIdx = -seqIdx;
			}
			int len = 0, seqCount = 0;
			for (int s = seqIdx; s < specialSequences.length && specialSequences[s].contentIndex < to; s++) {
				len += specialSequences[s].contentLength;
				seqCount++;
			}
			len += to - from - seqCount;
			return len;
		}

		CharSequence getSourceContent(int from, int to, StringBuilder str, String content) {
			if (specialSequences.length == 0)
				return content.subSequence(from, to);
			int seqIdx;
			if (from <= contentIndex)
				seqIdx = 0;
			else {
				seqIdx = getSeqIndex(from);
				if (seqIdx < 0)
					seqIdx = -seqIdx;
			}
			for (int c = from; c < to; c++) {
				if (seqIdx < specialSequences.length && c == specialSequences[seqIdx].contentIndex) {
					str.append(specialSequences[seqIdx].sequence);
					seqIdx++;
				} else
					str.append(content.charAt(c));
			}
			return str;
		}
	}

	static class SpecialCharSequence {
		final int contentIndex;
		final int contentLength;
		final String sequence;

		SpecialCharSequence(int contentIndex, int contentLength, String sequence) {
			this.contentIndex = contentIndex;
			this.contentLength = contentLength;
			this.sequence = sequence;
		}
	}

	static class SingleCharSequence implements CharSequence {
		private final char theChar;

		SingleCharSequence(char c) {
			theChar = c;
		}

		@Override
		public int length() {
			return 1;
		}

		@Override
		public char charAt(int index) {
			if (index != 0)
				throw new IndexOutOfBoundsException(index + " of 1");
			return theChar;
		}

		@Override
		public CharSequence subSequence(int start, int end) {
			if (start == 0) {
				if (end == 0)
					return "";
				else if (end == 1)
					return this;
			} else if (start == 1 && end == 1)
				return "";
			throw new IndexOutOfBoundsException(start + " to " + end + " of 1");
		}

		@Override
		public int hashCode() {
			return theChar;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof CharSequence && ((CharSequence) obj).length() == 1 && ((CharSequence) obj).charAt(0) == theChar;
		}

		@Override
		public String toString() {
			return String.valueOf(theChar);
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

				private String printStart(PositionedContent position) {
					return position.getPosition(0).toString();
				}

				private String printContent(String content) {
					return content.replace("\n", "\\\\n").replace("\t", "\\\\t");
				}

				@Override
				public void handleDeclaration(XmlDeclaration declaration) {
					System.out.println("Declaration " + declaration);
				}

				@Override
				public void handleProcessingInstruction(XmlProcessingInstruction pi) {
					indent();
					System.out.println(
						"Processing instruction @" + pi.getTargetPosition() + ": " + pi.getTargetName() + "=" + pi.getValueContent());
				}

				@Override
				public void handleComment(XmlComment comment) {
					indent();
					System.out.println(
						"Comment @" + printStart(comment.getValueContent()) + ": " + printContent(comment.getValueContent().toString()));
				}

				@Override
				public void handleElementStart(XmlElementTerminal element) {
					indent();
					System.out.println("Element @" + element.getNamePosition() + ": " + element.getName());
					indent++;
				}

				@Override
				public void handleAttribute(XmlAttribute attribute) {
					indent();
					System.out.println("Attribute @" + attribute.getNamePosition() + ": " + attribute.getName() + "="
						+ printContent(attribute.getValueContent().toString()) + " @" + printStart(attribute.getValueContent()));
				}

				@Override
				public void handleElementContent(String elementName, PositionedContent elementValue) {
					indent();
					System.out.println("Content @" + printStart(elementValue) + ": " + printContent(elementValue.toString()));
				}

				@Override
				public void handleCDataContent(String elementName, XmlCdata cdata) {
					indent();
					System.out
						.println("CDATA @" + printStart(cdata.getValueContent()) + ": " + printContent(cdata.getValueContent().toString()));
				}

				@Override
				public void handleElementEnd(XmlElementTerminal element, boolean selfClosing) {
					indent--;
					indent();
					System.out.println("Close @" + element.getContent().getPosition(0) + ": " + element.getName());
				}
			});
		}
	}
}

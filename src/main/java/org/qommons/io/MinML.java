package org.qommons.io;

import java.io.*;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.qommons.ArrayUtils;
import org.qommons.Named;
import org.qommons.QommonsUtils;
import org.qommons.ex.ExFunction;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * <p>
 * A minimalist XML parser.
 * </p>
 * <p>
 * I have need of an XML parser with more rigorous position handling than Java's native handling or any 3rd party parsers I can find are
 * capable of.
 * </p>
 * <p>
 * This class facilitates full tracking of every element, attribute, and value to an exact position with the XML file.
 * </p>
 * <p>
 * <b><font color="red">This class is NOT a full-featured XML parser.</font></b> Several features of XML are not supported or are not
 * handled traditionally:
 * <ul>
 * <li>Namespaces are not handled specially, but rather are treated as part of the element/attribute name. Referenced XML schemas are not
 * loaded.</li>
 * <li>This class completely lacks support for the DOCTYPE declaration. The parser will throw exceptions when these are encountered.</li>
 * </ul>
 * <p>
 * For uses that do not require schema validation in the parser, this class is a nice alternative. As a bonus, it is not subject to many
 * vulnerabilities that typical XML parsers are susceptible to due to their ability to pull in files as directed by the XML data.
 * </p>
 * {@link MinML} supports 3 methods of parsing:
 * <ul>
 * <li>Traditional SAX-style parsing is supported via {@link #parseXml(String, InputStream, ParseHandler)} (or
 * {@link #parseXml(String, Reader, ParseHandler)} if the source is non-binary). The parse handler is notified of each XML component as it
 * is encountered, with almost zero read-ahead.</li>
 * <li>DOM parsing is supported via {@link #parseDocument(String, InputStream)} (or {@link #parseDocument(String, Reader)}), which returns a
 * standard W3C XML {@link Document}. Position information is stored in the {@link Node#getUserData(String) user data} of the XML components
 * and can be accessed via {@link #getNamePosition(Node)} and {@link #getPositionContent(Node)}.</li>
 * <li>The {@link #parseByComponent(String, InputStream)} (or {@link #parseByComponent(String, Reader)}) method returns a
 * {@link ComponentParser}, which provides methods for quickly navigating an XML document. See the documentation on that class.</li>
 * </ul>
 */
public class MinML {
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
		private final int theDepth;
		private final String theName;
		private final FilePosition thePosition;

		/**
		 * @param parent The parent element
		 * @param name The name of the element
		 * @param position The position of the name of the element in its opening tag in the file
		 */
		public LocatedXmlElement(LocatedXmlElement parent, String name, FilePosition position) {
			theParent = parent;
			theDepth = parent == null ? 0 : parent.theDepth + 1;
			theName = name;
			thePosition = position;
		}

		/** @return This element's parent */
		public LocatedXmlElement getParent() {
			return theParent;
		}

		/** @return The number of element ancestors this element has */
		public int getDepth() {
			return theDepth;
		}

		@Override
		public String getName() {
			return theName;
		}

		/** @return The position of the name of the element in its opening tag in the file */
		public FilePosition getPosition() {
			return thePosition;
		}

		@Override
		public String toString() {
			return "<" + theName + ">@" + thePosition;
		}
	}

	/** Thrown from {@link MinML}'s parse methods */
	public static class XmlParseException extends TextParseException {
		private final LocatedXmlElement theElement;

		/**
		 * @param element The element under which the parse error occurred (may be null if the root element had not yet been encountered)
		 * @param fileLocation The file location being parsed, if given
		 * @param message The message indicating the nature of the XML malformation
		 * @param errorOffset The absolute position offset of the character in the file where the error was detected
		 * @param lineNumber The line number in the file where the error was detected (offset from zero)
		 * @param columnNumber The character number (in its line) in the file where the error was detected (offset from zero)
		 */
		public XmlParseException(LocatedXmlElement element, String fileLocation, String message, int errorOffset, int lineNumber,
			int columnNumber) {
			super(message, fileLocation == null ? new FilePosition(errorOffset, lineNumber, columnNumber)
				: new LocatedFilePosition(fileLocation, errorOffset, lineNumber, columnNumber));
			theElement = element;
		}

		/** @return The element under which the parse error occurred. May be null if the root element had not yet been encountered */
		public LocatedXmlElement getElement() {
			return theElement;
		}
	}

	/** A type of XML component */
	public enum XmlComponentType {
		/** The XML declaration at the head of the document, e.g. '&lt;?xml version="1.0" encoding="UTF-8"?>' */
		Declaration,
		/** A processing instruction, e.g. '&lt;?NAME Content?>' */
		ProcessingInstruction,
		/** An XML comment, e.g. '&lt;!-- Content -->' */
		Comment,
		/** Either the beginning (e.g. '&lt;name') or close (e.g. '&lt;/name>' or '/>' */
		ElementTerminal,
		/** Signifies the beginning of an element's content ('>') */
		ElementOpen,
		/** An attribute of an XML element (e.g. 'name="value"') */
		Attribute,
		/** Simple text content within an element */
		ElementContent,
		/** CDATA content within an element (e.g. '&lt;![CDATA[ Content ]]>') */
		CData,
		/** Content in an XML document that is not part of the syntax or content. Typically spaces, tabs, and new line characters. */
		IgnorableWhitespace;
	}

	/** Super interface for any XML structure passed to a {@link ParseHandler handler} */
	public interface XmlComponent {
		/** @return The type of this component */
		XmlComponentType getComponentType();

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

		@Override
		public XmlComponentType getComponentType() {
			return XmlComponentType.Declaration;
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
		 *         <li>{@link MinML#VERSION version}</li>
		 *         <li>{@link MinML#ENCODING encoding}</li>
		 *         <li>or {@link MinML#STANDALONE standalone}</li>
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
		 *        <li>{@link MinML#VERSION version}</li>
		 *        <li>{@link MinML#ENCODING encoding}</li>
		 *        <li>or {@link MinML#STANDALONE standalone}</li>
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
		 *        <li>{@link MinML#VERSION version}</li>
		 *        <li>{@link MinML#ENCODING encoding}</li>
		 *        <li>or {@link MinML#STANDALONE standalone}</li>
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
		 *        <li>{@link MinML#VERSION version}</li>
		 *        <li>{@link MinML#ENCODING encoding}</li>
		 *        <li>or {@link MinML#STANDALONE standalone}</li>
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
		 *        <li>{@link MinML#VERSION version}</li>
		 *        <li>{@link MinML#ENCODING encoding}</li>
		 *        <li>or {@link MinML#STANDALONE standalone}</li>
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

		/** @return The offset of the name of the {@link MinML#VERSION version} attribute, or -1 if it was not specified */
		public int getVersionNameOffset() {
			return theVersionNameOffset;
		}

		/** @return The offset of the value of the {@link MinML#VERSION version} attribute, or -1 if it was not specified */
		public int getVersionValueOffset() {
			return theVersionValueOffset;
		}

		/** @return The offset of the name of the {@link MinML#ENCODING encoding} attribute, or -1 if it was not specified */
		public int getEncodingNameOffset() {
			return theEncodingNameOffset;
		}

		/** @return The offset of the value of the {@link MinML#ENCODING encoding} attribute, or -1 if it was not specified */
		public int getEncodingValueOffset() {
			return theEncodingValueOffset;
		}

		/** @return The offset of the name of the {@link MinML#STANDALONE standalone} attribute, or -1 if it was not specified */
		public int getStandaloneNameOffset() {
			return theStandaloneNameOffset;
		}

		/** @return The offset of the value of the {@link MinML#STANDALONE standalone} attribute, or -1 if it was not specified */
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
	 * An XML processing instruction parsed by a {@link MinML}.
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

		@Override
		public XmlComponentType getComponentType() {
			return XmlComponentType.ProcessingInstruction;
		}

		/** @return The name of the processing instruction's target */
		public String getTargetName() {
			return theTargetName;
		}

		/** @return The offset of the processing instruction's content, or -1 if there was no content */
		public int getValueOffset() {
			return theValueOffset;
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

	/** An XML comment parsed by a {@link MinML} */
	public static class XmlComment implements XmlComponent {
		private final PositionedContent theContent;

		/** @param content The text content defining the comment, including <code>&lt;!--</code> and <code>--></code> */
		public XmlComment(PositionedContent content) {
			theContent = content;
		}

		@Override
		public XmlComponentType getComponentType() {
			return XmlComponentType.Comment;
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

	/** Represents an open or close tag of an XML element being parsed by a {@link MinML} */
	public static class XmlElementTerminal implements XmlComponent, Named {
		private final String theName;
		private final boolean isOpen;
		private final int theDepth;
		private final int theNameOffset;
		private final PositionedContent theContent;
		private final boolean isSelfClosing;

		/**
		 * @param elementName The name of the element
		 * @param open Whether this is an open or close tag
		 * @param depth The depth of this element below the root (root=0)
		 * @param nameOffset The offset of the name of the element in the open or close tag
		 * @param content The content defining the element's open (everything between and including the initial <code>&lt;</code> and the
		 *        element's name) or close tag (everything between and including <code>&lt;/</code> and <code>></code>
		 * @param selfClosing
		 */
		public XmlElementTerminal(String elementName, boolean open, int depth, int nameOffset, PositionedContent content,
			boolean selfClosing) {
			theName = elementName;
			isOpen = open;
			theDepth = depth;
			theNameOffset = nameOffset;
			theContent = content;
			isSelfClosing = selfClosing;
		}

		@Override
		public XmlComponentType getComponentType() {
			return XmlComponentType.ElementTerminal;
		}

		@Override
		public String getName() {
			return theName;
		}

		/** @return Whether this is an open or close tag */
		public boolean isOpen() {
			return isOpen;
		}

		/** @return The depth of this element below the root (root=0) */
		public int getDepth() {
			return theDepth;
		}

		/** @return The offset of the name of the element in the open or close tag */
		public int getNameOffset() {
			return theNameOffset;
		}

		/** @return The position of the beginning of the element's name */
		public PositionedContent getNamePosition() {
			if (theNameOffset < 0)
				return null; // Self-closing tag
			return theContent.subSequence(theNameOffset, theNameOffset + theName.length());
		}

		@Override
		public PositionedContent getContent() {
			return theContent;
		}

		/** @return The text content containing the element's name */
		public PositionedContent getNameContent() {
			return theContent.subSequence(theNameOffset);
		}

		/** @return If this is a close tag (see {@link #isOpen()}), then whether this represents the self-close of an element ('/>') */
		public boolean isSelfClosing() {
			return isSelfClosing;
		}

		@Override
		public String toString() {
			return theContent.toString();
		}
	}

	/** An XML component that has no variability other than its content */
	public static abstract class ContentOnlyXmlComponent implements XmlComponent, PositionedContent {
		private final PositionedContent theContent;

		/** @param content The content for this component */
		protected ContentOnlyXmlComponent(PositionedContent content) {
			theContent = content;
		}

		@Override
		public PositionedContent getContent() {
			return theContent;
		}

		@Override
		public int length() {
			return theContent.length();
		}

		@Override
		public char charAt(int index) {
			return theContent.charAt(index);
		}

		@Override
		public FilePosition getPosition(int index) {
			return theContent.getPosition(index);
		}

		@Override
		public int getSourceLength(int from, int to) {
			return theContent.getSourceLength(from, to);
		}

		@Override
		public CharSequence getSourceContent(int from, int to) {
			return theContent.getSourceContent(from, to);
		}

		@Override
		public int hashCode() {
			return theContent.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theContent.equals(obj);
		}

		@Override
		public String toString() {
			return theContent.toString();
		}
	}

	/** Member components of an XML element */
	public static abstract class XmlElementContentComponent extends ContentOnlyXmlComponent {
		private final String theElementName;

		/**
		 * @param elementName The name of the owner element
		 * @param content The content of this component
		 */
		protected XmlElementContentComponent(String elementName, PositionedContent content) {
			super(content);
			theElementName = elementName;
		}

		/** @return The name of the element owning this component */
		public String getElementName() {
			return theElementName;
		}
	}

	/** The beginning of an XML element's content ('>') */
	public static class XmlElementOpen extends XmlElementContentComponent {
		/**
		 * @param elementName The name of the element
		 * @param content The content of the opening characters
		 */
		public XmlElementOpen(String elementName, PositionedContent content) {
			super(elementName, content);
		}

		@Override
		public XmlComponentType getComponentType() {
			return XmlComponentType.ElementOpen;
		}
	}

	/** An XML attribute parsed by a {@link MinML} */
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
		public XmlComponentType getComponentType() {
			return XmlComponentType.Attribute;
		}

		@Override
		public String getName() {
			return theName;
		}

		/** @return The position of the start of the attribute's name */
		public PositionedContent getNamePosition() {
			return theContent.subSequence(0, theName.length());
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

	/** Text content between an XML element's opening and closing tags */
	public static class XmlElementContent extends XmlElementContentComponent {
		/**
		 * @param elementName The name of the XML element owning this content
		 * @param content The text in the XML content
		 */
		public XmlElementContent(String elementName, PositionedContent content) {
			super(elementName, content);
		}

		@Override
		public XmlComponentType getComponentType() {
			return XmlComponentType.ElementContent;
		}
	}

	/** An XML CDATA structure parsed by a {@link MinML} */
	public static class XmlCdata extends XmlElementContentComponent {
		/**
		 * @param elementName The name of the XML element containing this CDATA content
		 * @param content The content defining the CDATA, everything between and including the terminal {@link MinML#CDATA_START
		 *        &lt;![CDATA[} and {@link MinML#CDATA_END ]]>}
		 */
		public XmlCdata(String elementName, PositionedContent content) {
			super(elementName, content);
		}

		@Override
		public XmlComponentType getComponentType() {
			return XmlComponentType.CData;
		}

		/** @return The text content containing the character data in the CDATA structure */
		public PositionedContent getValueContent() {
			return getContent().subSequence(CDATA_START.length(), getContent().length() - CDATA_END.length());
		}
	}

	/** Ignorable white space encountered by the parser */
	public static class XmlIgnorableWhitespace extends ContentOnlyXmlComponent {
		/** @param content The content of the whitespace */
		public XmlIgnorableWhitespace(PositionedContent content) {
			super(content);
		}

		@Override
		public XmlComponentType getComponentType() {
			return XmlComponentType.IgnorableWhitespace;
		}
	}

	/** A handler to be notified for each item of content in an XML document */
	public interface ParseHandler {
		/** @param component The encountered component */
		default void handleXmlComponent(XmlComponent component) {
			switch (component.getComponentType()) {
			case Declaration:
				handleDeclaration((XmlDeclaration) component);
				break;
			case ProcessingInstruction:
				handleProcessingInstruction((XmlProcessingInstruction) component);
				break;
			case Comment:
				handleComment((XmlComment) component);
				break;
			case ElementTerminal:
				XmlElementTerminal terminal = (XmlElementTerminal) component;
				if (terminal.isOpen())
					handleElementStart(terminal);
				else
					handleElementEnd(terminal, terminal.isSelfClosing());
				break;
			case Attribute:
				handleAttribute((XmlAttribute) component);
				break;
			case ElementOpen:
				XmlElementOpen open = (XmlElementOpen) component;
				handleElementOpen(open.getElementName(), open);
				break;
			case ElementContent:
				XmlElementContent content = (XmlElementContent) component;
				handleElementContent(content.getElementName(), content);
				break;
			case CData:
				XmlCdata cdata = (XmlCdata) component;
				handleCDataContent(cdata.getElementName(), cdata);
				break;
			case IgnorableWhitespace:
				handleIgnorableWhitespace((XmlIgnorableWhitespace) component);
				break;
			}
		}

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
		default void handleElementOpen(String elementName, XmlElementOpen openEnd) {
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
		default void handleElementContent(String elementName, XmlElementContent elementValue) {
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
		default void handleIgnorableWhitespace(XmlIgnorableWhitespace whitespace) {
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
			node.setUserData(NAME_POSITION_KEY, pi.getTargetContent(), null);
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
		public void handleElementContent(String elementName, XmlElementContent elementValue) {
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

	/** A parse handler that writes the XML content to another stream, re-formatting it with indents and new lines */
	public static class ReformatPrinter implements ParseHandler {
		private final Writer theWriter;
		private final String theIndent;
		private final int theMaxLineLength;
		private final int theIndentLength;
		private int theIndentLevel;
		private int theLineLength;
		private boolean isElementMultiLine;

		/**
		 * @param writer The stream to write the XML to
		 * @param indent The indent string to reformat with
		 * @param maxLineLength The max line length to use to wrap elements onto new lines
		 */
		public ReformatPrinter(Writer writer, String indent, int maxLineLength) {
			theWriter = writer;
			theIndent = indent;
			theMaxLineLength = maxLineLength;
			int indentLength = 0;
			for (int c = 0; c < indent.length(); c++) {
				if (indent.charAt(c) == '\t')
					indentLength += 4;
				else
					indentLength++;
			}
			theIndentLength = indentLength;
		}

		private int indent() throws IOException {
			isElementMultiLine = true;
			theWriter.append('\n');
			int length = 0;
			for (int i = 0; i < theIndentLevel; i++) {
				theWriter.append(theIndent);
				length += theIndentLength;
			}
			return length;
		}

		@Override
		public void handleDeclaration(XmlDeclaration declaration) {
			try {
				theWriter.append(declaration.getContent());
			} catch (IOException e) {
				throw new IllegalStateException("Could not write XML data", e);
			}
		}

		@Override
		public void handleProcessingInstruction(XmlProcessingInstruction pi) {
			try {
				indent();
				theWriter.append(pi.getContent());
			} catch (IOException e) {
				throw new IllegalStateException("Could not write XML data", e);
			}
		}

		@Override
		public void handleComment(XmlComment comment) {
			try {
				indent();
				theWriter.append(comment.getContent());
			} catch (IOException e) {
				throw new IllegalStateException("Could not write XML data", e);
			}
		}

		@Override
		public void handleElementStart(XmlElementTerminal element) {
			try {
				theLineLength = indent() + element.getContent().length();
				theWriter.append(element.getContent());
			} catch (IOException e) {
				throw new IllegalStateException("Could not write XML data", e);
			}
		}

		@Override
		public void handleElementOpen(String elementName, XmlElementOpen openEnd) {
			try {
				theWriter.append(openEnd);
			} catch (IOException e) {
				throw new IllegalStateException("Could not write XML data", e);
			}
			theIndentLevel++;
			isElementMultiLine = false;
		}

		@Override
		public void handleAttribute(XmlAttribute attribute) {
			try {
				if (theLineLength + attribute.getContent().length() > theMaxLineLength) {
					theLineLength = indent();
				} else {
					theWriter.append(' ');
					theLineLength++;
				}
				theLineLength += attribute.getContent().length();
				theWriter.append(attribute.getContent());
			} catch (IOException e) {
				throw new IllegalStateException("Could not write XML data", e);
			}
		}

		@Override
		public void handleElementContent(String elementName, XmlElementContent elementValue) {
			try {
				if (theLineLength + elementValue.length() > theMaxLineLength) {
					theLineLength = indent();
				} else {
					theLineLength++;
				}
				theLineLength += elementValue.length();
				theWriter.append(elementValue);
			} catch (IOException e) {
				throw new IllegalStateException("Could not write XML data", e);
			}
		}

		@Override
		public void handleCDataContent(String elementName, XmlCdata cdata) {
			try {
				indent();
				theWriter.append(cdata.getContent());
			} catch (IOException e) {
				throw new IllegalStateException("Could not write XML data", e);
			}
		}

		@Override
		public void handleElementEnd(XmlElementTerminal element, boolean selfClosing) {
			if (!selfClosing)
				theIndentLevel--;
			try {
				if (!selfClosing && isElementMultiLine)
					indent();
				theWriter.append(element.getContent());
			} catch (IOException e) {
				throw new IllegalStateException("Could not write XML data", e);
			}
			isElementMultiLine = true;
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
	public MinML setTabLength(int tabLength) {
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
	public MinML withNamedEntity(String name, String sequence) {
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
	public MinML withNamedEntities(Map<String, String> entities) {
		theNamedEntities.putAll(entities);
		return this;
	}

	/**
	 * @param fileLocation The location of the file. This can be anything, including null, and only matters when errors are thrown. When
	 *        given, any {@link XmlParseException}s thrown will have a {@link LocatedFilePosition} for their
	 *        {@link TextParseException#getPosition() position}.
	 * @param in The input stream to parse XML from
	 * @return A {@link ComponentParser} for navigating the document
	 */
	public ComponentParser parseByComponent(String fileLocation, InputStream in) {
		if (in == null)
			throw new NullPointerException("Stream cannot be null");
		return new ComponentParser(fileLocation, in);
	}

	/**
	 * @param fileLocation The location of the file. This can be anything, including null, and only matters when errors are thrown. When
	 *        given, any {@link XmlParseException}s thrown will have a {@link LocatedFilePosition} for their
	 *        {@link TextParseException#getPosition() position}.
	 * @param in The reader stream to parse XML from
	 * @return A {@link ComponentParser} for navigating the document
	 */
	public ComponentParser parseByComponent(String fileLocation, Reader in) {
		if (in == null)
			throw new NullPointerException("Reader cannot be null");
		return new ComponentParser(fileLocation, in);
	}

	/**
	 * Parses XML from a binary stream. The character encoding may be specified by the XML declaration in the document, or is defaulted to
	 * UTF-8.
	 * 
	 * @param <H> The type of the handler
	 * @param fileLocation The location of the file. This can be anything, including null, and only matters when errors are thrown. When
	 *        given, any {@link XmlParseException}s thrown will have a {@link LocatedFilePosition} for their
	 *        {@link TextParseException#getPosition() position}.
	 * @param in The input stream to parse XML from
	 * @param handler The handler to be notified of each XML structure in the document
	 * @return The given handler
	 * @throws IOException If an error occurs reading the stream
	 * @throws XmlParseException If an error occurs parsing the XML
	 */
	public <H extends ParseHandler> H parseXml(String fileLocation, InputStream in, H handler) throws IOException, XmlParseException {
		return parseByComponent(fileLocation, in)//
			.parse(handler);
	}

	/**
	 * Parses XML from a reader. If character encoding is specified by the XML declaration in the document, it will be ignored, other than
	 * being {@link ParseHandler#handleDeclaration(XmlDeclaration) passed} to the handler.
	 * 
	 * @param <H> The type of the handler
	 * @param fileLocation The location of the file. This can be anything, including null, and only matters when errors are thrown. When
	 *        given, any {@link XmlParseException}s thrown will have a {@link LocatedFilePosition} for their
	 *        {@link TextParseException#getPosition() position}.
	 * @param in The reader to parse XML from
	 * @param handler The handler to be notified of each XML structure in the document
	 * @return The given handler
	 * @throws IOException If an error occurs reading the stream
	 * @throws XmlParseException If an error occurs parsing the XML
	 */
	public <H extends ParseHandler> H parseXml(String fileLocation, Reader in, H handler) throws IOException, XmlParseException {
		return parseByComponent(fileLocation, in)//
			.parse(handler);
	}

	/**
	 * Parses an XML document from a binary stream. The character encoding may be specified by the XML declaration in the document, or is
	 * defaulted to UTF-8. The document's {@link Node node}s will be populated with any relevant positions.
	 * 
	 * @param fileLocation The location of the file. This can be anything, including null, and only matters when errors are thrown. When
	 *        given, any {@link XmlParseException}s thrown will have a {@link LocatedFilePosition} for their
	 *        {@link TextParseException#getPosition() position}.
	 * @param in The stream to parse
	 * @return The parsed document
	 * @throws IOException If an error occurs reading the stream
	 * @throws XmlParseException If the XML is malformed
	 * @see #getNamePosition(Node)
	 * @see #getPositionContent(Node)
	 */
	public Document parseDocument(String fileLocation, InputStream in) throws IOException, XmlParseException {
		return parseByComponent(fileLocation, in)//
			.parse(new DomCreatorHandler(DOM_BUILDERS.get().createDocument()))//
			.getDocument();
	}

	/**
	 * Parses an XML document from a binary stream. If character encoding is specified by the XML declaration in the document, it will be
	 * ignored. The document's {@link Node node}s will be populated with any relevant positions.
	 * 
	 * @param fileLocation The location of the file. This can be anything, including null, and only matters when errors are thrown. When
	 *        given, any {@link XmlParseException}s thrown will have a {@link LocatedFilePosition} for their
	 *        {@link TextParseException#getPosition() position}.
	 * @param in The reader to parse
	 * @return The parsed document
	 * @throws IOException If an error occurs reading the stream
	 * @throws XmlParseException If the XML is malformed
	 * @see #getNamePosition(Node)
	 * @see #getPositionContent(Node)
	 */
	public Document parseDocument(String fileLocation, Reader in) throws IOException, XmlParseException {
		return parseByComponent(fileLocation, in)//
			.parse(new DomCreatorHandler(DOM_BUILDERS.get().createDocument()))//
			.getDocument();
	}

	/**
	 * @param fileLocation The location of the file. This can be anything, including null, and only matters when errors are thrown. When
	 *        given, any {@link XmlParseException}s thrown will have a {@link LocatedFilePosition} for their
	 *        {@link TextParseException#getPosition() position}.
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
	public Reader readXmlFile(String fileLocation, InputStream in) throws IOException {
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
		// First, find the declaration, if it exists, or the start of the root element if not
		Charset[] charSet = new Charset[1];
		ComponentParser session = new ComponentParser(fileLocation, wrapped);
		XmlComponent first;
		try {
			first = session.getNextComponent();
			if (first instanceof XmlDeclaration) {
				buffer.append(first.getContent()).append(session.currentChar());
				charSet[0] = ((XmlDeclaration) first).getEncoding();
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

	/** A type of location in an XML document */
	public enum XmlParseState {
		/** Before the XML declaration statement--at the very beginning of the document */
		PreDeclaration,
		/** After the XML declaration statement but prior to the beginning of the root element */
		PreRoot,
		/**
		 * Within an element declaration--after its name and before the '>' declaring the beginning of its content or the self-closing '/>'
		 */
		ElementDeclaration,
		/** Within an element's content, outside of an element declaration */
		ElementContent,
		/** After the closing of the root element */
		EndOfContent
	}

	/**
	 * <p>
	 * A view of an XML document that allows navigation (one-way only) of an XML document.
	 * </p>
	 * <p>
	 * Callers can examine each element as it is encountered, or they can skip forward in the document looking for specific content.
	 * </p>
	 * 
	 * @see #startNextElement(String, boolean)
	 * @see #getAttribute(String, boolean)
	 * @see #getElementContent(boolean)
	 * @see #closeCurrentElement()
	 * @see #parseUntil(ExFunction)
	 * @see #getNextComponent()
	 */
	public class ComponentParser {
		private final String theFileLocation;
		private final InputStream theStream;
		private Reader theReader;
		private XmlParseState theState;
		private char theChar;
		private int theNextChar = -1;
		private int thePosition;
		private int theLineNumber;
		private int theCharNumber;
		private boolean isContentComplete;
		private boolean isAtBeginning = true;
		private boolean isAtEnd;
		private boolean wasCRNL;
		private boolean wasEscapeSequence;

		private LocatedXmlElement theElement;
		private Set<String> theAttributes;
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

		ComponentParser(String fileLocation, InputStream stream) {
			theFileLocation = fileLocation;
			theStream = stream;
			theState = XmlParseState.PreDeclaration;
			theAttributes = new LinkedHashSet<>();
		}

		ComponentParser(String fileLocation, Reader reader) {
			theFileLocation = fileLocation;
			theStream = null;
			theReader = reader;
			theState = XmlParseState.PreDeclaration;
			theAttributes = new LinkedHashSet<>();
		}

		/** @return The location of the file being parsed */
		public String getFileLocation() {
			return theFileLocation;
		}

		/** @return The current location of this parser within the document */
		public XmlParseState getState() {
			return theState;
		}

		/** @return Whether the XML document has been completely read */
		public boolean isAtEnd() {
			return isAtEnd;
		}

		/** @return The character that was the end of the most-recently parsed XML component */
		public char currentChar() {
			return theChar;
		}

		/** @return The XML element whose content is currently (or has just finished) being parsed */
		public LocatedXmlElement getCurrentElement() {
			return theElement;
		}

		/** @return The number of characters that have been read before the {@link #currentChar() current character} */
		public int getPosition() {
			return thePosition; // This method returns the position of the current character
		}

		/** @return The file position of the {@link #currentChar() current character} */
		public FilePosition getFilePosition() {
			return getFilePosition(false);
		}

		/**
		 * Handles the remainder of the XML document using the given parse handler
		 * 
		 * @param <H> The type of the handler
		 * @param handler The parse handler
		 * @return The handler
		 * @throws IOException If the document data could not be read
		 * @throws XmlParseException If the document could not be parsed
		 */
		public <H extends ParseHandler> H parse(H handler) throws IOException, XmlParseException {
			if (handler == null)
				throw new NullPointerException("Handler cannot be null");
			try {
				while (!isAtEnd()) {
					XmlComponent component = getNextComponent();
					handler.handleXmlComponent(component);
				}
			} catch (IOException e) {
				handler.handleIOError(e, getFilePosition(false));
				throw e;
			} catch (XmlParseException e) {
				handler.handleParseError(e);
				throw e;
			}
			return handler;
		}

		/**
		 * Skips to the next child of the {@link #getCurrentElement() current element} (or the root element if this parser has not yet
		 * encountered the root) named <code>elementName</code>, or the next sibling encountered if <code>elementName</code> is null
		 * 
		 * @param elementName The name of the element to search for, or null to return the next child of the current element
		 * @param required Whether to throw a {@link TextParseException} exception if no such element is found before the end of the current
		 *        element
		 * @return The beginning of the next such element, or null if no such element was found (and <code>required</code> was false)
		 * @throws IOException If the document data could not be read
		 * @throws TextParseException If the document could not be parsed, or if no such element was found and <code>required</code> was
		 *         true
		 */
		public XmlElementTerminal startNextElement(String elementName, boolean required) throws IOException, TextParseException {
			int currentDepth = theElement == null ? 0 : (theElement.getDepth() + 1);
			XmlElementTerminal terminal = this.<XmlElementTerminal> parseUntil(component -> {
				if (!(component instanceof XmlElementTerminal))
					return null;
				XmlElementTerminal el = (XmlElementTerminal) component;
				if (el.getDepth() == currentDepth && (elementName == null || elementName.equals(el.getName())))
					return el;
				else if (el.getDepth() < currentDepth) {
					if (required) {
						if (elementName != null)
							throw new TextParseException("Element '" + elementName + "' expected", getFilePosition());
						else
							throw new TextParseException("Element expected", getFilePosition());
					}
					return el;
				} else
					return null;
			});
			if (terminal == null) {
				if (required) {
					if (elementName != null)
						throw new TextParseException("Element '" + elementName + "' expected", getFilePosition());
					else
						throw new TextParseException("Element expected", getFilePosition());
				}
			} else if (terminal.getDepth() < currentDepth)
				return null;
			return terminal;
		}

		/**
		 * Returns the attribute in the {@link #getCurrentElement() current element} named <code>attributeName</code> (or the next attribute
		 * of any name if <code>attributeName</code> is null)
		 * 
		 * @param attributeName The name of the attribute to search for, or null to return the next attribute in the current element
		 * @param required Whether to throw a {@link TextParseException} exception if no such attribute is found before the end of the
		 *        current element's declaration
		 * @return The next such attribute, or null if no such attribute was found (and <code>required</code> was false)
		 * @throws IOException If the document data could not be read
		 * @throws TextParseException If the document could not be parsed, or if no such attribute was found and <code>required</code> was
		 *         true
		 * @throws IllegalStateException If this is called while the parser is not just after the beginning of an XML element (such as just
		 *         after a successful call to {@link #startNextElement(String, boolean)} or this method)
		 */
		public XmlAttribute getAttribute(String attributeName, boolean required)
			throws IOException, TextParseException, IllegalStateException {
			if (theState != XmlParseState.ElementDeclaration)
				throw new IllegalStateException("This method must be called within an element declaration");
			XmlComponent found = parseUntil(component -> {
				if (component instanceof XmlElementOpen || component instanceof XmlElementTerminal) {
					if (required) {
						if (attributeName != null)
							throw new TextParseException("Attribute '" + attributeName + "' expected", getFilePosition());
						else
							throw new TextParseException("Attribute expected", getFilePosition());
					}
					return component;
				} else if (component instanceof XmlAttribute) {
					XmlAttribute attr = (XmlAttribute) component;
					if (attributeName == null || attributeName.equals(attr.getName()))
						return attr;
					else
						return null;
				} else
					return null;
			});
			return found instanceof XmlAttribute ? (XmlAttribute) found : null;
		}

		/**
		 * Returns the next text content of the {@link #getCurrentElement() current element}. An element may have multiple text content
		 * blocks separated by child elements.
		 * 
		 * @param required Whether to throw a {@link TextParseException} if the element has no content
		 * @return The current element's next text content, or null if the element had no more (and <code>required</code> was false)
		 * @throws IOException If the document data could not be read
		 * @throws TextParseException If the document could not be parsed, or if no text content was found and <code>required</code> was
		 *         true
		 * @throws IllegalStateException If this is called while the parser is not in an element declaration or content
		 */
		public XmlElementContent getElementContent(boolean required) throws IOException, TextParseException, IllegalStateException {
			if (theState != XmlParseState.ElementDeclaration && theState != XmlParseState.ElementContent)
				throw new IllegalStateException("This method must be called within an element's declaration or content");
			int currentDepth = theElement == null ? 0 : theElement.getDepth();
			XmlComponent found = parseUntil(component -> {
				if (component instanceof XmlElementTerminal) {
					XmlElementTerminal terminal = (XmlElementTerminal) component;
					if (!terminal.isOpen() && terminal.getDepth() == currentDepth) {
						if (required)
							throw new TextParseException("'" + getCurrentElement().getName() + "' element content expected",
								getFilePosition());
						return terminal;
					} else
						return null;
				} else if (component instanceof XmlElementContent && getCurrentElement().getDepth() == currentDepth) {
					return component;
				} else
					return null;
			});
			return found instanceof XmlElementContent ? (XmlElementContent) found : null;
		}

		/**
		 * Skips to the end of the {@link #getCurrentElement() current element}
		 * 
		 * @return The element close content
		 * @throws IOException If the document data could not be read
		 * @throws TextParseException If the document could not be parsed
		 * @throws IllegalStateException If this is called while the parser is not in an element declaration or content
		 */
		public XmlElementTerminal closeCurrentElement() throws IOException, TextParseException, IllegalStateException {
			if (theState != XmlParseState.ElementDeclaration && theState != XmlParseState.ElementContent)
				throw new IllegalStateException("This method must be called within an element's declaration or content");
			int currentDepth = theElement == null ? 0 : theElement.getDepth();
			return this.<XmlElementTerminal> parseUntil(component -> {
				if (component instanceof XmlElementTerminal) {
					XmlElementTerminal terminal = (XmlElementTerminal) component;
					if (!terminal.isOpen() && terminal.getDepth() == currentDepth) {
						return terminal;
					} else
						return null;
				} else
					return null;
			});
		}

		/**
		 * Parses content in the document until a target component is found
		 * 
		 * @param <C> The type of component to search for
		 * @param until A function that accepts each XML component encountered and returns the component if it matches the search, or null
		 *        otherwise
		 * @return The first encountered component matching the search
		 * @throws IOException If the document data could not be read
		 * @throws TextParseException If the document could not be parsed, or the search function throws an exception
		 */
		public <C extends XmlComponent> C parseUntil(ExFunction<XmlComponent, C, TextParseException> until)
			throws IOException, TextParseException {
			while (!isAtEnd()) {
				XmlComponent component = getNextComponent();
				C found = until.apply(component);
				if (found != null)
					return found;
			}
			return null;
		}

		/**
		 * @return The next encountered XML component
		 * @throws IOException If the document data could not be read
		 * @throws XmlParseException If the document could not be parsed
		 */
		public XmlComponent getNextComponent() throws IOException, XmlParseException{
			if (isAtBeginning)
				nextChar();
			// If we're parsing element content, whitespace is part of the element's content text, not ignorable whitespace
			else if (theState != XmlParseState.ElementContent) {
				if (Character.isWhitespace(theChar)) {
					while (Character.isWhitespace(nextChar())) { //
					}
					return new XmlIgnorableWhitespace(dumpSequence());
				}
			}
			boolean preDecl=false;
			switch(theState) {
			case PreDeclaration:
				preDecl=true;
				//$FALL-THROUGH$
			case PreRoot:
				if (theChar != '<') {
					if(preDecl)
						throwException(false, "The first non-whitespace character in an XML document must be '<', not '" + theChar + "'");
					else
						throwException(false,
							"The first non-whitespace character after the XML declaration must be '<', not '" + theChar + "'");
				}
				// Found the '<'. This is either the XML declaration or the start of the root element if the declaration is missing
				char ch = nextChar();
				if (preDecl && ch == '?') { // XML Declaration
					theState=XmlParseState.PreRoot;
					return parseXmlDeclaration();
				}
				return parsePostLT();
			case ElementDeclaration:
				switch (theChar) {
				case '>': // Element open
					theState = XmlParseState.ElementContent;
					nextChar();
					theAttributes.clear();
					return new XmlElementOpen(theElement.getName(), dumpSequence());
				case '/': // Self-closing element
					if (nextChar() != '>')
						throwException(false, "'>' expected");
					nextChar(); // Include the terminal '>'
					theAttributes.clear();
					theState=XmlParseState.ElementContent;
					String elementName = theElement.getName();
					int depth = theElement.getDepth();
					if (theElement.getParent() == null)
						setContentComplete();
					theElement = theElement.getParent();
					return new XmlElementTerminal(elementName, false, depth, -1, dumpSequence(), true);
				default: // Attribute
					return parseAttribute();
				}
			case ElementContent:
			case EndOfContent:
				if (theChar == '<') {
					nextChar();
					return parsePostLT();
				} else {
					parseXmlContent(false, ELEMENT_CONTENT_TERMINATION);
					return new XmlElementContent(theElement.getName(), dumpSequence());
				}
			}
			throw new IllegalStateException("Unrecognized XML parse state '" + theState + "'");
		}

		private XmlDeclaration parseXmlDeclaration() throws IOException, XmlParseException {
			int decPos = getPosition();
			if (decPos != 1)
				throwException(false, "XML declaration must be at the first position of the first line of the XML document");
			mark();
			if (!expect("xml"))
				throwException(true, "XML Declaration must start with '<?xml'");
			if (!Character.isWhitespace(nextChar()))
				throwException(false, "Expected whitespace after beginning of XML declaration");

			String versionStr = null;
			Charset encoding = null;
			Boolean standalone = null;
			int versionNameOffset = -1, versionValueOffset = -1;
			int encodingNameOffset = -1, encodingValueOffset = -1;
			int standaloneNameOffset = -1, standaloneValueOffset = -1;
			char ch = skipWS(null); // White space is part of the XML declaration
			mark();
			while (ch >= 'a' && ch <= 'z') {
				// New declaration attribute
				int namePos = getPosition();
				String attrName = parseXmlName();
				boolean version, enc;
				switch (attrName) {
				case VERSION:
					if (versionStr != null)
						throwException(true, "Duplicate '" + VERSION + "' attribute on XML declaration");
					version = true;
					enc = false;
					break;
				case ENCODING:
					if (encoding != null)
						throwException(true, "Duplicate '" + ENCODING + "' attribute on XML declaration");
					enc = true;
					version = false;
					break;
				case STANDALONE:
					if (standalone != null)
						throwException(true, "Duplicate '" + STANDALONE + "' attribute on XML declaration");
					version = enc = false;
					break;
				default:
					throwException(true, "Only '" + VERSION + "', '" + ENCODING + "', or '" + STANDALONE
						+ "' attributes are allowed on the XML declaration, not '" + attrName + "'");
					return null;
				}
				startAttribute(attrName);
				int valuePos = getPosition();
				mark();
				String value = parseXmlContent(false, ATTRIBUTE_TERMINATION);
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
						throwException(true, "Illegal character set name: " + value);
					} catch (UnsupportedCharsetException e) {
						throwException(true, "Unsupported character set: " + value);
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
						throwException(true, STANDALONE + " must be 'yes' or 'no', not '" + value + "'");
						break;
					}
				}

				ch = currentChar();
				if (Character.isWhitespace(ch))
					ch = skipWS(null); // White space is part of the XML declaration
			}
			mark();
			if (ch != '?' || nextChar() != '>')
				throwException(false, "XML declaration must end with '?>");
			if (versionStr == null)
				throwException(false, "XML declaration must include the '" + VERSION + "' attribute");
			nextChar(); // Move past the whole declaration so it's all in the sequence
			setEncoding(encoding == null ? StandardCharsets.UTF_8 : encoding);
			return new XmlDeclaration(versionStr, encoding, standalone, dumpSequence(), //
				versionNameOffset, versionValueOffset, encodingNameOffset, encodingValueOffset, standaloneNameOffset,
				standaloneValueOffset);
		}

		private XmlComponent parsePostLT() throws IOException, XmlParseException {
			switch (theChar) {
			case '?': // Processing instruction
				return parseProcessingInstruction();
			case '!':// DOCTYPE declaration, comment, or CDATA
				char ch = nextChar();
				mark();
				switch (ch) {
				case 'D':// DOCTYPE declaration
					if (!expect("OCTYPE"))
						throwException(false, "'<!DOCTYPE' expected but not found");
					throwException(true, "DOCTYPE declarations are not supported by this parser");
					return null;
				case '-': // Comment
					return parseComment();
				case '[':
					return parseCData();
				default:
					throwException(true, "Misplaced '<' or malformed XML construct");
					return null;
				}
			case '/':
				return parseElementClose();
			default:
				return parseElementStart();
			}
		}

		private XmlComment parseComment() throws IOException, XmlParseException {
			if (!expect("-"))
				throwException(false, "'<!-' here should be followed by another '-' for a comment");
			nextChar();
			parseXmlContent(true, COMMENT_TERMINATION);
			if (currentChar() != '>')
				throwException(true, "'--' is not allowed in comments");
			nextChar(); // Get the entire comment into the sequence
			return new XmlComment(dumpSequence());
		}

		private XmlProcessingInstruction parseProcessingInstruction() throws IOException, XmlParseException {
			nextChar();
			mark();
			String target = parseXmlName();
			if (target.equalsIgnoreCase("xml"))
				throwException(true, "Processing instruction cannot be 'xml' with any character case");
			if (!Character.isWhitespace(currentChar())) {
				mark();
				if (currentChar() != '?' || nextChar() != '>') // No content
					throwException(true, "Processing instruction target must be followed by '?>' or whitespace");
				nextChar(); // Include the terminal '>'
				return new XmlProcessingInstruction(target, -1, dumpSequence());
			} else {
				skipWS(null); // Initial white space is part of the processing instruction, but not of the value
				int valuePos = getPosition();
				parseXmlContent(true, PI_TERMINATION);
				return new XmlProcessingInstruction(target, valuePos - getSequenceStartPosition(), dumpSequence());
			}
		}

		private XmlElementTerminal parseElementStart() throws IOException, XmlParseException {
			if (isContentComplete)
				throwException(false, "Multiple root elements are not allowed");
			theState = XmlParseState.ElementDeclaration;
			if (Character.isWhitespace(currentChar()))
				skipWS(null); // White space is part of the element start
			int startPos = getPosition() - getSequenceStartPosition();
			FilePosition namePos = getFilePosition(false);
			String elementName = parseXmlName();
			theElement = new LocatedXmlElement(theElement, elementName, namePos);
			return new XmlElementTerminal(elementName, true, theElement.getDepth(), startPos, dumpSequence(), false);
		}

		private XmlCdata parseCData() throws IOException, XmlParseException {
			if (!expect("CDATA["))
				throwException(true, "Bad CDATA initializer");
			else if (theElement == null)
				throwException(true, "CDATA not allowed outside of the root element");
			nextChar(); // Move to the beginning of the CDATA content
			parseXmlContent(true, CDATA_TERMINATION);
			return new XmlCdata(theElement.getName(), dumpSequence());
		}

		private XmlAttribute parseAttribute() throws IOException, XmlParseException {
			mark();
			String attributeName = parseXmlName();
			if (!theAttributes.add(attributeName))
				throwException(true, "Multiple '" + attributeName + "' attributes specified on this element");
			startAttribute(attributeName);
			int attrValuePos = getPosition() - getSequenceStartPosition();
			parseXmlContent(false, ATTRIBUTE_TERMINATION);
			return new XmlAttribute(attributeName, attrValuePos, dumpSequence());
		}

		private XmlElementTerminal parseElementClose() throws IOException, XmlParseException {
			skipWS(null); // White space is part of the closing tag
			mark();
			int closePos = getPosition() - getSequenceStartPosition();
			String closingElement = parseXmlName();
			if (theElement == null)
				throwException(true, "Unmatched closing element '" + closingElement + "'");
			if (!closingElement.equals(theElement.getName()))
				throwException(true, "Closing element for '" + theElement.getName() + "' expected, not '" + closingElement + "'");
			if (Character.isWhitespace(currentChar()))
				skipWS(null);// White space is part of the closing tag
			if (currentChar() != '>')
				throwException(false, "'>' expected");
			if (theElement.getParent() == null)
				setContentComplete();
			nextChar(); // Include the terminal '>'
			int depth = theElement.getDepth();
			theElement = theElement.getParent();
			return new XmlElementTerminal(closingElement, false, depth, closePos, dumpSequence(), false);
		}

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

		private void setContentComplete() {
			isContentComplete = true;
			theState = XmlParseState.EndOfContent;
		}

		private char nextChar() throws IOException, XmlParseException {
			int ch;
			if (isAtBeginning) {
				isAtBeginning = false;
				ch = getNextStreamChar();
			} else if (wasEscapeSequence) {
				thePosition++;
				wasEscapeSequence = false;
				theSequenceBuffer.append(theChar);
				theCharNumber++;
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

		private PositionedContent dumpSequence() throws IOException, XmlParseException {
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

		private char skipWS(ParseHandler handler) throws IOException, XmlParseException {
			while (Character.isWhitespace(nextChar())) { //
			}
			if (theSequenceBuffer.length() > 0 && handler != null)
				handler.handleIgnorableWhitespace(new XmlIgnorableWhitespace(dumpSequence()));
			return theChar;
		}

		private String parseXmlContent(boolean permissive, Termination terminator) throws IOException, XmlParseException {
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

		private boolean expect(String text) throws IOException, XmlParseException {
			for (int c = 0; c < text.length(); c++) {
				if (nextChar() != text.charAt(c))
					return false;
			}
			return true;
		}

		private void mark() {
			theMarkPosition = thePosition;
			theMarkLineNumber = theLineNumber;
			theMarkCharNumber = theCharNumber;
		}

		private void throwException(boolean atMark, String message) throws XmlParseException {
			int pos = atMark ? theMarkPosition : thePosition;
			int line = atMark ? theMarkLineNumber : theLineNumber;
			int ch = atMark ? theMarkCharNumber : theCharNumber;
			throw new XmlParseException(theElement, theFileLocation, message, pos, line, ch);
		}

		private void setEncoding(Charset charSet) {
			if (theReader == null)
				theReader = new InputStreamReader(theStream, charSet);
		}

		private int getSequenceStartPosition() {
			return theSequencePosition[0];
		}

		private FilePosition getFilePosition(boolean atMark) {
			return new FilePosition(//
				atMark ? theMarkPosition : thePosition, //
				atMark ? theMarkLineNumber : theLineNumber, //
				atMark ? theMarkCharNumber : theCharNumber);
		}

		/** Parses an XML element or attribute name from the stream, including the current character */
		private String parseXmlName() throws IOException, XmlParseException {
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
		private void startAttribute(String attributeName) throws IOException, XmlParseException {
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
			wasEscapeSequence = true;
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
						throwException(true, "Entity name expected following '&'.  Use '&amp;' for the '&' character.");
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

	/**
	 * Gets the name position stored in a node, if it was parsed using this class and name position is relevant for the node
	 * 
	 * @param node The XML node to get the name position for
	 * @return The name position stored in the node
	 * @see #parseDocument(String, InputStream)
	 * @see DomCreatorHandler
	 */
	public static PositionedContent getNamePosition(Node node) {
		return (PositionedContent) node.getUserData(DomCreatorHandler.NAME_POSITION_KEY);
	}

	/**
	 * Gets the content position stored in a node, if it was parsed using this class and content position is relevant for the node
	 * 
	 * @param node The XML node to get the content position for
	 * @return The content position stored in the node
	 * @see #parseDocument(String, InputStream)
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
			new MinML().setTabLength(3).parseXml(null, in, new ParseHandler() {
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
						"Processing instruction @" + pi.getTargetContent().getPosition(0) + ": " + pi.getTargetName() + "="
							+ pi.getValueContent());
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
					System.out.println("Element @" + element.getNamePosition().getPosition(0) + ": " + element.getName());
					indent++;
				}

				@Override
				public void handleAttribute(XmlAttribute attribute) {
					indent();
					System.out.println("Attribute @" + attribute.getNamePosition().getPosition(0) + ": " + attribute.getName() + "="
						+ printContent(attribute.getValueContent().toString()) + " @" + printStart(attribute.getValueContent()));
				}

				@Override
				public void handleElementContent(String elementName, XmlElementContent elementValue) {
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

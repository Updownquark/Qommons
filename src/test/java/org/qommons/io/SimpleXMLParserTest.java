package org.qommons.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.io.SimpleXMLParser.XmlDeclaration;
import org.qommons.io.SimpleXMLParser.XmlParseException;

/** Unit test for {@link SimpleXMLParser} */
public class SimpleXMLParserTest {
	interface XmlExpectedItem {
	}

	/**
	 * Tests against a small XML file
	 * 
	 * @throws IOException If the file is missing from the classpath or cannot be read
	 * @throws XmlParseException If the file cannot be parsed as XML at all
	 */
	@Test
	public void testSimpleXmlParser() throws IOException, XmlParseException {
		try (InputStream in = SimpleXMLParserTest.class.getResourceAsStream("SimpleXmlTest.xml")) {
			SequenceTester tester = new SequenceTester(//
				new ExpectedXmlDeclaration()//
					.withVersion("1.0", 0, 6, 0, 15)//
					.withEncoding("UTF-8", 1, 4, 1, 14), //
				new ExpectedComment(" This is a header comment\nblah ", 3, 4), //
				new ExpectedElementStart("test", 6, 1), //
				new ExpectedAttribute("ns1:attr1", 6, 6, "Attribute 1", 6, 17), //
				new ExpectedAttribute("ns2:attr2", 6, 30, "Attribute 2", 6, 41), //
				new ExpectedAttribute("ns3:attr3", 7, 4, "Attribute 3", 7, 15), //
				new ExpectedElementContent("\n\t", 7, 28), //
				new ExpectedElementStart("child0", 8, 5), //
				new ExpectedElementContent("\n\t\t", 8, 12), //
				new ExpectedProcessingInstruction("CONTENTLESS-INSTRUCTION", 9, 10, null, -1, -1),
				new ExpectedElementContent("\n\t\t", 9, 35), //
				new ExpectedProcessingInstruction("INSTRUCTION", 10, 10, " ", 10, 21),
				new ExpectedElementContent("\n\t\t", 10, 24), //
				new ExpectedProcessingInstruction("INSTRUCTION", 11, 10, " CONTENT", 11, 21),
				new ExpectedElementContent("\n\t\t", 11, 31), //
				new ExpectedElementStart("child00", 12, 9), //
				new ExpectedElementEnd("child00", 12, 19), //
				new ExpectedElementContent("\n\t\t", 12, 27), //
				new ExpectedElementStart("child01", 13, 9), //
				new ExpectedAttribute("attr010", 13, 17, "\"Child Attribute'", 13, 26), //
				new ExpectedElementEnd("child01", -1, -1), //
				new ExpectedElementContent("\n\t", 13, 56), //
				new ExpectedElementEnd("child0", 14, 6), //
				new ExpectedElementContent("\n\t", 14, 13), //
				new ExpectedElementStart("child1", 15, 5), //
				new ExpectedAttribute("attr10", 15, 12, "Multi-\n\t\t-line attribute", 15, 20), //
				new ExpectedElementContent("\n\t\tText 1\n\t\t", 16, 25), //
				new ExpectedComment(" Comment 1", 18, 12), //
				new ExpectedElementContent("\n\t\tText <2>>\n\t", 18, 25), //
				new ExpectedElementEnd("child1", 20, 6), //
				new ExpectedElementContent("\n\t", 20, 13), //
				new ExpectedCdata("\n\t\t<This is some text & stuff>\n\t", 21, 13), //
				new ExpectedElementContent("\n", 23, 7), //
				new ExpectedElementEnd("test", 24, 2), //
				new ExpectedComment(" This is a footer comment with special characters &<'\nblah ", 26, 4)//
			);
			new SimpleXMLParser().setTabLength(4).parseXml(in, tester);
			tester.done();
		}
	}

	static class SequenceTester implements SimpleXMLParser.ParseHandler {
		private final Iterator<XmlExpectedItem> theSequence;
		private int indent;

		SequenceTester(XmlExpectedItem... sequence) {
			theSequence = Arrays.asList(sequence).iterator();
		}

		<X extends XmlExpectedItem> X next(Class<X> type, String descrip) {
			for (int i = 0; i < indent; i++)
				System.out.print('\t');
			System.out.println(descrip);
			XmlExpectedItem next = theSequence.next();
			if (!type.isInstance(next))
				throw new AssertionError("Expected " + next + ", but encountered " + descrip);
			return type.cast(next);
		}

		@Override
		public void handleDeclaration(XmlDeclaration declaration) {
			next(ExpectedXmlDeclaration.class, "XML declaration").validate(declaration);
		}

		@Override
		public void handleProcessingInstruction(String target, FilePosition targetPosition, PositionedContent content) {
			next(ExpectedProcessingInstruction.class, "PI " + target + "=" + content).validate(target, targetPosition, content);
		}

		@Override
		public void handleComment(PositionedContent comment) {
			next(ExpectedComment.class, "comment " + toString(comment)).validate(comment);
		}

		@Override
		public void handleElementStart(String name, FilePosition position) {
			next(ExpectedElementStart.class, "element start: " + name).validate(name, position);
			indent++;
		}

		@Override
		public void handleAttribute(String attributeName, FilePosition namePosition, PositionedContent attributeValue) {
			next(ExpectedAttribute.class, "attribute " + attributeName + "=" + toString(attributeValue)).validate(attributeName,
				namePosition,
				attributeValue);
		}

		@Override
		public void handleElementContent(String elementName, PositionedContent elementValue) {
			next(ExpectedElementContent.class, "element content " + toString(elementValue)).validateContent(elementValue);
		}

		@Override
		public void handleCDataContent(String elementName, PositionedContent content) {
			next(ExpectedCdata.class, "CDATA " + content).validate(content);
		}

		@Override
		public void handleElementEnd(String elementName, FilePosition position, boolean selfClosing) {
			indent--;
			next(ExpectedElementEnd.class, "element end: " + elementName).validate(elementName, position, selfClosing);
		}

		void done() {
			if (theSequence.hasNext())
				throw new IllegalStateException("Missing " + theSequence.next());
		}

		private static String toString(Object o) {
			String str = o.toString();
			return str.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
		}
	}

	static class ExpectedXmlDeclaration implements XmlExpectedItem {
		private String version;
		private String encoding;
		private Boolean standalone;
		private int versionNameLine;
		private int versionNameChar;
		private int versionValueLine;
		private int versionValueChar;
		private int encodingNameLine;
		private int encodingNameChar;
		private int encodingValueLine;
		private int encodingValueChar;
		private int standaloneNameLine;
		private int standaloneNameChar;
		private int standaloneValueLine;
		private int standaloneValueChar;

		ExpectedXmlDeclaration withVersion(String value, int nameLine, int nameChar, int valueLine, int valueChar) {
			version = value;
			versionNameLine = nameLine;
			versionNameChar = nameChar;
			versionValueLine = valueLine;
			versionValueChar = valueChar;
			return this;
		}

		ExpectedXmlDeclaration withEncoding(String value, int nameLine, int nameChar, int valueLine, int valueChar) {
			encoding = value;
			encodingNameLine = nameLine;
			encodingNameChar = nameChar;
			encodingValueLine = valueLine;
			encodingValueChar = valueChar;
			return this;
		}

		ExpectedXmlDeclaration withStandalone(boolean value, int nameLine, int nameChar, int valueLine, int valueChar) {
			standalone = value;
			standaloneNameLine = nameLine;
			standaloneNameChar = nameChar;
			standaloneValueLine = valueLine;
			standaloneValueChar = valueChar;
			return this;
		}

		void validate(XmlDeclaration declaration) {
			if (version != null) {
				Assert.assertEquals(version, declaration.getVersion());
				Assert.assertEquals(versionNameLine, declaration.getVersionNamePosition().getLineNumber());
				Assert.assertEquals(versionNameChar, declaration.getVersionNamePosition().getCharNumber());
				Assert.assertEquals(versionValueLine, declaration.getVersionValuePosition().getLineNumber());
				Assert.assertEquals(versionValueChar, declaration.getVersionValuePosition().getCharNumber());
			} else
				Assert.assertNull(declaration.getVersion());

			if (encoding != null) {
				Assert.assertNotNull(declaration.getEncoding());
				Assert.assertEquals(encoding, declaration.getEncoding().displayName());
				Assert.assertEquals(encodingNameLine, declaration.getEncodingNamePosition().getLineNumber());
				Assert.assertEquals(encodingNameChar, declaration.getEncodingNamePosition().getCharNumber());
				Assert.assertEquals(encodingValueLine, declaration.getEncodingValuePosition().getLineNumber());
				Assert.assertEquals(encodingValueChar, declaration.getEncodingValuePosition().getCharNumber());
			} else
				Assert.assertNull(declaration.getEncoding());

			if (standalone != null) {
				Assert.assertNotNull(declaration.isStandalone());
				Assert.assertEquals(standalone.booleanValue(), declaration.isStandalone().booleanValue());
				Assert.assertEquals(standaloneNameLine, declaration.getStandaloneNamePosition().getLineNumber());
				Assert.assertEquals(standaloneNameChar, declaration.getStandaloneNamePosition().getCharNumber());
				Assert.assertEquals(standaloneValueLine, declaration.getStandaloneValuePosition().getLineNumber());
				Assert.assertEquals(standaloneValueChar, declaration.getStandaloneValuePosition().getCharNumber());
			} else
				Assert.assertNull(declaration.isStandalone());
		}

		@Override
		public String toString() {
			return "XML declaration: " + version + ", " + encoding + ", " + standalone;
		}
	}


	static abstract class ExpectedContent implements XmlExpectedItem {
		private final String content;
		private final int lineNumber;
		private final int charNumber;

		protected ExpectedContent(String content, int lineNumber, int charNumber) {
			this.content = content;
			this.lineNumber = lineNumber;
			this.charNumber = charNumber;
		}

		public String getContent() {
			return content;
		}

		void validateContent(PositionedContent value) {
			Assert.assertEquals(content, value.toString());
			int line = lineNumber, ch = charNumber;
			for (int c = 0; c < content.length(); c++) {
				FilePosition pos = value.getPosition(c);
				if (line != pos.getLineNumber())
					Assert.assertEquals(line, value.getPosition(c).getLineNumber());
				if (ch != pos.getCharNumber())
					Assert.assertEquals(ch, value.getPosition(c).getCharNumber());
				switch (content.charAt(c)) {
				case '\n':
					line++;
					ch = 0;
					break;
				case '\t':
					ch += 4;
					break;
				case '"':
				case '\'':
				case '&':
				case '<':
				case '>':
					ch += value.getSourceContent(c, c + 1).length();
					break;
				default:
					ch++;
				}
			}
			FilePosition pos = value.getPosition(content.length());
			if (line != pos.getLineNumber())
				Assert.assertEquals(line, value.getPosition(content.length()).getLineNumber());
			if (ch != pos.getCharNumber())
				Assert.assertEquals(ch, value.getPosition(content.length()).getCharNumber());
		}
	}


	static class ExpectedComment extends ExpectedContent {
		ExpectedComment(String content, int lineNumber, int charNumber) {
			super(content, lineNumber, charNumber);
		}

		void validate(PositionedContent comment) {
			validateContent(comment);
		}

		@Override
		public String toString() {
			return "comment: " + getContent();
		}
	}

	static class ExpectedProcessingInstruction extends ExpectedContent {
		private final String theTarget;
		private final int theTargetLine;
		private final int theTargetChar;

		ExpectedProcessingInstruction(String target, int targetLine, int targetChar, String content, int contentLine, int contentChar) {
			super(content, contentLine, contentChar);
			theTarget = target;
			theTargetLine = targetLine;
			theTargetChar = targetChar;
		}

		void validate(String target, FilePosition targetPosition, PositionedContent content) {
			Assert.assertEquals(theTarget, target);
			Assert.assertEquals(theTargetLine, targetPosition.getLineNumber());
			Assert.assertEquals(theTargetChar, targetPosition.getCharNumber());
			if (content == null)
				Assert.assertNull(getContent());
			else
				validateContent(content);
		}

		@Override
		public String toString() {
			return "PI " + theTarget + "=" + getContent();
		}
	}

	static abstract class ElementStartOrEnd implements XmlExpectedItem {
		private final String elementName;
		private final int lineNumber;
		private final int charNumber;

		ElementStartOrEnd(String elementName, int lineNumber, int charNumber) {
			this.elementName = elementName;
			this.lineNumber = lineNumber;
			this.charNumber = charNumber;
		}

		public String getElementName() {
			return elementName;
		}

		void validate(String element, FilePosition position) {
			Assert.assertEquals(elementName, element);
			if (lineNumber >= 0) {
				Assert.assertNotNull(position);
				Assert.assertEquals(lineNumber, position.getLineNumber());
				Assert.assertEquals(charNumber, position.getCharNumber());
			} else
				Assert.assertNull(position);
		}
	}

	static class ExpectedElementStart extends ElementStartOrEnd {
		ExpectedElementStart(String elementName, int lineNumber, int charNumber) {
			super(elementName, lineNumber, charNumber);
		}

		@Override
		public String toString() {
			return "element start: " + getElementName();
		}
	}

	static class ExpectedAttribute extends ExpectedContent {
		private final String attributeName;
		private final int nameLineNumber;
		private final int nameCharNumber;

		ExpectedAttribute(String attributeName, int nameLineNumber, int nameCharNumber, String content, int lineNumber, int charNumber) {
			super(content, lineNumber, charNumber);
			this.attributeName = attributeName;
			this.nameLineNumber = nameLineNumber;
			this.nameCharNumber = nameCharNumber;
		}

		void validate(String attribute, FilePosition namePosition, PositionedContent value) {
			Assert.assertEquals(attributeName, attribute);
			Assert.assertEquals(nameLineNumber, namePosition.getLineNumber());
			Assert.assertEquals(nameCharNumber, namePosition.getCharNumber());
			validateContent(value);
		}

		@Override
		public String toString() {
			return "attribute: " + attributeName + "=" + getContent();
		}
	}

	static class ExpectedElementContent extends ExpectedContent {
		ExpectedElementContent(String content, int lineNumber, int charNumber) {
			super(content, lineNumber, charNumber);
		}

		@Override
		public String toString() {
			return "element text " + getContent();
		}
	}

	static class ExpectedCdata extends ExpectedContent {
		ExpectedCdata(String content, int lineNumber, int charNumber) {
			super(content, lineNumber, charNumber);
		}

		void validate(PositionedContent content) {
			validateContent(content);
		}

		@Override
		public String toString() {
			return "CDATA " + getContent();
		}
	}

	static class ExpectedElementEnd extends ElementStartOrEnd {
		ExpectedElementEnd(String elementName, int lineNumber, int charNumber) {
			super(elementName, lineNumber, charNumber);
		}

		void validate(String element, FilePosition position, boolean selfClosing) {
			validate(element, selfClosing ? null : position);
		}

		@Override
		public String toString() {
			return "element end: " + getElementName();
		}
	}
}

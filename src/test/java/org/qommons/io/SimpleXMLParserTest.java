package org.qommons.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.io.SimpleXMLParser.XmlAttribute;
import org.qommons.io.SimpleXMLParser.XmlCdata;
import org.qommons.io.SimpleXMLParser.XmlComment;
import org.qommons.io.SimpleXMLParser.XmlDeclaration;
import org.qommons.io.SimpleXMLParser.XmlElementTerminal;
import org.qommons.io.SimpleXMLParser.XmlParseException;
import org.qommons.io.SimpleXMLParser.XmlProcessingInstruction;

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
					.withVersion("1.0", 6, 0, 6, 15, 0, 15)//
					.withEncoding("UTF-8", 21, 1, 4, 31, 1, 14), //
				new ExpectedWhitespace("\n\n", 39, 1, 22), //
				new ExpectedComment(" This is a header comment\nblah ", 45, 3, 4), //
				new ExpectedWhitespace("\n\n", 79, 4, 8), //
				new ExpectedElementStart("test", 82, 6, 1), //
				new ExpectedWhitespace(" ", 86, 6, 5), //
				new ExpectedAttribute("ns1:attr1", 87, 6, 6, "Attribute 1", 98, 6, 17), //
				new ExpectedWhitespace(" ", 110, 6, 29), //
				new ExpectedAttribute("ns2:attr2", 111, 6, 30, "Attribute 2", 122, 6, 41), //
				new ExpectedWhitespace("\n\t", 134, 6, 53), //
				new ExpectedAttribute("ns3:attr3", 136, 7, 4, "Attribute 3", 147, 7, 15), //
				new ExpectedElementContent("\n\t", 160, 7, 28), //
				new ExpectedElementStart("child0", 163, 8, 5), //
				new ExpectedElementContent("\n\t\t", 170, 8, 12), //
				new ExpectedProcessingInstruction("CONTENTLESS-INSTRUCTION", 175, 9, 10, null, -1, -1, -1),
				new ExpectedElementContent("\n\t\t", 200, 9, 35), //
				new ExpectedProcessingInstruction("INSTRUCTION", 205, 10, 10, "", 217, 10, 22),
				new ExpectedElementContent("\n\t\t", 219, 10, 24), //
				new ExpectedProcessingInstruction("INSTRUCTION", 224, 11, 10, "CONTENT", 236, 11, 22),
				new ExpectedElementContent("\n\t\t", 245, 11, 31), //
				new ExpectedElementStart("child00", 249, 12, 9), //
				new ExpectedElementEnd("child00", 259, 12, 19), //
				new ExpectedElementContent("\n\t\t", 267, 12, 27), //
				new ExpectedElementStart("child01", 271, 13, 9), //
				new ExpectedWhitespace(" ", 278, 13, 16), //
				new ExpectedAttribute("attr010", 279, 13, 17, "\"Child Attribute'\u00a9", 288, 13, 26), //
				new ExpectedElementEnd("child01", -1, -1, -1), //
				new ExpectedElementContent("\n\t", 324, 13, 62), //
				new ExpectedElementEnd("child0", 328, 14, 6), //
				new ExpectedElementContent("\n\t", 335, 14, 13), //
				new ExpectedElementStart("child1", 338, 15, 5), //
				new ExpectedWhitespace(" ", 344, 15, 11), //
				new ExpectedAttribute("attr10", 345, 15, 12, "Multi-\n\t\t-line attribute", 353, 15, 20), //
				new ExpectedElementContent("\n\t\tText 1\u00b0\n\t\t", 379, 16, 25), //
				new ExpectedComment(" Comment 1", 403, 18, 12), //
				new ExpectedElementContent("\n\t\tText <2>>\n\t", 416, 18, 25), //
				new ExpectedElementEnd("child1", 438, 20, 6), //
				new ExpectedElementContent("\n\t", 445, 20, 13), //
				new ExpectedCdata("\n\t\t<This is some text & stuff>\n\t", 456, 21, 13), //
				new ExpectedElementContent("\n", 491, 23, 7), //
				new ExpectedElementEnd("test", 494, 24, 2), //
				new ExpectedWhitespace("\n\n", 499, 24, 7), //
				new ExpectedComment(" This is a footer comment with special characters &<'\nblah ", 505, 26, 4), //
				new ExpectedWhitespace("\n", 567, 27, 8)//
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
		public void handleIgnorableWhitespace(PositionedContent whitespace) {
			next(ExpectedWhitespace.class, "Whitespace: " + toString(whitespace)).validate(whitespace);
		}

		@Override
		public void handleProcessingInstruction(XmlProcessingInstruction pi) {
			next(ExpectedProcessingInstruction.class, "PI " + pi.getTargetName() + "=" + pi.getValueContent()).validate(pi);
		}

		@Override
		public void handleComment(XmlComment comment) {
			next(ExpectedComment.class, "comment " + toString(comment)).validate(comment);
		}

		@Override
		public void handleElementStart(XmlElementTerminal element) {
			next(ExpectedElementStart.class, "element start: " + element.getName()).validate(element);
			indent++;
		}

		@Override
		public void handleAttribute(XmlAttribute attribute) {
			next(ExpectedAttribute.class, "attribute " + attribute.getName() + "=" + toString(attribute.getValueContent()))
				.validate(attribute);
		}

		@Override
		public void handleElementContent(String elementName, PositionedContent elementValue) {
			next(ExpectedElementContent.class, "element content " + toString(elementValue)).validateContent(elementValue);
		}

		@Override
		public void handleCDataContent(String elementName, XmlCdata cdata) {
			next(ExpectedCdata.class, "CDATA " + cdata.getValueContent()).validate(cdata);
		}

		@Override
		public void handleElementEnd(XmlElementTerminal element, boolean selfClosing) {
			indent--;
			next(ExpectedElementEnd.class, "element end: " + element.getName()).validate(element, selfClosing);
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
		private int versionNamePos;
		private int versionNameLine;
		private int versionNameChar;
		private int versionValuePos;
		private int versionValueLine;
		private int versionValueChar;
		private int encodingNamePos;
		private int encodingNameLine;
		private int encodingNameChar;
		private int encodingValuePos;
		private int encodingValueLine;
		private int encodingValueChar;
		private int standaloneNamePos;
		private int standaloneNameLine;
		private int standaloneNameChar;
		private int standaloneValuePos;
		private int standaloneValueLine;
		private int standaloneValueChar;

		ExpectedXmlDeclaration withVersion(String value, int namePos, int nameLine, int nameChar, int valuePos, int valueLine,
			int valueChar) {
			version = value;
			versionNamePos = namePos;
			versionNameLine = nameLine;
			versionNameChar = nameChar;
			versionValuePos = valuePos;
			versionValueLine = valueLine;
			versionValueChar = valueChar;
			return this;
		}

		ExpectedXmlDeclaration withEncoding(String value, int namePos, int nameLine, int nameChar, int valuePos, int valueLine,
			int valueChar) {
			encoding = value;
			encodingNamePos = namePos;
			encodingNameLine = nameLine;
			encodingNameChar = nameChar;
			encodingValuePos = valuePos;
			encodingValueLine = valueLine;
			encodingValueChar = valueChar;
			return this;
		}

		ExpectedXmlDeclaration withStandalone(boolean value, int namePos, int nameLine, int nameChar, int valuePos, int valueLine,
			int valueChar) {
			standalone = value;
			standaloneNamePos = namePos;
			standaloneNameLine = nameLine;
			standaloneNameChar = nameChar;
			standaloneValuePos = valuePos;
			standaloneValueLine = valueLine;
			standaloneValueChar = valueChar;
			return this;
		}

		void validate(XmlDeclaration declaration) {
			if (version != null) {
				Assert.assertEquals(version, declaration.getVersion());
				Assert.assertEquals(versionNamePos, declaration.getVersionNamePosition().getPosition());
				Assert.assertEquals(versionNameLine, declaration.getVersionNamePosition().getLineNumber());
				Assert.assertEquals(versionNameChar, declaration.getVersionNamePosition().getCharNumber());
				Assert.assertEquals(versionValuePos, declaration.getVersionValuePosition().getPosition());
				Assert.assertEquals(versionValueLine, declaration.getVersionValuePosition().getLineNumber());
				Assert.assertEquals(versionValueChar, declaration.getVersionValuePosition().getCharNumber());
			} else
				Assert.assertNull(declaration.getVersion());

			if (encoding != null) {
				Assert.assertNotNull(declaration.getEncoding());
				Assert.assertEquals(encoding, declaration.getEncoding().displayName());
				Assert.assertEquals(encodingNamePos, declaration.getEncodingNamePosition().getPosition());
				Assert.assertEquals(encodingNameLine, declaration.getEncodingNamePosition().getLineNumber());
				Assert.assertEquals(encodingNameChar, declaration.getEncodingNamePosition().getCharNumber());
				Assert.assertEquals(encodingValuePos, declaration.getEncodingValuePosition().getPosition());
				Assert.assertEquals(encodingValueLine, declaration.getEncodingValuePosition().getLineNumber());
				Assert.assertEquals(encodingValueChar, declaration.getEncodingValuePosition().getCharNumber());
			} else
				Assert.assertNull(declaration.getEncoding());

			if (standalone != null) {
				Assert.assertNotNull(declaration.isStandalone());
				Assert.assertEquals(standalone.booleanValue(), declaration.isStandalone().booleanValue());
				Assert.assertEquals(standaloneNamePos, declaration.getStandaloneNamePosition().getPosition());
				Assert.assertEquals(standaloneNameLine, declaration.getStandaloneNamePosition().getLineNumber());
				Assert.assertEquals(standaloneNameChar, declaration.getStandaloneNamePosition().getCharNumber());
				Assert.assertEquals(standaloneValuePos, declaration.getStandaloneValuePosition().getPosition());
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
		private final int position;
		private final int lineNumber;
		private final int charNumber;

		protected ExpectedContent(String content, int position, int lineNumber, int charNumber) {
			this.content = content;
			this.position = position;
			this.lineNumber = lineNumber;
			this.charNumber = charNumber;
		}

		public String getContent() {
			return content;
		}

		void validateContent(PositionedContent value) {
			Assert.assertEquals(content, value.toString());
			int expectPos = position, line = lineNumber, ch = charNumber;
			for (int c = 0; c < content.length(); c++) {
				FilePosition pos = value.getPosition(c);
				if (expectPos != pos.getPosition())
					Assert.assertEquals(position, value.getPosition(c).getPosition());
				if (line != pos.getLineNumber())
					Assert.assertEquals(line, value.getPosition(c).getLineNumber());
				if (ch != pos.getCharNumber())
					Assert.assertEquals(ch, value.getPosition(c).getCharNumber());
				switch (content.charAt(c)) {
				case '\n':
					expectPos++;
					line++;
					ch = 0;
					break;
				case '\t':
					expectPos++;
					ch += 4;
					break;
				default:
					int seqLen = value.getSourceContent(c, c + 1).length();
					ch += seqLen;
					expectPos += seqLen;
					break;
				}
			}
			FilePosition pos = value.getPosition(content.length());
			if (line != pos.getLineNumber())
				Assert.assertEquals(line, value.getPosition(content.length()).getLineNumber());
			if (ch != pos.getCharNumber())
				Assert.assertEquals(ch, value.getPosition(content.length()).getCharNumber());
		}
	}

	static class ExpectedWhitespace extends ExpectedContent {
		ExpectedWhitespace(String content, int position, int lineNumber, int charNumber) {
			super(content, position, lineNumber, charNumber);
		}

		void validate(PositionedContent value) {
			validateContent(value);
		}
	}

	static class ExpectedComment extends ExpectedContent {
		ExpectedComment(String content, int position, int lineNumber, int charNumber) {
			super(content, position, lineNumber, charNumber);
		}

		void validate(XmlComment comment) {
			validateContent(comment.getValueContent());
		}

		@Override
		public String toString() {
			return "comment: " + getContent();
		}
	}

	static class ExpectedProcessingInstruction extends ExpectedContent {
		private final String theTarget;
		private final int theTargetPosition;
		private final int theTargetLine;
		private final int theTargetChar;

		ExpectedProcessingInstruction(String target, int targetPos, int targetLine, int targetChar, String content, int contentPos,
			int contentLine, int contentChar) {
			super(content, contentPos, contentLine, contentChar);
			theTarget = target;
			theTargetPosition = targetPos;
			theTargetLine = targetLine;
			theTargetChar = targetChar;
		}

		void validate(XmlProcessingInstruction pi) {
			Assert.assertEquals(theTarget, pi.getTargetName());
			Assert.assertEquals(theTargetPosition, pi.getTargetPosition().getPosition());
			Assert.assertEquals(theTargetLine, pi.getTargetPosition().getLineNumber());
			Assert.assertEquals(theTargetChar, pi.getTargetPosition().getCharNumber());
			if (pi.getValueOffset() < 0)
				Assert.assertNull(getContent());
			else
				validateContent(pi.getValueContent());
		}

		@Override
		public String toString() {
			return "PI " + theTarget + "=" + getContent();
		}
	}

	static abstract class ElementStartOrEnd implements XmlExpectedItem {
		private final String elementName;
		private final int position;
		private final int lineNumber;
		private final int charNumber;

		ElementStartOrEnd(String elementName, int position, int lineNumber, int charNumber) {
			this.elementName = elementName;
			this.position = position;
			this.lineNumber = lineNumber;
			this.charNumber = charNumber;
		}

		public String getElementName() {
			return elementName;
		}

		void validate(XmlElementTerminal element) {
			Assert.assertEquals(elementName, element.getName());
			if (lineNumber >= 0) {
				Assert.assertTrue(element.getNameOffset() >= 0);
				Assert.assertEquals(position, element.getNamePosition().getPosition());
				Assert.assertEquals(lineNumber, element.getNamePosition().getLineNumber());
				Assert.assertEquals(charNumber, element.getNamePosition().getCharNumber());
			} else
				Assert.assertTrue(element.getNameOffset() < 0);
		}
	}

	static class ExpectedElementStart extends ElementStartOrEnd {
		ExpectedElementStart(String elementName, int position, int lineNumber, int charNumber) {
			super(elementName, position, lineNumber, charNumber);
		}

		@Override
		public String toString() {
			return "element start: " + getElementName();
		}
	}

	static class ExpectedAttribute extends ExpectedContent {
		private final String attributeName;
		private final int namePosition;
		private final int nameLineNumber;
		private final int nameCharNumber;

		ExpectedAttribute(String attributeName, int namePosition, int nameLineNumber, int nameCharNumber, String content,
			int contentPosition, int lineNumber, int charNumber) {
			super(content, contentPosition, lineNumber, charNumber);
			this.attributeName = attributeName;
			this.namePosition = namePosition;
			this.nameLineNumber = nameLineNumber;
			this.nameCharNumber = nameCharNumber;
		}

		void validate(XmlAttribute attribute) {
			Assert.assertEquals(attributeName, attribute.getName());
			Assert.assertEquals(namePosition, attribute.getNamePosition().getPosition());
			Assert.assertEquals(nameLineNumber, attribute.getNamePosition().getLineNumber());
			Assert.assertEquals(nameCharNumber, attribute.getNamePosition().getCharNumber());
			validateContent(attribute.getValueContent());
		}

		@Override
		public String toString() {
			return "attribute: " + attributeName + "=" + getContent();
		}
	}

	static class ExpectedElementContent extends ExpectedContent {
		ExpectedElementContent(String content, int position, int lineNumber, int charNumber) {
			super(content, position, lineNumber, charNumber);
		}

		@Override
		public String toString() {
			return "element text " + getContent();
		}
	}

	static class ExpectedCdata extends ExpectedContent {
		ExpectedCdata(String content, int position, int lineNumber, int charNumber) {
			super(content, position, lineNumber, charNumber);
		}

		void validate(XmlCdata cdata) {
			validateContent(cdata.getValueContent());
		}

		@Override
		public String toString() {
			return "CDATA " + getContent();
		}
	}

	static class ExpectedElementEnd extends ElementStartOrEnd {
		ExpectedElementEnd(String elementName, int position, int lineNumber, int charNumber) {
			super(elementName, position, lineNumber, charNumber);
		}

		void validate(XmlElementTerminal element, boolean selfClosing) {
			validate(element);
		}

		@Override
		public String toString() {
			return "element end: " + getElementName();
		}
	}
}

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
					.withVersion("1.0", 0, 6, 0, 15)//
					.withEncoding("UTF-8", 1, 4, 1, 14), //
				new ExpectedWhitespace("\n\n", 1, 22), //
				new ExpectedComment(" This is a header comment\nblah ", 3, 4), //
				new ExpectedWhitespace("\n\n", 4, 8), //
				new ExpectedElementStart("test", 6, 1), //
				new ExpectedWhitespace(" ", 6, 5), //
				new ExpectedAttribute("ns1:attr1", 6, 6, "Attribute 1", 6, 17), //
				new ExpectedWhitespace(" ", 6, 29), //
				new ExpectedAttribute("ns2:attr2", 6, 30, "Attribute 2", 6, 41), //
				new ExpectedWhitespace("\n\t", 6, 53), //
				new ExpectedAttribute("ns3:attr3", 7, 4, "Attribute 3", 7, 15), //
				new ExpectedElementContent("\n\t", 7, 28), //
				new ExpectedElementStart("child0", 8, 5), //
				new ExpectedElementContent("\n\t\t", 8, 12), //
				new ExpectedProcessingInstruction("CONTENTLESS-INSTRUCTION", 9, 10, null, -1, -1),
				new ExpectedElementContent("\n\t\t", 9, 35), //
				new ExpectedProcessingInstruction("INSTRUCTION", 10, 10, "", 10, 22),
				new ExpectedElementContent("\n\t\t", 10, 24), //
				new ExpectedProcessingInstruction("INSTRUCTION", 11, 10, "CONTENT", 11, 22),
				new ExpectedElementContent("\n\t\t", 11, 31), //
				new ExpectedElementStart("child00", 12, 9), //
				new ExpectedElementEnd("child00", 12, 19), //
				new ExpectedElementContent("\n\t\t", 12, 27), //
				new ExpectedElementStart("child01", 13, 9), //
				new ExpectedWhitespace(" ", 13, 16), //
				new ExpectedAttribute("attr010", 13, 17, "\"Child Attribute'", 13, 26), //
				new ExpectedElementEnd("child01", -1, -1), //
				new ExpectedElementContent("\n\t", 13, 56), //
				new ExpectedElementEnd("child0", 14, 6), //
				new ExpectedElementContent("\n\t", 14, 13), //
				new ExpectedElementStart("child1", 15, 5), //
				new ExpectedWhitespace(" ", 15, 11), //
				new ExpectedAttribute("attr10", 15, 12, "Multi-\n\t\t-line attribute", 15, 20), //
				new ExpectedElementContent("\n\t\tText 1\u00b0\n\t\t", 16, 25), //
				new ExpectedComment(" Comment 1", 18, 12), //
				new ExpectedElementContent("\n\t\tText <2>>\n\t", 18, 25), //
				new ExpectedElementEnd("child1", 20, 6), //
				new ExpectedElementContent("\n\t", 20, 13), //
				new ExpectedCdata("\n\t\t<This is some text & stuff>\n\t", 21, 13), //
				new ExpectedElementContent("\n", 23, 7), //
				new ExpectedElementEnd("test", 24, 2), //
				new ExpectedWhitespace("\n\n", 24, 7), //
				new ExpectedComment(" This is a footer comment with special characters &<'\nblah ", 26, 4), //
				new ExpectedWhitespace("\n", 27, 8)//
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
				default:
					ch += value.getSourceContent(c, c + 1).length();
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
		ExpectedWhitespace(String content, int lineNumber, int charNumber) {
			super(content, lineNumber, charNumber);
		}

		void validate(PositionedContent value) {
			validateContent(value);
		}
	}

	static class ExpectedComment extends ExpectedContent {
		ExpectedComment(String content, int lineNumber, int charNumber) {
			super(content, lineNumber, charNumber);
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
		private final int theTargetLine;
		private final int theTargetChar;

		ExpectedProcessingInstruction(String target, int targetLine, int targetChar, String content, int contentLine, int contentChar) {
			super(content, contentLine, contentChar);
			theTarget = target;
			theTargetLine = targetLine;
			theTargetChar = targetChar;
		}

		void validate(XmlProcessingInstruction pi) {
			Assert.assertEquals(theTarget, pi.getTargetName());
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

		void validate(XmlElementTerminal element) {
			Assert.assertEquals(elementName, element.getName());
			if (lineNumber >= 0) {
				Assert.assertTrue(element.getNameOffset() >= 0);
				Assert.assertEquals(lineNumber, element.getNamePosition().getLineNumber());
				Assert.assertEquals(charNumber, element.getNamePosition().getCharNumber());
			} else
				Assert.assertTrue(element.getNameOffset() < 0);
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

		void validate(XmlAttribute attribute) {
			Assert.assertEquals(attributeName, attribute.getName());
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

		void validate(XmlCdata cdata) {
			validateContent(cdata.getValueContent());
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

		void validate(XmlElementTerminal element, boolean selfClosing) {
			validate(element);
		}

		@Override
		public String toString() {
			return "element end: " + getElementName();
		}
	}
}

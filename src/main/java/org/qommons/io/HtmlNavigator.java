package org.qommons.io;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.qommons.Named;

/** A tool for parsing HTML files */
public class HtmlNavigator {
	public static class Tag implements Named {
		private final Tag theParent;
		private final String theName;
		private final Set<String> theClasses;
		private final Map<String, String> theAttributes;
		private final boolean isSelfClosing;
		private final int theDepth;
		private boolean isClosed;

		public Tag(Tag parent, String name, Set<String> classes, Map<String, String> attributes, boolean selfClosing, int depth) {
			theParent = parent;
			theName = name;
			theClasses = classes == null ? Collections.emptySet() : Collections.unmodifiableSet(classes);
			theAttributes = attributes == null ? Collections.emptyMap() : Collections.unmodifiableMap(attributes);
			isClosed = isSelfClosing = selfClosing;
			theDepth = depth;
		}

		@Override
		public String getName() {
			return theName;
		}

		public Tag getParent() {
			return theParent;
		}

		public Set<String> getClasses() {
			return theClasses;
		}

		public Map<String, String> getAttributes() {
			return theAttributes;
		}

		public int getDepth() {
			return theDepth;
		}

		public boolean isClosed() {
			return isClosed;
		}

		public boolean matches(String tagName, String... className) {
			if (!theName.equalsIgnoreCase(tagName))
				return false;
			for (String c : className)
				if (!theClasses.contains(c))
					return false;
			return true;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append('<').append(theName);
			if (!theClasses.isEmpty()) {
				str.append(" class=\"");
				boolean first = true;
				for (String c : theClasses) {
					if (first) {
						first = false;
					} else {
						str.append(' ');
					}
					str.append(c);
				}
				str.append('"');
			}
			if (!theAttributes.isEmpty()) {
				for (Map.Entry<String, String> attr : theAttributes.entrySet()) {
					str.append(' ').append(attr.getKey()).append("=\"").append(attr.getValue()).append('"');
				}
			}
			if (isSelfClosing)
				str.append('/');
			str.append('>');
			return str.toString();
		}

		public String printEnd() {
			return new StringBuilder("</").append(theName).append('>').toString();
		}
	}

	public static final Set<String> UNCLOSED_TAGS = new HashSet<>(Arrays.asList("meta", "br"));

	private final Reader theReader;
	private Tag theTop;
	private final StringBuilder theContent;
	private boolean isDone;

	public HtmlNavigator(Reader reader) {
		theReader = reader;
		theContent = new StringBuilder();
	}

	public Tag getTop() {
		return theTop;
	}

	public boolean isDone() {
		return isDone;
	}

	public int getDepth() {
		return theTop == null ? 0 : theTop.getDepth();
	}

	public Tag find(String tagName, String... classNames) throws IOException {
		return find(//
			tag -> tag.matches(tagName, classNames));
	}

	public Tag find(Predicate<Tag> filter) throws IOException {
		Tag top = theTop;
		while (!isDone) {
			Tag tag = descend();
			if (tag != null) {
				if (filter.test(tag))
					return tag;
			} else if (theTop == null || (top != null && top.isClosed()))
				break;
		}
		return null;
	}

	public Tag descend() throws IOException {
		theContent.setLength(0);
		StringBuilder tagNameTemp = new StringBuilder();
		String[] attrName = new String[1];
		int r = theReader.read();
		while (r >= 0) {
			if (r == '<') {
				r = theReader.read();
				if (r == '/') {
					r = theReader.read();
					while (!Character.isAlphabetic(r))
						r = theReader.read();
					StringBuilder endTag = new StringBuilder();
					while (Character.isAlphabetic(r)) {
						endTag.append((char) r);
						r = theReader.read();
					}
					if (theTop != null && theTop.matches("script") && !endTag.toString().toLowerCase().equals("script")) {}
					{
						while (r != '>') {
							r = theReader.read();
						}
						ascend(endTag.toString());
						return null;
					}
				} else if (theTop != null && theTop.matches("script")) {
					// Don't parse script tag content
				} else {
					boolean closed[] = new boolean[1];
					while (r >= 0 && (Character.isAlphabetic(r) || (tagNameTemp.length() == 0 && r == '!'))) {
						if (r == '!')
							closed[0] = true;
						tagNameTemp.append((char) r);
						r = theReader.read();
					}
					Map<String, String> attributes = null;
					Set<String> classes = null;
					if (r == '/') {
						closed[0] = true;
						while (r != '>')
							r = theReader.read();
					} else if (r != '>') {
						String attr = getAttribute(attrName, closed);
						while (attr != null) {
							if (attrName[0].toLowerCase().equals("class")) {
								classes = new HashSet<>();
								for (String c : attr.split(" ")) {
									if (c.length() > 0) {
										classes.add(c);
									}
								}
							} else {
								if (attributes == null) {
									attributes = new LinkedHashMap<>();
								}
								attributes.put(attrName[0], attr);
							}
							attr = getAttribute(attrName, closed);
						}
					}
					if (!closed[0])
						closed[0] = UNCLOSED_TAGS.contains(tagNameTemp.toString());
					Tag tag = new Tag(theTop, tagNameTemp.toString(), //
						classes == null ? Collections.emptySet() : classes, //
						attributes == null ? Collections.emptyMap() : attributes, //
						closed[0], theTop == null ? 1 : theTop.getDepth() + 1);
					if (!tag.isClosed())
						theTop = tag;
					return tag;
				}
			} else {
				theContent.append((char) r);
			}
			r = theReader.read();
		}
		isDone = true;
		return null;
	}

	public void close(Tag tag) throws IOException {
		while (!tag.isClosed())
			descend();
	}

	public String getLastContent() {
		return theContent.toString();
	}

	public String getEmphasizedContent() throws IOException {
		Tag top = descend();
		while (top != null && descend() != null) {}
		String content = getLastContent();
		if (top != null && !top.isClosed())
			close(top);
		return content;
	}

	private void ascend(String tagName) {
		if (theTop == null)
			return;
		Tag oldTop = theTop;
		theTop = oldTop.getParent();
		oldTop.isClosed = true;
		while (theTop != null && !oldTop.matches(tagName)) {
			oldTop = theTop;
			theTop = oldTop.getParent();
			oldTop.isClosed = true;
		}
	}

	private String getAttribute(String[] attrName, boolean[] closed) throws IOException {
		int r = theReader.read();
		while (r >= 0 && r != '>' && !isAttChar(r)) {
			if (r == '/')
				closed[0] = true;
			r = theReader.read();
		}
		if (Character.isAlphabetic(r)) {
			StringBuilder str = new StringBuilder();
			do {
				str.append((char) r);
				r = theReader.read();
			} while (r >= 0 && isAttChar(r));
			while (Character.isWhitespace((char) r)) {
				r = theReader.read();
			}
			if (r != '=') {
				return null;
			}
			r = theReader.read();
			while (Character.isWhitespace((char) r)) {
				r = theReader.read();
			}
			if (r != '"') {
				return null;
			}
			r = theReader.read();
			attrName[0] = str.toString();
			str.setLength(0);
			while (r >= 0 && r != '"') {
				str.append((char) r);
				r = theReader.read();
			}
			return str.toString();
		} else {
			return null;
		}
	}

	private static boolean isAttChar(int r) {
		return Character.isAlphabetic(r) || Character.isDigit(r) || r == '-' || r == '_' || r == '.';
	}
}

package org.qommons.config;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.qommons.Named;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Wraps an XML element and allows easier access to its structure. This class also checks the structure as it is used as well as afterward
 * to verify that the element was specified as intended.
 */
public class StrictXmlReader implements Named {
	private final StrictXmlReader theParent;
	private final Element theElement;
	private final BitSet theUsedElements;
	private boolean isClosed;

	/** @param element The XML element to wrap */
	public StrictXmlReader(Element element) {
		this(null, element);
	}

	private StrictXmlReader(StrictXmlReader parent, Element element) {
		if (element == null)
			throw new NullPointerException();
		theParent = parent;
		theElement = element;
		theUsedElements = new BitSet();
	}

	/** @return This element's parent reader */
	public StrictXmlReader getParent() {
		return theParent;
	}

	@Override
	public String getName() {
		if (theElement.getPrefix() != null)
			return theElement.getPrefix() + ":" + theElement.getTagName();
		return theElement.getTagName();
	}

	/** @return The portion of this element's name before the first colon, or null if no colon was present */
	public String getPrefix() {
		String prefix = theElement.getPrefix();
		if (prefix != null)
			return prefix;
		int colon = theElement.getNodeName().indexOf(':');
		return colon < 0 ? null : theElement.getNodeName().substring(0, colon);
	}

	/** @return The portion of this element's name after the first colon, or the entire element name if no colon was present */
	public String getTagName() {
		int colon = theElement.getNodeName().indexOf(':');
		return colon < 0 ? theElement.getNodeName() : theElement.getNodeName().substring(colon + 1);
	}

	/**
	 * @param name The name of the attribute to get the value of
	 * @param required Whether to throw an exception if the attribute was not specified
	 * @return The attribute text, or null if none was specified
	 * @throws IllegalArgumentException If <code>required</code> and the attribute was not specified
	 */
	public String getAttribute(String name, boolean required) throws IllegalArgumentException {
		String value = null;
		for (int i = 0; i < theElement.getAttributes().getLength(); i++) {
			Node attr = theElement.getAttributes().item(i);
			if (name.equals(attr.getNodeName())) {
				value = attr.getNodeValue();
				theUsedElements.set(i);
				break;
			}
		}
		if (value == null && required)
			throw new IllegalArgumentException(getPath() + ": No attribute '" + name + "' specified on element " + getPath());
		return value;
	}

	/**
	 * @param pattern The pattern to match attribute names with
	 * @param min The minimum number of attributes expected
	 * @param max The maximum number of attributes expected, or &lt;0 to enforce no max
	 * @return A map of attribute name to value for all attributes whose names match the given pattern
	 * @throws IllegalArgumentException If the number of matching attributes specified was <code>&lt;min</code> or <code>&gt;max</code>
	 */
	public Map<String, String> findAttributes(Pattern pattern, int min, int max) throws IllegalArgumentException {
		Map<String, String> found = new LinkedHashMap<>();
		for (int i = 0; i < theElement.getAttributes().getLength(); i++) {
			Node n = theElement.getAttributes().item(i);
			if (!pattern.matcher(n.getNodeName()).matches())
				continue;
			found.put(n.getNodeName(), n.getNodeValue());
			theUsedElements.set(i);
		}
		if (found.size() < min || (max >= 0 && found.size() > max))
			throw new IllegalArgumentException(getPath() + ": Between " + min + " and " + max + " attribute"
				+ ((min == max && max == 1) ? "" : "s") + " matching " + pattern.pattern() + " expected," + " but found " + found.size());
		return found;
	}

	/** @return A name-to-value map of all attributes specified on this element */
	public Map<String, String> getAllAttributes() {
		Map<String, String> found = new LinkedHashMap<>();
		for (int i = 0; i < theElement.getAttributes().getLength(); i++) {
			Node n = theElement.getAttributes().item(i);
			found.put(n.getNodeName(), n.getNodeValue());
			theUsedElements.set(i);
		}
		return found;
	}

	/**
	 * @param name The name of the element to get
	 * @param required Whether to throw an exception if the element was not specified
	 * @return The child element with the given name, or null if none was specified
	 * @throws IllegalArgumentException If multiple elements with the given name were specified, or if <code>required</code> and no such
	 *         element was specified
	 */
	public StrictXmlReader getElement(String name, boolean required) throws IllegalArgumentException {
		Element found = null;
		for (int i = 0; i < theElement.getChildNodes().getLength(); i++) {
			Node n = theElement.getChildNodes().item(i);
			if (!(n instanceof Element) || !((Element) n).getTagName().equals(name))
				continue;
			if (found != null)
				throw new IllegalArgumentException(getPath() + ": Multiple '" + name + "' elements specified under parent " + getPath());
			found = (Element) n;
			theUsedElements.set(i + theElement.getAttributes().getLength());
		}
		if (found != null)
			return new StrictXmlReader(this, found);
		else if (required)
			throw new IllegalArgumentException(getPath() + ": No element '" + name + "' specified under parent " + getPath());
		else
			return null;
	}

	/**
	 * @param name The name of the elements to get
	 * @param min The minimum number of elements expected
	 * @param max The maximum number of elements expected, or &lt;0 to enforce no max
	 * @return All child elements with the given name
	 * @throws IllegalArgumentException If the number of matching elements specified was <code>&lt;min</code> or <code>&gt;max</code>
	 */
	public List<StrictXmlReader> getElements(String name, int min, int max) throws IllegalArgumentException {
		List<StrictXmlReader> found = new ArrayList<>();
		int attLen = theElement.getAttributes().getLength();
		for (int i = 0; i < theElement.getChildNodes().getLength(); i++) {
			Node n = theElement.getChildNodes().item(i);
			if (!(n instanceof Element) || !((Element) n).getTagName().equals(name))
				continue;
			found.add(new StrictXmlReader(this, (Element) n));
			theUsedElements.set(i + attLen);
		}
		if (found.size() < min || (max >= 0 && found.size() > max))
			throw new IllegalArgumentException(getPath() + ": Between " + min + " and " + max + " '" + name + "' element"
				+ ((min == max && max == 1) ? "" : "s") + " expected," + " but found " + found.size());
		return found;
	}

	/**
	 * @param min The minimum number of elements expected
	 * @param max The maximum number of elements expected, or &lt;0 to enforce no max
	 * @return All child elements
	 * @throws IllegalArgumentException If the number of matching elements specified was <code>&lt;min</code> or <code>&gt;max</code>
	 */
	public List<StrictXmlReader> getElements(int min, int max) throws IllegalArgumentException {
		List<StrictXmlReader> found = new ArrayList<>();
		int attLen = theElement.getAttributes().getLength();
		for (int i = 0; i < theElement.getChildNodes().getLength(); i++) {
			Node n = theElement.getChildNodes().item(i);
			if (!(n instanceof Element))
				continue;
			found.add(new StrictXmlReader(this, (Element) n));
			theUsedElements.set(i + attLen);
		}
		if (found.size() < min || (max >= 0 && found.size() > max))
			throw new IllegalArgumentException(getPath() + ": Between " + min + " and " + max + " element"
				+ ((min == max && max == 1) ? "" : "s") + " expected," + " but found " + found.size());
		return found;
	}

	/**
	 * @param required Whether to throw an exception if the element was specified as self-closing
	 * @return The text content of this element, trimmed for whitespace at both ends
	 * @throws IllegalArgumentException If multiple text sections were specified, or if <code>required</code> and the element was
	 *         self-closing
	 */
	public String getTextTrim(boolean required) throws IllegalArgumentException {
		String found = null;
		boolean anyText = false;
		int attLen = theElement.getAttributes().getLength();
		for (int i = 0; i < theElement.getChildNodes().getLength(); i++) {
			Node n = theElement.getChildNodes().item(i);
			if ((n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE)) {
				anyText = true;
				if (!isWhiteSpace(n.getNodeValue())) {
					if (found != null)
						throw new IllegalArgumentException(
							getPath() + ": Multiple text/CDATA sections specified under parent " + getPath());
					found = n.getNodeValue();
				}
				theUsedElements.set(i + attLen);
			}
		}
		if (!anyText) {
			if (required)
				throw new IllegalArgumentException(getPath() + ": No text specified on element " + getPath());
			else
				return null;
		} else if (found != null)
			return found.trim();
		else
			return "";
	}

	/**
	 * @param trim Whether to trim each text section
	 * @param required Whether to throw an exception if the element was specified as self-closing
	 * @return The text content of this element. If multiple text sections were specified, they will be appended with a newline character
	 *         between
	 * @throws IllegalArgumentException If <code>required</code> and the element was self-closing
	 */
	public String getCompleteText(boolean trim, boolean required) throws IllegalArgumentException {
		StringBuilder found = new StringBuilder();
		boolean anyText = false;
		int attLen = theElement.getAttributes().getLength();
		for (int i = 0; i < theElement.getChildNodes().getLength(); i++) {
			Node n = theElement.getChildNodes().item(i);
			if ((n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE)) {
				anyText = true;
				if (trim) {
					if (found.length() > 0)
						found.append('\n');
					found.append(n.getNodeValue().trim());
				} else
					found.append(n.getNodeValue());
				theUsedElements.set(i + attLen);
			}
		}
		if (!anyText && required)
			throw new IllegalArgumentException(getPath() + ": No text specified on element " + getPath());
		return found.toString();
	}

	/**
	 * @param trim Whether to trim each text section
	 * @param min The minimum number of text sections expected
	 * @param max The maximum number of text sections expected, or &lt;0 to enforce no max
	 * @return The content of all text sections of this element
	 * @throws IllegalArgumentException If the number of text sections specified was <code>&lt;min</code> or <code>&gt;max</code>
	 */
	public List<String> getAllText(boolean trim, int min, int max) {
		List<String> found = new ArrayList<>();
		int attLen = theElement.getAttributes().getLength();
		for (int i = 0; i < theElement.getChildNodes().getLength(); i++) {
			Node n = theElement.getChildNodes().item(i);
			if ((n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE)) {
				theUsedElements.set(i + attLen);
				if (trim && isWhiteSpace(n.getNodeValue()))
					continue;
				found.add(trim ? n.getNodeValue().trim() : n.getNodeValue());
			}
		}
		if (found.size() < min || (max >= 0 && found.size() > max))
			throw new IllegalArgumentException(getPath() + ": Between " + min + " and " + max + " text section"
				+ ((min == max && max == 1) ? "" : "s") + " expected," + " but found " + found.size());
		return found;
	}

	/** @return This element's path under the root */
	public StringBuilder getPath() {
		if (theParent != null)
			return theParent.getPath().append('/').append(theElement.getNodeName());
		else
			return new StringBuilder(theElement.getNodeName());
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder().append('<');
		str.append(getName());
		for (int i = 0; i < theElement.getAttributes().getLength(); i++) {
			Node n = theElement.getAttributes().item(i);
			str.append(' ').append(n.getNodeName()).append("=\"").append(n.getNodeValue()).append('"');
		}
		return str.append('>').toString();
	}

	/**
	 * Goes through all this element's content and throws an exception if any of it was not used
	 * 
	 * @throws IllegalArgumentException If any attributes, child elements, or non-whitespace text was unused (i.e. was not requested through
	 *         any methods)
	 */
	public void check() throws IllegalArgumentException {
		if (isClosed)
			return;
		isClosed = true;
		Map<String, Integer> errs = null;
		int attLen = theElement.getAttributes().getLength();
		for (int i = theUsedElements.nextClearBit(0); i >= 0
			&& i < attLen + theElement.getChildNodes().getLength(); i = theUsedElements.nextClearBit(i + 1)) {
			if (errs == null)
				errs = new LinkedHashMap<>();
			Node n = i < attLen ? theElement.getAttributes().item(i) : theElement.getChildNodes().item(i - attLen);
			switch (n.getNodeType()) {
			case Node.ELEMENT_NODE:
				errs.compute("element '" + ((Element) n).getTagName() + "'", (__, ct) -> ct == null ? 1 : ct + 1);
				break;
			case Node.ATTRIBUTE_NODE:
				errs.compute("attribute '" + n.getNodeName() + "'", (__, ct) -> ct == null ? 1 : ct + 1);
				break;
			case Node.TEXT_NODE:
				if (!isWhiteSpace(n.getNodeValue()))
					errs.compute("text", (__, ct) -> ct == null ? 1 : ct + 1);
				break;
			case Node.CDATA_SECTION_NODE:
				errs.compute("CDATA", (__, ct) -> ct == null ? 1 : ct + 1);
				break;
			default:
				continue;
			}
		}
		if (errs == null)
			return;
		switch (errs.size()) {
		case 0:
			break;
		case 1:
			if (errs.entrySet().iterator().next().getValue() == 1)
				throw new IllegalArgumentException(
					getPath() + ": Illegal content on element " + getPath() + ": " + errs.keySet().iterator().next());
			//$FALL-THROUGH$
		default:
			StringBuilder str = new StringBuilder(getPath() + ": Illegal content on element ").append(':');
			for (Map.Entry<String, Integer> err : errs.entrySet()) {
				str.append("\n\t").append(err.getKey());
				if (err.getValue() > 1)
					str.append("(x").append(err.getValue()).append(')');
			}
			throw new IllegalArgumentException(str.toString());
		}
	}

	private static boolean isWhiteSpace(String nodeValue) {
		if (nodeValue == null)
			return true;
		for (int i = 0; i < nodeValue.length(); i++) {
			switch (nodeValue.charAt(i)) {
			case ' ':
			case '\n':
			case '\r':
			case '\t':
				break;
			default:
				return false;
			}
		}
		return true;
	}
}

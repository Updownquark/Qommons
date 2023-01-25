package org.qommons.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Represents the specification of an {@link QonfigElementOwned element-owned item} from a stream */
public class ElementQualifiedParseItem {
	/** The pattern to parse qualified names */
	public static final Pattern QUALIFIED_ITEM_NAME = Pattern
		.compile("(?<wholeElement>((?<ns>[a-zA-Z0-9_\\-]+)\\:)?(?<element>[a-zA-Z0-9_\\-]+))\\.(?<attribute>[a-zA-Z0-9_\\-]+)");

	/** The namespace qualifier for the element */
	public final String declaredNamespace;
	/** The name of the element qualifier */
	public final String declaredElementName;
	/** The qualifying element */
	public final QonfigElementOrAddOn declaredElement;
	/** The name of the item */
	public final String itemName;

	/**
	 * @param ns The namespace qualifier for the element
	 * @param declaredElementName The name of the element qualifier
	 * @param declaredElement The qualifying element
	 * @param itemName The name of the item
	 */
	public ElementQualifiedParseItem(String ns, String declaredElementName, QonfigElementOrAddOn declaredElement, String itemName) {
		declaredNamespace = ns;
		this.declaredElementName = declaredElementName;
		this.declaredElement = declaredElement;
		this.itemName = itemName;
	}

	/** @return The qualifier, as it wa specified, or the empty string if there was no qualifier */
	public String printQualifier() {
		if (declaredElementName == null)
			return "";
		StringBuilder str = new StringBuilder();
		if (declaredNamespace != null)
			str.append(declaredNamespace).append(':');
		if (declaredElementName != null)
			str.append(declaredElementName);
		return str.toString();
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		if (declaredNamespace != null)
			str.append(declaredNamespace).append(':');
		if (declaredElementName != null)
			str.append(declaredElementName).append('.');
		str.append(itemName);
		return str.toString();
	}

	/**
	 * @param item The string to parse the qualified item from
	 * @param session The session to get the toolkit from and log errors to
	 * @return The qualified item, or null if it could not be parsed
	 */
	public static ElementQualifiedParseItem parse(String item, QonfigParseSession session) {
		Matcher m = QUALIFIED_ITEM_NAME.matcher(item);
		if (m.matches()) {
			QonfigElementOrAddOn qualifier;
			try {
				qualifier = session.getToolkit().getElementOrAddOn(m.group("wholeElement"));
				if (qualifier == null) {
					session.error("No such element found: " + m.group("wholeElement"));
					return null;
				}
			} catch (IllegalArgumentException e) {
				session.error(e.getMessage());
				return null;
			}
			String ns = m.group("ns");
			String elName = m.group("element");
			String itemName = m.group("attribute");
			return new ElementQualifiedParseItem(ns, elName, qualifier, itemName);
		} else
			return new ElementQualifiedParseItem(null, null, null, item);
	}
}
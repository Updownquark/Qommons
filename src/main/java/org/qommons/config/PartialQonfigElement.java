package org.qommons.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.qommons.MultiInheritanceSet;
import org.qommons.SelfDescribed;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMultiMap;
import org.qommons.config.QonfigAttributeDef.Declared;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;

public class PartialQonfigElement implements FileSourced, SelfDescribed {
	private final QonfigDocument theDocument;
	private final PartialQonfigElement theParent;
	private final QonfigElementOrAddOn theType;
	private final MultiInheritanceSet<QonfigAddOn> theInheritance;
	private final Set<QonfigChildDef> theParentRoles;
	private final Set<QonfigChildDef.Declared> theDeclaredRoles;
	private final Map<QonfigAttributeDef.Declared, QonfigValue> theAttributes;
	private final List<? extends PartialQonfigElement> theChildren;
	private final BetterMultiMap<QonfigChildDef.Declared, ? extends PartialQonfigElement> theChildrenByRole;
	private final QonfigValue theValue;
	private final LocatedPositionedContent theFilePosition;
	private final String theDescription;

	private final PartialQonfigElement thePromise;
	private final QonfigDocument theExternalContent;

	protected PartialQonfigElement(QonfigDocument document, PartialQonfigElement parent, QonfigElementOrAddOn type,
		MultiInheritanceSet<QonfigAddOn> inheritance, Set<QonfigChildDef> parentRoles, Set<QonfigChildDef.Declared> declaredRoles,
		Map<Declared, QonfigValue> attributes, List<? extends PartialQonfigElement> children,
		BetterMultiMap<QonfigChildDef.Declared, ? extends PartialQonfigElement> childrenByRole, QonfigValue value,
		LocatedPositionedContent filePosition, String description, PartialQonfigElement promise, QonfigDocument externalContent) {
		if (document.getPartialRoot() == null)
			document.setRoot(this);
		theDocument = document;
		theParent = parent;
		theType = type;
		theInheritance = inheritance;
		theParentRoles = parentRoles;
		theDeclaredRoles = declaredRoles;
		theAttributes = attributes;
		theChildren = children;
		theChildrenByRole = childrenByRole;
		theValue = value;
		theFilePosition = filePosition;
		theDescription = description;
		theExternalContent = externalContent;
		thePromise = promise;
	}

	/** @return The document this element belongs to */
	public QonfigDocument getDocument() {
		return theDocument;
	}

	@Override
	public LocatedPositionedContent getFilePosition() {
		return theFilePosition;
	}

	/** @return The position in the file where the start of this element was declared */
	public LocatedFilePosition getPositionInFile() {
		return getFilePosition().getPosition(0);
	}

	/** @return This element's parent */
	public PartialQonfigElement getParent() {
		return theParent;
	}

	/** @return This element's declared type */
	public QonfigElementOrAddOn getType() {
		return theType;
	}

	/** @return The roles that this element fulfills in its {@link #getParent() parent} */
	public Set<QonfigChildDef> getParentRoles() {
		return theParentRoles;
	}

	/** @return The declared roles that this element fulfills in its {@link #getParent() parent} */
	public Set<QonfigChildDef.Declared> getDeclaredRoles() {
		return theDeclaredRoles;
	}

	/** @return All add-ons that this element inherits */
	public MultiInheritanceSet<QonfigAddOn> getInheritance() {
		return theInheritance;
	}

	@Override
	public String getDescription() {
		return theDescription;
	}

	public PartialQonfigElement getPromise() {
		return thePromise;
	}

	public QonfigDocument getExternalContent() {
		return theExternalContent;
	}

	/**
	 * @param el The element-def or add-on to test
	 * @return Whether this element extends or inherits the given element-def or add-on
	 */
	public boolean isInstance(QonfigElementOrAddOn el) {
		if (el.isAssignableFrom(theType))
			return true;
		if (el instanceof QonfigAddOn) {
			for (QonfigAddOn inh : theInheritance.values())
				if (el.isAssignableFrom(inh))
					return true;
		}
		return false;
	}

	/** @return The values of all attributes specified for this element */
	public Map<QonfigAttributeDef.Declared, QonfigValue> getAttributes() {
		return theAttributes;
	}

	/**
	 * A shortcut for <code>getAttribute(toolkit.getAttribute(elementOrAddOnName, attributeName), type)</code>
	 * 
	 * @param <T> The type of the value to get
	 * @param toolkit The toolkit that defines the target attribute
	 * @param elementOrAddOnName The name of the element/add-on that declares the target attribute
	 * @param attributeName The name of the target attribute
	 * @param type The type of the value to get, or null if no check is to be done
	 * @return The value of the target attribute
	 * @throws IllegalArgumentException If:
	 *         <ul>
	 *         <li>No such attribute exists in the toolkit</li>
	 *         <li>The owner element/add-on of the target attribute is not inherited/extended by this element</li>
	 *         <li>The value for the attribute does not match the given type</li>
	 *         </ul>
	 */
	public <T> T getAttribute(QonfigToolkit toolkit, String elementOrAddOnName, String attributeName, Class<T> type)
		throws IllegalArgumentException {
		QonfigAttributeDef attr = toolkit.getAttribute(elementOrAddOnName, attributeName);
		QonfigValue value = theAttributes.get(attr.getDeclared());
		if (value == null) {
			if (!isInstance(attr.getDeclared().getOwner()))
				throw new IllegalArgumentException("This element (type " + theType.getName() + ") does not "
					+ (attr.getDeclared().getOwner() instanceof QonfigAddOn ? "inherit add-on" : "extend element-def") + " '"
					+ attr.getDeclared().getOwner().getName() + "' and cannot have a value for attribute " + attr);
			return null;
		} else if (type != null && !type.isInstance(value)) {
			boolean match = false;
			if (type.isPrimitive()) {
				if (type == boolean.class)
					match = value.value instanceof Boolean;
				else if (type == char.class)
					match = value.value instanceof Character;
				else if (type == byte.class)
					match = value.value instanceof Byte;
				else if (type == short.class)
					match = value.value instanceof Short;
				else if (type == int.class)
					match = value.value instanceof Integer;
				else if (type == long.class)
					match = value.value instanceof Long;
				else if (type == float.class)
					match = value.value instanceof Float;
				else if (type == double.class)
					match = value.value instanceof Double;
				else
					throw new IllegalStateException("Unaccounted primitive type " + type.getName());
			}
			if (!match)
				throw new IllegalArgumentException(
					"Value '" + value + "' for attribute " + attr + " is typed " + value.getClass().getName() + ", not " + type.getName());
		}
		return (T) value.value;
	}

	/**
	 * @param <T> The type of the attribute
	 * @param attr The attribute to get the value of
	 * @param type The type of the attribute
	 * @return The value of the given attribute, or null if it was not specified
	 * @throws IllegalArgumentException If no attribute with the given name is defined for this element's extended/inherited types, or if
	 *         multiple such attributes are defined
	 * @throws ClassCastException If the attribute was specified, but is not of the given type
	 */
	public <T> T getAttribute(QonfigAttributeDef attr, Class<T> type) throws IllegalArgumentException, ClassCastException {
		QonfigValue value = theAttributes.get(attr.getDeclared());
		if (value == null) {
			if (!isInstance(attr.getOwner()))
				throw new IllegalArgumentException("Attribute " + attr + " mis-applied to " + theType.getName());
			return null;
		}
		if (type.isInstance(value.value))
			return (T) value.value;
		else if (type.isPrimitive()) {
			boolean match;
			if (type == boolean.class)
				match = value.value instanceof Boolean;
			else if (type == char.class)
				match = value.value instanceof Character;
			else if (type == byte.class)
				match = value.value instanceof Byte;
			else if (type == short.class)
				match = value.value instanceof Short;
			else if (type == int.class)
				match = value.value instanceof Integer;
			else if (type == long.class)
				match = value.value instanceof Long;
			else if (type == float.class)
				match = value.value instanceof Float;
			else if (type == double.class)
				match = value.value instanceof Double;
			else
				throw new IllegalStateException("Unaccounted primitive type " + type.getName());
			if (!match)
				throw new ClassCastException(
					theType + ": Value " + value + ", type " + value.value.getClass().getName() + " cannot be cast to " + type.getName());
		} else
			throw new ClassCastException(
				theType + ": Value " + value + ", type " + value.value.getClass().getName() + " cannot be cast to " + type.getName());
		return (T) value.value;
	}

	/**
	 * @param attr The attribute to get the value of
	 * @return The text of the given attribute, or null if it was not specified
	 * @throws IllegalArgumentException If no attribute with the given name is defined for this element's extended/inherited types, or if
	 *         multiple such attributes are defined
	 */
	public String getAttributeText(QonfigAttributeDef attr) throws IllegalArgumentException {
		QonfigValue value = theAttributes.get(attr.getDeclared());
		if (value == null) {
			if (!isInstance(attr.getOwner()))
				throw new IllegalArgumentException("Attribute " + attr + " mis-applied to " + theType.getName());
			return null;
		}
		return value.text;
	}

	/**
	 * A shortcut for <code>(BetterList<QonfigElement>) getChildrenByRole().get(toolkit.getChild(elementOrAddOnName, childName))</code> that
	 * additionally throws an exception if the given child is declared by a type that this element does not extend/inherit.
	 * 
	 * @param toolkit The toolkit that defines the target child
	 * @param elementOrAddOnName The name of the element/add-on that declares the target child
	 * @param childName The name of the target child
	 * @return All children in this element that fulfill the given child role
	 * @throws IllegalArgumentException If:
	 *         <ul>
	 *         <li>No such child exists in the toolkit</li>
	 *         <li>The owner element/add-on of the target child is not inherited/extended by this element</li>
	 *         </ul>
	 */
	public BetterList<? extends PartialQonfigElement> getChildrenInRole(QonfigToolkit toolkit, String elementOrAddOnName, String childName)
		throws IllegalArgumentException {
		QonfigChildDef child = toolkit.getChild(elementOrAddOnName, childName);
		BetterList<PartialQonfigElement> children = (BetterList<PartialQonfigElement>) theChildrenByRole.get(child.getDeclared());
		if (children.isEmpty() && !isInstance(child.getDeclared().getOwner()))
			throw new IllegalArgumentException("This element (type " + getType().getName() + ") does not "
				+ (child.getDeclared().getOwner() instanceof QonfigAddOn ? "inherit add-on" : "extend element-def") + " '"
				+ child.getDeclared().getOwner().getName() + "' and cannot have a value for attribute " + child);
		return children;
	}

	/** @return All children specified for the element */
	public List<? extends PartialQonfigElement> getChildren() {
		return theChildren;
	}

	/** @return Children specified for the element, grouped by role */
	public BetterMultiMap<QonfigChildDef.Declared, ? extends PartialQonfigElement> getChildrenByRole() {
		return theChildrenByRole;
	}

	/** @return The value specified for this element */
	public QonfigValue getValue() {
		return theValue;
	}

	/** @return The value text specified for this element */
	public String getValueText() {
		return theValue == null ? null : theValue.toString();
	}

	public void copyInto(QonfigElement.Builder parent) {
		parent.withChild2(theParentRoles, theType, this::copy, theFilePosition, theDescription);
	}

	public void copy(QonfigElement.Builder child) {
		copyAttributes(child);
		copyChildren(child);
	}

	public void copyAttributes(QonfigElement.Builder child) {
		for (Map.Entry<QonfigAttributeDef.Declared, QonfigValue> attr : theAttributes.entrySet())
			child.withAttribute(attr.getKey(), attr.getValue());
		if (theValue != null)
			child.withValue(theValue.text, theValue.value, theValue.position);
	}

	public void copyChildren(QonfigElement.Builder child) {
		for (PartialQonfigElement myChild : theChildren)
			myChild.copyInto(child);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder().append('<').append(theType.getName());
		for (Map.Entry<QonfigAttributeDef.Declared, QonfigValue> attr : theAttributes.entrySet()) {
			if (attr.getValue() != null)
				str.append(' ').append(attr.getKey()).append("=\"").append(attr.getValue()).append('"');
		}
		if (theValue != null)
			str.append('>').append(theValue.text).append("</").append(getType().getName()).append('>');
		else
			str.append(" />");
		return str.toString();
	}
}
package org.qommons.config;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.qommons.MultiInheritanceSet;
import org.qommons.SelfDescribed;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMultiMap;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;
import org.qommons.io.PositionedContent;

/** An element in a Qonfig document */
public class QonfigElement implements FileSourced, SelfDescribed {
	/** An attribute or text content value */
	public static class QonfigValue {
		/** The source text of the value */
		public final String text;
		/** The parsed value */
		public final Object value;
		/** The location of the file where the value was specified */
		public final String fileLocation;
		/** The position in the file where this value was specified. This will be null if the value is defaulted from a definition. */
		public final PositionedContent position;

		/**
		 * @param text The source text of the value
		 * @param value The parsed value
		 * @param fileLocation The location of the file where the value was specified
		 * @param position The position in the file where this value was specified. This will be null if the value is defaulted from a
		 *        definition
		 */
		public QonfigValue(String text, Object value, String fileLocation, PositionedContent position) {
			this.text = text;
			this.value = value;
			this.fileLocation = fileLocation;
			this.position = position;
		}

		@Override
		public String toString() {
			return text;
		}
	}

	private final QonfigDocument theDocument;
	private final QonfigElement theParent;
	private final QonfigElementDef theType;
	private final MultiInheritanceSet<QonfigAddOn> theInheritance;
	private final Set<QonfigChildDef> theParentRoles;
	private final Set<QonfigChildDef.Declared> theDeclaredRoles;
	private final Map<QonfigAttributeDef.Declared, QonfigValue> theAttributes;
	private final List<QonfigElement> theChildren;
	private final BetterMultiMap<QonfigChildDef.Declared, QonfigElement> theChildrenByRole;
	private final QonfigValue theValue;
	private final LocatedPositionedContent theFilePosition;
	private final String theDescription;

	private QonfigElement(QonfigDocument doc, QonfigElement parent, QonfigElementDef type, MultiInheritanceSet<QonfigAddOn> inheritance,
		Set<QonfigChildDef> parentRoles, Set<QonfigChildDef.Declared> declaredRoles,
		Map<QonfigAttributeDef.Declared, QonfigValue> attributes, List<QonfigElement> children,
		BetterMultiMap<QonfigChildDef.Declared, QonfigElement> childrenByRole, QonfigValue value, LocatedPositionedContent filePosition,
		String description) {
		if (doc.getRoot() == null)
			doc.setRoot(this);
		theDocument = doc;
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
	}

	/** @return The document this element belongs to */
	public QonfigDocument getDocument() {
		return theDocument;
	}

	/** @return This element's parent */
	public QonfigElement getParent() {
		return theParent;
	}

	/** @return This element's declared type */
	public QonfigElementDef getType() {
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
	public LocatedPositionedContent getFilePosition() {
		return theFilePosition;
	}

	/** @return The position in the file where the start of this element was declared */
	public LocatedFilePosition getPositionInFile() {
		return getFilePosition().getPosition(0);
	}

	@Override
	public String getDescription() {
		return theDescription;
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
	public BetterList<QonfigElement> getChildrenInRole(QonfigToolkit toolkit, String elementOrAddOnName, String childName)
		throws IllegalArgumentException {
		QonfigChildDef child = toolkit.getChild(elementOrAddOnName, childName);
		BetterList<QonfigElement> children = (BetterList<QonfigElement>) theChildrenByRole.get(child.getDeclared());
		if (children.isEmpty() && !isInstance(child.getDeclared().getOwner()))
			throw new IllegalArgumentException("This element (type " + theType.getName() + ") does not "
				+ (child.getDeclared().getOwner() instanceof QonfigAddOn ? "inherit add-on" : "extend element-def") + " '"
				+ child.getDeclared().getOwner().getName() + "' and cannot have a value for attribute " + child);
		return children;
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

	/** @return All children specified for the element */
	public List<QonfigElement> getChildren() {
		return theChildren;
	}

	/** @return Children specified for the element, grouped by role */
	public BetterMultiMap<QonfigChildDef.Declared, QonfigElement> getChildrenByRole() {
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

	/**
	 * Creates a child of this element. However, the child will not be added to this element's {@link #getChildren() children}.
	 * 
	 * @param child The role for the new child to fill
	 * @param type The element type of the new child
	 * @param errors The error reporting for the builder
	 * @param description A description for the synthetic child
	 * @return A builder for a new, disconnected child of this element
	 */
	public Builder synthesizeChild(QonfigChildDef child, QonfigElementDef type, ErrorReporting errors, String description) {
		return new Builder(errors, getDocument(), this, type, Collections.singleton(child), Collections.emptySet(), description);
	}

	/** @return A string with this element's type name and shortened file location */
	public String printLocation() {
		StringBuilder str = new StringBuilder().append('<').append(theType.getName()).append("> ");
		if (theFilePosition == null)
			return str.toString();
		str.append(theFilePosition.getPosition(0).toShortString());
		return str.toString();
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder().append('<').append(theType.getName());
		for (Map.Entry<QonfigAttributeDef.Declared, QonfigValue> attr : theAttributes.entrySet()) {
			if (attr.getValue() != null)
				str.append(' ').append(attr.getKey()).append("=\"").append(attr.getValue()).append('"');
		}
		str.append('>');
		if (theValue != null)
			str.append(theValue.text).append("</").append(theType.getName()).append('>');
		return str.toString();
	}

	/**
	 * Builds an element
	 * 
	 * @param session The session for error logging
	 * @param doc The document being parsed
	 * @param parent The parent for the new element
	 * @param type The declared type of the element
	 * @param description A description for the element
	 * @return A builder for an element
	 */
	public static Builder build(QonfigParseSession session, QonfigDocument doc, QonfigElement parent, QonfigElementDef type,
		String description) {
		return new Builder(session, doc, parent, type, Collections.emptySet(), Collections.emptySet(), description);
	}

	/** Represents an attribute value before it is parsed */
	public static interface AttributeValue {
		/** @return The text value given for this attribute value */
		String getText();

		/** @return The positioning of this attribute value */
		PositionedContent getPosition();

		/**
		 * Parses the attribute value
		 * 
		 * @param toolkit The {@link QonfigDocument}'s toolkit
		 * @param attribute The attribute to parse the value for
		 * @param errors The reporting to log errors
		 * 
		 * @return The parsed value
		 */
		Object parseAttributeValue(QonfigToolkit toolkit, QonfigAttributeDef attribute, ErrorReporting errors);
	}

	/** Builds a QonfigElement */
	public static class Builder {
		private final ErrorReporting theErrors;
		private final QonfigDocument theDocument;
		private final QonfigElement theParent;
		private final QonfigElementDef theType;
		private final MultiInheritanceSet<QonfigAddOn> theInheritance;
		private final QonfigAutoInheritance.Compiler theAutoInheritance;
		private final List<ElementQualifiedParseItem> theDeclaredAttributes;
		private final List<AttributeValue> theDeclaredAttributeValues;
		private final Set<QonfigChildDef> theParentRoles;
		private final Set<QonfigChildDef.Declared> theDeclaredRoles;
		private final List<QonfigElement> theChildren;
		private final BetterMultiMap<QonfigChildDef.Declared, QonfigElement> theChildrenByRole;
		private final String theDescription;
		private QonfigValue theValue;
		private QonfigElement theElement;
		private int theStage;

		Builder(ErrorReporting errors, QonfigDocument doc, QonfigElement parent, QonfigElementDef type,
			Set<QonfigChildDef> parentRoles, Set<QonfigChildDef.Declared> declaredRoles, String description) {
			if (type.isAbstract())
				errors.error("Elements cannot be declared directly for abstract type " + type);
			theErrors = errors;
			theDocument = doc;
			theType = type;
			theParent = parent;
			theParentRoles = parentRoles;
			theDeclaredRoles = declaredRoles;
			theDeclaredAttributes = new ArrayList<>();
			theDeclaredAttributeValues = new ArrayList<>();
			theChildren = new ArrayList<>();
			theChildrenByRole = BetterHashMultiMap.<QonfigChildDef.Declared, QonfigElement> build().buildMultiMap();
			theInheritance = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
			for (QonfigChildDef child : parentRoles) {
				if (child.getType() != null && !child.getType().isAssignableFrom(type))
					errors
						.error("This element (" + theType + ") does not inherit " + child.getType() + "--cannot fulfill role " + child);
				theInheritance.addAll(child.getInheritance());
				for (QonfigAddOn inh : parent.getInheritance().getExpanded(QonfigAddOn::getInheritance)) {
					ChildDefModifier mod = inh.getChildModifiers().get(child.getDeclared());
					if (mod != null) {
						if (mod.getTypeRestriction() != null && !mod.getTypeRestriction().isAssignableFrom(type))
							errors.error("This element (" + theType + ") does not inherit " + mod.getTypeRestriction()
								+ " specified by inheritance " + inh + "--cannot fulfill role " + child);
						theInheritance.addAll(mod.getInheritance());
					}
				}
			}
			theAutoInheritance = new QonfigAutoInheritance.Compiler(Collections.singleton(doc.getDocToolkit()), theParentRoles,
				theInheritance::add);
			theAutoInheritance.add(type, theInheritance::add);
			theInheritance.addAll(doc.getDocToolkit().getAutoInheritance(theType, theParentRoles).values());
			theDescription = description;

			for (QonfigChildDef ch : parentRoles) {
				for (QonfigAddOn req : ch.getRequirement()) {
					if (!req.isAssignableFrom(type) && !theInheritance.contains(req))
						errors.error("Element " + type + " does not inherit " + req + ", which is required by role " + ch);
				}
			}
		}

		/** @return The declared type of the element */
		public QonfigElementDef getType() {
			return theType;
		}

		/**
		 * @param addOn An add-on directly declared for the element to inherit
		 * @return This builder
		 */
		public Builder inherits(QonfigAddOn addOn) {
			if (theStage > 0)
				throw new IllegalStateException("Cannot specify inheritance after children");
			boolean ok = true;
			if (addOn.isAbstract()) {
				theErrors.error("Add-on " + addOn + " is abstract and cannot be applied directly");
				ok = false;
			}
			if (addOn.getSuperElement() != null && !addOn.getSuperElement().isAssignableFrom(theType)) {
				theErrors
					.error("Add-on " + addOn + " requires " + addOn.getSuperElement() + ", which " + theType + " does not extend");
				ok = false;
			}
			for (QonfigAddOn inh : addOn.getFullInheritance().getExpanded(QonfigAddOn::getInheritance)) {
				if (inh.getSuperElement() != null && !inh.getSuperElement().isAssignableFrom(theType)) {
					theErrors
						.error("Add-on " + addOn + " requires " + addOn.getSuperElement() + ", which " + theType + " does not extend");
					ok = false;
				}
			}
			if (ok) {
				theInheritance.add(addOn);
				theAutoInheritance.add(addOn, theInheritance::add);
			}
			return this;
		}

		/**
		 * @param attr The attribute specification
		 * @param value The attribute value
		 * @return This builder
		 */
		public Builder withAttribute(ElementQualifiedParseItem attr, AttributeValue value) {
			if (theStage > 0)
				throw new IllegalStateException("Cannot specify attributes after children");
			if (attr.declaredElement != null && !attr.declaredElement.getDeclaredAttributes().containsKey(attr.itemName)) {
				int count = attr.declaredElement.getAttributesByName().get(attr.itemName).size();
				if (count == 0) {
					theErrors.at(attr.position)
						.error("Element " + attr.declaredElement + " does not declare attribute \"" + attr.itemName + "\"", null);
					return this;
				} else if (count > 1) {
					theErrors.at(attr.position)
						.error("Element " + attr.declaredElement + " inherits multiple attributes named \"" + attr.itemName + "\"", null);
					return this;
				}
			}
			theDeclaredAttributes.add(attr);
			theDeclaredAttributeValues.add(value);
			return this;
		}

		/**
		 * Called when all attributes have been declared
		 * 
		 * @return This builder
		 */
		public Builder doneWithAttributes() {
			create();
			return this;
		}

		/**
		 * @param text The text for the element's value
		 * @param value The value for this element
		 * @param position The position of the value
		 * @return This builder
		 */
		public Builder withValue(String text, Object value, PositionedContent position) {
			if (theStage > 0)
				throw new IllegalStateException("Cannot specify text after children");
			else if (theValue != null)
				throw new IllegalStateException("Cannot specify value twice");
			theValue = new QonfigValue(text, value, theDocument.getLocation(), position);
			return this;
		}

		/**
		 * @param declaredRoles The role specifications that the child is declared to fulfill
		 * @param type The declared type of the element
		 * @param child Consumer to configure the child element
		 * @param position The position in the file where the child was defined
		 * @param description A description for the new child
		 * @return This builder
		 */
		public Builder withChild(List<ElementQualifiedParseItem> declaredRoles, QonfigElementDef type,
			Consumer<QonfigElement.Builder> child, PositionedContent position, String description) {
			if (theStage > 1)
				throw new IllegalStateException("Cannot add children after the element has been built");
			// At this stage, we have all the information we need to determine the complete inheritance of the element
			create();
			ErrorReporting errors = theErrors.at(position);
			Set<QonfigChildDef> roles = new LinkedHashSet<>(declaredRoles.size() * 3 / 2 + 1);
			Set<QonfigChildDef.Declared> realRoles = new LinkedHashSet<>(declaredRoles.size() * 3 / 2 + 1);
			if (!declaredRoles.isEmpty()) {
				roleLoop: //
				for (ElementQualifiedParseItem roleDef : declaredRoles) {
					QonfigChildDef role;
					if (roleDef.declaredElement != null) {
						if (!(roleDef.declaredElement instanceof QonfigElementDef)) {
							errors.error(roleDef.printQualifier() + " is an add-on--roles must be qualified with element-defs");
							continue;
						} else if (theParent == null) {
							errors.error("Cannot declare a role on the root element: " + roleDef, null);
							continue;
						} else if (!theParent.isInstance(roleDef.declaredElement)) {
							errors.error("Parent does not inherit element " + roleDef.declaredElement
								+ "--cannot declare a child with role " + roleDef, null);
							continue;
						}
						role = ((QonfigElementDef) roleDef.declaredElement).getDeclaredChildren().get(roleDef.itemName);
						if (role == null) {
							BetterCollection<QonfigChildDef> elRoles = ((QonfigElementDef) roleDef.declaredElement).getChildrenByName()
								.get(roleDef.itemName);
							if (elRoles.isEmpty()) {
								errors.error(
									"Element " + roleDef.declaredElement + " does not declare or inherit role " + roleDef.itemName, null);
								continue;
							} else if (elRoles.size() > 1) {
								errors.error(
									"Element " + roleDef.declaredElement + " inherits multiple roles named " + roleDef.itemName, null);
								continue;
							} else
								role = elRoles.getFirst();
						}
						roles.add(role);
						realRoles.add(role.getDeclared());
					} else {
						role = null;
						BetterList<QonfigChildDef> inhRoles = (BetterList<QonfigChildDef>) theType.getChildrenByName()
							.get(roleDef.itemName);
						if (inhRoles.isEmpty())
							continue;
						else if (inhRoles.size() > 1) {
							errors.error("Multiple roles named " + roleDef.itemName + " found", null);
							continue roleLoop;
						} else
							role = inhRoles.getFirst();
						if (role == null)
							errors.error("No such role \"" + roleDef.itemName + "\" found", null);
						else {
							roles.add(role);
							realRoles.add(role.getDeclared());
						}
					}
				}
			} else { // Alright, we have to guess
				QonfigChildDef role = null;
				for (Map.Entry<QonfigChildDef.Declared, QonfigChildDef> childDef : theType.getAllChildren().entrySet()) {
					if (childDef.getValue().isCompatible(type)) {
						if (role != null) {
							errors.error("Child of type " + type + " is compatible with multiple roles--role must be specified");
							return this;
						}
						role = childDef.getValue();
					}
				}
				for (QonfigAddOn inh : theInheritance.getExpanded(QonfigAddOn::getInheritance)) {
					for (Map.Entry<String, QonfigChildDef.Declared> childDef : inh.getDeclaredChildren().entrySet()) {
						if (childDef.getValue().isCompatible(type)) {
							if (role == null)
								role = childDef.getValue();
							else if (!role.getDeclared().equals(childDef.getValue()))
								throw new IllegalArgumentException("Child of type " + type + " is compatible with multiple roles: " + role
									+ " and " + childDef.getValue() + "--role must be specified");
						}
					}
				}
				if (role == null) {
					errors.error("Child of type " + type + " is not compatible with any roles of parent " + theType);
					return this;
				}
				roles.add(role);
				realRoles.add(role.getDeclared());
			}
			Set<QonfigChildDef> children = new HashSet<>();
			for (QonfigChildDef role : roles) {
				if (role instanceof QonfigChildDef.Overridden)
					errors.error("Role " + role.getDeclared() + " is overridden by "
						+ ((QonfigChildDef.Overridden) role).getOverriding() + " and cannot be fulfilled directly");
				QonfigChildDef ch = theType.getAllChildren().get(role);
				if (ch != null)
					children.add(ch);
				else
					children.add(role);
			}
			Builder childBuilder = new Builder(errors, theDocument, theElement, type, Collections.unmodifiableSet(roles),
				Collections.unmodifiableSet(realRoles), description);
			child.accept(childBuilder);
			QonfigElement builtChild = childBuilder.build();
			theChildren.add(builtChild);
			for (QonfigChildDef.Declared role : realRoles)
				theChildrenByRole.add(role, builtChild);
			return this;
		}

		private void create() {
			if (theStage > 0)
				return;
			// Since attribute values can affect inheritance, we need to iterate through this loop as long as inheritance keeps changing
			Map<QonfigAttributeDef.Declared, QonfigValue> attrValues = new LinkedHashMap<>();
			MultiInheritanceSet<QonfigAddOn> completeInheritance = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
			completeInheritance.addAll(theType.getInheritance());
			completeInheritance.addAll(theInheritance.values());
			BitSet parsedAttrs = new BitSet(theDeclaredAttributes.size());
			boolean inheritanceChanged = true;
			// Primary attribute parsing. Since values can confer inheritance, which can affect attributes,
			// we need to go through all declared attributes, parsing those we understand, ignoring those we don't,
			// and updating inheritance.
			// Stop only after we don't understand anymore attributes
			while (inheritanceChanged && parsedAttrs.cardinality() < theDeclaredAttributes.size()) {
				inheritanceChanged = false;
				for (int i = parsedAttrs.nextClearBit(0); i >= 0; i = parsedAttrs.nextClearBit(i + 1)) {
					if (i >= theDeclaredAttributes.size())
						break;
					ElementQualifiedParseItem attrDef = theDeclaredAttributes.get(i);
					QonfigAttributeDef attr;
					BetterCollection<QonfigAttributeDef> attrs = null;
					if (attrDef.declaredElement != null) {
						if (!attrDef.declaredElement.isAssignableFrom(theType) && (!(attrDef.declaredElement instanceof QonfigAddOn)
							|| !completeInheritance.contains((QonfigAddOn) attrDef.declaredElement))) {
							theErrors.error("Element does not inherit element " + attrDef.declaredElement + "--cannot specify attribute "
								+ attrDef.itemName);
							parsedAttrs.set(i);
							continue;
						}
						// We already checked in withAttribute() to make sure this is valid
						attr = attrDef.declaredElement.getDeclaredAttributes().get(attrDef.itemName);
						if (attr == null)
							attrs = attrDef.declaredElement.getAttributesByName().get(attrDef.itemName);
					} else {
						attr = theType.getDeclaredAttributes().get(attrDef.itemName);
						if (attr == null) {
							attrs = BetterHashSet.build().build();
							for (QonfigAttributeDef inhAtt : theType.getAttributesByName().get(attrDef.itemName))
								attrs.add(inhAtt.getDeclared());
							for (QonfigAddOn inh : theInheritance.values()) {
								for (QonfigAttributeDef inhAtt : inh.getAttributesByName().get(attrDef.itemName))
									attrs.add(inhAtt.getDeclared());
							}
						}
					}
					if (attr == null) {
						switch (attrs.size()) {
						case 1:
							attr = attrs.getFirst();
							break;
						case 0:
							// May be parseable after inheritance is more filled in
							continue;
						default:
							boolean resolved = true;
							for (QonfigAttributeDef a : attrs) {
								if (attr == null)
									attr = a;
								else if (a.getDeclared() != attr.getDeclared()) {
									resolved = false;
									break;
								} else if (attr.getOwner().isAssignableFrom(a.getOwner()))
									attr = a;
							}
							if (!resolved) {
								theErrors.error("Multiple matching attributes inherited");
								continue;
							}
							break;
						}
					}
					parsedAttrs.set(i);
					if (attrValues.containsKey(attr.getDeclared())) {
						theErrors.error("Duplicate values supplied for attribute " + attrDef.itemName, null);
					}
					AttributeValue attrValue = theDeclaredAttributeValues.get(i);
					Object value;
					try {
						value = attrValue.parseAttributeValue(theDocument.getDocToolkit(), attr, theErrors.at(attrValue.getPosition()));
					} catch (RuntimeException e) {
						theErrors.error("Could not parse attribute " + theDeclaredAttributeValues.get(i).toString(), e);
						continue;
					}
					attrValues.put(attr.getDeclared(),
						new QonfigValue(attrValue.getText(), value, theDocument.getLocation(), attrValue.getPosition()));
					if (value != null) {
						if (attr.getType() instanceof QonfigAddOn) {
							if (completeInheritance.add((QonfigAddOn) value)) {
								inheritanceChanged = true;
								theInheritance.add((QonfigAddOn) value);
							}
						}
					}
				}
			}
			// Now we understand all the attributes we can. Make errors for those we don't.
			for (int i = parsedAttrs.nextClearBit(0); i >= 0; i = parsedAttrs.nextClearBit(i + 1)) {
				if (i >= theDeclaredAttributes.size())
					break;
				theErrors.at(theDeclaredAttributes.get(i).position)
					.error("No such attribute found: '" + theDeclaredAttributes.get(i) + "'");
			}
			// Now that we know our inheritance completely, we need to check all the attributes we've parsed
			// to make sure they still match exactly one attribute definition.
			// We also need to verify that declared attributes satisfy all inherited constraints
			for (int i = 0; i < theDeclaredAttributes.size(); i++) {
				ElementQualifiedParseItem attrDef = theDeclaredAttributes.get(i);
				QonfigAttributeDef attr;
				BetterCollection<QonfigAttributeDef> attrs = null;
				if (attrDef.declaredElement != null) {
					// We already checked in withAttribute() to make sure this is valid
					attr = attrDef.declaredElement.getDeclaredAttributes().get(attrDef.itemName);
					if (attr == null)
						attrs = attrDef.declaredElement.getAttributesByName().get(attrDef.itemName);
				} else {
					attr = theType.getDeclaredAttributes().get(attrDef.itemName);
					if (attr == null) {
						attrs = BetterHashSet.build().build();
						attr = theType.getDeclaredAttributes().get(attrDef.itemName);
						if (attr != null) {
							attrs.add(attr);
							attr = null;
						}
					}
					if (attr == null && attrs.isEmpty()) {
						for (QonfigAddOn inh : theInheritance.values())
							attrs.addAll(inh.getAttributesByName().get(attrDef.itemName));
					}
				}
				if (attr == null) {
					switch (attrs.size()) {
					case 0:
						// Already caught and error logged
						continue;
					case 1:
						attr = attrs.getFirst();
						break;
					default:
						boolean resolved = true;
						for (QonfigAttributeDef a : attrs) {
							if (attr == null)
								attr = a;
							else if (a.getDeclared() != attr.getDeclared()) {
								resolved = false;
								break;
							} else if (attr.getOwner().isAssignableFrom(a.getOwner()))
								attr = a;
						}
						if (!resolved) {
							theErrors.error("Multiple matching attributes inherited");
							continue;
						}
					}
				}
				Object value = attrValues.get(attr.getDeclared());
				ValueDefModifier mod = theType.getAttributeModifiers().get(attr.getDeclared());
				if (mod != null) {
					if (mod.getTypeRestriction() != null && !mod.getTypeRestriction().isInstance(value))
						theErrors.error("Type of value " + value + " does not match that required by " + theType + " ("
							+ mod.getTypeRestriction() + ")");
					if (mod.getSpecification() == SpecificationType.Forbidden)
						theErrors.error("Specification of value for attribute forbidden by type " + theType);
				}
				for (QonfigAddOn inh : theInheritance.getExpanded(QonfigAddOn::getInheritance)) {
					if (theType.isAssignableFrom(inh))
						continue;
					mod = inh.getAttributeModifiers().get(attr.getDeclared());
					if (mod != null) {
						if (mod.getTypeRestriction() != null && !mod.getTypeRestriction().isInstance(value))
							theErrors.error("Type of value " + value
								+ " does not match that required by " + inh + " (" + mod.getTypeRestriction() + ")");
						if (mod.getSpecification() == SpecificationType.Forbidden)
							theErrors.error("Specification of value for attribute forbidden by type " + inh);
					}
				}
			}
			// Now populate default values for optional/forbidden attributes
			Map<QonfigAttributeDef.Declared, Boolean> defaultedAttributes = new HashMap<>();
			for (QonfigAddOn inh : completeInheritance.getExpanded(QonfigAddOn::getInheritance)) {
				for (QonfigAttributeDef.Declared attr : inh.getDeclaredAttributes().values()) {
					if (attrValues.get(attr) == null && attr.getSpecification() != SpecificationType.Required) {
						Object defValue = attr.getDefaultValue();
						if (defValue != null) {
							attrValues.put(attr, new QonfigValue(defValue == null ? null : defValue.toString(), defValue,
								attr.getDeclarer().getLocationString(), null));
							defaultedAttributes.put(attr, attr.getSpecification() == SpecificationType.Forbidden);
						}
					}
				}
				for (Map.Entry<QonfigAttributeDef.Declared, ? extends ValueDefModifier> attr : inh.getAttributeModifiers().entrySet()) {
					if (attrValues.get(attr.getKey()) == null && attr.getValue().getSpecification() != SpecificationType.Required) {
						Object defValue = attr.getValue().getDefaultValue();
						attrValues.put(attr.getKey(), new QonfigValue(defValue == null ? null : defValue.toString(), defValue,
							attr.getValue().getDeclarer().getLocationString(), null));
						defaultedAttributes.put(attr.getKey(), attr.getValue().getSpecification() == SpecificationType.Forbidden);
					}
				}
			}
			for (Map.Entry<QonfigAttributeDef.Declared, QonfigAttributeDef> attr : theType.getAllAttributes().entrySet()) {
				if (attrValues.get(attr.getKey()) == null && attr.getValue().getSpecification() != SpecificationType.Required) {
					Object defValue = attr.getValue().getDefaultValue();
					if (defValue != null) {
						attrValues.put(attr.getKey(),
							new QonfigValue(defValue.toString(), defValue, attr.getValue().getDeclarer().getLocationString(), null));
						defaultedAttributes.put(attr.getKey(), attr.getValue().getSpecification() == SpecificationType.Forbidden);
					}
				}
			}
			// Now for defaulted values, verify that we don't inherit attribute specifications that conflict
			for (QonfigAddOn inh : theInheritance.getExpanded(QonfigAddOn::getInheritance)) {
				if (inh.isAssignableFrom(theType))
					continue;
				for (Map.Entry<QonfigAttributeDef.Declared, ? extends ValueDefModifier> mod : inh.getAttributeModifiers().entrySet()) {
					QonfigValue value = attrValues.get(mod.getKey());
					if (mod.getValue().getSpecification() != null) {
						switch (mod.getValue().getSpecification()) {
						case Required:
							break; // Can't require an attribute that doesn't start out that way
						case Forbidden:
							Boolean defaulted = defaultedAttributes.get(mod.getKey());
							if (value != null) {
								if (defaulted == null)
									theErrors.at(value.position)
										.error("Specification of value for attribute '" + mod.getKey() + "' forbidden by " + inh);
								else if (!Objects.equals(value.value, mod.getValue().getDefaultValue())) {
									if (defaulted) // Forbidden
										theErrors.error("Default values for forbidden attribute '" + mod.getKey()
											+ "' specified from multiple sources, including " + inh);
									else {
										defaultedAttributes.put(mod.getKey(), true);
										Object defValue = mod.getValue().getDefaultValue();
										attrValues.put(mod.getKey(), new QonfigValue(String.valueOf(defValue), defValue,
											mod.getValue().getDeclarer().getLocationString(), null));
									}
								}
							}
							break;
						case Optional:
							defaulted = defaultedAttributes.get(mod.getKey());
							if (value == null && defaulted == null) {
								Object defValue = mod.getValue().getDefaultValue();
								if (defValue != null) {
									attrValues.put(mod.getKey(), new QonfigValue(String.valueOf(defValue), defValue,
										mod.getValue().getDeclarer().getLocationString(), null));
									defaultedAttributes.put(mod.getKey(), false);
								}
							}
							break;
						}
					}
				}
			}
			for (Map.Entry<QonfigAttributeDef.Declared, QonfigAttributeDef> attr : theType.getAllAttributes().entrySet()) {
				if (attrValues.get(attr.getKey()) != null)
					continue;
				else if (attr.getValue().getSpecification() == SpecificationType.Required) {
					theErrors.error("Attribute " + attr.getKey() + " required by type " + theType);
					continue;
				}
				for (QonfigAddOn inh : completeInheritance.getExpanded(QonfigAddOn::getInheritance)) {
					if (!attr.getKey().getOwner().isAssignableFrom(inh))
						continue;
					QonfigAddOn.ValueModifier mod = inh.getAttributeModifiers().get(attr.getKey());
					if (mod != null && mod.getSpecification() == SpecificationType.Required) {
						theErrors.error("Attribute " + attr.getKey() + " required by type " + inh);
						break;
					}
				}
			}
			for (QonfigAddOn inh : completeInheritance.getExpanded(QonfigAddOn::getInheritance)) {
				if (inh.isAssignableFrom(theType))
					continue;
				for (QonfigAttributeDef.Declared attr : inh.getDeclaredAttributes().values()) {
					if (attrValues.get(attr) == null && attr.getSpecification() == SpecificationType.Required) {
						theErrors.error("Attribute " + attr + " required by type " + inh);
						break;
					}
				}
			}

			if (theValue != null) {
				// Check that the value satisfies all inherited constraints
				for (QonfigAddOn inh : theInheritance.getExpanded(QonfigAddOn::getInheritance)) {
					ValueDefModifier mod = inh.getValueModifier();
					if (mod == null)
						continue;
					if (mod.getTypeRestriction() != null && !mod.getTypeRestriction().isInstance(theValue))
						theErrors.error("Type of value " + theValue + " does not match that required by " + inh + " ("
							+ mod.getTypeRestriction() + ")");
					if (mod.getSpecification() == SpecificationType.Forbidden)
						theErrors.error("Specification of value forbidden by type " + inh);
				}
			} else {
				// Default the value if provided, checking for inherited specifications that conflict
				boolean forbidden = false;
				QonfigElementOrAddOn required = null;
				if (theType.getValue() != null) {
					if (theType.getValue().getDefaultValue() != null) {
						Object defValue = theType.getValue().getDefaultValue();
						if (defValue != null) {
							theValue = new QonfigValue(String.valueOf(defValue), defValue, theType.getDeclarer().getLocationString(), null);
							forbidden = theType.getValue().getSpecification() == SpecificationType.Forbidden;
						}
					}
					required = theType.getValue().getSpecification() == SpecificationType.Required ? theType : null;
				}
				for (QonfigAddOn inh : theInheritance.getExpanded(QonfigAddOn::getInheritance)) {
					ValueDefModifier mod = inh.getValueModifier();
					if (mod == null)
						continue;
					if (required == null && mod.getSpecification() == SpecificationType.Required)
						required = inh;
					if (mod.getSpecification() == SpecificationType.Forbidden && mod.getDefaultValue() != null) {
						if (forbidden && !Objects.equals(theValue, mod.getDefaultValue()))
							theErrors.error("Default values forbidden and specified from multiple sources, including " + inh);
						Object defValue = mod.getDefaultValue();
						theValue = new QonfigValue(String.valueOf(defValue), defValue, mod.getDeclarer().getLocationString(), null);
					}
					if (mod.getDefaultValue() != null && theValue == null) {
						Object defValue = mod.getDefaultValue();
						theValue = new QonfigValue(String.valueOf(defValue), defValue, mod.getDeclarer().getLocationString(), null);
					}
					forbidden = mod.getSpecification() == SpecificationType.Forbidden;
				}
				if (required != null && theValue == null)
					theErrors.error("Value required by " + required);
			}

			theElement = new QonfigElement(theDocument, theParent, theType, theInheritance, theParentRoles, theDeclaredRoles, attrValues,
				theChildren, theChildrenByRole, theValue, theErrors.getFileLocation(), theDescription);

			theStage = 1;
		}

		/** @return The built element */
		public QonfigElement build() {
			if (theStage == 0)
				create();
			if (theStage < 2) {
				// TODO Verify children
				theStage = 2;
			}
			return theElement;
		}

		@Override
		public String toString() {
			if (theElement != null)
				return theElement.toString();
			else
				return theType.getName();
		}
	}
}

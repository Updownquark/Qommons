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
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMultiMap;

/** An element in a Qonfig document */
public class QonfigElement {
	private final QonfigDocument theDocument;
	private final QonfigElement theParent;
	private final QonfigElementDef theType;
	private final MultiInheritanceSet<QonfigAddOn> theInheritance;
	private final Set<QonfigChildDef.Declared> theParentRoles;
	private final Map<QonfigAttributeDef.Declared, Object> theAttributes;
	private final List<QonfigElement> theChildren;
	private final BetterMultiMap<QonfigChildDef.Declared, QonfigElement> theChildrenByRole;
	private final Object theValue;

	private QonfigElement(QonfigDocument doc, QonfigElement parent, QonfigElementDef type, MultiInheritanceSet<QonfigAddOn> inheritance,
		Set<QonfigChildDef.Declared> parentRoles, Map<QonfigAttributeDef.Declared, Object> attributes, List<QonfigElement> children,
		BetterMultiMap<QonfigChildDef.Declared, QonfigElement> childrenByRole, Object value) {
		if (doc.getRoot() == null)
			doc.setRoot(this);
		theDocument = doc;
		theParent = parent;
		theType = type;
		theInheritance = inheritance;
		theParentRoles = parentRoles;
		theAttributes = attributes;
		theChildren = children;
		theChildrenByRole = childrenByRole;
		theValue = value;
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
	public Set<QonfigChildDef.Declared> getParentRoles() {
		return theParentRoles;
	}

	/** @return All add-ons that this element inherits */
	public MultiInheritanceSet<QonfigAddOn> getInheritance() {
		return theInheritance;
	}

	/**
	 * @param el The element-def or add-on to test
	 * @return Whether this element extends or inherits the given element-def or add-on
	 */
	public boolean isInstance(QonfigElementOrAddOn el) {
		if (el instanceof QonfigElementDef)
			return theType.isAssignableFrom(el);
		for (QonfigAddOn inh : theInheritance.values())
			if (inh.isAssignableFrom(el))
				return true;
		return false;
	}

	/**
	 * @param addOn The add-on to test
	 * @return Whether this element inherits the given add-on
	 */
	public boolean isInstance(QonfigAddOn addOn) {
		for (QonfigAddOn inh : theInheritance.values())
			if (inh.isAssignableFrom(addOn))
				return true;
		return false;
	}

	/** @return The values of all attributes specified for this element */
	public Map<QonfigAttributeDef.Declared, Object> getAttributes() {
		return theAttributes;
	}

	/**
	 * @param name The name of the attribute to get
	 * @return The value of the given attribute, or null if it was not specified
	 * @throws IllegalArgumentException If no attribute with the given name is defined for this element's extended/inherited types, or if
	 *         multiple such attributes are defined
	 */
	public Object getAttribute(String name) throws IllegalArgumentException {
		QonfigAttributeDef def = theType.getDeclaredAttributes().get(name);
		if (def == null) {
			switch (theType.getAttributesByName().get(name).size()) {
			case 0:
				break;
			case 1:
				def = theType.getAttributesByName().get(name).getFirst();
				break;
			default:
				throw new IllegalArgumentException(theType + ": Multiple attributes named '" + name + "' defined");
			}
		}
		if (def == null) {
			for (QonfigAddOn inh : theInheritance.values()) {
				switch (inh.getAttributesByName().get(name).size()) {
				case 0:
					break;
				case 1:
					if (def != null)
						throw new IllegalArgumentException(theType + ": Multiple attributes named '" + name + "' defined");
					def = inh.getAttributesByName().get(name).getFirst();
					break;
				default:
					throw new IllegalArgumentException(theType + ": Multiple attributes named '" + name + "' defined");
				}
			}
		}
		if (def == null)
			throw new IllegalArgumentException(theType + ": No such attribute '" + name + "' defined");
		return theAttributes.get(def.getDeclared());
	}

	/**
	 * @param <T> The type of the attribute
	 * @param name The name of the attribute to get
	 * @param type The type of the attribute
	 * @return The value of the given attribute, or null if it was not specified
	 * @throws IllegalArgumentException If no attribute with the given name is defined for this element's extended/inherited types, or if
	 *         multiple such attributes are defined
	 * @throws ClassCastException If the attribute was specified, but is not of the given type
	 */
	public <T> T getAttribute(String name, Class<T> type) throws IllegalArgumentException, ClassCastException {
		Object value = getAttribute(name);
		if (value == null)
			return null;
		else if (!type.isInstance(value))
			throw new ClassCastException(
				theType + ": Value " + value + ", specification " + value.getClass().getName() + " cannot be cast to " + type.getName());
		return (T) value;
	}

	/**
	 * @param name The name of the attribute to get
	 * @return The text of the given attribute, or null if it was not specified
	 * @throws IllegalArgumentException If no attribute with the given name is defined for this element's extended/inherited types, or if
	 *         multiple such attributes are defined
	 */
	public String getAttributeText(String name) throws IllegalArgumentException {
		Object value = getAttribute(name);
		if (value == null)
			return null;
		return value.toString();
	}

	/** @return All children specified for the element */
	public List<QonfigElement> getChildren() {
		return theChildren;
	}

	/** @return Children specified for the element, grouped by role */
	public BetterMultiMap<QonfigChildDef.Declared, QonfigElement> getChildrenByRole() {
		return theChildrenByRole;
	}

	/**
	 * @param roleName The name of the role to get the children for
	 * @return All children specified for this element fulfilling the given role
	 * @throws IllegalArgumentException If no child with the given name is defined for this element's extended/inherited types, or if
	 *         multiple such children are defined
	 */
	public BetterList<QonfigElement> getChildrenInRole(String roleName) {
		QonfigChildDef def = theType.getDeclaredChildren().get(roleName);
		if (def == null) {
			switch (theType.getChildrenByName().get(roleName).size()) {
			case 0:
				break;
			case 1:
				def = theType.getChildrenByName().get(roleName).getFirst();
				break;
			default:
				throw new IllegalArgumentException(theType + ": Multiple children named '" + roleName + "' defined");
			}
		}
		if (def == null) {
			for (QonfigAddOn inh : theInheritance.values()) {
				switch (inh.getChildrenByName().get(roleName).size()) {
				case 0:
					break;
				case 1:
					if (def != null)
						throw new IllegalArgumentException(theType + ": Multiple children named '" + roleName + "' defined");
					def = inh.getChildrenByName().get(roleName).getFirst();
					break;
				default:
					throw new IllegalArgumentException(theType + ": Multiple children named '" + roleName + "' defined");
				}
			}
		}
		if (def == null)
			throw new IllegalArgumentException(theType + ": No such child '" + roleName + "' defined");
		return (BetterList<QonfigElement>) theChildrenByRole.get(def.getDeclared());
	}

	/** @return The value specified for this element */
	public Object getValue() {
		return theValue;
	}

	/** @return The value text specified for this element */
	public String getValueText() {
		return theValue == null ? null : theValue.toString();
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder().append('<').append(theType.getName());
		for (Map.Entry<QonfigAttributeDef.Declared, Object> attr : theAttributes.entrySet()) {
			if (attr.getValue() != null)
				str.append(' ').append(attr.getKey()).append("=\"").append(attr.getValue()).append('"');
		}
		return str.append('>').toString();
	}

	/**
	 * Builds an element
	 * 
	 * @param session The session for error logging
	 * @param doc The document being parsed
	 * @param parent The parent for the new element
	 * @param type The declared type of the element
	 * @return A builder for an element
	 */
	public static Builder build(QonfigParseSession session, QonfigDocument doc, QonfigElement parent, QonfigElementDef type) {
		return new Builder(session, doc, parent, type, Collections.emptySet());
	}

	/** Represents an attribute value before it is parsed */
	public static interface AttributeValue {
		/**
		 * Parses the attribute value
		 * 
		 * @param session The session to log errors
		 * @param attribute The attribute to parse the value for
		 * @return The parsed value
		 */
		Object parseAttributeValue(QonfigParseSession session, QonfigAttributeDef attribute);
	}

	/** Builds a QonfigElement */
	public static class Builder {
		private final QonfigParseSession theSession;
		private final QonfigDocument theDocument;
		private final QonfigElement theParent;
		private final QonfigElementDef theType;
		private final MultiInheritanceSet<QonfigAddOn> theInheritance;
		private final List<ElementQualifiedParseItem> theDeclaredAttributes;
		private final List<AttributeValue> theDeclaredAttributeValues;
		private final Set<QonfigChildDef.Declared> theParentRoles;
		private final List<QonfigElement> theChildren;
		private final BetterMultiMap<QonfigChildDef.Declared, QonfigElement> theChildrenByRole;
		private Object theValue;
		private QonfigElement theElement;
		private int theStage;
		private int theChildCount;

		Builder(QonfigParseSession session, QonfigDocument doc, QonfigElement parent, QonfigElementDef type,
			Set<QonfigChildDef.Declared> parentRoles) {
			if (type.isAbstract())
				session.withError("Elements cannot be declared directly for abstract type " + type);
			theSession = session;
			theDocument = doc;
			theType = type;
			theParent = parent;
			theParentRoles = parentRoles;
			theDeclaredAttributes = new ArrayList<>();
			theDeclaredAttributeValues = new ArrayList<>();
			theChildren = new ArrayList<>();
			theChildrenByRole = BetterHashMultiMap.<QonfigChildDef.Declared, QonfigElement> build().safe(false).buildMultiMap();
			theInheritance = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
			for (QonfigChildDef.Declared role : parentRoles) {
				QonfigChildDef child = parent.getType().getAllChildren().get(role);
				if (child != null) {
					if (!child.getType().isAssignableFrom(type))
						session.withError(
							"This element (" + theType + ") does not inherit " + child.getType() + "--cannot fulfill role " + child);
					theInheritance.addAll(child.getInheritance());
				} else {
					if (!role.getType().isAssignableFrom(type))
						session.withError(
							"This element (" + theType + ") does not inherit " + role.getType() + "--cannot fulfill role " + child);
					theInheritance.addAll(role.getInheritance());
				}
				for (QonfigAddOn inh : parent.getInheritance().getExpanded(QonfigAddOn::getInheritance)) {
					ChildDefModifier mod = inh.getChildModifiers().get(role);
					if (mod != null) {
						if (mod.getTypeRestriction() != null && !mod.getTypeRestriction().isAssignableFrom(type))
							session.withError("This element (" + theType + ") does not inherit " + mod.getTypeRestriction()
								+ " specified by inheritance " + inh + "--cannot fulfill role " + role);
						theInheritance.addAll(mod.getInheritance());
					}
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
				theSession.withError("Add-on " + addOn + " is abstract and cannot be applied directly");
				ok = false;
			}
			if (addOn.getSuperElement() != null && !addOn.getSuperElement().isAssignableFrom(theType)) {
				theSession.withError(
					"Add-on " + addOn + " requires " + addOn.getSuperElement() + ", which " + theType + " does not extend");
				ok = false;
			}
			for (QonfigAddOn inh : addOn.getFullInheritance().getExpanded(QonfigAddOn::getInheritance)) {
				if (inh.getSuperElement() != null && !inh.getSuperElement().isAssignableFrom(theType)) {
					theSession.withError(
						"Add-on " + addOn + " requires " + addOn.getSuperElement() + ", which " + theType + " does not extend");
					ok = false;
				}
			}
			if (ok)
				theInheritance.add(addOn);
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
					theSession.forChild("attribute", attr.itemName)
						.withError("Element " + attr.declaredElement + " does not declare attribute \"" + attr.itemName + "\"", null);
					return this;
				} else if (count > 1) {
					theSession.forChild("attribute", attr.itemName).withError(
						"Element " + attr.declaredElement + " inherits multiple attributes named \"" + attr.itemName + "\"", null);
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
		 * @param value The value for this element
		 * @return This builder
		 */
		public Builder withValue(Object value) {
			if (theStage > 0)
				throw new IllegalStateException("Cannot specify text after children");
			else if (theValue != null)
				throw new IllegalStateException("Cannot specify value twice");
			theValue = value;
			return this;
		}

		/**
		 * @param declaredRoles The role specifications that the child is declared to fulfill
		 * @param type The declared type of the element
		 * @param child Consumer to configure the child element
		 * @return This builder
		 */
		public Builder withChild(List<ElementQualifiedParseItem> declaredRoles, QonfigElementDef type,
			Consumer<QonfigElement.Builder> child) {
			if (theStage > 1)
				throw new IllegalStateException("Cannot add children after the element has been built");
			// At this stage, we have all the information we need to determine the complete inheritance of the element
			create();
			QonfigParseSession session = theSession.forChild(type.toString(), theChildCount);
			Set<QonfigChildDef.Declared> realRoles = new LinkedHashSet<>(declaredRoles.size() * 3 / 2 + 1);
			if (!declaredRoles.isEmpty()) {
				roleLoop: //
				for (ElementQualifiedParseItem roleDef : declaredRoles) {
					QonfigChildDef.Declared role;
					if (roleDef.declaredElement != null) {
						if (!(roleDef.declaredElement instanceof QonfigElementDef)) {
							session.withError(roleDef.printQualifier() + " is an add-on--roles must be qualified with element-defs");
							continue;
						} else if (theParent == null) {
							session.withError("Cannot declare a role on the root element: " + roleDef, null);
							continue;
						} else if (!theParent.isInstance(roleDef.declaredElement)) {
							session.withError(
								"Parent does not inherit element " + roleDef.declaredElement + "--cannot declare a child with role "
									+ roleDef,
								null);
							continue;
						}
						role = ((QonfigElementDef) roleDef.declaredElement).getDeclaredChildren().get(roleDef.itemName);
						if (role == null) {
							BetterCollection<QonfigChildDef> elRoles = ((QonfigElementDef) roleDef.declaredElement).getChildrenByName()
								.get(roleDef.itemName);
							if (elRoles.isEmpty()) {
								session.withError(
									"Element " + roleDef.declaredElement + " does not declare or inherit role " + roleDef.itemName, null);
								continue;
							} else if (elRoles.size() > 1) {
								session.withError(
									"Element " + roleDef.declaredElement + " inherits multiple roles named " + roleDef.itemName, null);
								continue;
							} else
								role = elRoles.getFirst().getDeclared();
						}
						realRoles.add(role);
					} else {
						role = null;
						BetterList<QonfigChildDef> inhRoles = (BetterList<QonfigChildDef>) theType.getChildrenByName()
							.get(roleDef.itemName);
						if (inhRoles.isEmpty())
							continue;
						else if (inhRoles.size() > 1) {
							session.withError("Multiple roles named " + roleDef.itemName + " found", null);
							continue roleLoop;
						} else
							role = inhRoles.getFirst().getDeclared();
						if (role == null)
							session.withError("No such role \"" + roleDef.itemName + "\" found", null);
						realRoles.add(role);
					}
				}
			} else { // Alright, we have to guess
				QonfigChildDef.Declared role = null;
				for (Map.Entry<QonfigChildDef.Declared, QonfigChildDef> childDef : theType.getAllChildren().entrySet()) {
					if (childDef.getValue().getType().isAssignableFrom(type)) {
						if (role != null) {
							session.withError("Child of type " + type + " is compatible with multiple roles--role must be specified");
							return this;
						}
						role = childDef.getKey();
					}
				}
				for (QonfigAddOn inh : theInheritance.getExpanded(QonfigAddOn::getInheritance)) {
					for (Map.Entry<String, QonfigChildDef.Declared> childDef : inh.getDeclaredChildren().entrySet()) {
						if (childDef.getValue().getType().isAssignableFrom(type)) {
							if (role != null && !role.equals(childDef.getValue()))
								throw new IllegalArgumentException(
									"Child of type " + type + " is compatible with multiple roles--role must be specified");
							role = childDef.getValue();
						}
					}
				}
				if (role == null) {
					session.withError("Child of type " + type + " is not compatible with any roles of parent " + theType);
					return this;
				}
				realRoles.add(role);
			}
			Set<QonfigChildDef> children = new HashSet<>();
			for (QonfigChildDef.Declared role : realRoles) {
				if (role instanceof QonfigChildDef.Overridden)
					session.withError("Role " + role.getDeclared() + " is overridden by "
						+ ((QonfigChildDef.Overridden) role).getOverriding() + " and cannot be fulfilled directly");
				QonfigChildDef ch = theType.getAllChildren().get(role);
				if (ch != null)
					children.add(ch);
				else
					children.add(role);
			}
			Builder childBuilder = new Builder(session, theDocument, theElement, type, Collections.unmodifiableSet(realRoles));
			child.accept(childBuilder);
			QonfigElement builtChild = childBuilder.build();
			theChildren.add(builtChild);
			for (QonfigChildDef.Declared role : realRoles)
				theChildrenByRole.add(role, builtChild);
			theChildCount++;
			return this;
		}

		private void create() {
			if (theStage > 0)
				return;
			// Since attribute values can affect inheritance, we need to iterate through this loop as long as inheritance keeps changing
			Map<QonfigAttributeDef.Declared, Object> attrValues = new LinkedHashMap<>();
			theElement = new QonfigElement(theDocument, theParent, theType, theInheritance, theParentRoles, attrValues, theChildren,
				theChildrenByRole, theValue);
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
							theSession.forChild("attribute", attrDef.itemName).withError("Element does not inherit element "
								+ attrDef.declaredElement + "--cannot specify attribute " + attrDef.itemName);
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
							attrs = BetterHashSet.build().unsafe().buildSet();
							attrs.addAll(theType.getAttributesByName().get(attrDef.itemName));
							for (QonfigAddOn inh : completeInheritance.getExpanded(QonfigAddOn::getInheritance)) {
								attr = inh.getDeclaredAttributes().get(attrDef.itemName);
								if (attr != null) {
									attrs.add(attr);
									attr = null;
								}
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
							parsedAttrs.set(i);
							theSession.forChild("attribute", attrDef.toString()).withError("Multiple matching attributes inherited");
							continue;
						}
					}
					parsedAttrs.set(i);
					if (attrValues.containsKey(attr.getDeclared())) {
						theSession.forChild("attribute", attrDef.itemName)
							.withError("Duplicate values supplied for attribute " + attrDef.itemName, null);
					}
					Object value;
					try {
						value = theDeclaredAttributeValues.get(i).parseAttributeValue(theSession, attr);
					} catch (RuntimeException e) {
						theSession.forChild("attribute", attrDef.itemName)
							.withError("Could not parse attribute " + theDeclaredAttributeValues.get(i).toString(), e);
						continue;
					}
					attrValues.put(attr.getDeclared(), value);
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
				ElementQualifiedParseItem attrDef = theDeclaredAttributes.get(i);
				theSession.forChild("attribute", attrDef.toString()).withError("No such attribute found");
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
						attrs = BetterHashSet.build().unsafe().buildSet();
						attr = theType.getDeclaredAttributes().get(attrDef.itemName);
						if (attr != null) {
							attrs.add(attr);
							attr = null;
						}
					}
					if (attr == null && attrs.isEmpty()) {
						for (QonfigAddOn inh : completeInheritance.getExpanded(QonfigAddOn::getInheritance)) {
							attr = inh.getDeclaredAttributes().get(attrDef.itemName);
							if (attr != null) {
								attrs.add(attr);
								attr = null;
							}
						}
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
						theSession.forChild("attribute", attrDef.toString()).withError("Multiple matching attributes inherited");
						continue;
					}
				}
				Object value = attrValues.get(attr.getDeclared());
				ValueDefModifier mod = theType.getAttributeModifiers().get(attr.getDeclared());
				if (mod != null) {
					if (mod.getTypeRestriction() != null && !mod.getTypeRestriction().isInstance(value))
						theSession.forChild("attribute", attrDef.toString()).withError("Type of value " + value
							+ " does not match that required by " + theType + " (" + mod.getTypeRestriction() + ")");
					if (mod.getSpecification() == SpecificationType.Forbidden)
						theSession.forChild("attribute", attrDef.toString())
							.withError("Specification of value for attribute forbidden by type " + theType);
				}
				for (QonfigAddOn inh : theInheritance.getExpanded(QonfigAddOn::getInheritance)) {
					if (theType.isAssignableFrom(inh))
						continue;
					mod = inh.getAttributeModifiers().get(attr.getDeclared());
					if (mod != null) {
						if (mod.getTypeRestriction() != null && !mod.getTypeRestriction().isInstance(value))
							theSession.forChild("attribute", attrDef.toString()).withError("Type of value " + value
								+ " does not match that required by " + inh + " (" + mod.getTypeRestriction() + ")");
						if (mod.getSpecification() == SpecificationType.Forbidden)
							theSession.forChild("attribute", attrDef.toString())
								.withError("Specification of value for attribute forbidden by type " + inh);
					}
				}
			}
			// Now populate default values for optional/forbidden attributes
			Map<QonfigAttributeDef.Declared, Boolean> defaultedAttributes = new HashMap<>();
			for (Map.Entry<QonfigAttributeDef.Declared, QonfigAttributeDef> attr : theType.getAllAttributes().entrySet()) {
				if (!attrValues.containsKey(attr.getKey())) {
					switch (attr.getValue().getSpecification()) {
					case Required:
						break;
					default:
						attrValues.put(attr.getKey(), attr.getValue().getDefaultValue());
						defaultedAttributes.put(attr.getKey(), attr.getValue().getSpecification() == SpecificationType.Forbidden);
						break;
					}
				}
			}
			for (QonfigAddOn inh : completeInheritance.getExpanded(QonfigAddOn::getInheritance)) {
				if (theType.isAssignableFrom(inh))
					continue;
				for (QonfigAttributeDef.Declared attr : inh.getDeclaredAttributes().values()) {
					if (!attrValues.containsKey(attr)) {
						switch (attr.getSpecification()) {
						case Required:
							break;
						default:
							attrValues.put(attr, attr.getDefaultValue());
							defaultedAttributes.put(attr, attr.getSpecification() == SpecificationType.Forbidden);
							break;
						}
					}
				}
				for (Map.Entry<QonfigAttributeDef.Declared, ? extends ValueDefModifier> attr : inh.getAttributeModifiers().entrySet()) {
					if (!attrValues.containsKey(attr.getKey())) {
						switch (attr.getValue().getSpecification()) {
						case Required:
							break;
						default:
							attrValues.put(attr.getKey(), attr.getValue().getDefaultValue());
							defaultedAttributes.put(attr.getKey(), attr.getValue().getSpecification() == SpecificationType.Forbidden);
							break;
						}
					}
				}
			}
			// Now for defaulted values, verify that we don't inherit attribute specifications that conflict
			for (QonfigAddOn inh : theInheritance.getExpanded(QonfigAddOn::getInheritance)) {
				if (inh.isAssignableFrom(theType))
					continue;
				for (Map.Entry<QonfigAttributeDef.Declared, ? extends ValueDefModifier> mod : inh.getAttributeModifiers().entrySet()) {
					Object value = attrValues.get(mod.getKey());
					if (mod.getValue().getSpecification() != null) {
						switch (mod.getValue().getSpecification()) {
						case Required:
							break; // Can't require an attribute that doesn't start out that way
						case Forbidden:
							Boolean defaulted = defaultedAttributes.get(mod.getKey());
							if (value != null) {
								if (defaulted == null)
									theSession.forChild("attribute", mod.getKey().toString())
										.withError("Specification of value for attribute forbidden by " + inh);
								else if (!Objects.equals(value, mod.getValue().getDefaultValue())) {
									if (defaulted) // Forbidden
										theSession.forChild("attribute", mod.getKey().toString()).withError(
											"Default values for forbidden attribute specified from multiple sources, including " + inh);
									else {
										defaultedAttributes.put(mod.getKey(), true);
										attrValues.put(mod.getKey(), mod.getValue().getDefaultValue());
									}
								}
							}
							break;
						case Optional:
							defaulted = defaultedAttributes.get(mod.getKey());
							if (value == null && defaulted == null) {
								attrValues.put(mod.getKey(), mod.getValue().getDefaultValue());
								defaultedAttributes.put(mod.getKey(), false);
							}
							break;
						}
					}
				}
			}
			for (Map.Entry<QonfigAttributeDef.Declared, QonfigAttributeDef> attr : theType.getAllAttributes().entrySet()) {
				if (!attrValues.containsKey(attr.getKey()) && attr.getValue().getSpecification() == SpecificationType.Required) {
					theSession.withError("Attribute " + attr.getKey() + " required by type " + theType);
					break;
				}
			}
			for (QonfigAddOn inh : completeInheritance.getExpanded(QonfigAddOn::getInheritance)) {
				if (theType.isAssignableFrom(inh))
					continue;
				for (QonfigAttributeDef.Declared attr : inh.getDeclaredAttributes().values()) {
					if (!attrValues.containsKey(attr) && attr.getSpecification() == SpecificationType.Required) {
						theSession.withError("Attribute " + attr + " required by type " + theType);
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
						theSession.forChild("value", null).withError("Type of value " + theValue + " does not match that required by " + inh
							+ " (" + mod.getTypeRestriction() + ")");
					if (mod.getSpecification() == SpecificationType.Forbidden)
						theSession.forChild("value", null).withError("Specification of value forbidden by type " + inh);
				}
			} else {
				// Default the value if provided, checking for inherited specifications that conflict
				boolean forbidden = false;
				QonfigElementOrAddOn required = null;
				if (theType.getValue() != null) {
					if (theType.getValue().getDefaultValue() != null) {
						theValue = theType.getValue().getDefaultValue();
						forbidden = theType.getValue().getSpecification() == SpecificationType.Forbidden;
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
							theSession.forChild("value", null)
								.withError("Default values forbidden and specified from multiple sources, including " + inh);
						theValue = mod.getDefaultValue();
					}
					if (mod.getDefaultValue() != null && theValue == null)
						theValue = mod.getDefaultValue();
					forbidden = mod.getSpecification() == SpecificationType.Forbidden;
				}
				if (required != null && theValue == null)
					theSession.forChild("value", null).withError("Value required by " + required);
			}

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

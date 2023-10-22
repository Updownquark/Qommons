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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.qommons.MultiInheritanceSet;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMultiMap;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedPositionedContent;
import org.qommons.io.PositionedContent;

/** An element in a Qonfig document */
public final class QonfigElement extends PartialQonfigElement {
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

	public static class AttributeValue extends QonfigValue {
		private final PositionedContent theNamePosition;

		public AttributeValue(String text, Object value, String fileLocation, PositionedContent position, PositionedContent namePosition) {
			super(text, value, fileLocation, position);
			theNamePosition = namePosition;
		}

		public PositionedContent getNamePosition() {
			return theNamePosition;
		}
	}
	
	private QonfigElement(QonfigDocument doc, QonfigElement parent, QonfigElementDef type, MultiInheritanceSet<QonfigAddOn> inheritance,
		Set<QonfigChildDef> parentRoles, Set<QonfigChildDef.Declared> declaredRoles,
		Map<QonfigAttributeDef.Declared, AttributeValue> attributes, List<QonfigElement> children,
		BetterMultiMap<QonfigChildDef.Declared, QonfigElement> childrenByRole, QonfigValue value, LocatedPositionedContent filePosition,
		String description, QonfigElement promise, PartialQonfigElement externalContent) {
		super(doc, parent, type, inheritance, parentRoles, declaredRoles, attributes, children, childrenByRole, value, filePosition,
			description, promise, externalContent);
	}

	@Override
	public QonfigElement getParent() {
		return (QonfigElement) super.getParent();
	}

	@Override
	public QonfigElementDef getType() {
		return (QonfigElementDef) super.getType();
	}

	@Override
	public BetterList<QonfigElement> getChildrenInRole(QonfigToolkit toolkit, String elementOrAddOnName, String childName)
		throws IllegalArgumentException {
		return (BetterList<QonfigElement>) super.getChildrenInRole(toolkit, elementOrAddOnName, childName);
	}

	@Override
	public List<QonfigElement> getChildren() {
		return (List<QonfigElement>) super.getChildren();
	}

	@Override
	public BetterMultiMap<QonfigChildDef.Declared, QonfigElement> getChildrenByRole() {
		return (BetterMultiMap<QonfigChildDef.Declared, QonfigElement>) super.getChildrenByRole();
	}

	@Override
	public QonfigElement getPromise() {
		return (QonfigElement) super.getPromise();
	}

	/**
	 * Builds an element
	 * 
	 * @param reporting The session for error logging
	 * @param doc The document being parsed
	 * @param type The declared type of the element
	 * @param description A description for the element
	 * @return A builder for an element
	 */
	public static Builder buildRoot(boolean partial, ErrorReporting reporting, QonfigDocument doc, QonfigElementDef type,
		String description) {
		QonfigAutoInheritance.Compiler autoInheritance = new QonfigAutoInheritance.Compiler(Collections.singleton(doc.getDocToolkit()));
		autoInheritance.addTargetType(type, null);
		return new Builder(partial, reporting, doc, null, type, Collections.emptySet(), Collections.emptySet(), autoInheritance,
			description);
	}

	/** Represents an attribute value before it is parsed */
	public static interface AttributeValueInput {
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
		private final boolean isPartial;
		private final ErrorReporting theErrors;
		private QonfigDocument theDocument;
		private final PartialQonfigElement theParent;
		private final QonfigElementOrAddOn theType;
		private final MultiInheritanceSet<QonfigAddOn> theInheritance;
		private final QonfigAutoInheritance.Compiler theAutoInheritance;
		private final List<ElementQualifiedParseItem> theDeclaredAttributes;
		private final List<AttributeValueInput> theDeclaredAttributeValues;
		private final Map<QonfigAttributeDef.Declared, AttributeValue> theProvidedAttributes;
		private final Set<QonfigChildDef> theParentRoles;
		private final Set<QonfigChildDef.Declared> theDeclaredRoles;
		private final List<PartialQonfigElement> theChildren;
		private final BetterMultiMap<QonfigChildDef.Declared, PartialQonfigElement> theChildrenByRole;
		private final String theDescription;
		private QonfigValue theValue;

		private PartialQonfigElement theExternalContent;
		private PartialQonfigElement thePromise;

		private PartialQonfigElement theElement;
		private int theStage;

		Builder(boolean partial, ErrorReporting errors, QonfigDocument doc, PartialQonfigElement parent, QonfigElementOrAddOn type,
			Set<QonfigChildDef> parentRoles, Set<QonfigChildDef.Declared> declaredRoles, QonfigAutoInheritance.Compiler autoInheritance,
			String description) {
			isPartial = partial;
			if (!isPartial) {
				if (type.isAbstract())
					errors.error("Elements cannot be declared directly for abstract type " + type);
				if (parent != null && !(parent instanceof QonfigElement))
					throw new IllegalArgumentException("A fully-built element cannot have a partially-built parent");
				if (!(type instanceof QonfigElementDef))
					errors.error("Fully-built elements must declare an element-def as their type");
			}
			theErrors = errors;
			theDocument = doc;
			theType = type;
			theParent = parent;
			theParentRoles = parentRoles;
			theDeclaredRoles = declaredRoles;
			theDeclaredAttributes = new ArrayList<>();
			theDeclaredAttributeValues = new ArrayList<>();
			theProvidedAttributes = new LinkedHashMap<>();
			theChildren = new ArrayList<>();
			theChildrenByRole = BetterHashMultiMap.<QonfigChildDef.Declared, PartialQonfigElement> build().buildMultiMap();
			theInheritance = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);

			for (QonfigChildDef child : parentRoles) {
				if (child.getType() != null && !child.getType().isAssignableFrom(theType))
					errors.error("This element (" + theType + ") does not inherit " + child.getType() + "--cannot fulfill role " + child);
				theInheritance.addAll(child.getInheritance());
				for (QonfigAddOn inh : parent.getInheritance().getExpanded(QonfigAddOn::getInheritance)) {
					ChildDefModifier mod = inh.getChildModifiers().get(child.getDeclared());
					if (mod != null) {
						if (mod.getTypeRestriction() != null && !mod.getTypeRestriction().isAssignableFrom(theType))
							errors.error("This element (" + theType + ") does not inherit " + mod.getTypeRestriction()
								+ " specified by inheritance " + inh + "--cannot fulfill role " + child);
						theInheritance.addAll(mod.getInheritance());
					}
				}
			}
			theAutoInheritance = autoInheritance;
			theInheritance.addAll(theAutoInheritance.getInheritance().values());
			for (QonfigChildDef.Declared role : theDeclaredRoles)
				theAutoInheritance.addRole(role, theInheritance::add);
			theDescription = description;

			for (QonfigChildDef ch : parentRoles) {
				for (QonfigAddOn req : ch.getRequirement()) {
					if (!req.isAssignableFrom(type) && !theInheritance.contains(req))
						errors.error("Element " + type + " does not inherit " + req + ", which is required by role " + ch);
				}
			}
		}

		public QonfigDocument getDocument() {
			return theDocument;
		}

		/** @return The declared type of the element */
		public QonfigElementOrAddOn getType() {
			return theType;
		}

		public PartialQonfigElement getParent() {
			return theParent;
		}

		public boolean isPartial() {
			return isPartial;
		}

		public PartialQonfigElement getExternalContent() {
			return theExternalContent;
		}

		public PartialQonfigElement getPromise() {
			return thePromise;
		}

		MultiInheritanceSet<QonfigAddOn> getInheritance() {
			return theInheritance;
		}

		Set<QonfigChildDef> getParentRoles() {
			return theParentRoles;
		}

		Set<QonfigChildDef.Declared> getDeclaredRoles() {
			return theDeclaredRoles;
		}

		public ErrorReporting reporting() {
			return theErrors;
		}

		public Builder withDocument(QonfigDocument document) {
			theDocument = document;
			return this;
		}

		public Builder fulfills(PartialQonfigElement promise, PartialQonfigElement externalContent) {
			if (!isPartial && !(promise instanceof QonfigElement))
				throw new IllegalArgumentException("Full elements must have full promises");
			thePromise = promise;
			theExternalContent = externalContent;
			return this;
		}

		/**
		 * @param addOn An add-on directly declared for the element to inherit
		 * @return This builder
		 */
		public Builder inherits(QonfigAddOn addOn, boolean appliedDirect) {
			if (theStage > 0)
				throw new IllegalStateException("Cannot specify inheritance after children");
			boolean ok = true;
			if (appliedDirect && addOn.isAbstract()) {
				theErrors.error("Add-on " + addOn + " is abstract and cannot be applied directly");
				ok = false;
			}
			if (addOn.getSuperElement() != null && !addOn.getSuperElement().isAssignableFrom(theType)) {
				theErrors.error("Add-on " + addOn + " requires " + addOn.getSuperElement() + ", which " + theType + " does not extend");
				ok = false;
			}
			for (QonfigAddOn inh : addOn.getFullInheritance().getExpanded(QonfigAddOn::getInheritance)) {
				if (inh.getSuperElement() != null && !inh.getSuperElement().isAssignableFrom(theType)) {
					theErrors.error("Add-on " + addOn + " requires " + addOn.getSuperElement() + ", which " + theType + " does not extend");
					ok = false;
				}
			}
			if (ok) {
				theInheritance.add(addOn);
				theAutoInheritance.addTargetType(addOn, theInheritance::add);
			}
			return this;
		}

		/**
		 * @param attr The attribute specification
		 * @param value The attribute value
		 * @return This builder
		 */
		public Builder withAttribute(ElementQualifiedParseItem attr, AttributeValueInput value) {
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

		public Builder withAttribute(QonfigAttributeDef.Declared attr, AttributeValue value) {
			if (theStage > 0)
				throw new IllegalStateException("Cannot specify attributes after children");
			// Can't validate this yet because we don't know our full inheritance yet
			theProvidedAttributes.put(attr, value);
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

		public boolean isReadyForContent() {
			return theStage == 1;
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
		public Builder withChild(List<ElementQualifiedParseItem> declaredRoles, QonfigElementOrAddOn type,
			Consumer<QonfigElement.Builder> child, PositionedContent position, String description) {
			if (theStage > 1)
				throw new IllegalStateException("Cannot add children after the element has been built");
			// At this stage, we have all the information we need to determine the complete inheritance of the element
			create();
			ErrorReporting errors = theErrors.at(position);
			Set<QonfigChildDef> roles = new LinkedHashSet<>(declaredRoles.size() * 3 / 2 + 1);
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
								errors.error("Element " + roleDef.declaredElement + " does not declare or inherit role " + roleDef.itemName,
									null);
								continue;
							} else if (elRoles.size() > 1) {
								errors.error("Element " + roleDef.declaredElement + " inherits multiple roles named " + roleDef.itemName,
									null);
								continue;
							} else
								role = elRoles.getFirst();
						}
						roles.add(role);
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
						else
							roles.add(role);
					}
				}
				withChild2(Collections.unmodifiableSet(roles), type, child, position, description);
			} else
				withChild2(Collections.emptySet(), type, child, position, description);
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
		public Builder withChild2(Set<QonfigChildDef> declaredRoles, QonfigElementOrAddOn type, Consumer<QonfigElement.Builder> child,
			PositionedContent position, String description) {
			if (theStage > 1)
				throw new IllegalStateException("Cannot add children after the element has been built");
			// At this stage, we have all the information we need to determine the complete inheritance of the element
			create();
			ErrorReporting errors = theErrors.at(position);
			Set<QonfigChildDef> roles = new LinkedHashSet<>();
			Set<QonfigChildDef.Declared> realRoles = new LinkedHashSet<>();
			QonfigAutoInheritance.Compiler autoInheritance = new QonfigAutoInheritance.Compiler(
				Collections.singleton(theDocument.getDocToolkit()));
			autoInheritance.addParentType(theType, null);
			for (QonfigAddOn inh : theInheritance.values())
				autoInheritance.addParentType(inh, null);
			autoInheritance.addTargetType(type, null);
			if (!declaredRoles.isEmpty()) {
				for (QonfigChildDef role : declaredRoles) {
					boolean matches = role.getOwner().isAssignableFrom(theType);
					if (!matches) {
						for (QonfigAddOn inh : theInheritance.values()) {
							matches = role.getOwner().isAssignableFrom(inh);
							if (matches)
								break;
						}
					}
					if (!matches) {
						errors.error("Parent does not inherit element " + role.getOwner() + "--cannot declare a child with role " + role,
							null);
					} else {
						roles.add(role);
						realRoles.add(role.getDeclared());
					}
				}
			} else { // Alright, we have to guess
				QonfigChildDef role = null;
				for (Map.Entry<QonfigChildDef.Declared, QonfigChildDef> childDef : theType.getAllChildren().entrySet()) {
					if (type instanceof QonfigElementDef
						&& childDef.getValue().isCompatible((QonfigElementDef) type, autoInheritance.getInheritance())) {
						if (role != null) {
							errors.error("Child of type " + type + " is compatible with multiple roles: " + role + " and "
								+ childDef.getValue() + "--role must be specified");
							return this;
						}
						role = childDef.getValue();
					}
				}
				for (QonfigAddOn inh : theInheritance.getExpanded(QonfigAddOn::getInheritance)) {
					for (Map.Entry<String, QonfigChildDef.Declared> childDef : inh.getDeclaredChildren().entrySet()) {
						if (type instanceof QonfigElementDef
							&& childDef.getValue().isCompatible((QonfigElementDef) type, autoInheritance.getInheritance())) {
							if (role == null)
								role = childDef.getValue();
							else if (!role.getDeclared().equals(childDef.getValue())) {
								errors.error("Child of type " + type + " is compatible with multiple roles: " + role + " and "
									+ childDef.getValue() + "--role must be specified");
								return this;
							}
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
			for (QonfigChildDef role : declaredRoles) {
				if (role instanceof QonfigChildDef.Overridden)
					errors.error("Role " + role.getDeclared() + " is overridden by " + ((QonfigChildDef.Overridden) role).getOverriding()
						+ " and cannot be fulfilled directly");
				QonfigChildDef ch = theType.getAllChildren().get(role);
				if (ch != null)
					children.add(ch);
				else
					children.add(role);
			}
			Builder childBuilder = new Builder(isPartial, errors, theDocument, theElement, type, Collections.unmodifiableSet(roles),
				Collections.unmodifiableSet(realRoles), autoInheritance, description);
			child.accept(childBuilder);
			PartialQonfigElement builtChild = childBuilder.build();
			theChildren.add(builtChild);
			for (QonfigChildDef.Declared role : realRoles)
				theChildrenByRole.add(role, builtChild);
			return this;
		}

		private void create() {
			if (theStage > 0)
				return;
			AttributeCompiler attrs = new AttributeCompiler();
			attrs.compile();

			if (isPartial) {
				theElement = new PartialQonfigElement(theDocument, theParent, theType, theInheritance, theParentRoles, theDeclaredRoles,
					Collections.unmodifiableMap(attrs.attrValues), theChildren, theChildrenByRole, theValue, theErrors.getFileLocation(),
					theDescription, thePromise, theExternalContent);
			} else {
				theElement = new QonfigElement(theDocument, (QonfigElement) theParent, (QonfigElementDef) theType, theInheritance,
					theParentRoles, theDeclaredRoles, Collections.unmodifiableMap(attrs.attrValues),
					(List<QonfigElement>) (List<?>) theChildren, //
					(BetterMultiMap<QonfigChildDef.Declared, QonfigElement>) (BetterMultiMap<?, ?>) theChildrenByRole, //
					theValue, theErrors.getFileLocation(), theDescription, (QonfigElement) thePromise, theExternalContent);
			}
			theStage = 1;
		}

		public void createVariable(int minCount, int maxCount, BiConsumer<VariableQonfigElement, Builder> builder) {
			if (!isPartial)
				throw new IllegalStateException("This builder is not configured to build partial elements");
			if (theStage > 0)
				throw new IllegalStateException("Element is already created");
			AttributeCompiler attrs = new AttributeCompiler();
			attrs.compile();

			theElement = new VariableQonfigElement(theDocument, theParent, theType, theInheritance, theParentRoles, theDeclaredRoles,
				Collections.unmodifiableMap(attrs.attrValues), theValue, theErrors.getFileLocation(), theDescription, thePromise,
				theExternalContent, minCount, maxCount, builder);

			theStage = 1;
		}

		/** @return The built element */
		public QonfigElement buildFull() {
			if (isPartial)
				throw new IllegalStateException("This builder is configured to build partial elements");
			return (QonfigElement) build();
		}

		/** @return The built element */
		public PartialQonfigElement build() {
			if (theStage == 0)
				create();
			if (theStage < 2)
				checkChildren();
			return theElement;
		}

		private void checkChildren() {
			// Check the children
			// To be as helpful as possible, we need to first ensure that this element's type hierarchy is consistent.
			// E.g. if the type requires 2 instances of a child but one of the add-ons forbids more than 1,
			// tell the user that the type hierarchy is bad instead of a generic and confusing "between 2 and 1" message.
			// We also include in the error message for bad child count the types that define the constraints.
			Map<QonfigChildDef.Declared, ChildConstraint> childrenWarned = Collections.emptyMap();
			for (QonfigChildDef child : theType.getAllChildren().values()) {
				int count = theChildrenByRole.get(child.getDeclared()).size();
				if (count < child.getMin() || count > child.getMax()) {
					if (childrenWarned.isEmpty())
						childrenWarned = new LinkedHashMap<>();
					childrenWarned.put(child.getDeclared(), new ChildConstraint(child));
				}
			}
			for (QonfigAddOn inh : theInheritance.values()) {
				for (QonfigChildDef child : inh.getAllChildren().values()) {
					int count = theChildrenByRole.get(child.getDeclared()).size();
					if (count < child.getMin() || count > child.getMax()) {
						if (childrenWarned.isEmpty())
							childrenWarned = new LinkedHashMap<>();
						childrenWarned.compute(child.getDeclared(),
							(c, old) -> old == null ? new ChildConstraint(child) : old.constrain(child, theErrors));
					}
				}
			}
			for (Map.Entry<QonfigChildDef.Declared, ChildConstraint> child : childrenWarned.entrySet()) {
				if (isPartial) {
					int concreteCount = 0, minExternal = 0, maxExternal = 0;
					for (PartialQonfigElement ch : theChildrenByRole.get(child.getKey())) {
						if (ch instanceof VariableQonfigElement) {
							minExternal += ((VariableQonfigElement) ch).getMinimumCount();
							maxExternal += ((VariableQonfigElement) ch).getMaximumCount();
						} else
							concreteCount++;
					}
					child.getValue().check(child.getKey(), concreteCount, minExternal, maxExternal, theErrors);
				} else {
					int count = theChildrenByRole.get(child.getKey()).size();
					child.getValue().check(child.getKey(), count, 0, 0, theErrors);
				}
			}
			theStage = 2;
		}

		@Override
		public String toString() {
			if (theElement != null)
				return theElement.toString();
			else
				return theType.getName();
		}

		private class AttributeCompiler {
			final Map<QonfigAttributeDef.Declared, AttributeValue> attrValues;
			final MultiInheritanceSet<QonfigAddOn> completeInheritance;
			final BitSet parsedAttrs;

			AttributeCompiler() {
				attrValues = new LinkedHashMap<>();
				completeInheritance = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
				completeInheritance.addAll(theType.getInheritance());
				completeInheritance.addAll(theInheritance.values());
				parsedAttrs = new BitSet(theDeclaredAttributes.size());
			}

			void compile() {
				// Primary attribute parsing. Since values can confer inheritance, which can affect attributes,
				// we need to go through all declared attributes, parsing those we understand, ignoring those we don't,
				// and updating inheritance.
				// Stop only after we don't understand anymore attributes
				while (parsedAttrs.cardinality() < theDeclaredAttributes.size() && parseMoreAttrs()) {}

				// Now we understand all the attributes we can. Make errors for those we don't.
				for (int i = parsedAttrs.nextClearBit(0); i >= 0; i = parsedAttrs.nextClearBit(i + 1)) {
					if (i >= theDeclaredAttributes.size())
						break;
					theErrors.at(theDeclaredAttributes.get(i).position)
						.error("Unrecognized attribute: '" + theDeclaredAttributes.get(i) + "'");
				}

				// Now that we know our inheritance completely, we need to check all the attributes we've parsed
				// to make sure they still match exactly one attribute definition.
				// We also need to verify that declared attributes satisfy all inherited constraints
				checkParsedAttrs();

				// Now for the provided attributes, only using these as defaults
				boolean inheritanceChanged = true;
				while (inheritanceChanged) {
					inheritanceChanged = false;
					for (Map.Entry<QonfigAttributeDef.Declared, AttributeValue> attr : theProvidedAttributes.entrySet()) {
						QonfigElementOrAddOn owner = attr.getKey().getOwner();
						if (owner.isAssignableFrom(theType) //
							|| (owner instanceof QonfigAddOn && completeInheritance.contains((QonfigAddOn) owner))) {
							if (!attrValues.containsKey(attr.getKey())) {
								attrValues.put(attr.getKey(), attr.getValue());
								if (attr.getValue().value instanceof QonfigAddOn) {
									QonfigAddOn inh = (QonfigAddOn) attr.getValue().value;
									if (completeInheritance.add(inh)) {
										inheritanceChanged = true;
										theInheritance.add(inh);
									}
								}
							}
						}
					}
				}
				for (Map.Entry<QonfigAttributeDef.Declared, AttributeValue> attr : theProvidedAttributes.entrySet()) {
					QonfigElementOrAddOn owner = attr.getKey().getOwner();
					if (owner.isAssignableFrom(theType) //
						|| (owner instanceof QonfigAddOn && completeInheritance.contains((QonfigAddOn) owner))) {//
					}
					else
						theErrors.error("Element does not inherit element " + owner + "--cannot specify attribute " + attr.getKey());
				}

				if (!isPartial) {
					// Now populate default values for optional/forbidden attributes
					Map<QonfigAttributeDef.Declared, Boolean> defaultedAttributes = populateAttributeDefaults();
					// Now for defaulted values, verify that we don't inherit attribute specifications that conflict
					for (QonfigAddOn inh : theInheritance.getExpanded(QonfigAddOn::getInheritance)) {
						if (inh.isAssignableFrom(theType))
							continue;
						for (Map.Entry<QonfigAttributeDef.Declared, ? extends ValueDefModifier> mod : inh.getAttributeModifiers()
							.entrySet()) {
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
												attrValues.put(mod.getKey(),
													new AttributeValue(String.valueOf(defValue), defValue,
														mod.getValue().getDeclarer().getLocationString(),
														mod.getValue().getDefaultValueContent(), mod.getValue().getContent()));
											}
										}
									}
									break;
								case Optional:
									defaulted = defaultedAttributes.get(mod.getKey());
									if (value == null && defaulted == null) {
										Object defValue = mod.getValue().getDefaultValue();
										if (defValue != null) {
											attrValues.put(mod.getKey(),
												new AttributeValue(String.valueOf(defValue), defValue,
													mod.getValue().getDeclarer().getLocationString(),
													mod.getValue().getDefaultValueContent(), mod.getValue().getContent()));
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
				} else {
					for (QonfigAddOn inh : theInheritance.getExpanded(QonfigAddOn::getInheritance)) {
						if (inh.isAssignableFrom(theType))
							continue;
						for (Map.Entry<QonfigAttributeDef.Declared, ? extends ValueDefModifier> mod : inh.getAttributeModifiers()
							.entrySet()) {
							QonfigValue value = attrValues.get(mod.getKey());
							if (value != null && mod.getValue().getSpecification() != null
								&& mod.getValue().getSpecification() == SpecificationType.Forbidden) {
								theErrors.at(value.position)
									.error("Specification of value for attribute '" + mod.getKey() + "' forbidden by " + inh);
							}
						}
					}
				}

				checkValue();
			}

			private boolean parseMoreAttrs() {
				boolean inheritanceChanged = false;
				for (int i = parsedAttrs.nextClearBit(0); i >= 0; i = parsedAttrs.nextClearBit(i + 1)) {
					if (i >= theDeclaredAttributes.size())
						break;
					ElementQualifiedParseItem attrDef = theDeclaredAttributes.get(i);
					QonfigAttributeDef attr;
					BetterCollection<QonfigAttributeDef> attrs = null;
					boolean addAttr;
					if (attrDef.declaredElement != null) {
						addAttr = attrDef.declaredElement.isAssignableFrom(theType) || (attrDef.declaredElement instanceof QonfigAddOn
							&& completeInheritance.contains((QonfigAddOn) attrDef.declaredElement));
						if (addAttr) {
							// We already checked in withAttribute() to make sure this is valid
							attr = attrDef.declaredElement.getDeclaredAttributes().get(attrDef.itemName);
							if (attr == null)
								attrs = attrDef.declaredElement.getAttributesByName().get(attrDef.itemName);
						} else {
							theErrors.error("Element does not inherit element " + attrDef.declaredElement + "--cannot specify attribute "
								+ attrDef.itemName);
							parsedAttrs.set(i);
							continue;
						}
					} else {
						attr = theType.getDeclaredAttributes().get(attrDef.itemName);
						if (attr != null) {
							addAttr = true;
						} else {
							attrs = BetterHashSet.build().build();
							for (QonfigAttributeDef inhAtt : theType.getAttributesByName().get(attrDef.itemName))
								attrs.add(inhAtt.getDeclared());
							for (QonfigAddOn inh : theInheritance.values()) {
								for (QonfigAttributeDef inhAtt : inh.getAttributesByName().get(attrDef.itemName))
									attrs.add(inhAtt.getDeclared());
							}
							addAttr = !attrs.isEmpty();
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
								theErrors.error("Multiple attributes matching '" + attrDef + "' inherited");
								continue;
							}
							break;
						}
					}
					parsedAttrs.set(i);
					if (attrValues.containsKey(attr.getDeclared())) {
						theErrors.error("Duplicate values supplied for attribute " + attrDef.itemName, null);
					}
					AttributeValueInput attrValue = theDeclaredAttributeValues.get(i);
					Object value;
					try {
						value = attrValue.parseAttributeValue(theDocument.getDocToolkit(), attr, theErrors.at(attrValue.getPosition()));
					} catch (RuntimeException e) {
						theErrors.error("Could not parse attribute " + theDeclaredAttributeValues.get(i).toString(), e);
						continue;
					}
					if (addAttr) {
						attrValues.put(attr.getDeclared(),
							new AttributeValue(attrValue.getText(), value, theDocument.getLocation(), attrValue.getPosition(),
								attrDef.position));
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
				return inheritanceChanged;
			}

			private void checkParsedAttrs() {
				for (int i = 0; i < theDeclaredAttributes.size(); i++) {
					ElementQualifiedParseItem attrDef = theDeclaredAttributes.get(i);
					QonfigAttributeDef attr;
					BetterCollection<QonfigAttributeDef> attrs = null;
					if (attrDef.declaredElement != null) {
						if (attrDef.declaredElement.isAssignableFrom(theType) || (attrDef.declaredElement instanceof QonfigAddOn
							&& completeInheritance.contains((QonfigAddOn) attrDef.declaredElement))) {
							// We already checked in withAttribute() to make sure this is valid
							attr = attrDef.declaredElement.getDeclaredAttributes().get(attrDef.itemName);
							if (attr == null)
								attrs = attrDef.declaredElement.getAttributesByName().get(attrDef.itemName);
						} else {
							continue; // Already warned
						}
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

					// Now check the types and specifications
					QonfigValue value = attrValues.get(attr.getDeclared());
					ValueDefModifier mod = theType.getAttributeModifiers().get(attr.getDeclared());
					if (mod != null) {
						if (mod.getTypeRestriction() != null && !mod.getTypeRestriction().isInstance(value.value))
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
							if (mod.getTypeRestriction() != null && !mod.getTypeRestriction().isInstance(value.value))
								theErrors.error("Type of value " + value + " does not match that required by " + inh + " ("
									+ mod.getTypeRestriction() + ")");
							if (mod.getSpecification() == SpecificationType.Forbidden)
								theErrors.error("Specification of value for attribute forbidden by type " + inh);
						}
					}
				}
			}

			private Map<QonfigAttributeDef.Declared, Boolean> populateAttributeDefaults() {
				Map<QonfigAttributeDef.Declared, Boolean> defaultedAttributes = new HashMap<>();
				for (QonfigAddOn inh : completeInheritance.getExpanded(QonfigAddOn::getInheritance)) {
					for (QonfigAttributeDef.Declared attr : inh.getDeclaredAttributes().values()) {
						if (attrValues.get(attr) == null && attr.getSpecification() != SpecificationType.Required) {
							Object defValue = attr.getDefaultValue();
							if (defValue != null) {
								attrValues.put(attr, new AttributeValue(defValue.toString(), defValue,
									attr.getDeclarer().getLocationString(), attr.getDefaultValueContent(), attr.getFilePosition()));
								defaultedAttributes.put(attr, attr.getSpecification() == SpecificationType.Forbidden);
							}
						}
					}
					for (Map.Entry<QonfigAttributeDef.Declared, ? extends ValueDefModifier> attr : inh.getAttributeModifiers().entrySet()) {
						if (attrValues.get(attr.getKey()) == null && attr.getValue().getSpecification() != SpecificationType.Required) {
							Object defValue = attr.getValue().getDefaultValue();
							attrValues.put(attr.getKey(),
								new AttributeValue(defValue == null ? null : defValue.toString(), defValue,
									attr.getValue().getDeclarer().getLocationString(), attr.getValue().getDefaultValueContent(),
									attr.getValue().getContent()));
							defaultedAttributes.put(attr.getKey(), attr.getValue().getSpecification() == SpecificationType.Forbidden);
						}
					}
				}
				for (Map.Entry<QonfigAttributeDef.Declared, QonfigAttributeDef> attr : theType.getAllAttributes().entrySet()) {
					if (attrValues.get(attr.getKey()) == null && attr.getValue().getSpecification() != SpecificationType.Required) {
						Object defValue = attr.getValue().getDefaultValue();
						if (defValue != null) {
							attrValues.put(attr.getKey(),
								new AttributeValue(defValue.toString(), defValue, attr.getValue().getDeclarer().getLocationString(),
									attr.getValue().getDefaultValueContent(), attr.getValue().getFilePosition()));
							defaultedAttributes.put(attr.getKey(), attr.getValue().getSpecification() == SpecificationType.Forbidden);
						}
					}
				}
				return defaultedAttributes;
			}

			private void checkValue() {
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
								theValue = new QonfigValue(String.valueOf(defValue), defValue, theType.getDeclarer().getLocationString(),
									null);
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
			}
		}
	}

	private static class ChildConstraint {
		int min;
		QonfigElementOrAddOn minDefiner;
		int max;
		QonfigElementOrAddOn maxDefiner;

		ChildConstraint(QonfigChildDef child) {
			// Get as close to the root of the definition as we can
			while (child instanceof QonfigChildDef.Inherited)
				child = ((QonfigChildDef.Inherited) child).getInherited();
			min = child.getMin();
			max = child.getMax();
			minDefiner = maxDefiner = child.getOwner();
		}

		ChildConstraint constrain(QonfigChildDef child, ErrorReporting reporting) {
			while (child instanceof QonfigChildDef.Inherited)
				child = ((QonfigChildDef.Inherited) child).getInherited();
			if (child.getMin() > min) {
				if (child.getMin() > max)
					reporting.error("Illegal element configuration. " + child.getOwner() + " requires at least " + child.getMin()
						+ " children for role " + child.getDeclared() + ", but " + maxDefiner + " forbids more than " + max);
				else {
					min = child.getMin();
					minDefiner = child.getOwner();
				}
			}
			if (child.getMax() < max) {
				if (child.getMax() < min)
					reporting.error("Illegal element configuration. " + minDefiner + " requires at least " + min + " children for role "
						+ child.getDeclared() + ", but " + child.getOwner() + " forbids more than " + child.getMax());
				else {
					min = child.getMin();
					minDefiner = child.getOwner();
				}
			}
			return this;
		}

		void check(QonfigChildDef.Declared child, int concreteCount, int minExternal, int maxExternal, ErrorReporting reporting) {
			int totalMin = concreteCount + minExternal;
			if (totalMin < min) {
				if (max == 1) {
					if (child.getMin() == min)
						reporting.error("Child required: " + child);
					else
						reporting.error("Child " + child + " required by " + minDefiner);
				} else if (max == Integer.MAX_VALUE) {
					String error;
					if (child.getMin() == min)
						error = "At least " + min + " child" + (min == 1 ? "" : "ren") + " required for " + child;
					else
						error = "At least " + min + " child" + (min == 1 ? "" : "ren") + " required for " + child + " by " + minDefiner;
					if (totalMin == 0)
						reporting.error(error);
					else if (maxExternal == 0)
						reporting.error(error + ", but only " + concreteCount + " specified");
					else
						reporting.error(error + ", but a minimum of " + totalMin + " could be specified");
				} else if (min == max) {
					String error;
					if (child.getMin() == min)
						error = min + " child" + (min == 1 ? "" : "ren") + " required for " + child;
					else
						error = min + " child" + (min == 1 ? "" : "ren") + " required for " + child + " by " + minDefiner;
					if (concreteCount == 0)
						reporting.error(error);
					else if (maxExternal == 0)
						reporting.error(error + ", but only " + concreteCount + " specified");
					else
						reporting.error(error + ", but a minimum of " + totalMin + " could be specified");
				} else {
					String error;
					if (child.getMin() == min) {
						if (child.getMax() == max)
							error = "Between " + min + " and " + max + " children required for " + child;
						else
							error = "Between " + min + " and " + max + " children required for " + child + " by " + maxDefiner;
					} else if (child.getMax() == max || minDefiner == maxDefiner)
						error = "Between " + min + " and " + max + " children required for " + child + " by " + minDefiner;
					else
						error = "Between " + min + " and " + max + " children required for " + child + " by " + minDefiner + "/"
							+ maxDefiner;
					if (concreteCount == 0)
						reporting.error(error);
					else if (maxExternal == 0)
						reporting.error(error + ", but only " + concreteCount + " specified");
					else
						reporting.error(error + ", but a minimum of " + totalMin + " could be specified");
				}
			}
			int totalMax = add(concreteCount, maxExternal);
			if (totalMax > max) {
				if (max == 0) {
					if (child.getMax() == max) // Can't imagine why you'd define a child with a max of 0, but it's allowed
						reporting.error("Child forbidden: " + child);
					else
						reporting.error("Child " + child + " forbidden by " + maxDefiner);
				} else {
					String error;
					if (child.getMax() == max) {
						error = "No more than " + max + " children allowed for " + child;
					} else
						error = "No more than " + max + " children allowed for " + child + " by " + maxDefiner;
					if (maxExternal == 0)
						reporting.error(error + ", but " + concreteCount + " specified");
					else
						reporting.error(error + ", but up to " + totalMax + " could be specified");
				}
			}
		}

		private static int add(int i1, int i2) {
			int result = i1 + i2;
			if (result < i1 && result < i2)
				return Integer.MAX_VALUE;
			return result;
		}
	}
}

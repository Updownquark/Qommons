package org.qommons.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.qommons.MultiInheritanceSet;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.BetterMultiMap;
import org.qommons.config.QonfigAddOn.ValueModifier;
import org.qommons.config.QonfigAttributeDef.Declared;
import org.qommons.config.QonfigValidation.ValueSpec;

/** Super class of {@link QonfigElementDef} and {@link QonfigAddOn} */
public abstract class QonfigElementOrAddOn extends AbstractQonfigType {
	private final boolean isAbstract;

	private final QonfigElementDef theSuperElement;
	private final Set<QonfigAddOn> theInheritance;
	private final MultiInheritanceSet<QonfigAddOn> theFullInheritance;

	private final Map<String, QonfigAttributeDef.Declared> theDeclaredAttributes;
	private final Map<QonfigAttributeDef.Declared, ? extends ValueDefModifier> theAttributeModifiers;
	private final BetterMultiMap<String, QonfigAttributeDef> theAttributesByName;

	private final Map<String, QonfigChildDef.Declared> theDeclaredChildren;
	private final Map<QonfigChildDef.Declared, ? extends ChildDefModifier> theChildModifiers;
	private final BetterMultiMap<String, QonfigChildDef> theChildrenByName;

	private final ValueDefModifier theValueModifier;

	/**
	 * @param declarer The toolkit declaring the item (element-def or add-on)
	 * @param name The name of the item
	 * @param isAbstract Whether the item is abstract
	 * @param superElement The super-element for the item
	 * @param inheritance The declared inheritance for the item
	 * @param fullInheritance The complete inheritance for the item
	 * @param declaredAttributes The declared attributes for the item
	 * @param attributeModifiers The attribute modifiers for the item
	 * @param attributesByName Declared and inherited attributes for the item
	 * @param declaredChildren The declared children for the item
	 * @param childModifiers The child modifiers for the item
	 * @param childrenByName Declared and inherited children for the item
	 * @param valueModifier The value definition or modifier for the item
	 */
	protected QonfigElementOrAddOn(QonfigToolkit declarer, String name, boolean isAbstract, //
		QonfigElementDef superElement, Set<QonfigAddOn> inheritance, MultiInheritanceSet<QonfigAddOn> fullInheritance, //
		Map<String, Declared> declaredAttributes, Map<Declared, ? extends ValueDefModifier> attributeModifiers,
		BetterMultiMap<String, QonfigAttributeDef> attributesByName, //
		Map<String, QonfigChildDef.Declared> declaredChildren, Map<QonfigChildDef.Declared, ? extends ChildDefModifier> childModifiers,
		BetterMultiMap<String, QonfigChildDef> childrenByName, ValueDefModifier valueModifier) {
		super(declarer, name);
		this.isAbstract = isAbstract;
		theSuperElement = superElement;
		theInheritance = inheritance;
		theFullInheritance = fullInheritance;

		theDeclaredAttributes = declaredAttributes;
		theAttributeModifiers = attributeModifiers;
		theAttributesByName = attributesByName;

		theDeclaredChildren = declaredChildren;
		theChildModifiers = childModifiers;
		theChildrenByName = childrenByName;

		theValueModifier = valueModifier;
	}

	/** @return False if this type can be instantiated directly, true if it can only be extended/inherited */
	public boolean isAbstract() {
		return isAbstract;
	}

	/**
	 * @return For {@link QonfigElementDef}s, the element-def that this element extends; for {@link QonfigAddOn}s, the requirement--i.e. the
	 *         type that any attribute this add-on is inherited by must extend
	 */
	public QonfigElementDef getSuperElement() {
		return theSuperElement;
	}

	/** @return Add-ons declared as this item's inheritance */
	public Set<QonfigAddOn> getInheritance() {
		return theInheritance;
	}

	/** @return All add-ons inherited by this item */
	public MultiInheritanceSet<QonfigAddOn> getFullInheritance() {
		return theFullInheritance;
	}

	/** @return All attributes declared by this item */
	public Map<String, QonfigAttributeDef.Declared> getDeclaredAttributes() {
		return theDeclaredAttributes;
	}

	/** @return All attributes modified by this item */
	public Map<QonfigAttributeDef.Declared, ? extends ValueDefModifier> getAttributeModifiers() {
		return theAttributeModifiers;
	}

	/** @return All attributes declared or inherited by this item */
	public BetterMultiMap<String, QonfigAttributeDef> getAttributesByName() {
		return theAttributesByName;
	}

	/** @return The modification, if specified, to the inherited value */
	public ValueDefModifier getValueModifier() {
		return theValueModifier;
	}

	/** @return All children declared by this item */
	public Map<String, QonfigChildDef.Declared> getDeclaredChildren() {
		return theDeclaredChildren;
	}

	/** @return All children modified by this item */
	public Map<QonfigChildDef.Declared, ? extends ChildDefModifier> getChildModifiers() {
		return theChildModifiers;
	}

	/** @return All children declared or inherited by this item */
	public BetterMultiMap<String, QonfigChildDef> getChildrenByName() {
		return theChildrenByName;
	}

	/**
	 * @param other The other element-def or add-on
	 * @return Whether the given element-def or add-on inherits or extends this element-def or add-on
	 */
	public abstract boolean isAssignableFrom(QonfigElementOrAddOn other);

	/** Abstract builder for element-defs or add-ons */
	protected static abstract class Builder {
		/** Stage enum for element-def/add-on builder */
		public enum Stage {
			/** Extension, inheritance */
			Initial,
			/** Declared attribute definition */
			NewAttributes,
			/** Inherited attribute modification */
			ModifyAttributes,
			/** Declared child definition */
			NewChildren,
			/** Inherited child modification */
			ModifyChildren,
			/** Final state, no further modifications allowed */
			Built
		}

		/** The name for the element-def or add-on */
		protected final String theName;
		/** The session for error reporting */
		protected final QonfigParseSession theSession;
		private boolean isAbstract;
		private QonfigElementDef theSuperElement;
		private final Set<QonfigAddOn> theInheritance;

		private final Map<String, QonfigAttributeDef.Declared> theDeclaredAttributes;
		private final Map<QonfigAttributeDef.Declared, ValueDefModifier> theAttributeModifiers;
		private final Map<QonfigAttributeDef.Declared, QonfigAttributeDef> theCompiledAttributes;
		private final BetterMultiMap<String, QonfigAttributeDef> theAttributesByName;
		private final Map<QonfigAttributeDef.Declared, ValueSpec> theAttributeModifierOrigSpecs;

		private final Map<String, QonfigChildDef.Declared> theDeclaredChildren;
		private final Map<QonfigChildDef.Declared, ChildDefModifier> theChildModifiers;
		private final Map<QonfigChildDef.Declared, QonfigChildDef> theCompiledChildren;
		private final BetterMultiMap<String, QonfigChildDef> theChildrenByName;
		private ValueDefModifier theValueModifier;

		private final MultiInheritanceSet<QonfigAddOn> theFullInheritance;
		private Stage theStage;
		private QonfigElementOrAddOn theBuilt;

		/**
		 * @param name The name for the element-def/add-on
		 * @param session The session for error reporting
		 */
		protected Builder(String name, QonfigParseSession session) {
			theName = name;
			theSession = session;

			theInheritance = new LinkedHashSet<>();

			theDeclaredAttributes = new LinkedHashMap<>();
			theAttributeModifiers = new LinkedHashMap<>();
			theAttributesByName = BetterHashMultiMap.<String, QonfigAttributeDef> build().safe(false).buildMultiMap();
			theCompiledAttributes = new LinkedHashMap<>();
			theAttributeModifierOrigSpecs = new HashMap<>();

			theDeclaredChildren = new LinkedHashMap<>();
			theChildModifiers = new LinkedHashMap<>();
			theCompiledChildren = new LinkedHashMap<>();
			theChildrenByName = BetterHashMultiMap.<String, QonfigChildDef> build().safe(false).buildMultiMap();

			theFullInheritance = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);

			theStage = Stage.Initial;
		}

		/** @return This builder's session for error reporting */
		public QonfigParseSession getSession() {
			return theSession;
		}

		/** @return The name for the element-def or add-on */
		public String getName() {
			return theName;
		}

		/** @return Whether the item will be {@link QonfigElementOrAddOn#isAbstract()} */
		protected boolean isAbstract() {
			return isAbstract;
		}

		/** @return The declared inheritance of the item */
		protected Set<QonfigAddOn> getInheritance() {
			return Collections.unmodifiableSet(theInheritance);
		}

		/** @return The complete inheritance of the item */
		protected MultiInheritanceSet<QonfigAddOn> getFullInheritance() {
			return MultiInheritanceSet.unmodifiable(theFullInheritance);
		}

		/** @return The attributes declared by the item */
		protected Map<String, QonfigAttributeDef.Declared> getDeclaredAttributes() {
			return Collections.unmodifiableMap(theDeclaredAttributes);
		}

		/** @return The inherited attribute modifiers declared by the item */
		protected Map<QonfigAttributeDef.Declared, ? extends ValueDefModifier> getAttributeModifiers() {
			return Collections.unmodifiableMap(theAttributeModifiers);
		}

		/** @return All attributes declared or inherited by the item */
		protected Map<QonfigAttributeDef.Declared, QonfigAttributeDef> getCompiledAttributes() {
			return Collections.unmodifiableMap(theCompiledAttributes);
		}

		/** @return All attributes declared or inherited by the item, by name */
		protected BetterMultiMap<String, QonfigAttributeDef> getAttributesByName() {
			return BetterCollections.unmodifiableMultiMap(theAttributesByName);
		}

		/** @return The children declared by the item */
		protected Map<String, QonfigChildDef.Declared> getDeclaredChildren() {
			return Collections.unmodifiableMap(theDeclaredChildren);
		}

		/** @return The inherited child modifiers declared by the item */
		protected Map<QonfigChildDef.Declared, ? extends ChildDefModifier> getChildModifiers() {
			return Collections.unmodifiableMap(theChildModifiers);
		}

		/** @return All attributes declared or inherited by the item */
		protected Map<QonfigChildDef.Declared, QonfigChildDef> getCompiledChildren() {
			return Collections.unmodifiableMap(theCompiledChildren);
		}

		/** @return All children declared or inherited by the item, by name */
		protected BetterMultiMap<String, QonfigChildDef> getChildrenByName() {
			return BetterCollections.unmodifiableMultiMap(theChildrenByName);
		}

		/** @return The value definition or modification declared by this item */
		protected ValueDefModifier getValueModifier() {
			return theValueModifier;
		}

		/**
		 * Advances the stage of the builder, potentially performing intermediate build steps
		 * 
		 * @param stage The stage for the builder
		 */
		public void setStage(Stage stage) {
			if (!checkStage(stage))
				throw new IllegalArgumentException("Cannot revert from stage " + theStage + " to " + stage);
		}

		/**
		 * Checks and possibly advances the stage of the builder
		 * 
		 * @param stage The desired stage
		 * @return True if the current stage is (now) at the given stage, false if the current stage is greater
		 */
		protected boolean checkStage(Stage stage) {
			if (theStage.compareTo(stage) > 0)
				return false;
			while (theStage.compareTo(stage) < 0) {
				theStage = Stage.values()[theStage.ordinal() + 1];
				newStage(theStage);
			}
			return true;
		}

		/**
		 * Called when the stage is advanced to allow intermediate build operations
		 * 
		 * @param stage The new current stage
		 */
		protected void newStage(Stage stage) {
			switch (stage) {
			case NewAttributes:
				// Fill out inheritance
				if (theSuperElement != null)
					theFullInheritance.addAll(theSuperElement.getFullInheritance().values());
				theFullInheritance.addAll(theInheritance);
				break;
			case ModifyAttributes:
				// Add declared attributes to compiled
				// Add inherited attributes to compiled
				if (theSuperElement != null) {
					for (Map.Entry<QonfigAttributeDef.Declared, QonfigAttributeDef> attr : theSuperElement.getAllAttributes().entrySet()) {
						if (theCompiledAttributes.putIfAbsent(attr.getKey(), attr.getValue()) == null)
							theAttributesByName.add(attr.getKey().getName(), attr.getValue());
					}
				}
				for (QonfigAddOn inh : theFullInheritance.getExpanded(QonfigAddOn::getInheritance)) {
					for (QonfigAttributeDef.Declared attr : inh.getDeclaredAttributes().values()) {
						if (theCompiledAttributes.putIfAbsent(attr, attr) == null)
							theAttributesByName.add(attr.getName(), attr);
					}
				}
				for (QonfigAttributeDef.Declared attr : get().getDeclaredAttributes().values()) {
					theCompiledAttributes.put(attr, attr);
					theAttributesByName.add(attr.getName(), attr);
				}
				break;
			case NewChildren:
				// Add modified attributes to compiled
				for (Map.Entry<QonfigAttributeDef.Declared, ? extends ValueDefModifier> mod : get().getAttributeModifiers().entrySet()) {
					QonfigAttributeDef.Modified modified = new QonfigAttributeDef.Modified(mod.getKey(), get(), //
						mod.getValue().getTypeRestriction() != null ? mod.getValue().getTypeRestriction() : mod.getKey().getType(), //
						mod.getValue().getSpecification() != null ? mod.getValue().getSpecification() : mod.getKey().getSpecification(), //
						mod.getValue().getDefaultValue() != null ? mod.getValue().getDefaultValue() : mod.getKey().getDefaultValue());
					QonfigAttributeDef old = theCompiledAttributes.put(mod.getKey(), modified);
					theAttributesByName.add(mod.getKey().getName(), modified);
					if (old != null)
						theAttributesByName.remove(mod.getKey().getName(), old);
				}
				break;
			case Built:
				validate();
				break;
			default:
			}
		}

		/**
		 * @param abst Whether this item should be abstract
		 * @return This builder
		 */
		public Builder setAbstract(boolean abst) {
			if (!checkStage(Stage.Initial)) {
				theSession.withError("Abstract cannot be changed at this stage");
				return this;
			}
			isAbstract = abst;
			return this;
		}

		/** @return The super-element declared for the item */
		public QonfigElementDef getSuperElement() {
			return theSuperElement;
		}

		/**
		 * @param superElement The super-element for the item
		 * @return This builder
		 */
		public Builder setSuperElement(QonfigElementDef superElement) {
			if (!checkStage(Stage.Initial))
				throw new IllegalStateException("Super element cannot be set at this stage");
			else if (theSuperElement != null)
				throw new IllegalStateException("Super element has already been set");
			theSuperElement = superElement;
			return this;
		}

		/**
		 * @param addOn The add-on for this item to inherit
		 * @return This builder
		 */
		public Builder inherits(QonfigAddOn addOn) {
			// Inheritance doesn't have its own stage.
			// This call can be mingled with withAttribute(), since that call can also affect inheritance
			if (!checkStage(Stage.Initial))
				throw new IllegalStateException("Inheritance cannot be modified at this stage");
			String msg = _inherits(addOn);
			if (msg != null)
				theSession.withError(msg);
			return this;
		}

		private String _inherits(QonfigAddOn addOn) {
			if (addOn.getSuperElement() != null) {
				if (addOn.getSuperElement() == theBuilt) {//
				} else if (theSuperElement != null && addOn.getSuperElement().isAssignableFrom(theSuperElement)) {//
				} else
					return "Illegal inheritance: " + theName + " <- " + addOn + ": super element " + addOn.getSuperElement()
						+ " incompatible with " + theName;
			}
			theInheritance.add(addOn);
			return null;
		}

		/**
		 * Declares or modifies the value definition for this item
		 * 
		 * @param type The type for the value (or null to inherit it)
		 * @param specification The specification for the value (or null to inherit it)
		 * @param defaultValue The value to use if the value is not specified (or null to inherit it)
		 * @return This builder
		 */
		public Builder withValue(QonfigValueType type, SpecificationType specification, Object defaultValue) {
			if (!checkStage(Stage.Initial))
				throw new IllegalStateException("Value cannot be specified or modified at this stage");
			else if (theValueModifier != null)
				throw new IllegalStateException("Value has already been specified or modified");

			if (getSuperElement() != null && getSuperElement().getValue() != null) {
				ValueSpec newSpec = QonfigValidation.validateSpecification(//
					new ValueSpec(theSuperElement.getValue().getType(), theSuperElement.getValue().getSpecification(),
						theSuperElement.getValue().getDefaultValue()), //
					new ValueSpec(type, specification, defaultValue), //
					err -> theSession.forChild("text", null).withError(err), warn -> theSession.forChild("text", null).withWarning(warn));
				if (newSpec.specification != theSuperElement.getValue().getSpecification()
					|| !Objects.equals(newSpec.defaultValue, theSuperElement.getValue().getDefaultValue()))
					theValueModifier = valueModifier(type, specification, defaultValue);
			} else {
				if (type == null)
					theSession.forChild("value", null).withError("No type specified");
				else if (defaultValue != null && !type.isInstance(defaultValue))
					theSession.forChild("value", null).withError(defaultValue + " is not an instance of " + type);
				if (specification == null) {
					if (defaultValue != null)
						specification = SpecificationType.Optional;
					else
						specification = SpecificationType.Required;
				} else if (specification == SpecificationType.Forbidden && defaultValue == null)
					theSession.forChild("value", null).withError("No default specified");
				if (type != null)
					theValueModifier = valueModifier(type, specification, defaultValue);
			}
			return this;
		}

		/**
		 * Creates a value modifier
		 * 
		 * @param type The type for the value
		 * @param specification The specification for the value
		 * @param defaultValue The value to use if the value is not specified
		 * @return The value modifier to use for the item
		 */
		protected abstract ValueDefModifier valueModifier(QonfigValueType type, SpecificationType specification, Object defaultValue);

		/**
		 * Declares an attribute
		 * 
		 * @param name The name of the attribute
		 * @param type The type for the attribute value
		 * @param specify The specification for the attribute
		 * @param defaultValue The value to use if the attribute is not specified
		 * @return This builder
		 */
		public Builder withAttribute(String name, QonfigValueType type, SpecificationType specify, Object defaultValue) {
			if (!checkStage(Stage.NewAttributes))
				throw new IllegalStateException("Attributes cannot be added at this stage");
			else if (theDeclaredAttributes.containsKey(name)) {
				theSession.withError("Cannot declare multiple attributes with the same name: " + name);
				return this;
			} else if (specify == SpecificationType.Forbidden) {
				if (defaultValue == null) {
					theSession.withError("A default value must be specified for an attribute that is forbidden: " + name);
					return this;
				} else if (type instanceof QonfigAddOn) {
					String msg = _inherits((QonfigAddOn) defaultValue);
					if (msg != null) {
						theSession.withError("Attribute '" + name + "' requires inheritance of " + defaultValue + ": " + msg);
						type = QonfigValueType.STRING; // Allow to proceed, minimizing errors
						defaultValue = null;
					}
				}
			} else if (type instanceof QonfigAddOn) {
				String msg = _inherits((QonfigAddOn) type);
				if (msg != null) {
					theSession.withError("Attribute '" + name + "' requires inheritance of " + type + ": " + msg);
					type = QonfigValueType.STRING; // Allow to proceed, minimizing errors
				}
			}
			theDeclaredAttributes.put(name, new QonfigAttributeDef.DeclaredAttributeDef(theBuilt, name, type, specify, defaultValue));
			return this;
		}

		/**
		 * Modifies an inherited attribute
		 * 
		 * @param attribute The inherited attribute to modify
		 * @param type The new type for the value (or null to inherit it)
		 * @param specification The new specification for the value (or null to inherit it)
		 * @param defaultValue The value to use if the attribute is not specified (or null to inherit it)
		 * @return This builder
		 */
		public Builder modifyAttribute(QonfigAttributeDef attribute, QonfigValueType type, SpecificationType specification,
			Object defaultValue) {
			if (!checkStage(Stage.ModifyAttributes))
				throw new IllegalStateException("Attributes cannot be added at this stage");
			else if (theAttributeModifiers.containsKey(attribute)) {
				theSession.withError("Attribute " + attribute + " is already modified here");
				return this;
			} else if (attribute.getOwner().getDeclaredAttributes().get(attribute.getName()) != attribute)
				throw new IllegalStateException(
					"Bad attribute " + attribute + ": owner " + attribute.getOwner() + " does not recognize it");
			QonfigElementOrAddOn owner = attribute.getDeclared().getOwner();
			ValueSpec oldSpec;
			boolean ext;
			if (owner instanceof QonfigElementDef) {
				if (theSuperElement == null) {
					ext = false;
					oldSpec = new ValueSpec(attribute.getType(), attribute.getSpecification(), attribute.getDefaultValue());
				} else {
					ext = owner.isAssignableFrom(theSuperElement);
					oldSpec = new ValueSpec(attribute.getDeclared().getType(), attribute.getDeclared().getSpecification(),
						attribute.getDeclared().getDefaultValue());
					ValueDefModifier mod = theSuperElement.getAttributeModifiers().get(attribute.getDeclared());
					if (mod != null)
						oldSpec = QonfigValidation.validateSpecification(oldSpec, //
							new ValueSpec(null, mod.getSpecification(), mod.getDefaultValue()), //
							__ -> {
							}, __ -> {
							});
				}
			} else {
				ext = theFullInheritance.contains((QonfigAddOn) owner);
				oldSpec = new ValueSpec(attribute.getType(), attribute.getSpecification(), attribute.getDefaultValue());
			}
			if (!ext) {
				theSession.withError("Attribute " + attribute.getOwner() + "." + attribute.getName() + " does not apply to this element--"
					+ attribute.getOwner() + " not extended");
				return this;
			}
			theAttributeModifierOrigSpecs.put(attribute.getDeclared(), oldSpec);
			ValueSpec newSpec = QonfigValidation.validateSpecification(//
				oldSpec, new ValueSpec(null, specification, defaultValue), //
				err -> theSession.forChild("attribute", attribute.getOwner().getName() + "." + attribute.getName()).withError(err),
				warn -> theSession.forChild("attribute", attribute.getOwner().getName() + "." + attribute.getName()).withWarning(warn));
			theAttributeModifiers.put(attribute.getDeclared(), valueModifier(newSpec.type, newSpec.specification, newSpec.defaultValue));
			return this;
		}

		/**
		 * Declares a child definition
		 * 
		 * @param name The role name for the child definition
		 * @param type The super type for children of the child
		 * @param fulfillment The inherited child roles that the new child fulfills
		 * @param inheritance The add-ons inherited by the child
		 * @param min The minimum number of children of the given role that must be specified for {@link QonfigElement}s of this item's type
		 * @param max The maximum number of children of the given role that may be specified for {@link QonfigElement}s of this item's type
		 * @return This builder
		 */
		public Builder withChild(String name, QonfigElementDef type, Set<QonfigChildDef.Declared> fulfillment, Set<QonfigAddOn> inheritance,
			int min, int max) {
			if (!checkStage(Stage.NewChildren))
				throw new IllegalStateException("Attributes cannot be added at this stage");
			else if (theDeclaredChildren.containsKey(name)) {
				theSession.withError("Cannot declare multiple children with the same name: " + name);
				return this;
			}
			QonfigChildDef.Declared child = new QonfigChildDef.DeclaredChildDef(get(), name, type,
				QommonsUtils.unmodifiableDistinctCopy(fulfillment), QommonsUtils.unmodifiableDistinctCopy(inheritance), min, max);
			theDeclaredChildren.put(name, child);
			theCompiledChildren.put(child, child);
			for (QonfigChildDef.Declared fulfilled : fulfillment)
				theCompiledChildren.compute(fulfilled, (__, old) -> {
					if (old == null)
						return new QonfigChildDef.Overridden(get(), fulfilled, Collections.singleton(child));
					Set<QonfigChildDef.Declared> overriding = new LinkedHashSet<>();
					overriding.addAll(((QonfigChildDef.Overridden) old).getOverriding());
					overriding.add(child);
					return new QonfigChildDef.Overridden(get(), fulfilled, overriding);
				});
			theChildrenByName.add(name, child);
			return this;
		}

		/**
		 * Modifies an inherited child definition
		 * 
		 * @param child The inherited child definition to modify
		 * @param type The type restriction for the child
		 * @param inheritance Additional add-ons to be inherited by the child
		 * @param min The minimum number of children of the given role that must be specified for {@link QonfigElement}s of this item's type
		 * @param max The maximum number of children of the given role that may be specified for {@link QonfigElement}s of this item's type
		 * @return This builder
		 */
		public Builder modifyChild(QonfigChildDef.Declared child, QonfigElementDef type, Set<QonfigAddOn> inheritance, Integer min,
			Integer max) {
			if (!checkStage(Stage.ModifyChildren))
				throw new IllegalStateException("Children cannot be modified at this stage");
			else if (child.getDeclared().getOwner().getDeclaredChildren().get(child.getName()) != child.getDeclared())
				throw new IllegalStateException(
					"Bad child " + child + ": owner " + child.getDeclared().getOwner() + " does not recognize it");
			QonfigParseSession childSession = theSession.forChild("child-mod", child.toString());
			boolean inherited = theSuperElement != null && child.getOwner().isAssignableFrom(theSuperElement);
			if (!inherited && child.getOwner() instanceof QonfigAddOn) {
				for (QonfigAddOn inh : theInheritance) {
					inherited = inh.isAssignableFrom(child.getOwner());
					if (inherited)
						break;
				}
			}
			if (!inherited) {
				childSession.withError("Cannot modify child, because its owner is not inherited");
				return this;
			}
			QonfigChildDef override = theCompiledChildren.get(child.getDeclared());
			if (override instanceof QonfigChildDef.Overridden) {
				childSession.withError("Child has been overridden by " + ((QonfigChildDef.Overridden) override).getOverriding());
				return this;
			}
			if (theChildModifiers.containsKey(child)) {
				childSession.withError("Child " + child + " is already modified here");
				return this;
			}
			QonfigChildDef oldChild = theSuperElement == null ? null : theSuperElement.getAllChildren().get(child.getDeclared());
			if (type != null) {
				boolean badType = false;
				if (oldChild != null) {
					if (!oldChild.getType().isAssignableFrom(type) && !type.isAssignableFrom(oldChild.getType())) {
						childSession.withError("Child of type " + child.getType() + " cannot be restricted to type " + type);
						badType = true;
					}
				} else if (!child.getType().isAssignableFrom(type) && !type.isAssignableFrom(child.getType())) {
					childSession.withError("Child of type " + child.getType() + " cannot be restricted to type " + type);
					badType = true;
				}
				for (QonfigAddOn inh : theInheritance) {
					ChildDefModifier mod = inh.getChildModifiers().get(child);
					if (mod == null || mod.getTypeRestriction() == null)
						continue;
					if (!mod.getTypeRestriction().isAssignableFrom(type) && !type.isAssignableFrom(mod.getTypeRestriction())) {
						childSession.withError(
							"Inherited add-on " + inh + " requires type " + mod.getTypeRestriction() + "--cannot be restricted to " + type);
						badType = true;
					}
				}
				if (badType)
					type = null;
			}
			if (min != null) {
				if (min < 0) {
					childSession.withError("min<0 (" + min + ")");
					min = 0;
					if (max < 0)
						max = 0;
				} else if (max != null) {
					if (min > max) {
						childSession.withError("min (" + min + ")>max (" + max + ")");
						max = min;
					}
				}
				if (min < oldChild.getMin()) {
					childSession
						.withError("min (" + min + ") is less than declared by " + oldChild.getOwner() + " (" + oldChild.getMin() + ")");
					min = oldChild.getMin();
					if (max != null && max < min)
						max = min;
				}
			}
			if (max != null && max > oldChild.getMax()) {
				childSession
					.withError("max (" + max + ") is greater than declared by " + oldChild.getOwner() + " (" + oldChild.getMax() + ")");
				max = oldChild.getMax();
			}
			for (QonfigAddOn inh : theInheritance) {
				ChildDefModifier mod = inh.getChildModifiers().get(child);
				if (mod == null || (mod.getMin() == null && mod.getMax() == null))
					continue;
				if (min != null && mod.getMin() != null && min < mod.getMin()) {
					childSession.withWarning(
						"Inherited add-on " + inh + " specifies minimum " + mod.getMin() + "--cannot be overridden with min=" + min);
					min = mod.getMin();
				}
				if (max != null && mod.getMax() != null && max > mod.getMax()) {
					childSession.withWarning(
						"Inherited add-on " + inh + " specifies maximum " + mod.getMax() + "--cannot be overridden with max=" + max);
					max = mod.getMax();
				}
			}
			Iterator<QonfigAddOn> iter = inheritance.iterator();
			while (iter.hasNext()) {
				QonfigAddOn inh = iter.next();
				if (inh.getSuperElement() != null && !inh.getSuperElement().isAssignableFrom(oldChild.getType())) {
					childSession.withError(
						"Add-on '" + inh + "' requires " + inh.getSuperElement() + "--" + theSuperElement + " does not fulfill this");
					iter.remove();
				}
			}
			Set<QonfigAddOn> copy = QommonsUtils.unmodifiableDistinctCopy(inheritance);
			theChildModifiers.put(child.getDeclared(), childModifier(child, type, copy, min, max));
			return this;
		}

		/**
		 * Creates a child modifier
		 * 
		 * @param child The child to modify
		 * @param type The type restriction for the child
		 * @param inheritance The additional inheritance for the child
		 * @param min The minimum number of children of the given role that must be specified for {@link QonfigElement}s of this item's type
		 * @param max The maximum number of children of the given role that may be specified for {@link QonfigElement}s of this item's type
		 * @return The child modifier to use
		 */
		protected abstract ChildDefModifier childModifier(QonfigChildDef.Declared child, QonfigElementDef type,
			Set<QonfigAddOn> inheritance, Integer min, Integer max);

		/** @return The built item. The item may not be completely filled-in. */
		public QonfigElementOrAddOn get() {
			if (theBuilt != null)
				return theBuilt;
			checkStage(Stage.NewAttributes);
			theBuilt = build();
			return theBuilt;
		}

		/**
		 * Builds the item. This is called after declared extension/inheritance and value and before any attributes or children are
		 * declared.
		 * 
		 * @return The built item
		 */
		protected abstract QonfigElementOrAddOn build();

		private void validate() {
			boolean valueModified = theValueModifier != null;
			QonfigAddOn textModSource = null;
			Map<QonfigAttributeDef.Declared, QonfigAddOn> attrModSource = new HashMap<>();
			for (QonfigAddOn inh : theInheritance) {
				// Validate value
				if (inh.getValueModifier() != null) {
					if (theValueModifier == null) {
						textModSource = inh;
						theValueModifier = inh.getValueModifier();
					} else if (valueModified) {
						QonfigValidation.validateSpecification( //
							new ValueSpec(theSuperElement.getValue().getType(), inh.getValueModifier().getSpecification(),
								inh.getValueModifier().getDefaultValue()), //
							new ValueSpec(theValueModifier.getTypeRestriction(), theValueModifier.getSpecification(),
								theValueModifier.getDefaultValue()), //
							err -> theSession.forChild("text", null).withError(err), //
							warn -> theSession.forChild("text", null).withWarning(warn));
					} else if (theValueModifier.getSpecification() != inh.getValueModifier().getSpecification()
						|| !Objects.equals(theValueModifier.getDefaultValue(), inh.getValueModifier().getDefaultValue())) {
						theSession.forChild("text", null).withError("Inherited add-ons " + textModSource + " and " + inh
							+ " specify different text modifications. " + theName + " must specify text modification explicitly.");
					}
				}
				// Validate attribute modifications
				for (Map.Entry<QonfigAttributeDef.Declared, ValueModifier> attr : inh.getAttributeModifiers().entrySet()) {
					ValueDefModifier own = theAttributeModifiers.get(attr.getKey());
					if (own == null) {
						attrModSource.put(attr.getKey(), inh);
						theAttributeModifiers.put(attr.getKey(), attr.getValue());
					} else if (theAttributeModifierOrigSpecs.containsKey(attr.getKey())) {
						QonfigValidation.validateSpecification( //
							theAttributeModifierOrigSpecs.get(attr.getKey()), //
							new ValueSpec(own.getTypeRestriction(), own.getSpecification(), own.getDefaultValue()), //
							err -> theSession.forChild("attribute", attr.getKey().getOwner() + "." + attr.getKey().getName())
								.withError(err), //
							warn -> theSession.forChild("attribute", attr.getKey().getOwner() + "." + attr.getKey().getName())
								.withWarning(warn));
					} else {
						ValueDefModifier preMod = theAttributeModifiers.get(attr.getKey());
						if (preMod.getSpecification() != attr.getValue().getSpecification()
							|| !Objects.equals(preMod.getDefaultValue(), attr.getValue().getDefaultValue()))
							theSession.forChild("attribute", attr.getKey().getOwner() + "." + attr.getKey().getName())
								.withError("Inherited add-ons " + attrModSource.get(attr.getKey()) + " and " + inh
									+ " specify different modifications. " + theName + " must specify attribute modification explicitly.");
					}
				}
			}
			if (theSuperElement != null) {
				// Validate child modifications
				for (Map.Entry<QonfigChildDef.Declared, QonfigChildDef> child : theSuperElement.getAllChildren().entrySet()) {
					Map<QonfigAddOn, String> inheritanceChildren = new HashMap<>();
					for (QonfigAddOn inh : theInheritance) {
						QonfigAddOn.ChildModifier chMod = inh.getChildModifiers().get(child.getKey());
						if (chMod != null)
							for (QonfigAddOn inh2 : chMod.getInheritance())
								inheritanceChildren.put(inh, inh.getName() + '/' + inh2.getName());
					}
					QonfigValidation.validateChild(//
						theSuperElement.getAllChildren().get(child.getKey()), //
						inheritanceChildren, //
						theChildModifiers.get(child.getKey()),
						new StringBuilder().append(child.getKey().getOwner() + "." + child.getKey().getName()), theSession);
				}
			}

			for (Map.Entry<QonfigChildDef.Declared, ? extends ChildDefModifier> mod : get().getChildModifiers().entrySet()) {
				QonfigChildDef.Modified modified = new QonfigChildDef.Modified(mod.getKey(), get(), //
					mod.getValue().getTypeRestriction() != null ? mod.getValue().getTypeRestriction() : mod.getKey().getType(), //
					mod.getValue().getInheritance(), //
					mod.getValue().getMin() != null ? mod.getValue().getMin() : mod.getKey().getMin(), //
					mod.getValue().getMax() != null ? mod.getValue().getMax() : mod.getKey().getMax());
				theCompiledChildren.put(mod.getKey(), modified);
				theChildrenByName.add(mod.getKey().getName(), modified);
			}
			if (theSuperElement != null) {
				for (Map.Entry<QonfigChildDef.Declared, QonfigChildDef> child : theSuperElement.getAllChildren().entrySet()) {
					if (theCompiledChildren.containsKey(child.getKey()))
						continue;
					QonfigElementDef type = child.getValue().getType();
					Set<QonfigAddOn> inheritance = new LinkedHashSet<>(child.getValue().getInheritance());
					int min = child.getValue().getMin();
					int max = child.getValue().getMax();
					boolean modified = false;
					for (QonfigAddOn inh : theInheritance) {
						ChildDefModifier mod = inh.getChildModifiers().get(child.getKey());
						if (mod == null)
							continue;
						if (mod.getTypeRestriction() != null && !mod.getTypeRestriction().isAssignableFrom(type)) {
							if (!type.isAssignableFrom(mod.getTypeRestriction()))
								theSession.withError("Inherited child " + child.getKey() + " has incompatible inherited type restrictions: "
									+ type + " and " + mod.getTypeRestriction());
							else {
								type = mod.getTypeRestriction();
								modified = true;
							}
						}
						if (mod.getMin() != null && mod.getMin() > min) {
							min = mod.getMin();
							modified = true;
						}
						if (mod.getMax() != null && mod.getMax() < max) {
							max = mod.getMax();
							modified = true;
						}
						for (QonfigAddOn childInh : mod.getInheritance()) {
							boolean newInh = !type.isAssignableFrom(childInh);
							if (newInh) {
								for (QonfigAddOn childInh2 : inheritance) {
									newInh = !childInh2.isAssignableFrom(childInh);
									if (!newInh)
										break;
								}
							}
							if (newInh) {
								inheritance.add(childInh);
								modified = true;
							}
						}
					}
					if (min > max) {
						theSession.withError(
							"Inherited child " + child.getKey() + " has incompatible inherited min/max restrictions: " + min + "..." + max);
					} else if (!modified)
						theCompiledChildren.put(child.getKey(), child.getValue());
					else
						theCompiledChildren.put(child.getKey(),
							new QonfigChildDef.Modified(child.getKey(), get(), type, inheritance, min, max));
					theChildrenByName.add(child.getKey().getName(), theCompiledChildren.get(child.getKey()));
				}
			}
			for (QonfigAddOn inh : theFullInheritance.getExpanded(QonfigAddOn::getInheritance)) {
				for (QonfigChildDef.Declared child : inh.getDeclaredChildren().values()) {
					if (theCompiledChildren.containsKey(child))
						continue;
					QonfigElementDef type = child.getType();
					Set<QonfigAddOn> inheritance = new LinkedHashSet<>(child.getInheritance());
					int min = child.getMin();
					int max = child.getMax();
					boolean modified = false;
					if (theSuperElement != null && theSuperElement.getFullInheritance().contains(inh)) {
						ChildDefModifier mod = theSuperElement.getChildModifiers().get(child);
						if (mod == null)
							continue;
						if (mod.getTypeRestriction() != null && !mod.getTypeRestriction().isAssignableFrom(type)) {
							if (!type.isAssignableFrom(mod.getTypeRestriction()))
								theSession.withError("Inherited child " + child + " has incompatible inherited type restrictions: " + type
									+ " and " + mod.getTypeRestriction());
							else {
								type = mod.getTypeRestriction();
								modified = true;
							}
						}
						if (mod.getMin() != null && mod.getMin() > min) {
							min = mod.getMin();
							modified = true;
						}
						if (mod.getMax() != null && mod.getMax() < max) {
							max = mod.getMax();
							modified = true;
						}
						for (QonfigAddOn childInh : mod.getInheritance()) {
							boolean newInh = !type.isAssignableFrom(childInh);
							if (newInh) {
								for (QonfigAddOn childInh2 : inheritance) {
									newInh = !childInh2.isAssignableFrom(childInh);
									if (!newInh)
										break;
								}
							}
							if (newInh) {
								inheritance.add(childInh);
								modified = true;
							}
						}
					}
					for (QonfigAddOn inh2 : theInheritance) {
						ChildDefModifier mod = inh2.getChildModifiers().get(child);
						if (mod == null)
							continue;
						if (mod.getTypeRestriction() != null && !mod.getTypeRestriction().isAssignableFrom(type)) {
							if (!type.isAssignableFrom(mod.getTypeRestriction()))
								theSession.withError("Inherited child " + child + " has incompatible inherited type restrictions: " + type
									+ " and " + mod.getTypeRestriction());
							else {
								type = mod.getTypeRestriction();
								modified = true;
							}
						}
						if (mod.getMin() != null && mod.getMin() > min) {
							min = mod.getMin();
							modified = true;
						}
						if (mod.getMax() != null && mod.getMax() < max) {
							max = mod.getMax();
							modified = true;
						}
						for (QonfigAddOn childInh : mod.getInheritance()) {
							boolean newInh = !type.isAssignableFrom(childInh);
							if (newInh) {
								for (QonfigAddOn childInh2 : inheritance) {
									newInh = !childInh2.isAssignableFrom(childInh);
									if (!newInh)
										break;
								}
							}
							if (newInh) {
								inheritance.add(childInh);
								modified = true;
							}
						}
					}
					if (min > max) {
						theSession.withError(
							"Inherited child " + child + " has incompatible inherited min/max restrictions: " + min + "..." + max);
					} else if (modified)
						theCompiledChildren.put(child, new QonfigChildDef.Modified(child, get(), type, inheritance, min, max));
					else
						theCompiledChildren.put(child, child);
					theChildrenByName.add(child.getName(), theCompiledChildren.get(child));
				}
			}
		}

		@Override
		public String toString() {
			return theName;
		}
	}
}

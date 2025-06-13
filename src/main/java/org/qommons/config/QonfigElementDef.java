package org.qommons.config;

import java.util.Map;
import java.util.Set;

import org.qommons.MultiInheritanceSet;
import org.qommons.collect.BetterMultiMap;
import org.qommons.config.QonfigAttributeDef.Declared;
import org.qommons.io.LocatedPositionedContent;
import org.qommons.io.PositionedContent;

/** The definition of an element that can be declared in a document */
public class QonfigElementDef extends QonfigElementOrAddOn {
	private final QonfigValueDef theValue;

	/**
	 * @param declarer The toolkit declaring this type
	 * @param name The name of this type
	 * @param superElement The element that this type extends
	 * @param inheritance Add-ons that this type is declared to inherit
	 * @param isAbstract Whether this type is abstract
	 * @param declaredAttributes Attribute definitions declared on this type
	 * @param attributeModifiers Attribute modifiers declared on this type to affect values that can be specified for inherited attributes
	 * @param allAttributes All attributes specifiable on this type, by their declaration
	 * @param attributesByName All attributes specifiable on this type, by name
	 * @param declaredChildren Child roles declared on this type
	 * @param childModifiers Child modifiers declared on this type to affect elements that can fulfill inherited roles
	 * @param allChildren All child roles specifiable on this type, by their declaration
	 * @param childrenByName All child roles specifiable on this type, by name
	 * @param value The definition for the element value that can be declared on this type
	 * @param fullInheritance The full add-on inheritance of this type
	 * @param metaSpec The metadata specification for this type
	 * @param position The position of this element's declaration in its source file
	 * @param description The documentation description of this element
	 */
	protected QonfigElementDef(QonfigToolkit declarer, String name, QonfigElementDef superElement, Set<QonfigAddOn> inheritance,
		boolean isAbstract, //
		Map<String, QonfigAttributeDef.Declared> declaredAttributes, Map<QonfigAttributeDef.Declared, ValueDefModifier> attributeModifiers,
		Map<QonfigAttributeDef.Declared, QonfigAttributeDef> allAttributes, BetterMultiMap<String, QonfigAttributeDef> attributesByName, //
		Map<String, QonfigChildDef.Declared> declaredChildren, Map<QonfigChildDef.Declared, ChildDefModifier> childModifiers,
		Map<QonfigChildDef.Declared, QonfigChildDef> allChildren, BetterMultiMap<String, QonfigChildDef> childrenByName, //
		ValueDefModifier value, MultiInheritanceSet<QonfigAddOn> fullInheritance, QonfigElementDef metaSpec, PositionedContent position,
		String description) {
		super(declarer, name, isAbstract, superElement, inheritance, fullInheritance, declaredAttributes, attributeModifiers,
			attributesByName, allAttributes, declaredChildren, childModifiers, childrenByName, allChildren, value, metaSpec, position,
			description);

		if (value == null)
			theValue = superElement == null ? null : superElement.getValue();
		else if (superElement == null || superElement.getValue() == null)
			theValue = new QonfigValueDef.DeclaredValueDef(this, value.getTypeRestriction(), value.getSpecification(),
				value.getDefaultValue(), value.getDefaultValueContent(), null, value.getDescription());
		else {
			QonfigValidation.ValueSpec spec = QonfigValidation.validateSpecification(//
				new QonfigValidation.ValueSpec(superElement.getValue().getType(), superElement.getValue().getSpecification(),
					superElement.getValue().getDefaultValue(), null, superElement.getValue().getDefaultValueContent()), //
				new QonfigValidation.ValueSpec(value.getTypeRestriction(), value.getSpecification(), value.getDefaultValue(), null,
					value.getDefaultValueContent()), //
				__ -> {
				}, __ -> {
				}, false);
			theValue = new QonfigValueDef.Modified(superElement.getValue(), this, spec.type, spec.specification, spec.defaultValue,
				spec.defaultValueContent, null, value.getDescription());
		}
	}

	@Override
	public Map<QonfigAttributeDef.Declared, ValueDefModifier> getAttributeModifiers() {
		return (Map<Declared, ValueDefModifier>) super.getAttributeModifiers();
	}

	@Override
	public Map<QonfigChildDef.Declared, ChildDefModifier> getChildModifiers() {
		return (Map<org.qommons.config.QonfigChildDef.Declared, ChildDefModifier>) super.getChildModifiers();
	}

	@Override
	public QonfigValueDef getValue() {
		return theValue;
	}

	@Override
	public QonfigElementDef getMetaSpec() {
		return (QonfigElementDef) super.getMetaSpec();
	}

	@Override
	public boolean isAssignableFrom(QonfigElementOrAddOn other) {
		QonfigElementOrAddOn el = other;
		while (el != null) {
			if (equals(el))
				return true;
			el = el.getSuperElement();
		}
		if (other instanceof QonfigPromiseDef) {
			QonfigPromiseDef promise = (QonfigPromiseDef) other;
			if (promise.getPromisedType() != null && isAssignableFrom(promise.getPromisedType()))
				return true;
			for (QonfigAddOn inh : promise.getPromisedInheritance().values()) {
				if (isAssignableFrom(inh))
					return true;
			}
		}
		return false;
	}

	/**
	 * @param name The name for the element type
	 * @param session The session for error reporting
	 * @param promise Whether the new type is a promise
	 * @param description The description for the new element
	 * @return A builder to build an element-def
	 */
	public static Builder build(String name, QonfigParseSession session, boolean promise, String description) {
		return new Builder(name, session, promise, description);
	}

	/** Builds element-defs */
	public static class Builder extends QonfigElementOrAddOn.Builder {
		private boolean isPromise;
		private QonfigPromiseFulfillment theFulfillment;
		private QonfigElementDef thePromisedType;
		private MultiInheritanceSet<QonfigAddOn> thePromisedInheritance;

		Builder(String name, QonfigParseSession session, boolean promise, String description) {
			super(name, session, description);
			isPromise = promise;
		}

		@Override
		public QonfigElementDef get() {
			return (QonfigElementDef) super.get();
		}

		/** @return Whether this builder is building a promise type */
		public boolean isPromise() {
			return isPromise;
		}

		/**
		 * @param fulfillment The fulfillment for this promise
		 * @param promisedType The element type promised by the promise
		 * @param inheritance The inheritance promised by the promise
		 * @return This builder
		 */
		public Builder fulfillPromise(QonfigPromiseFulfillment fulfillment, QonfigElementDef promisedType,
			MultiInheritanceSet<QonfigAddOn> inheritance) {
			if (!isPromise)
				throw new IllegalStateException("This element is not a promise");
			else if (!checkStage(Stage.Initial))
				throw new IllegalStateException("Promise fulfillment cannot be set at this stage: " + getStage());
			else if (thePromisedType != null)
				throw new IllegalStateException("Promise fulfillment has already been set");
			theFulfillment = fulfillment;
			if (thePromisedType != null && !thePromisedType.isAssignableFrom(promisedType))
				theSession.error(getSuperElement() + " promises " + thePromisedType + ". An extension's promise type must extend this, and "
					+ thePromisedType + " does not");
			else
				thePromisedType = promisedType;
			thePromisedInheritance = inheritance;
			for (QonfigAddOn addOn : inheritance.values()) {
				if (addOn.getSuperElement() != null) {
					if (thePromisedType != null && addOn.getSuperElement().isAssignableFrom(thePromisedType)) {//
					} else if (thePromisedType == null) {
						// For add-ons, just inherit this element. For elements it must be declared.
						thePromisedType = addOn.getSuperElement();
					} else
						theSession.error("Illegal promised inheritance: " + theName + " <- " + addOn + ": super element "
							+ addOn.getSuperElement() + " incompatible with " + theName);
				}
			}
			return this;
		}

		@Override
		public Builder setSuperElement(QonfigElementDef superElement) {
			super.setSuperElement(superElement);
			if (superElement instanceof QonfigPromiseDef) {
				QonfigPromiseDef superPromise = (QonfigPromiseDef) superElement;
				if (!isPromise) {
					theSession.error("An extension of " + superPromise + " must be a promise");
					return this;
				}
				thePromisedType = superPromise.getPromisedType();
			}
			return this;
		}

		@Override
		protected ValueDefModifier valueModifier(QonfigValueType type, SpecificationType specification, Object defaultValue,
			LocatedPositionedContent namePosition, LocatedPositionedContent defaultValueContent, String description,
			PositionedContent position) {
			return new ValueDefModifier.Default(getSession().getToolkit(), type, specification, defaultValue, namePosition,
				defaultValueContent, description, position);
		}

		@Override
		protected ChildDefModifier childModifier(QonfigChildDef.Declared child, QonfigElementDef type, Set<QonfigAddOn> inheritance,
			Set<QonfigAddOn> requirement, Integer min, Integer max, PositionedContent position, String description) {
			QonfigChildDef override = getCompiledChildren().get(child.getDeclared());
			if (override instanceof QonfigChildDef.Overridden) {
				theSession.at(position).error("Child has been overridden by " + ((QonfigChildDef.Overridden) override).getOverriding());
				return null;
			}
			return new ChildDefModifier.Default(type, inheritance, requirement, min, max, position, description);
		}

		@Override
		protected boolean hasValue() {
			return super.hasValue() || (thePromisedType != null && thePromisedType.getValue() != null);
		}

		@Override
		protected boolean isAssignableTo(QonfigElementOrAddOn type) {
			if (super.isAssignableTo(type))
				return true;
			else if (thePromisedType != null && thePromisedType != null && type.isAssignableFrom(thePromisedType))
				return true;
			else if (type instanceof QonfigAddOn && thePromisedInheritance != null && thePromisedInheritance.contains((QonfigAddOn) type))
				return true;
			else
				return false;
		}

		@Override
		protected QonfigElementDef create() {
			if (isPromise) {
				if (theFulfillment == null)
					theSession.error("Promise fulfillment has not been installed");
				return new QonfigPromiseDef(theSession.getToolkit(), getName(), getSuperElement(), getInheritance(), isAbstract(), //
					getDeclaredAttributes(), (Map<QonfigAttributeDef.Declared, ValueDefModifier>) super.getAttributeModifiers(),
					getCompiledAttributes(), getAttributesByName(), //
					getDeclaredChildren(), (Map<QonfigChildDef.Declared, ChildDefModifier>) super.getChildModifiers(),
					getCompiledChildren(), getChildrenByName(), //
					super.getValue(), getFullInheritance(), //
					super.getMetaSpec() == null ? null : (QonfigElementDef) super.getMetaSpec().get(), getSession().getFileLocation(),
					getDescription(), theFulfillment, thePromisedType, MultiInheritanceSet.unmodifiable(thePromisedInheritance));
			} else {
				return new QonfigElementDef(theSession.getToolkit(), getName(), getSuperElement(), getInheritance(), isAbstract(), //
					getDeclaredAttributes(), (Map<QonfigAttributeDef.Declared, ValueDefModifier>) super.getAttributeModifiers(),
					getCompiledAttributes(), getAttributesByName(), //
					getDeclaredChildren(), (Map<QonfigChildDef.Declared, ChildDefModifier>) super.getChildModifiers(),
					getCompiledChildren(), getChildrenByName(), //
					super.getValue(), getFullInheritance(), //
					super.getMetaSpec() == null ? null : (QonfigElementDef) super.getMetaSpec().get(), getSession().getFileLocation(),
					getDescription());
			}
		}
	}
}

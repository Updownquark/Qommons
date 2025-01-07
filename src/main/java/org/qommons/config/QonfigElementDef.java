package org.qommons.config;

import java.util.Map;
import java.util.Set;

import org.qommons.MultiInheritanceSet;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterMultiMap;
import org.qommons.config.QonfigAttributeDef.Declared;
import org.qommons.io.LocatedPositionedContent;
import org.qommons.io.PositionedContent;

/** The definition of an element that can be declared in a document */
public class QonfigElementDef extends QonfigElementOrAddOn {
	/** The name of the default reference toolkit */
	public static final String QONFIG_REFERENCE_TK = "Qonfig-Reference";
	/** The name of the promise element in the default reference toolkit */
	public static final String QONFIG_PROMISE_ELEMENT = "promise";
	/** The name of the promised type attribute in the promise element in the default reference toolkit */
	public static final String QONFIG_EXT_PROMISED_TYPE_ATTR = "promised";
	/** The name of the promised inheritance attribute in the promise element in the default reference toolkit */
	public static final String QONFIG_EXT_PROMISED_INH_ATTR = "promised-inheritance";

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
					superElement.getValue().getDefaultValue(), superElement.getValue().getDefaultValueContent()), //
				new QonfigValidation.ValueSpec(value.getTypeRestriction(), value.getSpecification(), value.getDefaultValue(),
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
	 * @param description The description for the new element
	 * @return A builder to build an element-def
	 */
	public static Builder build(String name, QonfigParseSession session, String description) {
		return new Builder(name, session, description);
	}

	/** Builds element-defs */
	public static class Builder extends QonfigElementOrAddOn.Builder {
		private boolean isPromise;
		private ValueHolder<QonfigElementDef> thePromisedType;
		private MultiInheritanceSet<QonfigAddOn> thePromisedInheritance;

		Builder(String name, QonfigParseSession session, String description) {
			super(name, session, description);
			if (session.getToolkit().getName().equals(QONFIG_REFERENCE_TK) && name.equals(QONFIG_PROMISE_ELEMENT)) {
				isPromise = true;
				thePromisedType = new ValueHolder<>();
				thePromisedInheritance = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
			}
		}

		@Override
		public QonfigElementDef get() {
			return (QonfigElementDef) super.get();
		}

		@Override
		public Builder setSuperElement(QonfigElementDef superElement) {
			super.setSuperElement(superElement);
			if (superElement instanceof QonfigPromiseDef) {
				isPromise = true;
				thePromisedType = new ValueHolder<>(((QonfigPromiseDef) superElement).getPromisedType());
				thePromisedInheritance = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
				thePromisedInheritance.addAll(((QonfigPromiseDef) superElement).getPromisedInheritance().values());
			}
			return this;
		}

		@Override
		public Builder modifyAttribute(QonfigAttributeDef attribute, QonfigValueType type, SpecificationType specification,
			Object defaultValue, LocatedPositionedContent defaultValueContent, PositionedContent position, String description) {
			super.modifyAttribute(attribute, type, specification, defaultValue, defaultValueContent, position, description);
			if (defaultValue != null && isPromise && attribute.getDeclared().getDeclarer().getName().equals(QONFIG_REFERENCE_TK)
				&& attribute.getDeclared().getOwner().getName().equals(QONFIG_PROMISE_ELEMENT)) {
				switch (attribute.getName()) {
				case QONFIG_EXT_PROMISED_TYPE_ATTR:
					QonfigValueType.QonfigTypeReference<QonfigElementDef> promised = (QonfigValueType.QonfigTypeReference<QonfigElementDef>) defaultValue;
					if (thePromisedType.get() != null && !thePromisedType.get().isAssignableFrom(promised.reference)) {
						theSession.at(promised.content)
							.error(promised.reference + " does not extend " + thePromisedType.get() + ", promised by " + getSuperElement());
					} else
						thePromisedType.accept(promised.reference);
					break;
				case QONFIG_EXT_PROMISED_INH_ATTR:
					Set<QonfigValueType.QonfigTypeReference<QonfigAddOn>> inh = (Set<QonfigValueType.QonfigTypeReference<QonfigAddOn>>) defaultValue;
					for (QonfigValueType.QonfigTypeReference<QonfigAddOn> ao : inh) {
						if (!thePromisedInheritance.add(ao.reference)) {
							theSession.at(ao.content).warn(ao.reference + " is a duplicate");
						}
					}
					break;
				}
			}
			return this;
		}

		@Override
		protected ValueDefModifier valueModifier(QonfigValueType type, SpecificationType specification, Object defaultValue,
			LocatedPositionedContent defaultValueContent, String description, PositionedContent position) {
			return new ValueDefModifier.Default(getSession().getToolkit(), type, specification, defaultValue, defaultValueContent,
				description, position);
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
			return super.hasValue() || (thePromisedType.get() != null && thePromisedType.get().getValue() != null);
		}

		@Override
		protected boolean isAssignableTo(QonfigElementOrAddOn type) {
			if (super.isAssignableTo(type))
				return true;
			else if (thePromisedType != null && thePromisedType.get() != null && type.isAssignableFrom(thePromisedType.get()))
				return true;
			else if (type instanceof QonfigAddOn && thePromisedInheritance != null && thePromisedInheritance.contains((QonfigAddOn) type))
				return true;
			else
				return false;
		}

		@Override
		protected QonfigElementDef create() {
			if (isPromise) {
				return new QonfigPromiseDef(theSession.getToolkit(), getName(), getSuperElement(), getInheritance(), isAbstract(), //
					getDeclaredAttributes(), (Map<QonfigAttributeDef.Declared, ValueDefModifier>) super.getAttributeModifiers(),
					getCompiledAttributes(), getAttributesByName(), //
					getDeclaredChildren(), (Map<QonfigChildDef.Declared, ChildDefModifier>) super.getChildModifiers(),
					getCompiledChildren(), getChildrenByName(), //
					super.getValue(), getFullInheritance(), //
					super.getMetaSpec() == null ? null : (QonfigElementDef) super.getMetaSpec().get(), getSession().getFileLocation(),
					getDescription(), thePromisedType, MultiInheritanceSet.unmodifiable(thePromisedInheritance));
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

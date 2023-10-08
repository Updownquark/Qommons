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

	private final QonfigElementOrAddOn thePromise;

	private QonfigElementDef(QonfigToolkit declarer, String name, QonfigElementDef superElement, Set<QonfigAddOn> inheritance,
		boolean isAbstract, QonfigElementOrAddOn promise, //
		Map<String, QonfigAttributeDef.Declared> declaredAttributes, Map<QonfigAttributeDef.Declared, ValueDefModifier> attributeModifiers,
		Map<QonfigAttributeDef.Declared, QonfigAttributeDef> allAttributes, BetterMultiMap<String, QonfigAttributeDef> attributesByName, //
		Map<String, QonfigChildDef.Declared> declaredChildren, Map<QonfigChildDef.Declared, ChildDefModifier> childModifiers,
		Map<QonfigChildDef.Declared, QonfigChildDef> allChildren, BetterMultiMap<String, QonfigChildDef> childrenByName, //
		ValueDefModifier value, MultiInheritanceSet<QonfigAddOn> fullInheritance, QonfigElementDef metaSpec, PositionedContent position,
		String description) {
		super(declarer, name, isAbstract, superElement, inheritance, fullInheritance, declaredAttributes, attributeModifiers,
			attributesByName, allAttributes, declaredChildren, childModifiers, childrenByName, allChildren, value, metaSpec, position,
			description);

		thePromise = promise;
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
				});
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
		private QonfigElementOrAddOn thePromise;

		Builder(String name, QonfigParseSession session, String description) {
			super(name, session, description);
		}

		@Override
		public QonfigElementDef get() {
			return (QonfigElementDef) super.get();
		}

		public QonfigElementOrAddOn getPromise() {
			return thePromise;
		}

		public Builder promise(QonfigElementOrAddOn promise, PositionedContent position) {
			if (thePromise != null)
				getSession().at(position).error("Multiple promises declared");
			else
				thePromise = promise;
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
		protected QonfigElementOrAddOn create() {
			return new QonfigElementDef(theSession.getToolkit(), getName(), getSuperElement(), getInheritance(), isAbstract(), thePromise, //
				getDeclaredAttributes(), (Map<QonfigAttributeDef.Declared, ValueDefModifier>) super.getAttributeModifiers(),
				getCompiledAttributes(), getAttributesByName(), //
				getDeclaredChildren(), (Map<QonfigChildDef.Declared, ChildDefModifier>) super.getChildModifiers(), getCompiledChildren(),
				getChildrenByName(), //
				super.getValue(), getFullInheritance(), //
				super.getMetaSpec() == null ? null : (QonfigElementDef) super.getMetaSpec().get(), getSession().getFileLocation(),
				getDescription());
		}
	}
}

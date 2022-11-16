package org.qommons.config;

import java.util.Map;
import java.util.Set;

import org.qommons.MultiInheritanceSet;
import org.qommons.collect.BetterMultiMap;
import org.qommons.config.QonfigAttributeDef.Declared;

/** The definition of an element that can be declared in a document */
public class QonfigElementDef extends QonfigElementOrAddOn {
	private final Map<QonfigAttributeDef.Declared, QonfigAttributeDef> theCompiledAttributes;
	private final Map<QonfigChildDef.Declared, QonfigChildDef> theCompiledChildren;

	private final QonfigValueDef theValue;

	private QonfigElementDef(QonfigToolkit declarer, String name, QonfigElementDef superElement, Set<QonfigAddOn> inheritance,
		boolean isAbstract, //
		Map<String, QonfigAttributeDef.Declared> declaredAttributes, Map<QonfigAttributeDef.Declared, ValueDefModifier> attributeModifiers,
		Map<QonfigAttributeDef.Declared, QonfigAttributeDef> allAttributes, BetterMultiMap<String, QonfigAttributeDef> attributesByName, //
		Map<String, QonfigChildDef.Declared> declaredChildren, Map<QonfigChildDef.Declared, ChildDefModifier> childModifiers,
		Map<QonfigChildDef.Declared, QonfigChildDef> allChildren, BetterMultiMap<String, QonfigChildDef> childrenByName, //
		ValueDefModifier value, MultiInheritanceSet<QonfigAddOn> fullInheritance, QonfigElementDef metaSpec, int lineNumber) {
		super(declarer, name, isAbstract, superElement, inheritance, fullInheritance, declaredAttributes, attributeModifiers,
			attributesByName, declaredChildren, childModifiers, childrenByName, value, metaSpec, lineNumber);
		theCompiledAttributes = allAttributes;
		theCompiledChildren = allChildren;

		if (value == null)
			theValue = superElement == null ? null : superElement.getValue();
		else if (superElement == null || superElement.getValue() == null)
			theValue = new QonfigValueDef.DeclaredValueDef(this, value.getTypeRestriction(), value.getSpecification(),
				value.getDefaultValue(), -1);
		else {
			QonfigValidation.ValueSpec spec = QonfigValidation.validateSpecification(//
				new QonfigValidation.ValueSpec(superElement.getValue().getType(), superElement.getValue().getSpecification(),
					superElement.getValue().getDefaultValue()), //
				new QonfigValidation.ValueSpec(value.getTypeRestriction(), value.getSpecification(), value.getDefaultValue()), //
				__ -> {
				}, __ -> {
				});
			theValue = new QonfigValueDef.Modified(superElement.getValue(), this, spec.type, spec.specification, spec.defaultValue, -1);
		}
	}

	@Override
	public Map<QonfigAttributeDef.Declared, ValueDefModifier> getAttributeModifiers() {
		return (Map<Declared, ValueDefModifier>) super.getAttributeModifiers();
	}

	/**
	 * @return All attributes defined in this element, its {@link #getSuperElement() super-type}, or its {@link #getInheritance()
	 *         inheritance}
	 */
	public Map<QonfigAttributeDef.Declared, QonfigAttributeDef> getAllAttributes() {
		return theCompiledAttributes;
	}

	@Override
	public Map<QonfigChildDef.Declared, ChildDefModifier> getChildModifiers() {
		return (Map<org.qommons.config.QonfigChildDef.Declared, ChildDefModifier>) super.getChildModifiers();
	}

	/**
	 * @return All children defined in this element, its {@link #getSuperElement() super-type}, or its {@link #getInheritance() inheritance}
	 */
	public Map<QonfigChildDef.Declared, QonfigChildDef> getAllChildren() {
		return theCompiledChildren;
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
		if (!(other instanceof QonfigElementDef))
			return false;
		QonfigElementDef el = (QonfigElementDef) other;
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
	 * @return A builder to build an element-def
	 */
	public static Builder build(String name, QonfigParseSession session) {
		return new Builder(name, session);
	}

	/** Builds element-defs */
	public static class Builder extends QonfigElementOrAddOn.Builder {
		Builder(String name, QonfigParseSession session) {
			super(name, session);
		}

		@Override
		public QonfigElementDef get() {
			return (QonfigElementDef) super.get();
		}

		@Override
		protected ValueDefModifier valueModifier(QonfigValueType type, SpecificationType specification, Object defaultValue) {
			return new ValueDefModifier.Default(type, specification, defaultValue);
		}

		@Override
		protected ChildDefModifier childModifier(QonfigChildDef.Declared child, QonfigElementDef type, Set<QonfigAddOn> inheritance,
			Set<QonfigAddOn> requirement, Integer min, Integer max, int lineNumber) {
			QonfigChildDef override = getCompiledChildren().get(child.getDeclared());
			if (override instanceof QonfigChildDef.Overridden) {
				theSession.forChild("child-mod", child.getName(), lineNumber)
					.withError("Child has been overridden by " + ((QonfigChildDef.Overridden) override).getOverriding());
				return null;
			}
			return new ChildDefModifier.Default(type, inheritance, requirement, min, max, lineNumber);
		}

		@Override
		protected QonfigElementOrAddOn create() {
			return new QonfigElementDef(theSession.getToolkit(), getName(), getSuperElement(), getInheritance(), isAbstract(), //
				getDeclaredAttributes(), (Map<QonfigAttributeDef.Declared, ValueDefModifier>) getAttributeModifiers(),
				getCompiledAttributes(), getAttributesByName(), //
				getDeclaredChildren(), (Map<QonfigChildDef.Declared, ChildDefModifier>) getChildModifiers(), getCompiledChildren(),
				getChildrenByName(), //
				getValue(), getFullInheritance(), //
				getMetaSpec() == null ? null : (QonfigElementDef) getMetaSpec().get(), getSession().getPath().getLineNumber());
		}
	}
}

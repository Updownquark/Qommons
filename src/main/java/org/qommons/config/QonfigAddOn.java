package org.qommons.config;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.qommons.MultiInheritanceSet;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterMultiMap;

/** An add-on that can be applied to an element in various forms to alter its specification and behavior */
public class QonfigAddOn extends QonfigElementOrAddOn implements QonfigValueType, ElementDefModifier {
	/** A {@link ChildDefModifier} for add-ons */
	public static final class ChildModifier implements ChildDefModifier {
		private final Set<QonfigAddOn> theInheritance;
		private final Set<QonfigAddOn> theRequirement;

		/**
		 * @param inheritance The add-ons inherited by the child
		 * @param requirement The set of add-ons that an element must inherit to fulfill this role
		 */
		public ChildModifier(Set<QonfigAddOn> inheritance, Set<QonfigAddOn> requirement) {
			theInheritance = inheritance;
			theRequirement = requirement;
		}

		@Override
		public QonfigElementDef getTypeRestriction() {
			return null;
		}

		@Override
		public Set<QonfigAddOn> getInheritance() {
			return theInheritance;
		}

		@Override
		public Set<QonfigAddOn> getRequirement() {
			return theRequirement;
		}

		@Override
		public Map<String, QonfigAttributeDef.Declared> getDeclaredAttributes() {
			return Collections.emptyMap();
		}

		@Override
		public Map<QonfigAttributeDef.Declared, ValueDefModifier> getAttributeModifiers() {
			return Collections.emptyMap();
		}

		@Override
		public ValueDefModifier getValueModifier() {
			return null;
		}

		@Override
		public Integer getMin() {
			return null;
		}

		@Override
		public Integer getMax() {
			return null;
		}

		@Override
		public String toString() {
			return "+" + theInheritance;
		}
	}

	/** A {@link ValueDefModifier} for add-ons */
	public static final class ValueModifier implements ValueDefModifier {
		private final SpecificationType theSpecification;
		private final Object theDefaultValue;

		/**
		 * @param specification The specification type of the attribute or element value
		 * @param defaultValue The default value for the attribute or element value
		 */
		public ValueModifier(SpecificationType specification, Object defaultValue) {
			theDefaultValue = defaultValue;
			theSpecification = specification;
		}

		@Override
		public QonfigAddOn getTypeRestriction() {
			return null;
		}

		@Override
		public SpecificationType getSpecification() {
			return theSpecification;
		}

		@Override
		public Object getDefaultValue() {
			return theDefaultValue;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			if (theSpecification != null) {
				str.append(theSpecification);
				if (theDefaultValue != null)
					str.append(" (").append(theDefaultValue).append(')');
			} else if (theDefaultValue != null)
				str.append("default (" + theDefaultValue + ")");
			return str.toString();
		}
	}

	QonfigAddOn(QonfigToolkit declarer, String name, boolean isAbstract, QonfigElementDef requirement, Set<QonfigAddOn> inheritance,
		Map<String, QonfigAttributeDef.Declared> declaredAttributes, Map<QonfigAttributeDef.Declared, ValueModifier> attributeModifiers,
		BetterMultiMap<String, QonfigAttributeDef> attributesByName, //
		Map<String, QonfigChildDef.Declared> declaredChildren, Map<QonfigChildDef.Declared, ChildModifier> childModifiers,
		BetterMultiMap<String, QonfigChildDef> childrenByName, //
		ValueModifier value, MultiInheritanceSet<QonfigAddOn> fullInheritance, QonfigAddOn metaSpec) {
		super(declarer, name, isAbstract, requirement, inheritance, fullInheritance, declaredAttributes, attributeModifiers,
			attributesByName, declaredChildren, childModifiers, childrenByName, value, metaSpec);
	}

	/**
	 * @return Whether this add-on is abstract, meaning it cannot be applied to an element ad-hoc in a document, with the
	 *         {@link DefaultQonfigParser#DOC_ELEMENT_INHERITANCE_ATTR with-extension} attribute
	 */
	@Override
	public boolean isAbstract() {
		return super.isAbstract();
	}

	@Override
	public QonfigElementDef getTypeRestriction() {
		return null;
	}

	@Override
	public Set<QonfigAddOn> getRequirement() {
		return Collections.emptySet();
	}

	@Override
	public Map<QonfigAttributeDef.Declared, ValueModifier> getAttributeModifiers() {
		return (Map<org.qommons.config.QonfigAttributeDef.Declared, ValueModifier>) super.getAttributeModifiers();
	}

	@Override
	public ValueModifier getValueModifier() {
		return (ValueModifier) getValueSpec();
	}

	@Override
	public Map<QonfigChildDef.Declared, ChildModifier> getChildModifiers() {
		return (Map<QonfigChildDef.Declared, ChildModifier>) super.getChildModifiers();
	}

	@Override
	public QonfigAddOn getMetaSpec() {
		return (QonfigAddOn) super.getMetaSpec();
	}

	@Override
	public boolean isAssignableFrom(QonfigElementOrAddOn other) {
		if (equals(other))
			return true;
		else if (other.getFullInheritance().contains(this))
			return true;
		if (other.getFullInheritance().isEmpty()) { // Not filled out with, gotta do it the long way
			for (QonfigAddOn inh : other.getInheritance()) {
				if (isAssignableFrom(inh))
					return true;
			}
		}
		return getSuperElement() != null && getSuperElement().isAssignableFrom(other);
	}

	@Override
	public QonfigAddOn parse(String value, QonfigToolkit tk, QonfigParseSession session) {
		int colon = value.indexOf(':');
		QonfigAddOn found = null;
		if (colon >= 0) {
			String depName = value.substring(0, colon);
			QonfigToolkit dep = tk.getDependencies().get(depName);
			if (dep == null) {
				session.withError("No such dependency '" + depName + "'");
				return null;
			}
			String addOnName = value.substring(colon + 1);
			BetterCollection<QonfigAddOn> addOns = dep.getAllAddOns().get(addOnName);
			switch (addOns.size()) {
			case 1:
				found = addOns.getFirst();
				break;
			case 0:
				session.withError("No such add-on '" + addOnName + "' in toolkit " + depName + " (" + dep.getLocation() + ")");
				break;
			default:
				session.withError("Multiple add-ons named '" + addOnName + "' in toolkit " + depName + " (" + dep.getLocation() + ")");
			}
		} else {
			BetterCollection<QonfigAddOn> addOns = tk.getAllAddOns().get(value);
			switch (addOns.size()) {
			case 1:
				found = addOns.getFirst();
				break;
			case 0:
				session.withError("No such add-on '" + value + "' in toolkit " + tk.getLocation());
				break;
			default:
				session.withError("Multiple add-ons named '" + value + "' in toolkit " + tk.getLocation());
			}
		}
		if (found == null)
			return null;
		boolean ok = true;
		if (!isAssignableFrom(found)) {
			session.withError(value + " cannot be used as an instance of " + this);
			ok = false;
		}
		if (found.isAbstract()) {
			session.withError(value + " is abstract and cannot be specified as a value");
			ok = false;
		}
		return ok ? found : null;
	}

	@Override
	public boolean isInstance(Object value) {
		return value instanceof QonfigAddOn;
	}

	/**
	 * @param name The name for the add-on
	 * @param session The session for parsing
	 * @return The builder to create the add-on
	 */
	public static Builder build(String name, QonfigParseSession session) {
		return new Builder(name, session);
	}

	/** Creates add-ons */
	public static class Builder extends QonfigElementOrAddOn.Builder {
		Builder(String name, QonfigParseSession session) {
			super(name, session);
		}

		@Override
		public Builder setSuperElement(QonfigElementDef superElement) {
			super.setSuperElement(superElement);
			return this;
		}

		@Override
		protected QonfigAddOn create() {
			return new QonfigAddOn(theSession.getToolkit(), getName(), isAbstract(), getSuperElement(), getInheritance(), //
				getDeclaredAttributes(), (Map<QonfigAttributeDef.Declared, ValueModifier>) getAttributeModifiers(), getAttributesByName(), //
				getDeclaredChildren(), (Map<QonfigChildDef.Declared, ChildModifier>) getChildModifiers(), getChildrenByName(), //
				getValue(), getFullInheritance(), //
				getMetaSpec() == null ? null : (QonfigAddOn) getMetaSpec().get());
		}

		@Override
		protected ValueModifier getValue() {
			return (ValueModifier) super.getValue();
		}

		@Override
		public Builder withValue(QonfigValueType type, SpecificationType specification, Object defaultValue) {
			theSession.withError("Value cannot be specified by an add-on, only modified");
			return this;
		}

		@Override
		public Builder modifyValue(QonfigValueType type, SpecificationType specification, Object defaultValue) {
			if (getSuperElement() == null) {
				theSession.forChild("value", null).withError("No required element to modify the value for");
				return this;
			} else if (getSuperElement().getValue() == null) {
				theSession.forChild("value", null)
					.withError("Required element " + getSuperElement() + " does not specify a value to be modified");
				return this;
			} else if (type != null && !type.equals(getSuperElement().getValue().getType())) {
				theSession.forChild("value", null).withError("Value type cannot be modified by an add-on");
				type = null;
			}
			super.modifyValue(type, specification, defaultValue);
			return this;
		}

		@Override
		protected ValueModifier valueModifier(QonfigValueType type, SpecificationType specification, Object defaultValue) {
			return new ValueModifier(specification, defaultValue);
		}

		@Override
		public Builder withAttribute(String name, QonfigValueType type, SpecificationType specify, Object defaultValue) {
			super.withAttribute(name, type, specify, defaultValue);
			return this;
		}

		@Override
		public Builder modifyAttribute(QonfigAttributeDef attribute, QonfigValueType type, SpecificationType specification,
			Object defaultValue) {
			if (type != null && !type.equals(attribute.getType())) {
				theSession.forChild("attribute", attribute.getOwner().getName() + "." + attribute.getName())
					.withError("Attribute type cannot be modified by an add-on");
				type = null;
			}
			super.modifyAttribute(attribute, type, specification, defaultValue);
			return this;
		}

		@Override
		public Builder withChild(String name, QonfigElementDef type, Set<QonfigChildDef.Declared> fulfillment, Set<QonfigAddOn> inheritance,
			Set<QonfigAddOn> requirement, int min, int max) {
			if (!fulfillment.isEmpty()) {
				theSession.forChild("child-def", name).withError("Children of add-ons cannot fulfill roles");
				fulfillment = Collections.emptySet();
			}
			super.withChild(name, type, fulfillment, inheritance, requirement, min, max);
			return this;
		}

		@Override
		public Builder modifyChild(QonfigChildDef.Declared child, QonfigElementDef type, Set<QonfigAddOn> inheritance,
			Set<QonfigAddOn> requirement, Integer min,
			Integer max) {
			if (type != null && !type.equals(child.getType())) {
				theSession.forChild("child-mod", child.getOwner().getName() + "." + child.getName())
					.withError("Child min/max cannot be modified by an add-on");
			} else if (min != null || max != null) {
				theSession.forChild("child-mod", child.getOwner().getName() + "." + child.getName())
					.withError("Child type cannot be modified by an add-on");
			}
			super.modifyChild(child, type, inheritance, requirement, min, max);
			return this;
		}

		@Override
		protected ChildDefModifier childModifier(QonfigChildDef.Declared child, QonfigElementDef type, Set<QonfigAddOn> inheritance,
			Set<QonfigAddOn> requirement, Integer min, Integer max) {
			return new ChildModifier(inheritance, requirement);
		}

		@Override
		public QonfigAddOn get() {
			return (QonfigAddOn) super.get();
		}
	}
}

package org.qommons.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.qommons.MultiInheritanceSet;
import org.qommons.config.QonfigAddOn.ChildModifier;
import org.qommons.config.QonfigAddOn.ValueModifier;
import org.qommons.io.LocatedPositionedContent;

/** Provides validation for various toolkit constraints */
public class QonfigValidation {
	/** Simple structure for attribute or element text value specifications */
	public static class ValueSpec {
		/** The type that must be specified */
		public final QonfigValueType type;
		/** The specification of the value */
		public final SpecificationType specification;
		/** The value to use if it is not specified */
		public final Object defaultValue;
		public final LocatedPositionedContent defaultValueContent;

		/**
		 * @param type The type that must be specified
		 * @param specify The specification of the value
		 * @param defaultValue The value to use if it is not specified
		 */
		public ValueSpec(QonfigValueType type, SpecificationType specify, Object defaultValue,
			LocatedPositionedContent defaultValueContent) {
			this.type = type;
			this.specification = specify;
			this.defaultValue = defaultValue;
			this.defaultValueContent = defaultValueContent;
		}
	}

	/**
	 * @param old The inherited value specification
	 * @param override The overriding specification
	 * @param onError To report errors
	 * @param onWarning To report warnings
	 * @return The modified value specification
	 */
	public static ValueSpec validateSpecification(ValueSpec old, ValueSpec override, //
		Consumer<String> onError, Consumer<String> onWarning) {
		QonfigValueType type = old.type;
		if (override.type != null && !override.type.equals(old.type)) {
			if (!(type instanceof QonfigAddOn) || !(override.type instanceof QonfigAddOn)
				|| !((QonfigAddOn) type).isAssignableFrom((QonfigAddOn) override.type))
				onError.accept("Type " + old.type + " cannot be overridden with type " + override.type);
			else
				type = override.type;
		}
		Object oldDefaultValue = old.defaultValue;
		Object newDefaultValue = override.defaultValue;
		LocatedPositionedContent defaultValueContent = override.defaultValueContent;
		if (newDefaultValue != null) {
			if (newDefaultValue.equals(oldDefaultValue)) { //
			} else if (type != null && !type.isInstance(newDefaultValue)) {
				onError.accept("Default value '" + newDefaultValue + "' is not an instance of " + type);
				newDefaultValue = null;
				defaultValueContent = null;
			}
		} else {
			newDefaultValue = oldDefaultValue;
			defaultValueContent = old.defaultValueContent;
		}
		SpecificationType oldSpec = old.specification;
		SpecificationType newSpec = oldSpec;
		if (override.specification != null)
			newSpec = override.specification;
		if (newSpec != null || newDefaultValue != null) {
			if (oldSpec == SpecificationType.Forbidden) {
				if (newSpec != SpecificationType.Forbidden) {
					onError.accept("Cannot make a forbidden value specifiable");
					newSpec = oldSpec;
				} else if (!Objects.equals(newDefaultValue, oldDefaultValue)) {
					onError.accept("Cannot modify forbidden value: '" + oldDefaultValue + "' to '" + newDefaultValue + "'");
					newDefaultValue = oldDefaultValue;
				}
			}
			switch (newSpec) {
			case Required:
				if (override.defaultValue != null)
					onWarning.accept("Default value '" + override.defaultValue + "'will not be used--value must be specified");
				break;
			case Optional:
			case Forbidden:
				if (oldSpec == SpecificationType.Required && newDefaultValue == null) {
					onError.accept("Default value required to fulfill inherited requirement if value may be unspecified");
					newSpec = oldSpec;
				}
				break;
			}
		}
		return new ValueSpec(type, newSpec, newDefaultValue, defaultValueContent);
	}

	public static ValueSpec validateValue(ValueSpec override, List<ValueSpec> inherited, Consumer<String> onError,
		Consumer<String> onWarning) {
		// Check type first
		QonfigValueType type = null;
		if (override.type != null) {
			type = override.type;
			for (ValueSpec inh : inherited) {
				if (inh.type != null && inh.type != type) {
					if (!(type instanceof QonfigAddOn || !(inh.type instanceof QonfigAddOn))
						|| !((QonfigAddOn) inh.type).isAssignableFrom((QonfigAddOn) type))
						onError.accept("Type " + inh.type + " cannot be overridden with type " + override.type);
				}
			}
		} else {
			MultiInheritanceSet<QonfigAddOn> types = null;
			for (ValueSpec inh : inherited) {
				if (inh.type != null) {
					if (!(inh.type instanceof QonfigAddOn)) {
						type = inh.type;
						break;
					}
					if (types == null)
						types = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
					types.add((QonfigAddOn) inh.type);
				}
			}
			if (types != null) {
				if (types.values().size() > 1)
					onError.accept("Multiple incompatible types inherited: " + types + ".  Type must be specified.");
				type = types.values().iterator().next();
			} else if (type == null)
				throw new IllegalStateException("No type given");
		}

		Object defaultValue = override.defaultValue;
		LocatedPositionedContent defaultValueContent = override.defaultValueContent;
		boolean required = false, forbidden = false;
		for (ValueSpec inh : inherited) {
			switch (inh.specification) {
			case Required:
				required = true;
				break;
			case Optional:
				break;
			case Forbidden:
				if (override.defaultValue != null && !override.defaultValue.equals(inh.defaultValue))
					onError.accept("Cannot modify forbidden value: '" + inh.defaultValue + "' to '" + override.defaultValue + "'");
				else if (forbidden && !inh.defaultValue.equals(defaultValue))
					onError.accept("Inherited multiple values incompatible values for forbidden value");
			}
			if (inh.defaultValue != null && type.isInstance(inh.defaultValue)) {
				defaultValue = inh.defaultValue;
				defaultValueContent = inh.defaultValueContent;
			}
		}
		SpecificationType specification;
		if (override.specification != null) {
			specification = override.specification;
			switch (specification) {
			case Required:
				if (forbidden)
					onError.accept("Cannot make a forbidden value specifiable");
				else if (override.defaultValue != null)
					onWarning.accept("Default value '" + override.defaultValue + "'will not be used--value must be specified");
				break;
			case Optional:
			case Forbidden:
				if (required && defaultValue == null)
					onError.accept("Default value required to fulfill inherited requirement if value may be unspecified");
				break;
			}
		} else if (forbidden)
			specification = SpecificationType.Forbidden;
		else if (defaultValue != null || !required)
			specification = SpecificationType.Optional;
		else
			specification = SpecificationType.Required;
		return new ValueSpec(type, specification, defaultValue, defaultValueContent);
	}

	/**
	 * @param root The declared child
	 * @param inheritance Every add-on inherited by the child through {@link ChildDefModifier modifiers}, each with an associated
	 *        inheritance path for tracing
	 * @param finalWord The modifier specified by the owner calling this method
	 * @param path The child path for tracing
	 * @param session The session for error/warning reporting
	 * @param noRecurse Prevents recursion
	 */
	public static void validateChild(QonfigChildDef root, Map<QonfigAddOn, String> inheritance, ChildDefModifier finalWord,
		StringBuilder path, QonfigParseSession session, Set<QonfigChildDef> noRecurse) {
		boolean valueModified = finalWord != null && finalWord.getValueModifier() != null;
		String textModSource = null;
		Map<QonfigAttributeDef.Declared, String> attrModSource = new HashMap<>();
		Map<QonfigAttributeDef.Declared, ValueDefModifier> attrModifiers = new HashMap<>();
		ValueModifier valueModifier = null;
		for (Map.Entry<QonfigAddOn, String> inh : inheritance.entrySet()) {
			QonfigAddOn ao = inh.getKey();
			// Validate value
			if (ao.getValueModifier() != null) {
				if (valueModifier == null) {
					textModSource = inh.getValue();
					valueModifier = ao.getValueModifier();
				} else if (valueModified) {
					QonfigValidation.validateSpecification( //
						new ValueSpec(root.getType() == null ? null : root.getType().getValue().getType(),
							ao.getValueModifier().getSpecification(), ao.getValueModifier().getDefaultValue(),
							ao.getValueModifier().getDefaultValueContent()), //
						new ValueSpec(valueModifier.getTypeRestriction(), valueModifier.getSpecification(),
							valueModifier.getDefaultValue(), valueModifier.getDefaultValueContent()), //
						err -> session.at(root.getFilePosition()).error(err), //
						warn -> session.at(root.getFilePosition()).warn(warn));
				} else if (valueModifier.getSpecification() != ao.getValueModifier().getSpecification()
					|| !Objects.equals(valueModifier.getDefaultValue(), ao.getValueModifier().getDefaultValue())) {
					session.at(root.getFilePosition()).error("Inherited add-ons " + textModSource + " and " + inh.getValue()
						+ " specify different text modifications. Inheriting both of these root add-ons is illegal.");
				}
			}
			// Validate attribute modifications
			for (Map.Entry<QonfigAttributeDef.Declared, ValueModifier> attr : ao.getAttributeModifiers().entrySet()) {
				ValueDefModifier modifier = attrModifiers.get(attr.getKey());
				if (modifier == null) {
					attrModSource.put(attr.getKey(), inh.getValue());
					attrModifiers.put(attr.getKey(), attr.getValue());
				} else {
					ValueDefModifier preMod = attrModifiers.get(attr.getKey());
					if (preMod.getSpecification() != attr.getValue().getSpecification()
						|| !Objects.equals(preMod.getDefaultValue(), attr.getValue().getDefaultValue()))
						session.at(root.getFilePosition())
							.error("Inherited add-ons " + attrModSource.get(attr.getKey()) + " and " + inh.getValue()
								+ " specify different modifications. Inheriting cannot inherit both of these root add-ons is illegal.");
				}
			}
		}
		// Validate child modifications
		if (root.getType() != null) {
			for (Map.Entry<QonfigChildDef.Declared, QonfigChildDef> child : root.getType().getAllChildren().entrySet()) {
				Map<QonfigAddOn, String> inheritanceChildren = new HashMap<>();
				for (Map.Entry<QonfigAddOn, String> inh : inheritance.entrySet()) {
					ChildModifier chMod = inh.getKey().getChildModifiers().get(child.getKey());
					if (chMod != null)
						for (QonfigAddOn inh2 : chMod.getInheritance())
							inheritanceChildren.put(inh.getKey(), inh.getValue() + '/' + inh2.getName());
				}
				int preLen = path.length();
				QonfigChildDef childRoot = root.getType().getAllChildren().get(child.getKey());
				if (!noRecurse.add(childRoot))
					continue;
				validateChild(//
					childRoot, //
					inheritanceChildren, //
					null, // Child modifiers can't specify recursive constraints
					path.append('.').append(child.getKey().getName()), session, noRecurse);
				path.setLength(preLen);
			}
		}
	}
}

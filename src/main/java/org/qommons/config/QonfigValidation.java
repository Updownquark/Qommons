package org.qommons.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.qommons.config.QonfigAddOn.ChildModifier;
import org.qommons.config.QonfigAddOn.ValueModifier;

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

		/**
		 * @param type The type that must be specified
		 * @param specify The specification of the value
		 * @param defaultValue The value to use if it is not specified
		 */
		public ValueSpec(QonfigValueType type, SpecificationType specify, Object defaultValue) {
			this.type = type;
			this.specification = specify;
			this.defaultValue = defaultValue;
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
		if (override.type != null) {
			if (!(type instanceof QonfigAddOn) || !(override.type instanceof QonfigAddOn)
				|| !((QonfigAddOn) type).isAssignableFrom((QonfigAddOn) override.type))
				onError.accept("Type " + old.type + " cannot be overridden with type " + override.type);
			else
				type = override.type;
		}
		Object oldDefaultValue = old.defaultValue;
		Object newDefaultValue = override.defaultValue;
		if (newDefaultValue != null) {
			if (newDefaultValue.equals(oldDefaultValue))
				newDefaultValue = null;
			else if (type != null && !type.isInstance(newDefaultValue)) {
				onError.accept("Default value '" + newDefaultValue + "' is not an instance of " + type);
				newDefaultValue = null;
			}
		} else
			newDefaultValue = oldDefaultValue;
		SpecificationType oldSpec = old.specification;
		SpecificationType newSpec = oldSpec;
		if (override.specification != null)
			newSpec = override.specification;
		if (newSpec != null || newDefaultValue != null) {
			if (oldSpec == SpecificationType.Forbidden) {
				if (newSpec != SpecificationType.Forbidden) {
					onError.accept("Cannot make a forbidden value specifiable");
					newSpec = oldSpec;
				} else if (!newDefaultValue.equals(oldDefaultValue)) {
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
				if (newDefaultValue == null) {
					onError.accept("Default value required if value may be unspecified");
					newSpec = oldSpec;
				}
				break;
			}
		}
		return new ValueSpec(type, newSpec, newDefaultValue);
	}

	/**
	 * @param root The declared child
	 * @param inheritance Every add-on inherited by the child through {@link ChildDefModifier modifiers}, each with an associated
	 *        inheritance path for tracing
	 * @param finalWord The modifier specified by the owner calling this method
	 * @param path The child path for tracing
	 * @param session The session for error/warning reporting
	 */
	public static void validateChild(QonfigChildDef root, Map<QonfigAddOn, String> inheritance, ChildDefModifier finalWord,
		StringBuilder path, QonfigParseSession session) {
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
						new ValueSpec(root.getType().getValue().getType(), ao.getValueModifier().getSpecification(),
							ao.getValueModifier().getDefaultValue()), //
						new ValueSpec(valueModifier.getTypeRestriction(), valueModifier.getSpecification(),
							valueModifier.getDefaultValue()), //
						err -> session.forChild("text", null).withError(err), //
						warn -> session.forChild("text", null).withWarning(warn));
				} else if (valueModifier.getSpecification() != ao.getValueModifier().getSpecification()
					|| !Objects.equals(valueModifier.getDefaultValue(), ao.getValueModifier().getDefaultValue())) {
					session.forChild("child-def", path.toString()).withError("Inherited add-ons " + textModSource + " and " + inh.getValue()
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
						session.forChild("child-def", path.toString())
							.withError("Inherited add-ons " + attrModSource.get(attr.getKey()) + " and " + inh.getValue()
								+ " specify different modifications. Inheriting cannot inherit both of these root add-ons is illegal.");
				}
			}
		}
		// Validate child modifications
		for (Map.Entry<QonfigChildDef.Declared, QonfigChildDef> child : root.getType().getAllChildren().entrySet()) {
			Map<QonfigAddOn, String> inheritanceChildren = new HashMap<>();
			for (Map.Entry<QonfigAddOn, String> inh : inheritance.entrySet()) {
				ChildModifier chMod = inh.getKey().getChildModifiers().get(child.getKey());
				if (chMod != null)
					for (QonfigAddOn inh2 : chMod.getInheritance())
						inheritanceChildren.put(inh.getKey(), inh.getValue() + '/' + inh2.getName());
			}
			int preLen = path.length();
			validateChild(//
				root.getType().getAllChildren().get(child.getKey()), //
				inheritanceChildren, //
				null, // Child modifiers can't specify recursive constraints
				path.append(child.getKey().getName()), session);
			path.setLength(preLen);
		}
	}
}

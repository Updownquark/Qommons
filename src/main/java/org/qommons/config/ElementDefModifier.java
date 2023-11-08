package org.qommons.config;

import java.util.Map;
import java.util.Set;

import org.qommons.SelfDescribed;

/** Modification to an element-def */
public interface ElementDefModifier extends FileSourced, SelfDescribed {
	/** @return The type restriction on the kinds of element-defs that may be instantiated to fulfill an element definition */
	QonfigElementOrAddOn getTypeRestriction();

	/** @return Additional add-ons inherited by the element */
	Set<QonfigAddOn> getInheritance();

	/**
	 * @return requirement {@link QonfigAddOn#isAbstract()} add-ons that an element must inherit from elsewhere in order to fulfill this
	 *         role
	 */
	Set<QonfigAddOn> getRequirement();

	/** @return Additional attributes for the element */
	Map<String, QonfigAttributeDef.Declared> getDeclaredAttributes();

	/** @return Modifications to attributes declared by the element */
	Map<QonfigAttributeDef.Declared, ? extends ValueDefModifier> getAttributeModifiers();

	/** @return Modification to the value declared by the element */
	ValueDefModifier getValueModifier();

	/** @return Modifications to children declared by the element */
	Map<QonfigChildDef.Declared, ? extends ChildDefModifier> getChildModifiers();
}

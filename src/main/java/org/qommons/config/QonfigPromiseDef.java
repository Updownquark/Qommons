package org.qommons.config;

import java.util.Map;
import java.util.Set;

import org.qommons.MultiInheritanceSet;
import org.qommons.collect.BetterMultiMap;
import org.qommons.config.QonfigAttributeDef.Declared;
import org.qommons.io.PositionedContent;

/** A sub-type of element-def that specifies an element that refers to some content that is promised to extend/inherit certain types */
public class QonfigPromiseDef extends QonfigElementDef {
	private final QonfigPromiseFulfillment theFulfillment;
	private final QonfigElementDef thePromisedType;
	private final MultiInheritanceSet<QonfigAddOn> thePromisedInheritance;

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
	 * @param fulfillment The promise fulfillment for this promise type
	 * @param promisedType The element type that elements fulfilled by this promise must extend
	 * @param promisedInheritance The inheritance that elements fulfilled by this promise will inherit
	 */
	protected QonfigPromiseDef(QonfigToolkit declarer, String name, QonfigElementDef superElement, Set<QonfigAddOn> inheritance,
		boolean isAbstract, Map<String, Declared> declaredAttributes, Map<Declared, ValueDefModifier> attributeModifiers,
		Map<Declared, QonfigAttributeDef> allAttributes, BetterMultiMap<String, QonfigAttributeDef> attributesByName,
		Map<String, org.qommons.config.QonfigChildDef.Declared> declaredChildren,
		Map<org.qommons.config.QonfigChildDef.Declared, ChildDefModifier> childModifiers,
		Map<org.qommons.config.QonfigChildDef.Declared, QonfigChildDef> allChildren, BetterMultiMap<String, QonfigChildDef> childrenByName,
		ValueDefModifier value, MultiInheritanceSet<QonfigAddOn> fullInheritance, QonfigElementDef metaSpec, PositionedContent position,
		String description, QonfigPromiseFulfillment fulfillment, QonfigElementDef promisedType,
		MultiInheritanceSet<QonfigAddOn> promisedInheritance) {
		super(declarer, name, superElement, inheritance, isAbstract, declaredAttributes, attributeModifiers, allAttributes,
			attributesByName, declaredChildren, childModifiers, allChildren, childrenByName, value, fullInheritance, metaSpec, position,
			description);
		theFulfillment = fulfillment;
		thePromisedType = promisedType;
		thePromisedInheritance = promisedInheritance;
	}

	/** @return The promise fulfillment for this promise type */
	public QonfigPromiseFulfillment getFulfillment() {
		return theFulfillment;
	}

	/** @return The element type that elements fulfilled by this promise must extend */
	public QonfigElementDef getPromisedType() {
		return thePromisedType;
	}

	/** @return The inheritance that elements fulfilled by this promise will inherit */
	public MultiInheritanceSet<QonfigAddOn> getPromisedInheritance() {
		return thePromisedInheritance;
	}

	/**
	 * @param type The type to test
	 * @return Whether elements fulfilled by this promise will extend/inherit the given type
	 */
	public boolean isPromiseAssignableTo(QonfigElementOrAddOn type) {
		if (getPromisedType() != null && type.isAssignableFrom(getPromisedType()))
			return true;
		return type instanceof QonfigAddOn && thePromisedInheritance.contains((QonfigAddOn) type);
	}
}

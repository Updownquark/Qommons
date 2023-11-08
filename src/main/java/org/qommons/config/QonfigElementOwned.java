package org.qommons.config;

/** An item that is owned by a {@link QonfigElementDef} or {@link QonfigAddOn} */
public interface QonfigElementOwned extends QonfigType {
	/** An owned item declared by a type, as opposed to inherited from a super-type */
	interface Declared extends QonfigElementOwned {
	}

	/** @return The element-def or add-on that owns this type */
	QonfigElementOrAddOn getOwner();

	/** @return The declared item that this item is or inherits */
	Declared getDeclared();
}

package org.qommons.config;

/** An item that is owned by a {@link QonfigElementDef} or {@link QonfigAddOn} */
public interface QonfigElementOwned extends QonfigType {
	/** @return The element-def or add-on that owns this type */
	QonfigElementOrAddOn getOwner();
}

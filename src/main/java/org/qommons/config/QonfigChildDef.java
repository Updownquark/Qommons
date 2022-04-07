package org.qommons.config;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** A child that may be specified for an element */
public interface QonfigChildDef extends QonfigElementOwned {
	/** A child specification as originally declared */
	interface Declared extends QonfigChildDef {
	}

	/** @return The declared child that this definition is or inherits */
	Declared getDeclared();

	@Override
	default QonfigToolkit getDeclarer() {
		return getOwner().getDeclarer();
	}

	/** @return The super-type of elements that may be declared to fulfill this role */
	QonfigElementDef getType();

	/** @return The inherited roles that elements of this role fulfill in inherited element-defs and add-ons */
	Set<QonfigChildDef.Declared> getFulfillment();

	/** @return Add-ons that elements fulfilling this role will inherit */
	Set<QonfigAddOn> getInheritance();

	/** @return The minimum number of elements that may be specified for this role */
	int getMin();

	/** @return The maximum number of elements that may be specified for this role */
	int getMax();

	boolean isFulfilledBy(QonfigChildDef child);

	/** Abstract {@link QonfigChildDef} implementation */
	public static abstract class Abstract implements QonfigChildDef {
		private final QonfigElementOrAddOn theOwner;
		private final QonfigElementDef theType;
		private final Set<QonfigChildDef.Declared> theFulfillment;
		private final Set<QonfigAddOn> theInheritance;
		private final int theMin;
		private final int theMax;

		/**
		 * @param owner The element-def or add-on that this child belongs to
		 * @param type The super-type of elements that may be declared to fulfill this role
		 * @param fulfillment The inherited roles that elements of this role fulfill in inherited element-defs and add-ons
		 * @param inheritance Add-ons that elements fulfilling this role will inherit
		 * @param min The minimum number of elements that may be specified for this role
		 * @param max The maximum number of elements that may be specified for this role
		 */
		public Abstract(QonfigElementOrAddOn owner, QonfigElementDef type, Set<QonfigChildDef.Declared> fulfillment,
			Set<QonfigAddOn> inheritance, int min, int max) {
			theOwner = owner;
			theType = type;
			theFulfillment = fulfillment;
			theInheritance = inheritance;
			theMin = min;
			theMax = max;
		}

		@Override
		public abstract QonfigChildDef.Declared getDeclared();

		@Override
		public QonfigToolkit getDeclarer() {
			return theOwner.getDeclarer();
		}

		@Override
		public QonfigElementOrAddOn getOwner() {
			return theOwner;
		}

		@Override
		public QonfigElementDef getType() {
			return theType;
		}

		@Override
		public Set<QonfigChildDef.Declared> getFulfillment() {
			return theFulfillment;
		}

		@Override
		public Set<QonfigAddOn> getInheritance() {
			return theInheritance;
		}

		@Override
		public int getMin() {
			return theMin;
		}

		@Override
		public int getMax() {
			return theMax;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theOwner, getName());
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof QonfigChildDef))
				return false;
			return theOwner.equals(((QonfigChildDef) obj).getOwner()) && getName().equals(((QonfigChildDef) obj).getName());
		}

		@Override
		public String toString() {
			return theOwner + "." + getName() + "(" + theType + ")";
		}
	}

	/** {@link QonfigChildDef.Declared QonfigChildDef.Declared} implementation */
	public static class DeclaredChildDef extends Abstract implements Declared {
		private final String theName;

		/**
		 * @param owner The element-def or add-on that this child belongs to
		 * @param name The name of this child role
		 * @param type The super-type of elements that may be declared to fulfill this role
		 * @param fulfillment The inherited roles that elements of this role fulfill in inherited element-defs and add-ons
		 * @param inheritance Add-ons that elements fulfilling this role will inherit
		 * @param min The minimum number of elements that may be specified for this role
		 * @param max The maximum number of elements that may be specified for this role
		 */
		public DeclaredChildDef(QonfigElementOrAddOn owner, String name, QonfigElementDef type, Set<QonfigChildDef.Declared> fulfillment,
			Set<QonfigAddOn> inheritance, int min, int max) {
			super(owner, type, fulfillment, inheritance, min, max);
			theName = name;
		}

		@Override
		public QonfigChildDef.Declared getDeclared() {
			return this;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public boolean isFulfilledBy(QonfigChildDef child) {
			if (equals(child))
				return true;
			for (QonfigChildDef fulfills : child.getFulfillment())
				if (isFulfilledBy(fulfills))
					return true;
			return false;
		}
	}

	/** An inherited or modified child definition */
	public static class Modified extends Abstract {
		private final QonfigChildDef.Declared theDeclared;

		/**
		 * @param declared The inherited child
		 * @param owner The element-def or add-on that this child belongs to
		 * @param type The super-type of elements that may be declared to fulfill this role
		 * @param inheritance Additional add-ons that elements fulfilling this role will inherit
		 * @param min The minimum number of elements that may be specified for this role
		 * @param max The maximum number of elements that may be specified for this role
		 */
		public Modified(QonfigChildDef declared, QonfigElementOrAddOn owner, QonfigElementDef type, Set<QonfigAddOn> inheritance, int min,
			int max) {
			super(owner, type, Collections.emptySet(), inheritance, min, max);
			theDeclared = declared instanceof QonfigChildDef.Declared ? (QonfigChildDef.Declared) declared
				: ((QonfigChildDef.Modified) declared).getDeclared();
		}

		@Override
		public QonfigChildDef.Declared getDeclared() {
			return theDeclared;
		}

		@Override
		public String getName() {
			return theDeclared.getName();
		}

		@Override
		public boolean isFulfilledBy(QonfigChildDef child) {
			if (equals(child))
				return true;
			for (QonfigChildDef fulfills : child.getFulfillment())
				if (isFulfilledBy(fulfills))
					return true;
			return false;
		}
	}

	/**
	 * An inherited child that is fulfilled by another child definition. The declared child shall not be specified directly for the owner.
	 */
	public static class Overridden extends Abstract {
		private final QonfigChildDef.Declared theDeclared;
		private final Set<QonfigChildDef.Declared> theOverriding;

		/**
		 * @param declared The inherited child
		 * @param owner The element-def or add-on that this child belongs to
		 * @param overriding The new children that fulfill the role, overriding it
		 */
		public Overridden(QonfigElementOrAddOn owner, QonfigChildDef.Declared declared, Set<QonfigChildDef.Declared> overriding) {
			super(owner, declared.getType(), Collections.emptySet(), Collections.emptySet(), 0, 0);
			theDeclared = declared;
			theOverriding = overriding;
		}

		@Override
		public String getName() {
			return theDeclared.getName();
		}

		@Override
		public QonfigChildDef.Declared getDeclared() {
			return theDeclared;
		}

		/** @return The declared children fulfilling the role, overriding it */
		public Set<QonfigChildDef.Declared> getOverriding() {
			return theOverriding;
		}

		@Override
		public boolean isFulfilledBy(QonfigChildDef child) {
			return false;
		}
	}
}

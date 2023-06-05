package org.qommons.config;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.qommons.io.PositionedContent;

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

	/** @return {@link QonfigAddOn#isAbstract()} add-ons that an element must inherit from elsewhere in order to fulfill this role */
	Set<QonfigAddOn> getRequirement();

	/** @return Add-ons that elements fulfilling this role will inherit */
	Set<QonfigAddOn> getInheritance();

	/** @return The minimum number of elements that may be specified for this role */
	int getMin();

	/** @return The maximum number of elements that may be specified for this role */
	int getMax();

	/**
	 * @param child The child to test
	 * @return Whether the given child also fulfills this role
	 */
	boolean isFulfilledBy(QonfigChildDef child);

	/**
	 * @param element The element type to test
	 * @return Whether the given element type is able to fulfill this child role
	 */
	default boolean isCompatible(QonfigElementDef element) {
		if (getType() != null && !getType().isAssignableFrom(element))
			return false;
		for (QonfigAddOn req : getRequirement()) {
			if (!req.isAssignableFrom(element))
				return false;
		}
		return true;
	}

	/** Abstract {@link QonfigChildDef} implementation */
	public static abstract class Abstract implements QonfigChildDef {
		private final QonfigElementOrAddOn theOwner;
		private final QonfigElementDef theType;
		private final Set<QonfigChildDef.Declared> theFulfillment;
		private final Set<QonfigAddOn> theInheritance;
		private final Set<QonfigAddOn> theRequirement;
		private final int theMin;
		private final int theMax;
		private final PositionedContent thePosition;

		/**
		 * @param owner The element-def or add-on that this child belongs to
		 * @param type The super-type of elements that may be declared to fulfill this role
		 * @param fulfillment The inherited roles that elements of this role fulfill in inherited element-defs and add-ons
		 * @param inheritance Add-ons that elements fulfilling this role will inherit
		 * @param requirement {@link QonfigAddOn#isAbstract()} add-ons that an element must inherit from elsewhere in order to fulfill this
		 *        role
		 * @param min The minimum number of elements that may be specified for this role
		 * @param max The maximum number of elements that may be specified for this role
		 * @param position The position in the file where this child was defined
		 */
		public Abstract(QonfigElementOrAddOn owner, QonfigElementDef type, Set<QonfigChildDef.Declared> fulfillment,
			Set<QonfigAddOn> inheritance, Set<QonfigAddOn> requirement, int min, int max, PositionedContent position) {
			theOwner = owner;
			theType = type;
			theFulfillment = fulfillment;
			theInheritance = inheritance;
			theRequirement = requirement;
			theMin = min;
			theMax = max;
			thePosition = position;
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
		public Set<QonfigAddOn> getRequirement() {
			return theRequirement;
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
		public PositionedContent getFilePosition() {
			return thePosition;
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
			return theOwner + "." + getName() + "(" + (theType == null ? "typeless" : theType.toString()) + ")";
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
		 * @param requirement {@link QonfigAddOn#isAbstract()} add-ons that an element must inherit from elsewhere in order to fulfill this
		 *        role
		 * @param min The minimum number of elements that may be specified for this role
		 * @param max The maximum number of elements that may be specified for this role
		 * @param position The position in the file where this child was defined
		 */
		public DeclaredChildDef(QonfigElementOrAddOn owner, String name, QonfigElementDef type, Set<QonfigChildDef.Declared> fulfillment,
			Set<QonfigAddOn> inheritance, Set<QonfigAddOn> requirement, int min, int max, PositionedContent position) {
			super(owner, type, fulfillment, inheritance, requirement, min, max, position);
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
		private final Set<QonfigAddOn> theDeclaredInheritance;

		/**
		 * @param declared The inherited child
		 * @param owner The element-def or add-on that this child belongs to
		 * @param type The super-type of elements that may be declared to fulfill this role
		 * @param inheritance Additional add-ons that elements fulfilling this role will inherit
		 * @param requirement {@link QonfigAddOn#isAbstract()} add-ons that an element must inherit from elsewhere in order to fulfill this
		 *        role
		 * @param min The minimum number of elements that may be specified for this role
		 * @param max The maximum number of elements that may be specified for this role
		 * @param position The position in the file where this child was defined
		 */
		public Modified(QonfigChildDef declared, QonfigElementOrAddOn owner, QonfigElementDef type, Set<QonfigAddOn> inheritance,
			Set<QonfigAddOn> requirement, int min, int max, PositionedContent position) {
			super(owner, type, Collections.emptySet(), combine(declared.getInheritance(), inheritance), requirement, min, max, position);
			theDeclared = declared instanceof QonfigChildDef.Declared ? (QonfigChildDef.Declared) declared
				: ((QonfigChildDef.Modified) declared).getDeclared();
			theDeclaredInheritance = inheritance;
		}

		private static Set<QonfigAddOn> combine(Set<QonfigAddOn> inh1, Set<QonfigAddOn> inh2) {
			if (inh2.isEmpty())
				return inh1;
			else if (inh1.isEmpty())
				return inh2;
			Set<QonfigAddOn> combined = new LinkedHashSet<>((inh1.size() + inh2.size()) * 3 / 2);
			combined.addAll(inh1);
			combined.addAll(inh2);
			return Collections.unmodifiableSet(combined);
		}

		@Override
		public QonfigChildDef.Declared getDeclared() {
			return theDeclared;
		}

		@Override
		public String getName() {
			return theDeclared.getName();
		}

		/** @return Additional add-ons that elements fulfilling this role will inherit over and above the parent's role */
		public Set<QonfigAddOn> getDeclaredInheritance() {
			return theDeclaredInheritance;
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
		 * @param position The position in the file where this child was defined
		 */
		public Overridden(QonfigElementOrAddOn owner, QonfigChildDef.Declared declared, Set<QonfigChildDef.Declared> overriding,
			PositionedContent position) {
			super(owner, declared.getType(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), 0, 0, position);
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

	/** An inherited child that further inherits other add-ons */
	public static class Inherited implements QonfigChildDef {
		private final QonfigElementOrAddOn theOwner;
		private final QonfigChildDef theInherited;

		/**
		 * @param owner The owner of this child (not the definer)
		 * @param inherited The child inherited by this child
		 */
		public Inherited(QonfigElementOrAddOn owner, QonfigChildDef inherited) {
			theOwner = owner;
			theInherited = inherited;
		}

		@Override
		public QonfigElementOrAddOn getOwner() {
			return theOwner;
		}

		/** @return The child inherited by this child */
		public QonfigChildDef getInherited() {
			return theInherited;
		}

		@Override
		public String getName() {
			return theInherited.getName();
		}

		@Override
		public Declared getDeclared() {
			return theInherited.getDeclared();
		}

		@Override
		public QonfigElementDef getType() {
			return theInherited.getType();
		}

		@Override
		public Set<Declared> getFulfillment() {
			return theInherited.getFulfillment();
		}

		@Override
		public Set<QonfigAddOn> getRequirement() {
			return theInherited.getRequirement();
		}

		@Override
		public Set<QonfigAddOn> getInheritance() {
			return theInherited.getInheritance();
		}

		@Override
		public int getMin() {
			return theInherited.getMin();
		}

		@Override
		public int getMax() {
			return theInherited.getMax();
		}

		@Override
		public PositionedContent getFilePosition() {
			return null; // This child wasn't defined anywhere, it was inherited by default
		}

		@Override
		public boolean isFulfilledBy(QonfigChildDef child) {
			if (equals(child))
				return true;
			else if (child instanceof Inherited) {
				Inherited inh = (Inherited) child;
				return theOwner.isAssignableFrom(inh.theOwner) && isFulfilledBy(inh.theInherited);
			}
			for (QonfigChildDef fulfills : child.getFulfillment())
				if (isFulfilledBy(fulfills))
					return true;
			return false;
		}

		@Override
		public String toString() {
			return theOwner + "." + getName() + "(" + (getType() == null ? "typeless" : getType().toString()) + ")";
		}
	}
}

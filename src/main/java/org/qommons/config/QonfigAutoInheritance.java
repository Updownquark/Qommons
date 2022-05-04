package org.qommons.config;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.qommons.MultiInheritanceSet;
import org.qommons.QommonsUtils;

/** A set of add-ons that shall be inherited automatically by elements matching a condition */
public class QonfigAutoInheritance {
	/** A condition determining what elements shall inherit from a {@link QonfigAutoInheritance} specification */
	public static class AutoInheritTarget {
		private final QonfigElementOrAddOn theTarget;
		private final QonfigChildDef theRole;

		AutoInheritTarget(QonfigElementOrAddOn target, QonfigChildDef role) {
			theTarget = target;
			theRole = role;
		}

		/** @return The element or add-on type to target--may be null if this target is role-only */
		public QonfigElementOrAddOn getTarget() {
			return theTarget;
		}

		/** @return The role to target--may be null if this target is type-only */
		public QonfigChildDef getRole() {
			return theRole;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theTarget, theRole);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof AutoInheritTarget))
				return false;
			return Objects.equals(theTarget, ((AutoInheritTarget) obj).theTarget)
				&& Objects.equals(theRole, ((AutoInheritTarget) obj).theRole);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			if (theTarget != null) {
				if (theRole != null)
					str.append(theTarget).append('(').append(theRole).append(')');
				else
					str.append(theTarget);
			} else
				str.append(theRole);
			return str.toString();
		}
	}

	private final QonfigToolkit theDeclarer;
	private final MultiInheritanceSet<QonfigAddOn> theInheritance;
	private final Set<AutoInheritTarget> theTargets;

	QonfigAutoInheritance(QonfigToolkit declarer, MultiInheritanceSet<QonfigAddOn> inheritance, Set<AutoInheritTarget> targets) {
		theDeclarer = declarer;
		theInheritance = inheritance;
		theTargets = targets;
	}

	/** @return The toolkit that declared this auto-inheritance */
	public QonfigToolkit getDeclarer() {
		return theDeclarer;
	}

	/** @return The add-ons for the targets to inherit */
	public MultiInheritanceSet<QonfigAddOn> getInheritance() {
		return theInheritance;
	}

	/** @return The targets for this auto-inheritance */
	public Set<AutoInheritTarget> getTargets() {
		return theTargets;
	}

	@Override
	public String toString() {
		return theTargets + "<-" + theInheritance;
	}

	/**
	 * @param session The parse session to build auto-inheritance within
	 * @return An auto-inheritance builder
	 */
	public static Builder build(QonfigParseSession session) {
		return new Builder(session);
	}

	/** Builds a {@link QonfigAutoInheritance} instance */
	public static class Builder {
		private final QonfigParseSession theSession;
		private final MultiInheritanceSet<QonfigAddOn> theInheritance;
		private final Set<AutoInheritTarget> theTargets;

		Builder(QonfigParseSession session) {
			theSession = session;
			theInheritance = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
			theTargets = new LinkedHashSet<>();
		}

		/**
		 * Adds an add-on for targets to inherit automatically
		 * 
		 * @param inheritance The add-on for targets to inherit
		 * @return This builder
		 */
		public Builder inherits(QonfigAddOn inheritance) {
			if (inheritance.isAbstract())
				theSession.withError(inheritance + " is abstract and cannot be inherited automatically");
			if (inheritance.getSuperElement() != null) {
				for (AutoInheritTarget target : theTargets)
					checkInheritance(target, inheritance, theSession);
			}
			theInheritance.add(inheritance);
			return this;
		}

		/**
		 * Adds a target to inherit the add-ons automatically
		 * 
		 * @param target The element or add-on type to target--may be null if the target is to be role-only
		 * @param role The role to target--may be null if the target is type-only
		 * @return This builder
		 */
		public Builder withTarget(QonfigElementOrAddOn target, QonfigChildDef role) {
			if (target == null && role == null) {
				theSession.forChild("target", "(empty)")
					.withError("Either a target or a role or both must be specified for an auto-inheritance target");
				return this;
			}
			AutoInheritTarget ait = new AutoInheritTarget(target, role);
			QonfigParseSession targetSession = theSession.forChild("target", ait);
			if (role != null && role.getMax() == 0)
				targetSession
					.withError("As no children are allowed in role " + role + ", this role cannot be targeted for auto-inheritance");
			if (target != null && role != null && !role.getType().isAssignableFrom(target))
				targetSession.withError("Target " + target + " cannot fulfill role " + role + ", which requires " + role.getType());
			for (QonfigAddOn inheritance : theInheritance.values())
				checkInheritance(ait, inheritance, targetSession);
			theTargets.add(ait);
			return this;
		}

		private static void checkInheritance(AutoInheritTarget ait, QonfigAddOn inheritance, QonfigParseSession session) {
			if (inheritance.getSuperElement() == null)
				return;
			if (ait.getTarget() != null && inheritance.getSuperElement().isAssignableFrom(ait.getTarget())) {//
			} else if (ait.getRole() != null && inheritance.getSuperElement().isAssignableFrom(ait.getRole().getType())) {//
			} else
				session
					.withError("Cannot target " + ait + " to inherit " + inheritance + ", which requires "
					+ inheritance.getSuperElement());
		}

		/** @return The new auto-inheritance object */
		public QonfigAutoInheritance build() {
			return new QonfigAutoInheritance(theSession.getToolkit(), MultiInheritanceSet.unmodifiable(theInheritance.copy()),
				QommonsUtils.unmodifiableDistinctCopy(theTargets));
		}
	}
}

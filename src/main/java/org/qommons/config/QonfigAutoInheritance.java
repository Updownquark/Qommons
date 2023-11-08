package org.qommons.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.qommons.MultiInheritanceSet;
import org.qommons.QommonsUtils;
import org.qommons.io.PositionedContent;

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

		/**
		 * @param element The element to test
		 * @param roles The set of roles fulfilled by an element
		 * @return Whether an element of the given type, fulfilling the given roles, matches this inheritance target
		 */
		public boolean applies(QonfigElementOrAddOn element, MultiInheritanceSet<QonfigChildDef> roles) {
			if (theTarget != null && !theTarget.isAssignableFrom(element))
				return false;
			if (theRole != null) {
				for (QonfigChildDef role : roles.values()) {
					if (theRole.getOwner().isAssignableFrom(role.getOwner()) && theRole.isFulfilledBy(role))
						return true;
				}
				return false;
			}
			return true;
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

	/** A class to compile auto-inheritance from many sources */
	public static class Compiler {
		private final Collection<QonfigToolkit> theToolkits;
		private final MultiInheritanceSet<QonfigElementOrAddOn> theParentTypes;
		private final Set<QonfigChildDef.Declared> theDeclaredRoles;
		private final MultiInheritanceSet<QonfigChildDef> theFullRoles;
		private final MultiInheritanceSet<QonfigAddOn> theInheritance;
		private final MultiInheritanceSet<QonfigElementOrAddOn> theTargetTypes;

		/**
		 * @param toolkits The toolkits with auto-inheritance to consider
		 */
		public Compiler(Collection<QonfigToolkit> toolkits) {
			theToolkits = toolkits;
			theParentTypes = MultiInheritanceSet.create(QonfigElementOrAddOn::isAssignableFrom);
			theDeclaredRoles = new LinkedHashSet<>();
			theFullRoles = MultiInheritanceSet.create(QonfigChildDef::isFulfilledBy);
			theInheritance = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
			theTargetTypes = MultiInheritanceSet.create(QonfigElementOrAddOn::isAssignableFrom);
		}

		public MultiInheritanceSet<QonfigAddOn> getInheritance() {
			return MultiInheritanceSet.unmodifiable(theInheritance);
		}

		public Compiler addParentType(QonfigElementOrAddOn type, Consumer<QonfigAddOn> inheritance) {
			if (theParentTypes.add(type)) {
				for (QonfigChildDef.Declared role : theDeclaredRoles)
					addFullRole(type.getAllChildren().get(role), inheritance);
			}
			return this;
		}

		public Compiler addRole(QonfigChildDef.Declared role, Consumer<QonfigAddOn> inheritance) {
			if (theDeclaredRoles.add(role)) {
				addTargetType(role.getType(), inheritance);
				for (QonfigAddOn req : role.getRequirement())
					addTargetType(req, inheritance);
				for (QonfigAddOn inh : role.getInheritance())
					addTargetType(inh, inheritance);
				for (QonfigElementOrAddOn type : theParentTypes.values())
					addFullRole(type.getAllChildren().get(role), inheritance);
			}
			return this;
		}

		private void addFullRole(QonfigChildDef role, Consumer<QonfigAddOn> inheritance) {
			if (role != null && theFullRoles.add(role)) {
				for (QonfigElementOrAddOn type : theTargetTypes.values()) {
					for (QonfigToolkit toolkit : theToolkits) {
						MultiInheritanceSet<QonfigAddOn> autoInh = toolkit.getAutoInheritance(type,
							MultiInheritanceSet.singleton(role, QonfigChildDef::isFulfilledBy));
						for (QonfigAddOn inh : autoInh.values()) {
							theInheritance.add(inh);
							if (inheritance != null)
								inheritance.accept(inh);
							addTargetType(inh, inheritance);
						}
					}
				}
			}
		}

		/**
		 * @param type The type extended/inherited by the element to consider
		 * @param inheritance Consumer to be notified of add-ons auto-inherited due to inheriting the given type
		 * @return This compiler
		 */
		public Compiler addTargetType(QonfigElementOrAddOn type, Consumer<QonfigAddOn> inheritance) {
			if (type != null && theTargetTypes.add(type)) {
				for (QonfigToolkit toolkit : theToolkits) {
					MultiInheritanceSet<QonfigAddOn> autoInh = toolkit.getAutoInheritance(type, theFullRoles);
					for (QonfigAddOn inh : autoInh.values()) {
						if (theInheritance.add(inh)) {
							if (inheritance != null)
								inheritance.accept(inh);
							addTargetType(inh, inheritance);
						}
					}
				}
			}
			return this;
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
			// Removing this constraint
			// if (inheritance.isAbstract())
			// theSession.withError(inheritance + " is abstract and cannot be inherited automatically");
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
		 * @param position The position in the file where the target was defined
		 * @return This builder
		 */
		public Builder withTarget(QonfigElementOrAddOn target, QonfigChildDef role, PositionedContent position) {
			if (target == null && role == null) {
				theSession.at(position).error("Either a target or a role or both must be specified for an auto-inheritance target");
				return this;
			}
			AutoInheritTarget ait = new AutoInheritTarget(target, role);
			QonfigParseSession targetSession = theSession.at(position);
			if (role != null && role.getMax() == 0)
				targetSession.error("As no children are allowed in role " + role + ", this role cannot be targeted for auto-inheritance");
			if (target != null && role != null && role.getType() != null && !role.getType().isAssignableFrom(target))
				targetSession.error("Target " + target + " cannot fulfill role " + role + ", which requires " + role.getType());
			for (QonfigAddOn inheritance : theInheritance.values())
				checkInheritance(ait, inheritance, targetSession);
			theTargets.add(ait);
			return this;
		}

		private static void checkInheritance(AutoInheritTarget ait, QonfigAddOn inheritance, QonfigParseSession session) {
			if (inheritance.getSuperElement() == null)
				return;
			if (ait.getTarget() != null && inheritance.getSuperElement().isAssignableFrom(ait.getTarget())) {//
			} else if (ait.getRole() != null && ait.getRole().getType() != null
				&& inheritance.getSuperElement().isAssignableFrom(ait.getRole().getType())) {//
			} else
				session.error("Cannot target " + ait + " to inherit " + inheritance + ", which requires " + inheritance.getSuperElement());
		}

		/** @return The new auto-inheritance object */
		public QonfigAutoInheritance build() {
			return new QonfigAutoInheritance(theSession.getToolkit(), MultiInheritanceSet.unmodifiable(theInheritance.copy()),
				QommonsUtils.unmodifiableDistinctCopy(theTargets));
		}
	}
}

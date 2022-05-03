package org.qommons.config;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.qommons.MultiInheritanceSet;
import org.qommons.QommonsUtils;

public class QonfigAutoInheritance {
	public static class AutoInheritTarget {
		private final QonfigElementOrAddOn theTarget;
		private final QonfigChildDef theRole;

		AutoInheritTarget(QonfigElementOrAddOn target, QonfigChildDef role) {
			theTarget = target;
			theRole = role;
		}

		public QonfigElementOrAddOn getTarget() {
			return theTarget;
		}

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

	public QonfigToolkit getDeclarer() {
		return theDeclarer;
	}

	public MultiInheritanceSet<QonfigAddOn> getInheritance() {
		return theInheritance;
	}

	public Set<AutoInheritTarget> getTargets() {
		return theTargets;
	}

	@Override
	public String toString() {
		return theTargets + "<-" + theInheritance;
	}

	public static Builder build(QonfigParseSession session) {
		return new Builder(session);
	}

	public static class Builder {
		private final QonfigParseSession theSession;
		private final MultiInheritanceSet<QonfigAddOn> theInheritance;
		private final Set<AutoInheritTarget> theTargets;

		Builder(QonfigParseSession session) {
			theSession = session;
			theInheritance = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
			theTargets = new LinkedHashSet<>();
		}

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

		private void checkInheritance(AutoInheritTarget ait, QonfigAddOn inheritance, QonfigParseSession session) {
			if (inheritance.getSuperElement() == null)
				return;
			if (ait.getTarget() != null && inheritance.getSuperElement().isAssignableFrom(ait.getTarget())) {//
			} else if (ait.getRole() != null && inheritance.getSuperElement().isAssignableFrom(ait.getRole().getType())) {//
			} else
				session
					.withError("Cannot target " + ait + " to inherit " + inheritance + ", which requires "
					+ inheritance.getSuperElement());
		}

		public QonfigAutoInheritance build() {
			return new QonfigAutoInheritance(theSession.getToolkit(), MultiInheritanceSet.unmodifiable(theInheritance.copy()),
				QommonsUtils.unmodifiableDistinctCopy(theTargets));
		}
	}
}

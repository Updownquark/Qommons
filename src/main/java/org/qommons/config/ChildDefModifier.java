package org.qommons.config;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.qommons.io.PositionedContent;

/** Modifies an inherited child */
public interface ChildDefModifier extends ElementDefModifier {
	@Override
	default Map<QonfigChildDef.Declared, ? extends ChildDefModifier> getChildModifiers() {
		return Collections.emptyMap();
	}

	@Override
	default Map<String, QonfigAttributeDef.Declared> getDeclaredAttributes() {
		return Collections.emptyMap();
	}

	@Override
	default Map<QonfigAttributeDef.Declared, ? extends ValueDefModifier> getAttributeModifiers() {
		return Collections.emptyMap();
	}

	@Override
	default ValueDefModifier getValueModifier() {
		return null;
	}

	/** @return The minimum number of times the child must be specified, or null to inherit it */
	Integer getMin();

	/** @return The maximum number of times the child may be specified, or null to inherit it */
	Integer getMax();

	/** Default {@link ChildDefModifier} implementation */
	public static class Default implements ChildDefModifier {
		private final QonfigElementDef theTypeRestriction;
		private final Set<QonfigAddOn> theInheritance;
		private final Set<QonfigAddOn> theRequirement;
		private final Integer theMin;
		private final Integer theMax;
		private final PositionedContent thePosition;

		/**
		 * @param typeRestriction The type restriction for the child, or null to inherit it
		 * @param inheritance The additional inheritance for the child
		 * @param requirement {@link QonfigAddOn#isAbstract()} add-ons that an element must inherit from elsewhere in order to fulfill this
		 *        role
		 * @param min The minimum number of times the child must be specified, or null to inherit it
		 * @param max The maximum number of times the child may be specified, or null to inherit it
		 * @param position The position in the file where this child was defined
		 */
		public Default(QonfigElementDef typeRestriction, Set<QonfigAddOn> inheritance, Set<QonfigAddOn> requirement, Integer min,
			Integer max, PositionedContent position) {
			theTypeRestriction = typeRestriction;
			theInheritance = inheritance;
			theRequirement = requirement;
			theMin = min;
			theMax = max;
			thePosition = position;
		}

		@Override
		public QonfigElementDef getTypeRestriction() {
			return theTypeRestriction;
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
		public Integer getMin() {
			return theMin;
		}

		@Override
		public Integer getMax() {
			return theMax;
		}

		@Override
		public PositionedContent getFilePosition() {
			return thePosition;
		}
	}
}

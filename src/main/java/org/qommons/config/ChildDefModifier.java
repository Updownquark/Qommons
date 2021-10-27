package org.qommons.config;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

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
		private final Integer theMin;
		private final Integer theMax;

		/**
		 * @param typeRestriction The type restriction for the child, or null to inherit it
		 * @param inheritance The additional inheritance for the child
		 * @param min The minimum number of times the child must be specified, or null to inherit it
		 * @param max The maximum number of times the child may be specified, or null to inherit it
		 */
		public Default(QonfigElementDef typeRestriction, Set<QonfigAddOn> inheritance, Integer min, Integer max) {
			theTypeRestriction = typeRestriction;
			theInheritance = inheritance;
			theMin = min;
			theMax = max;
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
		public Integer getMin() {
			return theMin;
		}

		@Override
		public Integer getMax() {
			return theMax;
		}
	}
}

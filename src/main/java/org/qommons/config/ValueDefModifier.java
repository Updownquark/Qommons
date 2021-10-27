package org.qommons.config;

/** Modifies an attribute or element value defined by a super-type */
public interface ValueDefModifier {
	/** @return The sub-type of the value to restrict specified values to. Null means to inherit from the super-specified value. */
	QonfigValueType getTypeRestriction();

	/** @return The specification for the value. Null means to inherit from the super-specified value. */
	SpecificationType getSpecification();

	/** @return The default value to use if the value is not specified. Null means to inherit from the super-specified value. */
	Object getDefaultValue();

	/** Default {@link ValueDefModifier} implementation */
	public static class Default implements ValueDefModifier {
		private final QonfigValueType theTypeRestriction;
		private final SpecificationType thetype;
		private final Object theDefaultValue;

		/**
		 * @param typeRestriction The sub-type of the value to restrict specified values to. Null means to inherit from the super-specified
		 *        value.
		 * @param specify The specification for the value. Null means to inherit from the super-specified value.
		 * @param defaultValue The default value to use if the value is not specified. Null means to inherit from the super-specified value.
		 */
		public Default(QonfigValueType typeRestriction, SpecificationType specify, Object defaultValue) {
			theTypeRestriction = typeRestriction;
			thetype = specify;
			theDefaultValue = defaultValue;
		}

		@Override
		public QonfigValueType getTypeRestriction() {
			return theTypeRestriction;
		}

		@Override
		public SpecificationType getSpecification() {
			return thetype;
		}

		@Override
		public Object getDefaultValue() {
			return theDefaultValue;
		}
	}
}

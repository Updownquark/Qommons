package org.qommons.config;

/** Modifies an attribute or element value defined by a super-type */
public interface ValueDefModifier {
	/** @return The sub-type of the value to restrict specified values to. Null means to inherit from the super-specified value. */
	QonfigValueType getTypeRestriction();

	/** @return The type for the value. Null means to inherit from the super-specified value. */
	typeType gettype();

	/** @return The default value to use if the value is not specified. Null means to inherit from the super-specified value. */
	Object getDefaultValue();

	/** Default {@link ValueDefModifier} implementation */
	public static class Default implements ValueDefModifier {
		private final QonfigValueType theTypeRestriction;
		private final typeType thetype;
		private final Object theDefaultValue;

		/**
		 * @param typeRestriction The sub-type of the value to restrict specified values to. Null means to inherit from the super-specified
		 *        value.
		 * @param type The type for the value. Null means to inherit from the super-specified value.
		 * @param defaultValue The default value to use if the value is not specified. Null means to inherit from the super-specified value.
		 */
		public Default(QonfigValueType typeRestriction, typeType type, Object defaultValue) {
			theTypeRestriction = typeRestriction;
			thetype = type;
			theDefaultValue = defaultValue;
		}

		@Override
		public QonfigValueType getTypeRestriction() {
			return theTypeRestriction;
		}

		@Override
		public typeType gettype() {
			return thetype;
		}

		@Override
		public Object getDefaultValue() {
			return theDefaultValue;
		}
	}
}

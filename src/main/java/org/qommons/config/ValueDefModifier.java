package org.qommons.config;

/** Modifies an attribute or element value defined by a super-type */
public interface ValueDefModifier {
	/** @return The toolkit that declared this modifier */
	QonfigToolkit getDeclarer();

	/** @return The sub-type of the value to restrict specified values to. Null means to inherit from the super-specified value. */
	QonfigValueType getTypeRestriction();

	/** @return The specification for the value. Null means to inherit from the super-specified value. */
	SpecificationType getSpecification();

	/** @return The default value to use if the value is not specified. Null means to inherit from the super-specified value. */
	Object getDefaultValue();

	/** Default {@link ValueDefModifier} implementation */
	public static class Default implements ValueDefModifier {
		private final QonfigToolkit theDeclarer;
		private final QonfigValueType theTypeRestriction;
		private final SpecificationType theSpecification;
		private final Object theDefaultValue;

		/**
		 * @param declarer The toolkit that declared the modifier
		 * @param typeRestriction The sub-type of the value to restrict specified values to. Null means to inherit from the super-specified
		 *        value.
		 * @param specify The specification for the value. Null means to inherit from the super-specified value.
		 * @param defaultValue The default value to use if the value is not specified. Null means to inherit from the super-specified value.
		 */
		public Default(QonfigToolkit declarer, QonfigValueType typeRestriction, SpecificationType specify, Object defaultValue) {
			theDeclarer = declarer;
			theTypeRestriction = typeRestriction;
			theSpecification = specify;
			theDefaultValue = defaultValue;
		}

		@Override
		public QonfigToolkit getDeclarer() {
			return theDeclarer;
		}

		@Override
		public QonfigValueType getTypeRestriction() {
			return theTypeRestriction;
		}

		@Override
		public SpecificationType getSpecification() {
			return theSpecification;
		}

		@Override
		public Object getDefaultValue() {
			return theDefaultValue;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			if (theTypeRestriction != null)
				str.append("type ").append(theTypeRestriction);
			if (theSpecification != null) {
				if (str.length() > 0)
					str.append(", ");
				str.append("specify " + theSpecification);
			}
			if (theDefaultValue != null) {
				if (str.length() > 0)
					str.append(", ");
				str.append("default " + theDefaultValue);
			}
			return str.toString();
		}
	}
}

package org.qommons.config;

import org.qommons.SelfDescribed;
import org.qommons.io.LocatedPositionedContent;
import org.qommons.io.PositionedContent;

/** Modifies an attribute or element value defined by a super-type */
public interface ValueDefModifier extends SelfDescribed {
	/** @return The toolkit that declared this modifier */
	QonfigToolkit getDeclarer();

	/** @return The sub-type of the value to restrict specified values to. Null means to inherit from the super-specified value. */
	QonfigValueType getTypeRestriction();

	/** @return The specification for the value. Null means to inherit from the super-specified value. */
	SpecificationType getSpecification();

	/** @return The default value to use if the value is not specified. Null means to inherit from the super-specified value. */
	Object getDefaultValue();

	LocatedPositionedContent getDefaultValueContent();

	/** @return The content that specified the modifier */
	PositionedContent getContent();

	/** Default {@link ValueDefModifier} implementation */
	public static class Default implements ValueDefModifier {
		private final QonfigToolkit theDeclarer;
		private final QonfigValueType theTypeRestriction;
		private final SpecificationType theSpecification;
		private final Object theDefaultValue;
		private final LocatedPositionedContent theDefaultValueContent;
		private final String theDescription;
		private final PositionedContent theContent;

		/**
		 * @param declarer The toolkit that declared the modifier
		 * @param typeRestriction The sub-type of the value to restrict specified values to. Null means to inherit from the super-specified
		 *        value.
		 * @param specify The specification for the value. Null means to inherit from the super-specified value.
		 * @param defaultValue The default value to use if the value is not specified. Null means to inherit from the super-specified value.
		 * @param description The description for this modification
		 * @param content The content that specified the modifier
		 */
		public Default(QonfigToolkit declarer, QonfigValueType typeRestriction, SpecificationType specify, Object defaultValue,
			LocatedPositionedContent defaultValueContent, String description, PositionedContent content) {
			theDeclarer = declarer;
			theTypeRestriction = typeRestriction;
			theSpecification = specify;
			theDefaultValue = defaultValue;
			theDefaultValueContent = defaultValueContent;
			theDescription = description;
			theContent = content;
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
		public String getDescription() {
			return theDescription;
		}

		@Override
		public LocatedPositionedContent getDefaultValueContent() {
			return theDefaultValueContent;
		}

		@Override
		public PositionedContent getContent() {
			return theContent;
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

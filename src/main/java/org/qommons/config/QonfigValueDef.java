package org.qommons.config;

import org.qommons.io.LocatedPositionedContent;
import org.qommons.io.PositionedContent;

/** Represents an element value specification */
public interface QonfigValueDef extends QonfigElementOwned {
	/** An element value specification as originally declared */
	interface Declared extends QonfigValueDef, QonfigElementOwned.Declared {
		@Override
		default QonfigValueDef.Declared getDeclared() {
			return this;
		}
	}

	@Override
	Declared getDeclared();

	/** @return The type of value that must be specified */
	QonfigValueType getType();

	/** @return The specification of the value (whether the user must, may, or cannot specify the value) */
	SpecificationType getSpecification();

	/** @return The value to use if the user does not specify it */
	Object getDefaultValue();

	/** @return The content in the source file specifying the default value */
	LocatedPositionedContent getDefaultValueContent();

	/** Abstract {@link QonfigValueDef} implementation */
	public static abstract class Abstract implements QonfigValueDef {
		private final QonfigElementOrAddOn theOwner;
		private final QonfigValueType theType;
		private final SpecificationType theSpecification;
		private final Object theDefaultValue;
		private final LocatedPositionedContent theDefaultValueContent;
		private final PositionedContent thePosition;
		private final String theDescription;

		/**
		 * @param owner The owner of the value
		 * @param type The type that must be specified
		 * @param specify The specification of the value
		 * @param defaultValue The value to use if it is not specified
		 * @param defaultValueContent The content in the source file specifying the default value
		 * @param position The position in the file where this value was defined
		 * @param description The description for this value
		 */
		protected Abstract(QonfigElementOrAddOn owner, QonfigValueType type, SpecificationType specify, Object defaultValue,
			LocatedPositionedContent defaultValueContent, PositionedContent position, String description) {
			theOwner = owner;
			theType = type;
			theSpecification = specify;
			theDefaultValue = defaultValue;
			theDefaultValueContent = defaultValueContent;
			thePosition = position;
			theDescription = description;
		}

		@Override
		public abstract DeclaredValueDef getDeclared();

		@Override
		public QonfigElementOrAddOn getOwner() {
			return theOwner;
		}

		@Override
		public QonfigToolkit getDeclarer() {
			return getOwner().getDeclarer();
		}

		@Override
		public String getName() {
			return "text";
		}

		@Override
		public QonfigValueType getType() {
			return theType;
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
		public LocatedPositionedContent getDefaultValueContent() {
			return theDefaultValueContent;
		}

		@Override
		public PositionedContent getFilePosition() {
			return thePosition;
		}

		@Override
		public String getDescription() {
			return theDescription;
		}

		@Override
		public int hashCode() {
			return getOwner().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof QonfigValueDef))
				return false;
			return getOwner().equals(((QonfigValueDef) obj).getOwner());
		}

		@Override
		public String toString() {
			return getOwner() + ".text";
		}
	}

	/** {@link QonfigValueDef.Declared Declared} implementation */
	public static class DeclaredValueDef extends Abstract implements Declared {
		/**
		 * @param owner The element that declared the value
		 * @param type The type that must be specified
		 * @param specify The specification of the value
		 * @param defaultValue The value to use if it is not specified
		 * @param defaultValueContent The content in the source file specifying the default value
		 * @param position The position in the file where this value was defined
		 * @param description The description for this value
		 */
		public DeclaredValueDef(QonfigElementDef owner, QonfigValueType type, SpecificationType specify, Object defaultValue,
			LocatedPositionedContent defaultValueContent, PositionedContent position, String description) {
			super(owner, type, specify, defaultValue, defaultValueContent, position, description);
		}

		@Override
		public DeclaredValueDef getDeclared() {
			return this;
		}

		@Override
		public QonfigElementDef getOwner() {
			return (QonfigElementDef) super.getOwner();
		}
	}

	/** {@link QonfigValueDef} implementation for inherited or modified value definitions */
	public static class Modified extends Abstract {
		private final DeclaredValueDef theDeclared;

		/**
		 * @param declared The declared value being inherited
		 * @param owner The owner of the value
		 * @param type The type that must be specified
		 * @param specify The specification of the value
		 * @param defaultValue The value to use if it is not specified
		 * @param defaultValueContent
		 * @param position The position in the file where this value was defined
		 * @param description The description for the value modification
		 */
		public Modified(QonfigValueDef declared, QonfigElementOrAddOn owner, QonfigValueType type, SpecificationType specify,
			Object defaultValue, LocatedPositionedContent defaultValueContent, PositionedContent position, String description) {
			super(owner, type, specify, defaultValue, defaultValueContent, position, description);
			theDeclared = declared instanceof DeclaredValueDef ? (DeclaredValueDef) declared
				: ((QonfigValueDef.Modified) declared).getDeclared();
		}

		@Override
		public DeclaredValueDef getDeclared() {
			return theDeclared;
		}
	}
}

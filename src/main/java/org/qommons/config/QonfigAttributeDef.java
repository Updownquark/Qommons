package org.qommons.config;

import java.util.Objects;

import org.qommons.io.LocatedPositionedContent;
import org.qommons.io.PositionedContent;

/** An attribute that may be specified on an element */
public interface QonfigAttributeDef extends QonfigValueDef {
	/** An attribute specification as originally declared */
	interface Declared extends QonfigAttributeDef, QonfigValueDef.Declared {
		@Override
		default QonfigAttributeDef.Declared getDeclared() {
			return this;
		}
	}

	@Override
	QonfigAttributeDef.Declared getDeclared();

	/** Abstract {@link QonfigAttributeDef} implementation */
	public static abstract class Abstract implements QonfigAttributeDef {
		private final QonfigElementOrAddOn theOwner;
		private final QonfigValueType theType;
		private final SpecificationType theSpecification;
		private final Object theDefaultValue;
		private final LocatedPositionedContent theDefaultValueContent;
		private final PositionedContent thePosition;
		private final String theDescription;

		/**
		 * @param owner The element-def or add-on that the attribute belongs to
		 * @param type The type for the attribute value
		 * @param specify The specification of the attribute
		 * @param defaultValue The value to use if it is not specified
		 * @param position Number The line number in the file where this attribute was defined
		 * @param description The description for this attribute
		 */
		public Abstract(QonfigElementOrAddOn owner, QonfigValueType type, SpecificationType specify, Object defaultValue,
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
		public abstract DeclaredAttributeDef getDeclared();

		@Override
		public abstract String getName();

		@Override
		public QonfigElementOrAddOn getOwner() {
			return theOwner;
		}

		@Override
		public QonfigToolkit getDeclarer() {
			return getOwner().getDeclarer();
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
			return Objects.hash(getOwner(), getName());
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof QonfigAttributeDef))
				return false;
			return getOwner().equals(((QonfigAttributeDef) obj).getOwner()) && getName().equals(((QonfigAttributeDef) obj).getName());
		}

		@Override
		public String toString() {
			return getOwner() + "." + getName();
		}
	}

	/** {@link QonfigAttributeDef.Declared QonfigAttributeDef.Declared} implementation */
	public static class DeclaredAttributeDef extends Abstract implements Declared {
		private final String theName;

		/**
		 * @param owner The element-def or add-on that the attribute belongs to
		 * @param name The name of the attribute
		 * @param type The type for the attribute value
		 * @param specify The specification of the attribute
		 * @param defaultValue The value to use if it is not specified
		 * @param position The position in the file where this attribute was defined
		 * @param description The description for this attribute
		 */
		public DeclaredAttributeDef(QonfigElementOrAddOn owner, String name, QonfigValueType type, SpecificationType specify,
			Object defaultValue, LocatedPositionedContent defaultValueContent, PositionedContent position, String description) {
			super(owner, type, specify, defaultValue, defaultValueContent, position, description);
			theName = name;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public DeclaredAttributeDef getDeclared() {
			return this;
		}

		@Override
		public int hashCode() {
			return Objects.hash(getOwner(), theName);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof DeclaredAttributeDef))
				return false;
			DeclaredAttributeDef other = (DeclaredAttributeDef) obj;
			return getOwner().equals(other.getOwner()) && theName.equals(other.theName);
		}
	}

	/** A modified or inherited {@link QonfigAttributeDef} */
	public static class Modified extends Abstract implements Declared {
		private final DeclaredAttributeDef theDeclared;

		/**
		 * @param declared The inherited attribute
		 * @param owner The element-def or add-on that the attribute belongs to
		 * @param type The type for the attribute value
		 * @param specify The specification of the attribute
		 * @param defaultValue The value to use if it is not specified
		 * @param position The position in the file where this attribute modification was defined
		 * @param description The description for the attribute modification
		 */
		public Modified(QonfigAttributeDef declared, QonfigElementOrAddOn owner, QonfigValueType type,
			SpecificationType specify, Object defaultValue, LocatedPositionedContent defaultValueContent, PositionedContent position,
			String description) {
			super(owner, type, specify, defaultValue, defaultValueContent, position, description);
			theDeclared = declared instanceof DeclaredAttributeDef ? (DeclaredAttributeDef) declared
				: ((QonfigAttributeDef.Modified) declared).getDeclared();
		}

		@Override
		public DeclaredAttributeDef getDeclared() {
			return theDeclared;
		}

		@Override
		public String getName() {
			return theDeclared.getName();
		}
	}
}

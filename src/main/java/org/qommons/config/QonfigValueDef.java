package org.qommons.config;

import org.qommons.io.ContentPosition;

/** Represents an element value specification */
public interface QonfigValueDef extends QonfigElementOwned {
	/** An element value specification as originally declared */
	interface Declared extends QonfigValueDef {
	}

	/** @return The specification as originally declared */
	Declared getDeclared();

	/** @return The type of value that must be specified */
	QonfigValueType getType();

	/** @return The specification of the value (whether the user must, may, or cannot specify the value) */
	SpecificationType getSpecification();

	/** @return The value to use if the user does not specify it */
	Object getDefaultValue();

	/** Abstract {@link QonfigValueDef} implementation */
	public static abstract class Abstract implements QonfigValueDef {
		private final QonfigElementOrAddOn theOwner;
		private final QonfigValueType theType;
		private final SpecificationType theSpecification;
		private final Object theDefaultValue;
		private final ContentPosition thePosition;

		/**
		 * @param owner The owner of the value
		 * @param type The type that must be specified
		 * @param specify The specification of the value
		 * @param defaultValue The value to use if it is not specified
		 * @param position The position in the file where this value was defined
		 */
		public Abstract(QonfigElementOrAddOn owner, QonfigValueType type, SpecificationType specify, Object defaultValue,
			ContentPosition position) {
			theOwner = owner;
			theType = type;
			theSpecification = specify;
			theDefaultValue = defaultValue;
			thePosition = position;
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
		public ContentPosition getFilePosition() {
			return thePosition;
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

	/** {@link Declared QonfigValueDef.Declared} implementation */
	public static class DeclaredValueDef extends Abstract implements Declared {
		/**
		 * @param owner The element that declared the value
		 * @param type The type that must be specified
		 * @param specify The specification of the value
		 * @param defaultValue The value to use if it is not specified
		 * @param position The position in the file where this value was defined
		 */
		public DeclaredValueDef(QonfigElementDef owner, QonfigValueType type, SpecificationType specify, Object defaultValue,
			ContentPosition position) {
			super(owner, type, specify, defaultValue, position);
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
		 * @param position The position in the file where this value was defined
		 */
		public Modified(QonfigValueDef declared, QonfigElementOrAddOn owner, QonfigValueType type, SpecificationType specify,
			Object defaultValue, ContentPosition position) {
			super(owner, type, specify, defaultValue, position);
			theDeclared = declared instanceof DeclaredValueDef ? (DeclaredValueDef) declared
				: ((QonfigValueDef.Modified) declared).getDeclared();
		}

		@Override
		public DeclaredValueDef getDeclared() {
			return theDeclared;
		}
	}
}

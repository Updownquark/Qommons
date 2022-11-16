package org.qommons.config;

import java.util.Objects;

/** An attribute that may be specified on an element */
public interface QonfigAttributeDef extends QonfigValueDef {
	/** An attribute specification as originally declared */
	interface Declared extends QonfigAttributeDef, QonfigValueDef.Declared {
	}

	@Override
	QonfigAttributeDef.Declared getDeclared();

	/** Abstract {@link QonfigAttributeDef} implementation */
	public static abstract class Abstract implements QonfigAttributeDef {
		private final QonfigElementOrAddOn theOwner;
		private final QonfigValueType theType;
		private final SpecificationType theSpecification;
		private final Object theDefaultValue;
		private final int theLineNumber;

		/**
		 * @param owner The element-def or add-on that the attribute belongs to
		 * @param type The type for the attribute value
		 * @param specify The specification of the attribute
		 * @param defaultValue The value to use if it is not specified
		 * @param lineNumber The line number in the file where this attribute was defined
		 */
		public Abstract(QonfigElementOrAddOn owner, QonfigValueType type, SpecificationType specify, Object defaultValue, int lineNumber) {
			theOwner = owner;
			theType = type;
			theSpecification = specify;
			theDefaultValue = defaultValue;
			theLineNumber = lineNumber;
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
		public int getLineNumber() {
			return theLineNumber;
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
		 * @param lineNumber The line number in the file where this attribute was defined
		 */
		public DeclaredAttributeDef(QonfigElementOrAddOn owner, String name, QonfigValueType type, SpecificationType specify,
			Object defaultValue, int lineNumber) {
			super(owner, type, specify, defaultValue, lineNumber);
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
		 * @param lineNumber The line number in the file where this attribute was defined
		 */
		public Modified(QonfigAttributeDef declared, QonfigElementOrAddOn owner, QonfigValueType type,
			SpecificationType specify, Object defaultValue, int lineNumber) {
			super(owner, type, specify, defaultValue, lineNumber);
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

package org.qommons.config;

import java.util.List;

import org.qommons.Named;

/** A type of values that can be parsed from attribute or element text values */
public interface QonfigValueType extends Named {
	/** The "string" type */
	public static final QonfigValueType STRING = new QonfigValueType() {
		@Override
		public String getName() {
			return "string";
		}

		@Override
		public Object parse(String value, QonfigToolkit tk, QonfigParseSession session) {
			return value;
		}

		@Override
		public boolean isInstance(Object value) {
			return value instanceof String;
		}

		@Override
		public String toString() {
			return "string";
		}
	};
	/** The "boolean" type */
	public static final QonfigValueType BOOLEAN = new QonfigValueType() {
		@Override
		public String getName() {
			return "boolean";
		}

		@Override
		public Object parse(String value, QonfigToolkit tk, QonfigParseSession session) {
			switch (value) {
			case "true":
				return Boolean.TRUE;
			case "false":
				return Boolean.FALSE;
			default:
				session.withError("Illegal boolean value: " + value);
				return Boolean.FALSE;
			}
		}

		@Override
		public boolean isInstance(Object value) {
			return value instanceof Boolean;
		}

		@Override
		public String toString() {
			return "boolean";
		}
	};

	/** A declared type (as opposed to a modified one */
	public static interface Declared extends QonfigValueType, QonfigType {
	}

	/** A literal value */
	public static class LiteralAttributeType implements Declared {
		private final QonfigToolkit theDeclarer;
		private final String theValue;

		/**
		 * @param declarer The toolkit that declared the literal
		 * @param value The literal value to match
		 */
		public LiteralAttributeType(QonfigToolkit declarer, String value) {
			theDeclarer = declarer;
			theValue = value;
		}

		@Override
		public QonfigToolkit getDeclarer() {
			return theDeclarer;
		}

		@Override
		public String getName() {
			return "literal";
		}

		@Override
		public Object parse(String value, QonfigToolkit tk, QonfigParseSession session) {
			if (value.equals(theValue))
				return theValue;
			else
				session.withError("'" + theValue + "' expected, not '" + value + "'");
			return null;
		}

		@Override
		public boolean isInstance(Object value) {
			return theValue.equals(value);
		}

		@Override
		public String toString() {
			return "literal:" + theValue;
		}
	}

	/** An attribute type that delegates to others */
	public class OneOfAttributeType implements Declared {
		private final QonfigToolkit theDeclarer;
		private final List<QonfigValueType> theComponents;

		/**
		 * @param declarer The toolkit that declared the one-of type
		 * @param components The components to delegate to
		 */
		public OneOfAttributeType(QonfigToolkit declarer, List<QonfigValueType> components) {
			theDeclarer = declarer;
			theComponents = components;
		}

		@Override
		public QonfigToolkit getDeclarer() {
			return theDeclarer;
		}

		@Override
		public String getName() {
			return "one-of";
		}

		@Override
		public Object parse(String value, QonfigToolkit tk, QonfigParseSession session) {
			QonfigParseSession testEnv = QonfigParseSession.forRoot("", tk);
			QonfigValueType best = null;
			for (QonfigValueType component : theComponents) {
				testEnv.getErrors().clear();
				testEnv.getWarnings().clear();
				Object parsed = component.parse(value, tk, testEnv);
				if (testEnv.getErrors().isEmpty() && testEnv.getWarnings().isEmpty())
					return parsed;
				else if (best == null && testEnv.getErrors().isEmpty())
					best = component;
			}
			if (best != null)
				return best.parse(value, tk, session);
			else
				session.withError(this + " expected");
			return null;
		}

		@Override
		public boolean isInstance(Object value) {
			for (QonfigValueType component : theComponents)
				if (component.isInstance(value))
					return true;
			return false;
		}

		@Override
		public String toString() {
			return "one-of:" + theComponents;
		}
	}

	/** Wraps a {@link CustomValueType} */
	public class Custom implements Declared {
		private final QonfigToolkit theDeclarer;
		private final CustomValueType theCustomType;

		/**
		 * @param declarer The toolkit declaring the value type
		 * @param customType The custom-implemented value type
		 */
		public Custom(QonfigToolkit declarer, CustomValueType customType) {
			theDeclarer = declarer;
			theCustomType = customType;
		}

		@Override
		public QonfigToolkit getDeclarer() {
			return theDeclarer;
		}

		@Override
		public Object parse(String value, QonfigToolkit tk, QonfigParseSession session) {
			return theCustomType.parse(value, tk, session);
		}

		@Override
		public boolean isInstance(Object value) {
			return theCustomType.isInstance(value);
		}

		@Override
		public String getName() {
			return theCustomType.getName();
		}

		@Override
		public String toString() {
			return theCustomType.toString();
		}
	}

	/**
	 * Parses a value
	 * 
	 * @param value The text to parse
	 * @param tk The toolkit that the document belongs to
	 * @param session The session to report errors
	 * @return The parsed value
	 */
	Object parse(String value, QonfigToolkit tk, QonfigParseSession session);

	/**
	 * @param value The value to test
	 * @return Whether the given value is an instance of this type
	 */
	boolean isInstance(Object value);
}

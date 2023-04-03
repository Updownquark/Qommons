package org.qommons.config;

import java.util.List;

import org.qommons.Named;
import org.qommons.io.FilePosition;

/** A type of values that can be parsed from attribute or element text values */
public interface QonfigValueType extends Named, FileSourced {
	/** The "string" type */
	public static final QonfigValueType STRING = new QonfigValueType() {
		@Override
		public String getName() {
			return "string";
		}

		@Override
		public FilePosition getFilePosition() {
			return null;
		}

		@Override
		public Object parse(String value, QonfigToolkit tk, ErrorReporting session) {
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
		public FilePosition getFilePosition() {
			return null;
		}

		@Override
		public Object parse(String value, QonfigToolkit tk, ErrorReporting session) {
			switch (value) {
			case "true":
				return Boolean.TRUE;
			case "false":
				return Boolean.FALSE;
			default:
				session.error("Illegal boolean value: " + value);
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
	public static class Literal implements Declared {
		private final QonfigToolkit theDeclarer;
		private final String theValue;
		private final FilePosition thePosition;

		/**
		 * @param declarer The toolkit that declared the literal
		 * @param value The literal value to match
		 * @param position The position in the file where this type was defined
		 */
		public Literal(QonfigToolkit declarer, String value, FilePosition position) {
			theDeclarer = declarer;
			theValue = value;
			thePosition = position;
		}

		@Override
		public QonfigToolkit getDeclarer() {
			return theDeclarer;
		}

		@Override
		public String getName() {
			return "literal";
		}

		/** @return The literal value */
		public String getValue() {
			return theValue;
		}

		@Override
		public Object parse(String value, QonfigToolkit tk, ErrorReporting session) {
			if (value.equals(theValue))
				return theValue;
			else
				session.error("'" + theValue + "' expected, not '" + value + "'");
			return null;
		}

		@Override
		public boolean isInstance(Object value) {
			return theValue.equals(value);
		}

		@Override
		public FilePosition getFilePosition() {
			return thePosition;
		}

		@Override
		public String toString() {
			return "literal:" + theValue;
		}
	}

	/** An attribute type that delegates to others */
	public class OneOf implements Declared {
		private final QonfigToolkit theDeclarer;
		private final String theName;
		private final List<QonfigValueType> theComponents;
		private final FilePosition thePosition;

		/**
		 * @param declarer The toolkit that declared the one-of type
		 * @param name The name for the type
		 * @param components The components to delegate to
		 * @param position The position in the file where this type was defined
		 */
		public OneOf(QonfigToolkit declarer, String name, List<QonfigValueType> components, FilePosition position) {
			theDeclarer = declarer;
			theName = name;
			theComponents = components;
			thePosition = position;
		}

		@Override
		public QonfigToolkit getDeclarer() {
			return theDeclarer;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public Object parse(String value, QonfigToolkit tk, ErrorReporting session) {
			QonfigParseSession testEnv = QonfigParseSession.forRoot("", tk, thePosition);
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
				session.error(this + " expected");
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
		public FilePosition getFilePosition() {
			return thePosition;
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
		private final FilePosition thePosition;

		/**
		 * @param declarer The toolkit declaring the value type
		 * @param customType The custom-implemented value type
		 * @param position The position in the file where this type was defined
		 */
		public Custom(QonfigToolkit declarer, CustomValueType customType, FilePosition position) {
			theDeclarer = declarer;
			theCustomType = customType;
			thePosition = position;
		}

		@Override
		public QonfigToolkit getDeclarer() {
			return theDeclarer;
		}

		/** @return The custom type fulfilling this declared custom type */
		public CustomValueType getCustomType() {
			return theCustomType;
		}

		@Override
		public Object parse(String value, QonfigToolkit tk, ErrorReporting session) {
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
		public FilePosition getFilePosition() {
			return thePosition;
		}

		@Override
		public String toString() {
			return theCustomType.toString();
		}
	}

	/** Wraps another type, requiring it to be explicitly specified using a prefix and/or suffix */
	public class Explicit implements Declared{
		private final QonfigToolkit theDeclarer;
		private final String theName;
		private final QonfigValueType theType;
		private final String thePrefix;
		private final String theSuffix;
		private final FilePosition thePosition;

		/**
		 * @param declarer The toolkit declaring this type
		 * @param name The name of the type
		 * @param type The type to do the parsing
		 * @param prefix The prefix that must be prepended to values
		 * @param suffix The suffix that must be appended to values
		 * @param position The position in the file where this type was defined
		 */
		public Explicit(QonfigToolkit declarer, String name, QonfigValueType type, String prefix, String suffix, FilePosition position) {
			theDeclarer = declarer;
			theName = name;
			theType = type;
			thePrefix = prefix;
			theSuffix = suffix;
			thePosition = position;
		}

		@Override
		public Object parse(String value, QonfigToolkit tk, ErrorReporting session) {
			if (!value.startsWith(thePrefix) || !value.endsWith(theSuffix)) {
				StringBuilder err = new StringBuilder("Value must ");
				if (thePrefix.isEmpty()) {
					err.append("end with '" + theSuffix + "'");
				} else if (theSuffix.isEmpty()) {
					err.append("start with '" + thePrefix + "'");
				} else
					err.append("start with '" + thePrefix + "' and end with '" + theSuffix + "'");
				session.error(err.toString());
				return null;
			}
			return theType.parse(value.substring(thePrefix.length(), value.length() - theSuffix.length()), tk, session);
		}

		@Override
		public boolean isInstance(Object value) {
			return theType.isInstance(value);
		}

		@Override
		public QonfigToolkit getDeclarer() {
			return theDeclarer;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public FilePosition getFilePosition() {
			return thePosition;
		}

		@Override
		public String toString() {
			return theName;
		}
	}

	/**
	 * Parses a value
	 * 
	 * @param value The text to parse
	 * @param tk The toolkit that the document belongs to
	 * @param errors The reporting to report errors
	 * @return The parsed value
	 */
	Object parse(String value, QonfigToolkit tk, ErrorReporting errors);

	/**
	 * @param value The value to test
	 * @return Whether the given value is an instance of this type
	 */
	boolean isInstance(Object value);
}

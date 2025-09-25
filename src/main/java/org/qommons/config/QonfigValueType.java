package org.qommons.config;

import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.qommons.MultiInheritanceSet;
import org.qommons.Named;
import org.qommons.QommonsUtils;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedPositionedContent;
import org.qommons.io.PositionedContent;

/** A type of values that can be parsed from attribute or element text values */
public interface QonfigValueType extends Named, FileSourced {
	/** The "string" type */
	public static final QonfigValueType STRING = new QonfigValueType() {
		@Override
		public String getName() {
			return "string";
		}

		@Override
		public PositionedContent getFilePosition() {
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
		public PositionedContent getFilePosition() {
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

	/** The "int" type */
	public static final QonfigValueType INT = new QonfigValueType() {
		@Override
		public String getName() {
			return "int";
		}

		@Override
		public PositionedContent getFilePosition() {
			return null;
		}

		@Override
		public boolean isInstance(Object value) {
			return value instanceof Integer;
		}

		@Override
		public Object parse(String value, QonfigToolkit tk, ErrorReporting errors) {
			try {
				return Integer.parseInt(value);
			} catch (NumberFormatException e) {
				errors.error("Bad integer value: " + value);
				return 0;
			}
		}
	};

	/**
	 * An element-def or add-on type parsed from a {@link QonfigTypeValueType}
	 * 
	 * @param <T> element-def or add-on
	 */
	public static class QonfigTypeReference<T extends QonfigElementOrAddOn> {
		/** The element-def or add-on type value */
		public final T reference;
		/** The parsed content */
		public final LocatedPositionedContent content;

		/**
		 * @param reference The element-def or add-on type value
		 * @param content The parsed content
		 */
		public QonfigTypeReference(T reference, LocatedPositionedContent content) {
			this.reference = reference;
			this.content = content;
		}

		@Override
		public int hashCode() {
			return reference.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof QonfigTypeReference && reference.equals(((QonfigTypeReference<?>) obj).reference)
				&& Objects.equals(content, ((QonfigTypeReference<?>) obj).content);
		}

		@Override
		public String toString() {
			return content.toString();
		}
	}

	/** A value type referencing a Qonfig element or add-on type */
	public static class QonfigTypeValueType implements QonfigValueType {
		/** The pattern for matching element names */
		public static final Pattern PATTERN = Pattern.compile("((?<ns>[a-zA-Z0-9_\\-]+\\s+v?\\d+\\.\\d+)\\:)?(?<name>[a-zA-Z0-9_\\-]+)");

		@Override
		public String getName() {
			return "qonfig-type";
		}

		@Override
		public PositionedContent getFilePosition() {
			return null;
		}

		@Override
		public QonfigTypeReference<?> parse(String value, QonfigToolkit tk, ErrorReporting errors) {
			Matcher match = PATTERN.matcher(value);
			if (!match.matches()) {
				errors.error("`" + value + " is not a valid Qonfig type reference");
				return null;
			}
			String ns = match.group("ns");
			if (ns != null) {
				try {
					tk = tk.getDependency(ns);
				} catch (ParseException e) {
					errors.at(e.getErrorOffset()).error(e.getMessage(), e);
					return null;
				} catch (RuntimeException e) {
					errors.error(e.getMessage(), e);
					return null;
				}
			}
			QonfigElementOrAddOn type;
			try {
				type = tk.getElementOrAddOn(match.group("name"));
			} catch (RuntimeException e) {
				errors.at(match.start("name")).error(e.getMessage(), e);
				return null;
			}
			if (type == null) {
				errors.at(match.start("name")).error("No such type '" + match.group("name") + "' available to toolkit " + tk);
				return null;
			}
			return new QonfigTypeReference<>(type, errors.getFileLocation());
		}

		@Override
		public boolean isInstance(Object value) {
			return value instanceof QonfigTypeReference;
		}
	}

	/** Singleton {@link QonfigTypeValueType} */
	public static final QonfigTypeValueType QONFIG_TYPE = new QonfigTypeValueType();

	/** {@link QonfigTypeValueType} parsing element-def references */
	public static class QonfigElementTypeValueType extends QonfigTypeValueType {
		@Override
		public String getName() {
			return "qonfig-element-type";
		}

		@Override
		public QonfigTypeReference<QonfigElementDef> parse(String value, QonfigToolkit tk, ErrorReporting errors) {
			QonfigTypeReference<?> eoao = super.parse(value, tk, errors);
			if (eoao != null && !(eoao.reference instanceof QonfigElementDef)) {
				errors.error(eoao.reference + " is an add-on, not an element-def");
				return null;
			}
			return (QonfigTypeReference<QonfigElementDef>) eoao;
		}

		@Override
		public boolean isInstance(Object value) {
			return super.isInstance(value) && ((QonfigTypeReference<?>) value).reference instanceof QonfigElementDef;
		}
	}

	/** Singleton {@link QonfigElementTypeValueType} */
	public static final QonfigElementTypeValueType QONFIG_ELEMENT_TYPE = new QonfigElementTypeValueType();

	/** {@link QonfigTypeValueType} parsing add-on references */
	public static class QonfigAddOnTypeValueType extends QonfigTypeValueType {
		@Override
		public String getName() {
			return "qonfig-add-on";
		}

		@Override
		public QonfigTypeReference<QonfigAddOn> parse(String value, QonfigToolkit tk, ErrorReporting errors) {
			QonfigTypeReference<?> eoao = super.parse(value, tk, errors);
			if (eoao != null && !(eoao.reference instanceof QonfigAddOn)) {
				errors.error(eoao.reference + " is an element-def, not an add-on");
				return null;
			}
			return (QonfigTypeReference<QonfigAddOn>) eoao;
		}

		@Override
		public boolean isInstance(Object value) {
			return super.isInstance(value) && ((QonfigTypeReference<?>) value).reference instanceof QonfigAddOn;
		}
	}

	/** Singleton {@link QonfigAddOnTypeValueType} */
	public static final QonfigAddOnTypeValueType QONFIG_ADD_ON = new QonfigAddOnTypeValueType();

	/** A value type parsing sets of add-on references */
	public static class QonfigAddOnSetValueType implements QonfigValueType {
		@Override
		public String getName() {
			return "qonfig-add-on-set";
		}

		@Override
		public PositionedContent getFilePosition() {
			return null;
		}

		@Override
		public Set<QonfigTypeReference<QonfigAddOn>> parse(String value, QonfigToolkit tk, ErrorReporting errors) {
			if (value.isEmpty())
				return Collections.emptySet();
			int comma = value.indexOf(',');
			if (comma < 0)
				return Collections.singleton(QONFIG_ADD_ON.parse(value, tk, errors));
			MultiInheritanceSet<QonfigAddOn> addOns = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
			Set<QonfigTypeReference<QonfigAddOn>> refs = new LinkedHashSet<>();
			int start = 0;
			do {
				while (start < value.length() && Character.isWhitespace(value.charAt(start)))
					start++;
				if (start == value.length()) {
					errors.at(comma).warn("Missing terminal element");
					break;
				}
				int nextComma = value.indexOf(',', start);
				if (nextComma == start) {
					errors.at(comma).warn("Missing element");
					continue;
				}
				comma = nextComma;
				ErrorReporting elementErrors = errors.at(errors.getFileLocation().subSequence(start, comma < 0 ? value.length() : comma));
				QonfigTypeReference<QonfigAddOn> addOn = QONFIG_ADD_ON.parse(value.substring(0, comma).trim(), tk, elementErrors);
				refs.add(addOn);
				if (!addOns.add(addOn.reference)) {
					elementErrors.warn("Add-on " + addOn.reference + " is a duplicate");
				}
			} while (comma >= 0);
			return Collections.unmodifiableSet(refs);
		}

		@Override
		public boolean isInstance(Object value) {
			if (value instanceof Set) {
				for (Object v : ((Set<?>) value)) {
					if (!(v instanceof QonfigTypeReference) //
						|| !(((QonfigTypeReference<?>) v).reference instanceof QonfigAddOn))
						return false;
				}
				return true;
			} else
				return false;
		}
	}

	/** Singleton {@link QonfigAddOnSetValueType} */
	public static final QonfigAddOnSetValueType QONFIG_ADD_ON_SET = new QonfigAddOnSetValueType();

	/** All standard Qonfig value types by name */
	public static final Map<String, QonfigValueType> STANDARD_TYPES = QommonsUtils.<String, QonfigValueType> buildMap(null)//
		.with(STRING.getName(), STRING)//
		.with(BOOLEAN.getName(), BOOLEAN)//
		.with(INT.getName(), INT)//
		.with(QONFIG_TYPE.getName(), QONFIG_TYPE)//
		.with(QONFIG_ELEMENT_TYPE.getName(), QONFIG_ELEMENT_TYPE)//
		.with(QONFIG_ADD_ON.getName(), QONFIG_ADD_ON)//
		.with(QONFIG_ADD_ON_SET.getName(), QONFIG_ADD_ON_SET)//
		.getUnmodifiable();

	/** A declared type (as opposed to a modified one */
	public static interface Declared extends QonfigValueType, QonfigType {
	}

	/** A literal value */
	public static class Literal implements Declared {
		private final QonfigToolkit theDeclarer;
		private final String theValue;
		private final PositionedContent thePosition;
		private final String theDescription;

		/**
		 * @param declarer The toolkit that declared the literal
		 * @param value The literal value to match
		 * @param position The position in the file where this type was defined
		 * @param description The description for this value type
		 */
		public Literal(QonfigToolkit declarer, String value, PositionedContent position, String description) {
			theDeclarer = declarer;
			theValue = value;
			thePosition = position;
			theDescription = description;
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
				return this;
			else
				session.error("'" + theValue + "' expected, not '" + value + "'");
			return null;
		}

		@Override
		public boolean isInstance(Object value) {
			return theValue.equals(value);
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
		public String toString() {
			return String.valueOf(theValue);
		}
	}

	/** An attribute type that delegates to others */
	public class OneOf implements Declared {
		private final QonfigToolkit theDeclarer;
		private final String theName;
		private final List<QonfigValueType> theComponents;
		private final PositionedContent thePosition;
		private final String theDescription;

		/**
		 * @param declarer The toolkit that declared the one-of type
		 * @param name The name for the type
		 * @param components The components to delegate to
		 * @param position The position in the file where this type was defined
		 * @param description The description for this value type
		 */
		public OneOf(QonfigToolkit declarer, String name, List<QonfigValueType> components, PositionedContent position,
			String description) {
			theDeclarer = declarer;
			theName = name;
			theComponents = components;
			thePosition = position;
			theDescription = description;
		}

		@Override
		public QonfigToolkit getDeclarer() {
			return theDeclarer;
		}

		@Override
		public String getName() {
			return theName;
		}

		/** @return The type possibilities in this one-of */
		public List<QonfigValueType> getComponents() {
			return theComponents;
		}

		@Override
		public Object parse(String value, QonfigToolkit tk, ErrorReporting session) {
			QonfigParseSession testEnv = QonfigParseSession.forRoot(tk, thePosition);
			QonfigValueType best = null;
			for (QonfigValueType component : theComponents) {
				testEnv.getErrors().clear();
				Object parsed = component.parse(value, tk, testEnv);
				if (testEnv.getErrors().isEmpty())
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
		public PositionedContent getFilePosition() {
			return thePosition;
		}

		@Override
		public String getDescription() {
			return theDescription;
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
		private final PositionedContent thePosition;
		private final String theDescription;

		/**
		 * @param declarer The toolkit declaring the value type
		 * @param customType The custom-implemented value type
		 * @param position The position in the file where this type was defined
		 * @param description The description for this value type
		 */
		public Custom(QonfigToolkit declarer, CustomValueType customType, PositionedContent position, String description) {
			theDeclarer = declarer;
			theCustomType = customType;
			thePosition = position;
			theDescription = description;
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
		public PositionedContent getFilePosition() {
			return thePosition;
		}

		@Override
		public String getDescription() {
			return theDescription;
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
		private final PositionedContent thePosition;
		private final String theDescription;

		/**
		 * @param declarer The toolkit declaring this type
		 * @param name The name of the type
		 * @param type The type to do the parsing
		 * @param prefix The prefix that must be prepended to values
		 * @param suffix The suffix that must be appended to values
		 * @param position The position in the file where this type was defined
		 * @param description The description for this value type
		 */
		public Explicit(QonfigToolkit declarer, String name, QonfigValueType type, String prefix, String suffix, PositionedContent position,
			String description) {
			theDeclarer = declarer;
			theName = name;
			theType = type;
			thePrefix = prefix;
			theSuffix = suffix;
			thePosition = position;
			theDescription = description;
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
		public PositionedContent getFilePosition() {
			return thePosition;
		}

		@Override
		public String getDescription() {
			return theDescription;
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

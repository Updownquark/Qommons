package org.qommons.io;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.qommons.DefaultCharSubSequence;
import org.qommons.Named;
import org.qommons.QommonsUtils;
import org.qommons.TimeUtils;
import org.qommons.TimeUtils.TimeEvaluationOptions;

/**
 * Knows how to parse a type of value from text and to print a type of value into text
 * 
 * @param <T> The type of object that this object can parse and format
 */
public interface Format<T> {
	/**
	 * Appends a value into a StringBuilder in this format
	 * 
	 * @param text The text to append the value into
	 * @param value The value to append to the text
	 */
	void append(StringBuilder text, T value);

	/**
	 * Formats a value to a String
	 * 
	 * @param value The value to format
	 * @return The formatted value
	 */
	default String format(T value) {
		StringBuilder s = new StringBuilder();
		append(s, value);
		return s.toString();
	}

	/**
	 * @param text The text to parse
	 * @return The parsed value
	 * @throws ParseException If a value of this type was not recognized in the text
	 */
	T parse(CharSequence text) throws ParseException;

	/** Stupid-simple text format that just formats and parses text as-is */
	public static final Format<String> TEXT = new Format<String>() {
		@Override
		public void append(StringBuilder text, String value) {
			if (value != null)
				text.append(value);
		}

		@Override
		public String parse(CharSequence text) throws ParseException {
			return text.toString();
		}

		@Override
		public String toString() {
			return "TEXT";
		}
	};

	/** Parses integers from text */
	public static final Format<Integer> INT = new Format<Integer>() {
		@Override
		public void append(StringBuilder text, Integer value) {
			text.append(value);
		}

		@Override
		public Integer parse(CharSequence text) throws ParseException {
			Long parsed = LONG.parse(text);
			if (parsed.longValue() < Integer.MIN_VALUE || parsed.longValue() > Integer.MAX_VALUE)
				throw new ParseException("Integer values must be between " + Integer.MIN_VALUE + " and " + Integer.MAX_VALUE + ": " + text,
					0);
			return parsed.intValue();
		}

		@Override
		public String toString() {
			return "INT";
		}
	};

	/** Parses long integers from text */
	public static final Format<Long> LONG = new Format<Long>() {
		private static final String MAX_TEXT = "" + Long.MAX_VALUE;

		@Override
		public void append(StringBuilder text, Long value) {
			text.append(value);
		}

		@Override
		public Long parse(CharSequence text) throws ParseException {
			int i = 0;
			boolean neg = false;
			if (i < text.length() && text.charAt(i) == '-') {
				neg = true;
				i++;
			}
			if (text.length() == i)
				throw new ParseException("Must be an integer value", i);
			else if (text.length() - i > MAX_TEXT.length())
				throw new ParseException("Text is too large to be an integer", 0);
			long value = 0;
			while (i < text.length()) {
				char c = text.charAt(i);
				if (c >= '0' && c <= '9')
					value = value * 10 + (c - '0');
				else
					throw new ParseException("'" + c + "' is not valid for integer text", i);
				i++;
			}
			if (value < 0)
				throw new ParseException("Integer value is too large", 0);
			if (neg)
				value = -value;
			return value;
		}

		@Override
		public String toString() {
			return "LONG";
		}
	};

	/** Formats a boolean value to "true" or "false" (or "null") */
	public static final Format<Boolean> BOOLEAN = new Format<Boolean>() {
		@Override
		public void append(StringBuilder text, Boolean value) {
			text.append(value);
		}

		@Override
		public Boolean parse(CharSequence text) throws ParseException {
			String str = text.toString().toLowerCase();
			if (str.equals("true"))
				return Boolean.TRUE;
			else if (str.equals("null"))
				return null;
			else
				return Boolean.FALSE;
		}

		@Override
		public String toString() {
			return "BOOLEAN";
		}
	};

	/**
	 * @param <E> The enum type
	 * @param type The enum class
	 * @return A format to parse and format enum values from/to text
	 */
	public static <E extends Enum<?>> Format<E> enumFormat(Class<E> type) {
		return new EnumFormat<>(type);
	}

	/** Parses durations from text */
	public static final Format<Duration> DURATION = new Format<Duration>() {
		@Override
		public void append(StringBuilder text, Duration value) {
			if (value != null)
				QommonsUtils.printDuration(value, text, true);
		}

		@Override
		public Duration parse(CharSequence text) throws ParseException {
			return QommonsUtils.parseDuration(text);
		}

		@Override
		public String toString() {
			return "DURATION";
		}
	};

	/**
	 * @param pattern Pattern to match
	 * @param errorText The error message to throw for non-matches
	 * @return A text format that throws an exception when text does not match the pattern
	 */
	public static Format<String> validatedText(Pattern pattern, String errorText) {
		return validatedText(pattern, m -> m.matches() ? null : errorText);
	}

	/**
	 * @param pattern Pattern to match
	 * @param errorText Supplies an error message for non-matches
	 * @return A text format that throws an exception when text does not match the pattern
	 */
	public static Format<String> validatedText(Pattern pattern, Function<Matcher, String> errorText) {
		return new Format<String>() {
			@Override
			public void append(StringBuilder text, String value) {
				text.append(value);
			}

			@Override
			public String parse(CharSequence text) throws ParseException {
				Matcher matcher = pattern.matcher(text);
				String error = errorText.apply(matcher);
				if (error != null)
					throw new ParseException(error, 0);
				return text.toString();
			}

			@Override
			public String toString() {
				return pattern.pattern() + "(" + errorText + ")";
			}
		};
	}

	/**
	 * @param pattern The float format pattern
	 * @return A float-value format with the given pattern
	 * @see DecimalFormat#DecimalFormat(String)
	 */
	public static Format<Float> floatFormat(String pattern) {
		Format<Double> doubleFormat = doubleFormat(pattern);
		return new Format<Float>() {
			@Override
			public void append(StringBuilder text, Float value) {
				if (value == null)
					return;
				text.append(value);
			}

			@Override
			public Float parse(CharSequence text) throws ParseException {
				Double d = doubleFormat.parse(text);
				if (d.doubleValue() < Float.MIN_VALUE || d.doubleValue() > Float.MAX_VALUE)
					throw new ParseException("Float values must be between " + Float.MIN_VALUE + " and " + Float.MAX_VALUE, 0);
				return d.floatValue();
			}

			@Override
			public String toString() {
				return "FLOAT(" + pattern + ")";
			}
		};
	}

	/**
	 * @param pattern The float format pattern
	 * @return A double-value format with the given pattern
	 * @see DecimalFormat#DecimalFormat(String)
	 */
	public static Format<Double> doubleFormat(String pattern) {
		// DecimalFormat instances are not thread-safe
		ThreadLocal<DecimalFormat> format = ThreadLocal.withInitial(() -> new DecimalFormat(pattern));
		format.get(); // Validate the pattern
		return new Format<Double>() {
			@Override
			public void append(StringBuilder text, Double value) {
				if (value == null)
					return;
				text.append(format.get().format(value.doubleValue()));
			}

			@Override
			public Double parse(CharSequence text) throws ParseException {
				return parseDouble(text, format.get());
			}

			@Override
			public String toString() {
				return "DOUBLE(" + pattern + ")";
			}
		};
	}

	/*public static enum DecimalComponent {
		WHOLE, DECIMAL, FRACTION, E, EXPONENT
	}
	
	public static FlexibleFormat.FormatToken intToken(boolean withNegative, int separator) {
		class IntToken implements FlexibleFormat.FormatToken {
			private boolean neg() {
				return withNegative;
			}
	
			private int sep() {
				return separator;
			}
	
			@Override
			public int find(CharSequence seq, int start) {
				if (withNegative && seq.charAt(start) == '-') {
					start++;
					if (start == seq.length())
						return -1;
				}
				if (seq.charAt(start) < '0' || seq.charAt(start) > '9')
					return -1;
				start++;
				int lastSep = -1;
				while (start < seq.length()) {
					if (seq.charAt(start) >= '0' && seq.charAt(start) <= '9')
						continue;
					else if (seq.charAt(start) == separator) {
						if (lastSep >= 0 && start - lastSep != 3)
							break;
						lastSep = start;
					} else
						return start;
				}
				if (lastSep < 0 && separator != 0)
					return -1;
				if (lastSep >= 0 && start - lastSep != 3)
					return lastSep;
				return start;
			}
	
			@Override
			public int parse(CharSequence match) {
				if (separator == 0)
					return Integer.parseInt(match.toString());
				else
					return Integer.parseInt(match.toString().replaceAll("" + (char) separator, ""));
			}
	
			@Override
			public int hashCode() {
				return separator & (withNegative ? 0x800 : 0);
			}
	
			@Override
			public boolean equals(Object obj) {
				if (obj == this)
					return true;
				else if (!(obj instanceof IntToken))
					return false;
				return withNegative == ((IntToken) obj).neg() && separator == ((IntToken) obj).sep();
			}
	
			@Override
			public String toString() {
				return "int(" + withNegative + ", " + separator + ")";
			}
		}
		return new IntToken();
	}
	
	public static final FlexibleFormat<DecimalComponent> DOUBLE_FORMAT = FlexibleFormat.<DecimalComponent> build()//
		.withComponent("W", DecimalComponent.WHOLE, intToken(true, 0), null)//
		.withComponent("WC", DecimalComponent.WHOLE, intToken(true, ','), null)//
		.withComponent("WD", DecimalComponent.WHOLE, intToken(true, '.'), null)//
		.withComponent(".", DecimalComponent.DECIMAL, FlexibleFormat.single('.'), null)//
		.withComponent(",", DecimalComponent.DECIMAL, FlexibleFormat.single(','), null)//
		.withComponent("F", DecimalComponent.FRACTION, FlexibleFormat.DIGIT, null)//
		.withComponent("FC", DecimalComponent.FRACTION, intToken(false, ','), null)//
		.withComponent("FD", DecimalComponent.FRACTION, intToken(false, '.'), null)//
		.withComponent("E", DecimalComponent.E, FlexibleFormat.single('E', 'e'), null)//
		.withComponent("X", DecimalComponent.EXPONENT, intToken(true, 0), null)//
		.withOption("W")//
		.withOption("WC")//
		.withOption("WD")//
		.withOption("W", OPTIONAL, ".", "F")//
		.withOption("WC", OPTIONAL, ".", "FC")//
		.withOption("WD", OPTIONAL, ",", "FD")//
		.withOption("W", OPTIONAL, ".", "F", "E", "X")//
		.withOption("WC", OPTIONAL, ".", "FC", "E", "X")//
		.withOption("WD", OPTIONAL, ",", "FD", "E", "X")//
		.withOption("W", ",", "F")//
		.withOption("WD", ",", "FD")//
		.withOption("W", ",", "F", "E", "X")//
		.withOption("W", ".", OPTIONAL, "E", "X")//
		.withOption("WC", ".", OPTIONAL, "E", "X")//
		.withOption("WD", ",", OPTIONAL, "E", "X")//
		.build();*/

	static final Pattern ALT_DEC_PATTERN = Pattern.compile("\\d*,\\d*[Ee]\\d+");

	/**
	 * @param text The text to parse
	 * @param format The number format to do most of the work
	 * @return The parsed value
	 * @throws ParseException If the value cannot be parsed
	 */
	public static double parseDouble(CharSequence text, NumberFormat format) throws ParseException {
		String str = text.toString();
		if ("NaN".equals(str))
			return Double.NaN;
		else if ("-Inf".equals(str) || "-Infinity".equals(str))
			return Double.NEGATIVE_INFINITY;
		else if ("Inf".equals(str) || "Infinity".equals(str))
			return Double.POSITIVE_INFINITY;
		// FlexibleFormat.FormatSolution<DecimalComponent> soln = DOUBLE_FORMAT.parse(text, true, true);
		// double d = 0;
		// FlexibleFormat.ParsedElement el = soln.getElements().get(DecimalComponent.FRACTION);
		// if (el != null)
		// d = el.getValue() * Math.pow(10, -el.getText().length());
		// el = soln.getElements().get(DecimalComponent.WHOLE);
		// if (el != null)
		// d += el.getValue();
		// el = soln.getElements().get(DecimalComponent.EXPONENT);
		// if (el != null)
		// d *= Math.pow(10, el.getValue());
		// return d;

		ParsePosition pos = new ParsePosition(0);
		Number n = format.parse(str, pos);
		if (pos.getErrorIndex() >= 0 || pos.getIndex() < text.length()) {
			if (ALT_DEC_PATTERN.matcher(str).matches())
				n = format.parse(str.replace(',', '.'), pos);
		}
		if (pos.getErrorIndex() >= 0 || pos.getIndex() < text.length())
			throw new ParseException("Invalid number: " + text, pos.getIndex());
		if (n instanceof Double)
			return (Double) n;
		else
			return n.doubleValue();
	}

	/**
	 * @param sigDigs The number of significant digits to print
	 * @return A builder for a {@link SuperDoubleFormat}
	 */
	public static SuperDoubleFormatBuilder doubleFormat(int sigDigs) {
		return new SuperDoubleFormatBuilder(sigDigs);
	}

	/**
	 * Adds validation to a format, creating a format that throws an exception for values parsed by the given format that fail a filter
	 * 
	 * @param <T> The type of values to format
	 * @param format The format to format and parse values
	 * @param validation The validation function to supply an error message for illegal values (and null for legal ones)
	 * @return The validated format
	 */
	public static <T> Format<T> validate(Format<T> format, Function<? super T, String> validation) {
		return new Format<T>() {
			@Override
			public void append(StringBuilder text, T value) {
				format.append(text, value);
			}

			@Override
			public T parse(CharSequence text) throws ParseException {
				T parsed = format.parse(text);
				String validated = validation.apply(parsed);
				if (validated != null)
					throw new ParseException(validated, 0);
				return parsed;
			}

			@Override
			public String toString() {
				return format.toString() + ".validate(" + validation + ")";
			}
		};
	}

	/**
	 * @param dateFormat The date format pattern
	 * @return A date format with the given pattern
	 * @see SimpleDateFormat#SimpleDateFormat(String)
	 */
	public static Format<Instant> date(String dateFormat) {
		return date(new SimpleDateFormat(dateFormat));
	}

	/**
	 * @param dateFormat The date format
	 * @return A {@link Format} backed by the given {@link SimpleDateFormat}
	 */
	public static Format<Instant> date(SimpleDateFormat dateFormat) {
		return new Format<Instant>() {
			@Override
			public void append(StringBuilder text, Instant value) {
				if (value == null)
					return;
				text.append(dateFormat.format(Date.from(value)));
			}

			@Override
			public Instant parse(CharSequence text) throws ParseException {
				if (text.length() == 0)
					return null;
				return dateFormat.parse(text.toString()).toInstant();
			}

			@Override
			public String toString() {
				return "DATE(" + dateFormat.toPattern() + ")";
			}
		};
	}

	/**
	 * @param dayFormat The format for the day/month/year
	 * @param timeZone The time zone for the format (may be null)
	 * @return A flexible date format
	 */
	public static FlexDateFormat flexibleDate(String dayFormat, TimeZone timeZone) {
		return new FlexDateFormat(dayFormat, timeZone);
	}

	/**
	 * Builds a fielded format, used to persist entity-like objects to a string
	 * 
	 * @param <E> The type of value to persist
	 * @param creator Creates blank values
	 * @param delimiter The delimiter to insert between fields
	 * @param delimiterDetector A pattern to find the delimiter in text
	 * @return The builder for the fielded format
	 */
	public static <E> FieldedFormatBuilder<E> fielded(Supplier<? extends E> creator, String delimiter, Pattern delimiterDetector) {
		return new FieldedFormatBuilder<>(creator, delimiter, delimiterDetector);
	}

	/**
	 * Returned from {@link Format#parseUnitValue(CharSequence, Format)}
	 * 
	 * @param <T> The type of the value
	 */
	public static class ParsedUnitValue<T> {
		/** The parsed value */
		public final T value;
		/** The parsed unit */
		public final String unit;
		/** The index in the text where the unit started */
		public final int unitStart;

		/**
		 * @param value The parsed value
		 * @param unit The parsed unit
		 * @param unitStart The position in the text where the unit started
		 */
		public ParsedUnitValue(T value, String unit, int unitStart) {
			this.value = value;
			this.unit = unit;
			this.unitStart = unitStart;
		}
	}

	/**
	 * @param <T> The type of the value to parse
	 * @param text The text to parse
	 * @param format The double format to use for the value
	 * @return The parsed unit value
	 * @throws ParseException If the value cannot be parsed
	 */
	public static <T> ParsedUnitValue<T> parseUnitValue(CharSequence text, Format<T> format) throws ParseException {
		if (text.length() == 0) {
			throw new ParseException("Empty text", 0);
		}
		String unit = "";
		for (int c = text.length() - 1; c >= 0 && !Character.isDigit(text.charAt(c)); c--) {
			unit = text.charAt(c) + unit;
		}
		if (unit.length() == 0) {
			throw new ParseException("Power must end with a unit", text.length());
		}
		int unitStart = text.length() - unit.length();
		try {
			return new ParsedUnitValue<>(format.parse(text.subSequence(0, unitStart).toString().trim()), unit, unitStart);
		} catch (NumberFormatException e) {
			throw new ParseException("Unrecognized number", 0);
		}
	}

	/**
	 * Formats an enumeration
	 * 
	 * @param <E> The enum type
	 */
	public static class EnumFormat<E extends Enum<?>> implements Format<E> {
		private final Class<E> theType;

		/** @param type The enum type */
		public EnumFormat(Class<E> type) {
			theType = type;
		}

		@Override
		public void append(StringBuilder text, E value) {
			if (value != null)
				text.append(value);
		}

		@Override
		public E parse(CharSequence text) throws ParseException {
			String str = text.toString().toLowerCase();
			for (E value : theType.getEnumConstants()) {
				if (value.toString().toLowerCase().equals(str))
					return value;
			}
			throw new ParseException("Unrecognized " + theType.getSimpleName() + " constant: " + text, 0);
		}

		@Override
		public String toString() {
			return theType.getSimpleName();
		}
	}

	/** A flexible date format */
	public static class FlexDateFormat implements Format<Instant> {
		private final String theDayFormat;
		private TimeEvaluationOptions theOptions;

		/**
		 * @param dayFormat The format for the day/month/year
		 * @param timeZone The time zone for the format (may be null)
		 */
		public FlexDateFormat(String dayFormat, TimeZone timeZone) {
			theDayFormat = dayFormat;
			theOptions = TimeUtils.DEFAULT_OPTIONS.withTimeZone(timeZone);
		}

		/**
		 * @param maxResolution The maximum resolution to print
		 * @return This format
		 */
		public FlexDateFormat setMaxResolution(TimeUtils.DateElementType maxResolution) {
			theOptions = theOptions.withMaxResolution(maxResolution);
			return this;
		}

		/** @return The options on this format */
		public TimeEvaluationOptions getOptions() {
			return theOptions;
		}

		/** @param militaryTime Whether to use military or AM/PM type time */
		public void setMilitaryTime(boolean militaryTime) {
			theOptions = theOptions.with24HourFormat(militaryTime);
		}

		@Override
		public void append(StringBuilder text, Instant value) {
			if (value == null)
				return;
			text.append(TimeUtils.asFlexTime(value, theDayFormat, __ -> theOptions).toString());
		}

		@Override
		public Instant parse(CharSequence text) throws ParseException {
			if (text.length() == 0)
				return null;
			return TimeUtils.parseFlexFormatTime(text, true, true, __ -> theOptions).evaluate(Instant::now);
		}

		@Override
		public String toString() {
			return "FLEXDATE";
		}
	}

	/** A builder for a {@link SuperDoubleFormat} */
	public static class SuperDoubleFormatBuilder {
		private final int theSignificantDigits;
		private int theMaxIntDigits;
		private boolean printIntsWithPrefixes;
		private int theMaxNormalExp;
		private int theMinNormalExp;

		private String theBaseUnit;
		private boolean isBaseUnitRequired;
		private boolean isBaseUnitCaseSensitive;
		private boolean arePrefixesCaseSensitive;
		private final TreeMap<Integer, String> thePrefixes;

		SuperDoubleFormatBuilder(int sigDigs) {
			theSignificantDigits = sigDigs;
			theMaxIntDigits = -1;
			theMaxNormalExp = sigDigs;
			theMinNormalExp = 1;
			theBaseUnit = "";
			isBaseUnitRequired = true;
			isBaseUnitCaseSensitive = true;
			arePrefixesCaseSensitive = true;
			thePrefixes = new TreeMap<>();
		}

		/**
		 * @param maxIntDigits The maximum number of digits for which an integer will be printed (for int-exact values)
		 * @param withPrefixes Whether to also print integers for this condition in front of prefixes
		 * @return This builder
		 */
		public SuperDoubleFormatBuilder printIntFor(int maxIntDigits, boolean withPrefixes) {
			theMaxIntDigits = maxIntDigits;
			this.printIntsWithPrefixes = withPrefixes;
			return this;
		}

		/**
		 * @param maxNormalExp The maximum power of 10 for which values are printed without scientific notation
		 * @param minNormalExp The minimum power of 10 for which values are printed without scientific notation
		 * @return This builder
		 */
		public SuperDoubleFormatBuilder withExpCondition(int maxNormalExp, int minNormalExp) {
			theMaxNormalExp = maxNormalExp;
			theMinNormalExp = Math.abs(minNormalExp); // Always treated as a positive, which is then negated by the format
			return this;
		}

		/**
		 * @param baseUnit The base unit for the values to parse
		 * @param required Whether the base unit must be specified in parsed text
		 * @return This builder
		 */
		public SuperDoubleFormatBuilder withUnit(String baseUnit, boolean required) {
			theBaseUnit = baseUnit;
			isBaseUnitRequired = required;
			return this;
		}

		/**
		 * @param prefix The metric-style prefix to modify the unit
		 * @param exponent The ten-power exponent represented by the prefix
		 * @return This builder
		 */
		public SuperDoubleFormatBuilder withPrefix(String prefix, int exponent) {
			thePrefixes.put(exponent, prefix);
			return this;
		}

		/**
		 * @param unitCaseSensitive Whether the unit must be specified case-sensitively
		 * @param prefixCaseSensitive Whether the prefixes must be specified case-sensitively
		 * @return This builder
		 */
		public SuperDoubleFormatBuilder caseSensitive(boolean unitCaseSensitive, boolean prefixCaseSensitive) {
			isBaseUnitCaseSensitive = unitCaseSensitive;
			arePrefixesCaseSensitive = prefixCaseSensitive;
			return this;
		}

		/** @return A new {@link SuperDoubleFormat} configured by this builder */
		public SuperDoubleFormat build() {
			TreeMap<Integer, String> prefixCopy = new TreeMap<>(thePrefixes);
			Map<String, Integer> reversePrefixes = new LinkedHashMap<>();
			for (Map.Entry<Integer, String> prefix : prefixCopy.entrySet()) {
				if (reversePrefixes.put(arePrefixesCaseSensitive ? prefix.getValue() : prefix.getValue().toLowerCase(),
					prefix.getKey()) != null) {
					if (!arePrefixesCaseSensitive)
						throw new IllegalStateException("Duplicate case-insensitive prefixes matching: " + prefix.getValue());
					else
						throw new IllegalStateException("Duplicate prefixes: " + prefix.getValue());
				}
			}
			int maxIntDigits = theMaxIntDigits;
			if (maxIntDigits < 0)
				maxIntDigits = theMaxNormalExp;
			return new SuperDoubleFormat(theSignificantDigits, maxIntDigits, printIntsWithPrefixes, theMaxNormalExp, theMinNormalExp,
				theBaseUnit, isBaseUnitRequired, isBaseUnitCaseSensitive, arePrefixesCaseSensitive, prefixCopy, reversePrefixes);
		}

		/** @return A new {@link Float}-typed format configured by this builder */
		public Format<Float> buildFloat() {
			class SuperFloatFormat implements Format<Float> {
				private final SuperDoubleFormat theDoubleFormat;

				SuperFloatFormat(SuperDoubleFormat doubleFormat) {
					theDoubleFormat = doubleFormat;
				}

				@Override
				public void append(StringBuilder text, Float value) {
					theDoubleFormat.append(text, value == null ? null : Double.valueOf(value.doubleValue()));
				}

				@Override
				public Float parse(CharSequence text) throws ParseException {
					Double parsed = theDoubleFormat.parse(text);
					return parsed == null ? null : Float.valueOf(parsed.floatValue());
				}

				@Override
				public String format(Float value) {
					return theDoubleFormat.format(value == null ? null : Double.valueOf(value.doubleValue()));
				}

				@Override
				public int hashCode() {
					return theDoubleFormat.hashCode();
				}

				@Override
				public boolean equals(Object obj) {
					return obj instanceof SuperFloatFormat && theDoubleFormat.equals(((SuperFloatFormat) obj).theDoubleFormat);
				}

				@Override
				public String toString() {
					return theDoubleFormat.toString();
				}
			}
			return new SuperFloatFormat(build());
		}
	}

	/**
	 * <p>
	 * A double format with lots of customization for how to print the value, as well as support for units with exponential prefixes (e.g.
	 * metric "kilo" and "milli").
	 * </p>
	 * 
	 * <p>
	 * To create an instance of this class, use {@link Format#doubleFormat(int)}
	 * </p>
	 */
	public static class SuperDoubleFormat implements Format<Double> {
		private final int theSignificantDigits;
		private final int theMaxIntDigits;
		private final boolean printIntsWithPrefixes;
		private final int theMaxNormalExp;
		private final int theMinNormalExp;

		private final String theBaseUnit;
		private final boolean isBaseUnitRequired;
		private final boolean isBaseUnitCaseSensitive;
		private final boolean arePrefixesCaseSensitive;
		private final NavigableMap<Integer, String> thePrefixes;
		private final Map<String, Integer> theReversePrefixes;
		private final ThreadLocal<NumberFormat> theDoubleFormat; // DecimalFormat instances are not thread-safe

		SuperDoubleFormat(int significantDigits, int maxIntDigits, boolean intWithPrefixes, int maxNormalExp, int minNormalExp,
			String baseUnit,
			boolean baseUnitRequired, boolean baseUnitCaseSensitive, boolean arePrefixesCaseSensitive,
			NavigableMap<Integer, String> prefixes, Map<String, Integer> reversePrefixes) {
			theSignificantDigits = significantDigits;
			theMaxIntDigits = maxIntDigits;
			printIntsWithPrefixes = intWithPrefixes;
			theMaxNormalExp = maxNormalExp;
			theMinNormalExp = minNormalExp;
			theBaseUnit = baseUnit;
			isBaseUnitRequired = baseUnitRequired;
			isBaseUnitCaseSensitive = baseUnitCaseSensitive;
			this.arePrefixesCaseSensitive = arePrefixesCaseSensitive;
			thePrefixes = prefixes;
			theReversePrefixes = reversePrefixes;
			theDoubleFormat = ThreadLocal.withInitial(() -> DecimalFormat.getInstance());
		}

		@Override
		public Double parse(CharSequence text) throws ParseException {
			if (text.length() == 1 && text.charAt(0) == '?')
				return Double.NaN;
			StringBuilder prefix = new StringBuilder();
			int baseIndex = theBaseUnit.length() - 1;
			int i;
			for (i = text.length() - 1; i >= 0; i--) {
				if (Character.isWhitespace(text.charAt(i))) {
					if (prefix.length() > 0)
						break;
				} else if (baseIndex >= 0) {
					if (theBaseUnit.charAt(baseIndex) == text.charAt(i))
						baseIndex--;
					else if (!isBaseUnitCaseSensitive//
						&& Character.toLowerCase(theBaseUnit.charAt(baseIndex)) == Character.toLowerCase(text.charAt(i)))
						baseIndex--;
					else {
						if (isBaseUnitRequired)
							throw new ParseException("Terminal '" + theBaseUnit + "' expected", i);
						else if (Character.isAlphabetic(text.charAt(i)))
							prefix.insert(0, text.charAt(i));
						else
							break;
					}
				} else if (Character.isAlphabetic(text.charAt(i)))
					prefix.insert(0, text.charAt(i));
				else
					break;
			}
			Integer exp;
			if (prefix.length() == 0)
				exp = 0;
			else if (arePrefixesCaseSensitive)
				exp = theReversePrefixes.get(prefix.toString());
			else
				exp = theReversePrefixes.get(prefix.toString().toLowerCase());
			if (exp == null)
				throw new ParseException("Unrecognized prefix '" + prefix + "'", i + 1);
			while (i >= 0 && !Character.isDigit(text.charAt(i)))
				i--;
			if (i < 0)
				throw new ParseException("No value given", 0);
			double num = parseDouble(text.subSequence(0, i + 1), theDoubleFormat.get());
			if (exp != 0)
				num *= Math.pow(10, exp);
			return num;
		}

		@Override
		public void append(StringBuilder text, Double value) {
			if (value == null)
				text.append("");
			else if (Double.isNaN(value))
				text.append("?");
			else if (value.doubleValue() == Double.POSITIVE_INFINITY)
				text.append("Infinity");
			else if (value.doubleValue() == Double.NEGATIVE_INFINITY)
				text.append("-Infinity");
			else {
				int exp;
				int sign = Double.compare(value, 0.0);
				if (sign == 0)
					exp = 0;
				else if (sign > 0)
					exp = (int) Math.log10(value);
				else
					exp = (int) Math.log10(-value);
				append(text, value, exp);
			}
		}

		/**
		 * @param text The text to append to
		 * @param value The value to append
		 * @param exp The ten-power exponent of the value
		 */
		protected void append(StringBuilder text, double value, int exp) {
			Map.Entry<Integer, String> prefix;
			boolean printInt;
			if (exp >= 0 && exp < theMaxIntDigits && value == (int) value) {
				prefix = null;
				printInt = true;
			} else {
				prefix = thePrefixes.floorEntry(exp);
				if (prefix == null) {
					prefix = thePrefixes.firstEntry();
					if (prefix != null && prefix.getKey().intValue() > 0)
						prefix = null;
				}
				if (prefix != null && exp == 0 && prefix.getKey().intValue() != 0)
					prefix = null;
				if (prefix != null && prefix.getKey().intValue() != 0) {
					value *= Math.pow(10, -prefix.getKey());
					exp -= prefix.getKey();
					printInt = printIntsWithPrefixes && exp >= 0 && exp <= theMaxIntDigits && value == (long) value;
				} else
					printInt = false;
			}

			boolean expNotation;
			if (printInt)
				expNotation = false;
			else if (exp < -theMinNormalExp)
				expNotation = true;
			else if (exp > theMaxNormalExp)
				expNotation = true;
			else
				expNotation = false;
			int digits;
			if (printInt)
				digits = 0;
			else if (expNotation) {
				value /= Math.pow(10, exp);
				digits = theSignificantDigits - 1;
			} else
				digits = theSignificantDigits - exp - 1;
			DecimalFormat format = getFormat(Math.max(0, digits));
			text.append(format.format(value));
			if (expNotation)
				text.append('E').append(exp);

			if (prefix != null)
				text.append(prefix.getValue());
			text.append(theBaseUnit);
		}

		private static final ThreadLocal<List<DecimalFormat>> DECIMAL_FORMATS = ThreadLocal.withInitial(ArrayList::new);

		static DecimalFormat getFormat(int decimalDigits) {
			List<DecimalFormat> formats = DECIMAL_FORMATS.get();
			if (decimalDigits >= formats.size()) {
				StringBuilder format = new StringBuilder("#,##0");
				if (formats.isEmpty()) {
					formats.add(new DecimalFormat(format.toString()));
				}
				format.append('.');
				for (int i = 1; i < formats.size(); i++)
					format.append('0');
				while (decimalDigits >= formats.size()) {
					format.append('0');
					formats.add(new DecimalFormat(format.toString()));
				}
			}
			return formats.get(decimalDigits);
		}
	}

	/**
	 * Builds a fielded format, used to persist entity-like objects to a string
	 * 
	 * @param <E> The type of value to persist
	 */
	public static class FieldedFormatBuilder<E> {
		private final Map<String, FormattedField<E, ?>> theFields;
		private final Supplier<? extends E> theValueCreator;
		private final String theDelimiter;
		private final Pattern theDelimiterDetector;
		private boolean isDelimitingEmptyFields;
		private boolean isNullToEmpty;

		FieldedFormatBuilder(Supplier<? extends E> valueCreator, String delimiter, Pattern delimiterDetector) {
			theValueCreator = valueCreator;
			theDelimiter = delimiter;
			theDelimiterDetector = delimiterDetector;
			theFields = new LinkedHashMap<>();
		}

		/**
		 * @param nullToEmpty Whether null entity values should be persisted as an empty string
		 * @return This builder
		 */
		public FieldedFormatBuilder<E> nullToEmpty(boolean nullToEmpty) {
			this.isNullToEmpty = nullToEmpty;
			return this;
		}

		/**
		 * @param delimit Whether a delimiter should be inserted after fields that are formatted as an empty string
		 * @return This builder
		 */
		public FieldedFormatBuilder<E> delimitEmptyFields(boolean delimit) {
			isDelimitingEmptyFields = delimit;
			return this;
		}

		/**
		 * Configures a field to persist
		 * 
		 * @param <F> The type of the field
		 * @param fieldName The name for the field
		 * @param fieldFormat The format to use for the field
		 * @param getter The getter for the field
		 * @param builder Configures the field
		 * @return This builder
		 */
		public <F> FieldedFormatBuilder<E> withField(String fieldName, Format<F> fieldFormat, Function<? super E, ? extends F> getter,
			Function<FormattedField.Builder<E, F>, FormattedField<E, F>> builder) {
			theFields.put(fieldName, //
				builder.apply(//
					new FormattedField.Builder<>(fieldName, fieldFormat, getter, theDelimiterDetector, theValueCreator != null)));
			return this;
		}

		/** @return the fielded format */
		public FieldedFormat<E> build() {
			return new FieldedFormat<>(QommonsUtils.unmodifiableCopy(theFields), theValueCreator, theDelimiter, theDelimiterDetector,
				isDelimitingEmptyFields, isNullToEmpty);
		}
	}

	/**
	 * A field in a {@link FieldedFormat fielded format}
	 * 
	 * @param <E> The type of the entity
	 * @param <F> The type of the field
	 */
	public static class FormattedField<E, F> implements Named {
		private final String theName;
		private final Format<F> theFormat;
		private final Function<CharSequence, Integer> theDetector;
		private final Function<? super E, ? extends F> theGetter;
		private final BiFunction<? super E, ? super F, ? extends E> theSetter;

		FormattedField(String name, Format<F> format, Function<CharSequence, Integer> detector, Function<? super E, ? extends F> getter,
			BiFunction<? super E, ? super F, ? extends E> setter) {
			theName = name;
			theFormat = format;
			theDetector = detector;
			theGetter = getter;
			theSetter = setter;
		}

		@Override
		public String getName() {
			return theName;
		}

		/** @return The format used for this field */
		public Format<F> getFormat() {
			return theFormat;
		}

		/** @return Detects the presence of the field in a sequence */
		public Function<CharSequence, Integer> getDetector() {
			return theDetector;
		}

		/** @return The getter for the field */
		public Function<? super E, ? extends F> getGetter() {
			return theGetter;
		}

		/** @return The setter for the field */
		public BiFunction<? super E, ? super F, ? extends E> getSetter() {
			return theSetter;
		}

		/**
		 * Configures a formatted field
		 * 
		 * @param <E> The type of the entity
		 * @param <F> The type of the field
		 */
		public static class Builder<E, F> {
			private final String theName;
			private final Format<F> theFormat;
			private final Function<? super E, ? extends F> theGetter;
			private final boolean canCreate;
			private Function<CharSequence, Integer> theDetector;

			Builder(String name, Format<F> format, Function<? super E, ? extends F> getter, Pattern delimiter, boolean canCreate) {
				theName = name;
				theFormat = format;
				theGetter = getter;
				theDetector = delimiter == null ? null : text -> {
					Matcher match = delimiter.matcher(text.toString());
					if (match.find())
						return match.start();
					return text.length();
				};
				this.canCreate = canCreate;
			}

			/**
			 * Overrides the default detector (a simple text search for the entity's delimiter)
			 * 
			 * @param detector The function to detect the presence of the field--returns -1 if the field is not present in the sequence
			 * @return This builder
			 */
			public Builder<E, F> withDetector(Function<CharSequence, Integer> detector) {
				theDetector = detector;
				return this;
			}

			/**
			 * @param setter The setter for the field
			 * @return The new field
			 */
			public FormattedField<E, F> build(BiConsumer<? super E, ? super F> setter) {
				return build2((e, f) -> {
					setter.accept(e, f);
					return e;
				});
			}

			/**
			 * @param setter The setter for the field
			 * @return The new field
			 */
			public FormattedField<E, F> build2(BiFunction<? super E, ? super F, ? extends E> setter) {
				if (setter == null && canCreate)
					throw new NullPointerException("setter cannot be null");
				if (theDetector == null)
					throw new IllegalStateException("If no delimiter is used, a detector must be provided for field " + theName);
				return new FormattedField<>(theName, theFormat, theDetector, theGetter, setter);
			}
		}
	}

	/**
	 * A format used to persist entity-like objects to a string
	 * 
	 * @param <E> The type of the entity to persist
	 */
	public static class FieldedFormat<E> implements Format<E> {
		private final Map<String, FormattedField<E, ?>> theFields;
		private final Supplier<? extends E> theValueCreator;
		private final String theDelimiter;
		private final Pattern theDelimiterDetector;
		private final boolean isDelimitingEmptyFields;
		private final boolean isNullToEmpty;

		FieldedFormat(Map<String, FormattedField<E, ?>> fields, Supplier<? extends E> valueCreator, String delimiter,
			Pattern delimiterDetector, boolean delimitingEmptyFields, boolean nullToEmpty) {
			theFields = fields;
			theValueCreator = valueCreator;
			theDelimiter = delimiter;
			theDelimiterDetector = delimiterDetector;
			isDelimitingEmptyFields = delimitingEmptyFields;
			isNullToEmpty = nullToEmpty;
		}

		@Override
		public void append(StringBuilder text, E value) {
			if (isNullToEmpty && value == null)
				return;
			boolean first = true;
			boolean lastEmpty = false;
			for (FormattedField<E, ?> field : theFields.values()) {
				if (first)
					first = false;
				else if (theDelimiter != null && (isDelimitingEmptyFields || !lastEmpty))
					text.append(theDelimiter);
				Object fieldValue = field.getGetter().apply(value);
				((Format<Object>) field.getFormat()).append(text, fieldValue);
				lastEmpty = fieldValue == null;
			}
		}

		@Override
		public E parse(CharSequence text) throws ParseException {
			if (isNullToEmpty && text.length() == 0)
				return null;
			if (theValueCreator == null)
				throw new IllegalStateException("This format has not been enabled to parse values");
			E value = theValueCreator.get();
			int position = 0;
			for (FormattedField<E, ?> field : theFields.values()) {
				Integer found = field.getDetector().apply(new DefaultCharSubSequence(text, position, text.length()));
				if (found != null && found < 0)
					found = null;
				if (found != null) {
					Object fieldValue = field.getFormat().parse(new DefaultCharSubSequence(text, position, position + found));
					value = ((FormattedField<E, Object>) field).getSetter().apply(value, fieldValue);
					if (found > 0) {
						position += found;
					}
				}
				if (position < text.length() && theDelimiterDetector != null) {
					Matcher match = theDelimiterDetector.matcher(new DefaultCharSubSequence(text, position, text.length()));
					if (match.lookingAt())
						position += match.end();
					else if (found != null && found.intValue() != 0)
						throw new ParseException("'" + theDelimiterDetector + "' expected", position);
				}
			}
			return value;
		}
	}

	/**
	 * Persists lists of objects to a single string
	 * 
	 * @param <T> The type of elements in the list
	 */
	public static class ListFormat<T> implements Format<List<T>> {
		private final Format<T> theFormat;
		private final String theDelimiter;
		private final String postDelimit;

		/**
		 * @param format The format for list elements
		 * @param delimiter The delimiter between elements
		 * @param postDelimit An optional sequence to insert after the delimiter (e.g. whitespace in a UI text field)
		 */
		public ListFormat(Format<T> format, String delimiter, String postDelimit) {
			theFormat = format;
			theDelimiter = delimiter;
			this.postDelimit = postDelimit;
		}

		/** @return The format for list elements */
		public Format<T> getFormat() {
			return theFormat;
		}

		/** @return The delimiter between elements */
		public String getDelimiter() {
			return theDelimiter;
		}

		/** @return The optional sequence inserted after the delimiter */
		public String getPostDelimit() {
			return postDelimit;
		}

		@Override
		public void append(StringBuilder text, List<T> value) {
			if (value == null)
				return;
			boolean first = true;
			for (T v : value) {
				if (first)
					first = false;
				else {
					text.append(theDelimiter);
					if (postDelimit != null)
						text.append(postDelimit);
				}
				theFormat.append(text, v);
			}
		}

		@Override
		public List<T> parse(CharSequence text) throws ParseException {
			int start = 0;
			int delimitIdx = 0;
			List<T> list = new ArrayList<>();
			for (int i = 0; i < text.length(); i++) {
				if (text.charAt(i) == theDelimiter.charAt(delimitIdx)) {
					delimitIdx++;
					if (delimitIdx == theDelimiter.length()) {
						delimitIdx = 0;
						T value = theFormat.parse(text.subSequence(start, i + 1 - theDelimiter.length()));
						list.add(value);
						while (i < text.length() - 1 && Character.isWhitespace(text.charAt(i + 1)))
							i++;
						start = i + 1;
					}
				}
			}
			if (start < text.length()) {
				T value = theFormat.parse(text.subSequence(start, text.length()));
				list.add(value);
			}
			return list;
		}
	}
}

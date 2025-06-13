package org.qommons.io;

import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.qommons.DefaultCharSubSequence;
import org.qommons.Named;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.TimeUtils;
import org.qommons.TimeUtils.TimeEvaluationOptions;
import org.qommons.collect.BetterCollection;

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

	/**
	 * Creates a format from this format via a simple mapping
	 * 
	 * @param <T2> The type of value the mapped format can parse and print
	 * @param map The map from this format type to the mapped type
	 * @param reverse The reverse mapping
	 * @return The mapped format
	 */
	default <T2> Format<T2> map(Function<? super T, ? extends T2> map, Function<? super T2, ? extends T> reverse) {
		return new MappedFormat<>(this, map, reverse);
	}

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

	/** Stupid-simple text format that just formats and parses text as-is */
	public static final Format<CharSequence> CHAR_SEQUENCE = new Format<CharSequence>() {
		@Override
		public void append(StringBuilder text, CharSequence value) {
			if (value != null)
				text.append(value);
		}

		@Override
		public CharSequence parse(CharSequence text) throws ParseException {
			return text;
		}

		@Override
		public String toString() {
			return "TEXT";
		}
	};

	/** Parses long integers from text */
	public static final LongFormat LONG = new LongFormat();

	/** Parses integers from text */
	public static final IntFormat INT = new IntFormat(LONG);

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

	/**
	 * The {@link Enum#valueOf(Class, String)} method is so type-restrictive that it's basically impossible to call if you don't know have a
	 * reference to the enum class without resorting to raw types. This method gets around that.
	 * 
	 * @param <E> Unused enum-type parameter needed to get around the generic requirements of the {@link Enum#valueOf(Class, String)} method
	 * @param type The enum type
	 * @param value The value name
	 * @return The value of the given enum with the given name
	 * @throws IllegalArgumentException If the given enum does not have a value with the given name
	 */
	public static <E extends Enum<E>> Enum<?> parseEnum(Class<? extends Enum<?>> type, String value) throws IllegalArgumentException {
		return Enum.valueOf((Class<E>) type, value);
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

	/** Parses regex {@link Pattern}s from text */
	public static final Format<Pattern> PATTERN = new Format<Pattern>() {
		@Override
		public void append(StringBuilder text, Pattern value) {
			if (value != null) {
				text.append(value.pattern());
			}
		}

		@Override
		public Pattern parse(CharSequence text) throws ParseException {
			if (text == null || text.length() == 0) {
				return null;
			}
			try {
				return Pattern.compile(text.toString());
			} catch (PatternSyntaxException e) {
				throw new ParseException(e.getMessage(), e.getIndex());
			}
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
	 * Creates a Qommons format from a java number format. It is preferred to use {@link #doubleFormat(String)} since number formats are not
	 * thread-safe, so if this instance is used concurrently it will fail.
	 * 
	 * @param format The number format
	 * @return A double-value format with the given pattern
	 * @see DecimalFormat#DecimalFormat(String)
	 */
	public static Format<Double> doubleFormat(NumberFormat format) {
		// DecimalFormat instances are not thread-safe
		return new Format<Double>() {
			@Override
			public void append(StringBuilder text, Double value) {
				if (value == null)
					return;
				text.append(format.format(value.doubleValue()));
			}

			@Override
			public Double parse(CharSequence text) throws ParseException {
				return parseDouble(text, format);
			}

			@Override
			public String toString() {
				return "DOUBLE(" + format + ")";
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

	/** An alternate pattern to use to parse doubles */
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
		else if (StringUtils.equalsIgnoreCase("-inf", str) || StringUtils.equalsIgnoreCase("-infinity", str) || "-\u221E".equals(str))
			return Double.NEGATIVE_INFINITY;
		else if (StringUtils.equalsIgnoreCase("inf", str) || StringUtils.equalsIgnoreCase("infinity", str) || "\u221E".equals(str))
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
			if (pos.getErrorIndex() >= 0 || pos.getIndex() < text.length()) {
				try {
					n = Double.parseDouble(str);
					pos.setErrorIndex(-1);
					pos.setIndex(text.length());
				} catch (NumberFormatException e) {
				}
			}
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
		return date(dateFormat, null);
	}

	/**
	 * @param dateFormat The date format pattern
	 * @param config Configuration for the SimpleDateFormat
	 * @return A date format with the given pattern
	 * @see SimpleDateFormat#SimpleDateFormat(String)
	 */
	public static Format<Instant> date(String dateFormat, Consumer<SimpleDateFormat> config) {
		return date(() -> {
			SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
			if (config != null)
				config.accept(sdf);
			return sdf;
		});
	}

	/**
	 * @param dateFormat The date format
	 * @return A {@link Format} backed by the given {@link SimpleDateFormat}
	 */
	public static Format<Instant> date(Supplier<SimpleDateFormat> dateFormat) {
		return new Format<Instant>() {
			private final ThreadLocal<SimpleDateFormat> theSDF = ThreadLocal.withInitial(dateFormat);

			@Override
			public void append(StringBuilder text, Instant value) {
				if (value == null)
					return;
				SimpleDateFormat sdf = theSDF.get();
				text.append(sdf.format(Date.from(value)));
			}

			@Override
			public Instant parse(CharSequence text) throws ParseException {
				if (text.length() == 0)
					return null;
				SimpleDateFormat sdf = theSDF.get();
				return sdf.parse(text.toString()).toInstant();
			}

			@Override
			public String toString() {
				SimpleDateFormat sdf = theSDF.get();
				return "DATE(" + sdf.toPattern() + ")";
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
	 * @param <T> The type of the value to print
	 * @param print The print function for the value
	 * @return A format that can print with the given function, but cannot parse anything
	 */
	public static <T> PrintOnlyFormat<T> printOnly(Function<? super T, String> print) {
		return new PrintOnlyFormat<>(print);
	}

	/** Default {@link Integer} format */
	public static class IntFormat implements Format<Integer> {
		private final LongFormat theLongFormat;

		/** @param longFormat The {@link Long} format to wrap */
		public IntFormat(LongFormat longFormat) {
			if (longFormat == null)
				throw new NullPointerException();
			theLongFormat = longFormat;
		}

		/** @return The wrapped {@link Long} format */
		public LongFormat getLongFormat() {
			return theLongFormat;
		}

		/**
		 * @param sep The grouping separator for this format
		 * @return This format
		 */
		public IntFormat withGroupingSeparator(char sep) {
			LongFormat f = theLongFormat.withGroupingSeparator(sep);
			return f == theLongFormat ? this : new IntFormat(f);
		}

		/**
		 * @param emptyAllowed Whether the user may enter the empty string, resulting in a null value
		 * @return A format obeying the given empty-allowed setting
		 */
		public IntFormat withEmptyAllowed(boolean emptyAllowed) {
			LongFormat f = theLongFormat.withEmptyAllowed(emptyAllowed);
			return f == theLongFormat ? this : new IntFormat(f);
		}

		@Override
		public void append(StringBuilder text, Integer value) {
			if (value != null)
				theLongFormat.append(text, value.longValue());
		}

		@Override
		public Integer parse(CharSequence text) throws ParseException {
			Long parsed = theLongFormat.parse(text);
			if (parsed.longValue() < Integer.MIN_VALUE || parsed.longValue() > Integer.MAX_VALUE)
				throw new ParseException("Integer values must be between " + Integer.MIN_VALUE + " and " + Integer.MAX_VALUE + ": " + text,
					0);
			return parsed.intValue();
		}

		@Override
		public String toString() {
			return "INT";
		}
	}

	/** Default {@link Long} format */
	public static class LongFormat implements Format<Long> {
		private static final long[] GROUPS = new long[] { //
			0, 1_000, 1_000_000, 1_000_000_000, 1_000_000_000_000L, 1_000_000_000_000_000L, 1_000_000_000_000_000_000L };

		private final boolean isEmptyAllowed;
		private final char theGroupingSeparator;

		/** Creates a new Long format */
		public LongFormat() {
			isEmptyAllowed = false;
			theGroupingSeparator = 0;
		}

		/**
		 * @param emptyAllowed Whether the user may enter the empty string, resulting in a null value
		 * @param groupingSeparator The grouping separator for this format
		 */
		public LongFormat(boolean emptyAllowed, char groupingSeparator) {
			isEmptyAllowed = emptyAllowed;
			theGroupingSeparator = groupingSeparator;
		}

		/** @return The grouping separator used by this format */
		public char getGroupingSeparator() {
			return theGroupingSeparator;
		}

		/**
		 * @param sep The grouping separator for this format
		 * @return This format
		 */
		public LongFormat withGroupingSeparator(char sep) {
			if (sep == theGroupingSeparator)
				return this;
			return new LongFormat(isEmptyAllowed, sep);
		}

		/**
		 * @param emptyAllowed Whether the user may enter the empty string, resulting in a null value
		 * @return A format obeying the given empty-allowed setting
		 */
		public LongFormat withEmptyAllowed(boolean emptyAllowed) {
			if (emptyAllowed == isEmptyAllowed)
				return this;
			return new LongFormat(emptyAllowed, theGroupingSeparator);
		}

		@Override
		public void append(StringBuilder text, Long value) {
			if (value == null)
				return;
			long val = value.longValue();
			if (theGroupingSeparator == 0)
				text.append(val);
			else {
				if (val < 0) {
					text.append('-');
					val = -val;
				}
				int group = Arrays.binarySearch(GROUPS, val);
				if (group < 0)
					group = -group - 2;
				boolean first = true;
				while (group >= 0) {
					long dig;
					if (group == 0)
						dig = val;
					else {
						dig = val / GROUPS[group];
						val %= GROUPS[group];
					}
					if (first) {
						first = false;
						text.append(dig);
					} else {
						text.append(',');
						StringUtils.printInt(dig, 3, text);
					}
					group--;
				}
			}
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
			long value = 0;
			while (i < text.length()) {
				char c = text.charAt(i);
				if (c >= '0' && c <= '9') {
					value = value * 10 + (c - '0');
					if (value < 0)
						throw new ParseException("Text is too large to be an integer", 0);
				} else if (c != theGroupingSeparator)
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

	/** A simple file path format */
	public static class FileFormat implements Format<File> {
		private final boolean allowNull;

		/** @param allowNull Whether to allow an empty string, which parses to null */
		public FileFormat(boolean allowNull) {
			this.allowNull = allowNull;
		}

		@Override
		public void append(StringBuilder text, File value) {
			if (value != null)
				text.append(value.getPath());
		}

		@Override
		public File parse(CharSequence text) throws ParseException {
			if (text == null || text.length() == 0) {
				if (allowNull)
					return null;
				else
					throw new ParseException("Empty content not allowed", 0);
			} else {
				try {
					return new File(text.toString());
				} catch (IllegalArgumentException e) {
					throw e;
				}
			}
		}
	}

	/** A flexible date format */
	public static class FlexDateFormat implements Format<Instant> {
		private final TimeUtils.DayFormat theDayFormat;
		private TimeEvaluationOptions theOptions;

		/**
		 * @param dayFormat The format for the day/month/year
		 * @param timeZone The time zone for the format (may be null)
		 */
		public FlexDateFormat(String dayFormat, TimeZone timeZone) {
			theDayFormat = TimeUtils.DayFormat.parse(dayFormat);
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
			text.append(TimeUtils.asFlexInstant(value, null, theDayFormat, __ -> theOptions).toString());
		}

		@Override
		public Instant parse(CharSequence text) throws ParseException {
			if (text.length() == 0)
				return null;
			return TimeUtils.parseInstant(text, true, true, __ -> theOptions).evaluate(Instant::now);
		}

		@Override
		public String toString() {
			return "FLEXDATE";
		}
	}

	/** All standard metric prefixes that are powers of 1000 mapped to their corresponding powers of 10 */
	public static final Map<String, Integer> METRIC_PREFIXES_3K = QommonsUtils.<String, Integer> buildMap(new LinkedHashMap<>())//
		.with("y", -24) // yocto
		.with("z", -21) // zepto
		.with("a", -18) // atto
		.with("f", -15) // femto
		.with("p", -12) // pico
		.with("n", -9) // nano
		.with("\u03bc", -6) // Greek mu, micro
		.with("u", -6) // micro
		.with("m", -3) // milli
		.with("k", 3) // kilo
		.with("M", 6) // mega
		.with("G", 9) // giga
		.with("T", 12) // tera
		.with("P", 15) // peta
		.with("E", 18) // exa
		.with("Z", 21) // zetta
		.with("Y", 24) // yotta
		.getUnmodifiable();

	/** All standard metric prefixes mapped to their corresponding powers of 10 */
	public static final Map<String, Integer> METRIC_PREFIXES = QommonsUtils.<String, Integer> buildMap(new LinkedHashMap<>())//
		.withAll(METRIC_PREFIXES_3K)//
		.with("c", -2) // centi
		.with("d", -1) // deci
		.with("da", 1) // deka
		.with("h", 2) // hecto
		.getUnmodifiable();
	/**
	 * All standard 10^3 metric prefixes mapped to their corresponding multipliers, except that instead of 1000 multipliers, 1024 is used
	 */
	public static final Map<String, Double> METRIC_PREFIXES_P2 = QommonsUtils.<String, Double> buildMap(new LinkedHashMap<>())//
		.with("y", Math.pow(1024, -8)) // yocto
		.with("z", Math.pow(1024, -7)) // zepto
		.with("a", Math.pow(1024, -6)) // atto
		.with("f", Math.pow(1024, -5)) // femto
		.with("p", Math.pow(1024, -4)) // pico
		.with("n", Math.pow(1024, -3)) // nano
		.with("\u03bc", Math.pow(1024, -2)) // Greek mu, micro
		.with("u", Math.pow(1024, -2)) // micro
		.with("m", Math.pow(1024, -1)) // milli
		.with("k", Math.pow(1024, 1)) // kilo
		.with("M", Math.pow(1024, 2)) // mega
		.with("G", Math.pow(1024, 3)) // giga
		.with("T", Math.pow(1024, 4)) // tera
		.with("P", Math.pow(1024, 5)) // peta
		.with("E", Math.pow(1024, 6)) // exa
		.with("Z", Math.pow(1024, 7)) // zetta
		.with("Y", Math.pow(1024, 8)) // yotta
		.getUnmodifiable();

	/** A builder for a {@link SuperDoubleFormat} */
	public static class SuperDoubleFormatBuilder {

		private int theMinSignificantDigits;
		private int theMaxSignificantDigits;
		private int theMaxIntDigits;
		private boolean printIntsWithPrefixes;
		private int theMaxNormalExp;
		private int theMinNormalExp;
		private int theZeroExp;

		private boolean isEmptyAllowed;
		private boolean isSpaceBetween;
		private String theBaseUnit;
		private boolean isBaseUnitRequired;
		private boolean isBaseUnitCaseSensitive;
		private boolean arePrefixesCaseSensitive;
		private final TreeMap<Double, String> thePrefixesByMultiplier;
		private final Map<String, Double> theMultipliersByPrefix;
		private double theDefaultPrefixMultiplier;

		SuperDoubleFormatBuilder(int sigDigs) {
			theMinSignificantDigits = sigDigs;
			theMaxSignificantDigits = sigDigs;
			theMaxIntDigits = -1;
			theMaxNormalExp = -1;
			theMinNormalExp = 1;
			isSpaceBetween = true;
			theBaseUnit = "";
			isBaseUnitRequired = true;
			isBaseUnitCaseSensitive = true;
			arePrefixesCaseSensitive = true;
			thePrefixesByMultiplier = new TreeMap<>();
			theMultipliersByPrefix = new LinkedHashMap<>();
			theDefaultPrefixMultiplier = 1;
			theZeroExp = -1;
		}

		/**
		 * @param min The minimum number of significant digits the format should print
		 * @param max The maximum number of significant digits the format should print
		 * @return This builder
		 */
		public SuperDoubleFormatBuilder withSigDigs(int min, int max) {
			if (min <= 0)
				throw new IllegalArgumentException("Minimum significant digits must be positive: " + min);
			else if (min > max)
				throw new IllegalArgumentException("Minimum significant digits must be <= maximum: " + min + ", " + max);
			theMinSignificantDigits = min;
			theMaxSignificantDigits = max;
			return this;
		}

		/**
		 * @param allowed Whether the user should be allowed to enter empty text, which will result in a null value
		 * @return This builder
		 */
		public SuperDoubleFormatBuilder emptyAllowed(boolean allowed) {
			isEmptyAllowed = allowed;
			return this;
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
		 * @param zeroExp The minimum negative exponential that a number may have to be treated as zero by this format. E.g. if this is 5, a
		 *        value of 1E-5 will be rendered as zero.
		 * @return This builder
		 */
		public SuperDoubleFormatBuilder withZeroExp(int zeroExp) {
			theZeroExp = Math.abs(zeroExp); // Always treated as a positive, which is then negated by the format
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
		 * @param space Whether to insert a space between the number and the unit
		 * @return This builder
		 */
		public SuperDoubleFormatBuilder withSpaceBeforeUnit(boolean space) {
			isSpaceBetween = space;
			return this;
		}

		/**
		 * @param prefix The metric-style prefix to modify the unit
		 * @param exponent The ten-power exponent represented by the prefix
		 * @return This builder
		 */
		public SuperDoubleFormatBuilder withPrefix(String prefix, int exponent) {
			return withPrefix(prefix, Math.pow(10.0, exponent));
		}

		/**
		 * @param prefix The metric-style prefix to modify the unit
		 * @param mult The multiple represented by the prefix
		 * @return This builder
		 */
		public SuperDoubleFormatBuilder withPrefix(String prefix, double mult) {
			thePrefixesByMultiplier.putIfAbsent(mult, prefix);
			theMultipliersByPrefix.put(prefix, mult);
			return this;
		}

		/**
		 * Adds prefixes for all standard metric prefixes
		 * 
		 * @return This builder
		 */
		public SuperDoubleFormatBuilder withMetricPrefixes() {
			for (Map.Entry<String, Integer> prefix : METRIC_PREFIXES.entrySet())
				withPrefix(prefix.getKey(), prefix.getValue());
			return this;
		}

		/**
		 * Adds prefixes for all standard 10^3 metric prefixes, except that the multipliers are 2^10 (1024) instead of 1000. This is useful
		 * e.g. for displaying data amounts (bytes)
		 * 
		 * @return This builder
		 */
		public SuperDoubleFormatBuilder withMetricPrefixesPower2() {
			for (Map.Entry<String, Double> prefix : METRIC_PREFIXES_P2.entrySet())
				withPrefix(prefix.getKey(), prefix.getValue());
			return this;
		}

		/**
		 * Adds prefixes for all standard metric prefixes that are powers of 1000 (e.g. not centi- or hecto-)
		 * 
		 * @return This builder
		 */
		public SuperDoubleFormatBuilder withMetricPrefixesPower3K() {
			for (Map.Entry<String, Integer> prefix : METRIC_PREFIXES_3K.entrySet())
				withPrefix(prefix.getKey(), prefix.getValue());
			return this;
		}

		/**
		 * @param defaultPrefixMult The multiplier to use in case the unit is unspecified. This allows for e.g. the user to specify that the
		 *        value they specify is in GHz, but if they don't specify a unit, the value would be interpreted as MHz.
		 * @return This builder
		 */
		public SuperDoubleFormatBuilder withDefaultPrefixMultiplier(double defaultPrefixMult) {
			theDefaultPrefixMultiplier = defaultPrefixMult;
			isBaseUnitRequired = false;
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
			TreeMap<Double, String> prefixCopy = new TreeMap<>(thePrefixesByMultiplier);
			Map<String, Double> reversePrefixes = new LinkedHashMap<>();
			for (Map.Entry<String, Double> prefix : theMultipliersByPrefix.entrySet()) {
				if (reversePrefixes.put(arePrefixesCaseSensitive ? prefix.getKey() : prefix.getKey().toLowerCase(),
					prefix.getValue()) != null) {
					if (!arePrefixesCaseSensitive)
						throw new IllegalStateException("Duplicate case-insensitive prefixes matching: " + prefix.getValue());
					else
						throw new IllegalStateException("Duplicate prefixes: " + prefix.getValue());
				}
			}
			int maxNormalExp = theMaxNormalExp;
			if (maxNormalExp < 0)
				maxNormalExp = theMaxSignificantDigits;
			int maxIntDigits = theMaxIntDigits;
			if (maxIntDigits < 0)
				maxIntDigits = maxNormalExp;
			return new SuperDoubleFormat(theMinSignificantDigits, theMaxSignificantDigits, maxIntDigits, printIntsWithPrefixes,
				maxNormalExp, theMinNormalExp, theZeroExp, isEmptyAllowed, isSpaceBetween, theBaseUnit, isBaseUnitRequired,
				theDefaultPrefixMultiplier, isBaseUnitCaseSensitive, arePrefixesCaseSensitive, prefixCopy, reversePrefixes);
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
		private final int theMinSignificantDigits;
		private final int theMaxSignificantDigits;
		private final int theMaxIntDigits;
		private final boolean printIntsWithPrefixes;
		private final int theMaxNormalExp;
		private final int theMinNormalExp;
		private final int theZeroExp;

		private final boolean isEmptyAllowed;
		private final boolean isSpaceBetween;
		private final String theBaseUnit;
		private final boolean isBaseUnitRequired;
		private final boolean isBaseUnitCaseSensitive;
		private final boolean arePrefixesCaseSensitive;
		private final double theDefaultPrefixMultiplier;
		private final NavigableMap<Double, String> thePrefixes;
		private final Map<String, Double> theReversePrefixes;
		private final ThreadLocal<NumberFormat> theDoubleFormat; // DecimalFormat instances are not thread-safe

		private final double theExpMult;

		SuperDoubleFormat(int minSignificantDigits, int maxSignificantDigits, int maxIntDigits, boolean intWithPrefixes, int maxNormalExp,
			int minNormalExp, int zeroExp, boolean emptyAllowed, boolean spaceBetween, String baseUnit, boolean baseUnitRequired,
			double defaultPrefixMultiplier, boolean baseUnitCaseSensitive, boolean arePrefixesCaseSensitive,
			NavigableMap<Double, String> prefixes, Map<String, Double> reversePrefixes) {
			theMinSignificantDigits = minSignificantDigits;
			theMaxSignificantDigits = maxSignificantDigits;
			theMaxIntDigits = maxIntDigits;
			printIntsWithPrefixes = intWithPrefixes;
			theMaxNormalExp = maxNormalExp;
			theMinNormalExp = minNormalExp;
			theZeroExp = zeroExp;
			isEmptyAllowed = emptyAllowed;
			isSpaceBetween = spaceBetween;
			theBaseUnit = baseUnit;
			isBaseUnitRequired = baseUnitRequired;
			theDefaultPrefixMultiplier = defaultPrefixMultiplier;
			isBaseUnitCaseSensitive = baseUnitCaseSensitive;
			this.arePrefixesCaseSensitive = arePrefixesCaseSensitive;
			thePrefixes = prefixes;
			theReversePrefixes = reversePrefixes;
			theDoubleFormat = ThreadLocal.withInitial(DecimalFormat::getInstance);
			theExpMult = 1 + Math.pow(10, -theMaxSignificantDigits);
		}

		/** @return The base unit of this format */
		public String getBaseUnit() {
			return theBaseUnit;
		}

		/** @return Whether the unit MUST be specified to be parseable */
		public boolean isBaseUnitRequired() {
			return isBaseUnitRequired;
		}

		@Override
		public Double parse(CharSequence text) throws ParseException {
			if (isEmptyAllowed && text.length() == 0)
				return null;
			if (text.equals("?") || StringUtils.compareNumberTolerant(text, "nan", true, false) == 0)
				return Double.NaN;
			StringBuilder prefix = new StringBuilder();
			int baseIndex = theBaseUnit == null ? -1 : theBaseUnit.length() - 1;
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
			Double exp;
			if (prefix.length() == 0)
				exp = theDefaultPrefixMultiplier;
			else if (arePrefixesCaseSensitive)
				exp = theReversePrefixes.get(prefix.toString());
			else
				exp = theReversePrefixes.get(prefix.toString().toLowerCase());
			if (exp == null) {
				if (baseIndex == 0)
					throw new ParseException("Unrecognized prefix '" + prefix + "'", i + 1);
				else
					throw new ParseException("Unrecognized unit '" + prefix + "'", i + 1);
			}
			while (i >= 0 && !Character.isDigit(text.charAt(i)))
				i--;
			if (i < 0)
				throw new ParseException("No value given", 0);
			double num = parseDouble(text.subSequence(0, i + 1), theDoubleFormat.get());
			if (exp != 1)
				num *= exp;
			return num;
		}

		@Override
		public void append(StringBuilder text, Double value) {
			if (value == null)
				text.append("");
			else if (Double.isNaN(value))
				text.append("?");
			else if (value.doubleValue() == Double.POSITIVE_INFINITY)
				text.append("\u221E");
			else if (value.doubleValue() == Double.NEGATIVE_INFINITY)
				text.append("-\u221E");
			else {
				double abs = Math.abs(value);
				Map.Entry<Double, String> prefix;
				if (abs == 0)
					prefix = null;
				else {
					prefix = thePrefixes.floorEntry(abs);
					if (prefix != null && prefix.getKey() < 1.0 && abs >= 1)
						prefix = null;
					if (prefix == null && abs < 1) {
						prefix = thePrefixes.firstEntry();
						if (prefix != null && prefix.getKey().doubleValue() > 0.0)
							prefix = null;
					}
				}
				int exp;
				boolean printInt;
				if (prefix != null && prefix.getKey().doubleValue() != 0.0)
					value /= prefix.getKey();
				int sign = Double.compare(value, 0.0);
				if (sign == 0)
					exp = 0;
				else if (sign > 0)
					exp = (int) Math.log10(value * theExpMult) - 1;
				else
					exp = (int) Math.log10(-value * theExpMult) - 1;

				if (theZeroExp > 0 && -exp >= theZeroExp) {
					value = 0.0;
					exp = 0;
				}

				if (!printIntsWithPrefixes && prefix != null)
					printInt = false;
				else {
					printInt = exp >= 0 && exp < theMaxIntDigits && value == value.longValue();
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
					digits = theMinSignificantDigits - 1;
				} else
					digits = theMinSignificantDigits - exp - 1;
				DecimalFormat format = getFormat(Math.max(0, digits), theMaxSignificantDigits - theMinSignificantDigits);
				text.append(format.format(value));
				if (expNotation)
					text.append('E').append(exp);

				if (theBaseUnit != null && !theBaseUnit.isEmpty()) {
					if (isSpaceBetween)
						text.append(' ');

					if (prefix != null)
						text.append(prefix.getValue());
					text.append(theBaseUnit);
				}
			}
		}

		/** Indexed by minimum decimal digits, then optional digits */
		private static final ThreadLocal<List<List<DecimalFormat>>> DECIMAL_FORMATS = ThreadLocal.withInitial(ArrayList::new);

		static DecimalFormat getFormat(int minDecimalDigits, int optionalDigits) {
			List<List<DecimalFormat>> formats = DECIMAL_FORMATS.get();
			while (minDecimalDigits >= formats.size())
				formats.add(new ArrayList<>());
			List<DecimalFormat> targetFormats = formats.get(minDecimalDigits);
			if (minDecimalDigits + optionalDigits >= targetFormats.size()) {
				StringBuilder format = new StringBuilder("#,##0");
				format.append('.');
				for (int i = 0; i < minDecimalDigits; i++)
					format.append('0');

				for (int i = 0; i <= optionalDigits; i++) {
					format.append('#');
					if (i == targetFormats.size())
						targetFormats.add(new DecimalFormat(format.toString()));
				}
			}
			return targetFormats.get(optionalDigits);
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
	 * Persists collections of objects to a single string and formats those strings into collections
	 * 
	 * @param <T> The type of elements in the collection
	 * @param <C> The type of the collection
	 */
	public static class CollectionFormat<T, C extends Collection<T>> implements Format<C> {
		private final Format<T> theElementFormat;
		private final String theDelimiter;
		private final String postDelimit;
		private final String theAddFailMessage;
		private final Supplier<C> theCollectionCreator;

		/**
		 * @param elementFormat The format for collection elements
		 * @param delimiter The delimiter between elements
		 * @param postDelimit An optional sequence to insert after the delimiter (e.g. whitespace in a UI text field)
		 * @param addFailMessage The error message to throw when a value cannot be added to a collection
		 * @param collectionCreator The supplier to create collections for putting parsed values into
		 */
		public CollectionFormat(Format<T> elementFormat, String delimiter, String postDelimit, String addFailMessage,
			Supplier<C> collectionCreator) {
			theElementFormat = elementFormat;
			theDelimiter = delimiter;
			this.postDelimit = postDelimit;
			theAddFailMessage = addFailMessage;
			theCollectionCreator = collectionCreator;
		}

		/** @return The format for list elements */
		public Format<T> getElementFormat() {
			return theElementFormat;
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
		public void append(StringBuilder text, C value) {
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
				theElementFormat.append(text, v);
			}
		}

		/** @return A new collection to parse values into */
		protected C createCollection() {
			return theCollectionCreator.get();
		}

		@Override
		public C parse(CharSequence text) throws ParseException {
			int start = 0;
			int delimitIdx = 0;
			C collection = createCollection();
			for (int i = 0; i < text.length(); i++) {
				if (text.charAt(i) == theDelimiter.charAt(delimitIdx)) {
					delimitIdx++;
					if (delimitIdx == theDelimiter.length()) {
						delimitIdx = 0;
						T value = theElementFormat.parse(text.subSequence(start, i + 1 - theDelimiter.length()));
						if (collection instanceof BetterCollection) {
							String msg = ((BetterCollection<T>) collection).canAdd(value);
							if (msg != null)
								throw new ParseException("Could not add element[" + collection.size() + "]: " + msg, start);
						}
						if (!collection.add(value) && theAddFailMessage != null)
							throw new ParseException("Could not add element [" + collection.size() + "]: " + theAddFailMessage, start);
						while (i < text.length() - 1 && Character.isWhitespace(text.charAt(i + 1)))
							i++;
						start = i + 1;
					}
				} else
					delimitIdx = 0;
			}
			if (start < text.length()) {
				T value = theElementFormat.parse(text.subSequence(start, text.length()));
				collection.add(value);
			}
			return collection;
		}
	}

	/**
	 * A format that can only print and cannot parse
	 * 
	 * @param <T> The value to format
	 */
	public static class PrintOnlyFormat<T> implements Format<T> {
		private final Function<? super T, String> thePrint;

		/** @param print The print function for this format to use */
		public PrintOnlyFormat(Function<? super T, String> print) {
			thePrint = print;
		}

		@Override
		public void append(StringBuilder text, T value) {
			text.append(thePrint.apply(value));
		}

		@Override
		public T parse(CharSequence text) throws ParseException {
			if (text.length() == 0)
				return null;
			throw new ParseException("Print-only formatting cannot parse", 0);
		}
	}

	/**
	 * Implements {@link Format#map(Function, Function)}
	 * 
	 * @param <T1> The type of the source format
	 * @param <T2> The type of this format
	 */
	public static class MappedFormat<T1, T2> implements Format<T2> {
		private final Format<T1> theSource;
		private final Function<? super T1, ? extends T2> theMap;
		private final Function<? super T2, ? extends T1> theReverse;

		/**
		 * @param source The source format to do the parsing/printing work
		 * @param map The map from the source format type to the mapped type
		 * @param reverse The reverse mapping
		 */
		public MappedFormat(Format<T1> source, Function<? super T1, ? extends T2> map, Function<? super T2, ? extends T1> reverse) {
			theSource = source;
			theMap = map;
			theReverse = reverse;
		}

		@Override
		public void append(StringBuilder text, T2 value) {
			theSource.append(text, theReverse.apply(value));
		}

		@Override
		public T2 parse(CharSequence text) throws ParseException {
			return theMap.apply(theSource.parse(text));
		}
	}
}

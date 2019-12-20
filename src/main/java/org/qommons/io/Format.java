package org.qommons.io;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.qommons.QommonsUtils;
import org.qommons.TimeUtils;

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
			if (text.length() > 10)
				throw new ParseException("Integer values must be between " + Integer.MIN_VALUE + " and " + Integer.MAX_VALUE, 0);
			if (parsed.longValue() < Integer.MIN_VALUE || parsed.longValue() > Integer.MAX_VALUE)
				throw new ParseException("Integer values must be between " + Integer.MIN_VALUE + " and " + Integer.MAX_VALUE, 0);
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

	public static <E extends Enum<?>> Format<E> enumFormat(Class<E> type) {
		return new EnumFormat<>(type);
	}

	/** Parses durations from text */
	public static final Format<Duration> DURATION = new Format<Duration>() {
		@Override
		public void append(StringBuilder text, Duration value) {
			if (value == null)
				return;
			if (value.isNegative()) {
				text.append('-');
				value = value.abs();
			}
			if (value.isZero())
				text.append("0s");
			else if (value.compareTo(Duration.ofSeconds(1)) < 0) {
				appendNanos(text, value.getNano());
			} else {
				boolean appended = false;
				long seconds = value.getSeconds();
				int dayLength = 24 * 60 * 60;
				double yearLength = 365.25 * dayLength;
				int years = (int) Math.floor(seconds / yearLength);
				if (years > 0) {
					text.append(years).append('y');
					seconds -= Math.floor(years * yearLength);
					appended = true;
				}
				long days = (int) (seconds / dayLength);
				if (days > 0) {
					if (appended)
						text.append(' ');
					text.append(days).append('d');
					seconds -= days * dayLength;
					appended = true;
				}
				int hours = (int) seconds / 60 / 60;
				if (hours > 0) {
					if (appended)
						text.append(' ');
					text.append(hours).append('h');
					seconds -= hours * 60 * 60;
					appended = true;
				}
				int minutes = (int) seconds / 60;
				if (minutes > 0) {
					if (appended)
						text.append(' ');
					text.append(minutes).append('m');
					seconds -= minutes * 60;
					appended = true;
				}
				int ns = value.getNano();
				if (seconds > 0) {
					if (appended)
						text.append(' ');
					text.append(seconds);
					if (ns > 1000000) {
						text.append('.');
						int divisor = 100000000;
						while (ns > 0 && divisor > 0) {
							text.append(ns / divisor);
							ns %= divisor;
							divisor /= 10;
						}
					} else if (ns > 0) {
						text.append(' ');
						appendNanos(text, ns);
					}
					text.append('s');
				} else if (ns > 0) {
					if (appended)
						text.append(' ');
					appendNanos(text, ns);
				}
			}
		}

		private void appendNanos(StringBuilder text, int ns) {
			if (ns < 1000000)
				text.append(ns).append("ns");
			else if (ns % 1000000 == 0)
				text.append(ns / 1000000).append("ms");
			else {
				text.append(ns / 1000000).append('.');
				buffer(text, ns % 1000000, 6);
				text.append("ms");
			}
		}

		private StringBuilder buffer(StringBuilder text, int value, int length) {
			int minForLength = 1;
			for (int i = 0; i < length; i++) {
				minForLength *= 10;
				if (value < minForLength)
					text.append('0');
			}
			text.append(value);
			return text;
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
		DecimalFormat format = new DecimalFormat(pattern);
		return new Format<Double>() {
			@Override
			public void append(StringBuilder text, Double value) {
				if (value == null)
					return;
				text.append(format.format(value.doubleValue()));
			}

			@Override
			public Double parse(CharSequence text) throws ParseException {
				String str = text.toString();
				if ("NaN".equals(str))
					return Double.NaN;
				else if ("-Inf".equals(str) || "-Infinity".equals(str))
					return Double.NEGATIVE_INFINITY;
				else if ("Inf".equals(str) || "Infinity".equals(str))
					return Double.POSITIVE_INFINITY;
				ParsePosition pos = new ParsePosition(0);
				Number n = format.parse(str, pos);
				if (pos.getErrorIndex() >= 0 || pos.getIndex() < text.length())
					throw new ParseException("Invalid number", pos.getIndex());
				if (n instanceof Double)
					return (Double) n;
				else
					return n.doubleValue();
			}

			@Override
			public String toString() {
				return "DOUBLE(" + pattern + ")";
			}
		};
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
	public static Format<Instant> flexibleDate(String dayFormat, TimeZone timeZone) {
		return new FlexDateFormat(dayFormat, timeZone);
	}

	public static class EnumFormat<E extends Enum<?>> implements Format<E> {
		private final Class<E> theType;

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
			throw new ParseException("Unrecognized " + theType.getSimpleName() + " constant", 0);
		}

		@Override
		public String toString() {
			return theType.getSimpleName();
		}
	}

	/** A flexible date format */
	public static class FlexDateFormat implements Format<Instant> {
		private final String theDayFormat;
		private final TimeZone theTimeZone;

		/**
		 * @param dayFormat The format for the day/month/year
		 * @param timeZone The time zone for the format (may be null)
		 */
		public FlexDateFormat(String dayFormat, TimeZone timeZone) {
			theDayFormat = dayFormat;
			theTimeZone = timeZone;
		}

		@Override
		public void append(StringBuilder text, Instant value) {
			if (value == null)
				return;
			text.append(TimeUtils.asFlexTime(value, theTimeZone, theDayFormat).toString());
		}

		@Override
		public Instant parse(CharSequence text) throws ParseException {
			if (text.length() == 0)
				return null;
			return TimeUtils.parseFlexFormatTime(text, true, true).evaluate(Instant::now);
		}

		@Override
		public String toString() {
			return "FLEXDATE";
		}
	}
}

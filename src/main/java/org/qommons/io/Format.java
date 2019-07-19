package org.qommons.io;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.qommons.QommonsUtils;

public interface Format<T> {
	void append(StringBuilder text, T value);

	default String format(T value) {
		StringBuilder s = new StringBuilder();
		append(s, value);
		return s.toString();
	}

	T parse(CharSequence text) throws ParseException;

	public static final Format<String> TEXT = new Format<String>() {
		@Override
		public void append(StringBuilder text, String value) {
			text.append(value);
		}

		@Override
		public String parse(CharSequence text) throws ParseException {
			return text.toString();
		}
	};

	public static final Format<Integer> INT = new Format<Integer>() {
		@Override
		public void append(StringBuilder text, Integer value) {
			text.append(value);
		}

		@Override
		public Integer parse(CharSequence text) throws ParseException {
			Long parsed = LONG.parse(text);
			if (parsed.longValue() < Integer.MIN_VALUE || parsed.longValue() > Integer.MAX_VALUE)
				throw new ParseException("Integer values must be between " + Integer.MIN_VALUE + " and " + Integer.MAX_VALUE, 0);
			return parsed.intValue();
		}
	};

	public static final Format<Long> LONG = new Format<Long>() {
		private static final String MAX_TEXT = "" + Long.MAX_VALUE;
		private static final String MIN_TEXT = "" + Long.MIN_VALUE;

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
	};

	public static final Format<Duration> DURATION = new Format<Duration>() {
		@Override
		public void append(StringBuilder text, Duration value) {
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
	};

	public static Format<String> validatedText(Pattern pattern, String errorText) {
		return validatedText(pattern, m -> m.matches() ? null : errorText);
	}

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
		};
	}

	public static Format<Float> floatFormat(String pattern) {
		Format<Double> doubleFormat = doubleFormat(pattern);
		return new Format<Float>() {
			@Override
			public void append(StringBuilder text, Float value) {
				text.append(value);
			}

			@Override
			public Float parse(CharSequence text) throws ParseException {
				Double d = doubleFormat.parse(text);
				if (d.doubleValue() < Float.MIN_VALUE || d.doubleValue() > Float.MAX_VALUE)
					throw new ParseException("Float values must be between " + Float.MIN_VALUE + " and " + Float.MAX_VALUE, 0);
				return d.floatValue();
			}
		};
	}

	public static Format<Double> doubleFormat(String pattern) {
		DecimalFormat format = new DecimalFormat(pattern);
		return new Format<Double>() {
			@Override
			public void append(StringBuilder text, Double value) {
				text.append(format.format(value.doubleValue()));
			}

			@Override
			public Double parse(CharSequence text) throws ParseException {
				ParsePosition pos = new ParsePosition(0);
				Number n = format.parse(text.toString(), pos);
				if (pos.getErrorIndex() >= 0 || pos.getIndex() < text.length())
					throw new ParseException("Invalid number", pos.getIndex());
				if (n instanceof Double)
					return (Double) n;
				else
					return n.doubleValue();
			}
		};
	}

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
		};
	}

	public static Format<Instant> date(SimpleDateFormat dateFormat) {
		return new Format<Instant>() {
			@Override
			public void append(StringBuilder text, Instant value) {
				text.append(dateFormat.format(Date.from(value)));
			}

			@Override
			public Instant parse(CharSequence text) throws ParseException {
				return dateFormat.parse(text.toString()).toInstant();
			}
		};
	}
}

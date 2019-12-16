package org.qommons.io;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.BiTuple;
import org.qommons.StringUtils;
import org.qommons.TimeUtils;

/**
 * A format that supports incremental adjustment
 * 
 * @param <T> The type of value supported
 */
public interface SpinnerFormat<T> extends Format<T> {
	/**
	 * @param withContext Whether to query support for adjustment with or without cursor context
	 * @return Whether this format supports adjustment with or without a given cursor position, as per the parameter
	 */
	boolean supportsAdjustment(boolean withContext);

	/**
	 * @param value The current value of a field
	 * @param formatted The currently-formatted text
	 * @param cursor The location of the cursor, or -1 for a context-less adjustment
	 * @param up Whether to adjust up or down
	 * @return A tuple containing new value reflecting the adjustment and the formatted new value, or null if the adjustment is not valid
	 *         for any reason
	 */
	BiTuple<T, String> adjust(T value, String formatted, int cursor, boolean up);

	/** A text format that has the capability to increment or decrement integers at the cursor position in the text */
	public static final SpinnerFormat<String> NUMERICAL_TEXT = new AbstractSpinnerFormat<String>(Format.TEXT) {
		@Override
		public boolean supportsAdjustment(boolean withContext) {
			return withContext;
		}

		@Override
		public BiTuple<String, String> adjust(String value, String formatted, int cursor, boolean up) {
			if (value.isEmpty())
				return null;
			if (cursor > 0 && (cursor == value.length() || !Character.isDigit(value.charAt(cursor))))
				cursor--;
			if (!Character.isDigit(value.charAt(cursor)))
				return null;
			while (cursor < value.length() - 1 && Character.isDigit(value.charAt(cursor + 1)))
				cursor++;
			String str = StringUtils.add(value, cursor, up ? 1 : -1);
			return new BiTuple<>(str, str);
		}
	};
	/** Integer format with increment/decrement capability */
	public static final SpinnerFormat<Integer> INT = new AbstractSpinnerFormat<Integer>(Format.INT) {
		@Override
		public boolean supportsAdjustment(boolean withContext) {
			return !withContext;
		}

		@Override
		public BiTuple<Integer, String> adjust(Integer value, String formatted, int cursor, boolean up) {
			int newValue = up ? value + 1 : value - 1;
			if ((newValue > value) != up)
				return null; // Wraparound
			return new BiTuple<>(newValue, format(newValue));
		}
	};
	/** Long format with increment/decrement capability */
	public static final SpinnerFormat<Long> LONG = new AbstractSpinnerFormat<Long>(Format.LONG) {
		@Override
		public boolean supportsAdjustment(boolean withContext) {
			return !withContext;
		}

		@Override
		public BiTuple<Long, String> adjust(Long value, String formatted, int cursor, boolean up) {
			long newValue = up ? value + 1 : value - 1;
			if ((newValue > value) != up)
				return null; // Wraparound
			return new BiTuple<>(newValue, format(newValue));
		}
	};

	/**
	 * A {@link SpinnerFormat} that uses a plain {@link Format} for formatting and parsing
	 * 
	 * @param <T> The type of value supported
	 */
	public static abstract class AbstractSpinnerFormat<T> implements SpinnerFormat<T> {
		private final Format<T> theFormat;

		/** @param format The format to do the parsing and formatting */
		public AbstractSpinnerFormat(Format<T> format) {
			theFormat = format;
		}

		@Override
		public void append(StringBuilder text, T value) {
			theFormat.append(text, value);
		}

		@Override
		public T parse(CharSequence text) throws ParseException {
			return theFormat.parse(text);
		}
	}

	public interface SimpleParser<T> {
		T parse(CharSequence text) throws ParseException;
	}

	public static <T extends ParsedAdjustable<T, ?>> SpinnerFormat<T> forAdjustable(SimpleParser<T> parse) {
		return new AdjustableFormat<>(parse);
	}

	public static <T, A extends ParsedAdjustable<A, ?>> SpinnerFormat<T> wrapAdjustable(SpinnerFormat<A> adjustableFormat,
		Function<? super A, ? extends T> map, Function<? super T, ? extends A> reverse) {
		return new AdjustableFormatWrapper<>(adjustableFormat, map, reverse);
	}

	/**
	 * Creates a date format that can increment or decrement the date at integers within the format
	 * 
	 * @param reference The reference time for relative date formats (if null, <code>Instant::now</code> will be used)
	 * @param dayFormat The format for the day/month/year
	 * @param timeZone The time zone for the format (may be null)
	 * @return A date format with the given pattern
	 * @see SimpleDateFormat#SimpleDateFormat(String)
	 */
	public static SpinnerFormat<Instant> flexDate(Supplier<Instant> reference, String dayFormat, TimeZone timeZone) {
		return SpinnerFormat.<Instant, TimeUtils.ParsedTime> wrapAdjustable(
			forAdjustable(text -> TimeUtils.parseFlexFormatTime(text, true, true)), //
			time -> time.evaluate(Instant::now), instant -> TimeUtils.asFlexTime(instant, TimeUtils.GMT, "ddMMMyyyy"));
	}

	public static SpinnerFormat<Duration> flexDuration() {
		return SpinnerFormat.<Duration, TimeUtils.ParsedDuration> wrapAdjustable(forAdjustable(TimeUtils::parseDuration),
			TimeUtils.ParsedDuration::asDuration, TimeUtils.ParsedDuration::asParsedDuration);
	}

	/**
	 * Adds validation to a spinner format, creating a format that throws an exception for values parsed by the given format that fail a
	 * filter
	 * 
	 * @param <T> The type of values to format
	 * @param format The format to format and parse values
	 * @param validation The validation function to supply an error message for illegal values (and null for legal ones)
	 * @return The validated format
	 */
	public static <T> SpinnerFormat<T> validate(SpinnerFormat<T> format, Function<? super T, String> validation) {
		return new AbstractSpinnerFormat<T>(Format.validate(format, validation)) {
			@Override
			public boolean supportsAdjustment(boolean withContext) {
				return format.supportsAdjustment(withContext);
			}

			@Override
			public BiTuple<T, String> adjust(T value, String formatted, int cursor, boolean up) {
				BiTuple<T, String> adjusted = format.adjust(value, formatted, cursor, up);
				if (adjusted != null && validation.apply(adjusted.getValue1()) == null)
					return adjusted;
				return null;
			}
		};
	}

	public static class AdjustableFormat<T extends ParsedAdjustable<T, ?>> implements SpinnerFormat<T> {
		private final SimpleParser<T> theParser;

		public AdjustableFormat(SimpleParser<T> parser) {
			theParser = parser;
		}

		@Override
		public void append(StringBuilder text, T value) {
			if (value != null)
				text.append(value);
		}

		@Override
		public T parse(CharSequence text) throws ParseException {
			if (text.length() == 0)
				return null;
			return theParser.parse(text);
		}

		@Override
		public boolean supportsAdjustment(boolean withContext) {
			return true;
		}

		@Override
		public BiTuple<T, String> adjust(T value, String formatted, int cursor, boolean up) {
			if (value == null)
				return null;
			T adjusted = value.adjust(cursor, up ? 1 : -1);
			if (adjusted == null)
				return null;
			return new BiTuple<>(adjusted, adjusted.toString());
		}
	}

	public static class AdjustableFormatWrapper<T, A extends ParsedAdjustable<A, ?>> implements SpinnerFormat<T> {
		private final SpinnerFormat<A> theFormat;
		private final Function<? super A, ? extends T> theMap;
		private final Function<? super T, ? extends A> theReverse;

		private A theLastAdjustable;
		private T theLastValue;

		public AdjustableFormatWrapper(SpinnerFormat<A> format, Function<? super A, ? extends T> map,
			Function<? super T, ? extends A> reverse) {
			theFormat = format;
			theMap = map;
			theReverse = reverse;
		}

		protected A adjustable(T value) {
			if (value == theLastValue)
				return theLastAdjustable;
			A adjustable = theReverse.apply(value);
			theLastAdjustable = adjustable;
			theLastValue = value;
			return adjustable;
		}

		@Override
		public void append(StringBuilder text, T value) {
			if (value != null)
				text.append(adjustable(value));
		}

		@Override
		public T parse(CharSequence text) throws ParseException {
			if (text.length() == 0)
				return null;
			if (theLastAdjustable != null && text.toString().equals(theLastAdjustable.toString()))
				return theLastValue;
			A adjustable = theFormat.parse(text);
			T value = theMap.apply(adjustable);
			theLastAdjustable = adjustable;
			theLastValue = value;
			return value;
		}

		@Override
		public boolean supportsAdjustment(boolean withContext) {
			return theFormat.supportsAdjustment(withContext);
		}

		@Override
		public BiTuple<T, String> adjust(T value, String formatted, int cursor, boolean up) {
			if (value == null)
				return null;
			A adjustable = adjustable(value);
			BiTuple<A, String> adjusted = theFormat.adjust(adjustable, formatted, cursor, up);
			if (adjusted == null)
				return null;
			T newValue = theMap.apply(adjusted.getValue1());
			theLastAdjustable = adjusted.getValue1();
			theLastValue = newValue;
			return new BiTuple<>(newValue, adjusted.getValue2());
		}
	}
}

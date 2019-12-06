package org.qommons.io;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.BiTuple;
import org.qommons.StringUtils;
import org.qommons.TimeUtils;
import org.qommons.TimeUtils.ParsedTime;

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
			return new BiTuple<>(value, format(value));
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

	/** A flexible date format for {@link org.qommons.TimeUtils.ParsedTime}s */
	public static SpinnerFormat<TimeUtils.ParsedTime> FLEX_DATE2 = new SpinnerFormat<TimeUtils.ParsedTime>() {
		@Override
		public void append(StringBuilder text, ParsedTime value) {
			text.append(value);
		}

		@Override
		public ParsedTime parse(CharSequence text) throws ParseException {
			return TimeUtils.parseFlexFormatTime(text, true, true);
		}

		@Override
		public boolean supportsAdjustment(boolean withContext) {
			return withContext;
		}

		@Override
		public BiTuple<ParsedTime, String> adjust(ParsedTime value, String formatted, int cursor, boolean up) {
			TimeUtils.DateElementType element = value.getField(cursor);
			if (cursor > 0) {
				// If the field after the cursor is not adjustable, adjust the field adjacent to the left
				if (element == null)
					element = value.getField(cursor - 1);
				else {
					switch (element) {
					case AmPm:
					case TimeZone:
						element = value.getField(cursor - 1);
						break;
					default:
						break;
					}
				}
			}
			if (element == null)
				return null;
			switch (element) {
			case AmPm:
			case TimeZone:
				return null;
			default:
				TimeUtils.ParsedTime newTime = value.add(element, up ? 1 : -1);
				return new BiTuple<>(newTime, newTime.toString());
			}
		}
	};
	/** A flexible date format */
	public static SpinnerFormat<Instant> FLEX_DATE = flexDate(null, "ddMMMyyyy", null);

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
		return new FlexDateWrapper(FLEX_DATE2, reference == null ? Instant::now : reference, dayFormat, timeZone);
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

	/** An {@link Instant} formatter that uses a {@link org.qommons.TimeUtils.ParsedTime} formatter */
	public static class FlexDateWrapper implements SpinnerFormat<Instant> {
		private final SpinnerFormat<TimeUtils.ParsedTime> theFlexTimeFormat;
		private final Supplier<Instant> theReference;
		private final String theDayFormat;
		private final TimeZone theTimeZone;

		/**
		 * @param flexTimeFormat The parsed time formatter to do this formatter's work
		 * @param reference The reference time for relative-formatted times
		 * @param dayFormat The date format to print the day/month/year
		 * @param timeZone The time zone for the format--may be null
		 */
		public FlexDateWrapper(SpinnerFormat<TimeUtils.ParsedTime> flexTimeFormat, Supplier<Instant> reference, String dayFormat,
			TimeZone timeZone) {
			theFlexTimeFormat = flexTimeFormat;
			theReference = reference;
			theDayFormat = dayFormat;
			theTimeZone = timeZone;
		}

		@Override
		public void append(StringBuilder text, Instant value) {
			if (value == null)
				return;
			theFlexTimeFormat.append(text, TimeUtils.asFlexTime(value, theTimeZone, theDayFormat));
		}

		@Override
		public Instant parse(CharSequence text) throws ParseException {
			if (text.length() == 0)
				return null;
			return TimeUtils.parseFlexFormatTime(text, true, true).evaluate(theReference);
		}

		@Override
		public boolean supportsAdjustment(boolean withContext) {
			return theFlexTimeFormat.supportsAdjustment(withContext);
		}

		@Override
		public BiTuple<Instant, String> adjust(Instant value, String formatted, int cursor, boolean up) {
			TimeUtils.ParsedTime parsed;
			try {
				parsed = TimeUtils.parseFlexFormatTime(formatted, true, false);
			} catch (ParseException e) {
				return null; // Shouldn't happen, since the last argument is false, but whatever
			}
			if (parsed == null)
				return null; // Also shouldn't happen, since formatted should have been produced by this format
			BiTuple<TimeUtils.ParsedTime, String> adjusted = theFlexTimeFormat.adjust(parsed, formatted, cursor, up);
			if (adjusted == null)
				return null;
			return new BiTuple<>(adjusted.getValue1().evaluate(theReference), adjusted.getValue2());
		}
	}
}

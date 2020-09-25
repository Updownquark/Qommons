package org.qommons.io;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
	 * @param format The format String to use to format and parse the double value
	 * @param increment The amount to increment/decrement the value by for each action
	 * @return A spinner format for double values
	 */
	public static SpinnerFormat<Double> doubleFormat(String format, double increment) {
		return new AbstractSpinnerFormat<Double>(Format.doubleFormat(format)) {
			@Override
			public boolean supportsAdjustment(boolean withContext) {
				return !withContext;
			}

			@Override
			public BiTuple<Double, String> adjust(Double value, String formatted, int cursor, boolean up) {
				double d = up ? value + increment : value - increment;
				return new BiTuple<>(d, format(d));
			}
		};
	}

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
	 * A simple parser to use with {@link SpinnerFormat#forAdjustable(SimpleParser)}
	 * 
	 * @param <T> The type of value to parse
	 */
	public interface SimpleParser<T> {
		/**
		 * @param text The text representing the value to parse
		 * @return The parsed value
		 * @throws ParseException If the value could not be parsed
		 */
		T parse(CharSequence text) throws ParseException;
	}

	/**
	 * @param <T> The adjustable type to format
	 * @param parse The simple parser for the type
	 * @return A spinner format for the type
	 */
	public static <T extends ParsedAdjustable<T, ?>> SpinnerFormat<T> forAdjustable(SimpleParser<T> parse) {
		return new AdjustableFormat<>(parse);
	}

	/**
	 * @param <A> The type of the adjustable format
	 * @param <T> The type for the mapped format
	 * @param adjustableFormat The adjustable format
	 * @param map The function to produce target values from values provided by the adjustable format
	 * @param reverse The function to produce values for the adjustable format from target values
	 * @return A spinner format for the target type
	 */
	public static <A extends ParsedAdjustable<A, ?>, T> SpinnerFormat<T> wrapAdjustable(SpinnerFormat<A> adjustableFormat,
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
		return flexDate(reference, dayFormat, timeZone, TimeUtils.DateElementType.Second, true);
	}

	/**
	 * Creates a date format that can increment or decrement the date at integers within the format
	 * 
	 * @param reference The reference time for relative date formats (if null, <code>Instant::now</code> will be used)
	 * @param dayFormat The format for the day/month/year
	 * @param timeZone The time zone for the format (may be null)
	 * @param maxResolution The finest time component to print
	 * @param militaryTime Whether to use military or AM/PM type time
	 * @return A date format with the given pattern
	 * @see SimpleDateFormat#SimpleDateFormat(String)
	 */
	public static SpinnerFormat<Instant> flexDate(Supplier<Instant> reference, String dayFormat, TimeZone timeZone,
		TimeUtils.DateElementType maxResolution, boolean militaryTime) {
		return SpinnerFormat.<TimeUtils.ParsedTime, Instant> wrapAdjustable(
			forAdjustable(text -> TimeUtils.parseFlexFormatTime(text, true, true)), //
			time -> time.evaluate(Instant::now),
			instant -> TimeUtils.asFlexTime(instant, timeZone, dayFormat, maxResolution, militaryTime));
	}

	/**
	 * @param weeks Whether to print weeks or just days
	 * @return A spinner format that parses durations (time lengths) using a flexible format
	 */
	public static SpinnerFormat<Duration> flexDuration(boolean weeks) {
		return SpinnerFormat.<TimeUtils.ParsedDuration, Duration> wrapAdjustable(forAdjustable(TimeUtils::parseDuration),
			TimeUtils.ParsedDuration::asDuration, d -> TimeUtils.ParsedDuration.asParsedDuration(d, weeks));
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

	public static <T> SpinnerFormat<T> wrapAround(SpinnerFormat<T> format, Supplier<T> min, Supplier<T> max) {
		return new WrappingSpinnerFormat<>(format, min, max);
	}

	/**
	 * A spinner format for parsing {@link ParsedAdjustable} values
	 * 
	 * @param <T> The {@link ParsedAdjustable} sub-type to parse
	 */
	public static class AdjustableFormat<T extends ParsedAdjustable<T, ?>> implements SpinnerFormat<T> {
		private final SimpleParser<T> theParser;

		/** @param parser The simple parser to parse values from text */
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

	/**
	 * Implements {@link SpinnerFormat#wrapAdjustable(SpinnerFormat, Function, Function)}
	 * 
	 * @param <A> The type of the {@link ParsedAdjustable} that the adjustable format is for
	 * @param <T> Type for this format
	 */
	public static class AdjustableFormatWrapper<A extends ParsedAdjustable<A, ?>, T> implements SpinnerFormat<T> {
		private final SpinnerFormat<A> theFormat;
		private final Function<? super A, ? extends T> theMap;
		private final Function<? super T, ? extends A> theReverse;

		private A theLastAdjustable;
		private T theLastValue;

		/**
		 * @param format The spinner format for the source type
		 * @param map The source-to-target mapping
		 * @param reverse The target-to-source reverse mapping
		 */
		public AdjustableFormatWrapper(SpinnerFormat<A> format, Function<? super A, ? extends T> map,
			Function<? super T, ? extends A> reverse) {
			theFormat = format;
			theMap = map;
			theReverse = reverse;
		}

		/**
		 * @param value The target value
		 * @return The reverse-mapped source value
		 */
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

	public static class WrappingSpinnerFormat<T> implements SpinnerFormat<T> {
		private final SpinnerFormat<T> theWrapped;
		private final Supplier<T> theMinimum;
		private final Supplier<T> theMaximum;

		public WrappingSpinnerFormat(SpinnerFormat<T> wrapped, Supplier<T> minimum, Supplier<T> maximum) {
			theWrapped = wrapped;
			theMinimum = minimum;
			theMaximum = maximum;
		}

		@Override
		public void append(StringBuilder text, T value) {
			theWrapped.append(text, value);
		}

		@Override
		public T parse(CharSequence text) throws ParseException {
			return theWrapped.parse(text);
		}

		@Override
		public boolean supportsAdjustment(boolean withContext) {
			return theWrapped.supportsAdjustment(withContext);
		}

		@Override
		public BiTuple<T, String> adjust(T value, String formatted, int cursor, boolean up) {
			T min = theMinimum.get();
			T max = theMaximum.get();
			if (up && Objects.equals(value, max))
				return new BiTuple<>(min, format(min));
			else if (!up && Objects.equals(value, min))
				return new BiTuple<>(max, format(max));
			else
				return theWrapped.adjust(value, formatted, cursor, up);
		}
	}

	public static class ListFormat<T> implements SpinnerFormat<List<T>> {
		private final SpinnerFormat<T> theFormat;
		private final String theDelimiter;
		private final String postDelimit;

		public ListFormat(SpinnerFormat<T> format, String delimiter, String postDelimit) {
			theFormat = format;
			theDelimiter = delimiter;
			this.postDelimit = postDelimit;
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

		@Override
		public boolean supportsAdjustment(boolean withContext) {
			return withContext;
		}

		@Override
		public BiTuple<List<T>, String> adjust(List<T> value, String formatted, int cursor, boolean up) {
			int start = 0;
			int delimitIdx = 0;
			int index = 0;
			int i;
			for (i = 0; i < formatted.length(); i++) {
				if (formatted.charAt(i) == theDelimiter.charAt(delimitIdx)) {
					delimitIdx++;
					if (delimitIdx == theDelimiter.length()) {
						if (i > cursor) {
							if (i - cursor < theDelimiter.length())
								return null; // Adjustment on the delimiter
							BiTuple<T, String> adjusted = theFormat.adjust(//
								value.get(index), formatted.substring(start, i - theDelimiter.length()), cursor - start, up);
							if (adjusted == null)
								return null;
							List<T> newValue = new ArrayList<>(value.size());
							newValue.addAll(value);
							newValue.set(i, adjusted.getValue1());
							String newFormatted = formatted.substring(0, start) + adjusted.getValue2()
								+ formatted.substring(i - theDelimiter.length());
							return new BiTuple<>(newValue, newFormatted);
						}
						index++;
						delimitIdx = 0;
						while (i < formatted.length() - 1 && Character.isWhitespace(formatted.charAt(i + 1)))
							i++;
						start = i + 1;
					}
				}
			}
			if (delimitIdx > 0) {
				return null;
			}
			BiTuple<T, String> adjusted = theFormat.adjust(//
				value.get(index), formatted.substring(start, i - theDelimiter.length()), cursor - start, up);
			if (adjusted == null)
				return null;
			List<T> newValue = new ArrayList<>(value.size());
			newValue.addAll(value);
			newValue.set(i, adjusted.getValue1());
			String newFormatted = formatted.substring(0, start) + adjusted.getValue2() + formatted.substring(i - theDelimiter.length());
			return new BiTuple<>(newValue, newFormatted);
		}
	}

	public static class SetFormat<T> implements SpinnerFormat<Set<T>> {
		private final SpinnerFormat<T> theFormat;
		private final String theDelimiter;
		private final String postDelimit;

		public SetFormat(SpinnerFormat<T> format, String delimiter, String postDelimit) {
			theFormat = format;
			theDelimiter = delimiter;
			this.postDelimit = postDelimit;
		}

		@Override
		public void append(StringBuilder text, Set<T> value) {
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
		public Set<T> parse(CharSequence text) throws ParseException {
			int start = 0;
			int delimitIdx = 0;
			Set<T> set = new LinkedHashSet<>();
			for (int i = 0; i < text.length(); i++) {
				if (text.charAt(i) == theDelimiter.charAt(delimitIdx)) {
					delimitIdx++;
					if (delimitIdx == theDelimiter.length()) {
						delimitIdx = 0;
						CharSequence valueStr = text.subSequence(start, i + 1 - theDelimiter.length());
						T value = theFormat.parse(valueStr);
						if (!set.add(value)) {
							throw new ParseException("Value \"" + valueStr + "\" is present twice", start);
						}
						while (i < text.length() - 1 && Character.isWhitespace(text.charAt(i + 1)))
							i++;
						start = i + 1;
					}
				}
			}
			if (start < text.length()) {
				CharSequence valueStr = text.subSequence(start, text.length());
				T value = theFormat.parse(valueStr);
				if (!set.add(value)) {
					throw new ParseException("Value \"" + valueStr + "\" is present twice", start);
				}
				set.add(value);
			}
			return set;
		}

		@Override
		public boolean supportsAdjustment(boolean withContext) {
			return withContext;
		}

		@Override
		public BiTuple<Set<T>, String> adjust(Set<T> value, String formatted, int cursor, boolean up) {
			int start = 0;
			int delimitIdx = 0;
			int i;
			Set<T> newValue = new LinkedHashSet<>();
			Iterator<T> valueIter = value.iterator();
			for (i = 0; i < formatted.length(); i++) {
				if (formatted.charAt(i) == theDelimiter.charAt(delimitIdx)) {
					delimitIdx++;
					if (delimitIdx == theDelimiter.length()) {
						if (i > cursor) {
							if (i - cursor < theDelimiter.length())
								return null; // Adjustment on the delimiter
							T valueI = valueIter.next();
							BiTuple<T, String> adjusted = theFormat.adjust(//
								valueI, formatted.substring(start, i - theDelimiter.length()), cursor - start, up);
							if (adjusted == null)
								return null;
							newValue.add(adjusted.getValue1());
							while (valueIter.hasNext())
								newValue.add(valueIter.next());
							String newFormatted = formatted.substring(0, start) + adjusted.getValue2()
								+ formatted.substring(i - theDelimiter.length());
							return new BiTuple<>(newValue, newFormatted);
						}
						newValue.add(valueIter.next());
						delimitIdx = 0;
						while (i < formatted.length() - 1 && Character.isWhitespace(formatted.charAt(i + 1)))
							i++;
						start = i + 1;
					}
				}
			}
			if (delimitIdx > 0) {
				return null;
			}
			T valueI = valueIter.next();
			BiTuple<T, String> adjusted = theFormat.adjust(//
				valueI, formatted.substring(start, i - theDelimiter.length()), cursor - start, up);
			if (adjusted == null)
				return null;
			newValue.add(adjusted.getValue1());
			String newFormatted = formatted.substring(0, start) + adjusted.getValue2() + formatted.substring(i - theDelimiter.length());
			return new BiTuple<>(newValue, newFormatted);
		}
	}
}

package org.qommons;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.io.FieldedAdjustable;
import org.qommons.io.FieldedComponent;
import org.qommons.tree.BetterTreeSet;

/**
 * There are some operations on java.time classes for which methods are not provided by the library. Other existing methods end up being
 * very slow. This class contains optimized implementations of some useful time operations.
 */
public class TimeUtils {
	/** {@link TimeZone#getTimeZone(String) TimeZone.getTimeZone("GMT")} */
	public static final TimeZone GMT = TimeZone.getTimeZone("GMT");

	/** Components of a parsed date */
	public enum DateElementType {
		/** The year, either 2-digit or full */
		Year,
		/** The month, represented by digits or a full or partial name */
		Month,
		/** The day of the month, starting at 1 */
		Day,
		/** The hour, either 24-hour format or 12-hour, depending on the present of an {@link #AmPm} element */
		Hour,
		/** The minute within the hour */
		Minute,
		/** The second within the minute */
		Second,
		/** The sub-second within the second. Any number of digits may be specified up to 9 (nanoseconds). */
		SubSecond,
		/** The "a.m." or "p.m." marker for 12-hour time format */
		AmPm,
		/** The time zone specified in the format */
		TimeZone
	}

	/** Time units in a parsed duration */
	public enum DurationComponentType {
		/** Years */
		Year,
		/** Months */
		Month,
		/** Weeks */
		Week,
		/** Days */
		Day,
		/** Hours */
		Hour,
		/** Minutes */
		Minute,
		/** Seconds */
		Second,
		/** Milliseconds */
		Millisecond,
		/** Microseconds */
		Microsecond,
		/** Nanoseconds */
		Nanosecond
	}

	/** A parsed time returned from {@link TimeUtils#parseFlexFormatTime(CharSequence, TimeZone, boolean, boolean)} */
	public interface ParsedTime extends FieldedAdjustable<DateElementType, TimeComponent, ParsedTime> {
		/** @return The time zone of the parsed time, if specified */
		TimeZone getTimeZone();

		/**
		 * @param field The type to increment
		 * @param amount The amount to increment the date by
		 * @return The incremented time
		 */
		ParsedTime add(DateElementType field, int amount);

		/**
		 * @param reference The reference time that the parsed time may be relative to
		 * @return The instant represented by this time relative to the given reference time
		 */
		Instant evaluate(Supplier<Instant> reference);

		/**
		 * @param time The time to compare this to
		 * @return Whether this time may represent the given time relative to any reference
		 */
		boolean mayMatch(Instant time);

		/**
		 * Whether this time can be {@link #compareTo(ParsedTime) compared} to the given time. E.g. relative times cannot be compared if the
		 * largest time granularity they specify is not the same, like ("18May" and "12:15").
		 * 
		 * @param other The time to compare to
		 * @return Whether the result of {@link #compareTo(ParsedTime)} will have meaning for the given time
		 */
		boolean isComparable(ParsedTime other);
	}

	/** A component of a {@link ParsedTime} */
	public static class TimeComponent extends FieldedComponent<DateElementType> {
		final ParsedDateElement theParsedElement;

		TimeComponent(DateElementType field, ParsedDateElement element) {
			super(element.index, element.index + element.text.length(), field, element.value);
			theParsedElement = element;
		}

		@Override
		public String toString() {
			return theParsedElement.text.toString();
		}
	}

	/** A component of a {@link ParsedDuration} */
	public static class DurationComponent extends FieldedComponent<DurationComponentType> {
		private final String text;
		final int valueStart;
		final int valueEnd;

		/**
		 * @param start The character index of the start of this component
		 * @param valueStart The character index of the start of this component's value (as opposed to any unit)
		 * @param valueEnd The character index of the end of this component's value
		 * @param field The type of this component
		 * @param value The value of this component
		 * @param text The text that was parsed into this component
		 */
		public DurationComponent(int start, int valueStart, int valueEnd, DurationComponentType field, int value, String text) {
			super(start, start + text.length(), field, value);
			if (start < 0 || valueStart < start || valueEnd < valueStart || (valueEnd - valueStart) > text.length())
				throw new IndexOutOfBoundsException(start + ", " + valueStart + ", " + valueEnd + " of " + text.length());
			this.text = text;
			this.valueStart = valueStart;
			this.valueEnd = valueEnd;
		}

		@Override
		public String toString() {
			return text;
		}
	}

	/** A parsed time returned from {@link TimeUtils#parseDuration(CharSequence)} */
	public static class ParsedDuration implements FieldedAdjustable<DurationComponentType, DurationComponent, ParsedDuration> {
		private final boolean isNegative;
		private final EnumMap<DurationComponentType, DurationComponent> components;
		private final List<DurationComponent> theSequence;
		private final List<String> theSeparators;

		/**
		 * @param negative Whether this represents a negative duration
		 * @param sequence The parsed components composing this duration
		 * @param separators The whitespace separators between each component
		 */
		public ParsedDuration(boolean negative, List<DurationComponent> sequence, List<String> separators) {
			isNegative = negative;
			components = new EnumMap<>(DurationComponentType.class);
			for (DurationComponent c : sequence)
				if (components.put(c.getField(), c) != null)
					throw new IllegalArgumentException("Duplicate components of type " + c.getField());
			this.theSequence = sequence;
			theSeparators = separators;
		}

		@Override
		public List<DurationComponent> getComponents() {
			return theSequence;
		}

		@Override
		public Class<DurationComponentType> getFieldType() {
			return DurationComponentType.class;
		}

		@Override
		public DurationComponent getField(DurationComponentType type) {
			return components.get(type);
		}

		@Override
		public org.qommons.TimeUtils.ParsedDuration adjust(int position, int amount) {
			return FieldedAdjustable.super.adjust(position, isNegative ? -amount : amount);
		}

		@Override
		public ParsedDuration with(DurationComponentType type, int value) {
			EnumMap<DurationComponentType, Integer> fieldValues = new EnumMap<>(DurationComponentType.class);
			for (Map.Entry<DurationComponentType, DurationComponent> entry : components.entrySet())
				fieldValues.put(entry.getKey(), entry.getValue().getValue());
			DurationComponent found = getField(type);
			boolean adjusted;
			if (found == null)
				adjusted = adjustValues(fieldValues, type, value);
			else
				adjusted = adjustValues(fieldValues, type, value - found.getValue());
			if (!adjusted)
				return null;
			List<DurationComponent> sequence = new ArrayList<>();
			List<String> separators = new ArrayList<>();
			separators.add(theSeparators.get(0));
			int index = separators.get(0).length();
			for (DurationComponentType field : DurationComponentType.values()) {
				DurationComponent oldComp = components.get(field);
				Integer newVal = fieldValues.getOrDefault(field, 0);
				String valueStr = newVal.toString();
				String text;
				int valueStart;
				String separator;
				if (oldComp != null) {
					valueStart = oldComp.valueStart - oldComp.getStart();
					text = oldComp.text.substring(0, valueStart) + newVal + oldComp.text.substring(oldComp.valueEnd - oldComp.getStart());
					separator = theSeparators.get(theSequence.indexOf(oldComp) + 1);
				} else if (newVal != 0) {
					valueStart = 0;
					text = newVal.toString();
					switch (field) {
					case Year:
						text += "y";
						break;
					case Month:
						text += "mo";
						break;
					case Week:
						text += "w";
						break;
					case Day:
						text += "d";
						break;
					case Hour:
						text += "h";
						break;
					case Minute:
						text += "m";
						break;
					case Second:
						text += "s";
						break;
					case Millisecond:
						text += "ms";
						break;
					case Microsecond:
						text += "us";
						break;
					case Nanosecond:
						text += "ns";
						break;
					}
					if (sequence.isEmpty())
						separator = theSeparators.get(1);
					else
						separator = separators.get(separators.size() - 1);
				} else
					continue;
				sequence.add(new DurationComponent(index, index + valueStart, index + valueStart + valueStr.length(), field, newVal, text));
				separators.add(separator);
				index += text.length() + separator.length();
			}
			return new ParsedDuration(isNegative, sequence, separators);
		}

		private static boolean adjustValues(EnumMap<DurationComponentType, Integer> fieldValues, DurationComponentType type, int amount) {
			Integer oldValue = fieldValues.get(type);
			int newValue = (oldValue == null ? 0 : oldValue.intValue()) + amount;
			int limit = 0;
			DurationComponentType superType = null;
			if (type.ordinal() > 0)
				superType = DurationComponentType.values()[type.ordinal() - 1];
			switch (type) {
			case Year:
				limit = Integer.MAX_VALUE;
				break;
			case Month:
				superType = DurationComponentType.Year;
				limit = 12;
				break;
			case Week:
				superType = null;
				limit = Integer.MAX_VALUE;
				break;
			case Day:
				if (fieldValues.containsKey(DurationComponentType.Week)) {
					superType = DurationComponentType.Week;
					limit = 7;
				} else if (fieldValues.containsKey(DurationComponentType.Month)) {
					superType = DurationComponentType.Month;
					limit = 31;
				} else if (fieldValues.containsKey(DurationComponentType.Year)) {
					superType = DurationComponentType.Year;
					limit = 365;
				} else {
					superType = null;
					limit = Integer.MAX_VALUE;
				}
				break;
			case Hour:
				limit = 24;
				break;
			case Minute:
			case Second:
				limit = 60;
				break;
			case Millisecond:
				limit = 1000;
				break;
			case Microsecond:
				if (fieldValues.containsKey(DurationComponentType.Millisecond)) {
					superType = DurationComponentType.Millisecond;
					limit = 1000;
				} else {
					superType = DurationComponentType.Second;
					limit = 1000;
				}
				break;
			case Nanosecond:
				if (fieldValues.containsKey(DurationComponentType.Microsecond)
					|| fieldValues.containsKey(DurationComponentType.Millisecond)) {
					superType = DurationComponentType.Microsecond;
					limit = 1000;
				} else {
					superType = DurationComponentType.Second;
					limit = 1_000_000_000;
				}
				break;
			}
			if (superType != null) {
				int passOn = 0;
				if (newValue < 0) {
					passOn = newValue / limit;
					newValue %= limit;
					if (newValue < 0) {
						passOn--;
						newValue += limit;
					}
				} else if (newValue >= limit) {
					passOn = newValue / limit;
					newValue %= limit;
				}
				if (passOn != 0 && !adjustValues(fieldValues, superType, passOn))
					return false;
			} else if (newValue < 0)
				return false;
			fieldValues.put(type, newValue);
			return true;
		}

		/** @return A duration with this object's magnitude */
		public Duration asDuration() {
			Duration d = Duration.ZERO;
			for (DurationComponent c : theSequence) {
				switch (c.getField()) {
				case Year:
					d = d.plusDays(getDaysInYears(c.getValue()));
					break;
				case Month:
					d = d.plusDays(getDaysInMonths(c.getValue()));
					break;
				case Week:
					d = d.plusDays(c.getValue() * 7L);
					break;
				case Day:
					d = d.plusDays(c.getValue());
					break;
				case Hour:
					d = d.plusHours(c.getValue());
					break;
				case Minute:
					d = d.plusMinutes(c.getValue());
					break;
				case Second:
					d = d.plusSeconds(c.getValue());
					break;
				case Millisecond:
					d = d.plusMillis(c.getValue());
					break;
				case Microsecond:
					d = d.plusNanos(c.getValue() * 1000L);
					break;
				case Nanosecond:
					d = d.plusNanos(c.getValue());
					break;
				}
			}
			return d;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			if (isNegative)
				str.append('-');
			for (int i = 0; i < theSequence.size(); i++) {
				str.append(theSeparators.get(i));
				str.append(theSequence.get(i).toString());
			}
			str.append(theSeparators.get(theSeparators.size() - 1));
			return str.toString();
		}

		/**
		 * @param d The duration to convert
		 * @param withWeeks Whether to print weeks or just days
		 * @return A parsed duration with the same magnitude as the given duration
		 */
		public static ParsedDuration asParsedDuration(Duration d, boolean withWeeks) {
			boolean neg = d.isNegative();
			if (neg)
				d = d.negated();
			List<String> separators = new ArrayList<>();
			List<DurationComponent> components = new ArrayList<>();
			separators.add("");
			int index = 0;
			long seconds = d.getSeconds();
			if (seconds > WEEK.getSeconds() && withWeeks) {
				int weeks = (int) (seconds / WEEK.getSeconds());
				seconds %= WEEK.getSeconds();
				String valueStr = String.valueOf(weeks);
				components
					.add(new DurationComponent(index, index, index + valueStr.length(), DurationComponentType.Week, weeks, valueStr + "w"));
				index += valueStr.length() + 1;
			}
			if (seconds >= DAY.getSeconds()) {
				if (!components.isEmpty()) {
					separators.add(" ");
					index++;
				}
				int days = (int) (seconds / DAY.getSeconds());
				seconds %= DAY.getSeconds();
				String valueStr = String.valueOf(days);
				components
					.add(new DurationComponent(index, index, index + valueStr.length(), DurationComponentType.Day, days, valueStr + "d"));
				index += valueStr.length() + 1;
			}
			if (seconds >= HOUR.getSeconds()) {
				if (!components.isEmpty()) {
					separators.add(" ");
					index++;
				}
				int hours = (int) (seconds / HOUR.getSeconds());
				seconds %= HOUR.getSeconds();
				String valueStr = String.valueOf(hours);
				components
					.add(new DurationComponent(index, index, index + valueStr.length(), DurationComponentType.Hour, hours, valueStr + "h"));
				index += valueStr.length() + 1;
			}
			if (seconds >= 60) {
				if (!components.isEmpty()) {
					separators.add(" ");
					index++;
				}
				int minutes = (int) (seconds / 60);
				seconds %= 60;
				String valueStr = String.valueOf(minutes);
				components.add(
					new DurationComponent(index, index, index + valueStr.length(), DurationComponentType.Minute, minutes, valueStr + "m"));
				index += valueStr.length() + 1;
			}
			if (seconds > 0) {
				if (!components.isEmpty()) {
					separators.add(" ");
					index++;
				}
				String valueStr = String.valueOf(seconds);
				components.add(new DurationComponent(index, index, index + valueStr.length(), DurationComponentType.Second, (int) seconds,
					valueStr + "s"));
				index += valueStr.length() + 1;
			}
			int nanos = d.getNano();
			if (nanos == 0) {} else if (nanos % 1_000_000 == 0) {
				if (!components.isEmpty()) {
					separators.add(" ");
					index++;
				}
				int millis = nanos / 1_000_000;
				String valueStr = String.valueOf(millis);
				components.add(new DurationComponent(index, index, index + valueStr.length(), DurationComponentType.Millisecond, millis,
					valueStr + "ms"));
				index += valueStr.length() + 2;
			} else if (nanos % 1_000 == 0) {
				if (!components.isEmpty()) {
					separators.add(" ");
					index++;
				}
				int micros = nanos / 1_000;
				String valueStr = String.valueOf(micros);
				components.add(new DurationComponent(index, index, index + valueStr.length(), DurationComponentType.Microsecond, micros,
					valueStr + "us"));
				index += valueStr.length() + 2;
			} else {
				if (!components.isEmpty()) {
					separators.add(" ");
					index++;
				}
				String valueStr = String.valueOf(nanos);
				components.add(new DurationComponent(index, index, index + valueStr.length(), DurationComponentType.Nanosecond, nanos,
					valueStr + "ns"));
				index += valueStr.length() + 2;
			}
			separators.add("");
			if (components.isEmpty())
				components.add(new DurationComponent(index, index, index + 2, DurationComponentType.Second, 0, "0s"));
			return new ParsedDuration(neg, components, separators);
		}
	}

	private static final Duration WEEK = Duration.ofDays(7);
	private static final Duration DAY = Duration.ofDays(1);
	private static final Duration HOUR = Duration.ofHours(1);
	private static final Duration MINUTE = Duration.ofMinutes(1);

	static abstract class ParsedTimeImpl implements ParsedTime {
		private final String theText;
		private final TimeZone theTimeZone;
		protected final Map<DateElementType, TimeComponent> elements;
		protected final BetterSortedSet<TimeComponent> sequence;

		public ParsedTimeImpl(String text, TimeZone timeZone, Map<DateElementType, TimeComponent> elements) {
			theText = text;
			theTimeZone = timeZone;
			this.elements = elements;
			BetterSortedSet<TimeComponent> ebp = new BetterTreeSet<>(false, (t1, t2) -> Integer.compare(t1.getStart(), t2.getStart()));
			ebp.addAll(elements.values());
			sequence = BetterCollections.unmodifiableSortedSet(ebp);
		}

		@Override
		public TimeZone getTimeZone() {
			return theTimeZone;
		}

		@Override
		public Class<DateElementType> getFieldType() {
			return DateElementType.class;
		}

		@Override
		public TimeComponent getField(DateElementType type) {
			return elements.get(type);
		}

		@Override
		public List<TimeComponent> getComponents() {
			return sequence;
		}

		@Override
		public ParsedTime with(DateElementType type, int value) {
			switch (type) {
			case AmPm:
			case TimeZone:
				throw new IllegalArgumentException("Adding to field " + type + " is not supported");
			default:
				break;
			}
			TimeComponent component = elements.get(type);
			if (component == null)
				throw new IllegalArgumentException("Field " + type + " not present in this parsed time");
			return add(type, value - component.getValue());
		}

		protected Map<DateElementType, TimeComponent> adjustElements(StringBuilder str, ToIntFunction<DateElementType> value) {
			Map<DateElementType, TimeComponent> newElements = new EnumMap<>(DateElementType.class);
			newElements.putAll(elements);
			int indexBump = 0;
			for (TimeComponent component : sequence) {
				ParsedDateElement oldEl = elements.get(component.getField()).theParsedElement;
				ParsedDateElement newEl = null;
				int newValue;
				switch (component.getField()) {
				case Year:
					newValue = value.applyAsInt(component.getField());
					if (component.theParsedElement.text.length() == 2)
						newValue = newValue % 100;
					newEl = adjust(component.theParsedElement, newValue, indexBump);
					break;
				case Month:
					newEl = adjust(component.theParsedElement, value.applyAsInt(component.getField()), indexBump);
					break;
				case Day:
					newEl = adjust(component.theParsedElement, value.applyAsInt(component.getField()), indexBump);
					break;
				case Hour:
					newEl = adjust(component.theParsedElement, value.applyAsInt(component.getField()), indexBump);
					break;
				case Minute:
					newEl = adjust(component.theParsedElement, value.applyAsInt(component.getField()), indexBump);
					break;
				case Second:
					newEl = adjust(component.theParsedElement, value.applyAsInt(component.getField()), indexBump);
					break;
				case SubSecond:
					newEl = adjust(component.theParsedElement, value.applyAsInt(component.getField()), indexBump);
					break;
				case AmPm:
					newEl = adjust(component.theParsedElement, value.applyAsInt(component.getField()), indexBump);
					break;
				case TimeZone:
					break;
				}

				if (newEl != component.theParsedElement) {
					newElements.put(component.getField(), new TimeComponent(component.getField(), newEl));
					str.delete(newEl.index, newEl.index + oldEl.text.length());
					str.insert(newEl.index, newEl.text);
					indexBump += newEl.text.length() - oldEl.text.length();
				}
			}
			return newElements;
		}

		private static ParsedDateElement adjust(ParsedDateElement element, int newValue, int indexBump) {
			if (element.value == newValue)
				return element;
			String newText = null;
			switch (element.type) {
			case AM_PM:
				boolean lower = true;
				newText = element.text.toString();
				int idx = newText.indexOf(element.value == 0 ? 'a' : 'p');
				if (idx < 0) {
					lower = false;
					idx = newText.indexOf(element.value == 0 ? 'A' : 'P');
				}
				// Assume it's here, shouldn't have parsed without it
				char newChar;
				if (newValue == 0)
					newChar = lower ? 'a' : 'A';
				else
					newChar = lower ? 'p' : 'P';
				newText = newText.substring(0, idx) + newChar + newText.substring(idx + 1);
				break;
			case DIGIT:
				newText = StringUtils.add(element.text, element.text.length() - 1, newValue - element.value);
				break;
			case MONTH:
				if (element.text.length() != 3 && element.text.toString().toLowerCase().equals(MONTHS[element.value]))
					newText = MONTHS[newValue];
				else
					newText = MONTHS[newValue].substring(0, element.text.length());
				if (Character.isUpperCase(element.text.charAt(0)))
					newText = capitalize(newText);
				break;
			case SEP:
			case CHARS:
				break;
			}
			ParsedDateElement newEl = new ParsedDateElement(element.type, element.index + indexBump, newText);
			newEl.value = newValue;
			return newEl;
		}

		@Override
		public String toString() {
			return theText;
		}
	}

	/**
	 * A time parsed from {@link TimeUtils#parseFlexFormatTime(CharSequence, TimeZone, boolean, boolean)} for which the year is specified
	 * with 4 digits, meaning that it represents an absolute instant or a single range in time.
	 */
	public static class AbsoluteTime extends ParsedTimeImpl {
		/** The lower bound of the range represented by this time */
		public final Instant time;
		/** The upper bound of the range represented by this time */
		public final Instant maxTime;

		int nanoDigits;

		AbsoluteTime(String text, Instant time, Calendar cal, TimeZone timeZone, DateElementType lowestResolution, int nanoDigits,
			Map<DateElementType, TimeComponent> elements) {
			super(text, timeZone, elements);
			this.time = time;

			Instant max = null;
			switch (lowestResolution) {
			case Year:
				cal.add(Calendar.YEAR, 1);
				max = cal.toInstant();
				break;
			case Month:
				cal.add(Calendar.MONTH, 1);
				max = cal.toInstant();
				break;
			case Day:
				max = time.plus(DAY);
				break;
			case Hour:
				max = time.plus(HOUR);
				break;
			case Minute:
				max = time.plus(MINUTE);
				break;
			case Second:
				max = time.plusSeconds(1);
				break;
			case SubSecond:
				int nanos = 1;
				for (int dig = nanoDigits; dig > 0; dig--)
					nanos *= 10;
				max = time.plusNanos(nanos);
				break;
			case AmPm:
			case TimeZone:
				throw new IllegalStateException();
			}
			maxTime = max;
			this.nanoDigits = nanoDigits;
		}

		@Override
		public Instant evaluate(Supplier<Instant> reference) {
			return time;
		}

		@Override
		public boolean mayMatch(Instant t) {
			return t.compareTo(time) >= 0 && t.compareTo(maxTime) < 0;
		}

		@Override
		public boolean isComparable(ParsedTime other) {
			return true;
		}

		@Override
		public int compareTo(ParsedTime o) {
			if (o instanceof AbsoluteTime) {
				AbsoluteTime other = (AbsoluteTime) o;
				int comp = other.time.compareTo(time);
				if (comp == 0)
					return 0;
				else if (comp < 0) {
					comp = other.maxTime.compareTo(time);
					if (comp <= 0)
						return 1;
					else
						return 0;
				} else {
					comp = maxTime.compareTo(other.time);
					if (comp <= 0)
						return -1;
					else
						return 0;
				}
			} else {
				Instant t = o.evaluate(() -> time);
				int comp = t.compareTo(time);
				if (comp <= 0)
					return -comp;
				comp = t.compareTo(maxTime);
				if (comp >= 0)
					return -1;
				else
					return 0;
			}
		}

		@Override
		public ParsedTime add(DateElementType field, int amount) {
			switch (field) {
			case AmPm:
			case TimeZone:
				throw new IllegalArgumentException("Adding to field " + field + " is not supported");
			default:
				break;
			}
			if (!elements.containsKey(field))
				throw new IllegalArgumentException("Field " + field + " not present in this parsed time");
			int nanos = time.getNano();
			Calendar cal = CALENDAR.get();
			cal.setTimeZone(getTimeZone() == null ? GMT : getTimeZone());
			cal.setTimeInMillis(time.toEpochMilli());
			switch (field) {
			case Year:
				cal.add(Calendar.YEAR, amount);
				break;
			case Month:
				cal.add(Calendar.MONTH, amount);
				break;
			case Day:
				cal.add(Calendar.DAY_OF_MONTH, amount);
				break;
			case Hour:
				cal.add(Calendar.HOUR_OF_DAY, amount);
				break;
			case Minute:
				cal.add(Calendar.MINUTE, amount);
				break;
			case Second:
				cal.add(Calendar.SECOND, amount);
				break;
			case SubSecond:
				cal.set(Calendar.MILLISECOND, amount);
				int incAmount = 1;
				for (int dig = 9; dig > nanoDigits; dig--)
					incAmount *= 10;
				long newNanos = amount * 1L * incAmount;

				if (newNanos > 1_000_000_000) {
					cal.add(Calendar.SECOND, (int) (newNanos / 1_000_000_000));
					newNanos %= 1_000_000_000;
				}
				nanos = (int) newNanos;
				break;
			case AmPm:
			case TimeZone:
				break;
			}
			StringBuilder str = new StringBuilder(toString());
			int fNanos = nanos;
			Map<DateElementType, TimeComponent> newElements = adjustElements(str, type -> {
				switch (type) {
				case Year:
					return cal.get(Calendar.YEAR);
				case Month:
					return cal.get(Calendar.MONTH) - Calendar.JANUARY;
				case Day:
					return cal.get(Calendar.DAY_OF_MONTH);
				case Hour:
					return elements.containsKey(DateElementType.AmPm) ? cal.get(Calendar.HOUR) : cal.get(Calendar.HOUR_OF_DAY);
				case Minute:
					return cal.get(Calendar.MINUTE);
				case Second:
					return cal.get(Calendar.SECOND);
				case SubSecond:
					int subSecond = fNanos;
					for (int i = nanoDigits; i < 9; i++)
						subSecond /= 10;
					return subSecond;
				case AmPm:
					return cal.get(Calendar.AM_PM) - Calendar.AM;
				case TimeZone:
					break;
				}
				return 0;
			});
			Instant newTime = Instant.ofEpochSecond(cal.getTimeInMillis() / 1000, nanos);
			return new AbsoluteTime(str.toString(), newTime, cal, getTimeZone(), getLowestResolution(), nanoDigits, newElements);
		}

		private DateElementType getLowestResolution() {
			DateElementType res = null;
			for (DateElementType type : elements.keySet()) {
				switch (type) {
				case AmPm:
				case TimeZone:
					continue;
				default:
					break;
				}
				if (res == null)
					res = type;
				else if (type.ordinal() > res.ordinal())
					res = type;
			}
			return res;
		}
	}

	/**
	 * A time parsed from {@link TimeUtils#parseFlexFormatTime(CharSequence, TimeZone, boolean, boolean)} where the year was not specified
	 * with 4 digits, meaning that this time can represent any number of time ranges depending on a reference point.
	 */
	public static class RelativeTime extends ParsedTimeImpl {
		private final int nanoResolution;
		private final int nanoDigits;

		RelativeTime(String text, TimeZone timeZone, Map<DateElementType, TimeComponent> elements) {
			super(text, timeZone, elements);
			TimeComponent subSecEl = elements.get(DateElementType.SubSecond);
			if (subSecEl != null) {
				nanoDigits = subSecEl.theParsedElement.text.length();
				int nanoRes = 1_000_000_000;
				for (int i = 0; i < nanoDigits; i++)
					nanoRes /= 10;
				nanoResolution = nanoRes;
			} else {
				nanoDigits = 0;
				nanoResolution = 0;
			}
		}

		@Override
		public Instant evaluate(Supplier<Instant> reference) {
			Calendar cal = CALENDAR.get();
			Instant ref = reference.get();
			cal.setTimeInMillis(ref.getEpochSecond() * 1000);
			cal.setTimeZone(getTimeZone());
			int nanos = ref.getNano();
			for (Map.Entry<DateElementType, TimeComponent> element : elements.entrySet()) {
				switch (element.getKey()) {
				case Year:
					// If the year was absolute, it wouldn't be a relative time
					int year = cal.get(Calendar.YEAR);
					int newYear = (year / 100) * 100 + element.getValue().getValue();
					int diff = year - newYear;
					if (diff <= -50)
						newYear += 100;
					else if (diff > 50)
						newYear -= 100;
					cal.set(Calendar.YEAR, newYear);
					break;
				case Month:
					cal.set(Calendar.MONTH, element.getValue().getValue());
					break;
				case Day:
					cal.set(Calendar.DAY_OF_MONTH, element.getValue().getValue());
					break;
				case Hour:
					cal.set(Calendar.HOUR_OF_DAY, element.getValue().getValue());
					break;
				case Minute:
					cal.set(Calendar.MINUTE, element.getValue().getValue());
					break;
				case Second:
					cal.set(Calendar.SECOND, element.getValue().getValue());
					break;
				case SubSecond:
					nanos = element.getValue().getValue();
					break;
				case AmPm:
				case TimeZone:
					throw new IllegalStateException();
				}
			}
			return Instant.ofEpochSecond(cal.getTimeInMillis() / 1000, nanos);
		}

		@Override
		public boolean mayMatch(Instant time) {
			Calendar cal = CALENDAR.get();
			cal.setTimeInMillis(time.getEpochSecond() * 1000);
			for (Map.Entry<DateElementType, TimeComponent> element : elements.entrySet()) {
				switch (element.getKey()) {
				case Year:
					if (cal.get(Calendar.YEAR) % 100 != element.getValue().getValue())
						return false;
					break;
				case Month:
					if (cal.get(Calendar.MONTH) != element.getValue().getValue())
						return false;
					break;
				case Day:
					if (cal.get(Calendar.DAY_OF_MONTH) != element.getValue().getValue())
						return false;
					break;
				case Hour:
					if (cal.get(Calendar.HOUR_OF_DAY) != element.getValue().getValue())
						return false;
					break;
				case Minute:
					if (cal.get(Calendar.MINUTE) != element.getValue().getValue())
						return false;
					break;
				case Second:
					if (cal.get(Calendar.SECOND) != element.getValue().getValue())
						return false;
					break;
				case SubSecond:
					int nanoDiff = time.getNano() - element.getValue().getValue();
					if (nanoDiff < 0 || nanoDiff >= nanoResolution)
						return false;
					break;
				case AmPm:
				case TimeZone:
					throw new IllegalStateException();
				}
			}
			return true;
		}

		@Override
		public boolean isComparable(ParsedTime o) {
			if (o instanceof AbsoluteTime)
				return true;
			RelativeTime other = (RelativeTime) o;
			for (DateElementType type : DateElementType.values()) {
				if (elements.containsKey(type))
					return other.elements.containsKey(type);
				else if (other.elements.containsKey(type))
					return false;
			}
			throw new IllegalStateException();
		}

		@Override
		public int compareTo(ParsedTime o) {
			if (o instanceof AbsoluteTime)
				return -o.compareTo(this);
			RelativeTime other = (RelativeTime) o;
			for (DateElementType type : DateElementType.values()) {
				TimeComponent el = elements.get(type);
				TimeComponent otherEl = other.elements.get(type);
				if (el != null) {
					if (otherEl != null) {
						int comp = Integer.compare(el.getValue(), otherEl.getValue());
						if (comp != 0)
							return comp;
					} else
						return 0;
				} else if (otherEl != null)
					return 0;
			}
			return 0;
		}

		@Override
		public ParsedTime add(DateElementType field, int amount) {
			if (!elements.containsKey(field))
				throw new IllegalArgumentException("Field " + field + " is not present in this time");
			Map<DateElementType, Integer> newValues = new EnumMap<>(DateElementType.class);
			for (Map.Entry<DateElementType, TimeComponent> element : elements.entrySet())
				newValues.put(element.getKey(), element.getValue().getValue());
			DateElementType adjustingField = field;
			int superAdjust = amount;
			while (superAdjust != 0) {
				superAdjust = adjustValues(newValues, adjustingField, superAdjust);
				if (superAdjust != 0)
					adjustingField = DateElementType.values()[adjustingField.ordinal() - 1];
			}
			StringBuilder str = new StringBuilder(toString());
			Map<DateElementType, TimeComponent> newElements = adjustElements(str, newValues::get);
			return new RelativeTime(str.toString(), getTimeZone(), newElements);
		}

		private int adjustValues(Map<DateElementType, Integer> values, DateElementType field, int amount) {
			int oldValue = values.get(field);
			long newValue = oldValue + amount;
			int superAdjust = 0;
			switch (field) {
			case Year:
				break;
			case Month:
				while (newValue < 0) {
					superAdjust--;
					newValue += 12;
				}
				while (newValue >= 12) {
					superAdjust++;
					newValue -= 12;
				}
				break;
			case Day:
				Integer month = values.get(DateElementType.Month);
				int dayMax;
				if (month == null) {
					dayMax = 31;
					while (newValue < 1) {
						superAdjust--;
						newValue += dayMax;
					}
					while (newValue > dayMax) {
						superAdjust++;
						newValue -= dayMax;
					}
				} else {
					dayMax = getDayMax(month, 0);
					int monthsPast = 0;
					while (newValue < 1) {
						superAdjust--;
						newValue += dayMax;
						month--;
						if (month < 0)
							month += 12;
						dayMax = getDayMax(month, ++monthsPast);
					}
					while (newValue > dayMax) {
						superAdjust++;
						newValue -= dayMax;
						month++;
						if (month >= 12)
							month -= 12;
						dayMax = getDayMax(month, ++monthsPast);
					}
				}
				break;
			case Hour:
				if (values.containsKey(DateElementType.AmPm)) {
					boolean am = values.get(DateElementType.AmPm) == 0;
					while (newValue < 0) {
						newValue += 12;
						if (am)
							superAdjust--;
						am = !am;
					}
					while (newValue >= 12) {
						newValue -= 12;
						if (!am)
							superAdjust++;
						am = !am;
					}
					values.put(DateElementType.AmPm, am ? 0 : 1);
				} else {
					while (newValue < 0) {
						superAdjust--;
						newValue += 24;
					}
					while (newValue >= 24) {
						superAdjust++;
						newValue -= 24;
					}
				}
				break;
			case Minute:
			case Second:
				while (newValue < 0) {
					superAdjust--;
					newValue += 60;
				}
				while (newValue >= 60) {
					superAdjust++;
					newValue -= 60;
				}
				break;
			case SubSecond:
				int subSecondMax = 1_000_000_000 / nanoResolution;
				while (newValue < 0) {
					superAdjust--;
					newValue += subSecondMax;
				}
				while (newValue >= subSecondMax) {
					superAdjust++;
					newValue -= subSecondMax;
				}
				break;
			case AmPm:
			case TimeZone:
				break;
			}
			return superAdjust;
		}

		private static int getDayMax(int month, int monthsPast) {
			switch (month + 1) {
			// 30 days has
			case 9: // September,
			case 4: // April,
			case 6: // June,
			case 11: // and November
				return 30;
			// All the rest have 31
			case 1:
			case 3:
			case 5:
			case 7:
			case 8:
			case 10:
			case 12:
				return 31;
			// Except for February, blah blah blah
			case 2:
			default:
				if (monthsPast % 4 == 3)
					return 29;
				else
					return 28;
			}
		}
	}

	/**
	 * <p>
	 * Parses a date/time with flexible format.
	 * </p>
	 * <p>
	 * Standard formats supported are:
	 * <ul>
	 * <li>YYYY</li>
	 * <li>YYYY-MM</li>
	 * <li>YYYY-MM-DD</li>
	 * <li>MonthYY (Month=e.g. "January" or "Apr")</li>
	 * <li>MonthYYYY</li>
	 * <li>DDMonth</li>
	 * <li>DDMonthYY</li>
	 * <li>DDMonthYYYY</li>
	 * </p>
	 * For standard date formats, separators can be '-', '/', or '.'. Non-standard formats supported are:
	 * <ul>
	 * <li>MM/YYYY</li>
	 * <li>MM/DD</li>
	 * <li>MM/DD/YY</li>
	 * <li>MM/DD/YYYY</li>
	 * <li>DD.MM</li>
	 * <li>DD.MM.YY</li>
	 * <li>DD.MM.YYYY</li>
	 * </ul>
	 * For non-standard, slash-separated formats, '/' may be replaced with '-'.
	 * </p>
	 * <p>
	 * Time format is HH(:MM(:ss(.S*)))(am|pm). am/pm may also contain '.' characters. The time may end with a time zone identifier. The
	 * hour-only format (HH) may only be used in conjunction with a date format--this parser will fail to parse a sole integer.
	 * </p>
	 * <p>
	 * Dates and times can be used separately. If used together, time can come after the date with a separator of 'T', ':' or '.' or before
	 * the date with a separator of 'T' or '.' or no separator at all if the time ends with a letter or a '.'. A time zone identifier may
	 * come after the date if there is no time or the time comes first (and does not include a time zone).
	 * </p>
	 * <p>
	 * "st", "nd", "rd", and "th" are tolerated after "DD" in any format as long as they are not immediately followed by other alphabetic
	 * characters.
	 * </p>
	 * 
	 * @param str The text to parse
	 * @param defaultTimeZone The time zone to parse times in, unless specified in the text
	 * @param wholeText Whether the entire sequence should be parsed. If false, this will return a value if the beginning of the sequence is
	 *        understood.
	 * @param throwIfNotFound Whether to throw a {@link ParseException} or return null if a time cannot be parsed for any reason
	 * @return A ParsedTime evaluated from the text
	 * @throws ParseException If <code>throwIfNotFound</code> and the string cannot be parsed
	 */
	public static ParsedTime parseFlexFormatTime(CharSequence str, TimeZone defaultTimeZone, boolean wholeText, boolean throwIfNotFound)
		throws ParseException {
		List<ParsedDateElement> elements = new ArrayList<>();
		int i = 0;
		boolean found = true;
		while (found && i < str.length()) {
			if (Character.isWhitespace(str.charAt(i))) {
				i++;
				continue;
			}
			found = false;
			for (ParsedDateElementType type : ParsedDateElementType.values()) {
				int end = type.find(str, i);
				if (end > i) {
					found = true;
					elements.add(new ParsedDateElement(type, i, str.subSequence(i, end)));
					i = end;
					break;
				}
			}
		}
		if (elements.isEmpty() || (wholeText && !found)) {
			if (throwIfNotFound) {
				if (i == 0)
					throw new ParseException("No date/time found", 0);
				else
					throw new ParseException("Unrecognized date/time component", i);
			}
			return null;
		}
		String text = str.subSequence(0, elements.get(elements.size() - 1).index + elements.get(elements.size() - 1).text.length())
			.toString();

		DateFormat first = null, second = null;
		EnumMap<DateElementType, ParsedDateElement> firstInfo = new EnumMap<>(DateElementType.class);
		EnumMap<DateElementType, ParsedDateElement> secondInfo = null;
		int secondIndex = -1;
		for (DateFormat format : FLEX_FORMATS) {
			firstInfo.clear();
			secondIndex = format.match(firstInfo, elements);
			if (secondIndex > 0) {
				first = format;
				break;
			}
		}
		if (secondIndex > 0 && secondIndex < elements.size()) {
			elements.subList(0, secondIndex).clear();
			if (elements.get(0).type == ParsedDateElementType.SEP && elements.get(0).text.charAt(0) == ' ')
				elements.remove(0);
			if (!elements.isEmpty() && elements.get(0).type == ParsedDateElementType.SEP) {
				char sep = elements.get(0).text.charAt(0);
				if (sep == 'T' || sep == ':' || sep == '.') {
					elements.remove(0);
					secondIndex++;
				} else if (!wholeText)
					elements.clear();
			}

			if (!elements.isEmpty()) {
				secondInfo = new EnumMap<>(DateElementType.class);
				int index = -1;
				for (DateFormat format : FLEX_FORMATS) {
					secondInfo.clear();
					index = format.match(secondInfo, elements);
					if (index > 0) {
						second = format;
						break;
					}
				}
			}
		}
		if (first == null) {
			if (throwIfNotFound)
				throw new ParseException("Unrecognized date/time format", 0);
			else
				return null;
		}
		if (second != null) {
			for (Map.Entry<DateElementType, ParsedDateElement> entry : secondInfo.entrySet()) {
				if (firstInfo.put(entry.getKey(), entry.getValue()) != null) {
					if (!throwIfNotFound)
						return null;
					throw new ParseException("Formats " + first + " and " + second + " may not be used together", entry.getValue().index);
				}
			}
		} else if (firstInfo.size() == 1 && firstInfo.get(DateElementType.Hour) != null) {
			if (!throwIfNotFound)
				return null;
			else
				throw new ParseException("The hour-only format is not valid without a date", 0);
		}
		TimeZone timeZone = defaultTimeZone;
		try {
			ParsedDateElement element = firstInfo.remove(DateElementType.TimeZone);
			if (element != null) {
				String zoneId = element.text.toString();
				int zoneIndex = TIME_ZONES.keyIndexTolerant(zoneId);
				if (zoneIndex < 0) {
					if (!throwIfNotFound)
						return null;
					throw new ParseException("Unrecognized time zone " + element.text, element.index);
				}
				if (TIME_ZONES.get(zoneIndex) == null)
					TIME_ZONES.put(zoneIndex, TimeZone.getTimeZone(zoneId));
				timeZone = TIME_ZONES.get(zoneIndex);
			}
			element = firstInfo.remove(DateElementType.AmPm);
			if (element != null) {
				boolean pm = element.type.parse(element.text) > 0;
				element = firstInfo.get(DateElementType.Hour);
				int hour = validate(DateElementType.Hour, element);
				if (hour == 0 || hour > 12) {
					if (!throwIfNotFound)
						return null;
					throw new ParseException("Hour must be between 1 and 12 if AM/PM is specified", element.index);
				} else if (pm) {
					if (hour != 12)
						hour += 11;
				} else if (hour == 12)
					hour = 0;
				element.value = hour;
			}

			element = firstInfo.get(DateElementType.Year);
			if (element != null && element.text.length() >= 4) {
				Calendar cal = CALENDAR.get();
				cal.clear();
				cal.setTimeZone(timeZone);

				cal.set(Calendar.YEAR, validate(DateElementType.Year, element));

				DateElementType minType = DateElementType.Year;
				element = firstInfo.get(DateElementType.Month);
				if (element != null)
					cal.set(Calendar.MONTH, validate(minType = DateElementType.Month, element));
				element = firstInfo.get(DateElementType.Day);
				if (element != null)
					cal.set(Calendar.DAY_OF_MONTH, validate(minType = DateElementType.Day, element));
				element = firstInfo.get(DateElementType.Hour);
				if (element != null)
					cal.set(Calendar.HOUR_OF_DAY, validate(minType = DateElementType.Hour, element));
				element = firstInfo.get(DateElementType.Minute);
				if (element != null)
					cal.set(Calendar.MINUTE, validate(minType = DateElementType.Minute, element));
				element = firstInfo.get(DateElementType.Second);
				if (element != null)
					cal.set(Calendar.SECOND, validate(minType = DateElementType.Second, element));
				int nanos;
				element = firstInfo.get(DateElementType.SubSecond);
				if (element != null)
					nanos = validate(minType = DateElementType.SubSecond, element);
				else
					nanos = 0;
				Instant time = Instant.ofEpochSecond(cal.getTimeInMillis() / 1000, nanos);
				return new AbsoluteTime(text, time, cal, timeZone, minType, element == null ? 0 : element.text.length(),
					toComponents(firstInfo));
			} else {
				for (Map.Entry<DateElementType, ParsedDateElement> entry : firstInfo.entrySet())
					validate(entry.getKey(), entry.getValue());
				return new RelativeTime(text, timeZone, toComponents(firstInfo));
			}
		} catch (ParseException e) {
			if (!throwIfNotFound)
				return null;
			throw e;
		}
	}

	private static Map<DateElementType, TimeComponent> toComponents(EnumMap<DateElementType, ParsedDateElement> fields) {
		EnumMap<DateElementType, TimeComponent> components = new EnumMap<>(DateElementType.class);
		for (Map.Entry<DateElementType, ParsedDateElement> entry : fields.entrySet()) {
			components.put(entry.getKey(), new TimeComponent(entry.getKey(), entry.getValue()));
		}
		return components;
	}

	private static int validate(DateElementType type, ParsedDateElement element) throws ParseException {
		int value = element.type.parse(element.text);
		switch (type) {
		case Year:
		case AmPm:
		case TimeZone:
			break;
		case Month:
			if (value < 0 || value > 11)
				throw new ParseException("Unrecognized month " + element.text, element.index);
			break;
		case Day:
			if (value < 1 || value > 31)
				throw new ParseException("Unrecognized day " + element.text, element.index);
			break;
		case Hour:
			if (value < 0 || value > 23)
				throw new ParseException("Unrecognized hour " + element.text, element.index);
			break;
		case Minute:
		case Second:
			if (value < 0 || value > 59)
				throw new ParseException("Unrecognized " + type + " " + element.text, element.index);
			break;
		case SubSecond:
			for (int dig = 10; dig > element.text.length(); dig--)
				value *= 10;
			break;
		}
		element.value = value;
		return value;
	}

	/** A thread-local calendar. Calendars are rather heavy objects but are thread-unsafe. */
	public static final ThreadLocal<Calendar> CALENDAR = ThreadLocal.withInitial(Calendar::getInstance);

	private static final String[] MONTHS = new String[] { "january", "february", "march", "april", "may", "june", "july", "august",
		"september", "october", "november", "december" };

	private enum ParsedDateElementType {
		DIGIT {
			@Override
			int find(CharSequence seq, int start) {
				while (start < seq.length()) {
					char ch = seq.charAt(start);
					if (ch < '0' || ch > '9')
						break;
					start++;
				}
				return start;
			}

			@Override
			int parse(CharSequence match) {
				return Integer.parseInt(match.toString());
			}
		},
		MONTH {
			@Override
			int find(CharSequence seq, int start) {
				for (String month : MONTHS) {
					int i;
					for (i = 0; i + start < seq.length() && i < month.length(); i++) {
						if (Character.toLowerCase(seq.charAt(start + i)) != month.charAt(i))
							break;
					}
					if (i >= 3)
						return start + i;
				}
				return start;
			}

			@Override
			int parse(CharSequence match) {
				for (int m = 0; m < MONTHS.length; m++) {
					int i;
					for (i = 0; i < match.length() && Character.toLowerCase(match.charAt(i)) == MONTHS[m].charAt(i); i++) {}
					if (i == match.length())
						return m;
				}
				throw new IllegalStateException();
			}
		},
		AM_PM {
			@Override
			int find(CharSequence seq, int start) {
				int c = start;
				if (seq.length() - start < 2)
					return start;
				char ch = seq.charAt(c);
				if (ch != 'a' && ch != 'A' && ch != 'p' && ch != 'P')
					return start;
				ch = seq.charAt(++c);
				if (ch == '.') {
					if (seq.length() - start < 3)
						return start;
					ch = seq.charAt(++c);
				}
				if (ch != 'm' && ch != 'M')
					return start;
				c++;
				if (seq.length() - start > c && seq.charAt(c) == '.')
					c++;
				return c;
			}

			@Override
			int parse(CharSequence match) {
				return Character.toLowerCase(match.charAt(0)) == 'p' ? 1 : 0;
			}
		},
		SEP {
			@Override
			int find(CharSequence seq, int start) {
				if (seq.length() == start)
					return start;
				char ch = seq.charAt(start);
				if (ch == ' ') {
					int end = start + 1;
					while (end < seq.length() && seq.charAt(end) == ' ')
						end++;
					return end;
				}
				if (ch == ':' || ch == '.' || ch == '/' || ch == '-' || ch == '.' || ch == 'T')
					return start + 1;
				else
					return start;
			}

			@Override
			int parse(CharSequence match) {
				return -1;
			}
		},
		CHARS {
			@Override
			int find(CharSequence seq, int start) {
				while (start < seq.length() && Character.isAlphabetic(start))
					start++;
				return start;
			}

			@Override
			int parse(CharSequence match) {
				return -1;
			}
		};

		abstract int find(CharSequence seq, int start);

		abstract int parse(CharSequence match);
	}

	private static class ParsedDateElement {
		final ParsedDateElementType type;
		final int index;
		final CharSequence text;
		int value;

		ParsedDateElement(ParsedDateElementType type, int index, CharSequence text) {
			this.type = type;
			this.index = index;
			this.text = text;
		}

		ParsedDateElement setValue(int value) {
			this.value = value;
			return this;
		}

		@Override
		public String toString() {
			return text.toString();
		}
	}

	private static class DateFormatComponent {
		final DateElementType dateType;
		final ParsedDateElementType parsedType;
		final Predicate<CharSequence> filter;
		final boolean required;

		DateFormatComponent(DateElementType dateType, ParsedDateElementType parsedType, Predicate<CharSequence> filter, boolean required) {
			this.dateType = dateType;
			this.parsedType = parsedType;
			this.filter = filter;
			this.required = required;
		}

		boolean matches(ParsedDateElement element) {
			if (parsedType != element.type)
				return false;
			return filter.test(element.text);
		}

		@Override
		public String toString() {
			if (dateType != null)
				return dateType.toString();
			else
				return parsedType.toString();
		}
	}

	private static class DateFormat {
		final List<DateFormatComponent> components;
		final Map<DateElementType, DateFormatComponent> componentsByType;

		DateFormat(DateFormatComponent... components) {
			this.components = Arrays.asList(components);
			componentsByType = new EnumMap<>(DateElementType.class);
			for (DateFormatComponent c : components) {
				if (c.dateType != null)
					componentsByType.put(c.dateType, c);
			}
		}

		int match(Map<DateElementType, ParsedDateElement> info, List<ParsedDateElement> elements) {
			int i = 0, j = 0;
			while (i < elements.size() && j < components.size()) {
				DateFormatComponent component = components.get(j);
				ParsedDateElement element = elements.get(i);
				if (component.matches(element)) {
					if (component.dateType != null)
						info.put(component.dateType, element);
					i++;
				} else if (component.required)
					return 0;
				j++;
			}
			while (j < components.size())
				if (components.get(j++).required)
					return 0;
			return i;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			for (DateFormatComponent component : components) {
				if (component.dateType != null)
					str.append(component.dateType);
				else
					str.append(component.parsedType);
			}
			return str.toString();
		}
	}

	// public static final String FLEX_FORMAT_DESCRIP;

	private static final List<DateFormat> FLEX_FORMATS;
	private static final QuickMap<String, TimeZone> TIME_ZONES;

	static {
		Predicate<CharSequence> oneOrTwoDigits = str -> str.length() == 1 || str.length() == 2;
		Predicate<CharSequence> twoDigits = str -> str.length() == 2;
		Predicate<CharSequence> threeDigits = str -> str.length() == 3;
		Predicate<CharSequence> fourDigits = str -> str.length() == 4;
		DateFormatComponent year2 = new DateFormatComponent(DateElementType.Year, ParsedDateElementType.DIGIT, twoDigits, true);
		DateFormatComponent year4 = new DateFormatComponent(DateElementType.Year, ParsedDateElementType.DIGIT, fourDigits, true);
		DateFormatComponent monthDig = new DateFormatComponent(DateElementType.Month, ParsedDateElementType.DIGIT, oneOrTwoDigits, true);
		DateFormatComponent monthCh = new DateFormatComponent(DateElementType.Month, ParsedDateElementType.MONTH,
			str -> Character.isAlphabetic(str.charAt(0)), true);
		DateFormatComponent day = new DateFormatComponent(DateElementType.Day, ParsedDateElementType.DIGIT, oneOrTwoDigits, true);
		DateFormatComponent hour = new DateFormatComponent(DateElementType.Hour, ParsedDateElementType.DIGIT, oneOrTwoDigits, true);
		DateFormatComponent minute = new DateFormatComponent(DateElementType.Minute, ParsedDateElementType.DIGIT, twoDigits, true);
		DateFormatComponent second = new DateFormatComponent(DateElementType.Second, ParsedDateElementType.DIGIT, twoDigits, true);
		DateFormatComponent subSecond = new DateFormatComponent(DateElementType.SubSecond, ParsedDateElementType.DIGIT, str -> true, false);
		DateFormatComponent ampm = new DateFormatComponent(DateElementType.AmPm, ParsedDateElementType.AM_PM, str -> true, false);
		DateFormatComponent timeZone = new DateFormatComponent(DateElementType.TimeZone, ParsedDateElementType.CHARS, threeDigits, false);
		DateFormatComponent stndrdth = new DateFormatComponent(null, ParsedDateElementType.CHARS, str -> {
			if (str.length() != 2)
				return false;
			String suffix = str.toString().toLowerCase();
			return suffix.equals("st") || suffix.equals("nd") || suffix.equals("rd") || suffix.equals("th");
		}, false);

		DateFormatComponent stdDateSep = new DateFormatComponent(null, ParsedDateElementType.SEP, str -> {
			char c = str.charAt(0);
			return c == '-' || c == '/' || c == '.';
		}, true);
		DateFormatComponent nonStdDateSep = new DateFormatComponent(null, ParsedDateElementType.SEP, str -> {
			char c = str.charAt(0);
			return c == '-' || c == '/';
		}, true);
		DateFormatComponent optionalonStdDateSep = new DateFormatComponent(null, ParsedDateElementType.SEP, str -> {
			char c = str.charAt(0);
			return c == '-' || c == '/';
		}, false);
		DateFormatComponent dot = new DateFormatComponent(null, ParsedDateElementType.SEP, str -> str.charAt(0) == '.', true);
		DateFormatComponent colon = new DateFormatComponent(null, ParsedDateElementType.SEP, str -> str.charAt(0) == ':', false);

		List<DateFormat> formats = new ArrayList<>();
		formats.addAll(Arrays.asList(//
			// Date formats
			new DateFormat(year4, stdDateSep, monthDig, stdDateSep, day, stndrdth, timeZone), //
			new DateFormat(year4, stdDateSep, monthDig, timeZone), //
			new DateFormat(year4, timeZone), //
			new DateFormat(monthCh, optionalonStdDateSep, year4, timeZone), //
			new DateFormat(day, optionalonStdDateSep, monthCh, optionalonStdDateSep, year4, timeZone), //
			new DateFormat(day, optionalonStdDateSep, monthCh, optionalonStdDateSep, year2, timeZone), //
			new DateFormat(day, optionalonStdDateSep, monthCh, timeZone), //
			new DateFormat(monthDig, nonStdDateSep, day, stndrdth, nonStdDateSep, year4, timeZone), //
			new DateFormat(monthDig, nonStdDateSep, day, stndrdth, nonStdDateSep, year2, timeZone), //
			new DateFormat(monthDig, nonStdDateSep, day, stndrdth, timeZone), //
			new DateFormat(monthDig, dot, year4, timeZone), //
			new DateFormat(day, stndrdth, dot, monthDig, dot, year4, timeZone), //
			new DateFormat(day, stndrdth, dot, monthDig, dot, year2, timeZone), //
			new DateFormat(day, stndrdth, dot, monthDig, timeZone), //
			new DateFormat(monthCh, optionalonStdDateSep, day, timeZone), //
			// Time formats
			new DateFormat(hour, colon, minute, colon, second, dot, subSecond, ampm, timeZone), //
			new DateFormat(hour, colon, minute, colon, second, ampm, timeZone), //
			new DateFormat(hour, colon, minute, ampm, timeZone), //
			new DateFormat(hour, ampm, timeZone)//
		));
		FLEX_FORMATS = Collections.unmodifiableList(formats);

		TIME_ZONES = QuickSet.of(String::compareToIgnoreCase, TimeZone.getAvailableIDs()).createMap();
	}

	private static final String DAY_FORMAT = "ddMMMyyyy";

	/**
	 * @param time The time
	 * @param zone The time zone (may be null, will be formatted as GMT)
	 * @param maxResolution The finest-grained date element to print
	 * @param militaryTime Whether to use military or AM/PM type time
	 * @return An {@link AbsoluteTime} whose minimum resolution may be days, hours, minutes, seconds, or sub-second
	 */
	public static ParsedTime asFlexTime(Instant time, TimeZone zone, DateElementType maxResolution, boolean militaryTime) {
		return asFlexTime(time, zone, DAY_FORMAT, maxResolution, militaryTime);
	}

	private static final String[] DAYS = new String[] { "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" };
	private static final String[] DAYS_ABBREV = new String[] { "Sun", "Mon", "Tues", "Wed", "Thurs", "Fri", "Sat" };

	/**
	 * @param time The time
	 * @param zone The time zone (may be null, will be formatted as GMT)
	 * @param dayFormat The date format to use to format the year, month, and day
	 * @param maxResolution The finest-grained date element to print
	 * @param militaryTime Whether to use military or AM/PM type time
	 * @return A parsed time whose minimum resolution may be days, hours, minutes, seconds, or sub-second
	 */
	public static ParsedTime asFlexTime(Instant time, TimeZone zone, String dayFormat, DateElementType maxResolution,
		boolean militaryTime) {
		EnumMap<DateElementType, ParsedDateElement> elements = new EnumMap<>(DateElementType.class);
		StringBuilder str = new StringBuilder();
		Calendar cal = TimeUtils.CALENDAR.get();
		cal.setTimeZone(zone == null ? TimeZone.getDefault() : zone);
		cal.setTimeInMillis(time.toEpochMilli());

		boolean foundDay = false, foundMonth = false, foundYear = false;
		{
			char type = 0;
			int start = 0;
			for (int i = 0; i < dayFormat.length(); i++) {
				char newType = dayFormat.charAt(i);
				switch (newType) {
				case 'd':
					foundDay = true;
					break;
				case 'M':
				case 'L':
					newType = 'M';
					foundMonth = true;
					break;
				case 'y':
				case 'Y':
					foundYear = true;
					break;
				case 'E':
					break;
				default:
					newType = 1;
					break;
				}
				if (start > 0 && type != newType) {
					int strStart = str.length();
					switch (type) {
					case 'd':
						if (i - start == 1)
							str.append(cal.get(Calendar.DAY_OF_MONTH));
						else
							StringUtils.printInt(cal.get(Calendar.DAY_OF_MONTH), i - start, str);
						elements.put(DateElementType.Day,
							new ParsedDateElement(ParsedDateElementType.DIGIT, strStart, str.substring(strStart))
								.setValue(cal.get(Calendar.DAY_OF_MONTH)));
						break;
					case 'M':
						if (i - start == 2) {
							StringUtils.printInt(cal.get(Calendar.DAY_OF_MONTH), 2, str);
							elements.put(DateElementType.Month,
								new ParsedDateElement(ParsedDateElementType.DIGIT, strStart, str.substring(strStart)));
						} else {
							String mo = capitalize(MONTHS[cal.get(Calendar.MONTH) - Calendar.JANUARY]);
							if (i - start > 1 && mo.length() > i - start)
								mo = mo.substring(0, i - start);
							str.append(mo);
							elements.put(DateElementType.Month,
								new ParsedDateElement(ParsedDateElementType.MONTH, strStart, mo).setValue(cal.get(Calendar.MONTH)));
						}
						break;
					case 'y':
						if (i - start == 1)
							str.append(cal.get(Calendar.YEAR));
						else
							StringUtils.printInt(cal.get(Calendar.YEAR), i - start, str);
						elements.put(DateElementType.Year,
							new ParsedDateElement(ParsedDateElementType.DIGIT, strStart, str.substring(strStart))
								.setValue(cal.get(Calendar.YEAR)));
						break;
					case 'E':
						if (i - start == 3)
							str.append(DAYS_ABBREV[cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY]);
						else {
							String day = DAYS[cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY];
							if (i - start > 1 && day.length() > i - start)
								day = day.substring(0, i - start);
							str.append(day);
						}
						// Nothing to add to elements, day-of-week is not a field type here
						break;
					default:
						str.append(dayFormat.substring(start, i));
						break;
					}

					type = newType;
					start = i;
				}
			}
			str.append(new SimpleDateFormat(dayFormat).format(cal.getTime()));
		}
		if (foundYear && (!foundMonth || !foundDay)) {
			throw new IllegalArgumentException("Cannot specify year without month and day");
		} else if (foundMonth && !foundDay)
			throw new IllegalArgumentException("Cannot specify month without day");
		DateElementType resolution;
		int nanoDigits = 0;
		if (time.getNano() != 0) {
			resolution = maxResolution == null ? DateElementType.SubSecond : maxResolution;
		} else if (time.getEpochSecond() % 60 != 0) {
			resolution = DateElementType.Second;
		} else if (time.getEpochSecond() % DAY.getSeconds() != 0) {
			resolution = DateElementType.Minute;
		} else
			resolution = DateElementType.Day;
		if (resolution.compareTo(DateElementType.Hour) >= 0) {
			str.append(' ');
			int index = str.length();
			int hour = cal.get(Calendar.HOUR_OF_DAY);
			boolean am = hour < 12;
			if (militaryTime)
				StringUtils.printInt(hour, 2, str);
			else {
				if (hour >= 12)
					hour -= 12;
				if (hour == 0)
					str.append("12");
				else
					str.append(hour);
			}
			elements.put(DateElementType.Hour, new ParsedDateElement(ParsedDateElementType.DIGIT, str.length(), str.substring(index))
				.setValue(cal.get(Calendar.HOUR_OF_DAY)));
			if (resolution.compareTo(DateElementType.Minute) >= 0) {
				str.append(':');
				index = str.length();
				StringUtils.printInt(cal.get(Calendar.MINUTE), 2, str);
				elements.put(DateElementType.Minute, new ParsedDateElement(ParsedDateElementType.DIGIT, str.length(), str.substring(index))
					.setValue(cal.get(Calendar.MINUTE)));
				if (resolution.compareTo(DateElementType.Second) >= 0) {
					str.append(':');
					index = str.length();
					StringUtils.printInt(cal.get(Calendar.SECOND), 2, str);
					elements.put(DateElementType.Second,
						new ParsedDateElement(ParsedDateElementType.DIGIT, str.length(), str.substring(index))
							.setValue(cal.get(Calendar.SECOND)));
					if (resolution.compareTo(DateElementType.SubSecond) >= 0) {
						str.append('.');
						nanoDigits = 9;
						index = str.length();
						StringUtils.printInt(time.getNano(), nanoDigits, str);
						while (str.charAt(str.length() - 1) == '0') {
							nanoDigits--;
							str.setLength(str.length() - 1);
						}
						elements.put(DateElementType.SubSecond,
							new ParsedDateElement(ParsedDateElementType.DIGIT, str.length(), str.substring(index))
								.setValue(time.getNano()));
					}
				}
			}
			if (!militaryTime)
				str.append(am ? "am" : "pm");
		}
		if (foundYear)
			return new AbsoluteTime(str.toString(), time, cal, zone, resolution, nanoDigits, toComponents(elements));
		else
			return new RelativeTime(str.toString(), zone, toComponents(elements));
	}

	private static String capitalize(String str) {
		StringBuilder ret = new StringBuilder(str);
		ret.setCharAt(0, Character.toUpperCase(str.charAt(0)));
		return ret.toString();
	}

	/**
	 * @param text The text to parse
	 * @return A duration parsed from the text
	 * @throws ParseException If the duration cannot be parsed
	 */
	public static ParsedDuration parseDuration(CharSequence text) throws ParseException {
		ArrayList<DurationComponent> components = new ArrayList<>();
		ArrayList<String> separators = new ArrayList<>();
		Duration duration = Duration.ZERO;
		int c = 0;
		StringBuilder spacer = new StringBuilder();
		while (c < text.length() && Character.isWhitespace(text.charAt(c))) {
			spacer.append(text.charAt(c));
			c++;
		}

		boolean neg = c < text.length() && text.charAt(c) == '-';
		if (neg)
			c++;
		StringBuilder unit = new StringBuilder();
		boolean hadContent = false;
		while (true) {
			while (c < text.length() && Character.isWhitespace(text.charAt(c))) {
				spacer.append(text.charAt(c));
				c++;
			}
			separators.add(spacer.toString());
			spacer.setLength(0);
			if (c == text.length())
				break;
			hadContent = true;
			int valueStart = c;
			long value = 0;
			while (c < text.length() && text.charAt(c) >= '0' && text.charAt(c) <= '9') {
				if (c - valueStart > 10)
					throw new ParseException("Too many digits in value", c);
				value = value * 10 + (text.charAt(c) - '0');
				c++;
			}
			if (value > Integer.MAX_VALUE)
				throw new ParseException("Too many digits in value", c);
			int decimalStart = c;
			double decimal = Double.NaN;
			if (c < text.length() && text.charAt(c) == '.') {
				decimal = 0;
				c++;
				double place = 0.1;
				while (c < text.length() && text.charAt(c) >= '0' && text.charAt(c) <= '9') {
					if (c - decimalStart < 9) {
						// Only ns precision is supported. Ignore remaining digits.
						decimal = decimal + (text.charAt(c) - '0') * place;
						place /= 10;
					}
					c++;
				}
				if (c == decimalStart)
					throw new ParseException("Unrecognized duration", 0);
			}
			while (c < text.length() && Character.isWhitespace(text.charAt(c)))
				c++;

			int unitStart = c;
			unit.setLength(0);
			while (c < text.length() && Character.isAlphabetic(text.charAt(c))) {
				unit.append(text.charAt(c));
				c++;
			}
			if (unit.length() == 0)
				throw new ParseException("Unit expected", unitStart);
			if (unit.length() > 2 && unit.charAt(unit.length() - 1) == 's')
				unit.deleteCharAt(unit.length() - 1); // Remove the plural
			String unitStr = unit.toString().toLowerCase();
			if (!Double.isNaN(decimal)) {
				switch (unitStr) {
				case "s":
				case "sec":
				case "second":
				case "ms":
				case "milli":
				case "millisecond":
					break;
				default:
					throw new ParseException("Decimal values are only permitted for unit 'second' and 'millisecond'", decimalStart);
				}
			}
			switch (unit.toString()) {
			case "y":
			case "yr":
			case "year":
				components.add(new DurationComponent(valueStart, valueStart, decimalStart, DurationComponentType.Year, (int) value,
					text.subSequence(valueStart, c).toString()));
				break;
			case "mo":
			case "month":
				components.add(new DurationComponent(valueStart, valueStart, decimalStart, DurationComponentType.Month, (int) value,
					text.subSequence(valueStart, c).toString()));
				break;
			case "w":
			case "wk":
			case "week":
				components.add(new DurationComponent(valueStart, valueStart, decimalStart, DurationComponentType.Week, (int) value,
					text.subSequence(valueStart, c).toString()));
				break;
			case "d":
			case "dy":
			case "day":
				components.add(new DurationComponent(valueStart, valueStart, decimalStart, DurationComponentType.Day, (int) value,
					text.subSequence(valueStart, c).toString()));
				break;
			case "h":
			case "hr":
			case "hour":
				components.add(new DurationComponent(valueStart, valueStart, decimalStart, DurationComponentType.Hour, (int) value,
					text.subSequence(valueStart, c).toString()));
				break;
			case "m":
			case "min":
			case "minute":
				components.add(new DurationComponent(valueStart, valueStart, decimalStart, DurationComponentType.Minute, (int) value,
					text.subSequence(valueStart, c).toString()));
				break;
			case "s":
			case "sec":
			case "second":
				if (value > 0 || decimalStart > valueStart)
					components.add(new DurationComponent(valueStart, valueStart, decimalStart, DurationComponentType.Second, (int) value,
						text.subSequence(valueStart, decimalStart).toString()));
				if (!Double.isNaN(decimal)) {
					components.add(new DurationComponent(decimalStart, decimalStart, decimalStart + 1, DurationComponentType.Nanosecond,
						(int) (decimal * 1_000_000_000), text.subSequence(decimalStart, c).toString()));
				}
				break;
			case "ms":
			case "milli":
			case "millisecond":
				if (value > 0 || decimalStart > valueStart)
					components.add(new DurationComponent(valueStart, valueStart, decimalStart, DurationComponentType.Millisecond,
						(int) value, text.subSequence(valueStart, decimalStart).toString()));
				if (!Double.isNaN(decimal)) {
					components.add(new DurationComponent(decimalStart, decimalStart, decimalStart + 1, DurationComponentType.Nanosecond,
						(int) (decimal * 1_000_000), text.subSequence(decimalStart, c).toString()));
				}
				break;
			case "us":
			case "micro":
			case "microsecond":
				if (value > 0 || decimalStart > valueStart)
					components.add(new DurationComponent(valueStart, valueStart, decimalStart, DurationComponentType.Microsecond,
						(int) value, text.subSequence(valueStart, decimalStart).toString()));
				if (!Double.isNaN(decimal)) {
					components.add(new DurationComponent(decimalStart, decimalStart, decimalStart + 1, DurationComponentType.Nanosecond,
						(int) (decimal * 1_000), text.subSequence(decimalStart, c).toString()));
				}
				break;
			case "ns":
			case "nano":
			case "nanosecond":
				components.add(new DurationComponent(valueStart, valueStart, decimalStart, DurationComponentType.Minute, (int) value,
					text.subSequence(valueStart, c).toString()));
				break;
			default:
				throw new ParseException("Unrecognized unit: " + unitStr, unitStart);
			}
		}
		if (!hadContent)
			throw new ParseException("No content to parse", c);
		if (neg)
			duration = duration.negated();
		return new ParsedDuration(neg, components, separators);
	}

	/**
	 * @param years The number of years to convert
	 * @return The number of days in the given number of years
	 */
	public static final long getDaysInYears(int years) {
		boolean neg = years < 0;
		if (neg)
			years = -years;
		long days = years * 365L;
		if (years >= 4) {
			days += years / 4;
			if (years >= 100) {
				days -= years / 100;
				if (years >= 400) {
					days += years / 400;
				}
			}
		}
		return neg ? -days : days;
	}

	/**
	 * @param months The number of months to convert
	 * @return The number of days in the given number of months
	 */
	public static final long getDaysInMonths(int months) {
		boolean neg = months < 0;
		if (neg)
			months = -months;
		long days;
		boolean subMonthDays;
		if (months >= 12) {
			int years = months / 12;
			months %= 12;
			if (months >= 6) {
				years++;
				months = -6;
				subMonthDays = true;
			} else
				subMonthDays = false;
			days = getDaysInYears(years);
		} else {
			days = 0;
			subMonthDays = false;
		}
		int monthDays = months * 30;
		if (months >= 2) {
			monthDays += months / 2;
			if (months >= 10)
				monthDays--;
		}
		if (subMonthDays)
			days -= monthDays;
		else
			days += monthDays;
		return neg ? -days : days;
	}

	/**
	 * Same as {@link Duration#between(java.time.temporal.Temporal, java.time.temporal.Temporal)}, re-implemented for performance.
	 * 
	 * @param t1 The first time
	 * @param t2 The second time
	 * @return The duration between the two instants
	 */
	public static Duration between(Instant t1, Instant t2) {
		long seconds = t2.getEpochSecond() - t1.getEpochSecond();
		long nanos = t2.getNano() - t1.getNano();
		Duration d = Duration.ofSeconds(seconds, nanos);
		if (!d.equals(Duration.between(t1, t2))) {
			throw new IllegalStateException("Bad logic here");
		}
		return d;
	}

	/**
	 * @param d1 The first duration (numerator)
	 * @param d2 The second duration (denominator)
	 * @return The maximum number <code>n</code> such that <code>d2*n<=d1</code>. The result will be negative IFF d1 XOR d2 is negative.
	 */
	public static int divide(Duration d1, Duration d2) {
		if (d2.isZero()) {
			throw new ArithmeticException("Zero duration divisor");
		}
		boolean neg;
		if (d1.isNegative()) {
			d1 = negate(d1);
			neg = !d2.isNegative();
			if (!neg) {
				d2 = negate(d2);
			}
		} else if (d2.isNegative()) {
			neg = true;
			d2 = negate(d2);
		} else {
			neg = false;
		}
		int divNano = d2.getNano();
		if (divNano == 0) {
			return applyNeg((int) (d1.getSeconds() / d2.getSeconds()), neg);
		}

		int comp = d1.compareTo(d2);
		if (comp < 0) {
			return 0;
		} else if (comp == 0) {
			return applyNeg(1, neg);
		}

		long numSecs = d1.getSeconds();
		if (numSecs == 0) {
			return d1.getNano() / divNano; // We already know d2.seconds is zero because we tested above for d2>d1
		}

		long divSecs = d2.getSeconds();
		if (divSecs > 0x1_0000_0000L) {
			// We can just use the nanos to round the seconds of the divisor, then divide the seconds,
			// as the nanos can't affect the result more significantly than that
			if (d1.getNano() >= 500_000_000) {
				numSecs++;
			}
			if (divNano >= 500_000_000) {
				divSecs++;
			}
			long res = numSecs / divSecs;
			if (Long.numberOfLeadingZeros(res) < 33) {
				return neg ? Integer.MIN_VALUE : Integer.MAX_VALUE;
			}
			return (int) res;
		} else if (numSecs > 10_0000_0000L) {
			return neg ? Integer.MIN_VALUE : Integer.MAX_VALUE;
		}

		// (d1.seconds*1E9+d1.nanos)/(d2.seconds*1E9+d2.nanos)
		long numerator = numSecs * 1_000_000_000;
		if (numerator < 0) {
			// Overflow
			return neg ? Integer.MIN_VALUE : Integer.MAX_VALUE;
		}
		if (d1.getNano() != 0) {
			numerator += d1.getNano();
		}
		long denominator = divSecs;
		if (denominator > 0) {
			denominator *= 1_000_000_000;
		}
		denominator += divNano;
		long res = numerator / denominator;
		if (res > Integer.MAX_VALUE) {
			return neg ? Integer.MIN_VALUE : Integer.MAX_VALUE;
		}
		return applyNeg((int) res, neg);
	}

	private static int applyNeg(int v, boolean neg) {
		return neg ? -v : v;
	}

	/**
	 * @param d The duration to get the number of seconds within
	 * @return The number of seconds in the duration (as a double)
	 */
	public static double toSeconds(Duration d) {
		boolean neg = d.isNegative();
		if (neg) {
			d = d.negated();
		}
		double secs = d.getSeconds();
		int nanos = d.getNano();
		if (nanos != 0) {
			secs += nanos / 1E9;
		}
		return secs;
	}

	/**
	 * Same as <code>{@link Duration#dividedBy(long) Duration.dividedBy}(2)</code>, implemented for performance
	 * 
	 * @param d The duration to halve
	 * @return The given duration divided by two
	 */
	public static Duration div2(Duration d) {
		long seconds = d.getSeconds();
		int nanos = d.getNano();
		boolean neg = seconds < 0;
		boolean odd = (seconds & 1) != 0;
		seconds = seconds >> 1;
		nanos = nanos >> 1;
		if (odd) {
			if (neg) {
				nanos -= 500_000_000;
			} else {
				nanos += 500_000_000;
			}
		}
		return Duration.ofSeconds(seconds, nanos);
	}

	/**
	 * Same as {@link Duration#negated()}, implemented for performance
	 * 
	 * @param d The duration to negate
	 * @return <code>-d</code>
	 */
	public static Duration negate(Duration d) {
		Duration neg = Duration.ofSeconds(-d.getSeconds(), -d.getNano());
		return neg;
	}
}

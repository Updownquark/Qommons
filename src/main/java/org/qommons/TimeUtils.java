package org.qommons;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Function;
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
		/** The month, represented by digits or a full or partial name, starting at 0 */
		Month,
		/** The day of the month, starting at 1 */
		Day,
		/** The day of the week, starting at 0=Sunday */
		Weekday,
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
		Year(ChronoUnit.YEARS),
		/** Months */
		Month(ChronoUnit.MONTHS),
		/** Weeks */
		Week(ChronoUnit.WEEKS),
		/** Days */
		Day(ChronoUnit.DAYS),
		/** Hours */
		Hour(ChronoUnit.HOURS),
		/** Minutes */
		Minute(ChronoUnit.MINUTES),
		/** Seconds */
		Second(ChronoUnit.SECONDS),
		/** Milliseconds */
		Millisecond(ChronoUnit.MILLIS),
		/** Microseconds */
		Microsecond(ChronoUnit.MICROS),
		/** Nanoseconds */
		Nanosecond(ChronoUnit.NANOS);

		/** The {@link ChronoUnit} corresponding to this duration component */
		public final ChronoUnit unit;

		private DurationComponentType(ChronoUnit unit) {
			this.unit = unit;
		}
	}

	/** A parsed time returned from {@link TimeUtils#parseFlexFormatTime(CharSequence, boolean, boolean, Function)} */
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

		/** @return 0 if this duration is empty, -1 if it is negative, or 1 if it is positive */
		public int signum() {
			for (DurationComponent comp : theSequence)
				if (comp.getValue() != 0)
					return isNegative ? -1 : 1;
			return 0;
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

		/**
		 * @param multiple The multiple to multiply this duration by
		 * @return A new duration that is equal to this duration times the given multiple
		 */
		public ParsedDuration times(int multiple) {
			if (multiple == 1)
				return this;
			boolean neg = isNegative;
			if (multiple < 0) {
				neg = !neg;
				multiple = -multiple;
			}

			if (multiple == 1)
				return new ParsedDuration(neg, theSequence, theSeparators);
			List<DurationComponent> newComponents = new ArrayList<>(theSequence.size());
			int start = 0;
			for (int i = 0; i < theSequence.size(); i++) {
				DurationComponent comp = theSequence.get(i);
				int newVal = comp.getValue() * multiple;
				if (newVal < 0)
					throw new IllegalArgumentException("Overflow for " + comp + "x" + multiple);
				int valueStart = comp.valueStart + start - comp.getStart();
				int valueEnd = valueStart + StringUtils.getDigits(newVal);
				String newText = new StringBuilder(comp.toString().substring(0, comp.valueStart - comp.getStart()))//
					.append(newVal).append(comp.toString().substring(comp.valueEnd - comp.getStart())).toString();
				newComponents.add(new DurationComponent(start, valueStart, valueEnd, comp.getField(), newVal, newText));
			}
			return new ParsedDuration(neg, Collections.unmodifiableList(newComponents), theSeparators);
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

		/**
		 * @param time The reference time
		 * @param timeZone The time zone to use for the evaluation
		 * @return The reference time plus this duration
		 */
		public Instant addTo(Instant time, TimeZone timeZone) {
			Calendar cal = Calendar.getInstance();
			cal.setTimeZone(timeZone);
			cal.setTimeInMillis(time.getEpochSecond() * 1000);
			int mult = isNegative ? -1 : 1;
			long nanos = time.getNano() * mult;
			for (Map.Entry<DurationComponentType, DurationComponent> component : components.entrySet()) {
				switch (component.getKey()) {
				case Year:
					cal.add(Calendar.YEAR, component.getValue().getValue() * mult);
					break;
				case Month:
					cal.add(Calendar.MONTH, component.getValue().getValue() * mult);
					break;
				case Week:
					cal.add(Calendar.DAY_OF_YEAR, component.getValue().getValue() * 7 * mult);
					break;
				case Day:
					cal.add(Calendar.DAY_OF_YEAR, component.getValue().getValue() * mult);
					break;
				case Hour:
					cal.add(Calendar.HOUR_OF_DAY, component.getValue().getValue() * mult);
					break;
				case Minute:
					cal.add(Calendar.MINUTE, component.getValue().getValue() * mult);
					break;
				case Second:
					cal.add(Calendar.SECOND, component.getValue().getValue() * mult);
					break;
				case Millisecond:
					nanos += component.getValue().getValue() * 1_000_000L * mult;
					break;
				case Microsecond:
					nanos += component.getValue().getValue() * 1_000L * mult;
					break;
				case Nanosecond:
					nanos += component.getValue().getValue() * mult;
					break;
				}
			}
			return Instant.ofEpochSecond(cal.getTimeInMillis() / 1000, nanos);
		}

		@Override
		public int compareTo(ParsedDuration other) {
			for (DurationComponentType type : DurationComponentType.values()) {
				DurationComponent thisC = components.get(type);
				DurationComponent otherC = other.components.get(type);
				if (thisC == null || thisC.getValue() == 0) {
					if (otherC == null || otherC.getValue() == 0) {
						continue;
					} else {
						return -1;
					}
				} else if (otherC == null || otherC.getValue() == 0) {
					return 1;
				} else {
					int comp = Integer.compare(thisC.getValue(), otherC.getValue());
					if (comp != 0)
						return comp;
				}
			}
			return 0;
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
			if (nanos == 0) {
			} else if (nanos % 1_000_000 == 0) {
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
				case Weekday:
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

				if (newEl != null && newEl != component.theParsedElement) {
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
			case DAY_NAME:
				if (element.text.toString().toLowerCase().equals(DAYS[element.value].toLowerCase()))
					newText = DAYS[newValue];
				else
					newText = DAYS_ABBREV[newValue];
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
	 * A time parsed from {@link TimeUtils#parseFlexFormatTime(CharSequence, boolean, boolean, Function)} for which the year is specified
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
			case Weekday:
				break; // Week day is ignored
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
			case Weekday:
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
				case Weekday:
					return cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
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

	/** Strategies for evaluating relative times */
	public enum RelativeTimeEvaluation {
		/** Chooses the closest time matching the relative time <b>before</b> the reference time */
		PAST,
		/** Chooses the closest time matching the relative time <b>after</b> the reference time */
		FUTURE,
		/** Chooses the closest time matching the relative time either before or after the reference time */
		CLOSEST
	}

	/**
	 * A time parsed from {@link TimeUtils#parseFlexFormatTime(CharSequence, boolean, boolean, Function)} where the year was not specified
	 * with 4 digits, meaning that this time can represent any number of time ranges depending on a reference point.
	 */
	public static class RelativeTime extends ParsedTimeImpl {
		private final int nanoResolution;
		private final int nanoDigits;
		private final boolean is24Hour;
		private final RelativeTimeEvaluation theEvaluationType;

		RelativeTime(String text, TimeZone timeZone, Map<DateElementType, TimeComponent> elements, boolean is24Hour,
			RelativeTimeEvaluation evaluationType) {
			super(text, timeZone, elements);
			this.is24Hour = is24Hour;
			theEvaluationType = evaluationType;
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

		/** @return The relative time evaluation strategy to use for {@link #evaluate(Supplier) evaluation} */
		public RelativeTimeEvaluation getEvaluationType() {
			return theEvaluationType;
		}

		@Override
		public Instant evaluate(Supplier<Instant> reference) {
			Calendar cal = CALENDAR.get();
			Instant ref = reference.get();
			cal.setTimeInMillis(ref.getEpochSecond() * 1000);
			cal.setTimeZone(getTimeZone());
			int nanos = ref.getNano();
			Calendar refCal = null;
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
				case Weekday:
					if (elements.containsKey(DateElementType.Day))
						break;
					if (refCal == null) {
						refCal = Calendar.getInstance();
						refCal.setTimeZone(getTimeZone());
						refCal.setTimeInMillis(ref.toEpochMilli());
					}
					int currentDay = refCal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
					int dayDiff = element.getValue().getValue() - currentDay;
					if (dayDiff < -3)
						dayDiff += 7;
					else if (dayDiff > 3)
						dayDiff -= 7;
					cal.add(Calendar.DAY_OF_MONTH, dayDiff);
					break;
				case Hour:
					TimeComponent comp = getField(DateElementType.AmPm);
					int hour = element.getValue().getValue();
					if (comp != null) {
						if (comp.getValue() == 0) {
							if (hour == 12)
								hour = 0;
						} else {
							if (hour != 12)
								hour += 12;
						}
					} else if (!is24Hour && hour <= 12 && element.getValue().toString().charAt(0) != '0') { // Assume AM/PM time
						if (refCal == null) {
							refCal = Calendar.getInstance();
							refCal.setTimeZone(getTimeZone());
							refCal.setTimeInMillis(ref.toEpochMilli());
						}
						int refHour = refCal.get(Calendar.HOUR_OF_DAY);
						int amHour = hour == 12 ? 0 : hour, pmHour = hour == 12 ? 12 : (hour + 12);
						if (Math.abs(pmHour - refHour) < Math.abs(amHour - refHour))
							hour += 12;
					}
					cal.set(Calendar.HOUR_OF_DAY, hour);
					if (!elements.containsKey(DateElementType.Minute)) {
						cal.set(Calendar.MINUTE, 0);
						cal.set(Calendar.SECOND, 0);
						nanos = 0;
					}
					break;
				case Minute:
					cal.set(Calendar.MINUTE, element.getValue().getValue());
					if (!elements.containsKey(DateElementType.Second)) {
						cal.set(Calendar.SECOND, 0);
						nanos = 0;
					}
					break;
				case Second:
					cal.set(Calendar.SECOND, element.getValue().getValue());
					if (!elements.containsKey(DateElementType.SubSecond))
						nanos = 0;
					break;
				case SubSecond:
					nanos = element.getValue().getValue();
					break;
				case AmPm:
				case TimeZone:
					break;
				}
			}
			boolean evaluationMatches = true;
			switch (theEvaluationType) {
			case PAST:
				evaluationMatches = cal.getTimeInMillis() <= ref.toEpochMilli();
				break;
			case FUTURE:
				evaluationMatches = cal.getTimeInMillis() >= ref.toEpochMilli();
				break;
			default:
				evaluationMatches = false; // Need to check
				break;
			}
			if (!evaluationMatches) { // Fix to match the evaluation type
				if (elements.containsKey(DateElementType.Year)) {
					// Again, if the year was absolute, it wouldn't be a relative time
					// Let's not back up or move forward by centuries--that's probably not the intent. Maybe I'll reconsider later
				} else if (elements.containsKey(DateElementType.Month)) {
					switch (theEvaluationType) {
					case PAST:
						cal.add(Calendar.YEAR, -1);
						break;
					case FUTURE:
						cal.add(Calendar.YEAR, 1);
						break;
					case CLOSEST:
						long diff = cal.getTimeInMillis() - ref.toEpochMilli();
						if (Math.abs(diff) > 365L * 24 * 60 * 60 * 1000 / 2)
							cal.add(Calendar.YEAR, diff < 0 ? 1 : -1);
						break;
					}
				} else if (elements.containsKey(DateElementType.Day)) {
					switch (theEvaluationType) {
					case PAST:
						cal.add(Calendar.MONTH, -1);
						break;
					case FUTURE:
						cal.add(Calendar.MONTH, 1);
						break;
					case CLOSEST:
						long diff = cal.getTimeInMillis() - ref.toEpochMilli();
						cal.add(Calendar.MONTH, diff < 0 ? 1 : -1);
						long postDiff = cal.getTimeInMillis() - ref.toEpochMilli();
						if (Math.abs(diff) <= Math.abs(postDiff))
							cal.add(Calendar.MONTH, diff < 0 ? -1 : 1);
						break;
					}
				} else if (elements.containsKey(DateElementType.Weekday)) {
					switch (theEvaluationType) {
					case PAST:
						cal.add(Calendar.DAY_OF_MONTH, -7);
						break;
					case FUTURE:
						cal.add(Calendar.DAY_OF_MONTH, 7);
						break;
					case CLOSEST:
						long diff = cal.getTimeInMillis() - ref.toEpochMilli();
						if (Math.abs(diff) > 7L * 24 * 60 * 60 * 1000 / 2)
							cal.add(Calendar.DAY_OF_MONTH, diff < 0 ? 7 : -7);
						break;
					}
				} else if (elements.containsKey(DateElementType.Hour)) {
					int threshHours;
					if (is24Hour || elements.containsKey(DateElementType.AmPm)) {
						threshHours = 24;
					} else {
						threshHours = 12;
					}
					switch (theEvaluationType) {
					case PAST:
						cal.add(Calendar.HOUR_OF_DAY, -threshHours);
						break;
					case FUTURE:
						cal.add(Calendar.HOUR_OF_DAY, threshHours);
						break;
					case CLOSEST:
						long diff = cal.getTimeInMillis() - ref.toEpochMilli();
						if (Math.abs(diff) > threshHours * 60L * 60 * 1000 / 2)
							cal.add(Calendar.HOUR_OF_DAY, diff < 0 ? threshHours : -threshHours);
						break;
					}
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
				case Weekday:
					if (getField(DateElementType.Day) == null
						&& (cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY) != element.getValue().getValue())
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
			if (field == DateElementType.Weekday && elements.containsKey(DateElementType.Day))
				return add(DateElementType.Day, amount);
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
			return new RelativeTime(str.toString(), getTimeZone(), newElements, is24Hour, theEvaluationType);
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
			case Weekday:
				newValue = newValue % 7;
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

	/** Default time evaluation options */
	public static final TimeEvaluationOptions DEFAULT_OPTIONS = new TimeEvaluationOptions(TimeZone.getDefault(), DateElementType.SubSecond,
		false, RelativeTimeEvaluation.CLOSEST);

	/** Different options that can be used when evaluating and parsing times */
	public static class TimeEvaluationOptions {
		private final TimeZone theTimeZone;
		private final DateElementType theMaxResolution;
		private final boolean is24HourFormat;
		private final RelativeTimeEvaluation theEvaluationType;

		private TimeEvaluationOptions(TimeZone timeZone, DateElementType resolution, boolean is24HourFormat,
			RelativeTimeEvaluation evaluationType) {
			theTimeZone = timeZone;
			theMaxResolution = resolution;
			this.is24HourFormat = is24HourFormat;
			theEvaluationType = evaluationType;
		}

		/** @return The time zone to parse/format in */
		public TimeZone getTimeZone() {
			return theTimeZone;
		}

		/** @return The maximum resolution to print times in */
		public DateElementType getMaxResolution() {
			return theMaxResolution;
		}

		/** @return Whether to print in 24-hour or 12-hour format */
		public boolean is24HourFormat() {
			return is24HourFormat;
		}

		/** @return The relative time evaluation strategy to use */
		public RelativeTimeEvaluation getEvaluationType() {
			return theEvaluationType;
		}

		/**
		 * @param timeZone The time zone to parse/format in
		 * @return A set of time evaluation options identical to this but with the given time zone
		 */
		public TimeEvaluationOptions withTimeZone(TimeZone timeZone) {
			if (timeZone.equals(theTimeZone))
				return this;
			return new TimeEvaluationOptions(timeZone, theMaxResolution, is24HourFormat, theEvaluationType);
		}

		/**
		 * @param resolution The maximum resolution to print times in
		 * @return A set of time evaluation options identical to this but with the given max resolution
		 */
		public TimeEvaluationOptions withMaxResolution(DateElementType resolution) {
			if (theMaxResolution == resolution)
				return this;
			return new TimeEvaluationOptions(theTimeZone, resolution, is24HourFormat, theEvaluationType);
		}

		/**
		 * @param twentyFourHourFormat Whether to print in 24- or 12-hour format
		 * @return A set of time evaluation options identical to this but with the given 24-hour format setting
		 */
		public TimeEvaluationOptions with24HourFormat(boolean twentyFourHourFormat) {
			if (is24HourFormat == twentyFourHourFormat)
				return this;
			return new TimeEvaluationOptions(theTimeZone, theMaxResolution, twentyFourHourFormat, theEvaluationType);
		}

		/**
		 * @param evalutionType The relative time evaluation strategy to use
		 * @return A set of time evaluation options identical to this but with the given relative time evaluation strategy
		 */
		public TimeEvaluationOptions withEvaluationType(RelativeTimeEvaluation evalutionType) {
			if (theEvaluationType == evalutionType)
				return this;
			return new TimeEvaluationOptions(theTimeZone, theMaxResolution, is24HourFormat, evalutionType);
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
	 * @param wholeText Whether the entire sequence should be parsed. If false, this will return a value if the beginning of the sequence is
	 *        understood.
	 * @param throwIfNotFound Whether to throw a {@link ParseException} or return null if a time cannot be parsed for any reason
	 * @param opts Configures options for parsing and for evaluating the parsed time
	 * @return A ParsedTime evaluated from the text
	 * @throws ParseException If <code>throwIfNotFound</code> and the string cannot be parsed
	 */
	public static ParsedTime parseFlexFormatTime(CharSequence str, boolean wholeText, boolean throwIfNotFound,
		Function<TimeEvaluationOptions, TimeEvaluationOptions> opts) throws ParseException {
		TimeEvaluationOptions options = DEFAULT_OPTIONS;
		if (opts != null)
			options = opts.apply(options);
		List<ParsedDateElement> elements = new ArrayList<>();
		int i = 0;
		boolean found = true;
		while (found && i < str.length()) {
			if (Character.isWhitespace(str.charAt(i)) || str.charAt(i) == ',') {
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
		TimeZone timeZone = options.getTimeZone();
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
			ParsedDateElement year = firstInfo.get(DateElementType.Year);
			element = firstInfo.get(DateElementType.AmPm);
			if (element != null && year != null && year.text.length() >= 4) {
				boolean pm = element.type.parse(element.text) > 0;
				element = firstInfo.get(DateElementType.Hour);
				int hour = validate(DateElementType.Hour, element);
				if (hour == 0 || hour > 12) {
					if (!throwIfNotFound)
						return null;
					throw new ParseException("Hour must be between 1 and 12 if AM/PM is specified", element.index);
				} else if (pm) {
					if (hour != 12)
						hour += 12;
				} else if (hour == 12)
					hour = 0;
				element.value = hour;
			}

			if (year != null && year.text.length() >= 4) {
				Calendar cal = CALENDAR.get();
				cal.clear();
				cal.setTimeZone(timeZone);

				cal.set(Calendar.YEAR, validate(DateElementType.Year, year));

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
				return new RelativeTime(text, timeZone, toComponents(firstInfo), options.is24HourFormat(), options.getEvaluationType());
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
		if (element.value >= 0)
			return element.value;
		int value = element.type.parse(element.text) + element.offset;
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
		case Weekday:
			if (value < 0 || value > 6)
				throw new ParseException("Unrecognized week day " + element.text, element.index);
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
					for (i = 0; i < match.length() && Character.toLowerCase(match.charAt(i)) == MONTHS[m].charAt(i); i++) {
					}
					if (i == match.length())
						return m;
				}
				throw new IllegalStateException();
			}
		},
		DAY_NAME {
			@Override
			int find(CharSequence seq, int start) {
				for (String day : DAYS) {
					int i;
					for (i = 0; i + start < seq.length() && i < day.length(); i++) {
						if (Character.toLowerCase(seq.charAt(start + i)) != Character.toLowerCase(day.charAt(i)))
							break;
					}
					if (i >= 3)
						return start + i;
				}
				return start;
			}

			@Override
			int parse(CharSequence match) {
				for (int d = 0; d < DAYS.length; d++) {
					int i;
					for (i = 0; i < match.length()
						&& Character.toLowerCase(match.charAt(i)) == Character.toLowerCase(DAYS[d].charAt(i)); i++) {
					}
					if (i == match.length())
						return d;
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
				if (ch == ',' || ch == ':' || ch == '.' || ch == '/' || ch == '-' || ch == '.' || ch == 'T')
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
				while (start < seq.length() && Character.isAlphabetic(seq.charAt(start)))
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
		int offset;
		int value;

		ParsedDateElement(ParsedDateElementType type, int index, CharSequence text) {
			this.type = type;
			this.index = index;
			this.text = text;
			value = -1;
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
		int theOffset;

		DateFormatComponent(DateElementType dateType, ParsedDateElementType parsedType, Predicate<CharSequence> filter, boolean required) {
			this.dateType = dateType;
			this.parsedType = parsedType;
			this.filter = filter;
			this.required = required;
		}

		DateFormatComponent offset(int offset) {
			this.theOffset = offset;
			return this;
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
		@SuppressWarnings("unused")
		final String theName;
		final List<DateFormatComponent> components;
		final Map<DateElementType, DateFormatComponent> componentsByType;

		DateFormat(String name, DateFormatComponent... components) {
			theName = name;
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
					if (component.dateType != null) {
						element.offset = component.theOffset;
						info.put(component.dateType, element);
					}
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
			return theName;
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
		Predicate<CharSequence> weekDay = str -> {
			if (str.length() < 3)
				return false;
			switch (str.subSequence(0, 3).toString().toLowerCase()) {
			case "sun":
				return str.toString().toLowerCase().equals("sunday".substring(0, str.length()));
			case "mon":
				return str.toString().toLowerCase().equals("monday".substring(0, str.length()));
			case "tue":
				return str.toString().toLowerCase().equals("tuesday".substring(0, str.length()));
			case "wed":
				return str.toString().toLowerCase().equals("wednesday".substring(0, str.length()));
			case "thu":
				return str.toString().toLowerCase().equals("thursday".substring(0, str.length()));
			case "fri":
				return str.toString().toLowerCase().equals("friday".substring(0, str.length()));
			case "sat":
				return str.toString().toLowerCase().equals("saturday".substring(0, str.length()));
			}
			return false;
		};
		DateFormatComponent weekDayOpt = new DateFormatComponent(DateElementType.Weekday, ParsedDateElementType.DAY_NAME, weekDay, false);
		DateFormatComponent weekDayReq = new DateFormatComponent(DateElementType.Weekday, ParsedDateElementType.DAY_NAME, weekDay, true);
		DateFormatComponent year2 = new DateFormatComponent(DateElementType.Year, ParsedDateElementType.DIGIT, twoDigits, true);
		DateFormatComponent year4 = new DateFormatComponent(DateElementType.Year, ParsedDateElementType.DIGIT, fourDigits, true);
		DateFormatComponent monthDig = new DateFormatComponent(DateElementType.Month, ParsedDateElementType.DIGIT, oneOrTwoDigits, true)
			.offset(-1);
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
		DateFormatComponent optionalNonStdDateSep = new DateFormatComponent(null, ParsedDateElementType.SEP, str -> {
			char c = str.charAt(0);
			return c == '-' || c == '/';
		}, false);
		DateFormatComponent dot = new DateFormatComponent(null, ParsedDateElementType.SEP, str -> str.charAt(0) == '.', true);
		DateFormatComponent colon = new DateFormatComponent(null, ParsedDateElementType.SEP, str -> str.charAt(0) == ':', false);

		List<DateFormat> formats = new ArrayList<>();
		formats.addAll(Arrays.asList(//
			// Date formats
			new DateFormat("Y4MDthZ", year4, stdDateSep, monthDig, stdDateSep, day, stndrdth, timeZone), //
			new DateFormat("Y4MZ", year4, stdDateSep, monthDig, timeZone), //
			new DateFormat("Y4Z", year4, timeZone), //
			new DateFormat("MY4Z", monthCh, optionalNonStdDateSep, year4, timeZone), //
			new DateFormat("DMY4Z", weekDayOpt, day, optionalNonStdDateSep, monthCh, optionalNonStdDateSep, year4, timeZone), //
			new DateFormat("DMY2Z", weekDayOpt, day, optionalNonStdDateSep, monthCh, optionalNonStdDateSep, year2, timeZone), //
			new DateFormat("DMZ", weekDayOpt, day, optionalNonStdDateSep, monthCh, timeZone), //
			new DateFormat("MdDthY4Z", weekDayOpt, monthDig, nonStdDateSep, day, stndrdth, nonStdDateSep, year4, timeZone), //
			new DateFormat("MchDthY4Z", weekDayOpt, monthCh, optionalNonStdDateSep, day, stndrdth, optionalNonStdDateSep, year4, timeZone), //
			new DateFormat("MdDthY2Z", weekDayOpt, monthDig, nonStdDateSep, day, stndrdth, nonStdDateSep, year2, timeZone), //
			new DateFormat("MchDthY2Z", weekDayOpt, monthCh, optionalNonStdDateSep, day, stndrdth, nonStdDateSep, year2, timeZone), //
			new DateFormat("MdDthZ", weekDayOpt, monthDig, nonStdDateSep, day, stndrdth, timeZone), //
			new DateFormat("MchDthZ", weekDayOpt, monthCh, optionalNonStdDateSep, day, stndrdth, timeZone), //
			new DateFormat("M.Y4Z", weekDayOpt, monthDig, dot, year4, timeZone), //
			new DateFormat("Dth.M.Y4Z", weekDayOpt, day, stndrdth, dot, monthDig, dot, year4, timeZone), //
			new DateFormat("Dth.M.Y2Z", weekDayOpt, day, stndrdth, dot, monthDig, dot, year2, timeZone), //
			new DateFormat("Dth.MZ", weekDayOpt, day, stndrdth, dot, monthDig, timeZone), //
			new DateFormat("MDZ", weekDayOpt, monthCh, optionalNonStdDateSep, day, timeZone), //
			new DateFormat("WD", weekDayReq), //
			// Time formats
			new DateFormat("HMSSaZ", hour, colon, minute, colon, second, dot, subSecond, ampm, timeZone), //
			new DateFormat("HMSaZ", hour, colon, minute, colon, second, ampm, timeZone), //
			new DateFormat("HMaZ", hour, colon, minute, ampm, timeZone), //
			new DateFormat("HaZ", hour, ampm, timeZone)//
		));
		FLEX_FORMATS = Collections.unmodifiableList(formats);

		TIME_ZONES = QuickSet.of(String::compareToIgnoreCase, TimeZone.getAvailableIDs()).createMap();
	}

	private static final String[] DAYS = new String[] { "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" };
	private static final String[] DAYS_ABBREV = new String[] { "Sun", "Mon", "Tues", "Wed", "Thurs", "Fri", "Sat" };

	/**
	 * @param time The time
	 * @param dayFormat The date format to use to format the year, month, and day
	 * @param opts Configures parsing options
	 * @return A parsed time whose minimum resolution may be days, hours, minutes, seconds, or sub-second
	 */
	public static ParsedTime asFlexTime(Instant time, String dayFormat, Function<TimeEvaluationOptions, TimeEvaluationOptions> opts) {
		TimeEvaluationOptions options = DEFAULT_OPTIONS;
		if (opts != null)
			options = opts.apply(options);
		EnumMap<DateElementType, ParsedDateElement> elements = new EnumMap<>(DateElementType.class);
		StringBuilder str = new StringBuilder();
		Calendar cal = TimeUtils.CALENDAR.get();
		cal.setTimeZone(options.getTimeZone());
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
		DateElementType maxResolution = options.getMaxResolution();
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
			if (options.is24HourFormat())
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
			if (!options.is24HourFormat())
				str.append(am ? "am" : "pm");
		}
		if (foundYear)
			return new AbsoluteTime(str.toString(), time, cal, options.getTimeZone(), resolution, nanoDigits, toComponents(elements));
		else
			return new RelativeTime(str.toString(), options.getTimeZone(), toComponents(elements), options.is24HourFormat(),
				options.getEvaluationType());
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
						text.subSequence(valueStart, c).toString()));
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
						(int) value, text.subSequence(valueStart, c).toString()));
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
						(int) value, text.subSequence(valueStart, c).toString()));
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

	/** @return ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"] */
	public static List<String> getWeekDays() {
		return Arrays.asList(DAYS);
	}

	/** @return ["Sun", "Mon", "Tues", "Wed", "Thurs", "Fri", "Sat"] */
	public static List<String> getWeekDaysAbbrev() {
		return Arrays.asList(DAYS_ABBREV);
	}

	/**
	 * @param amount The time amount
	 * @param unit The time unit
	 * @return A parsed duration object with the given duration
	 */
	public static ParsedDuration flexDuration(int amount, DurationComponentType unit) {
		StringBuilder str = new StringBuilder().append(Math.abs(amount));
		int valueEnd = str.length();
		str.append(DURATION_PRECISION_ABBREVS.get(unit.ordinal()));
		return new ParsedDuration(amount < 0,
			Collections.unmodifiableList(Arrays.asList(new DurationComponent(0, 0, valueEnd, unit, Math.abs(amount), str.toString()))),
			Collections.emptyList());
	}

	/**
	 * @param d The duration to represent as a {@link ParsedDuration}
	 * @return The parsed duration with the same duration as the given duration
	 */
	public static ParsedDuration asFlexDuration(Duration d) {
		boolean neg = d.isNegative();
		if (neg)
			d = d.negated();
		ArrayList<DurationComponent> components = new ArrayList<>();
		ArrayList<String> separators = new ArrayList<>();
		long seconds = d.getSeconds();
		StringBuilder str = new StringBuilder();
		if (seconds > 0) {
			long days = seconds / (24 * 60 * 60);
			seconds %= 24 * 60 * 60;
			if (days > 0) {
				int years = (int) (days / 365.25);
				long daysInYears = getDaysInYears(years);
				days -= daysInYears;
				if (days < 0) {
					years--;
					days += daysInYears - getDaysInYears(years);
				} else if (days > 365) {
					years++;
					days += getDaysInYears(years) - daysInYears;
				} else if (days == 365 && getDaysInYears(years + 1) == daysInYears + 365) {
					years++;
					days = 0;
				}
				addComponent(components, separators, str, years, DurationComponentType.Year);

				int months = (int) (days / 30);
				long daysInMonths = getDaysInMonths(months);
				if (days < 0) {
					months--;
					days += daysInMonths - getDaysInMonths(months);
				} else if (days > 30) {
					months++;
					days += getDaysInMonths(months) - daysInMonths;
				} else if (days == 30 && getDaysInMonths(months + 1) == daysInMonths + 30) {
					months++;
					days = 0;
				}
				addComponent(components, separators, str, months, DurationComponentType.Month);

				addComponent(components, separators, str, (int) days, DurationComponentType.Day);
			}

			int hours = (int) (seconds / 3600);
			seconds %= 3600;
			int minutes = (int) (seconds / 60);
			seconds %= 60;
			addComponent(components, separators, str, hours, DurationComponentType.Hour);
			addComponent(components, separators, str, minutes, DurationComponentType.Minute);
			addComponent(components, separators, str, (int) seconds, DurationComponentType.Second);
		}
		int nanos = d.getNano();
		if (nanos > 1_000_000) {
			addComponent(components, separators, str, nanos / 1_000_000, DurationComponentType.Millisecond);
			nanos %= 1_000_000;
		}
		if (nanos > 1000) {
			addComponent(components, separators, str, nanos / 1000, DurationComponentType.Microsecond);
			nanos %= 1000;
		}
		if (nanos != 0)
			addComponent(components, separators, str, nanos, DurationComponentType.Nanosecond);

		components.trimToSize();
		separators.trimToSize();
		return new ParsedDuration(neg, Collections.unmodifiableList(components), Collections.unmodifiableList(separators));
	}

	private static void addComponent(List<DurationComponent> components, List<String> separators, StringBuilder str, int amount,
		DurationComponentType type) {
		if (amount == 0)
			return;
		int start = str.length();
		if (start > 0) {
			separators.add(" ");
			start++;
			str.append(' ');
		}
		str.append(amount);
		int valueEnd = str.length();
		str.append(DURATION_PRECISION_ABBREVS.get(type.ordinal()));
		components.add(new DurationComponent(start, start, valueEnd, type, amount, str.substring(start, valueEnd)));
	}

	private static final List<String> DURATION_PRECISION_NAMES;
	private static final List<String> DURATION_PRECISION_ABBREVS;

	static {
		List<String> precisions = new ArrayList<>(DurationComponentType.values().length);
		List<String> abbrevs = new ArrayList<>(DurationComponentType.values().length);
		for (DurationComponentType dc : DurationComponentType.values()) {
			switch (dc) {
			case Year:
				precisions.add("year");
				abbrevs.add("y");
				break;
			case Month:
				precisions.add("month");
				abbrevs.add("mo");
				break;
			case Week:
				precisions.add("week");
				abbrevs.add("w");
				break;
			case Day:
				precisions.add("day");
				abbrevs.add("d");
				break;
			case Hour:
				precisions.add("hour");
				abbrevs.add("h");
				break;
			case Minute:
				precisions.add("minute");
				abbrevs.add("m");
				break;
			case Second:
				precisions.add("second");
				abbrevs.add("s");
				break;
			case Millisecond:
				precisions.add("millis");
				abbrevs.add("ms");
				break;
			case Microsecond:
				precisions.add("micros");
				abbrevs.add("us");
				break;
			case Nanosecond:
				precisions.add("nanos");
				abbrevs.add("ns");
				break;
			}
		}

		DURATION_PRECISION_NAMES = Collections.unmodifiableList(precisions);
		DURATION_PRECISION_ABBREVS = Collections.unmodifiableList(abbrevs);
	}

	/** @see RelativeTimeFormat#withAboveDayStrategy(AboveDaysStrategy) */
	public enum AboveDaysStrategy {
		/** Prints >1 day time differences in terms of months and years */
		MonthYear,
		/** Prints >1 day time differences in terms of weeks */
		Week,
		/** Prints >1 day time differences in terms of days */
		None;
	}

	/** @return A new relative time format */
	public static RelativeTimeFormat relativeFormat() {
		return new RelativeTimeFormat();
	}

	/** Prints {@link Instant}s to {@link String}s in a custom way */
	public static class RelativeTimeFormat {
		private Supplier<Instant> theReference;
		private TimeZone theTimeZone;
		private DurationComponentType theMaxPrecision;
		private int theMaxElements;
		private List<String> thePrecisionNames;
		private AboveDaysStrategy theAboveDayStrategy;
		private boolean isPluralized;
		private String theJustNow;
		private String theAgo;

		RelativeTimeFormat() {
			theReference = Instant::now;
			theTimeZone = TimeZone.getDefault();
			theMaxPrecision = DurationComponentType.Minute;
			theMaxElements = 1;
			thePrecisionNames = DURATION_PRECISION_ABBREVS;
			theAboveDayStrategy = AboveDaysStrategy.None;
			theAgo = "ago";
		}

		/**
		 * @param reference The reference time to use when printing
		 * @return This time format
		 */
		public RelativeTimeFormat withReference(Supplier<Instant> reference) {
			theReference = reference;
			return this;
		}

		/**
		 * @param timeZone The time zone to use
		 * @return This format
		 */
		public RelativeTimeFormat withTimeZone(TimeZone timeZone) {
			theTimeZone = timeZone;
			return this;
		}

		/**
		 * @param maxPrecision The maximum precision to print
		 * @return This format
		 */
		public RelativeTimeFormat withMaxPrecision(DurationComponentType maxPrecision) {
			theMaxPrecision = maxPrecision;
			return this;
		}

		/**
		 * @param elements The maximum number of different units to print
		 * @return This format
		 */
		public RelativeTimeFormat withMaxElements(int elements) {
			theMaxElements = elements;
			return this;
		}

		/**
		 * @param abbrev Whether to abbreviate time units
		 * @param pluralized Whether to pluralize time units
		 * @return This format
		 */
		public RelativeTimeFormat abbreviated(boolean abbrev, boolean pluralized) {
			thePrecisionNames = abbrev ? DURATION_PRECISION_ABBREVS : DURATION_PRECISION_NAMES;
			isPluralized = pluralized;
			return this;
		}

		/**
		 * Sets a custom unit name set. Use {@link #abbreviated(boolean, boolean)} to use standard unit names.
		 * 
		 * @param precisionNames The names to use for each of the {@link DurationComponentType}s
		 * @return This format
		 */
		public RelativeTimeFormat withPrecisionNames(List<String> precisionNames) {
			if (precisionNames.size() != DurationComponentType.values().length)
				throw new IllegalArgumentException("Precision names must contain " + DurationComponentType.values().length
					+ " names, one for each of: " + Arrays.toString(DurationComponentType.values()));
			thePrecisionNames = precisionNames;
			return this;
		}

		/**
		 * @param justNow The string to print if the target instant is equal to the {@link #withReference(Supplier) reference} time within
		 *        the configured {@link #withMaxPrecision(DurationComponentType) max precision}, or null to just print 0&lt;max prec unit>
		 * @return This format
		 */
		public RelativeTimeFormat withJustNow(String justNow) {
			theJustNow = justNow;
			return this;
		}

		/**
		 * @param ago The string to print after a formatted time that is before the {@link #withReference(Supplier) reference}
		 * @return This format
		 */
		public RelativeTimeFormat withAgo(String ago) {
			theAgo = ago;
			return this;
		}

		/**
		 * Sets how to print time differences
		 * 
		 * @param ads The strategy to use for printing time differences beyond 1 day
		 * @return This format
		 */
		public RelativeTimeFormat withAboveDayStrategy(AboveDaysStrategy ads) {
			theAboveDayStrategy = ads;
			return this;
		}

		/**
		 * Uses an {@link #withAboveDayStrategy(AboveDaysStrategy) above day strategy} of {@link AboveDaysStrategy#MonthYear}
		 * 
		 * @return This format
		 */
		public RelativeTimeFormat withMonthsAndYears() {
			return withAboveDayStrategy(AboveDaysStrategy.MonthYear);
		}

		/**
		 * Uses an {@link #withAboveDayStrategy(AboveDaysStrategy) above day strategy} of {@link AboveDaysStrategy#Week}
		 * 
		 * @return This format
		 */
		public RelativeTimeFormat withWeeks() {
			return withAboveDayStrategy(AboveDaysStrategy.Week);
		}

		/**
		 * @param time The time to print
		 * @param str The string builder to print the time to (null to create a new one)
		 * @return The string builder
		 */
		public StringBuilder print(Instant time, StringBuilder str) {
			Instant ref = theReference.get();
			Duration d = between(ref, time);
			int[] diffs = new int[DurationComponentType.values().length];
			int[] threshes = new int[diffs.length];
			threshes[DurationComponentType.Year.ordinal()] = -1;
			threshes[DurationComponentType.Month.ordinal()] = 12;
			threshes[DurationComponentType.Hour.ordinal()] = 24;
			threshes[DurationComponentType.Minute.ordinal()] = 60;
			threshes[DurationComponentType.Second.ordinal()] = 60;
			threshes[DurationComponentType.Millisecond.ordinal()] = 1000;
			threshes[DurationComponentType.Microsecond.ordinal()] = 1000;
			threshes[DurationComponentType.Nanosecond.ordinal()] = 1000;
			boolean neg = d.isNegative();
			if (neg)
				d = d.negated();
			if (theAboveDayStrategy == AboveDaysStrategy.MonthYear) {
				Instant t1 = neg ? time : ref;
				Instant t2 = neg ? ref : time;
				Calendar cal = CALENDAR.get();
				cal.setTimeZone(theTimeZone);
				cal.setTimeInMillis(t1.toEpochMilli());
				diffs[DurationComponentType.Year.ordinal()] = cal.get(Calendar.YEAR);
				diffs[DurationComponentType.Month.ordinal()] = cal.get(Calendar.MONTH);
				diffs[DurationComponentType.Day.ordinal()] = cal.get(Calendar.DAY_OF_MONTH);
				diffs[DurationComponentType.Hour.ordinal()] = cal.get(Calendar.HOUR_OF_DAY);
				diffs[DurationComponentType.Minute.ordinal()] = cal.get(Calendar.MINUTE);
				diffs[DurationComponentType.Second.ordinal()] = cal.get(Calendar.SECOND);
				cal.setTimeInMillis(t2.toEpochMilli());
				int amt = cal.get(Calendar.SECOND) - diffs[DurationComponentType.Second.ordinal()];
				int carry;
				if (amt < 0) {
					amt += 60;
					carry = -1;
				} else if (amt >= 60) {
					amt -= 60;
					carry = 1;
				} else
					carry = 0;
				diffs[DurationComponentType.Second.ordinal()] = amt;

				amt = cal.get(Calendar.MINUTE) - diffs[DurationComponentType.Minute.ordinal()] + carry;
				if (amt < 0) {
					amt += 60;
					carry = -1;
				} else if (amt >= 60) {
					amt -= 60;
					carry = 1;
				} else
					carry = 0;
				diffs[DurationComponentType.Minute.ordinal()] = amt;

				amt = cal.get(Calendar.HOUR_OF_DAY) - diffs[DurationComponentType.Hour.ordinal()] + carry;
				if (amt < 0) {
					amt += 24;
					carry = -1;
				} else if (amt >= 24) {
					amt -= 24;
					carry = 1;
				} else
					carry = 0;
				diffs[DurationComponentType.Hour.ordinal()] = amt;

				amt = cal.get(Calendar.DAY_OF_MONTH) - diffs[DurationComponentType.Day.ordinal()] + carry;
				threshes[DurationComponentType.Day.ordinal()] = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
				if (amt < 0) {
					amt += threshes[DurationComponentType.Day.ordinal()];
					carry = -1;
				} else if (amt >= threshes[DurationComponentType.Day.ordinal()]) {
					amt -= threshes[DurationComponentType.Day.ordinal()];
					carry = 1;
				} else
					carry = 0;
				diffs[DurationComponentType.Day.ordinal()] = amt;

				amt = cal.get(Calendar.MONTH) - diffs[DurationComponentType.Month.ordinal()] + carry;
				if (amt < 0) {
					amt += 12;
					carry = -1;
				} else if (amt >= 12) {
					amt -= 12;
					carry = 1;
				} else
					carry = 0;
				diffs[DurationComponentType.Month.ordinal()] = amt;

				amt = cal.get(Calendar.YEAR) - diffs[DurationComponentType.Year.ordinal()] + carry;
				diffs[DurationComponentType.Year.ordinal()] = Math.abs(amt);
			} else {
				diffs[0] = diffs[1] = 0;
				diffs[DurationComponentType.Day.ordinal()] = Math.abs((int) (d.getSeconds() / (24 * 60 * 60)));
				if (theAboveDayStrategy == AboveDaysStrategy.Week) {
					threshes[DurationComponentType.Week.ordinal()] = -1;
					threshes[DurationComponentType.Day.ordinal()] = 7;
					diffs[DurationComponentType.Week.ordinal()] = diffs[DurationComponentType.Day.ordinal()] / 7;
					diffs[DurationComponentType.Day.ordinal()] %= 7;
				} else
					threshes[DurationComponentType.Day.ordinal()] = -1;
				int secs = Math.abs((int) (d.getSeconds() % (24 * 60 * 60)));
				diffs[DurationComponentType.Second.ordinal()] = secs % 60;
				secs /= 60;
				diffs[DurationComponentType.Minute.ordinal()] = secs % 60;
				secs /= 60;
				diffs[DurationComponentType.Hour.ordinal()] = secs;
			}
			int nano = d.getNano();
			diffs[DurationComponentType.Millisecond.ordinal()] = nano / 1_000_000;
			nano %= 1_000_000;
			diffs[DurationComponentType.Microsecond.ordinal()] = nano / 1000;
			nano %= 1000;
			diffs[DurationComponentType.Nanosecond.ordinal()] = nano;
			int minPrecision;
			switch (theAboveDayStrategy) {
			case MonthYear:
				minPrecision = 0;
				break;
			case Week:
				minPrecision = DurationComponentType.Week.ordinal();
				break;
			default:
				minPrecision = DurationComponentType.Day.ordinal();
				break;
			}
			while (diffs[minPrecision] == 0 && minPrecision < theMaxPrecision.ordinal() - theMaxElements + 1) {
				minPrecision++;
			}
			int maxPrecision = minPrecision;
			for (int i = 1; maxPrecision < diffs.length && i < theMaxElements; i++) {
				maxPrecision++;
				while (threshes[maxPrecision] == 0)
					maxPrecision++;
			}
			int roundPrecision = maxPrecision + 1;
			while (roundPrecision < diffs.length && threshes[roundPrecision] == 0)
				roundPrecision++;
			if (roundPrecision < diffs.length && diffs[roundPrecision] >= threshes[roundPrecision] / 2.0)
				increment(diffs, threshes, maxPrecision);
			if (str == null)
				str = new StringBuilder();
			if (neg && theAgo == null)
				str.append('-');
			boolean firstHit = true;
			for (int precision = minPrecision; precision <= maxPrecision; precision++) {
				if (diffs[precision] != 0) {
					if (firstHit)
						firstHit = false;
					else
						str.append(' ');
					str.append(diffs[precision]).append(//
						(isPluralized && diffs[precision] != 1) ? StringUtils.pluralize(thePrecisionNames.get(precision))
							: thePrecisionNames.get(precision));
				}
			}
			if (firstHit) {
				if (theJustNow != null)
					str.append(theJustNow);
				else
					str.append(diffs[maxPrecision]).append(//
						(isPluralized && diffs[maxPrecision] != 1) ? StringUtils.pluralize(thePrecisionNames.get(maxPrecision))
							: thePrecisionNames.get(maxPrecision));
			}

			if (!firstHit && neg && theAgo != null)
				str.append(' ').append(theAgo);
			return str;
		}

		private static void increment(int[] diffs, int[] threshes, int precision) {
			diffs[precision]++;
			while (precision > 0 && (threshes[precision] == 0 || diffs[precision] == threshes[precision])) {
				diffs[precision] = 0;
				precision--;
				diffs[precision]++;
			}
		}

		/**
		 * @param time The time to print
		 * @return The formatted time
		 */
		public String print(Instant time) {
			return print(time, null).toString();
		}

		/** @return This format's {@link #withMaxPrecision(DurationComponentType) max precision} */
		public DurationComponentType getMaxPrecision() {
			return theMaxPrecision;
		}

		/** @return This format's {@link #withMaxElements(int) element limit} */
		public int getMaxElements() {
			return theMaxElements;
		}

		/** @return This format's {@link #withPrecisionNames(List) precision names} */
		public List<String> getPrecisionNames() {
			return thePrecisionNames;
		}

		/** @return Whether units are pluralized */
		public boolean isPluralized() {
			return isPluralized;
		}

		/** @return The {@link #withJustNow(String) just now} String */
		public String getJustNow() {
			return theJustNow;
		}
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
		if (neg)
			secs = -secs;
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

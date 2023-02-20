package org.qommons;

import java.io.IOException;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.io.FieldedAdjustable;
import org.qommons.io.FieldedComponent;
import org.qommons.io.Format;
import org.qommons.io.SimpleSequenceParser;
import org.qommons.io.SimpleSequenceParser.ComponentParser;
import org.qommons.io.SimpleSequenceParser.ParsedElement;
import org.qommons.io.SimpleSequenceParser.ParsedSequence;
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

	/** A parsed instant returned from {@link TimeUtils#parseInstant(CharSequence, boolean, boolean, Function)} */
	public interface ParsedInstant extends FieldedAdjustable<DateElementType, Integer, ParsedElement<DateElementType, Integer>, ParsedInstant> {
		/** @return The time zone of the parsed time, if specified */
		TimeZone getTimeZone();

		/**
		 * @param field The type to increment
		 * @param amount The amount to increment the date by
		 * @return The incremented time
		 */
		ParsedInstant add(DateElementType field, int amount);

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
		 * Whether this time can be {@link #compareTo(ParsedInstant) compared} to the given time. E.g. relative times cannot be compared if the
		 * largest time granularity they specify is not the same, like ("18May" and "12:15").
		 * 
		 * @param other The time to compare to
		 * @return Whether the result of {@link #compareTo(ParsedInstant)} will have meaning for the given time
		 */
		boolean isComparable(ParsedInstant other);
	}

	/** A component of a {@link ParsedDuration} */
	public static class DurationComponent extends FieldedComponent<DurationComponentType, Integer> {
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
	public static class ParsedDuration implements FieldedAdjustable<DurationComponentType, Integer, DurationComponent, ParsedDuration> {
		/** An empty duration */
		public static final ParsedDuration ZERO;
		static {
			try {
				ZERO = parseDuration("0s");
			} catch (ParseException e) {
				throw new IllegalStateException(e);
			}
		}

		private final String theText;
		private final boolean isNegative;
		private final EnumMap<DurationComponentType, DurationComponent> components;
		private final List<DurationComponent> theSequence;
		private final List<String> theSeparators;
		private final RelativeTimeFormat theFormat;

		/**
		 * @param text The text this duration was parsed from, or null if not parsed from text
		 * @param negative Whether this represents a negative duration
		 * @param sequence The parsed components composing this duration
		 * @param separators The whitespace separators between each component
		 * @param format The format to use for printing the duration
		 */
		public ParsedDuration(String text, boolean negative, List<DurationComponent> sequence, List<String> separators,
			RelativeTimeFormat format) {
			isNegative = negative;
			components = new EnumMap<>(DurationComponentType.class);
			for (DurationComponent c : sequence)
				if (components.put(c.getField(), c) != null)
					throw new IllegalArgumentException("Duplicate components of type " + c.getField());
			this.theSequence = sequence;
			theSeparators = separators;
			theFormat = format;
			if (text == null) {
				StringBuilder str = new StringBuilder();
				if (isNegative)
					str.append('-');
				for (int i = 0; i < theSequence.size(); i++) {
					str.append(theSeparators.get(i));
					str.append(theSequence.get(i).toString());
				}
				str.append(theSeparators.get(theSeparators.size() - 1));
				if (theSequence.isEmpty())
					str.append("0s");
				text = str.toString();
			}
			theText = text;
		}

		/** @return 0 if this duration is empty, -1 if it is negative, or 1 if it is positive */
		public int signum() {
			for (DurationComponent comp : theSequence)
				if (comp.getValue() != 0)
					return isNegative ? -1 : 1;
			return 0;
		}

		/** @return The format used to print this duration */
		public RelativeTimeFormat getFormat() {
			return theFormat;
		}

		/**
		 * @param format The format to use for printing the duration
		 * @return A new duration, identical to this one, that uses the given format
		 */
		public ParsedDuration withFormat(RelativeTimeFormat format) {
			// TODO Re-format the components
			return new ParsedDuration(theText, isNegative, theSequence, theSeparators, format);
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
		public ParsedDuration adjust(int position, int amount) {
			DurationComponent component = getComponent(position);
			if (component == null)
				return null;
			return with(component.getField(), component.getValue() + amount);
		}

		@Override
		public ParsedDuration with(DurationComponentType type, Integer value) {
			return adjust((dct, found) -> {
				if (dct == type)
					return value - found;
				else
					return found;
			});
		}

		public ParsedDuration adjust(BiFunction<DurationComponentType, Integer, Integer> adjustment) {
			EnumMap<DurationComponentType, Integer> fieldValues = new EnumMap<>(DurationComponentType.class);
			for (Map.Entry<DurationComponentType, DurationComponent> entry : components.entrySet())
				fieldValues.put(entry.getKey(), entry.getValue().getValue());
			boolean adjusted = false;
			for (DurationComponentType type : DurationComponentType.values()) {
				DurationComponent found = getField(type);
				int oldValue = found == null ? 0 : found.getValue();
				int newValue = adjustment.apply(type, oldValue);
				if (newValue != oldValue)
					adjusted |= adjustValues(fieldValues, type, newValue);
			}
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
			return new ParsedDuration(null, isNegative, sequence, separators, theFormat);
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

		public ParsedDuration plus(ParsedDuration duration) {
			boolean neg = isNegative != duration.isNegative;
			return adjust((type, found) -> {
				DurationComponent comp = duration.getField(type);
				if (comp == null)
					return found;
				else if (neg)
					return found - comp.getValue();
				else
					return found + comp.getValue();
			});
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
				return new ParsedDuration(null, neg, theSequence, theSeparators, theFormat);
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
			return new ParsedDuration(null, neg, Collections.unmodifiableList(newComponents), theSeparators, theFormat);
		}

		public ParsedDuration times(double multiple) {
			if (multiple == 1)
				return this;
			boolean neg = multiple < 0;
			if (neg)
				multiple = -multiple;

			final double mult = multiple;
			double[] remainder = new double[1];
			ParsedDuration product = adjust((type, found) -> {
				double compProduct = found * mult + remainder[0];
				double rem = compProduct - (int) compProduct;
				switch (type) {
				case Year:
					remainder[0] = rem * 12;
					if (Math.abs(rem * 12 - Math.round(rem * 12)) <= 1 / 28)
						remainder[DurationComponentType.Month.ordinal()] = (int) Math.round(rem * 12);
					else
						remainder[DurationComponentType.Day.ordinal()] = rem * 365.25;
					break;
				case Month:
					// If we're less than about a day off, round to the month
					if (rem <= 1 / 28)
						remainder[0] = 0;
					else
						remainder[0] = rem * 365.25 / 12 / 7;
					break;
				case Week:
					remainder[0] = rem * 7;
					break;
				case Day:
					remainder[0] = rem * 24;
					break;
				case Hour:
				case Minute:
					remainder[0] = rem * 60;
					break;
				case Second:
				case Millisecond:
				case Microsecond:
					remainder[0] = rem * 1000;
					break;
				case Nanosecond:
					if (remainder[0] > 500)
						compProduct++;
					break;
				}
				return (int) compProduct;
			});
			return neg ? product.negate() : product;
		}

		public double divide(ParsedDuration duration) {
			if (components.isEmpty())
				return 0;
			else if (duration.components.isEmpty())
				return Double.NaN;
			else if (components.size() == 1 && duration.components.size() == 1) {
				DurationComponent comp1 = components.get(components.keySet().iterator().next());
				DurationComponent comp2 = duration.components.get(duration.components.keySet().iterator().next());
				if (comp1.getField() == comp2.getField())
					return comp1.getValue() * 1.0 / comp2.getValue();
				else if (comp1.getField().compareTo(comp2.getField()) > 0)
					return duration.divide(this);
				switch (comp1.getField()) {
				case Year:
					switch (comp2.getField()) {
					case Year:
						throw new IllegalStateException();
					case Month:
						return comp1.getValue() * 12.0 / comp2.getValue();
					case Week:
						return getDaysInYears(comp1.getValue()) / 7.0 / comp2.getValue();
					case Day:
						return getDaysInYears(comp1.getValue()) * 1.0 / comp2.getValue();
					case Hour:
						return getDaysInYears(comp1.getValue()) * 24.0 / comp2.getValue();
					case Minute:
						return getDaysInYears(comp1.getValue()) * 24.0 * 60.0 / comp2.getValue();
					case Second:
						return getDaysInYears(comp1.getValue()) * 24.0 * 60.0 * 60.0 / comp2.getValue();
					case Millisecond:
						return getDaysInYears(comp1.getValue()) * 24.0 * 60.0 * 6E4 / comp2.getValue();
					case Microsecond:
						return getDaysInYears(comp1.getValue()) * 24.0 * 60.0 * 6E7 / comp2.getValue();
					case Nanosecond:
						return getDaysInYears(comp1.getValue()) * 24.0 * 60.0 * 6E10 / comp2.getValue();
					}
					throw new IllegalStateException();
				case Month:
					switch (comp2.getField()) {
					case Year:
					case Month:
						throw new IllegalStateException();
					case Week:
						return getDaysInMonths(comp1.getValue()) / 7.0 / comp2.getValue();
					case Day:
						return getDaysInMonths(comp1.getValue()) * 1.0 / comp2.getValue();
					case Hour:
						return getDaysInMonths(comp1.getValue()) * 24.0 / comp2.getValue();
					case Minute:
						return getDaysInMonths(comp1.getValue()) * 24.0 * 60.0 / comp2.getValue();
					case Second:
						return getDaysInMonths(comp1.getValue()) * 24.0 * 60.0 * 60.0 / comp2.getValue();
					case Millisecond:
						return getDaysInMonths(comp1.getValue()) * 24.0 * 60.0 * 6E4 / comp2.getValue();
					case Microsecond:
						return getDaysInMonths(comp1.getValue()) * 24.0 * 60.0 * 6E7 / comp2.getValue();
					case Nanosecond:
						return getDaysInMonths(comp1.getValue()) * 24.0 * 60.0 * 6E10 / comp2.getValue();
					}
					throw new IllegalStateException();
				case Week:
					switch (comp2.getField()) {
					case Year:
					case Month:
					case Week:
						throw new IllegalStateException();
					case Day:
						return comp1.getValue() * 7.0 / comp2.getValue();
					case Hour:
						return comp1.getValue() * 7.0 * 24.0 / comp2.getValue();
					case Minute:
						return comp1.getValue() * 7.0 * 24.0 * 60.0 / comp2.getValue();
					case Second:
						return comp1.getValue() * 7.0 * 24.0 * 60.0 * 60.0 / comp2.getValue();
					case Millisecond:
						return comp1.getValue() * 7.0 * 24.0 * 60.0 * 6E4 / comp2.getValue();
					case Microsecond:
						return comp1.getValue() * 7.0 * 24.0 * 60.0 * 6E7 / comp2.getValue();
					case Nanosecond:
						return comp1.getValue() * 7.0 * 24.0 * 60.0 * 6E10 / comp2.getValue();
					}
					throw new IllegalStateException();
				case Day:
					switch (comp2.getField()) {
					case Year:
					case Month:
					case Week:
					case Day:
						throw new IllegalStateException();
					case Hour:
						return comp1.getValue() * 24.0 / comp2.getValue();
					case Minute:
						return comp1.getValue() * 24.0 * 60.0 / comp2.getValue();
					case Second:
						return comp1.getValue() * 24.0 * 60.0 * 60.0 / comp2.getValue();
					case Millisecond:
						return comp1.getValue() * 24.0 * 60.0 * 6E4 / comp2.getValue();
					case Microsecond:
						return comp1.getValue() * 24.0 * 60.0 * 6E7 / comp2.getValue();
					case Nanosecond:
						return comp1.getValue() * 24.0 * 60.0 * 6E10 / comp2.getValue();
					}
					throw new IllegalStateException();
				case Hour:
					switch (comp2.getField()) {
					case Year:
					case Month:
					case Week:
					case Day:
					case Hour:
						throw new IllegalStateException();
					case Minute:
						return comp1.getValue() * 60.0 / comp2.getValue();
					case Second:
						return comp1.getValue() * 60.0 * 60.0 / comp2.getValue();
					case Millisecond:
						return comp1.getValue() * 60.0 * 6E4 / comp2.getValue();
					case Microsecond:
						return comp1.getValue() * 60.0 * 6E7 / comp2.getValue();
					case Nanosecond:
						return comp1.getValue() * 60.0 * 6E10 / comp2.getValue();
					}
					throw new IllegalStateException();
				case Minute:
					switch (comp2.getField()) {
					case Year:
					case Month:
					case Week:
					case Day:
					case Hour:
					case Minute:
						throw new IllegalStateException();
					case Second:
						return comp1.getValue() * 60.0 / comp2.getValue();
					case Millisecond:
						return comp1.getValue() * 6E4 / comp2.getValue();
					case Microsecond:
						return comp1.getValue() * 6E7 / comp2.getValue();
					case Nanosecond:
						return comp1.getValue() * 6E10 / comp2.getValue();
					}
					throw new IllegalStateException();
				case Second:
					switch (comp2.getField()) {
					case Year:
					case Month:
					case Week:
					case Day:
					case Hour:
					case Minute:
					case Second:
						throw new IllegalStateException();
					case Millisecond:
						return comp1.getValue() * 1E3 / comp2.getValue();
					case Microsecond:
						return comp1.getValue() * 1E6 / comp2.getValue();
					case Nanosecond:
						return comp1.getValue() * 1E9 / comp2.getValue();
					}
					throw new IllegalStateException();
				case Millisecond:
					switch (comp2.getField()) {
					case Year:
					case Month:
					case Week:
					case Day:
					case Hour:
					case Minute:
					case Second:
					case Millisecond:
						throw new IllegalStateException();
					case Microsecond:
						return comp1.getValue() * 1E3 / comp2.getValue();
					case Nanosecond:
						return comp1.getValue() * 1E6 / comp2.getValue();
					}
					throw new IllegalStateException();
				case Microsecond:
					switch (comp2.getField()) {
					case Year:
					case Month:
					case Week:
					case Day:
					case Hour:
					case Minute:
					case Second:
					case Millisecond:
					case Microsecond:
						throw new IllegalStateException();
					case Nanosecond:
						return comp1.getValue() * 1E3 / comp2.getValue();
					}
					throw new IllegalStateException();
				case Nanosecond:
					throw new IllegalStateException();
				}
				throw new IllegalStateException();
			} else {
				Duration d1 = asDuration();
				Duration d2 = duration.asDuration();
				double s1 = toSeconds(d1);
				double s2 = toSeconds(d2);
				return s1 / s2;
			}
		}

		public ParsedDuration negate() {
			String text = theText;
			if (isNegative) {
				if (text.startsWith("-"))
					text = text.substring(1);
			} else
				text = "-" + text;
			return new ParsedDuration(text, !isNegative, theSequence, theSeparators, theFormat);
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
			return theText;
		}

		/**
		 * @param d The duration to convert
		 * @param withWeeks Whether to print weeks or just days
		 * @return A parsed duration with the same magnitude as the given duration
		 */
		public static ParsedDuration asParsedDuration(Duration d, boolean withWeeks) {
			return asParsedDuration(d, relativeFormat().withAboveDayStrategy(withWeeks ? AboveDaysStrategy.Week : AboveDaysStrategy.None));
		}

		/**
		 * @param d The duration to convert
		 * @param format The format to use to convert the duration
		 * @return A parsed duration with the same magnitude as the given duration
		 */
		public static ParsedDuration asParsedDuration(Duration d, RelativeTimeFormat format) {
			boolean neg = d.isNegative();
			if (neg)
				d = d.negated();
			List<String> separators = new ArrayList<>();
			List<DurationComponent> components = new ArrayList<>();
			separators.add("");
			int index = 0;
			long seconds = d.getSeconds();
			if (seconds > WEEK.getSeconds() && format.getAboveDayStrategy() != AboveDaysStrategy.None) {
				if (format.getAboveDayStrategy() == AboveDaysStrategy.Week) {
					int weeks = (int) (seconds / WEEK.getSeconds());
					seconds %= WEEK.getSeconds();
					String valueStr = String.valueOf(weeks);
					String text = format.printComponent(weeks, DurationComponentType.Week, null).toString();
					components.add(new DurationComponent(index, index, index + valueStr.length(), DurationComponentType.Week, weeks, text));
					index += text.length();
				} else {
					long days = seconds / DAY.getSeconds();
					// T is now days
					if (days >= 365) {
						int years = (int) (days / 365L);
						long yearDays = getDaysInYears(years);
						if (days - yearDays >= 365) {
							long yearPlusOneDays = getDaysInYears(years + 1);
							if (yearPlusOneDays >= days) {
								years++;
								yearDays = yearPlusOneDays;
							}
						}
						String valueStr = String.valueOf(years);
						String text = format.printComponent(years, DurationComponentType.Year, null).toString();
						days -= yearDays;
						components
							.add(new DurationComponent(index, index, index + valueStr.length(), DurationComponentType.Year, years, text));
						index += text.length();
					}
					if (days >= 30) {
						int months = (int) (days / 30L);
						long monthDays = getDaysInMonths(months);
						if (days - monthDays >= 30) {
							long monthPlusOneDays = getDaysInMonths(months + 1);
							if (monthPlusOneDays >= days) {
								months++;
								monthDays = monthPlusOneDays;
							}
						}
						days -= monthDays;
						String valueStr = String.valueOf(months);
						String text = format.printComponent(months, DurationComponentType.Month, null).toString();
						days -= monthDays;
						components
							.add(new DurationComponent(index, index, index + valueStr.length(), DurationComponentType.Month, months, text));
						index += text.length();
					}
				}
			}
			if (seconds >= DAY.getSeconds()) {
				if (!components.isEmpty()) {
					separators.add(" ");
					index++;
				}
				int days = (int) (seconds / DAY.getSeconds());
				seconds %= DAY.getSeconds();
				String valueStr = String.valueOf(days);
				String text = format.printComponent(days, DurationComponentType.Day, null).toString();
				components
					.add(new DurationComponent(index, index, index + valueStr.length(), DurationComponentType.Day, days, text));
				index += text.length();
			}
			if (seconds >= HOUR.getSeconds() && components.size() < format.getMaxElements()) {
				if (!components.isEmpty()) {
					separators.add(" ");
					index++;
				}
				int hours = (int) (seconds / HOUR.getSeconds());
				seconds %= HOUR.getSeconds();
				String valueStr = String.valueOf(hours);
				String text = format.printComponent(hours, DurationComponentType.Hour, null).toString();
				components
					.add(new DurationComponent(index, index, index + valueStr.length(), DurationComponentType.Hour, hours, text));
				index += text.length();
			}
			if (seconds >= 60 && components.size() < format.getMaxElements()) {
				if (!components.isEmpty()) {
					separators.add(" ");
					index++;
				}
				int minutes = (int) (seconds / 60);
				seconds %= 60;
				String valueStr = String.valueOf(minutes);
				String text = format.printComponent(minutes, DurationComponentType.Minute, null).toString();
				components.add(
					new DurationComponent(index, index, index + valueStr.length(), DurationComponentType.Minute, minutes, text));
				index += text.length();
			}
			if (seconds > 0 && components.size() < format.getMaxElements()) {
				if (!components.isEmpty()) {
					separators.add(" ");
					index++;
				}
				String valueStr = String.valueOf(seconds);
				String text = format.printComponent((int) seconds, DurationComponentType.Second, null).toString();
				components.add(new DurationComponent(index, index, index + valueStr.length(), DurationComponentType.Second, (int) seconds,
					text));
				index += text.length();
			}
			int nanos = d.getNano();
			if (nanos == 0 || components.size() >= format.getMaxElements()) { //
			} else if (nanos % 1_000_000 == 0) {
				if (!components.isEmpty()) {
					separators.add(" ");
					index++;
				}
				int millis = nanos / 1_000_000;
				String valueStr = String.valueOf(millis);
				String text = format.printComponent(millis, DurationComponentType.Millisecond, null).toString();
				components.add(new DurationComponent(index, index, index + valueStr.length(), DurationComponentType.Millisecond, millis,
					text));
				index += text.length();
			} else if (nanos % 1_000 == 0) {
				if (!components.isEmpty()) {
					separators.add(" ");
					index++;
				}
				int micros = nanos / 1_000;
				String valueStr = String.valueOf(micros);
				String text = format.printComponent(micros, DurationComponentType.Microsecond, null).toString();
				components.add(new DurationComponent(index, index, index + valueStr.length(), DurationComponentType.Microsecond, micros,
					text));
				index += text.length();
			} else {
				if (!components.isEmpty()) {
					separators.add(" ");
					index++;
				}
				String valueStr = String.valueOf(nanos);
				String text = format.printComponent(nanos, DurationComponentType.Nanosecond, null).toString();
				components.add(new DurationComponent(index, index, index + valueStr.length(), DurationComponentType.Nanosecond, nanos,
					text));
				index += text.length();
			}
			separators.add("");
			return new ParsedDuration(null, neg, components, separators, format);
		}

		/**
		 * @param months The number of months in the duration
		 * @return A ParsedDuration with its {@link DurationComponentType#Year} and {@link DurationComponentType#Month} components populated
		 *         according to the <code>months</code> parameter
		 */
		public static ParsedDuration ofMonths(int months) {
			ParsedDuration duration = ParsedDuration.ZERO;
			if (months >= 12) {
				duration = duration.with(DurationComponentType.Year, months / 12);
				months %= 12;
			}
			if (months > 0)
				duration = duration.with(DurationComponentType.Month, months);
			return duration;
		}
	}

	private static final Duration WEEK = Duration.ofDays(7);
	private static final Duration DAY = Duration.ofDays(1);
	private static final Duration HOUR = Duration.ofHours(1);
	private static final Duration MINUTE = Duration.ofMinutes(1);

	static abstract class ParsedInstantImpl implements ParsedInstant {
		private final String theText;
		private final TimeZone theTimeZone;
		protected final Map<DateElementType, ParsedElement<DateElementType, Integer>> elements;
		protected final BetterSortedSet<ParsedElement<DateElementType, Integer>> sequence;

		public ParsedInstantImpl(String text, TimeZone timeZone, Map<DateElementType, ParsedElement<DateElementType, Integer>> elements) {
			theText = text;
			theTimeZone = timeZone;
			this.elements = elements;
			BetterSortedSet<ParsedElement<DateElementType, Integer>> ebp = BetterTreeSet
				.<ParsedElement<DateElementType, Integer>> buildTreeSet((t1, t2) -> Integer.compare(t1.getStart(), t2.getStart())).build();
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
		public ParsedElement<DateElementType, Integer> getField(DateElementType type) {
			return elements.get(type);
		}

		@Override
		public List<ParsedElement<DateElementType, Integer>> getComponents() {
			return sequence;
		}

		@Override
		public ParsedInstant with(DateElementType type, Integer value) {
			switch (type) {
			case AmPm:
			case TimeZone:
				throw new IllegalArgumentException("Adding to field " + type + " is not supported");
			default:
				break;
			}
			ParsedElement<DateElementType, Integer> component = elements.get(type);
			if (component == null)
				throw new IllegalArgumentException("Field " + type + " not present in this parsed time");
			return add(type, value - component.getValue());
		}

		@Override
		public ParsedInstant adjust(int position, int amount) {
			ParsedElement<DateElementType, Integer> component = getComponent(position);
			if (component == null)
				return null;
			return with(component.getField(), component.getValue() + amount);
		}

		protected Map<DateElementType, ParsedElement<DateElementType, Integer>> adjustElements(StringBuilder str,
			ToIntFunction<DateElementType> value) {
			Map<DateElementType, ParsedElement<DateElementType, Integer>> newElements = new EnumMap<>(DateElementType.class);
			newElements.putAll(elements);
			int indexBump = 0;
			for (ParsedElement<DateElementType, Integer> component : sequence) {
				ParsedElement<DateElementType, Integer> oldEl = elements.get(component.getField());
				ParsedElement<DateElementType, Integer> newEl = null;
				int newValue;
				switch (component.getField()) {
				case Year:
					newValue = value.applyAsInt(component.getField());
					if (component.getText().length() == 2)
						newValue = newValue % 100;
					newEl = adjust(component, newValue, indexBump);
					break;
				case Month:
					newEl = adjust(component, value.applyAsInt(component.getField()), indexBump);
					break;
				case Day:
					newEl = adjust(component, value.applyAsInt(component.getField()), indexBump);
					break;
				case Weekday:
					newEl = adjust(component, value.applyAsInt(component.getField()), indexBump);
					break;
				case Hour:
					newEl = adjust(component, value.applyAsInt(component.getField()), indexBump);
					break;
				case Minute:
					newEl = adjust(component, value.applyAsInt(component.getField()), indexBump);
					break;
				case Second:
					newEl = adjust(component, value.applyAsInt(component.getField()), indexBump);
					break;
				case SubSecond:
					newEl = adjust(component, value.applyAsInt(component.getField()), indexBump);
					break;
				case AmPm:
					newEl = adjust(component, value.applyAsInt(component.getField()), indexBump);
					break;
				case TimeZone:
					break;
				}

				if (newEl != null && newEl != component) {
					newElements.put(component.getField(), newEl);
					str.delete(newEl.getStart(), newEl.getStart() + oldEl.getText().length());
					str.insert(newEl.getStart(), newEl.getText());
					indexBump += newEl.getText().length() - oldEl.getText().length();
				} else if (indexBump != 0) {
					newElements.put(component.getField(), oldEl.offset(indexBump));
				}
			}
			return newElements;
		}

		private ParsedElement<DateElementType, Integer> adjust(ParsedElement<DateElementType, Integer> element, int newValue,
			int indexBump) {
			if (element.getValue() == newValue)
				return element;
			String newText = null;
			if (element.getField() == DateElementType.Hour) {
				if (elements.containsKey(DateElementType.AmPm))
					newText = element.getParser().format(newValue);
				else
					newText = StringUtils.printInt(newValue, 2, null).toString();
			} else if (element.getParser() == Format.INT)
				newText = StringUtils.add(element.getText(), element.getText().length() - 1, newValue - element.getValue());
			else if (element.getParser() == MONTH_FORMAT) {
				if (element.getText().length() != 3
					&& element.getText().toString().toLowerCase().equals(MONTHS[element.getValue() - Calendar.JANUARY]))
					newText = MONTHS[newValue];
				else
					newText = MONTHS[newValue].substring(0, element.getText().length());
				if (Character.isUpperCase(element.getText().charAt(0)))
					newText = capitalize(newText);
			} else if (element.getParser() == WEEKDAY_FORMAT) {
				if (element.getText().toString().toLowerCase().equals(DAYS[element.getValue() - Calendar.SUNDAY].toLowerCase()))
					newText = DAYS[newValue];
				else
					newText = DAYS_ABBREV[newValue];
			} else if (element.getField() == DateElementType.AmPm) {
				boolean lower = true;
				newText = element.getText();
				int idx = newText.indexOf(element.getValue() == Calendar.AM ? 'a' : 'p');
				if (idx < 0) {
					lower = false;
					idx = newText.indexOf(element.getValue() == Calendar.AM ? 'A' : 'P');
				}
				// Assume it's here, shouldn't have parsed without it
				char newChar;
				if (newValue == 0)
					newChar = lower ? 'a' : 'A';
				else
					newChar = lower ? 'p' : 'P';
				newText = newText.substring(0, idx) + newChar + newText.substring(idx + 1);
			}
			ParsedElement<DateElementType, Integer> newEl = new ParsedElement<>(element.getParser(), element.getStart() + indexBump,
				element.getField(), newValue, newText);
			return newEl;
		}

		@Override
		public String toString() {
			return theText;
		}
	}

	/**
	 * A time parsed from {@link TimeUtils#parseInstant(CharSequence, boolean, boolean, Function)} for which the year is specified in full,
	 * meaning that it represents an absolute instant or a single range in time.
	 */
	public static class AbsoluteInstant extends ParsedInstantImpl {
		/** The lower bound of the range represented by this time */
		public final Instant time;
		/** The upper bound of the range represented by this time */
		public final Instant maxTime;

		int nanoDigits;

		AbsoluteInstant(String text, Instant time, Calendar cal, TimeZone timeZone, DateElementType lowestResolution, int nanoDigits,
			Map<DateElementType, ParsedElement<DateElementType, Integer>> elements) {
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
		public boolean isComparable(ParsedInstant other) {
			return true;
		}

		@Override
		public int compareTo(ParsedInstant o) {
			if (o instanceof AbsoluteInstant) {
				AbsoluteInstant other = (AbsoluteInstant) o;
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
		public ParsedInstant add(DateElementType field, int amount) {
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
			Map<DateElementType, ParsedElement<DateElementType, Integer>> newElements = adjustElements(str, type -> {
				switch (type) {
				case Year:
					return cal.get(Calendar.YEAR);
				case Month:
					return cal.get(Calendar.MONTH);
				case Day:
					return cal.get(Calendar.DAY_OF_MONTH);
				case Weekday:
					return cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
				case Hour:
					int hour = cal.get(Calendar.HOUR_OF_DAY);
					if (elements.containsKey(DateElementType.AmPm)) {
						if (hour == 0)
							hour = 12;
						else if (hour > 12)
							hour -= 12;
					}
					return hour;
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
			return new AbsoluteInstant(str.toString(), newTime, cal, getTimeZone(), getLowestResolution(), nanoDigits, newElements);
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

	/** Strategies for evaluating relative instants */
	public enum RelativeInstantEvaluation {
		/** Chooses the closest time matching the relative instant <b>before</b> the reference time */
		Past,
		/** Chooses the closest time matching the relative instant <b>after</b> the reference time */
		Future,
		/** Chooses the closest time matching the relative instant either before or after the reference time */
		Closest
	}

	/**
	 * A time parsed from {@link TimeUtils#parseInstant(CharSequence, boolean, boolean, Function)} where the year was not specified in full,
	 * meaning that this time can represent any number of time ranges depending on a reference point.
	 */
	public static class RelativeInstant extends ParsedInstantImpl {
		private final int nanoResolution;
		private final int nanoDigits;
		private final boolean is24Hour;
		private final RelativeInstantEvaluation theEvaluationType;

		RelativeInstant(String text, TimeZone timeZone, Map<DateElementType, ParsedElement<DateElementType, Integer>> elements,
			boolean is24Hour,
			RelativeInstantEvaluation evaluationType) {
			super(text, timeZone, elements);
			this.is24Hour = is24Hour;
			theEvaluationType = evaluationType;
			ParsedElement<DateElementType, Integer> subSecEl = elements.get(DateElementType.SubSecond);
			if (subSecEl != null) {
				nanoDigits = subSecEl.getText().length();
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
		public RelativeInstantEvaluation getEvaluationType() {
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
			DateElementType[] types = elements.keySet().toArray(new DateElementType[elements.size()]);
			ArrayUtils.reverse(types);
			for (DateElementType type : types) {
				ParsedElement<DateElementType, Integer> value = elements.get(type);
				switch (type) {
				case Year:
					// If the year was absolute, it wouldn't be a relative time, so it must be a 2-digit year
					int year = cal.get(Calendar.YEAR);
					int newYear = (year / 100) * 100 + value.getValue();
					int diff = newYear - year;
					if (diff <= -50)
						newYear += 100;
					else if (diff > 50)
						newYear -= 100;
					cal.set(Calendar.YEAR, newYear);
					break;
				case Month:
					cal.set(Calendar.MONTH, value.getValue());
					break;
				case Day:
					cal.set(Calendar.DAY_OF_MONTH, value.getValue());
					break;
				case Weekday:
					if (elements.containsKey(DateElementType.Day))
						break;
					if (refCal == null) {
						refCal = Calendar.getInstance();
						refCal.setTimeZone(getTimeZone());
						refCal.setTimeInMillis(cal.getTimeInMillis());
					}
					int currentDay = refCal.get(Calendar.DAY_OF_WEEK);
					int dayDiff = value.getValue() - currentDay;
					if (dayDiff < -3)
						dayDiff += 7;
					else if (dayDiff > 3)
						dayDiff -= 7;
					cal.add(Calendar.DAY_OF_MONTH, dayDiff);
					break;
				case Hour:
					ParsedElement<DateElementType, Integer> comp = getField(DateElementType.AmPm);
					int hour = value.getValue();
					if (comp != null) {
						if (comp.getValue() == 0) {
							if (hour == 12)
								hour = 0;
						} else {
							if (hour != 12)
								hour += 12;
						}
					} else if (!is24Hour && hour <= 12 && value.toString().charAt(0) != '0') { // Assume AM/PM time
						if (refCal == null) {
							refCal = Calendar.getInstance();
							refCal.setTimeZone(getTimeZone());
							refCal.setTimeInMillis(cal.getTimeInMillis());
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
					cal.set(Calendar.MINUTE, value.getValue());
					if (!elements.containsKey(DateElementType.Second)) {
						cal.set(Calendar.SECOND, 0);
						nanos = 0;
					}
					break;
				case Second:
					cal.set(Calendar.SECOND, value.getValue());
					if (!elements.containsKey(DateElementType.SubSecond))
						nanos = 0;
					break;
				case SubSecond:
					nanos = value.getValue();
					break;
				case AmPm:
				case TimeZone:
					break;
				}
			}
			boolean evaluationMatches = true;
			switch (theEvaluationType) {
			case Past:
				evaluationMatches = cal.getTimeInMillis() <= ref.toEpochMilli();
				break;
			case Future:
				evaluationMatches = cal.getTimeInMillis() >= ref.toEpochMilli();
				break;
			default:
				evaluationMatches = false; // Need to check
				break;
			}
			if (!evaluationMatches) { // Fix to match the evaluation type
				if (elements.containsKey(DateElementType.Year)) {
					// Again, if the year was absolute, it wouldn't be a relative time; so this must have a 2-digit year
					switch (theEvaluationType) {
					case Past:
						cal.add(Calendar.YEAR, -100);
						break;
					case Future:
						cal.add(Calendar.YEAR, 100);
						break;
					case Closest:
						break; // We have logic in the loop above to make this so
					}
				} else if (elements.containsKey(DateElementType.Month)) {
					switch (theEvaluationType) {
					case Past:
						cal.add(Calendar.YEAR, -1);
						break;
					case Future:
						cal.add(Calendar.YEAR, 1);
						break;
					case Closest:
						long diff = cal.getTimeInMillis() - ref.toEpochMilli();
						if (Math.abs(diff) > 365L * 24 * 60 * 60 * 1000 / 2)
							cal.add(Calendar.YEAR, diff < 0 ? 1 : -1);
						break;
					}
				} else if (elements.containsKey(DateElementType.Day)) {
					switch (theEvaluationType) {
					case Past:
						cal.add(Calendar.MONTH, -1);
						break;
					case Future:
						cal.add(Calendar.MONTH, 1);
						break;
					case Closest:
						long diff = cal.getTimeInMillis() - ref.toEpochMilli();
						cal.add(Calendar.MONTH, diff < 0 ? 1 : -1);
						long postDiff = cal.getTimeInMillis() - ref.toEpochMilli();
						if (Math.abs(diff) <= Math.abs(postDiff))
							cal.add(Calendar.MONTH, diff < 0 ? -1 : 1);
						break;
					}
				} else if (elements.containsKey(DateElementType.Weekday)) {
					switch (theEvaluationType) {
					case Past:
						cal.add(Calendar.DAY_OF_MONTH, -7);
						break;
					case Future:
						cal.add(Calendar.DAY_OF_MONTH, 7);
						break;
					case Closest:
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
					case Past:
						cal.add(Calendar.HOUR_OF_DAY, -threshHours);
						break;
					case Future:
						cal.add(Calendar.HOUR_OF_DAY, threshHours);
						break;
					case Closest:
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
			for (Map.Entry<DateElementType, ParsedElement<DateElementType, Integer>> element : elements.entrySet()) {
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
		public boolean isComparable(ParsedInstant o) {
			if (o instanceof AbsoluteInstant)
				return true;
			RelativeInstant other = (RelativeInstant) o;
			for (DateElementType type : DateElementType.values()) {
				if (elements.containsKey(type))
					return other.elements.containsKey(type);
				else if (other.elements.containsKey(type))
					return false;
			}
			throw new IllegalStateException();
		}

		@Override
		public int compareTo(ParsedInstant o) {
			if (o instanceof AbsoluteInstant)
				return -o.compareTo(this);
			RelativeInstant other = (RelativeInstant) o;
			for (DateElementType type : DateElementType.values()) {
				ParsedElement<DateElementType, Integer> el = elements.get(type);
				ParsedElement<DateElementType, Integer> otherEl = other.elements.get(type);
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
		public ParsedInstant add(DateElementType field, int amount) {
			if (!elements.containsKey(field))
				throw new IllegalArgumentException("Field " + field + " is not present in this time");
			if (field == DateElementType.Weekday && elements.containsKey(DateElementType.Day))
				return add(DateElementType.Day, amount);
			Map<DateElementType, Integer> newValues = new EnumMap<>(DateElementType.class);
			for (Map.Entry<DateElementType, ParsedElement<DateElementType, Integer>> element : elements.entrySet())
				newValues.put(element.getKey(), element.getValue().getValue());
			DateElementType adjustingField = field;
			int superAdjust = amount;
			while (superAdjust != 0) {
				superAdjust = adjustValues(newValues, adjustingField, superAdjust);
				if (superAdjust != 0)
					adjustingField = DateElementType.values()[adjustingField.ordinal() - 1];
			}
			StringBuilder str = new StringBuilder(toString());
			Map<DateElementType, ParsedElement<DateElementType, Integer>> newElements = adjustElements(str, newValues::get);
			return new RelativeInstant(str.toString(), getTimeZone(), newElements, is24Hour, theEvaluationType);
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
		false, RelativeInstantEvaluation.Closest);

	/** Different options that can be used when evaluating and parsing times */
	public static class TimeEvaluationOptions {
		private final TimeZone theTimeZone;
		private final DateElementType theMaxResolution;
		private final boolean is24HourFormat;
		private final RelativeInstantEvaluation theEvaluationType;

		private TimeEvaluationOptions(TimeZone timeZone, DateElementType resolution, boolean is24HourFormat,
			RelativeInstantEvaluation evaluationType) {
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
		public RelativeInstantEvaluation getEvaluationType() {
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
		 * @param timeZone The name of the time zone to use for this set of options
		 * @return The new time options with the given time zone
		 */
		public TimeEvaluationOptions withTimeZone(String timeZone) {
			return withTimeZone(TimeZone.getTimeZone(timeZone));
		}

		/** @return A new time options set that uses local time */
		public TimeEvaluationOptions localTime() {
			return withTimeZone(TimeZone.getDefault());
		}

		/** @return A new time options set that uses Greenwich Mean Time */
		public TimeEvaluationOptions gmt() {
			return withTimeZone("GMT");
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
		public TimeEvaluationOptions withEvaluationType(RelativeInstantEvaluation evalutionType) {
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
	public static ParsedInstant parseInstant(CharSequence str, boolean wholeText, boolean throwIfNotFound,
		Function<TimeEvaluationOptions, TimeEvaluationOptions> opts) throws ParseException {
		TimeEvaluationOptions options = DEFAULT_OPTIONS;
		if (opts != null)
			options = opts.apply(options);
		ParsedSequence<DateElementType, Integer> firstInfo = DATE_PARSER.parse(str, false, throwIfNotFound);
		if (firstInfo == null)
			return null;
		ParsedSequence<DateElementType, Integer> secondInfo = null;
		if (firstInfo.getLength() < str.length())
			secondInfo = DATE_PARSER.parse(//
				str.subSequence(firstInfo.getLength(), str.length()), wholeText, wholeText && throwIfNotFound);

		Map<DateElementType, ParsedElement<DateElementType, Integer>> components = new EnumMap<>(DateElementType.class);
		components.putAll(firstInfo.getComponentsByType());
		if (secondInfo != null) {
			for (ParsedElement<DateElementType, Integer> comp : secondInfo.getComponents()) {
				if (comp.getField() != null && components.put(comp.getField(), comp.offset(firstInfo.getLength())) != null) {
					if (!throwIfNotFound)
						return null;
					throw new ParseException(
						"Formats " + firstInfo.getFormat() + " and " + secondInfo.getFormat() + " cannot be used together",
						firstInfo.getLength());
				}
			}
		}
		TimeZone timeZone = options.getTimeZone();
		try {
			ParsedElement<DateElementType, Integer> element = components.get(DateElementType.TimeZone);
			if (element != null) {
				String zoneId = element.getText();
				int zoneIndex = TIME_ZONES.keyIndexTolerant(zoneId);
				if (zoneIndex < 0) {
					if (!throwIfNotFound)
						return null;
					throw new ParseException("Unrecognized time zone " + element.getText(), element.getStart());
				}
				if (TIME_ZONES.get(zoneIndex) == null)
					TIME_ZONES.put(zoneIndex, TimeZone.getTimeZone(zoneId));
				timeZone = TIME_ZONES.get(zoneIndex);
			}
			ParsedElement<DateElementType, Integer> year = components.get(DateElementType.Year);
			element = components.get(DateElementType.AmPm);
			if (element != null && year != null && year.getText().length() >= 4) {
				boolean pm = element.getValue() == Calendar.PM;
				element = components.get(DateElementType.Hour);
				int hour = validate(DateElementType.Hour, element);
				if (hour == 0 || hour > 12) {
					if (!throwIfNotFound)
						return null;
					throw new ParseException("Hour must be between 1 and 12 if AM/PM is specified", element.getStart());
				} else if (pm) {
					if (hour != 12)
						hour += 12;
				} else if (hour == 12)
					hour = 0;
				components.put(DateElementType.Hour, new ParsedElement<>(element.getParser(), element.getStart(), DateElementType.Hour,
					hour, StringUtils.printInt(hour, 2, null).toString()));
			}

			String text;
			if (secondInfo != null) {
				int len = firstInfo.getLength() + secondInfo.getLength();
				if (len == str.length())
					text = str.toString();
				else
					text = str.subSequence(0, len).toString();
			} else if (firstInfo.getLength() == str.length())
				text = str.toString();
			else
				text = str.subSequence(0, firstInfo.getLength()).toString();
			if (year != null && year.getText().length() >= 4) {
				Calendar cal = CALENDAR.get();
				cal.clear();
				cal.setTimeZone(timeZone);

				cal.set(Calendar.YEAR, validate(DateElementType.Year, year));

				DateElementType minType = DateElementType.Year;
				element = components.get(DateElementType.Month);
				if (element != null)
					cal.set(Calendar.MONTH, validate(minType = DateElementType.Month, element));
				element = components.get(DateElementType.Day);
				if (element != null)
					cal.set(Calendar.DAY_OF_MONTH, validate(minType = DateElementType.Day, element));
				element = components.get(DateElementType.Hour);
				if (element != null)
					cal.set(Calendar.HOUR_OF_DAY, validate(minType = DateElementType.Hour, element));
				element = components.get(DateElementType.Minute);
				if (element != null)
					cal.set(Calendar.MINUTE, validate(minType = DateElementType.Minute, element));
				element = components.get(DateElementType.Second);
				if (element != null)
					cal.set(Calendar.SECOND, validate(minType = DateElementType.Second, element));
				int nanos;
				element = components.get(DateElementType.SubSecond);
				if (element != null)
					nanos = validate(minType = DateElementType.SubSecond, element);
				else
					nanos = 0;
				Instant time = Instant.ofEpochSecond(cal.getTimeInMillis() / 1000, nanos);
				return new AbsoluteInstant(text, time, cal, timeZone, minType, element == null ? 0 : element.getText().length(), components);
			} else {
				for (Map.Entry<DateElementType, ParsedElement<DateElementType, Integer>> entry : components.entrySet())
					validate(entry.getKey(), entry.getValue());
				return new RelativeInstant(text, timeZone, components, options.is24HourFormat(), options.getEvaluationType());
			}
		} catch (ParseException e) {
			if (!throwIfNotFound)
				return null;
			throw e;
		}
	}

	private static int validate(DateElementType type, ParsedElement<DateElementType, Integer> element) throws ParseException {
		if (element.getValue() >= 0)
			return element.getValue();
		int value = element.getParser().parse(element.getText());
		switch (type) {
		case Year:
		case AmPm:
		case TimeZone:
			break;
		case Month:
			if (value < Calendar.JANUARY || value > Calendar.DECEMBER)
				throw new ParseException("Unrecognized month " + element.getText(), element.getStart());
			break;
		case Day:
			if (value < 1 || value > 31)
				throw new ParseException("Unrecognized day " + element.getText(), element.getStart());
			break;
		case Weekday:
			if (value < 0 || value > 6)
				throw new ParseException("Unrecognized week day " + element.getText(), element.getStart());
			break;
		case Hour:
			if (value < 0 || value > 23)
				throw new ParseException("Unrecognized hour " + element.getText(), element.getStart());
			break;
		case Minute:
		case Second:
			if (value < 0 || value > 59)
				throw new ParseException("Unrecognized " + type + " " + element.getText(), element.getStart());
			break;
		case SubSecond:
			for (int dig = 10; dig > element.getText().length(); dig--)
				value *= 10;
			break;
		}
		return value;
	}

	/** A thread-local calendar. Calendars are rather heavy objects but are thread-unsafe. */
	public static final ThreadLocal<Calendar> CALENDAR = ThreadLocal.withInitial(Calendar::getInstance);

	private static final String[] MONTHS = new String[] { "january", "february", "march", "april", "may", "june", "july", "august",
		"september", "october", "november", "december" };

	// public static final String FLEX_FORMAT_DESCRIP;

	// private static final List<DateFormat> FLEX_FORMATS;
	private static final QuickMap<String, TimeZone> TIME_ZONES;

	// static {
	// Predicate<CharSequence> oneOrTwoDigits = str -> str.length() == 1 || str.length() == 2;
	// Predicate<CharSequence> twoDigits = str -> str.length() == 2;
	// Predicate<CharSequence> threeDigits = str -> str.length() == 3;
	// Predicate<CharSequence> fourDigits = str -> str.length() == 4;
	// Predicate<CharSequence> weekDay = str -> {
	// if (str.length() < 3)
	// return false;
	// switch (str.subSequence(0, 3).toString().toLowerCase()) {
	// case "sun":
	// return str.toString().toLowerCase().equals("sunday".substring(0, str.length()));
	// case "mon":
	// return str.toString().toLowerCase().equals("monday".substring(0, str.length()));
	// case "tue":
	// return str.toString().toLowerCase().equals("tuesday".substring(0, str.length()));
	// case "wed":
	// return str.toString().toLowerCase().equals("wednesday".substring(0, str.length()));
	// case "thu":
	// return str.toString().toLowerCase().equals("thursday".substring(0, str.length()));
	// case "fri":
	// return str.toString().toLowerCase().equals("friday".substring(0, str.length()));
	// case "sat":
	// return str.toString().toLowerCase().equals("saturday".substring(0, str.length()));
	// }
	// return false;
	// };
	// DateFormatComponent weekDayOpt = new DateFormatComponent(DateElementType.Weekday, ParsedDateElementType.DAY_NAME, weekDay, false);
	// DateFormatComponent weekDayReq = new DateFormatComponent(DateElementType.Weekday, ParsedDateElementType.DAY_NAME, weekDay, true);
	// DateFormatComponent year2 = new DateFormatComponent(DateElementType.Year, ParsedDateElementType.DIGIT, twoDigits, true);
	// DateFormatComponent year4 = new DateFormatComponent(DateElementType.Year, ParsedDateElementType.DIGIT, fourDigits, true);
	// DateFormatComponent monthDig = new DateFormatComponent(DateElementType.Month, ParsedDateElementType.DIGIT, oneOrTwoDigits, true)
	// .offset(-1);
	// DateFormatComponent monthCh = new DateFormatComponent(DateElementType.Month, ParsedDateElementType.MONTH,
	// str -> Character.isAlphabetic(str.charAt(0)), true);
	// DateFormatComponent day = new DateFormatComponent(DateElementType.Day, ParsedDateElementType.DIGIT, oneOrTwoDigits, true);
	// DateFormatComponent hour = new DateFormatComponent(DateElementType.Hour, ParsedDateElementType.DIGIT, oneOrTwoDigits, true);
	// DateFormatComponent minute = new DateFormatComponent(DateElementType.Minute, ParsedDateElementType.DIGIT, twoDigits, true);
	// DateFormatComponent second = new DateFormatComponent(DateElementType.Second, ParsedDateElementType.DIGIT, twoDigits, true);
	// DateFormatComponent subSecond = new DateFormatComponent(DateElementType.SubSecond, ParsedDateElementType.DIGIT, str -> true, false);
	// DateFormatComponent ampm = new DateFormatComponent(DateElementType.AmPm, ParsedDateElementType.AM_PM, str -> true, false);
	// DateFormatComponent timeZone = new DateFormatComponent(DateElementType.TimeZone, ParsedDateElementType.CHARS, threeDigits, false);
	// DateFormatComponent stndrdth = new DateFormatComponent(null, ParsedDateElementType.CHARS, str -> {
	// if (str.length() != 2)
	// return false;
	// String suffix = str.toString().toLowerCase();
	// return suffix.equals("st") || suffix.equals("nd") || suffix.equals("rd") || suffix.equals("th");
	// }, false);
	//
	// DateFormatComponent stdDateSep = new DateFormatComponent(null, ParsedDateElementType.SEP, str -> {
	// char c = str.charAt(0);
	// return c == '-' || c == '/' || c == '.';
	// }, true);
	// DateFormatComponent nonStdDateSep = new DateFormatComponent(null, ParsedDateElementType.SEP, str -> {
	// char c = str.charAt(0);
	// return c == '-' || c == '/';
	// }, true);
	// DateFormatComponent optionalNonStdDateSep = new DateFormatComponent(null, ParsedDateElementType.SEP, str -> {
	// char c = str.charAt(0);
	// return c == '-' || c == '/';
	// }, false);
	// DateFormatComponent dot = new DateFormatComponent(null, ParsedDateElementType.SEP, str -> str.charAt(0) == '.', true);
	// DateFormatComponent colon = new DateFormatComponent(null, ParsedDateElementType.SEP, str -> str.charAt(0) == ':', false);
	//
	// List<DateFormat> formats = new ArrayList<>();
	// formats.addAll(Arrays.asList(//
	// // Date formats
	// new DateFormat("Y4MDthZ", year4, stdDateSep, monthDig, stdDateSep, day, stndrdth, timeZone), //
	// new DateFormat("Y4MZ", year4, stdDateSep, monthDig, timeZone), //
	// new DateFormat("Y4Z", year4, timeZone), //
	// new DateFormat("MY4Z", monthCh, optionalNonStdDateSep, year4, timeZone), //
	// new DateFormat("DMY4Z", weekDayOpt, day, optionalNonStdDateSep, monthCh, optionalNonStdDateSep, year4, timeZone), //
	// new DateFormat("DMY2Z", weekDayOpt, day, optionalNonStdDateSep, monthCh, optionalNonStdDateSep, year2, timeZone), //
	// new DateFormat("DMZ", weekDayOpt, day, optionalNonStdDateSep, monthCh, timeZone), //
	// new DateFormat("MdDthY4Z", weekDayOpt, monthDig, nonStdDateSep, day, stndrdth, nonStdDateSep, year4, timeZone), //
	// new DateFormat("MchDthY4Z", weekDayOpt, monthCh, optionalNonStdDateSep, day, stndrdth, optionalNonStdDateSep, year4, timeZone), //
	// new DateFormat("MdDthY2Z", weekDayOpt, monthDig, nonStdDateSep, day, stndrdth, nonStdDateSep, year2, timeZone), //
	// new DateFormat("MchDthY2Z", weekDayOpt, monthCh, optionalNonStdDateSep, day, stndrdth, nonStdDateSep, year2, timeZone), //
	// new DateFormat("MdDthZ", weekDayOpt, monthDig, nonStdDateSep, day, stndrdth, timeZone), //
	// new DateFormat("MchDthZ", weekDayOpt, monthCh, optionalNonStdDateSep, day, stndrdth, timeZone), //
	// new DateFormat("M.Y4Z", weekDayOpt, monthDig, dot, year4, timeZone), //
	// new DateFormat("Dth.M.Y4Z", weekDayOpt, day, stndrdth, dot, monthDig, dot, year4, timeZone), //
	// new DateFormat("Dth.M.Y2Z", weekDayOpt, day, stndrdth, dot, monthDig, dot, year2, timeZone), //
	// new DateFormat("Dth.MZ", weekDayOpt, day, stndrdth, dot, monthDig, timeZone), //
	// new DateFormat("MDZ", weekDayOpt, monthCh, optionalNonStdDateSep, day, timeZone), //
	// new DateFormat("WD", weekDayReq), //
	// // Time formats
	// new DateFormat("HMSSaZ", hour, colon, minute, colon, second, dot, subSecond, ampm, timeZone), //
	// new DateFormat("HMSaZ", hour, colon, minute, colon, second, ampm, timeZone), //
	// new DateFormat("HMaZ", hour, colon, minute, ampm, timeZone), //
	// new DateFormat("HaZ", hour, ampm, timeZone)//
	// ));
	// FLEX_FORMATS = Collections.unmodifiableList(formats);
	//
	// Set<String> tzIds = new TreeSet<>();
	// tzIds.add("Z");
	// tzIds.addAll(Arrays.asList(TimeZone.getAvailableIDs()));
	// TIME_ZONES = QuickSet.of(String::compareToIgnoreCase, tzIds).createMap();
	// }

	private static final SimpleSequenceParser<DateElementType, Integer> DATE_PARSER;

	private static int parseWeekday(CharSequence text, int start) {
		switch (text.charAt(start)) {
		case 's':
		case 'S':
			switch (text.charAt(start + 1)) {
			case 'u':
			case 'U':
				return Calendar.SUNDAY;
			case 'a':
			case 'A':
				return Calendar.SATURDAY;
			}
			break;
		case 'm':
		case 'M':
			return Calendar.MONDAY;
		case 't':
		case 'T':
			switch (text.charAt(start + 1)) {
			case 'u':
			case 'U':
				return Calendar.TUESDAY;
			case 'h':
			case 'H':
				return Calendar.THURSDAY;
			}
			break;
		case 'w':
		case 'W':
			return Calendar.WEDNESDAY;
		case 'f':
		case 'F':
			return Calendar.FRIDAY;
		}
		return -1;
	}

	private static int parseMonth(CharSequence text, int start) {
		switch (text.charAt(start)) {
		case 'j':
		case 'J':
			switch (text.charAt(start + 1)) {
			case 'a':
			case 'A':
				return Calendar.JANUARY;
			case 'u':
			case 'U':
				switch (text.charAt(start + 2)) {
				case 'n':
				case 'N':
					return Calendar.JUNE;
				case 'l':
				case 'L':
					return Calendar.JULY;
				}
			}
			break;
		case 'f':
		case 'F':
			return Calendar.FEBRUARY;
		case 'm':
		case 'M':
			switch (text.charAt(start + 1)) {
			case 'a':
			case 'A':
				switch (text.charAt(start + 2)) {
				case 'r':
				case 'R':
					return Calendar.MARCH;
				case 'y':
				case 'Y':
					return Calendar.MAY;
				}
			}
			break;
		case 'a':
		case 'A':
			switch (text.charAt(start + 1)) {
			case 'p':
			case 'P':
				return Calendar.APRIL;
			case 'u':
			case 'U':
				return Calendar.AUGUST;
			}
			break;
		case 's':
		case 'S':
			return Calendar.SEPTEMBER;
		case 'o':
		case 'O':
			return Calendar.OCTOBER;
		case 'n':
		case 'N':
			return Calendar.NOVEMBER;
		case 'd':
		case 'D':
			return Calendar.DECEMBER;
		}
		return -1;
	}

	private static int parseTh(CharSequence str, int start) {
		switch (str.charAt(start)) {
		case 's':
		case 'S':
			return 1;
		case 'n':
		case 'N':
			return 2;
		case 'r':
		case 'R':
			return 3;
		case 't':
		case 'T':
			return 4;
		}
		return -1;
	}

	private static int startsWithLowerCase(CharSequence str, String test, int strStart, int testStart) {
		int len = Math.min(str.length() - strStart, test.length());
		for (int i = testStart; i < len; i++) {
			char ch1 = str.charAt(strStart + i);
			char ch2 = test.charAt(i);
			if (ch1 != ch2 && ch1 + StringUtils.a_MINUS_A != ch2)
				return i;
		}
		return len;
	}

	private static final Format<Integer> WEEKDAY_FORMAT = new Format<Integer>() {
		@Override
		public void append(StringBuilder text, Integer value) {
			switch (value - Calendar.SUNDAY) {
			case 0:
				text.append("Sun");
				break;
			case 1:
				text.append("Mon");
				break;
			case 2:
				text.append("Tue");
				break;
			case 3:
				text.append("Wed");
				break;
			case 4:
				text.append("Thu");
				break;
			case 5:
				text.append("Fri");
				break;
			case 6:
				text.append("Sat");
				break;
			}
		}

		@Override
		public Integer parse(CharSequence text) throws ParseException {
			// This should only be called if there is, in fact, a match
			return parseWeekday(text, 0);
		}
	};
	private static final Format<Integer> MONTH_FORMAT = new Format<Integer>() {
		@Override
		public void append(StringBuilder text, Integer value) {
			switch (value - Calendar.JANUARY) {
			case 0:
				text.append("Jan");
				break;
			case 1:
				text.append("Feb");
				break;
			case 2:
				text.append("Mar");
				break;
			case 3:
				text.append("Apr");
				break;
			case 4:
				text.append("May");
				break;
			case 5:
				text.append("Jun");
				break;
			case 6:
				text.append("Jul");
				break;
			case 7:
				text.append("Aug");
				break;
			case 8:
				text.append("Sep");
				break;
			case 9:
				text.append("Oct");
				break;
			case 10:
				text.append("Nov");
				break;
			case 11:
				text.append("Dec");
				break;
			}
		}

		@Override
		public Integer parse(CharSequence text) throws ParseException {
			// This should only be called if there is, in fact, a match
			return parseMonth(text, 0);
		}
	};
	private static final Format<Integer> AM_PM_PARSER = new Format<Integer>() {
		@Override
		public void append(StringBuilder text, Integer value) {
			text.append(value == Calendar.AM ? "a.m." : "p.m.");
		}

		@Override
		public Integer parse(CharSequence text) throws ParseException {
			char ch = Character.toLowerCase(text.charAt(0));
			switch (ch) {
			case 'a':
				return Calendar.AM;
			case 'p':
				return Calendar.PM;
			default:
				throw new ParseException("Expected [ap].?m.?", 0);
			}
		}
	};
	private static final Format<Integer> SUB_SECOND_FORMAT = new Format<Integer>() {
		@Override
		public void append(StringBuilder text, Integer value) {
			if (value != null)
				StringUtils.printInt(value, 9, text);
		}

		@Override
		public Integer parse(CharSequence text) throws ParseException {
			int value = Integer.parseInt(text.toString());
			if (value == 0)
				return value;
			for (int dig = 9; dig > text.length(); dig--)
				value *= 10;
			return value;
		}
	};

	private static final Format<Integer> TIME_ZONE_FORMAT = new Format<Integer>() {
		@Override
		public void append(StringBuilder text, Integer value) {
			text.append(TimeZone.getAvailableIDs(value)[0]);
		}

		@Override
		public Integer parse(CharSequence text) throws ParseException {
			TimeZone zone = TIME_ZONES.getIfPresent(text.toString());
			if (zone == null)
				throw new ParseException("Unrecognized time zone", 0);
			return zone.getRawOffset();
		}
	};

	private static class OffsetFormat implements Format<Integer> {
		private final int theOffset;

		public OffsetFormat(int offset) {
			theOffset = offset;
		}

		@Override
		public void append(StringBuilder text, Integer value) {
			Format.INT.append(text, value - theOffset);
		}

		@Override
		public Integer parse(CharSequence text) throws ParseException {
			return theOffset + Format.INT.parse(text);
		}
	}

	private static final ComponentParser WHITESPACE_PARSER = (str, start) -> {
		// Whitespace parsers must parse only a single character at a time,
		// since whitespace is sometimes specified and required in the format
		if (str.length() == start)
			return -1;
		switch (str.charAt(start)) {
		case ' ':
		case '\n':
		case '\t':
		case ',':
		case '-':
		case '_':
			return 1;
		default:
			return -1;
		}
	};

	static {
		SimpleSequenceParser.Builder<DateElementType, Integer> parserBuilder = SimpleSequenceParser.build(DateElementType.class, Format.INT,
			(old, adj) -> old + adj);
		parserBuilder.withWhiteSpace(WHITESPACE_PARSER);
		parserBuilder.withParser("$weekday", (str, start) -> {
			if (str.length() < start + 3)
				return -1;
			String test;
			switch (parseWeekday(str, start)) {
			case Calendar.SUNDAY:
				test = "sunday";
				break;
			case Calendar.MONDAY:
				test = "monday";
				break;
			case Calendar.TUESDAY:
				test = "tuesday";
				break;
			case Calendar.WEDNESDAY:
				test = "wednesday";
				break;
			case Calendar.THURSDAY:
				test = "thursday";
				break;
			case Calendar.FRIDAY:
				test = "friday";
				break;
			case Calendar.SATURDAY:
				test = "saturday";
				break;
			default:
				return -1;
			}
			int len = startsWithLowerCase(str, test, start, 1);
			if (len < 3)
				return -1;
			return len;
		});
		parserBuilder.withFormat("weekday", WEEKDAY_FORMAT);
		parserBuilder.withParser("$th", (str, start) -> {
			if (str.length() < start + 2)
				return -1;
			String test;
			switch (parseTh(str, start)) {
			case 1:
				test = "st";
				break;
			case 2:
				test = "nd";
				break;
			case 3:
				test = "rd";
				break;
			case 4:
				test = "th";
				break;
			default:
				return -1;
			}
			int len = startsWithLowerCase(str, test, start, 1);
			if (len < 2)
				return -1;
			return len;
		});
		parserBuilder.withFormat("ampm", AM_PM_PARSER);
		parserBuilder.withParser("$month", (str, start) -> {
			if (str.length() < start + 3)
				return -1;
			String test;
			switch (parseMonth(str, start)) {
			case Calendar.JANUARY:
				test = "january";
				break;
			case Calendar.FEBRUARY:
				test = "february";
				break;
			case Calendar.MARCH:
				test = "march";
				break;
			case Calendar.APRIL:
				test = "april";
				break;
			case Calendar.MAY:
				test = "may";
				break;
			case Calendar.JUNE:
				test = "june";
				break;
			case Calendar.JULY:
				test = "july";
				break;
			case Calendar.AUGUST:
				test = "august";
				break;
			case Calendar.SEPTEMBER:
				test = "september";
				break;
			case Calendar.OCTOBER:
				test = "october";
				break;
			case Calendar.NOVEMBER:
				test = "november";
				break;
			case Calendar.DECEMBER:
				test = "december";
				break;
			default:
				return -1;
			}
			int len = startsWithLowerCase(str, test, start, 1);
			if (len < 3)
				return -1;
			return len;
		});
		parserBuilder.withFormat("month", MONTH_FORMAT);
		parserBuilder.withFormat("timeZone", TIME_ZONE_FORMAT);
		parserBuilder.withFormat("monthDig", new OffsetFormat(-1));
		parserBuilder.withFormat("subSecond", SUB_SECOND_FORMAT);
		try {
			parserBuilder.parse(TimeUtils.class.getResource("time-formats.xml"));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to access included time formats configuration", e);
		}
		DATE_PARSER = parserBuilder.build();

		Set<String> tzIds = new TreeSet<>();
		tzIds.add("Z");
		tzIds.addAll(Arrays.asList(TimeZone.getAvailableIDs()));
		TIME_ZONES = QuickSet.of(String::compareToIgnoreCase, tzIds).createMap();
	}

	private static final String[] DAYS = new String[] { "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" };
	private static final String[] DAYS_ABBREV = new String[] { "Sun", "Mon", "Tues", "Wed", "Thurs", "Fri", "Sat" };

	/** Parses the day format for {@link TimeUtils#asFlexInstant(Instant, String, Function)} */
	public static class DayFormat {
		/** A component of a {@link DayFormat} */
		public static class DayComponent {
			/** The component as specified in the input text */
			public final String spec;
			/** The date type of this component */
			public final DateElementType type;
			/** The text format for this component */
			public final Format<Integer> format;

			/**
			 * @param spec The component as specified in the input text
			 * @param type The date type for the component
			 * @param format The text format for the component
			 */
			public DayComponent(String spec, DateElementType type, Format<Integer> format) {
				this.spec = spec;
				this.type = type;
				this.format = format;
			}

			/**
			 * Appends this component's value in the calendar to the string
			 * 
			 * @param cal The calendar whose time to print
			 * @param str The string to print the value to
			 */
			public void append(Calendar cal, StringBuilder str) {
				switch (type) {
				case Day:
					StringUtils.printInt(cal.get(Calendar.DAY_OF_MONTH), spec.length(), str);
					break;
				case Month:
					if (spec.length() == 3)
						format.append(str, cal.get(Calendar.MONTH - Calendar.JANUARY));
					else
						StringUtils.printInt(cal.get(Calendar.MONTH) - Calendar.JANUARY, spec.length(), str);
					break;
				case Year:
					StringUtils.printInt(cal.get(Calendar.YEAR), spec.length(), str);
					break;
				case Weekday:
					if (spec.length() == 3)
						str.append(DAYS_ABBREV[cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY]);
					else
						str.append(DAYS[cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY]);
					break;
				default:
					throw new IllegalStateException("Unrecognized day component: " + type);
				}
			}

			@Override
			public int hashCode() {
				return Objects.hash(type, spec.length());
			}

			@Override
			public boolean equals(Object obj) {
				if (obj == this)
					return true;
				else if (!(obj instanceof DayComponent))
					return false;
				return type == ((DayComponent) obj).type && spec.equals(((DayComponent) obj).spec);
			}

			@Override
			public String toString() {
				return spec;
			}
		}

		/** This format's components */
		public final List<DayComponent> components;
		/** This format's components by type */
		public final Map<DateElementType, DayComponent> componentsByType;
		/** The separators between each component */
		public final List<String> separators;

		/**
		 * @param components The components for the format
		 * @param separators The separators between each component
		 */
		public DayFormat(List<DayComponent> components, List<String> separators) {
			this.components = components;
			Map<DateElementType, DayComponent> cbt = new EnumMap<>(DateElementType.class);
			for (DayComponent comp : components)
				cbt.put(comp.type, comp);
			componentsByType = Collections.unmodifiableMap(cbt);
			this.separators = separators;
		}

		/**
		 * @param dayFormat The string to pares the day format from
		 * @return The parsed day format
		 * @throws IllegalArgumentException If the format could not be parsed
		 */
		public static DayFormat parse(String dayFormat) throws IllegalArgumentException {
			List<DayComponent> components = new ArrayList<>(5);
			List<String> separators = new ArrayList<>(6);
			StringBuilder separator = new StringBuilder();
			boolean foundDay = false, foundMonth = false, foundYear = false;
			{
				char type = 0;
				int start = 0;
				for (int i = 0; i <= dayFormat.length(); i++) {
					char newType = i < dayFormat.length() ? dayFormat.charAt(i) : 0;
					switch (newType) {
					case 'd':
					case 'D':
						newType = 'D';
						foundDay = true;
						break;
					case 'M':
					case 'L':
						newType = 'M';
						foundMonth = true;
						break;
					case 'y':
					case 'Y':
						newType = 'Y';
						foundYear = true;
						break;
					case 'E':
					case 'e':
						newType = 'E';
						break;
					default:
						newType = 1;
						break;
					}
					if (type != newType) {
						switch (type) {
						case 'D':
							separators.add(separator.toString());
							separator.setLength(0);
							components.add(new DayComponent(dayFormat.substring(start, i), DateElementType.Day, Format.INT));
							break;
						case 'M':
							separators.add(separator.toString());
							separator.setLength(0);
							if (i - start == 2)
								components.add(new DayComponent(dayFormat.substring(start, i), DateElementType.Month, Format.INT));
							else
								components.add(new DayComponent(dayFormat.substring(start, i), DateElementType.Month, MONTH_FORMAT));
							break;
						case 'Y':
							separators.add(separator.toString());
							separator.setLength(0);
							components.add(new DayComponent(dayFormat.substring(start, i), DateElementType.Year, Format.INT));
							break;
						case 'E':
							separators.add(separator.toString());
							separator.setLength(0);
							components.add(new DayComponent(dayFormat.substring(start, i), DateElementType.Weekday, WEEKDAY_FORMAT));
							break;
						default:
							separator.append(dayFormat.substring(start, i));
							break;
						}

						type = newType;
						start = i;
					}
				}
				// str.append(new SimpleDateFormat(dayFormat).format(cal.getTime()));
			}
			separators.add(separator.toString());
			if (foundYear && (!foundMonth || !foundDay)) {
				throw new IllegalArgumentException("Cannot specify year without month and day");
			} else if (foundMonth && !foundDay)
				throw new IllegalArgumentException("Cannot specify month without day");
			return new DayFormat(Collections.unmodifiableList(components), Collections.unmodifiableList(separators));
		}

		@Override
		public int hashCode() {
			return components.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof DayFormat))
				return false;
			return components.equals(((DayFormat) obj).components);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append(separators.get(0));
			for (int i = 0; i < components.size(); i++) {
				str.append(components.get(i).spec);
				str.append(separators.get(i + 1));
			}
			return str.toString();
		}
	}

	/**
	 * @param time The time
	 * @param dayFormat The date format to use to format the year, month, and day
	 * @param opts Configures parsing options
	 * @return A parsed time whose minimum resolution may be days, hours, minutes, seconds, or sub-second
	 */
	public static ParsedInstant asFlexInstant(Instant time, String dayFormat, Function<TimeEvaluationOptions, TimeEvaluationOptions> opts) {
		TimeEvaluationOptions options = DEFAULT_OPTIONS;
		if (opts != null)
			options = opts.apply(options);
		EnumMap<DateElementType, ParsedElement<DateElementType, Integer>> elements = new EnumMap<>(DateElementType.class);
		StringBuilder str = new StringBuilder();
		Calendar cal = TimeUtils.CALENDAR.get();
		cal.setTimeZone(options.getTimeZone());
		cal.setTimeInMillis(time.toEpochMilli());

		DayFormat df = DayFormat.parse(dayFormat);

		for (int i = 0; i < df.components.size(); i++) {
			str.append(df.separators.get(i));
			df.components.get(i).append(cal, str);
		}
		str.append(df.separators.get(df.components.size()));

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
			elements.put(DateElementType.Hour,
				new ParsedElement<>(Format.INT, index, DateElementType.Hour, cal.get(Calendar.HOUR_OF_DAY), str.substring(index)));
			if (resolution.compareTo(DateElementType.Minute) >= 0) {
				str.append(':');
				index = str.length();
				StringUtils.printInt(cal.get(Calendar.MINUTE), 2, str);
				elements.put(DateElementType.Minute,
					new ParsedElement<>(Format.INT, index, DateElementType.Minute, cal.get(Calendar.MINUTE), str.substring(index)));
				if (resolution.compareTo(DateElementType.Second) >= 0) {
					str.append(':');
					index = str.length();
					StringUtils.printInt(cal.get(Calendar.SECOND), 2, str);
					elements.put(DateElementType.Second,
						new ParsedElement<>(Format.INT, index, DateElementType.Second, cal.get(Calendar.SECOND), str.substring(index)));
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
							new ParsedElement<>(Format.INT, index, DateElementType.SubSecond, time.getNano(), str.substring(index)));
					}
				}
			}
			if (!options.is24HourFormat()) {
				index = str.length();
				str.append(am ? 'a' : 'p').append('m');
				elements.put(DateElementType.AmPm,
					new ParsedElement<>(AM_PM_PARSER, index, DateElementType.AmPm, cal.get(Calendar.AM_PM), str.substring(index)));
			}
		}
		if (df.componentsByType.containsKey(DateElementType.Year))
			return new AbsoluteInstant(str.toString(), time, cal, options.getTimeZone(), resolution, nanoDigits, elements);
		else
			return new RelativeInstant(str.toString(), options.getTimeZone(), elements, options.is24HourFormat(),
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
		return parseDuration(text, true);
	}

	/**
	 * @param text The text to parse
	 * @param throwIfNotFound If true, this method will throw an exception if a duration cannot be parsed. Otherwise null will be returned.
	 * @return A duration parsed from the text
	 * @throws ParseException If the duration cannot be parsed and <code>throwIfNotFount</code> is true
	 */
	public static ParsedDuration parseDuration(CharSequence text, boolean throwIfNotFound) throws ParseException {
		return parseDuration(text, true, throwIfNotFound);
	}

	/**
	 * @param text The text to parse
	 * @param fullText Whether the entire string must be parseable as a duration, or only the first part
	 * @param throwIfNotFound If true, this method will throw an exception if a duration cannot be parsed. Otherwise null will be returned.
	 * @return A duration parsed from the text
	 * @throws ParseException If the duration cannot be parsed and <code>throwIfNotFount</code> is true
	 */
	public static ParsedDuration parseDuration(CharSequence text, boolean fullText, boolean throwIfNotFound) throws ParseException {
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
		int parsed = c;
		while (true) {
			while (c < text.length() && Character.isWhitespace(text.charAt(c))) {
				spacer.append(text.charAt(c));
				c++;
			}
			separators.add(spacer.toString());
			spacer.setLength(0);
			parsed = c;
			if (c == text.length())
				break;
			int valueStart = c;
			long value = 0;
			boolean hasValue = false;
			while (c < text.length() && text.charAt(c) >= '0' && text.charAt(c) <= '9') {
				if (c - valueStart > 10) {
					if (throwIfNotFound)
						throw new ParseException("Too many digits in value", c);
					else if (fullText || !hadContent)
						return null;
					else
						break;
				}
				hasValue = true;
				value = value * 10 + (text.charAt(c) - '0');
				c++;
			}
			if (value > Integer.MAX_VALUE) {
				if (throwIfNotFound)
					throw new ParseException("Too many digits in value", c);
				else if (fullText || !hadContent)
					return null;
				else
					break;
			}
			int decimalStart = c;
			double decimal = Double.NaN;
			if (c < text.length() && text.charAt(c) == '.') {
				decimal = 0;
				c++;
				double place = 0.1;
				while (c < text.length() && text.charAt(c) >= '0' && text.charAt(c) <= '9') {
					hasValue = true;
					if (c - decimalStart < 9) {
						// Only ns precision is supported. Ignore remaining digits.
						decimal = decimal + (text.charAt(c) - '0') * place;
						place /= 10;
					}
					c++;
				}
				if (c == decimalStart) {
					if (throwIfNotFound)
						throw new ParseException("Unrecognized duration", 0);
					else if (fullText || !hadContent)
						return null;
					else
						break;
				}
			}
			if (!hasValue) {
				if (throwIfNotFound)
					throw new ParseException("No number value found", valueStart);
				else if (fullText || !hadContent)
					return null;
				else
					break;
			}
			while (c < text.length() && Character.isWhitespace(text.charAt(c)))
				c++;

			int unitStart = c;
			unit.setLength(0);
			while (c < text.length() && Character.isAlphabetic(text.charAt(c))) {
				unit.append(text.charAt(c));
				c++;
			}
			if (unit.length() == 0) {
				if (throwIfNotFound)
					throw new ParseException("Unit expected", unitStart);
				else if (fullText || !hadContent)
					return null;
				else
					break;
			}
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
					if (throwIfNotFound)
						throw new ParseException("Decimal values are only permitted for unit 'second' and 'millisecond'", decimalStart);
					else if (fullText || !hadContent)
						return null;
					else
						break;
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
				if (throwIfNotFound)
					throw new ParseException("Unrecognized unit: " + unitStr, unitStart);
				else if (fullText || !hadContent)
					return null;
				else
					break;
			}
			hadContent = true;
		}
		if (!hadContent) {
			if (throwIfNotFound)
				throw new ParseException("No content to parse", c);
			else
				return null;
		}
		if (neg)
			duration = duration.negated();
		return new ParsedDuration(text.subSequence(0, parsed).toString(), neg, components, separators, relativeFormat());
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
	 * @param time The time to query
	 * @param timeZone The time zone of interest
	 * @return The day of the week that the given time occurs on in the given time zone--Sunday==0, Saturday=7
	 */
	public static int getDayOfWeek(Instant time, TimeZone timeZone) {
		Calendar cal = CALENDAR.get();
		cal.setTimeZone(timeZone);
		cal.setTimeInMillis(time.toEpochMilli());
		return cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
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
		return new ParsedDuration(null, amount < 0,
			Collections.unmodifiableList(Arrays.asList(new DurationComponent(0, 0, valueEnd, unit, Math.abs(amount), str.toString()))),
			Collections.singletonList(""), relativeFormat());
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
		separators.add("");
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
				days -= daysInMonths;
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
		return new ParsedDuration(null, neg, Collections.unmodifiableList(components), Collections.unmodifiableList(separators),
			relativeFormat());
	}

	private static void addComponent(List<DurationComponent> components, List<String> separators, StringBuilder str, int amount,
		DurationComponentType type) {
		if (amount == 0)
			return;
		int start = str.length();
		if (start > 0) {
			str.append(' ');
			separators.add(" ");
			start++;
		}
		str.append(amount);
		int valueEnd = str.length();
		str.append(DURATION_PRECISION_ABBREVS.get(type.ordinal()));
		components.add(new DurationComponent(start, start, valueEnd, type, amount, str.substring(start)));
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
		private TimeZone theTimeZone;
		private DurationComponentType theMaxPrecision;
		private int theMaxElements;
		private List<String> thePrecisionNames;
		private AboveDaysStrategy theAboveDayStrategy;
		private boolean isPluralized;
		private String theJustNow;
		private String theAgo;
		private boolean is24HourFormat;

		RelativeTimeFormat() {
			theTimeZone = TimeZone.getDefault();
			theMaxPrecision = DurationComponentType.Minute;
			theMaxElements = 1;
			thePrecisionNames = DURATION_PRECISION_ABBREVS;
			theAboveDayStrategy = AboveDaysStrategy.None;
			theAgo = "ago";
		}

		/** @return The time zone used */
		public TimeZone getTimeZone() {
			return theTimeZone;
		}

		/** @return How to print times with reference to units greater than a day */
		public AboveDaysStrategy getAboveDayStrategy() {
			return theAboveDayStrategy;
		}

		/** @return The "ago" string to use for printing times in the past */
		public String getAgo() {
			return theAgo;
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
			theMaxElements = Math.min(elements, DurationComponentType.values().length);
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
		 * @param justNow The string to print if the duration is zero within the configured {@link #withMaxPrecision(DurationComponentType)
		 *        max precision}, or null to just print 0&lt;max prec unit>
		 * @return This format
		 */
		public RelativeTimeFormat withJustNow(String justNow) {
			theJustNow = justNow;
			return this;
		}

		/**
		 * @param ago The string to print after a negative duration
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

		/** @return Whether {@link #relative(Instant, Instant, String)} should use 24-hour format or not */
		public boolean is24HourFormat() {
			return is24HourFormat;
		}

		/**
		 * @param twentyFour Whether {@link #relative(Instant, Instant, String)} should use 24-hour format or not
		 * @return This format
		 */
		public RelativeTimeFormat with24HourFormat(boolean twentyFour) {
			this.is24HourFormat = twentyFour;
			return this;
		}

		private static int[] genDefaultThresholds() {
			int[] threshes = new int[DurationComponentType.values().length];
			threshes[DurationComponentType.Year.ordinal()] = -1;
			threshes[DurationComponentType.Month.ordinal()] = 12;
			threshes[DurationComponentType.Hour.ordinal()] = 24;
			threshes[DurationComponentType.Minute.ordinal()] = 60;
			threshes[DurationComponentType.Second.ordinal()] = 60;
			threshes[DurationComponentType.Millisecond.ordinal()] = 1000;
			threshes[DurationComponentType.Microsecond.ordinal()] = 1000;
			threshes[DurationComponentType.Nanosecond.ordinal()] = 1000;
			return threshes;
		}

		static class DurationComponents {
			final Duration duration;
			final boolean negative;
			final int[] diffs;
			final int[] threshes;
			int minPrecision;
			int maxPrecision;

			DurationComponents(Duration d) {
				this.negative = d.isNegative();
				duration = negative ? d.negated() : d;
				diffs = new int[DurationComponentType.values().length];
				threshes = genDefaultThresholds();
			}
		}

		private void fill(DurationComponents comps, Instant time, Instant reference) {
			if (theAboveDayStrategy == AboveDaysStrategy.MonthYear) {
				Instant t1 = comps.negative ? time : reference;
				Instant t2 = comps.negative ? reference : time;
				Calendar cal = CALENDAR.get();
				cal.setTimeZone(theTimeZone);
				cal.setTimeInMillis(t1.toEpochMilli());
				comps.diffs[DurationComponentType.Year.ordinal()] = cal.get(Calendar.YEAR);
				comps.diffs[DurationComponentType.Month.ordinal()] = cal.get(Calendar.MONTH);
				comps.diffs[DurationComponentType.Day.ordinal()] = cal.get(Calendar.DAY_OF_MONTH);
				comps.diffs[DurationComponentType.Hour.ordinal()] = cal.get(Calendar.HOUR_OF_DAY);
				comps.diffs[DurationComponentType.Minute.ordinal()] = cal.get(Calendar.MINUTE);
				comps.diffs[DurationComponentType.Second.ordinal()] = cal.get(Calendar.SECOND);
				cal.setTimeInMillis(t2.toEpochMilli());
				int amt = cal.get(Calendar.SECOND) - comps.diffs[DurationComponentType.Second.ordinal()];
				int carry;
				if (amt < 0) {
					amt += 60;
					carry = -1;
				} else if (amt >= 60) {
					amt -= 60;
					carry = 1;
				} else
					carry = 0;
				comps.diffs[DurationComponentType.Second.ordinal()] = amt;

				amt = cal.get(Calendar.MINUTE) - comps.diffs[DurationComponentType.Minute.ordinal()] + carry;
				if (amt < 0) {
					amt += 60;
					carry = -1;
				} else if (amt >= 60) {
					amt -= 60;
					carry = 1;
				} else
					carry = 0;
				comps.diffs[DurationComponentType.Minute.ordinal()] = amt;

				amt = cal.get(Calendar.HOUR_OF_DAY) - comps.diffs[DurationComponentType.Hour.ordinal()] + carry;
				if (amt < 0) {
					amt += 24;
					carry = -1;
				} else if (amt >= 24) {
					amt -= 24;
					carry = 1;
				} else
					carry = 0;
				comps.diffs[DurationComponentType.Hour.ordinal()] = amt;

				amt = cal.get(Calendar.DAY_OF_MONTH) - comps.diffs[DurationComponentType.Day.ordinal()] + carry;
				comps.threshes[DurationComponentType.Day.ordinal()] = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
				if (amt < 0) {
					amt += comps.threshes[DurationComponentType.Day.ordinal()];
					carry = -1;
				} else if (amt >= comps.threshes[DurationComponentType.Day.ordinal()]) {
					amt -= comps.threshes[DurationComponentType.Day.ordinal()];
					carry = 1;
				} else
					carry = 0;
				comps.diffs[DurationComponentType.Day.ordinal()] = amt;

				amt = cal.get(Calendar.MONTH) - comps.diffs[DurationComponentType.Month.ordinal()] + carry;
				if (amt < 0) {
					amt += 12;
					carry = -1;
				} else if (amt >= 12) {
					amt -= 12;
					carry = 1;
				} else
					carry = 0;
				comps.diffs[DurationComponentType.Month.ordinal()] = amt;

				amt = cal.get(Calendar.YEAR) - comps.diffs[DurationComponentType.Year.ordinal()] + carry;
				comps.diffs[DurationComponentType.Year.ordinal()] = Math.abs(amt);
			} else {
				comps.diffs[0] = comps.diffs[1] = 0;
				comps.diffs[DurationComponentType.Day.ordinal()] = Math.abs((int) (comps.duration.getSeconds() / (24 * 60 * 60)));
				if (theAboveDayStrategy == AboveDaysStrategy.Week) {
					comps.threshes[DurationComponentType.Week.ordinal()] = -1;
					comps.threshes[DurationComponentType.Day.ordinal()] = 7;
					comps.diffs[DurationComponentType.Week.ordinal()] = comps.diffs[DurationComponentType.Day.ordinal()] / 7;
					comps.diffs[DurationComponentType.Day.ordinal()] %= 7;
				} else
					comps.threshes[DurationComponentType.Day.ordinal()] = -1;
				int secs = Math.abs((int) (comps.duration.getSeconds() % (24 * 60 * 60)));
				comps.diffs[DurationComponentType.Second.ordinal()] = secs % 60;
				secs /= 60;
				comps.diffs[DurationComponentType.Minute.ordinal()] = secs % 60;
				secs /= 60;
				comps.diffs[DurationComponentType.Hour.ordinal()] = secs;
			}
		}

		void computePrecision(DurationComponents comps) {
			int nano = comps.duration.getNano();
			comps.diffs[DurationComponentType.Millisecond.ordinal()] = nano / 1_000_000;
			nano %= 1_000_000;
			comps.diffs[DurationComponentType.Microsecond.ordinal()] = nano / 1000;
			nano %= 1000;
			comps.diffs[DurationComponentType.Nanosecond.ordinal()] = nano;
			switch (theAboveDayStrategy) {
			case MonthYear:
				comps.minPrecision = 0;
				break;
			case Week:
				comps.minPrecision = DurationComponentType.Week.ordinal();
				break;
			default:
				comps.minPrecision = DurationComponentType.Day.ordinal();
				break;
			}
			while (comps.minPrecision < theMaxPrecision.ordinal() && comps.diffs[comps.minPrecision] == 0)
				comps.minPrecision++;
			comps.maxPrecision = comps.minPrecision;
			int nonZeroEls = 0;
			while (comps.maxPrecision < theMaxPrecision.ordinal() && nonZeroEls < theMaxElements) {
				if (comps.diffs[comps.maxPrecision] != 0)
					nonZeroEls++;
				comps.maxPrecision++;
			}
			int roundPrecision = comps.maxPrecision + 1;
			while (roundPrecision < comps.diffs.length && comps.threshes[roundPrecision] == 0)
				roundPrecision++;
			if (roundPrecision < comps.diffs.length && comps.diffs[roundPrecision] >= comps.threshes[roundPrecision] / 2.0)
				increment(comps.diffs, comps.threshes, comps.maxPrecision);
		}

		/**
		 * @param time The time to print
		 * @param reference The reference time to print the given time relative to
		 * @param str The string builder to print the time to (null to create a new one)
		 * @return The string builder
		 */
		public StringBuilder printAsDuration(Instant time, Instant reference, StringBuilder str) {
			DurationComponents comps = new DurationComponents(between(reference, time));
			fill(comps, time, reference);
			computePrecision(comps);
			return printDuration(comps, str);
		}

		/**
		 * @param time The time to turn into an instant
		 * @param reference The reference time to determine the precision of the result
		 * @param dayFormat The day format to use if the time difference is >= 1 day
		 * @return The instant
		 */
		public ParsedInstant relative(Instant time, Instant reference, String dayFormat) {
			DurationComponents comps = new DurationComponents(between(reference, time));
			fill(comps, time, reference);
			computePrecision(comps);
			Calendar cal = CALENDAR.get();
			cal.setTimeZone(theTimeZone);
			cal.setTimeInMillis(time.toEpochMilli());
			Map<DateElementType, ParsedElement<DateElementType, Integer>> components = new LinkedHashMap<>();

			String lastSep = "";
			StringBuilder str = new StringBuilder();
			if (comps.minPrecision <= DurationComponentType.Day.ordinal()) {
				DayFormat df = DayFormat.parse(dayFormat);
				str.append(df.separators.get(0));
				for (int c = 0; c < df.components.size(); c++) {
					DayFormat.DayComponent comp = df.components.get(c);
					DurationComponentType durationType = null;
					switch (comp.type) {
					case Year:
						durationType = DurationComponentType.Year;
						break;
					case Month:
						durationType = DurationComponentType.Day; // Not a typo--if we include the day, also include the month
						break;
					case Day:
						durationType = DurationComponentType.Day;
						break;
					case Weekday:
						durationType = DurationComponentType.Day;
						break;
					default:
						break;
					}
					if (durationType == null || durationType.ordinal() < comps.minPrecision)
						continue;
					comp.append(cal, str);
					lastSep = df.separators.get(c + 1);
					str.append(lastSep);
				}
			}
			StringBuilder text = new StringBuilder();
			int nanoDigits = 0;
			for (int i = DurationComponentType.Hour.ordinal(); i <= theMaxPrecision.ordinal(); i++) {
				DateElementType type = DateElementType.values()[i];
				int value = 0;
				text.setLength(0);
				switch (type) {
				case Hour:
					if (str.length() > 0 && lastSep.length() == 0)
						str.append(' ');
					value = cal.get(Calendar.HOUR_OF_DAY);
					if (is24HourFormat) {
						StringUtils.printInt(value, 2, text);
					} else if (value == 0) {
						value = 12;
						text.append("12a.m.");
					} else if (value <= 12)
						text.append(value).append("a.m.");
					else {
						value -= 12;
						text.append(value).append("p.m.");
					}
					break;
				case Minute:
					str.append(':');
					value = cal.get(Calendar.MINUTE);
					StringUtils.printInt(value, 2, text);
					break;
				case Second:
					str.append(':');
					value = cal.get(Calendar.SECOND);
					StringUtils.printInt(value, 2, text);
					break;
				case SubSecond:
					str.append('.');
					value = reference.getNano();
					nanoDigits = 9;

					StringUtils.printInt(time.getNano(), nanoDigits, text);
					while (text.charAt(text.length() - 1) == '0') {
						nanoDigits--;
						text.setLength(text.length() - 1);
					}
					break;
				default:
					break;
				}
				components.put(type, new ParsedElement<>(Format.INT, str.length(), type, value, text.toString()));
				str.append(text);
			}
			if (components.containsKey(DateElementType.Year))
				return new AbsoluteInstant(str.toString(), time, cal, theTimeZone, DateElementType.values()[comps.maxPrecision], nanoDigits,
					components);
			else
				return new RelativeInstant(str.toString(), theTimeZone, components, is24HourFormat, RelativeInstantEvaluation.Closest);

		}

		/**
		 * @param d The duration to print
		 * @param str The string builder to print the duration to (null to create a new one)
		 * @return The string builder
		 */
		public StringBuilder print(Duration d, StringBuilder str) {
			DurationComponents comps = new DurationComponents(d);
			if (theAboveDayStrategy == AboveDaysStrategy.MonthYear) {
				long t = d.getSeconds();
				comps.diffs[DurationComponentType.Second.ordinal()] = (int) (t % 60);
				t /= 60;
				comps.diffs[DurationComponentType.Minute.ordinal()] = (int) (t % 60);
				t /= 60;
				comps.diffs[DurationComponentType.Hour.ordinal()] = (int) (t % 24);
				t /= 24;
				// T is now days
				if (t >= 365) {
					int years = (int) (t / 365L);
					long yearDays = getDaysInYears(years);
					if (t - yearDays >= 365) {
						long yearPlusOneDays = getDaysInYears(years + 1);
						if (yearPlusOneDays >= t) {
							years++;
							yearDays = yearPlusOneDays;
						}
					}
					t -= yearDays;
					comps.diffs[DurationComponentType.Year.ordinal()] = years;
				}
				if (t >= 30) {
					int months = (int) (t / 30L);
					long monthDays = getDaysInMonths(months);
					if (t - monthDays >= 30) {
						long monthPlusOneDays = getDaysInMonths(months + 1);
						if (monthPlusOneDays >= t) {
							months++;
							monthDays = monthPlusOneDays;
						}
					}
					t -= monthDays;
					comps.diffs[DurationComponentType.Month.ordinal()] = months;
				}
				comps.diffs[DurationComponentType.Day.ordinal()] = (int) t;
			} else {
				comps.diffs[0] = comps.diffs[1] = 0;
				comps.diffs[DurationComponentType.Day.ordinal()] = Math.abs((int) (d.getSeconds() / (24 * 60 * 60)));
				if (theAboveDayStrategy == AboveDaysStrategy.Week) {
					comps.threshes[DurationComponentType.Week.ordinal()] = -1;
					comps.threshes[DurationComponentType.Day.ordinal()] = 7;
					comps.diffs[DurationComponentType.Week.ordinal()] = comps.diffs[DurationComponentType.Day.ordinal()] / 7;
					comps.diffs[DurationComponentType.Day.ordinal()] %= 7;
				} else
					comps.threshes[DurationComponentType.Day.ordinal()] = -1;
				int secs = Math.abs((int) (d.getSeconds() % (24 * 60 * 60)));
				comps.diffs[DurationComponentType.Second.ordinal()] = secs % 60;
				secs /= 60;
				comps.diffs[DurationComponentType.Minute.ordinal()] = secs % 60;
				secs /= 60;
				comps.diffs[DurationComponentType.Hour.ordinal()] = secs;
			}
			computePrecision(comps);
			return printDuration(comps, str);
		}

		private StringBuilder printDuration(DurationComponents comps, StringBuilder str) {
			if (str == null)
				str = new StringBuilder();
			if (comps.negative && theAgo == null)
				str.append('-');
			boolean firstHit = true;
			for (int precision = comps.minPrecision; precision <= comps.maxPrecision; precision++) {
				if (comps.diffs[precision] != 0) {
					if (firstHit)
						firstHit = false;
					else
						str.append(' ');
					printComponent(comps.diffs[precision], DurationComponentType.values()[precision], str);
				}
			}
			if (firstHit) {
				if (theJustNow != null)
					str.append(theJustNow);
				else
					printComponent(comps.diffs[comps.maxPrecision], DurationComponentType.values()[comps.maxPrecision], str);
			}

			if (!firstHit && comps.negative && theAgo != null)
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
		 * @param reference The reference time to print the given time relative to
		 * @return The formatted time
		 */
		public String printAsDuration(Instant time, Instant reference) {
			return printAsDuration(time, reference, null).toString();
		}

		/**
		 * @param duration The duration to print
		 * @return The formatted time
		 */
		public String print(Duration duration) {
			return print(duration, null).toString();
		}

		/**
		 * @param value The duration value
		 * @param unit The unit of the value
		 * @param into The StringBuilder to print into
		 * @return The given string builder
		 */
		public StringBuilder printComponent(int value, DurationComponentType unit, StringBuilder into) {
			if (into == null)
				into = new StringBuilder();
			into.append(value).append(//
				(isPluralized && value != 1) ? StringUtils.pluralize(thePrecisionNames.get(unit.ordinal()))
					: thePrecisionNames.get(unit.ordinal()));
			return into;
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
		return Duration.ofSeconds(seconds, nanos);
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

	/**
	 * @param d The duration to multiply
	 * @param mult The multiplier
	 * @return A duration that is the product of the given duration and the scalar multiplier
	 */
	public static Duration multiply(Duration d, double mult) {
		if (d.isZero())
			return d;
		else if (mult == 0)
			return Duration.ZERO;
		boolean neg;
		if (d.isNegative()) {
			d = negate(d);
			neg = mult > 0;
			if (!neg)
				mult = -mult;
		} else if (mult < 0) {
			neg = true;
			mult = -mult;
		} else {
			neg = false;
		}

		long nano = Math.round(d.getNano() * mult);
		double secsD = d.getSeconds() * mult;
		nano += Math.round((secsD - (long) secsD) * 1E9);
		long secs = Math.round(secsD) + nano / 1_000_000_000;
		nano %= 1_000_000_000;
		if (neg)
			secs = -secs;
		return Duration.ofSeconds(secs, nano);
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

	/**
	 * <p>
	 * Represents an interval between recurrence of some event, which may be based on a constant duration or on calendar months.
	 * </p>
	 * <p>
	 * Examples of non-duration-based intervals are:
	 * <ul>
	 * <li>Monthly on the Nth day of the month</li>
	 * <li>Monthly on the Nth [weekday] of the month</li>
	 * <li>Monthly on the last day of the month</li>
	 * </ul>
	 * The intervals do not necessarily need to recur every month. E.g.
	 * <ul>
	 * <li>Annually on the Nth of [month]</li>
	 * <li>Quarterly on the Nth [weekday] of [month]</li>
	 * </ul>
	 * </p>
	 * <p>
	 * This class does not contain the anchor determining when exactly the event occurs--only the interval between events. The anchor must
	 * be passed to all methods of this class.
	 * </p>
	 */
	public static class RecurrenceInterval {
		private final String theTextRepresentation;
		private final ParsedDuration theDuration;
		private final int theMonths;
		private final int theWeek;
		private final int theDay;

		private RecurrenceInterval(String text, ParsedDuration duration, int months, int week, int day) {
			theTextRepresentation = text;
			theDuration = duration;
			theMonths = months;
			theWeek = week;
			theDay = day;
		}

		/** @return The text representation of this recurrence interval */
		public String getTextRepresentation() {
			return theTextRepresentation;
		}

		/** @return The duration represented by this recurrence. The duration is only a component of recurrence, and may be an estimate. */
		public ParsedDuration getDuration() {
			return theDuration;
		}

		/**
		 * Creates a recurrence interval based on a "normal" interpretation of an interval. This can be a constant-duration-based interval,
		 * or based on calendar months. The {@link DurationComponentType#Year} and {@link DurationComponentType#Month}
		 * {@link ParsedDuration#getComponents() components} will affect the interval as expected, lengthening and shortening for long and
		 * short months.
		 * 
		 * @param textRepresentation The text representation of this interval. This value is not used by the class only kept for reference.
		 * @param duration The duration of the interval
		 * @return The recurrence interval
		 */
		public static RecurrenceInterval normal(String textRepresentation, ParsedDuration duration) {
			return new RecurrenceInterval(textRepresentation, duration, -1, -1, -1);
		}

		/**
		 * @param textRepresentation The text representation of this interval. This value is not used by the class only kept for reference.
		 * @param intervalMonths The number of months between recurrences
		 * @param daysBeforeLast The number of days before the last day of the month of the occurrence
		 * @return A recurrence interval that occurs on the [daysBeforeLast]-to-last day of the month every [intervalMonths] month
		 */
		public static RecurrenceInterval onLastOfMonth(String textRepresentation, int intervalMonths, int daysBeforeLast) {
			if (intervalMonths <= 0)
				throw new IllegalArgumentException("Months must be >0");
			return new RecurrenceInterval(textRepresentation, ParsedDuration.ofMonths(intervalMonths), intervalMonths, -1, daysBeforeLast);
		}

		/**
		 * @param textRepresentation The text representation of this interval. This value is not used by the class only kept for reference.
		 * @param intervalMonths The number of months between recurrences
		 * @param weekOfMonth The week of the month of the recurrence, starting at one
		 * @param dayOfWeek The day of the week of the occurrence, starting at {@link Calendar#SUNDAY}, which is 1
		 * @return A recurrence interval that occurs on the [weekOfMonth]th [dayOfWeek] every [intervalMonths] month
		 */
		public static RecurrenceInterval onDayOfMonthWeek(String textRepresentation, int intervalMonths, int weekOfMonth, int dayOfWeek) {
			if (intervalMonths <= 0)
				throw new IllegalArgumentException("Months must be >0");
			return new RecurrenceInterval(textRepresentation, ParsedDuration.ofMonths(intervalMonths), intervalMonths, weekOfMonth,
				dayOfWeek);
		}

		/**
		 * 
		 * @param reference Any occurrence time of the event
		 * @param relative Any arbitrary instant
		 * @param after Whether to return a time after <code>relative</code> or before
		 * @param strict Whether to return a time strictly after/before <code>relative</code>. If false, a time equal to
		 *        <code>reference</code> will be returned if the event occurs at that time.
		 * @return The occurrence of the event that occurs closest to <code>reference</code> with the given constraints
		 */
		public Instant getOccurrence(Instant reference, Instant relative, boolean after, boolean strict) {
			Instant occur;
			// First, get an estimate that can't be later than after
			if (relative == null) {
				return reference;
			} else if (theDay >= 0) {
				Calendar cal = TimeUtils.CALENDAR.get();
				cal.setTimeZone(TimeZone.getDefault());
				cal.setTimeInMillis(reference.toEpochMilli());
				cal.set(Calendar.DAY_OF_MONTH, 1);
				long afterMillis = relative.toEpochMilli();
				long intervalMillis = theMonths * 31L * 24 * 60 * 60 * 1000;
				int div = (int) ((afterMillis - cal.getTimeInMillis()) / intervalMillis);
				while (div != 0) {
					cal.add(Calendar.MONTH, div * theMonths);
					div = (int) ((afterMillis - cal.getTimeInMillis()) / intervalMillis);
				}
				if (theWeek >= 0) {
					int day = cal.get(Calendar.DAY_OF_WEEK);
					if (day < theDay) {
						cal.add(Calendar.DAY_OF_MONTH, theDay - day);
					} else if (day > theDay) {
						cal.add(Calendar.DAY_OF_MONTH, theDay - day + 7);
					}
					cal.add(Calendar.DAY_OF_MONTH, (theWeek - 1) * 7);
				} else {
					cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH) - theDay);
				}
				occur = Instant.ofEpochMilli(cal.getTimeInMillis());
			} else {
				Duration estDuration = theDuration.asDuration();
				int times = TimeUtils.divide(Duration.between(reference, relative), estDuration);
				if (times > 1) {
					occur = theDuration.times(times - 2).addTo(reference, TimeZone.getDefault());
				} else if (times < 0) {
					occur = theDuration.times(times - 1).addTo(reference, TimeZone.getDefault());
				} else {
					occur = reference;
				}
			}

			// Next, enforce the time constraint
			while (true) {
				int comp = occur.compareTo(relative);
				if (comp == 0) {
					if (!strict) {
						break;
					}
				} else if ((comp > 0) == after) {
					break;
				}
				occur = adjacentOccurrence(occur, after);
			}
			return occur;
		}

		/**
		 * @param occurrence An occurrence of the event
		 * @param next Whether to return the next or previous occurrence
		 * @return The occurrence of the event occurring immediately after/before <code>lastOccurrence</code>
		 */
		public Instant adjacentOccurrence(Instant occurrence, boolean next) {
			if (theWeek >= 0) { // Xth [weekday] of the month
				long millis = occurrence.toEpochMilli();
				Calendar cal = TimeUtils.CALENDAR.get();
				cal.setTimeInMillis(millis);
				cal.set(Calendar.DAY_OF_MONTH, 1);
				cal.add(Calendar.MONTH, theMonths * (next ? 1 : -1));
				int day = cal.get(Calendar.DAY_OF_WEEK);
				if (day < theDay) {
					cal.add(Calendar.DAY_OF_MONTH, theDay - day);
				} else if (day > theDay) {
					cal.add(Calendar.DAY_OF_MONTH, theDay - day + 7);
				}
				cal.add(Calendar.DAY_OF_MONTH, (theWeek - 1) * 7);
				return Instant.ofEpochMilli(cal.getTimeInMillis());
			} else if (theDay >= 0) {// X days before the end of the month
				Calendar cal = TimeUtils.CALENDAR.get();
				cal.setTimeInMillis(occurrence.toEpochMilli());
				cal.set(Calendar.DAY_OF_MONTH, 1);
				cal.add(Calendar.MONTH, theMonths * (next ? 1 : -1));
				cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH) - theDay);
				return Instant.ofEpochMilli(cal.getTimeInMillis());
			} else {
				if (next) {
					return theDuration.addTo(occurrence, TimeZone.getDefault());
				} else {
					return theDuration.times(-1).addTo(occurrence, TimeZone.getDefault());
				}
			}
		}

		@Override
		public int hashCode() {
			return Objects.hash(theDuration, theMonths, theWeek, theDay);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof RecurrenceInterval))
				return false;
			RecurrenceInterval other = (RecurrenceInterval) obj;
			return theDuration.equals(other.theDuration)//
				&& theMonths == other.theMonths//
				&& theWeek == other.theWeek//
				&& theDay == other.theDay;
		}

		@Override
		public String toString() {
			if (theMonths > 0) {
				StringBuilder str = new StringBuilder();
				switch (theMonths) {
				case 1:
					str.append("monthly, ");
					break;
				case 2:
					str.append("every other ");
					break;
				case 3:
					str.append("quarterly, ");
					break;
				case 6:
					str.append("bianually, ");
					break;
				case 12:
					str.append("annually, ");
					break;
				default:
					if (theMonths % 12 == 0) {
						str.append(" every ").append(theMonths / 12).append(" yrs, ");
					} else {
						str.append(" every ").append(theMonths).append(" mos, ");
					}
					break;
				}
				if (theWeek >= 0) {
					str.append(theWeek);
					switch (theWeek) {
					case 1:
						str.append("st");
						break;
					case 2:
						str.append("nd");
						break;
					case 3:
						str.append("rd");
						break;
					default:
						str.append("th");
						break;
					}
					str.append(' ').append(TimeUtils.getWeekDaysAbbrev().get(theDay));
				} else {
					if (theDay == 0) {
						str.append(" last day of month");
					} else {
						str.append(theDay).append(" days before end of month");
					}
				}
				return str.toString();
			} else {
				return theDuration.toString();
			}
		}
	}

	/**
	 * <p>
	 * Parses a recurrence interval from text.
	 * </p>
	 * <p>
	 * This method uses '-' and '#' notation to denote the 2 non-{@link RecurrenceInterval#normal(String, ParsedDuration) normal} recurrence
	 * types. The presence of a '-' terminator is used to denote an interval that occurs on the last Nth day of a month. A '#' terminator
	 * denotes an interval that occurs on a particular Nth [weekday] of a month. When using these special types, only
	 * {@link DurationComponentType#Year year} and {@link DurationComponentType#Month month} components may be used for the interval.
	 * </p>
	 * <p>
	 * Examples:
	 * <ul>
	 * <li>"1mo-" This recurrence will occur on the same day of the month before the end as the given occurrence. E.g. If the occurrence is
	 * on the last day of the month, the event will always recur then. If the occurrence is 5 days before the end of its month, the event
	 * will always recur 5 days before the end of a month</li>
	 * <li>"1mo#" This recurrence will occur on the same Nth [weekday] of a month as the given occurrence</li>
	 * </ul>
	 * </p>
	 * 
	 * @param recur The String to parse the recurrence from
	 * @param occurrence An occurrence time of the vent. This time will be used to determine the week and day of special-type intervals.
	 * @return The parsed recurrence
	 * @throws ParseException If the recurrence string could not be parsed
	 */
	public static RecurrenceInterval parseRecurrenceInterval(String recur, Instant occurrence) throws ParseException {
		if (occurrence == null || recur == null || recur.isEmpty()) {
			return null;
		}

		char lastChar = recur.charAt(recur.length() - 1);
		String dStr;
		int day, week;
		switch (lastChar) {
		case '-': // Code for days from the last of the month
			dStr = recur.substring(0, recur.length() - 1);
			Calendar cal = TimeUtils.CALENDAR.get();
			cal.setTimeZone(TimeZone.getDefault());
			cal.setTimeInMillis(occurrence.toEpochMilli());
			day = cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH);
			week = -1;
			break;
		case '#': // Code for Xth [weekday] of the month
			dStr = recur.substring(0, recur.length() - 1);
			cal = TimeUtils.CALENDAR.get();
			cal.setTimeZone(TimeZone.getDefault());
			cal.setTimeInMillis(occurrence.toEpochMilli());
			day = cal.get(Calendar.DAY_OF_WEEK);
			week = cal.get(Calendar.WEEK_OF_MONTH);
			break;
		default: // Normal frequency
			day = week = -1;
			dStr = recur;
		}
		ParsedDuration duration;
		if (!dStr.isEmpty()) {
			duration = TimeUtils.parseDuration(dStr);
		} else {
			duration = null;
		}
		int months = 0;
		if (day >= 0) {
			if (duration != null) {
				for (DurationComponent component : duration.getComponents()) {
					switch (component.getField()) {
					case Year:
						months += component.getValue() * 12;
						break;
					case Month:
						months += component.getValue();
						break;
					default:
						throw new ParseException("Bad duration--" + lastChar + " notation can only be used with months and years",
							recur.length() - 1);
					}
				}
			}
			if (months == 0) {
				months = 1;
			}
			if (week >= 0)
				return RecurrenceInterval.onDayOfMonthWeek(recur, months, week, day);
			else
				return RecurrenceInterval.onLastOfMonth(recur, months, day);
		}
		return RecurrenceInterval.normal(recur, duration);
	}
}

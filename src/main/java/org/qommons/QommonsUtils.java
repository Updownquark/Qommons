package org.qommons;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.qommons.collect.BetterList;
import org.qommons.collect.SimpleImmutableList;
import org.qommons.ex.ExFunction;
import org.qommons.ex.ExPredicate;
import org.qommons.io.Format;

/** Even more general utilities that I can't think where else to put */
public class QommonsUtils {
	/** Represents different precisions to which a time can be represented in text */
	public enum TimePrecision {
		/** Represents a time to days */
		DAYS("ddMMMyyyy", false),
		/** Represents a time to minutes */
		MINUTES("ddMMMyyyy HHmm"),
		/** Represents a time to seconds */
		SECONDS("ddMMMyyyy HH:mm:ss"),
		/** Represents a time to milliseconds */
		MILLIS("ddMMMyyyy HH:mm:ss.SSS");

		private java.util.concurrent.locks.Lock theLock;

		/** The GMT date format for this precision */
		private final java.text.SimpleDateFormat gmtFormat;

		/** The local date format for this precision */
		private final java.text.SimpleDateFormat localFormat;

		TimePrecision(String dateFormat) {
			this(dateFormat, true);
		}

		TimePrecision(String dateFormat, boolean withZ) {
			theLock = new java.util.concurrent.locks.ReentrantLock();
			localFormat = new java.text.SimpleDateFormat(dateFormat);
			if (withZ)
				dateFormat += "'Z'";
			gmtFormat = new java.text.SimpleDateFormat(dateFormat);
			gmtFormat.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
		}

		/**
		 * Prints a GMT-refrenced representation of the given time for this precision
		 *
		 * @param time The time to represent
		 * @param local Whether to print the time in the local time zone or in GMT
		 * @return The representation of <code>time</code>
		 */
		public String print(long time, boolean local) {
			java.text.SimpleDateFormat format = local ? localFormat : gmtFormat;
			theLock.lock();
			try {
				return format.format(new java.util.Date(time));
			} finally {
				theLock.unlock();
			}
		}

		/**
		 * Parses a GMT-referenced date for this precision
		 *
		 * @param time The formatted time string
		 * @param local Whether to print the time in the local time zone or in GMT
		 * @return The java time value represented by <code>time</code>
		 */
		public long parse(String time, boolean local) {
			java.text.SimpleDateFormat format = local ? localFormat : gmtFormat;
			theLock.lock();
			try {
				return format.parse(time).getTime();
			} catch (java.text.ParseException e) {
				throw new IllegalArgumentException("Cannot parse " + time + " to a time", e);
			} finally {
				theLock.unlock();
			}
		}
	}

	private static final java.util.Random theRandom = new java.util.Random();

	/**
	 * Creates a random hexadecimal string with the given length
	 *
	 * @param chars The length for the random string
	 * @return The random string
	 */
	public static String getRandomString(int chars) {
		StringBuilder ret = new StringBuilder();
		while (ret.length() < chars) {
			int next = theRandom.nextInt();
			ret.append(Integer.toHexString(next));
		}
		if (ret.length() != chars)
			ret.setLength(chars);
		return ret.toString();
	}

	/**
	 * @return A non-negative random integer
	 */
	public static int getRandomInt() {
		return theRandom.nextInt() >>> 1;
	}

	/**
	 * Creates a date format based on {@link SimpleDateFormat} that can be used across threads
	 * 
	 * @param format The format string
	 * @return The date format
	 */
	public static Format<Date> simpleDateFormat(String format) {
		return simpleDateFormat(format, TimeZone.getDefault());
	}

	/**
	 * Creates a date format based on {@link SimpleDateFormat} that can be used across threads
	 * 
	 * @param format The format string
	 * @param timeZone The time zone for the format
	 * @return The date format
	 */
	public static Format<Date> simpleDateFormat(String format, TimeZone timeZone) {
		ThreadLocal<SimpleDateFormat> localFormat = ThreadLocal.withInitial(() -> {
			SimpleDateFormat sdf = new SimpleDateFormat(format);
			sdf.setTimeZone(timeZone);
			return sdf;
		});
		return new Format<Date>() {
			@Override
			public void append(StringBuilder text, Date value) {
				SimpleDateFormat sdf = localFormat.get();
				text.append(sdf.format(value));
			}

			@Override
			public Date parse(CharSequence text) throws ParseException {
				SimpleDateFormat sdf = localFormat.get();
				return sdf.parse(text.toString());
			}

			@Override
			public String toString() {
				return format;
			}
		};
	}

	/**
	 * Prints a military-style GMT-referenced date
	 *
	 * @param time The time to represent
	 * @return The <code>ddMMMyyyy HHmm'Z'</code> representation of <code>time</code>
	 */
	public static String print(long time) {
		return TimePrecision.MINUTES.print(time, false);
	}

	/**
	 * Parses a military-style GMT-referenced date
	 *
	 * @param time The formatted time string
	 * @return The java time value represented by <code>time</code>
	 */
	public static long parse(String time) {
		return TimePrecision.MINUTES.parse(time, false);
	}

	/**
	 * Prints a time length to a string
	 *
	 * @param length The length of time in milliseconds
	 * @return The representation of the time length
	 */
	public static String printTimeLength(long length) {
		StringBuilder sb = new StringBuilder();
		printTimeLength(length, sb, false);
		return sb.toString();
	}

	/**
	 * Prints a time length to a string
	 *
	 * @param seconds The length of time in seconds
	 * @param nanos Additional nanoseconds past the seconds
	 * @return The representation of the time length
	 */
	public static String printTimeLength(long seconds, int nanos) {
		StringBuilder sb = new StringBuilder();
		printTimeLength(seconds, nanos, sb, false);
		return sb.toString();
	}

	/**
	 * Prints a time length to a string builder
	 *
	 * @param length The length of time in milliseconds
	 * @param sb The StringBuilder to write the representation of the time length into
	 * @param abbrev Whether to shorten the string intelligibly as much as possible
	 * @return The printed time length
	 */
	public static StringBuilder printTimeLength(long length, StringBuilder sb, boolean abbrev) {
		return printTimeLength(length / 1000, ((int) (length % 1000)) * 1000000, sb, abbrev);
	}

	/**
	 * Prints a time length to a string
	 *
	 * @param d The length of time
	 * @param abbrev Whether to shorten the string intelligibly as much as possible
	 * @return The representation of the time length
	 */
	public static String printDuration(Duration d, boolean abbrev) {
		return printDuration(d, new StringBuilder(), abbrev).toString();
	}

	/**
	 * Prints a time length to a StringBuilder
	 *
	 * @param d The length of time
	 * @param sb The StringBuilder to append into
	 * @param abbrev Whether to shorten the string intelligibly as much as possible
	 * @return The representation of the time length
	 */
	public static StringBuilder printDuration(Duration d, StringBuilder sb, boolean abbrev) {
		return printTimeLength(d.getSeconds(), d.getNano(), sb, abbrev);
	}

	/**
	 * Prints a time length to a StringBuilder
	 *
	 * @param seconds The length of time in seconds
	 * @param nanos Additional nanoseconds past the seconds
	 * @param sb The StringBuilder to append into
	 * @param abbrev Whether to shorten the string intelligibly as much as possible
	 * @return The representation of the time length
	 */
	public static StringBuilder printTimeLength(long seconds, int nanos, StringBuilder sb, boolean abbrev) {
		if (seconds == 0 && nanos == 0) {
			sb.append("no time");
			return sb;
		} else if (seconds < 0) {
			seconds = -seconds;
			nanos = -nanos;
			sb.append('-');
		}
		if (nanos < 0) {
			if (seconds == 0) {
				nanos = -nanos;
				sb.append('-');
			} else {
				seconds--;
				nanos += 1_000_000_000;
			}
		}
		int days, hrs, mins, secs, millis;
		millis = nanos / 1000000;
		nanos %= 1000000;
		secs = (int) (seconds % 60);
		seconds /= 60;
		mins = (int) (seconds % 60);
		seconds /= 60;
		hrs = (int) (seconds % 24);
		seconds /= 24;
		days = (int) seconds;
		if (days > 0) {
			sb.append(days);
			if (abbrev)
				sb.append("d ");
			else if (days > 1)
				sb.append(" days ");
			else
				sb.append(" day ");
		}
		if (hrs > 0) {
			sb.append(hrs);
			if (abbrev)
				sb.append("h ");
			else if (hrs > 1)
				sb.append(" hours ");
			else
				sb.append(" hour ");
		}
		if (mins > 0) {
			sb.append(mins);
			if (abbrev)
				sb.append("m ");
			else if (mins > 1)
				sb.append(" minutes ");
			else
				sb.append(" minute ");
		}
		if (secs > 0) {
			sb.append(secs);
			if (millis > 0) {
				sb.append('.');
				if (millis < 100)
					sb.append('0');
				if (millis < 10)
					sb.append('0');
				sb.append(millis);
				millis = 0;
			}
			if (abbrev)
				sb.append("s ");
			else if (secs > 1)
				sb.append(" seconds ");
			else
				sb.append(" second ");
		}
		if (millis > 0) {
			sb.append(millis);
			if (abbrev)
				sb.append("ms ");
			else if (millis > 1)
				sb.append(" millis ");
			else
				sb.append(" milli ");
		}
		if (nanos > 0) {
			sb.append(nanos);
			if (abbrev)
				sb.append(" ns ");
			else if (millis > 1)
				sb.append(" nanos ");
			else
				sb.append(" nano ");
		}
		if (sb.charAt(sb.length() - 1) == ' ')
			sb.deleteCharAt(sb.length() - 1);
		return sb;
	}

	/**
	 * Parses a time interval expressed in english. Some examples:
	 * <ul>
	 * <li>5min</li>
	 * <li>10 sec</li>
	 * <li>3d (3 days)</li>
	 * <li>2 weeks</li>
	 * <li>6mo (6 months)</li>
	 * <li>1.5y</li>
	 * </ul>
	 * <p>
	 * As shown, the number may have a decimal and the unit may or may not be separated by white space, may be abbreviated back to a single
	 * character (except months, which may have 2 characters) or be spelled out completely. Case is insensitive. If no unit is specified,
	 * minutes are assumed. Supported units are seconds, minutes, hours, days, weeks, months, and years.
	 * </p>
	 * <p>
	 * Negative values are also supported and returned as such. An empty string is interpreted as a zero-length time interval. If no unit is
	 * given and no decimal is present in the number, the value is assumed to be in milliseconds if the value is a multiple of 250 or of
	 * 100, or in seconds otherwise. To avoid problems that may arise from incorrect assumptions of the unit, use units explicitly.
	 * </p>
	 *
	 * @param time The time to parse
	 * @return The parsed time amount, in milliseconds
	 * @throws IllegalArgumentException If the time cannot be parsed
	 */
	public static long parseEnglishTime(String time) {
		time = time.trim();
		if (time.length() == 0)
			return 0;
		StringBuilder num = new StringBuilder();
		int c = 0;
		boolean neg = time.charAt(0) == '-';
		if (neg)
			c++;
		for (; c < time.length() && ((time.charAt(c) >= '0' && time.charAt(c) <= '9') || time.charAt(c) == '.'); c++)
			num.append(time.charAt(c));

		if (num.length() == time.length() - (neg ? 1 : 0)) { // No unit specified
			boolean hasDec = false;
			for (c = 0; c < num.length() && !hasDec; c++)
				hasDec = num.charAt(c) == '.';

			if (!hasDec) {
				long ret;
				try {
					ret = Long.parseLong(num.toString());
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Could not parse numeric time " + time, e);
				}
				if (ret % 250 != 0 && ret % 100 != 0)
					ret *= 1000;
				if (neg)
					ret = -ret;
				return ret;
			}
		}
		float scalar;
		try {
			scalar = Float.parseFloat(num.toString());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Could not parse numeric part of time " + time, e);
		}
		while (c < time.length() && Character.isWhitespace(time.charAt(c)))
			c++;
		String unit = time.substring(c).trim().toLowerCase();
		if (unit.length() > 1 && unit.charAt(unit.length() - 1) == 's')
			unit = unit.substring(0, unit.length() - 1);

		long mult;
		if (unit.startsWith("s"))
			mult = 1000;
		else if (unit.equals("m") || unit.startsWith("min"))
			mult = 60000;
		else if (unit.startsWith("h"))
			mult = 60L * 60000;
		else if (unit.startsWith("d"))
			mult = 24L * 60 * 60000;
		else if (unit.startsWith("w"))
			mult = 7L * 24 * 60 * 60000;
		else if (unit.startsWith("mo"))
			mult = 30L * 24 * 60 * 60000;
		else if (unit.startsWith("y"))
			mult = 365L * 24 * 60 * 60000;
		else
			throw new IllegalArgumentException("Could not parse unit part of time " + time);
		if (neg)
			scalar = -scalar;
		return Math.round((double) scalar * mult);
	}

	/**
	 * Parses number/unit pairs either adjacent or separated by whitespace and adds them together. Units recognized are:
	 * <ul>
	 * <li>year/yr/y (interpreted as 365.25 days)</li>
	 * <li>month/mo (interpreted as 30 days)</li>
	 * <li>week/wk/w</li>
	 * <li>day/dy/d</li>
	 * <li>hour/hr/h</li>
	 * <li>minute/min/m</li>
	 * <li>second/sec/s</li>
	 * <li>millisecond/milli/ms</li>
	 * <li>nanosecond/nano/ns</li>
	 * </ul>
	 * 
	 * The plural 's' is accepted on any units > 1 character
	 * 
	 * @param text The text to parse
	 * @return The duration specified in the text
	 * @throws ParseException If the specified text is unrecognized as a duration
	 */
	public static Duration parseDuration(CharSequence text) throws ParseException {
		// return TimeUtils.parseDuration(text).asDuration(); TODO Uncomment this when the componentized parser is working
		Duration duration = Duration.ZERO;
		int c = 0;
		if (c < text.length() && Character.isWhitespace(text.charAt(c)))
			c++;
		boolean neg = c < text.length() && text.charAt(c) == '-';
		if (neg)
			c++;
		StringBuilder unit = new StringBuilder();
		boolean hadContent = false;
		for (; c < text.length(); c++) {
			if (c < text.length() && Character.isWhitespace(text.charAt(c)))
				c++;
			if (c == text.length())
				break;
			hadContent = true;
			int valueStart = c;
			long value = 0;
			while (c < text.length() && text.charAt(c) >= '0' && text.charAt(c) <= '9') {
				if (c - valueStart > 19)
					throw new ParseException("Too many digits in value", c);
				value = value * 10 + (text.charAt(c) - '0');
				c++;
			}
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
				duration = duration.plus(Duration.ofSeconds((long) (value * 365.25 * 24 * 60 * 60)));
				break;
			case "mo":
			case "month":
				duration = duration.plus(Duration.ofSeconds(value * 30 * 24 * 60 * 60));
				break;
			case "w":
			case "wk":
			case "week":
				duration = duration.plus(Duration.ofSeconds(value * 7 * 24 * 60 * 60));
				break;
			case "d":
			case "dy":
			case "day":
				duration = duration.plus(Duration.ofSeconds(value * 24 * 60 * 60));
				break;
			case "h":
			case "hr":
			case "hour":
				duration = duration.plus(Duration.ofSeconds(value * 60 * 60));
				break;
			case "m":
			case "min":
			case "minute":
				duration = duration.plus(Duration.ofSeconds(value * 60));
				break;
			case "s":
			case "sec":
			case "second":
				if (value > 0)
					duration = duration.plus(Duration.ofSeconds(value));
				if (!Double.isNaN(decimal))
					duration = duration.plus(Duration.ofNanos((long) (decimal * 1000000000)));
				break;
			case "ms":
			case "milli":
			case "millisecond":
				if (value > 0)
					duration = duration.plus(Duration.ofNanos(value * 1000000));
				if (!Double.isNaN(decimal))
					duration = duration.plus(Duration.ofNanos((long) (decimal * 1000000)));
				break;
			case "ns":
			case "nano":
			case "nanosecond":
				duration = duration.plus(Duration.ofNanos(value));
				break;
			}
		}
		if (!hadContent)
			throw new ParseException("No content to parse", c);
		if (neg)
			duration = duration.negated();
		return duration;
	}

	private static final long MINUTE_LENGTH = 60000;
	private static final long HOUR_LENGTH = 60 * MINUTE_LENGTH;
	private static final long DAY_LENGTH = HOUR_LENGTH * 24;
	private static final long YEAR_LENGTH = DAY_LENGTH * 365;

	static enum TimeDifferenceClass {
		HOUR, DAY, YEAR;

		static TimeDifferenceClass of(long millisDiff) {
			if (millisDiff < DAY_LENGTH - HOUR_LENGTH)
				return HOUR;
			else if (millisDiff < YEAR_LENGTH - DAY_LENGTH)
				return DAY;
			else
				return YEAR;
		}
	}

	private static final ThreadLocal<Map<BiTuple<TimePrecision, TimeDifferenceClass>, SimpleDateFormat>> REL_TIME_FORMATS = new ThreadLocal<Map<BiTuple<TimePrecision, TimeDifferenceClass>, SimpleDateFormat>>() {
		@Override
		protected Map<BiTuple<TimePrecision, TimeDifferenceClass>, SimpleDateFormat> initialValue() {
			return new HashMap<>();
		}
	};

	private static SimpleDateFormat getDifferenceFormat(TimePrecision precision, TimeDifferenceClass diffClass) {
		StringBuilder format = new StringBuilder();
		if (diffClass.compareTo(TimeDifferenceClass.DAY) >= 0) {
			format.append("ddMMM");
			if (diffClass.compareTo(TimeDifferenceClass.YEAR) >= 0)
				format.append("yyyy");
		}
		if (format.length() > 0)
			format.append(' ');
		if (precision.compareTo(TimePrecision.MINUTES) >= 0)
			format.append("HH:mm");
		if (precision.compareTo(TimePrecision.SECONDS) >= 0)
			format.append(":ss");
		if (precision.compareTo(TimePrecision.MILLIS) >= 0)
			format.append(".SSS");
		return new SimpleDateFormat(format.toString());
	}

	/**
	 * <p>
	 * Prints a time in a human-readable format relative to a reference time. This method produces very natural string whose format varies
	 * depending on how far removed the given time is from the reference.
	 * </p>
	 * <p>
	 * For example, if the time is less than a day removed from the reference and a precision of seconds is given, the result will be
	 * formatted like "HH:mm". If it is more than a day removed but less than a year, the day and month ("ddMMM") will be prepended. If more
	 * than a year removed, the string will be absolute formatted like "ddMMMyyyy HH:mm".
	 * </p>
	 * 
	 * @param time The time to print
	 * @param referenceTime The reference time relative to which to print the given time
	 * @param precision The maximum precision to print the time in
	 * @param timeZone The timezone to use
	 * @param justNowThreshold A time difference threshold below which the given justNow string will be returned
	 * @param justNow A string to return if the difference between the time and the reference is below either the given justNowThreshold or
	 *        the given precision
	 * @return The relative-printed string
	 */
	public static String printRelativeTime(long time, long referenceTime, TimePrecision precision, TimeZone timeZone, long justNowThreshold,
		String justNow) {
		long diff = time - referenceTime;
		if (diff < 0)
			diff = -diff;
		else if (diff <= justNowThreshold && justNow != null)
			return justNow;

		TimeDifferenceClass diffClass = TimeDifferenceClass.of(diff);
		if (diffClass == TimeDifferenceClass.HOUR) {
			switch (precision) {
			case DAYS:
				if (justNow != null)
					return justNow;
				else
					precision = TimePrecision.MINUTES;
				break;
			case MINUTES:
				if (justNow != null && diff < MINUTE_LENGTH)
					return justNow;
				break;
			case SECONDS:
				if (justNow != null && diff < 1000)
					return justNow;
				break;
			case MILLIS:
				break;
			}
			if (precision == TimePrecision.DAYS) {
				if (justNow != null)
					return justNow;
				else
					precision = TimePrecision.MINUTES;
			}
		}
		SimpleDateFormat format = REL_TIME_FORMATS.get().computeIfAbsent(new BiTuple<>(precision, diffClass),
			t -> getDifferenceFormat(t.getValue1(), t.getValue2()));
		format.setTimeZone(timeZone);
		return format.format(new Date(time));
	}

	/**
	 * Suppresses print operations to {@link System#out} or {@link System#err} by replacing the target stream with a dud temporarily
	 * 
	 * @param err Whether to suppress {@link System#err} or {@link System#out}
	 * @return The transaction to {@link Transaction#close()} when stream suppression should cease
	 */
	public static Transaction suppressSystemPrint(boolean err) {
		PrintStream oldStream = err ? System.err : System.out;
		PrintStream dud = new PrintStream(new OutputStream() {
			@Override
			public void write(int b) throws IOException {
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
			}
		});
		if (err)
			System.setErr(dud);
		else
			System.setOut(dud);
		return () -> {
			if (err)
				System.setErr(oldStream);
			else
				System.setOut(oldStream);
		};
	}

	/**
	 * @param start The start time of the interval whose end time to print
	 * @param end The end time of the interval to print
	 * @param timeZone The timezone to print in
	 * @param calField The calendar field precision to print to
	 * @return A string representing the interval's end time in a context where the start time is known. This results in a shorter string
	 *         that is quicker to read.
	 */
	public static String printEndTime(long start, long end, java.util.TimeZone timeZone, int calField) {
		Calendar sc = Calendar.getInstance();
		if (timeZone != null)
			sc.setTimeZone(timeZone);
		sc.setTimeInMillis(start);
		Calendar ec = Calendar.getInstance();
		if (timeZone != null)
			ec.setTimeZone(timeZone);
		ec.setTimeInMillis(end);

		StringBuilder ret = new StringBuilder();
		int length = 0;
		switch (calField) {
		case Calendar.MILLISECOND:
			ret.append(ec.get(Calendar.MILLISECOND));
			length += 3;
			while (ret.length() < length)
				ret.insert(0, '0');
			//$FALL-THROUGH$
		case Calendar.SECOND:
			ret.insert(0, ec.get(Calendar.SECOND));
			length += 3;
			while (ret.length() < length)
				ret.insert(0, '0');
			if (end - start < 60L * 1000 && sc.get(Calendar.MINUTE) == ec.get(Calendar.MINUTE))
				return ret.toString();
			//$FALL-THROUGH$
		case Calendar.MINUTE:
		case Calendar.HOUR_OF_DAY:
			ret.insert(0, ec.get(Calendar.MINUTE));
			length += 2;
			while (ret.length() < length)
				ret.insert(0, '0');
			ret.insert(0, ':');
			ret.insert(0, ec.get(Calendar.HOUR_OF_DAY));
			length += 3;
			while (ret.length() < length)
				ret.insert(0, '0');
			if (end - start < 24L * 60 * 60 * 1000 && sc.get(Calendar.DAY_OF_MONTH) == ec.get(Calendar.DAY_OF_MONTH))
				return ret.toString();
			//$FALL-THROUGH$
		case Calendar.DAY_OF_MONTH:
			int day = ec.get(Calendar.DAY_OF_MONTH);
			ret.insert(0, day);
			length += 2;
			while (ret.length() < length)
				ret.insert(0, '0');
			if (end - start < 31L * 24 * 60 * 60 * 1000 && sc.get(Calendar.MONTH) == ec.get(Calendar.MONTH)) {
				String suffix;
				if (day / 10 == 1)
					suffix = "th";
				else if (day % 10 == 1)
					suffix = "st";
				else if (day % 10 == 2)
					suffix = "nd";
				else if (day % 10 == 3)
					suffix = "rd";
				else
					suffix = "th";
				ret.insert(2, suffix);
				return ret.toString();
			}
			//$FALL-THROUGH$
		case Calendar.MONTH:
			String[] months = new String[] { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };
			ret.insert(2, months[ec.get(Calendar.MONTH)]);
			length += 3;
			if (sc.get(Calendar.YEAR) == ec.get(Calendar.YEAR))
				return ret.toString();
			//$FALL-THROUGH$
		case Calendar.YEAR:
			return print(end);
		}
		return ret.toString();
	}

	/**
	 * @param <T> The type of values in the list
	 * @param values The values to include
	 * @return An unmodifiable copy of the given list
	 */
	public static <T> List<T> unmodifiableCopy(Collection<? extends T> values) {
		if (values == null || values.isEmpty())
			return Collections.emptyList();
		else if (values.size() == 1)
			return Collections.singletonList(values.iterator().next());
		return new SimpleImmutableList<>(values);
	}

	/**
	 * @param <T> The type of values in the set
	 * @param values The values to include
	 * @return An unmodifiable set containing all values in the given collection
	 */
	public static <T> Set<T> unmodifiableDistinctCopy(Collection<? extends T> values) {
		if (values == null || values.isEmpty())
			return Collections.emptySet();
		else if (values.size() == 1)
			return Collections.singleton(values.iterator().next());
		LinkedHashSet<T> set = new LinkedHashSet<>(values.size() * 3 / 2 + 1);
		set.addAll(values);
		return Collections.unmodifiableSet(set);
	}

	/**
	 * @param <T> The type of values in the list
	 * @param values The values to include
	 * @return An unmodifiable copy of the given list
	 */
	public static <T> List<T> unmodifiableCopy(T... values) {
		if (values == null || values.length == 0)
			return Collections.emptyList();
		else if (values.length == 0)
			return Collections.singletonList(values[0]);
		return new SimpleImmutableList<>(values);
	}

	/**
	 * @param <T> The type of values in the list
	 * @param values The values to include
	 * @return An unmodifiable set containing all values in the given array
	 */
	public static <T> Set<T> unmodifiableDistinctCopy(T... values) {
		if (values == null || values.length == 0)
			return Collections.emptySet();
		else if (values.length == 0)
			return Collections.singleton(values[0]);
		LinkedHashSet<T> set = new LinkedHashSet<>(values.length * 3 / 2 + 1);
		for (T v : values)
			set.add(v);
		return Collections.unmodifiableSet(set);
	}
	/**
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to create the copy of
	 * @return An unmodifiable copy of the given map
	 */
	public static <K, V> Map<K, V> unmodifiableCopy(Map<? extends K, ? extends V> map) {
		if (map == null || map.isEmpty())
			return Collections.emptyMap();
		else if (map.size() == 1) {
			Map.Entry<? extends K, ? extends V> entry = map.entrySet().iterator().next();
			return Collections.singletonMap(entry.getKey(), entry.getValue());
		}
		Map<K, V> copy = new LinkedHashMap<>(map.size() * 2);
		copy.putAll(map);
		return Collections.unmodifiableMap(copy);
	}

	/**
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to populate (null to create a new one)
	 * @return A builder to populate the map
	 */
	public static <K, V> MapBuilder<K, V> buildMap(Map<K, V> map) {
		return new MapBuilder<>(map);
	}

	/**
	 * Populates a map
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	public static class MapBuilder<K, V> {
		private final Map<K, V> theMap;

		MapBuilder(Map<K, V> map) {
			theMap = map == null ? new LinkedHashMap<>() : map;
		}

		/**
		 * @param key The key to insert
		 * @param value The value to insert for the key
		 * @return This builder
		 */
		public MapBuilder<K, V> with(K key, V value) {
			theMap.put(key, value);
			return this;
		}

		/** @return The map */
		public Map<K, V> get() {
			return theMap;
		}

		/** @return The unmodifiable map */
		public Map<K, V> getUnmodifiable() {
			return Collections.unmodifiableMap(theMap);
		}
	}

	/**
	 * @param <T> The type of the source values
	 * @param <V> The type of the mapped values
	 * @param values The values to map
	 * @param map The mapping function
	 * @param unmodifiable Whether the result should be unmodifiable
	 * @return The list of mapped values
	 */
	public static <T, V> List<V> map(Collection<? extends T> values, Function<? super T, ? extends V> map, boolean unmodifiable) {
		if (values.isEmpty())
			return unmodifiable ? Collections.emptyList() : new ArrayList<>(5);
		ArrayList<V> list = new ArrayList<>(values.size());
		for (T value : values)
			list.add(map.apply(value));
		return unmodifiable ? Collections.unmodifiableList(list) : list;
	}

	/**
	 * Same as {@link #map(Collection, Function, boolean)} but produces an unmodifiable {@link BetterList}
	 * 
	 * @param <T> The type of the source values
	 * @param <V> The type of the mapped values
	 * @param values The values to map
	 * @param map The mapping function
	 * @return The BetterList of mapped values
	 */
	public static <T, V> BetterList<V> map2(Collection<? extends T> values, Function<? super T, ? extends V> map) {
		if (values.isEmpty())
			return BetterList.empty();
		ArrayList<V> list = new ArrayList<>(values.size());
		for (T value : values)
			list.add(map.apply(value));
		return BetterList.of(list);
	}

	/**
	 * @param <T> The type of the source values
	 * @param <V> The type of the mapped values
	 * @param values The values to map
	 * @param filter The source value filter, may be null to use all source values
	 * @param map The mapping function, may be null if source and value types are the same
	 * @return An unmodifiable BetterList containing all source values passing the given filter, mapped with the given map
	 */
	public static <T, V> BetterList<V> filterMap(Collection<? extends T> values, Predicate<? super T> filter,
		Function<? super T, ? extends V> map) {
		if (values.isEmpty())
			return BetterList.empty();
		ArrayList<V> list = new ArrayList<>(values.size());
		for (T value : values) {
			if (filter == null || filter.test(value))
				list.add(map == null ? (V) value : map.apply(value));
		}
		list.trimToSize();
		return BetterList.of(list);
	}

	/**
	 * @param <T> The type of the source values
	 * @param <V> The type of the mapped values
	 * @param <FE> The type of exception that may be thrown by the filter
	 * @param <ME> The type of exception that may be thrown by the map
	 * @param values The values to map
	 * @param filter The source value filter, may be null to use all source values
	 * @param map The mapping function, may be null if source and value types are the same
	 * @return An unmodifiable BetterList containing all source values passing the given filter, mapped with the given map
	 * @throws FE If the filter throws an exception
	 * @throws ME If the map throws an exception
	 */
	public static <T, V, FE extends Throwable, ME extends Throwable> BetterList<V> filterMapE(Collection<? extends T> values,
		ExPredicate<? super T, ? extends FE> filter, ExFunction<? super T, ? extends V, ? extends ME> map) throws FE, ME {
		if (values.isEmpty())
			return BetterList.empty();
		ArrayList<V> list = new ArrayList<>(values.size());
		for (T value : values) {
			if (filter == null || filter.test(value))
				list.add(map == null ? (V) value : map.apply(value));
		}
		list.trimToSize();
		return BetterList.of(list);
	}

	/**
	 * A comparator that returns the result of {@link #compareNumberTolerant(String, String, boolean, boolean)
	 * compareNumberTolerant}<code>(s1, s2, true, true)</code>
	 */
	public static final Comparator<CharSequence> DISTINCT_NUMBER_TOLERANT = StringUtils.DISTINCT_NUMBER_TOLERANT;

	/**
	 * Compares two strings in such a way that strings with embedded multi-digit numbers in the same position are sorted intuitively.
	 * 
	 * @param s1 The first string to compare
	 * @param s2 The second string to compare
	 * @param ignoreCase Whether to ignore case for the comparison
	 * @param onlyZeroIfEqual If this is true, the method will revert to a number-intolerant, case-attentive comparison if the strings are
	 *        equivalent with number tolerance and case-ignorance (if <code>ignoreCase==true</code>). If true, this method will only return
	 *        zero if <code>s1.equals(s2)</code>.
	 * @return
	 *         <ul>
	 *         <li><b>-1</b> if <code>s1 &lt; s2</code></li>
	 *         <li><b>1</b> if <code>s1 &gt; s2</code></li>
	 *         <li><b>0</b> if <code>s1 == s2</code></li>
	 *         </ul>
	 */
	public static int compareNumberTolerant(String s1, String s2, boolean ignoreCase, boolean onlyZeroIfEqual) {
		return StringUtils.compareNumberTolerant(s1, s2, ignoreCase, onlyZeroIfEqual);
	}

	private static boolean checkedGroupsGetter;
	private static Method PATTERN_NAMED_GROUPS_GETTER;

	/**
	 * <p>
	 * Gets named capturing groups configured for a pattern.
	 * </p>
	 * <p>
	 * <b>WARNING:</b>{@link Pattern} does not expose this information, so it must be reflected from a package-private method.
	 * 
	 * This will fail if security is set up against this. In this case, this method will throw an IlS
	 * 
	 * @param pattern The pattern
	 * @return A map of the names of each named capture group in the pattern to the group index in the matcher
	 * @throws IllegalStateException If the information cannot be accessed
	 */
	public static Map<String, Integer> getCaptureGroupNames(Pattern pattern) throws IllegalStateException {
		if (PATTERN_NAMED_GROUPS_GETTER == null) {
			if (!checkedGroupsGetter) {
				checkedGroupsGetter = true;
				Method getter;
				try {
					// Yes, I know this causes an "Illegal reflective access" warning. I can't help it.
					// There's no other way to get this information.
					getter = Pattern.class.getDeclaredMethod("namedGroups");
					getter.setAccessible(true);
				} catch (NoSuchMethodException | SecurityException e) {
					throw new IllegalStateException("Could not get or access Pattern named group getter", e);
				}
				PATTERN_NAMED_GROUPS_GETTER = getter;
			} else
				throw new IllegalStateException("Could not get or access Pattern named group getter");
			return Collections.emptyMap();
		}
		try {
			return (Map<String, Integer>) PATTERN_NAMED_GROUPS_GETTER.invoke(pattern);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			System.err.println("Could not invoke Pattern named group getter");
			return Collections.emptyMap();
		}
	}

	/**
	 * @param matcher The matcher
	 * @return A map of capture group name to capture group value for each named capture group in the macher's pattern
	 */
	public static Map<String, NamedGroupCapture> getCaptureGroups(Matcher matcher) {
		Map<String, Integer> captureGroupNames = getCaptureGroupNames(matcher.pattern());
		if (captureGroupNames.isEmpty())
			return Collections.emptyMap();
		Map<String, NamedGroupCapture> captureGroups = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> cg : captureGroupNames.entrySet()) {
			int start = matcher.start(cg.getValue());
			if (start >= 0)
				captureGroups.put(cg.getKey(), new NamedGroupCapture(start, matcher.group(cg.getValue())));
		}
		return captureGroups;
	}

	/** Represents the capture of a named group in a pattern-matched text sequence */
	public static class NamedGroupCapture {
		/** The start of the captured sequence within the whole */
		public final int start;
		/** The matched group sequence */
		public final String value;

		/**
		 * @param start The start of the captured sequence within the whole
		 * @param value The matched group sequence
		 */
		public NamedGroupCapture(int start, String value) {
			this.start = start;
			this.value = value;
		}

		@Override
		public int hashCode() {
			return value.hashCode() ^ start;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof NamedGroupCapture))
				return false;
			NamedGroupCapture other = (NamedGroupCapture) obj;
			return start == other.start && value.equals(other.value);
		}

		@Override
		public String toString() {
			return value;
		}
	}

	/**
	 * Encodes all unicode special characters in a string to an ascii form. This method is necessary because the mechanism which PRISMS uses
	 * to send strings from server to client does not correctly account for these characters. They must then be decoded on the client.
	 *
	 * @param str The string to encode the unicode characters in before passing to the client
	 * @return The original string with all the unicode characters encoded to "\\u****" as they would be typed in java code
	 */
	public static String encodeUnicode(String str) {
		int c;
		for (c = 0; c < str.length() && str.codePointAt(c) <= 0x7f; c++) {}
		if (c == str.length())
			return str;

		StringBuilder ret = new StringBuilder(str);
		encodeUnicode(ret);
		return ret.toString();
	}

	/**
	 * @param str The sequence to encode
	 * @return The number of characters replaced by their unicode equivalent strings
	 */
	public static int encodeUnicode(StringBuilder str) {
		int ret = 0;
		int c;
		for (c = 0; c < str.length() && str.codePointAt(c) <= 0x7f; c++) {}
		if (c == str.length())
			return ret;

		for (; c < str.length(); c++) {
			int ch = str.codePointAt(c);
			if (ch > 0x7f) {
				ret++;
				str.setCharAt(c, '\\');
				c++;
				str.insert(c, "u0000");
				c++;
				String hexString = Integer.toHexString(ch);
				c += 4 - hexString.length();
				for (int i = 0; i < hexString.length(); i++)
					str.setCharAt(c++, hexString.charAt(i));
			}
		}
		return ret;
	}

	/**
	 * Decodes a string encoded by {@link #encodeUnicode(String)}. Returns all encoded unicode special characters in a string to their
	 * unicode characters from the ascii form. This method is necessary because the mechanism which PRISMS uses to send strings from server
	 * to client and back does not correctly account for these characters. They must then be decoded on the client.
	 *
	 * @param str The string to decode the unicode characters from
	 * @return The original string with all the unicode characters decoded from "\\u****" as they would be typed in java code
	 */
	public static String decodeUnicode(String str) {
		if (str == null)
			return null;
		int index = str.indexOf("\\u");
		if (index >= 0) {
			int idx2 = str.indexOf("\\\\u");
			if (idx2 < index)
				index = idx2;
			StringBuilder sb = new StringBuilder();
			charLoop: for (int c = 0; c < str.length(); c++) {
				if (c >= index && c < str.length() - 5 && str.charAt(c) == '\\') {
					final int preC = c;
					if (str.charAt(c + 1) == 'u')
						c += 2;
					else if (c < str.length() - 6 && str.charAt(c + 1) == '\\' && str.charAt(c + 2) == 'u')
						c += 3;
					else {
						sb.append(str.charAt(c));
						continue;
					}
					int code = 0;
					for (int i = 0; i < 4; i++) {
						int hex = fromHex(str.charAt(c));
						if (hex < 0) { // Doesn't match \\uXXXX--don't adjust
							c = preC;
							sb.append(str.charAt(c));
							continue charLoop;
						}
						code = code * 16 + hex;
						c++;
					}
					c--;
					char[] codeChars = Character.toChars(code);
					for (char ch : codeChars)
						sb.append(ch);
				} else
					sb.append(str.charAt(c));
			}
			str = sb.toString();
		}
		return str;
	}

	/**
	 * @param c The hex digit to return the value of
	 * @return The 0-15 value of the hex digit
	 */
	public static int fromHex(char c) {
		if (c >= '0' && c <= '9')
			return c - '0';
		else if (c >= 'a' && c <= 'f')
			return c - 'a' + 10;
		else if (c >= 'A' && c <= 'F')
			return c - 'A' + 10;
		else
			return -1;
	}

	/**
	 * Replaces all instances of a string with its replacement. This method functions differently than
	 * {@link String#replaceAll(String, String)} in that the strings are replaced literally instead of being interpreted as regular
	 * expressions.
	 *
	 * @param str The string to replace content in
	 * @param srch The content to replace
	 * @param replacement The content to replace <code>srch</code> with
	 * @return The replaced string
	 */
	public static String replaceAll(String str, String srch, String replacement) {
		if (str.indexOf(srch) < 0)
			return str;
		StringBuilder ret = new StringBuilder(str);
		replaceAll(ret, srch, replacement);
		return ret.toString();
	}

	/**
	 * Like {@link #replaceAll(String, String, String)}, but more efficient for string builders
	 *
	 * @param str The string builder to replace content in
	 * @param srch The content to replace
	 * @param replacement The content to replace <code>srch</code> with
	 * @return The number of times <code>srch</code> was found and replaced in <code>str</code>
	 */
	public static int replaceAll(StringBuilder str, String srch, String replacement) {
		if (srch.length() == 0)
			return 0;
		if (replacement == null)
			replacement = "";
		String sub = null;
		if (replacement.length() > srch.length())
			sub = replacement.substring(srch.length());
		int ret = 0;
		for (int i = 0; i <= str.length() - srch.length(); i++) {
			int j;
			for (j = 0; j < srch.length() && str.charAt(i + j) == srch.charAt(j); j++) {}
			if (j == srch.length()) {
				ret++;
				for (j = 0; j < srch.length() && j < replacement.length(); j++)
					str.setCharAt(i + j, replacement.charAt(j));
				if (srch.length() > replacement.length())
					str.delete(i + replacement.length(), i + srch.length());
				else if (replacement.length() > srch.length())
					str.insert(i + srch.length(), sub);
				i += replacement.length() - 1;
			}
		}
		return ret;
	}

	/**
	 * A utility to assist with optimizing for code coverage. May be used instead of <code>
	 * if(condition)
	 * 		throw SomeException(message);
	 * </code> without generating code coverage holes because the condition never occurs
	 * 
	 * @param <X> The type of the exception to throw
	 * @param condition The condition to test
	 * @param ex Produces the exception
	 * @param message The message for the exception
	 * @throws X If the condition is false
	 */
	public static <X extends Throwable> void assertThat(boolean condition, Function<String, X> ex, Object... message) throws X {
		if (message.length == 0)
			throw new IllegalArgumentException("Need a message");
		else if (condition)
			return;
		throw ex.apply(createMessage(message));
	}

	/**
	 * A utility to assist with optimizing for code coverage. May be used instead of <code>
	 * if(condition)
	 * 		throw SomeException(message);
	 * </code> without generating code coverage holes because the condition never occurs
	 * 
	 * @param condition The condition to test
	 * @param arg Whether to throw an {@link IllegalArgumentException} or an {@link IllegalStateException}
	 * @param message The message for the exception
	 * @throws IllegalArgumentException If the condition is false and arg is true
	 * @throws IllegalStateException If the condition is false and arg is false
	 */
	public static void assertThat(boolean condition, boolean arg, Object... message)
		throws IllegalArgumentException, IllegalStateException {
		if (message.length == 0)
			throw new IllegalArgumentException("Need a message");
		if (condition)
			return;
		String msg = createMessage(message);
		if (arg)
			throw new IllegalArgumentException(msg);
		else
			throw new IllegalStateException(msg);
	}

	private static String createMessage(Object... message) {
		if (message.length == 1 && message[0] instanceof String)
			return (String) message[0];
		else {
			StringBuilder str = new StringBuilder();
			for (Object m : message)
				str.append(m);
			return str.toString();
		}
	}

	/**
	 * @param index The index to check
	 * @param max The maximum index
	 * @param maxOk Whether index==max is ok
	 * @param message The message for the exception
	 * @throws IndexOutOfBoundsException If index&lt;0, index>max, or !maxOk and index==max
	 */
	public static void checkIndex(int index, int max, boolean maxOk, Object... message) throws IndexOutOfBoundsException {
		if (index < 0 || (index > max) || (!maxOk && index == max)) {
			if (message.length == 0)
				throw new IndexOutOfBoundsException(index + " of " + max);
			else
				throw new IndexOutOfBoundsException(createMessage(message) + ": " + index + " of " + max);
		}
	}

	private static final long SIGN_MASK = 1L << 63;
	private static final long UNSIGNED_MASK = ~SIGN_MASK;

	/**
	 * <p>
	 * Compares the {@link Double#doubleToLongBits(double)} values of 2 doubles. Produces the same results as comparing the doubles directly
	 * except in the case of {@link Double#NaN NaN} values.
	 * </p>
	 * 
	 * <p>
	 * NaN values are treated as larger than {@link Double#POSITIVE_INFINITY infinity}. If both doubles are NaN, they will be equal (unless
	 * the long bits were generated with {@link Double#doubleToRawLongBits(double)}, in which case different NaN values may have different
	 * bits). If only 1 double is NaN, it will always be the largest.
	 * </p>
	 * 
	 * @param d1Bits The long bits of the first double (<code>d1</code>) to compare
	 * @param d2Bits The long bits of the second double (<code>d2</code>) to compare
	 * @return
	 *         <ul>
	 *         <li><b>-1</b> if <code>d1 &lt; d2</code></li>
	 *         <li><b>1</b> if <code>d1 &gt; d2</code></li>
	 *         <li><b>0</b> if <code>d1 == d2</code>
	 *         </ul>
	 */
	public static int compareDoubleBits(long d1Bits, long d2Bits) {
		boolean d1Neg = d1Bits < 0;
		boolean d2Neg = d2Bits < 0;
		if (d1Neg) {
			if (d2Neg) {
				d1Bits &= UNSIGNED_MASK;
				d2Bits &= UNSIGNED_MASK;
			} else
				return -1;
		} else if (d2Neg)
			return 1;

		if (d1Bits < d2Bits)
			return d1Neg ? 1 : -1;
		else if (d1Bits > d2Bits)
			return d1Neg ? -1 : 1;
		else
			return 0;
	}

	/**
	 * Modifies the stack trace to add information from the code that caused a Runnable task to be run. The result is as if the runnable run
	 * method was called directly instead of adding the task to be run later. This is beneficial because it gives information about what
	 * really caused the task to be run.
	 *
	 * @param innerTrace The stack trace of the exception that was thrown in response to an error
	 * @param outerTrace The stack trace of an exception that was created (but not thrown) outside the runnable task for reference
	 * @param className The name of the runnable class--typically calling this method with "getClass().getName()" is what is required
	 * @param methodName The name of the method called in the runnable class--equivalent to {@link Runnable#run()}
	 * @return The modified stack trace
	 */
	public static StackTraceElement[] patchStackTraces(StackTraceElement[] innerTrace, StackTraceElement[] outerTrace, String className,
		String methodName) {
		if (innerTrace == null || innerTrace.length == 0 || outerTrace == null || outerTrace.length == 0)
			return innerTrace;
		int i = innerTrace.length - 1;
		while (i >= 0 && !(innerTrace[i].getClassName().equals(className) && innerTrace[i].getMethodName().equals(methodName)))
			i--;
		if (i < 0)
			return innerTrace;
		StackTraceElement[] ret = new StackTraceElement[i + outerTrace.length + 1];
		System.arraycopy(innerTrace, 0, ret, 0, i + 1);
		System.arraycopy(outerTrace, 0, ret, i + 1, outerTrace.length);
		return ret;
	}
}

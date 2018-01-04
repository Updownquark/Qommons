package org.qommons;

import java.text.ParseException;
import java.time.Duration;
import java.util.Calendar;

/** Even more general utilities that I can't think where else to put */
public class QommonsUtils {
	/** Represents different precisions to which a time can be represented in text */
	public enum TimePrecision {
		/** Represents a time to days */
		DAYS("ddMMMyyyy", false), /** Represents a time to minutes */
		MINUTES("ddMMMyyyy HHmm"), /** Represents a time to seconds */
		SECONDS("ddMMMyyyy HH:mm:ss"), /** Represents a time to milliseconds */
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
			if(withZ)
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
			} catch(java.text.ParseException e) {
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
		while(ret.length() < chars) {
			int next = theRandom.nextInt();
			ret.append(Integer.toHexString(next));
		}
		if(ret.length() != chars)
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
	 * Prints a time length to a string builder
	 *
	 * @param length The length of time in milliseconds
	 * @param sb The StringBuilder to write the representation of the time length into
	 * @param abbrev Whether to shorten the string intelligibly as much as possible
	 */
	public static void printTimeLength(long length, StringBuilder sb, boolean abbrev) {
		if (length == 0) {
			sb.append("no time");
			return;
		} else if (length < 0) {
			length = -length;
			sb.append('-');
		}
		int days, hrs, mins, secs, millis;
		millis = (int) (length % 1000);
		length /= 1000;
		secs = (int) (length % 60);
		length /= 60;
		mins = (int) (length % 60);
		length /= 60;
		hrs = (int) (length % 24);
		length /= 24;
		days = (int) length;
		if(days > 0) {
			sb.append(days);
			if(abbrev)
				sb.append("d ");
			else if(days > 1)
				sb.append(" days ");
			else
				sb.append(" day ");
		}
		if(hrs > 0) {
			sb.append(hrs);
			if(abbrev)
				sb.append("h ");
			else if(hrs > 1)
				sb.append(" hours ");
			else
				sb.append(" hour ");
		}
		if(mins > 0) {
			sb.append(mins);
			if(abbrev)
				sb.append("m ");
			else if(mins > 1)
				sb.append(" minutes ");
			else
				sb.append(" minute ");
		}
		if(secs > 0) {
			sb.append(secs);
			if(millis > 0) {
				sb.append('.');
				if(millis < 100)
					sb.append('0');
				if(millis < 10)
					sb.append('0');
				sb.append(millis);
				millis = 0;
			}
			if(abbrev)
				sb.append("s ");
			else if(secs > 1)
				sb.append(" seconds ");
			else
				sb.append(" second ");
		}
		if(millis > 0) {
			sb.append(millis);
			if(abbrev)
				sb.append("mil ");
			else if(millis > 1)
				sb.append(" millis");
			else
				sb.append(" milli");
		}
		if (sb.charAt(sb.length() - 1) == ' ')
			sb.deleteCharAt(sb.length() - 1);
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
		if(time.length() == 0)
			return 0;
		StringBuilder num = new StringBuilder();
		int c = 0;
		boolean neg = time.charAt(0) == '-';
		if(neg)
			c++;
		for(; c < time.length() && ((time.charAt(c) >= '0' && time.charAt(c) <= '9') || time.charAt(c) == '.'); c++)
			num.append(time.charAt(c));

		if(num.length() == time.length() - (neg ? 1 : 0)) { // No unit specified
			boolean hasDec = false;
			for(c = 0; c < num.length() && !hasDec; c++)
				hasDec = num.charAt(c) == '.';

			if(!hasDec) {
				long ret;
				try {
					ret = Long.parseLong(num.toString());
				} catch(NumberFormatException e) {
					throw new IllegalArgumentException("Could not parse numeric time " + time, e);
				}
				if(ret % 250 != 0 && ret % 100 != 0)
					ret *= 1000;
				if(neg)
					ret = -ret;
				return ret;
			}
		}
		float scalar;
		try {
			scalar = Float.parseFloat(num.toString());
		} catch(NumberFormatException e) {
			throw new IllegalArgumentException("Could not parse numeric part of time " + time, e);
		}
		while(c < time.length() && Character.isWhitespace(time.charAt(c)))
			c++;
		String unit = time.substring(c).trim().toLowerCase();
		if(unit.length() > 1 && unit.charAt(unit.length() - 1) == 's')
			unit = unit.substring(0, unit.length() - 1);

		long mult;
		if(unit.startsWith("s"))
			mult = 1000;
		else if(unit.equals("m") || unit.startsWith("min"))
			mult = 60000;
		else if(unit.startsWith("h"))
			mult = 60L * 60000;
		else if(unit.startsWith("d"))
			mult = 24L * 60 * 60000;
		else if(unit.startsWith("w"))
			mult = 7L * 24 * 60 * 60000;
		else if(unit.startsWith("mo"))
			mult = 30L * 24 * 60 * 60000;
		else if(unit.startsWith("y"))
			mult = 365L * 24 * 60 * 60000;
		else
			throw new IllegalArgumentException("Could not parse unit part of time " + time);
		if(neg)
			scalar = -scalar;
		return Math.round((double) scalar * mult);
	}

	public static Duration parseDuration(CharSequence text) throws ParseException {
		Duration duration=Duration.ZERO;
		int c = 0;
		if (c < text.length() && Character.isWhitespace(text.charAt(c)))
			c++;
		boolean neg = c < text.length() && text.charAt(c) == '-';
		if (neg)
			c++;
		StringBuilder unit=new StringBuilder();
		for (; c < text.length(); c++) {
			if (c < text.length() && Character.isWhitespace(text.charAt(c)))
				c++;
			int valueStart=c;
			long value = 0;
			while (c < text.length() && text.charAt(c) >= '0' && text.charAt(c) <= '9') {
				if (c - valueStart > 19)
					throw new ParseException("Too many digits in value", c);
				value=value*10+(text.charAt(c)-'0');
				c++;
			}
			if (c < text.length() && Character.isWhitespace(text.charAt(c)))
				c++;
			int decimalStart = c;
			double decimal = Double.NaN;
			if (c < text.length() && text.charAt(c) == '.') {
				decimal=0;
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
			}
			if (c == decimalStart) {
				if(duration.isZero())
					throw new ParseException("Unrecognized duration", 0);
				else
					throw new ParseException("Numerical value expected", valueStart);
			}
			if (c < text.length() && Character.isWhitespace(text.charAt(c)))
				c++;
			
			int unitStart=c;
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
			switch(unit.toString()){
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
		if (neg)
			duration = duration.negated();
		return duration;
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
		if(timeZone != null)
			sc.setTimeZone(timeZone);
		sc.setTimeInMillis(start);
		Calendar ec = Calendar.getInstance();
		if(timeZone != null)
			ec.setTimeZone(timeZone);
		ec.setTimeInMillis(end);

		StringBuilder ret = new StringBuilder();
		int length = 0;
		switch (calField) {
		case Calendar.MILLISECOND:
			ret.append(ec.get(Calendar.MILLISECOND));
			length += 3;
			while(ret.length() < length)
				ret.insert(0, '0');
			//$FALL-THROUGH$
		case Calendar.SECOND:
			if(length > 0) {
				ret.insert(0, '.');
				length++;
			}
			ret.insert(0, ec.get(Calendar.SECOND));
			length += 3;
			while(ret.length() < length)
				ret.insert(0, '0');
			if(end - start < 60L * 1000 && sc.get(Calendar.MINUTE) == ec.get(Calendar.MINUTE))
				return ret.toString();
			//$FALL-THROUGH$
		case Calendar.MINUTE:
		case Calendar.HOUR_OF_DAY:
			if(length > 0) {
				ret.insert(0, ':');
				length++;
			}
			ret.insert(0, ec.get(Calendar.MINUTE));
			length += 2;
			while(ret.length() < length)
				ret.insert(0, '0');
			ret.insert(0, ':');
			ret.insert(0, ec.get(Calendar.HOUR_OF_DAY));
			length += 3;
			while(ret.length() < length)
				ret.insert(0, '0');
			if(end - start < 24L * 60 * 60 * 1000 && sc.get(Calendar.DAY_OF_MONTH) == ec.get(Calendar.DAY_OF_MONTH))
				return ret.toString();
			//$FALL-THROUGH$
		case Calendar.DAY_OF_MONTH:
			int day = ec.get(Calendar.DAY_OF_MONTH);
			if(length > 0) {
				ret.insert(0, ' ');
				length++;
			}
			ret.insert(0, day);
			length += 2;
			while(ret.length() < length)
				ret.insert(0, '0');
			if(end - start < 31L * 24 * 60 * 60 * 1000 && sc.get(Calendar.MONTH) == ec.get(Calendar.MONTH)) {
				String suffix;
				if(day / 10 == 1)
					suffix = "th";
				else if(day % 10 == 1)
					suffix = "st";
				else if(day % 10 == 2)
					suffix = "nd";
				else if(day % 10 == 3)
					suffix = "rd";
				else
					suffix = "th";
				ret.insert(2, suffix);
				return ret.toString();
			}
			//$FALL-THROUGH$
		case Calendar.MONTH:
			String [] months = new String[] {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
			ret.insert(2, months[ec.get(Calendar.MONTH)]);
			length += 3;
			if(sc.get(Calendar.YEAR) == ec.get(Calendar.YEAR))
				return ret.toString();
			//$FALL-THROUGH$
		case Calendar.YEAR:
			return print(end);
		}
		return ret.toString();
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
		for(c = 0; c < str.length() && str.codePointAt(c) <= 0x7f; c++);
		if(c == str.length())
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
		for(c = 0; c < str.length() && str.codePointAt(c) <= 0x7f; c++);
		if(c == str.length())
			return ret;

		for(; c < str.length(); c++) {
			int ch = str.codePointAt(c);
			if(ch > 0x7f) {
				ret++;
				str.setCharAt(c, '\\');
				c++;
				str.insert(c, "u0000");
				c++;
				String hexString = Integer.toHexString(ch);
				c += 4 - hexString.length();
				for(int i = 0; i < hexString.length(); i++)
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
		if(str == null)
			return null;
		int index = str.indexOf("\\u");
		if(index >= 0) {
			int idx2 = str.indexOf("\\\\u");
			if(idx2 < index)
				index = idx2;
			StringBuilder sb = new StringBuilder();
			charLoop: for(int c = 0; c < str.length(); c++) {
				if(c >= index && c < str.length() - 5 && str.charAt(c) == '\\') {
					final int preC = c;
					if(str.charAt(c + 1) == 'u')
						c += 2;
					else if(c < str.length() - 6 && str.charAt(c + 1) == '\\' && str.charAt(c + 2) == 'u')
						c += 3;
					else {
						sb.append(str.charAt(c));
						continue;
					}
					int code = 0;
					for(int i = 0; i < 4; i++) {
						int hex = fromHex(str.charAt(c));
						if(hex < 0) { // Doesn't match \\uXXXX--don't adjust
							c = preC;
							sb.append(str.charAt(c));
							continue charLoop;
						}
						code = code * 16 + hex;
						c++;
					}
					c--;
					char [] codeChars = Character.toChars(code);
					for(char ch : codeChars)
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
		if(c >= '0' && c <= '9')
			return c - '0';
		else if(c >= 'a' && c <= 'f')
			return c - 'a' + 10;
		else if(c >= 'A' && c <= 'F')
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
		if(str.indexOf(srch) < 0)
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
		if(srch.length() == 0)
			return 0;
		if(replacement == null)
			replacement = "";
		String sub = null;
		if(replacement.length() > srch.length())
			sub = replacement.substring(srch.length());
		int ret = 0;
		for(int i = 0; i <= str.length() - srch.length(); i++) {
			int j;
			for(j = 0; j < srch.length() && str.charAt(i + j) == srch.charAt(j); j++);
			if(j == srch.length()) {
				ret++;
				for(j = 0; j < srch.length() && j < replacement.length(); j++)
					str.setCharAt(i + j, replacement.charAt(j));
				if(srch.length() > replacement.length())
					str.delete(i + replacement.length(), i + srch.length());
				else if(replacement.length() > srch.length())
					str.insert(i + srch.length(), sub);
				i += replacement.length() - 1;
			}
		}
		return ret;
	}

	/**
	 * A quick function to write a properties map--intended for the PrismsEvent constructor
	 *
	 * @param props The properties in name value pairs
	 * @return A Map of the given properties
	 * @throws IllegalArgumentException If the arguments are not in String, Object, String, Object... order
	 */
	public static java.util.Map<String, Object> eventProps(Object... props) throws IllegalArgumentException {
		return rEventProps(props);
	}

	/**
	 * A quick function to write a properties JSONObject
	 *
	 * @param props The properties in name value pairs
	 * @return A Map of the given properties
	 * @throws IllegalArgumentException If the arguments are not in String, Object, String, Object... order
	 */
	public static org.json.simple.JSONObject rEventProps(Object... props) {
		if(props.length % 2 != 0)
			throw new IllegalArgumentException("eventProps takes an even number of" + " arguments, not " + props.length);
		org.json.simple.JSONObject ret = new org.json.simple.JSONObject();
		for(int i = 0; i < props.length; i += 2) {
			if(!(props[i] instanceof String))
				throw new IllegalArgumentException("Every other object passed to" + " eventProps must be a string, not " + props[i]);
			ret.put(props[i], props[i + 1]);
		}
		return ret;
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
	public static StackTraceElement [] patchStackTraces(StackTraceElement [] innerTrace, StackTraceElement [] outerTrace, String className,
		String methodName) {
		if(innerTrace == null || innerTrace.length == 0 || outerTrace == null || outerTrace.length == 0)
			return innerTrace;
		int i = innerTrace.length - 1;
		while(i >= 0 && !(innerTrace[i].getClassName().equals(className) && innerTrace[i].getMethodName().equals(methodName)))
			i--;
		if(i < 0)
			return innerTrace;
		StackTraceElement [] ret = new StackTraceElement[i + outerTrace.length + 1];
		System.arraycopy(innerTrace, 0, ret, 0, i + 1);
		System.arraycopy(outerTrace, 0, ret, i + 1, outerTrace.length);
		return ret;
	}
}

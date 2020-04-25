package org.qommons;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Common utilities for dealing with Strings */
public class StringUtils {
	/**
	 * A comparator that returns the result of {@link #compareNumberTolerant(String, String, boolean, boolean)
	 * compareNumberTolerant}<code>(s1, s2, true, true)</code>
	 */
	public static final Comparator<String> DISTINCT_NUMBER_TOLERANT = (s1, s2) -> compareNumberTolerant(s1, s2, true, true);

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
		if (s1 == s2) {
			return 0;
		} else if (s1 == null)
			return -1;
		else if (s2 == null)
			return 1;

		int i1, i2;
		int intolerantResult = 0;
		for (i1 = 0, i2 = 0; i1 < s1.length() && i2 < s2.length(); i1++, i2++) {
			char ch1 = s1.charAt(i1);
			char ch2 = s2.charAt(i2);
			if (onlyZeroIfEqual && intolerantResult == 0)
				intolerantResult = ch2 - ch1;
			boolean firstIsDigit = ch1 >= '0' && ch1 <= '9';
			boolean secondIsDigit = ch2 >= '0' && ch2 <= '9';
			if (firstIsDigit && secondIsDigit) {
				// We're only number tolerant when BOTH strings contain a number starting at the same place
				// First, ignore leading zeros
				while (ch1 == '0') {
					i1++;
					if (i1 == s1.length()) {
						firstIsDigit = false;
						break;
					}
					ch1 = s1.charAt(i1);
					firstIsDigit = ch1 >= '0' && ch1 <= '9';
				}
				while (ch2 == '0') {
					i2++;
					if (i2 == s2.length()) {
						secondIsDigit = false;
						break;
					}
					ch2 = s2.charAt(i2);
					secondIsDigit = ch2 >= '0' && ch2 <= '9';
				}
				boolean firstGreater = false;
				boolean secondGreater = false;
				while (firstIsDigit && secondIsDigit) {
					if (!firstGreater && !secondGreater) {
						int comp = ch1 - ch2;
						if (comp < 0)
							secondGreater = true;
						else if (comp > 0)
							firstGreater = true;
					}
					i1++;
					if (i1 < s1.length()) {
						ch1 = s1.charAt(i1);
						firstIsDigit = ch1 >= '0' && ch1 <= '9';
					} else
						firstIsDigit = false;
					i2++;
					if (i2 < s2.length()) {
						ch2 = s2.charAt(i2);
						secondIsDigit = ch2 >= '0' && ch2 <= '9';
					} else
						secondIsDigit = false;
				}
				if (firstIsDigit)
					return 1; // The number in o1 has more digits and is therefore larger
				else if (secondIsDigit)
					return -1; // The number in o2 has more digits and is therefore larger
				else if (firstGreater)
					return 1;
				else if (secondGreater)
					return -1;
				if (i1 == s1.length() || i2 == s2.length())
					break; // We've advanced past the number (which was the same in both strings) now and ch1 and ch2 are now
			}
			if (ignoreCase) {
				boolean upper1 = Character.isUpperCase(ch1);
				boolean upper2 = Character.isUpperCase(ch2);
				if (upper1 != upper2) {
					if (upper1)
						ch1 = Character.toLowerCase(ch1);
					else
						ch2 = Character.toLowerCase(ch2);
				}
			}
			int diff = ch1 - ch2;
			if (diff < 0)
				return -1;
			else if (diff > 0)
				return 1;
		}
		if (i1 < s1.length())
			return 1;
		else if (i2 < s2.length())
			return -1;
		return intolerantResult;
	}

	/**
	 * Adds a number to a String integer
	 * 
	 * @param string The String containing a number to add to
	 * @param cursor The character position at which to add the amount
	 * @param amount The number to add to the String (may be negative)
	 * @return The String with the number at the given position changed by the given amount
	 */
	public static String add(CharSequence string, int cursor, int amount) {
		StringBuilder str = new StringBuilder(string);
		char ch = str.charAt(cursor);
		if (ch < '0' || ch > '9') {
			// If the given character is not a digit, we'll just insert the amount there
			str.insert(cursor, amount);
			return str.toString();
		}
		int remainder;
		int sum = amount + ch - '0';
		remainder = sum / 10;
		int dig = sum % 10;
		if (dig < 0) {
			dig += 10;
			remainder--;
		}
		str.setCharAt(cursor, (char) ('0' + dig));
		while (remainder != 0) {
			if (cursor > 0 && Character.isDigit(str.charAt(cursor - 1)))
				cursor--;
			else if (remainder != -1)
				str.insert(cursor, '0');
			else
				break;
			sum = remainder + str.charAt(cursor) - '0';
			remainder = sum / 10;
			dig = sum % 10;
			if (dig < 0) {
				dig += 10;
				remainder--;
			}
			str.setCharAt(cursor, (char) ('0' + dig));
		}
		return str.toString();
	}

	/**
	 * Prints an integer into a StringBuilder, padding zeros as necessary to a specified number of places
	 * 
	 * @param value The value to append
	 * @param places The number of digits (not including the '-', if any) for the integer
	 * @param into The StringBuilder to append to (or null to create a new one)
	 * @return The StringBuilder
	 */
	public static StringBuilder printInt(long value, int places, StringBuilder into) {
		if (into == null)
			into = new StringBuilder();
		int start = into.length();
		into.append(value);
		if (into.charAt(start) == '-')
			start++;
		while (into.length() - start < places)
			into.insert(start, '0');
		return into;
	}

	/**
	 * Prints a sequence of values to a StringBuilder
	 * 
	 * @param <T> The type of values to print
	 * @param delimiter The character sequence to place between each value
	 * @param values The sequence to print
	 * @param format The formatter for the sequence (e.g. <code>StringBuilder::append</code>)
	 * @return The printed StringBuilder
	 */
	public static <T> StringBuilder print(CharSequence delimiter, Iterable<? extends T> values,
		Function<? super T, ? extends CharSequence> format) {
		return print(new StringBuilder(), delimiter, values, (v, str) -> str.append(format == null ? String.valueOf(v) : format.apply(v)));
	}

	/**
	 * Prints a sequence of values to a StringBuilder
	 * 
	 * @param <T> The type of values to print
	 * @param into The StringBuilder to print into--may be null, in which case a new one will be created
	 * @param delimiter The character sequence to place between each value
	 * @param values The sequence to print
	 * @param format The formatter for the sequence (e.g. <code>StringBuilder::append</code>)
	 * @return The printed StringBuilder
	 */
	public static <T> StringBuilder print(StringBuilder into, CharSequence delimiter, Iterable<? extends T> values,
		BiConsumer<? super T, ? super StringBuilder> format) {
		if (into == null)
			into = new StringBuilder();
		boolean first = true;
		for (T value : values) {
			if (first)
				first = false;
			else
				into.append(delimiter);
			if (format != null)
				format.accept(value, into);
			else
				into.append(value);
		}
		return into;
	}

	/** Prints values from a sequence to a String */
	public interface SequencePrinter {
		/**
		 * Prints a value into a StringBuilder
		 * 
		 * @param <T> The type of the value
		 * @param into The StringBuilder to append to
		 * @param value The value to append
		 * @param format The format to use to print the value
		 * @param before The number of values that were in the sequence before this one
		 * @param last Whether the value is the last in the sequence
		 */
		<T> void printValue(StringBuilder into, T value, BiConsumer<? super StringBuilder, ? super T> format, int before, boolean last);

		/**
		 * @param <T> The type of the value
		 * @param into The StringBuilder to append to
		 * @param values The values to print
		 * @param format The format to use to print the values
		 * @return The StringBuilder
		 */
		default <T> StringBuilder print(StringBuilder into, Iterable<? extends T> values,
			BiConsumer<? super StringBuilder, ? super T> format) {
			if (into == null)
				into = new StringBuilder();
			Iterator<? extends T> iter = values.iterator();
			boolean last = !iter.hasNext();
			int count = 0;
			while (!last) {
				T value = iter.next();
				last = !iter.hasNext();
				printValue(into, value, format, count, last);
				count++;
			}
			return into;
		}

		/**
		 * @param <T> The type of the value
		 * @param into The StringBuilder to append to
		 * @param values The values to print
		 * @param format The format to use to print the values
		 * @return The StringBuilder
		 */
		default <T> StringBuilder print(StringBuilder into, T[] values, BiConsumer<? super StringBuilder, ? super T> format) {
			return print(into, Arrays.asList(values), format);
		}

		/**
		 * @param <T> The type of the value
		 * @param values The values to print
		 * @param format The format to use to print the values
		 * @return The StringBuilder
		 */
		default <T> StringBuilder print(Iterable<? extends T> values, BiConsumer<? super StringBuilder, ? super T> format) {
			return print(new StringBuilder(), values, format);
		}

		/**
		 * @param <T> The type of the value
		 * @param values The values to print
		 * @param format The format to use to print the values
		 * @return The StringBuilder
		 */
		default <T> StringBuilder print(T[] values, BiConsumer<? super StringBuilder, ? super T> format) {
			return print(Arrays.asList(values), format);
		}
	}

	/**
	 * Creates a printer to print a sequence like "v1, v2, and v3" or "v1 and v2". In this example, the <code>delimiter</code> would be ", "
	 * and the <code>preTerminal</code> would be " and ". As shown, if the delimiter ends with a space and the preTerminal starts with a
	 * space, a space will be removed to avoid a double-space. If the delimiter or preTerminal is null, it will not be printed.
	 * 
	 * @param delimiter The sequence to insert between each value in the sequence
	 * @param preTerminal The sequence to insert after the delimiter
	 * @return The SequencePrinter to {@link #print(StringBuilder, CharSequence, Iterable, BiConsumer) print} a sequence of values
	 */
	public static SequencePrinter conversational(CharSequence delimiter, CharSequence preTerminal) {
		return new SequencePrinter() {
			@Override
			public <T> void printValue(StringBuilder into, T value, BiConsumer<? super StringBuilder, ? super T> format, int before,
				boolean last) {
				boolean first = before == 0;
				boolean delimit = delimiter != null && !first && (!last || before > 1);
				if (delimit)
					into.append(delimiter);
				if (last && !first && preTerminal != null) {
					if (delimit && delimiter.charAt(delimiter.length() - 1) == ' ' && preTerminal.charAt(0) == ' ')
						into.deleteCharAt(into.length() - 1); // No double-space
					into.append(preTerminal);
				}
				format.accept(into, value);
			}
		};
	}

	/** Represents a composite name made up of joined components */
	public static class Name {
		private final String[] theComponents;

		/** @param components The components of the name */
		public Name(String[] components) {
			theComponents = components;
		}

		/** @return The comonents of this name */
		public String[] getComponents() {
			return theComponents;
		}

		/**
		 * Prints the name using a case scheme
		 * 
		 * @param str The StringBuilder to print into
		 * @param initialCapital Whether the initial character should be capitalized
		 * @param intermediateCapital Whether the first character of each component but the first should be capitalized
		 * @param delimiter A sequence to place between each component, null is interpreted as empty
		 * @return The StringBuilder
		 */
		public StringBuilder toCaseScheme(StringBuilder str, boolean initialCapital, boolean intermediateCapital, CharSequence delimiter) {
			for (int i = 0; i < theComponents.length; i++) {
				if (i == 0) {
					if (initialCapital) {
						str.append(Character.toUpperCase(theComponents[i].charAt(0)));
						str.append(theComponents[i], 1, theComponents[i].length());
					} else
						str.append(theComponents[i]);
				} else {
					if (delimiter != null)
						str.append(delimiter);
					if (intermediateCapital) {
						str.append(Character.toUpperCase(theComponents[i].charAt(0)));
						str.append(theComponents[i], 1, theComponents[i].length());
					} else
						str.append(theComponents[i]);
				}
			}
			return str;
		}

		/**
		 * Prints the name using a case scheme
		 * 
		 * @param initialCapital Whether the initial character should be capitalized
		 * @param intermediateCapital Whether the first character of each component but the first should be capitalized
		 * @param delimiter A sequence to place between each component
		 * @return The printed string
		 */
		public String toCaseScheme(boolean initialCapital, boolean intermediateCapital, CharSequence delimiter) {
			return toCaseScheme(new StringBuilder(), initialCapital, intermediateCapital, delimiter).toString();
		}

		/**
		 * Converts this name to pacal case, the same as camel-case, but with an initial capital
		 * 
		 * @return The pascal-cased name
		 */
		public String toPascalCase() {
			return toPascalCase(new StringBuilder()).toString();
		}

		/**
		 * Converts this name to pacal case, the same as camel-case, but with an initial capital
		 * 
		 * @param str The StringBuilder to append into
		 * @return The pascal-cased name
		 */
		public StringBuilder toPascalCase(StringBuilder str) {
			return toCaseScheme(str, true, true, null);
		}

		/**
		 * Same as {@link #toCaseScheme(boolean, boolean, CharSequence) toCaseScheme(false, true, null)}
		 * 
		 * @return The camel case string
		 */
		public String toCamelCase() {
			return toCamelCase(new StringBuilder()).toString();
		}

		/**
		 * Same as {@link #toCaseScheme(StringBuilder, boolean, boolean, CharSequence) toCaseScheme(str, false, true, null)}
		 * 
		 * @param str The StringBuilder to print to
		 * @return The StringBuilder
		 */
		public StringBuilder toCamelCase(StringBuilder str) {
			return toCaseScheme(str, false, true, null);
		}

		/**
		 * Same as {@link #toCaseScheme(boolean, boolean, CharSequence) toCaseScheme(false, false, "-")}
		 * 
		 * @return The kebab-case string
		 */
		public String toKebabCase() {
			return toKebabCase(new StringBuilder()).toString();
		}

		/**
		 * Same as {@link #toCaseScheme(StringBuilder, boolean, boolean, CharSequence) toCaseScheme(str, false, false, "-")}
		 * 
		 * @param str The StringBuilder to print to
		 * @return The StringBuilder
		 */
		public StringBuilder toKebabCase(StringBuilder str) {
			return toCaseScheme(str, false, false, "-");
		}
	}

	/**
	 * @param name The name to parse by its capitalization
	 * @param ignoreRepeatCapitals Whether to treat adjacent capitals as part of the same name component or not. This often results in
	 *        prettier names, but also destroys information that cannot be re-created when parsing the formatted name.
	 * @return The name object to be formatted in a different way
	 */
	public static Name parseByCase(String name, boolean ignoreRepeatCapitals) {
		if (name.length() == 0)
			return new Name(new String[0]);
		List<String> components = new LinkedList<>();
		StringBuilder comp = new StringBuilder();
		boolean hadLower = false;
		for (int c = 0; c < name.length(); c++) {
			if (Character.isUpperCase(name.charAt(c))) {
				boolean newComponent = c > 0 && (hadLower || !ignoreRepeatCapitals);
				if (newComponent) {
					components.add(comp.toString());
					comp.setLength(0);
					hadLower = false;
				}
				comp.append(Character.toLowerCase(name.charAt(c)));
			} else {
				comp.append(name.charAt(c));
				hadLower = true;
			}
		}
		components.add(comp.toString());
		return new Name(components.toArray(new String[components.size()]));
	}

	/**
	 * Parses a name sequence from a string with a character delimiter (e.g. '.' to parse java.util.Collection)
	 * 
	 * @param str The string to parse
	 * @param delimiter The character delimiter
	 * @return The parsed name
	 */
	public static Name split(String str, char delimiter) {

		List<String> components = new LinkedList<>();
		int start = 0;
		for (int c = 0; c < str.length(); c++) {
			if (str.charAt(c) == delimiter) {
				components.add(str.substring(start, c));
				start = c + 1; // Skip the delimiter
			}
		}
		components.add(str.substring(start));
		return new Name(components.toArray(new String[components.size()]));
	}

	/**
	 * @param name The singular name
	 * @return The plural of the given name
	 */
	public static String pluralize(String name) {
		if (name.endsWith("y")) {
			name = name.substring(0, name.length() - 1) + "ie";
		} else if (name.endsWith("s") || name.endsWith("x") || name.endsWith("z") || name.endsWith("ch") || name.endsWith("sh")) {
			name += "e";
		}
		name += "s";
		return name;
	}

	/**
	 * @param name The plural name
	 * @return The singular of the given name
	 */
	public static String singularize(String name) {
		if (name.endsWith("eries")) // Don't singularize "series" to "sery"
			return name;
		else if (name.endsWith("ies"))
			return name.substring(0, name.length() - 3) + "y";
		else if (name.endsWith("ses"))
			return name.substring(0, name.length() - 2);
		else if (name.endsWith("s"))
			return name.substring(0, name.length() - 1);
		else
			return name;
	}

	/**
	 * @param str The string to get the article for
	 * @return "a" or "an"
	 */
	public static String getIndefiniteArticle(String str) {
		if (str.isEmpty())
			return "";
		char init = Character.toLowerCase(str.charAt(0));
		if (init == 'a' || init == 'e' || init == 'i' || init == 'o' || init == 'u')
			return "an";
		else
			return "a";
	}

	/**
	 * A naming scheme to detect and produce duplicate names for
	 * {@link StringUtils#getNewItemName(Iterable, Function, String, DuplicateItemNamer) getNewItemName}
	 */
	public interface DuplicateItemNamer {
		/**
		 * @param name The name containing the first try to append the duplicate suffix onto
		 * @param suffix The suffix integer to append
		 */
		void appendDuplicate(StringBuilder name, int suffix);

		/**
		 * @param name The name to test
		 * @return The parsed name duplicate if the given name matches duplicate names produced by this format, or null if the name is not
		 *         recognized as a duplicate
		 */
		DuplicateName detectDuplicate(String name);
	}

	/** A name that is recognized as a duplicate by {@link DuplicateItemNamer#detectDuplicate(String)} */
	public static class DuplicateName {
		/** The name of the item that was duplicated */
		public final String originalName;
		/** The duplicate suffix that was appended to make the name unique in the set if items */
		public final int duplicateSuffix;

		/**
		 * @param originalName The name of the item that was duplicated
		 * @param duplicateSuffix The duplicate suffix that was appended to make the name unique in the set if items
		 */
		public DuplicateName(String originalName, int duplicateSuffix) {
			this.originalName = originalName;
			this.duplicateSuffix = duplicateSuffix;
		}
	}

	/**
	 * Creates a naming scheme for {@link #getNewItemName(Iterable, Function, String, DuplicateItemNamer) getNewItemName} that makes
	 * duplicate names like <code>originalName+between+index+post</code>.
	 * 
	 * @param between The string to put between the original name and the suffix (null is the same as empty)
	 * @param post The string to put after the suffix (null is the same as empty)
	 * @return The naming scheme
	 */
	public static DuplicateItemNamer duplicateAppending(String between, String post) {
		StringBuilder patternStr = new StringBuilder("(?<original>.*)");
		if (between != null)
			patternStr.append(Pattern.quote(between));
		patternStr.append("(?<suffix>\\d{1,9})");
		if (post != null)
			patternStr.append(Pattern.quote(post));
		Pattern pattern = Pattern.compile(patternStr.toString());
		return new DuplicateItemNamer() {
			@Override
			public void appendDuplicate(StringBuilder name, int suffix) {
				if (between != null)
					name.append(between);
				name.append(suffix);
				if (post != null)
					name.append(post);
			}

			@Override
			public DuplicateName detectDuplicate(String name) {
				Matcher matcher = pattern.matcher(name);
				if (matcher.matches())
					return new DuplicateName(matcher.group("original"), Integer.parseInt(matcher.group("suffix")));
				return null;
			}
		};
	}

	/**
	 * A naming scheme for {@link #getNewItemName(Iterable, Function, String, DuplicateItemNamer) getNewItemName} that makes duplicate names
	 * like "originalName (2)"
	 */
	public static DuplicateItemNamer PAREN_DUPLICATES = duplicateAppending(" (", ")");
	/**
	 * A naming scheme for {@link #getNewItemName(Iterable, Function, String, DuplicateItemNamer) getNewItemName} that makes duplicate names
	 * like "originalName 2"
	 */
	public static DuplicateItemNamer SIMPLE_DUPLICATES = duplicateAppending(" ", null);

	/**
	 * Gets unique name for a new item in a named item list
	 * 
	 * @param <E> The type of the list items
	 * @param items The list of items
	 * @param itemName The item name function
	 * @param firstTry The default new item name
	 * @param namer A scheme for modifying a name with a try number (starting with 2) in a user-friendly format.
	 * @return The name for a new list item that is not the same as that of any existing item in the list
	 * @see #PAREN_DUPLICATES
	 * @see #SIMPLE_DUPLICATES
	 */
	public static <E> String getNewItemName(Iterable<? extends E> items, Function<? super E, String> itemName, String firstTry,
		DuplicateItemNamer namer) {
		if (namer == null)
			namer = PAREN_DUPLICATES;
		String name = firstTry;
		boolean nameExists = false;
		for (E item : items) {
			String name_i = itemName.apply(item);
			if (name.equals(name_i)) {
				nameExists = true;
				break;
			}
		}
		if (!nameExists)
			return name;
		String original;
		int tryNumber;

		DuplicateName duplicate = namer.detectDuplicate(firstTry);
		if (duplicate != null) {
			original = duplicate.originalName;
			tryNumber = duplicate.duplicateSuffix + 1;
		} else {
			original = firstTry;
			tryNumber = 2;
		}
		StringBuilder newName = new StringBuilder(original);
		while (true) {
			newName.setLength(original.length());
			// Just in case the user does weird stuff with the string
			for (int c = 0; c < original.length(); c++)
				newName.setCharAt(c, original.charAt(c));
			namer.appendDuplicate(newName, tryNumber);
			String newNameStr = newName.toString();
			if (name.equals(newNameStr)) // A catch to avoid an infinite loop if the suffix doesn't use the try number properly
				throw new IllegalStateException("Suffix is not working properly");
			name = newNameStr;

			nameExists = false;
			for (E item : items) {
				String name_i = itemName.apply(item);
				if (name.equals(name_i)) {
					nameExists = true;
					break;
				}
			}
			if (!nameExists)
				return name;
			tryNumber++;
		}
	}

	/**
	 * @param <E> The type of named items
	 * @param items The set of named items
	 * @param itemName The name function for the items
	 * @param name The name to test
	 * @param exclude A potential item in the list to exclude from consideration (e.g. the item being renamed)
	 * @return Whether the given name is unique within the set (with the possible exception of the excluded item)
	 */
	public static <E> boolean isUniqueName(Iterable<? extends E> items, Function<? super E, String> itemName, String name, E exclude) {
		for (E item : items) {
			if (item == exclude)
				continue;
			if (name.equals(itemName.apply(item)))
				return false;
		}
		return true;
	}

	/** A source of binary data */
	public interface ByteIterator {
		/**
		 * @return Whether another byte exists in the sequence
		 * @throws IOException If the information cannot be determined
		 */
		boolean hasNext() throws IOException;

		/**
		 * @return The next byte in the sequence
		 * @throws IOException If the information cannot be retrieved
		 */
		int next() throws IOException;

		/**
		 * @param bytes The bytes to iterate over
		 * @return A ByteIterator over the given bytes
		 */
		public static ByteIterator of(byte[] bytes) {
			return of(bytes, 0, bytes.length);
		}

		/**
		 * @param bytes The bytes to iterate over
		 * @param start The start offset in the byte array
		 * @param end The end offset in the byte array
		 * @return A ByteIterator over the given data
		 */
		public static ByteIterator of(byte[] bytes, int start, int end) {
			if (start < 0)
				throw new IndexOutOfBoundsException(start + "<0");
			else if (start > end)
				throw new IndexOutOfBoundsException(start + ">" + end);
			else if (start > bytes.length)
				throw new IndexOutOfBoundsException(start + ">" + bytes.length);
			int realEnd = Math.min(end, bytes.length);
			return new ByteIterator() {
				private int thePosition;

				@Override
				public boolean hasNext() throws IOException {
					return thePosition < realEnd;
				}

				@Override
				public int next() throws IOException {
					return bytes[thePosition++] & 0xff;
				}
			};
		}

		/**
		 * @param in The input stream to iterate over
		 * @return A ByteIterator over the given stream
		 */
		public static ByteIterator of(InputStream in) {
			return new ByteIterator() {
				private boolean hasNext;
				private int theNext;

				@Override
				public boolean hasNext() throws IOException {
					if (!hasNext)
						hasNext = (theNext = in.read()) >= 0;
					return hasNext;
				}

				@Override
				public int next() throws IOException {
					if (!hasNext())
						throw new NoSuchElementException();
					int next = theNext;
					hasNext = false;
					return next;
				}
			};
		}
	}

	/** Accepts a sequence of binary data */
	public interface BinaryAccumulator {
		/** @return Whether this accumulator can accept any more bytes */
		boolean canAcceptMore();

		/**
		 * Accumulates the next byte into this binary sequence
		 * 
		 * @param nextByte The byte to accumulate
		 * @return Whether this accumulator can accept any more bytes
		 * @throws IOException If an error occurs accumulating the byte
		 */
		boolean accumulate(byte nextByte) throws IOException;
	}

	/** Accepts a sequence of characters */
	public interface CharAccumulator {
		/** @return Whether this accumulator can accept any more characters */
		boolean canAcceptMore();

		/**
		 * Accumulates the next character into this sequence
		 * 
		 * @param nextChar The character to accumulate
		 * @return Whether this accumulator can accept any more characters
		 * @throws IOException If an error occurs accumulating the character
		 */
		boolean accumulate(char nextChar) throws IOException;
	}

	/** Implements {@link BinaryAccumulator} for a {@link ByteBuffer} */
	public static class ByteBufferAccumulator implements BinaryAccumulator {
		private final ByteBuffer theBuffer;

		/** @param buffer The buffer to append data into */
		public ByteBufferAccumulator(ByteBuffer buffer) {
			theBuffer = buffer;
		}

		/** @return The buffer being appended to */
		public ByteBuffer getBuffer() {
			return theBuffer;
		}

		/** @return The buffer being appended to, after having its position set to zero */
		public ByteBuffer getResetBuffer() {
			theBuffer.position(0);
			return theBuffer;
		}

		@Override
		public boolean canAcceptMore() {
			return theBuffer.position() < theBuffer.capacity();
		}

		@Override
		public boolean accumulate(byte nextByte) {
			theBuffer.put(nextByte);
			return theBuffer.position() < theBuffer.capacity();
		}
	}

	/** A {@link BinaryAccumulator} for a byte array */
	public static class ByteArrayAccumulator implements BinaryAccumulator {
		private final byte[] theBytes;
		private final int theLimit;
		private int thePosition;

		/**
		 * @param bytes The bytes to put data into
		 * @param start The start offset in the array
		 * @param end The end offset in the array
		 */
		public ByteArrayAccumulator(byte[] bytes, int start, int end) {
			if (start < 0)
				throw new IndexOutOfBoundsException(start + "<0");
			else if (start > end)
				throw new IndexOutOfBoundsException(start + ">" + end);
			else if (start > bytes.length)
				throw new IndexOutOfBoundsException(start + ">" + bytes.length);

			theBytes = bytes;
			thePosition = start;
			theLimit = end;
		}

		/** @param bytes The bytes to put data into */
		public ByteArrayAccumulator(byte[] bytes) {
			theBytes = bytes;
			thePosition = 0;
			theLimit = bytes.length;
		}

		/** @return The index in the byte array at which the next byte will be put */
		public int getPosition() {
			return thePosition;
		}

		/** @return The byte array being modified */
		public byte[] getBytes() {
			return theBytes;
		}

		/** @return The last position plus one at which this accumulator will write data into the array */
		public int getLimit() {
			return theLimit;
		}

		@Override
		public boolean canAcceptMore() {
			return thePosition < theLimit;
		}

		@Override
		public boolean accumulate(byte nextByte) {
			theBytes[thePosition++] = nextByte;
			return thePosition < theLimit;
		}
	}

	/** A {@link BinaryAccumulator} for an {@link OutputStream} */
	public static class OutputStreamAccumulator implements BinaryAccumulator {
		private final OutputStream theStream;

		/** @param stream The stream to write the data into */
		public OutputStreamAccumulator(OutputStream stream) {
			theStream = stream;
		}

		/** @return The stream being written to */
		public OutputStream getStream() {
			return theStream;
		}

		@Override
		public boolean canAcceptMore() {
			return true;
		}

		@Override
		public boolean accumulate(byte nextByte) throws IOException {
			theStream.write(nextByte);
			return true;
		}
	}

	/** A {@link OutputStreamAccumulator} specifically for a {@link ByteArrayOutputStream} */
	public static class ByteArrayOSAccumulator extends OutputStreamAccumulator {
		/** Creates a new {@link ByteArrayOutputStream} to accumulate into */
		public ByteArrayOSAccumulator() {
			this(new ByteArrayOutputStream());
		}

		/** @param bytes The {@link ByteArrayOutputStream} to accumulate into */
		public ByteArrayOSAccumulator(ByteArrayOutputStream bytes) {
			super(bytes);
		}

		@Override
		public ByteArrayOutputStream getStream() {
			return (ByteArrayOutputStream) super.getStream();
		}
	}

	/**
	 * A {@link Reader} of a {@link CharSequence}
	 * 
	 * @param <C> The sub-type of CharSequence
	 */
	public static class CharSequenceReader<C extends CharSequence> extends Reader {
		private final C theSequence;
		private int thePosition;

		/** @param sequence The sequence to read */
		public CharSequenceReader(C sequence) {
			theSequence = sequence;
		}

		/** @return The position in the sequence where the next character will be read from */
		public int getPosition() {
			return thePosition;
		}

		/** @return The sequence */
		public C getSequence() {
			return theSequence;
		}

		@Override
		public int read(char[] cbuf, int off, int len) {
			if (thePosition >= theSequence.length())
				return -1;
			int count;
			for (count = 0; count < len && thePosition < theSequence.length(); count++)
				cbuf[off + count] = theSequence.charAt(thePosition++);
			return count;
		}

		@Override
		public void close() {}
	}

	/**
	 * A {@link CharAccumulator} for an {@link Appendable}
	 * 
	 * @param <A> The sub-type of Appendable
	 */
	public static class AppendableWriter<A extends Appendable> implements CharAccumulator {
		private final A theAppendable;

		/** @param appendable The appendable to write character data to */
		public AppendableWriter(A appendable) {
			theAppendable = appendable;
		}

		/** @return The appendable being written to */
		public A getAppendable() {
			return theAppendable;
		}

		@Override
		public boolean canAcceptMore() {
			return true;
		}

		@Override
		public boolean accumulate(char nextChar) throws IOException {
			theAppendable.append(nextChar);
			return true;
		}
	}

	/** A {@link CharAccumulator} for a {@link Writer} */
	public static class WriterAccumulator implements CharAccumulator {
		private final Writer theWriter;

		/** @param writer The writer to write to */
		public WriterAccumulator(Writer writer) {
			theWriter = writer;
		}

		@Override
		public boolean canAcceptMore() {
			return true;
		}

		@Override
		public boolean accumulate(char nextChar) throws IOException {
			theWriter.append(nextChar);
			return true;
		}
	}

	/** Encodes and decodes binary data to and from encoded character sequences */
	public interface BinaryDataEncoder {
		/** @return The radix of this encoder */
		int getRadix();

		/**
		 * @param byteLength The number of bytes to encode
		 * @return The maximum number of characters required to encode a sequence of the given length
		 */
		int getEncodedLength(int byteLength);

		/**
		 * @param encodedLength The length of the sequence encoded with this encoder
		 * @return The maximum number of bytes that the sequence may represent
		 */
		int getByteLength(int encodedLength);

		/**
		 * @param <A> The type of the character accumulator to accumulate the data into
		 * @param in The bytes to encode
		 * @param out The character accumulator to accumulate the data into
		 * @param byteCount Accepts the number of bytes that were in the iterator
		 * @return The accumulator
		 * @throws IOException If an exception is thrown from the byte iterator or the accumulator
		 */
		<A extends CharAccumulator> A format(ByteIterator in, A out, LongConsumer byteCount) throws IOException;

		/**
		 * @param <B> The type of the binary data accumulator to accumulate the data into
		 * @param in The reader containing the character sequence data to parse
		 * @param out The binary data accumulator to accumulate the data into
		 * @param byteCount Accepts the number of bytes that were parsed from the sequence
		 * @return The accumulator
		 * @throws IOException If an exception is thrown from the reader or the accumulator
		 */
		<B extends BinaryAccumulator> B parse(Reader in, B out, LongConsumer byteCount) throws IOException;

		/**
		 * @param <B> The type of the binary data accumulator to accumulate the data into
		 * @param str The character sequence to parse
		 * @param out The binary data accumulator to accumulate the data into
		 * @param byteCount Accepts the number of bytes that were parsed from the sequence
		 * @return The accumulator
		 * @throws IOException If an exception is thrown from the reader or the accumulator
		 */
		default <B extends BinaryAccumulator> B parse(CharSequence str, B out, LongConsumer byteCount) throws IOException {
			return parse(//
				new CharSequenceReader<>(str), out, null);
		}

		/**
		 * @param str The string to parse
		 * @return The bytes represented by the string
		 */
		default byte[] parse(CharSequence str) {
			try {
				return parse(//
					new CharSequenceReader<>(str), new ByteArrayOSAccumulator(), null)//
						.getStream().toByteArray();
			} catch (IOException e) {
				throw new IllegalStateException("WHAT??!!", e);
			}
		}

		/**
		 * @param str The string builder to format the data into, or null to create a new one
		 * @param bytes The bytes to format
		 * @param start The start offset in the bytes array
		 * @param end The end offset in the bytes array
		 * @return The StringBuilder
		 */
		default StringBuilder format(StringBuilder str, byte[] bytes, int start, int end) {
			if (str == null)
				str = new StringBuilder();
			try {
				return format(ByteIterator.of(bytes, start, end), new AppendableWriter<>(str), null).getAppendable();
			} catch (IOException e) {
				throw new IllegalStateException("WHAT??!!!", e);
			}
		}

		/**
		 * @param bytes The binary data to format
		 * @return The formatted data
		 */
		default String format(byte[] bytes) {
			return format(new StringBuilder(), bytes, 0, bytes.length).toString();
		}
	}

	/** The hex characters output by {@link #encodeHex()} (A-F are upper case, but lower case will be parsed as well) */
	public static final String HEX_CHARS = "0123456789ABCDEF";

	/** @return An encoder that encodes and decodes data to and from hex (16-base) sequences */
	public static BinaryDataEncoder encodeHex() {
		return new HexDataParser();
	}

	static class HexDataParser implements BinaryDataEncoder {
		@Override
		public int getRadix() {
			return 16;
		}

		@Override
		public int getEncodedLength(int byteLength) {
			return byteLength * 2;
		}

		@Override
		public int getByteLength(int encodedLength) {
			int evenStrLen = encodedLength & ~1;
			int byteLen = evenStrLen >> 1;
			if (evenStrLen != encodedLength)
				byteLen++;
			return byteLen;
		}

		@Override
		public <A extends CharAccumulator> A format(ByteIterator in, A out, LongConsumer byteCount) throws IOException {
			boolean readyForMore = out.canAcceptMore();
			long count = 0;
			while (readyForMore && in.hasNext()) {
				count++;
				int nextByte = in.next();
				readyForMore = out.accumulate(HEX_CHARS.charAt(nextByte >>> 4));
				if (readyForMore)
					readyForMore = out.accumulate(HEX_CHARS.charAt(nextByte & 0xf));
			}
			if (byteCount != null)
				byteCount.accept(count);
			return out;
		}

		@Override
		public <B extends BinaryAccumulator> B parse(Reader in, B out, LongConsumer byteCount) throws IOException {
			boolean readyForMore = out.canAcceptMore();
			if (!readyForMore) {
				if (byteCount != null)
					byteCount.accept(0);
				return out;
			}
			long count = 0;
			int read = in.read();
			while (readyForMore && read >= 0) {
				count++;
				byte nextByte = (byte) (hexDigit((char) read) << 4);
				read = in.read();
				if (read >= 0) {
					nextByte |= (hexDigit((char) read) & 0xf);
					read = in.read();
				}
				readyForMore = out.accumulate(nextByte);
			}
			if (byteCount != null)
				byteCount.accept(count);
			return out;
		}
	}

	private static final char A_MINUS_0 = 'A' - '0';
	private static final char a_MINUS_A = 'a' - 'A';

	/**
	 * @param ch The hex character to parse
	 * @return The digit (0-15) represented by the character
	 */
	public static int hexDigit(char ch) {
		int dig = ch - '0';
		if (dig < 0)
			throw new IllegalArgumentException("Character " + ch + " (decimal " + (int) ch + ") is not a hex digit");
		else if (dig < 10)
			return dig;
		dig -= A_MINUS_0;
		if (dig >= 6)
			dig -= a_MINUS_A;
		if (dig < 0 || dig >= 6)
			throw new IllegalArgumentException("Character " + ch + " (decimal " + (int) ch + ") is not a hex digit");
		return 10 + dig;
	}

	/**
	 * @param str The hex-formatted character sequence
	 * @return The sequence of bytes, each of which is represented by 2 characters in the sequence
	 */
	public static byte[] parseHex(CharSequence str) {
		return encodeHex().parse(str);
	}

	/** Characters representing digits 0-63 in 64-base */
	public static final String BASE_64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
	/** The padding character to use on the end of base-64 encoded sequences so that there are a multiple of 3 characters */
	public static final char BASE_64_PADDING = '=';
	private static final int ZERO_POSITION_64 = 52;
	private static final int PLUS_POSITION_64 = ZERO_POSITION_64 + 10;
	private static final int ZERO_MINUS_PLUS = '0' - '+';

	/** Encodes data to and from base-64 sequences */
	public static class Base64Encoder implements BinaryDataEncoder {
		@Override
		public int getRadix() {
			return 64;
		}

		@Override
		public int getEncodedLength(int byteLength) {
			int div3 = byteLength / 3;
			if (byteLength % 3 != 0)
				div3++;
			return div3 * 4;
		}

		@Override
		public int getByteLength(int encodedLength) {
			int even4Len = encodedLength & ~0b11;
			int byteLen = (even4Len >> 2) * 3;
			int add = encodedLength - even4Len;
			if (add >= 2)
				byteLen = 2;
			else if (add > 0)
				byteLen++;
			return byteLen;
		}

		@Override
		public <A extends CharAccumulator> A format(ByteIterator in, A out, LongConsumer byteCount) throws IOException {
			boolean readyForMore = out.canAcceptMore();
			long count = 0;
			while (readyForMore && in.hasNext()) {
				count++;
				int firstByte = in.next();
				int firstDig = firstByte >>> 2;
				int secondDig = (firstByte & 0b11) << 4;
				readyForMore = out.accumulate(BASE_64_CHARS.charAt(firstDig));
				if (readyForMore && in.hasNext()) {
					int secondByte = in.next();
					secondDig |= secondByte >>> 4;
					int thirdDig = (secondByte & 0b1111) << 2;
					readyForMore = out.accumulate(BASE_64_CHARS.charAt(secondDig));
					if (readyForMore && in.hasNext()) {
						int thirdByte = in.next();
						thirdDig |= thirdByte >>> 6;
						readyForMore = out.accumulate(BASE_64_CHARS.charAt(thirdDig));
						if (readyForMore) {
							int fourthDig = thirdByte & 0b111111;
							readyForMore = out.accumulate(BASE_64_CHARS.charAt(fourthDig));
						} else
							break;
					} else {
						if (readyForMore) {
							readyForMore = out.accumulate(BASE_64_CHARS.charAt(thirdDig));
							if (readyForMore)
								readyForMore = out.accumulate(BASE_64_PADDING);
						}
						break;
					}
				} else {
					if (readyForMore) {
						readyForMore = out.accumulate(BASE_64_CHARS.charAt(secondDig));
						if (readyForMore)
							readyForMore = out.accumulate(BASE_64_PADDING);
						if (readyForMore)
							readyForMore = out.accumulate(BASE_64_PADDING);
					}
					break;
				}
			}
			if (byteCount != null)
				byteCount.accept(count);
			return out;
		}

		@Override
		public <B extends BinaryAccumulator> B parse(Reader in, B out, LongConsumer byteCount) throws IOException {
			boolean readyForMore = out.canAcceptMore();
			if (!readyForMore) {
				if (byteCount != null)
					byteCount.accept(0);
				return out;
			}
			long count = 0;
			int read = in.read();
			while (readyForMore && read >= 0) {
				count++;
				int firstDig = base64Digit((char) read);
				byte firstByte = (byte) (firstDig << 2);
				read = in.read();
				if (isPadding(read))
					break;
				else if (read >= 0) {
					int secondDig = base64Digit((char) read);
					firstByte |= (byte) (secondDig >>> 4);
					readyForMore = out.accumulate(firstByte);
					if (!readyForMore)
						break;
					count++;
					byte secondByte = (byte) (secondDig << 4);
					read = in.read();
					if (isPadding(read))
						break;
					else if (read >= 0) {
						int thirdDig = base64Digit((char) read);
						secondByte |= (thirdDig >>> 2);
						readyForMore = out.accumulate(secondByte);
						if (!readyForMore)
							break;
						count++;
						byte thirdByte = (byte) (thirdDig << 6);
						read = in.read();
						if (isPadding(read))
							break;
						else if (read >= 0) {
							int fourthDig = base64Digit((char) read);
							thirdByte |= fourthDig;
							read = in.read();
						}
						readyForMore = out.accumulate(thirdByte);
					} else
						readyForMore = out.accumulate(secondByte);
				} else
					readyForMore = out.accumulate(firstByte);
			}
			if (byteCount != null)
				byteCount.accept(count);
			return out;
		}
	}

	/** @return An encoder that encodes and decodes data to and from 64-base sequences */
	public static BinaryDataEncoder encodeBase64() {
		return new Base64Encoder();
	}

	/**
	 * @param ch The 64-base character in a sequence
	 * @return The digit (0-63) represented by the character
	 */
	public static int base64Digit(char ch) {
		if (ch == '=')
			return 0; // Padding
		int dig = ch - '+';
		if (dig < 0)
			throw new IllegalArgumentException("Character " + ch + " (decimal " + (int) ch + ") is not a base-64 digit");
		if (dig < 2)
			return PLUS_POSITION_64 + dig;
		dig -= ZERO_MINUS_PLUS;
		if (dig < 0)
			throw new IllegalArgumentException("Character " + ch + " (decimal " + (int) ch + ") is not a base-64 digit");
		if (dig < 10)
			return ZERO_POSITION_64 + dig;
		dig -= A_MINUS_0;
		if (dig < 0)
			throw new IllegalArgumentException("Character " + ch + " (decimal " + (int) ch + ") is not a base-64 digit");
		if (dig < 26)
			return dig;
		dig -= a_MINUS_A;
		if (dig < 0 || dig >= 26)
			throw new IllegalArgumentException("Character " + ch + " (decimal " + (int) ch + ") is not a base-64 digit");
		return 26 + dig;
	}

	private static boolean isPadding(int base64Byte) {
		return base64Byte == BASE_64_PADDING;
	}
}

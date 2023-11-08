package org.qommons.io;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.qommons.QommonsUtils;
import org.qommons.StringUtils;

/** An interface similar to that of {@link Pattern}. Allows for use of things other than java's regex pattern in place. */
public interface BetterPattern {
	/** A matcher of a pattern against a particular character sequence */
	public interface Matcher {
		/** @return The pattern the text is being matched against */
		BetterPattern getPattern();

		/** @return The match if the entirety of the target sequence matches the pattern, or null otherwise */
		Match matches();

		/** @return The match if the target sequence begins with text matching the pattern, or null otherwise */
		Match lookingAt();

		/** @return The next match in the target sequence, if any */
		Match find();

		/**
		 * @param start The starting index in the sequence to start searching at
		 * @return The next match in the target sequence, if any
		 */
		Match find(int start);
	}

	/** A match of a pattern against a particular sub-sequence of a target character sequence */
	public interface Match {
		/** @return The pattern that matched the text */
		BetterPattern getPattern();

		/** @return The index in the target sequence of the start of the match, inclusive */
		int getStart();

		/** @return The index in the target sequence of the start of the match, exclusive */
		int getEnd();

		/** @return Each captured named group in the match */
		Map<String, NamedGroupCapture> getGroups();
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

	/** @return The names of all named capturing groups in this pattern */
	List<String> getGroups();

	/**
	 * @param text The text to match
	 * @return A matcher for the text
	 */
	Matcher matcher(CharSequence text);

	/**
	 * @param pattern The java regex pattern to wrap
	 * @return A BetterPattern that wraps the regex pattern
	 */
	static BetterPattern forJavaRegex(Pattern pattern) {
		return pattern == null ? null : new JavaRegexPattern(pattern);
	}

	/**
	 * Works the same as {@link Pattern#compile(String)}
	 * 
	 * @param regex The regex string to create a matcher for
	 * @return The regex pattern matcher
	 * @throws PatternSyntaxException If the expression's syntax is invalid
	 */
	static BetterPattern compile(String regex) throws PatternSyntaxException {
		return compile(regex, 0);
	}

	/**
	 * Works the same as {@link Pattern#compile(String, int)}
	 * 
	 * @param regex flags Match flags, a bit mask that may include CASE_INSENSITIVE, MULTILINE, DOTALL, UNICODE_CASE, CANON_EQ, UNIX_LINES,
	 *        LITERAL, UNICODE_CHARACTER_CLASSand COMMENTS
	 * @param flags Match flags, a bit mask that may include CASE_INSENSITIVE, MULTILINE, DOTALL, UNICODE_CASE, CANON_EQ, UNIX_LINES,
	 *        LITERAL, UNICODE_CHARACTER_CLASSand COMMENTS
	 * @return The regex pattern matcher
	 * @throws PatternSyntaxException If the expression's syntax is invalid
	 * @throws IllegalArgumentException If the expression's syntax is invalid
	 */
	static BetterPattern compile(String regex, int flags) throws PatternSyntaxException, IllegalArgumentException {
		return forJavaRegex(Pattern.compile(regex, flags));
	}

	/** Wraps a {@link Pattern} */
	public static class JavaRegexPattern implements BetterPattern {
		private final Pattern thePattern;
		private List<String> theGroups;

		/** @param pattern The pattern to wrap */
		public JavaRegexPattern(Pattern pattern) {
			thePattern = pattern;
		}

		@Override
		public List<String> getGroups() {
			if (theGroups == null)
				theGroups = QommonsUtils.unmodifiableCopy(QommonsUtils.getCaptureGroupNames(thePattern).keySet());
			return theGroups;
		}

		@Override
		public Matcher matcher(CharSequence text) {
			return new JPMatcher(thePattern.matcher(text));
		}

		@Override
		public String toString() {
			return thePattern.pattern();
		}

		class JPMatcher implements Matcher {
			private final java.util.regex.Matcher theMatcher;

			JPMatcher(java.util.regex.Matcher matcher) {
				theMatcher = matcher;
			}

			@Override
			public BetterPattern getPattern() {
				return JavaRegexPattern.this;
			}

			@Override
			public Match matches() {
				if (theMatcher.matches())
					return new JPMatch(theMatcher);
				return null;
			}

			@Override
			public Match lookingAt() {
				if (theMatcher.lookingAt())
					return new JPMatch(theMatcher);
				return null;
			}

			@Override
			public Match find() {
				if (theMatcher.find())
					return new JPMatch(theMatcher);
				return null;
			}

			@Override
			public Match find(int start) {
				if (theMatcher.find(start))
					return new JPMatch(theMatcher);
				return null;
			}
		}

		class JPMatch implements Match {
			private final java.util.regex.Matcher theMatcher;
			private Map<String, NamedGroupCapture> theMatchGroups;

			JPMatch(java.util.regex.Matcher matcher) {
				theMatcher = matcher;
			}

			@Override
			public BetterPattern getPattern() {
				return JavaRegexPattern.this;
			}

			@Override
			public int getStart() {
				return theMatcher.start();
			}

			@Override
			public int getEnd() {
				return theMatcher.end();
			}

			@Override
			public Map<String, NamedGroupCapture> getGroups() {
				if (theMatchGroups == null) {
					List<String> groups = JavaRegexPattern.this.getGroups();
					if (groups.isEmpty())
						return Collections.emptyMap();
					Map<String, NamedGroupCapture> matchGroups = new LinkedHashMap<>(groups.size() * 3 / 2);
					for (String group : groups) {
						int index = theMatcher.start(group);
						if (index >= 0)
							matchGroups.put(group, new NamedGroupCapture(index, theMatcher.group(group)));
					}
					theMatchGroups = Collections.unmodifiableMap(matchGroups);
				}
				return theMatchGroups;
			}

			@Override
			public String toString() {
				return theMatcher.group();
			}
		}
	}

	/** A pattern that is just a simple string search, capable of ignoring white space and character case */
	public static class SimpleStringSearch implements BetterPattern {
		private final CharSequence theMatcherText;
		private final boolean isCaseInsensitive;
		private final boolean isWhiteSpaceInsensitive;

		/**
		 * @param matchText The text to match
		 * @param caseInsensitive Whether to ignore character case
		 * @param whiteSpaceInsensitive Whether to ignore whitespace
		 */
		public SimpleStringSearch(CharSequence matchText, boolean caseInsensitive, boolean whiteSpaceInsensitive) {
			if (caseInsensitive || whiteSpaceInsensitive) {
				StringBuilder matchTextStr = null;
				for (int i = 0; i < matchText.length(); i++) {
					char ch = matchText.charAt(i);
					if (whiteSpaceInsensitive && ch <= ' ') {
						if (matchTextStr == null)
							matchTextStr = new StringBuilder().append(matchText, 0, i);
						continue;
					} else if (caseInsensitive && ch >= 'A' && ch <= 'Z') {
						if (matchTextStr == null)
							matchTextStr = new StringBuilder().append(matchText, 0, i);
						matchTextStr.append((char) (ch + StringUtils.a_MINUS_A));
					} else if (matchTextStr != null)
						matchTextStr.append(ch);
				}
				if (matchTextStr != null)
					matchText = matchTextStr.toString();
			}
			theMatcherText = matchText;
			isCaseInsensitive = caseInsensitive;
			isWhiteSpaceInsensitive = whiteSpaceInsensitive;
		}

		@Override
		public List<String> getGroups() {
			return Collections.emptyList();
		}

		@Override
		public Matcher matcher(CharSequence text) {
			return new SSMatcher(text);
		}

		/** @return Whether this pattern ignores character case */
		public boolean isCaseInsensitive() {
			return isCaseInsensitive;
		}

		/** @return Whether this pattern ignores whitespace */
		public boolean isWhiteSpaceInsensitive() {
			return isWhiteSpaceInsensitive;
		}

		@Override
		public String toString() {
			if (!isCaseInsensitive && !isWhiteSpaceInsensitive)
				return theMatcherText.toString();
			StringBuilder str = new StringBuilder(theMatcherText).append('(');
			if (isCaseInsensitive)
				str.append('i');
			if (isWhiteSpaceInsensitive)
				str.append('w');
			return str.append(')').toString();
		}

		class SSMatcher implements Matcher {
			private final CharSequence theText;
			private int theIndex;

			SSMatcher(CharSequence text) {
				theText = text;
			}

			@Override
			public BetterPattern getPattern() {
				return SimpleStringSearch.this;
			}

			@Override
			public Match matches() {
				Match match = check(theIndex);
				if (match != null && match.getEnd() == theText.length() - theIndex)
					return match;
				return null;
			}

			@Override
			public Match lookingAt() {
				return check(theIndex);
			}

			@Override
			public Match find() {
				for (int i = theIndex; i < theText.length() - theMatcherText.length(); i++) {
					Match found = check(i);
					if (found != null) {
						theIndex = i + 1;
						return found;
					}
				}
				return null;
			}

			@Override
			public Match find(int start) {
				theIndex = start;
				return find();
			}

			Match check(int start) {
				if (theText.length() - start < theMatcherText.length())
					return null;
				int i = 0, j = start;
				while (i < theMatcherText.length() && j < theText.length()) {
					char c1 = theMatcherText.charAt(i);
					char c2 = theText.charAt(j);
					int diff = c1 - c2;
					if (diff == 0) {
						i++;
						j++;
					} else if (isWhiteSpaceInsensitive && c2 <= ' ')
						j++;
					else if (isCaseInsensitive && diff == StringUtils.a_MINUS_A && c1 >= 'a' && c1 <= 'z') {
						i++;
						j++;
					} else
						return null;
				}
				if (isWhiteSpaceInsensitive) {
					while (i < theMatcherText.length() && theMatcherText.charAt(i) <= ' ')
						i++;
					while (j < theText.length() && theText.charAt(j) <= ' ')
						j++;
				}
				return new SSMatch(theText, start, j);
			}
		}

		class SSMatch implements Match {
			private final CharSequence theText;
			private final int theStart;
			private final int theEnd;
			private String theMatchText;

			SSMatch(CharSequence text, int start, int end) {
				theText = text;
				theStart = start;
				theEnd = end;
			}

			@Override
			public BetterPattern getPattern() {
				return SimpleStringSearch.this;
			}

			@Override
			public int getStart() {
				return theStart;
			}

			@Override
			public int getEnd() {
				return theEnd;
			}

			@Override
			public Map<String, NamedGroupCapture> getGroups() {
				return Collections.emptyMap();
			}

			@Override
			public String toString() {
				if (theMatchText == null)
					theMatchText = theText.subSequence(theStart, theEnd).toString();
				return theMatchText;
			}
		}
	}
}

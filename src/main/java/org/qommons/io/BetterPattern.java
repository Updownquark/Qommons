package org.qommons.io;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.qommons.DefaultCharSubSequence;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.collect.BetterList;
import org.qommons.collect.MappedSet;

/** An interface similar to that of {@link Pattern}. Allows for use of things other than java's regex pattern in place. */
public interface BetterPattern {
	/** A pattern that never matches anything */
	public static final BetterPattern EMPTY = new BetterPattern() {
		private final Matcher theEmptyMatch = new Matcher() {
			@Override
			public BetterPattern getPattern() {
				return BetterPattern.EMPTY;
			}

			@Override
			public Match matches() {
				return null;
			}

			@Override
			public Match lookingAt() {
				return null;
			}

			@Override
			public Match find() {
				return null;
			}

			@Override
			public Match find(int start) {
				return null;
			}

			@Override
			public String toString() {
				return "(empty)";
			}
		};

		@Override
		public Set<String> getGroups() {
			return Collections.emptySet();
		}

		@Override
		public int getAllGroupCount() {
			return 0;
		}

		@Override
		public Matcher matcher(CharSequence text) {
			return theEmptyMatch;
		}

		@Override
		public String toString() {
			return "(empty)";
		}
	};

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

	/** Represents the capture of a named group in a pattern-matched text sequence */
	public static class GroupCapture {
		/** The start of the captured sequence within the whole */
		public final int start;
		/** The matched group sequence */
		public final String value;

		/**
		 * @param start The start of the captured sequence within the whole
		 * @param value The matched group sequence
		 */
		public GroupCapture(int start, String value) {
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
			else if (!(obj instanceof GroupCapture))
				return false;
			GroupCapture other = (GroupCapture) obj;
			return start == other.start && value.equals(other.value);
		}

		@Override
		public String toString() {
			return value;
		}
	}

	/** A reference to a group in a pattern, either by name or by index */
	class GroupReference {
		private final int theIndex;
		private final String theName;

		GroupReference(int index) {
			theIndex = index;
			theName = null;
		}

		GroupReference(String name) {
			theIndex = -1;
			theName = name;
		}

		public int getIndex() {
			return theIndex;
		}

		public String getName() {
			return theName;
		}

		@Override
		public int hashCode() {
			if (theIndex >= 0)
				return theIndex;
			else
				return theName.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof GroupReference))
				return false;
			GroupReference other = (GroupReference) obj;
			return theIndex == other.theIndex && Objects.equals(theName, other.theName);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder().append('\\');
			if (theName != null)
				str.append('{').append(theName).append('}');
			else
				str.append(theIndex + 1);
			return str.toString();
		}
	}

	/** Interface for access to captured groups */
	public interface GroupCaptureAccess {
		/** @return Each captured named group in the match */
		Map<String, GroupCapture> getNamedGroups();

		/** @return The number of groups available by index */
		int getAllGroupCount();

		/**
		 * @param index The index of the group to get
		 * @return The capture in the match for the group at the given index
		 */
		GroupCapture getGroup(int index);

		/**
		 * @param reference The group reference to get the capture for
		 * @return The capture referred to by the reference
		 */
		default GroupCapture getGroup(GroupReference reference) {
			if (reference.getName() != null)
				return getNamedGroups().get(reference.getName());
			else
				return getGroup(reference.getIndex());
		}
	}

	/** A match of a pattern against a particular sub-sequence of a target character sequence */
	public interface Match extends GroupCaptureAccess, Comparable<Match> {
		/** @return The pattern that matched the text */
		BetterPattern getPattern();

		/** @return The index in the target sequence of the start of the match, inclusive */
		int getStart();

		/** @return The index in the target sequence of the start of the match, exclusive */
		int getEnd();

		@Override
		default int compareTo(Match o) {
			return Integer.compare(getStart(), o.getStart());
		}

		/** @return The entire text matched in this match */
		CharSequence getMatchText();

		@Override
		default GroupCapture getGroup(GroupReference reference) {
			if (reference.getName() != null)
				return getNamedGroups().get(reference.getName());
			else if (reference.getIndex() < 0)
				return new GroupCapture(getStart(), getMatchText().toString());
			else
				return getGroup(reference.getIndex());
		}
	}

	/** @return The names of all named capturing groups in this pattern */
	Set<String> getGroups();

	/** @return The total number of groups (named or unnamed) in the pattern */
	int getAllGroupCount();

	/**
	 * @param text The text to match
	 * @return A matcher for the text
	 */
	Matcher matcher(CharSequence text);

	/** @return A pattern that matches identically to this one, but returns an empty match on failure */
	default BetterPattern optional() {
		return times(0, 1);
	}

	/**
	 * @param min The minimum number of times this pattern must match in the sequence
	 * @param max The minimum number of times this pattern will be matched in the sequence
	 * @return A pattern that matches this pattern a number of times
	 */
	default BetterPattern times(int min, int max) {
		return new MultiInstancePattern(this, min, max);
	}

	/**
	 * @param name The name of the capturing group, or null to only provide the captured content by index
	 * @return This pattern, but with the matched content exposed as a capturing group
	 */
	default BetterPattern asCapturingGroup(String name) {
		return new GroupPattern(this, true, name);
	}

	/**
	 * @param next The patterns to match after this one
	 * @return A pattern that matches text matching this pattern followed by text matching the given patterns
	 */
	default BetterPattern andThen(BetterPattern... next) {
		List<BetterPattern> components = new ArrayList<>(next.length + 1);
		components.add(this);
		for (BetterPattern p : next)
			components.add(p);
		return new StructuredPattern(Collections.unmodifiableList(components));
	}

	/**
	 * @param secondOption The first option after this pattern
	 * @param moreOptions Further options
	 * @return A pattern that matches text matched by this pattern or any of the other given options
	 */
	default BetterPattern or(BetterPattern secondOption, BetterPattern... moreOptions) {
		List<BetterPattern> options = new ArrayList<>(2 + moreOptions.length);
		options.add(this);
		options.add(secondOption);
		for (BetterPattern option : moreOptions)
			options.add(option);
		return new OrPattern(Collections.unmodifiableList(options));
	}

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
	 *        LITERAL, UNICODE_CHARACTER_CLASS and COMMENTS
	 * @param flags Match flags, a bit mask that may include CASE_INSENSITIVE, MULTILINE, DOTALL, UNICODE_CASE, CANON_EQ, UNIX_LINES,
	 *        LITERAL, UNICODE_CHARACTER_CLASS and COMMENTS
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
		private Set<String> theGroups;
		private int theTotalGroupCount;

		/** @param pattern The pattern to wrap */
		public JavaRegexPattern(Pattern pattern) {
			thePattern = pattern;
			theTotalGroupCount = -1;
		}

		@Override
		public Set<String> getGroups() {
			if (theGroups == null)
				theGroups = Collections.unmodifiableSet(QommonsUtils.getCaptureGroupNames(thePattern).keySet());
			return theGroups;
		}

		@Override
		public int getAllGroupCount() {
			if (theTotalGroupCount < 0) {
				// No API to get this. We'll do our best.
				int count = 0;
				int open = 0;
				boolean escaped = false;
				for (int c = 0; c < thePattern.pattern().length(); c++) {
					char ch = thePattern.pattern().charAt(c);
					if (c == '\\')
						escaped = !escaped;
					else if (escaped) {//
					} else if (ch == '(') {
						open++;
					} else if (ch == ')' && open > 0) {
						open--;
						count++;
					}
				}
				theTotalGroupCount = count;
			}
			return theTotalGroupCount;
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
			private Map<String, GroupCapture> theMatchGroups;

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
			public Map<String, GroupCapture> getNamedGroups() {
				if (theMatchGroups == null) {
					Set<String> groups = JavaRegexPattern.this.getGroups();
					if (groups.isEmpty())
						return Collections.emptyMap();
					Map<String, GroupCapture> matchGroups = new LinkedHashMap<>(groups.size() * 3 / 2);
					for (String group : groups) {
						int index = theMatcher.start(group);
						if (index >= 0)
							matchGroups.put(group, new GroupCapture(index, theMatcher.group(group)));
					}
					theMatchGroups = Collections.unmodifiableMap(matchGroups);
				}
				return theMatchGroups;
			}

			@Override
			public int getAllGroupCount() {
				return theMatcher.groupCount();
			}

			@Override
			public GroupCapture getGroup(int index) {
				if (index < 0)
					throw new IndexOutOfBoundsException(index + " of " + getAllGroupCount());
				index++; // Group zero is the entire match
				int start = theMatcher.start(index);
				String value = theMatcher.group(index);
				return new GroupCapture(start, value);
			}

			@Override
			public CharSequence getMatchText() {
				return theMatcher.group();
			}

			@Override
			public String toString() {
				return theMatcher.group();
			}
		}
	}

	/** Interface to support back references in a pattern */
	public interface BackRefEnabledMatcher extends Matcher {
		/**
		 * @param captures Access to groups captured so far in the sequence
		 * @return The match if the target sequence begins with text matching the pattern, or null otherwise
		 */
		Match lookingAt(GroupCaptureAccess captures);
	}

	/** A pattern that is just a simple string search, capable of ignoring white space and character case */
	public static class SimpleStringSearch implements BetterPattern {
		private final CharSequence theMatcherText;
		private final boolean isCaseInsensitive;
		private final boolean isWhiteSpaceInsensitive;

		/** @param matchText The text to match */
		public SimpleStringSearch(CharSequence matchText) {
			this(matchText, false, false);
		}

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
		public Set<String> getGroups() {
			return Collections.emptySet();
		}

		@Override
		public int getAllGroupCount() {
			return 0;
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
			public Map<String, GroupCapture> getNamedGroups() {
				return Collections.emptyMap();
			}

			@Override
			public int getAllGroupCount() {
				return getPattern().getAllGroupCount();
			}

			@Override
			public GroupCapture getGroup(int index) {
				throw new IndexOutOfBoundsException(index + " of 0");
			}

			@Override
			public CharSequence getMatchText() {
				return toString();
			}

			@Override
			public String toString() {
				if (theMatchText == null)
					theMatchText = theText.subSequence(theStart, theEnd).toString();
				return theMatchText;
			}
		}
	}

	/** A pattern that matches exactly those sequences that a given pattern doesn't match */
	public static class NotPattern implements BetterPattern {
		private final BetterPattern theWrapped;

		/** @param wrapped The pattern to be the converse of */
		public NotPattern(BetterPattern wrapped) {
			theWrapped = wrapped;
		}

		/** @return The pattern that this is the converse of */
		public BetterPattern getWrapped() {
			return theWrapped;
		}

		@Override
		public Set<String> getGroups() {
			return Collections.emptySet();
		}

		@Override
		public int getAllGroupCount() {
			return 0;
		}

		@Override
		public Matcher matcher(CharSequence text) {
			return new NotMatcher(text, theWrapped.matcher(text));
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(theWrapped.toString());
			if (str.length() > 0 && str.charAt(0) == '[')
				str.insert(1, '^');
			else
				str.insert(0, '^');
			return str.toString();
		}

		// Not thinking too hard about this--this pattern class is intended to be used with simple wrapped patterns
		// like one-character
		class NotMatcher implements BackRefEnabledMatcher {
			private final CharSequence theText;
			private final Matcher theWrappedMatcher;

			NotMatcher(CharSequence text, Matcher wrappedMatcher) {
				theText = text;
				theWrappedMatcher = wrappedMatcher;
			}

			@Override
			public BetterPattern getPattern() {
				return NotPattern.this;
			}

			@Override
			public Match matches() {
				if (theWrappedMatcher.matches() != null)
					return null;
				return new NotMatch(theText, 0);
			}

			@Override
			public Match lookingAt() {
				return lookingAt(null);
			}

			@Override
			public Match lookingAt(GroupCaptureAccess captures) {
				if (theWrappedMatcher instanceof BackRefEnabledMatcher) {
					if (((BackRefEnabledMatcher) theWrappedMatcher).lookingAt(captures) != null)
						return null;
				} else if (theWrappedMatcher.lookingAt() != null)
					return null;
				return new NotMatch(theText.length() == 1 ? theText : new DefaultCharSubSequence(theText, 0, 1), 0);
			}

			@Override
			public Match find() {
				return find(0);
			}

			@Override
			public Match find(int start) {
				if (start >= theText.length())
					return null;
				Match wrapped = theWrappedMatcher.find(0);
				if (wrapped != null && wrapped.getStart() == start) {
					if (wrapped.getEnd() == theText.length())
						return null;
					else
						return new NotMatch(new DefaultCharSubSequence(theText, wrapped.getEnd(), wrapped.getEnd() + 1), wrapped.getEnd());
				} else
					return new NotMatch(theText.length() == start + 1 ? theText : new DefaultCharSubSequence(theText, start, start + 1),
						start);
			}
		}

		class NotMatch implements Match {
			private final CharSequence theMatchText;
			private final int theStart;

			NotMatch(CharSequence matchText, int start) {
				theMatchText = matchText;
				theStart = start;
			}

			@Override
			public BetterPattern getPattern() {
				return NotPattern.this;
			}

			@Override
			public int getStart() {
				return theStart;
			}

			@Override
			public int getEnd() {
				return theStart + 1;
			}

			@Override
			public Map<String, GroupCapture> getNamedGroups() {
				return Collections.emptyMap();
			}

			@Override
			public int getAllGroupCount() {
				return getPattern().getAllGroupCount();
			}

			@Override
			public GroupCapture getGroup(int index) {
				throw new IndexOutOfBoundsException(index + " of 0");
			}

			@Override
			public CharSequence getMatchText() {
				return theMatchText;
			}

			@Override
			public String toString() {
				return theMatchText.toString();
			}
		}
	}

	/** Default implementation of {@link BetterPattern#times(int, int)} */
	public static class MultiInstancePattern implements BetterPattern {
		private final BetterPattern theComponent;
		private final int theMinCount;
		private final int theMaxCount;

		/**
		 * @param component The pattern to match
		 * @param minCount The minimum number of times that content matching the pattern must be found
		 * @param maxCount The maximum number of times that content matching the pattern will be matched
		 */
		public MultiInstancePattern(BetterPattern component, int minCount, int maxCount) {
			theComponent = component;
			theMinCount = minCount;
			theMaxCount = maxCount;
		}

		/** @return The pattern to match against content */
		public BetterPattern getComponent() {
			return theComponent;
		}

		/** @return The minimum number of times that content matching the pattern must be found */
		public int getMinCount() {
			return theMinCount;
		}

		/** @return The maximum number of times that content matching the pattern will be matched */
		public int getMaxCount() {
			return theMaxCount;
		}

		@Override
		public Set<String> getGroups() {
			return theComponent.getGroups();
		}

		@Override
		public int getAllGroupCount() {
			return theComponent.getAllGroupCount();
		}

		@Override
		public Matcher matcher(CharSequence text) {
			return new MultiInstanceMatcher(text);
		}

		@Override
		public BetterPattern times(int min, int max) {
			long maxL = theMaxCount * max;
			int newMax = maxL > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) maxL;
			return new MultiInstancePattern(theComponent, theMinCount * min, newMax);
		}

		class MultiInstanceMatcher implements BackRefEnabledMatcher {
			private final CharSequence theText;

			MultiInstanceMatcher(CharSequence text) {
				theText = text;
			}

			@Override
			public BetterPattern getPattern() {
				return MultiInstancePattern.this;
			}

			@Override
			public Match matches() {
				Match match = lookingAt();
				if (match == null || match.getEnd() != theText.length())
					return null;
				return match;
			}

			@Override
			public Match lookingAt() {
				Match match = theComponent.matcher(theText).lookingAt();
				return getMatch(match, null);
			}

			private Match getMatch(Match firstMatch, GroupCaptureAccess captures) {
				if (firstMatch == null) {
					if (theMinCount == 0)
						return new MultiInstanceMatch(Collections.emptyList(), 0, "");
					else
						return null;
				}
				List<Match> components = new ArrayList<>();
				int start = 0;
				Match match = firstMatch;
				while (match != null) {
					components.add(match);
					start += match.getEnd();
					if (start == theText.length() || components.size() == theMaxCount)
						break;
					Matcher matcher = theComponent.matcher(new DefaultCharSubSequence(theText, start, theText.length()));
					if (captures == null || !(matcher instanceof BackRefEnabledMatcher))
						match = matcher.lookingAt();
					else
						match = ((BackRefEnabledMatcher) matcher).lookingAt(captures);
				}
				CharSequence matchText;
				if (firstMatch.getStart() == 0) {
					if (start == theText.length())
						matchText = theText;
					else
						matchText = new DefaultCharSubSequence(theText, 0, start);
				} else
					matchText = new DefaultCharSubSequence(theText, firstMatch.getStart(), start);
				return new MultiInstanceMatch(components, firstMatch.getStart(), matchText);
			}

			@Override
			public Match find() {
				return find(0);
			}

			@Override
			public Match find(int start) {
				Match match = theComponent.matcher(theText).find(start);
				return match == null ? null : getMatch(match, null);
			}

			@Override
			public Match lookingAt(GroupCaptureAccess captures) {
				Matcher matcher = theComponent.matcher(theText);
				Match firstMatch;
				if (matcher instanceof BackRefEnabledMatcher)
					firstMatch = ((BackRefEnabledMatcher) matcher).lookingAt(captures);
				else
					firstMatch = matcher.lookingAt();
				return firstMatch == null ? null : getMatch(firstMatch, captures);
			}
		}

		class MultiInstanceMatch implements Match {
			private final List<Match> theComponents;
			private final int theStart;
			private final CharSequence theMatchText;

			MultiInstanceMatch(List<Match> components, int start, CharSequence matchText) {
				theComponents = components;
				theStart = start;
				theMatchText = matchText;
			}

			@Override
			public Map<String, GroupCapture> getNamedGroups() {
				if (theComponents.isEmpty()) {
					if (getGroups().isEmpty())
						return Collections.emptyMap();
					else
						return new NoValuesMap<>(getGroups());
				}
				return theComponents.get(0).getNamedGroups();
			}

			@Override
			public int getAllGroupCount() {
				return theComponent.getAllGroupCount();
			}

			@Override
			public GroupCapture getGroup(int index) {
				return theComponents.get(0).getGroup(index);
			}

			@Override
			public BetterPattern getPattern() {
				return MultiInstancePattern.this;
			}

			@Override
			public int getStart() {
				return theStart;
			}

			@Override
			public int getEnd() {
				return theStart + theMatchText.length();
			}

			@Override
			public CharSequence getMatchText() {
				return theMatchText;
			}

			@Override
			public String toString() {
				return theMatchText.toString();
			}
		}
	}

	/** Default implementation of {@link BetterPattern#or(BetterPattern, BetterPattern...)} */
	public static class OrPattern implements BetterPattern {
		private final List<BetterPattern> theOptions;

		OrPattern(List<BetterPattern> options) {
			theOptions = options;
		}

		/** @return The pattern options */
		public List<BetterPattern> getOptions() {
			return theOptions;
		}

		@Override
		public Set<String> getGroups() {
			if (theOptions.stream().allMatch(p -> p.getGroups().isEmpty()))
				return Collections.emptySet();
			Set<String> groups = theOptions.stream()//
				.flatMap(p -> p.getGroups().stream())//
				.collect(Collectors.toCollection(LinkedHashSet::new));
			return Collections.unmodifiableSet(groups);
		}

		@Override
		public int getAllGroupCount() {
			return theOptions.stream()//
				.mapToInt(BetterPattern::getAllGroupCount)//
				.reduce(0, Math::max);
		}

		@Override
		public Matcher matcher(CharSequence text) {
			return new OrMatcher(QommonsUtils.map(theOptions, p -> p.matcher(text), false));
		}

		@Override
		public BetterPattern or(BetterPattern secondOption, BetterPattern... moreOptions) {
			List<BetterPattern> options = new ArrayList<>(theOptions.size() + 1 + moreOptions.length);
			options.addAll(theOptions);
			options.add(secondOption);
			for (BetterPattern p : moreOptions)
				options.add(p);
			return new OrPattern(Collections.unmodifiableList(options));
		}

		class OrMatcher implements BackRefEnabledMatcher {
			private final List<Matcher> theOptionMatchers;

			OrMatcher(List<Matcher> optionMatchers) {
				theOptionMatchers = optionMatchers;
			}

			@Override
			public BetterPattern getPattern() {
				return OrPattern.this;
			}

			private Match matchFor(Function<Matcher, Match> op) {
				for (Matcher option : theOptionMatchers) {
					Match match = op.apply(option);
					if (match != null)
						return new OrMatch(match);
				}
				return null;
			}

			@Override
			public Match matches() {
				return matchFor(Matcher::matches);
			}

			@Override
			public Match lookingAt() {
				return matchFor(Matcher::lookingAt);
			}

			@Override
			public Match find() {
				return find(0);
			}

			@Override
			public Match find(int start) {
				return matchFor(m -> m.find(start));
			}

			@Override
			public Match lookingAt(GroupCaptureAccess captures) {
				return matchFor(m -> {
					if (m instanceof BackRefEnabledMatcher)
						return ((BackRefEnabledMatcher) m).lookingAt(captures);
					else
						return m.lookingAt();
				});
			}
		}

		class OrMatch implements Match {
			private final Match theOption;
			private final Map<String, GroupCapture> theNamedGroups;

			OrMatch(Match option) {
				theOption = option;
				Set<String> patternGroups = getPattern().getGroups();
				Map<String, GroupCapture> optionGroups = theOption.getNamedGroups();
				if (patternGroups.isEmpty())
					theNamedGroups = Collections.emptyMap();
				else if (optionGroups.keySet().containsAll(patternGroups))
					theNamedGroups = optionGroups;
				else if (theOption.getNamedGroups().isEmpty()) {
					theNamedGroups = new NoValuesMap<>(patternGroups);
				} else {
					Map<String, GroupCapture> groups = new LinkedHashMap<>();
					for (String group : patternGroups) {
						groups.put(group, optionGroups.get(group));
					}
					theNamedGroups = Collections.unmodifiableMap(groups);
				}
			}

			@Override
			public Map<String, GroupCapture> getNamedGroups() {
				return theNamedGroups;
			}

			@Override
			public int getAllGroupCount() {
				return getPattern().getAllGroupCount();
			}

			@Override
			public GroupCapture getGroup(int index) {
				if (index < 0 || index > getAllGroupCount())
					throw new IndexOutOfBoundsException(index + " of " + getAllGroupCount());
				else if (index < theOption.getAllGroupCount())
					return theOption.getGroup(index);
				else
					return null;
			}

			@Override
			public BetterPattern getPattern() {
				return OrPattern.this;
			}

			@Override
			public int getStart() {
				return theOption.getStart();
			}

			@Override
			public int getEnd() {
				return theOption.getEnd();
			}

			@Override
			public CharSequence getMatchText() {
				return theOption.getMatchText();
			}

			@Override
			public String toString() {
				return theOption.toString();
			}
		}
	}

	/**
	 * A pattern to match a sequence of characters all in the same "class". A character "class" can be anything. Common instances are digits
	 * (0-9), or whitespace (e.g. space or tab).
	 */
	public static class CharClassPattern implements BetterPattern {
		/** A test for whether a character is in the class */
		public interface CharClass {
			/**
			 * @param ch The character to test
			 * @return Whether the character belongs to this class
			 */
			public boolean matches(char ch);
		}

		/** {@link CharClass} implementation with a name */
		public static class DefaultCharClass implements CharClass {
			private final CharClass theWrapped;
			private final String theName;

			/**
			 * @param wrapped The wrapped character class
			 * @param name The name of the class
			 */
			public DefaultCharClass(CharClass wrapped, String name) {
				theWrapped = wrapped;
				theName = name;
			}

			@Override
			public boolean matches(char ch) {
				return theWrapped.matches(ch);
			}

			@Override
			public String toString() {
				return theName;
			}
		}

		/** A character class matching any character */
		public static final CharClass ALL_CLASS = new DefaultCharClass(ch -> true, ".");
		/** A character class matching any character except the new line */
		public static final CharClass ALL_NO_NEWLINE_CLASS = new DefaultCharClass(ch -> ch != '\n', ".");
		/** A character class matching digits 0-9 */
		public static final CharClass DIGITS = new DefaultCharClass(ch -> ch >= '0' && ch <= '9', "\\d");
		/** A character class matching white space */
		public static final CharClass WHITE_SPACE = new DefaultCharClass(Character::isWhitespace, "\\s");

		// public static final FlagPresentCondition DOT_MATCHES_ALL = new FlagPresentCondition(Pattern.DOTALL);
		/** A pattern matching any single character */
		public static final CharClassPattern ALL_SEARCH = new CharClassPattern(ALL_CLASS, 1, Integer.MAX_VALUE);
		/** A pattern matching any single character except the new line */
		public static final CharClassPattern ALL_NO_NEWLINE_SEARCH = new CharClassPattern(ALL_NO_NEWLINE_CLASS, 1, Integer.MAX_VALUE);
		/** A pattern matching any single digit 0-9 */
		public static final CharClassPattern ONE_DIGIT = new CharClassPattern(DIGITS, 1, 1);

		private final CharClass theClass;
		private final int theMin;
		private final int theMax;

		/**
		 * @param class1 The character class
		 * @param min The minimum number of characters in a matched sequence
		 * @param max The maximum number of characters that will be matched in a sequence
		 */
		public CharClassPattern(CharClass class1, int min, int max) {
			if (min < 0 || max == 0 || min > max)
				throw new IllegalArgumentException(min + " to " + max);
			theClass = class1;
			theMin = min;
			theMax = max;
		}

		/** @return The character class */
		public CharClass getCharClass() {
			return theClass;
		}

		/** @return The minimum number of characters in a sequence that may be matched with this pattern */
		public int getMin() {
			return theMin;
		}

		/** @return The maximum number of characters in sequences matched with this pattern */
		public int getMax() {
			return theMax;
		}

		@Override
		public Set<String> getGroups() {
			return Collections.emptySet();
		}

		@Override
		public int getAllGroupCount() {
			return 0;
		}

		@Override
		public Matcher matcher(CharSequence text) {
			return new CharClassMatcher(text);
		}

		@Override
		public BetterPattern times(int min, int max) {
			long maxL = theMax * max;
			int newMax = maxL > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) maxL;
			return new CharClassPattern(theClass, theMin * min, newMax);
		}

		@Override
		public String toString() {
			if (theMin == 1 && theMax == 1)
				return theClass.toString();
			StringBuilder str = new StringBuilder(theClass.toString());
			switch (theMin) {
			case 0:
				switch (theMax) {
				case 1:
					str.append('?');
					break;
				case Integer.MAX_VALUE:
					str.append('*');
					break;
				default:
					str.append("{0,").append(theMax).append('}');
					break;
				}
				break;
			case 1:
				if (theMax == Integer.MAX_VALUE)
					str.append('+');
				else
					str.append("{1,").append(theMax).append('}');
				break;
			default:
				str.append('{');
				str.append(theMin);
				if (theMin != theMax)
					str.append(',').append(theMax);
				str.append('}');
				break;
			}
			return str.toString();
		}

		class CharClassMatcher implements Matcher {
			private final CharSequence theText;

			CharClassMatcher(CharSequence text) {
				theText = text;
			}

			@Override
			public BetterPattern getPattern() {
				return CharClassPattern.this;
			}

			private Match match(int start, int end) {
				if (start == 0 && end == theText.length())
					return new CharClassMatch(theText, 0);
				else
					return new CharClassMatch(new DefaultCharSubSequence(theText, start, end), start);
			}

			@Override
			public Match matches() {
				if (theText.length() < theMin || theText.length() > theMax)
					return null;
				int c;
				for (c = 0; c < theText.length(); c++) {
					if (!theClass.matches(theText.charAt(c)))
						break;
				}
				if (c < theText.length())
					return null;
				return new CharClassMatch(theText, 0);
			}

			@Override
			public Match lookingAt() {
				int c;
				int end = Math.min(theText.length(), theMax);
				for (c = 0; c < end; c++) {
					if (!theClass.matches(theText.charAt(c)))
						break;
				}
				if (c < theMin)
					return null;
				return match(0, c);
			}

			@Override
			public Match find() {
				return find(0);
			}

			@Override
			public Match find(int start) {
				int maxStart = theText.length() - theMin;
				int matchStart, matchEnd;
				for (matchStart = start; matchStart < maxStart; matchStart = matchEnd + 1) {
					int matchMax = Math.min(theText.length(), add(matchStart, theMax));
					for (matchEnd = matchStart; matchEnd < matchMax; matchEnd++) {
						if (!theClass.matches(theText.charAt(matchEnd)))
							break;
					}
					if (matchEnd >= matchStart + theMin)
						return match(matchStart, matchEnd);
				}
				return null;
			}
		}

		static int add(int i1, int i2) {
			int sum = i1 + i2;
			if (sum < 0)
				return Math.max(i1, i2);
			return sum;
		}

		class CharClassMatch implements Match {
			private final CharSequence theMatchText;
			private final int theStart;

			CharClassMatch(CharSequence matchText, int start) {
				theMatchText = matchText;
				theStart = start;
			}

			@Override
			public BetterPattern getPattern() {
				return CharClassPattern.this;
			}

			@Override
			public int getStart() {
				return theStart;
			}

			@Override
			public int getEnd() {
				return theStart + theMatchText.length();
			}

			@Override
			public Map<String, GroupCapture> getNamedGroups() {
				return Collections.emptyMap();
			}

			@Override
			public int getAllGroupCount() {
				return getPattern().getAllGroupCount();
			}

			@Override
			public GroupCapture getGroup(int index) {
				throw new IndexOutOfBoundsException(index + " of 0");
			}

			@Override
			public CharSequence getMatchText() {
				return theMatchText;
			}

			@Override
			public String toString() {
				return theMatchText.toString();
			}
		}
	}

	/** Default implementation of {@link BetterPattern#asCapturingGroup(String)} */
	public static class GroupPattern implements BetterPattern {
		private final BetterPattern theBacking;
		private final boolean isCapturing;
		private final String theGroupName;

		/**
		 * @param backing The pattern to match
		 * @param capturing Whether to expose the content as a capturing group
		 * @param groupName The name of the group, if it is to be exposed by name
		 */
		public GroupPattern(BetterPattern backing, boolean capturing, String groupName) {
			theBacking = backing;
			isCapturing = capturing;
			theGroupName = groupName;
		}

		/** @return The pattern to match */
		public BetterPattern getBacking() {
			return theBacking;
		}

		/** @return Whether the content is exposed as a capturing group */
		public boolean isCapturing() {
			return isCapturing;
		}

		/** @return The name of the group, if it is to be exposed by name */
		public String getGroupName() {
			return theGroupName;
		}

		@Override
		public Set<String> getGroups() {
			Set<String> backingGroups = theBacking.getGroups();
			if (!isCapturing || theGroupName == null)
				return backingGroups;
			Set<String> groups = new LinkedHashSet<>();
			groups.add(theGroupName);
			if (!backingGroups.contains(theGroupName))
				groups.addAll(backingGroups);
			else {
				for (String group : backingGroups) {
					if (!theGroupName.equals(group))
						groups.add(group);
				}
			}
			return Collections.unmodifiableSet(groups);
		}

		@Override
		public int getAllGroupCount() {
			if (isCapturing)
				return theBacking.getAllGroupCount() + 1;
			else
				return theBacking.getAllGroupCount();
		}

		@Override
		public Matcher matcher(CharSequence text) {
			return new GroupMatcher(theBacking.matcher(text));
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append('(');
			if (isCapturing) {
				if (theGroupName != null)
					str.append("?<").append(theGroupName).append('>');
			} else
				str.append("?:");
			str.append(theBacking).append(')');
			return str.toString();
		}

		/*public static GroupPattern parseComponentDefault(PatternParsing parsing) throws ParseException {
			CharSequence matchText = parsing.getTextUntil(")");
			if (matchText.length() == 0)
				return new GroupPattern(new SimpleStringSearch("", false, false), true, null);
			boolean q = matchText.charAt(1) == '?';
			String name;
			int contentStart;
			if (q && matchText.length() > 4 && matchText.charAt(2) == '<') {
				int nameEnd;
				for (nameEnd = 3; nameEnd < matchText.length(); nameEnd++) {
					if (matchText.charAt(nameEnd) == '>')
						break;
				}
				if (nameEnd == matchText.length())
					throw new ParseException("Unmatched group name: '>' expected", nameEnd);
				name = matchText.subSequence(3, nameEnd).toString();
				contentStart = nameEnd + 1;
			} else {
				name = null;
				if (q)
					contentStart = 2;
				else
					contentStart = 1;
			}
			BetterPattern content = parsing.parsePattern(contentStart, matchText.length(), parsing.getFlags());
			boolean capturing = name != null || !q;
			return new GroupPattern(content, capturing, name);
		}*/

		class GroupMatcher implements BackRefEnabledMatcher {
			private final Matcher theBackingMatcher;

			GroupMatcher(Matcher backingMatcher) {
				theBackingMatcher = backingMatcher;
			}

			@Override
			public BetterPattern getPattern() {
				return GroupPattern.this;
			}

			private Match wrap(Match wrapped) {
				return wrapped == null ? null : new GroupMatch(wrapped);
			}

			@Override
			public Match matches() {
				return wrap(theBackingMatcher.matches());
			}

			@Override
			public Match lookingAt() {
				return wrap(theBackingMatcher.lookingAt());
			}

			@Override
			public Match lookingAt(GroupCaptureAccess captures) {
				if (theBackingMatcher instanceof BackRefEnabledMatcher)
					return wrap(((BackRefEnabledMatcher) theBackingMatcher).lookingAt(captures));
				else
					return wrap(theBackingMatcher.lookingAt());
			}

			@Override
			public Match find() {
				return wrap(theBackingMatcher.find());
			}

			@Override
			public Match find(int start) {
				return wrap(theBackingMatcher.find(start));
			}
		}

		class GroupMatch implements Match {
			private final Match theBackingMatch;
			private final GroupCapture theCapture;
			private final Map<String, GroupCapture> theNamedGroups;

			GroupMatch(Match backingMatch) {
				theBackingMatch = backingMatch;
				theCapture = new GroupCapture(theBackingMatch.getStart(), theBackingMatch.toString());
				if (!isCapturing || theGroupName == null)
					theNamedGroups = theBackingMatch.getNamedGroups();
				else {
					Map<String, GroupCapture> groups = new LinkedHashMap<>();
					groups.put(theGroupName, theCapture);
					groups.putAll(theBackingMatch.getNamedGroups());
					theNamedGroups = Collections.unmodifiableMap(groups);
				}
			}

			@Override
			public BetterPattern getPattern() {
				return GroupPattern.this;
			}

			@Override
			public int getStart() {
				return theBackingMatch.getStart();
			}

			@Override
			public int getEnd() {
				return theBackingMatch.getEnd();
			}

			@Override
			public Map<String, GroupCapture> getNamedGroups() {
				return theNamedGroups;
			}

			@Override
			public int getAllGroupCount() {
				return getPattern().getAllGroupCount();
			}

			@Override
			public GroupCapture getGroup(int index) {
				if (!isCapturing)
					return theBackingMatch.getGroup(index);
				else if (index == 0)
					return theCapture;
				else
					return theBackingMatch.getGroup(index - 1);
			}

			@Override
			public CharSequence getMatchText() {
				return theBackingMatch.getMatchText();
			}

			@Override
			public String toString() {
				return theBackingMatch.toString();
			}
		}
	}

	/** A pattern that matches against content previously encountered in the match and exposed as a capturing group */
	public static class BackReferencePattern implements BetterPattern {
		private final GroupReference theReference;
		private final boolean isCaseSensitive;

		BackReferencePattern(GroupReference reference, boolean caseSensitive) {
			theReference = reference;
			isCaseSensitive = caseSensitive;
		}

		/** @return The reference to the group whose content must be repeated in the match */
		public GroupReference getReference() {
			return theReference;
		}

		/** @return Whether to enforce case-sensitivity between the captured content and the target content */
		public boolean isCaseSensitive() {
			return isCaseSensitive;
		}

		@Override
		public Set<String> getGroups() {
			return Collections.emptySet();
		}

		@Override
		public int getAllGroupCount() {
			return 0;
		}

		@Override
		public Matcher matcher(CharSequence text) {
			return new BackReferenceMatcher(text);
		}

		@Override
		public String toString() {
			return theReference.toString();
		}

		class BackReferenceMatcher implements BackRefEnabledMatcher {
			private final CharSequence theText;

			BackReferenceMatcher(CharSequence text) {
				theText = text;
			}

			@Override
			public BetterPattern getPattern() {
				return BackReferencePattern.this;
			}

			@Override
			public Match matches() {
				return null;
			}

			@Override
			public Match lookingAt() {
				return null;
			}

			@Override
			public Match find() {
				return null;
			}

			@Override
			public Match find(int start) {
				return null;
			}

			@Override
			public Match lookingAt(GroupCaptureAccess captures) {
				GroupCapture group = captures.getGroup(theReference);
				if (group == null || theText.length() < group.value.length())
					return null;
				CharSequence matchText;
				if (group.value.length() == theText.length())
					matchText = theText;
				else
					matchText = new DefaultCharSubSequence(theText, 0, group.value.length());
				boolean matches;
				if (isCaseSensitive)
					matches = StringUtils.equals(group.value, matchText);
				else
					matches = StringUtils.equalsIgnoreCase(group.value, matchText);
				if (matches)
					return new BackReferenceMatch(matchText);
				else
					return null;
			}
		}

		class BackReferenceMatch implements Match {
			private final CharSequence theMatchText;

			BackReferenceMatch(CharSequence matchText) {
				theMatchText = matchText;
			}

			@Override
			public Map<String, GroupCapture> getNamedGroups() {
				return Collections.emptyMap();
			}

			@Override
			public int getAllGroupCount() {
				return getPattern().getAllGroupCount();
			}

			@Override
			public GroupCapture getGroup(int index) {
				throw new IndexOutOfBoundsException(index + " of 0");
			}

			@Override
			public BetterPattern getPattern() {
				return BackReferencePattern.this;
			}

			@Override
			public int getStart() {
				return 0;
			}

			@Override
			public int getEnd() {
				return theMatchText.length();
			}

			@Override
			public CharSequence getMatchText() {
				return theMatchText;
			}

			@Override
			public String toString() {
				return theMatchText.toString();
			}
		}
	}

	/** Default implementation of {@link BetterPattern#andThen(BetterPattern...)} */
	public static class StructuredPattern implements BetterPattern {
		private final List<BetterPattern> theComponents;
		private final Set<String> theNamedGroups;
		private final int theGroupCount;

		/** @param components The components to match */
		public StructuredPattern(BetterPattern... components) {
			this(BetterList.of(components));
		}

		/** @param components The components to match */
		public StructuredPattern(List<BetterPattern> components) {
			if (components.isEmpty())
				throw new IllegalArgumentException("Cannot create a structured pattern with no components");
			theComponents = components;

			Set<String> namedGroups = new LinkedHashSet<>();
			int groupCount = 0;
			for (int c = 0; c < components.size(); c++) {
				BetterPattern child = components.get(c);
				groupCount += child.getAllGroupCount();
				for (String g : child.getGroups())
					namedGroups.add(g);
			}
			theNamedGroups = Collections.unmodifiableSet(namedGroups);
			theGroupCount = groupCount;
		}

		/** @return The components to match */
		public List<BetterPattern> getComponents() {
			return theComponents;
		}

		@Override
		public Set<String> getGroups() {
			return theNamedGroups;
		}

		@Override
		public int getAllGroupCount() {
			return theGroupCount;
		}

		@Override
		public Matcher matcher(CharSequence text) {
			return new StructuredPatternMatcher(text);
		}

		@Override
		public BetterPattern andThen(BetterPattern... next) {
			List<BetterPattern> components = new ArrayList<>(theComponents.size() + next.length);
			components.addAll(theComponents);
			for (BetterPattern p : next)
				components.add(p);
			return new StructuredPattern(Collections.unmodifiableList(components));
		}

		class StructuredPatternMatcher implements Matcher {
			private final CharSequence theText;

			StructuredPatternMatcher(CharSequence text) {
				theText = text;
			}

			@Override
			public BetterPattern getPattern() {
				return StructuredPattern.this;
			}

			@Override
			public Match matches() {
				Match match = lookingAt();
				if (match == null || match.getEnd() < theText.length())
					return null;
				return match;
			}

			@Override
			public Match lookingAt() {
				return new MatchBuilder().buildMatch(theText, theComponents.get(0).matcher(theText).lookingAt());
			}

			@Override
			public Match find() {
				return find(0);
			}

			@Override
			public Match find(int start) {
				MatchBuilder builder = new MatchBuilder();
				Matcher firstMatcher = theComponents.get(0).matcher(theText);
				for (Match firstMatch = firstMatcher.find(); firstMatch != null; firstMatch = firstMatcher.find()) {
					Match match = builder.buildMatch(theText, firstMatch);
					if (match != null)
						return match;
				}
				return null;
			}
		}

		class MatchBuilder implements GroupCaptureAccess {
			private final Match[] theMatchComponents;
			private final List<GroupCapture> theCaptures;
			private final Map<String, GroupCapture> theNamedCaptures;
			private final Map<Integer, Map<Integer, GroupCapture>> theCapturesByPosition;

			MatchBuilder() {
				theMatchComponents = new Match[theComponents.size()];
				theCaptures = new ArrayList<>(getAllGroupCount());
				theNamedCaptures = new LinkedHashMap<>();
				theCapturesByPosition = new TreeMap<>();
			}

			@Override
			public Map<String, GroupCapture> getNamedGroups() {
				return Collections.unmodifiableMap(theNamedCaptures);
			}

			@Override
			public int getAllGroupCount() {
				return theCaptures.size();
			}

			@Override
			public GroupCapture getGroup(int index) {
				return theCaptures.get(index);
			}

			Match buildMatch(CharSequence text, Match firstMatch) {
				if (firstMatch == null)
					return null;
				theMatchComponents[0] = firstMatch;
				for (int i = 0; i < theComponents.get(i).getAllGroupCount(); i++)
					theCaptures.add(firstMatch.getGroup(i));
				theNamedCaptures.putAll(firstMatch.getNamedGroups());

				int start = firstMatch.getEnd();
				int c;
				for (c = 1; c < theComponents.size(); c++) {
					Matcher matcher = theComponents.get(c).matcher(new DefaultCharSubSequence(text, start, text.length()));
					if (matcher instanceof BackRefEnabledMatcher)
						theMatchComponents[c] = ((BackRefEnabledMatcher) matcher).lookingAt(this);
					else
						theMatchComponents[c] = matcher.lookingAt();
					if (theMatchComponents[c] == null)
						break;
					for (int i = 0; i < theComponents.get(c).getAllGroupCount(); i++) {
						GroupCapture capture = theMatchComponents[c].getGroup(i);
						GroupCapture offsetCapture;
						if (start == 0)
							offsetCapture = capture;
						else
							offsetCapture = new GroupCapture(start + capture.start, capture.value);
						theCapturesByPosition.computeIfAbsent(offsetCapture.start, __ -> new TreeMap<>())//
							.put(offsetCapture.start + offsetCapture.value.length(), offsetCapture);
						theCaptures.add(offsetCapture);
					}
					for (Map.Entry<String, GroupCapture> capture : theMatchComponents[c].getNamedGroups().entrySet()) {
						if (theNamedCaptures.containsKey(capture.getKey()))
							continue;
						int offset = start + capture.getValue().start;
						GroupCapture offsetCapture = theCapturesByPosition.computeIfAbsent(offset, __ -> new TreeMap<>())//
							.computeIfAbsent(offset + capture.getValue().value.length(),
								__ -> new GroupCapture(offset, capture.getValue().value));
						theNamedCaptures.putIfAbsent(capture.getKey(), offsetCapture);
					}
					start += theMatchComponents[c].getEnd();
				}
				if (c == theMatchComponents.length) {
					CharSequence matchText;
					if (start == text.length())
						matchText = text;
					else
						matchText = new DefaultCharSubSequence(text, 0, start);
					return new StructuredPatternMatch(matchText, theMatchComponents, Collections.unmodifiableList(theCaptures),
						Collections.unmodifiableMap(theNamedCaptures));
				} else {
					Arrays.setAll(theMatchComponents, null);
					theCaptures.clear();
					theNamedCaptures.clear();
					theCapturesByPosition.clear();
					return null;
				}
			}
		}

		class StructuredPatternMatch implements Match {
			private final CharSequence theText;
			private final Match[] theMatchComponents;
			private final int[] theOffsets;
			private final List<GroupCapture> theCaptures;
			private final Map<String, GroupCapture> theNamedCaptures;

			StructuredPatternMatch(CharSequence text, Match[] components, List<GroupCapture> captures,
				Map<String, GroupCapture> namedCaptures) {
				theText = text;
				theMatchComponents = components;
				theOffsets = new int[components.length];
				theCaptures = captures;
				theNamedCaptures = namedCaptures;
			}

			@Override
			public BetterPattern getPattern() {
				return StructuredPattern.this;
			}

			@Override
			public int getStart() {
				return theMatchComponents[0].getStart();
			}

			@Override
			public int getEnd() {
				int lastIdx = theMatchComponents.length - 1;
				return theOffsets[lastIdx] + theMatchComponents[lastIdx].getEnd();
			}

			@Override
			public Map<String, GroupCapture> getNamedGroups() {
				return theNamedCaptures;
			}

			@Override
			public int getAllGroupCount() {
				return getPattern().getAllGroupCount();
			}

			@Override
			public GroupCapture getGroup(int index) {
				return theCaptures.get(index);
			}

			@Override
			public CharSequence getMatchText() {
				return theText;
			}

			@Override
			public String toString() {
				return theText.toString();
			}
		}
	}

	/** An interface for a pattern that can replace a pattern match with text */
	public interface BetterPatternReplacement {
		/**
		 * @param str The string builder to append the replacement text to
		 * @param match The match to generate the replacement text from
		 */
		void appendReplacement(StringBuilder str, Match match);

		/** Default implementation of a {@link BetterPatternReplacement} */
		public class Default implements BetterPatternReplacement {
			private final List<CharSequence> theBetweenTexts;
			private final List<GroupReference> theGroupReferences;

			/**
			 * @param betweenTexts The texts before the first group reference, between each group reference, and after the last group
			 *        reference
			 * @param groupReferences The group references
			 */
			public Default(List<CharSequence> betweenTexts, List<GroupReference> groupReferences) {
				if (betweenTexts.size() != groupReferences.size() + 1)
					throw new IllegalArgumentException(
						"A replacement must have initial and terminal texts, as well as one betweeen each pair of group references: "
							+ betweenTexts.size() + ", " + groupReferences.size());
				theBetweenTexts = betweenTexts;
				theGroupReferences = groupReferences;
			}

			/** @return The texts before the first group reference, between each group reference, and after the last group reference */
			public List<CharSequence> getBetweenTexts() {
				return theBetweenTexts;
			}

			/** @return The group references */
			public List<GroupReference> getGroupReferences() {
				return theGroupReferences;
			}

			@Override
			public void appendReplacement(StringBuilder str, Match match) {
				str.append(theBetweenTexts.get(0));
				for (int g = 0; g < theGroupReferences.size(); g++) {
					GroupReference ref = theGroupReferences.get(g);
					GroupCapture capture = match.getGroup(ref);
					if (capture != null)
						str.append(capture.value);
					str.append(theBetweenTexts.get(g + 1));
				}
			}
		}

		/** Pattern for group references by index in {@link #parsePatternReplacement(CharSequence, BetterPattern)} */
		static final Pattern REPLACEMENT_GROUP_REF_BY_INDEX = Pattern.compile("\\\\(?<index>\\d{1,8})");
		/** Pattern for group references by name in {@link #parsePatternReplacement(CharSequence, BetterPattern)} */
		static final Pattern REPLACEMENT_GROUP_REF_BY_NAME = Pattern.compile("\\\\\\{(?<name>[^\\}]*)\\}");

		/**
		 * @param text The text to parse
		 * @param pattern The pattern containing the groups that may be referenced by the pattern replacement
		 * @return The parsed pattern replacement
		 */
		public static BetterPatternReplacement.Default parsePatternReplacement(CharSequence text, BetterPattern pattern) {
			java.util.regex.Matcher byIndex = REPLACEMENT_GROUP_REF_BY_INDEX.matcher(text);
			java.util.regex.Matcher byName = REPLACEMENT_GROUP_REF_BY_NAME.matcher(text);
			int hasByIndex = byIndex.find() ? byIndex.start() : -1;
			int hasByName = byName.find() ? byName.start() : -1;
			if (hasByIndex < 0 && hasByName < 0)
				return new Default(Collections.singletonList(text), Collections.emptyList());
			int index = 0;
			List<CharSequence> betweenTexts = new ArrayList<>();
			List<GroupReference> groupRefs = new ArrayList<>();
			while (hasByIndex >= 0 || hasByName >= 0) {
				if (hasByIndex >= 0 && (hasByName < 0 || hasByIndex < hasByName)) {
					betweenTexts.add(escape(text.subSequence(index, hasByIndex)));
					GroupReference ref = new GroupReference(Integer.parseInt(byIndex.group("index")) - 1);
					if (ref.getIndex() < -1 || ref.getIndex() >= pattern.getAllGroupCount())
						throw new IllegalArgumentException("Reference to group index " + (ref.getIndex() + 1) + " when only "
							+ pattern.getAllGroupCount() + " are present");
					groupRefs.add(ref);
					index = byIndex.end();
					hasByIndex = byIndex.find() ? byIndex.start() : -1;
				} else {
					betweenTexts.add(escape(text.subSequence(index, hasByName)));
					GroupReference ref = new GroupReference(byName.group("name"));
					if (!pattern.getGroups().contains(ref.getName()))
						throw new IllegalArgumentException("Reference to non-existent group '" + ref.getName() + "'");
					groupRefs.add(ref);
					index = byName.end();
					hasByName = byName.find() ? byName.start() : -1;
				}
			}
			betweenTexts.add(escape(text.subSequence(index, text.length())));
			return new Default(Collections.unmodifiableList(betweenTexts), Collections.unmodifiableList(groupRefs));
		}

		/**
		 * @param str The character sequence to escape
		 * @return A character sequence with escape sequences such as "\\\\" or "\\n" replaced with the characters they represent
		 */
		static CharSequence escape(CharSequence str) {
			StringBuilder builder = null;
			boolean escaped = false;
			for (int c = 0; c < str.length(); c++) {
				char ch = str.charAt(c);
				if (escaped) {
					char replace;
					switch (ch) {
					case 'n':
						replace = '\n';
						break;
					case 't':
						replace = '\t';
						break;
					case 'r':
						replace = '\r';
						break;
					default:
						replace = ch;
						break;
					}
					if (replace != ch) {
						if (builder == null)
							builder = new StringBuilder().append(str, 0, c - 1);
						builder.append(replace);
					}
					escaped = false;
				} else if (ch == '\\')
					escaped = true;
				else if (builder != null)
					builder.append(ch);
			}
			if (escaped && builder != null)
				builder.append('\\');
			return builder == null ? str : builder.toString();
		}
	}

	/**
	 * Utility map with a non-empty key set but whose values are all null
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	static class NoValuesMap<K, V> extends AbstractMap<K, V> {
		private final Set<K> theKeySet;

		public NoValuesMap(Collection<K> keys) {
			theKeySet = keys instanceof Set ? (Set<K>) keys : QommonsUtils.unmodifiableDistinctCopy(keys);
		}

		@Override
		public int size() {
			return theKeySet.size();
		}

		@Override
		public boolean isEmpty() {
			return theKeySet.isEmpty();
		}

		@Override
		public boolean containsValue(Object value) {
			return value == null;
		}

		@Override
		public boolean containsKey(Object key) {
			return theKeySet.contains(key);
		}

		@Override
		public Set<K> keySet() {
			return theKeySet;
		}

		@Override
		public Set<Entry<K, V>> entrySet() {
			return new MappedSet<>(theKeySet, NoValueMapEntry::new,
				e -> e instanceof Map.Entry && theKeySet.contains(((Map.Entry<?, ?>) e).getKey()));
		}

		static class NoValueMapEntry<K, V> implements Map.Entry<K, V> {
			private final K theKey;

			NoValueMapEntry(K key) {
				theKey = key;
			}

			@Override
			public K getKey() {
				return theKey;
			}

			@Override
			public V getValue() {
				return null;
			}

			@Override
			public V setValue(V value) {
				throw new UnsupportedOperationException();
			}

			@Override
			public int hashCode() {
				return Objects.hashCode(theKey);
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof Map.Entry && Objects.equals(theKey, ((Map.Entry<?, ?>) obj).getKey())
					&& ((Map.Entry<?, ?>) obj).getValue() == null;
			}

			@Override
			public String toString() {
				return theKey + "=null";
			}
		}
	}

	/* Started a parser for my regex classes, but it's too complicated.  I need ANTLR or something.
	
	public interface PatternParser extends Format<BetterPattern> {
		int getFlags();
	
		PatternParser setFlags(int flags);
	
		@Override
		default BetterPattern parse(CharSequence text) throws ParseException {
			return parse(text, getFlags());
		}
	
		BetterPattern parse(CharSequence text, int flags) throws ParseException;
	}
	
	public interface PatternParsing {
		int getFlags();
	
		boolean hasNamedGroup(String groupName);
	
		int getAllGroupCount();
	
		BetterPattern getPreviousComponent();
	
		void throwParseEx(String message, int offset) throws ParseException;
	
		char charAt(int c);
	
		CharSequence getText(int from, int to);
	
		CharSequence getTextUntil(CharSequence lookingFor) throws ParseException;
	
		BetterPattern parsePattern(int start, int end, int flags) throws ParseException;
	}
	
	public interface PatternComponentParser {
		BetterPattern parseComponent(PatternParsing parsing) throws ParseException;
	}
	
	public static final PatternParser DEFAULT_PARSER = new DefaultPatternParser()//
		.withEscape('\\')//
		.withSpecialEscape('n', "\n")//
		.withSpecialEscape('t', "\t")//
		.withSpecialEscape('r', "\r")//
		.withSpecialEscape('b', "\b")//
		.withSpecialEscape('f', "\f")//
		.withSpecialEscape('[', "[")//
		.withSpecialEscape('(', "(")//
		.withSpecialEscape('.', ".")//
		.withSpecialEscape('*', "*")//
		.withComponentMatcher('(', GroupPattern::parseComponentDefault)//
		.withComponentMatcher('\\', CharClassPattern::parseComponentDefault)//
	;
	
	public static class DefaultPatternParser implements PatternParser {
		public static final IntPredicate UNCONDITIONAL = __ -> true;
		public static final FlagAbsentCondition CASE_SENSITIVE = new FlagAbsentCondition(Pattern.CASE_INSENSITIVE);
		public static final FlagPresentCondition CASE_INSENSITIVE = new FlagPresentCondition(Pattern.CASE_INSENSITIVE);
	
		private final Set<Character> theEscapes;
		private final Map<Character, String> theSpecialEscapes;
		private final Map<Character, List<PatternComponentParser>> theComponents;
		private final PatternParser theUnmodifiable;
	
		private int theFlags;
	
		public DefaultPatternParser() {
			theEscapes = new LinkedHashSet<>();
			theSpecialEscapes = new LinkedHashMap<>();
			theComponents = new LinkedHashMap<>();
			theUnmodifiable = new PatternParser() {
				private int theFlags;
	
				@Override
				public int getFlags() {
					return theFlags;
				}
	
				@Override
				public PatternParser setFlags(int flags) {
					theFlags = flags;
					return this;
				}
	
				@Override
				public void append(StringBuilder text, BetterPattern value) {
					DefaultPatternParser.this.append(text, value);
				}
	
				@Override
				public BetterPattern parse(CharSequence text, int flags) throws ParseException {
					return DefaultPatternParser.this.parse(text, flags);
				}
			};
		}
	
		public DefaultPatternParser withEscape(char escape) {
			theEscapes.add(escape);
			return this;
		}
	
		public DefaultPatternParser withSpecialEscape(char escape, String sequence) {
			theSpecialEscapes.put(escape, sequence);
			return this;
		}
	
		public DefaultPatternParser withComponentMatcher(char initChar, PatternComponentParser matcher) {
			theComponents.computeIfAbsent(initChar, __ -> new ArrayList<>()).add(matcher);
			return this;
		}
	
		public PatternParser unmodifiable() {
			return theUnmodifiable;
		}
	
		@Override
		public int getFlags() {
			return theFlags;
		}
	
		@Override
		public DefaultPatternParser setFlags(int flags) {
			theFlags = flags;
			return this;
		}
	
		@Override
		public void append(StringBuilder text, BetterPattern value) {
			if (value != null)
				text.append(value);
		}
	
		@Override
		public BetterPattern parse(CharSequence text, int flags) throws ParseException {
			return new PatternInstanceParser()//
				.parsePattern(text, 0, flags);
		}
	
		class PatternInstanceParser {
			private final List<PatternComponentParser> theSpecialMatchers0;
	
			private final List<BetterPattern> theParsedComponents;
			private final StringBuilder theLiteral;
			private BetterPattern thePreviousComponent;
	
			PatternInstanceParser() {
				theSpecialMatchers0 = theComponents.get((char) 0);
				theParsedComponents = new ArrayList<>();
				theLiteral = new StringBuilder();
			}
	
			BetterPattern parsePattern(CharSequence text, int offset, int flags) throws ParseException {
				boolean caseInsensitive = CASE_INSENSITIVE.test(flags);
				char escape = 0;
				for (int c = 0; c < text.length(); c++) {
					char ch = text.charAt(c);
					if (escape>0) {
						String seq = theSpecialEscapes.get(ch);
						if (seq != null)
							theLiteral.append(seq);
						else {
							theLiteral.append(escape);
							theLiteral.append(ch);
						}
						escape = 0;
						continue;
					}
					List<PatternComponentParser> specialMatchers = theComponents.get(ch);
					BetterPattern specialMatch = matchComponent(specialMatchers, text, offset, c, flags);
					if (specialMatch != null) {
						if (theLiteral.length() > 0) {
							theParsedComponents.add(new SimpleStringSearch(theLiteral.toString(), caseInsensitive, false));
							theLiteral.setLength(0);
						}
						theParsedComponents.add(specialMatch);
						continue;
					} else if (theEscapes.contains(ch))
						escape = ch;
					else
						theLiteral.append(ch);
				}
				if (escape>0)
					theLiteral.append(escape);
				if (theLiteral.length() > 0) {
					theParsedComponents.add(new SimpleStringSearch(theLiteral.toString(), caseInsensitive, false));
				}
				return new StructuredPattern(Collections.unmodifiableList(theParsedComponents));
			}
	
			private BetterPattern matchComponent(List<PatternComponentParser> specialMatchers, CharSequence text, int offset,
				int c, int flags) throws ParseException {
				if (specialMatchers == null && theSpecialMatchers0 == null)
					return null;
				PatternParseRecursiveView view = new PatternParseRecursiveView(text, offset + c, flags);
				for (List<PatternComponentParser> matcherSet : Arrays.asList(specialMatchers, theSpecialMatchers0)) {
					if (matcherSet == null)
						continue;
					for (PatternComponentParser specialMatcher : matcherSet) {
						BetterPattern pattern = specialMatcher.parseComponent(view);
						if (pattern != null)
							return pattern;
					}
				}
				return null;
			}
	
			class PatternParseRecursiveView implements PatternParsing {
				private final CharSequence theText;
				private final String theEscapedSequence;
				private final NavigableMap<Integer, String> theEscapeSequences;
				private final int theOffset;
				private final int theViewFlags;
	
				PatternParseRecursiveView(CharSequence text, int offset, int flags) {
					theText = text;
					theOffset = offset;
					theViewFlags = flags;
	
					/* This is a bit squirrelly.
					 * Basically, we don't want the components to have to worry about escaping, so the pattern is called on a sequence
					 * that has been escaped.
					 * But when a component needs sub-components, we need to use the raw text so we're not double-escaping things.
					 * So we keep track of all the information we need to map positions in the escaped sequence
					 * to positions in the raw sequence.
					 * When a component calls parsePattern(int, int, int), we reverse-map the positions and call the parser recursively.
					 /
					NavigableMap<Integer, String> escapeSequences;
					if (text.length() == 0) {
						escapeSequences = Collections.emptyNavigableMap();
						theEscapedSequence = "";
					} else {
						StringBuilder escapedSeq = new StringBuilder();
						char escape = 0;
						escapeSequences = null;
						for (int c = 0; c < text.length(); c++) {
							char ch = text.charAt(c);
							if (escape>0) {
								String seq = theSpecialEscapes.get(ch);
								if (seq == null)
									seq = new String(new char[] {escape, ch});
								if (escapeSequences == null)
									escapeSequences = new TreeMap<>();
								escapeSequences.put(escapedSeq.length(), seq);
								escapedSeq.append(seq);
								escape = 0;
							} else if (theEscapes.contains(ch))
								escape = ch;
							else {
								escapedSeq.append(ch);
							}
						}
						if (escape>0)
							escapedSeq.append(escape);
						theEscapedSequence = escapedSeq.toString();
					}
					if (escapeSequences != null)
						theEscapeSequences = escapeSequences;
					else
						theEscapeSequences = Collections.emptyNavigableMap();
				}
	
				String getEscapedSequence() {
					return theEscapedSequence;
				}
	
				@Override
				public int getFlags() {
					return theViewFlags;
				}
	
				@Override
				public boolean hasNamedGroup(String groupName) {
					for (BetterPattern component : theParsedComponents)
						if (component.getGroups().contains(groupName))
							return true;
					return false;
				}
	
				@Override
				public int getAllGroupCount() {
					int sum = 0;
					for (BetterPattern component : theParsedComponents)
						sum += component.getAllGroupCount();
					return sum;
				}
	
				@Override
				public BetterPattern getPreviousComponent() {
					if (thePreviousComponent == null) {
						if (theLiteral.length() > 0) {
							thePreviousComponent = new SimpleStringSearch(String.valueOf(theLiteral.charAt(theLiteral.length() - 1)),
								CASE_INSENSITIVE.test(theViewFlags), false);
							theLiteral.setLength(theLiteral.length() - 1);
						} else if (!theParsedComponents.isEmpty()) {
							thePreviousComponent = theParsedComponents.remove(theParsedComponents.size() - 1);
						} else
							return null;
					}
					return thePreviousComponent;
				}
	
				@Override
				public void throwParseEx(String message, int offset) throws ParseException {
					throw new ParseException(message, theOffset + offset);
				}
	
				@Override
				public char charAt(int c) {
					if (c < theEscapedSequence.length())
						return theEscapedSequence.charAt(c);
					return 0;
				}
	
				@Override
				public CharSequence getText(int from, int to) {
					if (to < theEscapedSequence.length())
						return theEscapedSequence.substring(from, to);
					return null;
				}
	
				@Override
				public CharSequence getTextUntil(CharSequence lookingFor) throws ParseException {
					int found = StringUtils.indexOf(theEscapedSequence, lookingFor);
					if (found < 0)
						throw new ParseException("'" + lookingFor + "' expected", theOffset + theText.length());
					return theEscapedSequence.substring(found, found + lookingFor.length());
				}
	
				@Override
				public BetterPattern parsePattern(int start, int end, int flags) throws ParseException {
					if (start < 0 || end > theEscapedSequence.length() || start > end)
						throw new IndexOutOfBoundsException(start + " to " + end + " of " + theEscapedSequence.length());
					int rawStart = start, rawEnd = end;
					for (Map.Entry<Integer, String> seq : theEscapeSequences.entrySet()) {
						if (seq.getKey() < start) {
							rawStart++;
							rawEnd++;
						} else if (seq.getKey() < end)
							rawEnd++;
						else
							break;
					}
					return PatternInstanceParser.this.parsePattern(new DefaultCharSubSequence(theText, rawStart, rawEnd),
						theOffset + rawStart, flags);
				}
			}
		}
	}
	
	public static class FlagPresentCondition implements IntPredicate {
		private final int theFlag;
	
		public FlagPresentCondition(int flag) {
			theFlag = flag;
		}
	
		@Override
		public boolean test(int value) {
			return (value & theFlag) == theFlag;
		}
	
		@Override
		public int hashCode() {
			return theFlag;
		}
	
		@Override
		public boolean equals(Object obj) {
			return obj instanceof FlagPresentCondition && theFlag == ((FlagPresentCondition) obj).theFlag;
		}
	
		@Override
		public String toString() {
			return Integer.toHexString(theFlag);
		}
	}
	
	public static class FlagAbsentCondition implements IntPredicate {
		private final int theFlag;
	
		public FlagAbsentCondition(int flag) {
			theFlag = flag;
		}
	
		@Override
		public boolean test(int value) {
			return (value & theFlag) == 0;
		}
	
		@Override
		public int hashCode() {
			return theFlag;
		}
	
		@Override
		public boolean equals(Object obj) {
			return obj instanceof FlagAbsentCondition && theFlag == ((FlagAbsentCondition) obj).theFlag;
		}
	
		@Override
		public String toString() {
			return "~" + Integer.toHexString(theFlag);
		}
	}*/
}

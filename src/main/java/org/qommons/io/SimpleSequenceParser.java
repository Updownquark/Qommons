package org.qommons.io;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.qommons.Named;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.config.QommonsConfig;

/**
 * Parses simple sequences from character strings, where each element in the sequence is associated with an enumerated type
 * 
 * @param <E> The type of this parser
 * @param <V> The value type of this parser
 */
public class SimpleSequenceParser<E extends Enum<E>, V extends Comparable<V>> {
	/** A simple parser that knows how to recognize an element in a sequence */
	public interface ComponentParser {
		/**
		 * @param str The character sequence to look at
		 * @param start The start index to search at
		 * @return The end of the sub-sequence matching this parser's format at the beginning of the string, or -1 if it is not found at the
		 *         beginning of the string
		 */
		int find(CharSequence str, int start);
	}

	/** A regex-based parser */
	public static class PatternParser implements ComponentParser {
		private final Pattern thePattern;

		/** @param pattern The pattern to search for */
		public PatternParser(Pattern pattern) {
			thePattern = pattern;
		}

		@Override
		public int find(CharSequence str, int start) {
			Matcher m = thePattern.matcher(StringUtils.cheapSubSequence(str, start, str.length()));
			if (m.lookingAt())
				return m.end();
			else
				return -1;
		}

		@Override
		public int hashCode() {
			return thePattern.pattern().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof PatternParser && thePattern.pattern().equals(((PatternParser) obj).thePattern.pattern());
		}

		@Override
		public String toString() {
			return thePattern.pattern();
		}
	}

	/**
	 * Adjusts values of a particular type up or down
	 * 
	 * @param <V> The type to adjust
	 */
	public interface Adjuster<V extends Comparable<V>> {
		/**
		 * @param value The value to adjust
		 * @param adjustment The adjustment amount (typically 1 or -1)
		 * @return The adjusted value
		 */
		V adjust(V value, int adjustment);
	}

	/**
	 * Represents an element in one type of sequence/format
	 * 
	 * @param <E> The type of the parser
	 * @param <V> The value type of the parser
	 */
	public static class ParserElement<E extends Enum<E>, V extends Comparable<V>> {
		private final E theType;
		private final ComponentParser theParser;
		private final boolean isRequired;
		private final Format<V> theFormat;

		/**
		 * @param type The type of this component
		 * @param parser The parser to match the sequence element
		 * @param required Whether this element MUST be found in the sequence to match the format
		 * @param format The format to parse the value
		 */
		public ParserElement(E type, ComponentParser parser, boolean required, Format<V> format) {
			theType = type;
			theParser = parser;
			isRequired = required;
			theFormat = format;
		}

		/** @return The type of this component */
		public E getType() {
			return theType;
		}

		/** @return The parser to match this sequence element */
		public ComponentParser getParser() {
			return theParser;
		}

		/** @return Whether this element MUST be found in the sequence to match the format */
		public boolean isRequired() {
			return isRequired;
		}

		/** @return The format to parse the value */
		public Format<V> getFormat() {
			return theFormat;
		}

		@Override
		public String toString() {
			if (theType != null)
				return theType.toString();
			else
				return theParser.toString();
		}
	}

	/**
	 * Represents one sequence form that can be parsed by a parser
	 * 
	 * @param <E> The type of the parser
	 * @param <V> The value type of the parser
	 */
	public static class ParserFormat<E extends Enum<E>, V extends Comparable<V>> implements Named {
		private final String theName;
		private final List<ParserElement<E, V>> theComponents;

		ParserFormat(String name, List<ParserElement<E, V>> components) {
			theName = name;
			theComponents = QommonsUtils.unmodifiableCopy(components);
		}

		@Override
		public String getName() {
			return theName;
		}

		/** @return This format's element sequence */
		public List<ParserElement<E, V>> getComponents() {
			return theComponents;
		}

		@Override
		public String toString() {
			return theName;
		}
	}

	/**
	 * An element in a successfully-parsed sequence
	 * 
	 * @param <E> The type of the parser
	 * @param <V> The value type of the parser
	 */
	public static class ParsedElement<E extends Enum<E>, V extends Comparable<V>> extends FieldedComponent<E, V> {
		private final Format<V> theParser;
		private final String theText;

		/**
		 * @param parser The format that this component was parsed as a part of
		 * @param start The index in the sequence that is the start of this element
		 * @param type The type of this element
		 * @param value The value for this element
		 * @param text The text representation of this element
		 */
		public ParsedElement(Format<V> parser, int start, E type, V value, String text) {
			super(start, start + text.length(), type, value);
			theParser = parser;
			theText = text;
		}

		/** @return The format that this element was parsed as a part of */
		public Format<V> getParser() {
			return theParser;
		}

		/** @return The text representation of this element */
		public String getText() {
			return theText;
		}

		/**
		 * @param offset The offset to apply
		 * @return This element, but with the offset applied to its {@link #getStart() start} and {@link #getEnd() end} indexes
		 */
		public ParsedElement<E, V> offset(int offset) {
			if (offset == 0)
				return this;
			return new ParsedElement<>(theParser, getStart() + offset, getField(), getValue(), getText());
		}

		@Override
		public String toString() {
			return getField() + "=" + theText;
		}
	}

	/**
	 * A successfully-parsed sequence
	 * 
	 * @param <E> The type of the parser
	 * @param <V> The value type of the parser
	 */
	public static class ParsedSequence<E extends Enum<E>, V extends Comparable<V>>
		implements FieldedAdjustable<E, V, ParsedElement<E, V>, ParsedSequence<E, V>> {
		private final SimpleSequenceParser<E, V> theParser;
		private final ParserFormat<E, V> theFormat;
		private final List<ParsedElement<E, V>> theSequence;
		private final List<String> theSeparators;
		private final Map<E, ParsedElement<E, V>> theComponentsByType;

		ParsedSequence(SimpleSequenceParser<E, V> parser, ParserFormat<E, V> format, List<ParsedElement<E, V>> sequence,
			List<String> separators, Map<E, ParsedElement<E, V>> componentsByType) {
			theParser = parser;
			theFormat = format;
			theSequence = sequence;
			theSeparators = separators;
			theComponentsByType = componentsByType;
		}

		/** @return The format that parsed this sequence */
		public ParserFormat<E, V> getFormat() {
			return theFormat;
		}

		@Override
		public List<ParsedElement<E, V>> getComponents() {
			return theSequence;
		}

		@Override
		public Class<E> getFieldType() {
			return theParser.getType();
		}

		/** @return This sequence's components, by type */
		public Map<E, ParsedElement<E, V>> getComponentsByType() {
			return theComponentsByType;
		}

		@Override
		public ParsedElement<E, V> getField(E type) {
			return theComponentsByType.get(type);
		}

		/** @return The length of the text this sequence was parsed from */
		public int getLength() {
			return theSequence.get(theSeparators.size() - 1).getEnd() + theSeparators.get(theSeparators.size() - 1).length();
		}

		@Override
		public ParsedSequence<E, V> with(E type, V value) {
			ParsedElement<E, V> old = theComponentsByType.get(type);
			Format<V> format;
			int index, start;
			if (old != null) {
				index = theSequence.indexOf(old);
				start = old.getStart();
				format = old.getParser();
			} else {
				index = theSequence.size();
				start = theSequence.get(theSequence.size() - 1).getEnd() + theSeparators.get(theSeparators.size() - 1).length();
				format = theParser.getFormat(type);
			}
			String text = format.format(value);
			ParsedElement<E, V> newComponent = new ParsedElement<>(format, start, type, value, text);
			Map<E, ParsedElement<E, V>> cbt = theParser.createTypeMap(theComponentsByType.size() + 1);
			cbt.put(type, newComponent);
			List<ParsedElement<E, V>> seq = new ArrayList<>(theSequence.size() + 1);
			List<String> seps = new ArrayList<>(theSequence.size() + 1);
			if (index < seq.size()) {
				seq.addAll(theSequence.subList(0, index));
				seq.add(newComponent);
				int posDiff = text.length() - old.getText().length();
				if (posDiff == 0)
					seq.addAll(theSequence.subList(index + 1, theSequence.size()));
				else {
					for (int i = index + 1; i < theSequence.size(); i++)
						seq.add(theSequence.get(i).offset(posDiff));
				}
			} else {
				seq.addAll(theSequence);
				seps.addAll(theSeparators.subList(0, theSeparators.size() - 1));
				if (theSeparators.size() <= 1)
					seps.add(" ");
				else
					seps.add(theSeparators.get(theSeparators.size() - 2));
				seps.add(theSeparators.get(theSeparators.size() - 1));
			}
			return new ParsedSequence<>(theParser, theFormat, Collections.unmodifiableList(seq), Collections.unmodifiableList(seps),
				Collections.unmodifiableMap(cbt));
		}

		@Override
		public ParsedSequence<E, V> adjust(int position, int amount) {
			ParsedElement<E, V> component = getComponent(position);
			if (component == null || component.getField() == null)
				return this;
			V newValue = theParser.getAdjuster(component.getField()).adjust(component.getValue(), amount);
			return with(component.getField(), newValue);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			for (int i = 0; i < theSequence.size(); i++) {
				str.append(theSequence.get(i));
				str.append(theSeparators.get(i));
			}
			return str.toString();
		}
	}

	private final Class<E> theType;
	private final List<ParserFormat<E, V>> theParserFormats;
	private final List<ComponentParser> theWhiteSpace;
	private final int theValueCount;
	private final Format<V> theDefaultFormat;
	private final Map<E, Format<V>> theSpecificFormats;
	private final Adjuster<V> theDefaultAdjuster;
	private final Map<E, Adjuster<V>> theSpecificAdjusters;

	SimpleSequenceParser(Class<E> type, List<ParserFormat<E, V>> formats, List<ComponentParser> whiteSpace, Format<V> defaultFormat,
		Map<E, Format<V>> specificFormats, Adjuster<V> defaultAdjuster, Map<E, Adjuster<V>> specificAdjusters) {
		theType = type;
		theParserFormats = formats;
		theWhiteSpace = whiteSpace;
		theValueCount = type.getEnumConstants().length;
		theDefaultFormat = defaultFormat;
		theSpecificFormats = specificFormats;
		theDefaultAdjuster = defaultAdjuster;
		theSpecificAdjusters = specificAdjusters;
	}

	/** @return The component type of this parser */
	public Class<E> getType() {
		return theType;
	}

	/** @return All formats that can match sequences for this parser */
	public List<ParserFormat<E, V>> getParserFormats() {
		return theParserFormats;
	}

	/**
	 * @param type The component type to get the format for
	 * @return The format to parse values for the given component type
	 */
	public Format<V> getFormat(E type) {
		Format<V> format = theSpecificFormats.get(type);
		if (format == null)
			format = theDefaultFormat;
		return format;
	}

	/**
	 * @param type The component type to get the adjuster for
	 * @return The adjuster to adjust values for the given component type
	 */
	public Adjuster<V> getAdjuster(E type) {
		Adjuster<V> format = theSpecificAdjusters.get(type);
		if (format == null)
			format = theDefaultAdjuster;
		return format;
	}

	static class MatchSet {
		private int[] theMatches;
		private int theSize; // This is double the number of matches in the set, as each match takes two spots

		public MatchSet() {
			this(10);
		}

		public MatchSet(int capacity) {
			theMatches = new int[capacity << 1];
		}

		public MatchSet add(int start, int end) {
			if (theMatches.length == theSize) {
				int[] newMatches = new int[theSize * 2];
				System.arraycopy(theMatches, 0, newMatches, 0, theSize);
				theMatches = newMatches;
			}
			theMatches[theSize] = start;
			theMatches[theSize + 1] = end;
			theSize += 2;
			return this;
		}

		public MatchSet clear() {
			theSize = 0;
			return this;
		}

		public int size() {
			return theSize >>> 1;
		}

		public int getStart(int i) {
			int idx = i << 1;
			if (idx >= theSize)
				throw new IndexOutOfBoundsException(i + " of " + (theSize / 2));
			return theMatches[idx];
		}

		public int getEnd(int i) {
			int idx = i << 1;
			if (idx >= theSize)
				throw new IndexOutOfBoundsException(i + " of " + (theSize / 2));
			return theMatches[idx + 1];
		}
	}

	/**
	 * @param seq The text to parse
	 * @param wholeText Whether the entire text must be parsed as part of a single sequence
	 * @param throwIfNotFound Whether to throw an {@link ParseException} if the sequence matches no configured formats (otherwise null will
	 *        be returned)
	 * @return The parsed sequence
	 * @throws ParseException If the sequence matches no configured formats (and <code>throwIfNotFound</code>) or a value {@link Format}
	 *         throws an exception parsing a value
	 */
	@SuppressWarnings({ "unused", "null" }) // I do not understand why I need these, but eclipse complains
	public ParsedSequence<E, V> parse(CharSequence seq, boolean wholeText, boolean throwIfNotFound) throws ParseException {
		TreeMap<Integer, Map<ComponentParser, Integer>> componentsByPosition = new TreeMap<>();
		MatchSet components = new MatchSet();
		ParseException valueEx = null;
		for (ParserFormat<E, V> format : theParserFormats) {
			components.clear();
			int c = 0;
			int componentCount = 0;
			for (ParserElement<E, V> component : format.getComponents()) {
				int end = c == seq.length() ? -1 : matches(component.getParser(), seq, c, componentsByPosition);
				if (end < 0 && c < seq.length()) {
					int oldC = c;
					boolean hasWS = true;
					while (hasWS && end < 0) {
						hasWS = false;
						for (ComponentParser ws : theWhiteSpace) {
							end = matches(ws, seq, c, componentsByPosition);
							if (end >= 0) {
								hasWS = true;
								c += end;
								end = c == seq.length() ? -1 : matches(component.getParser(), seq, c, componentsByPosition);
							}
						}
					}
					if (end < 0)
						c = oldC;
				}
				if (end >= 0) {
					componentCount++;
					end += c;
					components.add(c, end);
					c = end;
				} else if (!component.isRequired())
					components.add(-1, -1);
				else
					break;
			}
			if (components.size() == format.getComponents().size() && (!wholeText || c == seq.length())) {
				Map<E, ParsedElement<E, V>> componentsByType = createTypeMap(componentCount);
				List<ParsedElement<E, V>> sequence = new ArrayList<>(componentCount);
				List<String> separators = new ArrayList<>(componentCount - 1);
				for (int i = 0; i < components.size(); i++) {
					int compStart = components.getStart(i);
					if (compStart < 0)
						continue;
					int compEnd = components.getEnd(i);
					ParserElement<E, V> component = format.getComponents().get(i);
					if (component == null)
						throw new IllegalStateException();
					E type = component.getType();
					String text = seq.subSequence(compStart, compEnd).toString();
					V value;
					if (type == null)
						value = null;
					else {
						try {
							value = component.getFormat().parse(text);
						} catch (ParseException e) {
							if (valueEx == null)
								valueEx = new ParseException(e.getMessage(), e.getErrorOffset() + compStart);
							continue;
						}
					}
					ParsedElement<E, V> pc = new ParsedElement<>(component.getFormat(), compStart, type, value, text);
					sequence.add(pc);
					if (type != null)
						componentsByType.put(type, pc);
					int nextComp = -1;
					for (int j = i + 1; nextComp < 0 && j < components.size(); j++)
						nextComp = components.getStart(j);
					if (compEnd == seq.length() || (nextComp < 0 && compEnd == nextComp))
						separators.add("");
					else if (nextComp >= 0)
						separators.add(seq.subSequence(compEnd, nextComp).toString());
					else
						separators.add(seq.subSequence(compEnd, c).toString());
				}
				return new ParsedSequence<>(this, format, Collections.unmodifiableList(sequence), Collections.unmodifiableList(separators),
					Collections.unmodifiableMap(componentsByType));
			}
		}
		if (valueEx != null)
			throw valueEx;
		else if (throwIfNotFound)
			throw new ParseException("No format found matching \"" + seq + "\"", 0);
		else
			return null;
	}

	private static int matches(ComponentParser parser, CharSequence seq, int c,
		TreeMap<Integer, Map<ComponentParser, Integer>> componentsByPosition) {
		return componentsByPosition.computeIfAbsent(c, __ -> new HashMap<>()).computeIfAbsent(parser, __ -> {
			return parser.find(seq, c);
		});
	}

	private Map<E, ParsedElement<E, V>> createTypeMap(int componentCount) {
		if (theValueCount < 10 || componentCount * 2 > theValueCount)
			return new EnumMap<>(theType);
		else
			return new HashMap<>(componentCount * 3 / 2 + 1);
	}

	/**
	 * Creates a builder to build a new {@link SimpleSequenceParser}
	 * 
	 * @param <E> The type for the parser
	 * @param <V> The value type for the parser
	 * @param type The type for the parser
	 * @param defaultFormat The format to use for values when a builder is not specified on the sequence element or the component type
	 * @param defaultAdjuster The adjuster to use for values when a builder is not specified on the component type
	 * @return The builder
	 */
	public static <E extends Enum<E>, V extends Comparable<V>> Builder<E, V> build(Class<E> type, Format<V> defaultFormat,
		Adjuster<V> defaultAdjuster) {
		return new Builder<>(type, defaultFormat, defaultAdjuster);
	}

	/**
	 * Builds a {@link SimpleSequenceParser}
	 * 
	 * @param <E> The type of the parser to build
	 * @param <V> The value type of the parser to build
	 */
	public static class Builder<E extends Enum<E>, V extends Comparable<V>> {
		private static final Pattern DIGIT_PARSER = Pattern.compile("\\\\d(?<plus>\\+)?(\\{(?<min>\\d+)(\\s*,\\s*(?<max>\\d+))?\\})?");

		private final Class<E> theType;
		private final List<ParserFormat<E, V>> theParserFormats;
		private final Map<String, ComponentParser> theParsers;
		private final Map<String, Format<V>> theNamedFormats;
		private final Map<String, ParserElement<E, V>> theElements;
		private final List<ComponentParser> theWhitespace;
		private final Format<V> theDefaultFormat;
		private final Map<E, Format<V>> theSpecificFormats;
		private final Adjuster<V> theDefaultAdjuster;
		private final Map<E, Adjuster<V>> theSpecificAdjusters;

		Builder(Class<E> type, Format<V> defaultFormat, Adjuster<V> defaultAdjuster) {
			theType = type;
			theParserFormats = new ArrayList<>();
			theParsers = new HashMap<>();
			theNamedFormats = new HashMap<>();
			theElements = new HashMap<>();
			theWhitespace = new ArrayList<>();
			theDefaultFormat = defaultFormat;
			theSpecificFormats = new EnumMap<>(theType);
			theDefaultAdjuster = defaultAdjuster;
			theSpecificAdjusters = new EnumMap<>(theType);
		}

		/**
		 * Adds white space to the parser that can be parsed between sequence elements without affecting values
		 * 
		 * @param parser The white space parser to add
		 * @return This builder
		 */
		public Builder<E, V> withWhiteSpace(ComponentParser parser) {
			theWhitespace.add(parser);
			return this;
		}

		/**
		 * Adds white space to the parser that can be parsed between sequence elements without affecting values
		 * 
		 * @param pattern The white space pattern to add
		 * @return This builder
		 */
		public Builder<E, V> withWhiteSpace(Pattern pattern) {
			theWhitespace.add(new PatternParser(pattern));
			return this;
		}

		/**
		 * @param name The name for the component
		 * @param parser The parser for the component
		 * @return This builder
		 */
		public Builder<E, V> withParser(String name, ComponentParser parser) {
			theParsers.put(name, parser);
			return this;
		}

		/**
		 * Adds a format to parse values
		 * 
		 * @param name The name for the format
		 * @param format The format to add
		 * @return This builder
		 */
		public Builder<E, V> withFormat(String name, Format<V> format) {
			theNamedFormats.put(name, format);
			return this;
		}

		/**
		 * Adds a sequence element to be part of formats parsed from XML
		 * 
		 * @param name The name of the component
		 * @param component The component to add
		 * @return This builder
		 */
		public Builder<E, V> withComponent(String name, ParserElement<E, V> component) {
			theElements.put(name, component);
			return this;
		}

		/**
		 * @param type The component type that the format is for
		 * @param format The format to parse values
		 * @return This builder
		 */
		public Builder<E, V> withSpecificFormat(E type, Format<V> format) {
			theSpecificFormats.put(type, format);
			return this;
		}

		/**
		 * @param type The component type that the adjuster is for
		 * @param adjuster The adjuster to adjust values
		 * @return This builder
		 */
		public Builder<E, V> withSpecificAdjuster(E type, Adjuster<V> adjuster) {
			theSpecificAdjusters.put(type, adjuster);
			return this;
		}

		/**
		 * @param format The format to add
		 * @return This builder
		 */
		public Builder<E, V> withParserFormat(ParserFormat<E, V> format) {
			theParserFormats.add(format);
			return this;
		}

		/**
		 * @param parserXml URL to an XML file containing components and formats to include
		 * @return This builder
		 * @throws IOException If the XML could not be read or parsed
		 */
		public Builder<E, V> parse(URL parserXml) throws IOException {
			return parse(QommonsConfig.fromXml(parserXml));
		}

		/**
		 * @param parserXml A stream containing XML with components and formats to include
		 * @return This builder
		 * @throws IOException If the XML could not be read or parsed
		 */
		public Builder<E, V> parse(InputStream parserXml) throws IOException {
			return parse(QommonsConfig.fromXml(QommonsConfig.getRootElement(parserXml)));
		}

		/**
		 * @param parserXml XML with components and formats to include
		 * @return This builder
		 */
		public Builder<E, V> parse(QommonsConfig parserXml) {
			Map<String, ParserElement<E, V>> reqComponents = new HashMap<>();
			Map<String, ParserElement<E, V>> optComponents = new HashMap<>();
			Set<String> badComponents = new HashSet<>();
			List<String> errors = new ArrayList<>();
			Map<String, ComponentParser> patternParsers = new HashMap<>();
			for (QommonsConfig compEl : parserXml.subConfigs("components/component")) {
				String name = compEl.get("name");
				if (name == null) {
					System.err.println("Unnamed for component");
					continue;
				}
				errors.clear();
				boolean goodName = true;
				for (int c = 1; goodName && c < name.length(); c++) {
					goodName = name.charAt(c) == name.charAt(c - 1);
				}
				if (!goodName)
					errors.add("Illegal name for component: \"" + name + "\"--names may only contain a single repeated character");
				String valueParserName = compEl.get("value-type");
				String componentTypeName = compEl.get("component-type");
				if (componentTypeName == null) {
					errors.add("No component-type given for component " + name);
				}
				String parserName = compEl.get("parser");
				if (parserName == null) {
					errors.add("No parser given for component " + name);
				}
				E componentType;
				try {
					componentType = (componentTypeName == null || componentTypeName.equals("None")) ? null
						: Enum.valueOf(theType, componentTypeName);
				} catch (IllegalArgumentException e) {
					componentType = null;
					errors.add("Unrecognized date type \"" + componentTypeName + "\" for component " + name);
				}
				ComponentParser parser = theParsers.get(parserName);
				if (parser == null) {
					parser = patternParsers.computeIfAbsent(parserName, __ -> {
						Matcher digitMatcher = DIGIT_PARSER.matcher(parserName);
						if (digitMatcher.matches()) {
							if (digitMatcher.group("plus") != null)
								return new DigitParser(1, Integer.MAX_VALUE);
							else if (digitMatcher.group("max") != null)
								return new DigitParser(//
									Integer.parseInt(digitMatcher.group("min")), //
									Integer.parseInt(digitMatcher.group("max")));
							else if (digitMatcher.group("min") != null) {
								int num = Integer.parseInt(digitMatcher.group("min"));
								return new DigitParser(num, num);
							} else
								return new DigitParser(1, 1);
						}
						try {
							Pattern patt = Pattern.compile(parserName);
							return new PatternParser(patt);
						} catch (PatternSyntaxException e) {
							errors.add("Invalid pattern \"" + parserName + "\" for component " + name + ": " + e.getMessage());
							return null;
						}
					});
				}
				Format<V> valueParser;
				if (componentType == null)
					valueParser = null;
				else if (valueParserName != null)
					valueParser = theNamedFormats.get(valueParserName);
				else {
					valueParser = theSpecificFormats.get(componentType);
					if (valueParser == null)
						valueParser = theDefaultFormat;
				}
				if (componentType != null && valueParser == null)
					errors.add("Unrecognized value-type \"" + valueParserName + "\" for component " + name);
				if (errors.isEmpty()) {
					reqComponents.put(name, new ParserElement<>(componentType, parser, true, valueParser));
					optComponents.put(name, new ParserElement<>(componentType, parser, false, valueParser));
				} else {
					System.err.println("Invalid date/time component:");
					for (String err : errors)
						System.err.println("\t" + err);
					badComponents.add(name);
				}
			}
			List<ParserElement<E, V>> patternComponents = new ArrayList<>();
			StringBuilder compStr = new StringBuilder();
			for (String format : parserXml.getAll("formats/format")) {
				patternComponents.clear();
				char lastChar = format.charAt(0);
				compStr.append(lastChar);
				boolean goodPattern = true;
				for (int c = 1; goodPattern && c <= format.length(); c++) {
					if (c == format.length() || format.charAt(c) != lastChar) {
						boolean required = c == format.length() || format.charAt(c) != '?';
						if (!required)
							c++;
						ParserElement<E, V> component = (required ? reqComponents : optComponents).get(compStr.toString());
						if (component == null) {
							goodPattern = false;
							if (badComponents.contains(compStr.toString()))
								errors.add("Ignoring format " + format + " that uses component with errors: " + compStr);
							else
								errors.add("Format " + format + " refers to unrecognized component: " + compStr);
						} else
							patternComponents.add(component);
						compStr.setLength(0);
					}
					if (c < format.length()) {
						lastChar = format.charAt(c);
						compStr.append(lastChar);
					}
				}
				if (goodPattern)
					theParserFormats.add(new ParserFormat<>(format, QommonsUtils.unmodifiableCopy(patternComponents)));
				else {
					System.err.println("Invalid date/time format \"" + format + "\":");
					for (String err : errors)
						System.err.println("\t" + err);
				}
			}
			return this;
		}

		/** @return A new {@link SimpleSequenceParser} configured by this builder */
		public SimpleSequenceParser<E, V> build() {
			return new SimpleSequenceParser<>(theType, QommonsUtils.unmodifiableCopy(theParserFormats),
				QommonsUtils.unmodifiableCopy(theWhitespace), theDefaultFormat, QommonsUtils.unmodifiableCopy(theSpecificFormats), //
				theDefaultAdjuster, QommonsUtils.unmodifiableCopy(theSpecificAdjusters));
		}
	}

	/** A component parser that matches a sequence of decimal digits */
	public static class DigitParser implements ComponentParser {
		private final int theMin;
		private final int theMax;

		/**
		 * @param min The minimum number of digits in a matched sequence
		 * @param max The maximum number of digits in a matched sequence
		 */
		public DigitParser(int min, int max) {
			theMin = min;
			theMax = max;
		}

		@Override
		public int find(CharSequence str, int start) {
			int end = start + Math.min(str.length() - start, theMax);
			int i;
			for (i = start; i < end; i++) {
				char ch = str.charAt(i);
				if (ch < '0' || ch > '9')
					break;
			}
			i -= start;
			return i < theMin ? -1 : i;
		}
	}
}

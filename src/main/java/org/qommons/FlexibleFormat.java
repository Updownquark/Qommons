package org.qommons;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class FlexibleFormat<T> {
	public static final String OPTIONAL = "<optional>";

	public static interface FormatToken {
		/**
		 * Tests to see whether the given point in the character sequence matches this token
		 * 
		 * @param seq The character sequence to test
		 * @param start The index in the sequence to begin the test
		 * @return The index in the sequence that is the end of the found match, or a number &lt;<code>start</code> if a match was not found
		 */
		int find(CharSequence seq, int start);

		/**
		 * @param match The character sequence found by {@link #find(CharSequence, int)}
		 * @return The value represented by the sequence
		 */
		int parse(CharSequence match);
	}
	
	public static final FormatToken DIGIT = new FormatToken() {
		@Override
		public int find(CharSequence seq, int start) {
			while (start < seq.length()) {
				char ch = seq.charAt(start);
				if (ch < '0' || ch > '9')
					break;
				start++;
			}
			return start;
		}

		@Override
		public int parse(CharSequence match) {
			return Integer.parseInt(match.toString());
		}

		@Override
		public String toString() {
			return "digit";
		}
	};
	public static final FormatToken CHARS = new FormatToken() {
		@Override
		public int find(CharSequence seq, int start) {
			while (start < seq.length() && Character.isAlphabetic(seq.charAt(start)))
				start++;
			return start;
		}

		@Override
		public int parse(CharSequence match) {
			return -1;
		}

		@Override
		public String toString() {
			return "alpha";
		}
	};

	public static FormatToken single(char... chars) {
		class SingleCharToken implements FormatToken {
			private char[] chars() {
				return chars;
			}

			@Override
			public int find(CharSequence seq, int start) {
				for (char ch : chars) {
					if (seq.charAt(start) == ch)
						return start + 1;
				}
				return -1;
			}

			@Override
			public int parse(CharSequence match) {
				for (int i = 0; i < chars.length; i++) {
					if (chars[i] == match.charAt(0))
						return i;
				}
				throw new IllegalStateException("No match");
			}

			@Override
			public int hashCode() {
				return Arrays.hashCode(chars);
			}

			@Override
			public boolean equals(Object obj) {
				if (this == obj)
					return true;
				else if (!(obj instanceof SingleCharToken))
					return false;
				return Arrays.equals(chars, ((SingleCharToken) obj).chars());
			}

			@Override
			public String toString() {
				return Arrays.toString(chars);
			}
		}
		return new SingleCharToken();
	}

	public static final FormatToken simple(CharSequence... texts) {
		class SimpleToken implements FormatToken {
			private CharSequence[] texts() {
				return texts;
			}

			@Override
			public int find(CharSequence seq, int start) {
				for (CharSequence text : texts) {
					int end = start + text.length();
					if (end > seq.length())
						continue;
					boolean found = true;
					for (int i = 0; i < text.length(); i++) {
						if (seq.charAt(start + i) != text.charAt(i)) {
							found = false;
							break;
						}
					}
					if (found)
						return end;
				}
				return -1;
			}

			@Override
			public int parse(CharSequence match) {
				for (int i = 0; i < texts.length; i++) {
					if (texts[i].length() > match.length())
						continue;
					boolean found = true;
					for (int c = 0; c < texts[i].length(); c++) {
						if (match.charAt(c) != texts[i].charAt(c)) {
							found = false;
							break;
						}
					}
					if (found)
						return i;
				}
				throw new IllegalStateException("No match");
			}

			@Override
			public int hashCode() {
				return Arrays.hashCode(texts);
			}

			@Override
			public boolean equals(Object obj) {
				if (this == obj)
					return true;
				else if (!(obj instanceof SimpleToken))
					return false;
				return Arrays.equals(texts, ((SimpleToken) obj).texts());
			}

			@Override
			public String toString() {
				return Arrays.toString(texts);
			}
		}
		return new SimpleToken();
	}
	
	public static abstract class FormatComponent<T> implements Named{
		public abstract T getComponentType();

		public abstract FormatToken getParsedType();

		public abstract Predicate<CharSequence> getFilter();

		public abstract int getValueOffset();
		
		public abstract FormatComponent<T> optional();
		
		public abstract boolean isRequired();

		public boolean matches(ParsedElement element) {
			if (getComponentType() != element.token)
				return false;
			return getFilter()!=null || getFilter().test(element.text);
		}
	}
	
	private static class FormatComponentImpl<T> extends FormatComponent<T> {
		private final String theName;
		private final T componentType;
		private final FormatToken parsedType;
		private final Predicate<CharSequence> filter;
		private int theValueOffset;

		FormatComponentImpl(String name, T componentType, FormatToken parsedType, Predicate<CharSequence> filter) {
			theName=name;
			this.componentType = componentType;
			this.parsedType = parsedType;
			this.filter = filter;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public T getComponentType() {
			return componentType;
		}

		@Override
		public FormatToken getParsedType() {
			return parsedType;
		}

		@Override
		public Predicate<CharSequence> getFilter() {
			return filter;
		}

		@Override
		public int getValueOffset() {
			return theValueOffset;
		}

		@Override
		public FormatComponent<T> optional() {
			return new OptionalComponent<>(this);
		}

		@Override
		public boolean isRequired() {
			return true;
		}

		FormatComponent<T> offset(int offset) {
			this.theValueOffset = offset;
			return this;
		}

		@Override
		public boolean matches(ParsedElement element) {
			if (parsedType != element.token)
				return false;
			return filter == null || filter.test(element.text);
		}

		@Override
		public String toString() {
			if (componentType != null)
				return componentType.toString();
			else
				return parsedType.toString();
		}
	}
	
	private static class OptionalComponent<T> extends FormatComponent<T> {
		private final FormatComponent<T> theWrapped;

		OptionalComponent(FormatComponent<T> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public String getName() {
			return theWrapped.getName();
		}

		@Override
		public T getComponentType() {
			return theWrapped.getComponentType();
		}

		@Override
		public FormatToken getParsedType() {
			return theWrapped.getParsedType();
		}

		@Override
		public Predicate<CharSequence> getFilter() {
			return theWrapped.getFilter();
		}

		@Override
		public int getValueOffset() {
			return theWrapped.getValueOffset();
		}

		@Override
		public FormatComponent<T> optional() {
			return this;
		}

		@Override
		public boolean isRequired() {
			return false;
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	public static class ParsedElement {
		private final FormatToken token;
		private final int index;
		private final CharSequence text;
		private int offset;
		private int value;

		public ParsedElement(FormatToken token, int index, CharSequence text) {
			this.token = token;
			this.index = index;
			this.text = text;
			value = -1;
		}

		public FormatToken getToken() {
			return token;
		}

		public int getIndex() {
			return index;
		}

		public CharSequence getText() {
			return text;
		}

		public int getOffset() {
			return offset;
		}

		public int getValue() {
			return value;
		}

		ParsedElement setValue(int value) {
			this.value = value;
			return this;
		}

		@Override
		public String toString() {
			return text.toString();
		}
	}
	
	public static class FormatOption<T>{
		private final List<FormatComponent<T>> components;
		private final Map<T, FormatComponent<T>> componentsByType;
	
		private FormatOption(List<FormatComponent<T>> components) {
			this.components = components;
			componentsByType = new HashMap<>();
			for (FormatComponent<T> c : components) {
				if (c.getComponentType() != null)
					componentsByType.put(c.getComponentType(), c);
			}
		}
	
		int match(Map<T, ParsedElement> info, List<ParsedElement> elements) {
			int i = 0, j = 0;
			while (i < elements.size() && j < components.size()) {
				FormatComponent<T> component = components.get(j);
				ParsedElement element = elements.get(i);
				if (component.matches(element)) {
					if (component.getComponentType() != null) {
						element.offset = component.getValueOffset();
						info.put(component.getComponentType(), element);
					}
					i++;
				} else if (component.isRequired())
					return 0;
				j++;
			}
			while (j < components.size())
				if (components.get(j++).isRequired())
					return 0;
			return i;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			for (FormatComponent<T> comp : components)
				str.append(comp.getName());
			return str.toString();
		}
	}
	
	public static class TokenizedText {
		private final List<ParsedElement> theElements;
		private final int theEndIndex;

		TokenizedText(List<ParsedElement> elements, int endIndex) {
			theElements = elements;
			theEndIndex = endIndex;
		}

		public List<ParsedElement> getElements() {
			return theElements;
		}

		public int getEndIndex() {
			return theEndIndex;
		}

		@Override
		public String toString() {
			return theElements.toString();
		}
	}

	public static class FormatSolution<T> {
		private final String theText;
		private final FormatOption<T> theOption;
		private final Map<T, ParsedElement> theElements;

		FormatSolution(String text, FormatOption<T> option, Map<T, ParsedElement> elements) {
			theText = text;
			theOption = option;
			theElements = elements;
		}

		public String getText() {
			return theText;
		}

		public FormatOption<T> getOption() {
			return theOption;
		}

		public Map<T, ParsedElement> getElements() {
			return theElements;
		}

		@Override
		public String toString() {
			return theText;
		}
	}

	private final Collection<FormatToken> theTokens;
	private final List<FormatOption<T>> theOptions;
	private final char[] theWhitespace;
	
	FlexibleFormat(Collection<FormatToken> tokens, List<FormatOption<T>> options, char[] whitespace) {
		theTokens = tokens;
		theOptions = options;
		theWhitespace = whitespace;
	}
	
	public FormatSolution<T> parse(CharSequence str, boolean wholeText, boolean throwIfNotFound) throws ParseException {
		TokenizedText tokenized = tokenize(str, wholeText, throwIfNotFound);
		if (tokenized == null)
			return null;
		return parse(str, tokenized, wholeText, throwIfNotFound);

	}

	public FormatSolution<T> parse(CharSequence str, TokenizedText tokenized, boolean wholeText, boolean throwIfNotFound)
		throws ParseException {
		Map<T, ParsedElement> elements = new LinkedHashMap<>();
		int bestIndex = -1;
		FormatOption<T> found = null;
		for (FormatOption<T> option : theOptions) {
			elements.clear();
			int index = option.match(elements, tokenized.getElements());
			if (index > 0 && (bestIndex<0 || index>bestIndex)) {
				found = option;
//				if(
				break;
			}
		}
		if (found == null) {
			if (throwIfNotFound)
				throw new ParseException("Unrecognized format", 0);
			else
				return null;
		}
		int todo = 0; // Previously this was in the expression below as "elements.".
		// Don't know what I was doing with this, but I wanted to get rid of the error
		String text = str.subSequence(0, todo).toString();
		return new FormatSolution<>(text, found, elements);
	}

	public TokenizedText tokenize(CharSequence str, boolean wholeText, boolean throwIfNotFound) throws ParseException {
		List<ParsedElement> elements = new ArrayList<>();
		int i = 0;
		boolean found = true;
		while (found && i < str.length()) {
			if (Arrays.binarySearch(theWhitespace, str.charAt(i)) >= 0) {
				i++;
				continue;
			}
			found = false;
			for (FormatToken token : theTokens) {
				int end = token.find(str, i);
				if (end > i) {
					found = true;
					elements.add(new ParsedElement(token, i, str.subSequence(i, end)));
					i = end;
					break;
				}
			}
		}
		if (elements.isEmpty() || (wholeText && !found)) {
			if (throwIfNotFound) {
				if (i == 0)
					throw new ParseException("Not item found", 0);
				else
					throw new ParseException("Unrecognized component", i);
			}
			return null;
		}
		return new TokenizedText(elements, elements.get(elements.size() - 1).index + elements.get(elements.size() - 1).getText().length());
	}

	public static <T> FormatBuilder<T> build() {
		return new FormatBuilder<>();
	}

	public static class FormatBuilder<T>{
		private final Set<FormatToken> theTokens;
		private final Map<String, FormatComponent<T>> theComponents;
		private final List<FormatOption<T>> theOptions;
		private final StringBuilder theWhitespace;
		
		FormatBuilder(){
			theTokens = new LinkedHashSet<>();
			theComponents=new HashMap<>();
			theOptions=new ArrayList<>();
			theWhitespace = new StringBuilder();
		}
		
		public FormatBuilder<T> withWhitespace(String whitespace) {
			theWhitespace.append(whitespace);
			return this;
		}

		public FormatComponent<T> createComponent(String name, T componentType, FormatToken token, Predicate<CharSequence> filter) {
			if (name.equals(OPTIONAL))
				throw new IllegalArgumentException("The component name " + name + " is reserved");
			theTokens.add(token);
			FormatComponent<T> comp = new FormatComponentImpl<>(name, componentType, token, filter);
			FormatComponent<T> old=theComponents.putIfAbsent(name, comp);
			if (old != null)
				throw new IllegalArgumentException("A component named "+name+" has already been created");
			return comp;
		}
		
		public FormatBuilder<T> withComponent(String name, T componentType, FormatToken parseType, Predicate<CharSequence> filter) {
			createComponent(name, componentType, parseType, filter);
			return this;
		}
		
		public FormatBuilder<T> withOption(FormatComponent<T>... components){
			theOptions.add(new FormatOption<>(Arrays.asList(components)));
			return this;
		}
		
		public FormatBuilder<T> withOption(Object... components){
			List<FormatComponent<T>> options=new ArrayList<>(components.length);
			FormatComponent<T> last=null;
			for(Object o : components) {
				if (OPTIONAL.equals(o)) {
					if(last==null)
						throw new IllegalArgumentException(OPTIONAL + " must immediately follow the name of a component");
					last = last.optional();
					options.add(last);
					last=null;
				} else {
					if(last!=null) {
						options.add(last);
						last=null;
					}
					if(o instanceof String) {
						last=theComponents.get(o);
						if(last==null)
							throw new IllegalArgumentException("No such component named "+o);
					} else if(o instanceof FormatComponent)
						options.add((FormatComponent<T>) o);
					else if(o ==null)
						throw new NullPointerException("Null object in arguments: "+components);
					else
						throw new IllegalArgumentException(
							"Objects in the arguments must be strings (the name of a component or " + OPTIONAL + ")," + " or instances of "
								+ FormatComponent.class.getSimpleName() + ", not " + o.getClass().getName());
				}
			}
			if(last!=null)
				options.add(last);
			theOptions.add(new FormatOption<>(options));
			return this;
		}
		
		public FlexibleFormat<T> build(){
			if(theOptions.isEmpty())
				throw new IllegalStateException("No options specified for format");
			char[] whitespace = theWhitespace.toString().toCharArray();
			Arrays.sort(whitespace);
			int j = 0;
			for (int i = 0; i < whitespace.length; i++) {
				if (j == 0 || whitespace[i] != whitespace[j - 1]) {
					whitespace[j] = whitespace[i];
					j++;
				}
			}
			if (j < whitespace.length) {
				char[] newWS = new char[j];
				System.arraycopy(whitespace, 0, newWS, 0, j);
				whitespace = newWS;
			}

			return new FlexibleFormat<>(QommonsUtils.unmodifiableCopy(theTokens), QommonsUtils.unmodifiableCopy(theOptions), whitespace);
		}
	}
}

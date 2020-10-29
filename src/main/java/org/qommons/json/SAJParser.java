/* SAJParser.java Created Jul 23, 2010 by Andrew Butler, PSL */
package org.qommons.json;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Parses JSON from a stream incrementally, notifying a handler as each piece is parsed */
public class SAJParser {
	/** Handles the JSON input from the parser */
	public interface ParseHandler {
		/**
		 * Called when the start of a JSON object is encountered
		 *
		 * @param state The current parsing state
		 */
		void startObject(ParseState state);

		/**
		 * Called when the start of a property within JSON object is encountered
		 *
		 * @param state The current parsing state
		 * @param name The name of the new property
		 */
		void startProperty(ParseState state, String name);

		/**
		 * Called when a separator is encountered in the JSON (comma or colon)
		 *
		 * @param state The current parsing state
		 */
		void separator(ParseState state);

		/**
		 * Called when the end of a property within a JSON object is encountered
		 *
		 * @param state The current parsing state
		 * @param propName The name of the property that was parsed
		 */
		void endProperty(ParseState state, String propName);

		/**
		 * Called when the end of a JSON object is encountered
		 *
		 * @param state The current parsing state
		 */
		void endObject(ParseState state);

		/**
		 * Called when the start of a JSON array is encountered
		 *
		 * @param state The current parsing state
		 */
		void startArray(ParseState state);

		/**
		 * Called when the end of a JSON array is encountered
		 *
		 * @param state The current parsing state
		 */
		void endArray(ParseState state);

		/**
		 * Called when a boolean is encountered in the JSON
		 *
		 * @param state The current parsing state
		 * @param value The boolean value that was parsed
		 */
		void valueBoolean(ParseState state, boolean value);

		/**
		 * Called when a string is encountered in the JSON
		 *
		 * @param state The current parsing state
		 * @param value A reader to read the string value
		 * @throws IOException If an error occurs reading the string
		 */
		void valueString(ParseState state, Reader value) throws IOException;

		/**
		 * Called when a number is encountered in the JSON
		 *
		 * @param state The current parsing state
		 * @param value The number value that was parsed
		 */
		void valueNumber(ParseState state, Number value);

		/**
		 * Called when a null value is encountered
		 *
		 * @param state The current parsing state
		 */
		void valueNull(ParseState state);

		/**
		 * Called when the white space is encountered
		 *
		 * @param state The current parsing state
		 * @param ws The white space that was ignored
		 */
		void whiteSpace(ParseState state, String ws);

		/**
		 * Called when a line- or block-style comment is encountered
		 *
		 * @param state The current parsing state
		 * @param fullComment The full comment
		 * @param content The content of the comment
		 */
		void comment(ParseState state, String fullComment, String content);

		/**
		 * Called when parsing has finished
		 *
		 * @return The value that this handler produced from the parsing
		 */
		Object finalValue();

		/**
		 * Called when an error is encountered in the JSON content that prevents parsing from continuing. This method is always called just
		 * before the parser throws a {@link ParseException}.
		 *
		 * @param state The parsing state at the time of the error
		 * @param error The description of the error that occurred in the JSON content
		 */
		void error(ParseState state, String error);
	}

	/**
	 * A simple handler that uses the parser's notifications to create the JSON value as it was represented in the stream.
	 */
	public static class DefaultHandler implements ParseHandler {
		private ParseState theState;

		private Object theValue;

		private java.util.ArrayList<Object> thePath;

		/** Creates a DefaultHandler */
		public DefaultHandler() {
			thePath = new java.util.ArrayList<>();
		}

		/**
		 * @return The current state of parsing
		 */
		public ParseState getState() {
			return theState;
		}

		/**
		 * Resets this handler's state. This is only useful in order to reuse the handler after a parsing error.
		 */
		public void reset() {
			theState = null;
			theValue = null;
			thePath.clear();
		}

		@Override
		public void startObject(ParseState state) {
			theState = state;
			JsonObject value = new JsonObject();
			if (top() instanceof List)
				((List<Object>) top()).add(value);
			else if (top() instanceof JsonObject)
				((JsonObject) top()).with(state.fromTop(1).getPropertyName(), value);
			thePath.add(value);
		}

		@Override
		public void startProperty(ParseState state, String name) {
			theState = state;
		}

		@Override
		public void separator(ParseState state) {
			theState = state;
		}

		@Override
		public void endProperty(ParseState state, String propName) {
			theState = state;
		}

		@Override
		public void endObject(ParseState state) {
			theState = state;
			pop();
		}

		@Override
		public void startArray(ParseState state) {
			theState = state;
			List<Object> value = new ArrayList<>();
			if (top() instanceof List)
				((List<Object>) top()).add(value);
			else if (top() instanceof JsonObject)
				((JsonObject) top()).with(state.fromTop(1).getPropertyName(), value);
			thePath.add(value);
		}

		@Override
		public void endArray(ParseState state) {
			theState = state;
			pop();
		}

		@Override
		public void valueString(ParseState state, Reader value) throws IOException {
			StringBuilder val = new StringBuilder();
			int read = value.read();
			while(read >= 0) {
				val.append((char) read);
				read = value.read();
			}
			valueString(state, val.toString());
		}

		/**
		 * By default, {@link #valueString(ParseState, Reader)} merely reads the string and passes it to this method for easier handling
		 *
		 * @param state The current parsing state
		 * @param value The string value that was parsed
		 */
		public void valueString(ParseState state, String value) {
			theState = state;
			if (top() instanceof List)
				((List<Object>) top()).add(value);
			else if (top() instanceof JsonObject)
				((JsonObject) top()).with(state.top().getPropertyName(), value);
			else
				theValue = value;
		}

		@Override
		public void valueNumber(ParseState state, Number value) {
			theState = state;
			if (top() instanceof List)
				((List<Object>) top()).add(value);
			else if (top() instanceof JsonObject)
				((JsonObject) top()).with(state.top().getPropertyName(), value);
			else
				theValue = value;
		}

		@Override
		public void valueBoolean(ParseState state, boolean value) {
			Boolean bValue = value ? Boolean.TRUE : Boolean.FALSE;
			theState = state;
			if (top() instanceof List)
				((List<Object>) top()).add(bValue);
			else if (top() instanceof JsonObject)
				((JsonObject) top()).with(state.top().getPropertyName(), bValue);
			else
				theValue = Boolean.valueOf(value);
		}

		@Override
		public void valueNull(ParseState state) {
			theState = state;
			if (top() instanceof List)
				((List<Object>) top()).add(null);
			else if (top() instanceof JsonObject)
				((JsonObject) top()).with(state.top().getPropertyName(), null);
			else
				theValue = null;
		}

		@Override
		public void whiteSpace(ParseState state, String ws) {
			theState = state;
		}

		@Override
		public void comment(ParseState state, String fullComment, String content) {
			theState = state;
		}

		@Override
		public Object finalValue() {
			return theValue;
		}

		@Override
		public void error(ParseState state, String error) {
			theState = state;
		}

		/**
		 * Replaces the most recently parsed value with another. This is useful for modifying the content of a JSON structure as it is
		 * parsed.
		 *
		 * @param lastValue The value to replace the most recently parsed value with.
		 */
		protected void setLastValue(Object lastValue) {
			theValue = lastValue;
			Object top = top();
			if (top instanceof List) {
				List<Object> jsonA = (List<Object>) top;
				jsonA.set(jsonA.size() - 1, lastValue);
			}
		}

		/**
		 * @return The depth of the item that is currently being parsed
		 */
		public int getDepth() {
			return thePath.size();
		}

		/**
		 * @return The item that is currently being parsed
		 */
		public Object top() {
			if(thePath.size() == 0)
				return null;
			return thePath.get(thePath.size() - 1);
		}

		/**
		 * @param depth The depth of the item to get
		 * @return The item that is being parsed at the given depth, or null if depth> {@link #getDepth()}
		 */
		public Object fromTop(int depth) {
			if(thePath.size() <= depth)
				return null;
			return thePath.get(thePath.size() - depth - 1);
		}

		private void pop() {
			theValue = thePath.remove(thePath.size() - 1);
		}
	}

	/** Represents a type of JSON item that can be parsed */
	public static enum ParseToken {
		/** Represents a JSON object */
		OBJECT, /** Represents a JSON array */
		ARRAY, /** Represents a property within a JSON object */
		PROPERTY;
	}

	/** Represents an object whose parsing has not been completed */
	public static class ParseNode {
		/** The type of object that this node represents */
		public final ParseToken token;

		private String thePropertyName;

		private boolean hasContent;

		/**
		 * @param _token The parse token that this node is for
		 */
		public ParseNode(ParseToken _token) {
			token = _token;
		}

		void setPropertyName(String propName) {
			thePropertyName = propName;
		}

		void setHasContent() {
			hasContent = true;
		}

		/**
		 * @return The name of the property that this represents (if its token is {@link ParseToken#PROPERTY})
		 */
		public String getPropertyName() {
			return thePropertyName;
		}

		/**
		 * @return Whether this node has content yet
		 */
		public boolean hasContent() {
			return hasContent;
		}

		@Override
		public String toString() {
			switch (token) {
			case ARRAY:
				return "array";
			case OBJECT:
				return "object";
			case PROPERTY:
				return "property(" + thePropertyName + ")";
			}
			return "Unrecognized";
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof ParseNode && ((ParseNode) o).token == token
				&& org.qommons.ArrayUtils.equals(thePropertyName, ((ParseNode) o).thePropertyName);
		}

		@Override
		public int hashCode() {
			return token.hashCode() * 17 + (thePropertyName == null ? 0 : thePropertyName.hashCode());
		}
	}

	/** A reader that parses a JSON-escaped string on the fly */
	public static class StringParseReader extends Reader {
		private final Reader theWrapped;

		private char theStartChar;

		private int theBuffer;

		/**
		 * @param wrap The reader to read JSON-escaped the string from
		 */
		public StringParseReader(Reader wrap) {
			theWrapped = wrap;
			theBuffer = -1;
		}

		/**
		 * Starts this reader. This may be called after the reader has parsed a string, assuming the stream cursor has moved
		 *
		 * @param startChar The character that started the string (" or ')
		 */
		public void start(char startChar) {
			theStartChar = startChar;
		}

		@Override
		public int read() throws IOException {
			if(theStartChar == 0)
				return -1;
			int buffer = theBuffer;
			theBuffer = -1;
			if(buffer >= 0)
				return buffer;

			int read = theWrapped.read();
			if(read < 0) {
				theStartChar = 0;
				return read;
			}
			if(read == '"') {
				theStartChar = 0;
				return -1;
			}
			if(read != '\\')
				return read;
			read = theWrapped.read();
			if(read != 'u')
				switch (read) {
				case 'n':
					return '\n';
				case 'r':
					return '\r';
				case 't':
					return '\t';
				case 'b':
					return '\b';
				case 'f':
					return '\f';
				case '/':
					// Solidus
					return '\u2044';
				case '\'':
					return '\'';
				case '"':
					return '"';
				case '\\':
					return '\\';
				default:
					throw new IOException(((char) read) + " is not escapable");
				}
			int unicode = 0;
			StringBuilder unicodeStr = new StringBuilder();

			for(int i = 0; i < 4; i++) {
				read = theWrapped.read();
				unicodeStr.append((char) read);
				unicode <<= 4;
				if(read >= '0' && read <= '9')
					unicode |= read - '0';
				else if(read >= 'a' && read <= 'f')
					unicode |= read - 'a' + 10;
				else if(read >= 'A' && read <= 'F')
					unicode |= read - 'A' + 10;
				else
					throw new IOException("Invalid unicode sequence: \"\\u" + unicodeStr);
			}
			char [] chars = Character.toChars(unicode);
			if(chars.length > 1)
				theBuffer = chars[1];
			return chars[0];
		}

		@Override
		public int read(char [] cbuf, int off, int len) throws IOException {
			for(int i = 0; i < len; i++) {
				int read = read();
				if(read < 0)
					return i;
				cbuf[off + i] = (char) read;
			}
			return len;
		}

		@Override
		public void close() throws IOException {
			while (read() >= 0) {}
		}
	}

	/** A state object which can be queried for the path to the currently parsed object */
	public static class ParseState {
		private final Reader theReader;

		private final StringParseReader theStringReader;

		private final ParseHandler [] theHandlers;

		private final java.util.ArrayList<ParseNode> thePath;

		private int theLastChar;

		private int theCurrentChar;

		private int theIndex;

		private boolean wasLastLine;

		private int theLineNumber;

		private int theCharNumber;

		private boolean isSeparated;

		ParseState(Reader reader, ParseHandler... helper) {
			theReader = reader;
			theHandlers = helper;
			thePath = new java.util.ArrayList<>();
			theLastChar = -1;
			theCurrentChar = -1;
			theLineNumber = 1;
			theStringReader = new StringParseReader(reader);
		}

		int nextChar() throws IOException {
			theStringReader.close(); // If a string was being parsed, finish parsing it
			wasLastLine = false;
			do {
				theIndex++;
				theCharNumber++;
				if(theLastChar >= 0)
					theCurrentChar = theLastChar;
				else
					theCurrentChar = theReader.read();
				theLastChar = -1;
				if(theCurrentChar == '\n' || theCurrentChar == '\r') {
					wasLastLine = true;
					theCharNumber = 0;
					theLineNumber++;
				}
				/* Ignoring newlines helps when parsing output that is wrapped regardless of
				 * content, e.g. when copying from DOS command prompt output */
			} while(theCurrentChar >= 0 && (theCurrentChar == '\n' || theCurrentChar == '\r'));
			return theCurrentChar;
		}

		int currentChar() {
			return theCurrentChar;
		}

		boolean wasLastLine() {
			return wasLastLine;
		}

		/**
		 * "Backs up" the stream so that the most recent character read is the next character returned from {@link #nextChar()}. This only
		 * works once in between calls to {@link #nextChar()}
		 */
		void backUp() {
			if(theLastChar >= 0)
				throw new IllegalStateException("Can't back up before the first read" + " or more than once between reads!");
			theLastChar = theCurrentChar;
			theIndex--;
		}

		private void startItem() throws ParseException {
			ParseNode top = top();
			if(top != null) {
				switch (top.token) {
				case OBJECT:
					error("Property name missing in object");
					break;
				case ARRAY:
					if(top.hasContent() && !isSeparated)
						error("Missing comma separator in array");
					top.setHasContent();
					break;
				case PROPERTY:
					if(!isSeparated) {
						if(top.hasContent())
							error("Missing separator comma after value of object property " + top.getPropertyName());
						else
							error("Missing separator colon after object property " + top.getPropertyName());
					}
					top.setHasContent();
					fromTop(1).setHasContent();
					break;
				}
			}
			isSeparated = false;
		}

		void startObject() throws ParseException {
			startItem();
			push(ParseToken.OBJECT);
			for(ParseHandler handler : theHandlers)
				handler.startObject(this);
		}

		void startArray() throws ParseException {
			startItem();
			push(ParseToken.ARRAY);
			for(ParseHandler handler : theHandlers)
				handler.startArray(this);
		}

		void startProperty(String propertyName) throws ParseException {
			push(ParseToken.PROPERTY);
			thePath.get(thePath.size() - 1).setPropertyName(propertyName);
			for(ParseHandler handler : theHandlers)
				handler.startProperty(this, propertyName);
		}

		void startString(char startChar) throws ParseException, IOException {
			startItem();
			theStringReader.start(startChar);
			if(theHandlers.length > 1) {
				/* If we have more than one handler, we have to read the contents and feed them to each handler from memory */
				StringBuilder contents = new StringBuilder();
				int read = theStringReader.read();
				while(read >= 0) {
					contents.append((char) read);
					read = theStringReader.read();
				}
				String contentStr = contents.toString();
				for(int i = 0; i < theHandlers.length; i++)
					theHandlers[i].valueString(this, new java.io.StringReader(contentStr));
			} else if(theHandlers.length == 1)
				theHandlers[0].valueString(this, theStringReader);
		}

		void setPrimitive(Object value) throws ParseException {
			startItem();
			for(ParseHandler handler : theHandlers) {
				if(value == null)
					handler.valueNull(this);
				else if(value instanceof Boolean)
					handler.valueBoolean(this, ((Boolean) value).booleanValue());
				else if(value instanceof Number)
					handler.valueNumber(this, (Number) value);
				else if(value instanceof String)
					error("Internal error: Primitive string given, not in reader");
				else
					error("Unrecognized primitive value: " + value);
			}
		}

		private void push(ParseToken token) throws ParseException {
			if(top() != null) {
				switch (top().token) {
				case OBJECT:
					if(token != ParseToken.PROPERTY)
						error("Property name required in object");
					break;
				case ARRAY:
					break;
				case PROPERTY:
					break;
				}
			}
			isSeparated = false;
			thePath.add(new ParseNode(token));
		}

		void separate(char ch) throws ParseException {
			if(ch == ',') {
				ParseNode top = top();
				if(top == null)
					error("Unexpected separator comma without object or array");
				switch (top.token) {
				case OBJECT:
					if(!top.hasContent())
						error("Unexpected separator comma before first property in object");
					break;
				case ARRAY:
					if(!top.hasContent())
						error("Unexpected separator comma before first element in array");
					break;
				case PROPERTY:
					if(top.hasContent())
						pop(ParseToken.PROPERTY);
					else
						error("Unexpected separator comma after object property");
					break;
				}
			} else if(ch == ':') {
				ParseNode top = top();
				if(top == null)
					error("Unexpected separator colon without object or array");
				switch (top.token) {
				case OBJECT:
					error("Unexpected separator colon before property declaration in object");
					break;
				case ARRAY:
					error("Unexpected separator colon in array");
					break;
				case PROPERTY:
					if(isSeparated)
						error("Dual separator colons in object property");
					break;
				}
			} else
				error("'" + ch + "' is not a separator");
			isSeparated = true;
			for(ParseHandler handler : theHandlers)
				handler.separator(this);
		}

		void whiteSpace(String ws) {
			for(ParseHandler handler : theHandlers)
				handler.whiteSpace(this, ws);
		}

		void comment(String comment, String content) {
			for(ParseHandler handler : theHandlers)
				handler.comment(this, comment, content);
		}

		ParseNode pop(ParseToken token) throws ParseException {
			ParseNode ret = top();
			switch (token) {
			case OBJECT:
				if(ret == null)
					error("Unexpected '}' before input");
				switch (ret.token) {
				case ARRAY:
					error("Expected ']' for array end but found '}'");
					break;
				case PROPERTY:
					if(!ret.hasContent())
						error("Property " + ret.getPropertyName() + " missing content");
					else if(isSeparated())
						error("Expected next property after " + ret.getPropertyName());
					pop(ParseToken.PROPERTY);
					//$FALL-THROUGH$
				case OBJECT:
					thePath.remove(thePath.size() - 1);
					for(ParseHandler handler : theHandlers)
						handler.endObject(this);
					break;
				}
				break;
			case ARRAY:
				if(ret == null)
					error("Unexpected ']' before input");
				switch (ret.token) {
				case OBJECT:
				case PROPERTY:
					error("Expected '}' for object end but fount ']'");
					break;
				case ARRAY:
					thePath.remove(thePath.size() - 1);
					for(ParseHandler handler : theHandlers)
						handler.endArray(this);
					break;
				}
				break;
			case PROPERTY:
				if(ret == null)
					error("Unexpected property end");
				switch (ret.token) {
				case OBJECT:
				case ARRAY:
					error("Unexpected property end");
					break;
				case PROPERTY:
					thePath.remove(thePath.size() - 1);
					for(ParseHandler handler : theHandlers)
						handler.endProperty(this, ret.getPropertyName());
					break;
				}
			}
			return ret;
		}

		/**
		 * @return The current depth of the parsing (e.g. an array within a property within an object=depth of 3)
		 */
		public int getDepth() {
			return thePath.size();
		}

		/**
		 * @return The item that is currently being parsed
		 */
		public ParseNode top() {
			if(thePath.size() == 0)
				return null;
			return thePath.get(thePath.size() - 1);
		}

		/**
		 * @param depth The depth of the item to get
		 * @return The item that is being parsed at the given depth, or null if depth>= {@link #getDepth()}
		 */
		public ParseNode fromTop(int depth) {
			if(thePath.size() <= depth)
				return null;
			return thePath.get(thePath.size() - depth - 1);
		}

		/**
		 * @return The number of characters that have been read from the stream so far
		 */
		public int getIndex() {
			return theIndex;
		}

		/**
		 * @return The number of new lines that have been read so far plus one
		 */
		public int getLineNumber() {
			return theLineNumber;
		}

		/**
		 * @return The number of characters that have been read since the last new line
		 */
		public int getCharNumber() {
			return theCharNumber;
		}

		/**
		 * @return Whether the current parsing node has been separated, e.g. whether a comma has occurred after an array element or an
		 *         object property (if this token is {@link ParseToken#OBJECT}) or if a colon has occurred after a property declaration (if
		 *         this token is {@link ParseToken#PROPERTY})
		 */
		public boolean isSeparated() {
			return isSeparated;
		}

		/**
		 * <p>
		 * Causes the parser to act as if it just encountered a value. This is useful when parsing an outer JSON shell in which individual
		 * elements are parsed by a different parser. For instance, if an event has a data property whose value should be parsed externally,
		 * this parser can be used for the event, then after the data property is encountered (and after the separator colon), the external
		 * parser can parse the value of data and this method can be called to satisfy the parser so the event can be further parsed. This
		 * method does not cause any callbacks to be invoked in the handler.
		 * </p>
		 * <p>
		 * This method will throw a ParseException unless it is invoked in one of the following situations:
		 * <ul>
		 * <li>Before any content has been parsed. Calling this in this situation will have no effect at all.</li>
		 * <li>After the separator colon after a property declaration in a JSON object</li>
		 * <li>After the beginning of an array</li>
		 * <li>After a separator comma within an array</li>
		 * </ul>
		 * </p>
		 * <p>
		 * If this method is called superfluously even though the stream is read exclusively from this parser, the parser will throw a
		 * ParseException after the stream's content is read since it will believe it already encountered a value and duplicate values
		 * without a separator are illegal in JSON.
		 * </p>
		 *
		 * @throws ParseException If the current state is not appropriate for a value
		 */
		public void spoofValue() throws ParseException {
			startItem();
		}

		void error(String error) throws ParseException {
			for(ParseHandler handler : theHandlers)
				handler.error(this, error);
			throw new ParseException(error, this);
		}

		@Override
		public String toString() {
			StringBuilder ret = new StringBuilder();
			ret.append("Line ").append(theLineNumber).append(", Column ").append(theCharNumber).append('\n');
			for(ParseNode node : thePath)
				ret.append(node.toString()).append('/');
			if(ret.length() > 0)
				ret.setLength(ret.length() - 1);
			return ret.toString();
		}
	}

	/** An exception that occurs because of invalid JSON content */
	public static class ParseException extends Exception {
		private final ParseState theState;

		/**
		 * Creates a ParseException
		 *
		 * @param message The message detailing what was wrong with the JSON content
		 * @param state The state that the parsing was in when the illegal content was encountered
		 */
		public ParseException(String message, ParseState state) {
			super(state.toString() + "\n" + message);
			theState = state;
		}

		/**
		 * @return The state that the parsing was in when the illegal content was encountered
		 */
		public ParseState getParseState() {
			return theState;
		}
	}

	private boolean allowComments;

	private boolean useFormalJson;

	/** Creates a parser */
	public SAJParser() {
		allowComments = true;
	}

	/**
	 * @return Whether or not this parser allows comments (block- and line-style)
	 */
	public boolean allowsComments() {
		return allowComments;
	}

	/**
	 * @param allowed Whether this parser should allow block- and line-style comments
	 */
	public void setAllowsComments(boolean allowed) {
		allowComments = allowed;
	}

	/**
	 * @return Whether this parser will parse its content strictly
	 * @see #setFormal(boolean)
	 */
	public boolean isFormal() {
		return useFormalJson;
	}

	/**
	 * Sets whether this parser parses its content strictly or loosely. Some examples of content that would violate strict parsing but would
	 * be acceptable to a loose parser are:
	 * <ul>
	 * <li>Property names that are not enclosed in quotation marks</li>
	 * <li>Strings enclosed in tick (') marks instead of quotations</li>
	 * <li>A "+" sign before a positive number</li>
	 * <li>"Inf", "Infinity", "-Inf", "-Infinity", and "NaN" as numerical identifiers</li>
	 * <li>Octal and hexadecimal integers (prefixed by 0 and 0x, respectively)</li>
	 * <li>"undefined" as an identifier (interpreted identically to null)</li>
	 * </ul>
	 *
	 * @param formal Whether this parser should parse its content strictly
	 */
	public void setFormal(boolean formal) {
		useFormalJson = formal;
	}

	/**
	 * Parses a single JSON item from a stream
	 *
	 * @param reader The stream to parse data from
	 * @param handlers The handlers to be notified of JSON content
	 * @return The value returned from the handler after parsing has finished
	 * @throws IOException If an error occurs reading from the stream
	 * @throws ParseException If an error occurs parsing the JSON content
	 */
	public Object parse(Reader reader, ParseHandler... handlers) throws IOException, ParseException {
		StringBuilder sb = new StringBuilder();
		ParseState state = new ParseState(reader, handlers);
		boolean hadContent = false;
		int origDepth = state.getDepth();
		do {
			if(!parseNext(state, sb))
				state.error("Unexpected end of content");
			if(state.getDepth() != origDepth)
				hadContent = true;
		} while(state.getDepth() > 0 || !hadContent);
		if(handlers.length > 0)
			return handlers[0].finalValue();
		else
			return null;
	}

	/**
	 * Parses the next JSON-related item in the stream. The next item may be:
	 * <ul>
	 * <li>White space--a set of spaces, new lines, etc. that are acceptable characters that do not affect the content of a JSON document.
	 * </li>
	 * <li>A comment. Either a line comment or a block comment</li>
	 * <li>The beginning of a JSON object</li>
	 * <li>The end of a JSON object</li>
	 * <li>The beginning of a JSON array</li>
	 * <li>The end of a JSON array</li>
	 * <li>A separator. A comma between object property name/value pairs or between array elements or a colon between the name and value of
	 * an object property</li>
	 * <li>A string</li>
	 * <li>A number</li>
	 * <li>A boolean</li>
	 * <li>null</li>
	 * </ul>
	 *
	 * @param state The state to parse the next item for
	 * @return Whether there was more data in the stream
	 * @throws IOException If an error occurs reading the data from the stream
	 * @throws ParseException If the next item cannot be parsed
	 */
	public boolean parseNext(ParseState state) throws IOException, ParseException {
		return parseNext(state, new StringBuilder());
	}

	private boolean parseNext(ParseState state, StringBuilder sb) throws IOException, ParseException {
		int ch = state.nextChar();
		if(ch < 0)
			return false;
		else if(isWhiteSpace(ch))
			state.whiteSpace(parseWhiteSpace(sb, state));
		else if(ch == '{')
			state.startObject();
		else if(ch == '[')
			state.startArray();
		else if(ch == ',' || ch == ':')
			state.separate((char) ch);
		else if(ch == '}')
			state.pop(ParseToken.OBJECT);
		else if(ch == ']')
			state.pop(ParseToken.ARRAY);
		else if(ch == '/' && allowComments)
			parseComment(sb, state);
		else if(ch == '"' || ch == '\'') {
			if(state.top() != null && state.top().token == ParseToken.OBJECT)
				state.startProperty(parsePropertyName(sb, state));
			else
				parseString(state); // Parses the string as a stream
		} else {
			if(state.top() == null)
				state.setPrimitive(parsePrimitive(sb, state));
			else {
				switch (state.top().token) {
				case OBJECT:
					state.startProperty(parsePropertyName(sb, state));
					break;
				case ARRAY:
				case PROPERTY:
					state.setPrimitive(parsePrimitive(sb, state));
					break;
				}
			}
		}
		return true;
	}

	/**
	 * @param ch The character to test
	 * @return Whether the character qualifies as white space
	 */
	public static boolean isWhiteSpace(int ch) {
		return ch <= ' ';
	}

	/**
	 * @param ch The character to test
	 * @return Whether the character is a syntax token in JSON
	 */
	public static boolean isSyntax(int ch) {
		return ch == '{' || ch == '}' || ch == '[' || ch == ']' || ch == ',' || ch == ':';
	}

	static String parseWhiteSpace(StringBuilder sb, ParseState state) throws IOException {
		int ch = state.currentChar();
		do {
			sb.append((char) ch);
			ch = state.nextChar();
		} while(isWhiteSpace(ch));
		state.backUp();
		String ret = sb.toString();
		sb.setLength(0);
		return ret;
	}

	static void parseComment(StringBuilder sb, ParseState state) throws IOException, ParseException {
		int ch = state.currentChar();
		if(ch != '/')
			state.error("Invalid comment");
		ch = state.nextChar();
		sb.append('/');
		if(ch == '/') { // Line comment
			sb.append('/');
			ch = state.nextChar();
			while(ch >= 0 && !state.wasLastLine()) {
				sb.append((char) ch);
				ch = state.nextChar();
			}
			state.backUp();
			String comment = sb.toString();
			sb.delete(0, 2);
			state.comment(comment, sb.toString().trim());
			sb.setLength(0);
		} else if(ch == '*') { // Block comment
			sb.append('*');
			boolean lastStar = false;
			ch = state.nextChar();
			while(ch >= 0 && (!lastStar || ch != '/')) {
				if(ch == '*')
					lastStar = true;
				else
					lastStar = false;
				sb.append((char) ch);
				ch = state.nextChar();
			}
			if(ch < 0)
				state.backUp();
			else
				sb.append((char) ch);
			String comment = sb.toString();
			sb.delete(0, 2);
			sb.delete(sb.length() - 2, sb.length());
			/* Now we "clean up" the comment to deliver just the content. This involves removing
			 * initial and terminal white space on each line, and the initial asterisks--
			 * specifically the first continuous set of asterisks that occur. */
			boolean newLine = true;
			boolean lineHadStar = false;
			lastStar = false;
			for(int c = 0; c < sb.length(); c++) {
				ch = sb.charAt(c);
				if(newLine) {
					if(isWhiteSpace(ch)) {
						lastStar = false;
						sb.deleteCharAt(c);
						c--;
					} else if(ch == '*' && (lastStar || !lineHadStar)) {
						sb.deleteCharAt(c);
						c--;
						lineHadStar = true;
						lastStar = true;
					} else {
						lastStar = false;
						lineHadStar = false;
						newLine = false;
					}
				} else if(ch == '\n') {
					newLine = true;
					while(c > 0 && isWhiteSpace(sb.charAt(c - 1))) {
						sb.deleteCharAt(c - 1);
						c--;
					}
					if(c == 0) {
						sb.deleteCharAt(c - 1);
						c--;
					}
				}
			}
			for(int c = sb.length() - 1; c >= 0 && isWhiteSpace(sb.charAt(c)); c--)
				sb.deleteCharAt(c);
			state.comment(comment, sb.toString());
		} else
			state.error("Invalid comment");
		sb.setLength(0);
	}

	Object parsePrimitive(StringBuilder sb, ParseState state) throws IOException, ParseException {
		int ch = state.currentChar();
		if(ch == '\'' || ch == '"')
			return parseString(sb, state);
		else if(ch == '-' || ch == '+' || ch == 'I' || ch == 'N' || (ch >= '0' && ch <= '9'))
			return parseNumber(sb, state);
		else if(ch == 't' || ch == 'f')
			return parseBoolean(state);
		else if(ch == 'n' || ch == 'u')
			return parseNull(state);
		else {
			state.error("Unrecognized start of value: " + (char) ch);
			return null;
		}
	}

	void parseString(ParseState state) throws IOException, ParseException {
		int startChar = state.currentChar();
		if(useFormalJson && startChar != '"')
			state.error("Strings must be enclosed in quotations in formal JSON");
		state.startString((char) startChar);
	}

	String parseString(StringBuilder sb, ParseState state) throws IOException, ParseException {
		int startChar = state.currentChar();
		if(useFormalJson && startChar != '"')
			state.error("Strings must be enclosed in quotations in formal JSON");
		int ch = state.nextChar();
		boolean escaped = false;
		while(true) {
			if(escaped) {
				switch (ch) {
				case '"':
					sb.append('"');
					break;
				case '\'':
					sb.append('\'');
					break;
				case '\\':
					sb.append('\\');
					break;
				case '/':
					// Solidus
					sb.append('\u2044');
					break;
				case 'n':
					sb.append('\n');
					break;
				case 'r':
					sb.append('\r');
					break;
				case 't':
					sb.append('\t');
					break;
				case 'b':
					sb.append('\b');
					break;
				case 'f':
					sb.append('\f');
					break;
				case 'u':
					int unicode = 0;
					int ch2 = 0;
					int i;
					for(i = 0; i < 4; i++) {
						ch2 = state.nextChar();
						if(ch2 >= '0' && ch2 <= '9')
							unicode = (unicode << 4) | (ch2 - '0');
						else if(ch2 >= 'a' && ch2 <= 'f')
							unicode = (unicode << 4) | (ch2 - 'a' + 10);
						else if(ch2 >= 'A' && ch2 <= 'F')
							unicode = (unicode << 4) | (ch2 - 'A' + 10);
						else
							break;
					}
					sb.append(new String(Character.toChars(unicode)));
					if(i < 4) {
						ch = ch2;
						continue;
					}
					break;
				default:
					state.error(((char) ch) + " is not escapable");
				}
				escaped = false;
			} else if(ch == '\\')
				escaped = true;
			else if(ch == startChar)
				break;
			else
				sb.append((char) ch);
			ch = state.nextChar();
		}
		String ret = sb.toString();
		sb.setLength(0);
		return ret;
	}

	Number parseNumber(StringBuilder sb, ParseState state) throws IOException, ParseException {
		int ch = state.currentChar();
		boolean neg = ch == '-';
		if(neg)
			ch = state.nextChar();
		else if(ch == '+') {
			if(useFormalJson)
				state.error("Plus sign not allowed as number prefix in formal JSON");
			ch = state.nextChar();
		}
		while(isWhiteSpace(ch))
			ch = state.nextChar();
		if(ch == 'I') {
			ch = state.nextChar();
			if(ch != 'n')
				state.error("Invalid infinite");
			ch = state.nextChar();
			if(ch != 'f')
				state.error("Invalid infinite");
			ch = state.nextChar();
			if(ch == 'i') {
				ch = state.nextChar();
				if(ch != 'n')
					state.error("Invalid infinite");
				ch = state.nextChar();
				if(ch != 'i')
					state.error("Invalid infinite");
				ch = state.nextChar();
				if(ch != 't')
					state.error("Invalid infinite");
				ch = state.nextChar();
				if(ch != 'y')
					state.error("Invalid infinite");
			} else
				state.backUp();
			if(useFormalJson)
				state.error("Infinite values not allowed in formal JSON");
			return new Float(neg ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY);
		}
		if(ch == 'N') {
			ch = state.nextChar();
			if(ch != 'a')
				state.error("Invalid NaN");
			ch = state.nextChar();
			if(ch != 'N')
				state.error("Invalid NaN");
			if(useFormalJson)
				state.error("NaN not allowed in formal JSON");
			return new Float(Float.NaN);
		}
		int radix = ch == '0' ? 8 : 10;
		if(radix == 8) {
			ch = state.nextChar();
			if(ch == 'x') {
				radix = 16;
				ch = state.nextChar();
			} else if(ch < '0' || ch > '9')
				radix = 10;
		}
		if(useFormalJson && radix != 10)
			state.error("Numbers in formal JSON must be in base 10");
		// 0=whole, 1=part, 2=exp
		int numState = 0;
		String whole = null;
		String part = null;
		String expNum = null;
		boolean expNeg = false;
		char type = 0;
		while(true) {
			if(isWhiteSpace(ch)) {
				break;
			} else if(ch >= '0' && ch <= '9') {
				if(ch >= '8' && radix == 8)
					state.error("8 or 9 digit used in octal number");
				sb.append((char) ch);
				ch = state.nextChar();
			} else if(ch >= 'a' && ch <= 'f') {
				if(radix < 16) {
					if(ch == 'e') {
						if(part != null)
							state.error("Multiple exponentials in number");
						switch (numState) {
						case 0:
							whole = sb.toString();
							break;
						case 1:
							part = sb.toString();
							break;
						}
						numState = 2;
						sb.setLength(0);
						ch = state.nextChar();
					} else if(ch == 'f') {
						if(radix != 10)
							state.error("No octal floating point numbers");
						if(useFormalJson)
							state.error("Formal JSON does not allow type specification on numbers");
						switch (numState) {
						case 0:
							whole = sb.toString();
							break;
						case 1:
							part = sb.toString();
							break;
						default:
							expNum = sb.toString();
						}
						sb.setLength(0);
						type = 'f';
						ch = state.nextChar();
						break;
					} else
						state.error("Hexadecimal digits used in " + radix + "-based number");
				} else {
					if(numState > 0)
						state.error("No hexadecimal floating-point numbers");
					sb.append((char) ch);
					ch = state.nextChar();
				}
			} else if(ch >= 'A' && ch <= 'F') {
				if(radix < 16) {
					if(ch == 'E') {
						if(part != null)
							state.error("Multiple exponentials in number");
						switch (numState) {
						case 0:
							whole = sb.toString();
							break;
						case 1:
							part = sb.toString();
							break;
						}
						numState = 2;
						sb.setLength(0);
						ch = state.nextChar();
					} else if(ch == 'F') {
						if(radix != 10)
							state.error("No octal floating point numbers");
						switch (numState) {
						case 0:
							whole = sb.toString();
							break;
						case 1:
							part = sb.toString();
							break;
						default:
							expNum = sb.toString();
						}
						sb.setLength(0);
						type = 'f';
						ch = state.nextChar();
						break;
					} else
						state.error("Hexadecimal digits used in " + radix + "-based number");
				} else {
					if(numState > 0)
						state.error("No hexadecimal floating-point numbers");
					sb.append((char) ch);
					ch = state.nextChar();
				}
			} else if(ch == '-' || ch == '+') {
				if(numState != 2 || sb.length() > 0)
					state.error("Subtraction in JSON number not supported");
				expNeg = ch == '-';
				ch = state.nextChar();
			} else if(ch == '.') {
				if(radix != 10)
					state.error("No octal or hexadecimal floating point numbers");
				if(numState != 0)
					state.error("Decimal in incorrect place in number");
				numState = 1;
				whole = sb.toString();
				sb.setLength(0);
				ch = state.nextChar();
			} else if(ch == 'l' || ch == 'L') {
				switch (numState) {
				case 0:
					whole = sb.toString();
					break;
				default:
					state.error("No decimals or exponentials in long numbers");
				}
				sb.setLength(0);
				type = 'l';
				ch = state.nextChar();
				break;
			} else
				break;
		}
		state.backUp();
		if(sb.length() > 0) {
			switch (numState) {
			case 0:
				whole = sb.toString();
				break;
			case 1:
				part = sb.toString();
				break;
			default:
				expNum = sb.toString();
			}
		}
		sb.setLength(0);
		if(part != null || expNum != null || numState > 0 || type == 'f') {
			double ret = 0;
			String max = Long.toString(Long.MAX_VALUE, radix);
			if(testMax(whole, max))
				state.error("Number size greater than maximum");
			if(testMax(part, max))
				state.error("Number size greater than maximum");
			if(testMax(expNum, max))
				state.error("Number size greater than maximum");
			if(whole != null && whole.length() > 0)
				ret = Long.parseLong(whole, radix);
			if(part != null && part.length() > 0)
				ret += Long.parseLong(part) * powNeg10(part.length());
			if(expNum != null && expNum.length() > 0) {
				if(!expNeg)
					ret *= pow10((int) Long.parseLong(expNum));
				else
					ret /= pow10((int) Long.parseLong(expNum));
			}
			if(type == 'f')
				return Float.valueOf(neg ? -(float) ret : (float) ret);
			else
				return Double.valueOf(neg ? -ret : ret);
		} else {
			if(!testMax(whole, Integer.toString(Integer.MAX_VALUE, radix))) {
				int ret;
				if(whole != null && whole.length() > 0)
					ret = Integer.parseInt(whole, radix);
				else
					ret = 0;
				return Integer.valueOf(neg ? -ret : ret);
			} else {
				if(testMax(whole, Long.toString(Long.MAX_VALUE, radix)))
					state.error("Number size greater than maximum");
				long ret;
				if(whole != null && whole.length() > 0)
					ret = Long.parseLong(whole, radix);
				else
					ret = 0;
				return Long.valueOf(neg ? -ret : ret);
			}
		}
	}

	private static boolean testMax(String num, String max) {
		if(num == null || num.length() < max.length())
			return false;
		if(num.length() > max.length())
			return true;
		if(num.compareToIgnoreCase(max) > 0)
			return true;
		return false;
	}

	private static double powNeg10(int pow) {
		double ret = 1;
		for(; pow > 0; pow--)
			ret /= 10;
		return ret;
	}

	private static double pow10(int pow) {
		double ret = 1;
		for(; pow > 0; pow--)
			ret *= 10;
		return ret;
	}

	static Boolean parseBoolean(ParseState state) throws IOException, ParseException {
		Boolean ret;
		int ch = state.currentChar();
		if(ch == 't') {
			ch = state.nextChar();
			if(ch != 'r')
				state.error("Invalid true boolean");
			ch = state.nextChar();
			if(ch != 'u')
				state.error("Invalid true boolean");
			ch = state.nextChar();
			if(ch != 'e')
				state.error("Invalid true boolean");
			ret = Boolean.TRUE;
		} else if(ch == 'f') {
			ch = state.nextChar();
			if(ch != 'a')
				state.error("Invalid false boolean");
			ch = state.nextChar();
			if(ch != 'l')
				state.error("Invalid false boolean");
			ch = state.nextChar();
			if(ch != 's')
				state.error("Invalid false boolean");
			ch = state.nextChar();
			if(ch != 'e')
				state.error("Invalid false boolean");
			ret = Boolean.FALSE;
		} else {
			state.error("Invalid boolean");
			return null;
		}
		return ret;
	}

	Object parseNull(ParseState state) throws IOException, ParseException {
		int ch = state.currentChar();
		if(ch == 'n') {
			ch = state.nextChar();
			if(ch != 'u')
				state.error("Invalid null");
			ch = state.nextChar();
			if(ch != 'l')
				state.error("Invalid null");
			ch = state.nextChar();
			if(ch != 'l')
				state.error("Invalid null");
		} else if(ch == 'u') {
			ch = state.nextChar();
			if(ch != 'n')
				state.error("Invalid null");
			ch = state.nextChar();
			if(ch != 'd')
				state.error("Invalid null");
			ch = state.nextChar();
			if(ch != 'e')
				state.error("Invalid null");
			ch = state.nextChar();
			if(ch != 'f')
				state.error("Invalid null");
			ch = state.nextChar();
			if(ch != 'i')
				state.error("Invalid null");
			ch = state.nextChar();
			if(ch != 'n')
				state.error("Invalid null");
			ch = state.nextChar();
			if(ch != 'e')
				state.error("Invalid null");
			ch = state.nextChar();
			if(ch != 'd')
				state.error("Invalid null");
			if(useFormalJson)
				state.error("undefined is not a valid identifier in formal JSON");
		} else
			state.error("Invalid null");
		return null;
	}

	String parsePropertyName(StringBuilder sb, ParseState state) throws IOException, ParseException {
		int ch = state.currentChar();
		int isQuoted = 0;
		if(ch == '\'')
			isQuoted = 1;
		if(ch == '"')
			isQuoted = 2;
		if(isQuoted > 0)
			return parseString(sb, state);
		else if(useFormalJson)
			state.error("Property names must be quoted in formal JSON");
		boolean escaped = false;
		while(true) {
			if(escaped) {
				switch (ch) {
				case '"':
					sb.append('"');
					break;
				case '\'':
					sb.append('\'');
					break;
				case '\\':
					sb.append('\\');
					break;
				case '/':
					// Solidus
					sb.append('\u2044');
					break;
				case 'n':
					sb.append('\n');
					break;
				case 'r':
					sb.append('\r');
					break;
				case 't':
					sb.append('\t');
					break;
				case 'b':
					sb.append('\b');
					break;
				case 'f':
					sb.append('\f');
					break;
				case 'u':
					int unicode = 0;
					int ch2 = 0;
					int i;
					for(i = 0; i < 4; i++) {
						ch2 = state.nextChar();
						if(ch2 >= '0' && ch2 <= '9')
							unicode = unicode << 4 + (ch2 - '0');
						else if(ch2 >= 'a' && ch2 <= 'f')
							unicode = unicode << 4 + (ch2 - 'a' + 10);
						else if(ch2 >= 'A' && ch2 <= 'F')
							unicode = unicode << 4 + (ch2 - 'A' + 10);
						else
							break;
					}
					sb.append(new String(Character.toChars(unicode)));
					if(i < 4) {
						ch = ch2;
						continue;
					}
					break;
				default:
					state.error(((char) ch) + " is not escapable");
				}
			} else if(ch == '\\')
				escaped = true;
			else if(isSyntax(ch) || isWhiteSpace(ch))
				break; // Finished--not valid for unquoted property
			else
				sb.append((char) ch);
			ch = state.nextChar();
		}
		state.backUp();
		String ret = sb.toString();
		sb.setLength(0);
		return ret;
	}

	/**
	 * Parses the first JSON value from a stream. The stream is only read up to the last character of the first JSON value in the stream.
	 *
	 * @param reader The stream to parse
	 * @return The first JSON value in the stream. May be null if that is the first value in the stream. Otherwise the value will be an
	 *         instance of:
	 *         <ul>
	 *         <li>{@link JsonObject}</li>
	 *         <li>{@link List}</li>
	 *         <li>{@link java.lang.String}</li>
	 *         <li>{@link java.lang.Number}</li>
	 *         <li>{@link java.lang.Boolean}</li>
	 *         </ul>
	 * @throws IOException If the stream cannot be read
	 * @throws ParseException If the contents of the stream cannot be parsed
	 */
	public static Object parse(Reader reader) throws IOException, ParseException {
		return new SAJParser().parse(reader, new DefaultHandler());
	}

	/**
	 * Parses the first JSON value from a string. Further content in the string is ignored.
	 *
	 * @param string The string to parse
	 * @return The first JSON value in the stream. May be null if that is the first value in the string. Otherwise the value will be an
	 *         instance of:
	 *         <ul>
	 *         <li>{@link JsonObject}</li>
	 *         <li>{@link List}</li>
	 *         <li>{@link java.lang.String}</li>
	 *         <li>{@link java.lang.Number}</li>
	 *         <li>{@link java.lang.Boolean}</li>
	 *         </ul>
	 * @throws ParseException If the string's contents cannot be parsed
	 */
	public static Object parse(String string) throws ParseException {
		try {
			return parse(new java.io.StringReader(string));
		} catch(IOException e) {
			throw new IllegalStateException("IO Exception thrown from StringReader?!!", e);
		}
	}
}

/*
 * JsonSerialReader.java Created Sep 15, 2011 by Andrew Butler, PSL
 */
package org.qommons.json;

import java.io.IOException;
import java.io.Reader;

import org.qommons.json.SAJParser.ParseException;
import org.qommons.json.SAJParser.ParseNode;
import org.qommons.json.SAJParser.ParseState;
import org.qommons.json.SAJParser.ParseToken;

/**
 * <p>
 * A JsonSerialReader provides a way of navigating sequentially through a JSON document in a more flexible way than implementing a
 * {@link org.qommons.json.SAJParser.ParseHandler} and using
 * {@link SAJParser#parse(java.io.Reader, org.qommons.json.SAJParser.ParseHandler...)}.
 * </p>
 * <p>
 * The navigation methods of this class always move forward. If an item is skipped during a navigation operation, that item cannot be
 * retrieved through that instance of this class.
 * </p>
 */
public class JsonSerialReader
{
	/** The type of an item parsed from a JSON stream */
	public static enum JsonParseType
	{
		/** Represents some white space which has no effect on the JSON content */
		WHITESPACE,
		/** Represents either a line comment started with // or a block comment beginning with /* (slash-star) and ending with star-slash */
		COMMENT,
		/**
		 * Represents a comma between elements in an array, a colon between the name and value of an object property, or a comma between
		 * name/value pairs in an object
		 */
		SEPARATOR,
		/** Represents either the beginning ('{') or end ('}') of a JSON object */
		OBJECT,
		/** Represents either the beginning ('[') or end (']') of a JSON array */
		ARRAY,
		/** Represents the beginning of a property in a JSON object */
		PROPERTY,
		/**
		 * Represents a String, Number, Boolean, or null as a property value in a JSON object, an element in an array, or a standalone value
		 */
		PRIMITIVE;
	}

	/** A JsonParseItem is an item parsed from a JSON stream */
	public static interface JsonParseItem
	{
		/** @return The type of this item */
		public JsonParseType getType();
	}

	/** Represents white space which has no effect on the JSON content */
	public static class WhiteSpace implements JsonParseItem
	{
		private final String theContent;

		/** @param content The white space sequence */
		public WhiteSpace(String content)
		{
			theContent = content;
		}

		@Override
		public JsonParseType getType()
		{
			return JsonParseType.WHITESPACE;
		}

		/** @return The white space sequence */
		public String getContent()
		{
			return theContent;
		}
	}

	/** Represents either a line comment started with // or a block comment beginning with /* (slash-star) and ending with star-slash */
	public static class Comment implements JsonParseItem
	{
		private final String theContent;

		private final String theFullComment;

		/**
		 * @param content The content of the comment, excluding commenting characters
		 * @param fullComment The full text of the comment
		 */
		public Comment(String content, String fullComment)
		{
			theContent = content;
			theFullComment = fullComment;
		}

		@Override
		public JsonParseType getType()
		{
			return JsonParseType.COMMENT;
		}

		/** @return Whether this comment is a block comment or a line comment */
		public boolean isBlock()
		{
			return theFullComment.startsWith("/*");
		}

		/** @return The content of the comment, excluding commenting characters */
		public String getContent()
		{
			return theContent;
		}

		/** @return The full text of the comment */
		public String getFullComment()
		{
			return theFullComment;
		}
	}

	/**
	 * Represents a comma between elements in an array, a colon between the name and value of an object property, or a comma between
	 * name/value pairs in an object
	 */
	public static class Separator implements JsonParseItem
	{
		private final ParseToken theSepType;

		/** @param sepType The type of separator that this item represents */
		public Separator(ParseToken sepType)
		{
			theSepType = sepType;;
		}

		@Override
		public JsonParseType getType()
		{
			return JsonParseType.SEPARATOR;
		}

		/**
		 * @return The type of separator that this item represents:
		 *         <ul>
		 *         <li>{@link org.qommons.json.SAJParser.ParseToken#OBJECT} For a comma between properties of an object</li>
		 *         <li>{@link org.qommons.json.SAJParser.ParseToken#PROPERTY} For a colon between the name and value of an object property</li>
		 *         <li>{@link org.qommons.json.SAJParser.ParseToken#OBJECT} For a comma between elements of an array</li>
		 *         </ul>
		 */
		public ParseToken getSeparatorType()
		{
			return theSepType;
		}
	}

	/** A tagging interface that marks an item as important to the JSON data structure */
	public static interface JsonContentItem extends JsonParseItem
	{
	}

	/** Represents a JSON object or array */
	public static abstract class StructItem implements JsonContentItem
	{
		private final boolean isBegin;

		/** @param begin Whether this item represents the beginning or end of the structure */
		public StructItem(boolean begin)
		{
			isBegin = begin;
		}

		/** @return Whether this item represents the beginning or end of the structure */
		public boolean isBegin()
		{
			return isBegin;
		}
	}

	/** Represents either the beginning ('{') or end ('}') of a JSON object */
	public static class ObjectItem extends StructItem
	{
		/** @param begin Whether this item represents the beginning or end of the structure */
		public ObjectItem(boolean begin)
		{
			super(begin);
		}

		@Override
		public JsonParseType getType()
		{
			return JsonParseType.OBJECT;
		}
	}

	/** Represents either the beginning ('[') or end (']') of a JSON array */
	public static class ArrayItem extends StructItem
	{
		/** @param begin Whether this item represents the beginning or end of the structure */
		public ArrayItem(boolean begin)
		{
			super(begin);
		}

		@Override
		public JsonParseType getType()
		{
			return JsonParseType.ARRAY;
		}
	}

	/** Represents the beginning of a property in a JSON object */
	public static class PropertyItem implements JsonContentItem
	{
		private final String theName;

		/** @param name The name of the property that was parsed */
		public PropertyItem(String name)
		{
			theName = name;
		}

		@Override
		public JsonParseType getType()
		{
			return JsonParseType.PROPERTY;
		}

		/** @return The name of the property that was parsed */
		public String getName()
		{
			return theName;
		}
	}

	/** epresents a String, Number, Boolean, or null as a property value in a JSON object, an element in an array, or a standalone value */
	public static class PrimitiveItem implements JsonContentItem
	{
		private Object theValue;

		/** @param value The value that was parsed */
		public PrimitiveItem(Object value)
		{
			theValue = value;
		}

		@Override
		public JsonParseType getType()
		{
			return JsonParseType.PRIMITIVE;
		}

		/** @return The value that was parsed */
		public Object getValue()
		{
			return theValue;
		}
	}

	/** Represents a saved state that may be referred to later */
	public static class StructState
	{
		private ParseNode [] thePath;

		private StructState(ParseNode [] path)
		{
			thePath = path;
		}

		StructState(ParseState state, ParseNode... add)
		{
			ParseNode top;
			if(add.length > 0)
				top = add[add.length - 1];
			else
				top = state.top();
			if(top == null || (top.token != ParseToken.OBJECT && top.token != ParseToken.ARRAY))
				throw new IllegalArgumentException("State can only be saved for an object or array");

			thePath = new ParseNode [state.getDepth() + add.length];
			int i;
			for(i = 0; i < state.getDepth(); i++)
				thePath[state.getDepth() - i - 1] = state.fromTop(i);
			for(; i < thePath.length; i++)
				thePath[i] = add[i - state.getDepth()];
		}

		/** @return The parent state of this state */
		public StructState getParent()
		{
			if(thePath.length == 0)
				return null;
			ParseNode [] newPath = new ParseNode [thePath.length - 1];
			System.arraycopy(thePath, 0, newPath, 0, newPath.length);
			return new StructState(newPath);
		}

		/** @return The depth of this state */
		public int getDepth()
		{
			return thePath.length;
		}

		/** @return The top parse node of this state. Will be of type {@link ParseToken#OBJECT object} or {@link ParseToken#ARRAY array} */
		public ParseNode top()
		{
			return thePath[thePath.length - 1];
		}

		/**
		 * @param depth The depth to get the node of
		 * @return The parse node of this state <code>depth</code> deep
		 */
		public ParseNode fromTop(int depth)
		{
			if(thePath.length <= depth)
				return null;
			return thePath[thePath.length - depth - 1];
		}

		/** @return this state's path */
		public ParseNode [] getPath()
		{
			return thePath;
		}

		@Override
		public String toString()
		{
			StringBuilder ret = new StringBuilder();
			for(ParseNode node : thePath)
				ret.append(node.toString()).append('/');
			if(ret.length() > 0)
				ret.setLength(ret.length() - 1);
			return ret.toString();
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof StructState && org.qommons.ArrayUtils.equals(thePath, ((StructState) o).thePath);
		}

		@Override
		public int hashCode()
		{
			return org.qommons.ArrayUtils.hashCode(thePath);
		}

		/**
		 * @param state The state to check
		 * @return Whether this state matches the given state exactly
		 */
		public boolean matches(ParseState state)
		{
			if(state.getDepth() != thePath.length)
				return false;
			return isStartOf(state);
		}

		/**
		 * @param state The state to check
		 * @return Whether the given state begins with this state
		 */
		public boolean isStartOf(ParseState state)
		{
			if(state.getDepth() < thePath.length)
				return false;
			for(int i = 0; i < thePath.length; i++)
				if(!thePath[i].equals(state.fromTop(thePath.length - i - 1)))
					return false;
			return true;
		}
	}

	/** Allows the JSON serial reader class to capture parsed data selectively */
	public static class ToggleHandler extends org.qommons.json.SAJParser.DefaultHandler
	{
		private Boolean theMode;

		private boolean isStringAsReader;

		private boolean isSep;

		private boolean isWhitespace;

		private boolean isComment;

		private boolean isNull;

		private String theContent;

		private String theFullComment;

		private Reader theStringReader;

		/**
		 * @param mode The mode that this handler should be in:
		 *        <ul>
		 *        <li>null - Off. This handler will ignore any calls it receives</li>
		 *        <li>false - Partial. This handler will accept metadata, but no full content except primitives</li>
		 *        <li>true - On. This handler accepts all input</li>
		 *        </ul>
		 */
		public void setMode(Boolean mode)
		{
			theMode = mode;
		}

		/**
		 * @param stringAsReader Whether, when strings are encountered, to store them in the stringReader field for later retrieval, or to
		 *        parse them as normal string objects
		 */
		public void setStringAsReader(boolean stringAsReader)
		{
			isStringAsReader = stringAsReader;
		}

		/** @return The mode that this handler is in */
		public Boolean getMode()
		{
			return theMode;
		}

		/** @return Whether the last parsed entity was a separator */
		public boolean isSeparator()
		{
			return isSep;
		}

		/** @return Whether the last parsed entity was whitespace */
		public boolean isWhitespace()
		{
			return isWhitespace;
		}

		/** @return Whether the last parsed entity was a comment */
		public boolean isComment()
		{
			return isComment;
		}

		/** @return Whether the last parsed entity was the null value */
		public boolean isNull()
		{
			return isNull;
		}

		/** @return The reader for the string just encountered */
		public Reader getString()
		{
			return theStringReader;
		}

		/** @return The value of the last white space or comment parsed */
		public String getContent()
		{
			return theContent;
		}

		/** @return The full text of the last comment parsed */
		public String getFullComment()
		{
			return theFullComment;
		}

		private void clearNonContent()
		{
			isSep = false;
			isWhitespace = false;
			isComment = false;
			isNull = false;
			theContent = null;
			theFullComment = null;
			theStringReader = null;
		}

		@Override
		public void startObject(ParseState state)
		{
			clearNonContent();
			if(Boolean.TRUE.equals(theMode))
				super.startObject(state);
		}

		@Override
		public void startProperty(ParseState state, String name)
		{
			clearNonContent();
			if(Boolean.TRUE.equals(theMode))
				super.startProperty(state, name);
		}

		@Override
		public void endProperty(ParseState state, String propName)
		{
			clearNonContent();
			if(Boolean.TRUE.equals(theMode))
				super.endProperty(state, propName);
		}

		@Override
		public void endObject(ParseState state)
		{
			clearNonContent();
			if(Boolean.TRUE.equals(theMode) && getDepth() > 0)
				super.endObject(state);
		}

		@Override
		public void startArray(ParseState state)
		{
			clearNonContent();
			if(Boolean.TRUE.equals(theMode))
				super.startArray(state);
		}

		@Override
		public void endArray(ParseState state)
		{
			clearNonContent();
			if(Boolean.TRUE.equals(theMode) && getDepth() > 0)
				super.endArray(state);
		}

		@Override
		public void valueString(ParseState state, java.io.Reader value) throws IOException
		{
			if(theMode != null)
			{
				clearNonContent();
				if(isStringAsReader)
				{
					theStringReader = value;
					super.valueString(state, "");
				}
				else
					super.valueString(state, value);
			}
		}

		@Override
		public void valueNumber(ParseState state, Number value)
		{
			if(theMode != null)
			{
				super.valueNumber(state, value);
				clearNonContent();
			}
		}

		@Override
		public void valueBoolean(ParseState state, boolean value)
		{
			if(theMode != null)
			{
				super.valueBoolean(state, value);
				clearNonContent();
			}
		}

		@Override
		public void valueNull(ParseState state)
		{
			if(theMode != null)
			{
				super.valueNull(state);
				clearNonContent();
				isNull = true;
			}
		}

		@Override
		public void separator(ParseState state)
		{
			if(theMode != null)
			{
				super.separator(state);
				clearNonContent();
				isSep = true;
			}
		}

		@Override
		public void whiteSpace(ParseState state, String ws)
		{
			if(theMode != null)
			{
				super.whiteSpace(state, ws);
				clearNonContent();
				isWhitespace = true;
				theContent = ws;
			}
		}

		@Override
		public void comment(ParseState state, String fullComment, String content)
		{
			if(theMode != null)
			{
				super.comment(state, fullComment, content);
				clearNonContent();
				isComment = true;
				theContent = content;
				theFullComment = fullComment;
			}
		}

		@Override
		public void reset()
		{
			super.reset();
			clearNonContent();
		}
	}

	/**
	 * Returned from method in this reader for which <code>null</code> means that no more data is left when the value "null" is actually in
	 * the stream.
	 */
	public static final Object NULL = new Object();

	private SAJParser theParser;

	private ToggleHandler theHandler;

	private SAJParser.ParseState theState;

	private StructItem theLastEnded;

	private StructState theLastState;

	private boolean needsPastSep;

	/**
	 * Creates a serial reader
	 * 
	 * @param reader The input reader to parse the JSON from
	 */
	public JsonSerialReader(java.io.Reader reader)
	{
		theParser = new SAJParser();
		theHandler = new ToggleHandler();
		theState = new SAJParser.ParseState(reader, theHandler);
	}

	/** @return The current state of parsing */
	public ParseState getState()
	{
		return theState;
	}

	/**
	 * Parses the next item in the stream. If <code>jsonOnly</code> is true, this method will skip over items that do not affect the actual
	 * JSON content and the returned item will be of type {@link JsonParseType#OBJECT}, {@link JsonParseType#ARRAY},
	 * {@link JsonParseType#PROPERTY}, or {@link JsonParseType#PRIMITIVE}. Otherwise any of the JSON types may be returned.
	 * 
	 * @param jsonOnly Whether to skip over non-JSON content items
	 * @param stringAsReader Whether to return string in the JSON structure as readers instead of String objects
	 * @return The next item in the stream, or null if the stream's content is exhausted
	 * @throws IOException If an error occurs reading the data from the stream
	 * @throws ParseException If the next item cannot be parsed in context
	 */
	public JsonParseItem getNextItem(boolean jsonOnly, boolean stringAsReader) throws IOException, ParseException
	{
		return getNextItem(jsonOnly, stringAsReader, false);
	}

	private JsonParseItem getNextItem(boolean jsonOnly, boolean stringAsReader, boolean calledInternal) throws IOException, ParseException
	{
		needsPastSep = false;
		if(theLastEnded != null)
		{
			JsonParseItem ret = theLastEnded;
			theLastEnded = null;
			theLastState = null;
			if(calledInternal)
				return ret;
		}
		theHandler.setStringAsReader(stringAsReader);
		boolean preOff = theHandler.getMode() == null;
		if(preOff)
			theHandler.setMode(Boolean.FALSE);
		try
		{
			Object preValue = theHandler.finalValue();
			org.qommons.json.SAJParser.ParseNode top;
			do
			{
				top = theState.top();
				if(!theParser.parseNext(theState))
					return null;
			} while(jsonOnly && (top == theState.top() || theHandler.isSeparator()) && theHandler.finalValue() == preValue
				&& !theHandler.isNull());

			if(theHandler.isNull())
				return new PrimitiveItem(null);
			else if(theHandler.getString() != null)
			{
				if(stringAsReader)
					return new PrimitiveItem(theHandler.getString());
				else
				{
					StringBuilder contents = new StringBuilder();
					int read = theHandler.getString().read();
					while(read >= 0)
					{
						contents.append((char) read);
						read = theHandler.getString().read();
					}
					return new PrimitiveItem(contents.toString());
				}
			}
			else if(theHandler.finalValue() != preValue)
				return new PrimitiveItem(theHandler.finalValue());
			else if(theHandler.isSeparator())
				return new Separator(top.token);
			else if(top != theState.top())
			{
				if(top == null || theState.getDepth() > 1 && theState.fromTop(1) == top)
				{ // Beginning of a new item
					switch(theState.top().token)
					{
					case OBJECT:
						return new ObjectItem(true);
					case ARRAY:
						return new ArrayItem(true);
					case PROPERTY:
						return new PropertyItem(theState.top().getPropertyName());
					}
					throw new IllegalStateException("Unrecognized parse token: " + theState.top());
				}
				else
				{
					switch(top.token)
					{
					case OBJECT: {
						ObjectItem ret = new ObjectItem(false);
						theLastEnded = ret;
						theLastState = new StructState(theState, new ParseNode(ParseToken.OBJECT));
						return ret;
					}
					case ARRAY: {
						ArrayItem ret = new ArrayItem(false);
						theLastEnded = ret;
						theLastState = new StructState(theState, new ParseNode(ParseToken.ARRAY));
						return ret;
					}
					case PROPERTY:
						ObjectItem ret = new ObjectItem(false);
						theLastEnded = ret;
						theLastState = new StructState(theState, new ParseNode(ParseToken.OBJECT));
						return ret;
					}
					throw new IllegalStateException("Unrecognized parse token: " + theState.top());
				}
			}
			else if(theHandler.isWhitespace())
				return new WhiteSpace(theHandler.getContent());
			else if(theHandler.isComment())
				return new Comment(theHandler.getContent(), theHandler.getFullComment());
			else
				throw new AssertionError("Unable to determine what was parsed");
		} finally
		{
			if(preOff)
			{
				theHandler.setMode(null);
				theHandler.reset();
			}
		}
	}

	/**
	 * @return The current state of this reader, to use with {@link #endObject(StructState)} or {@link #endArray(StructState)}
	 * @throws IllegalArgumentException If the current state is not an object or array
	 */
	public StructState save()
	{
		return new StructState(theState);
	}

	/**
	 * Parses through the start of the next item, which must be an object
	 * 
	 * @return The state of the object that was started. This object may be ended with {@link #endObject(StructState)}.
	 * @throws IOException If an error occurs reading the data from the stream
	 * @throws ParseException If the next item in the stream is not an object
	 */
	public StructState startObject() throws IOException, ParseException
	{
		if(theState.top() != null && theState.top().token == ParseToken.OBJECT)
			throw new IllegalStateException("The current state is an object. The next item cannot be an object");
		JsonParseItem item = getNextItem(true, false, true);
		if(item == null)
			theState.error("Unexpected end of content");
		if(item instanceof ObjectItem && ((ObjectItem) item).isBegin())
			return new StructState(theState);
		else
			throw new ParseException("An object was not next in the stream", theState);
	}

	/**
	 * Gets the name of the next property in an object
	 * 
	 * @return The name of the next property in the current object, or null if the object has no more properties
	 * @throws IllegalStateException If the top of this parser's state is not an on object or property
	 * @throws IOException If an error occurs reading the data from the stream
	 * @throws ParseException If an error occurs parsing to the next property
	 */
	public String getNextProperty() throws IOException, ParseException
	{
		if(theLastEnded instanceof ObjectItem)
			return null;
		parsePastPropertySeparator();
		if(theState.top() == null || (theState.top().token != ParseToken.OBJECT && theState.top().token != ParseToken.PROPERTY))
			throw new IllegalStateException("The current state is not in an object.");
		int preDepth = theState.getDepth();
		if(theState.top().token == ParseToken.PROPERTY)
			preDepth--;
		JsonParseItem item;
		do
		{
			item = getNextItem(true, false, true);
			if(item == null)
				theState.error("Unexpected end of content");
			if(item instanceof PropertyItem)
				return ((PropertyItem) item).getName();

		} while(theState.getDepth() >= preDepth);
		return null;
	}

	/**
	 * Goes to the value of the given property within the current object. Note that all methods in this class, including this one, only
	 * navigate forward. If the given property has been passed already, this method will leave the stream position at the end of the current
	 * object and will return false.
	 * 
	 * @param name The name of the property to go to
	 * @return Whether the property exists in the current object
	 * @throws IOException If an error occurs reading the stream between the current stream position and the value of the given property, or
	 *         the end of the current object if the property cannot be found
	 * @throws ParseException If an error occurs parsing the data between the current stream position and the value of the given property,
	 *         or the end of the current object if the property cannot be found
	 */
	public boolean goToProperty(String name) throws IOException, ParseException
	{
		String propName = getNextProperty();
		while(propName != null && !propName.equals(name))
			propName = getNextProperty();
		return propName != null;
	}

	/**
	 * Skips to the end of the current object
	 * 
	 * @param state The object state to end, or null to end the current object
	 * @return The number of properties skipped over in the object
	 * 
	 * @throws IllegalStateException If the top of this parser's state is not an object or property
	 * @throws IOException If an error occurs reading the data from the stream
	 * @throws ParseException If the data between the current location and the end of the object cannot be parsed
	 */
	public int endObject(StructState state) throws IOException, ParseException
	{
		int ret = 0;
		if(state != null)
		{
			if(state.equals(theLastState))
			{
				theLastEnded = null;
				theLastState = null;
				return ret;
			}
			if(state.top().token != ParseToken.OBJECT)
				throw new IllegalArgumentException("The given state is not of an object");
			// Make sure we're still in the object that the state points to
			if(theState.getDepth() < state.getPath().length)
				throw new IllegalArgumentException(state + " has already been parsed past");
			for(int i = 0; i < state.getPath().length; i++)
				if(!theState.fromTop(theState.getDepth() - i - 1).equals(state.getPath()[i]))
					throw new IllegalArgumentException(state + " has already been parsed past");
			// Parse to the level of the object that the state points to
			while(theState.getDepth() > state.getPath().length)
			{
				JsonParseItem item = getNextItem(false, false, true);
				if(item == null)
					theState.error("Unexpected end of content");
				if(item instanceof PropertyItem)
					ret++;
			}
			if(theState.getDepth() < state.getPath().length)
			{
				theLastEnded = null;
				theLastState = null;
				return ret;
			}
		}
		if(theLastEnded instanceof ObjectItem)
		{
			theLastEnded = null;
			theLastState = null;
			return ret;
		}
		if(theState.top() == null || (theState.top().token != ParseToken.OBJECT && theState.top().token != ParseToken.PROPERTY))
			throw new IllegalStateException("The current state is not in an object: " + theState);
		int preDepth = theState.getDepth();
		if(theState.top().token == ParseToken.PROPERTY)
			preDepth--;
		JsonParseItem item;
		do
		{
			item = getNextItem(true, false);
			if(item == null)
				theState.error("Unexpected end of content");
			if(item instanceof PropertyItem && theState.getDepth() == preDepth + 1)
				ret++;
		} while(theState.getDepth() >= preDepth);
		return ret;
	}

	/**
	 * Parses through the start of the next item, which must be an array
	 * 
	 * @return The state of the array that was started. This array may be ended with {@link #endArray(StructState)}.
	 * @throws IOException If an error occurs reading the data from the stream
	 * @throws ParseException If the next item in the stream is not an array
	 */
	public StructState startArray() throws IOException, ParseException
	{
		if(theState.top() != null && theState.top().token == ParseToken.OBJECT)
			throw new IllegalStateException("The current state is an object. The next item cannot be an array.");
		JsonParseItem item = getNextItem(true, false, true);
		if(item == null)
			theState.error("Unexpected end of content");
		if(item instanceof ArrayItem && ((ArrayItem) item).isBegin())
			return new StructState(theState);
		else
			throw new ParseException("An array was not next in the stream", theState);
	}

	/**
	 * Skips to the end of the current array
	 * 
	 * @param state The array state to end, or null to end the current array
	 * @return The number of elements skipped over in the array
	 * @throws IllegalStateException If the top of this parser's state is not an array
	 * @throws IOException If an error occurs reading the data from the stream
	 * @throws ParseException If the data between the current location and the end of the array cannot be parsed
	 */
	public int endArray(StructState state) throws IOException, ParseException
	{
		parsePastPropertySeparator();
		int ret = 0;
		if(state != null)
		{
			if(state.equals(theLastState))
			{
				theLastEnded = null;
				theLastState = null;
				return ret;
			}
			if(state.top().token != ParseToken.OBJECT)
				throw new IllegalArgumentException("The given state is not of an array");
			// Make sure we're still in the array that the state points to
			if(theState.getDepth() < state.getPath().length)
				throw new IllegalArgumentException(state + " has already been parsed past");
			for(int i = 0; i < state.getPath().length; i++)
				if(!theState.fromTop(theState.getDepth() - i - 1).equals(state.getPath()[i]))
					throw new IllegalArgumentException(state + " has already been parsed past");
			// Parse to the level of the array that the state points to
			while(theState.getDepth() > state.getPath().length)
			{
				JsonParseItem item = getNextItem(false, false, true);
				if(item == null)
					theState.error("Unexpected end of content");
				if(item instanceof JsonContentItem && state.getDepth() == state.getPath().length)
					ret++;
			}
			if(theState.getDepth() < state.getPath().length)
			{
				theLastEnded = null;
				theLastState = null;
				return ret;
			}
		}
		if(theLastEnded instanceof ArrayItem)
		{
			theLastEnded = null;
			theLastState = null;
			return ret;
		}
		if(theState.top() == null || theState.top().token != ParseToken.ARRAY)
			throw new IllegalStateException("The current state is not in an array");
		int preDepth = theState.getDepth();
		JsonParseItem item;
		do
		{
			item = getNextItem(true, false);
			if(item == null)
				theState.error("Unexpected end of content");
			if(theState.getDepth() == preDepth + 1 && item instanceof StructItem && ((StructItem) item).isBegin())
				ret++;
			else if(theState.getDepth() == preDepth && item instanceof PrimitiveItem)
				ret++;
		} while(theState.getDepth() >= preDepth);
		return ret;
	}

	/**
	 * Parses the next element in an array, value of a property in an object, or value in a stream
	 * 
	 * @param stringAsReader Whether, if the next element in the array or object is a string, to return a Reader or a String object
	 * @return The value of the current property or the next element in the array. May be an instance of {@link org.json.simple.JSONObject},
	 *         {@link org.json.simple.JSONArray}, Number, String, Boolean, or may be {@link #NULL} if the next parsed value is null. An
	 *         actual null will be returned if the last element of the array has already been parsed.
	 * @throws IllegalStateException If the top of this parser's state is not either an array or an object property
	 * @throws IOException If an error occurs reading the data from the stream
	 * @throws ParseException If the element cannot be parsed
	 */
	public Object parseNext(boolean stringAsReader) throws IOException, ParseException
	{
		if(theLastEnded != null)
			return null;
		parsePastPropertySeparator();
		if(theState.top() != null && theState.top().token != ParseToken.ARRAY && theState.top().token != ParseToken.PROPERTY)
			throw new IllegalStateException("The current state is not an array or object property");

		Object ret = null;
		theHandler.setMode(Boolean.TRUE);
		try
		{
			JsonParseItem item = getNextItem(true, stringAsReader, true);
			if(item == null)
				return null;
			switch(item.getType())
			{
			case WHITESPACE:
			case COMMENT:
			case SEPARATOR:
				throw new IllegalStateException("Should not have returned non-JSON content");
			case PROPERTY:
				throw new IllegalStateException("State was not an object--should not have returned a property");
			case OBJECT:
				if(((StructItem) item).isBegin())
				{
					endObject(null);
					ret = theHandler.finalValue();
				}
				break;
			case ARRAY:
				if(((StructItem) item).isBegin())
				{
					endArray(null);
					ret = theHandler.finalValue();
				}
				break;
			case PRIMITIVE:
				ret = ((PrimitiveItem) item).getValue();
				if(ret == null)
					ret = NULL;
				needsPastSep = true;
			}
		} finally
		{
			theHandler.setMode(null);
			theHandler.reset();
		}
		return ret;
	}

	private void parsePastPropertySeparator() throws IOException, ParseException
	{
		if(!needsPastSep || theState.top() == null || theState.top().token != ParseToken.PROPERTY)
			return;
		needsPastSep = false;

		JsonParseItem item;
		do
		{
			item = getNextItem(false, false, true);
			if(item instanceof ObjectItem && !((ObjectItem) item).isBegin())
				break;
			if(item instanceof JsonContentItem)
				throw new IllegalStateException("Should not encounter content before the property separator");
		} while(!(item instanceof Separator));
	}

	/**
	 * Same as {@link #parseNext(boolean)}, but asserting that the next element, if present, is a JSON object. If the current state is an
	 * array and there are no elements left, this method will return null. Note that if the next item is a null value, this method will
	 * throw an {@link IllegalStateException}.
	 * 
	 * 
	 * @return The value of the current property or the next element in the array as a JSON object, or null if the current state is in an
	 *         array and there is no next element
	 * @throws IllegalStateException If the top of this parser's state is not either an array or an object property, or the next element is
	 *         not a JSON object
	 * @throws IOException If an error occurs reading the data from the stream
	 * @throws ParseException If the next element cannot be parsed
	 */
	public org.json.simple.JSONObject parseObject() throws IOException, ParseException
	{
		Object ret = parseNext(false);
		if(ret == null)
			return null;
		else if(!(ret instanceof org.json.simple.JSONObject))
			throw new IllegalStateException("Next element is type " + getType(ret) + ", not a JSON object");
		else
			return (org.json.simple.JSONObject) ret;
	}

	/**
	 * Same as {@link #parseNext(boolean)}, but asserting that the next element, if present, is a JSON array. If the current state is an
	 * array and there are no elements left, this method will return null. Note that if the next item is a null value, this method will
	 * throw an {@link IllegalStateException}.
	 * 
	 * 
	 * @return The value of the current property or the next element in the array as a JSON array, or null if the current state is in an
	 *         array and there is no next element
	 * @throws IllegalStateException If the top of this parser's state is not either an array or an object property, or the next element is
	 *         not a JSON array
	 * @throws IOException If an error occurs reading the data from the stream
	 * @throws ParseException If the next element cannot be parsed
	 */
	public org.json.simple.JSONArray parseArray() throws IOException, ParseException
	{
		Object ret = parseNext(false);
		if(ret == null)
			return null;
		else if(!(ret instanceof org.json.simple.JSONArray))
			throw new IllegalStateException("Next element is a " + getType(ret) + ", not a JSON array");
		else
			return (org.json.simple.JSONArray) ret;
	}

	/**
	 * Same as {@link #parseNext(boolean)}, but asserting that the next element, if present, is a number. If the current state is an array
	 * and there are no elements left, this method will return null. Note that if the next item is a null value, this method will throw an
	 * {@link IllegalStateException}.
	 * 
	 * @return The value of the current property or the next element in the array as a number, or null if the current state is in an array
	 *         and there is no next element
	 * @throws IllegalStateException If the top of this parser's state is not either an array or an object property, or the next element is
	 *         not a number
	 * @throws IOException If an error occurs reading the data from the stream
	 * @throws ParseException If the next element cannot be parsed
	 */
	public Number parseNumber() throws IOException, ParseException
	{
		Object ret = parseNext(false);
		if(ret == null)
			return null;
		else if(!(ret instanceof Number))
			throw new IllegalStateException("Next element is a " + getType(ret) + ", not a number");
		else
			return (Number) ret;
	}

	/**
	 * Same as {@link #parseNext(boolean)}, but asserting that the next element, if present, is a string. If the current state is an array
	 * and there are no elements left, this method will return null. Note that if the next item is a null value, this method will throw an
	 * {@link IllegalStateException}.
	 * 
	 * @return The value of the current property or the next element in the array as a string, or null if the current state is in an array
	 *         and there is no next element
	 * @throws IllegalStateException If the top of this parser's state is not either an array or an object property, or the next element is
	 *         not a string
	 * @throws IOException If an error occurs reading the data from the stream
	 * @throws ParseException If the next element cannot be parsed
	 */
	public String parseString() throws IOException, ParseException
	{
		Object ret = parseNext(false);
		if(ret == null)
			return null;
		else if(!(ret instanceof String))
			throw new IllegalStateException("Next element is a " + getType(ret) + ", not a string");
		else
			return (String) ret;
	}

	/**
	 * Same as {@link #parseNext(boolean)}, but asserting that the next element, if present, is a string. If the current state is an array
	 * and there are no elements left, this method will return null. Note that if the next item is a null value, this method will throw an
	 * {@link IllegalStateException}.
	 * 
	 * @return The value of the current string property or the next element in the array as a reader, or null if the current state is in an
	 *         array and there is no next element
	 * @throws IllegalStateException If the top of this parser's state is not either an array or an object property, or the next element is
	 *         not a string
	 * @throws IOException If an error occurs reading the data from the stream
	 * @throws ParseException If the next element cannot be parsed
	 */
	public java.io.Reader parseStringAsReader() throws IOException, ParseException
	{
		Object ret = parseNext(true);
		if(ret == null)
			return null;
		else if(!(ret instanceof Reader))
			throw new IllegalStateException("Next element is a " + getType(ret) + ", not a string");
		else
			return (Reader) ret;
	}

	/**
	 * Same as {@link #parseNext(boolean)}, but asserting that there is a next element and that it is an int
	 * 
	 * @return The value of the current property or the next element in the array as an int
	 * @throws IllegalStateException If the top of this parser's state is not either an array or an object property, if there is no next
	 *         element in the array, or the next element is not an int
	 * @throws IOException If an error occurs reading the data from the stream
	 * @throws ParseException If the next element cannot be parsed
	 */
	public int parseInt() throws IOException, ParseException
	{
		Object ret = parseNext(false);
		if(ret == null)
			throw new IllegalStateException("No more elements in array");
		else if(!(ret instanceof Integer))
			throw new IllegalStateException("Next element is a " + getType(ret) + ", not an integer");
		else
			return ((Number) ret).intValue();
	}

	/**
	 * Same as {@link #parseNext(boolean)}, but asserting that there is a next element and that it is a long
	 * 
	 * @return The value of the current property or the next element in the array as a long
	 * @throws IllegalStateException If the top of this parser's state is not either an array or an object property, if there is no next
	 *         element in the array, or the next element is not a long
	 * @throws IOException If an error occurs reading the data from the stream
	 * @throws ParseException If the next element cannot be parsed
	 */
	public long parseLong() throws IOException, ParseException
	{
		Object ret = parseNext(false);
		if(ret == null)
			throw new IllegalStateException("No more elements in array");
		else if(!(ret instanceof Integer) && !(ret instanceof Long))
			throw new IllegalStateException("Next element is a " + getType(ret) + ", not a long");
		else
			return ((Number) ret).longValue();
	}

	/**
	 * Same as {@link #parseNext(boolean)}, but asserting that there is a next element and that it is a float or double (a double will be
	 * cast)
	 * 
	 * @return The value of the current property or the next element in the array as a float
	 * @throws IllegalStateException If the top of this parser's state is not either an array or an object property, if there is no next
	 *         element in the array, or the next element is not a float or a double
	 * @throws IOException If an error occurs reading the data from the stream
	 * @throws ParseException If the next element cannot be parsed
	 */
	public float parseFloat() throws IOException, ParseException
	{
		Object ret = parseNext(false);
		if(ret == null)
			throw new IllegalStateException("No more elements in array");
		else if(!(ret instanceof Float) && !(ret instanceof Double))
			throw new IllegalStateException("Next element is a " + getType(ret) + ", not a float");
		else
			return ((Number) ret).floatValue();
	}

	/**
	 * Same as {@link #parseNext(boolean)}, but asserting that there is a next element and that it is a float or double (a float will be
	 * cast)
	 * 
	 * @return The value of the current property or the next element in the array as a double
	 * @throws IllegalStateException If the top of this parser's state is not either an array or an object property, if there is no next
	 *         element in the array, or the next element is not a float or a double
	 * @throws IOException If an error occurs reading the data from the stream
	 * @throws ParseException If the next element cannot be parsed
	 */
	public double parseDouble() throws IOException, ParseException
	{
		Object ret = parseNext(false);
		if(ret == null)
			throw new IllegalStateException("No more elements in array");
		else if(!(ret instanceof Float) && !(ret instanceof Double))
			throw new IllegalStateException("Next element is a " + getType(ret) + ", not a double");
		else
			return ((Number) ret).doubleValue();
	}

	/**
	 * Same as {@link #parseNext(boolean)}, but asserting that there is a next element and that it is a boolean
	 * 
	 * @return The value of the current property or the next element in the array as a boolean
	 * @throws IllegalStateException If the top of this parser's state is not either an array or an object property, if there is no next
	 *         element in the array, or the next element is not a boolean
	 * @throws IOException If an error occurs reading the data from the stream
	 * @throws ParseException If the next element cannot be parsed
	 */
	public boolean parseBoolean() throws IOException, ParseException
	{
		Object ret = parseNext(false);
		if(ret == null)
			throw new IllegalStateException("No more elements in array");
		else if(!(ret instanceof Boolean))
			throw new IllegalStateException("Next element is a " + getType(ret) + ", not a boolean ");
		else
			return ((Boolean) ret).booleanValue();
	}

	/**
	 * Same as {@link #parseNext(boolean)}, but asserting that there is a next element and that it is null. If this method returns normally,
	 * the value was parsed and was null.
	 * 
	 * @throws IllegalStateException If the top of this parser's state is not either an array or an object property, if there is no next
	 *         element in the array, or the next element is not null
	 * @throws IOException If an error occurs reading the data from the stream
	 * @throws ParseException If the next element cannot be parsed
	 */
	public void parseNull() throws IOException, ParseException
	{
		Object ret = parseNext(false);
		if(ret == null)
			throw new IllegalStateException("No more elements in array");
		else if(ret != NULL)
			throw new IllegalStateException("Next element is a " + getType(ret) + ", not null");
	}

	/**
	 * @param item The JSON item to describe
	 * @return The name of the JSON item's type
	 */
	public static String getType(Object item)
	{
		if(item instanceof org.json.simple.JSONObject)
			return "object";
		else if(item instanceof org.json.simple.JSONArray)
			return "array";
		else if(item instanceof Long)
			return "long";
		else if(item instanceof Integer)
			return "integer";
		else if(item instanceof Float)
			return "float";
		else if(item instanceof Double)
			return "double";
		else if(item instanceof Number)
			return "number";
		else if(item instanceof Boolean)
			return "boolean";
		else if(item == NULL || item == null)
			return "null";
		else
			return item.getClass().getName();
	}
}

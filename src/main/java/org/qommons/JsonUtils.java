/*
 * JsonUtils.java Created Oct 16, 2007 by Andrew Butler, PSL
 */
package org.qommons;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/** A utility class to simplify manipulating certain kinds of data in the JSON format */
public class JsonUtils
{
	private JsonUtils()
	{
	}

	/**
	 * Parses a color from a JSON object. The normal RGB channels may be specified as integer values
	 * with "red"/"green"/"blue" keys or "r"/"g"/"b" keys. The alpha channel may be specified as
	 * "alpha" (if the "red"/"green"/"blue" scheme is used) or "a" (with the "r"/"g"/"b" scheme).
	 * 
	 * @param jsonColor The JSON object to parse the color from
	 * @return The parsed color
	 */
	public static java.awt.Color deserializeColor(JSONObject jsonColor)
	{
		int r, g, b, a;
		if(jsonColor.containsKey("red"))
		{
			if(jsonColor.containsKey("alpha"))
				a = ((Integer) jsonColor.get("alpha")).intValue();
			else
				a = -1;
			r = ((Integer) jsonColor.get("red")).intValue();
			g = ((Integer) jsonColor.get("green")).intValue();
			b = ((Integer) jsonColor.get("blue")).intValue();
		}
		else if(jsonColor.containsKey("r"))
		{
			if(jsonColor.containsKey("a"))
				a = ((Integer) jsonColor.get("a")).intValue();
			else
				a = -1;
			r = ((Integer) jsonColor.get("r")).intValue();
			g = ((Integer) jsonColor.get("g")).intValue();
			b = ((Integer) jsonColor.get("b")).intValue();
		}
		else
			throw new IllegalArgumentException("Argument " + jsonColor + " is not a valid color");
		if(a < 0)
			return new java.awt.Color(r, g, b);
		else
			return new java.awt.Color(r, g, b, a);
	}

	/**
	 * Writes a JSON type object into a more human-readable JSON format
	 * 
	 * @param json The object to format
	 * @return A whitespaced human-readable string representing the object
	 */
	public static String format(Object json)
	{
		StringBuilder ret = new StringBuilder();
		format(json, ret, 0);
		return ret.toString();
	}

	private static void format(Object json, StringBuilder ret, int indent)
	{
		if(json instanceof JSONObject)
			formatObject((JSONObject) json, ret, indent);
		else if(json instanceof JSONArray)
			formatArray((JSONArray) json, ret, indent);
		else if(json instanceof String)
		{
			ret.append('"');
			ret.append(json);
			ret.append('"');
		}
		else
			ret.append(json);
	}

	private static void formatObject(JSONObject json, StringBuilder ret, int indent)
	{
		ret.append('{');
		indent++;
		java.util.Iterator<java.util.Map.Entry<String, Object>> iter = json.entrySet().iterator();
		while(iter.hasNext())
		{
			java.util.Map.Entry<String, Object> entry = iter.next();
			ret.append('\n');
			indent(ret, indent);
			ret.append('"');
			ret.append(entry.getKey());
			ret.append('"');
			ret.append(':');
			ret.append(' ');
			format(entry.getValue(), ret, indent);
			if(iter.hasNext())
				ret.append(',');
		}
		if(json.size() > 0)
		{
			ret.append('\n');
			indent(ret, indent - 1);
		}
		ret.append('}');
	}

	private static void formatArray(JSONArray json, StringBuilder ret, int indent)
	{
		ret.append('[');
		indent++;
		java.util.Iterator<Object> iter = json.iterator();
		while(iter.hasNext())
		{
			ret.append('\n');
			indent(ret, indent);
			format(iter.next(), ret, indent);
			if(iter.hasNext())
				ret.append(',');
		}
		if(json.size() > 0)
		{
			ret.append('\n');
			indent(ret, indent - 1);
		}
		ret.append(']');
	}

	private static void indent(StringBuilder ret, int indent)
	{
		for(int i = 0; i < indent; i++)
			ret.append('\t');
	}

	/**
	 * Runs the {@link #format(Object) on the JSON-parsed contents of a file}. Alternately, all
	 * white space is removed fromt the file if the -noformat option is given as the second argument
	 * 
	 * @param args <ol>
	 *        <li>The path to the file to format</li>
	 *        <li>(optional) -noformat</li>
	 *        </ol?>
	 * @throws java.io.IOException If the file's contents could not be read or written
	 */
	public static void main(String [] args) throws java.io.IOException
	{
		StringBuilder contents = new StringBuilder();
		{
			java.io.Reader fileReader = new java.io.FileReader(args[0]);
			int read = fileReader.read();
			while(read >= 0)
			{
				contents.append((char) read);
				read = fileReader.read();
			}
			fileReader.close();
		}
		Object json = org.json.simple.JSONValue.parse(contents.toString());
		java.io.FileWriter fileWriter = new java.io.FileWriter(args[0]);

		if(args.length == 2 && args[1].equals("-noformat"))
			fileWriter.write(json.toString());
		else
			fileWriter.write(format(json));
		fileWriter.flush();
		fileWriter.close();
	}
}

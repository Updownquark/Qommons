package org.qommons;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

/**
 * <p>
 * A utility class for dealing with colors. This class contains constants for all named colors in HTML, the ability to print and parse them
 * in a friendly way, and a few other miscellaneous utilities.
 * </p>
 * <p>
 * The general parsing ({@link #parseColor(String)}) recognizes colors either by name (the constant name, with camel-case converted to kebab
 * case, e.g. "dark-green" is specified instead of "darkGreen"), red-green-blue components (e.g. "rgb(0, 100, 0)") or
 * hue-saturation-brightness components (e.g. "hsb(120, 100, 39)").<br>
 * rgb values can be specified via hex like "#006400", and hsb values like "$786427".<br>
 * </p>
 */
public class Colors {
	private Colors() {
	}

	/** Not available via {@link #parseColor(String)}, this is a code utility constant representing a completely transparent color */
	public static final transient Color transparent = new Color(0, 0, 0, 0);

	/** Basic black, #000000<font color="black">######</font> */
	public static final Color black = Color.black;

	/** Basic white, #ffffff <font color="white">######</font> */
	public static final Color white = Color.white;

	/** Basic red, #ff0000 <font color="red">######</font> */
	public static final Color red = Color.red;

	/** "Lime" is basic green from HTML. "Green" is a darker color. #00ff00 <font color="green">######</font>. */
	public static final Color lime = Color.green;

	/** Basic blue, #0000ff <font color="blue">######</font> */
	public static final Color blue = Color.blue;

	/** Basic yellow, a mix of full red and green, #ffff00 <font color="yellow">######</font> */
	public static final Color yellow = Color.yellow;

	/** Basic cyan, a mix of full green and blue, #00ffff <font color="cyan">######</font> */
	public static final Color cyan = Color.cyan;

	/** Same as cyan, #00ffff <font color="aqua">######</font> */
	public static final Color aqua = cyan;

	/** Basic magenta, a mix of full red and blue, #ff00ff <font color="magenta">######</font> */
	public static final Color magenta = Color.magenta;

	/** Same as magenta, #ff00ff <font color="fuchsia">######</font> */
	public static final Color fuchsia = magenta;

	/** A darker red, #800000 <font color="maroon">######</font> */
	public static final Color maroon = new Color(128, 0, 0);

	/** A darker green than {@link java.awt.Color#green}. Use {@link #lime} for that green. #008000<font color="#008000">######</font> */
	public static final Color green = new Color(0, 128, 0);

	/** A darker blue, #000080 <font color="navy">######</font> */
	public static final Color navy = new Color(0, 0, 128);

	/** A darker yellow, #808000 <font color="olive">######</font> */
	public static final Color olive = new Color(128, 128, 0);

	/** A darker cyan, <font color="teal">######</font> */
	public static final Color teal = new Color(0, 128, 128);

	/** A darker magenta, <font color="purple">######</font> */
	public static final Color purple = new Color(128, 0, 128);

	/** #ffa500 <font color="#ffa500">######</font> */
	public static final Color orange = _parseColor("#ffa500");

	/** <font color="#a52a2a">#a52a2a</font> */
	public static final Color brown = _parseColor("#a52a2a");

	/** #f0f8ff <font color="#f0f8ff">######</font> */
	public static final Color aliceBlue = _parseColor("#f0f8ff");

	/** #faebd7 <font color="#faebd7">######</font> */
	public static final Color antiqueWhite = _parseColor("#faebd7");

	/** #7fffd4 <font color="#7ffd4">######</font> */
	public static final Color aquaMarine = _parseColor("#7fffd4");

	/** #f0ffff <font color="#f0ffff">######</font> */
	public static final Color azure = _parseColor("#f0ffff");

	/** #f5f5dc <font color="#f5f5dc">######</font> */
	public static final Color beige = _parseColor("#f5f5dc");

	/** #ffe4c4 <font color="#ffe4c4">######</font> */
	public static final Color bisque = _parseColor("#ffe4c4");

	/** #ffebcd <font color="#ffebcd">######</font> */
	public static final Color blanchedAlmond = _parseColor("#ffebcd");

	/** #8a2be2 <font color="#8a2be2">######</font> */
	public static final Color blueViolet = _parseColor("#8a2be2");

	/** #deb887 <font color="#deb887">######</font> */
	public static final Color burlyWood = _parseColor("#deb887");

	/** #5f9ea0 <font color="#5f9ea0">######</font> */
	public static final Color cadetBlue = _parseColor("#5f9ea0");

	/** #7fff00 <font color="#7fff00">######</font> */
	public static final Color chartreuse = _parseColor("#7fff00");

	/** #d2691e <font color="#d2691e">######</font> */
	public static final Color chocolate = _parseColor("#d2691e");

	/** #ff7f50 <font color="#ff7f50">######</font> */
	public static final Color coral = _parseColor("#ff7f50");

	/** #6495ed <font color="#6495ed">######</font> */
	public static final Color cornflowerBlue = _parseColor("#6495ed");

	/** #fff8dc <font color="#fff8dc">######</font> */
	public static final Color cornsilk = _parseColor("#fff8dc");

	/** #dc143c <font color="#dc143c">######</font> */
	public static final Color crimson = _parseColor("#dc143c");

	/** #00008b <font color="#00008b">######</font> */
	public static final Color darkBlue = _parseColor("#00008b");

	/** #008b8b <font color="#008b8b">######</font> */
	public static final Color darkCyan = _parseColor("#008b8b");

	/** #b8860b <font color="#b8860b">######</font> */
	public static final Color darkGoldenRod = _parseColor("#b8860b");

	/** #a9a9a9 <font color="#a9a9a9">######</font> */
	public static final Color darkGray = _parseColor("#a9a9a9");

	/** #006400 <font color="#006400">######</font> */
	public static final Color darkGreen = _parseColor("#006400");

	/** #bdb76b <font color="#bdb76b">######</font> */
	public static final Color darkKhaki = _parseColor("#bdb76b");

	/** #8b008b <font color="#8b008b">######</font> */
	public static final Color darkMagenta = _parseColor("#8b008b");

	/** #556b2f <font color="#556b2f">######</font> */
	public static final Color darkOliveGreen = _parseColor("#556b2f");

	/** #ff8c00 <font color="#ff8c00">######</font> */
	public static final Color darkOrange = _parseColor("#ff8c00");

	/** #9932cc <font color="#9932cc">######</font> */
	public static final Color darkOrchid = _parseColor("#9932cc");

	/** #8b0000 <font color="#8b0000">######</font> */
	public static final Color darkRed = _parseColor("#8b0000");

	/** #e9967a <font color="#e9967a">######</font> */
	public static final Color darkSalmon = _parseColor("#e9967a");

	/** #8fbc8f <font color="#8fbc8f">######</font> */
	public static final Color darkSeaGreen = _parseColor("#8fbc8f");

	/** #483d8b <font color="#483d8b">######</font> */
	public static final Color darkSlateBlue = _parseColor("#483d8b");

	/** #2f4f4f <font color="#2f4f4f">######</font> */
	public static final Color darkSlateGray = _parseColor("#2f4f4f");

	/** #00ced1 <font color="#00ced1">######</font> */
	public static final Color darkTurquoise = _parseColor("#00ced1");

	/** #9400d3 <font color="#9400d3">######</font> */
	public static final Color darkViolet = _parseColor("#9400d3");

	/** #ff1493 <font color="#ff1493">######</font> */
	public static final Color deepPink = _parseColor("#ff1493");

	/** #00bfff <font color="#00bfff">######</font> */
	public static final Color deepSkyBlue = _parseColor("#00bfff");

	/** #696969 <font color="#696969">######</font> */
	public static final Color dimGray = _parseColor("#696969");

	/** #1e90ff <font color="#1e90ff">######</font> */
	public static final Color dodgerBlue = _parseColor("#1e90ff");

	/** #b22222 <font color="#b22222">######</font> */
	public static final Color fireBrick = _parseColor("#b22222");

	/** #fffaf0 <font color="#fffaf0">######</font> */
	public static final Color floralWhite = _parseColor("#fffaf0");

	/** #228b22 <font color="#228b22">######</font> */
	public static final Color forestGreen = _parseColor("#228b22");

	/** #dcdcdc <font color="#dcdcdc">######</font> */
	public static final Color gainsboro = _parseColor("#dcdcdc");

	/** #f8f8ff <font color="#f8f8ff">######</font> */
	public static final Color ghostWhite = _parseColor("#f8f8ff");

	/** #ffd700 <font color="#ffd700">######</font> */
	public static final Color gold = _parseColor("#ffd700");

	/** #daa520 <font color="#daa520">######</font> */
	public static final Color goldenRod = _parseColor("#daa520");

	/** #808080 <font color="#808080">######</font> */
	public static final Color gray = _parseColor("#808080");

	/** #adff2f <font color="#adff2f">######</font> */
	public static final Color greenYellow = _parseColor("#adff2f");

	/** #f0fff0 <font color="#f0fff0">######</font> */
	public static final Color honeydew = _parseColor("#f0fff0");

	/** #ff69b4 <font color="#ff69b4">######</font> */
	public static final Color hotPink = _parseColor("#ff69b4");

	/** #cd5c5c <font color="#cd5c5c">######</font> */
	public static final Color indianRed = _parseColor("#cd5c5c");

	/** #4b0082 <font color="#4b0082">######</font> */
	public static final Color indigo = _parseColor("#4b0082");

	/** #fffff0 <font color="#fffff0">######</font> */
	public static final Color ivory = _parseColor("#fffff0");

	/** #f0e68c <font color="#f0e68c">######</font> */
	public static final Color khaki = _parseColor("#f0e68c");

	/** #e6e6fa <font color="#e6e6fa">######</font> */
	public static final Color lavender = _parseColor("#e6e6fa");

	/** #fff0f5 <font color="#fff0f5">######</font> */
	public static final Color lavenderBlush = _parseColor("#fff0f5");

	/** #7cfc00 <font color="#7cfc00">######</font> */
	public static final Color lawnGreen = _parseColor("#7cfc00");

	/** #fffacd <font color="#fffacd">######</font> */
	public static final Color lemonChiffon = _parseColor("#fffacd");

	/** #add8e6 <font color="#add8e6">######</font> */
	public static final Color lightBlue = _parseColor("#add8e6");

	/** #f08080 <font color="#f08080">######</font> */
	public static final Color lightCoral = _parseColor("#f08080");

	/** #e0ffff <font color="#e0ffff">######</font> */
	public static final Color lightCyan = _parseColor("#e0ffff");

	/** #fafad2 <font color="#fafad2">######</font> */
	public static final Color lightGoldenRodYellow = _parseColor("#fafad2");

	/** #d3d3d3 <font color="#d3d3d3">######</font> */
	public static final Color lightGray = _parseColor("#d3d3d3");

	/** #90ee90 <font color="#90ee90">######</font> */
	public static final Color lightGreen = _parseColor("#90ee90");

	/** #ffb6c1 <font color="#ffb6c1">######</font> */
	public static final Color lightPink = _parseColor("#ffb6c1");

	/** #ffa07a <font color="#ffa07a">######</font> */
	public static final Color lightSalmon = _parseColor("#ffa07a");

	/** #20b2aa <font color="#20b2aa">######</font> */
	public static final Color lightSeaGreen = _parseColor("#20b2aa");

	/** #87cefa <font color="#87cefa">######</font> */
	public static final Color lightSkyBlue = _parseColor("#87cefa");

	/** #778899 <font color="#778899">######</font> */
	public static final Color lightSlateGray = _parseColor("#778899");

	/** #b0c4de <font color="#b0c4de">######</font> */
	public static final Color lightSteelBlue = _parseColor("#b0c4de");

	/** #ffffe0 <font color="#ffffe0">######</font> */
	public static final Color lightYellow = _parseColor("#ffffe0");

	/** #32cd32 <font color="#32cd32">######</font> */
	public static final Color limeGreen = _parseColor("#32cd32");

	/** #faf0e6 <font color="#faf0e6">######</font> */
	public static final Color linen = _parseColor("#faf0e6");

	/** #66cdaa <font color="#66cdaa">######</font> */
	public static final Color mediumAquaMarine = _parseColor("#66cdaa");

	/** #0000cd <font color="#0000cd">######</font> */
	public static final Color mediumBlue = _parseColor("#0000cd");

	/** #ba55d3 <font color="#ba55d3">######</font> */
	public static final Color mediumOrchid = _parseColor("#ba55d3");

	/** #9370d8 <font color="#9370d8">######</font> */
	public static final Color mediumPurple = _parseColor("#9370d8");

	/** #3cb371 <font color="#3cb371">######</font> */
	public static final Color mediumSeaGreen = _parseColor("#3cb371");

	/** #7b68ee <font color="#7b68ee">######</font> */
	public static final Color mediumSlateBlue = _parseColor("#7b68ee");

	/** #00fa9a <font color="#00fa9a">######</font> */
	public static final Color mediumSpringGreen = _parseColor("#00fa9a");

	/** #48d1cc <font color="#48d1cc">######</font> */
	public static final Color mediumTurquoise = _parseColor("#48d1cc");

	/** #c71585 <font color="#c71585">######</font> */
	public static final Color mediumVioletRed = _parseColor("#c71585");

	/** #191970 <font color="#191970">######</font> */
	public static final Color midnightBlue = _parseColor("#191970");

	/** #f5fffa <font color="#f5fffa">######</font> */
	public static final Color mintCream = _parseColor("#f5fffa");

	/** #ffe4e1 <font color="#ffe4e1">######</font> */
	public static final Color mistyRose = _parseColor("#ffe4e1");

	/** #ffe4b5 <font color="#ffe4b5">######</font> */
	public static final Color moccasin = _parseColor("#ffe4b5");

	/** #ffdead <font color="#ffdead">######</font> */
	public static final Color navajoWhite = _parseColor("#ffdead");

	/** #fdf5e6 <font color="#fdf5e6">######</font> */
	public static final Color oldLace = _parseColor("#fdf5e6");

	/** #6b8e23 <font color="#6b8e23">######</font> */
	public static final Color oliveDrab = _parseColor("#6b8e23");

	/** #ff4500 <font color="#ff4500">######</font> */
	public static final Color orangeRed = _parseColor("#ff4500");

	/** #da70d6 <font color="#da70d6">######</font> */
	public static final Color orchid = _parseColor("#da70d6");

	/** #eee8aa <font color="#eee8aa">######</font> */
	public static final Color paleGoldenRod = _parseColor("#eee8aa");

	/** #98fb98 <font color="#98fb98">######</font> */
	public static final Color paleGreen = _parseColor("#98fb98");

	/** #afeeee <font color="#afeeee">######</font> */
	public static final Color paleTurquoise = _parseColor("#afeeee");

	/** #d87093 <font color="#d87093">######</font> */
	public static final Color paleVioletRed = _parseColor("#d87093");

	/** #ffefd5 <font color="#ffefd5">######</font> */
	public static final Color papayaWhip = _parseColor("#ffefd5");

	/** #ffdab9 <font color="#ffdab9">######</font> */
	public static final Color peachPuff = _parseColor("#ffdab9");

	/** #cd853f <font color="#cd853f">######</font> */
	public static final Color peru = _parseColor("#cd853f");

	/** #ffc0cb <font color="#ffc0cb">######</font> */
	public static final Color pink = _parseColor("#ffc0cb");

	/** #dda0dd <font color="#dda0dd">######</font> */
	public static final Color plum = _parseColor("#dda0dd");

	/** #b0e0e6 <font color="#b0e0e6">######</font> */
	public static final Color powderBlue = _parseColor("#b0e0e6");

	/** #bc8f8f <font color="#bc8f8f">######</font> */
	public static final Color rosyBrown = _parseColor("#bc8f8f");

	/** #4169e1 <font color="#4169e1">######</font> */
	public static final Color royalBlue = _parseColor("#4169e1");

	/** #8b4513 <font color="#8b4513">######</font> */
	public static final Color saddleBrown = _parseColor("#8b4513");

	/** #fa8072 <font color="#fa8072">######</font> */
	public static final Color salmon = _parseColor("#fa8072");

	/** #f4a460 <font color="#f4a460">######</font> */
	public static final Color sandyBrown = _parseColor("#f4a460");

	/** #2e8b57 <font color="#2e8b57">######</font> */
	public static final Color seaGreen = _parseColor("#2e8b57");

	/** #fff5ee <font color="#fff5ee">######</font> */
	public static final Color seaShell = _parseColor("#fff5ee");

	/** #a0522d <font color="#a0522d">######</font> */
	public static final Color sienna = _parseColor("#a0522d");

	/** #c0c0c0 <font color="#c0c0c0">######</font> */
	public static final Color silver = _parseColor("#c0c0c0");

	/** #a7ceeb <font color="#a7ceeb">######</font> */
	public static final Color skyBlue = _parseColor("#a7ceeb");

	/** #6a5acd <font color="#6a5acd">######</font> */
	public static final Color slateBlue = _parseColor("#6a5acd");

	/** #708090 <font color="#708090">######</font> */
	public static final Color slateGray = _parseColor("#708090");

	/** #fffafa <font color="#fffafa">######</font> */
	public static final Color snow = _parseColor("#fffafa");

	/** #00ff7f <font color="#00ff7f">######</font> */
	public static final Color springGreen = _parseColor("#00ff7f");

	/** #4682b4 <font color="#4682b4">######</font> */
	public static final Color steelBlue = _parseColor("#4682b4");

	/** #d2b4bc <font color="#d2b4bc">######</font> */
	public static final Color tan = _parseColor("#d2b4bc");

	/** #d8bfd8 <font color="#d8bfd8">######</font> */
	public static final Color thistle = _parseColor("#d8bfd8");

	/** #ff6347 <font color="#ff6347">######</font> */
	public static final Color tomato = _parseColor("#ff6347");

	/** #40e0d0 <font color="#40e0d0">######</font> */
	public static final Color turquoise = _parseColor("#40e0d0");

	/** #ee82ee <font color="#ee82ee">######</font> */
	public static final Color violet = _parseColor("#ee82ee");

	/** #f5deb3 <font color="#f5deb3">######</font> */
	public static final Color wheat = _parseColor("#f5deb3");

	/** #f5f5f5 <font color="#f5f5f5">######</font> */
	public static final Color whiteSmoke = _parseColor("#f5f5f5");

	/** #9acd32 <font color="#9acd32">######</font> */
	public static final Color yellowGreen = _parseColor("#9acd32");

	/** All named colors in this class */
	public static final Map<String, Color> NAMED_COLORS;

	private static final Map<Color, String> theColorNames;

	static {
		Map<String, Color> namedColors = new LinkedHashMap<>();
		Map<Color, String> colorNames = new HashMap<>();
		Field[] colorFields = Colors.class.getDeclaredFields();
		for (Field field : colorFields) {
			int mods = field.getModifiers();
			if (!Modifier.isPublic(mods) || !Modifier.isStatic(mods) || Modifier.isTransient(mods)) {
				continue;
			}
			if (field.getType() != Color.class) {
				continue;
			}
			try {
				Color value = (Color) field.get(null);
				namedColors.put(StringUtils.parseByCase(field.getName(), true).toKebabCase(), value);
				colorNames.put(value, field.getName());
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		NAMED_COLORS = Collections.unmodifiableMap(namedColors);
		theColorNames = Collections.unmodifiableMap(colorNames);
	}

	/** An internal method to swallow the exception from {@link #parseColor(String)} */
	private static Color _parseColor(String str) {
		try {
			return parseColor(str);
		} catch (ParseException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Parses a color from a string
	 *
	 * @param str The string representation of the color. This value may be in any of 5 forms:
	 *        <ul>
	 *        <li>Hexadecimal RGB: Looks like #XXXXXX where each 'X' is a hexadecimal digit (0-9 or a-f)</li>
	 *        <li>Hexadecimal HSB: Looks like $XXXXXX where each 'X' is a hexadecimal digit (0-9 or a-f)</li>
	 *        <li>Decimal RGB: Looks like rgb(rrr, ggg, bbb) with decimal r, g, b values.</li>
	 *        <li>Decimal HSB: Looks like hsb(hhh, sss, bbb) with decimal r, g, b values.</li>
	 *        <li>A named color matching the name of one of this class's static color constant fields</li>
	 *        </ul>
	 *        The value is not case-sensitive.
	 * @return The color corresponding to the string, or null if the given string is not formatted as a color
	 * @throws ParseException If the color cannot be parsed
	 */
	public static Color parseIfColor(String str) throws ParseException {
		final String original = str;
		str = str.toLowerCase();
		if (str.startsWith("#")) {
			str = str.substring(1);
			if (!str.matches("[0-9a-f]{6}")) {
				throw new ParseException("RGB colors must be in the form of #XXXXXX where X is 0-9 or a-f: \"" + original + "\"", 0);
			}
			return rgb(hexInt(str, 0), hexInt(str, 2), hexInt(str, 4));
		} else if (str.startsWith("$")) {
			str = str.substring(1);
			if (!str.matches("[0-9a-f]{6}")) {
				throw new ParseException("HSB colors must be in the form of #XXXXXX where X is 0-9 or a-f: \"" + original + "\"", 0);
			}
			return Color.getHSBColor(hexInt(str, 0) / 255f, hexInt(str, 2) / 255f, hexInt(str, 4) / 255f);
		} else if (str.startsWith("rgb(")) {
			if (str.charAt(str.length() - 1) != ')') {
				throw new ParseException("Colors that start with 'rgb(' must end with ')': \"" + original + "\"", 0);
			}
			str = str.substring(4, str.length() - 1);
			int r, g, b;
			try {
				int idx = str.indexOf(',');
				if (idx < 0) {
					throw new ParseException(
						"Colors that start with 'rgb('" + " must have 3 integers separated by commas: \"" + original + "\"", 0);
				}
				r = Integer.parseInt(str.substring(0, idx));
				str = str.substring(idx + 1).trim();
				idx = str.indexOf(',');
				if (idx < 0) {
					throw new ParseException(
						"Colors that start with 'rgb('" + " must have 3 integers separated by commas: \"" + original + "\"", 0);
				}
				g = Integer.parseInt(str.substring(0, idx));
				str = str.substring(idx + 1).trim();
				b = Integer.parseInt(str);
			} catch (NumberFormatException e) {
				throw new ParseException(
					"Colors that start with 'rgb('" + " must have 3 integers separated by commas: \"" + original + "\"", 0);
			}
			if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
				throw new ParseException("Colors that start with 'rgb('"
					+ " must have three integers between 0 and 255 separated by commas: \"" + original + "\"", 0);
			}
			return rgb(r, g, b);
		} else if (str.startsWith("hsb(")) {
			if (str.charAt(str.length() - 1) != ')') {
				throw new ParseException("Colors that start with 'hsb(' must end with ')': \"" + original + "\"", 0);
			}
			str = str.substring(4, str.length() - 1);
			float h, s, b;
			try {
				int idx = str.indexOf(',');
				if (idx < 0) {
					throw new ParseException(
						"Colors that start with 'hsb('" + " must have 3 float values separated by commas: \"" + original + "\"", 0);
				}
				h = Float.parseFloat(str.substring(0, idx));
				str = str.substring(idx + 1).trim();
				idx = str.indexOf(',');
				if (idx < 0) {
					throw new ParseException(
						"Colors that start with 'hsb('" + " must have 3 float values separated by commas: \"" + original + "\"", 0);
				}
				s = Float.parseFloat(str.substring(0, idx));
				str = str.substring(idx + 1).trim();
				b = Float.parseFloat(str);
			} catch (NumberFormatException e) {
				throw new ParseException(
					"Colors that start with 'hsb('" + " must have 3 float values separated by commas: \"" + original + "\"", 0);
			}
			if (h < 0 || h > 255 || s < 0 || s > 255 || b < 0 || b > 255) {
				throw new ParseException("Colors that start with 'hsb('"
					+ " must have three float values between 0 and 255 separated by commas: \"" + original + "\"", 0);
			}
			return Color.getHSBColor(h, s, b);
		} else {
			return NAMED_COLORS.get(str);
		}
	}

	/**
	 * Parses a color from a string, not tolerating unrecognized values
	 *
	 * @param str The string representation of the color.
	 * @return The color corresponding to the string
	 * @throws ParseException If the color is unrecognized or cannot be parsed
	 * @see #parseIfColor(String)
	 */
	public static Color parseColor(String str) throws ParseException {
		Color ret = parseIfColor(str);
		if (ret == null) {
			throw new ParseException("No color named " + str, 0);
		}
		return ret;
	}

	/** @return The names of all named colors in this class */
	public static Set<String> getColorNames() {
		return Collections.unmodifiableSet(NAMED_COLORS.keySet());
	}

	/**
	 * @param r The red component of the color, from 0-255
	 * @param g The green component of the color, from 0-255
	 * @param b The blue component of the color, from 0-255
	 * @return The color with the given components
	 */
	public static Color rgb(int r, int g, int b) {
		return new Color(r, g, b);
	}

	private static final String hexDigits = "0123456789abcdef";

	/**
	 * A utility method for 2-digit parsing hexadecimal numbers out of a string
	 *
	 * @param str The string
	 * @param start The index at which the number starts
	 * @return The hex digit
	 */
	public static int hexInt(String str, int start) {
		int ret = hexDigits.indexOf(str.charAt(start));
		ret *= 16;
		ret += hexDigits.indexOf(str.charAt(start + 1));
		return ret;
	}

	/**
	 * @param c The color to get the name of
	 * @return The name of the given color, or null if the color is not named
	 */
	public static String getColorName(Color c) {
		return theColorNames.get(c);
	}

	/**
	 * @param c The color to print
	 * @return The name of the given color, or its hex RGB value (e.g. "#006400") otherwise
	 */
	public static String toString(Color c) {
		String ret = theColorNames.get(c);
		if (ret != null) {
			return ret;
		}
		StringBuilder sb = new StringBuilder();
		sb.append('#');
		sb.append(hexDigits.charAt(c.getRed() >>> 4)).append(hexDigits.charAt(c.getRed() & 0xf));
		sb.append(hexDigits.charAt(c.getGreen() >>> 4)).append(hexDigits.charAt(c.getGreen() & 0xf));
		sb.append(hexDigits.charAt(c.getBlue() >>> 4)).append(hexDigits.charAt(c.getBlue() & 0xf));
		return sb.toString();
	}

	/**
	 * Serializes a color to its HTML markup (e.g. "#ff0000" for red)
	 * 
	 * @param c The color to serialize
	 * @return The HTML markup of the color
	 */
	public static String toHTML(Color c) {
		String ret = "#";
		String hex;
		hex = Integer.toHexString(c.getRed());
		if (hex.length() < 2)
			hex = "0" + hex;
		ret += hex;
		hex = Integer.toHexString(c.getGreen());
		if (hex.length() < 2)
			hex = "0" + hex;
		ret += hex;
		hex = Integer.toHexString(c.getBlue());
		if (hex.length() < 2)
			hex = "0" + hex;
		ret += hex;
		return ret;
	}

	/**
	 * Serializes a color to its HTML markup <b>plus its alpha value</b> (e.g. "#ff000000" for red)
	 * 
	 * @param c The color to serialize
	 * @return The HTML markup plus alpha of the color
	 */
	public static String toHTMLA(java.awt.Color c) {
		String ret = toHTML(c);
		String hex = Integer.toHexString(c.getAlpha());
		if (hex.length() < 2)
			hex = "0" + hex;
		return ret + hex;
	}

	/**
	 * Parses a java.awt.Color from an HTML color string in the form '#RRGGBB' where RR, GG, and BB are the red, green, and blue bytes in
	 * hexadecimal form
	 * 
	 * @param htmlColor The HTML color string to parse
	 * @return The java.awt.Color represented by the HTML color string
	 */
	public static Color fromHTML(String htmlColor) {
		if (htmlColor.charAt(0) != '#')
			throw new IllegalArgumentException(htmlColor + " is not an HTML color string");
		int r, g, b, a = -1;
		if (htmlColor.length() == 9)
			a = Integer.parseInt(htmlColor.substring(7, 9), 16);
		else if (htmlColor.length() != 7)
			throw new IllegalArgumentException(htmlColor + " is not an HTML color string");
		r = Integer.parseInt(htmlColor.substring(1, 3), 16);
		g = Integer.parseInt(htmlColor.substring(3, 5), 16);
		b = Integer.parseInt(htmlColor.substring(5, 7), 16);
		if (a >= 0)
			return new java.awt.Color(r, g, b, a);
		else
			return new java.awt.Color(r, g, b);
	}

	/**
	 * Performs a somewhat subjective analysis of a color to determine how dark it looks to a user
	 * 
	 * @param color The color to analyze
	 * @return The darkness of the color
	 */
	public static float getDarkness(Color color) {
		float ret = color.getRed() + color.getGreen() + color.getBlue() / 10;
		ret /= (255 + 255 + 255 / 10);
		ret = 1 - ret;
		final float lightDarkBorder = 0.7f;
		if (ret > lightDarkBorder)
			ret = 0.5f + (ret - lightDarkBorder) * 0.5f / (1 - lightDarkBorder);
		else
			ret = ret * 0.5f / lightDarkBorder;
		return ret;
	}

	/**
	 * Lightens a color by a given amount
	 * 
	 * @param color The color to lighten
	 * @param amount The amount to lighten the color. 0 will leave the color unchanged; 1 will make the color completely white
	 * @return The bleached color
	 */
	public static Color bleach(Color color, float amount) {
		int r = (int) ((color.getRed() * (1 - amount) / 255 + amount) * 255);
		int g = (int) ((color.getGreen() * (1 - amount) / 255 + amount) * 255);
		int b = (int) ((color.getBlue() * (1 - amount) / 255 + amount) * 255);
		return new Color(r, g, b);
	}

	/**
	 * Darkens a color by a given amount
	 * 
	 * @param color The color to darken
	 * @param amount The amount to darken the color. 0 will leave the color unchanged; 1 will make the color completely black
	 * @return The stained color
	 */
	public static Color stain(Color color, float amount) {
		int r = (int) ((color.getRed() * (1 - amount) / 255) * 255);
		int g = (int) ((color.getGreen() * (1 - amount) / 255) * 255);
		int b = (int) ((color.getBlue() * (1 - amount) / 255) * 255);
		return new Color(r, g, b);
	}

	/**
	 * @param a The first color to merge
	 * @param b The second color to merge
	 * @param amount The amount to favor <code>b</code> over <code>a</b>.  A value of zero here (or less) would cause this method to
	 * return <code>a</code>. A value of one (or greater) would cause it to return <code>b</code>. In between zero and one will create a
	 *        color somewhere between the two colors.
	 * @return The merged color
	 */
	public static Color merge(Color a, Color b, float amount) {
		if (amount <= 0)
			return a;
		else if (amount >= 1)
			return b;
		return new Color(//
			a.getRed() + Math.round(amount * (b.getRed() - a.getRed())), //
			a.getGreen() + Math.round(amount * (b.getGreen() - a.getGreen())), //
			a.getBlue() + Math.round(amount * (b.getBlue() - a.getBlue()))//
		);
	}

	/**
	 * Generates an RGB color hexagon, with tips that are (from left, clockwise) red, yellow, green, cyan, blue, and magenta. The middle of
	 * the hexagon is white. The purpose of the hexagon is to be placed in a color editor (like prisms.widget.ColorPicker in js) to allow a
	 * user to choose any color. This method also generates a plain black hexagon and a black-and-white patterned hexagon of the same sizes
	 * to help with shading and alpha channel. This method writes the files to PNG image files within the given directory
	 * 
	 * @param dim The largest dimension (width) of the image to generate
	 * @param directory The directory to write the files to
	 */
	public static void generateColorHexagons(int dim, File directory) {
		class ColorMetadata {
			int height;

			int width;

			float sideLength;

			float sqrt3;

			int background;

			ColorMetadata(int _dim) {
				height = _dim;
				width = (int) Math.ceil(_dim * Math.sqrt(3) / 2);
				sideLength = height / 2.0f;
				sqrt3 = (float) Math.sqrt(3);
				background = 0x00000000;
			}

			int getRGB(int x, int y) {
				x -= width / 2 + 1;
				y -= height / 2;
				y = -y;

				float r, g, b;
				if (x >= 0) {
					if (y >= x / sqrt3) {
						r = 1;
						g = 1 - (y - x / sqrt3) / sideLength;
						b = 1 - (y + x / sqrt3) / sideLength;
					} else {
						r = 1 - (x / sqrt3 - y) / sideLength;
						g = 1;
						b = 1 - 2 * x / sqrt3 / sideLength;
					}
				} else {
					if (y >= -x / sqrt3) {
						r = 1;
						g = 1 - (y - x / sqrt3) / sideLength;
						b = 1 - (y + x / sqrt3) / sideLength;
					} else {
						r = 1 + (y + x / sqrt3) / sideLength;
						g = 1 + 2 * x / sqrt3 / sideLength;
						b = 1;
					}
				}

				if (r < 0 || r > 1 || g < 0 || g > 1 || b < 0 || b > 1)
					return background;
				int ret = 0xFF000000;
				ret |= Math.round(r * 255) << 16;
				ret |= Math.round(g * 255) << 8;
				ret |= Math.round(b * 255);
				return ret;
			}

			boolean shaded = true;

			int getAlphaHex(int x, int y, int rgb) {
				if (shaded)
					return getShadedHex(x, y, rgb);
				else
					return getCheckeredHex(x, y, rgb);
			}

			int mod = 64;

			int getShadedHex(int x, int y, int rgb) {
				if (rgb == background)
					return rgb;
				int r = (rgb & 0xFF0000) >> 16;
				int g = (rgb & 0xFF00) >> 8;
				int b = rgb & 0xFF;

				if (r == 255) {
					if (g == 255 || b == 255)
						return 0xFF000000;
					r = mod;
					g = mod - ((g + 1) % mod);
					b = mod - ((b + 1) % mod);
					if (g >= mod / 2)
						g = mod - g;
					g *= 2;
					if (b >= mod / 2)
						b = mod - b;
					b *= 2;
				} else if (g == 255) {
					if (b == 255)
						return 0xFF000000;
					g = mod;
					r = mod - ((r + 1) % mod);
					b = mod - ((b + 1) % mod);
					if (r >= mod / 2)
						r = mod - r;
					r *= 2;
					if (b >= mod / 2)
						b = mod - b;
					b *= 2;
				} else {
					b = mod;
					r = mod - ((r + 1) % mod);
					g = mod - ((g + 1) % mod);
					if (r >= mod / 2)
						r = mod - r;
					r *= 2;
					if (g >= mod / 2)
						g = mod - g;
					g *= 2;
				}
				float intens = r * 1.0f * g * b / (float) Math.pow(mod, 3);
				intens = (float) Math.pow(intens, 0.5);
				// intens = ((intens + 0.5f) * (intens + 0.5f) - .25f) / 2.25f;

				int dark = Math.round(intens * 255);
				return 0xFF000000 | (dark << 16) | (dark << 8) | dark;
			}

			int getCheckeredHex(int x, int y, int rgb) {
				if (rgb == background)
					return rgb;
				int r = (rgb & 0xFF0000) >> 16;
				int g = (rgb & 0xFF00) >> 8;
				int b = rgb & 0xFF;

				if (r <= 4 || b <= 4 || g <= 4)
					return 0xFF000000;

				int bwg;
				if (r == 255)
					bwg = ((g / mod) + (b / mod)) % 3;
				else if (g == 255)
					bwg = ((r / mod) + (b / mod) + 1) % 3;
				else
					bwg = ((r / mod) + (g / mod) + 2) % 3;
				if (bwg == 0)
					return 0xFF000000;
				else if (bwg == 1)
					return 0xFFFFFFFF;
				else
					return 0xFF808080;
			}
		}

		ColorMetadata md = new ColorMetadata(dim);
		BufferedImage colorImg = new BufferedImage(md.height, md.width, BufferedImage.TYPE_INT_ARGB);
		BufferedImage blackImg = new BufferedImage(md.height, md.width, BufferedImage.TYPE_INT_ARGB);
		BufferedImage bwImg = new BufferedImage(md.height, md.width, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < md.width; y++)
			for (int x = 0; x < md.height; x++) {
				int rgb = md.getRGB(md.width - y, x);
				colorImg.setRGB(x, y, rgb);
				if (rgb != md.background)
					blackImg.setRGB(x, y, 0xFF000000);
				else
					blackImg.setRGB(x, y, rgb);
				if (rgb == md.background)
					bwImg.setRGB(x, y, rgb);
				else {
					bwImg.setRGB(x, y, md.getAlphaHex(md.width - y, x, rgb));
				}
			}
		try {
			FileOutputStream out = new FileOutputStream(new File(directory, "ColorHexagon.png"));
			ImageIO.write(colorImg, "png", out);
			out.close();

			out = new FileOutputStream(new File(directory, "ShadeHexagon.png"));
			ImageIO.write(blackImg, "png", out);
			out.close();

			out = new FileOutputStream(new File(directory, "AlphaHexagon.png"));
			ImageIO.write(bwImg, "png", out);
			out.close();
		} catch (IOException e) {
			throw new IllegalStateException("Could not write image files to " + directory.getPath(), e);
		}
	}

	/**
	 * Generates an image of a sphere with a light shining on it. I used this to generate greenDot.png and redDot.png in ObServe's images.
	 * 
	 * @param diameter The diameter of the sphere, i.e. the height and width of the image, in pixels
	 * @param lightSourcePhi The angle of the light source away from the line between the sphere center and the eye, in radians
	 * @param lightSourceTheta The angle of the light source, in degrees East of North
	 * @param sphereColor The color of the sphere
	 * @param lightColor The color of the light
	 * @param luminance The luminance of the light, i.e. how much the light "washes out" the color of the sphere
	 * @return The image
	 */
	public static BufferedImage generateSpecularDot(int diameter, double lightSourcePhi, double lightSourceTheta, Color sphereColor,
		Color lightColor, double luminance) {
		BufferedImage image = new BufferedImage(diameter, diameter, BufferedImage.TYPE_4BYTE_ABGR);

		// Calculate the direction of light from the source to the sphere (assuming infinitely far away), then reverse it
		double lightSourceDz = Math.cos(lightSourcePhi);
		double sinLSPhi = Math.sin(lightSourcePhi);
		double lightSourceDx = Math.sin(lightSourceTheta) * sinLSPhi;
		double lightSourceDy = Math.cos(lightSourceTheta) * sinLSPhi;
		int radius = diameter / 2;
		int dRed = lightColor.getRed() - sphereColor.getRed();
		int dGreen = lightColor.getGreen() - sphereColor.getGreen();
		int dBlue = lightColor.getBlue() - sphereColor.getBlue();
		int dAlpha = lightColor.getAlpha() - sphereColor.getAlpha();
		for (int y = 0; y < diameter; y++) {
			int y2 = radius - y;
			for (int x = 0; x < diameter; x++) {
				int x2 = x - radius;
				double r = Math.sqrt(y2 * y2 + x2 * x2);
				if (r > radius) {
					image.setRGB(x, y, 0);
					continue;
				}
				// This is the reflection angle of the ray from the "eye" (positioned infinitely far from the sphere)
				// reflecting off the sphere away from the line between the eye and the sphere center
				double phi = 2 * Math.asin(r / radius);
				double theta = Math.PI / 2 - Math.atan2(y2, x2);
				// The Z axis is from the sphere center to the eye
				double reflectDz = Math.cos(phi);
				double sinPhi = Math.sin(phi);
				double reflectDx = Math.sin(theta) * sinPhi;
				double reflectDy = Math.cos(theta) * sinPhi;
				double dot = reflectDx * lightSourceDx + reflectDy * lightSourceDy + reflectDz * lightSourceDz;
				double bleach = luminance * dot;
				Color pixel;
				if (bleach < 0.0)
					pixel = sphereColor;
				else if (bleach >= 1.0)
					pixel = lightColor;
				else
					pixel = new Color(//
						sphereColor.getRed() + (int) (dRed * bleach), //
						sphereColor.getGreen() + (int) (dGreen * bleach), //
						sphereColor.getBlue() + (int) (dBlue * bleach), //
						sphereColor.getAlpha() + (int) (dAlpha * bleach));
				image.setRGB(x, y, pixel.getRGB());
			}
		}
		return image;
	}

	private enum MainMethodType {
		ColorHex, SpecularDot
	}

	/**
	 * The main method. Calls {@link #generateColorHexagons(int, File)} with default values to generate a set of hexagons for use in a color
	 * editor into the current working directory, or {@link #generateSpecularDot(int, double, double, Color, Color, double)} to generate an
	 * image of a sphere with light shining on it.
	 * 
	 * @param clArgs Command-line arguments. Call empty for documentation
	 */
	public static void main(String[] clArgs) {
		ArgumentParsing2.Arguments args = ArgumentParsing2.build()//
			.forValuePattern(patt -> patt//
				.addEnumArgument("type", MainMethodType.class, a -> a.required())//
				.addFileArgument("dir", a -> a.directory(true).create(true)//
					.when("type", MainMethodType.class, a2 -> a2.matches(v -> v.eq(MainMethodType.ColorHex)).required())//
					.when("type", MainMethodType.class, a2 -> a2.matches(v -> v.neq(MainMethodType.ColorHex)).forbidden()))//
				.addIntArgument("size", a -> a.constrain(v -> v.gte(10))//
					.when("type", MainMethodType.class, a2 -> a2.matches(v -> v.eq(MainMethodType.ColorHex)).required())//
					.when("type", MainMethodType.class, a2 -> a2.matches(v -> v.neq(MainMethodType.ColorHex)).forbidden()))//
				.addIntArgument("diameter", a -> a.constrain(v -> v.gte(5))//
					.when("type", MainMethodType.class, a2 -> a2.matches(v -> v.eq(MainMethodType.SpecularDot)).required())//
					.when("type", MainMethodType.class, a2 -> a2.matches(v -> v.neq(MainMethodType.SpecularDot)).forbidden()))//
				.addDoubleArgument("light-source-phi", a -> a.constrain(v -> v.between(-180.0, 180.0)).defaultValue(75.0)//
					.when("type", MainMethodType.class, a2 -> a2.matches(v -> v.neq(MainMethodType.SpecularDot)).forbidden()))//
				.addDoubleArgument("light-source-theta", a -> a.constrain(v -> v.between(-180.0, 360.0)).defaultValue(-30.0)//
					.when("type", MainMethodType.class, a2 -> a2.matches(v -> v.neq(MainMethodType.SpecularDot)).forbidden()))//
				.addArgument("sphere-color", Color.class, (text, otherArgs) -> Colors.parseColor(text), a -> a//
					.when("type", MainMethodType.class, a2 -> a2.matches(v -> v.eq(MainMethodType.SpecularDot)).required())//
					.when("type", MainMethodType.class, a2 -> a2.matches(v -> v.neq(MainMethodType.SpecularDot)).forbidden()))//
				.addArgument("light-color", Color.class, (text, otherArgs) -> Colors.parseColor(text), a -> a.defaultValue(Color.white)//
					.when("type", MainMethodType.class, a2 -> a2.matches(v -> v.neq(MainMethodType.SpecularDot)).forbidden()))//
				.addDoubleArgument("luminance", a -> a.constrain(v -> v.between(0.0, 1000.0)).defaultValue(1.0)//
					.when("type", MainMethodType.class, a2 -> a2.matches(v -> v.neq(MainMethodType.SpecularDot)).forbidden()))//
				.addFileArgument("file", a -> a.directory(false)//
					.when("type", MainMethodType.class, a2 -> a2.matches(v -> v.eq(MainMethodType.SpecularDot)).required())//
					.when("type", MainMethodType.class, a2 -> a2.matches(v -> v.neq(MainMethodType.SpecularDot)).forbidden()))//
			)//
			.build().parse(clArgs);
		switch (args.get("type", MainMethodType.class)) {
		case ColorHex:
			generateColorHexagons(args.get("size", int.class), args.get("dir", File.class));
			break;
		case SpecularDot:
			BufferedImage image = generateSpecularDot(args.get("diameter", int.class), //
				args.get("light-source-phi", double.class) / 180 * Math.PI, args.get("light-source-theta", double.class) / 180 * Math.PI, //
				args.get("sphere-color", Color.class), args.get("light-color", Color.class), args.get("luminance", double.class));
			try (FileOutputStream out = new FileOutputStream(args.get("file", File.class))) {
				ImageIO.write(image, "png", out);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}

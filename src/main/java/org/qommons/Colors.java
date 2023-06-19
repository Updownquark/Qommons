package org.qommons;

import java.awt.Color;
import java.awt.Point;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.qommons.io.Format;

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
 * <p>
 * In addition to {@link #transparent} (which is not available via {@link #parseColor(String)}), the constants in this class are:
 * <table>
 * <tr>
 * <td style="background-color:#000000;color:white">black</td>
 * <td>Basic black, #000000</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ffffff;color:black">white</td>
 * <td>Basic white, #ffffff</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ff0000;color:white">red</td>
 * <td>Basic red, #ff0000</td>
 * </tr>
 * <tr>
 * <td style="background-color:#00ff00;color:black">lime</td>
 * <td>&quot;Lime&quot; is basic green from HTML. &quot;Green&quot; is a darker color. #00ff00</td>
 * </tr>
 * <tr>
 * <td style="background-color:#0000ff;color:white">blue</td>
 * <td>Basic blue, #0000ff</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ffff00;color:black">yellow</td>
 * <td>Basic yellow, a mix of full red and green, #ffff00</td>
 * </tr>
 * <tr>
 * <td style="background-color:#00ffff;color:black">cyan/aqua</td>
 * <td>Basic cyan, a mix of full green and blue, #00ffff</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ff00ff;color:black">magenta/fuchsia</td>
 * <td>Basic magenta, a mix of full red and blue, #ff00ff</td>
 * </tr>
 * <tr>
 * <td style="background-color:#800000;color:white">maroon</td>
 * <td>A darker red, #800000</td>
 * </tr>
 * <tr>
 * <td style="background-color:#008000;color:white">green</td>
 * <td>A darker green than {@link java.awt.Color#green}. Use {@link #lime} for that green. #008000</td>
 * </tr>
 * <tr>
 * <td style="background-color:#000080;color:white">navy</td>
 * <td>A darker blue, #000080</td>
 * </tr>
 * <tr>
 * <td style="background-color:#808000;color:white">olive</td>
 * <td>A darker yellow, #808000</td>
 * </tr>
 * <tr>
 * <td style="background-color:#008080;color:white">teal</td>
 * <td>A darker cyan: #008080</td>
 * </tr>
 * <tr>
 * <td style="background-color:#800080;color:white">purple</td>
 * <td>A darker magenta: #800080</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ffa500;color:white">orange</td>
 * <td>#ffa500</td>
 * </tr>
 * <tr>
 * <td style="background-color:#a52a2a;color:white">brown</td>
 * <td>a52a2a</td>
 * </tr>
 * <tr>
 * <td style="background-color:#f0f8ff;color:black">alice blue</td>
 * <td>f0f8ff</td>
 * </tr>
 * <tr>
 * <td style="background-color:#faebd7;color:black">antique white</td>
 * <td>faebd7</td>
 * </tr>
 * <tr>
 * <td style="background-color:#7fffd4;color:black">aquamarine</td>
 * <td>7fffd4</td>
 * </tr>
 * <tr>
 * <td style="background-color:#f0ffff;color:black">azure</td>
 * <td>#f0ffff</td>
 * </tr>
 * <tr>
 * <td style="background-color:#f5f5dc;color:black">beige</td>
 * <td>#f5f5dc</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ffe4c4;color:black">bisque</td>
 * <td>#ffe4c4</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ffebcd;color:black">blanched almond</td>
 * <td>A very light brown: #ffebcd</td>
 * </tr>
 * <tr>
 * <td style="background-color:#8a2be2;color:white">blue violet</td>
 * <td>Lilac color: #8a2be2</td>
 * </tr>
 * <tr>
 * <td style="background-color:#deb887;color:white">burly wood</td>
 * <td>A light brown: #deb887</td>
 * </tr>
 * <tr>
 * <td style="background-color:#5f9ea0;color:white">cadet blue</td>
 * <td>#5f9ea0</td>
 * </tr>
 * <tr>
 * <td style="background-color:#7fff00;color:black">chartreuse</td>
 * <td>A bright green #7fff00</td>
 * </tr>
 * <tr>
 * <td style="background-color:#d2691e;color:white">chocolate</td>
 * <td>An orangey brown #d2691e</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ff7f50;color:white">coral</td>
 * <td>#ff7f50</td>
 * </tr>
 * <tr>
 * <td style="background-color:#6495ed;color:white">cornflower blue</td>
 * <td>#6495ed</td>
 * </tr>
 * <tr>
 * <td style="background-color:#fff8dc;color:black">corn silk</td>
 * <td>#fff8dc</td>
 * </tr>
 * <tr>
 * <td style="background-color:#dc143c;color:white">crimson</td>
 * <td>#dc143c</td>
 * </tr>
 * <tr>
 * <td style="background-color:#00008b;color:white">dark blue</td>
 * <td>#00008b</td>
 * </tr>
 * <tr>
 * <td style="background-color:#008b8b;color:white">dark cyan</td>
 * <td>#008b8b</td>
 * </tr>
 * <tr>
 * <td style="background-color:#b8860b;color:white">dark goldenrod</td>
 * <td>#b8860b</td>
 * </tr>
 * <tr>
 * <td style="background-color:#a9a9a9;color:white">dark gray</td>
 * <td>#a9a9a9</td>
 * </tr>
 * <tr>
 * <td style="background-color:#006400;color:white">dark green</td>
 * <td>#006400</td>
 * </tr>
 * <tr>
 * <td style="background-color:#bdb76b;color:white">dark khaki</td>
 * <td>#bdb76b</td>
 * </tr>
 * <tr>
 * <td style="background-color:#8b008b;color:white">dark magenta</td>
 * <td>#8b008b</td>
 * </tr>
 * <tr>
 * <td style="background-color:#556b2f;color:white">dark olive green</td>
 * <td>#556b2f</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ff8c00;color:white">dark orange</td>
 * <td>#ff8c00</td>
 * </tr>
 * <tr>
 * <td style="background-color:#9932cc;color:white">dark orchid</td>
 * <td>#9932cc</td>
 * </tr>
 * <tr>
 * <td style="background-color:#8b0000;color:white">dark red</td>
 * <td>#8b0000</td>
 * </tr>
 * <tr>
 * <td style="background-color:#e9967a;color:white">dark salmon</td>
 * <td>#e9967a</td>
 * </tr>
 * <tr>
 * <td style="background-color:#8fbc8f;color:white">dark sea green</td>
 * <td>#8fbc8f</td>
 * </tr>
 * <tr>
 * <td style="background-color:#483d8b;color:white">dark slate blue</td>
 * <td>#483d8b</td>
 * </tr>
 * <tr>
 * <td style="background-color:#2f4f4f;color:white">dark slate gray</td>
 * <td>#2f4f4f</td>
 * </tr>
 * <tr>
 * <td style="background-color:#00ced1;color:white">dark turquoise</td>
 * <td>#00ced1</td>
 * </tr>
 * <tr>
 * <td style="background-color:#9400d3;color:white">dark violet</td>
 * <td>#9400d3</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ff1493;color:white">deep pink</td>
 * <td>#ff1493</td>
 * </tr>
 * <tr>
 * <td style="background-color:#00bfff;color:white">deep sky blue</td>
 * <td>#00bfff</td>
 * </tr>
 * <tr>
 * <td style="background-color:#696969;color:white">dim gray</td>
 * <td>#696969</td>
 * </tr>
 * <tr>
 * <td style="background-color:#1e90ff;color:white">dodger blue</td>
 * <td>#1e90ff</td>
 * </tr>
 * <tr>
 * <td style="background-color:#b22222;color:white">fire brick</td>
 * <td>#b22222</td>
 * </tr>
 * <tr>
 * <td style="background-color:#fffaf0;color:black">floral white</td>
 * <td>#fffaf0</td>
 * </tr>
 * <tr>
 * <td style="background-color:#228b22;color:white">forest green</td>
 * <td>#228b22</td>
 * </tr>
 * <tr>
 * <td style="background-color:#dcdcdc;color:black">gainsboro</td>
 * <td>#dcdcdc</td>
 * </tr>
 * <tr>
 * <td style="background-color:#f8f8ff;color:black">ghost white</td>
 * <td>#f8f8ff</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ffd700;color:white">gold</td>
 * <td>#ffd700</td>
 * </tr>
 * <tr>
 * <td style="background-color:#daa520;color:white">goldenrod</td>
 * <td>#daa520</td>
 * </tr>
 * <tr>
 * <td style="background-color:#808080;color:white">gray</td>
 * <td>#808080</td>
 * </tr>
 * <tr>
 * <td style="background-color:#adff2f;color:black">green yellow</td>
 * <td>#adff2f</td>
 * </tr>
 * <tr>
 * <td style="background-color:#f0fff0;color:black">honeydew</td>
 * <td>#f0fff0</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ff69b4;color:white">hot pink</td>
 * <td>#ff69b4</td>
 * </tr>
 * <tr>
 * <td style="background-color:#cd5c5c;color:white">indian red</td>
 * <td>#cd5c5c</td>
 * </tr>
 * <tr>
 * <td style="background-color:#4b0082;color:white">indigo</td>
 * <td>#4b0082</td>
 * </tr>
 * <tr>
 * <td style="background-color:#fffff0;color:black">ivory</td>
 * <td>#fffff0</td>
 * </tr>
 * <tr>
 * <td style="background-color:#f0e68c;color:black">khaki</td>
 * <td>#f0e68c</td>
 * </tr>
 * <tr>
 * <td style="background-color:#e6e6fa;color:black">lavender</td>
 * <td>#e6e6fa</td>
 * </tr>
 * <tr>
 * <td style="background-color:#fff0f5;color:black">lavender blush</td>
 * <td>#fff0f5</td>
 * </tr>
 * <tr>
 * <td style="background-color:#7cfc00;color:black">lawn green</td>
 * <td>#7cfc00</td>
 * </tr>
 * <tr>
 * <td style="background-color:#fffacd;color:black">lemon chiffon</td>
 * <td>#fffacd</td>
 * </tr>
 * <tr>
 * <td style="background-color:#add8e6;color:black">light blue</td>
 * <td>#add8e6</td>
 * </tr>
 * <tr>
 * <td style="background-color:#f08080;color:white">light coral</td>
 * <td>#f08080</td>
 * </tr>
 * <tr>
 * <td style="background-color:#e0ffff;color:black">light cyan</td>
 * <td>#e0ffff</td>
 * </tr>
 * <tr>
 * <td style="background-color:#fafad2;color:black">light goldenrod yellow</td>
 * <td>#fafad2</td>
 * </tr>
 * <tr>
 * <td style="background-color:#d3d3d3;color:black">light gray</td>
 * <td>#d3d3d3</td>
 * </tr>
 * <tr>
 * <td style="background-color:#90ee90;color:black">light green</td>
 * <td>#90ee90</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ffb6c1;color:white">light pink</td>
 * <td>#ffb6c1</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ffa07a;color:white">light salmon</td>
 * <td>#ffa07a</td>
 * </tr>
 * <tr>
 * <td style="background-color:#20b2aa;color:white">light sea green</td>
 * <td>#20b2aa</td>
 * </tr>
 * <tr>
 * <td style="background-color:#87cefa;color:black">light sky blue</td>
 * <td>#87cefa</td>
 * </tr>
 * <tr>
 * <td style="background-color:#778899;color:white">light slate gray</td>
 * <td>#778899</td>
 * </tr>
 * <tr>
 * <td style="background-color:#b0c4de;color:black">light steel blue</td>
 * <td>#b0c4de</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ffffe0;color:black">light yellow</td>
 * <td>#ffffe0</td>
 * </tr>
 * <tr>
 * <td style="background-color:#32cd32;color:white">lime green</td>
 * <td>#32cd32</td>
 * </tr>
 * <tr>
 * <td style="background-color:#faf0e6;color:black">linen</td>
 * <td>#faf0e6</td>
 * </tr>
 * <tr>
 * <td style="background-color:#66cdaa;color:white">medium aquamarine</td>
 * <td>#66cdaa</td>
 * </tr>
 * <tr>
 * <td style="background-color:#0000cd;color:white">medium blue</td>
 * <td>#0000cd</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ba55d3;color:white">medium orchid</td>
 * <td>#ba55d3</td>
 * </tr>
 * <tr>
 * <td style="background-color:#9370d8;color:white">medium purple</td>
 * <td>#9370d8</td>
 * </tr>
 * <tr>
 * <td style="background-color:#3cb371;color:white">medium sea green</td>
 * <td>#3cb371</td>
 * </tr>
 * <tr>
 * <td style="background-color:#7b68ee;color:white">medium slate blue</td>
 * <td>#7b68ee</td>
 * </tr>
 * <tr>
 * <td style="background-color:#00fa9a;color:black">medium spring green</td>
 * <td>#00fa9a</td>
 * </tr>
 * <tr>
 * <td style="background-color:#48d1cc;color:white">medium turquoise</td>
 * <td>#48d1cc</td>
 * </tr>
 * <tr>
 * <td style="background-color:#c71585;color:white">medium violet red</td>
 * <td>#c71585</td>
 * </tr>
 * <tr>
 * <td style="background-color:#191970;color:white">midnight blue</td>
 * <td>#191970</td>
 * </tr>
 * <tr>
 * <td style="background-color:#f5fffa;color:black">mint cream</td>
 * <td>#f5fffa</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ffe4e1;color:black">misty rose</td>
 * <td>#ffe4e1</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ffe4b5;color:black">moccasin</td>
 * <td>#ffe4b5</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ffdead;color:black">navajo white</td>
 * <td>#ffdead</td>
 * </tr>
 * <tr>
 * <td style="background-color:#fdf5e6;color:black">old lace</td>
 * <td>#fdf5e6</td>
 * </tr>
 * <tr>
 * <td style="background-color:#6b8e23;color:white">olive drab</td>
 * <td>#6b8e23</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ff4500;color:white">orange red</td>
 * <td>#ff4500</td>
 * </tr>
 * <tr>
 * <td style="background-color:#da70d6;color:white">orchid</td>
 * <td>#da70d6</td>
 * </tr>
 * <tr>
 * <td style="background-color:#eee8aa;color:black">pale goldenrod</td>
 * <td>#eee8aa</td>
 * </tr>
 * <tr>
 * <td style="background-color:#98fb98;color:black">pale green</td>
 * <td>#98fb98</td>
 * </tr>
 * <tr>
 * <td style="background-color:#afeeee;color:black">pale turquoise</td>
 * <td>#afeeee</td>
 * </tr>
 * <tr>
 * <td style="background-color:#d87093;color:white">pale violet red</td>
 * <td>#d87093</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ffefd5;color:black">papaya whip</td>
 * <td>#ffefd5</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ffdab9;color:black">peach puff</td>
 * <td>#ffdab9</td>
 * </tr>
 * <tr>
 * <td style="background-color:#cd853f;color:white">peru</td>
 * <td>#cd853f</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ffc0cb;color:black">pink</td>
 * <td>#ffc0cb</td>
 * </tr>
 * <tr>
 * <td style="background-color:#dda0dd;color:black">plum</td>
 * <td>#dda0dd</td>
 * </tr>
 * <tr>
 * <td style="background-color:#b0e0e6;color:black">powder blue</td>
 * <td>#b0e0e6</td>
 * </tr>
 * <tr>
 * <td style="background-color:#bc8f8f;color:white">rosy brown</td>
 * <td>#bc8f8f</td>
 * </tr>
 * <tr>
 * <td style="background-color:#4169e1;color:white">royal blue</td>
 * <td>#4169e1</td>
 * </tr>
 * <tr>
 * <td style="background-color:#8b4513;color:white">saddle brown</td>
 * <td>#8b4513</td>
 * </tr>
 * <tr>
 * <td style="background-color:#fa8072;color:white">salmon</td>
 * <td>#fa8072</td>
 * </tr>
 * <tr>
 * <td style="background-color:#f4a460;color:white">sandy brown</td>
 * <td>#f4a460</td>
 * </tr>
 * <tr>
 * <td style="background-color:#2e8b57;color:white">sea green</td>
 * <td>#2e8b57</td>
 * </tr>
 * <tr>
 * <td style="background-color:#fff5ee;color:black">sea shell</td>
 * <td>#fff5ee</td>
 * </tr>
 * <tr>
 * <td style="background-color:#a0522d;color:white">sienna</td>
 * <td>#a0522d</td>
 * </tr>
 * <tr>
 * <td style="background-color:#c0c0c0;color:black">silver</td>
 * <td>#c0c0c0</td>
 * </tr>
 * <tr>
 * <td style="background-color:#a7ceeb;color:black">sky blue</td>
 * <td>#a7ceeb</td>
 * </tr>
 * <tr>
 * <td style="background-color:#6a5acd;color:white">slate blue</td>
 * <td>#6a5acd</td>
 * </tr>
 * <tr>
 * <td style="background-color:#708090;color:white">slate gray</td>
 * <td>#708090</td>
 * </tr>
 * <tr>
 * <td style="background-color:#fffafa;color:black">snow</td>
 * <td>#fffafa</td>
 * </tr>
 * <tr>
 * <td style="background-color:#00ff7f;color:black">spring green</td>
 * <td>#00ff7f</td>
 * </tr>
 * <tr>
 * <td style="background-color:#4682b4;color:white">steel blue</td>
 * <td>#4682b4</td>
 * </tr>
 * <tr>
 * <td style="background-color:#d2b4bc;color:black">tan</td>
 * <td>#d2b4bc</td>
 * </tr>
 * <tr>
 * <td style="background-color:#d8bfd8;color:black">thistle</td>
 * <td>#d8bfd8</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ff6347;color:white">tomato</td>
 * <td>#ff6347</td>
 * </tr>
 * <tr>
 * <td style="background-color:#40e0d0;color:black">turquoise</td>
 * <td>#40e0d0</td>
 * </tr>
 * <tr>
 * <td style="background-color:#ee82ee;color:white">violet</td>
 * <td>#ee82ee</td>
 * </tr>
 * <tr>
 * <td style="background-color:#f5deb3;color:black">wheat</td>
 * <td>#f5deb3</td>
 * </tr>
 * <tr>
 * <td style="background-color:#f5f5f5;color:black">white smoke</td>
 * <td>#f5f5f5</td>
 * </tr>
 * <tr>
 * <td style="background-color:#9acd32;color:black">yellow green</td>
 * <td>#9acd32</td>
 * </tr>
 * </table>
 * </ul>
 * </p>
 */
public class Colors {
	private Colors() {
	}

	private static final Pattern HTML_RGB;
	private static final Pattern HTML_HSB;
	private static final Pattern RGB_FN;
	private static final Pattern HSB_FN;
	static {
		HTML_RGB = Pattern.compile("\\#(?<hex>[0-9a-fA-F]{6,8})");
		HTML_HSB = Pattern.compile("\\$(?<hex>[0-9a-fA-F]{6,8})");
		RGB_FN = Pattern.compile("[rR][gG][bB]\\(\\s*"//
			+ "(?<r>\\d{1,3})\\s*,"//
			+ "\\s*(?<g>\\d{1,3})\\s*,"//
			+ "\\s*(?<b>\\d{1,3})\\s*" //
			+ "(?:,\\s*(?<a>\\d{1,3})\\s*)?"//
			+ "\\)");
		HSB_FN = Pattern.compile("[hH][sS][bB]\\(\\s*"//
			+ "(?<h>\\d*\\.?\\d*\\%?)\\s*,"//
			+ "\\s*(?<s>\\d*\\.?\\d*\\%?)\\s*,"//
			+ "\\s*(?<b>\\d*\\.?\\d*\\%?)\\s*" //
			+ "(?:,\\s*(?<a>\\d*\\.?\\d*\\%?)\\s*)?"//
			+ "\\)");
	}

	/** Not available via {@link #parseColor(String)}, this is a code utility constant representing a completely transparent color */
	public static final transient Color transparent = new Color(0, 0, 0, 0);

	/** Basic black, #000000 <span style="font-weight:bolder;color:black">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color black = Color.black;

	/** Basic white, #ffffff <span style="font-weight:bolder;color:white">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color white = Color.white;

	/** Basic red, #ff0000 <span style="font-weight:bolder;color:red">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color red = Color.red;

	/**
	 * "Lime" is basic green from HTML. "Green" is a darker color. #00ff00
	 * <span style="font-weight:bolder;color:green">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span>
	 */
	public static final Color lime = Color.green;

	/** Basic blue, #0000ff <span style="font-weight:bolder;color:blue">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color blue = Color.blue;

	/**
	 * Basic yellow, a mix of full red and green, #ffff00
	 * <span style="font-weight:bolder;color:yellow">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span>
	 */
	public static final Color yellow = Color.yellow;

	/**
	 * Basic cyan, a mix of full green and blue, #00ffff
	 * <span style="font-weight:bolder;color:cyan">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span>
	 */
	public static final Color cyan = Color.cyan;

	/** Same as cyan, #00ffff <span style="font-weight:bolder;color:aqua">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color aqua = cyan;

	/**
	 * Basic magenta, a mix of full red and blue, #ff00ff
	 * <span style="font-weight:bolder;color:magenta">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span>
	 */
	public static final Color magenta = Color.magenta;

	/** Same as magenta, #ff00ff <span style="font-weight:bolder;color:fuchsia">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color fuchsia = magenta;

	/** A darker red, #800000 <span style="font-weight:bolder;color:maroon">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color maroon = new Color(128, 0, 0);

	/**
	 * A darker green than {@link java.awt.Color#green}. Use {@link #lime} for that green. #008000
	 * <span style="font-weight:bolder;color:#008000">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span>
	 */
	public static final Color green = new Color(0, 128, 0);

	/** A darker blue, #000080 <span style="font-weight:bolder;color:navy">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color navy = new Color(0, 0, 128);

	/** A darker yellow, #808000 <span style="font-weight:bolder;color:olive">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color olive = new Color(128, 128, 0);

	/** A darker cyan, <span style="font-weight:bolder;color:teal">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color teal = new Color(0, 128, 128);

	/** A darker magenta, <span style="font-weight:bolder;color:purple">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color purple = new Color(128, 0, 128);

	/** #ffa500 <span style="font-weight:bolder;color:#ffa500">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color orange = _parseColor("#ffa500");

	/** #a52a2a" <span style="font-weight:bolder;color:#a52a2a">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color brown = _parseColor("#a52a2a");

	/** #f0f8ff <span style="font-weight:bolder;color:#f0f8ff">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color aliceBlue = _parseColor("#f0f8ff");

	/** #faebd7 <span style="font-weight:bolder;color:#faebd7">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color antiqueWhite = _parseColor("#faebd7");

	/** #7fffd4 <span style="font-weight:bolder;color:#7fffd4">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color aquamarine = _parseColor("#7fffd4");

	/** #f0ffff <span style="font-weight:bolder;color:#f0ffff">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color azure = _parseColor("#f0ffff");

	/** #f5f5dc <span style="font-weight:bolder;color:#f5f5dc">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color beige = _parseColor("#f5f5dc");

	/** #ffe4c4 <span style="font-weight:bolder;color:#ffe4c4">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color bisque = _parseColor("#ffe4c4");

	/** A very light brown #ffebcd <span style="font-weight:bolder;color:#ffebcd">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color blanchedAlmond = _parseColor("#ffebcd");

	/** Lilac color #8a2be2 <span style="font-weight:bolder;color:#8a2be2">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color blueViolet = _parseColor("#8a2be2");

	/** A light brown #deb887 <span style="font-weight:bolder;color:#deb887">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color burlyWood = _parseColor("#deb887");

	/** #5f9ea0 <span style="font-weight:bolder;color:#5f9ea0">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color cadetBlue = _parseColor("#5f9ea0");

	/** A bright green #7fff00 <span style="font-weight:bolder;color:#7fff00">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color chartreuse = _parseColor("#7fff00");

	/** An orangey brown #d2691e <span style="font-weight:bolder;color:#d2691e">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color chocolate = _parseColor("#d2691e");

	/** #ff7f50 <span style="font-weight:bolder;color:#ff7f50">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color coral = _parseColor("#ff7f50");

	/** #6495ed <span style="font-weight:bolder;color:#6495ed">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color cornflowerBlue = _parseColor("#6495ed");

	/** #fff8dc <span style="font-weight:bolder;color:#fff8dc">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color cornsilk = _parseColor("#fff8dc");

	/** #dc143c <span style="font-weight:bolder;color:#dc143c">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color crimson = _parseColor("#dc143c");

	/** #00008b <span style="font-weight:bolder;color:#00008b">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color darkBlue = _parseColor("#00008b");

	/** #008b8b <span style="font-weight:bolder;color:#008b8b">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color darkCyan = _parseColor("#008b8b");

	/** #b8860b <span style="font-weight:bolder;color:#b8860b">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color darkGoldenrod = _parseColor("#b8860b");

	/** #a9a9a9 <span style="font-weight:bolder;color:#a9a9a9">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color darkGray = _parseColor("#a9a9a9");

	/** #006400 <span style="font-weight:bolder;color:#006400">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color darkGreen = _parseColor("#006400");

	/** #bdb76b <span style="font-weight:bolder;color:#bdb76b">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color darkKhaki = _parseColor("#bdb76b");

	/** #8b008b <span style="font-weight:bolder;color:#8b008b">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color darkMagenta = _parseColor("#8b008b");

	/** #556b2f <span style="font-weight:bolder;color:#556b2f">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color darkOliveGreen = _parseColor("#556b2f");

	/** #ff8c00 <span style="font-weight:bolder;color:#ff8c00">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color darkOrange = _parseColor("#ff8c00");

	/** #9932cc <span style="font-weight:bolder;color:#9932cc">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color darkOrchid = _parseColor("#9932cc");

	/** #8b0000 <span style="font-weight:bolder;color:#8b0000">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color darkRed = _parseColor("#8b0000");

	/** #e9967a <span style="font-weight:bolder;color:#e9967a">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color darkSalmon = _parseColor("#e9967a");

	/** #8fbc8f <span style="font-weight:bolder;color:#8fbc8f">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color darkSeaGreen = _parseColor("#8fbc8f");

	/** #483d8b <span style="font-weight:bolder;color:#483d8b">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color darkSlateBlue = _parseColor("#483d8b");

	/** #2f4f4f <span style="font-weight:bolder;color:#2f4f4f">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color darkSlateGray = _parseColor("#2f4f4f");

	/** #00ced1 <span style="font-weight:bolder;color:#00ced1">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color darkTurquoise = _parseColor("#00ced1");

	/** #9400d3 <span style="font-weight:bolder;color:#9400d3">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color darkViolet = _parseColor("#9400d3");

	/** #ff1493 <span style="font-weight:bolder;color:#ff1493">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color deepPink = _parseColor("#ff1493");

	/** #00bfff <span style="font-weight:bolder;color:#00bfff">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color deepSkyBlue = _parseColor("#00bfff");

	/** #696969 <span style="font-weight:bolder;color:#696969">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color dimGray = _parseColor("#696969");

	/** #1e90ff <span style="font-weight:bolder;color:#1e90ff">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color dodgerBlue = _parseColor("#1e90ff");

	/** #b22222 <span style="font-weight:bolder;color:#b22222">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color fireBrick = _parseColor("#b22222");

	/** #fffaf0 <span style="font-weight:bolder;color:#fffaf0">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color floralWhite = _parseColor("#fffaf0");

	/** #228b22 <span style="font-weight:bolder;color:#228b22">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color forestGreen = _parseColor("#228b22");

	/** #dcdcdc <span style="font-weight:bolder;color:#dcdcdc">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color gainsboro = _parseColor("#dcdcdc");

	/** #f8f8ff <span style="font-weight:bolder;color:#f8f8ff">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color ghostWhite = _parseColor("#f8f8ff");

	/** #ffd700 <span style="font-weight:bolder;color:#ffd700">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color gold = _parseColor("#ffd700");

	/** #daa520 <span style="font-weight:bolder;color:#daa520">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color goldenrod = _parseColor("#daa520");

	/** #808080 <span style="font-weight:bolder;color:#808080">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color gray = _parseColor("#808080");

	/** #adff2f <span style="font-weight:bolder;color:#adff2f">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color greenYellow = _parseColor("#adff2f");

	/** #f0fff0 <span style="font-weight:bolder;color:#f0fff0">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color honeydew = _parseColor("#f0fff0");

	/** #ff69b4 <span style="font-weight:bolder;color:#ff69b4">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color hotPink = _parseColor("#ff69b4");

	/** #cd5c5c <span style="font-weight:bolder;color:#cd5c5c">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color indianRed = _parseColor("#cd5c5c");

	/** #4b0082 <span style="font-weight:bolder;color:#4b0082">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color indigo = _parseColor("#4b0082");

	/** #fffff0 <span style="font-weight:bolder;color:#fffff0">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color ivory = _parseColor("#fffff0");

	/** #f0e68c <span style="font-weight:bolder;color:#f0e68c">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color khaki = _parseColor("#f0e68c");

	/** #e6e6fa <span style="font-weight:bolder;color:#e6e6fa">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color lavender = _parseColor("#e6e6fa");

	/** #fff0f5 <span style="font-weight:bolder;color:#fff0f5">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color lavenderBlush = _parseColor("#fff0f5");

	/** #7cfc00 <span style="font-weight:bolder;color:#7cfc00">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color lawnGreen = _parseColor("#7cfc00");

	/** #fffacd <span style="font-weight:bolder;color:#fffacd">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color lemonChiffon = _parseColor("#fffacd");

	/** #add8e6 <span style="font-weight:bolder;color:#add8e6">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color lightBlue = _parseColor("#add8e6");

	/** #f08080 <span style="font-weight:bolder;color:#f08080">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color lightCoral = _parseColor("#f08080");

	/** #e0ffff <span style="font-weight:bolder;color:#e0ffff">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color lightCyan = _parseColor("#e0ffff");

	/** #fafad2 <span style="font-weight:bolder;color:#fafad2">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color lightGoldenrodYellow = _parseColor("#fafad2");

	/** #d3d3d3 <span style="font-weight:bolder;color:#d3d3d3">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color lightGray = _parseColor("#d3d3d3");

	/** #90ee90 <span style="font-weight:bolder;color:#90ee90">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color lightGreen = _parseColor("#90ee90");

	/** #ffb6c1 <span style="font-weight:bolder;color:#ffb6c1">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color lightPink = _parseColor("#ffb6c1");

	/** #ffa07a <span style="font-weight:bolder;color:#ffa07a">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color lightSalmon = _parseColor("#ffa07a");

	/** #20b2aa <span style="font-weight:bolder;color:#20b2aa">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color lightSeaGreen = _parseColor("#20b2aa");

	/** #87cefa <span style="font-weight:bolder;color:#87cefa">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color lightSkyBlue = _parseColor("#87cefa");

	/** #778899 <span style="font-weight:bolder;color:#778899">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color lightSlateGray = _parseColor("#778899");

	/** #b0c4de <span style="font-weight:bolder;color:#b0c4de">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color lightSteelBlue = _parseColor("#b0c4de");

	/** #ffffe0 <span style="font-weight:bolder;color:#ffffe0">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color lightYellow = _parseColor("#ffffe0");

	/** #32cd32 <span style="font-weight:bolder;color:#32cd32">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color limeGreen = _parseColor("#32cd32");

	/** #faf0e6 <span style="font-weight:bolder;color:#faf0e6">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color linen = _parseColor("#faf0e6");

	/** #66cdaa <span style="font-weight:bolder;color:#66cdaa">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color mediumAquamarine = _parseColor("#66cdaa");

	/** #0000cd <span style="font-weight:bolder;color:#0000cd">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color mediumBlue = _parseColor("#0000cd");

	/** #ba55d3 <span style="font-weight:bolder;color:#ba55d3">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color mediumOrchid = _parseColor("#ba55d3");

	/** #9370d8 <span style="font-weight:bolder;color:#9370d8">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color mediumPurple = _parseColor("#9370d8");

	/** #3cb371 <span style="font-weight:bolder;color:#3cb371">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color mediumSeaGreen = _parseColor("#3cb371");

	/** #7b68ee <span style="font-weight:bolder;color:#7b68ee">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color mediumSlateBlue = _parseColor("#7b68ee");

	/** #00fa9a <span style="font-weight:bolder;color:#00fa9a">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color mediumSpringGreen = _parseColor("#00fa9a");

	/** #48d1cc <span style="font-weight:bolder;color:#48d1cc">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color mediumTurquoise = _parseColor("#48d1cc");

	/** #c71585 <span style="font-weight:bolder;color:#c71585">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color mediumVioletRed = _parseColor("#c71585");

	/** #191970 <span style="font-weight:bolder;color:#191970">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color midnightBlue = _parseColor("#191970");

	/** #f5fffa <span style="font-weight:bolder;color:#f5fffa">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color mintCream = _parseColor("#f5fffa");

	/** #ffe4e1 <span style="font-weight:bolder;color:#ffe4e1">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color mistyRose = _parseColor("#ffe4e1");

	/** #ffe4b5 <span style="font-weight:bolder;color:#ffe4b5">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color moccasin = _parseColor("#ffe4b5");

	/** #ffdead <span style="font-weight:bolder;color:#ffdead">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color navajoWhite = _parseColor("#ffdead");

	/** #fdf5e6 <span style="font-weight:bolder;color:#fdf5e6">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color oldLace = _parseColor("#fdf5e6");

	/** #6b8e23 <span style="font-weight:bolder;color:#6b8e23">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color oliveDrab = _parseColor("#6b8e23");

	/** #ff4500 <span style="font-weight:bolder;color:#ff4500">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color orangeRed = _parseColor("#ff4500");

	/** #da70d6 <span style="font-weight:bolder;color:#da70d6">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color orchid = _parseColor("#da70d6");

	/** #eee8aa <span style="font-weight:bolder;color:#eee8aa">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color paleGoldenrod = _parseColor("#eee8aa");

	/** #98fb98 <span style="font-weight:bolder;color:#98fb98">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color paleGreen = _parseColor("#98fb98");

	/** #afeeee <span style="font-weight:bolder;color:#afeeee">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color paleTurquoise = _parseColor("#afeeee");

	/** #d87093 <span style="font-weight:bolder;color:#d87093">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color paleVioletRed = _parseColor("#d87093");

	/** #ffefd5 <span style="font-weight:bolder;color:#ffefd5">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color papayaWhip = _parseColor("#ffefd5");

	/** #ffdab9 <span style="font-weight:bolder;color:#ffdab9">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color peachPuff = _parseColor("#ffdab9");

	/** #cd853f <span style="font-weight:bolder;color:#cd853f">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color peru = _parseColor("#cd853f");

	/** #ffc0cb <span style="font-weight:bolder;color:#ffc0cb">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color pink = _parseColor("#ffc0cb");

	/** #dda0dd <span style="font-weight:bolder;color:#dda0dd">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color plum = _parseColor("#dda0dd");

	/** #b0e0e6 <span style="font-weight:bolder;color:#b0e0e6">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color powderBlue = _parseColor("#b0e0e6");

	/** #bc8f8f <span style="font-weight:bolder;color:#bc8f8f">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color rosyBrown = _parseColor("#bc8f8f");

	/** #4169e1 <span style="font-weight:bolder;color:#4169e1">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color royalBlue = _parseColor("#4169e1");

	/** #8b4513 <span style="font-weight:bolder;color:#8b4513">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color saddleBrown = _parseColor("#8b4513");

	/** #fa8072 <span style="font-weight:bolder;color:#fa8072">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color salmon = _parseColor("#fa8072");

	/** #f4a460 <span style="font-weight:bolder;color:#f4a460">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color sandyBrown = _parseColor("#f4a460");

	/** #2e8b57 <span style="font-weight:bolder;color:#2e8b57">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color seaGreen = _parseColor("#2e8b57");

	/** #fff5ee <span style="font-weight:bolder;color:#fff5ee">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color seaShell = _parseColor("#fff5ee");

	/** #a0522d <span style="font-weight:bolder;color:#a0522d">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color sienna = _parseColor("#a0522d");

	/** #c0c0c0 <span style="font-weight:bolder;color:#c0c0c0">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color silver = _parseColor("#c0c0c0");

	/** #a7ceeb <span style="font-weight:bolder;color:#a7ceeb">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color skyBlue = _parseColor("#a7ceeb");

	/** #6a5acd <span style="font-weight:bolder;color:#6a5acd">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color slateBlue = _parseColor("#6a5acd");

	/** #708090 <span style="font-weight:bolder;color:#708090">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color slateGray = _parseColor("#708090");

	/** #fffafa <span style="font-weight:bolder;color:#fffafa">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color snow = _parseColor("#fffafa");

	/** #00ff7f <span style="font-weight:bolder;color:#00ff7f">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color springGreen = _parseColor("#00ff7f");

	/** #4682b4 <span style="font-weight:bolder;color:#4682b4">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color steelBlue = _parseColor("#4682b4");

	/** #d2b4bc <span style="font-weight:bolder;color:#d2b4bc">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color tan = _parseColor("#d2b4bc");

	/** #d8bfd8 <span style="font-weight:bolder;color:#d8bfd8">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color thistle = _parseColor("#d8bfd8");

	/** #ff6347 <span style="font-weight:bolder;color:#ff6347">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color tomato = _parseColor("#ff6347");

	/** #40e0d0 <span style="font-weight:bolder;color:#40e0d0">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color turquoise = _parseColor("#40e0d0");

	/** #ee82ee <span style="font-weight:bolder;color:#ee82ee">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color violet = _parseColor("#ee82ee");

	/** #f5deb3 <span style="font-weight:bolder;color:#f5deb3">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color wheat = _parseColor("#f5deb3");

	/** #f5f5f5 <span style="font-weight:bolder;color:#f5f5f5">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color whiteSmoke = _parseColor("#f5f5f5");

	/** #9acd32 <span style="font-weight:bolder;color:#9acd32">&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;&#x2588;</span> */
	public static final Color yellowGreen = _parseColor("#9acd32");

	private static final Map<String, Color> NAMED_COLORS;

	private static final Map<Color, String> COLOR_NAMES;

	static {
		// This code needs to live below all the color constants so they have values when this runs
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
				String colorName = StringUtils.parseByCase(field.getName(), true).toKebabCase();
				namedColors.put(colorName, value);
				colorNames.put(value, colorName);
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		NAMED_COLORS = Collections.unmodifiableMap(namedColors);
		COLOR_NAMES = Collections.unmodifiableMap(colorNames);
	}

	/** A Format to parse/format colors */
	public static final Format<Color> FORMAT = new Format<Color>() {
		@Override
		public void append(StringBuilder text, Color value) {
			if (value != null)
				text.append(Colors.toString(value));
		}

		@Override
		public Color parse(CharSequence text) throws ParseException {
			return Colors.parseColor(text.toString());
		}
	};

	/** A Format to parse/format colors as HTML hex representation */
	public static final Format<Color> HTML_FORMAT = new Format<Color>() {
		@Override
		public void append(StringBuilder text, Color value) {
			if (value != null)
				text.append(Colors.toHTML(value));
		}

		@Override
		public Color parse(CharSequence text) throws ParseException {
			return Colors.fromHTML(text.toString());
		}
	};

	/** A Format to parse/format colors by name */
	public static final Format<Color> NAME_FORMAT = new Format<Color>() {
		@Override
		public void append(StringBuilder text, Color value) {
			if (value != null) {
				String name = getColorName(value);
				if (name != null)
					text.append(name);
			}
		}

		@Override
		public Color parse(CharSequence text) throws ParseException {
			Color color = getColorByName(text.toString());
			if (color == null)
				throw new ParseException("Unrecognized color name: " + text, 0);
			return Colors.fromHTML(text.toString());
		}
	};

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
		Matcher m = HTML_RGB.matcher(str);
		if (m.matches()) {
			int value = Integer.parseUnsignedInt(m.group("hex"), 16);
			switch (m.group("hex").length()) {
			case 6:
				return new Color(value);
			case 8:
				value = Integer.rotateRight(value, 8);
				return new Color(value, true);
			default:
				throw new ParseException("HTML color must be specified with either six or eight hexadecimal digits", m.end("hex"));
			}
		}

		m = HTML_HSB.matcher(str);
		if (m.matches()) {
			int value = Integer.parseUnsignedInt(m.group("hex"), 16);
			boolean withAlpha = false;
			switch (m.group("hex").length()) {
			case 6:
				value <<= 8;
				break;
			case 8:
				withAlpha = true;
				break;
			default:
				throw new ParseException("HTML HSB color must be specified with either six or eight hexadecimal digits", m.end("hex"));
			}
			Color color = Color.getHSBColor(//
				((value >> 24) & 0xff) * 1.0f / 255, //
				((value >> 16) & 0xff) * 1.0f / 255, //
				((value >> 8) & 0xff) * 1.0f / 255);
			if (withAlpha)
				color = new Color((color.getRGB() & 0x00ffffff) | (value << 24), true);
			return color;
		}

		m = RGB_FN.matcher(str);
		if (m.matches()) {
			int r = Integer.parseInt(m.group("r"));
			if (r > 255)
				throw new ParseException("RGB values must be between 0 and 255", m.start("r"));

			int g = Integer.parseInt(m.group("g"));
			if (g > 255)
				throw new ParseException("RGB values must be between 0 and 255", m.start("g"));

			int b = Integer.parseInt(m.group("b"));
			if (b > 255)
				throw new ParseException("RGB values must be between 0 and 255", m.start("b"));

			Color color = new Color(r, g, b);

			String aStr = m.group("a");
			if (aStr != null) {
				int a = Integer.parseInt(aStr);
				if (a > 255)
					throw new ParseException("RGB values must be between 0 and 255", m.start("a"));
				color = new Color((color.getRGB() & 0x00ffffff) | (a << 24), true);
			}

			return color;
		}

		m = HSB_FN.matcher(str);
		if (m.matches()) {
			String fs = m.group("h");
			boolean percent = fs.endsWith("%");
			if (percent)
				fs = fs.substring(0, fs.length() - 1);
			float h = Float.parseFloat(fs);
			if (percent)
				h /= 100;
			if (h > 1.0f)
				throw new ParseException("HSB values must be between 0 and 1", m.start("h"));

			fs = m.group("s");
			percent = fs.endsWith("%");
			if (percent)
				fs = fs.substring(0, fs.length() - 1);
			float s = Float.parseFloat(fs);
			if (percent)
				s /= 100;
			if (s > 1.0f)
				throw new ParseException("HSB values must be between 0 and 1", m.start("s"));

			fs = m.group("b");
			percent = fs.endsWith("%");
			if (percent)
				fs = fs.substring(0, fs.length() - 1);
			float b = Float.parseFloat(fs);
			if (percent)
				b /= 100;
			if (b > 1.0f)
				throw new ParseException("HSB values must be between 0 and 1", m.start("b"));

			Color color = Color.getHSBColor(h, s, b);

			String aStr = m.group("a");
			if (aStr != null) {
				percent = aStr.endsWith("%");
				if (percent)
					aStr = aStr.substring(0, aStr.length() - 1);
				float a = Float.parseFloat(aStr);
				if (percent)
					a /= 100;
				if (a > 1.0f)
					throw new ParseException("HSB values must be between 0 and 1", m.start("a"));
				color = new Color((color.getRGB() & 0x00ffffff) | (Math.round(a * 255) << 24), true);
			}

			return color;
		}

		if (NAMED_COLORS != null) // Will be null while parsing all the static field colors during class initialization
			return NAMED_COLORS.get(str);
		return null;
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
		return COLOR_NAMES.get(c);
	}

	/**
	 * @param name The name of the color to get
	 * @return The color with the given name, or null if the name is not known as a color by this class
	 */
	public static Color getColorByName(String name) {
		return NAMED_COLORS.get(name);
	}

	/**
	 * @param c The color to print
	 * @return The name of the given color, or its hex RGB value (e.g. "#006400") otherwise
	 */
	public static String toString(Color c) {
		if (c == null)
			return "null";
		String ret = COLOR_NAMES.get(c);
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
	 * @return The darkness of the color on a scale of 1 (completely black) to 0 (completely white)
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
	 * <p>
	 * A color that is stored with its hue/saturation/brightness components.
	 * </p>
	 * <p>
	 * This class allows modification of each component independently without affecting the others. This allows for optimum preservation of
	 * information, as well as maximum utility.
	 * </p>
	 */
	public static class HsbColor {
		private final float hue;
		private final float saturation;
		private final float brightness;
		private final int color;

		/**
		 * @param hue The color's hue
		 * @param saturation The color's saturation
		 * @param brightness The color's brightness
		 * @param alpha The color's alpha value
		 */
		public HsbColor(float hue, float saturation, float brightness, int alpha) {
			this.hue = hue;
			this.saturation = saturation;
			this.brightness = brightness;
			color = (Color.HSBtoRGB(hue, saturation, brightness) & 0x00ffffff) | (alpha << 24);
		}

		/**
		 * @param color The color to represent
		 * @param withAlpha Whether to use the given color's alpha value, or default it to 255
		 * @see Color#Color(int, boolean)
		 */
		public HsbColor(Color color, boolean withAlpha) {
			float[] hsb = color == null ? new float[] { 1.0f, 0.0f, 0.0f }
				: Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
			hue = hsb[0];
			saturation = hsb[1];
			brightness = hsb[2];
			if (color == null)
				this.color = Color.white.getRGB();
			else if (withAlpha)
				this.color = color.getRGB();
			else
				this.color = (color.getRGB() | 0xff000000);
		}

		/** @return The java color represented by this HSB color */
		public Color toColor() {
			return new Color(color, true);
		}

		/** @return The java color represented by this HSB color, disregarding alpha */
		public Color toOpaqueColor() {
			return new Color(color);
		}

		/** @return This color's red component */
		public int getRed() {
			return (color >> 16) & 0xff;
		}

		/** @return This color's green component */
		public int getGreen() {
			return (color >> 8) & 0xff;
		}

		/** @return This color's blue component */
		public int getBlue() {
			return color & 0xff;
		}

		/** @return This color's alpha value */
		public int getAlpha() {
			return color >>> 24;
		}

		/**
		 * @return This color's red/green/blue value, with alpha. (Bits 24-31 are alpha, 16-23 are red, 8-15 are green, 0-7 are blue).
		 * @see Color#getRGB()
		 */
		public int getRGB() {
			return color;
		}

		/** @return This color's hue */
		public float getHue() {
			return hue;
		}

		/** @return This color's saturation */
		public float getSaturation() {
			return saturation;
		}

		/** @return This color's brightness */
		public float getBrightness() {
			return brightness;
		}

		/**
		 * @param hue The hue for the new color
		 * @return A new color identical to this one except for the hue
		 */
		public HsbColor setHue(float hue) {
			if (hue == this.hue)
				return this;
			return new HsbColor(hue, saturation, brightness, getAlpha());
		}

		/**
		 * @param saturation The saturation for the new color
		 * @return A new color identical to this one except for the saturation
		 */
		public HsbColor setSaturation(float saturation) {
			if (saturation == this.saturation)
				return this;
			return new HsbColor(hue, saturation, brightness, getAlpha());
		}

		/**
		 * @param brightness The brightness for the new color
		 * @return A new color identical to this one except for the brightness
		 */
		public HsbColor setBrightness(float brightness) {
			if (brightness == this.brightness)
				return this;
			return new HsbColor(hue, saturation, brightness, getAlpha());
		}

		/**
		 * @param red The red component for the new color
		 * @return A new color identical to this one except for the red component
		 */
		public HsbColor setRed(int red) {
			if (getRed() == red)
				return this;
			float[] hsb = Color.RGBtoHSB(//
				red, //
				getGreen(), //
				getBlue(), //
				null);
			return new HsbColor(hsb[0], hsb[1], hsb[2], getAlpha());
		}

		/**
		 * @param green The green component for the new color
		 * @return A new color identical to this one except for the green component
		 */
		public HsbColor setGreen(int green) {
			if (green == getGreen())
				return this;
			float[] hsb = Color.RGBtoHSB(//
				getRed(), //
				green, //
				getBlue(), //
				null);
			return new HsbColor(hsb[0], hsb[1], hsb[2], getAlpha());
		}

		/**
		 * @param blue The blue component for the new color
		 * @return A new color identical to this one except for the blue component
		 */
		public HsbColor setBlue(int blue) {
			if (blue == getBlue())
				return this;
			float[] hsb = Color.RGBtoHSB(//
				getRed(), //
				getGreen(), //
				blue, //
				null);
			return new HsbColor(hsb[0], hsb[1], hsb[2], getAlpha());
		}

		/**
		 * @param alpha The alpha value for the new color
		 * @return A new color identical to this one except for the alpha value
		 */
		public HsbColor setAlpha(int alpha) {
			if (alpha == getAlpha())
				return this;
			return new HsbColor(hue, saturation, brightness, alpha);
		}

		/** @return Whether this component has any transparency (i.e. whether it's {@link #getAlpha() alpha value} is less than 255) */
		public boolean isTransparent() {
			return (color & 0xff000000) != 0xff000000;
		}

		/**
		 * @param red The red component for the new color
		 * @param green The green component for the new color
		 * @param blue The blue component for the new color
		 * @return A new color with the given red/green/blue components and this color's {@link #getAlpha() alpha value}
		 */
		public HsbColor setRGB(int red, int green, int blue) {
			if (red == getRed() && green == getGreen() && blue == getBlue())
				return this;
			float[] hsb = Color.RGBtoHSB(red, green, blue, null);
			return new HsbColor(hsb[0], hsb[1], hsb[2], getAlpha());
		}

		/**
		 * @param color The RGB value of the color to set
		 * @return A new color whose {@link #getHue() hue} and {@link #getSaturation() saturation} are defined by the given color, but with
		 *         this color's {@link #getBrightness() brightness} and {@link #getAlpha() alpha value}
		 */
		public HsbColor setHueAndSaturationFrom(int color) {
			int r = (color >> 16) & 0xff;
			int g = (color >> 8) & 0xff;
			int b = color & 0xff;
			float[] hsb = Color.RGBtoHSB(r, g, b, null);
			if (hsb[0] == hue && hsb[1] == saturation)
				return this;
			return new HsbColor(hsb[0], hsb[1], brightness, getAlpha());
		}

		/**
		 * @param otherColor The color to apply this color's {@link #getAlpha() alpha value} to
		 * @return A new color identical to the given color but with this color's {@link #getAlpha() alpha value}
		 */
		public Color applyAlpha(Color otherColor) {
			if (otherColor.getAlpha() == getAlpha())
				return otherColor;
			return new Color(otherColor.getRed(), otherColor.getGreen(), otherColor.getBlue(), getAlpha());
		}

		@Override
		public int hashCode() {
			return color;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof HsbColor))
				return false;
			HsbColor other = (HsbColor) obj;
			return color == other.color;
		}

		@Override
		public String toString() {
			return Colors.toString(new Color(color, true));
		}
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
		ColorHex md = new ColorHex(dim);
		BufferedImage[] images = md.genColorHexImages();
		BufferedImage colorImg = images[0];
		BufferedImage blackImg = images[1];
		BufferedImage bwImg = images[2];
		try {
			try (FileOutputStream out = new FileOutputStream(new File(directory, "ColorHexagon.png"))) {
				ImageIO.write(colorImg, "png", out);
			}

			try (FileOutputStream out = new FileOutputStream(new File(directory, "ShadeHexagon.png"))) {
				ImageIO.write(blackImg, "png", out);
			}

			try (FileOutputStream out = new FileOutputStream(new File(directory, "AlphaHexagon.png"))) {
				ImageIO.write(bwImg, "png", out);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Could not write image files to " + directory.getPath(), e);
		}
	}

	/**
	 * <p>
	 * Represents a 2D hexagonal image that is a continuum of colors from red (left corner) to yellow (top-left corner) to green (top-right
	 * corner) to cyan (right corner) to blue (bottom-right corner) to magenta (bottom-left corner.
	 * </p>
	 * <p>
	 * This class is intended to support a color chooser user interface.
	 * </p>
	 */
	public static class ColorHex {
		private static final float SQRT3 = (float) Math.sqrt(3);
		private static final int CHECKER_SIZE = 64;

		private final int theWidth;
		private final int theHeight;

		private final float sideLength;

		private int theBackground;
		private boolean isShaded;

		/** @param width The width for the hexagon */
		public ColorHex(int width) {
			this.theWidth = width;
			theHeight = (int) Math.ceil(width * Math.sqrt(3) / 2);
			sideLength = width / 2.0f;
			theBackground = 0;
			isShaded = true;
		}

		/** @return The {@link Color#getRGB() RGB} value of the color to paint outside the hexagon */
		public int getBackground() {
			return theBackground;
		}

		/**
		 * @param background The {@link Color#getRGB() RGB} value of the color to paint outside the hexagon
		 * @return This color hex
		 */
		public ColorHex setBackground(int background) {
			this.theBackground = background;
			return this;
		}

		/** @return The hexagon's width */
		public int getWidth() {
			return theWidth;
		}

		/** @return The hexagon's height */
		public int getHeight() {
			return theHeight;
		}

		/** @return The length of one side of the hexagon */
		public float getSideLength() {
			return sideLength;
		}

		/**
		 * @return Whether the {@link #getAlphaHex(int, int, int)} method assumes a shading continuum or a simple checker pattern. The
		 *         default is true and this is recommended, as otherwise colors in front of the dark checker spots will appear starkly
		 *         different than those in front of light spots.
		 */
		public boolean isShaded() {
			return isShaded;
		}

		/**
		 * @param shaded Whether the {@link #getAlphaHex(int, int, int)} method assumes a shading continuum or a simple checker pattern
		 * @see #isShaded()
		 */
		public void setShaded(boolean shaded) {
			isShaded = shaded;
		}

		/**
		 * @param x The x coordinate in the image from the left
		 * @param y The y coordinate in the image <b>from the bottom</b>
		 * @return The color to paint for the colored hexagon at the given pixel
		 */
		public int getRGB(int x, int y) {
			float xf = -(x - theWidth / 2.0f), yf = y - theHeight / 2.0f;
			float r, g, b;
			if (yf >= 0) {
				if (xf >= yf / SQRT3) {
					r = 1;
					g = 1 - (xf - yf / SQRT3) / sideLength;
					b = 1 - (xf + yf / SQRT3) / sideLength;
				} else {
					r = 1 - (yf / SQRT3 - xf) / sideLength;
					g = 1;
					b = 1 - 2 * yf / SQRT3 / sideLength;
				}
			} else {
				if (xf >= -yf / SQRT3) {
					r = 1;
					g = 1 - (xf - yf / SQRT3) / sideLength;
					b = 1 - (xf + yf / SQRT3) / sideLength;
				} else {
					r = 1 + (xf + yf / SQRT3) / sideLength;
					g = 1 + 2 * yf / SQRT3 / sideLength;
					b = 1;
				}
			}

			int ri = Math.round(r * 255);
			int gi = Math.round(g * 255);
			int bi = Math.round(b * 255);
			if (ri < 0 || ri > 255 || gi < 0 || gi > 255 || bi < 0 || bi > 255)
				return theBackground;
			int ret = 0xFF000000;
			ret |= ri << 16;
			ret |= gi << 8;
			ret |= bi;
			return ret;
		}

		/**
		 * @param x The x coordinate in the image from the left
		 * @param y The y coordinate in the image <b>from the bottom</b>
		 * @param rgb The color of the colored hexagon at the given pixel
		 * @return The color to paint for the alpha hexagon at the given pixel
		 */
		public int getAlphaHex(int x, int y, int rgb) {
			if (isShaded)
				return getShadedHex(y, x, rgb);
			else
				return getCheckeredHex(y, x, rgb);
		}

		private int getShadedHex(int x, int y, int rgb) {
			if (rgb == theBackground)
				return rgb;
			int r = (rgb & 0xFF0000) >> 16;
			int g = (rgb & 0xFF00) >> 8;
			int b = rgb & 0xFF;

			if (r == 255) {
				if (g == 255 || b == 255)
					return 0xFF000000;
				r = CHECKER_SIZE;
				g = CHECKER_SIZE - ((g + 1) % CHECKER_SIZE);
				b = CHECKER_SIZE - ((b + 1) % CHECKER_SIZE);
				if (g >= CHECKER_SIZE / 2)
					g = CHECKER_SIZE - g;
				g *= 2;
				if (b >= CHECKER_SIZE / 2)
					b = CHECKER_SIZE - b;
				b *= 2;
			} else if (g == 255) {
				if (b == 255)
					return 0xFF000000;
				g = CHECKER_SIZE;
				r = CHECKER_SIZE - ((r + 1) % CHECKER_SIZE);
				b = CHECKER_SIZE - ((b + 1) % CHECKER_SIZE);
				if (r >= CHECKER_SIZE / 2)
					r = CHECKER_SIZE - r;
				r *= 2;
				if (b >= CHECKER_SIZE / 2)
					b = CHECKER_SIZE - b;
				b *= 2;
			} else {
				b = CHECKER_SIZE;
				r = CHECKER_SIZE - ((r + 1) % CHECKER_SIZE);
				g = CHECKER_SIZE - ((g + 1) % CHECKER_SIZE);
				if (r >= CHECKER_SIZE / 2)
					r = CHECKER_SIZE - r;
				r *= 2;
				if (g >= CHECKER_SIZE / 2)
					g = CHECKER_SIZE - g;
				g *= 2;
			}
			float intens = r * 1.0f * g * b / (float) Math.pow(CHECKER_SIZE, 3);
			intens = (float) Math.pow(intens, 0.5);
			// intens = ((intens + 0.5f) * (intens + 0.5f) - .25f) / 2.25f;

			int dark = Math.round(intens * 255);
			return 0xFF000000 | (dark << 16) | (dark << 8) | dark;
		}

		private int getCheckeredHex(int y, int x, int rgb) {
			if (rgb == theBackground)
				return rgb;
			int r = (rgb & 0xFF0000) >> 16;
			int g = (rgb & 0xFF00) >> 8;
			int b = rgb & 0xFF;

			if (r <= 4 || b <= 4 || g <= 4)
				return 0xFF000000;

			int bwg;
			if (r == 255)
				bwg = ((g / CHECKER_SIZE) + (b / CHECKER_SIZE)) % 3;
			else if (g == 255)
				bwg = ((r / CHECKER_SIZE) + (b / CHECKER_SIZE) + 1) % 3;
			else
				bwg = ((r / CHECKER_SIZE) + (g / CHECKER_SIZE) + 2) % 3;
			if (bwg == 0)
				return 0xFF000000;
			else if (bwg == 1)
				return 0xFFFFFFFF;
			else
				return 0xFF808080;
		}

		/**
		 * @param color The color to get the location of
		 * @return The pixel on this image whose color is closest to the given color
		 */
		public Point getLocation(Color color) {
			int x = theWidth / 2, y = theHeight / 2;
			int brightness = Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()));
			if (color.getRed() < brightness)
				x += Math.round(sideLength * (brightness - color.getRed()) / brightness);
			if (color.getGreen() < brightness) {
				float greenDist = sideLength * (brightness - color.getGreen()) / brightness;
				x -= Math.round(greenDist / 2);
				y += Math.round(greenDist * SQRT3 / 2);
			}
			if (color.getBlue() < brightness) {
				float blueDist = sideLength * (brightness - color.getBlue()) / brightness;
				x -= Math.round(blueDist / 2);
				y -= Math.round(blueDist * SQRT3 / 2);
			}
			return new Point(x, y);
		}

		/**
		 * Generates color, shading (black), and alpha hexagon images according to this hex's settings
		 * 
		 * @return The 3 images
		 */
		public BufferedImage[] genColorHexImages() {
			BufferedImage colorImg = new BufferedImage(theWidth, theHeight, BufferedImage.TYPE_INT_ARGB);
			BufferedImage blackImg = new BufferedImage(theWidth, theHeight, BufferedImage.TYPE_INT_ARGB);
			BufferedImage bwImg = new BufferedImage(theWidth, theHeight, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < theHeight; y++) {
				for (int x = 0; x < theWidth; x++) {
					int rgb = getRGB(x, theHeight - y);
					colorImg.setRGB(x, y, rgb);
					if (rgb != theBackground)
						blackImg.setRGB(x, y, 0xFF000000);
					else
						blackImg.setRGB(x, y, rgb);
					if (rgb == theBackground)
						bwImg.setRGB(x, y, rgb);
					else {
						bwImg.setRGB(x, y, getAlphaHex(x, theHeight - y, rgb));
					}
				}
			}
			return new BufferedImage[] { colorImg, blackImg, bwImg };
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
		ArgumentParsing.Arguments args = ArgumentParsing.build()//
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

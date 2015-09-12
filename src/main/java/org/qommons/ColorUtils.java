/*
 * ColorUtils.java Created Nov 17, 2010 by Andrew Butler, PSL
 */
package org.qommons;

import java.awt.Color;

/** A set of tools for analyzing and manipulating colors */
public class ColorUtils
{
	private static class ColorMetadata
	{
		int height;

		int width;

		float sideLength;

		float sqrt3;

		int background;

		ColorMetadata(int _dim)
		{
			height = _dim;
			width = (int) Math.ceil(_dim * Math.sqrt(3) / 2);
			sideLength = height / 2.0f;
			sqrt3 = (float) Math.sqrt(3);
			background = 0x00000000;
		}

		int getRGB(int x, int y)
		{
			x -= width / 2 + 1;
			y -= height / 2;
			y = -y;

			float r, g, b;
			if(x >= 0)
			{
				if(y >= x / sqrt3)
				{
					r = 1;
					g = 1 - (y - x / sqrt3) / sideLength;
					b = 1 - (y + x / sqrt3) / sideLength;
				}
				else
				{
					r = 1 - (x / sqrt3 - y) / sideLength;
					g = 1;
					b = 1 - 2 * x / sqrt3 / sideLength;
				}
			}
			else
			{
				if(y >= -x / sqrt3)
				{
					r = 1;
					g = 1 - (y - x / sqrt3) / sideLength;
					b = 1 - (y + x / sqrt3) / sideLength;
				}
				else
				{
					r = 1 + (y + x / sqrt3) / sideLength;
					g = 1 + 2 * x / sqrt3 / sideLength;
					b = 1;
				}
			}

			if(r < 0 || r > 1 || g < 0 || g > 1 || b < 0 || b > 1)
				return background;
			int ret = 0xFF000000;
			ret |= Math.round(r * 255) << 16;
			ret |= Math.round(g * 255) << 8;
			ret |= Math.round(b * 255);
			return ret;
		}

		boolean shaded = true;

		int getAlphaHex(int x, int y, int rgb)
		{
			if(shaded)
				return getShadedHex(x, y, rgb);
			else
				return getCheckeredHex(x, y, rgb);
		}

		int mod = 64;

		int getShadedHex(int x, int y, int rgb)
		{
			if(rgb == background)
				return rgb;
			int r = (rgb & 0xFF0000) >> 16;
			int g = (rgb & 0xFF00) >> 8;
			int b = rgb & 0xFF;

			if(r == 255)
			{
				if(g == 255 || b == 255)
					return 0xFF000000;
				r = mod;
				g = mod - ((g + 1) % mod);
				b = mod - ((b + 1) % mod);
				if(g >= mod / 2)
					g = mod - g;
				g *= 2;
				if(b >= mod / 2)
					b = mod - b;
				b *= 2;
			}
			else if(g == 255)
			{
				if(b == 255)
					return 0xFF000000;
				g = mod;
				r = mod - ((r + 1) % mod);
				b = mod - ((b + 1) % mod);
				if(r >= mod / 2)
					r = mod - r;
				r *= 2;
				if(b >= mod / 2)
					b = mod - b;
				b *= 2;
			}
			else
			{
				b = mod;
				r = mod - ((r + 1) % mod);
				g = mod - ((g + 1) % mod);
				if(r >= mod / 2)
					r = mod - r;
				r *= 2;
				if(g >= mod / 2)
					g = mod - g;
				g *= 2;
			}
			float intens = r * 1.0f * g * b / (float) Math.pow(mod, 3);
			intens = (float) Math.pow(intens, 0.5);
			// intens = ((intens + 0.5f) * (intens + 0.5f) - .25f) / 2.25f;

			int dark = Math.round(intens * 255);
			return 0xFF000000 | (dark << 16) | (dark << 8) | dark;
		}

		int getCheckeredHex(int x, int y, int rgb)
		{
			if(rgb == background)
				return rgb;
			int r = (rgb & 0xFF0000) >> 16;
			int g = (rgb & 0xFF00) >> 8;
			int b = rgb & 0xFF;

			if(r <= 4 || b <= 4 || g <= 4)
				return 0xFF000000;

			int bwg;
			if(r == 255)
				bwg = ((g / mod) + (b / mod)) % 3;
			else if(g == 255)
				bwg = ((r / mod) + (b / mod) + 1) % 3;
			else
				bwg = ((r / mod) + (g / mod) + 2) % 3;
			if(bwg == 0)
				return 0xFF000000;
			else if(bwg == 1)
				return 0xFFFFFFFF;
			else
				return 0xFF808080;
		}
	}

	private ColorUtils()
	{
	}

	/**
	 * Serializes a color to its HTML markup (e.g. "#ff0000" for red)
	 * 
	 * @param c The color to serialize
	 * @return The HTML markup of the color
	 */
	public static String toHTML(Color c)
	{
		String ret = "#";
		String hex;
		hex = Integer.toHexString(c.getRed());
		if(hex.length() < 2)
			hex = "0" + hex;
		ret += hex;
		hex = Integer.toHexString(c.getGreen());
		if(hex.length() < 2)
			hex = "0" + hex;
		ret += hex;
		hex = Integer.toHexString(c.getBlue());
		if(hex.length() < 2)
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
	public static String toHTMLA(java.awt.Color c)
	{
		String ret = toHTML(c);
		String hex = Integer.toHexString(c.getAlpha());
		if(hex.length() < 2)
			hex = "0" + hex;
		return ret + hex;
	}

	/**
	 * Parses a java.awt.Color from an HTML color string in the form '#RRGGBB' where RR, GG, and BB
	 * are the red, green, and blue bytes in hexadecimal form
	 * 
	 * @param htmlColor The HTML color string to parse
	 * @return The java.awt.Color represented by the HTML color string
	 */
	public static Color fromHTML(String htmlColor)
	{
		if(htmlColor.charAt(0) != '#')
			throw new IllegalArgumentException(htmlColor + " is not an HTML color string");
		int r, g, b, a = -1;
		if(htmlColor.length() == 9)
			a = Integer.parseInt(htmlColor.substring(7, 9), 16);
		else if(htmlColor.length() != 7)
			throw new IllegalArgumentException(htmlColor + " is not an HTML color string");
		r = Integer.parseInt(htmlColor.substring(1, 3), 16);
		g = Integer.parseInt(htmlColor.substring(3, 5), 16);
		b = Integer.parseInt(htmlColor.substring(5, 7), 16);
		if(a >= 0)
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
	public static float getDarkness(Color color)
	{
		float ret = color.getRed() + color.getGreen() + color.getBlue() / 10;
		ret /= (255 + 255 + 255 / 10);
		ret = 1 - ret;
		final float lightDarkBorder = 0.7f;
		if(ret > lightDarkBorder)
			ret = 0.5f + (ret - lightDarkBorder) * 0.5f / (1 - lightDarkBorder);
		else
			ret = ret * 0.5f / lightDarkBorder;
		return ret;
	}

	/**
	 * Lightens a color by a given amount
	 * 
	 * @param color The color to lighten
	 * @param amount The amount to lighten the color. 0 will leave the color unchanged; 1 will make
	 *        the color completely white
	 * @return The bleached color
	 */
	public static Color bleach(Color color, float amount)
	{
		int red = (int) ((color.getRed() * (1 - amount) / 255 + amount) * 255);
		int green = (int) ((color.getGreen() * (1 - amount) / 255 + amount) * 255);
		int blue = (int) ((color.getBlue() * (1 - amount) / 255 + amount) * 255);
		return new Color(red, green, blue);
	}

	/**
	 * Darkens a color by a given amount
	 * 
	 * @param color The color to darken
	 * @param amount The amount to darken the color. 0 will leave the color unchanged; 1 will make
	 *        the color completely black
	 * @return The stained color
	 */
	public static Color stain(Color color, float amount)
	{
		int red = (int) ((color.getRed() * (1 - amount) / 255) * 255);
		int green = (int) ((color.getGreen() * (1 - amount) / 255) * 255);
		int blue = (int) ((color.getBlue() * (1 - amount) / 255) * 255);
		return new Color(red, green, blue);
	}

	/**
	 * Generates an RGB color hexagon, with tips that are (from left, clockwise) red, yellow, green,
	 * cyan, blue, and magenta. The middle of the hexagon is white. The purpose of the hexagon is to
	 * be placed in a color editor (like prisms.widget.ColorPicker in js) to allow a user to choose
	 * any color. This method also generates a plain black hexagon and a black-and-white patterned
	 * hexagon of the same sizes to help with shading and alpha channel. This method writes the
	 * files to PNG image files within the given directory
	 * 
	 * @param dim The largest dimension (width) of the image to generate
	 * @param dirName The name of the directory to write the filesto
	 */
	public static void generateColorHexagons(int dim, String dirName)
	{
		if(dirName.charAt(dirName.length() - 1) != '/'
			&& dirName.charAt(dirName.length() - 1) != '\\')
			dirName += "/";
		ColorMetadata md = new ColorMetadata(dim);
		java.awt.image.BufferedImage colorImg = new java.awt.image.BufferedImage(md.height,
			md.width, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.image.BufferedImage blackImg = new java.awt.image.BufferedImage(md.height,
			md.width, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.image.BufferedImage bwImg = new java.awt.image.BufferedImage(md.height, md.width,
			java.awt.image.BufferedImage.TYPE_INT_ARGB);
		for(int y = 0; y < md.width; y++)
			for(int x = 0; x < md.height; x++)
			{
				int rgb = md.getRGB(md.width - y, x);
				colorImg.setRGB(x, y, rgb);
				if(rgb != md.background)
					blackImg.setRGB(x, y, 0xFF000000);
				else
					blackImg.setRGB(x, y, rgb);
				if(rgb == md.background)
					bwImg.setRGB(x, y, rgb);
				else
				{
					bwImg.setRGB(x, y, md.getAlphaHex(md.width - y, x, rgb));
				}
			}
		try
		{
			java.io.FileOutputStream out = new java.io.FileOutputStream(dirName
				+ "ColorHexagon.png");
			javax.imageio.ImageIO.write(colorImg, "png", out);
			out.close();

			out = new java.io.FileOutputStream(dirName + "ShadeHexagon.png");
			javax.imageio.ImageIO.write(blackImg, "png", out);
			out.close();

			out = new java.io.FileOutputStream(dirName + "AlphaHexagon.png");
			javax.imageio.ImageIO.write(bwImg, "png", out);
			out.close();
		} catch(java.io.IOException e)
		{
			throw new IllegalStateException("Could not write image files to " + dirName, e);
		}
	}

	/**
	 * The main method. Calls {@link #generateColorHexagons(int, String)} with default values to
	 * generate a set of hexagons for use in a color editor into the current working directory.
	 * 
	 * @param args Command-line arguments, ignored
	 */
	public static void main(String [] args)
	{
		generateColorHexagons(128, ".");
	}
}

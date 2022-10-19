package org.qommons;

import java.util.Random;

/** A randomness utility for testing */
public class TestUtil {
	private final long theSeed;
	private final Random theRandomness;
	private long theBytes;

	/** Creates a utility with a random seed */
	public TestUtil() {
		this(Long.reverse(System.nanoTime()) ^ System.nanoTime(), 0);
	}

	/**
	 * @param seed The seed for the randomness
	 * @param bytes The number of bytes to advance the randomness (for reproduction)
	 */
	public TestUtil(long seed, long bytes) {
		theSeed = seed;
		theRandomness = new Random(seed);
		while (theBytes < bytes - 7)
			getAnyLong();
		while (theBytes < bytes)
			getBoolean();
	}

	private void advance(int bytes) {
		long oldPos = theBytes;
		theBytes += bytes;
		advanced(oldPos, theBytes);
	}

	/**
	 * Can be overridden in subclasses to be notified of position advancement
	 * 
	 * @param oldPosition The position before the advancement
	 * @param newPosition The new position after the advancement
	 */
	protected void advanced(long oldPosition, long newPosition) {
	}

	/** @return The seed of the current test case */
	public long getSeed() {
		return theSeed;
	}

	/** @return The current random generation position */
	public long getPosition() {
		return theBytes;
	}

	/** @return A random integer between {@link Integer#MIN_VALUE} and {@link Integer#MAX_VALUE} */
	public int getAnyInt() {
		advance(4);
		return theRandomness.nextInt();
	}

	/**
	 * @param min The minimum
	 * @param max The maximum
	 * @return An integer <code>i</code> such that <code>min&lt;=i&lt;max</code>
	 */
	public int getInt(int min, int max) {
		if (min == max)
			return min;
		return min + Math.abs(getAnyInt() % (max - min));
	}

	/** @return A random long between {@link Long#MIN_VALUE} and {@link Long#MAX_VALUE} */
	public long getAnyLong() {
		advance(8);
		return theRandomness.nextLong();
	}

	/**
	 * @param min The minimum
	 * @param max The maximum
	 * @return A long <code>i</code> such that <code>min&lt;=i&lt;max</code>
	 */
	public long getLong(long min, long max) {
		return min + Math.abs(getAnyLong() % (max - min));
	}

	/** @return A random float from a uniform distribution between 0.0 (inclusive) and 1.0 (exclusive) */
	public float getFloat() {
		advance(4);
		return theRandomness.nextFloat();
	}

	/**
	 * @return A float generated from a random byte sequence. The distribution of values for such floats is quite irregular and many random
	 *         byte sequences map to {@link Float#NaN}
	 */
	public float getAnyFloat() {
		return Float.intBitsToFloat(getAnyInt());
	}

	/**
	 * @param min The minimum
	 * @param max The maximum
	 * @return A float <code>f</code> such that <code>min&lt;=f&lt;max</code>
	 */
	public float getFloat(float min, float max) {
		return min + getFloat() * (max - min);
	}

	/**
	 * @param min The minimum
	 * @param max The maximum
	 * @param avg The average value
	 * @return A float <code>f</code> such that <code>min&lt;=f&lt;max</code>. The result is weighted toward <code>avg</code>.
	 */
	public float getFloat(float min, float avg, float max) {
		if (avg <= min || avg >= max)
			throw new IllegalArgumentException("min=" + min + ", avg=" + avg + ", max=" + max);
		float random = getFloat();
		float skew = (avg - min) * 2 / (max - min);
		random = (float) Math.pow(random, 1 / skew);
		if (random <= 0.5)
			return min + random * (avg - min);
		else
			return avg + random * (max - avg);
	}

	/** @return A random double from a uniform distribution between 0.0 (inclusive) and 1.0 (exclusive) */
	public double getDouble() {
		advance(8);
		return theRandomness.nextDouble();
	}

	/** @return A random float from a normal distribution between 0.0 (inclusive) and 1.0 (exclusive) */
	public double getGaussian() {
		advance(8);
		return theRandomness.nextGaussian();
	}

	/**
	 * @return A double generated from a random byte sequence. The distribution of values for such doubles is quite irregular and many
	 *         random byte sequences map to {@link Double#NaN}
	 */
	public double getAnyDouble() {
		return Double.longBitsToDouble(getAnyLong());
	}

	/**
	 * @param min The minimum
	 * @param max The maximum
	 * @return A double <code>d</code> such that <code>min&lt;=d&lt;max</code>
	 */
	public double getDouble(double min, double max) {
		return min + getDouble() * (max - min);
	}

	/**
	 * @param min The minimum
	 * @param max The maximum
	 * @param avg The average value
	 * @return A double <code>d</code> such that <code>min&lt;=d&lt;max</code>. The result is weighted toward <code>avg</code>.
	 */
	public double getDouble(double min, double avg, double max) {
		if (avg < min || avg > max)
			throw new IllegalArgumentException("min=" + min + ", avg=" + avg + ", max=" + max);
		double random = getDouble();
		double skew = (avg - min) * 2 / (max - min);
		random = Math.pow(random, 1 / skew);
		if (random <= 0.5)
			return min + random * (avg - min);
		else
			return avg + random * (max - avg);
	}

	/** @return A random boolean */
	public boolean getBoolean() {
		advance(1);
		byte[] bytes = new byte[1];
		theRandomness.nextBytes(bytes);
		return bytes[0] >= 0;
	}

	/**
	 * @param odds The odds of getting <code>true</code>
	 * @return A random boolean weighted by <code>odds</code>
	 */
	public boolean getBoolean(double odds) {
		return getDouble() < odds;
	}

	/**
	 * @param bytes The bytes to populate with random data
	 * @param offset The offset in the array to populate
	 * @param length The number of bytes to populate
	 */
	public void populate(byte[] bytes, int offset, int length) {
		int r = 0;
		for (int i = 0; i < length; i++) {
			switch (i % 4) {
			case 0:
				r = getAnyInt();
				bytes[offset + i] = (byte) (r >>> 24);
				break;
			case 1:
				bytes[offset + i] = (byte) (r >>> 16);
				break;
			case 2:
				bytes[offset + i] = (byte) (r >>> 8);
				break;
			case 3:
				bytes[offset + i] = (byte) r;
				break;
			}
		}
	}

	/**
	 * @param numBytes The number of bytes to produce
	 * @return An array of <code>numBytesrandom
	 */
	public byte[] getBytes(int numBytes) {
		byte[] bytes = new byte[numBytes];
		populate(bytes, 0, numBytes);
		return bytes;
	}

	private static final char[] ALPHA_NUMERIC = new char[62];
	static {
		for (int i = 0; i < 10; i++)
			ALPHA_NUMERIC[i] = (char) ('0' + i);
		for (char c = 'a'; c <= 'z'; c++) {
			ALPHA_NUMERIC[c - 'a' + 10] = c;
			ALPHA_NUMERIC[c - 'a' + 36] = (char) (c - 'a' + 'A');
		}
	}

	/**
	 * @param minLength The minimum length for the string
	 * @param maxLength The maximum length for the string
	 * @return A random alphanumeric string (potentially with both upper- and lower-case letters) with a length in the given range
	 */
	public String getAlphaNumericString(int minLength, int maxLength) {
		int length;
		if (minLength == maxLength)
			length = minLength;
		else
			length = getInt(minLength, maxLength);
		StringBuilder str = new StringBuilder(length);
		for (int i = 0; i < length; i += 5) {
			int randomInt = getAnyInt();
			int mask = 0x3f000000;
			for (int j = 0; j < 5 && i + j < length; j++) {
				int c = (randomInt & mask) >>> (24 - j * 6);
				if (c >= ALPHA_NUMERIC.length) {
					c = Integer.rotateRight(randomInt, j) % ALPHA_NUMERIC.length;
					if (c < 0)
						c += ALPHA_NUMERIC.length;
				}
				str.append(ALPHA_NUMERIC[c]);
				mask >>>= 6;
			}
		}
		return str.toString();
	}
}

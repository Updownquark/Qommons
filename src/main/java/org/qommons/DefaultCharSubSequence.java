package org.qommons;

/** Default implementation for {@link CharSequence#subSequence(int, int)} */
public class DefaultCharSubSequence implements CharSequence {
	private final CharSequence theBacking;
	private final int theStart;
	private final int theEnd;
	private int hash; // Default to 0

	/**
	 * @param backing The char sequence that this is a sub-sequence of
	 * @param start The start for the sub-sequence
	 * @param end The end for the sub-sequence
	 */
	public DefaultCharSubSequence(CharSequence backing, int start, int end) {
		if (start < 0 || start > end || end > backing.length())
			throw new IndexOutOfBoundsException(start + "..." + end + " of " + backing.length());
		theBacking = backing;
		theStart = start;
		theEnd = end;
	}

	/** @return This sub-sequence's starting position in its backing sequence */
	public int getStart() {
		return theStart;
	}

	/** @return This sub-sequence's end position in its backing sequence */
	public int getEnd() {
		return theEnd;
	}

	@Override
	public int length() {
		return theEnd - theStart;
	}

	@Override
	public char charAt(int index) {
		int length = length();
		if (index < 0 || index >= length)
			throw new IndexOutOfBoundsException(index + " of " + length);
		return theBacking.charAt(index + theStart);
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		int length = length();
		if (start < 0 || start > end || end > length)
			throw new IndexOutOfBoundsException(start + "..." + end + " of " + length);
		return new DefaultCharSubSequence(theBacking, theStart + start, theStart + end);
	}

	@Override
	public int hashCode() {
		int h = hash;
		int length = length();
		if (h == 0 && length > 0) {
			for (int i = theStart; i < theEnd; i++) {
				h = 31 * h + theBacking.charAt(i);
			}
			hash = h;
		}
		return h;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		else if (!(obj instanceof CharSequence))
			return false;
		CharSequence other = (CharSequence) obj;
		int length = length();
		if (other.length() != length)
			return false;
		for (int i = 0; i < length; i++) {
			if (charAt(i) != other.charAt(i))
				return false;
		}
		return true;
	}

	@Override
	public String toString() {
		char[] ch = new char[length()];
		for (int i = 0; i < ch.length; i++)
			ch[i] = theBacking.charAt(theStart + i);
		return new String(ch);
	}
}

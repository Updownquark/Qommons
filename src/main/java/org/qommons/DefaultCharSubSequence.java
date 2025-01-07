package org.qommons;

/** Default implementation for {@link CharSequence#subSequence(int, int)} */
public class DefaultCharSubSequence extends AbstractCharSequence {
	private final CharSequence theBacking;
	private final int theStart;
	private final int theEnd;

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
}

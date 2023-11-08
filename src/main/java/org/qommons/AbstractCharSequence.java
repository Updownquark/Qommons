package org.qommons;

/** Abstract implementation of {@link CharSequence} that handles {@link #subSequence(int, int)} and the Object methods */
public abstract class AbstractCharSequence implements CharSequence {
	private int hash; // Default to 0

	@Override
	public CharSequence subSequence(int start, int end) {
		int length = length();
		if (start < 0 || start > end || end > length)
			throw new IndexOutOfBoundsException(start + "..." + end + " of " + length);
		return new DefaultCharSubSequence(this, start, end);
	}

	@Override
	public int hashCode() {
		int h = hash;
		int length = length();
		if (h == 0 && length > 0) {
			for (int i = 0; i < length; i++) {
				h = 31 * h + charAt(i);
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
			ch[i] = charAt(i);
		return new String(ch);
	}
}

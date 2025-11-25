package org.qommons;

/**
 * <p>
 * Abstract implementation of {@link CharSequence} that handles {@link #subSequence(int, int)} and the Object methods.
 * </p>
 * <p>
 * This class also has the property that its {@link #hashCode()} method produces the same results as equivalent {@link String}s.
 * </p>
 */
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

	/**
	 * @param sequences The sequences to concatenate
	 * @return The concatenated sequence
	 */
	public static Concatenated concat(CharSequence... sequences) {
		return new Concatenated(sequences);
	}

	/**
	 * @param seq The sequence to lower-case
	 * @return A character sequence that is the same as the given sequence, but with all lower-case characters. The new sequence tracks the
	 *         source, changing as the source does
	 */
	public static LowerCase toLowerCase(CharSequence seq) {
		return new LowerCase(seq);
	}

	/**
	 * @param seq The sequence to reverse
	 * @return A character sequence that is the reverse of the given sequence. The new sequence tracks the source, changing as the source
	 *         does
	 */
	public static Reversed reverse(CharSequence seq) {
		return new Reversed(seq);
	}

	/**
	 * @param seq The sequence to multiply
	 * @param times The number of times to reproduce the sequence
	 * @return The multiplied sequence--e.g. multiply("Something", 2) would be "SomethingSomething"
	 */
	public static Multiplied multiply(CharSequence seq, int times) {
		return new Multiplied(seq, times);
	}

	/** Implements {@link AbstractCharSequence#concat(CharSequence...)} */
	public static class Concatenated extends AbstractCharSequence {
		private final CharSequence[] theSequences;

		/** @param sequences The sequences to concatenate */
		public Concatenated(CharSequence[] sequences) {
			theSequences = sequences;
		}

		@Override
		public int length() {
			int sum = 0;
			for (int i = 0; i < theSequences.length; i++)
				sum += theSequences[i].length();
			return sum;
		}

		@Override
		public char charAt(int index) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			int remaining = index;
			for (int i = 0; i < theSequences.length; i++) {
				int length = theSequences[i].length();
				if (remaining < length)
					return theSequences[i].charAt(remaining);
				else
					remaining -= length;
			}
			throw new IndexOutOfBoundsException(index + " of " + (index - remaining));
		}
	}

	/** A simple, immutable implementation of {@link AbstractCharSequence} */
	public static class Simple extends AbstractCharSequence {
		private final char[] chars;

		/** @param chars The content for this character sequence */
		public Simple(char[] chars) {
			this.chars = chars;
		}

		@Override
		public int length() {
			return chars.length;
		}

		@Override
		public char charAt(int index) {
			return chars[index];
		}
	}

	/** Implements {@link AbstractCharSequence#toLowerCase(CharSequence)} */
	public static class LowerCase extends AbstractCharSequence {
		private final CharSequence theWrapped;

		/** @param wrapped The character sequence to lower-case */
		public LowerCase(CharSequence wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public int length() {
			return theWrapped.length();
		}

		@Override
		public char charAt(int index) {
			char ch = theWrapped.charAt(index);
			if (ch >= 'A' && ch <= 'Z')
				return (char) (ch - 'A' + 'a');
			return ch;
		}
	}

	/** Implements {@link AbstractCharSequence#reverse(CharSequence)} */
	public static class Reversed extends AbstractCharSequence {
		private final CharSequence theWrapped;

		/** @param wrapped The character sequence to reverse */
		public Reversed(CharSequence wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public int length() {
			return theWrapped.length();
		}

		@Override
		public char charAt(int index) {
			int length = theWrapped.length();
			if (index < 0 || index >= length)
				throw new IndexOutOfBoundsException(index + " of " + length);
			return theWrapped.charAt(length - index - 1);
		}
	}

	/** Implements {@link AbstractCharSequence#multiply(CharSequence, int)} */
	public static class Multiplied extends AbstractCharSequence {
		private final CharSequence theBase;
		private final int theMultiplier;

		/**
		 * @param base The sequence to multiply
		 * @param multiplier The number of times to reproduce the sequence
		 */
		public Multiplied(CharSequence base, int multiplier) {
			theBase = base;
			theMultiplier = multiplier;
		}

		/** @return The sequence being multiplied */
		public CharSequence getBase() {
			return theBase;
		}

		/** @return The number of times the {@link #getBase() base sequence} is reproduced */
		public int getMultiplier() {
			return theMultiplier;
		}

		@Override
		public int length() {
			return theBase.length() * theMultiplier;
		}

		@Override
		public char charAt(int index) {
			int length = length();
			if (index < 0 || index >= length)
				throw new IndexOutOfBoundsException(index + " of " + length);
			int baseIdx = index % theMultiplier;
			return theBase.charAt(baseIdx);
		}
	}
}

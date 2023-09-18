package org.qommons.io;

/** Provides information about the position in the XML file of characters in XML content */
public interface PositionedContent extends CharSequence {
	/**
	 * @param index The index in the content to get file position information for
	 * @return The position of the given character in the content in the XML file
	 */
	FilePosition getPosition(int index);

	/**
	 * @param from The start index (inclusive) to get source content for
	 * @param to The end index (exclusive) to get source content for
	 * @return The number of text characters specified in the source file that was parsed into the characters in this sequence between the
	 *         two indexes
	 */
	int getSourceLength(int from, int to);

	/**
	 * @param from The start index (inclusive) to get source content for
	 * @param to The end index (exclusive) to get source content for
	 * @return The text specified in the source file that was parsed into the characters in this sequence between the two indexes
	 */
	CharSequence getSourceContent(int from, int to);

	/**
	 * @param startIndex The start index (inclusive) for the sub-sequence
	 * @return The sub sequence
	 */
	default PositionedContent subSequence(int startIndex) {
		if (startIndex < 0 || startIndex > length())
			throw new IndexOutOfBoundsException(startIndex + " of " + length());
		else if (startIndex == 0)
			return this;
		return new SubContentPosition(this, startIndex, length());
	}

	/**
	 * @param startIndex The start index (inclusive) for the sub-sequence
	 * @param endIndex The end index (exclusive) for the sub-sequence
	 * @return The sub sequence
	 */
	@Override
	default PositionedContent subSequence(int startIndex, int endIndex) {
		if (startIndex < 0 || startIndex > endIndex || endIndex > length())
			throw new IndexOutOfBoundsException(startIndex + " to " + endIndex + " of " + length());
		else if (startIndex == 0 && endIndex == length())
			return this;
		return new SubContentPosition(this, startIndex, endIndex);
	}

	/** @return A string representing the file location at the start of this sequence */
	default String toLocationString() {
		return getPosition(0).toString();
	}

	/** A {@link PositionedContent} that is a sub-sequence of another */
	class SubContentPosition implements PositionedContent {
		private final PositionedContent theWrapped;
		private final int theStart;
		private final int theEnd;

		SubContentPosition(PositionedContent wrap, int start, int end) {
			if (start < 0 || start > end || end > wrap.length())
				throw new IndexOutOfBoundsException(start + " to " + end + " of " + wrap.length());
			theWrapped = wrap;
			this.theStart = start;
			this.theEnd = end;
		}

		@Override
		public int length() {
			return theEnd - theStart;
		}

		void checkIndex(int index, boolean endInclusive) {
			int last = theEnd;
			if (!endInclusive)
				last--;
			if (index < 0 || theStart + index > last)
				throw new IndexOutOfBoundsException(index + " of " + length());
		}

		void checkRange(int startIndex, int endIndex) {
			if (startIndex < 0 || startIndex > endIndex || theStart + endIndex > theEnd)
				throw new IndexOutOfBoundsException(startIndex + " to " + endIndex + " of " + length());
		}

		@Override
		public char charAt(int index) {
			checkIndex(index, false);
			return theWrapped.charAt(theStart + index);
		}

		@Override
		public CharSequence getSourceContent(int from, int to) {
			checkRange(from, to);
			return theWrapped.getSourceContent(theStart + from, theStart + to);
		}

		@Override
		public int getSourceLength(int from, int to) {
			checkRange(from, to);
			return theWrapped.getSourceLength(theStart + from, theStart + to);
		}

		@Override
		public FilePosition getPosition(int index) {
			checkIndex(index, true);
			return theWrapped.getPosition(theStart + index);
		}

		@Override
		public PositionedContent subSequence(int startIndex) {
			checkIndex(startIndex, false);
			return new SubContentPosition(theWrapped, theStart + startIndex, theEnd);
		}

		@Override
		public PositionedContent subSequence(int startIndex, int endIndex) {
			checkRange(startIndex, endIndex);
			return new SubContentPosition(theWrapped, theStart + startIndex, theStart + endIndex);
		}

		@Override
		public String toLocationString() {
			return theWrapped + "{" + theStart + "," + theEnd + "}";
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			for (int c = theStart; c < theEnd; c++)
				str.append(theWrapped.charAt(c));
			return str.toString();
		}
	}

	/** A file position of length 0 whose {@link #getPosition(int)} method always returns the same value */
	public static class Fixed implements PositionedContent {
		private final FilePosition position;

		/** @param position The position for this content position */
		public Fixed(FilePosition position) {
			this.position = position;
		}

		@Override
		public int length() {
			return 0;
		}

		@Override
		public char charAt(int index) {
			throw new IndexOutOfBoundsException(index + " of 0");
		}

		@Override
		public CharSequence getSourceContent(int from, int to) {
			if (from == 0 && to == 0)
				return this;
			throw new IndexOutOfBoundsException(from + " to " + to + " of 0");
		}

		@Override
		public int getSourceLength(int from, int to) {
			if (from == 0 && to == 0)
				return 0;
			throw new IndexOutOfBoundsException(from + " to " + to + " of 0");
		}

		@Override
		public FilePosition getPosition(int index) {
			return position;
		}

		@Override
		public int hashCode() {
			return position.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof PositionedContent.Fixed && position.equals(((PositionedContent.Fixed) obj).position);
		}

		@Override
		public String toLocationString() {
			return position.toString();
		}

		@Override
		public String toString() {
			return "";
		}
	}

	/** Simple {@link PositionedContent} implementation */
	public static class Simple implements PositionedContent {
		private final FilePosition theStart;
		private final String theContent;

		/**
		 * @param start The start position
		 * @param content The content
		 */
		public Simple(FilePosition start, String content) {
			theStart = start;
			theContent = content;
		}

		@Override
		public int length() {
			return theContent.length();
		}

		@Override
		public char charAt(int index) {
			return theContent.charAt(index);
		}

		@Override
		public CharSequence getSourceContent(int from, int to) {
			return theContent.substring(from, to);
		}

		@Override
		public int getSourceLength(int from, int to) {
			return to - from;
		}

		@Override
		public FilePosition getPosition(int index) {
			if (index == 0)
				return theStart;
			else if (index < 0 || index > theContent.length())
				throw new IndexOutOfBoundsException(index + " of " + theContent.length());
			return new FilePosition(theStart.getPosition() + index, theStart.getLineNumber(), theStart.getCharNumber() + index);
		}

		@Override
		public String toLocationString() {
			return theStart + ":" + theContent;
		}

		@Override
		public String toString() {
			return theContent;
		}
	}
}

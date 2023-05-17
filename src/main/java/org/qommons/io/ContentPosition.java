package org.qommons.io;

/** Provides information about the position in the XML file of characters in XML content */
public interface ContentPosition {
	/** @return The length of the content that this position structure is for */
	int length();

	/**
	 * @param index The index in the content to get file position information for
	 * @return The position of the given character in the content in the XML file
	 */
	FilePosition getPosition(int index);

	/**
	 * @param startIndex The start index (inclusive) for the sub-sequence
	 * @return The sub sequence
	 */
	default ContentPosition subSequence(int startIndex) {
		if (startIndex < 0 || startIndex > length())
			throw new IndexOutOfBoundsException(startIndex + " of " + length());
		return new SubContentPosition(this, startIndex, length());
	}

	/**
	 * @param startIndex The start index (inclusive) for the sub-sequence
	 * @param endIndex The end index (exclusive) for the sub-sequence
	 * @return The sub sequence
	 */
	default ContentPosition subSequence(int startIndex, int endIndex) {
		if (startIndex < 0 || startIndex > endIndex || endIndex > length())
			throw new IndexOutOfBoundsException(startIndex + " to " + endIndex + " of " + length());
		return new SubContentPosition(this, startIndex, endIndex);
	}

	/** A {@link ContentPosition} that is a sub-sequence of another */
	class SubContentPosition implements ContentPosition {
		private final ContentPosition theWrapped;
		private final int theStart;
		private final int theEnd;

		SubContentPosition(ContentPosition wrap, int start, int end) {
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

		@Override
		public FilePosition getPosition(int index) {
			if (theStart + index > theEnd)
				throw new IndexOutOfBoundsException(index + " of " + length());
			return theWrapped.getPosition(theStart + index);
		}

		@Override
		public ContentPosition subSequence(int startIndex, int endIndex) {
			if (startIndex < 0 || startIndex > endIndex || theStart + endIndex > theEnd)
				throw new IndexOutOfBoundsException(startIndex + " to " + endIndex + " of " + length());
			return new SubContentPosition(theWrapped, theStart + startIndex, theStart + endIndex);
		}

		@Override
		public String toString() {
			return theWrapped + "{" + theStart + "," + theEnd + "}";
		}
	}

	/** A file position of length 0 whose {@link #getPosition(int)} method always returns the same value */
	public static class Fixed implements ContentPosition {
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
		public FilePosition getPosition(int index) {
			return position;
		}

		@Override
		public int hashCode() {
			return position.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ContentPosition.Fixed && position.equals(((ContentPosition.Fixed) obj).position);
		}

		@Override
		public String toString() {
			return position.toString();
		}
	}

	/** Simple {@link ContentPosition} implementation */
	public static class Simple implements ContentPosition {
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
		public FilePosition getPosition(int index) {
			if (index == 0)
				return theStart;
			else if (index < 0 || index > theContent.length())
				throw new IndexOutOfBoundsException(index + " of " + theContent.length());
			return new FilePosition(theStart.getPosition() + index, theStart.getLineNumber(), theStart.getCharNumber() + index);
		}

		@Override
		public String toString() {
			return theStart + ":" + theContent;
		}
	}
}
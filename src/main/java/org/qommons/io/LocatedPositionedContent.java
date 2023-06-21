package org.qommons.io;

/** A {@link PositionedContent} that knows the file its in, so it can produce {@link LocatedFilePosition}s */
public interface LocatedPositionedContent extends PositionedContent {
	/** @return The file that this position is in */
	String getFileLocation();

	@Override
	LocatedFilePosition getPosition(int index);

	@Override
	LocatedPositionedContent subSequence(int startIndex);

	@Override
	LocatedPositionedContent subSequence(int startIndex, int endIndex);

	/**
	 * @param fileLocation The file location
	 * @param position The position in the file
	 * @return The file content position
	 */
	public static LocatedPositionedContent of(String fileLocation, PositionedContent position) {
		if (position instanceof LocatedPositionedContent)
			return (LocatedPositionedContent) position;
		return new Default(fileLocation, position);
	}

	/** Default {@link LocatedPositionedContent} implementation */
	class Default implements LocatedPositionedContent {
		private final String theFileLocation;
		private final PositionedContent theContent;

		public Default(String fileLocation, PositionedContent content) {
			theFileLocation = fileLocation;
			theContent = content;
		}

		@Override
		public String getFileLocation() {
			return theFileLocation;
		}

		@Override
		public int length() {
			return theContent.length();
		}

		@Override
		public CharSequence getSourceContent(int from, int to) {
			return theContent.getSourceContent(from, to);
		}

		@Override
		public int getSourceLength(int from, int to) {
			return theContent.getSourceLength(from, to);
		}

		@Override
		public char charAt(int index) {
			return theContent.charAt(index);
		}

		@Override
		public LocatedFilePosition getPosition(int index) {
			return new LocatedFilePosition(theFileLocation, theContent.getPosition(index));
		}

		@Override
		public LocatedPositionedContent subSequence(int startIndex) {
			if (startIndex == 0)
				return this;
			return new Default(theFileLocation, theContent.subSequence(startIndex));
		}

		@Override
		public LocatedPositionedContent subSequence(int startIndex, int endIndex) {
			if (startIndex == 0 && endIndex == length())
				return this;
			return new Default(theFileLocation, theContent.subSequence(startIndex, endIndex));
		}

		@Override
		public String toString() {
			return theContent.toString();
		}
	}

	/** {@link LocatedPositionedContent} on a single line */
	class SimpleLine implements LocatedPositionedContent {
		private final LocatedFilePosition theStart;
		private final String theContent;

		/**
		 * @param start The start of the content
		 * @param content The content
		 */
		public SimpleLine(LocatedFilePosition start, String content) {
			theStart = start;
			theContent = content;
		}

		@Override
		public int getSourceLength(int from, int to) {
			return to - from;
		}

		@Override
		public CharSequence getSourceContent(int from, int to) {
			return theContent.substring(from, to);
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
		public String getFileLocation() {
			return theStart.getFileLocation();
		}

		@Override
		public LocatedFilePosition getPosition(int index) {
			if (index == 0)
				return theStart;
			return new LocatedFilePosition(theStart.getFileLocation(), theStart.getPosition() + index, theStart.getLineNumber(),
				theStart.getCharNumber() + index);
		}

		@Override
		public LocatedPositionedContent subSequence(int startIndex) {
			if (startIndex == 0)
				return this;
			return new SimpleLine(getPosition(startIndex), theContent.substring(startIndex));
		}

		@Override
		public LocatedPositionedContent subSequence(int startIndex, int endIndex) {
			if (startIndex == 0 && endIndex == length())
				return this;
			return new SimpleLine(getPosition(startIndex), theContent.substring(startIndex, endIndex));
		}

		@Override
		public String toString() {
			return theContent;
		}
	}
}

package org.qommons.io;

public interface LocatedContentPosition extends ContentPosition {
	String getFileLocation();

	@Override
	LocatedFilePosition getPosition(int index);

	@Override
	LocatedContentPosition subSequence(int startIndex);

	@Override
	LocatedContentPosition subSequence(int startIndex, int endIndex);

	public static LocatedContentPosition of(String fileLocation, ContentPosition position) {
		if (position instanceof LocatedContentPosition)
			return (LocatedContentPosition) position;
		return new Default(fileLocation, position);
	}

	class Default implements LocatedContentPosition {
		private final String theFileLocation;
		private final ContentPosition thePosition;

		public Default(String fileLocation, ContentPosition position) {
			theFileLocation = fileLocation;
			thePosition = position;
		}

		@Override
		public String getFileLocation() {
			return theFileLocation;
		}

		@Override
		public int length() {
			return thePosition.length();
		}

		@Override
		public LocatedFilePosition getPosition(int index) {
			return new LocatedFilePosition(theFileLocation, thePosition.getPosition(index));
		}

		@Override
		public LocatedContentPosition subSequence(int startIndex) {
			if (startIndex == 0)
				return this;
			return new Default(theFileLocation, thePosition.subSequence(startIndex));
		}

		@Override
		public LocatedContentPosition subSequence(int startIndex, int endIndex) {
			if (startIndex == 0 && endIndex == length())
				return this;
			return new Default(theFileLocation, thePosition.subSequence(startIndex, endIndex));
		}

		@Override
		public String toString() {
			return theFileLocation + ":" + thePosition;
		}
	}
}

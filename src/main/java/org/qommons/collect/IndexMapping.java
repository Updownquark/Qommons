package org.qommons.collect;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;

public interface IndexMapping {
	public int toDest(int sourceIndex);

	public int toSource(int destIndex);

	public static IndexMapping unity(int size) {
		return new Unity(size);
	}

	public static IndexMapping of(int sourceSize, int destSize, IntUnaryOperator sourceToDest) {
		return new Default(sourceSize, destSize, sourceToDest);
	}

	public static class Default implements IndexMapping {
		private final int[] theSourceToDest;
		private final int[] theDestToSource;

		public Default(int[] sourceToDest, int[] destToSource) {
			theSourceToDest = sourceToDest;
			theDestToSource = destToSource;
		}

		public Default(int sourceSize, int destSize, IntUnaryOperator sourceToDest) {
			theSourceToDest = new int[sourceSize];
			theDestToSource = new int[destSize];
			Arrays.fill(theDestToSource, -1);
			for (int s = 0; s < sourceSize; s++) {
				int dest = sourceToDest.applyAsInt(s);
				theSourceToDest[s] = dest;
				if (dest >= 0)
					theDestToSource[dest] = s;
			}
		}

		@Override
		public int toDest(int sourceIndex) {
			return theSourceToDest[sourceIndex];
		}

		@Override
		public int toSource(int destIndex) {
			return theDestToSource[destIndex];
		}
	}

	public static class Unity implements IndexMapping {
		private final int theSize;

		public Unity(int size) {
			theSize = size;
		}

		@Override
		public int toDest(int sourceIndex) {
			if (sourceIndex < 0 || sourceIndex > theSize)
				throw new IndexOutOfBoundsException(sourceIndex + " of " + theSize);
			return sourceIndex;
		}

		@Override
		public int toSource(int destIndex) {
			if (destIndex < 0 || destIndex > theSize)
				throw new IndexOutOfBoundsException(destIndex + " of " + theSize);
			return destIndex;
		}
	}
}

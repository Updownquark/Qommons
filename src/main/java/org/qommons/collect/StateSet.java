package org.qommons.collect;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * <p>
 * A StateSet is like a {@link BetterBitSet bit set} that stores multiple bits per index. This class is useful for storing a variable number
 * of very small pieces of information, such as multiple booleans or enum values or a small set of bit flags.
 * </p>
 * <p>
 * The {@link Iterable} implementation returns each item in this state set (up to the last non-empty item) as a 1-length int array. The
 * array is re-used over the iteration, so it should not be stored. This gives better performance than if I were to implement
 * Iterable&lt;{@link Integer}&gt;, which would incur boxing penalties. The {@link Iterator#remove()} method sets the most recently-returned
 * state to empty (zero).
 * </p>
 * <p>
 * I debated over it, but chose to retain the 'Set' part of the name from BitSet, even though neither this class nor BitSet is actually a
 * set, since multiple copies of the same value may be contained.
 * </p>
 * I also preserved several other API conventions, such as:
 * <ul>
 * <li>The lack of any variables keeping track of trailing zero values. The {@link #length()} method returns the index plus one of the last
 * non-zero value, if any.</li>
 * <li>The {@link #nextNotEmpty(int)} and {@link #previousNotEmpty(int)} methods will return the parameter if that item is not empty.</li>
 * </ul>
 */
public class StateSet implements Iterable<int[]> {
	private final BetterBitSet theBitSet;
	private final int theStateSize;

	/** @param stateSize The number of bits for each item in this state set */
	public StateSet(int stateSize) {
		if (stateSize <= 0)
			throw new IllegalArgumentException("State size must be positive");
		else if (stateSize > 32)
			throw new IllegalArgumentException("State size cannot exceed 32");
		theStateSize = stateSize;
		theBitSet = new BetterBitSet();
	}

	/** @return The number of bits per item in this state set */
	public int getStateSize() {
		return theStateSize;
	}

	/** @return Whether there are no non-zero items in this state set */
	public boolean isEmpty() {
		return theBitSet.isEmpty();
	}

	/** @return The index plus one of the last non-zero item in this state set, or zero if this state set is empty */
	public int length() {
		int bsLen = theBitSet.length();
		return (bsLen + (theStateSize - 1)) / theStateSize;
	}

	@Override
	public Iterator<int[]> iterator() {
		return new AllStateIterator();
	}

	/**
	 * @param index The index of the state to get
	 * @return The state value at the given index
	 */
	public int get(int index) {
		int bitIndex = index * theStateSize;
		return (int) theBitSet.getBits(bitIndex, theStateSize, 0);
	}

	/**
	 * @param index The index of the state to get the bit for
	 * @param stateOffset The index of the bit within the state to get
	 * @return The given bit within the given state in this state set
	 */
	public boolean getBit(int index, int stateOffset) {
		if (stateOffset < 0 || stateOffset >= theStateSize)
			throw new IndexOutOfBoundsException("State offset " + stateOffset + " of " + theStateSize);
		return theBitSet.get(index * theStateSize + stateOffset);
	}

	/**
	 * @param index The index of the state to set
	 * @param value The value for the state (only the least significant {@link #getStateSize() stateSize} bits will be used)
	 * @return The previous value of the state
	 */
	public int set(int index, int value) {
		return (int) theBitSet.getAndSetBits(index * theStateSize, theStateSize, value);
	}

	/**
	 * @param index The index of the state to set the bit for
	 * @param stateOffset The index of the bit within the state to set
	 * @param value The new value for the bit in the state
	 */
	public void setBit(int index, int stateOffset, boolean value) {
		if (stateOffset < 0 || stateOffset >= theStateSize)
			throw new IndexOutOfBoundsException("State offset " + stateOffset + " of " + theStateSize);
		theBitSet.set(index * theStateSize + stateOffset, value);
	}

	/**
	 * @param index The index of the state to clear (set to zero)
	 * @return This state set
	 */
	public StateSet clear(int index) {
		int startBit = index * theStateSize;
		theBitSet.clear(startBit, startBit + theStateSize);
		return this;
	}

	/**
	 * Clears all states in this set
	 * 
	 * @return This state set
	 */
	public StateSet clear() {
		theBitSet.clear();
		return this;
	}

	/**
	 * @param start The index of the first state to clear
	 * @param end The index after the last state to clear
	 * @return Clears all states (sets them to zero) with indexes >=start and &lt;=end
	 */
	public StateSet clear(int start, int end) {
		theBitSet.clear(start * theStateSize, end * theStateSize);
		return this;
	}

	/**
	 * @param start The location to insert the interval
	 * @param length The number of states to insert
	 * @return This state set
	 */
	public StateSet insertInterval(int start, int length) {
		theBitSet.insertInterval(start * theStateSize, length * theStateSize);
		return this;
	}

	/**
	 * @param start The index of the first location to remove
	 * @param length The number of states to remove
	 * @return This state set
	 */
	public StateSet removeInterval(int start, int length) {
		theBitSet.removeInterval(start * theStateSize, length * theStateSize);
		return this;
	}

	/**
	 * @param start The starting index of the search
	 * @return The index of the first state in this state set at or after <code>start</code> that is not zero
	 */
	public int nextNotEmpty(int start) {
		int nextBit = theBitSet.nextSetBit(start * theStateSize);
		return nextBit < 0 ? -1 : nextBit / theStateSize;
	}

	/**
	 * @param start The starting index of the search
	 * @return The index of the first state in this state set at or before <code>start</code> that is not zero
	 */
	public int previousNotEmpty(int start) {
		int prevBit = theBitSet.previousSetBit((start + 1) * theStateSize - 1);
		return prevBit < 0 ? -1 : prevBit / theStateSize;
	}

	/**
	 * @param start The starting index of the search
	 * @param stateOffset The bit in this state set to check
	 * @return The index of the first state in this state set at or after <code>start</code> for which the given bit is set, or -1 if there
	 *         are no more such states.
	 */
	public int nextWithBit(int start, int stateOffset) {
		if (stateOffset < 0 || stateOffset >= theStateSize)
			throw new IndexOutOfBoundsException("Bit " + stateOffset + " of " + theStateSize);
		int nextBit = theBitSet.nextSetBitMatching(start * theStateSize, theStateSize, stateOffset);
		return nextBit < 0 ? -1 : nextBit / theStateSize;
	}

	/**
	 * @param start The starting index of the search
	 * @param bit The bit in this state set to check
	 * @return The index of the first state in this state set at or before <code>start</code> for which the given bit is set, or -1 if there
	 *         are no more such states.
	 */
	public int previousWithBit(int start, int bit) {
		if (bit < 0 || bit >= theStateSize)
			throw new IndexOutOfBoundsException("Bit " + bit + " of " + theStateSize);
		// TODO At some point I should probably implement BetterBitSet.previousSetBitMatching(start, divisor, modulus) like the method above
		int prevBit = theBitSet.previousSetBit((start + 1) * theStateSize - 1);
		while (prevBit >= 0 && (prevBit % theStateSize != bit))
			prevBit = theBitSet.previousSetBit(prevBit - 1);
		return prevBit < 0 ? -1 : prevBit / theStateSize;
	}

	@Override
	public int hashCode() {
		return theBitSet.hashCode() ^ theStateSize;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof StateSet))
			return false;
		StateSet other = (StateSet) obj;
		return theStateSize == other.theStateSize && theBitSet.equals(other.theBitSet);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append('{');
		boolean first = true;
		for (int i = nextNotEmpty(0); i >= 0; i = nextNotEmpty(i + 1)) {
			if (first)
				first = false;
			else
				str.append(", ");
			str.append('[').append(i).append("]=");
			int state = get(i);
			int mask = 1;
			for (int s = 0; s < theStateSize; s++) {
				str.append((state & mask) == 0 ? '0' : '1');
				mask <<= 1;
			}
		}
		return str.append('}').toString();
	}

	class AllStateIterator implements Iterator<int[]> {
		private final int[] theState = new int[1];
		private int theIndex = 0;
		private int theNextNonEmpty = nextNotEmpty(0);

		@Override
		public boolean hasNext() {
			return theIndex <= theNextNonEmpty;
		}

		@Override
		public int[] next() {
			if (theIndex == theNextNonEmpty) {
				theState[0] = get(theIndex);
				theIndex++;
				theNextNonEmpty = nextNotEmpty(theIndex);
			} else if (theIndex < theNextNonEmpty) {
				theState[0] = 0;
				theIndex++;
			} else
				throw new NoSuchElementException();
			return theState;
		}

		@Override
		public void remove() {
			if (theIndex == 0)
				throw new IllegalStateException();
			set(theIndex, 0);
		}
	}
}

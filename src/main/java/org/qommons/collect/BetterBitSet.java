package org.qommons.collect;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

/**
 * I really do hate to do this, but {@link java.util.BitSet} is missing some methods that I really need but can't implements without access
 * to private fields. It's a copy from JDK 11 with some augmentations.
 */
public class BetterBitSet implements Cloneable {
	/*
	 * BitSets are packed into arrays of "words."  Currently a word is
	 * a long, which consists of 64 bits, requiring 6 address bits.
	 * The choice of word size is determined purely by performance concerns.
	 */
	private static final int ADDRESS_BITS_PER_WORD = 6;
	private static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;
	private static final int BIT_INDEX_MASK = BITS_PER_WORD - 1;

	/* Used to shift left or right for a partial word mask */
	private static final long WORD_MASK = 0xffffffffffffffffL;

	/**
	 * @serialField bits long[]
	 *
	 *              The bits in this BitSet. The ith bit is stored in bits[i/64] at bit position i % 64 (where bit position 0 refers to the
	 *              least significant bit and 63 refers to the most significant bit).
	 */
	@SuppressWarnings("unused")
	private static final ObjectStreamField[] serialPersistentFields = { new ObjectStreamField("bits", long[].class), };

	/**
	 * The internal field corresponding to the serialField "bits".
	 */
	private long[] words;

	/**
	 * The number of words in the logical size of this BitSet.
	 */
	private transient int wordsInUse = 0;

	/**
	 * Whether the size of "words" is user-specified. If so, we assume the user knows what he's doing and try harder to preserve it.
	 */
	private transient boolean sizeIsSticky = false;

	@SuppressWarnings("unused")
	private static final long serialVersionUID = 7997698588986878753L;

	/**
	 * Given a bit index, return word index containing it.
	 */
	private static int wordIndex(int bitIndex) {
		return bitIndex >> ADDRESS_BITS_PER_WORD;
	}

	/**
	 * Every public method must preserve these invariants.
	 */
	private void checkInvariants() {
		assert (wordsInUse == 0 || words[wordsInUse - 1] != 0);
		assert (wordsInUse >= 0 && wordsInUse <= words.length);
		assert (wordsInUse == words.length || words[wordsInUse] == 0);
	}

	/**
	 * Sets the field wordsInUse to the logical size in words of the bit set. WARNING:This method assumes that the number of words actually
	 * in use is less than or equal to the current value of wordsInUse!
	 */
	private void recalculateWordsInUse() {
		// Traverse the bitset until a used word is found
		int i;
		for (i = wordsInUse - 1; i >= 0; i--)
			if (words[i] != 0)
				break;

		wordsInUse = i + 1; // The new logical size
	}

	/**
	 * Creates a new bit set. All bits are initially {@code false}.
	 */
	public BetterBitSet() {
		initWords(BITS_PER_WORD);
		sizeIsSticky = false;
	}

	/**
	 * Creates a bit set whose initial size is large enough to explicitly represent bits with indices in the range {@code 0} through
	 * {@code nbits-1}. All bits are initially {@code false}.
	 *
	 * @param nbits the initial size of the bit set
	 * @throws NegativeArraySizeException if the specified initial size is negative
	 */
	public BetterBitSet(int nbits) {
		// nbits can't be negative; size 0 is OK
		if (nbits < 0)
			throw new NegativeArraySizeException("nbits < 0: " + nbits);

		initWords(nbits);
		sizeIsSticky = true;
	}

	private void initWords(int nbits) {
		words = new long[wordIndex(nbits - 1) + 1];
	}

	/**
	 * Creates a bit set using words as the internal representation. The last word (if there is one) must be non-zero.
	 */
	private BetterBitSet(long[] words) {
		this.words = words;
		this.wordsInUse = words.length;
		checkInvariants();
	}

	/**
	 * Returns a new bit set containing all the bits in the given long array.
	 *
	 * <p>
	 * More precisely, <br>
	 * {@code BetterBitSet.valueOf(longs).get(n) == ((longs[n/64] & (1L<<(n%64))) != 0)} <br>
	 * for all {@code n < 64 * longs.length}.
	 *
	 * <p>
	 * This method is equivalent to {@code BetterBitSet.valueOf(LongBuffer.wrap(longs))}.
	 *
	 * @param longs a long array containing a little-endian representation of a sequence of bits to be used as the initial bits of the new
	 *        bit set
	 * @return a {@code BetterBitSet} containing all the bits in the long array
	 * @since 1.7
	 */
	public static BetterBitSet valueOf(long[] longs) {
		int n;
		for (n = longs.length; n > 0 && longs[n - 1] == 0; n--)
			;
		return new BetterBitSet(Arrays.copyOf(longs, n));
	}

	/**
	 * Returns a new bit set containing all the bits in the given long buffer between its position and limit.
	 *
	 * <p>
	 * More precisely, <br>
	 * {@code BetterBitSet.valueOf(lb).get(n) == ((lb.get(lb.position()+n/64) & (1L<<(n%64))) != 0)} <br>
	 * for all {@code n < 64 * lb.remaining()}.
	 *
	 * <p>
	 * The long buffer is not modified by this method, and no reference to the buffer is retained by the bit set.
	 *
	 * @param lb a long buffer containing a little-endian representation of a sequence of bits between its position and limit, to be used as
	 *        the initial bits of the new bit set
	 * @return a {@code BetterBitSet} containing all the bits in the buffer in the specified range
	 * @since 1.7
	 */
	public static BetterBitSet valueOf(LongBuffer lb) {
		lb = lb.slice();
		int n;
		for (n = lb.remaining(); n > 0 && lb.get(n - 1) == 0; n--)
			;
		long[] words = new long[n];
		lb.get(words);
		return new BetterBitSet(words);
	}

	/**
	 * Returns a new bit set containing all the bits in the given byte array.
	 *
	 * <p>
	 * More precisely, <br>
	 * {@code BetterBitSet.valueOf(bytes).get(n) == ((bytes[n/8] & (1<<(n%8))) != 0)} <br>
	 * for all {@code n <  8 * bytes.length}.
	 *
	 * <p>
	 * This method is equivalent to {@code BetterBitSet.valueOf(ByteBuffer.wrap(bytes))}.
	 *
	 * @param bytes a byte array containing a little-endian representation of a sequence of bits to be used as the initial bits of the new
	 *        bit set
	 * @return a {@code BetterBitSet} containing all the bits in the byte array
	 * @since 1.7
	 */
	public static BetterBitSet valueOf(byte[] bytes) {
		return BetterBitSet.valueOf(ByteBuffer.wrap(bytes));
	}

	/**
	 * Returns a new bit set containing all the bits in the given byte buffer between its position and limit.
	 *
	 * <p>
	 * More precisely, <br>
	 * {@code BetterBitSet.valueOf(bb).get(n) == ((bb.get(bb.position()+n/8) & (1<<(n%8))) != 0)} <br>
	 * for all {@code n < 8 * bb.remaining()}.
	 *
	 * <p>
	 * The byte buffer is not modified by this method, and no reference to the buffer is retained by the bit set.
	 *
	 * @param bb a byte buffer containing a little-endian representation of a sequence of bits between its position and limit, to be used as
	 *        the initial bits of the new bit set
	 * @return a {@code BetterBitSet} containing all the bits in the buffer in the specified range
	 * @since 1.7
	 */
	public static BetterBitSet valueOf(ByteBuffer bb) {
		bb = bb.slice().order(ByteOrder.LITTLE_ENDIAN);
		int n;
		for (n = bb.remaining(); n > 0 && bb.get(n - 1) == 0; n--)
			;
		long[] words = new long[(n + 7) / 8];
		bb.limit(n);
		int i = 0;
		while (bb.remaining() >= 8)
			words[i++] = bb.getLong();
		for (int remaining = bb.remaining(), j = 0; j < remaining; j++)
			words[i] |= (bb.get() & 0xffL) << (8 * j);
		return new BetterBitSet(words);
	}

	/**
	 * Returns a new byte array containing all the bits in this bit set.
	 *
	 * <p>
	 * More precisely, if <br>
	 * {@code byte[] bytes = s.toByteArray();} <br>
	 * then {@code bytes.length == (s.length()+7)/8} and <br>
	 * {@code s.get(n) == ((bytes[n/8] & (1<<(n%8))) != 0)} <br>
	 * for all {@code n < 8 * bytes.length}.
	 *
	 * @return a byte array containing a little-endian representation of all the bits in this bit set
	 * @since 1.7
	 */
	public byte[] toByteArray() {
		int n = wordsInUse;
		if (n == 0)
			return new byte[0];
		int len = 8 * (n - 1);
		for (long x = words[n - 1]; x != 0; x >>>= 8)
			len++;
		byte[] bytes = new byte[len];
		ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < n - 1; i++)
			bb.putLong(words[i]);
		for (long x = words[n - 1]; x != 0; x >>>= 8)
			bb.put((byte) (x & 0xff));
		return bytes;
	}

	/**
	 * Returns a new long array containing all the bits in this bit set.
	 *
	 * <p>
	 * More precisely, if <br>
	 * {@code long[] longs = s.toLongArray();} <br>
	 * then {@code longs.length == (s.length()+63)/64} and <br>
	 * {@code s.get(n) == ((longs[n/64] & (1L<<(n%64))) != 0)} <br>
	 * for all {@code n < 64 * longs.length}.
	 *
	 * @return a long array containing a little-endian representation of all the bits in this bit set
	 * @since 1.7
	 */
	public long[] toLongArray() {
		return Arrays.copyOf(words, wordsInUse);
	}

	/**
	 * Ensures that the BitSet can hold enough words.
	 * 
	 * @param wordsRequired the minimum acceptable number of words.
	 */
	private void ensureCapacity(int wordsRequired) {
		if (words.length < wordsRequired) {
			// Allocate larger of doubled size or required size
			int request = Math.max(2 * words.length, wordsRequired);
			words = Arrays.copyOf(words, request);
			sizeIsSticky = false;
		}
	}

	/**
	 * Ensures that the BitSet can accommodate a given wordIndex, temporarily violating the invariants. The caller must restore the
	 * invariants before returning to the user, possibly using recalculateWordsInUse().
	 * 
	 * @param wordIndex the index to be accommodated.
	 */
	private void expandTo(int wordIndex) {
		int wordsRequired = wordIndex + 1;
		if (wordsInUse < wordsRequired) {
			ensureCapacity(wordsRequired);
			wordsInUse = wordsRequired;
		}
	}

	/**
	 * Checks that fromIndex ... toIndex is a valid range of bit indices.
	 */
	private static void checkRange(int fromIndex, int toIndex) {
		if (fromIndex < 0)
			throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);
		if (toIndex < 0)
			throw new IndexOutOfBoundsException("toIndex < 0: " + toIndex);
		if (fromIndex > toIndex)
			throw new IndexOutOfBoundsException("fromIndex: " + fromIndex + " > toIndex: " + toIndex);
	}

	/**
	 * Sets the bit at the specified index to the complement of its current value.
	 *
	 * @param bitIndex the index of the bit to flip
	 * @throws IndexOutOfBoundsException if the specified index is negative
	 * @since 1.4
	 */
	public void flip(int bitIndex) {
		if (bitIndex < 0)
			throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);

		int wordIndex = wordIndex(bitIndex);
		expandTo(wordIndex);

		words[wordIndex] ^= (1L << bitIndex);

		recalculateWordsInUse();
		checkInvariants();
	}

	/**
	 * Sets each bit from the specified {@code fromIndex} (inclusive) to the specified {@code toIndex} (exclusive) to the complement of its
	 * current value.
	 *
	 * @param fromIndex index of the first bit to flip
	 * @param toIndex index after the last bit to flip
	 * @throws IndexOutOfBoundsException if {@code fromIndex} is negative, or {@code toIndex} is negative, or {@code fromIndex} is larger
	 *         than {@code toIndex}
	 * @since 1.4
	 */
	public void flip(int fromIndex, int toIndex) {
		checkRange(fromIndex, toIndex);

		if (fromIndex == toIndex)
			return;

		int startWordIndex = wordIndex(fromIndex);
		int endWordIndex = wordIndex(toIndex - 1);
		expandTo(endWordIndex);

		long firstWordMask = WORD_MASK << fromIndex;
		long lastWordMask = WORD_MASK >>> -toIndex;
		if (startWordIndex == endWordIndex) {
			// Case 1: One word
			words[startWordIndex] ^= (firstWordMask & lastWordMask);
		} else {
			// Case 2: Multiple words
			// Handle first word
			words[startWordIndex] ^= firstWordMask;

			// Handle intermediate words, if any
			for (int i = startWordIndex + 1; i < endWordIndex; i++)
				words[i] ^= WORD_MASK;

			// Handle last word
			words[endWordIndex] ^= lastWordMask;
		}

		recalculateWordsInUse();
		checkInvariants();
	}

	/**
	 * Sets the bit at the specified index to {@code true}.
	 *
	 * @param bitIndex a bit index
	 * @throws IndexOutOfBoundsException if the specified index is negative
	 * @since 1.0
	 */
	public void set(int bitIndex) {
		if (bitIndex < 0)
			throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);

		int wordIndex = wordIndex(bitIndex);
		expandTo(wordIndex);

		words[wordIndex] |= (1L << bitIndex); // Restores invariants

		checkInvariants();
	}

	/**
	 * Sets many bits in one call
	 * 
	 * @param bits The indexes of the bits to set
	 * @return This bit set
	 */
	public BetterBitSet setAll(int... bits) {
		for (int bit : bits)
			set(bit);
		return this;
	}

	/**
	 * Sets the bit at the specified index to the specified value.
	 *
	 * @param bitIndex a bit index
	 * @param value a boolean value to set
	 * @throws IndexOutOfBoundsException if the specified index is negative
	 * @since 1.4
	 */
	public void set(int bitIndex, boolean value) {
		if (value)
			set(bitIndex);
		else
			clear(bitIndex);
	}

	/**
	 * Sets the bits from the specified {@code fromIndex} (inclusive) to the specified {@code toIndex} (exclusive) to {@code true}.
	 *
	 * @param fromIndex index of the first bit to be set
	 * @param toIndex index after the last bit to be set
	 * @throws IndexOutOfBoundsException if {@code fromIndex} is negative, or {@code toIndex} is negative, or {@code fromIndex} is larger
	 *         than {@code toIndex}
	 * @since 1.4
	 */
	public void set(int fromIndex, int toIndex) {
		checkRange(fromIndex, toIndex);

		if (fromIndex == toIndex)
			return;

		// Increase capacity if necessary
		int startWordIndex = wordIndex(fromIndex);
		int endWordIndex = wordIndex(toIndex - 1);
		expandTo(endWordIndex);

		long firstWordMask = WORD_MASK << fromIndex;
		long lastWordMask = WORD_MASK >>> -toIndex;
		if (startWordIndex == endWordIndex) {
			// Case 1: One word
			words[startWordIndex] |= (firstWordMask & lastWordMask);
		} else {
			// Case 2: Multiple words
			// Handle first word
			words[startWordIndex] |= firstWordMask;

			// Handle intermediate words, if any
			for (int i = startWordIndex + 1; i < endWordIndex; i++)
				words[i] = WORD_MASK;

			// Handle last word (restores invariants)
			words[endWordIndex] |= lastWordMask;
		}

		checkInvariants();
	}

	/**
	 * Sets the bits from the specified {@code fromIndex} (inclusive) to the specified {@code toIndex} (exclusive) to the specified value.
	 *
	 * @param fromIndex index of the first bit to be set
	 * @param toIndex index after the last bit to be set
	 * @param value value to set the selected bits to
	 * @throws IndexOutOfBoundsException if {@code fromIndex} is negative, or {@code toIndex} is negative, or {@code fromIndex} is larger
	 *         than {@code toIndex}
	 * @since 1.4
	 */
	public void set(int fromIndex, int toIndex, boolean value) {
		if (value)
			set(fromIndex, toIndex);
		else
			clear(fromIndex, toIndex);
	}

	/**
	 * Sets the bit specified by the index to {@code false}.
	 *
	 * @param bitIndex the index of the bit to be cleared
	 * @throws IndexOutOfBoundsException if the specified index is negative
	 * @since 1.0
	 */
	public void clear(int bitIndex) {
		if (bitIndex < 0)
			throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);

		int wordIndex = wordIndex(bitIndex);
		if (wordIndex >= wordsInUse)
			return;

		words[wordIndex] &= ~(1L << bitIndex);

		recalculateWordsInUse();
		checkInvariants();
	}

	/**
	 * Sets the bits from the specified {@code fromIndex} (inclusive) to the specified {@code toIndex} (exclusive) to {@code false}.
	 *
	 * @param fromIndex index of the first bit to be cleared
	 * @param toIndex index after the last bit to be cleared
	 * @throws IndexOutOfBoundsException if {@code fromIndex} is negative, or {@code toIndex} is negative, or {@code fromIndex} is larger
	 *         than {@code toIndex}
	 * @since 1.4
	 */
	public void clear(int fromIndex, int toIndex) {
		checkRange(fromIndex, toIndex);

		if (fromIndex == toIndex)
			return;

		int startWordIndex = wordIndex(fromIndex);
		if (startWordIndex >= wordsInUse)
			return;

		int endWordIndex = wordIndex(toIndex - 1);
		if (endWordIndex >= wordsInUse) {
			toIndex = length();
			endWordIndex = wordsInUse - 1;
		}

		long firstWordMask = WORD_MASK << fromIndex;
		long lastWordMask = WORD_MASK >>> -toIndex;
		if (startWordIndex == endWordIndex) {
			// Case 1: One word
			words[startWordIndex] &= ~(firstWordMask & lastWordMask);
		} else {
			// Case 2: Multiple words
			// Handle first word
			words[startWordIndex] &= ~firstWordMask;

			// Handle intermediate words, if any
			for (int i = startWordIndex + 1; i < endWordIndex; i++)
				words[i] = 0;

			// Handle last word
			words[endWordIndex] &= ~lastWordMask;
		}

		recalculateWordsInUse();
		checkInvariants();
	}

	/**
	 * Sets all of the bits in this BitSet to {@code false}.
	 *
	 * @since 1.4
	 */
	public void clear() {
		while (wordsInUse > 0)
			words[--wordsInUse] = 0;
	}

	/**
	 * Shifts all bits in this set between <code>index</code> and {@link #length()} <code>length</code> bits forward, leaving a gap between
	 * <code>index</code> (inclusive) and <code>index+length</code> (exclusive)
	 * 
	 * @param index The index at which to insert the interval
	 * @param length The length of the interval to insert
	 */
	public void insertInterval(int index, int length) {
		if (index < 0)
			throw new IndexOutOfBoundsException("index < 0: " + index);
		else if (length == 0)
			return;
		else if (length < 0)
			throw new IllegalArgumentException("length < 0: " + length);
		int thisLen = length();
		if (index >= thisLen)
			return; // No-op

		int firstWord = wordIndex(index);
		int lastSourceIndex = thisLen - 1;
		int lastSourceWord = wordIndex(lastSourceIndex);
		int lastWord = wordIndex(lastSourceIndex + length);
		int sourceWord;
		int destWord = lastWord;
		expandTo(lastWord);
		if (length % BITS_PER_WORD == 0) { // Easier/faster case, since we can move whole words
			sourceWord = lastSourceWord;
			for (; sourceWord > firstWord; sourceWord--, destWord--)
				words[destWord] = words[sourceWord];
			if (index % BITS_PER_WORD == 0) {
				words[destWord] = words[sourceWord];
				words[sourceWord] = 0;
			} else {
				int firstWordBitsToKeep = index - firstWord * BITS_PER_WORD;
				long firstWordMask = WORD_MASK >>> (BITS_PER_WORD - firstWordBitsToKeep);
				long antiFWM = ~firstWordMask;
				words[destWord] = words[sourceWord] & antiFWM;
				words[sourceWord] &= firstWordMask;
			}
		} else {
			int wordDiff = length / BITS_PER_WORD;
			sourceWord = destWord - wordDiff;
			int shift = length - wordDiff * BITS_PER_WORD;
			int antiShift = BITS_PER_WORD - shift;
			int firstWordBitsToKeep = index - firstWord * BITS_PER_WORD;
			long firstWordMask;
			if (firstWordBitsToKeep == 0)
				firstWordMask = 0;
			else
				firstWordMask = WORD_MASK >>> (BITS_PER_WORD - firstWordBitsToKeep);
			long antiFWM = ~firstWordMask;
			if (sourceWord > firstWord) {
				for (; sourceWord > firstWord + 1; sourceWord--, destWord--)
					words[destWord] = (words[sourceWord] << shift) | (words[sourceWord - 1] >>> antiShift);
				words[destWord] = (words[sourceWord] << shift) | ((words[sourceWord - 1] & antiFWM) >>> antiShift);
				sourceWord--;
				destWord--;
			}
			// Now sourceWord==firstWord
			if (destWord == firstWord)
				words[destWord] = ((words[firstWord] & antiFWM) << shift) | (words[firstWord] & firstWordMask);
			else {
				words[destWord] = (words[firstWord] & antiFWM) << shift;
				words[firstWord] &= firstWordMask;

				// Clear any full words in the interval
				for (destWord--; destWord > firstWord; destWord--)
					words[destWord] = 0;
			}
		}
	}

	/**
	 * Removes all bits between <code>index</code> (inclusive) and <code>index+length</code> (exclusive), shifting all bits between
	 * <code>index+length</code> and {@link #length()} backward <code>length</code> bits.
	 * 
	 * @param index The starting index of the interval to remove
	 * @param length The length of the interval to remove
	 */
	public void removeInterval(int index, int length) {
		if (index < 0)
			throw new IndexOutOfBoundsException("index < 0: " + index);
		else if (length == 0)
			return;
		else if (length < 0)
			throw new IllegalArgumentException("length < 0: " + length);
		int thisLen = length();
		if (index >= thisLen)
			return; // No-op
		else if (index + length >= thisLen) {
			clear(index, thisLen);
			return;
		}

		int firstWord = wordIndex(index);
		int firstSourceWord = wordIndex(index + length);
		int sourceWord = firstSourceWord;
		int destWord = firstWord;
		if (length % BITS_PER_WORD == 0) {
			if (index % BITS_PER_WORD == 0) {
				words[destWord] = words[sourceWord];
				words[sourceWord] = 0;
			} else {
				int firstWordBitsToKeep = index - firstWord * BITS_PER_WORD;
				long firstWordMask = WORD_MASK >>> (BITS_PER_WORD - firstWordBitsToKeep);
				long antiFWM = ~firstWordMask;
				words[destWord] = (words[destWord] & firstWordMask) | (words[sourceWord] & antiFWM);
			}
			for (sourceWord++, destWord++; sourceWord < wordsInUse; sourceWord++, destWord++)
				words[destWord] = words[sourceWord];

			for (int w = destWord; w < wordsInUse; w++)
				words[w] = 0;
			wordsInUse = destWord;
		} else {
			int wordDiff = length / BITS_PER_WORD;
			int shift = length - wordDiff * BITS_PER_WORD;
			int antiShift = BITS_PER_WORD - shift;
			int firstWordBitsToKeep = index - firstWord * BITS_PER_WORD;
			long firstWordMask;
			if (firstWordBitsToKeep == 0)
				firstWordMask = 0;
			else
				firstWordMask = WORD_MASK >>> (BITS_PER_WORD - firstWordBitsToKeep);
			long antiFWM = ~firstWordMask;
			if (sourceWord == destWord) {
				sourceWord++;
				if (sourceWord == wordsInUse)
					words[destWord] = (words[destWord] & firstWordMask) | ((words[destWord] >>> shift) & antiFWM);
				else {
					words[destWord] = (words[destWord] & firstWordMask) | ((words[destWord] >>> shift) & antiFWM)
						| (words[sourceWord] << antiShift);
					for (sourceWord++, destWord++; sourceWord < wordsInUse; sourceWord++, destWord++)
						words[destWord] = (words[destWord] >>> shift) | (words[sourceWord] << antiShift);
					words[destWord] >>>= shift;
				}
			} else if (wordDiff == 0 || firstWordBitsToKeep + shift >= BITS_PER_WORD) {
				words[destWord] = (words[destWord] & firstWordMask) | ((words[sourceWord] << antiShift) & antiFWM);
				for (destWord++; sourceWord < wordsInUse - 1; sourceWord++, destWord++)
					words[destWord] = (words[sourceWord] >>> shift) | (words[sourceWord + 1] << antiShift);
				words[destWord] = words[sourceWord] >>> shift;
			} else {
				words[destWord] &= firstWordMask;
				long shifted = words[sourceWord] >>> shift;
				if (sourceWord + 1 < wordsInUse) {
					shifted |= words[sourceWord + 1] << antiShift;
					words[destWord] |= shifted & antiFWM;
					for (sourceWord++, destWord++; sourceWord < wordsInUse - 1; sourceWord++, destWord++)
						words[destWord] = (words[sourceWord] >>> shift) | (words[sourceWord + 1] << antiShift);
					words[destWord] = words[sourceWord] >>> shift;
				} else
					words[destWord] |= shifted & antiFWM;
			}
			if (words[destWord] != 0)
				destWord++;
			for (int w = destWord; w < wordsInUse; w++)
				words[w] = 0;
			wordsInUse = destWord;
		}
	}

	/**
	 * Returns the value of the bit with the specified index. The value is {@code true} if the bit with the index {@code bitIndex} is
	 * currently set in this {@code BetterBitSet}; otherwise, the result is {@code false}.
	 *
	 * @param bitIndex the bit index
	 * @return the value of the bit with the specified index
	 * @throws IndexOutOfBoundsException if the specified index is negative
	 */
	public boolean get(int bitIndex) {
		if (bitIndex < 0)
			throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);

		checkInvariants();

		int wordIndex = wordIndex(bitIndex);
		return (wordIndex < wordsInUse) && ((words[wordIndex] & (1L << bitIndex)) != 0);
	}

	/**
	 * Returns a new {@code BetterBitSet} composed of bits from this {@code BetterBitSet} from {@code fromIndex} (inclusive) to
	 * {@code toIndex} (exclusive).
	 *
	 * @param fromIndex index of the first bit to include
	 * @param toIndex index after the last bit to include
	 * @return a new {@code BetterBitSet} from a range of this {@code BetterBitSet}
	 * @throws IndexOutOfBoundsException if {@code fromIndex} is negative, or {@code toIndex} is negative, or {@code fromIndex} is larger
	 *         than {@code toIndex}
	 * @since 1.4
	 */
	public BetterBitSet get(int fromIndex, int toIndex) {
		checkRange(fromIndex, toIndex);

		checkInvariants();

		int len = length();

		// If no set bits in range return empty bitset
		if (len <= fromIndex || fromIndex == toIndex)
			return new BetterBitSet(0);

		// An optimization
		if (toIndex > len)
			toIndex = len;

		BetterBitSet result = new BetterBitSet(toIndex - fromIndex);
		int targetWords = wordIndex(toIndex - fromIndex - 1) + 1;
		int sourceIndex = wordIndex(fromIndex);
		boolean wordAligned = ((fromIndex & BIT_INDEX_MASK) == 0);

		// Process all words but the last word
		for (int i = 0; i < targetWords - 1; i++, sourceIndex++)
			result.words[i] = wordAligned ? words[sourceIndex]
				: (words[sourceIndex] >>> fromIndex) | (words[sourceIndex + 1] << -fromIndex);

		// Process the last word
		long lastWordMask = WORD_MASK >>> -toIndex;
		result.words[targetWords - 1] = ((toIndex - 1) & BIT_INDEX_MASK) < (fromIndex & BIT_INDEX_MASK) ? /* straddles source words */
			((words[sourceIndex] >>> fromIndex) | (words[sourceIndex + 1] & lastWordMask) << -fromIndex)
			: ((words[sourceIndex] & lastWordMask) >>> fromIndex);

		// Set wordsInUse correctly
		result.wordsInUse = targetWords;
		result.recalculateWordsInUse();
		result.checkInvariants();

		return result;
	}

	/**
	 * Returns the index of the first bit that is set to {@code true} that occurs on or after the specified starting index. If no such bit
	 * exists then {@code -1} is returned.
	 *
	 * <p>
	 * To iterate over the {@code true} bits in a {@code BetterBitSet}, use the following loop:
	 *
	 * <pre>
	 *  {@code
	 * for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
	 *     // operate on index i here
	 *     if (i == Integer.MAX_VALUE) {
	 *         break; // or (i+1) would overflow
	 *     }
	 * }}
	 * </pre>
	 *
	 * @param fromIndex the index to start checking from (inclusive)
	 * @return the index of the next set bit, or {@code -1} if there is no such bit
	 * @throws IndexOutOfBoundsException if the specified index is negative
	 * @since 1.4
	 */
	public int nextSetBit(int fromIndex) {
		if (fromIndex < 0)
			throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);

		checkInvariants();

		int u = wordIndex(fromIndex);
		if (u >= wordsInUse)
			return -1;

		long word = words[u] & (WORD_MASK << fromIndex);

		while (true) {
			if (word != 0)
				return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
			if (++u == wordsInUse)
				return -1;
			word = words[u];
		}
	}

	/**
	 * Returns the index of the first bit that is set to {@code false} that occurs on or after the specified starting index.
	 *
	 * @param fromIndex the index to start checking from (inclusive)
	 * @return the index of the next clear bit
	 * @throws IndexOutOfBoundsException if the specified index is negative
	 * @since 1.4
	 */
	public int nextClearBit(int fromIndex) {
		// Neither spec nor implementation handle bitsets of maximal length.
		// See 4816253.
		if (fromIndex < 0)
			throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);

		checkInvariants();

		int u = wordIndex(fromIndex);
		if (u >= wordsInUse)
			return fromIndex;

		long word = ~words[u] & (WORD_MASK << fromIndex);

		while (true) {
			if (word != 0)
				return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
			if (++u == wordsInUse)
				return wordsInUse * BITS_PER_WORD;
			word = ~words[u];
		}
	}

	/**
	 * Returns the index of the nearest bit that is set to {@code true} that occurs on or before the specified starting index. If no such
	 * bit exists, or if {@code -1} is given as the starting index, then {@code -1} is returned.
	 *
	 * <p>
	 * To iterate over the {@code true} bits in a {@code BetterBitSet}, use the following loop:
	 *
	 * <pre>
	 *  {@code
	 * for (int i = bs.length(); (i = bs.previousSetBit(i-1)) >= 0; ) {
	 *     // operate on index i here
	 * }}
	 * </pre>
	 *
	 * @param fromIndex the index to start checking from (inclusive)
	 * @return the index of the previous set bit, or {@code -1} if there is no such bit
	 * @throws IndexOutOfBoundsException if the specified index is less than {@code -1}
	 * @since 1.7
	 */
	public int previousSetBit(int fromIndex) {
		if (fromIndex < 0) {
			if (fromIndex == -1)
				return -1;
			throw new IndexOutOfBoundsException("fromIndex < -1: " + fromIndex);
		}

		checkInvariants();

		int u = wordIndex(fromIndex);
		if (u >= wordsInUse)
			return length() - 1;

		long word = words[u] & (WORD_MASK >>> -(fromIndex + 1));

		while (true) {
			if (word != 0)
				return (u + 1) * BITS_PER_WORD - 1 - Long.numberOfLeadingZeros(word);
			if (u-- == 0)
				return -1;
			word = words[u];
		}
	}

	/**
	 * Returns the index of the nearest bit that is set to {@code false} that occurs on or before the specified starting index. If no such
	 * bit exists, or if {@code -1} is given as the starting index, then {@code -1} is returned.
	 *
	 * @param fromIndex the index to start checking from (inclusive)
	 * @return the index of the previous clear bit, or {@code -1} if there is no such bit
	 * @throws IndexOutOfBoundsException if the specified index is less than {@code -1}
	 * @since 1.7
	 */
	public int previousClearBit(int fromIndex) {
		if (fromIndex < 0) {
			if (fromIndex == -1)
				return -1;
			throw new IndexOutOfBoundsException("fromIndex < -1: " + fromIndex);
		}

		checkInvariants();

		int u = wordIndex(fromIndex);
		if (u >= wordsInUse)
			return fromIndex;

		long word = ~words[u] & (WORD_MASK >>> -(fromIndex + 1));

		while (true) {
			if (word != 0)
				return (u + 1) * BITS_PER_WORD - 1 - Long.numberOfLeadingZeros(word);
			if (u-- == 0)
				return -1;
			word = ~words[u];
		}
	}

	/**
	 * Returns the "logical size" of this {@code BetterBitSet}: the index of the highest set bit in the {@code BetterBitSet} plus one.
	 * Returns zero if the {@code BetterBitSet} contains no set bits.
	 *
	 * @return the logical size of this {@code BetterBitSet}
	 * @since 1.2
	 */
	public int length() {
		if (wordsInUse == 0)
			return 0;

		return BITS_PER_WORD * (wordsInUse - 1) + (BITS_PER_WORD - Long.numberOfLeadingZeros(words[wordsInUse - 1]));
	}

	/**
	 * Returns true if this {@code BetterBitSet} contains no bits that are set to {@code true}.
	 *
	 * @return boolean indicating whether this {@code BetterBitSet} is empty
	 * @since 1.4
	 */
	public boolean isEmpty() {
		return wordsInUse == 0;
	}

	/**
	 * Returns true if the specified {@code BetterBitSet} has any bits set to {@code true} that are also set to {@code true} in this
	 * {@code BetterBitSet}.
	 *
	 * @param set {@code BetterBitSet} to intersect with
	 * @return boolean indicating whether this {@code BetterBitSet} intersects the specified {@code BetterBitSet}
	 * @since 1.4
	 */
	public boolean intersects(BetterBitSet set) {
		for (int i = Math.min(wordsInUse, set.wordsInUse) - 1; i >= 0; i--)
			if ((words[i] & set.words[i]) != 0)
				return true;
		return false;
	}

	/**
	 * Returns the number of bits set to {@code true} in this {@code BetterBitSet}.
	 *
	 * @return the number of bits set to {@code true} in this {@code BetterBitSet}
	 * @since 1.4
	 */
	public int cardinality() {
		int sum = 0;
		for (int i = 0; i < wordsInUse; i++)
			sum += Long.bitCount(words[i]);
		return sum;
	}

	/**
	 * @param fromIndex The start index
	 * @param toIndex The end index
	 * @return The number of true bits between fromIndex (inclusive) and toIndex (exclusive)
	 */
	public int countBitsSetBetween(int fromIndex, int toIndex) {
		checkRange(fromIndex, toIndex);

		if (fromIndex == toIndex)
			return 0;

		// Increase capacity if necessary
		int startWordIndex = wordIndex(fromIndex);
		if (startWordIndex >= wordsInUse)
			return 0;
		int endWordIndex = wordIndex(toIndex - 1);

		int count = 0;
		int startWordOffset = startWordIndex * BITS_PER_WORD;
		int startBitIndex = fromIndex - startWordOffset;
		int endBitIndex;
		long mask;
		if (endWordIndex == startWordIndex) {
			endBitIndex = toIndex - startWordOffset;
			mask = 1L << startBitIndex;
			for (int i = startBitIndex; i < endBitIndex; i++) {
				if ((words[startWordIndex] & mask) != 0)
					count++;
				mask <<= 1;
			}
		} else {
			if (startBitIndex == 0)
				count += Long.bitCount(words[startBitIndex]);
			else {
				mask = 1L << startBitIndex;
				while (mask != 0) {
					if ((words[startWordIndex] & mask) != 0)
						count++;
					mask <<= 1;
				}
			}
			if (endWordIndex >= wordsInUse) {
				endWordIndex = wordsInUse;
				endBitIndex = 0;
			} else
				endBitIndex = toIndex - (endWordIndex * BITS_PER_WORD);
			for (int w = startWordIndex + 1; w < endWordIndex; w++)
				count += Long.bitCount(words[w]);
			mask = 1L;
			for (int i = 0; i < endBitIndex; i++) {
				if ((words[endWordIndex] & mask) != 0)
					count++;
				mask <<= 1;
			}
		}
		return count;
	}

	/**
	 * @param n The number of set bits to determine the length for
	 * @return The index in this bit set of the <code>n</code>th set bit if <code>n&lt;{@link #cardinality()}</code>, or
	 *         <code>-{@link #cardinality()}-1</code> otherwise
	 */
	public int indexOfNthSetBit(int n) {
		if (n < 0)
			throw new IndexOutOfBoundsException("n < 0: " + n);
		int count = 0;
		for (int w = 0; w < wordsInUse; w++) {
			int wordBits = Long.bitCount(words[w]);
			int nextCount = count + wordBits;
			if (nextCount > n) {
				long mask = 1L;
				int b;
				for (b = 0; true; b++) {
					if ((words[w] & mask) != 0) {
						if (count == n)
							break;
						count++;
					}
					mask <<= 1;
				}
				return w * BITS_PER_WORD + b;
			} else
				count = nextCount;
		}
		return -count - 1;
	}

	/**
	 * @param n The number of clear bits to determine the length for
	 * @return The index in this bit set of the <code>n</code>th clear bit
	 */
	public int indexOfNthClearBit(int n) {
		if (n < 0)
			throw new IndexOutOfBoundsException("n < 0: " + n);
		int count = 0;
		for (int w = 0; w < wordsInUse; w++) {
			int wordClearBits = BITS_PER_WORD - Long.bitCount(words[w]);
			int nextCount = count + wordClearBits;
			if (nextCount > n) {
				long mask = 1L;
				int b;
				for (b = 0; true; b++) {
					if ((words[w] & mask) == 0) {
						if (count == n)
							break;
						count++;
					}
					mask <<= 1;
				}
				return w * BITS_PER_WORD + b;
			} else
				count = nextCount;
		}
		return wordsInUse * BITS_PER_WORD + n - count;
	}

	/**
	 * @param other The bit set to compare to
	 * @param index The starting index to check
	 * @return The smallest index, <code>i &gt;= index</code>, for which <code>this.get(i)!=other.get(i)</code>, or <code>-1</code> if there
	 *         is no such difference
	 */
	public int nextDifference(BetterBitSet other, int index) {
		if (index < 0)
			throw new IndexOutOfBoundsException("index < 0: " + index);
		int word = wordIndex(index);
		if (word >= wordsInUse && word >= other.wordsInUse)
			return -1;

		if (word < wordsInUse && word < other.wordsInUse) {
			if (words[word] != other.words[word]) {
				int bitIndex = index - word * BITS_PER_WORD;
				long wordDiff = words[word] ^ other.words[word];
				if (Long.numberOfLeadingZeros(wordDiff) < BITS_PER_WORD - bitIndex) {
					long mask = 1L << bitIndex;
					while (mask != 0) {
						if ((wordDiff & mask) != 0)
							return index;
						index++;
						mask <<= 1;
					}
				} else
					index = (word + 1) * BITS_PER_WORD;
			} else
				index = (word + 1) * BITS_PER_WORD;

			for (word++; word < wordsInUse && word < other.wordsInUse; word++) {
				if (words[word] != other.words[word])
					break;
				index += BITS_PER_WORD;
			}
		}

		if (word < wordsInUse) {
			if (word < other.wordsInUse) {
				long wordDiff = words[word] ^ other.words[word];
				return index + Long.numberOfTrailingZeros(wordDiff);
			} else
				return index + Long.numberOfTrailingZeros(words[word]);
		} else if (word < other.wordsInUse)
			return index + Long.numberOfTrailingZeros(other.words[word]);
		else
			return -1;
	}

	/**
	 * @param other The bit set to compare to
	 * @param index The starting index to check
	 * @return The largest index, <code>i &lt;= index</code>, for which <code>this.get(i)!=other.get(i)</code>, or <code>-1</code> if there
	 *         is no such difference
	 */
	public int previousDifference(BetterBitSet other, int index) {
		if (index < 0)
			throw new IndexOutOfBoundsException("index < 0: " + index);
		int word = wordIndex(index);
		if (word >= wordsInUse) {
			int length = length() - 1;
			if (word >= other.wordsInUse) {
				int oLen = other.length() - 1;
				if (length < oLen)
					return oLen;
				else if (length > oLen)
					return length;
				else {
					word = wordsInUse - 1;
					index = length;
				}
			} else {
				int psb = other.previousSetBit(index);
				if (length < psb)
					return psb;
				else if (length > psb)
					return length;
				else {
					word = wordsInUse - 1;
					index = length;
				}
			}
		} else if (word >= other.wordsInUse) {
			int length = other.length() - 1;
			int psb = previousSetBit(index);
			if (length < psb)
				return psb;
			else if (length > psb)
				return length;
			else {
				word = other.wordsInUse - 1;
				index = length;
			}
		}

		if (words[word] != other.words[word]) {
			int bitIndex = index - word * BITS_PER_WORD;
			long wordDiff = words[word] ^ other.words[word];
			if (Long.numberOfTrailingZeros(wordDiff) <= bitIndex) {
				long mask = 1L << bitIndex;
				while (mask != 0) {
					if ((wordDiff & mask) != 0)
						return index;
					index--;
					mask >>= 1;
				}
			} else
				index = word * BITS_PER_WORD - 1;
		} else
			index = word * BITS_PER_WORD - 1;

		for (word--; word >= 0; word--) {
			if (words[word] != other.words[word])
				break;
			index -= BITS_PER_WORD;
		}

		if (word < 0)
			return -1;
		long wordDiff = words[word] ^ other.words[word];
		return index - Long.numberOfLeadingZeros(wordDiff);
	}

	/**
	 * Performs a logical <b>AND</b> of this target bit set with the argument bit set. This bit set is modified so that each bit in it has
	 * the value {@code true} if and only if it both initially had the value {@code true} and the corresponding bit in the bit set argument
	 * also had the value {@code true}.
	 *
	 * @param set a bit set
	 */
	public void and(BetterBitSet set) {
		if (this == set)
			return;

		while (wordsInUse > set.wordsInUse)
			words[--wordsInUse] = 0;

		// Perform logical AND on words in common
		for (int i = 0; i < wordsInUse; i++)
			words[i] &= set.words[i];

		recalculateWordsInUse();
		checkInvariants();
	}

	/**
	 * Performs a logical <b>OR</b> of this bit set with the bit set argument. This bit set is modified so that a bit in it has the value
	 * {@code true} if and only if it either already had the value {@code true} or the corresponding bit in the bit set argument has the
	 * value {@code true}.
	 *
	 * @param set a bit set
	 */
	public void or(BetterBitSet set) {
		if (this == set)
			return;

		int wordsInCommon = Math.min(wordsInUse, set.wordsInUse);

		if (wordsInUse < set.wordsInUse) {
			ensureCapacity(set.wordsInUse);
			wordsInUse = set.wordsInUse;
		}

		// Perform logical OR on words in common
		for (int i = 0; i < wordsInCommon; i++)
			words[i] |= set.words[i];

		// Copy any remaining words
		if (wordsInCommon < set.wordsInUse)
			System.arraycopy(set.words, wordsInCommon, words, wordsInCommon, wordsInUse - wordsInCommon);

		// recalculateWordsInUse() is unnecessary
		checkInvariants();
	}

	/**
	 * Performs a logical <b>XOR</b> of this bit set with the bit set argument. This bit set is modified so that a bit in it has the value
	 * {@code true} if and only if one of the following statements holds:
	 * <ul>
	 * <li>The bit initially has the value {@code true}, and the corresponding bit in the argument has the value {@code false}.
	 * <li>The bit initially has the value {@code false}, and the corresponding bit in the argument has the value {@code true}.
	 * </ul>
	 *
	 * @param set a bit set
	 */
	public void xor(BetterBitSet set) {
		int wordsInCommon = Math.min(wordsInUse, set.wordsInUse);

		if (wordsInUse < set.wordsInUse) {
			ensureCapacity(set.wordsInUse);
			wordsInUse = set.wordsInUse;
		}

		// Perform logical XOR on words in common
		for (int i = 0; i < wordsInCommon; i++)
			words[i] ^= set.words[i];

		// Copy any remaining words
		if (wordsInCommon < set.wordsInUse)
			System.arraycopy(set.words, wordsInCommon, words, wordsInCommon, set.wordsInUse - wordsInCommon);

		recalculateWordsInUse();
		checkInvariants();
	}

	/**
	 * Clears all of the bits in this {@code BetterBitSet} whose corresponding bit is set in the specified {@code BetterBitSet}.
	 *
	 * @param set the {@code BetterBitSet} with which to mask this {@code BetterBitSet}
	 * @since 1.2
	 */
	public void andNot(BetterBitSet set) {
		// Perform logical (a & !b) on words in common
		for (int i = Math.min(wordsInUse, set.wordsInUse) - 1; i >= 0; i--)
			words[i] &= ~set.words[i];

		recalculateWordsInUse();
		checkInvariants();
	}

	/**
	 * Replaces this bit set's content with the content of the given bit set. Immediately after this call, <code>this.equals(other)</code>
	 * will return true.
	 * 
	 * @param other The bit set to replace this bit set's content with
	 * @return This bit set
	 */
	public BetterBitSet replaceWith(BetterBitSet other) {
		ensureCapacity(other.wordsInUse);
		wordsInUse = other.wordsInUse;
		System.arraycopy(other.words, 0, words, 0, wordsInUse);
		sizeIsSticky = other.sizeIsSticky;
		return this;
	}

	/**
	 * Returns the hash code value for this bit set. The hash code depends only on which bits are set within this {@code BetterBitSet}.
	 *
	 * <p>
	 * The hash code is defined to be the result of the following calculation:
	 * 
	 * <pre>
	 *  {@code
	 * public int hashCode() {
	 *     long h = 1234;
	 *     long[] words = toLongArray();
	 *     for (int i = words.length; --i >= 0; )
	 *         h ^= words[i] * (i + 1);
	 *     return (int)((h >> 32) ^ h);
	 * }}
	 * </pre>
	 * 
	 * Note that the hash code changes if the set of bits is altered.
	 *
	 * @return the hash code value for this bit set
	 */
	@Override
	public int hashCode() {
		long h = 1234;
		for (int i = wordsInUse; --i >= 0;)
			h ^= words[i] * (i + 1);

		return (int) ((h >> 32) ^ h);
	}

	/**
	 * Returns the number of bits of space actually in use by this {@code BetterBitSet} to represent bit values. The maximum element in the
	 * set is the size - 1st element.
	 *
	 * @return the number of bits currently in this bit set
	 */
	public int size() {
		return words.length * BITS_PER_WORD;
	}

	/**
	 * Compares this object against the specified object. The result is {@code true} if and only if the argument is not {@code null} and is
	 * a {@code Bitset} object that has exactly the same set of bits set to {@code true} as this bit set. That is, for every nonnegative
	 * {@code int} index {@code k},
	 * 
	 * <pre>
	 * ((BetterBitSet) obj).get(k) == this.get(k)
	 * </pre>
	 * 
	 * must be true. The current sizes of the two bit sets are not compared.
	 *
	 * @param obj the object to compare with
	 * @return {@code true} if the objects are the same; {@code false} otherwise
	 * @see #size()
	 */
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof BetterBitSet))
			return false;
		if (this == obj)
			return true;

		BetterBitSet set = (BetterBitSet) obj;

		checkInvariants();
		set.checkInvariants();

		if (wordsInUse != set.wordsInUse)
			return false;

		// Check words in use by both BitSets
		for (int i = 0; i < wordsInUse; i++)
			if (words[i] != set.words[i])
				return false;

		return true;
	}

	/**
	 * Cloning this {@code BetterBitSet} produces a new {@code BetterBitSet} that is equal to it. The clone of the bit set is another bit
	 * set that has exactly the same bits set to {@code true} as this bit set.
	 *
	 * @return a clone of this bit set
	 * @see #size()
	 */
	@Override
	public BetterBitSet clone() {
		if (!sizeIsSticky)
			trimToSize();

		try {
			BetterBitSet result = (BetterBitSet) super.clone();
			result.words = words.clone();
			result.checkInvariants();
			return result;
		} catch (CloneNotSupportedException e) {
			throw new InternalError(e);
		}
	}

	/**
	 * Attempts to reduce internal storage used for the bits in this bit set. Calling this method may, but is not required to, affect the
	 * value returned by a subsequent call to the {@link #size()} method.
	 */
	private void trimToSize() {
		if (wordsInUse != words.length) {
			words = Arrays.copyOf(words, wordsInUse);
			checkInvariants();
		}
	}

	/**
	 * Save the state of the {@code BetterBitSet} instance to a stream (i.e., serialize it).
	 */
	private void writeObject(ObjectOutputStream s) throws IOException {

		checkInvariants();

		if (!sizeIsSticky)
			trimToSize();

		ObjectOutputStream.PutField fields = s.putFields();
		fields.put("bits", words);
		s.writeFields();
	}

	/**
	 * Reconstitute the {@code BetterBitSet} instance from a stream (i.e., deserialize it).
	 */
	private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {

		ObjectInputStream.GetField fields = s.readFields();
		words = (long[]) fields.get("bits", null);

		// Assume maximum length then find real length
		// because recalculateWordsInUse assumes maintenance
		// or reduction in logical size
		wordsInUse = words.length;
		recalculateWordsInUse();
		sizeIsSticky = (words.length > 0 && words[words.length - 1] == 0L); // heuristic
		checkInvariants();
	}

	/**
	 * Returns a string representation of this bit set. For every index for which this {@code BetterBitSet} contains a bit in the set state,
	 * the decimal representation of that index is included in the result. Such indices are listed in order from lowest to highest,
	 * separated by ",&nbsp;" (a comma and a space) and surrounded by braces, resulting in the usual mathematical notation for a set of
	 * integers.
	 *
	 * <p>
	 * Example:
	 * 
	 * <pre>
	 * BetterBitSet drPepper = new BetterBitSet();
	 * </pre>
	 * 
	 * Now {@code drPepper.toString()} returns "{@code {}}".
	 * 
	 * <pre>
	 * drPepper.set(2);
	 * </pre>
	 * 
	 * Now {@code drPepper.toString()} returns "{@code {2}}".
	 * 
	 * <pre>
	 * drPepper.set(4);
	 * drPepper.set(10);
	 * </pre>
	 * 
	 * Now {@code drPepper.toString()} returns "{@code {2, 4, 10}}".
	 *
	 * @return a string representation of this bit set
	 */
	@Override
	public String toString() {
		checkInvariants();

		int numBits = (wordsInUse > 128) ? cardinality() : wordsInUse * BITS_PER_WORD;
		StringBuilder b = new StringBuilder(6 * numBits + 2);
		b.append('{');

		int i = nextSetBit(0);
		if (i != -1) {
			b.append(i);
			while (true) {
				if (++i < 0)
					break;
				if ((i = nextSetBit(i)) < 0)
					break;
				int endOfRun = nextClearBit(i);
				do {
					b.append(", ").append(i);
				} while (++i != endOfRun);
			}
		}

		b.append('}');
		return b.toString();
	}

	/**
	 * Returns a stream of indices for which this {@code BetterBitSet} contains a bit in the set state. The indices are returned in order,
	 * from lowest to highest. The size of the stream is the number of bits in the set state, equal to the value returned by the
	 * {@link #cardinality()} method.
	 *
	 * <p>
	 * The stream binds to this bit set when the terminal stream operation commences (specifically, the spliterator for the stream is
	 * <a href="Spliterator.html#binding"><em>late-binding</em></a>). If the bit set is modified during that operation then the result is
	 * undefined.
	 *
	 * @return a stream of integers representing set indices
	 * @since 1.8
	 */
	public IntStream stream() {
		class BitSetSpliterator implements Spliterator.OfInt {
			private int index; // current bit index for a set bit
			private int fence; // -1 until used; then one past last bit index
			private int est; // size estimate
			private boolean root; // true if root and not split
			// root == true then size estimate is accurate
			// index == -1 or index >= fence if fully traversed
			// Special case when the max bit set is Integer.MAX_VALUE

			BitSetSpliterator(int origin, int fence, int est, boolean root) {
				this.index = origin;
				this.fence = fence;
				this.est = est;
				this.root = root;
			}

			private int getFence() {
				int hi;
				if ((hi = fence) < 0) {
					// Round up fence to maximum cardinality for allocated words
					// This is sufficient and cheap for sequential access
					// When splitting this value is lowered
					hi = fence = (wordsInUse >= wordIndex(Integer.MAX_VALUE)) ? Integer.MAX_VALUE : wordsInUse << ADDRESS_BITS_PER_WORD;
					est = cardinality();
					index = nextSetBit(0);
				}
				return hi;
			}

			@Override
			public boolean tryAdvance(IntConsumer action) {
				Objects.requireNonNull(action);

				int hi = getFence();
				int i = index;
				if (i < 0 || i >= hi) {
					// Check if there is a final bit set for Integer.MAX_VALUE
					if (i == Integer.MAX_VALUE && hi == Integer.MAX_VALUE) {
						index = -1;
						action.accept(Integer.MAX_VALUE);
						return true;
					}
					return false;
				}

				index = nextSetBit(i + 1, wordIndex(hi - 1));
				action.accept(i);
				return true;
			}

			@Override
			public void forEachRemaining(IntConsumer action) {
				Objects.requireNonNull(action);

				int hi = getFence();
				int i = index;
				index = -1;

				if (i >= 0 && i < hi) {
					action.accept(i++);

					int u = wordIndex(i); // next lower word bound
					int v = wordIndex(hi - 1); // upper word bound

					words_loop: for (; u <= v && i <= hi; u++, i = u << ADDRESS_BITS_PER_WORD) {
						long word = words[u] & (WORD_MASK << i);
						while (word != 0) {
							i = (u << ADDRESS_BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
							if (i >= hi) {
								// Break out of outer loop to ensure check of
								// Integer.MAX_VALUE bit set
								break words_loop;
							}

							// Flip the set bit
							word &= ~(1L << i);

							action.accept(i);
						}
					}
				}

				// Check if there is a final bit set for Integer.MAX_VALUE
				if (i == Integer.MAX_VALUE && hi == Integer.MAX_VALUE) {
					action.accept(Integer.MAX_VALUE);
				}
			}

			@Override
			public OfInt trySplit() {
				int hi = getFence();
				int lo = index;
				if (lo < 0) {
					return null;
				}

				// Lower the fence to be the upper bound of last bit set
				// The index is the first bit set, thus this spliterator
				// covers one bit and cannot be split, or two or more
				// bits
				hi = fence = (hi < Integer.MAX_VALUE || !get(Integer.MAX_VALUE)) ? previousSetBit(hi - 1) + 1 : Integer.MAX_VALUE;

				// Find the mid point
				int mid = (lo + hi) >>> 1;
				if (lo >= mid) {
					return null;
				}

				// Raise the index of this spliterator to be the next set bit
				// from the mid point
				index = nextSetBit(mid, wordIndex(hi - 1));
				root = false;

				// Don't lower the fence (mid point) of the returned spliterator,
				// traversal or further splitting will do that work
				return new BitSetSpliterator(lo, mid, est >>>= 1, false);
			}

			@Override
			public long estimateSize() {
				getFence(); // force init
				return est;
			}

			@Override
			public int characteristics() {
				// Only sized when root and not split
				return (root ? Spliterator.SIZED : 0) | Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.SORTED;
			}

			@Override
			public Comparator<? super Integer> getComparator() {
				return null;
			}
		}
		return StreamSupport.intStream(new BitSetSpliterator(0, -1, 0, true), false);
	}

	/**
	 * Returns the index of the first bit that is set to {@code true} that occurs on or after the specified starting index and up to and
	 * including the specified word index If no such bit exists then {@code -1} is returned.
	 *
	 * @param fromIndex the index to start checking from (inclusive)
	 * @param toWordIndex the last word index to check (inclusive)
	 * @return the index of the next set bit, or {@code -1} if there is no such bit
	 */
	private int nextSetBit(int fromIndex, int toWordIndex) {
		int u = wordIndex(fromIndex);
		// Check if out of bounds
		if (u > toWordIndex)
			return -1;

		long word = words[u] & (WORD_MASK << fromIndex);

		while (true) {
			if (word != 0)
				return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
			// Check if out of bounds
			if (++u > toWordIndex)
				return -1;
			word = words[u];
		}
	}

}

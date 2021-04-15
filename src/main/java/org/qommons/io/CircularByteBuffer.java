package org.qommons.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.qommons.StringUtils;
import org.qommons.StringUtils.CharAccumulator;

/** A character buffer that uses its storage circularly to make for efficient FIFO functionality */
public class CircularByteBuffer implements StringUtils.BinaryAccumulator {
	private byte[] theBuffer;
	private int theOffset;
	private int theLength;

	/** @param initialCapacity The initial capacity for the buffer, or &lt;0 to use a default capacity */
	public CircularByteBuffer(int initialCapacity) {
		theBuffer = new byte[initialCapacity <= 0 ? 1024 : initialCapacity];
	}

	private void ensureCapacity(int cap) {
		if (cap > theBuffer.length) {
			int newCap = theBuffer.length * 2;
			while (newCap < cap)
				newCap *= 2;
			byte[] newBuffer = new byte[newCap];
			if (theLength > 0) {
				int firstLen = Math.min(theLength, theBuffer.length - theOffset);
				System.arraycopy(theBuffer, theOffset, newBuffer, 0, firstLen);
				if (firstLen < theLength) {
					System.arraycopy(theBuffer, 0, newBuffer, firstLen, theLength - firstLen);
				}
			}
			theBuffer = newBuffer;
			theOffset = 0;
		}
	}

	/** @return This buffer's current capacity */
	public int getCapacity() {
		return theBuffer.length;
	}

	/** @return The number of bytes currently in this buffer */
	public int length() {
		return theLength;
	}

	/**
	 * @param index the index of the byte to get
	 * @return The byte in this buffer at the given index
	 * @throws IndexOutOfBoundsException If the index is &lt;0 or beyond the number of bytes in the buffer
	 */
	public byte get(int index) throws IndexOutOfBoundsException {
		if (index < 0 || index >= theLength)
			throw new IndexOutOfBoundsException(index + " of " + theLength);
		int i = theOffset + index;
		if (i >= theBuffer.length)
			i -= theBuffer.length;
		return theBuffer[i];
	}

	/**
	 * Copies data from this buffer into the given character array
	 * 
	 * @param sourceOffset The offset in this buffer to copy from
	 * @param buffer The buffer to copy the data into
	 * @param destOffset The offset in the destination array
	 * @param length The number of characters to copy
	 * @return This buffer
	 */
	public CircularByteBuffer copyTo(int sourceOffset, byte[] buffer, int destOffset, int length) {
		if (sourceOffset < 0 || length < 0 || sourceOffset + length > theLength)
			throw new IndexOutOfBoundsException(sourceOffset + ":" + length + " of " + theLength);
		if (length > buffer.length - destOffset)
			throw new IndexOutOfBoundsException(destOffset + ":" + length + " of " + buffer.length);
		int off = theOffset + sourceOffset;
		if (off >= theBuffer.length)
			off -= theBuffer.length;
		int firstLen = Math.min(length, theBuffer.length - off);
		System.arraycopy(theBuffer, off, buffer, destOffset, firstLen);
		if (firstLen < length)
			System.arraycopy(theBuffer, 0, buffer, destOffset + firstLen, length - firstLen);
		return this;
	}

	/**
	 * @param bytes The other buffer copy content from
	 * @return This buffer
	 */
	public CircularByteBuffer append(CircularByteBuffer bytes) {
		return insert(theLength, bytes.theBuffer, 0, bytes.length());
	}

	/**
	 * @param bytes The other buffer to copy content from
	 * @param start The start index in the other buffer to copy from
	 * @param end The end index in the other buffer to copy from
	 * @return This buffer
	 */
	public CircularByteBuffer append(CircularByteBuffer bytes, int start, int end) {
		return insert(theLength, bytes, start, end);
	}

	/**
	 * @param bytes The byte array to append data from
	 * @param start The offset in the array to copy from (inclusive)
	 * @param end The offset in the array to copy to (exclusive)
	 * @return This buffer
	 */
	public CircularByteBuffer append(byte[] bytes, int start, int end) {
		return insert(theLength, bytes, start, end);
	}

	/**
	 * Appends one byte to this buffer
	 * 
	 * @param b The byte to append
	 * @return This buffer
	 */
	public CircularByteBuffer append(byte b) {
		ensureCapacity(theLength + 1);
		int pos = theOffset + theLength;
		if (pos >= theBuffer.length)
			pos -= theBuffer.length;
		theBuffer[pos] = b;
		theLength++;
		return this;
	}

	@Override
	public boolean canAcceptMore() {
		return true;
	}

	@Override
	public boolean accumulate(byte nextByte) {
		append(nextByte);
		return true;
	}

	/** @return A byte iterator for this buffer's content */
	public StringUtils.ByteIterator iterator() {
		return new StringUtils.ByteIterator() {
			private int theIndex;

			@Override
			public boolean hasNext() throws IOException {
				return theIndex < length();
			}

			@Override
			public int next() throws IOException {
				if (theIndex >= length())
					throw new IOException("No more bytes");
				return get(theIndex++);
			}
		};
	}

	/**
	 * Clears this buffer of content
	 * 
	 * @param hard Whether to actually erase the content from memory (for security)
	 * @return This buffer
	 */
	public CircularByteBuffer clear(boolean hard) {
		theOffset = theLength = 0;
		if (hard)
			Arrays.fill(theBuffer, (byte) 0);
		return this;
	}

	/**
	 * @param atIndex The index in this buffer to insert the content at
	 * @param bytes The byte array containing the content to insert
	 * @param start The start index in the array to copy from (inclusive)
	 * @param end The end index in the array to copy from (exclusive)
	 * @return This buffer
	 */
	public CircularByteBuffer insert(int atIndex, CircularByteBuffer bytes, int start, int end) {
		if (end > bytes.theLength)
			throw new IndexOutOfBoundsException(start + " to " + end + " of " + bytes.theLength);
		if (start < 0 || start > end || end > bytes.theLength)
			throw new IndexOutOfBoundsException(start + " to " + end + " of " + bytes.theLength);
		int len = end - start;
		int startPos = bytes.theOffset + start;
		if (startPos >= bytes.theBuffer.length)
			startPos -= bytes.theBuffer.length;
		int endPos = startPos + len;
		if (endPos < theBuffer.length)
			insert(theLength, bytes.theBuffer, startPos, endPos);
		else {
			insert(theLength, bytes.theBuffer, startPos, bytes.theBuffer.length);
			insert(theLength, bytes.theBuffer, 0, endPos - bytes.theBuffer.length);
		}
		return insert(atIndex, bytes.theBuffer, start, end);
	}

	/**
	 * @param atIndex The index in this buffer to insert the content at
	 * @param bytes The byte sequence containing the content to insert
	 * @param start The start index in the sequence to copy from (inclusive)
	 * @param end The end index in the sequence to copy from (exclusive)
	 * @return This buffer
	 */
	public CircularByteBuffer insert(int atIndex, byte[] bytes, int start, int end) {
		if (start < 0 || start > end || end > bytes.length)
			throw new IndexOutOfBoundsException(start + "..." + end + " of " + bytes.length);
		if (atIndex < 0 || atIndex > theLength)
			throw new IndexOutOfBoundsException(atIndex + " of " + theLength);
		int len = end - start;
		ensureCapacity(theLength + len);
		// First, move content after the insertion point to where it will need to go
		if (atIndex < theLength)
			moveContent(atIndex, theLength - atIndex, atIndex + len);
		int pos = theOffset + theLength;
		if (pos >= theBuffer.length)
			pos -= theBuffer.length;
		for (int i = start; i < end; i++, pos++) {
			if (pos >= theBuffer.length)
				pos = 0;
			theBuffer[pos] = bytes[i];
		}
		theLength += len;
		return this;
	}

	/**
	 * @param start The start position of the range to delete (inclusive)
	 * @param end The end position of the range to delete (exclusive)
	 * @return This buffer
	 */
	public CircularByteBuffer delete(int start, int end) {
		return delete(start, end, false);
	}

	/**
	 * @param start The start position of the range to delete (inclusive)
	 * @param end The end position of the range to delete (exclusive)
	 * @param hard Whether to actually erase the content from memory (for security)
	 * @return This buffer
	 */
	public CircularByteBuffer delete(int start, int end, boolean hard) {
		if (start < 0 || start > end || end > theLength)
			throw new IndexOutOfBoundsException(start + "..." + end + " of " + theLength);
		int len = end - start;
		if (len > 0) {
			if (end < theLength) {
				if (start == 0) {
					if (hard) {
						int firstLen = Math.min(len, theBuffer.length - theOffset);
						Arrays.fill(theBuffer, theOffset, theOffset + firstLen, (byte) 0);
						if (firstLen < len)
							Arrays.fill(theBuffer, 0, len - firstLen, (byte) 0);
					}
					theOffset += len;
					if (theOffset >= theBuffer.length)
						theOffset -= theBuffer.length;
				} else {
					moveContent(end, theLength - end, start);
					if (hard) {
						int firstLen = Math.min(len, theBuffer.length - theOffset);
						Arrays.fill(theBuffer, theOffset + start, theOffset + start + firstLen, (byte) 0);
						if (firstLen < len)
							Arrays.fill(theBuffer, 0, len - firstLen, (byte) 0);
					}
				}
			} else if (hard) {
				int firstLen = Math.min(len, theBuffer.length - theOffset);
				Arrays.fill(theBuffer, theOffset + start, theOffset + start + firstLen, (byte) 0);
				if (firstLen < len)
					Arrays.fill(theBuffer, 0, len - firstLen, (byte) 0);
			}
			theLength -= end - start;
			if (theLength == 0)
				theOffset = 0;
		}
		return this;
	}

	/**
	 * @param input The stream to pull byte data from
	 * @param max The maximum number of bytes to read, or &lt;0 to read all the content from the stream
	 * @return The number of bytes read, or -1 if no bytes were read and the end of the stream has been reached
	 * @throws IOException If the reader throws an exception
	 */
	public int appendFrom(InputStream input, int max) throws IOException {
		if (max < 0)
			max = Integer.MAX_VALUE;
		// Read from the buffer as long as it has more content, up to the max
		int totalRead = 0;
		int target, read;
		do {
			if (theLength == theBuffer.length)
				ensureCapacity(theLength * 2);

			int off = theOffset + theLength;
			if (off >= theBuffer.length) {
				off -= theBuffer.length;
			}
			target = Math.min(max - totalRead, theBuffer.length - off);
			read = input.read(theBuffer, off, target);
			if (read < 0)
				return totalRead == 0 ? -1 : totalRead;
			totalRead += read;
			theLength += read;
		} while (read == target && totalRead < max && input.available() > 0);
		return totalRead;
	}

	/**
	 * Writes some of the content from this buffer to an OutputStream
	 * 
	 * @param output The stream to write to
	 * @param offset The offset in this buffer to write from
	 * @param length The number of bytes to write
	 * @return This buffer
	 * @throws IOException If an error occurs writing the data
	 */
	public CircularByteBuffer writeContent(OutputStream output, int offset, int length) throws IOException {
		if (offset < 0 || length < 0 || offset + length > theLength)
			throw new IndexOutOfBoundsException(offset + " to " + (offset + length) + " of " + theLength);
		if (length < 0)
			length = theLength - offset;
		if (length == 0)
			return this;
		int startPos = theOffset + offset;
		if (startPos >= theBuffer.length)
			startPos -= theBuffer.length;
		if (startPos + length <= theBuffer.length) {
			output.write(theBuffer, startPos, length);
		} else {
			output.write(theBuffer, startPos, theBuffer.length - startPos);
			output.write(theBuffer, 0, length - (theBuffer.length - startPos));
		}
		return this;
	}

	private void moveContent(int srcStart, int length, int dest) {
		// There may be up to 3 moves that are required depending on whether the source range, the destination range, or both are wrapped
		int srcLen1 = Math.min(length, theBuffer.length - srcStart);
		int destLen1 = Math.min(length, theBuffer.length - dest);
		if (srcLen1 < destLen1) { // The source sequence wraps earliest
			System.arraycopy(theBuffer, srcStart, theBuffer, dest, srcLen1);
			int len2 = destLen1 - srcLen1;
			System.arraycopy(theBuffer, 0, theBuffer, dest + srcLen1, len2);
			if (destLen1 < length)
				System.arraycopy(theBuffer, len2, theBuffer, 0, length - destLen1);
		} else {
			System.arraycopy(theBuffer, srcStart, theBuffer, dest, destLen1);
			if (destLen1 < srcLen1) {
				int len2 = srcLen1 - destLen1;
				System.arraycopy(theBuffer, srcStart + destLen1, theBuffer, 0, len2);
				if (srcLen1 < length) {
					System.arraycopy(theBuffer, 0, theBuffer, len2, length - srcLen1);
				}
			}
		}
	}

	@Override
	public int hashCode() {
		int hash = 0, fourBytes = 0, position = theOffset;
		for (int i = 0; i < theLength; i++) {
			switch (i % 4) {
			case 0:
				fourBytes = theBuffer[position] << 24;
				break;
			case 1:
				fourBytes |= (theBuffer[position] & 0xff0000) << 16;
				break;
			case 2:
				fourBytes |= (theBuffer[position] & 0xff00) << 8;
				break;
			case 3:
				fourBytes |= theBuffer[position] & 0xff;
				hash ^= fourBytes;
				fourBytes = 0;
				break;
			}
			position++;
			if (position == theBuffer.length)
				position = 0;
		}
		hash ^= fourBytes;
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (!(obj instanceof CircularByteBuffer))
			return false;
		CircularByteBuffer other = (CircularByteBuffer) obj;
		if (theLength != other.theLength)
			return false;
		for (int i = 0; i < theLength; i++) {
			if (get(i) != other.get(i))
				return false;
		}
		return true;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder(theLength * 2);
		CharAccumulator accum = new StringUtils.AppendableWriter<>(str);
		try {
			StringUtils.encodeHex().format(iterator(), accum, null);
		} catch (IOException e) {
			throw new IllegalStateException("Should not happen");
		}
		return str.toString();
	}
}

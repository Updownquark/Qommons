package org.qommons.io;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;

import org.qommons.AbstractCharSequence;
import org.qommons.StringUtils;

/** A character buffer that uses its storage circularly to make for efficient FIFO functionality */
public class CircularCharBuffer extends AbstractCharSequence implements Appendable, StringUtils.CharAccumulator {
	private char[] theBuffer;
	private int theOffset;
	private int theLength;

	/** @param initialCapacity The initial capacity for the buffer, or &lt;0 to use a default capacity */
	public CircularCharBuffer(int initialCapacity) {
		theBuffer = new char[initialCapacity <= 0 ? 1024 : initialCapacity];
	}

	private void ensureCapacity(int cap) {
		if (cap > theBuffer.length) {
			int newCap = theBuffer.length * 2;
			while (newCap < cap)
				newCap *= 2;
			char[] newBuffer = new char[newCap];
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

	@Override
	public int length() {
		return theLength;
	}

	/**
	 * @param index the index of the character to get
	 * @return The character in this buffer at the given index
	 * @throws IndexOutOfBoundsException If the index is &lt;0 or beyond the number of characters in the buffer
	 */
	public char get(int index) throws IndexOutOfBoundsException {
		if (index < 0 || index >= theLength)
			throw new IndexOutOfBoundsException(index + " of " + theLength);
		int i = theOffset + index;
		if (i >= theBuffer.length)
			i -= theBuffer.length;
		return theBuffer[i];
	}

	/**
	 * Similar to {@link Reader#read()}, returns and discards the first character in the buffer, or returns -1 if the buffer is empty
	 * 
	 * @return The first character in the buffer, or -1 if the buffer is empty
	 */
	public int pop() {
		if (theLength == 0)
			return -1;
		char value = theBuffer[theOffset];
		theOffset = (theOffset + 1) % theBuffer.length;
		return value;
	}

	@Override
	public char charAt(int index) {
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
	public CircularCharBuffer copyTo(int sourceOffset, char[] buffer, int destOffset, int length) {
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

	@Override
	public CircularCharBuffer append(CharSequence csq) {
		return insert(theLength, csq, 0, csq.length());
	}

	@Override
	public CircularCharBuffer append(CharSequence csq, int start, int end) {
		return insert(theLength, csq, start, end);
	}

	/**
	 * @param csq The character array to append data from
	 * @param start The offset in the array to copy from (inclusive)
	 * @param end The offset in the array to copy to (exclusive)
	 * @return This buffer
	 */
	public CircularCharBuffer append(char[] csq, int start, int end) {
		return insert(theLength, new String(csq), start, end);
	}

	@Override
	public CircularCharBuffer append(char c) {
		ensureCapacity(theLength + 1);
		int pos = theOffset + theLength;
		if (pos >= theBuffer.length)
			pos -= theBuffer.length;
		theBuffer[pos] = c;
		theLength++;
		return this;
	}

	@Override
	public boolean canAcceptMore() {
		return true;
	}

	@Override
	public boolean accumulate(char nextChar) {
		append(nextChar);
		return true;
	}

	/**
	 * Clears this buffer of content
	 * 
	 * @param hard Whether to actually erase the content from memory (for security)
	 * @return This buffer
	 */
	public CircularCharBuffer clear(boolean hard) {
		theOffset = theLength = 0;
		if (hard)
			Arrays.fill(theBuffer, (char) 0);
		return this;
	}

	/**
	 * @param atIndex The index in this buffer to insert the content at
	 * @param csq The character array containing the content to insert
	 * @param start The start index in the array to copy from (inclusive)
	 * @param end The end index in the array to copy from (exclusive)
	 * @return This buffer
	 */
	public CircularCharBuffer insert(int atIndex, char[] csq, int start, int end) {
		return insert(atIndex, new String(csq), start, end);
	}

	/**
	 * @param atIndex The index in this buffer to insert the content at
	 * @param csq The character sequence containing the content to insert
	 * @param start The start index in the sequence to copy from (inclusive)
	 * @param end The end index in the sequence to copy from (exclusive)
	 * @return This buffer
	 */
	public CircularCharBuffer insert(int atIndex, CharSequence csq, int start, int end) {
		if (start < 0 || start > end || end > csq.length())
			throw new IndexOutOfBoundsException(start + "..." + end + " of " + csq.length());
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
			theBuffer[pos] = csq.charAt(i);
		}
		theLength += len;
		return this;
	}

	/**
	 * @param start The start position of the range to delete (inclusive)
	 * @param end The end position of the range to delete (exclusive)
	 * @return This buffer
	 */
	public CircularCharBuffer delete(int start, int end) {
		return delete(start, end, false);
	}

	/**
	 * @param start The start position of the range to delete (inclusive)
	 * @param end The end position of the range to delete (exclusive)
	 * @param hard Whether to actually erase the content from memory (for security)
	 * @return This buffer
	 */
	public CircularCharBuffer delete(int start, int end, boolean hard) {
		if (start < 0 || start > end || end > theLength)
			throw new IndexOutOfBoundsException(start + "..." + end + " of " + theLength);
		int len = end - start;
		if (len > 0) {
			if (end < theLength) {
				if (start == 0) {
					if (hard) {
						int firstLen = Math.min(len, theBuffer.length - theOffset);
						Arrays.fill(theBuffer, theOffset, theOffset + firstLen, (char) 0);
						if (firstLen < len)
							Arrays.fill(theBuffer, 0, len - firstLen, (char) 0);
					}
					theOffset += len;
					if (theOffset >= theBuffer.length)
						theOffset -= theBuffer.length;
				} else {
					moveContent(end, theLength - end, start);
					if (hard) {
						int firstLen = Math.min(len, theBuffer.length - theOffset);
						Arrays.fill(theBuffer, theOffset + start, theOffset + start + firstLen, (char) 0);
						if (firstLen < len)
							Arrays.fill(theBuffer, 0, len - firstLen, (char) 0);
					}
				}
			} else if (hard) {
				int firstLen = Math.min(len, theBuffer.length - theOffset);
				Arrays.fill(theBuffer, theOffset + start, theOffset + start + firstLen, (char) 0);
				if (firstLen < len)
					Arrays.fill(theBuffer, 0, len - firstLen, (char) 0);
			}
			theLength -= end - start;
			if (theLength == 0)
				theOffset = 0;
		}
		return this;
	}

	/**
	 * @param reader The reader to pull character data from
	 * @param max The maximum number of characters to read, or &lt;0 to read all the content from the reader
	 * @return The number of characters read, or -1 if no characters were read and the end of the stream has been reached
	 * @throws IOException If the reader throws an exception
	 */
	public int appendFrom(Reader reader, int max) throws IOException {
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
			read = reader.read(theBuffer, off, target);
			if (read < 0)
				return totalRead == 0 ? -1 : totalRead;
			totalRead += read;
			theLength += read;
		} while (read == target && totalRead < max && reader.ready());
		return totalRead;
	}

	/**
	 * Writes some of the content from this buffer to a Writer
	 * 
	 * @param writer The writer to write to
	 * @param offset The offset in this buffer to write from
	 * @param length The number of characters to write
	 * @return This buffer
	 * @throws IOException If an error occurs writing the data
	 */
	public CircularCharBuffer writeContent(Writer writer, int offset, int length) throws IOException {
		if (offset < 0 || offset + length > theLength)
			throw new IndexOutOfBoundsException(offset + " to " + (offset + length) + " of " + theLength);
		if (length < 0)
			length = theLength - offset;
		if (length == 0)
			return this;
		int startPos = theOffset + offset;
		if (startPos > theBuffer.length)
			startPos -= theBuffer.length;
		if (startPos + length <= theBuffer.length) {
			writer.write(theBuffer, startPos, length);
		} else {
			writer.write(theBuffer, startPos, theBuffer.length - startPos);
			writer.write(theBuffer, 0, length - (theBuffer.length - startPos));
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

	/** @return A {@link Reader} that reads characters from this buffer */
	public Reader asReader() {
		class CCBReader extends Reader {
			private int theStreamOffset;

			@Override
			public int read() {
				if (theStreamOffset >= theLength)
					return -1;
				return get(theStreamOffset++) & 0xff;
			}

			@Override
			public int read(char[] b, int off, int len) {
				int remain = theLength - theStreamOffset;
				if (remain == 0)
					return -1;
				else if (len < remain) {
					copyTo(theStreamOffset, b, off, len);
					theStreamOffset += len;
				} else {
					len = remain;
					copyTo(theStreamOffset, b, off, remain);
					theStreamOffset = theLength;
				}
				return len;
			}

			@Override
			public void close() {
			}
		}
		return new CCBReader();
	}

	/** @return A {@link Reader} that reads (and removes) characters off the front of this buffer */
	public Reader asDeletingReader() {
		class DeletingCCBReader extends Reader {
			@Override
			public int read() {
				return pop() & 0xff;
			}

			@Override
			public int read(char[] b, int off, int len) {
				if (len < theLength) {
					copyTo(0, b, off, len);
					delete(0, len);
				} else {
					len = theLength;
					copyTo(0, b, off, theLength);
					clear(false);
				}
				return len;
			}

			@Override
			public void close() {
			}
		}
		return new DeletingCCBReader();
	}

	/** @return A {@link Writer} that appends to the end of this buffer */
	public Writer asWriter() {
		class CBBWriter extends Writer {
			@Override
			public void write(int b) {
				CircularCharBuffer.this.append((char) b);
			}

			@Override
			public void write(char[] b, int off, int len) {
				CircularCharBuffer.this.append(b, off, len - off);
			}

			@Override
			public void flush() {
			}

			@Override
			public void close() {
			}
		}
		return new CBBWriter();
	}
}

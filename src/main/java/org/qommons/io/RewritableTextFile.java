package org.qommons.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import org.qommons.AbstractCharSequence;

/**
 * RewritableBinaryFile allows a caller to read the content of a file, then at any position begin re-writing its content from that position
 * forward
 */
public class RewritableTextFile implements AutoCloseable {
	private final File theFile;
	private final Charset theCharset;
	private final int theAvgCharLength;
	private final int theMaxCharLength;
	private final CircularCharBuffer theBuffer;
	private Reader theInput;
	private Writer theOutput;

	private long theInputPosition;
	private long theOutputPosition;
	private boolean isInputExhausted;

	/**
	 * @param file The file to rewrite
	 * @param charSet The character set to use for the file's text
	 * @param initialCapacity The initial buffer capacity, or &lt;0 to use the default size
	 */
	public RewritableTextFile(File file, Charset charSet, int initialCapacity) {
		theFile = file;
		theCharset = charSet;
		CharsetEncoder encoder = charSet.newEncoder();
		theAvgCharLength = (int) Math.floor(encoder.averageBytesPerChar());
		theMaxCharLength = (int) Math.ceil(encoder.maxBytesPerChar());
		theBuffer = new CircularCharBuffer(initialCapacity);
	}

	/** @return The number of <b>bytes (not chars)</b> that have been read from the {@link #getIn() input} Reader */
	public long getInputPosition() {
		return theInputPosition;
	}

	/** @return The position of the {@link #getOut(long) output} Writer, <b>in bytes (not chars)</b> */
	public long getOutputPosition() {
		return theOutputPosition;
	}

	/**
	 * @return A reader to read the file's content
	 * @throws IOException If an error occurs reading the file
	 */
	public Reader getIn() throws IOException {
		if (theInput == null)
			theInput = new InputStreamReader(new FileInputStream(theFile), theCharset);
		return new Reader() {
			@Override
			public int read(char[] c, int off, int len) throws IOException {
				if (theInput == null)
					throw new IOException("The stream has been closed");
				if (len > c.length - off)
					len = c.length - off;
				if (len == 0)
					return 0;
				int read = theInput.read(c, off, len);
				if (read >= 0) {
					if (theOutput != null)
						theBuffer.append(c, off, read);
					theInputPosition += theCharset.encode(CharBuffer.wrap(c, off, read)).limit();
				} else
					isInputExhausted = true;
				return read;
			}

			@Override
			public void close() throws IOException {
				if (theInput == null)
					return;
				if (isInputExhausted) {
					theInput.close();
					theInput = null;
				}
			}
		};
	}

	/**
	 * @param position The starting byte position to rewrite the file from
	 * @return A writer to rewrite the file's content from the given byte position
	 * @throws IOException If an error occurs writing to the file
	 */
	public Writer getOut(long position) throws IOException {
		if (position > theInputPosition)
			throw new IllegalArgumentException("Cannot start writing at a position beyond where the file has been read to");
		if (theOutput == null) {
			RandomAccessFile raf = new RandomAccessFile(theFile, "rw");
			raf.seek(position);
			theOutput = new OutputStreamWriter(new RandomAccessFileOutputStream(raf), theCharset);
			theOutputPosition = raf.getFilePointer();
		}
		return new Writer() {
			@Override
			public void write(char[] c, int off, int len) throws IOException {
				if (theOutput == null)
					throw new IOException("The stream has been closed");
				if (len > c.length - off)
					len = c.length - off;
				if (len == 0)
					return;
				flushBuffer();
				long diff = theInputPosition - theOutputPosition;
				if (diff >= theMaxCharLength) {
					int write = Math.min(getWriteChars(new CharArraySeq(c, off, len), diff), len);
					theOutput.write(c, off, write);
					theOutputPosition += theCharset.encode(CharBuffer.wrap(c, off, write)).limit();
					if (write == len)
						return;
					len -= write;
					off += write;
				}
				theBuffer.append(c, off, off + len);
			}

			@Override
			public void flush() throws IOException {
				if (theOutput == null)
					throw new IOException("The stream has been closed");
				flushBuffer();
				theOutput.flush();
			}

			@Override
			public void close() throws IOException {
				// Ambiguous--ignore
			}
		};
	}

	/**
	 * @return A writer to rewrite the file's content from the current input position
	 * @throws IOException If an error occurs writing to the file
	 */
	public Writer getOut() throws IOException {
		return getOut(theInputPosition);
	}

	/**
	 * @return The size of the file
	 * @throws IOException If an error occurs retrieving the file size
	 */
	public long size() throws IOException {
		return theFile.length();
	}

	/**
	 * Writes everything written to {@link #getOut()} to the file, discarding any content that has not been read from {@link #getIn()}. This
	 * file is then closed. If {@link #getOut()} has not been called, this just closes the input stream with no change to the file.
	 * 
	 * @return This file
	 * @throws IOException If an error occurs writing the file
	 */
	public RewritableTextFile truncate() throws IOException {
		if (theOutput != null) {
			theBuffer.writeContent(theOutput, 0, theBuffer.length()).clear(false);
			theOutput.close();
		}
		if (theInput != null) {
			theInput.close();
			theInput = null;
		}
		return this;
	}

	/**
	 * Writes everything written to {@link #getOut()} to the file, followed by any content that has not been read from {@link #getIn()}.
	 * This file is then closed. If {@link #getOut()} has not been called, this just closes the input stream with no change to the file.
	 * 
	 * @return This file
	 * @throws IOException If an error occurs reading or writing the file
	 */
	public RewritableTextFile transfer() throws IOException {
		if (theOutput != null) {
			while (!isInputExhausted) {
				flushBuffer();
				int read = theBuffer.appendFrom(theInput, theBuffer.getCapacity() - theBuffer.length());
				if (read >= 0)
					theInputPosition += theCharset
						.encode(CharBuffer.wrap(theBuffer.subSequence(theBuffer.length() - read, theBuffer.length()))).limit();
				else
					isInputExhausted = true;
			}
		}
		return truncate();
	}

	/** Same as {@link #truncate()} */
	@Override
	public void close() throws IOException {
		truncate();
	}

	void flushBuffer() throws IOException {
		long diff = theInputPosition - theOutputPosition;
		if (diff >= theMaxCharLength && theBuffer.length() > 0) {
			int write = getWriteChars(theBuffer, diff);
			theBuffer.writeContent(theOutput, 0, write);
			theOutputPosition += theCharset.encode(CharBuffer.wrap(theBuffer.subSequence(0, write))).limit();
			theBuffer.delete(0, write);
		}
	}

	int getWriteChars(CharSequence seq, long byteLimit) {
		if (seq.length() * theMaxCharLength <= byteLimit)
			return seq.length();
		int min = (int) (byteLimit * 1.0 / theMaxCharLength);
		int max = Math.min(seq.length(), (int) Math.ceil(byteLimit * 1.0 / theAvgCharLength));
		ByteBuffer bytes = ByteBuffer.allocate(max * theMaxCharLength);
		CharsetEncoder encoder = theCharset.newEncoder();
		while (min < max) {
			int i = (min + max + 1) / 2;
			bytes.rewind();
			encoder.encode(CharBuffer.wrap(seq.subSequence(0, i)), bytes, true);
			if (bytes.position() > byteLimit)
				max = i - 1;
			else if (bytes.position() > byteLimit - theAvgCharLength)
				return i;
			else if (bytes.position() < byteLimit - theMaxCharLength)
				min = i + 1;
			else
				min = i;
		}
		return max;
	}

	static class CharArraySeq extends AbstractCharSequence {
		private final char[] ch;
		private final int offset;
		private final int length;

		CharArraySeq(char[] ch, int offset, int length) {
			this.ch = ch;
			this.offset = offset;
			this.length = length;
		}

		@Override
		public int length() {
			return length;
		}

		@Override
		public char charAt(int index) {
			return ch[offset + index];
		}
	}
}

package org.qommons.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/**
 * RewritableBinaryFile allows a caller to read the content of a file, then at any position begin re-writing its content from that position
 * forward
 */
public class RewritableBinaryFile implements AutoCloseable {
	private final File theFile;
	private final CircularByteBuffer theBuffer;
	private InputStream theInput;
	private OutputStream theOutput;

	private long theInputPosition;
	private long theOutputPosition;
	private boolean isInputExhausted;

	/**
	 * @param file The file to rewrite
	 * @param initialCapacity The initial buffer capacity, or &lt;0 to use the default size
	 */
	public RewritableBinaryFile(File file, int initialCapacity) {
		theFile = file;
		theBuffer = new CircularByteBuffer(initialCapacity);
	}

	/** @return The number of bytes that have been read from the {@link #getIn() input} stream */
	public long getInputPosition() {
		return theInputPosition;
	}

	/** @return The position of the {@link #getOut(long) output} stream, in bytes */
	public long getOutputPosition() {
		return theOutputPosition;
	}

	/**
	 * @return An input stream to read the file's content
	 * @throws IOException If an error occurs reading the file
	 */
	public InputStream getIn() throws IOException {
		if (theInput == null)
			theInput = new FileInputStream(theFile);
		return new InputStream() {
			@Override
			public int read() throws IOException {
				if (theInput == null)
					throw new IOException("The stream has been closed");
				int read = theInput.read();
				if (read >= 0) {
					if (theOutput != null)
						theBuffer.append((byte) read);
					theInputPosition++;
				} else
					isInputExhausted = true;
				return read;
			}

			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				if (theInput == null)
					throw new IOException("The stream has been closed");
				if (len > b.length - off)
					len = b.length - off;
				if (len == 0)
					return 0;
				int read = theInput.read(b, off, len);
				if (read >= 0) {
					if (theOutput != null)
						theBuffer.append(b, off, read);
					theInputPosition += read;
				} else
					isInputExhausted = true;
				return read;
			}

			@Override
			public long skip(long n) throws IOException {
				if (theInput == null)
					throw new IOException("The stream has been closed");
				if (theOutput == null) {
					long skipped = theInput.skip(n);
					theInputPosition += skipped;
					return skipped;
				}
				int toSkip = theBuffer.getCapacity() - theBuffer.length();
				if (toSkip < 128)
					toSkip = theBuffer.getCapacity();
				if (toSkip > n)
					toSkip = (int) n;
				int skipped = theBuffer.appendFrom(theInput, toSkip);
				if (skipped >= 0) {
					theInputPosition += skipped;
					return skipped;
				} else {
					isInputExhausted = true;
					return 0;
				}
			}

			@Override
			public int available() throws IOException {
				if (theInput == null)
					throw new IOException("The stream has been closed");
				return theInput.available();
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
	 * @param position The starting position to rewrite the file from
	 * @return An output stream to rewrite the file's content from the given position
	 * @throws IOException If an error occurs writing to the file
	 */
	public OutputStream getOut(long position) throws IOException {
		if (position > theInputPosition)
			throw new IllegalArgumentException("Cannot start writing at a position beyond where the file has been read to");
		if (theOutput == null) {
			RandomAccessFile raf = new RandomAccessFile(theFile, "rw");
			raf.seek(position);
			theOutput = new RandomAccessFileOutputStream(raf);
			theOutputPosition = raf.getFilePointer();
		}
		return new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				if (theOutput == null)
					throw new IOException("The stream has been closed");
				flushBuffer();
				if (theOutputPosition < theInputPosition) {
					theOutput.write(b);
					theOutputPosition++;
				} else
					theBuffer.append((byte) b);
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				if (theOutput == null)
					throw new IOException("The stream has been closed");
				if (len > b.length - off)
					len = b.length - off;
				if (len == 0)
					return;
				flushBuffer();
				long diff = theInputPosition - theOutputPosition;
				if (diff > 0) {
					int write = (int) Math.min(diff, len);
					theOutput.write(b, off, write);
					theOutputPosition += write;
					if (write == len)
						return;
					len -= write;
					off += write;
				}
				theBuffer.append(b, off, off + len);
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
	 * @return An output stream to rewrite the file's content from the current input position
	 * @throws IOException If an error occurs writing to the file
	 */
	public OutputStream getOut() throws IOException {
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
	public RewritableBinaryFile truncate() throws IOException {
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
	public RewritableBinaryFile transfer() throws IOException {
		if (theOutput != null) {
			while (!isInputExhausted) {
				flushBuffer();
				int read = theBuffer.appendFrom(theInput, theBuffer.getCapacity() - theBuffer.length());
				if (read >= 0)
					theInputPosition += read;
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
		if (diff > 0) {
			int write = (int) Math.max(diff, theBuffer.length());
			theBuffer.writeContent(theOutput, 0, write).delete(0, write);
			theOutputPosition += write;
		}
	}
}

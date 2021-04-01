package org.qommons.io;

import java.io.IOException;
import java.nio.CharBuffer;

/**
 * Serves as a pipe between two parts of a process, providing a {@link #read() Reader} that reads data supplied by a {@link #write()
 * Writer}. The Reader blocks appropriately when there is no content.
 */
public class BufferedReaderWriter {
	private final Reader theReader;
	private final Writer theWriter;
	private final CircularCharBuffer theBuffer;
	private volatile boolean isClosed;

	/** Creates the reader/writer */
	public BufferedReaderWriter() {
		theBuffer = new CircularCharBuffer(-1);
		theReader = new Reader();
		theWriter = new Writer();
	}

	/** @return A Reader that reads content written by the {@link #write() Writer} */
	public java.io.Reader read() {
		return theReader;
	}

	/** @return A Writer that writes content for consumption by the {@link #read() Reader} */
	public java.io.Writer write() {
		return theWriter;
	}

	/** @return The buffer used to store written data for consumption by the reader */
	public CircularCharBuffer getBuffer() {
		return theBuffer;
	}

	class Reader extends java.io.Reader {
		boolean waitForContent() {
			if (isClosed)
				return false;
			int len = theBuffer.length();
			while (len == 0) {
				try {
					BufferedReaderWriter.this.wait();
				} catch (InterruptedException e) {
				}
				if (isClosed)
					return false;
				len = theBuffer.length();
			}
			return true;
		}

		@Override
		public int read(CharBuffer target) {
			synchronized (BufferedReaderWriter.this) {
				if (!waitForContent())
					return -1;
				int prePos = target.position();
				target.append(theBuffer);
				int read = target.position() - prePos;
				theBuffer.delete(0, read);
				return read;
			}
		}

		@Override
		public int read() {
			synchronized (BufferedReaderWriter.this) {
				if (!waitForContent())
					return -1;
				int read = theBuffer.charAt(0);
				theBuffer.delete(0, 1);
				return read;
			}
		}

		@Override
		public int read(char[] cbuf, int off, int len) {
			synchronized (BufferedReaderWriter.this) {
				if (!waitForContent())
					return -1;
				int read = Math.min(len, theBuffer.length());
				theBuffer.copyTo(0, cbuf, off, read);
				theBuffer.delete(0, read);
				return read;
			}
		}

		@Override
		public long skip(long n) {
			synchronized (BufferedReaderWriter.this) {
				if (!waitForContent())
					return -1;
				int skipped = (int) Math.min(theBuffer.length(), n);
				theBuffer.delete(0, skipped);
				return skipped;
			}
		}

		@Override
		public boolean ready() {
			return !isClosed && theBuffer.length() > 0;
		}

		@Override
		public void close() {
			isClosed = true;
			synchronized (BufferedReaderWriter.this) {
				BufferedReaderWriter.this.notify();
			}
		}
	}

	class Writer extends java.io.Writer {
		@Override
		public void write(String str, int off, int len) throws IOException {
			synchronized (BufferedReaderWriter.this) {
				if (isClosed)
					throw new IOException("The connection has been closed");
				theBuffer.append(str, off, off + len);
				BufferedReaderWriter.this.notify();
			}
		}

		@Override
		public void write(char[] cbuf, int off, int len) throws IOException {
			synchronized (BufferedReaderWriter.this) {
				if (isClosed)
					throw new IOException("The connection has been closed");
				theBuffer.append(cbuf, off, off + len);
				BufferedReaderWriter.this.notify();
			}
		}

		@Override
		public Writer append(CharSequence csq, int start, int end) throws IOException {
			synchronized (BufferedReaderWriter.this) {
				if (isClosed)
					throw new IOException("The connection has been closed");
				theBuffer.append(csq, start, end);
				BufferedReaderWriter.this.notify();
			}
			return this;
		}

		@Override
		public Writer append(char c) throws IOException {
			synchronized (BufferedReaderWriter.this) {
				if (isClosed)
					throw new IOException("The connection has been closed");
				theBuffer.append(c);
				BufferedReaderWriter.this.notify();
			}
			return this;
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
			isClosed = true;
			synchronized (BufferedReaderWriter.this) {
				BufferedReaderWriter.this.notify();
			}
		}
	}
}

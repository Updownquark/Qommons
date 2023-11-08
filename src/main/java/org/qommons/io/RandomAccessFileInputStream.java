package org.qommons.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import org.qommons.ex.CheckedExceptionWrapper;

/** Simple InputStream that reads from a {@link RandomAccessFile} */
public class RandomAccessFileInputStream extends InputStream {
	private final RandomAccessFile theFile;
	private long theMark;

	/** @param file The file to read from */
	public RandomAccessFileInputStream(RandomAccessFile file) {
		theFile = file;
		theMark = -1;
	}

	/** @return The file this stream reads from */
	public RandomAccessFile getFile() {
		return theFile;
	}

	@Override
	public int read() throws IOException {
		return theFile.read();
	}

	@Override
	public int read(byte[] b) throws IOException {
		return theFile.read(b);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return theFile.read(b, off, len);
	}

	@Override
	public long skip(long n) throws IOException {
		long newPos = theFile.getFilePointer() + n;
		if (newPos > theFile.length()) {
			newPos = theFile.length();
			n = newPos - theFile.getFilePointer();
		}
		theFile.seek(newPos);
		return n;
	}

	@Override
	public int available() throws IOException {
		return 0;
	}

	@Override
	public void close() throws IOException {
		theFile.close();
	}

	@Override
	public boolean markSupported() {
		return true;
	}

	@Override
	public synchronized void mark(int readlimit) {
		try {
			theMark = theFile.getFilePointer();
		} catch (IOException e) {
			throw new CheckedExceptionWrapper(e);
		}
	}

	@Override
	public synchronized void reset() throws IOException {
		if (theMark < 0)
			throw new IOException("No mark set");
		theFile.seek(theMark);
	}
}

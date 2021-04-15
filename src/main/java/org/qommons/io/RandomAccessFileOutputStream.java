package org.qommons.io;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/** A simple OutputStream that writes to a {@link RandomAccessFile} */
public class RandomAccessFileOutputStream extends OutputStream {
	private final RandomAccessFile theFile;

	/** @param file The file to write to */
	public RandomAccessFileOutputStream(RandomAccessFile file) {
		theFile = file;
	}

	/** @return The file that this stream writes to */
	public RandomAccessFile getFile() {
		return theFile;
	}

	@Override
	public void write(int b) throws IOException {
		theFile.write(b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		theFile.write(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		theFile.write(b, off, len);
	}

	@Override
	public void close() throws IOException {
		theFile.setLength(theFile.getFilePointer());
		theFile.close();
	}
}

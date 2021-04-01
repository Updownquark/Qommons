package org.qommons.io;

import java.io.IOException;
import java.io.InputStream;

/** An {@link InputStream} that can be limited and keeps track of its position offset */
public class CountingInputStream extends InputStream {
	private final InputStream theWrapped;
	private final long theLimit;
	private long thePosition;

	private int theProgress;

	/** @param wrapped The stream to read data from */
	public CountingInputStream(InputStream wrapped) {
		theWrapped = wrapped;
		theLimit = -1;
	}

	/**
	 * @param wrapped The stream to read data from
	 * @param limit The limit to read from this stream (e.g. file size for progress, or compressed size of an entry in an archive file)
	 */
	public CountingInputStream(InputStream wrapped, long limit) {
		theWrapped = wrapped;
		theLimit = limit;
	}

	/** @return The number of bytes that have been read from this stream */
	public long getPosition() {
		return thePosition;
	}

	/** Prints a representation of this stream's progress to System.out (console progress user feedback) */
	public void printProgress() {
		if (theLimit <= 0)
			return;
		int newProgress = (int) Math.floor(thePosition * 100.0 / theLimit);
		if (newProgress > 100)
			newProgress = 100;
		if (newProgress > theProgress) {
			for (int p = theProgress + 1; p <= newProgress; p++) {
				if (p % 10 == 0)
					System.out.print(p + "%");
				else
					System.out.print('.');
			}
			theProgress = newProgress;
			System.out.flush();
		}
	}

	@Override
	public int read() throws IOException {
		if (thePosition == theLimit)
			return -1;
		int read = theWrapped.read();
		if (read >= 0) {
			thePosition++;
		}
		return read;
	}

	@Override
	public int read(byte[] b) throws IOException {
		if (theLimit >= 0) {
			if (thePosition == theLimit)
				return -1;
			else if (thePosition + b.length > theLimit)
				return read(b, 0, (int) (theLimit - thePosition));
		}
		int read = theWrapped.read(b);
		if (read > 0) {
			thePosition += read;
		}
		return read;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (theLimit >= 0) {
			if (thePosition == theLimit)
				return -1;
			else if (thePosition + len > theLimit)
				len = (int) (theLimit - thePosition);
		}
		int read = theWrapped.read(b, off, len);
		if (read > 0) {
			thePosition += read;
		}
		return read;
	}

	@Override
	public long skip(long n) throws IOException {
		if (theLimit >= 0) {
			if (thePosition == theLimit)
				return -1;
			else if (thePosition + n > theLimit)
				n = (theLimit - thePosition);
		}
		long skipped = theWrapped.skip(n);
		if (skipped > 0) {
			thePosition += skipped;
		}
		return skipped;
	}

	@Override
	public int available() throws IOException {
		if (thePosition == theLimit)
			return 0;
		int av = theWrapped.available();
		if (theLimit >= 0 && thePosition + av > theLimit)
			av = (int) (theLimit - thePosition);
		return av;
	}

	@Override
	public void close() throws IOException {
		super.close();
		theWrapped.close();
	}
}
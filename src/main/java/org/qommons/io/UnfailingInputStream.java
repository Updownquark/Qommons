package org.qommons.io;

import java.io.IOException;
import java.io.InputStream;

public abstract class UnfailingInputStream extends InputStream {
	@Override
	public abstract int read();

	@Override
	public int read(byte[] b) {
		try {
			return super.read(b);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public int read(byte[] b, int off, int len) {
		try {
			return super.read(b, off, len);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public long skip(long n) {
		try {
			return super.skip(n);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public int available() {
		try {
			return super.available();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void close() {
		try {
			super.close();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public synchronized void reset() {
		try {
			super.reset();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}

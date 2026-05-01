package org.qommons.io;

import java.io.IOException;
import java.io.OutputStream;

public abstract class UnfailingOutputStream extends OutputStream {
	@Override
	public abstract void write(int b);

	@Override
	public void write(byte[] b) {
		try {
			super.write(b);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void write(byte[] b, int off, int len) {
		try {
			super.write(b, off, len);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void flush() {
		try {
			super.flush();
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
}

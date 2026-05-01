package org.qommons.io;

import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;

public abstract class UnfailingReader extends Reader {
	@Override
	public int read(CharBuffer target) {
		try {
			return super.read(target);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public int read() {
		try {
			return super.read();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public int read(char[] cbuf) {
		try {
			return super.read(cbuf);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public abstract int read(char[] cbuf, int off, int len);

	@Override
	public long skip(long n) {
		try {
			return super.skip(n);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public boolean ready() {
		try {
			return super.ready();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void mark(int readAheadLimit) {
		try {
			super.mark(readAheadLimit);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void reset() {
		try {
			super.reset();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public abstract void close();
}

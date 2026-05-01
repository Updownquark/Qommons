package org.qommons.io;

import java.io.IOException;
import java.io.Writer;

public abstract class UnfailingWriter extends Writer {
	@Override
	public void write(int c) {
		try {
			super.write(c);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void write(char[] cbuf) {
		try {
			super.write(cbuf);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public abstract void write(char[] cbuf, int off, int len);

	@Override
	public void write(String str) {
		try {
			super.write(str);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void write(String str, int off, int len) {
		try {
			super.write(str, off, len);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public Writer append(CharSequence csq) {
		try {
			return super.append(csq);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public Writer append(CharSequence csq, int start, int end) {
		try {
			return super.append(csq, start, end);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public Writer append(char c) {
		try {
			return super.append(c);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public abstract void flush();

	@Override
	public abstract void close();
}

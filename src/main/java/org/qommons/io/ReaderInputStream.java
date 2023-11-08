package org.qommons.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

/** The opposite of a {@link InputStreamReader}--formats text from a {@link Reader} into bytes in an {@link InputStream} */
public class ReaderInputStream extends InputStream {
	private final Reader theReader;

	private final CharsetEncoder theEncoder;

	private final CharBuffer theCharBuffer;
	private final ByteBuffer theBuffer;

	/** @param reader The reader to read character data from */
	public ReaderInputStream(Reader reader) {
		this(reader, Charset.defaultCharset().newEncoder());
	}

	/**
	 * @param reader The reader to read character data from
	 * @param encoder The char set encoder to use to convert from character data into binary data
	 */
	public ReaderInputStream(Reader reader, CharsetEncoder encoder) {
		theReader = reader;
		theEncoder = encoder;
		theCharBuffer = CharBuffer.allocate(1024);
		theCharBuffer.clear();
		theBuffer = ByteBuffer.allocate(theCharBuffer.capacity() * (int) Math.ceil(theEncoder.maxBytesPerChar()));
		theBuffer.clear();
		theBuffer.limit(0);
	}

	private boolean readNext() throws IOException {
		theCharBuffer.clear();
		int read = theReader.read(theCharBuffer);
		if (read < 0)
			return false;
		theCharBuffer.rewind();
		theBuffer.clear();
		@SuppressWarnings("unused")
		CoderResult result = theEncoder.encode(theCharBuffer, theBuffer, false);
		theBuffer.limit(theBuffer.position());
		theBuffer.rewind();
		return true;
	}

	@Override
	public int read() throws IOException {
		if (!theBuffer.hasRemaining() && !readNext())
			return -1;
		return theBuffer.get();
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int pos = off;
		while (pos < len - off) {
			int remain = theBuffer.remaining();
			if (remain > 0) {
				int len2 = Math.min(len - (pos - off), remain);
				theBuffer.get(b, pos, len2);
				pos += len2;
			}
			if (off + pos == len || !readNext())
				break;
		}
		return pos == off ? -1 : pos - off;
	}

	@Override
	public long skip(long n) throws IOException {
		if (n <= 0)
			return 0;
		long skipped = 0;
		while (skipped < n) {
			int buffered = theBuffer.remaining();
			if (buffered > 0) {
				if (skipped + buffered > n) {
					theBuffer.position(theBuffer.position() + (int) (n - skipped));
					skipped = n;
				} else {
					skipped += buffered;
					theBuffer.clear();
				}
			}
			if (skipped == n || !readNext())
				break;
		}
		return skipped;
	}

	@Override
	public int available() throws IOException {
		return theBuffer.remaining() + (theReader.ready() ? 1 : 0);
	}

	@Override
	public void close() throws IOException {
		theReader.close();
	}

	@Override
	public int hashCode() {
		return theReader.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof ReaderInputStream && theReader.equals(((ReaderInputStream) obj).theReader);
	}

	@Override
	public String toString() {
		return "streamOf(" + theReader + ")";
	}
}

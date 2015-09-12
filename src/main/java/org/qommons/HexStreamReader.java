package org.qommons;

import java.io.IOException;
import java.io.InputStream;

/** An input stream that reads hexadecimally-formatted binary data from a wrapped reader */
public class HexStreamReader extends InputStream
{
	private static final String HEX = "0123456789ABCDEF";

	private java.io.Reader theInput;

	private boolean closeWrapped;

	/** Creates a HexStreamReader whose wrapped reader may be set later using {@link #setWrapped(java.io.Reader)} */
	public HexStreamReader()
	{
		closeWrapped = true;
	}

	/** @param in The reader to read hexadecimal data from */
	public HexStreamReader(java.io.Reader in)
	{
		this();
		theInput = in;
	}

	/** @param in The reader to read hexadecimal data from */
	public void setWrapped(java.io.Reader in)
	{
		theInput = in;
	}

	@Override
	public int read() throws IOException
	{
		int ret = 0;
		int read = theInput.read();
		if(read < 0)
			return -1;
		int hex = HEX.indexOf((char) read);
		if(hex < 0)
			return -1;
		ret = hex << 4;
		read = theInput.read();
		if(read < 0)
			return ret;
		hex = HEX.indexOf((char) read);
		if(hex < 0)
			return ret;
		ret |= hex;
		return ret;
	}

	@Override
	public void close() throws IOException
	{
		super.close();
		if(closeWrapped && theInput != null)
			theInput.close();
	}
}

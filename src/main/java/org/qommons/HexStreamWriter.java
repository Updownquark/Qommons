package org.qommons;

import java.io.IOException;
import java.io.Writer;

/** An output stream that writes the hexadecimal-formatted binary data received to a wrapped writer */
public class HexStreamWriter extends java.io.OutputStream
{
	private static final String HEX = "0123456789ABCDEF";

	private Writer theOutput;

	private boolean closeWrapped;

	/** Creates a HexStreamWriter whose wrapped output writer may be set later using {@link #setWrapped(Writer)} */
	public HexStreamWriter()
	{
		closeWrapped = true;
	}

	/** @param out The writer to write hexadecimal data to */
	public HexStreamWriter(Writer out)
	{
		this();
		theOutput = out;
	}

	/** @param out The output stream to write hexadecimal dat to */
	public void setWrapped(Writer out)
	{
		theOutput = out;
	}

	/** @param close Whether this writer should close its wrapped writer when it is closed. Default is true. */
	public void setCloseWrapped(boolean close)
	{
		closeWrapped = close;
	}

	@Override
	public void write(int b) throws IOException
	{
		b = (b + 0x100) % 0x100;
		theOutput.write(HEX.charAt(b / 16));
		theOutput.write(HEX.charAt(b % 16));
	}

	@Override
	public void close() throws IOException
	{
		super.close();
		if(closeWrapped)
			theOutput.close();
	}
}

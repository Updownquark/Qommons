/*
 * ExportStream.java Created Sep 9, 2010 by Andrew Butler, PSL
 */
package org.qommons;

import java.io.IOException;

/**
 * Wraps a stream to export data in a non-readable and compressed form. The stream is first zipped,
 * then obfsucated to prevent human interaction.
 */
public class ExportStream extends java.io.OutputStream
{
	private java.util.zip.ZipOutputStream theOutput;

	/**
	 * Wraps a stream to export data
	 * 
	 * @param wrap The stream to write exported data to
	 * @throws IOException If an error occurs wrapping the stream
	 */
	public ExportStream(java.io.OutputStream wrap) throws IOException
	{
		theOutput = new java.util.zip.ZipOutputStream(ObfuscatingStream.obfuscate(wrap));
		java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry("export.json");
		theOutput.putNextEntry(zipEntry);
		theOutput.setLevel(9);
	}

	@Override
	public void write(int b) throws IOException
	{
		theOutput.write(b);
	}

	@Override
	public void write(byte [] b) throws IOException
	{
		theOutput.write(b);
	}

	@Override
	public void write(byte [] b, int off, int len) throws IOException
	{
		theOutput.write(b, off, len);
	}

	@Override
	public void flush() throws IOException
	{
		theOutput.flush();
	}

	@Override
	public void close() throws IOException
	{
		theOutput.finish();
		theOutput.close();
	}
}

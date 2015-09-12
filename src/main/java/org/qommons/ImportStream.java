/*
 * ImportStream.java Created Sep 9, 2010 by Andrew Butler, PSL
 */
package org.qommons;

import java.io.IOException;

/** Wraps a stream that has been exported to import the data in readable form */
public class ImportStream extends java.io.InputStream
{
	private java.io.InputStream theInput;

	/**
	 * Wraps a stream with an import stream
	 * 
	 * @param wrap The exported stream to wrap
	 * @throws IOException If an error occurs wrapping the stream
	 */
	public ImportStream(java.io.InputStream wrap) throws IOException
	{
		java.util.zip.ZipInputStream zis;
		zis = new java.util.zip.ZipInputStream(ObfuscatingStream.unobfuscate(wrap));
		zis.getNextEntry();
		theInput = zis;
	}

	@Override
	public int read() throws IOException
	{
		return theInput.read();
	}

	@Override
	public int read(byte [] b) throws IOException
	{
		return theInput.read(b);
	}

	@Override
	public int read(byte [] b, int off, int len) throws IOException
	{
		return theInput.read(b, off, len);
	}

	@Override
	public long skip(long n) throws IOException
	{
		return theInput.skip(n);
	}

	@Override
	public int available() throws IOException
	{
		return theInput.available();
	}

	@Override
	public boolean markSupported()
	{
		return theInput.markSupported();
	}

	@Override
	public synchronized void mark(int readlimit)
	{
		theInput.mark(readlimit);
	}

	@Override
	public synchronized void reset() throws IOException
	{
		theInput.reset();
	}

	@Override
	public void close() throws IOException
	{
		theInput.close();
	}

	/**
	 * Tests the compatibility of the import/export streams
	 * 
	 * @param args Command-line args, ignored
	 * @throws IOException If an IO exception occurs writing or reading the data to internal buffers
	 */
	public static void main(String [] args) throws IOException
	{
		String test = null;
		java.io.Reader reader;
		if(args.length == 0)
		{
			test = "This is a test.  This is only a test.  If this had been an actual string, it"
				+ " would have been proceeded by content that matters to the application.  BEEP!!!";
			java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
			java.io.Writer writer = new java.io.OutputStreamWriter(new ExportStream(bytes));
			writer.write(test);
			writer.close();
			reader = new java.io.InputStreamReader(new ImportStream(
				new java.io.ByteArrayInputStream(bytes.toByteArray())));
		}
		else
			reader = new java.io.InputStreamReader(new ImportStream(
				new java.io.ByteArrayInputStream(args[0].getBytes())));
		java.io.StringWriter str = new java.io.StringWriter();
		int read = reader.read();
		while(read >= 0)
		{
			str.write(read);
			read = reader.read();
		}
		if(test != null)
		{
			if(!test.equals(str.toString()))
				System.err.println("Import/Export does not work!  " + str.toString());
			else
				System.out.println("Import/Export successful.");
		}
		else
			System.out.println(str.toString());
	}
}

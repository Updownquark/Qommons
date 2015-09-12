/*
 * LoggingWriter.java Created Sep 7, 2010 by Andrew Butler, PSL
 */
package org.qommons;

import java.io.IOException;
import java.nio.CharBuffer;

/** Wraps a stream, printing to standard out everything that is read from it. */
public class LoggingReader extends java.io.Reader
{
	private java.io.Reader theBase;

	private java.io.Writer theLog;

	/**
	 * @param base The reader to wrap
	 * @param file The name of the file to log to, or null to log to System.out
	 * @throws IOException If an error occurs setting up the log file
	 */
	public LoggingReader(java.io.Reader base, String file) throws IOException
	{
		theBase = base;
		if(file != null)
			theLog = new java.io.BufferedWriter(new java.io.FileWriter(file));
	}

	/**
	 * @param base The reader to wrap
	 * @param file The file to log to, or null to log to System.out
	 * @throws IOException If an error occurs setting up the log file
	 */
	public LoggingReader(java.io.Reader base, java.io.File file) throws IOException
	{
		theBase = base;
		if(file != null)
			theLog = new java.io.BufferedWriter(new java.io.FileWriter(file));
	}

	/** @return The reader wrapped by this logging reader */
	public java.io.Reader getBase()
	{
		return theBase;
	}

	@Override
	public int read(CharBuffer target) throws IOException
	{
		int read = theBase.read(target);
		if(theLog != null)
			theLog.write(target.array(), 0, read);
		else
			System.out.print(target.toString().substring(0, read));
		return read;
	}

	@Override
	public int read() throws IOException
	{
		int read = theBase.read();
		if(read >= 0)
		{
			if(theLog != null)
				theLog.write(read);
			else
				System.out.print((char) read);
		}
		return read;
	}

	@Override
	public int read(char [] cbuf) throws IOException
	{
		int read = theBase.read(cbuf);
		if(theLog != null)
			theLog.write(cbuf, 0, read);
		else
			System.out.print(new String(cbuf, 0, read));
		return read;
	}

	@Override
	public int read(char [] cbuf, int off, int len) throws IOException
	{
		int read = theBase.read(cbuf, off, len);
		if(theLog != null)
			theLog.write(cbuf, off, read);
		else
			System.out.print(new String(cbuf, off, read));
		return read;
	}

	@Override
	public long skip(long n) throws IOException
	{
		return super.skip(n);
	}

	@Override
	public boolean ready() throws IOException
	{
		return theBase.ready();
	}

	@Override
	public boolean markSupported()
	{
		return false;
	}

	@Override
	public void close() throws java.io.IOException
	{
		if(theLog != null)
			theLog.close();
		else
			System.out.flush();
		theBase.close();
	}
}

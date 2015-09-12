/*
 * LoggingWriter.java Created Sep 7, 2010 by Andrew Butler, PSL
 */
package org.qommons;

import java.io.Writer;

/** Wraps a stream, printing to standard out everything that is written to it. */
public class LoggingWriter extends Writer
{
	private java.io.Writer theBase;

	private java.io.Writer theLog;

	/**
	 * @param base The writer to wrap
	 * @param logFile The name of the file to write to
	 * @throws java.io.IOException If the file cannot be written to
	 */
	public LoggingWriter(java.io.Writer base, String logFile) throws java.io.IOException
	{
		theBase = base;
		if(logFile != null)
			theLog = new java.io.BufferedWriter(new java.io.FileWriter(logFile));
	}

	/**
	 * @return The writer wrapped by this logging writer
	 */
	public java.io.Writer getBase()
	{
		return theBase;
	}

	@Override
	public void write(char [] cbuf, int off, int len) throws java.io.IOException
	{
		theBase.write(cbuf, off, len);
		if(theLog != null)
			theLog.write(cbuf, off, len);
		else
			System.out.print(new String(cbuf, off, len));
	}

	@Override
	public void flush() throws java.io.IOException
	{
		theBase.flush();
		if(theLog != null)
			theLog.flush();
		else
			System.out.flush();
	}

	@Override
	public void close() throws java.io.IOException
	{
		theBase.close();
		if(theLog != null)
			theLog.close();
		else
			System.out.println();
	}
}

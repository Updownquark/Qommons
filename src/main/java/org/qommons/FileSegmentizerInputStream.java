/*
 * FileSegmentizerInputStream.java Created Sep 15, 2011 by Andrew Butler, PSL
 */
package org.qommons;

import java.io.IOException;
import java.io.InputStream;

/** Reads data from files written by {@link FileSegmentizerOutputStream} */
public class FileSegmentizerInputStream extends InputStream
{
	private final String theFile;

	private java.io.FileInputStream theCurrentStream;

	private int theFileCount;

	/**
	 * @param file The file to read
	 * @throws java.io.FileNotFoundException If the file cannot be found
	 */
	public FileSegmentizerInputStream(java.io.File file) throws java.io.FileNotFoundException
	{
		this(file.getPath());
	}

	/**
	 * @param fileName The name of the file to read
	 * @throws java.io.FileNotFoundException If the file cannot be found
	 */
	public FileSegmentizerInputStream(String fileName) throws java.io.FileNotFoundException
	{
		theFile = fileName;
		theCurrentStream = new java.io.FileInputStream(fileName);
		theFileCount = 1;
	}

	private void advanceFile() throws IOException
	{
		theCurrentStream.close();
		theCurrentStream = null;
		theFileCount++;
		String fileName = FileSegmentizerOutputStream.getNextFileName(theFile, theFileCount);
		java.io.File file = new java.io.File(fileName);
		if(!file.exists())
			return;
		theCurrentStream = new java.io.FileInputStream(fileName);
	}

	@Override
	public int read() throws IOException
	{
		if(theCurrentStream == null)
			return -1;
		int ret = theCurrentStream.read();
		while(ret < 0)
		{
			advanceFile();
			if(theCurrentStream == null)
				break;
			ret = theCurrentStream.read();
		}
		return ret;
	}

	@Override
	public int read(byte [] b, int off, int len) throws IOException
	{
		if(theCurrentStream == null)
			return -1;
		int read = theCurrentStream.read(b, off, len);
		if(read == len)
			return read;
		int ret = read >= 0 ? read : 0;
		while(ret < len)
		{
			advanceFile();
			if(theCurrentStream == null)
				break;
			read = theCurrentStream.read(b, off + ret, len - ret);
			if(read >= 0)
				ret += read;
		}
		if(ret == 0)
			return -1;
		else
			return ret;
	}

	@Override
	public long skip(long n) throws IOException
	{
		if(theCurrentStream == null)
			return 0;
		long read = theCurrentStream.skip(n);
		if(read == n)
			return read;
		long ret = read >= 0 ? read : 0;
		while(ret < n)
		{
			advanceFile();
			if(theCurrentStream == null)
				break;
			read = theCurrentStream.skip(n - ret);
			if(read >= 0)
				ret += read;
		}
		return ret;
	}

	@Override
	public int available() throws IOException
	{
		return theCurrentStream == null ? 0 : theCurrentStream.available();
	}

	@Override
	public void close() throws IOException
	{
		if(theCurrentStream != null)
		{
			theCurrentStream.close();
			theCurrentStream = null;
		}
		super.close();
	}

}

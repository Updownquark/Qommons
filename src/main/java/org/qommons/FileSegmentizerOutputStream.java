/*
 * FileSegmentizerInputStream.java Created Sep 15, 2011 by Andrew Butler, PSL
 */
package org.qommons;

import java.io.IOException;

/** Writes data to a file, breaking into multiple files to keep the individual files small */
public class FileSegmentizerOutputStream extends java.io.OutputStream
{
	private final String theFile;

	private java.io.FileOutputStream theCurrentStream;

	private int theFileCount;

	private int theByteCount;

	private int theMaxSize;

	/**
	 * @param file The file to read
	 * @throws java.io.FileNotFoundException If the file cannot be found
	 */
	public FileSegmentizerOutputStream(java.io.File file) throws java.io.FileNotFoundException
	{
		this(file.getPath());
	}

	/**
	 * @param fileName The name of the file to read
	 * @throws java.io.FileNotFoundException If the file cannot be found
	 */
	public FileSegmentizerOutputStream(String fileName) throws java.io.FileNotFoundException
	{
		theFile = fileName;
		theCurrentStream = new java.io.FileOutputStream(fileName);
		theFileCount = 1;
		theMaxSize = 1024 * 1024;
	}

	/** @return The maximum size of a file to write before moving on to another file */
	public int getMaxSize()
	{
		return theMaxSize;
	}

	/**
	 * @param size The maximum size of a file to write before moving on to another file. The default
	 *        is 1MB.
	 */
	public void setMaxSize(int size)
	{
		theMaxSize = size;
	}

	/** @return The name of the file currently being written */
	public String getCurrentFileName()
	{
		return getNextFileName(theFile, theFileCount);
	}

	@Override
	public void write(int b) throws IOException
	{
		if(theByteCount >= theMaxSize)
		{
			theCurrentStream.close();
			theFileCount++;
			theByteCount = 0;
			theCurrentStream = new java.io.FileOutputStream(getCurrentFileName());
		}
		theCurrentStream.write(b);
		theByteCount++;
	}

	@Override
	public void write(byte [] b, int off, int len) throws IOException
	{
		if(theByteCount >= theMaxSize)
		{
			theCurrentStream.close();
			theFileCount++;
			theByteCount = 0;
			theCurrentStream = new java.io.FileOutputStream(getCurrentFileName());
		}
		if(len + theByteCount > theMaxSize)
		{
			int written = theMaxSize - theByteCount;
			theCurrentStream.write(b, off, written);
			theCurrentStream.close();
			theFileCount++;
			theCurrentStream = new java.io.FileOutputStream(getCurrentFileName());
			theByteCount = len - written;
			theCurrentStream.write(b, off + written, theByteCount);
		}
		else
		{
			theCurrentStream.write(b, off, len);
			theByteCount += b.length;
		}
	}

	@Override
	public void flush() throws IOException
	{
		super.flush();
		if(theCurrentStream != null)
			theCurrentStream.flush();
	}

	@Override
	public void close() throws IOException
	{
		super.close();
		if(theCurrentStream != null)
		{
			theCurrentStream.close();
			theCurrentStream = null;
		}
	}

	/**
	 * @param fileName The name of the base file
	 * @param fileCount The index of the file (starting at 1) to read
	 * @return The name of the indexed file after the original
	 */
	public static String getNextFileName(String fileName, int fileCount)
	{
		if(fileCount == 1)
			return fileName;
		int dotIdx = fileName.lastIndexOf('.');
		if(dotIdx > 0) // Disregard initial dot
			return fileName.substring(0, dotIdx) + "_" + fileCount + fileName.substring(dotIdx);
		else
			return fileName + "_" + fileCount;
	}

	/**
	 * Deletes a file for which data has been written using this class. This method deletes the
	 * given file and all files that have been or would have been written to it by this class
	 * 
	 * @param file The file to delete
	 * @return Whether the file was successfully deleted
	 */
	public static boolean delete(java.io.File file)
	{
		String fileName = file.getPath();
		int fileCount = 1;
		boolean ret = true;
		while(file.exists())
		{
			ret &= file.delete();
			fileCount++;
			file = new java.io.File(getNextFileName(fileName, fileCount));
		}
		return ret;
	}
}

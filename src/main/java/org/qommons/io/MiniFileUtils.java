package org.qommons.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.qommons.ex.ExBiConsumer;
import org.qommons.ex.ExConsumer;

/** A small class with a few file utilities */
public class MiniFileUtils {
	/** No-call constructor, protected so this class can be extended */
	protected MiniFileUtils() {
		throw new IllegalStateException();
	}

	private static final int BUFFER_SIZE = 1024 * 1024; // 1MB

	/** Thread-safe byte buffer cache */
	protected static final ThreadLocal<byte[]> BUFFERS = ThreadLocal.withInitial(() -> new byte[BUFFER_SIZE]);
	/** Thread-safe char buffer cache */
	protected static final ThreadLocal<char[]> CHAR_BUFFERS = ThreadLocal.withInitial(() -> new char[BUFFER_SIZE]);

	/**
	 * Simple stream copy utility
	 * 
	 * @param from The input stream to copy from
	 * @param to The output stream to copy to
	 * @return The number of bytes copied
	 * @throws IOException If an error occurs reading or writing the data
	 */
	public static long copy(InputStream from, OutputStream to) throws IOException {
		return copy(from, to, null);
	}

	/**
	 * Simple stream copy utility
	 * 
	 * @param from The input stream to copy from
	 * @param to The output stream to copy to
	 * @return The number of bytes copied
	 * @param progress Callback to be notified of the copy operation's progress--the total number of bytes copied
	 * @throws IOException If an error occurs reading or writing the data
	 */
	public static long copy(InputStream from, OutputStream to, LongConsumer progress) throws IOException {
		return copy(from, to, progress, null);
	}

	/**
	 * Simple stream copy utility
	 * 
	 * @param from The input stream to copy from
	 * @param to The output stream to copy to
	 * @return The number of bytes copied
	 * @param progress Callback to be notified of the copy operation's progress--the total number of bytes copied
	 * @param canceled Returns true if the operation should immediately cease
	 * @throws IOException If an error occurs reading or writing the data
	 */
	public static long copy(InputStream from, OutputStream to, LongConsumer progress, BooleanSupplier canceled) throws IOException {
		if (canceled != null && canceled.getAsBoolean())
			return -1;
		byte[] buffer = BUFFERS.get();
		long total = 0;
		int read = from.read(buffer);
		while (read >= 0) {
			if (read > 0) {
				total += read;
				to.write(buffer, 0, read);
				if (progress != null)
					progress.accept(total);
			}
			if (canceled != null && canceled.getAsBoolean())
				return -1;
			read = from.read(buffer);
		}
		return total;
	}

	/**
	 * Simple character stream copy utility
	 * 
	 * @param from The reader to copy from
	 * @param to The writer to copy to
	 * @return The number of characters copied
	 * @param progress Callback to be notified of the copy operation's progress--the total number of characters copied
	 * @param canceled Returns true if the operation should immediately cease
	 * @throws IOException If an error occurs reading or writing the data
	 */
	public static long copy(Reader from, Writer to, LongConsumer progress, BooleanSupplier canceled) throws IOException {
		if (canceled != null && canceled.getAsBoolean())
			return -1;
		char[] buffer = CHAR_BUFFERS.get();
		long total = 0;
		int read = from.read(buffer);
		while (read >= 0) {
			if (read > 0) {
				total += read;
				to.write(buffer, 0, read);
				if (progress != null)
					progress.accept(total);
			}
			if (canceled != null && canceled.getAsBoolean())
				return -1;
			read = from.read(buffer);
		}
		return total;
	}

	/**
	 * Extracts a zip file in a secure way, avoiding the zip-slip vulnerability
	 * 
	 * @param in The zip-formatted input stream to parse
	 * @param onEntry The callback to deal with non-malicious entries
	 * @param canceled Returns true if the operation should immediately cease
	 * @throws IOException If the file could not be read or parsed as a zip, or if the callback throws an exception
	 */
	public static void extractZip(InputStream in, ExBiConsumer<ZipEntry, ZipInputStream, IOException> onEntry, BooleanSupplier canceled)
		throws IOException {
		File root = new File(System.getProperty("user.dir")); // Just need a file to ensure we don't fall prey to zip-slip
		String rootPath;
		try {
			rootPath = root.getCanonicalPath() + File.separator;
		} catch (IOException e) {
			throw new IOException("Could not get canonical path for current directory " + root.getPath(), e);
		}
		try (ZipInputStream zip = new ZipInputStream(in)) {
			for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				if (canceled != null && canceled.getAsBoolean())
					break;
				File entryFile = new File(root, entry.getName());
				try {
					if (!entryFile.getCanonicalPath().startsWith(rootPath)) {
						System.err.println("Entry '" + entry.getName() + "' would not be extracted into '" + rootPath + "'");
						continue;
					}
				} catch (IOException e) {
					System.err.println("Bad path for entry '" + entry.getName() + "': " + e.getMessage());
					continue;
				}
				onEntry.accept(entry, zip);
				zip.closeEntry();
			}
		}
	}

	/**
	 * Extracts a zip archive into a directory. It does this in a secure way, avoiding the zip-slip vulnerability.
	 * 
	 * @param in The zip-formatted input stream to parse
	 * @param directory The directory to extract the zip into
	 * @param onEntry A callback to be notified of each extracted file
	 * @return The extracted directory (same as the argument)
	 * @throws IOException If the file could not be read, the extracted files could not be written, or the callback throws an exception
	 */
	public static File extractZip(InputStream in, File directory, ExBiConsumer<File, String, IOException> onEntry) throws IOException {
		return extractZip(in, directory, onEntry, null);
	}

	/**
	 * Extracts a zip archive into a directory. It does this in a secure way, avoiding the zip-slip vulnerability.
	 * 
	 * @param in The zip-formatted input stream to parse
	 * @param directory The directory to extract the zip into
	 * @param onEntry A callback to be notified of each extracted file
	 * @param canceled Returns true if the operation should immediately cease
	 * @return The extracted directory (same as the argument)
	 * @throws IOException If the file could not be read, the extracted files could not be written, or the callback throws an exception
	 */
	public static File extractZip(InputStream in, File directory, ExBiConsumer<File, String, IOException> onEntry, BooleanSupplier canceled)
		throws IOException {
		extractZip(in, (entry, zip) -> {
			File entryFile = new File(directory, entry.getName());
			if (entry.isDirectory())
				entryFile.mkdirs();
			else {
				entryFile.getParentFile().mkdirs();
				try (OutputStream out = new FileOutputStream(entryFile)) {
					copy(zip, out, null, canceled);
				}
			}
			entryFile.setLastModified(entry.getLastModifiedTime().toMillis());
			if (onEntry != null)
				onEntry.accept(entryFile, entry.getName());
		}, canceled);
		return directory;
	}

	/**
	 * Extracts a zip file in a secure way, avoiding the zip-slip vulnerability. This class uses an abstraction which is nice in itself, but
	 * also gets around tools
	 * 
	 * @param in The zip-formatted input stream to parse
	 * @param onEntry A callback to be notified of each extracted file
	 * @param canceled Returns true if the operation should immediately cease
	 * @throws IOException If the file could not be read or the callback throws an exception
	 */
	public static void extractZip(InputStream in, ExConsumer<ArchiveEntry, IOException> onEntry, BooleanSupplier canceled)
		throws IOException {
		extractZip(in, (entry, zip) -> onEntry.accept(new ArchiveEntry.Default(entry, zip)), canceled);
	}

	/** An entry in an archive file */
	public interface ArchiveEntry {
		/** @return The path of this entry in the Zip file */
		String getPath();

		/** @return Whether this entry represents a directory */
		boolean isDirectory();

		/** @return The last-modified time of this entry, in milliseconds since the epoch */
		long getLastModified();

		/** @return The (uncompressed) size of the entry */
		long size();

		/** @return The compressed size of the entry */
		long getCompressedSize();

		/** @return The comment associated with the entry */
		String getComment();

		/** @return The content of the entry */
		InputStream getContent();

		/** An archive entry around a {@link ZipEntry} */
		public class Default implements ArchiveEntry {
			private final ZipEntry theEntry;
			private final ZipInputStream theInput;

			/**
			 * @param entry The entry to wrap
			 * @param input The zip input stream to get the content of the entry from
			 */
			public Default(ZipEntry entry, ZipInputStream input) {
				theEntry = entry;
				theInput = input;
			}

			@Override
			public String getPath() {
				return theEntry.getName();
			}

			@Override
			public boolean isDirectory() {
				return theEntry.isDirectory();
			}

			@Override
			public long getLastModified() {
				return theEntry.getLastModifiedTime().toMillis();
			}

			@Override
			public long size() {
				return theEntry.getSize();
			}

			@Override
			public long getCompressedSize() {
				return theEntry.getCompressedSize();
			}

			@Override
			public String getComment() {
				return theEntry.getComment();
			}

			@Override
			public InputStream getContent() {
				return theInput;
			}
		}
	}
}

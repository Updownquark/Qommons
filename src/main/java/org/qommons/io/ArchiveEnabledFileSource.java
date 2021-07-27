package org.qommons.io;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.qommons.ArrayUtils;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.ex.ExBiConsumer;
import org.qommons.io.BetterFile.FileBacking;
import org.qommons.io.BetterFile.FileBooleanAttribute;
import org.qommons.io.FileUtils.DirectorySyncResults;

import sun.nio.cs.ArrayDecoder;

/** Enables using archive files as a directory structure within an existing file system */
public class ArchiveEnabledFileSource implements BetterFile.FileDataSource {
	private final BetterFile.FileDataSource theWrapped;
	private final List<FileArchival> theArchivalMethods;
	private int theMaxArchiveDepth;

	/** @param wrapped The file source to wrap */
	public ArchiveEnabledFileSource(BetterFile.FileDataSource wrapped) {
		theWrapped = wrapped;
		theArchivalMethods = new ArrayList<>();
		theMaxArchiveDepth = 10;
	}

	/**
	 * @param compression The compression method to enable
	 * @return This file source
	 */
	public ArchiveEnabledFileSource withArchival(ArchiveEnabledFileSource.FileArchival compression) {
		theArchivalMethods.add(compression);
		return this;
	}

	/** @return The maximum depth to descend within archives (e.g. zips inside zips) */
	public int getMaxArchiveDepth() {
		return theMaxArchiveDepth; // Zipception
	}

	/**
	 * @param maxZipDepth The maximum depth to descend within archives (e.g. zips inside zips)
	 * @return This file source
	 */
	public ArchiveEnabledFileSource setMaxArchiveDepth(int maxZipDepth) {
		theMaxArchiveDepth = maxZipDepth;
		return this;
	}

	// static File getNativeFile(BetterFile.FileBacking backing, boolean[] tempFile) {
	// if (backing instanceof NativeFileSource.NativeFileBacking) {
	// return ((NativeFileSource.NativeFileBacking) backing).getFile();
	// } else {
	// tempFile[0] = true;
	// File file = null;
	// try {
	// file = java.io.File.createTempFile(FileUtils.class.getName(), "zipTemp");
	// } catch (IOException e) {
	// return null;
	// }
	// try (InputStream in = new BufferedInputStream(backing.read());
	// OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
	// byte[] buffer = new byte[64 * 1024];
	// int read = in.read(buffer);
	// while (read >= 0) {
	// out.write(buffer, 0, read);
	// read = in.read(buffer);
	// }
	// } catch (IOException e) {
	// file.delete();
	// file = null;
	// return null;
	// }
	// return file;
	// }
	// }

	boolean isArchive(BetterFile.FileBacking backing) {
		if (backing.get(FileBooleanAttribute.Directory))
			return false;
		for (ArchiveEnabledFileSource.FileArchival compression : theArchivalMethods) {
			if (compression.detectPossibleCompressedFile(backing.getName()) && backing.exists()
				&& !backing.get(FileBooleanAttribute.Directory)) {
				try {
					if (compression.detectCompressedFile(backing, () -> false))
						return true;
				} catch (IOException e) {}
			}
		}
		return false;
	}

	ArchiveEntry getArchive(ArchiveEnabledFileBacking backing, Consumer<? super ArchiveEntry> onChild,
		ExBiConsumer<ArchiveEntry, CharSequence, IOException> forEach, BooleanSupplier canceled) throws IOException {
		for (ArchiveEnabledFileSource.FileArchival archival : theArchivalMethods) {
			if (archival.detectPossibleCompressedFile(backing.getSource().getName()) && backing.getSource().exists()
				&& !backing.getSource().get(FileBooleanAttribute.Directory)) {
				try {
					if (!archival.detectCompressedFile(backing.getSource(), canceled))
						continue;
				} catch (IOException e) {
					continue;
				}

				backing.setArchival(archival);
				return archival.parseStructure(backing, null, onChild, forEach, canceled);
			}
		}
		return null;
	}

	@Override
	public List<FileBacking> getRoots() {
		return QommonsUtils.map(theWrapped.getRoots(), r -> new ArchiveEnabledFileBacking(null, r, 0), true);
	}

	@Override
	public FileBacking getRoot(String name) {
		FileBacking wrapped = theWrapped.getRoot(name);
		return new ArchiveEnabledFileBacking(null, wrapped, 0);
	}

	@Override
	public String getUrlRoot() {
		return theWrapped.getUrlRoot();
	}

	@Override
	public String toString() {
		return theWrapped.toString();
	}

	static final long FILE_CHECK_INTERVAL = 10;

	class ArchiveEnabledFileBacking implements BetterFile.FileBacking {
		private final ArchiveEnabledFileBacking theParent;
		private final BetterFile.FileBacking theBacking;
		private final int theArchiveDepth;
		private final long theLastModified;
		private volatile boolean hasCheckedForArchive;
		private volatile FileArchival theArchival;
		private volatile ArchiveEntry theRootEntry;

		private volatile long theLastCheck;

		ArchiveEnabledFileBacking(ArchiveEnabledFileBacking parent, BetterFile.FileBacking wrapped, int archiveDepth) {
			theParent = parent;
			theBacking = wrapped;
			theArchiveDepth = archiveDepth;
			if (theParent != null && theParent.getZipDepth() >= theMaxArchiveDepth)
				hasCheckedForArchive = true; // Don't check for archive--too deep
			theLastModified = theBacking.getLastModified();
		}

		BetterFile.FileBacking getSource() {
			return theBacking;
		}

		FileArchival getArchival() {
			return theArchival;
		}

		void setArchival(FileArchival archival) {
			theArchival = archival;
		}

		ArchiveEntry getArchiveData(Consumer<ArchiveEntry> onChild, ExBiConsumer<ArchiveEntry, CharSequence, IOException> forEach,
			BooleanSupplier canceled) throws IOException {
			boolean newData = false;
			if (!hasCheckedForArchive) {
				synchronized (this) {
					if (!hasCheckedForArchive) {
						theRootEntry = getArchive(this, onChild, forEach, canceled);
						if (theRootEntry != null || !canceled.getAsBoolean()) // If the user canceled, we may need to check again
							hasCheckedForArchive = true;
						newData = true;
					}
				}
			}
			if (!newData && theRootEntry != null) {
				if (onChild != null) {
					for (ArchiveEntry child : theRootEntry.listFiles())
						onChild.accept(child);
				}
				if (forEach != null)
					theArchival.parseStructure(theBacking, theRootEntry, null, forEach, canceled);
			}
			return theRootEntry;
		}

		public int getZipDepth() {
			return theArchiveDepth;
		}

		@Override
		public String getName() {
			return theBacking.getName();
		}

		@Override
		public boolean check() {
			if (!theBacking.check())
				return false;
			long now = System.currentTimeMillis();
			if (now - theLastCheck > FILE_CHECK_INTERVAL) {
				if (theLastModified != theBacking.getLastModified())
					return false;
				theLastCheck = now;
			}
			return true;
		}

		@Override
		public boolean isRoot() {
			return theBacking.isRoot();
		}

		@Override
		public boolean exists() {
			return theBacking.exists();
		}

		@Override
		public long getLastModified() {
			return theBacking.getLastModified();
		}

		@Override
		public boolean get(FileBooleanAttribute attribute) {
			if (attribute == FileBooleanAttribute.Directory) {
				if (theRootEntry != null)
					return true;
				else if (!hasCheckedForArchive && isArchive(theBacking))
					return true;
			}
			return theBacking.get(attribute);
		}

		@Override
		public boolean discoverContents(Consumer<? super FileBacking> onDiscovered, BooleanSupplier canceled) {
			try {
				if (getArchiveData(entry -> {
					onDiscovered.accept(new ArchiveEnabledFileBacking(this, new ArchiveFileBacking(this, entry), theArchiveDepth + 1));
				}, null, canceled) != null)
					return true;
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
			if (canceled.getAsBoolean())
				return false;
			else {
				return theBacking.discoverContents(f -> {
					onDiscovered.accept(new ArchiveEnabledFileBacking(this, f, theArchiveDepth));
				}, canceled);
			}
		}

		@Override
		public FileBacking getChild(String fileName) {
			try {
				ArchiveEntry root = getArchiveData(null, null, () -> false);
				if (root != null) {
					ArchiveEntry child = root.getFile(fileName);
					return child == null ? null
						: new ArchiveEnabledFileBacking(this, new ArchiveFileBacking(this, child), theArchiveDepth + 1);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			FileBacking f = theBacking.getChild(fileName);
			return f == null ? null : new ArchiveEnabledFileBacking(this, f, theArchiveDepth);
		}

		@Override
		public long length() {
			return theBacking.length();
		}

		@Override
		public InputStream read(long startFrom, BooleanSupplier canceled) throws IOException {
			return theBacking.read(startFrom, canceled);
		}

		@Override
		public BetterFile.FileBacking createChild(String fileName, boolean directory) throws IOException {
			ArchiveEntry root = getArchiveData(null, null, () -> false);
			if (root != null) {
				ArchiveEntry child = root.getFile(fileName);
				if (child != null)
					return new ArchiveEnabledFileBacking(this, new ArchiveFileBacking(this, child), theArchiveDepth + 1);
				throw new IOException("Cannot create archive entries");
			}
			FileBacking f = theBacking.createChild(fileName, directory);
			return f == null ? null : new ArchiveEnabledFileBacking(this, f, theArchiveDepth);
		}

		@Override
		public void delete(DirectorySyncResults results) throws IOException {
			theBacking.delete(results);
		}

		@Override
		public OutputStream write() throws IOException {
			return theBacking.write();
		}

		@Override
		public boolean set(BetterFile.FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
			return get(attribute) != value;
		}

		@Override
		public boolean setLastModified(long lastModified) {
			return false;
		}

		@Override
		public void move(String newFilePath) throws IOException {
			throw new IOException("Cannot move archive entries");
		}

		@Override
		public void visitAll(ExBiConsumer<? super FileBacking, CharSequence, IOException> forEach, BooleanSupplier canceled)
			throws IOException {
			forEach.accept(this, "");
			if (getArchiveData(null, (entry, path) -> {
				forEach.accept(new ArchiveEnabledFileBacking(this, new ArchiveFileBacking(this, entry), theArchiveDepth + 1), path);
			}, canceled) != null)
				return;
			theBacking.visitAll(forEach, canceled);
		}

		@Override
		public StringBuilder alterUrl(StringBuilder url) {
			if (theArchival != null)
				return theArchival.alterUrl(url);
			else if (theBacking != null)
				return theBacking.alterUrl(url);
			else
				return url;
		}

		@Override
		public int hashCode() {
			return theBacking.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ArchiveEnabledFileBacking && theBacking.equals(((ArchiveEnabledFileBacking) obj).theBacking);
		}

		@Override
		public String toString() {
			return theBacking.toString();
		}
	}

	static class ArchiveFileBacking implements BetterFile.FileBacking {
		private final ArchiveEnabledFileBacking theRoot;
		private final ArchiveEntry theEntry;

		ArchiveFileBacking(ArchiveEnabledFileBacking root, ArchiveEntry entry) {
			theRoot = root;
			theEntry = entry;
		}

		@Override
		public boolean check() {
			return theRoot.check();
		}

		@Override
		public boolean isRoot() {
			return false;
		}

		@Override
		public boolean exists() {
			return check();
		}

		@Override
		public long getLastModified() {
			if (!theRoot.check())
				return 0;
			return theEntry.getLastModified();
		}

		@Override
		public boolean get(BetterFile.FileBooleanAttribute attribute) {
			if (!theRoot.check())
				return false;
			switch (attribute) {
			case Directory:
				return theEntry.isDirectory();
			case Hidden:
				return false;
			case Readable:
				return true;
			case Writable:
				return false;
			case Symbolic:
				return false;
			}
			throw new IllegalStateException("" + attribute);
		}

		@Override
		public long length() {
			if (!theRoot.check())
				return 0;
			return theEntry.length();
		}

		@Override
		public InputStream read(long startFrom, BooleanSupplier canceled) throws IOException {
			if (!theRoot.check())
				throw new FileNotFoundException("Data may have changed");
			return theRoot.getArchival().read(theRoot.getSource(), theEntry, startFrom, canceled);
		}

		@Override
		public String getName() {
			return theEntry.getName();
		}

		@Override
		public boolean discoverContents(Consumer<? super FileBacking> onDiscovered, BooleanSupplier canceled) {
			for (ArchiveEntry entry : theEntry.listFiles()) {
				if (canceled.getAsBoolean())
					return false;
				onDiscovered.accept(new ArchiveFileBacking(theRoot, entry));
			}
			return true;
		}

		@Override
		public BetterFile.FileBacking getChild(String fileName) {
			ArchiveEntry file = theEntry.getFile(fileName);
			if (file != null)
				return new ArchiveFileBacking(theRoot, file);
			return new DanglingArchiveEntry(this, fileName);
		}

		@Override
		public BetterFile.FileBacking createChild(String fileName, boolean directory) throws IOException {
			ArchiveEntry child = theEntry.getFile(fileName);
			if (child == null)
				throw new IOException("Cannot create zip entries");
			return new ArchiveFileBacking(theRoot, child);
		}

		@Override
		public void delete(DirectorySyncResults results) throws IOException {
			throw new IOException("Cannot delete zip entries this way");
		}

		@Override
		public OutputStream write() throws IOException {
			throw new IOException("Cannot overwrite zip entries");
		}

		@Override
		public boolean set(BetterFile.FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
			return get(attribute) != value;
		}

		@Override
		public boolean setLastModified(long lastModified) {
			return false;
		}

		@Override
		public void move(String newFilePath) throws IOException {
			throw new IOException("Cannot move archive entries");
		}

		@Override
		public void visitAll(ExBiConsumer<? super FileBacking, CharSequence, IOException> forEach, BooleanSupplier canceled)
			throws IOException {
			visitAll(new StringBuilder(), forEach, canceled);
		}

		private void visitAll(StringBuilder path, ExBiConsumer<? super FileBacking, CharSequence, IOException> forEach,
			BooleanSupplier canceled) throws IOException {
			if (canceled.getAsBoolean())
				return;
			forEach.accept(this, path);
			int oldLen = path.length();
			try {
				discoverContents(child -> {
					if (canceled.getAsBoolean())
						return;
					path.append(child.getName());
					try {
						((ArchiveFileBacking) child).visitAll(path, forEach, canceled);
					} catch (IOException e) {
						throw new IOWrapperException(e);
					}
					path.setLength(oldLen);
				}, canceled);
			} catch (IOWrapperException e) {
				throw e.getCause();
			}
		}

		@Override
		public StringBuilder alterUrl(StringBuilder url) {
			return theRoot.getArchival().alterUrl(url);
		}

		static class IOWrapperException extends RuntimeException {
			IOWrapperException(IOException wrap) {
				super(wrap);
			}

			@Override
			public synchronized IOException getCause() {
				return (IOException) super.getCause();
			}
		}
	}

	static class DanglingArchiveEntry implements BetterFile.FileBacking {
		private final ArchiveFileBacking theAncestor;
		private final String theName;

		DanglingArchiveEntry(ArchiveFileBacking ancestor, String name) {
			theAncestor = ancestor;
			theName = name;
		}

		@Override
		public boolean check() {
			return theAncestor.check();
		}

		@Override
		public boolean isRoot() {
			return false;
		}

		@Override
		public boolean exists() {
			return false;
		}

		@Override
		public long getLastModified() {
			return 0;
		}

		@Override
		public boolean get(BetterFile.FileBooleanAttribute attribute) {
			return false;
		}

		@Override
		public long length() {
			return 0;
		}

		@Override
		public InputStream read(long startFrom, BooleanSupplier canceled) throws IOException {
			throw new FileNotFoundException("No such entry");
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public boolean discoverContents(Consumer<? super FileBacking> onDiscovered, BooleanSupplier canceled) {
			return true;
		}

		@Override
		public BetterFile.FileBacking getChild(String fileName) {
			return new DanglingArchiveEntry(theAncestor, FileUtils.concatPath(theName, fileName));
		}

		@Override
		public BetterFile.FileBacking createChild(String fileName, boolean directory) throws IOException {
			throw new IOException("Cannot create zip entries this way");
		}

		@Override
		public void delete(DirectorySyncResults results) throws IOException {
		}

		@Override
		public OutputStream write() throws IOException {
			throw new IOException("Cannot create zip entries this way");
		}

		@Override
		public boolean set(BetterFile.FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
			return !value;
		}

		@Override
		public boolean setLastModified(long lastModified) {
			return false;
		}

		@Override
		public void move(String newFilePath) throws IOException {
			throw new IOException("No such archive entry");
		}

		@Override
		public void visitAll(ExBiConsumer<? super FileBacking, CharSequence, IOException> forEach, BooleanSupplier canceled)
			throws IOException {
			forEach.accept(this, "");
		}
	}

	/** Represents an entry in an archive */
	public interface ArchiveEntry {
		/** @return The name of the entry */
		String getName();

		/** @return The child entries of this directory entry */
		List<? extends ArchiveEntry> listFiles();

		/** @return The length of this entry's file content */
		long length();

		/** @return The last modified time of this entry */
		long getLastModified();

		/** @return Whether this entry represents a directory */
		boolean isDirectory();

		/**
		 * @param name The name of the entry to get (not a full path)
		 * @return The child of this directory entry with the given name, or null if it does not exist in the archive
		 */
		ArchiveEntry getFile(String name);
	}

	/** Represents a method of archival which can be used to represent a type of archive files as a directory structure */
	public interface FileArchival {
		/**
		 * Performs a first test on a file name to see if it is possibly an archive file recognized by this method
		 * 
		 * @param fileName The name of the file to test
		 * @return Whether the file name is recognized as a potential archive
		 */
		boolean detectPossibleCompressedFile(String fileName);

		/**
		 * A deeper test, where the file's content may be quickly tested to confirm that it is in fact an archive recognized by this method
		 * 
		 * @param file The file to test
		 * @param canceled Returns true if the user cancels the operation
		 * @return Whether the file is confirmed as an archive
		 * @throws IOException If the file cannot be read
		 */
		boolean detectCompressedFile(FileBacking file, BooleanSupplier canceled) throws IOException;

		/**
		 * @param file The file to parse
		 * @param existingRoot The archive entry that was previously parsed as the root of the archive, if any
		 * @param onChild Accepts each archive entry directly under the root--may be null
		 * @param forEach Accepts each archive entry found in the archive and its name--may be null
		 * @param cancel Returns true if the user cancels the operation
		 * @return The root entry of the archive
		 * @throws IOException
		 */
		ArchiveEntry parseStructure(FileBacking file, ArchiveEntry existingRoot, Consumer<? super ArchiveEntry> onChild,
			ExBiConsumer<ArchiveEntry, CharSequence, IOException> forEach, BooleanSupplier cancel) throws IOException;

		/**
		 * @param file The archive file
		 * @param entry The entry to read
		 * @param startFrom The offset from the beginning of the entry to start at
		 * @param canceled Returns true if the user cancels the operation
		 * @return An input stream to read the entry's contents
		 * @throws IOException If an error occurs reading the entry
		 */
		InputStream read(FileBacking file, ArchiveEntry entry, long startFrom, BooleanSupplier canceled) throws IOException;

		/**
		 * @param url The string builder containing a URL representing the archive file
		 * @return The same string builder, altered to represent a prefix addressing an entry inside the archive (e.g. jar://
		 */
		StringBuilder alterUrl(StringBuilder url);
	}

	static class VisitingEntry implements ArchiveEntry {
		private final ArchiveEntry theWrapped;
		private final String theName;
		private final long theSize;
		private final long theLM;
		private final long thePosition;
		private final boolean isDirectory;
		private final InputStream in;
		private boolean isExhausted;

		VisitingEntry(ArchiveEntry wrapped, String name, long size, long lM, long position, boolean directory, InputStream in) {
			theWrapped = wrapped;
			theName = name;
			theSize = size;
			theLM = lM;
			thePosition = position;
			isDirectory = directory;
			this.in = in;
		}

		ArchiveEntry getWrapped() {
			return theWrapped;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public boolean isDirectory() {
			return isDirectory;
		}

		@Override
		public long length() {
			return theSize;
		}

		@Override
		public long getLastModified() {
			return theLM;
		}

		@Override
		public List<? extends ArchiveEntry> listFiles() {
			if (theWrapped != null)
				return theWrapped.listFiles();
			return Collections.emptyList();
		}

		@Override
		public ArchiveEntry getFile(String name) {
			return theWrapped != null ? theWrapped.getFile(name) : null;
		}

		long getPosition() {
			return thePosition;
		}

		public InputStream getIn() throws IOException {
			if (isDirectory)
				throw new IOException("Not a file");
			if (isExhausted)
				return null;
			isExhausted = true;
			return in;
		}
	}

	static class DefaultArchiveEntry implements ArchiveEntry {
		private final String theName;
		private final long thePosition;
		private long theSize;
		private long theLastModified;
		private final List<DefaultArchiveEntry> theChildren;

		DefaultArchiveEntry(String name, long position, boolean directory) {
			theName = name;
			thePosition = position;
			theChildren = directory ? new ArrayList<>() : null;
		}

		DefaultArchiveEntry fill(long size, long lastModified) {
			theSize = size;
			theLastModified = lastModified;
			return this;
		}

		long getPosition() {
			return thePosition;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public long length() {
			return theSize;
		}

		@Override
		public long getLastModified() {
			return theLastModified;
		}

		@Override
		public boolean isDirectory() {
			return theChildren != null;
		}

		@Override
		public List<? extends ArchiveEntry> listFiles() {
			if (theChildren != null)
				return Collections.unmodifiableList(theChildren);
			else
				return Collections.emptyList();
		}

		@Override
		public DefaultArchiveEntry getFile(String name) {
			if (theChildren == null)
				return null;
			int index = ArrayUtils.binarySearch(theChildren, f -> StringUtils.compareNumberTolerant(name, f.getName(), true, true));
			return index < 0 ? null : theChildren.get(index);
		}

		DefaultArchiveEntry add(String[] path, int pathIndex, long position, boolean directory) {
			int index = ArrayUtils.binarySearch(theChildren,
				f -> StringUtils.compareNumberTolerant(path[pathIndex], f.getName(), true, true));
			DefaultArchiveEntry child;
			if (index < 0) {
				if (pathIndex == path.length - 1)
					child = new DefaultArchiveEntry(path[pathIndex], position, directory);
				else
					child = new DefaultArchiveEntry(path[pathIndex], 0, true);
				theChildren.add(-index - 1, child);
			} else
				child = theChildren.get(index);
			if (pathIndex < path.length - 1)
				return child.add(path, pathIndex + 1, position, directory);
			else
				return child;
		}

		DefaultArchiveEntry at(String[] path, int index) {
			if (index == path.length)
				return this;
			DefaultArchiveEntry entry = getFile(path[index]);
			return entry == null ? null : entry.at(path, index + 1);
		}

		void clear() {
			theChildren.clear();
		}

		@Override
		public String toString() {
			return theName;
		}
	}

	/**
	 * An archival method for interfacing with ZIP files. Because ZIP files contain their contents in a continuous record at the end of the
	 * file, this class is able to represent the file structure and access individual files very quickly on file systems where random seek
	 * is fast.
	 */
	public static class ZipCompression implements FileArchival {
		@Override
		public boolean detectPossibleCompressedFile(String fileName) {
			int dotIdx = fileName.lastIndexOf('.');
			if (dotIdx < 0)
				return false;
			String ext = fileName.substring(dotIdx + 1).toLowerCase();
			switch (ext) {
			case "zip":
			case "jar":
				return true;
			default:
				return false;
			}
		}

		@Override
		public boolean detectCompressedFile(FileBacking file, BooleanSupplier canceled) throws IOException {
			try (InputStream in = file.read(0, canceled)) {
				if (in == null)
					return false;
				return in.read() == 'P' && in.read() == 'K';
			}
		}

		@Override
		public ArchiveEntry parseStructure(FileBacking file, ArchiveEntry existingRoot, Consumer<? super ArchiveEntry> onChild,
			ExBiConsumer<ArchiveEntry, CharSequence, IOException> forEach, BooleanSupplier canceled) throws IOException {
			long len = existingRoot == null ? file.length() : -1;
			if (len >= 0) {
				byte[] buffer = new byte[(int) Math.min(len, 64 * 1024)];
				long seekPos = len - buffer.length;
				EndOfCentralDirectoryRecord eocd = null;
				while (eocd == null) {
					if (canceled.getAsBoolean())
						return null;
					try (InputStream in = file.read(seekPos, canceled)) {
						if (in == null)
							return null;
						fillBuffer(in, buffer, 0, buffer.length);
						eocd = findEOCD(buffer);
						if (eocd == null) {
							if (seekPos < 46) {
								// Central directory header has minimum size 46 bytes. If we haven't found the ECOD yet, it doesn't exist.
								break;
							}
							seekPos = Math.max(0, seekPos - buffer.length);
						}
					}
				}
				if (eocd == null)
					throw new IOException("Not actually a ZIP file: " + file.getName());
				InputStream eocdIn;
				if (eocd.centralDirOffset >= seekPos) {
					// We have the central directory in the buffer
					eocdIn = new ByteArrayInputStream(buffer, (int) (eocd.centralDirOffset - seekPos), (int) eocd.centralDirSize);
				} else
					eocdIn = file.read(eocd.centralDirOffset, canceled);
				if (eocdIn == null)
					return null;
				return new CDRReader(eocd, eocdIn).readCDR((DefaultArchiveEntry) existingRoot, onChild, forEach, canceled);
			} else {
				// If we don't know where the end is, looking for the directory at the end of the file can't be efficient
				// We can do one more thing before we just parse the entire file.
				// We can look for local entry headers and, if the compressed size is recorded in the header skip the compressed data.
				// We'll still have to read the entire file, but we won't have to deal with decompression.
				// Zip allows for this information to be missing from the beginning of the file, though, in which case we're hosed.
				DefaultArchiveEntry root = new DefaultArchiveEntry("", 0, true);
				long pos = 0;
				int entryIndex = 1;
				boolean badEntry = false;
				Set<String> children = onChild != null ? new HashSet<>() : null;
				try (InputStream in = file.read(0, canceled)) {
					if (in == null)
						return null;
					int nextRead;
					while ((nextRead = in.read()) >= 0) {
						if (canceled.getAsBoolean())
							return null;
						long entryPos = pos;
						if (nextRead != 'P' || in.read() != 'K' || in.read() != 3 || in.read() != 4)
							throw new IOException("Bad ZIP entry #" + entryIndex + "@" + pos);
						pos += 4;
						in.read();
						in.read();// Version to extract
						int flags = in.read() | (in.read() << 8);
						if ((flags & 0x80) != 0) {
							// This flag means that the entry sizes are not recorded here, but at the end of the compressed data
							// In this case, we can't catalog all the entries without actually parsing the data
							badEntry = true;
							break;
						}
						in.read();
						in.read();// compression method
						pos += 6;
						long modTime = in.read() | (in.read() << 8) | (in.read() << 16) | (((long) in.read()) << 24);
						pos += 4;
						modTime = dosToJavaTime(modTime);
						in.skip(4); // CRC32
						pos += 4;
						long compressedSize = in.read() | (in.read() << 8) | (in.read() << 16) | (((long) in.read()) << 24);
						pos += 4;
						long uncompressedSize = in.read() | (in.read() << 8) | (in.read() << 16) | (((long) in.read()) << 24);
						pos += 4;
						int fileNameLen = in.read() | (in.read() << 8);
						int extraLen = in.read() | (in.read() << 8);
						pos += 4;
						byte[] nameBytes = new byte[fileNameLen];
						int read = 0;
						while (read < fileNameLen)
							read += in.read(nameBytes, read, fileNameLen - read);
						pos += fileNameLen;
						String fileName = read(nameBytes, 0, fileNameLen);
						if (extraLen > 0)
							in.skip(extraLen);
						pos += extraLen;
						String[] path = FileUtils.splitPath(fileName);
						DefaultArchiveEntry newEntry = root.add(path, 0, entryPos, fileName.endsWith("/")).fill(uncompressedSize, modTime);
						if (forEach != null) {
							ArchiveEntry oldEntry = existingRoot != null ? ((DefaultArchiveEntry) existingRoot).at(path, 0) : null;
							forEach.accept(oldEntry != null ? oldEntry : newEntry, fileName);
						}
						if (children != null && children.add(path[0]))
							onChild.accept(root.getFile(path[0]));
						long longRead = 0;
						while (longRead < compressedSize) {
							if (canceled.getAsBoolean())
								return null;
							longRead = in.skip(compressedSize - longRead);
						}
						pos += compressedSize;

						entryIndex++;
					}
				}
				if (!badEntry)
					return root;
				root.clear();
				try (InputStream in = file.read(0, canceled)) {
					if (in == null)
						return null;
					try (CountingInputStream countingIn = new CountingInputStream(in); //
						ZipInputStream zip = new ZipInputStream(in)) {
						long entryPos = countingIn.getPosition();
						for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
							if (canceled.getAsBoolean())
								return null;
							String[] path = FileUtils.splitPath(entry.getName());
							DefaultArchiveEntry newEntry = root.add(path, 0, entryPos, entry.isDirectory()).fill(entry.getSize(),
								entry.getLastModifiedTime().toMillis());
							if (forEach != null) {
								ArchiveEntry oldEntry = existingRoot != null ? ((DefaultArchiveEntry) existingRoot).at(path, 0) : null;
								forEach.accept(oldEntry != null ? oldEntry : newEntry, entry.getName());
							}
							if (children != null && children.add(path[0]))
								onChild.accept(root.getFile(path[0]));
						}
					}
				}
				return root;
			}
		}

		static void fillBuffer(InputStream in, byte[] buffer, int offset, int length) throws IOException {
			int read = in.read(buffer, offset, length);
			while (read >= 0 && read < length) {
				offset += read;
				length -= read;
				read = in.read(buffer, offset, length);
			}
			if (read < 0)
				throw new EOFException();
		}

		static EndOfCentralDirectoryRecord findEOCD(byte[] buffer) {
			int eocd = -1;
			for (int i = buffer.length - 20; i >= 0; i--) {
				if (buffer[i] == 0x50 && buffer[i + 1] == 0x4b && buffer[i + 2] == 0x05 && buffer[i + 3] == 0x06) {
					eocd = i;
					break;
				}
			}
			if (eocd < 0)
				return null;
			return new EndOfCentralDirectoryRecord(getInt(buffer, eocd + 4), //
				getInt(buffer, eocd + 8), //
				getLong(buffer, eocd + 12), //
				getLong(buffer, eocd + 16));
		}

		static class CDRReader {
			private final EndOfCentralDirectoryRecord theEnd;
			private final InputStream theFile;
			private long theCdrOffset;
			private int theBufferOffset;

			CDRReader(EndOfCentralDirectoryRecord end, InputStream file) {
				theEnd = end;
				theFile = file;
			}

			ArchiveEntry readCDR(DefaultArchiveEntry existingRoot, Consumer<? super ArchiveEntry> onChild,
				ExBiConsumer<ArchiveEntry, CharSequence, IOException> forEach, BooleanSupplier canceled) throws IOException {
				byte[] buffer = new byte[Math.min(64 * 1028, (int) theEnd.centralDirSize)];
				fillBuffer(theFile, buffer, 0, buffer.length);
				DefaultArchiveEntry root = new DefaultArchiveEntry("", 0, true);
				CharsetDecoder utfDecoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT);
				Set<String> children = onChild != null ? new HashSet<>() : null;
				for (int i = 0; i < theEnd.centralDirCount; i++) {
					if (canceled.getAsBoolean())
						return null;
					if (buffer[theBufferOffset] != 0x50 || buffer[theBufferOffset + 1] != 0x4b || buffer[theBufferOffset + 2] != 0x01
						|| buffer[theBufferOffset + 3] != 0x02)
						throw new IOException("Could not read Zip file");
					int nameLen = getInt(buffer, theBufferOffset + 28);
					int extraLen = getInt(buffer, theBufferOffset + 30);
					int commentLen = getInt(buffer, theBufferOffset + 32);
					int disk = getInt(buffer, 34);
					if (disk != theEnd.diskNumber) {
						if (!advance(buffer, 46 + nameLen + extraLen + commentLen, canceled))
							return null;
						continue;
					}
					int flag = getInt(buffer, theBufferOffset + 8);
					long modTime = getLong(buffer, theBufferOffset + 12);
					long lastMod = extendedDosToJavaTime(modTime);
					@SuppressWarnings("unused")
					long compressedSize = getLong(buffer, theBufferOffset + 20);
					long uncompressedSize = getLong(buffer, theBufferOffset + 24);
					long position = getLong(buffer, theBufferOffset + 42);
					String name;
					if ((flag & UTF_FLAG) != 0)
						name = readUtf(buffer, theBufferOffset + 46, nameLen, utfDecoder);
					else
						name = read(buffer, theBufferOffset + 46, nameLen);
					String[] path = name.split("/");
					DefaultArchiveEntry newEntry = root.add(path, 0, position, name.endsWith("/")).fill(uncompressedSize, lastMod);
					if (children != null && children.add(path[0]))
						onChild.accept(root.getFile(path[0]));
					if (forEach != null) {
						ArchiveEntry oldEntry = existingRoot != null ? existingRoot.at(path, 0) : null;
						forEach.accept(oldEntry != null ? oldEntry : newEntry, name);
					}

					try {
						if (!advance(buffer, 46 + nameLen + extraLen + commentLen, canceled))
							return null;
					} catch (RuntimeException | IOException e) {
						throw e;
					}
				}
				return root;
			}

			private boolean advance(byte[] buffer, int recordLength, BooleanSupplier canceled) throws IOException {
				theBufferOffset += recordLength;
				theCdrOffset += recordLength;
				boolean readMore;
				if (theBufferOffset >= buffer.length - 46)
					readMore = true;
				else {
					int nameLen = getInt(buffer, theBufferOffset + 28);
					int extraLen = getInt(buffer, theBufferOffset + 30);
					int commentLen = getInt(buffer, theBufferOffset + 32);
					readMore = theBufferOffset > buffer.length - 46 - nameLen - extraLen - commentLen;
				}
				if (readMore) {
					if (canceled.getAsBoolean())
						return false;
					int remaining = buffer.length - theBufferOffset;
					System.arraycopy(buffer, theBufferOffset, buffer, 0, remaining);
					int needed = (int) Math.min(buffer.length - remaining, theEnd.centralDirSize - theCdrOffset - remaining);
					if (needed > 0)
						fillBuffer(theFile, buffer, remaining, needed);
					theBufferOffset = 0;
				}
				return true;
			}
		}

		private static final int UTF_FLAG = 0x800;

		private static String read(byte[] buffer, int offset, int length) {
			char[] ch = new char[length];
			for (int i = 0; i < length; i++)
				ch[i] = (char) buffer[offset + i];
			return new String(ch);
		}

		private static String readUtf(byte[] buffer, int offset, int length, CharsetDecoder cd) {
			cd.reset();
			int len = (int) (length * cd.maxCharsPerByte());
			char[] ca = new char[len];
			if (len == 0)
				return new String(ca);
			// UTF-8 only for now. Other ArrayDeocder only handles
			// CodingErrorAction.REPLACE mode. ZipCoder uses
			// REPORT mode.
			if (cd instanceof ArrayDecoder) {
				int clen = ((ArrayDecoder) cd).decode(buffer, offset, length, ca);
				if (clen == -1) // malformed
					throw new IllegalArgumentException("MALFORMED");
				return new String(ca, 0, clen);
			}
			ByteBuffer bb = ByteBuffer.wrap(buffer, offset, length);
			CharBuffer cb = CharBuffer.wrap(ca);
			CoderResult cr = cd.decode(bb, cb, true);
			if (!cr.isUnderflow())
				throw new IllegalArgumentException(cr.toString());
			cr = cd.flush(cb);
			if (!cr.isUnderflow())
				throw new IllegalArgumentException(cr.toString());
			return new String(ca, 0, cb.position());
		}

		private static int getInt(byte[] buffer, int offset) {
			return unsigned(buffer[offset]) | unsigned(buffer[offset + 1]) << 8;
		}

		private static long getLong(byte[] buffer, int offset) {
			return unsigned(buffer[offset])//
				| ((long) unsigned(buffer[offset + 1])) << 8//
				| ((long) unsigned(buffer[offset + 2])) << 16//
				| ((long) unsigned(buffer[offset + 3])) << 24;
		}

		/**
		 * Converts DOS time to Java time (number of milliseconds since epoch).
		 */
		private static long dosToJavaTime(long dtime) {
			@SuppressWarnings("deprecation") // Use of date constructor.
			Date d = new Date((int) (((dtime >> 25) & 0x7f) + 80), (int) (((dtime >> 21) & 0x0f) - 1), (int) ((dtime >> 16) & 0x1f),
				(int) ((dtime >> 11) & 0x1f), (int) ((dtime >> 5) & 0x3f), (int) ((dtime << 1) & 0x3e));
			return d.getTime();
		}

		/**
		 * Converts extended DOS time to Java time, where up to 1999 milliseconds might be encoded into the upper half of the returned long.
		 *
		 * @param xdostime the extended DOS time value
		 * @return milliseconds since epoch
		 */
		static long extendedDosToJavaTime(long xdostime) {
			long time = dosToJavaTime(xdostime);
			return time + (xdostime >> 32);
		}

		@Override
		public InputStream read(FileBacking file, ArchiveEntry entry, long startFrom, BooleanSupplier canceled) throws IOException {
			if (entry instanceof VisitingEntry) {
				InputStream in = ((VisitingEntry) entry).getIn();
				if (in != null)
					return in;
			}
			InputStream fileIn = null;
			ZipInputStream zip = null;
			try {
				fileIn = file.read(0, canceled);
				if (fileIn == null)
					return null;
				zip = new ZipInputStream(fileIn);
				if (entry instanceof DefaultArchiveEntry)
					fileIn.skip(((DefaultArchiveEntry) entry).getPosition());
				else
					fileIn.skip(((VisitingEntry) entry).getPosition());
				zip.getNextEntry();
				WrappedEntryStream retStream = new WrappedEntryStream(fileIn, zip);
				if (startFrom > 0) {
					if (canceled.getAsBoolean()) {
						retStream.close();
						return null;
					}
					long skipped = retStream.skip(startFrom);
					long totalSkipped = skipped;
					while (skipped >= 0 && totalSkipped < startFrom) {
						if (canceled.getAsBoolean()) {
							retStream.close();
							return null;
						}
						skipped = retStream.skip(startFrom);
						totalSkipped += skipped;
					}
					if (skipped < 0) {
						retStream.close();
						throw new IOException("Only " + totalSkipped + " bytes in stream, not " + startFrom);
					}
				}
				return retStream;
			} catch (IOException e) {
				if (zip != null)
					zip.close();
				if (fileIn != null)
					fileIn.close();
				throw e;
			}
		}

		@Override
		public StringBuilder alterUrl(StringBuilder url) {
			int idx = url.indexOf(".jar/");
			if (idx < 0)
				return url;
			url.insert(idx + 4, '!');
			url.insert(0, "jar:");
			return url;
		}

		static class EndOfCentralDirectoryRecord {
			final int diskNumber;
			final int centralDirCount;
			final long centralDirSize;
			final long centralDirOffset;

			EndOfCentralDirectoryRecord(int diskNumber, int centralDirCount, long centralDirSize, long centralDirOffset) {
				this.diskNumber = diskNumber;
				this.centralDirCount = centralDirCount;
				this.centralDirSize = centralDirSize;
				this.centralDirOffset = centralDirOffset;
			}
		}

		static class WrappedEntryStream extends InputStream {
			private final InputStream theFileIn;
			private final InputStream theWrapped;

			WrappedEntryStream(InputStream fileIn, InputStream wrapped) {
				theFileIn = fileIn;
				theWrapped = wrapped;
			}

			@Override
			public int read() throws IOException {
				return theWrapped.read();
			}

			@Override
			public int read(byte[] b) throws IOException {
				return theWrapped.read(b);
			}

			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				return theWrapped.read(b, off, len);
			}

			@Override
			public long skip(long n) throws IOException {
				return theWrapped.skip(n);
			}

			@Override
			public int available() throws IOException {
				return theWrapped.available();
			}

			@Override
			public void close() throws IOException {
				theWrapped.close();
				theFileIn.close();
			}

			@Override
			public synchronized void mark(int readlimit) {
				theWrapped.mark(readlimit);
			}

			@Override
			public synchronized void reset() throws IOException {
				theWrapped.reset();
			}

			@Override
			public boolean markSupported() {
				return theWrapped.markSupported();
			}
		}
	}

	/**
	 * Allows interface with GZIP files as a directory containing a single entry. Because GZIP compression is not seekable, entries within
	 * archives stored in a .gz file may be slow to access
	 */
	public static class GZipCompression implements FileArchival {
		private static final CharsetDecoder NAME_DECODER = Charset.forName("ISO-8859-1").newDecoder();

		@Override
		public boolean detectPossibleCompressedFile(String fileName) {
			if (fileName.length() <= 3)
				return false;
			else if (fileName.charAt(fileName.length() - 3) != '.')
				return false;
			else
				return fileName.substring(fileName.length() - 2).equalsIgnoreCase("gz");
		}

		@Override
		public boolean detectCompressedFile(FileBacking file, BooleanSupplier canceled) throws IOException {
			try (InputStream in = file.read(0, canceled)) {
				if (in == null)
					return false;
				return in.read() == 0x1f && in.read() == 0x08b;
			}
		}

		@Override
		public ArchiveEntry parseStructure(FileBacking file, ArchiveEntry existingRoot, Consumer<? super ArchiveEntry> onChild,
			ExBiConsumer<ArchiveEntry, CharSequence, IOException> forEach, BooleanSupplier canceled) throws IOException {
			if (existingRoot != null) {
				forEach.accept(((GZipRoot) existingRoot).getFile(), "");
				return existingRoot;
			}
			// Although GZIP files can technically contain multiple "members", each with a file name in the header,
			// this feature is almost never used. In fact, java's GZIPInputStream jumps over these member boundaries
			// and supplies the data in a continuous stream, without exposing anything in the way of an entry.
			// Although I'd like to be able to provide fuller support of the format, there's also no fast way
			// to find the entries in the file, because unlike Zip, GZip doesn't provide compressed data size in the headers.
			// In order to find all the entries, we'd have to just parse the whole file.
			// So I'm doing what everyone else does--just represent the contents of a GZip file as a single file,
			// though I might as well parse some of the file data in the first header.
			String defaultName = file.getName();
			if (defaultName.length() > 3 && defaultName.charAt(defaultName.length() - 3) == '.'
				&& defaultName.substring(defaultName.length() - 2).equalsIgnoreCase("gz"))
				defaultName = defaultName.substring(0, defaultName.length() - 3);
			try (InputStream fileIn = file.read(0, canceled)) {
				if (fileIn == null)
					return null;
				try (BufferedInputStream in = new BufferedInputStream(fileIn)) {
					if (in.read() != 0x1f || in.read() != 0x8b)
						throw new IOException("Not a GZip file");
					if (in.read() != 8)
						throw new IOException("Only DEFLATE compression is supported");
					int flags = in.read();
					if ((flags & 0b00000111) != 0)
						throw new IOException("Information in reserved bits of flag");
					long mTime = in.read() | (in.read() << 8) | (in.read() << 16) | (((long) in.read()) << 24);
					mTime *= 1000;
					in.read(); // XFL
					in.read();// OS
					if ((flags & 0b001) != 0) { // Compressor directives
						int xLen = in.read() | (in.read() << 8);
						for (int i = 0; i < xLen; i++)
							in.read();
					}
					String name;
					if ((flags & 0b0001) != 0) {
						ByteArrayOutputStream nameBytes = new ByteArrayOutputStream();
						int b = in.read();
						while (b != 0) {
							nameBytes.write(b);
							b = in.read();
						}
						name = NAME_DECODER.decode(ByteBuffer.wrap(nameBytes.toByteArray())).toString();
					} else
						name = defaultName;
					GZipRoot root = new GZipRoot(name, mTime);
					if (onChild != null)
						onChild.accept(root.getFile());
					return root;
				}
			}
		}

		@Override
		public InputStream read(FileBacking file, ArchiveEntry entry, long startFrom, BooleanSupplier canceled) throws IOException {
			InputStream fileIn = file.read(0, canceled);
			InputStream gzIn = fileIn == null ? null : new GZIPInputStream(fileIn);
			if (startFrom > 0) {
				long skipped = gzIn.skip(startFrom);
				long totalSkipped = skipped;
				while (totalSkipped < startFrom && skipped >= 0) {
					skipped = gzIn.skip(startFrom - totalSkipped);
					totalSkipped += skipped;
				}
				if (skipped < 0)
					throw new IOException("Could not skip to " + startFrom);
			}
			return gzIn;
		}

		@Override
		public StringBuilder alterUrl(StringBuilder url) {
			return url;
		}

		static class GZipRoot implements ArchiveEntry {
			private final GZipCompressedFile theFile;

			GZipRoot(String name, long modTime) {
				theFile = new GZipCompressedFile(name, modTime);
			}

			GZipCompressedFile getFile() {
				return theFile;
			}

			@Override
			public String getName() {
				return theFile.getName();
			}

			@Override
			public long length() {
				return -1;
			}

			@Override
			public long getLastModified() {
				return theFile.getLastModified();
			}

			@Override
			public boolean isDirectory() {
				return true;
			}

			@Override
			public List<? extends ArchiveEntry> listFiles() {
				return Arrays.asList(theFile);
			}

			@Override
			public ArchiveEntry getFile(String name) {
				if (name.equals(theFile.getName()))
					return theFile;
				return null;
			}
		}

		static class GZipCompressedFile implements ArchiveEntry {
			private final String theName;
			private final long theModTime;

			GZipCompressedFile(String name, long modTime) {
				theName = name;
				theModTime = modTime;
			}

			@Override
			public String getName() {
				return theName;
			}

			@Override
			public long length() {
				return -1;
			}

			@Override
			public long getLastModified() {
				return theModTime;
			}

			@Override
			public boolean isDirectory() {
				return false;
			}

			@Override
			public List<? extends ArchiveEntry> listFiles() {
				return Collections.emptyList();
			}

			@Override
			public ArchiveEntry getFile(String name) {
				return null;
			}
		}
	}

	/**
	 * Allows interfacing with TAR files as a directory structure. Because there is no continuous directory structure record in a tar file,
	 * the entire file must be read to understand its structure. Once read, individual entries will remember their location in the file and
	 * can be accessed quickly if the wrapped file supports fast random seek.
	 */
	public static class TarArchival implements FileArchival {
		@Override
		public boolean detectPossibleCompressedFile(String fileName) {
			if (fileName.length() <= 4)
				return false;
			else if (fileName.charAt(fileName.length() - 4) != '.')
				return false;
			else
				return fileName.substring(fileName.length() - 3).equalsIgnoreCase("tar");
		}

		@Override
		public boolean detectCompressedFile(FileBacking file, BooleanSupplier canceled) throws IOException {
			long len = file.length();
			if (len >= 0 && len < 512) // Header is 512 bytes
				return false;
			try (InputStream in = file.read(0, canceled)) {
				if (in == null)
					return false;
				return new TarHeaderParser().parse(in, false) != null;
			}
		}

		static class TarHeader {
			final String name;
			final long size;
			final long lastModified;
			final byte fileType;

			TarHeader(String name, long size, long lastModified, byte fileType) {
				this.name = name;
				this.size = size;
				this.lastModified = lastModified;
				this.fileType = fileType;
			}

			@Override
			public String toString() {
				return name;
			}
		}

		static class TarHeaderParser {
			private final byte[] header;
			private final StringBuilder fileName;

			TarHeaderParser() {
				header = new byte[512];
				fileName = new StringBuilder();
			}

			TarHeader parse(InputStream in, boolean throwOnError) throws IOException {
				int read = in.read(header);
				if (read < 0) {
					if (throwOnError)
						throw new IOException("Could not read tar file header");
					else
						return null;
				}
				int buffered = read;
				while (read >= 0 && buffered < 512) {
					read = in.read(header, buffered, 512 - buffered);
					buffered += read;
				}
				if (read < 0) {
					if (throwOnError)
						throw new IOException("Could not read tar file header");
					else
						return null;
				}
				if (header[154] != 0 || header[155] != ' ') {
					boolean allEmpty = true;
					for (int i = 0; i < header.length; i++)
						if (header[i] != 0 && header[i] != ' ')
							allEmpty = false;
					if (allEmpty)
						return null;
					if (throwOnError)
						throw new IOException("Not a tar file");
					else
						return null;
				}
				long checkSum = 0;
				for (int i = 0; i < header.length; i++) {
					if (i >= 148 && i < 156)
						checkSum += ' ';
					else
						checkSum += unsigned(header[i]);
				}
				byte[] octalCheckSum = new byte[6];
				for (int i = 0; i < octalCheckSum.length; i++) {
					byte checkSumDigit = (byte) (checkSum % 8);
					if (('0' + checkSumDigit) != header[148 + 5 - i]) {
						if (throwOnError)
							throw new IOException("Not a tar file");
						else
							return null;
					}
					checkSum /= 8;
				}
				fileName.setLength(0);
				for (int i = 0; i < 100 && header[i] != 0; i++) {
					fileName.append((char) (header[i] & 0xff));
				}
				long size = 0;
				for (int i = 0; i < 11; i++) {
					if (header[124 + i] != ' ')
						size = size * 8 + header[124 + i] - '0';
				}
				long lastModified = 0;
				for (int i = 0; i < 11; i++) {
					if (header[136 + i] != ' ')
						lastModified = lastModified * 8 + header[136 + i] - '0';
				}
				byte fileType = header[156];
				return new TarHeader(fileName.toString(), size, lastModified * 1000, fileType);
			}
		}

		@Override
		public ArchiveEntry parseStructure(FileBacking file, ArchiveEntry existingRoot, Consumer<? super ArchiveEntry> onChild,
			ExBiConsumer<ArchiveEntry, CharSequence, IOException> forEach, BooleanSupplier canceled) throws IOException {
			long len = file.length();
			if (len >= 0 && len < 512) // Header is 512 bytes
				throw new IOException("Not a tar file");
			DefaultArchiveEntry root = new DefaultArchiveEntry("", 0, true);
			TarHeaderParser parser = new TarHeaderParser();
			int entryIndex = 0;
			Set<String> children = onChild != null ? new HashSet<>() : null;
			ByteArrayOutputStream longNameBuffer = new ByteArrayOutputStream();
			StringBuilder longName = new StringBuilder();
			try (InputStream fileIn = file.read(0, canceled); CountingInputStream in = new CountingInputStream(fileIn)) {
				if (fileIn == null)
					return null;
				while (true) {
					if (canceled.getAsBoolean())
						return null;
					long entryPos = in.getPosition();
					TarHeader header = parser.parse(in, true);
					if (header == null)
						break;
					boolean dir = header.fileType == '5' || header.name.charAt(header.name.length() - 1) == '/';
					boolean readLength = false;
					if (header.fileType == 'L') { // Long name entry
						longNameBuffer.reset();
						for (int i = 0, read = in.read(); i < header.size; i++) {
							if (read < 0)
								throw new IOException("Bad TAR header: EOF reached");
							longNameBuffer.write(read);
							if (i + 1 < header.size)
								read = in.read();
						}
						readLength = true;
					} else if (dir || header.fileType == 0 || header.fileType == '0') { // Don't deal with the wacky file types
						String name;
						if (longNameBuffer.size() > 0) {
							InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(longNameBuffer.toByteArray()));
							for (int read = reader.read(); read >= 0; read = reader.read())
								longName.append((char) read);
							name = longName.toString();
							longName.setLength(0);
						} else
							name = header.name;
						String[] path = FileUtils.splitPath(name);
						DefaultArchiveEntry newEntry = root.add(path, 0, entryPos + 512, dir).fill(header.size, header.lastModified);
						if (children != null && children.add(path[0])) {
							onChild.accept(root.getFile(path[0]));
						}
						if (forEach != null) {
							ArchiveEntry oldEntry = existingRoot != null ? ((DefaultArchiveEntry) existingRoot).at(path, 0) : null;
							forEach.accept(oldEntry != null ? oldEntry : newEntry, name);
						}
						longNameBuffer.reset();
					}
					if (header.size > 0) {
						if (canceled.getAsBoolean())
							return null;
						long toSkip = readLength ? 0 : header.size;
						int mod512 = (int) (header.size % 512);
						if (mod512 != 0)
							toSkip += (512 - mod512);
						long s = in.skip(toSkip);
						long skipped = s;
						while (s >= 0 && skipped < toSkip) {
							if (canceled.getAsBoolean())
								return null;
							s = in.skip(toSkip - skipped);
							skipped += s;
						}
						if (s < 0)
							throw new IOException("Could not read tar file entry #" + (entryIndex + 1));
					}
					entryIndex++;
				}
			}
			return root;
		}

		@Override
		public InputStream read(FileBacking file, ArchiveEntry entry, long startFrom, BooleanSupplier canceled) throws IOException {
			if (entry.isDirectory())
				throw new IOException(file.getName() + " is a directory");
			if (entry instanceof VisitingEntry) {
				InputStream in = ((VisitingEntry) entry).getIn();
				if (in != null)
					return in;
			}
			InputStream fileIn = null;
			long pos = ((DefaultArchiveEntry) entry).getPosition();
			long len = entry.length();
			boolean showFullEntry = false;
			if (showFullEntry) {
				pos -= 512;
				len += 512;
				long mod512 = len % 512;
				if (mod512 != 0)
					len += 512 - mod512;
			}
			if (entry instanceof DefaultArchiveEntry)
				fileIn = file.read(pos, canceled);
			else
				fileIn = file.read(((VisitingEntry) entry).getPosition(), canceled);
			return fileIn == null ? null : new CountingInputStream(fileIn, len);
		}

		@Override
		public StringBuilder alterUrl(StringBuilder url) {
			return url;
		}
	}

	private static int unsigned(byte b) {
		if (b < 0)
			return b + 256;
		else
			return b;
	}
}
package org.qommons.io;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.ex.ExBiConsumer;
import org.qommons.io.BetterFile.CheckSumType;
import org.qommons.io.BetterFile.FileBacking;
import org.qommons.io.BetterFile.FileBooleanAttribute;
import org.qommons.io.BetterFile.FileDataSource;
import org.qommons.io.FileUtils.DirectorySyncResults;

/** {@link FileDataSource} for the local native file system */
public class NativeFileSource implements BetterFile.FileDataSource {
	private final FileSystem theFileSystem;

	/** Creates the file source for the default file system */
	public NativeFileSource() {
		this(FileSystems.getDefault());
	}

	/** @param fileSystem The file system to create the file source for */
	public NativeFileSource(FileSystem fileSystem) {
		theFileSystem = fileSystem;
	}

	/** @return The file system backing this file source */
	public FileSystem getFileSystem() {
		return theFileSystem;
	}

	@Override
	public List<BetterFile.FileBacking> getRoots() {
		List<BetterFile.FileBacking> roots = new ArrayList<>();
		for (Path root : theFileSystem.getRootDirectories())
			roots.add(new NativeFileBacking(null, root));
		return Collections.unmodifiableList(roots);
	}

	@Override
	public FileBacking getRoot(String name) throws IllegalArgumentException {
		Path path = theFileSystem.getPath(name);
		if (!Files.exists(path))
			throw new IllegalArgumentException("No such file root: '" + name + "'");
		return new NativeFileBacking(null, path);
	}

	/**
	 * @param file The native file to convert
	 * @return The better file under this file source representing the given file
	 */
	public BetterFile toBetter(File file) {
		return BetterFile.at(this, file.getAbsolutePath());
	}

	@Override
	public String getUrlRoot() {
		return "file:///";
	}

	/**
	 * @param file The native file to convert
	 * @return The better file under the default native file source representing the given file
	 */
	public static BetterFile of(File file) {
		return new NativeFileSource().toBetter(file);
	}

	/**
	 * @param filePath The file path
	 * @return The better file under the default native file source representing the given file
	 */
	public static BetterFile of(String filePath) {
		return of(new File(filePath));
	}

	class NativeFileBacking implements BetterFile.FileBacking {
		@SuppressWarnings("unused")
		private final NativeFileBacking theParent;
		private final Path thePath;
		private final String theName;

		NativeFileBacking(NativeFileBacking parent, Path path) {
			theParent = parent;
			thePath = path;
			Path fileName = path.getFileName();
			String name = fileName != null ? fileName.toString() : path.toString();
			if (name.length() > 1 && (name.charAt(name.length() - 1) == '/' || name.charAt(name.length() - 1) == '\\'))
				name = name.substring(0, name.length() - 1);
			theName = name;
		}

		public Path getPath() {
			return thePath;
		}

		@Override
		public boolean check() {
			return true;
		}

		@Override
		public boolean isRoot() {
			return thePath.getNameCount() == 1;
		}

		@Override
		public boolean exists() {
			return Files.exists(thePath);
		}

		@Override
		public long getLastModified() {
			try {
				return Files.getLastModifiedTime(thePath).toMillis();
			} catch (IOException e) {
				return 0;
			}
		}

		@Override
		public boolean isDirectory() {
			return Files.isDirectory(thePath);
		}

		@Override
		public boolean isFile() {
			return !isDirectory();
		}

		@Override
		public boolean get(FileBooleanAttribute attribute) {
			switch (attribute) {
			case Hidden:
				try {
					return Files.isHidden(thePath);
				} catch (IOException e) {
					return false;
				}
			case Readable:
				return Files.isReadable(thePath);
			case Writable:
				return Files.isWritable(thePath);
			case Symbolic:
				return Files.isSymbolicLink(thePath);
			}
			throw new IllegalStateException("" + attribute);
		}

		@Override
		public long length() {
			try {
				return Files.size(thePath);
			} catch (IOException e) {
				return 0;
			}
		}

		@Override
		public String getCheckSum(CheckSumType type, BooleanSupplier canceled) throws IOException {
			return FileUtils.getCheckSum(() -> read(0, canceled), type, canceled);
		}

		@SuppressWarnings("resource")
		@Override
		public InputStream read(long startFrom, BooleanSupplier canceled) throws IOException {
			if (canceled.getAsBoolean())
				return null;
			InputStream stream = null;
			// More efficient to use RandomAccessFile if we can
			try {
				File file = thePath.toFile();
				RandomAccessFile raf = new RandomAccessFile(file, "r");
				raf.seek(startFrom);
				stream = new RandomAccessFileStream(raf);
			} catch (UnsupportedOperationException e) {
				// Do it the standard way then
			}
			if (stream == null) {
				stream = Files.newInputStream(thePath, StandardOpenOption.READ);
				if (startFrom > 0) {
					boolean success = false;
					try {
						long skipped = stream.skip(startFrom);
						long totalSkipped = skipped;
						while (skipped >= 0 && totalSkipped < startFrom) {
							skipped = stream.skip(startFrom - totalSkipped);
							totalSkipped += skipped;
						}
						if (skipped < 0) {
							throw new IOException("File is only " + Files.size(thePath) + " long, can't skip to " + startFrom);
						} else
							success = true;
					} finally {
						if (!success)
							stream.close();
					}
				}
			}
			return new BufferedInputStream(stream);
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public boolean discoverContents(Consumer<? super FileBacking> onDiscovered, BooleanSupplier canceled) {
			if (!Files.isDirectory(thePath))
				return true;
			boolean[] canceledB = new boolean[1];
			try {
				try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(thePath)) {
					dirStream.forEach(f -> {
						if (canceled.getAsBoolean()) {
							canceledB[0] = true;
							return;
						}
						onDiscovered.accept(new NativeFileBacking(this, f));
					});
				}
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
			return !canceledB[0];
		}

		@Override
		public BetterFile.FileBacking getChild(String fileName) {
			Path childPath = thePath.resolve(fileName);
			// There's a weird issue which I think is a java bug, but I'm not sure:
			// When using resolve on a root path, java doesn't add the backslash after the root name (e.g. C:),
			// so the child path ends up being something like "C:blah" with no separator, which doesn't work.
			if (needsTerribleResolveHack())
				childPath = Paths.get(thePath + File.separator, fileName);
			else
				childPath = thePath.resolve(fileName);
			return new NativeFileBacking(this, childPath);
		}

		private boolean needsTerribleResolveHack() {
			return theName.endsWith(":");
		}

		@Override
		public BetterFile.FileBacking createChild(String fileName, boolean directory) throws IOException {
			Path file = thePath.resolve(fileName);
			if (Files.exists(file)) {
				if (Files.isDirectory(file) != directory)
					throw new IOException(file + " already exists as a " + (directory ? "file" : "directory"));
				return new NativeFileBacking(this, file);
			}
			if (directory)
				Files.createDirectories(file);
			else
				Files.createFile(file);
			return new NativeFileBacking(this, file);
		}

		@Override
		public void delete(DirectorySyncResults results) throws IOException {
			if (!Files.exists(thePath))
				return;
			boolean dir = isDirectory();
			if (dir) {
				try {
					discoverContents(child -> {
						try {
							child.delete(results);
						} catch (IOException e) {
							throw new CheckedExceptionWrapper(e);
						}
					}, () -> false);
				} catch (CheckedExceptionWrapper e) {
					if (e.getCause() instanceof IOException)
						throw (IOException) e.getCause();
					else
						throw e;
				}
			}
			Files.delete(thePath);
			if (results != null)
				results.deleted(dir);
		}

		@Override
		public OutputStream write() throws IOException {
			return Files.newOutputStream(thePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
				StandardOpenOption.TRUNCATE_EXISTING);
		}

		@Override
		public boolean set(FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
			switch (attribute) {
			case Hidden:
				try {
					if (Files.isHidden(thePath) == value)
						return true;
				} catch (IOException e) {
					return false;
				}
				try {
					Files.setAttribute(thePath, "dos:hidden", true);
					return true;
				} catch (IOException e) {
					return false;
				}
			case Readable:
				if (Files.isReadable(thePath) == value)
					return true;
				return thePath.toFile().setReadable(value, ownerOnly);
			case Writable:
				return thePath.toFile().setWritable(value, ownerOnly);
			case Symbolic:
				return false;
			}
			throw new IllegalStateException("" + attribute);
		}

		@Override
		public boolean setLastModified(long lastModified) {
			try {
				Files.setLastModifiedTime(thePath, FileTime.fromMillis(lastModified));
				return true;
			} catch (IOException e) {
				return false;
			}
		}

		@Override
		public void move(List<String> newFilePath) throws IOException {
			String first = newFilePath.get(0);
			String[] more = new String[newFilePath.size() - 1];
			for (int i = 0; i < more.length; i++)
				more[i] = newFilePath.get(i + 1);
			Files.move(thePath, Paths.get(first, more));
		}

		@Override
		public void visitAll(ExBiConsumer<? super FileBacking, CharSequence, IOException> forEach, BooleanSupplier canceled)
			throws IOException {
			StringBuilder path = new StringBuilder();
			visitAll(path, forEach, canceled);
		}

		private void visitAll(StringBuilder path, ExBiConsumer<? super FileBacking, CharSequence, IOException> forEach,
			BooleanSupplier canceled) throws IOException {
			if (canceled.getAsBoolean())
				return;
			forEach.accept(this, path);
			if (!Files.exists(thePath))
				return;
			int oldLen = path.length();
			try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(thePath)) {
				for (Path f : dirStream) {
					if (canceled.getAsBoolean())
						return;
					path.append(getName()).append('/');
					new NativeFileBacking(this, f).visitAll(path, forEach, canceled);
					path.setLength(oldLen);
				}
			}
		}

		@Override
		public int hashCode() {
			return thePath.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof NativeFileBacking && thePath.equals(((NativeFileBacking) obj).thePath);
		}

		@Override
		public String toString() {
			return thePath.toString();
		}
	}

	/** A file stream wrapping a {@link RandomAccessFile} */
	public static class RandomAccessFileStream extends InputStream {
		private final RandomAccessFile theFile;
		private long theMark;

		/** @param file The {@link RandomAccessFile} to wrap */
		public RandomAccessFileStream(RandomAccessFile file) {
			theFile = file;
			theMark = -1;
		}

		@Override
		public int read() throws IOException {
			return theFile.read();
		}

		@Override
		public int read(byte[] b) throws IOException {
			return theFile.read(b);
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			return theFile.read(b, off, len);
		}

		@Override
		public long skip(long n) throws IOException {
			theFile.seek(theFile.getFilePointer() + n);
			return n;
		}

		@Override
		public int available() throws IOException {
			return 0;
		}

		@Override
		public void close() throws IOException {
			theFile.close();
		}

		@Override
		public synchronized void mark(int readlimit) {
			try {
				theMark = theFile.getFilePointer();
			} catch (IOException e) {
				// The contract of this method doesn't allow us to throw anything
				theMark = -1;
			}
		}

		@Override
		public synchronized void reset() throws IOException {
			if (theMark < 0)
				throw new IOException("No mark set");
			theFile.seek(theMark);
		}

		@Override
		public boolean markSupported() {
			return true;
		}

		@Override
		public int hashCode() {
			return theFile.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof RandomAccessFileStream && theFile.equals(((RandomAccessFileStream) obj).theFile);
		}

		@Override
		public String toString() {
			return theFile.toString();
		}
	}
}

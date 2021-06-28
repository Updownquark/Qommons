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

import org.qommons.ex.ExBiConsumer;
import org.qommons.io.BetterFile.FileBacking;
import org.qommons.io.BetterFile.FileBooleanAttribute;
import org.qommons.io.FileUtils.DirectorySyncResults;

public class NativeFileSource implements BetterFile.FileDataSource {
	private final FileSystem theFileSystem;

	public NativeFileSource() {
		this(FileSystems.getDefault());
	}

	public NativeFileSource(FileSystem fileSystem) {
		theFileSystem = fileSystem;
	}

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

	public BetterFile toBetter(File file) {
		return BetterFile.at(this, file.getAbsolutePath());
	}

	@Override
	public String getUrlRoot() {
		return "file:///";
	}

	public static BetterFile of(File file) {
		return new NativeFileSource().toBetter(file);
	}

	public static BetterFile of(String filePath) {
		return of(new File(filePath));
	}

	class NativeFileBacking implements BetterFile.FileBacking {
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
		public boolean get(FileBooleanAttribute attribute) {
			switch (attribute) {
			case Directory:
				return Files.isDirectory(thePath);
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
			return new NativeFileBacking(this, thePath.resolve(fileName));
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
			Files.deleteIfExists(thePath);
		}

		@Override
		public OutputStream write() throws IOException {
			return Files.newOutputStream(thePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
				StandardOpenOption.TRUNCATE_EXISTING);
		}

		@Override
		public boolean set(FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
			switch (attribute) {
			case Directory:
				if (Files.exists(thePath))
					return value == Files.isDirectory(thePath);
				else if (value) {
					try {
						Files.createDirectories(thePath);
						return true;
					} catch (IOException e) {
						return false;
					}
				} else {
					try {
						Files.createFile(thePath);
						return true;
					} catch (IOException e) {
						return false;
					}
				}
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
		public void move(String newFilePath) throws IOException {
			Files.move(thePath, Paths.get(newFilePath));
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

	public static class RandomAccessFileStream extends InputStream {
		private final RandomAccessFile theFile;
		private long theMark;

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

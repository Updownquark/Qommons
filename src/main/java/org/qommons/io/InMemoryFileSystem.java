package org.qommons.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.qommons.StringUtils;
import org.qommons.ex.ExBiConsumer;
import org.qommons.io.BetterFile.CheckSumType;
import org.qommons.io.BetterFile.FileBacking;
import org.qommons.io.BetterFile.FileBooleanAttribute;
import org.qommons.io.FileUtils.DirectorySyncResults;

/** A {@link BetterFile} system consisting of in-memory files and directories */
public class InMemoryFileSystem implements BetterFile.FileDataSource {
	private final MemoryDirectory theRoot = new MemoryDirectory(null, "/");

	@Override
	public String getUrlRoot() {
		return "memory:///";
	}

	@Override
	public List<BetterFile.FileBacking> getRoots() {
		return Arrays.asList(theRoot.getReference());
	}

	@Override
	public FileBacking getRoot(String name) throws IllegalArgumentException {
		if (name.equals("/"))
			return theRoot.getReference();
		throw new IllegalArgumentException("No such root: '" + name + "'");
	}

	private static class MemoryFileReference implements BetterFile.FileBacking {
		private final MemoryFileReference theParent;
		private final String theName;
		private AbstractMemoryFileBacking theExistingFile;

		public MemoryFileReference(MemoryFileReference parent, String name, AbstractMemoryFileBacking existingFile) {
			theParent = parent;
			theName = name;
			theExistingFile = existingFile;
		}

		AbstractMemoryFileBacking getRealFile() {
			if (theExistingFile != null)
				theExistingFile = theExistingFile.getRealFile();
			else {
				AbstractMemoryFileBacking parent = theParent.getRealFile();
				if (parent == null || !(parent instanceof MemoryDirectory))
					return null;
				else
					theExistingFile = ((MemoryDirectory) parent).theChildren.get(theName);
			}
			return theExistingFile;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public boolean check() {
			getRealFile();
			return true;
		}

		@Override
		public boolean isRoot() {
			return theParent == null;
		}

		@Override
		public boolean exists() {
			return getRealFile() != null;
		}

		@Override
		public long getLastModified() {
			AbstractMemoryFileBacking real = getRealFile();
			if (real == null)
				return -1;
			return real.getLastModified();
		}

		@Override
		public boolean isDirectory() {
			AbstractMemoryFileBacking real = getRealFile();
			if (real == null)
				return false;
			return real.isDirectory();
		}

		@Override
		public boolean isFile() {
			AbstractMemoryFileBacking real = getRealFile();
			if (real == null)
				return false;
			return real.isFile();
		}

		@Override
		public boolean get(FileBooleanAttribute attribute) {
			AbstractMemoryFileBacking real = getRealFile();
			if (real == null)
				return false;
			return real.get(attribute);
		}

		@Override
		public boolean discoverContents(Consumer<? super FileBacking> onDiscovered, BooleanSupplier canceled) {
			AbstractMemoryFileBacking real = getRealFile();
			if (real == null)
				return true;
			return real.discoverContents(onDiscovered, canceled);
		}

		@Override
		public MemoryFileReference getChild(String fileName) {
			AbstractMemoryFileBacking real = getRealFile();
			if (real != null && !(real instanceof MemoryDirectory))
				throw new IllegalStateException("Not a directory: " + getName());
			return new MemoryFileReference(this, fileName, real == null ? null : ((MemoryDirectory) real).theChildren.get(fileName));
		}

		@Override
		public long length() {
			AbstractMemoryFileBacking real = getRealFile();
			if (real == null)
				return 0;
			return real.length();
		}

		@Override
		public String getCheckSum(CheckSumType type, BooleanSupplier canceled) throws IOException {
			AbstractMemoryFileBacking real = getRealFile();
			if (real == null)
				throw new FileNotFoundException("No such file: " + getName());
			return real.getCheckSum(type, canceled);
		}

		@Override
		public InputStream read(long startFrom, BooleanSupplier canceled) throws IOException {
			AbstractMemoryFileBacking real = getRealFile();
			if (real == null)
				throw new FileNotFoundException("No such file: " + getName());
			return real.read(startFrom, canceled);
		}

		@Override
		public MemoryFileReference createChild(String fileName, boolean directory) throws IOException {
			AbstractMemoryFileBacking real = getRealFile();
			if (real == null)
				real = theParent.createChild(theName, true).theExistingFile;
			return new MemoryFileReference(this, fileName, real.createChild(fileName, directory));
		}

		@Override
		public void delete(DirectorySyncResults results) throws IOException {
			AbstractMemoryFileBacking real = getRealFile();
			if (real == null)
				return;
			real.delete(results);
		}

		@Override
		public OutputStream write() throws IOException {
			AbstractMemoryFileBacking real = getRealFile();
			if (real == null)
				real = theParent.createChild(theName, false).theExistingFile;
			return real.write();
		}

		@Override
		public boolean set(FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
			AbstractMemoryFileBacking real = getRealFile();
			if (real == null)
				return false;
			return real.set(attribute, value, ownerOnly);
		}

		@Override
		public boolean setLastModified(long lastModified) {
			AbstractMemoryFileBacking real = getRealFile();
			if (real == null)
				return false;
			return real.setLastModified(lastModified);
		}

		@Override
		public void move(List<String> newFilePath) throws IOException {
			AbstractMemoryFileBacking real = getRealFile();
			if (real == null)
				throw new FileNotFoundException("No such file: " + getName());
			real.move(newFilePath);
			theExistingFile = null;
		}

		@Override
		public void visitAll(ExBiConsumer<? super FileBacking, CharSequence, IOException> forEach, BooleanSupplier canceled)
			throws IOException {
			AbstractMemoryFileBacking real = getRealFile();
			if (real != null)
				real.visitAll(forEach, canceled);
		}

		@Override
		public String toString() {
			return theName;
		}
	}

	private static abstract class AbstractMemoryFileBacking implements BetterFile.FileBacking {
		private MemoryDirectory theParent;
		private String theName;
		private final MemoryFileReference theReference;

		public AbstractMemoryFileBacking(MemoryDirectory parent, String name) {
			theParent = parent;
			theName = name;
			theReference = new MemoryFileReference(//
				parent == null ? null : parent.getReference(), name, this);
		}

		AbstractMemoryFileBacking getRealFile() {
			if (theParent == null)
				return this;
			AbstractMemoryFileBacking parent = theParent.getRealFile();
			if (parent != theParent) {
				if (!(parent instanceof MemoryDirectory))
					return null;
				theParent = (MemoryDirectory) parent;
			}
			return theParent.theChildren.get(theName);
		}

		MemoryDirectory getRoot() {
			if (theParent == null)
				return (MemoryDirectory) this;
			else
				return theParent.getRoot();
		}

		public MemoryFileReference getReference() {
			return theReference;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public boolean check() {
			return true;
		}

		@Override
		public boolean isRoot() {
			return theParent == null;
		}

		@Override
		public boolean exists() {
			return true;
		}

		@Override
		public abstract AbstractMemoryFileBacking createChild(String fileName, boolean directory) throws IOException;

		@Override
		public synchronized void delete(DirectorySyncResults results) throws IOException {
			if (theParent == null)
				throw new IOException("Cannot delete '/'");
			theParent.theChildren.remove(theName);
		}

		@Override
		public void move(List<String> newFilePath) throws IOException {
			if (theParent == null)
				throw new IOException("Cannot move '/'");
			getRoot().create(newFilePath, 1, this);
			theParent.theChildren.remove(theName);
		}
	}

	private static class MemoryDirectory extends AbstractMemoryFileBacking {
		private final Map<String, AbstractMemoryFileBacking> theChildren;

		public MemoryDirectory(MemoryDirectory parent, String name) {
			super(parent, name);
			theChildren = new TreeMap<>(StringUtils.DISTINCT_NUMBER_TOLERANT);
		}

		public MemoryDirectory(MemoryDirectory parent, String name, MemoryDirectory source) {
			this(parent, name);
			for (AbstractMemoryFileBacking child : source.theChildren.values()) {
				if (child instanceof MemoryDirectory)
					theChildren.put(child.getName(), new MemoryDirectory(this, child.getName(), (MemoryDirectory) child));
				else
					theChildren.put(child.getName(), new MemoryFile(this, child.getName(), (MemoryFile) child));
			}
		}

		@Override
		public long getLastModified() {
			return 0;
		}

		@Override
		public boolean isDirectory() {
			return true;
		}

		@Override
		public boolean isFile() {
			return false;
		}

		@Override
		public boolean get(FileBooleanAttribute attribute) {
			switch (attribute) {
			case Hidden:
				return false;
			case Readable:
				return true;
			case Symbolic:
				return false;
			case Writable:
				return true;
			default:
				throw new IllegalStateException("Unrecognized file attribute: " + attribute);
			}
		}

		@Override
		public boolean discoverContents(Consumer<? super FileBacking> onDiscovered, BooleanSupplier canceled) {
			for (AbstractMemoryFileBacking file : theChildren.values()) {
				if (canceled.getAsBoolean())
					return false;
				onDiscovered.accept(file.theReference);
			}
			return true;
		}

		@Override
		public FileBacking getChild(String fileName) {
			return new MemoryFileReference(getReference(), fileName, theChildren.get(fileName));
		}

		@Override
		public long length() {
			return 0;
		}

		@Override
		public String getCheckSum(CheckSumType type, BooleanSupplier canceled) throws IOException {
			throw new IOException("Checksum not supported for directories: " + getName());
		}

		@Override
		public InputStream read(long startFrom, BooleanSupplier canceled) throws IOException {
			throw new IOException("This is a directory: " + getName());
		}

		@Override
		public OutputStream write() throws IOException {
			throw new IOException("This is a directory: " + getName());
		}

		@Override
		public boolean set(FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
			return false;
		}

		@Override
		public boolean setLastModified(long lastModified) {
			return false;
		}

		@Override
		public synchronized void visitAll(ExBiConsumer<? super FileBacking, CharSequence, IOException> forEach, BooleanSupplier canceled)
			throws IOException {
			_visitAll(new StringBuilder(), forEach, canceled);
		}

		private void _visitAll(StringBuilder path, ExBiConsumer<? super FileBacking, CharSequence, IOException> forEach,
			BooleanSupplier canceled) throws IOException {
			int pathLen = path.length();
			forEach.accept(getReference(), path);
			for (AbstractMemoryFileBacking file : theChildren.values()) {
				if (canceled.getAsBoolean())
					return;
				forEach.accept(file.getReference(), path);
				if (file instanceof MemoryDirectory) {
					path.append(file.getName()).append('/');
					((MemoryDirectory) file)._visitAll(path, forEach, canceled);
					path.setLength(pathLen);
				} else
					forEach.accept(file.getReference(), path);
			}
		}

		@Override
		public synchronized AbstractMemoryFileBacking createChild(String fileName, boolean directory) throws IOException {
			AbstractMemoryFileBacking file = theChildren.get(fileName);
			if (file != null)
				throw new IOException("File '" + fileName + "' already exists");
			AbstractMemoryFileBacking newFile = directory ? new MemoryDirectory(this, fileName) : new MemoryFile(this, fileName);
			theChildren.put(fileName, newFile);
			return newFile;
		}

		void create(List<String> path, int pathIndex, AbstractMemoryFileBacking source) throws IOException {
			String name = path.get(pathIndex);
			if (pathIndex == path.size() - 1) {
				if (theChildren.get(name) != null)
					throw new IOException(String.join("/", path) + " already exists");
				if (source instanceof MemoryDirectory)
					theChildren.put(name, new MemoryDirectory(this, name, (MemoryDirectory) source));
				else
					theChildren.put(name, new MemoryFile(this, name, (MemoryFile) source));
			} else {
				AbstractMemoryFileBacking child = theChildren.get(name);
				if (child == null) {
					child = new MemoryDirectory(this, name);
					theChildren.put(name, child);
				} else if (!(child instanceof MemoryDirectory))
					throw new IOException(String.join("/", path.subList(0, pathIndex + 1)) + " is not a directory");
				((MemoryDirectory) child).create(path, pathIndex + 1, source);
			}
		}

		@Override
		public String toString() {
			return getName() + "/";
		}
	}

	private static class MemoryFile extends AbstractMemoryFileBacking {
		private final CircularByteBuffer theContent;
		private long theLastModified;

		MemoryFile(MemoryDirectory parent, String name) {
			super(parent, name);
			theContent = new CircularByteBuffer(1024);
			theLastModified = System.currentTimeMillis();
		}

		MemoryFile(MemoryDirectory parent, String name, MemoryFile source) {
			super(parent, name);
			theContent = source.theContent;
			theLastModified = source.theLastModified;
		}

		@Override
		public long getLastModified() {
			return theLastModified;
		}

		@Override
		public boolean isDirectory() {
			return false;
		}

		@Override
		public boolean isFile() {
			return true;
		}

		@Override
		public boolean get(FileBooleanAttribute attribute) {
			switch (attribute) {
			case Hidden:
				return false;
			case Readable:
				return true;
			case Symbolic:
				return false;
			case Writable:
				return true;
			default:
				throw new IllegalStateException("Unrecognized file attribute: " + attribute);
			}
		}

		@Override
		public boolean discoverContents(Consumer<? super FileBacking> onDiscovered, BooleanSupplier canceled) {
			return true;
		}

		@Override
		public FileBacking getChild(String fileName) {
			return new MemoryFileReference(getReference(), fileName, null);
		}

		@Override
		public long length() {
			return theContent.length();
		}

		@Override
		public String getCheckSum(CheckSumType type, BooleanSupplier canceled) throws IOException {
			return FileUtils.getCheckSum(() -> read(0, canceled), type, canceled);
		}

		@Override
		public InputStream read(long startFrom, BooleanSupplier canceled) throws IOException {
			InputStream stream = theContent.asInputStream();
			if (startFrom > 0 && stream.skip(startFrom) < startFrom)
				throw new IOException("Cannot start at " + startFrom + "--only " + theContent.length());
			return stream;
		}

		@Override
		public OutputStream write() throws IOException {
			theContent.clear(false);
			OutputStream contentOS = theContent.asOutputStream();
			return new OutputStream() {
				@Override
				public void write(int b) throws IOException {
					contentOS.write(b);
					theLastModified = System.currentTimeMillis();
				}

				@Override
				public void write(byte[] b) throws IOException {
					contentOS.write(b);
					theLastModified = System.currentTimeMillis();
				}

				@Override
				public void write(byte[] b, int off, int len) throws IOException {
					contentOS.write(b, off, len);
					theLastModified = System.currentTimeMillis();
				}
			};
		}

		@Override
		public boolean set(FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
			return false;
		}

		@Override
		public boolean setLastModified(long lastModified) {
			theLastModified = lastModified;
			return true;
		}

		@Override
		public void visitAll(ExBiConsumer<? super FileBacking, CharSequence, IOException> forEach, BooleanSupplier canceled)
			throws IOException {
			if(!canceled.getAsBoolean())
				forEach.accept(this, getName());
		}

		@Override
		public AbstractMemoryFileBacking createChild(String fileName, boolean directory) throws IOException {
			throw new IOException("File is not a directory: " + getName());
		}

		@Override
		public String toString() {
			return getName();
		}
	}
}

package org.qommons.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.qommons.Named;
import org.qommons.ex.ExBiConsumer;
import org.qommons.ex.ExConsumer;
import org.qommons.io.FileUtils.DirectorySyncResults;

public interface BetterFile extends Named {
	enum FileBooleanAttribute {
		Readable, Writable, Directory, Hidden, Symbolic
	}

	class FileFormat implements Format<BetterFile> {
		private final BetterFile.FileDataSource theFileSource;
		private final BetterFile theWorkingDir;
		private final boolean allowNull;
	
		public FileFormat(BetterFile.FileDataSource fileSource, BetterFile workingDir, boolean allowNull) {
			theFileSource = fileSource;
			theWorkingDir = workingDir;
			this.allowNull = allowNull;
		}
	
		public BetterFile getWorkingDir() {
			return theWorkingDir;
		}
	
		@Override
		public void append(StringBuilder text, BetterFile value) {
			if (value != null)
				text.append(value);
		}
	
		@Override
		public BetterFile parse(CharSequence text) throws ParseException {
			if (text.length() == 0) {
				if (allowNull)
					return null;
				else
					throw new ParseException("Empty content not allowed", 0);
			} else {
				try {
					return BetterFile.at(theFileSource, text.toString());
				} catch (IllegalArgumentException e) {
					if (theWorkingDir != null)
						return theWorkingDir.at(text.toString());
					throw e;
				}
			}
		}
	}

	/** @return The path from the file root to this file */
	String getPath();

	/** @return Whether this file currently exists in the data source */
	boolean exists();

	/** @return The time (millis since epoch) at which this file is marked as having last been modified */
	long getLastModified();

	/** @return Whether this file represents a directory potentially containing other file structures */
	default boolean isDirectory() {
		return get(BetterFile.FileBooleanAttribute.Directory);
	}

	boolean get(BetterFile.FileBooleanAttribute attribute);

	/** @return The number of bytes in this file; 0 if it is not a valid, readable file; or -1 if the length cannot be quickly accessed */
	long length();

	BetterFile getRoot();

	BetterFile getParent();

	BetterFile at(String path);

	List<? extends BetterFile> discoverContents(Consumer<? super BetterFile> onDiscovered, BooleanSupplier canceled);

	/** @return The files contained in this directory, or null if this is not a directory */
	default List<? extends BetterFile> listFiles() {
		return discoverContents(null, null);
	}

	/**
	 * @param startFrom The byte index to start reading from
	 * @return A stream to read this file's content
	 * @throws IOException If the content could not be accessed
	 */
	InputStream read(long startFrom, BooleanSupplier canceled) throws IOException;

	/**
	 * @return A stream to read this file's content
	 * @throws IOException If the content could not be accessed
	 */
	default InputStream read() throws IOException {
		return read(0L, null);
	}

	/**
	 * Attempts to delete the file and all its sub-content, if any
	 * 
	 * @param results The results to update with the result of this operation
	 * @throws IOException If this file could not be deleted
	 */
	void delete(DirectorySyncResults results) throws IOException;

	/**
	 * Attempts to create this resource as a file or a directory (and any necessary parent directories)
	 * 
	 * @param directory Whether to create a directory or a file
	 * @return This resource
	 * @throws IOException If the file or directory could not be created
	 */
	BetterFile create(boolean directory) throws IOException;

	/**
	 * @return A stream to write data into this resource
	 * @throws IOException If the data could not be accessed for write
	 */
	OutputStream write() throws IOException;

	boolean set(BetterFile.FileBooleanAttribute attribute, boolean value, boolean ownerOnly);

	/**
	 * @param lastModified The time (millis since epoch) to mark this file as having been modified
	 * @return Whether the mark succeeded
	 */
	boolean setLastModified(long lastModified);

	/**
	 * Attempts to move and/or rename this file
	 * 
	 * @param newFile The new name and location for this file
	 * @return The new file
	 * @throws IOException If the move did not succeed
	 */
	BetterFile move(BetterFile newFile) throws IOException;

	void visitAll(ExConsumer<? super BetterFile, IOException> forEach, BooleanSupplier canceled) throws IOException;

	default BetterFile unmodifiable() {
		return new UnmodifiableFile(this);
	}

	/**
	 * @param childFilter The filter to apply
	 * @return A file identical to this file but whose {@link #listFiles() file list} will exclude files not passing the given filter
	 */
	default BetterFile filterContent(Predicate<? super BetterFile> childFilter) {
		return new FilteredFile(this, childFilter);
	}

	static List<BetterFile> getRoots(FileDataSource dataSource) {
		List<FileBacking> backing = dataSource.getRoots();
		List<BetterFile> roots = new ArrayList<>(backing.size());
		for (int i = 0; i < backing.size(); i++)
			roots.add(new FileRoot(dataSource, i));
		return Collections.unmodifiableList(roots);
	}

	static BetterFile at(FileDataSource dataSource, String path) {
		StringBuilder name = new StringBuilder();
		AbstractWrappingFile parent = null;
		List<FileBacking> roots = dataSource.getRoots();
		for (int c = 0; c < path.length(); c++) {
			if (path.charAt(c) == '/' || path.charAt(c) == '\\') {
				if (c == 0) {//
					continue;
				} else if (name.length() == 0)
					throw new IllegalArgumentException("Illegal path: " + path);
				if (parent == null) {
					String rootName = name.toString();
					for (int r = 0; r < roots.size(); r++) {
						if (roots.get(r).getName().equals(rootName)) {
							parent = new FileRoot(dataSource, r);
							break;
						}
					}
					if (parent == null)
						throw new IllegalArgumentException("No such root: " + rootName);
				} else
					parent = parent.createChild(name.toString(), null);
				name.setLength(0);
			} else
				name.append(path.charAt(c));
		}
		if (name.length() > 0) {
			if (parent == null) {
				String rootName = name.toString();
				for (int r = 0; r < roots.size(); r++) {
					if (roots.get(r).getName().equals(rootName)) {
						parent = new FileRoot(dataSource, r);
						break;
					}
				}
				if (parent == null)
					throw new IllegalArgumentException("No such root: " + rootName);
			} else
				parent = parent.createChild(name.toString(), null);
		}
		return parent;
	}

	public interface FileBacking extends Named {
		/**
		 * Performs a check on this file to see if it may have changed.
		 * 
		 * @return True if this backing is still consistent with the file, or false if the backing needs to be regenerated
		 */
		boolean check();

		boolean isRoot();

		boolean exists();

		long getLastModified();

		boolean get(FileBooleanAttribute attribute);

		boolean discoverContents(Consumer<? super FileBacking> onDiscovered, BooleanSupplier canceled);

		FileBacking getChild(String fileName);

		long length();

		InputStream read(long startFrom, BooleanSupplier canceled) throws IOException;

		FileBacking createChild(String fileName, boolean directory) throws IOException;

		void delete(DirectorySyncResults results) throws IOException;

		OutputStream write() throws IOException;

		boolean set(BetterFile.FileBooleanAttribute attribute, boolean value, boolean ownerOnly);

		boolean setLastModified(long lastModified);

		void move(String newFilePath) throws IOException;

		void visitAll(ExBiConsumer<? super FileBacking, CharSequence, IOException> forEach, BooleanSupplier canceled) throws IOException;
	}

	public interface FileDataSource {
		List<FileBacking> getRoots();
	}

	public abstract class AbstractWrappingFile implements BetterFile {
		protected volatile FileBacking theBacking;
	
		protected abstract FileBacking findBacking();
	
		protected abstract FileBacking createBacking(boolean directory) throws IOException;

		protected FileBacking check() {
			FileBacking backing = theBacking;
			if (backing == null) {//
				backing = findBacking();
			} else if (!backing.check()) {
				backing.check();
				backing = findBacking();
			}
			theBacking = backing;
			return backing;
		}
	
		protected AbstractWrappingFile createChild(String name, FileBacking backing) {
			return new BetterFile.FileWrapper(this, name.toString(), backing);
		}
	
		@Override
		public abstract FileRoot getRoot();
	
		@Override
		public abstract AbstractWrappingFile getParent();
	
		@Override
		public String getPath() {
			LinkedList<String> path = new LinkedList<>();
			AbstractWrappingFile file = this;
			while (file != null) {
				path.addFirst(file.getName());
				if (file.check() == null)
					file = file.getParent();
				else
					file = null;
			}
	
			StringBuilder str = new StringBuilder();
			for (String p : path) {
				str.append(p);
				if (!p.equals("/"))
					str.append('/');
			}
			str.deleteCharAt(str.length() - 1);
			return str.toString();
		}
	
		@Override
		public AbstractWrappingFile at(String path) {
			StringBuilder name = new StringBuilder();
			AbstractWrappingFile parent = this;
			for (int c = 0; c < path.length(); c++) {
				if (path.charAt(c) == '/' || path.charAt(c) == '\\') {
					if (c == 0) {
						parent = getRoot();
						continue;
					} else if (name.length() == 0)
						throw new IllegalArgumentException("Illegal path: " + path);
					parent = parent.createChild(name.toString(), null);
					name.setLength(0);
				} else
					name.append(path.charAt(c));
			}
			if (name.length() > 0)
				parent = parent.createChild(name.toString(), null);
			return parent;
		}
	
		@Override
		public boolean exists() {
			FileBacking backing = check();
			return backing != null && backing.exists();
		}
	
		@Override
		public long getLastModified() {
			FileBacking backing = check();
			return backing == null ? 0 : backing.getLastModified();
		}
	
		@Override
		public boolean get(FileBooleanAttribute attribute) {
			FileBacking backing = check();
			return backing != null && backing.get(attribute);
		}
	
		@Override
		public List<? extends BetterFile> discoverContents(Consumer<? super BetterFile> onDiscovered, BooleanSupplier canceled) {
			FileBacking backing = check();
			if (backing == null)
				return Collections.emptyList();
			List<BetterFile> list = new ArrayList<>();
			if (!backing.discoverContents(b -> {
				BetterFile file = createChild(b.getName(), b);
				list.add(file);
				if (onDiscovered != null)
					onDiscovered.accept(file);
			}, canceled != null ? canceled : () -> false))
				return null;
			return Collections.unmodifiableList(list);
		}

		@Override
		public long length() {
			FileBacking backing = check();
			return backing == null ? 0 : backing.length();
		}
	
		@Override
		public InputStream read(long startFrom, BooleanSupplier canceled) throws IOException {
			FileBacking backing = check();
			if (backing == null)
				throw new FileNotFoundException(getPath() + " not found");
			return backing.read(startFrom, canceled != null ? canceled : () -> false);
		}

		@Override
		public void delete(DirectorySyncResults results) throws IOException {
			FileBacking backing = check();
			if (backing == null)
				return;
			backing.delete(results);
		}

		@Override
		public BetterFile create(boolean directory) throws IOException {
			theBacking = createBacking(directory);
			return this;
		}

		@Override
		public OutputStream write() throws IOException {
			FileBacking backing = createBacking(false);
			theBacking = backing;
			return backing.write();
		}

		@Override
		public boolean set(BetterFile.FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
			FileBacking backing = check();
			return backing == null ? false : backing.set(attribute, value, ownerOnly);
		}
	
		@Override
		public boolean setLastModified(long lastModified) {
			FileBacking backing = check();
			if (backing == null)
				return false;
			if (backing.getLastModified() == lastModified)
				return true;
			return backing.setLastModified(lastModified);
		}

		@Override
		public BetterFile move(BetterFile newFile) throws IOException {
			if (getPath().equals(newFile))
				return this;
			FileBacking backing = check();
			if (backing == null)
				return null;
			backing.move(newFile.getPath());
			return at(newFile.getPath());
		}

		@Override
		public void visitAll(ExConsumer<? super BetterFile, IOException> forEach, BooleanSupplier canceled) throws IOException {
			FileBacking backing = check();
			if (backing == null) {
				forEach.accept(this);
				return;
			}
			backing.visitAll((f, path) -> {
				AbstractWrappingFile parent = path.length() > 0 ? at(path.toString()) : this;
				forEach.accept(parent.createChild(f.getName(), f));
			}, canceled != null ? canceled : () -> false);
		}

		@Override
		public int hashCode() {
			AbstractWrappingFile parent = getParent();
			int hash = parent == null ? 0 : parent.hashCode() * 7;
			hash += getName().hashCode();
			return hash;
		}
	
		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof BetterFile))
				return false;
			BetterFile other = (BetterFile) obj;
			return getName().equals(other.getName()) && Objects.equals(getParent(), other.getParent());
		}
	
		@Override
		public String toString() {
			return getPath();
		}
	}

	public class FileRoot extends BetterFile.AbstractWrappingFile {
		private final BetterFile.FileDataSource theDataSource;
		private final int theRootIndex;

		public FileRoot(BetterFile.FileDataSource dataSource, int rootIndex) {
			theDataSource = dataSource;
			theRootIndex = rootIndex;
		}

		protected BetterFile.FileDataSource getDataSource() {
			return theDataSource;
		}

		@Override
		protected FileBacking check() {
			FileBacking backing = super.check();
			if (backing == null)
				throw new IllegalStateException("Root should never not resolve");
			return backing;
		}

		@Override
		public String getName() {
			return check().getName();
		}

		@Override
		protected FileBacking findBacking() {
			return theDataSource.getRoots().get(theRootIndex);
		}

		@Override
		protected FileBacking createBacking(boolean directory) throws IOException {
			if (!directory)
				throw new IllegalArgumentException("A root cannot be anything but a directory");
			FileBacking backing = check();
			if (backing == null)
				throw new IllegalStateException("Root should never not resolve");
			return backing;
		}

		@Override
		public FileRoot getRoot() {
			return this;
		}

		@Override
		public BetterFile.AbstractWrappingFile getParent() {
			return null;
		}
	}

	public class FileWrapper extends AbstractWrappingFile {
		private final AbstractWrappingFile theParent;
		private final String theName;
	
		public FileWrapper(AbstractWrappingFile parent, String name, FileBacking backing) {
			if (parent == null)
				throw new IllegalStateException("This implementation cannot be used for a root");
			theParent = parent;
			theName = name;
			theBacking = backing;
		}
	
		@Override
		protected FileBacking findBacking() {
			FileBacking parentBacking = theParent.check();
			return parentBacking == null ? null : parentBacking.getChild(theName);
		}

		@Override
		protected FileBacking createBacking(boolean directory) throws IOException {
			FileBacking backing = findBacking();
			if (backing != null && backing.exists()) {
				if (backing.get(BetterFile.FileBooleanAttribute.Directory) != directory)
					throw new IOException(getPath() + " already exists as a " + (directory ? "file" : "directory"));
			}
			FileBacking parentBacking = theParent.check();
			if (parentBacking == null)
				parentBacking = theParent.createBacking(true);
			else if (!parentBacking.get(BetterFile.FileBooleanAttribute.Directory))
				throw new IOException("Cannot create a child of a non-directory file " + theParent);
			return parentBacking.createChild(theName, directory);
		}

		@Override
		public String getName() {
			return theName;
		}
	
		@Override
		public FileRoot getRoot() {
			AbstractWrappingFile parent = theParent;
			while (parent.getParent() != null)
				parent = parent.getParent();
			return (FileRoot) parent;
		}
	
		@Override
		public AbstractWrappingFile getParent() {
			return theParent;
		}
	}

	class FilteredFile implements BetterFile {
		private final BetterFile theSource;
		private final Predicate<? super BetterFile> theFilter;
	
		FilteredFile(BetterFile source, Predicate<? super BetterFile> filter) {
			theSource = source;
			theFilter = filter;
		}
	
		@Override
		public BetterFile getRoot() {
			return theSource.getRoot();
		}
	
		@Override
		public String getName() {
			return theSource.getName();
		}
	
		@Override
		public String getPath() {
			return theSource.getPath();
		}

		@Override
		public BetterFile getParent() {
			return theSource.getParent();
		}
	
		@Override
		public boolean exists() {
			return theSource.exists();
		}
	
		@Override
		public long getLastModified() {
			return theSource.getLastModified();
		}
	
		@Override
		public boolean isDirectory() {
			return theSource.isDirectory();
		}
	
		@Override
		public List<? extends BetterFile> discoverContents(Consumer<? super BetterFile> onDiscovered, BooleanSupplier canceled) {
			List<BetterFile> files = new ArrayList<>();
			if (theSource.discoverContents(file -> {
				if (theFilter.test(file)) {
					FilteredFile filteredFile = new FilteredFile(file, theFilter);
					files.add(filteredFile);
					if (onDiscovered != null)
						onDiscovered.accept(filteredFile);
				}
			}, canceled) == null)
				return null;
			return Collections.unmodifiableList(files);
		}

		@Override
		public long length() {
			return theSource.length();
		}
	
		@Override
		public boolean get(FileBooleanAttribute attribute) {
			return theSource.get(attribute);
		}
	
		@Override
		public InputStream read(long startFrom, BooleanSupplier canceled) throws IOException {
			return theSource.read(startFrom, canceled);
		}
	
		@Override
		public BetterFile at(String path) {
			return theSource.at(path);
		}

		@Override
		public void delete(DirectorySyncResults results) throws IOException {
			theSource.delete(results);
		}

		@Override
		public BetterFile create(boolean directory) throws IOException {
			theSource.create(directory);
			return this;
		}

		@Override
		public OutputStream write() throws IOException {
			return theSource.write();
		}

		@Override
		public boolean set(FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
			return theSource.set(attribute, value, ownerOnly);
		}

		@Override
		public boolean setLastModified(long lastModified) {
			return theSource.setLastModified(lastModified);
		}

		@Override
		public BetterFile move(BetterFile newFile) throws IOException {
			String path = newFile.getPath();
			BetterFile newSourceFile = theSource.getParent().at(path);
			if (!theFilter.test(newFile))
				return this;
			newSourceFile = theSource.move(newSourceFile);
			if (newSourceFile == null)
				return null;
			return new FilteredFile(newSourceFile, theFilter);
		}

		@Override
		public void visitAll(ExConsumer<? super BetterFile, IOException> forEach, BooleanSupplier canceled) throws IOException {
			theSource.visitAll(f -> {
				if (theFilter.test(f))
					forEach.accept(f);
			}, canceled);
		}

		@Override
		public int hashCode() {
			return theSource.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof FilteredFile)
				return theSource.equals(((FilteredFile) obj).theSource);
			else
				return theSource.equals(obj);
		}

		@Override
		public String toString() {
			return theSource.toString();
		}
	}

	static class UnmodifiableFile implements BetterFile {
		private final BetterFile theSource;

		public UnmodifiableFile(BetterFile source) {
			theSource = source;
		}

		@Override
		public String getName() {
			return theSource.getName();
		}

		@Override
		public String getPath() {
			return theSource.getPath();
		}

		@Override
		public BetterFile getRoot() {
			return new UnmodifiableFile(theSource.getRoot());
		}

		@Override
		public BetterFile getParent() {
			BetterFile p = theSource.getParent();
			return p == null ? null : new UnmodifiableFile(p);
		}

		@Override
		public boolean exists() {
			return theSource.exists();
		}

		@Override
		public long getLastModified() {
			return theSource.getLastModified();
		}

		@Override
		public boolean get(FileBooleanAttribute attribute) {
			switch (attribute) {
			case Writable:
				return false;
			default:
				return theSource.get(attribute);
			}
		}

		@Override
		public List<? extends BetterFile> discoverContents(Consumer<? super BetterFile> onDiscovered, BooleanSupplier canceled) {
			List<BetterFile> files = new ArrayList<>();
			if (theSource.discoverContents(file -> {
				UnmodifiableFile umodFile = new UnmodifiableFile(file);
				files.add(umodFile);
				if (onDiscovered != null)
					onDiscovered.accept(umodFile);
			}, canceled) == null)
				return null;
			return Collections.unmodifiableList(files);
		}

		@Override
		public long length() {
			return theSource.length();
		}

		@Override
		public InputStream read(long startFrom, BooleanSupplier canceled) throws IOException {
			return theSource.read(startFrom, canceled);
		}

		@Override
		public BetterFile at(String path) {
			return new UnmodifiableFile(theSource.at(path));
		}

		@Override
		public void delete(DirectorySyncResults results) throws IOException {
			if (theSource.exists())
				throw new IOException("Deletion not allowed");
		}

		@Override
		public BetterFile create(boolean directory) throws IOException {
			if (!exists() || isDirectory() != directory)
				throw new IOException("Cannot create or change this file");
			return this;
		}

		@Override
		public OutputStream write() throws IOException {
			throw new IOException("Cannot create or change this file");
		}

		@Override
		public boolean set(FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
			return theSource.get(attribute) == value;
		}

		@Override
		public boolean setLastModified(long lastModified) {
			return theSource.getLastModified() == lastModified;
		}

		@Override
		public BetterFile move(BetterFile newFile) {
			if (getPath().equals(newFile.getPath()))
				return this;
			return null;
		}

		@Override
		public BetterFile unmodifiable() {
			return this;
		}

		@Override
		public void visitAll(ExConsumer<? super BetterFile, IOException> forEach, BooleanSupplier canceled) throws IOException {
			theSource.visitAll(f -> {
				forEach.accept(new UnmodifiableFile(f));
			}, canceled);
		}

		@Override
		public int hashCode() {
			return theSource.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof UnmodifiableFile)
				return theSource.equals(((UnmodifiableFile) obj).theSource);
			else
				return theSource.equals(obj);
		}

		@Override
		public String toString() {
			return theSource.toString();
		}
	}
}
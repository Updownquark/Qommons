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
import java.util.function.Predicate;

import org.qommons.Named;
import org.qommons.QommonsUtils;
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
					return theWorkingDir.at(text.toString());
				}
			}
		}
	}

	BetterFile getRoot();

	BetterFile getParent();

	/** @return The path from the file root to this file */
	String getPath();

	BetterFile at(String path);

	/** @return Whether this file currently exists in the data source */
	boolean exists();

	/** @return The time (millis since epoch) at which this file is marked as having last been modified */
	long getLastModified();

	/** @return Whether this file represents a directory potentially containing other file structures */
	default boolean isDirectory() {
		return get(BetterFile.FileBooleanAttribute.Directory);
	}

	boolean get(BetterFile.FileBooleanAttribute attribute);

	/** @return The files contained in this directory, or null if this is not a directory */
	List<? extends BetterFile> listFiles();

	/** @return The number of bytes in this file, or 0 if it is not a valid, readable file */
	long length();

	/**
	 * @return A stream to read this file's content
	 * @throws IOException If the content could not be accessed
	 */
	InputStream read() throws IOException;

	/**
	 * Attempts to delete the file and all its sub-content, if any
	 * 
	 * @param results The results to update with the result of this operation
	 * @return Whether the deletion succeeded
	 */
	boolean delete(DirectorySyncResults results);

	/**
	 * Attempts to create this resource as a file or a directory (and any necessary parent directories)
	 * 
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

	default BetterFile unmodifiable() {
		return new UnmodifiableFile(this);
	}

	/**
	 * @param childFilter The filter to apply
	 * @return A file identical to this file but whose {@link #listFiles() file list} will exclude files not passing the given filter
	 */
	default BetterFile filterContent(Predicate<BetterFile> childFilter) {
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
		boolean check();
	
		boolean isRoot();
	
		boolean exists();
	
		long getLastModified();
	
		boolean get(FileBooleanAttribute attribute);
	
		List<? extends FileBacking> listFiles();
	
		FileBacking getChild(String fileName);
	
		long length();
	
		InputStream read() throws IOException;

		FileBacking createChild(String fileName, boolean directory) throws IOException;
	
		boolean delete(DirectorySyncResults results);
	
		OutputStream write() throws IOException;
	
		boolean set(BetterFile.FileBooleanAttribute attribute, boolean value, boolean ownerOnly);
	
		boolean setLastModified(long lastModified);
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
		public abstract AbstractWrappingFile getRoot();
	
		@Override
		public abstract AbstractWrappingFile getParent();
	
		@Override
		public String getPath() {
			LinkedList<String> path = new LinkedList<>();
			AbstractWrappingFile file = this;
			while (file != null) {
				path.addFirst(file.getName());
				if (file.check() == null || !file.theBacking.isRoot())
					file = file.getParent();
				else
					file = null;
			}
	
			StringBuilder str = new StringBuilder();
			for (String p : path)
				str.append(p).append('/');
			str.deleteCharAt(str.length() - 1);
			return str.toString();
		}
	
		@Override
		public BetterFile at(String path) {
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
		public List<? extends BetterFile> listFiles() {
			FileBacking backing = check();
			if (backing == null)
				return null;
			List<? extends FileBacking> list = backing.listFiles();
			if (list == null)
				return null;
			return QommonsUtils.map(list, b -> createChild(b.getName(), b), true);
		}
	
		@Override
		public long length() {
			FileBacking backing = check();
			return backing == null ? 0 : backing.length();
		}
	
		@Override
		public InputStream read() throws IOException {
			FileBacking backing = check();
			if (backing == null)
				throw new FileNotFoundException(getPath() + " not found");
			return backing.read();
		}

		@Override
		public boolean delete(DirectorySyncResults results) {
			FileBacking backing = check();
			if (backing == null)
				return true;
			return backing.delete(results);
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
			return backing == null ? false : backing.setLastModified(lastModified);
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
		public BetterFile.AbstractWrappingFile getRoot() {
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
		private FileBacking theBacking;
	
		public FileWrapper(AbstractWrappingFile parent, String name, FileBacking backing) {
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
			if (backing != null) {
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
		public AbstractWrappingFile getRoot() {
			if (theParent == null)
				return this;
			AbstractWrappingFile parent = theParent;
			while (parent.getParent() != null)
				parent = parent.getParent();
			return parent;
		}
	
		@Override
		public AbstractWrappingFile getParent() {
			return theParent;
		}
	}

	class FilteredFile implements BetterFile {
		private final BetterFile theSource;
		private final Predicate<BetterFile> theFilter;
	
		FilteredFile(BetterFile source, Predicate<BetterFile> filter) {
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
		public List<? extends BetterFile> listFiles() {
			List<? extends BetterFile> list = theSource.listFiles();
			if (list == null)
				return list;
			List<BetterFile> filtered = new ArrayList<>(list.size());
			for (BetterFile f : list) {
				if (theFilter.test(f))
					filtered.add(f);
			}
			return Collections.unmodifiableList(filtered);
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
		public InputStream read() throws IOException {
			return theSource.read();
		}
	
		@Override
		public BetterFile at(String path) {
			return theSource.at(path);
		}

		@Override
		public boolean delete(DirectorySyncResults results) {
			return theSource.delete(results);
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
		public List<? extends BetterFile> listFiles() {
			List<? extends BetterFile> children = theSource.listFiles();
			if (children == null)
				return null;
			List<BetterFile> mapped = new ArrayList<>(children.size());
			for (BetterFile f : children)
				mapped.add(new UnmodifiableFile(f));
			return Collections.unmodifiableList(mapped);
		}

		@Override
		public long length() {
			return theSource.length();
		}

		@Override
		public InputStream read() throws IOException {
			return theSource.read();
		}

		@Override
		public BetterFile at(String path) {
			return new UnmodifiableFile(theSource.at(path));
		}

		@Override
		public boolean delete(DirectorySyncResults results) {
			return false;
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
		public BetterFile unmodifiable() {
			return this;
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
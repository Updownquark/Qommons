package org.qommons.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.qommons.QommonsUtils;
import org.qommons.io.BetterFile.FileBooleanAttribute;
import org.qommons.io.FileUtils.DirectorySyncResults;

public class NativeFileSource implements BetterFile.FileDataSource {
	private final List<File> theRoots;

	public NativeFileSource() {
		this(File.listRoots());
	}

	public NativeFileSource(File... roots) {
		theRoots = Collections.unmodifiableList(Arrays.asList(roots));
	}

	public List<File> getRootFiles() {
		return theRoots;
	}

	@Override
	public List<BetterFile.FileBacking> getRoots() {
		return QommonsUtils.map(theRoots, r -> new NativeFileBacking(null, r), true);
	}

	public BetterFile toBetter(File file) {
		for (int r = 0; r < theRoots.size(); r++) {
			File root = theRoots.get(r);
			if (!file.getPath().startsWith(root.getPath()))
				throw new IllegalArgumentException(file + " is not under " + root);
			BetterFile.FileRoot rootFile = new BetterFile.FileRoot(this, r);
			if (file.equals(rootFile))
				return rootFile;
			else if (file.getPath().charAt(rootFile.getPath().length()) != '/'
				&& file.getPath().charAt(rootFile.getPath().length()) != '\\')
				throw new IllegalArgumentException(file + " is not under " + rootFile);
			return rootFile.at(file.getPath().substring(rootFile.getPath().length() + 1));
		}
		throw new IllegalArgumentException("Unrecognized root for " + file);
	}
	
	public static BetterFile of(File file) {
		return BetterFile.getRoots(new NativeFileSource(file)).get(0);
	}

	class NativeFileBacking implements BetterFile.FileBacking {
		private final NativeFileBacking theParent;
		private final File theFile;
		private final String theName;

		NativeFileBacking(NativeFileBacking parent, File file) {
			theParent = parent;
			theFile = file;
			String name = file.getName();
			if (name.length() == 0)
				name = file.getPath();
			if (name.length() > 1 && (name.charAt(name.length() - 1) == '/' || name.charAt(name.length() - 1) == '\\'))
				name = name.substring(0, name.length() - 1);
			theName = name;
		}

		public File getFile() {
			return theFile;
		}

		@Override
		public boolean check() {
			return true;
		}

		@Override
		public boolean isRoot() {
			return theRoots.contains(theFile);
		}

		@Override
		public boolean exists() {
			return theFile.exists();
		}

		@Override
		public long getLastModified() {
			return theFile.lastModified();
		}

		@Override
		public boolean get(FileBooleanAttribute attribute) {
			switch (attribute) {
			case Directory:
				return theFile.isDirectory();
			case Hidden:
				return theFile.isHidden();
			case Readable:
				return theFile.canRead();
			case Writable:
				return theFile.canWrite();
			case Symbolic:
				return Files.isSymbolicLink(theFile.toPath());
			}
			throw new IllegalStateException("" + attribute);
		}

		@Override
		public long length() {
			return theFile.length();
		}

		@Override
		public InputStream read() throws IOException {
			return new FileInputStream(theFile);
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public List<? extends BetterFile.FileBacking> listFiles() {
			java.io.File[] list = theFile.listFiles();
			return list == null ? null : QommonsUtils.map(Arrays.asList(list), f -> new NativeFileBacking(this, f), true);
		}

		@Override
		public BetterFile.FileBacking getChild(String fileName) {
			return new NativeFileBacking(this, new java.io.File(theFile, fileName));
		}

		@Override
		public BetterFile.FileBacking createChild(String fileName, boolean directory) throws IOException {
			java.io.File file = new java.io.File(theFile, fileName);
			if (file.exists()) {
				if (file.isDirectory() != directory)
					throw new IOException(file.getPath() + " already exists as a " + (directory ? "file" : "directory"));
				return new NativeFileBacking(this, file);
			} else if (directory && !file.mkdirs())
				throw new IOException("Could not create " + file.getPath());
			else if (!directory && !file.createNewFile())
				throw new IOException("Could not create " + file.getPath());
			return new NativeFileBacking(this, file);
		}

		@Override
		public boolean delete(DirectorySyncResults results) {
			return NativeFileSource.delete(theFile, results);
		}

		@Override
		public OutputStream write() throws IOException {
			return new FileOutputStream(theFile);
		}

		@Override
		public boolean set(FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
			switch (attribute) {
			case Directory:
				if (theFile.exists())
					return value == theFile.isDirectory();
				else if (value)
					return theFile.mkdirs();
				else
					try {
						return theFile.createNewFile();
					} catch (IOException e) {
						return false;
					}
			case Hidden:
				return theFile.isHidden() == value;
			case Readable:
				return theFile.setReadable(value, ownerOnly);
			case Writable:
				return theFile.setWritable(value, ownerOnly);
			}
			throw new IllegalStateException("" + attribute);
		}

		@Override
		public boolean setLastModified(long lastModified) {
			return theFile.setLastModified(lastModified);
		}

		@Override
		public int hashCode() {
			return theFile.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof NativeFileBacking && theFile.equals(((NativeFileBacking) obj).theFile);
		}

		@Override
		public String toString() {
			return theFile.toString();
		}
	}

	private static boolean delete(java.io.File file, DirectorySyncResults results) {
		if (!file.exists()) {
			return true;
		}
		if (file.isDirectory() && !Files.isSymbolicLink(file.toPath())) {
			if (results != null) {
				results.deleted(true);
			}
			java.io.File[] contents = file.listFiles();
			for (java.io.File f : contents) {
				if (!delete(f, results)) {
					return false;
				}
			}
		} else if (results != null) {
			results.deleted(false);
		}
		return file.delete();
	}
}

package org.qommons.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.qommons.ArrayUtils;
import org.qommons.ex.ExSupplier;

public class FileUtils {
	private FileUtils() {}

	public interface FileDataSource {
		String getName();

		FileDataSource getParent();

		boolean exists();

		boolean isSymbolicLink();

		long getLastModified();

		boolean isDirectory();

		FileDataSource[] listFiles();

		InputStream read() throws IOException;

		FileDataSource at(String path);

		default FileDataSource filterContent(Predicate<FileDataSource> childFilter) {
			return new FilteredDataSource(this, childFilter);
		}
	}

	public interface MutableFileDataSource extends FileDataSource {
		@Override
		MutableFileDataSource getParent();

		@Override
		MutableFileDataSource at(String path);

		@Override
		MutableFileDataSource[] listFiles();

		boolean delete(DirectorySyncResults results);

		MutableFileDataSource createDirectory() throws IOException;

		OutputStream write() throws IOException;

		boolean setLastModified(long lastModified);
	}

	public static MutableFileDataSource dataSource(File file) {
		return new DefaultFileDataSource(null, file);
	}

	public static FileDataSource asSubFile(String path, FileDataSource dataSource) {
		return new SubFile(dataSource, path.split("[\\\\/]+"));
	}

	public static FileDataSource dataSource(String name, ExSupplier<InputStream, IOException> data, LongSupplier lastModified) {
		return new SingleFileSource(name, data, lastModified);
	}

	public static FileDataSource combine(String name, FileDataSource... sources) {
		return combine(name, Arrays.asList(sources));
	}

	public static FileDataSource combine(String name, Iterable<? extends FileDataSource> sources) {
		return new CombinedFileDataSource(name, sources);
	}

	public static FileSyncOperation sync() {
		return new FileSyncOperation();
	}

	public static FileDataSource find(FileDataSource file, boolean folder, Predicate<FileDataSource> test) {
		if (!file.exists()) {
			return null;
		} else if (file.isDirectory()) {
			if (folder && test.test(file)) {
				return file;
			}
			for (FileDataSource f : file.listFiles()) {
				FileDataSource found = find(f, folder, test);
				if (found != null) {
					return found;
				}
			}
		} else if (!folder && test.test(file)) {
			return file;
		}
		return null;
	}

	public static File find(File file, boolean folder, Predicate<File> test) {
		FileDataSource found = find(dataSource(file), folder, f -> test.test(((DefaultFileDataSource) f).getRoot()));
		return found == null ? null : ((DefaultFileDataSource) found).getRoot();
	}

	public static long getResourceLastModified(Class<?> clazz, String resourcePath) {
		URL resource = clazz.getResource(resourcePath);
		if (resource == null)
			return 0;
		switch (resource.getProtocol()) {
		case "file":
			File f = new File(resource.getPath());
			if (!f.exists())
				throw new IllegalStateException("Bug!");
			return f.lastModified();
		case "jar":
			JarURLConnection jarUrl;
			try {
				jarUrl = (JarURLConnection) resource.openConnection();
			} catch (IOException e) {
				throw new IllegalStateException("huh?", e);
			}
			try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(//
				jarUrl.getJarFileURL().getFile())))) {
				ZipEntry entry = zip.getNextEntry();
				while (entry != null) {
					if (entry.getName().equals(jarUrl.getEntryName()))
						return entry.getTime();
					entry = zip.getNextEntry();
				}
				throw new IllegalStateException("Probably a bug!");
			} catch (IOException e) {
				throw new IllegalStateException("Probably a bug!", e);
			}
		default:
			return 0;
		}
	}

	public enum FileAction {
		IGNORE, DELETE, COPY;
	}

	public interface FileSyncControl {
		FileAction getAction(FileDataSource source, MutableFileDataSource dest) throws IOException;
	}

	public static final FileSyncControl NO_DELETE = (src, dest) -> src == null ? FileAction.IGNORE : FileAction.COPY;

	public static class FileSyncOperation {
		private FileDataSource theSource;
		private MutableFileDataSource theDest;
		private FileSyncControl theControl;
		private boolean isCaseSensitive;

		FileSyncOperation() {
			isCaseSensitive = true;
		}

		public FileSyncOperation from(FileDataSource source) {
			theSource = source;
			return this;
		}

		public FileSyncOperation from(File source) {
			return from(dataSource(source));
		}

		public FileSyncOperation to(MutableFileDataSource dest) {
			theDest = dest;
			return this;
		}

		public FileSyncOperation to(File dest) {
			return to(dataSource(dest));
		}

		public FileSyncOperation caseSensitive(boolean caseSensitive) {
			isCaseSensitive = caseSensitive;
			return this;
		}

		public FileSyncOperation control(FileSyncControl control) {
			theControl = control;
			return this;
		}

		public DirectorySyncResults sync() throws IOException {
			return sync(//
				new DirectorySyncResults());
		}

		public DirectorySyncResults sync(DirectorySyncResults results) throws IOException {
			if (theSource == null)
				throw new IllegalStateException("No source configured");
			if (theDest == null)
				throw new IllegalStateException("No destination configured");
			byte[] buffer = new byte[1024 * 1024];
			sync(theSource, theDest, buffer, results, new LinkedList<>());
			return results;
		}

		private void sync(FileDataSource authority, MutableFileDataSource copy, byte[] buffer, DirectorySyncResults results,
			LinkedList<String> path) throws IOException {
			path.add(authority.getName());
			if (copy.exists() && copy.isSymbolicLink()) {//
			} else if (authority.isDirectory()) {
				results.checked(true);
				if (copy.exists() && !copy.isDirectory() && !copy.delete(results)) {
					throw new IOException("Could not delete file " + printPath(path) + " to replace it with the directory from OneSAF");
				}
				try {
					copy.createDirectory();
				} catch (IOException e) {
					throw new IOException("Could not create directory " + printPath(path), e);
				}
				FileDataSource[] authContents = authority.listFiles();
				MutableFileDataSource[] copyContents = copy.listFiles();
				new ArrayUtils.ArrayAdjuster<>(copyContents, authContents,
					new ArrayUtils.DifferenceListenerE<MutableFileDataSource, FileDataSource, IOException>() {
						@Override
						public boolean identity(MutableFileDataSource o1, FileDataSource o2) {
							if (isCaseSensitive)
								return o1.getName().equals(o2.getName());
							else
								return o1.getName().equalsIgnoreCase(o2.getName());
						}

						@Override
						public MutableFileDataSource added(FileDataSource o, int mIdx, int retIdx) throws IOException {
							FileAction action = theControl == null ? FileAction.COPY : theControl.getAction(o, null);
							switch (action) {
							case IGNORE:
							case DELETE:
								break;
							case COPY:
								sync(o, copy.at(o.getName()), buffer, results, path);
								break;
							}
							return null; // Doesn't matter
						}

						@Override
						public MutableFileDataSource removed(MutableFileDataSource o, int oIdx, int incMod, int retIdx) throws IOException {
							FileAction action = theControl == null ? FileAction.DELETE : theControl.getAction(null, o);
							switch (action) {
							case IGNORE:
								break;
							case DELETE:
							case COPY:
								path.add(o.getName());
								if (!o.delete(results)) {
									throw new IOException("Could not delete file " + printPath(path) + " that is not present in OneSAF");
								}
								path.removeLast();
								break;
							}
							return o; // Doesn't matter
						}

						@Override
						public MutableFileDataSource set(MutableFileDataSource o1, int idx1, int incMod, FileDataSource o2, int idx2,
							int retIdx) throws IOException {
							FileAction action = theControl == null ? FileAction.COPY : theControl.getAction(o2, o1);
							switch (action) {
							case IGNORE:
								break;
							case DELETE:
								path.add(o2.getName());
								if (!o1.delete(results)) {
									throw new IOException("Could not delete file " + printPath(path) + " that is not present in OneSAF");
								}
								path.removeLast();
								break;
							case COPY:
								sync(o2, o1, buffer, results, path);
							}
							return o1; // Doesn't matter
						}
					}).noCreate().adjust();
			} else if (!copy.exists() || copy.isDirectory() || authority.getLastModified() != copy.getLastModified()) {
				results.checked(false);
				if (copy.isDirectory() && !copy.delete(results)) {
					throw new IOException("Could not delete directory " + printPath(path) + ", to replace it with the file from OneSAF");
				} else if (copy.exists()) {
					results.updated();
				} else {
					results.added(false);
				}
				try (InputStream in = new BufferedInputStream(authority.read()); //
					OutputStream out = new BufferedOutputStream(copy.write())) {
					int read = in.read(buffer);
					while (read > 0) {
						out.write(buffer, 0, read);
						read = in.read(buffer);
					}
				}
				if (!copy.setLastModified(authority.getLastModified())) {
					results.lastModFail();
				}
			} else {
				results.checked(false);
			}
			path.removeLast();
		}

		private static String printPath(List<String> path) {
			StringBuilder str = new StringBuilder();
			for (String s : path) {
				if (str.length() > 0)
					str.append('/');
				str.append(s);
			}
			return str.toString();
		}
	}

	public static class DirectorySyncResults {
		private int filesAdded;
		private int directoriesAdded;
		private int filesDeleted;
		private int directoriesDeleted;
		private int filesUpdated;
		private int totalFiles;
		private int totalDirectories;
		private int lastModifyFailures;

		public int getFilesAdded() {
			return filesAdded;
		}

		public int getDirectoriesAdded() {
			return directoriesAdded;
		}

		public int getFilesDeleted() {
			return filesDeleted;
		}

		public int getDirectoriesDeleted() {
			return directoriesDeleted;
		}

		public int getFilesUpdated() {
			return filesUpdated;
		}

		public int getTotalFiles() {
			return totalFiles;
		}

		public int getTotalDirectories() {
			return totalDirectories;
		}

		public int getLastModifyFailures() {
			return lastModifyFailures;
		}

		public DirectorySyncResults clear() {
			filesAdded = 0;
			directoriesAdded = 0;
			filesDeleted = 0;
			directoriesDeleted = 0;
			filesUpdated = 0;
			totalFiles = 0;
			totalDirectories = 0;
			lastModifyFailures = 0;
			return this;
		}

		void added(boolean directory) {
			if (directory) {
				directoriesAdded++;
			} else {
				filesAdded++;
			}
		}

		void deleted(boolean directory) {
			if (directory) {
				directoriesDeleted++;
			} else {
				filesDeleted++;
			}
		}

		void updated() {
			filesUpdated++;
		}

		void checked(boolean directory) {
			if (directory) {
				totalDirectories++;
			} else {
				totalFiles++;
			}
		}

		void lastModFail() {
			lastModifyFailures++;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			if (filesAdded > 0 || directoriesAdded > 0) {
				str.append("Added ");
				if (filesAdded > 0) {
					str.append(filesAdded).append(" file").append(filesAdded == 1 ? "" : "s");
					if (directoriesAdded > 0) {
						str.append(" and ").append(directoriesAdded).append(" director").append(directoriesAdded == 1 ? "y" : "ies");
					}
				} else {
					str.append(directoriesAdded).append(" directories");
				}
			}
			if (filesDeleted > 0 || directoriesDeleted > 0) {
				if (str.length() > 0) {
					str.append(", ");
				}
				str.append("Deleted ");
				if (filesDeleted > 0) {
					str.append(filesDeleted).append(" file").append(filesDeleted == 1 ? "" : "s");
					if (directoriesDeleted > 0) {
						str.append(" and ").append(directoriesDeleted).append(" director").append(directoriesDeleted == 1 ? "y" : "ies");
					}
				} else {
					str.append(directoriesDeleted).append(" directories");
				}
			}
			if (filesUpdated > 0) {
				if (str.length() > 0) {
					str.append(", ");
				}
				str.append("Updated ").append(filesUpdated).append(" file").append(filesUpdated == 1 ? "" : "s");
			}
			if (str.length() == 0) {
				str.append("No changes found among ");
			} else {
				str.append(" in ");
			}
			str.append(totalFiles).append(" files and ").append(totalDirectories).append(" directories");
			if (lastModifyFailures > 0) {
				str.append("\n\tFailed to set the last modify time for ").append(lastModifyFailures).append(" file")
					.append(lastModifyFailures == 1 ? "" : "s");
			}
			return str.toString();
		}
	}

	public static boolean delete(File file, DirectorySyncResults results) {
		if (!file.exists()) {
			return true;
		}
		if (file.isDirectory() && !Files.isSymbolicLink(file.toPath())) {
			if (results != null) {
				results.deleted(true);
			}
			File[] contents = file.listFiles();
			for (File f : contents) {
				if (!delete(f, results)) {
					return false;
				}
			}
		} else if (results != null) {
			results.deleted(false);
		}
		return file.delete();
	}

	public static class DefaultFileDataSource implements MutableFileDataSource {
		private DefaultFileDataSource theParent;
		private final File theRoot;

		DefaultFileDataSource(DefaultFileDataSource parent, File root) {
			theParent = parent;
			theRoot = root;
		}

		public File getRoot() {
			return theRoot;
		}

		@Override
		public String getName() {
			return theRoot.getName();
		}

		@Override
		public DefaultFileDataSource getParent() {
			if (theParent == null)
				theParent = new DefaultFileDataSource(null, theRoot.getParentFile());
			return theParent;
		}

		@Override
		public boolean exists() {
			return theRoot.exists();
		}

		@Override
		public boolean isSymbolicLink() {
			return Files.isSymbolicLink(theRoot.toPath());
		}

		@Override
		public long getLastModified() {
			return theRoot.lastModified();
		}

		@Override
		public boolean setLastModified(long lastModified) {
			return theRoot.setLastModified(lastModified);
		}

		@Override
		public boolean isDirectory() {
			return theRoot.isDirectory();
		}

		@Override
		public DefaultFileDataSource[] listFiles() {
			File[] files = theRoot.listFiles();
			if (files == null)
				return null;
			DefaultFileDataSource[] children = new DefaultFileDataSource[files.length];
			for (int i = 0; i < files.length; i++)
				children[i] = new DefaultFileDataSource(this, files[i]);
			return children;
		}

		@Override
		public InputStream read() throws IOException {
			return new FileInputStream(theRoot);
		}

		@Override
		public DefaultFileDataSource at(String path) {
			boolean multiLevel = path.indexOf('/') >= 0 || path.indexOf('\\') >= 0;
			return new DefaultFileDataSource(multiLevel ? null : this, new File(theRoot, path));
		}

		@Override
		public boolean delete(DirectorySyncResults results) {
			return FileUtils.delete(theRoot, results);
		}

		@Override
		public DefaultFileDataSource createDirectory() throws IOException {
			if (theRoot.exists()) {
				if (theRoot.isDirectory())
					return this;
				else
					throw new IOException(theRoot.getPath() + " is a file, not a directory");
			} else if (theRoot.mkdirs())
				return this;
			else
				throw new IOException("Could not create directory " + theRoot.getPath());
		}

		@Override
		public OutputStream write() throws IOException {
			return new FileOutputStream(theRoot);
		}

		@Override
		public String toString() {
			return theRoot.getPath();
		}
	}

	static class FilteredDataSource implements FileDataSource {
		private final FileDataSource theSource;
		private final Predicate<FileDataSource> theFilter;

		FilteredDataSource(FileDataSource source, Predicate<FileDataSource> filter) {
			theSource = source;
			theFilter = filter;
		}

		@Override
		public String getName() {
			return theSource.getName();
		}

		@Override
		public FileDataSource getParent() {
			return theSource.getParent();
		}

		@Override
		public boolean exists() {
			return theSource.exists();
		}

		@Override
		public boolean isSymbolicLink() {
			return theSource.isSymbolicLink();
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
		public FileDataSource[] listFiles() {
			FileDataSource[] list = theSource.listFiles();
			if (list == null)
				return list;
			List<FileDataSource> filtered = new ArrayList<>(list.length);
			for (int i = 0; i < list.length; i++) {
				if (theFilter.test(list[i]))
					filtered.add(list[i]);
			}
			return filtered.toArray(new FileDataSource[filtered.size()]);
		}

		@Override
		public InputStream read() throws IOException {
			return theSource.read();
		}

		@Override
		public FileDataSource at(String path) {
			return theSource.at(path);
		}
	}

	static class CombinedFileDataSource implements FileDataSource {
		private final String theName;
		private final Iterable<? extends FileDataSource> theSources;

		CombinedFileDataSource(String name, Iterable<? extends FileDataSource> sources) {
			theName = name;
			theSources = sources;
			if (!sources.iterator().hasNext())
				throw new IllegalArgumentException("No data sources");
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public FileDataSource getParent() {
			List<FileDataSource> sourceParents = null;
			String name = null;
			for (FileDataSource source : theSources) {
				FileDataSource parent = source.getParent();
				if (parent == null)
					return null;
				name = parent.getName();
				if (sourceParents == null)
					sourceParents = new ArrayList<>();
				sourceParents.add(parent);
			}
			return new CombinedFileDataSource(name, sourceParents);
		}

		@Override
		public boolean exists() {
			for (FileDataSource source : theSources)
				if (source.exists())
					return true;
			return false;
		}

		@Override
		public boolean isSymbolicLink() {
			for (FileDataSource source : theSources)
				if (source.isSymbolicLink())
					return true;
			return false;
		}

		@Override
		public long getLastModified() {
			for (FileDataSource source : theSources)
				if (source.exists())
					return source.getLastModified();
			return 0;
		}

		@Override
		public boolean isDirectory() {
			for (FileDataSource source : theSources)
				if (source.exists())
					return source.isDirectory();
			return false;
		}

		@Override
		public FileDataSource[] listFiles() {
			Map<String, List<FileDataSource>> names = null;
			for (FileDataSource source : theSources) {
				FileDataSource[] sourceList = source.listFiles();
				if (sourceList != null) {
					if (names == null)
						names = new LinkedHashMap<>();
					for (FileDataSource s : sourceList)
						names.computeIfAbsent(s.getName(), __ -> new LinkedList<>()).add(s);
				}
			}
			if (names == null)
				return null;
			FileDataSource[] ret = new FileDataSource[names.size()];
			int i = 0;
			for (Map.Entry<String, List<FileDataSource>> entry : names.entrySet()) {
				if (entry.getValue().size() == 1)
					ret[i] = entry.getValue().get(0);
				else
					ret[i] = new CombinedFileDataSource(theName, entry.getValue());
				i++;
			}
			return ret;
		}

		@Override
		public InputStream read() throws IOException {
			for (FileDataSource source : theSources)
				if (source.exists())
					return source.read();
			throw new FileNotFoundException("No such file found: " + theName);
		}

		@Override
		public FileDataSource at(String path) {
			while (path.length() > 0 && (path.charAt(path.length() - 1) == '/' || path.charAt(path.length() - 1) == '\\'))
				path = path.substring(0, path.length() - 1);
			if (path.length() == 0)
				return this;
			List<FileDataSource> sources = new ArrayList<>();
			for (FileDataSource source : theSources)
				sources.add(source.at(path));
			String name;
			int lastSlash = path.lastIndexOf('/');
			int lastBackSlash = path.lastIndexOf('\\');
			if (lastBackSlash > lastSlash)
				lastSlash = lastBackSlash;
			if (lastSlash < 0)
				name = path;
			else
				name = path.substring(lastSlash + 1);
			return new CombinedFileDataSource(name, sources);
		}
	}

	static class SubFile implements FileDataSource {
		private final FileDataSource theSource;
		private final String[] thePath;

		SubFile(FileDataSource source, String[] path) {
			theSource = source;
			thePath = path;
		}

		@Override
		public String getName() {
			return thePath[0];
		}

		@Override
		public FileDataSource getParent() {
			return null;
		}

		@Override
		public boolean exists() {
			return true;
		}

		@Override
		public boolean isSymbolicLink() {
			return false;
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
		public FileDataSource[] listFiles() {
			if (thePath.length == 1)
				return new FileDataSource[] { theSource };
			else
				return new FileDataSource[] { new SubFile(theSource, ArrayUtils.remove(thePath, 0)) };
		}

		@Override
		public InputStream read() throws IOException {
			throw new IOException(getName() + " is a directory");
		}

		@Override
		public FileDataSource at(String path) {
			// TODO Can do better than this, but don't need it at the moment
			throw new IllegalStateException("Cannot make children of this file source");
		}
	}

	static class SingleFileSource implements FileDataSource {
		private final String theName;
		private final ExSupplier<InputStream, IOException> theData;
		private final LongSupplier theLastModified;

		SingleFileSource(String name, ExSupplier<InputStream, IOException> data, LongSupplier lastModified) {
			theName = name;
			theData = data;
			theLastModified = lastModified;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public FileDataSource getParent() {
			return null;
		}

		@Override
		public boolean exists() {
			return true;
		}

		@Override
		public boolean isSymbolicLink() {
			return false;
		}

		@Override
		public long getLastModified() {
			return theLastModified.getAsLong();
		}

		@Override
		public boolean isDirectory() {
			return false;
		}

		@Override
		public FileDataSource[] listFiles() {
			return null;
		}

		@Override
		public InputStream read() throws IOException {
			InputStream data = theData.get();
			if (data == null)
				throw new IOException("No such data found");
			return data;
		}

		@Override
		public FileDataSource at(String path) {
			throw new IllegalStateException("Cannot make children of this file source");
		}

		@Override
		public String toString() {
			return theName;
		}
	}
}

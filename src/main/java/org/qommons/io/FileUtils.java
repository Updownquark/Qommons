package org.qommons.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
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
import org.qommons.Named;
import org.qommons.ex.ExSupplier;

/** Utilities to use on {@link File}s or similar structures */
public class FileUtils {
	private FileUtils() {}

	/** A structure representing a {@link File file}, directory, or some other such hierarchical data storage structure */
	public interface FileDataSource extends Named {
		/** @return The path from the file root to this file */
		default String getPath() {
			LinkedList<String> path = new LinkedList<>();
			FileDataSource file = this;
			while (file != null) {
				path.addFirst(file.getName());
				file = file.getParent();
			}

			StringBuilder str = new StringBuilder();
			for (String p : path)
				str.append(p).append('/');
			str.deleteCharAt(str.length() - 1);
			return str.toString();
		}

		/** @return This file's parent */
		FileDataSource getParent();

		/** @return Whether this file exists in the data source */
		boolean exists();

		/** @return Whether this file represents a symbolic link to another location in the data source */
		boolean isSymbolicLink();

		/** @return The time (millis since epoch) at which this file is marked as having last been modified */
		long getLastModified();

		/** @return Whether this file represents a directory potentially containing other file structures */
		boolean isDirectory();

		/** @return The files contained in this directory, or null if this is not a directory */
		FileDataSource[] listFiles();

		/** @return The number of bytes in this file, or 0 if it is not a valid, readable file */
		long length();

		/**
		 * @return A stream to read this file's content
		 * @throws IOException If the content could not be accessed
		 */
		InputStream read() throws IOException;

		/**
		 * @param path The path of the resource to get relative to this resource
		 * @return A structure representing the given resource
		 */
		FileDataSource at(String path);

		/**
		 * @param childFilter The filter to apply
		 * @return A file identical to this file but whose {@link #listFiles() file list} will exclude files not passing the given filter
		 */
		default FileDataSource filterContent(Predicate<FileDataSource> childFilter) {
			return new FilteredDataSource(this, childFilter);
		}
	}

	/** A structure representing a {@link FileDataSource} whose content can be modified */
	public interface MutableFileDataSource extends FileDataSource {
		@Override
		MutableFileDataSource getParent();

		@Override
		MutableFileDataSource at(String path);

		@Override
		MutableFileDataSource[] listFiles();

		/**
		 * Attempts to delete the file and all its sub-content, if any
		 * 
		 * @param results The results to update with the result of this operation
		 * @return Whether the deletion succeeded
		 */
		boolean delete(DirectorySyncResults results);

		/**
		 * Attempts to create this resource as a directory (and any necessary parent directories)
		 * 
		 * @return This resource
		 * @throws IOException If the directory could not be created
		 */
		MutableFileDataSource createDirectory() throws IOException;

		/**
		 * @return A stream to write data into this resource
		 * @throws IOException If the data could not be accessed for write
		 */
		OutputStream write() throws IOException;

		/**
		 * @param lastModified The time (millis since epoch) to mark this file as having been modified
		 * @return Whether the mark succeeded
		 */
		boolean setLastModified(long lastModified);
	}

	/**
	 * @param file The file to represent
	 * @return A {@link MutableFileDataSource} representing the file on the file system
	 */
	public static MutableFileDataSource dataSource(File file) {
		if (file == null)
			return null;
		else if (file instanceof SyntheticFile && ((SyntheticFile<?>) file).getFile() instanceof MutableFileDataSource)
			return (MutableFileDataSource) ((SyntheticFile<?>) file).getFile();
		else
			return new DefaultFileDataSource(null, file);
	}

	/**
	 * @param <F> The {@link FileDataSource} sub-type of the file
	 * @param file The resource to represent as a {@link File}
	 * @return The {@link File} representing the resource
	 */
	public static <F extends FileDataSource> File asFile(F file) {
		if (file == null)
			return null;
		else if (file instanceof DefaultFileDataSource)
			return ((DefaultFileDataSource) file).getRoot();
		else
			return new SyntheticFile<>(file);
	}

	/**
	 * @param path The path for the resource
	 * @param dataSource The resource
	 * @return A resource representing a synthetic ancestor directory with the given data source as its only content at the given relative
	 *         path
	 */
	public static FileDataSource asSubFile(String path, FileDataSource dataSource) {
		return new SubFile(dataSource, path.split("[\\\\/]+"));
	}

	/**
	 * Creates a synthetic file data source
	 * 
	 * @param name The name for the resource
	 * @param data The data supplier for the resource
	 * @param size The size supplier for the resource
	 * @param lastModified The last-modified supplier for the resource
	 * @return The synthetic resource
	 */
	public static FileDataSource dataSource(String name, ExSupplier<InputStream, IOException> data, LongSupplier size,
		LongSupplier lastModified) {
		return new SingleFileSource(name, data, size, lastModified);
	}

	/**
	 * @param name The name for the resource
	 * @param sources The resources to combine
	 * @return A directory resource whose content is the union of all the content of the given sources.
	 * @see #combine(String, Iterable)
	 */
	public static FileDataSource combine(String name, FileDataSource... sources) {
		return combine(name, Arrays.asList(sources));
	}

	/**
	 * @param name The name for the resource
	 * @param sources The resources to combine
	 * @return A directory resource whose content is the union of all the content of the given sources. If multiple sources contain the same
	 *         resource (by name), the first source in the list containing the resource will be considered the authority for it.
	 */
	public static FileDataSource combine(String name, Iterable<? extends FileDataSource> sources) {
		return new CombinedFileDataSource(name, sources);
	}

	/** @return An empty file synchronization operation */
	public static FileSyncOperation sync() {
		return new FileSyncOperation();
	}

	/**
	 * @param file The file or directory to search in
	 * @param folder Whether to search for a folder
	 * @param test The test to search for a pass
	 * @return The file or directory resource in the given root that matches the given folder and test requirements
	 */
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

	/**
	 * @param file The file or directory to search in
	 * @param folder Whether to search for a folder
	 * @param test The test to search for a pass
	 * @return The file or directory resource in the given root that matches the given folder and test requirements
	 */
	public static File find(File file, boolean folder, Predicate<File> test) {
		FileDataSource found = find(dataSource(file), folder, f -> test.test(((DefaultFileDataSource) f).getRoot()));
		return found == null ? null : ((DefaultFileDataSource) found).getRoot();
	}

	/**
	 * @param clazz The java class responsible for the resource
	 * @param resourcePath The path of the resource relative to the class's definition file
	 * @return The last modified time of the resource, or 0 if it could not be discovered
	 */
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

	/** Possible actions to take for a source-dest resource pair in a {@link FileSyncOperation} */
	public enum FileAction {
		/** Ignores the resource performs no action */
		IGNORE,
		/** Deletes the resource from the destination */
		DELETE,
		/** Copies the resource from the source (authority) into the destination, creating it if it does not yet exist */
		COPY;
	}

	/**
	 * Controls the behavior of a {@link FileSyncOperation} by deciding what action to take for each resource pair between the source
	 * (authority) and destination data sets.
	 */
	public interface FileSyncControl {
		/**
		 * <p>
		 * Determines what action to perform for a resource pair between the source (authority) and destination data sets.
		 * </p>
		 * <p>
		 * Either the source or destination resources or neither (but not both) may be non-existent
		 * </p>
		 * 
		 * @param source The resource from the source (authority) data set
		 * @param dest The resource in the destination data set
		 * @return The action to perform on the destination resource
		 * @throws IOException If the action could not be determined
		 */
		FileAction getAction(FileDataSource source, MutableFileDataSource dest) throws IOException;
	}

	/**
	 * A simple {@link FileSyncControl} instance that copies all data from the source to the destination but will not delete data from the
	 * destination if it is not found in the source
	 */
	public static final FileSyncControl NO_DELETE = (src, dest) -> (src == null || !src.exists()) ? FileAction.IGNORE : FileAction.COPY;

	/** An operation to synchronize resources between 2 file sources (typically directories) */
	public static class FileSyncOperation {
		private FileDataSource theSource;
		private MutableFileDataSource theDest;
		private FileSyncControl theControl;
		private boolean isCaseSensitive;

		FileSyncOperation() {
			isCaseSensitive = true;
		}

		/**
		 * @param source The data source to synchronize information from (the authority)
		 * @return This operation
		 */
		public FileSyncOperation from(FileDataSource source) {
			theSource = source;
			return this;
		}

		/**
		 * @param source The data source to synchronize information from (the authority)
		 * @return This operation
		 */
		public FileSyncOperation from(File source) {
			return from(dataSource(source));
		}

		/**
		 * @param dest The data source to synchronize information into (the destination)
		 * @return This operation
		 */
		public FileSyncOperation to(MutableFileDataSource dest) {
			theDest = dest;
			return this;
		}

		/**
		 * @param dest The data source to synchronize information into (the destination)
		 * @return This operation
		 */
		public FileSyncOperation to(File dest) {
			return to(dataSource(dest));
		}

		/**
		 * @param caseSensitive Whether resources whose names differ only in case should be regarded as distinct or as representing the same
		 *        resource
		 * @return This operation
		 */
		public FileSyncOperation caseSensitive(boolean caseSensitive) {
			isCaseSensitive = caseSensitive;
			return this;
		}

		/**
		 * @param control The control mechanism to determine the action to perform for each resource pair between the source and dest
		 *        resources
		 * @return This operation
		 */
		public FileSyncOperation control(FileSyncControl control) {
			theControl = control;
			return this;
		}

		/**
		 * Performs the synchronization operation
		 * 
		 * @return The results of the operation
		 * @throws IOException If an I/O exception occurs reading from the source or modifying the destination
		 */
		public DirectorySyncResults sync() throws IOException {
			return sync(//
				new DirectorySyncResults());
		}

		/**
		 * Performs the synchronization operation
		 * 
		 * @param results The results structure to accumulate the operation results into
		 * @return The results of the operation
		 * @throws IOException If an I/O exception occurs reading from the source or modifying the destination
		 */
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

	/** The results of a {@link FileSyncOperation} */
	public static class DirectorySyncResults {
		private int filesAdded;
		private int directoriesAdded;
		private int filesDeleted;
		private int directoriesDeleted;
		private int filesUpdated;
		private int totalFiles;
		private int totalDirectories;
		private int lastModifyFailures;

		/** @return The number of data files added to the destination resource structure */
		public int getFilesAdded() {
			return filesAdded;
		}

		/** @return The number of directories added to the destination resource structure */
		public int getDirectoriesAdded() {
			return directoriesAdded;
		}

		/** @return The number of data files deleted from the destination resource structure */
		public int getFilesDeleted() {
			return filesDeleted;
		}

		/** @return The number of directories deleted from the destination resource structure */
		public int getDirectoriesDeleted() {
			return directoriesDeleted;
		}

		/** @return The number of data files updated in the destination resource structure */
		public int getFilesUpdated() {
			return filesUpdated;
		}

		/** @return The total number of data file resources checked between the resource structures */
		public int getTotalFiles() {
			return totalFiles;
		}

		/** @return The total number of directories checked between the resource structures */
		public int getTotalDirectories() {
			return totalDirectories;
		}

		/** @return The number of {@link MutableFileDataSource#setLastModified(long)} failures on destination files and directories */
		public int getLastModifyFailures() {
			return lastModifyFailures;
		}

		/**
		 * Clears this results set for re-use
		 * 
		 * @return This results set
		 */
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

	/**
	 * @param file The file or directory resource to delete
	 * @param results The results to accumulate the deletion results into
	 * @return Whether the resource was fully removed from the data source
	 */
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

	/** Default {@link File}-based implementation of {@link MutableFileDataSource} */
	public static class DefaultFileDataSource implements MutableFileDataSource {
		private DefaultFileDataSource theParent;
		private final File theRoot;

		DefaultFileDataSource(DefaultFileDataSource parent, File root) {
			theParent = parent;
			theRoot = root;
		}

		/** @return The root file of this data structure */
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
		public long length() {
			return theRoot.length();
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

	/**
	 * A {@link File} backed by a {@link FileDataSource}
	 * 
	 * @param <F> The {@link FileDataSource} sub-type
	 */
	public static class SyntheticFile<F extends FileDataSource> extends File {
		private final F theFile;

		/** @param file The {@link FileDataSource} to back this file */
		public SyntheticFile(F file) {
			super(file.getPath());
			theFile = file;
		}

		/** @return The {@link FileDataSource} backing this file */
		public F getFile() {
			return theFile;
		}

		@Override
		public String getName() {
			return theFile.getName();
		}

		@Override
		public String getParent() {
			File parentFile = getParentFile();
			return parentFile == null ? null : parentFile.getName();
		}

		@Override
		public File getParentFile() {
			return asFile(theFile.getParent());
		}

		@Override
		public String getPath() {
			return theFile.getPath();
		}

		@Override
		public boolean exists() {
			return theFile.exists();
		}

		@Override
		public boolean isDirectory() {
			return theFile.isDirectory();
		}

		@Override
		public boolean isFile() {
			return !theFile.isDirectory();
		}

		@Override
		public boolean isHidden() {
			return false;
		}

		@Override
		public boolean isAbsolute() {
			return true;
		}

		@Override
		public long lastModified() {
			return theFile.getLastModified();
		}

		@Override
		public long length() {
			return theFile.length();
		}

		@Override
		public String[] list() {
			FileDataSource[] files = theFile.listFiles();
			if (files == null)
				return null;
			String[] list = new String[files.length];
			for (int i = 0; i < files.length; i++)
				list[i] = files[i].getName();
			return list;
		}

		@Override
		public String[] list(FilenameFilter filter) {
			FileDataSource[] files = theFile.listFiles();
			if (files == null)
				return null;
			List<String> list = new ArrayList<>(files.length);
			for (FileDataSource f : files) {
				if (filter.accept(asFile(f), f.getName()))
					list.add(f.getName());
			}
			return list.toArray(new String[list.size()]);
		}

		@Override
		public File[] listFiles() {
			FileDataSource[] files = theFile.listFiles();
			if (files == null)
				return null;
			File[] list = new File[files.length];
			for (int i = 0; i < files.length; i++)
				list[i] = asFile(files[i]);
			return list;
		}

		@Override
		public File[] listFiles(FilenameFilter filter) {
			FileDataSource[] files = theFile.listFiles();
			if (files == null)
				return null;
			List<File> list = new ArrayList<>(files.length);
			for (FileDataSource f : files) {
				File file = asFile(f);
				if (filter.accept(file, f.getName()))
					list.add(file);
			}
			return list.toArray(new File[list.size()]);
		}

		@Override
		public File[] listFiles(FileFilter filter) {
			FileDataSource[] files = theFile.listFiles();
			if (files == null)
				return null;
			List<File> list = new ArrayList<>(files.length);
			for (FileDataSource f : files) {
				File file = asFile(f);
				if (filter.accept(file))
					list.add(file);
			}
			return list.toArray(new File[list.size()]);
		}

		@Override
		public boolean delete() {
			if (theFile instanceof MutableFileDataSource)
				return ((MutableFileDataSource) theFile).delete(null);
			else
				return false;
		}

		@Override
		public boolean mkdir() {
			return mkdirs();
		}

		@Override
		public boolean mkdirs() {
			if (theFile instanceof MutableFileDataSource) {
				try {
					((MutableFileDataSource) theFile).createDirectory();
					return true;
				} catch (IOException e) {
					return false;
				}
			} else
				return false;
		}

		@Override
		public boolean setLastModified(long time) {
			if (theFile instanceof MutableFileDataSource)
				return ((MutableFileDataSource) theFile).setLastModified(time);
			else
				return false;
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
		public long length() {
			return theSource.length();
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
		public long length() {
			for (FileDataSource source : theSources)
				if (source.exists())
					return source.length();
			return 0;
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
		public long length() {
			return 0;
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
		private final LongSupplier theSize;
		private final LongSupplier theLastModified;

		SingleFileSource(String name, ExSupplier<InputStream, IOException> data, LongSupplier size, LongSupplier lastModified) {
			theName = name;
			theData = data;
			theSize = size;
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
		public long length() {
			return theSize.getAsLong();
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

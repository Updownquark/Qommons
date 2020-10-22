package org.qommons.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import org.qommons.io.BetterFile.FileBooleanAttribute;

/** Utilities to use on {@link File}s or similar structures */
public class FileUtils {
	private FileUtils() {}

	/**
	 * @param path The path for the resource
	 * @param dataSource The resource
	 * @return A resource representing a synthetic ancestor directory with the given data source as its only content at the given relative
	 *         path
	 */
	public static BetterFile asSubFile(String path, BetterFile dataSource) {
		return new SubFile(dataSource, path.split("[\\\\/]+"));
	}

	/**
	 * @param file The resource to represent as a {@link File}
	 * @return The {@link File} representing the resource
	 */
	public static File asFile(BetterFile file) {
		if (file == null)
			return null;
		else
			return new SyntheticFile(file);
	}

	public static BetterFile better(File file) {
		if (file instanceof SyntheticFile)
			return ((SyntheticFile) file).getFile();
		else
			return NativeFileSource.of(file);
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
	public static BetterFile syntheticFile(String name, ExSupplier<InputStream, IOException> data, LongSupplier size,
		LongSupplier lastModified) {
		return new SyntheticBetterFile(name, data, size, lastModified);
	}

	public static BetterFile.FileDataSource getDefaultFileSource() {
		return new CompressionEnabledFileSource(new NativeFileSource())//
			.withCompression(new CompressionEnabledFileSource.ZipCompression());
	}

	public static BetterFile getClassFile(Class<?> clazz) throws MalformedURLException {
		String classFileName = clazz.getName();
		int dotIdx = classFileName.lastIndexOf('.');
		if (dotIdx >= 0)
			classFileName = classFileName.substring(dotIdx + 1);
		classFileName += ".class";
		URL resource = clazz.getResource(classFileName);
		if (resource == null)
			throw new IllegalStateException("Could not find class file for " + clazz.getName());
		return ofUrl(resource);
	}

	public static BetterFile ofUrl(URL url) throws MalformedURLException {
		return ofUrl(getDefaultFileSource(), url);
	}

	public static BetterFile ofUrl(BetterFile.FileDataSource fileSource, URL url) throws MalformedURLException {
		switch (url.getProtocol()) {
		case "file":
			// TODO Better escape handling
			return BetterFile.at(fileSource, url.getPath().replaceAll("%20", " "));
		case "jar":
			String path = url.getPath();
			int div = path.indexOf('!');
			URL jarUrl = new URL(path.substring(0, div));
			BetterFile jarFile = ofUrl(fileSource, jarUrl);
			return jarFile.at(path.substring(div + 2));
		default:
			return BetterFile.at(new CompressionEnabledFileSource(new UrlFileSource(new URL(url, "/")))//
				.withCompression(new CompressionEnabledFileSource.ZipCompression()), url.getPath());
		}
	}

	/**
	 * @param name The name for the resource
	 * @param sources The resources to combine
	 * @return A directory resource whose content is the union of all the content of the given sources.
	 * @see #combine(String, Iterable)
	 */
	public static BetterFile combine(String name, BetterFile... sources) {
		return combine(name, Arrays.asList(sources));
	}

	/**
	 * @param name The name for the resource
	 * @param sources The resources to combine
	 * @return A directory resource whose content is the union of all the content of the given sources. If multiple sources contain the same
	 *         resource (by name), the first source in the list containing the resource will be considered the authority for it.
	 */
	public static BetterFile combine(String name, Iterable<? extends BetterFile> sources) {
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
	public static BetterFile find(BetterFile file, boolean folder, Predicate<BetterFile> test) {
		if (!file.exists()) {
			return null;
		} else if (file.isDirectory()) {
			if (folder && test.test(file)) {
				return file;
			}
			for (BetterFile f : file.listFiles()) {
				BetterFile found = find(f, folder, test);
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
		BetterFile found = find(NativeFileSource.of(file), folder, f -> test.test(asFile(f)));
		return found == null ? null : asFile(found);
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
		FileAction getAction(BetterFile source, BetterFile dest) throws IOException;
	}

	/**
	 * A simple {@link FileSyncControl} instance that copies all data from the source to the destination but will not delete data from the
	 * destination if it is not found in the source
	 */
	public static final FileSyncControl NO_DELETE = (src, dest) -> (src == null || !src.exists()) ? FileAction.IGNORE : FileAction.COPY;

	/** An operation to synchronize resources between 2 file sources (typically directories) */
	public static class FileSyncOperation {
		private BetterFile theSource;
		private BetterFile theDest;
		private FileSyncControl theControl;
		private boolean isCaseSensitive;

		FileSyncOperation() {
			isCaseSensitive = true;
		}

		/**
		 * @param source The data source to synchronize information from (the authority)
		 * @return This operation
		 */
		public FileSyncOperation from(BetterFile source) {
			theSource = source;
			return this;
		}

		/**
		 * @param source The data source to synchronize information from (the authority)
		 * @return This operation
		 */
		public FileSyncOperation from(File source) {
			return from(NativeFileSource.of(source));
		}

		/**
		 * @param dest The data source to synchronize information into (the destination)
		 * @return This operation
		 */
		public FileSyncOperation to(BetterFile dest) {
			theDest = dest;
			return this;
		}

		/**
		 * @param dest The data source to synchronize information into (the destination)
		 * @return This operation
		 */
		public FileSyncOperation to(File dest) {
			return to(NativeFileSource.of(dest));
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

		private void sync(BetterFile authority, BetterFile copy, byte[] buffer, DirectorySyncResults results,
			LinkedList<String> path) throws IOException {
			path.add(authority.getName());
			if (copy.exists() && copy.get(FileBooleanAttribute.Symbolic)) {//
			} else if (authority.isDirectory()) {
				results.checked(true);
				if (copy.exists() && !copy.isDirectory() && !copy.delete(results)) {
					throw new IOException("Could not delete file " + printPath(path) + " to replace it with the directory from OneSAF");
				}
				try {
					copy.create(true);
				} catch (IOException e) {
					throw new IOException("Could not create directory " + printPath(path), e);
				}
				List<? extends BetterFile> authContents = authority.listFiles();
				List<? extends BetterFile> copyContents = copy.listFiles();
				new ArrayUtils.ArrayAdjuster<>(copyContents.toArray(new BetterFile[copyContents.size()]), //
					authContents.toArray(new BetterFile.FilteredFile[authContents.size()]), //
					new ArrayUtils.DifferenceListenerE<BetterFile, BetterFile, IOException>() {
						@Override
						public boolean identity(BetterFile o1, BetterFile o2) {
							if (isCaseSensitive)
								return o1.getName().equals(o2.getName());
							else
								return o1.getName().equalsIgnoreCase(o2.getName());
						}

						@Override
						public BetterFile added(BetterFile o, int mIdx, int retIdx) throws IOException {
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
						public BetterFile removed(BetterFile o, int oIdx, int incMod, int retIdx) throws IOException {
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
						public BetterFile set(BetterFile o1, int idx1, int incMod, BetterFile o2, int idx2,
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

	/** A {@link File} backed by a {@link BetterFile} */
	public static class SyntheticFile extends File {
		private final BetterFile theFile;

		/** @param file The {@link BetterFile} to back this file */
		public SyntheticFile(BetterFile file) {
			super(file.getPath());
			theFile = file;
		}

		/** @return The {@link BetterFile} backing this file */
		public BetterFile getFile() {
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
		public String getCanonicalPath() {
			return getPath();
		}

		@Override
		public File getCanonicalFile() {
			return this;
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
			return theFile.get(FileBooleanAttribute.Hidden);
		}

		@Override
		public boolean canRead() {
			return theFile.get(FileBooleanAttribute.Readable);
		}

		@Override
		public boolean canWrite() {
			return theFile.get(FileBooleanAttribute.Writable);
		}

		@Override
		public boolean setReadOnly() {
			return setWritable(false);
		}

		@Override
		public boolean setWritable(boolean writable, boolean ownerOnly) {
			return theFile.set(FileBooleanAttribute.Writable, writable, ownerOnly);
		}

		@Override
		public boolean setWritable(boolean writable) {
			return theFile.set(FileBooleanAttribute.Writable, writable, false);
		}

		@Override
		public boolean setReadable(boolean readable, boolean ownerOnly) {
			return theFile.set(FileBooleanAttribute.Readable, readable, ownerOnly);
		}

		@Override
		public boolean setReadable(boolean readable) {
			return theFile.set(FileBooleanAttribute.Readable, readable, false);
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
			BetterFile[] files = theFile.listFiles().toArray(new BetterFile[0]);
			if (files == null)
				return null;
			String[] list = new String[files.length];
			for (int i = 0; i < files.length; i++)
				list[i] = files[i].getName();
			return list;
		}

		@Override
		public String[] list(FilenameFilter filter) {
			BetterFile[] files = theFile.listFiles().toArray(new BetterFile[0]);
			if (files == null)
				return null;
			List<String> list = new ArrayList<>(files.length);
			for (BetterFile f : files) {
				if (filter.accept(asFile(f), f.getName()))
					list.add(f.getName());
			}
			return list.toArray(new String[list.size()]);
		}

		@Override
		public File[] listFiles() {
			BetterFile[] files = theFile.listFiles().toArray(new BetterFile[0]);
			if (files == null)
				return null;
			File[] list = new File[files.length];
			for (int i = 0; i < files.length; i++)
				list[i] = asFile(files[i]);
			return list;
		}

		@Override
		public File[] listFiles(FilenameFilter filter) {
			BetterFile[] files = theFile.listFiles().toArray(new BetterFile[0]);
			if (files == null)
				return null;
			List<File> list = new ArrayList<>(files.length);
			for (BetterFile f : files) {
				File file = asFile(f);
				if (filter.accept(file, f.getName()))
					list.add(file);
			}
			return list.toArray(new File[list.size()]);
		}

		@Override
		public File[] listFiles(FileFilter filter) {
			BetterFile[] files = theFile.listFiles().toArray(new BetterFile[0]);
			if (files == null)
				return null;
			List<File> list = new ArrayList<>(files.length);
			for (BetterFile f : files) {
				File file = asFile(f);
				if (filter.accept(file))
					list.add(file);
			}
			return list.toArray(new File[list.size()]);
		}

		@Override
		public boolean delete() {
			return theFile.delete(null);
		}

		@Override
		public boolean mkdir() {
			return mkdirs();
		}

		@Override
		public boolean mkdirs() {
			try {
				theFile.create(true);
				return true;
			} catch (IOException e) {
				return false;
			}
		}

		@Override
		public boolean createNewFile() throws IOException {
			theFile.create(false);
			return true;
		}

		@Override
		public boolean setLastModified(long time) {
			return theFile.setLastModified(time);
		}
	}

	static class CombinedFileDataSource implements BetterFile {
		private final String theName;
		private final Iterable<? extends BetterFile> theSources;

		CombinedFileDataSource(String name, Iterable<? extends BetterFile> sources) {
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
		public String getPath() {
			return theName;
		}

		@Override
		public BetterFile getRoot() {
			return this;
		}

		@Override
		public BetterFile getParent() {
			return null;
		}

		@Override
		public boolean exists() {
			for (BetterFile source : theSources)
				if (source.exists())
					return true;
			return false;
		}

		@Override
		public long getLastModified() {
			for (BetterFile source : theSources)
				if (source.exists())
					return source.getLastModified();
			return 0;
		}

		@Override
		public List<? extends BetterFile> listFiles() {
			Map<String, List<BetterFile>> names = null;
			for (BetterFile source : theSources) {
				List<? extends BetterFile> sourceList = source.listFiles();
				if (sourceList != null) {
					if (names == null)
						names = new LinkedHashMap<>();
					for (BetterFile s : sourceList)
						names.computeIfAbsent(s.getName(), __ -> new LinkedList<>()).add(s);
				}
			}
			if (names == null)
				return null;
			List<BetterFile> ret = new ArrayList<>(names.size());
			for (Map.Entry<String, List<BetterFile>> entry : names.entrySet()) {
				if (entry.getValue().size() == 1)
					ret.add(entry.getValue().get(0));
				else
					ret.add(new CombinedFileDataSource(theName, entry.getValue()));
			}
			return Collections.unmodifiableList(ret);
		}

		@Override
		public long length() {
			for (BetterFile source : theSources)
				if (source.exists())
					return source.length();
			return 0;
		}

		@Override
		public InputStream read() throws IOException {
			for (BetterFile source : theSources)
				if (source.exists())
					return source.read();
			throw new FileNotFoundException("No such file found: " + theName);
		}

		@Override
		public BetterFile at(String path) {
			while (path.length() > 0 && (path.charAt(path.length() - 1) == '/' || path.charAt(path.length() - 1) == '\\'))
				path = path.substring(0, path.length() - 1);
			if (path.length() == 0)
				return this;
			List<BetterFile> sources = new ArrayList<>();
			for (BetterFile source : theSources)
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

		@Override
		public boolean get(FileBooleanAttribute attribute) {
			boolean or;
			switch (attribute) {
			case Directory:
			case Readable:
				or = true;
				break;
			case Hidden:
			case Symbolic:
				or = false;
				break;
			default:
				return false;
			}
			if (or) {
				for (BetterFile source : theSources)
					if (source.get(attribute))
						return true;
				return false;
			} else {
				for (BetterFile source : theSources)
					if (!source.get(attribute))
						return false;
				return true;
			}
		}

		@Override
		public boolean delete(DirectorySyncResults results) {
			return false;
		}

		@Override
		public BetterFile create(boolean directory) throws IOException {
			throw new IOException("Cannot create directories here");
		}

		@Override
		public OutputStream write() throws IOException {
			throw new IOException("Not writable");
		}

		@Override
		public boolean set(FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
			return get(attribute) == value;
		}

		@Override
		public boolean setLastModified(long lastModified) {
			return getLastModified() == lastModified;
		}

		@Override
		public BetterFile move(BetterFile newFile) {
			return null;
		}

		@Override
		public String toString() {
			return getPath();
		}
	}

	static class SubFile implements BetterFile {
		private final BetterFile theSource;
		private final String[] thePath;

		SubFile(BetterFile source, String[] path) {
			theSource = source;
			thePath = path;
		}

		@Override
		public BetterFile getRoot() {
			return theSource.getRoot();
		}

		@Override
		public String getName() {
			return thePath[0];
		}


		@Override
		public String getPath() {
			StringBuilder str = new StringBuilder();
			for (String p : thePath)
				str.append(p).append('/');
			str.append(theSource.getName());
			return str.toString();
		}

		@Override
		public BetterFile getParent() {
			return null;
		}

		@Override
		public boolean exists() {
			return true;
		}

		@Override
		public long getLastModified() {
			return 0;
		}

		@Override
		public boolean get(FileBooleanAttribute attribute) {
			switch (attribute) {
			case Readable:
				return true;
			case Writable:
				return false;
			case Directory:
				return true;
			case Hidden:
			case Symbolic:
				return false;
			}
			throw new IllegalStateException();
		}

		@Override
		public List<? extends BetterFile> listFiles() {
			if (thePath.length == 1)
				return Arrays.asList(theSource);
			else
				return Arrays.asList(new SubFile(theSource, ArrayUtils.remove(thePath, 0)));
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
		public BetterFile at(String path) {
			// TODO Can do better than this, but don't need it at the moment
			throw new IllegalStateException("Cannot make children of this file source");
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
			return getLastModified() == lastModified;
		}

		@Override
		public BetterFile move(BetterFile newFile) {
			return null;
		}

		@Override
		public int hashCode() {
			return theSource.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof SubFile && ArrayUtils.equals(thePath, ((SubFile) obj).thePath)
				&& theSource.equals(((SubFile) obj).theSource);
		}

		@Override
		public String toString() {
			return getPath();
		}
	}

	static class SyntheticBetterFile implements BetterFile {
		private final String theName;
		private final ExSupplier<InputStream, IOException> theData;
		private final LongSupplier theSize;
		private final LongSupplier theLastModified;

		SyntheticBetterFile(String name, ExSupplier<InputStream, IOException> data, LongSupplier size, LongSupplier lastModified) {
			theName = name;
			theData = data;
			theSize = size;
			theLastModified = lastModified;
		}

		@Override
		public BetterFile getRoot() {
			return this;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public String getPath() {
			return theName;
		}

		@Override
		public BetterFile getParent() {
			return null;
		}

		@Override
		public boolean exists() {
			return true;
		}

		@Override
		public long getLastModified() {
			return theLastModified.getAsLong();
		}

		@Override
		public boolean get(FileBooleanAttribute attribute) {
			switch (attribute) {
			case Readable:
				return theData != null;
			case Writable:
				return false;
			case Directory:
				return false;
			case Hidden:
			case Symbolic:
				return false;
			}
			throw new IllegalStateException();
		}

		@Override
		public List<? extends BetterFile> listFiles() {
			return theData == null ? Collections.emptyList() : null;
		}

		@Override
		public long length() {
			return theSize.getAsLong();
		}

		@Override
		public InputStream read() throws IOException {
			if (theData == null)
				throw new IOException("Not readable");
			InputStream data = theData.get();
			if (data == null)
				throw new IOException("No such data found");
			return data;
		}

		@Override
		public BetterFile at(String path) {
			throw new IllegalStateException("Cannot make children of this file source");
		}

		@Override
		public boolean delete(DirectorySyncResults results) {
			return false;
		}

		@Override
		public BetterFile create(boolean directory) throws IOException {
			if (isDirectory() != directory)
				throw new IOException("This file is a " + (directory ? "file" : "directory"));
			return this;
		}

		@Override
		public OutputStream write() throws IOException {
			throw new IOException("This file is not writable");
		}

		@Override
		public boolean set(FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
			return get(attribute) == value;
		}

		@Override
		public boolean setLastModified(long lastModified) {
			return getLastModified() == lastModified;
		}

		@Override
		public BetterFile move(BetterFile newFile) {
			return null;
		}

		@Override
		public String toString() {
			return theName;
		}
	}
}

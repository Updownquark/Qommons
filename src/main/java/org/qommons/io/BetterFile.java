package org.qommons.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.qommons.Named;
import org.qommons.ex.ExBiConsumer;
import org.qommons.ex.ExConsumer;
import org.qommons.io.FileUtils.DirectorySyncResults;

/**
 * <p>
 * The BetterFile API is a way of interfacing with different kinds of file systems in a unified way. Using this API, implementations can
 * easily be written to access {@link NativeFileSource native} file systems, {@link SftpFileSource SFTP}, {@link ArchiveEnabledFileSource
 * file archives}, {@link UrlFileSource URLs}, and anything else that resembles a file system.
 * </p>
 * <p>
 * All that needs to be done to implement the API for a new file system is to write a {@link FileDataSource}. The
 * {@link BetterFile#getRoots(FileDataSource)} and {@link BetterFile#at(FileDataSource, String)} method does the rest.
 * </p>
 */
public interface BetterFile extends Named {
	/** A boolean attribute on a file */
	enum FileBooleanAttribute {
		Readable, Writable, Directory, Hidden, Symbolic
	}

	/** Produces hash values from binary input */
	public interface Hasher {
		/** @param b The byte to add to the hash */
		void update(byte b);

		/**
		 * @param input The buffer containing bytes to add to the hash
		 * @param offset The start offset in the buffer of the content to add
		 * @param len The number of bytes to add
		 */
		void update(byte[] input, int offset, int len);

		/** @param buffer The byte content to add to the hash */
		void update(ByteBuffer buffer);

		/** @return The hashed bytes */
		byte[] getHash();

		/** Resets this hasher to be used again fresh */
		void reset();

		/** A hasher that works with a standard java {@link MessageDigest} */
		public static class MessageDigestHasher implements Hasher {
			private final MessageDigest digest;

			/** @param digest The MessageDigest to wrap */
			public MessageDigestHasher(MessageDigest digest) {
				this.digest = digest;
			}

			@Override
			public void update(byte b) {
				digest.update(b);
			}

			@Override
			public void update(byte[] input, int offset, int len) {
				digest.update(input, offset, len);
			}

			@Override
			public void update(ByteBuffer buffer) {
				digest.update(buffer);
			}

			@Override
			public byte[] getHash() {
				return digest.digest();
			}

			@Override
			public void reset() {
				digest.reset();
			}
		}

		/** A hasher that computes a CRC-32 hash value */
		public static class CRC32 implements Hasher {
			private final java.util.zip.CRC32 theHash = new java.util.zip.CRC32();

			@Override
			public void update(byte b) {
				theHash.update(b);
			}

			@Override
			public void update(byte[] input, int offset, int len) {
				theHash.update(input, offset, len);
			}

			@Override
			public void update(ByteBuffer buffer) {
				theHash.update(buffer);
			}

			@Override
			public byte[] getHash() {
				long hash = theHash.getValue();
				byte[] byteHash = new byte[4];
				for (int i = 0; i < byteHash.length; i++) {
					byteHash[byteHash.length - i - 1] = (byte) hash;
					hash >>= 8;
				}
				return byteHash;
			}

			@Override
			public void reset() {
				theHash.reset();
			}
		}
	}

	/** The type of a {@link BetterFile#getCheckSum(CheckSumType, BooleanSupplier) check sum} */
	enum CheckSumType {
		/** 32-bit cyclic redundancy check */
		CRC32(32, () -> new Hasher.CRC32()),
		/** MD5. Cryptographically broken, but still widely used. */
		MD5(128, () -> {
			try {
				return new Hasher.MessageDigestHasher(MessageDigest.getInstance("MD5"));
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException("No MD5?", e);
			}
		}),
		/** SHA-1. Cryptographically broken, but still widely used. */
		SHA1(160, () -> {
			try {
				return new Hasher.MessageDigestHasher(MessageDigest.getInstance("SHA-1"));
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException("No SHA-1?", e);
			}
		}),
		/** 256-bit version of NSA's SHA-2 cryptographic algorithm. */
		SHA256(256, () -> {
			try {
				return new Hasher.MessageDigestHasher(MessageDigest.getInstance("SHA-256"));
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException("No SHA-256?", e);
			}
		}),
		/** 512-bit version of NSA's SHA-2 cryptographic algorithm. */
		SHA512(512, () -> {
			try {
				return new Hasher.MessageDigestHasher(MessageDigest.getInstance("SHA-512"));
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException("No SHA-512?", e);
			}
		});

		public final int bits;
		public final int hexChars;
		private Supplier<Hasher> hasher;

		private CheckSumType(int bits, Supplier<Hasher> hasher) {
			this.bits = bits;
			int hex = bits / 4;
			if (hex * 4 < bits)
				hex++;
			hexChars = hex;
			this.hasher = hasher;
		}

		public Hasher hasher() {
			return hasher.get();
		}
	}

	/** A format object for parsing/formatting {@link BetterFile}s */
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

	/** @return The file source backing this file */
	FileDataSource getSource();

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

	/**
	 * @param attribute The attribute to get
	 * @return The value of the attribute for this file
	 */
	boolean get(BetterFile.FileBooleanAttribute attribute);

	/** @return The number of bytes in this file; 0 if it is not a valid, readable file; or -1 if the length cannot be quickly accessed */
	long length();

	/**
	 * @param type The hash type for the check sum
	 * @param canceled Returns true if the user cancels the operation
	 * @return The hex-encoded checksum of the file, or null if the operation was canceled
	 * @throws IOException If this is not a valid, readable file, or an error occurred reading the file
	 */
	String getCheckSum(CheckSumType type, BooleanSupplier canceled) throws IOException;

	/** @return The root of this file's file source */
	BetterFile getRoot();

	/** @return The parent of this file */
	BetterFile getParent();

	/**
	 * @param path The path to get the file of
	 * @return The file at the given path under this file
	 */
	BetterFile at(String path);

	/**
	 * Discovers the immediate file children of this file, if it is a directory
	 * 
	 * @param onDiscovered Accepts each file discovered
	 * @param canceled Returns true if the user cancels the operation
	 * @return The list of discovered files
	 */
	List<? extends BetterFile> discoverContents(Consumer<? super BetterFile> onDiscovered, BooleanSupplier canceled);

	/**
	 * @param str The string builder to append the URL to, or null to create a new one
	 * @return The string builder with this file appended to it, as a URL
	 */
	StringBuilder toUrl(StringBuilder str);

	/** @return The files contained in this directory, or null if this is not a directory */
	default List<? extends BetterFile> listFiles() {
		return discoverContents(null, null);
	}

	/**
	 * @param startFrom The byte index to start reading from
	 * @param canceled Returns true if the user cancels the operation
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

	/**
	 * @param attribute The attribute to set
	 * @param value The value for the attribute
	 * @param ownerOnly Whether the attribute is to be set for the file's owner only (e.g. permissions)
	 * @return Whether the modification was successful, or the attribute was already set to the given value
	 */
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

	/**
	 * Visits all files and directories beneath this directory. This has the potential to be much faster than using
	 * {@link #discoverContents(Consumer, BooleanSupplier)} recursively, e.g. in archives where this would require many seek operations.
	 * Using this method can result in a single, continuous read operation.
	 * 
	 * @param forEach Accepts each file in turn
	 * @param canceled Returns true if the user cancels the operation
	 * @throws IOException If an exception occurs reading the contents
	 */
	void visitAll(ExConsumer<? super BetterFile, IOException> forEach, BooleanSupplier canceled) throws IOException;

	/** @return A BetterFile identical to this, but unmodifiable */
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

	/**
	 * @param dataSource The file source
	 * @return The file roots for the file source
	 */
	static List<BetterFile> getRoots(FileDataSource dataSource) {
		List<BetterFile> roots = new ArrayList<>(dataSource.getRoots().size());
		for (FileBacking root : dataSource.getRoots())
			roots.add(new FileRoot(dataSource, root));
		return Collections.unmodifiableList(roots);
	}

	/**
	 * @param dataSource The file source
	 * @param path The path under the file source root
	 * @return A better file at the given path under the file source root
	 */
	static BetterFile at(FileDataSource dataSource, String path) {
		if (path.isEmpty()) {
			throw new IllegalArgumentException("Empty path");
		} else if (path.charAt(0) == '.') {
			return at(dataSource, System.getProperty("user.dir")).at(path);
		}
		StringBuilder name = new StringBuilder();
		AbstractWrappingFile parent = null;
		for (int c = 0; c < path.length(); c++) {
			if (path.charAt(c) == '/' || path.charAt(c) == '\\') {
				if (c == 0) { // Initial slash--hopefully a linux path
					for (FileBacking root : dataSource.getRoots()) {
						if (root.getName().equals("/")) {
							parent = new FileRoot(dataSource, root);
							break;
						} else if (root.getName().charAt(0) == '/') {
							name.append(path.charAt(c));
							break;
						}
					}
					continue;
				} else if (name.length() == 0)
					throw new IllegalArgumentException("Illegal path: " + path);
				if (parent == null) {
					String rootName = name.toString();
					if (rootName.equals(".")) // Start at current directory
						return at(dataSource, System.getProperty("user.dir")).at(path.substring(c + 1));
					if (rootName.indexOf(':') >= 0) // Roots must be '/' or else have a colon in them, e.g. Windows paths or URLs
						parent = new FileRoot(dataSource, dataSource.getRoot(rootName));
					else
						parent = (AbstractWrappingFile) at(dataSource, System.getProperty("user.dir")).at(rootName);
				} else
					parent = parent.createChild(name.toString(), null);
				name.setLength(0);
			} else
				name.append(path.charAt(c));
		}
		if (name.length() > 0) {
			if (parent == null) {
				String rootName = name.toString();
				if (rootName.equals("."))
					return at(dataSource, System.getProperty("user.dir")); // Current directory
				parent = new FileRoot(dataSource, dataSource.getRoot(rootName));
			} else
				parent = parent.createChild(name.toString(), null);
		}
		return parent;
	}

	/** Represents a file system for use with the BetterFile API */
	public interface FileDataSource {
		/** @return A URL representing the root of the file source */
		String getUrlRoot();

		/** @return {@link FileBacking} instances representing each root directory of the file source */
		List<FileBacking> getRoots();

		/**
		 * @param name The name of the root to get
		 * @return The {@link FileBacking} of the root with the given name
		 * @throws IllegalArgumentException If no such root exists
		 */
		default FileBacking getRoot(String name) throws IllegalArgumentException {
			for (FileBacking root : getRoots())
				if (root.getName().equals(name))
					return root;
			throw new IllegalArgumentException("No such root: " + name);
		}

		/**
		 * @param path The path to the file to get
		 * @return The BetterFile at the given path in this file source
		 */
		default BetterFile at(String path) {
			return BetterFile.at(this, path);
		}
	}

	/** Represents a file--the backing for a BetterFile instance */
	public interface FileBacking extends Named {
		/**
		 * Performs a check on this file to see if it may have changed.
		 * 
		 * @return True if this backing is still consistent with the file, or false if the backing needs to be regenerated
		 */
		boolean check();

		/** @return Whether this represents a file {@link FileDataSource#getRoots() root} */
		boolean isRoot();

		/** @return WHether this file exists on the system */
		boolean exists();

		/** @return The last modified time of this file */
		long getLastModified();

		/**
		 * @param attribute The attribute to get
		 * @return The value of the attribute for this file
		 */
		boolean get(FileBooleanAttribute attribute);

		/**
		 * Gets the children of this directory, if it is one
		 * 
		 * @param onDiscovered Accepts each child as it is discovered
		 * @param canceled Returns true if the user cancels the operation
		 * @return Whether all children were discovered successfully (false if canceled)
		 */
		boolean discoverContents(Consumer<? super FileBacking> onDiscovered, BooleanSupplier canceled);

		/**
		 * @param fileName The name of the chid to get
		 * @return The FileBacking instance representing the given child of this file, or null if it does not exist
		 */
		FileBacking getChild(String fileName);

		/** @return The length of this file's content */
		long length();

		/**
		 * @param type The hash type for the check sum
		 * @param canceled Returns true if the user cancels the operation
		 * @return The hex-encoded checksum of the file, or null if the operation was canceled
		 * @throws IOException If this is not a valid, readable file, or an error occurred reading the file
		 */
		String getCheckSum(CheckSumType type, BooleanSupplier canceled) throws IOException;

		/**
		 * @param startFrom The initial offset to read from
		 * @param canceled Returns true if the user cancels the operation
		 * @return The input stream to use to read the file's content
		 * @throws IOException If an error occurs reading the file
		 */
		InputStream read(long startFrom, BooleanSupplier canceled) throws IOException;

		/**
		 * @param fileName The name of the child to create
		 * @param directory Whether to create the child as a directory or a file
		 * @return The file backing representing the new child
		 * @throws IOException If the file could not be created
		 */
		FileBacking createChild(String fileName, boolean directory) throws IOException;

		/**
		 * Deletes this file and all its content
		 * 
		 * @param results The results to update with the content deleted--may be null
		 * @throws IOException If an error occurs deleting the files
		 */
		void delete(DirectorySyncResults results) throws IOException;

		/**
		 * @return A stream to write this file's content
		 * @throws IOException If the file could not be written
		 */
		OutputStream write() throws IOException;

		/**
		 * @param attribute The attribute to set
		 * @param value The value for the attribute
		 * @param ownerOnly Whether to set the attribute value for the file's owner only (e.g. permissions)
		 * @return Whether the operation was successful (or the value was already as given)
		 */
		boolean set(BetterFile.FileBooleanAttribute attribute, boolean value, boolean ownerOnly);

		/**
		 * @param lastModified The last modified time to set for the file
		 * @return Whether the operation was successful
		 */
		boolean setLastModified(long lastModified);

		/**
		 * @param newFilePath The path to move this file to
		 * @throws IOException If the move operation fails
		 */
		void move(String newFilePath) throws IOException;

		/**
		 * Visits all of this file's content--may be more efficient than using {@link #visitAll(ExBiConsumer, BooleanSupplier)} recursively,
		 * especially for archives
		 * 
		 * @param forEach Accepts each file as encountered
		 * @param canceled Returns true if the user cancels the operation
		 * @throws IOException If an error occurs reading the content
		 */
		void visitAll(ExBiConsumer<? super FileBacking, CharSequence, IOException> forEach, BooleanSupplier canceled) throws IOException;

		/**
		 * @param url A string builer containing a representation of this file as a URL
		 * @return The string builder modified to represent the file correctly if needed
		 */
		default StringBuilder alterUrl(StringBuilder url) {
			return url;
		}
	}

	/** The abstract BetterFile implementation potentially wrapping a {@link FileBacking} instance provided by a {@link FileDataSource} */
	public abstract class AbstractWrappingFile implements BetterFile {
		/** The file backing instance, if it exists and has been retrieved from the {@link FileDataSource} */
		protected volatile FileBacking theBacking;

		/** @return The file backing instance for this file, if it currently exists (null if not) */
		protected abstract FileBacking findBacking();
	
		/**
		 * Creates a file or directory at this file's path under its data source
		 * 
		 * @param directory Whether to create a file or a directory
		 * @return The file backing representing the new file or directory
		 * @throws IOException If the file or directory could not be created for any reason
		 */
		protected abstract FileBacking createBacking(boolean directory) throws IOException;

		/**
		 * If {@link #theBacking} has not been retrieved or its {@link FileBacking#check() check} is no longer valid, retrieves it
		 * 
		 * @return The file backing instance for this file, if it currently exists (null if not)
		 */
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

		/**
		 * @param name The name of the child to create the wrapper for
		 * @param backing The file backing of the child to create
		 * @return The BetterFile wrapper for the file
		 */
		protected AbstractWrappingFile createChild(String name, FileBacking backing) {
			return new BetterFile.FileWrapper(this, name, backing);
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
				if (file.check() != null)
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
					String childName = name.toString();
					switch (childName) {
					case ".":
						break;
					case "..":
						parent = parent.getParent();
						break;
					default:
						parent = parent.createChild(childName, null);
						break;
					}
					name.setLength(0);
				} else
					name.append(path.charAt(c));
			}
			if (name.length() > 0) {
				String childName = name.toString();
				switch (childName) {
				case ".":
					break;
				case "..":
					parent = parent.getParent();
					break;
				default:
					parent = parent.createChild(childName, null);
					break;
				}
			}
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
				return Collections.emptyList();
			return Collections.unmodifiableList(list);
		}

		@Override
		public long length() {
			FileBacking backing = check();
			return backing == null ? 0 : backing.length();
		}
	
		@Override
		public String getCheckSum(CheckSumType type, BooleanSupplier canceled) throws IOException {
			FileBacking backing = check();
			return backing == null ? null : backing.getCheckSum(type, canceled);
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
			if (getPath().equals(newFile.getPath()))
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
				AbstractWrappingFile file = path.length() > 0 ? at(path.toString()) : this;
				forEach.accept(file);
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

	/** A BetterFile representing a {@link FileDataSource#getRoots() root} of a {@link FileDataSource} */
	public class FileRoot extends BetterFile.AbstractWrappingFile {
		private final BetterFile.FileDataSource theSource;
		private final FileBacking theRoot;

		/**
		 * @param dataSource The data source that this file belongs to
		 * @param root The file backing representing this file
		 */
		public FileRoot(BetterFile.FileDataSource dataSource, FileBacking root) {
			theSource = dataSource;
			theRoot = root;
		}

		@Override
		public FileDataSource getSource() {
			return theSource;
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
			return theRoot;
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

		@Override
		public StringBuilder toUrl(StringBuilder str) {
			if (str == null)
				str = new StringBuilder();
			str.append(theSource.getUrlRoot());
			if (str.charAt(str.length() - 1) != '/')
				str.append('/');
			int c = 0;
			while (c < theRoot.getName().length() && theRoot.getName().charAt(c) == '/')
				c++;
			while (c < theRoot.getName().length())
				str.append(theRoot.getName().charAt(c++));
			return theRoot.alterUrl(str);
		}
	}

	/** Default, non-root BetterFile implementation */
	public class FileWrapper extends AbstractWrappingFile {
		private final AbstractWrappingFile theParent;
		private final String theName;
	
		/**
		 * @param parent The parent of this file
		 * @param name The name of this file
		 * @param backing The file backing representing this file, if it has already been retrieved
		 */
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
			else if (parentBacking.exists() && !parentBacking.get(BetterFile.FileBooleanAttribute.Directory))
				throw new IOException("Cannot create a child of a non-directory file " + theParent);
			return parentBacking.createChild(theName, directory);
		}

		@Override
		public FileDataSource getSource() {
			return theParent.getSource();
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

		@Override
		public StringBuilder toUrl(StringBuilder str) {
			str = theParent.toUrl(str);
			if (str.charAt(str.length() - 1) != '/')
				str.append('/');
			str.append(theName);
			FileBacking backing = check();
			if (backing != null)
				str = backing.alterUrl(str);
			return str;
		}
	}

	/** A wrapping BetterFile that selectively filters out some directory content */
	class FilteredFile implements BetterFile {
		private final BetterFile theSource;
		private final Predicate<? super BetterFile> theFilter;
	
		FilteredFile(BetterFile source, Predicate<? super BetterFile> filter) {
			theSource = source;
			theFilter = filter;
		}
	
		@Override
		public FileDataSource getSource() {
			throw new UnsupportedOperationException("Source not supported for this type");
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
		public String getCheckSum(CheckSumType type, BooleanSupplier canceled) throws IOException {
			return theSource.getCheckSum(type, canceled);
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
		public StringBuilder toUrl(StringBuilder str) {
			return theSource.toUrl(str);
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

	/** Implements {@link BetterFile#unmodifiable()} */
	static class UnmodifiableFile implements BetterFile {
		private final BetterFile theSource;

		public UnmodifiableFile(BetterFile source) {
			theSource = source;
		}

		@Override
		public FileDataSource getSource() {
			throw new UnsupportedOperationException("Source not supported for this type");
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
		public String getCheckSum(CheckSumType type, BooleanSupplier canceled) throws IOException {
			return theSource.getCheckSum(type, canceled);
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
		public StringBuilder toUrl(StringBuilder str) {
			return theSource.toUrl(str);
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
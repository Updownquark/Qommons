package org.qommons.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.qommons.DynamicCache;
import org.qommons.DynamicCache.Resource;
import org.qommons.QommonsUtils;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.ex.ExBiConsumer;
import org.qommons.io.BetterFile.CheckSumType;
import org.qommons.io.BetterFile.FileBacking;
import org.qommons.io.BetterFile.FileBooleanAttribute;
import org.qommons.io.FileUtils.DirectorySyncResults;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.*;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.UserAuthException;

/** Facilitates use of the {@link BetterFile} API with a remote file system via SFTP */
public class SftpFileSource extends RemoteFileSource {
	/** An SSH session configuration */
	public interface SftpSessionConfig {
		/**
		 * @param timeout The timeout to wait for connection
		 * @return This session configuration
		 */
		SftpSessionConfig withTimeout(int timeout);

		/**
		 * @param userName The user name to connect with
		 * @param password The password to connect with
		 * @return This session configuration
		 */
		SftpSessionConfig withAuthentication(String userName, String password);
	}

	class SftpSession {
		final SSHClient client;
		final SFTPClient session;

		SftpSession(SSHClient client, SFTPClient s) {
			this.client = client;
			session = s;
		}

		/** SSH sessions in this library are single-use */
		Session getSshSession() throws ConnectionException, TransportException {
			return client.startSession();
		}

		void dispose() {
			try {
				session.close();
			} catch (IOException e) {
				System.err.println("Error closing SFTP session: " + e.getMessage());
			}
			try {
				client.close();
			} catch (IOException e) {
				System.err.println("Error closing client: " + e.getMessage());
			}
		}
	}

	private final String theHost;
	private final int thePort;
	private final Consumer<SftpSessionConfig> theSessionConfiguration;
	private final String theRootDir;
	private String theKnownOS;

	private final DynamicCache<SftpSession, IOException> theSessionCache;
	private final boolean doStreamsHoldSessions;
	private int theRetryCount;

	private SftpFile theRoot;

	/**
	 * Creates the file source
	 * 
	 * @param host The host to communicate with
	 * @param user The user to connect as
	 * @param sessionConfiguration Configures each session as it is needed
	 * @param rootDir The directory to use as the root of this file source
	 */
	public SftpFileSource(String host, String user, Consumer<SftpSessionConfig> sessionConfiguration, String rootDir) {
		this(host, SSHClient.DEFAULT_PORT, user, sessionConfiguration, rootDir);
	}

	/**
	 * Creates the file source
	 * 
	 * @param host The host to communicate with
	 * @param port The port to connect on
	 * @param user The user to connect as
	 * @param sessionConfiguration Configures each session as it is needed
	 * @param rootDir The directory to use as the root of this file source
	 */
	public SftpFileSource(String host, int port, String user, Consumer<SftpSessionConfig> sessionConfiguration, String rootDir) {
		theHost = host;
		thePort = port;
		theSessionConfiguration = session -> {
			session.withAuthentication(user, null);
			sessionConfiguration.accept(session);
		};
		theRootDir = rootDir;
		doStreamsHoldSessions = true;
		theSessionCache = DynamicCache.build().build(this::createSession, SftpSession::dispose);
	}

	private SftpFileSource(String host, int port, Consumer<SftpSessionConfig> config, String rootDir, int retryCount,
		boolean streamsHoldSessions, Builder cacheBuilder) {
		theHost = host;
		thePort = port;
		theSessionConfiguration = config;
		theRootDir = rootDir;
		theRetryCount = retryCount;
		doStreamsHoldSessions = streamsHoldSessions;
		theSessionCache = cacheBuilder.buildCache(this);
	}

	/** @return The number of times this file source will re-attempt a connection before failing an operation */
	public int getRetryCount() {
		return theRetryCount;
	}

	/**
	 * @param retryCount The number of times this file source should re-attempt a connection before failing an operation
	 * @return This file source
	 */
	public SftpFileSource setRetryCount(int retryCount) {
		if (retryCount < 0)
			retryCount = 0;
		theRetryCount = retryCount;
		return this;
	}

	/**
	 * Attempts to connect to the host
	 * 
	 * @return This file source
	 * @throws IOException If the connection could not be made
	 */
	public SftpFileSource check() throws IOException {
		try (Resource<SftpSession> session = getSession()) {
		}
		return this;
	}

	@Override
	public String getUrlRoot() {
		StringBuilder str = new StringBuilder("sftp://")//
			.append(theHost).append('/');
		if (theRootDir.charAt(0) == '/')
			str.append(theRootDir, 1, theRootDir.length());
		else
			str.append(theRootDir);
		return str.toString();
	}

	@Override
	protected SftpFile getRoot() {
		if (theRoot == null) {
			theRoot = new SftpFile(null, theRootDir);
		}
		return theRoot;
	}

	@Override
	public List<FileBacking> getRoots() {
		return Collections.singletonList(getRoot());
	}

	@Override
	protected SftpFile createFile(RemoteFileBacking parent, String name) {
		return new SftpFile((SftpFile) parent, name);
	}

	Resource<SftpSession> getSession() throws IOException {
		return theSessionCache.get();
	}

	@SuppressWarnings("resource") // The session is kept around to be closed later
	SftpSession createSession() throws IOException {
		Exception ex = null;
		for (int i = 0; i <= theRetryCount; i++) {
			try {
				SSHClient s = new SSHClient();
				s.loadKnownHosts();
				s.addHostKeyVerifier(new PromiscuousVerifier());
				theSessionConfiguration.accept(new SftpSessionConfig() {
					@Override
					public SftpSessionConfig withTimeout(int timeout) {
						s.setConnectTimeout(timeout);
						s.setTimeout(timeout);
						return this;
					}

					@Override
					public SftpSessionConfig withAuthentication(String userName, String password) {
						return this;
					}
				});
				s.connect(theHost, thePort);
				try {
					theSessionConfiguration.accept(new SftpSessionConfig() {
						@Override
						public SftpSessionConfig withTimeout(int timeout) {
							return this;
						}

						@Override
						public SftpSessionConfig withAuthentication(String userName, String password) {
							try {
								s.authPassword(userName, password);
							} catch (UserAuthException | TransportException e) {
								throw new CheckedExceptionWrapper(e);
							}
							return this;
						}
					});
				} catch (CheckedExceptionWrapper e) {
					if (e.getCause() instanceof IOException)
						throw (IOException) e.getCause();
					else if (e.getCause() instanceof RuntimeException)
						throw (RuntimeException) e.getCause();
					else
						throw e;
				}
				return new SftpSession(s, s.newSFTPClient());
			} catch (Exception e) {
				e.getStackTrace();
				ex = e;
			}
		}
		// Just suppressing a code analysis warning here
		if (ex == null)
			throw new IllegalStateException();
		throw new IOException("Could not connect to " + theHost + " (" + ex.getMessage() + ")", ex);
	}

	static final Set<OpenMode> CREATE_MODE = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(OpenMode.CREAT, OpenMode.WRITE)));
	static final Set<OpenMode> WRITE_MODE = Collections
		.unmodifiableSet(new HashSet<>(Arrays.asList(OpenMode.CREAT, OpenMode.TRUNC, OpenMode.WRITE)));

	private static final Consumer<SftpSessionConfig> NOTHING = __ -> {};

	/**
	 * @param host The hostname or IP address to connect to
	 * @return A Builder for building an {@link SftpFileSource}
	 */
	public static Builder build(String host) {
		return new Builder(host);
	}

	/** Builds an {@link SftpFileSource} */
	public static class Builder extends DynamicCache.AbstractBuilder<Builder> {
		private final String theHost;
		private int thePort;
		private Consumer<SftpSessionConfig> theSessionConfig;
		private String theRootDir;
		private int theRetryCount;
		private boolean doStreamsHoldSessions;

		Builder(String host) {
			theHost = host;
			thePort = SSHClient.DEFAULT_PORT;
			theSessionConfig = NOTHING;
			theRootDir = "/";
		}

		/**
		 * @param port The port to connect to
		 * @return This builder
		 */
		public Builder withPort(int port) {
			thePort = port;
			return this;
		}

		/**
		 * @param user The user name to connect as
		 * @param password The password to authenticate with
		 * @return This builder
		 */
		public Builder withAuthentication(String user, String password) {
			return configureSession(session -> session.withAuthentication(user, password));
		}

		/**
		 * @param timeout The timeout to wait for connection
		 * @return This builder
		 */
		public Builder withTimeout(int timeout) {
			return configureSession(session -> session.withTimeout(timeout));
		}

		/**
		 * @param rootDir The directory on the file system to represent as the root of this file source
		 * @return This builder
		 */
		public Builder withRootDir(String rootDir) {
			theRootDir = rootDir;
			return this;
		}

		/**
		 * @param config Configures the session before connection
		 * @return This builder
		 */
		public Builder configureSession(Consumer<SftpSessionConfig> config) {
			if (theSessionConfig == NOTHING)
				theSessionConfig = config;
			else {
				Consumer<SftpSessionConfig> previous = theSessionConfig;
				theSessionConfig = session -> {
					previous.accept(session);
					config.accept(session);
				};
			}
			return this;
		}

		/**
		 * @param count The number of times to retry connecting on failure
		 * @return This builder
		 */
		public Builder retry(int count) {
			theRetryCount = Math.max(0, count);
			return this;
		}

		/**
		 * @param hold Whether input and output streams created by the file source hold on to their sessions. If true, repeated use of
		 *        streams may be slightly faster, but holding multiple streams open may be more expensive.
		 * 
		 * @return This builder
		 */
		public Builder streamsHoldSessions(boolean hold) {
			doStreamsHoldSessions = hold;
			return this;
		}

		/**
		 * @param fileSource The file source to create the cache for
		 * @return The session cache for the file source
		 */
		protected DynamicCache<SftpSession, IOException> buildCache(SftpFileSource fileSource) {
			return super.build(fileSource::createSession, SftpSession::dispose);
		}

		/** @return The new {@link SftpFileSource} */
		public SftpFileSource build() {
			return new SftpFileSource(theHost, thePort, theSessionConfig, theRootDir, theRetryCount, doStreamsHoldSessions, this);
		}
	}

	@SuppressWarnings("resource")
	private class SftpFile extends RemoteFileBacking {
		private String thePath;
		private int thePermissions;

		SftpFile(SftpFile parent, String fileName) {
			super(parent, fileName);
		}

		SftpFile setAttributes(FileAttributes attrs) {
			if (attrs == null) {
				setData(false, -1, 0);
				return this;
			}
			thePermissions = attrs.getMode().getPermissionsMask();
			if (attrs.getType() == FileMode.Type.DIRECTORY)
				setData(true, attrs.getMtime() * 1000L, 0);
			else
				setData(false, attrs.getMtime() * 1000L, attrs.getSize());
			return this;
		}

		@Override
		protected SftpFile getParent() {
			return (SftpFile) super.getParent();
		}

		@Override
		public SftpFile getChild(String fileName) {
			return (SftpFile) super.getChild(fileName);
		}

		@Override
		protected void queryData() throws IOException {
			try (Resource<SftpSession> session = getSession()) {
				setAttributes(session.get().session.statExistence(getPath()));
			} catch (Exception e) {
				throw new IOException(e);
			}
		}

		@Override
		public String getCheckSum(CheckSumType type, BooleanSupplier canceled) throws IOException {
			try (Resource<SftpSession> session = getSession()) {
				try {
					if (theKnownOS != null) {
						CheckSumCommand cmd = CheckSumCommand.CHECK_SUM_COMMANDS.get(theKnownOS);
						if (cmd != null)
							return getCheckSum(cmd, type, session.get());
					} else {
						for (Map.Entry<String, CheckSumCommand> cmd : CheckSumCommand.CHECK_SUM_COMMANDS.entrySet()) {
							try {
								String checkSum = getCheckSum(cmd.getValue(), type, session.get());
								if (checkSum != null)
									theKnownOS = cmd.getKey();
								return checkSum;
							} catch (IOException e) {
								// Don't throw here, just try the next potential OS
							}
						}
					}
				} catch (IOException e) {
					// Don't throw here, just try it the long way
				}
			}
			return FileUtils.getCheckSum(() -> read(0, canceled), type, canceled);
		}

		private String getCheckSum(CheckSumCommand cmd, CheckSumType type, SftpSession session) throws IOException {
			String cmdStr = cmd.getCommand(type, getPath());
			if (cmdStr == null)
				return null; // OS doesn't support command-line checksum for this type
			try (Session ssh = session.getSshSession(); Session.Command sessionCmd = ssh.exec(cmdStr)) {
				try (Reader r = new InputStreamReader(sessionCmd.getInputStream())) {
					return cmd.readOutput(r, type);
				}
			}
		}

		@Override
		public boolean discoverContents(Consumer<? super FileBacking> onDiscovered, BooleanSupplier canceled) {
			try {
				if (!isDirectory())
					return true;
				try (Resource<SftpSession> session = getSession()) {
					for (RemoteResourceInfo file : session.get().session.ls(getPath())) {
						if (canceled.getAsBoolean())
							return false;
						if (file.getName().equals(".") || file.getName().equals(".."))
							continue;
						onDiscovered.accept(new SftpFile(this, file.getName()).setAttributes(file.getAttributes()));
					}
				}
				return true;
			} catch (Exception e) {
				System.err.println("Could not get directory listing of " + this);
				e.printStackTrace();
				return false;
			}
		}

		/** @return The actual path into the remote file system */
		private String getPath() {
			if (getParent() == null)
				return getName();
			else if (thePath == null) {
				StringBuilder str = new StringBuilder(getParent().getPath());
				if (str.charAt(str.length() - 1) != '/' && str.charAt(str.length() - 1) != '\\')
					str.append('/');
				str.append(getName());
				thePath = str.toString();
			}
			return thePath;
		}

		@Override
		public InputStream read(long startFrom, BooleanSupplier canceled) throws IOException {
			if (doStreamsHoldSessions)
				return new SessionHoldingInputStream(this, startFrom);
			else
				return new DynamicSessionInputStream(this, startFrom);
		}

		@Override
		public FileBacking createChild(String fileName, boolean directory) throws IOException {
			if (getParent() != null && !getParent().exists())
				getParent().createChild(getName(), true);
			try (Resource<SftpSession> session = getSession()) {
				if (directory) {
					session.get().session.mkdirs(getPath() + "/" + fileName);
				} else {
					session.get().session.open(getPath() + "/" + fileName, CREATE_MODE).close();
				}
				return getChild(fileName);
			} catch (Exception e) {
				throw new IOException("Could not create " + getPath(), e);
			}
		}

		@Override
		public void delete(DirectorySyncResults results) throws IOException {
			try (Resource<SftpSession> session = getSession()) {
				SFTPClient client = session.get().session;
				String path = getPath();
				delete(client, path, client.lstat(path), results);
			} catch (Exception e) {
				throw new IOException("Could not delete " + getPath(), e);
			}
		}

		private void delete(SFTPClient session, String path, FileAttributes attrs, DirectorySyncResults results) throws IOException {
			if (attrs.getType() == FileMode.Type.DIRECTORY) {
				session.rmdir(path);
				if (results != null)
					results.deleted(true);
			} else {
				session.rm(path);
				if (results != null)
					results.deleted(false);
			}
		}

		@Override
		public OutputStream write(boolean append) throws IOException {
			long startFrom = append ? length() : 0;
			if (doStreamsHoldSessions)
				return new SessionHoldingOutputStream(this, startFrom);
			else
				return new DynamicSessionOutputStream(this, startFrom);
		}

		@Override
		public boolean set(FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
			checkData();
			if(!exists())
				return false;
			switch(attribute) {
			case Symbolic:
			case Hidden:
				return false;
			case Readable:
			case Writable:
				int newPerms=thePermissions;
				if (attribute == FileBooleanAttribute.Writable) {
					if (value) {
						if (ownerOnly)
							newPerms |= 700;
						else
							newPerms |= 777;
					} else {
						if (ownerOnly)
							newPerms &= 477;
						else
							newPerms = 444;
					}
				} else {
					if (value) {
						if (ownerOnly)
							newPerms |= 400;
						else
							newPerms |= 444;
					} else {
						if (ownerOnly)
							newPerms &= 077;
						else
							newPerms = 000;
					}
				}
				if (newPerms == thePermissions)
					return true;
				try (Resource<SftpSession> session = getSession()) {
					SFTPClient client = session.get().session;
					FileAttributes atts = client.lstat(getPath());
					client.setattr(getPath(), new FileAttributes(newPerms, atts.getSize(), atts.getUID(), atts.getGID(), atts.getMode(),
						atts.getAtime(), atts.getMtime(), Collections.emptyMap()));
				} catch (Exception e) {
					return false;
				}
			}
			return false;
		}

		@Override
		public boolean setLastModified(long lastModified) {
			try (Resource<SftpSession> session = getSession()) {
				SFTPClient client = session.get().session;
				FileAttributes atts = client.lstat(getPath());
				client.setattr(getPath(), new FileAttributes(atts.getMode().getPermissionsMask(), atts.getSize(), atts.getUID(),
					atts.getGID(), atts.getMode(), atts.getAtime(), lastModified, Collections.emptyMap()));
				return true;
			} catch (Exception e) {
				return false;
			}
		}

		@Override
		public void move(List<String> newFilePath) throws IOException {
			try (Resource<SftpSession> session = getSession()) {
				String path = String.join("/", newFilePath);
				session.get().session.rename(getPath(), path);
			} catch (Exception e) {
				throw new IOException("Could not rename " + getPath() + " to " + newFilePath, e);
			}
		}

		@Override
		public void visitAll(ExBiConsumer<? super FileBacking, CharSequence, IOException> forEach, BooleanSupplier canceled)
			throws IOException {
			throw new IOException("Not yet implemented");
		}

		@Override
		public int hashCode() {
			return Objects.hash(theHost, getPath());
		}

		SftpFileSource getSource() {
			return SftpFileSource.this;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof SftpFile))
				return false;
			SftpFile other = (SftpFile) obj;
			return theHost.equals(other.getSource().theHost)//
				&& getPath().equals(other.getPath());
		}

		@Override
		public String toString() {
			if (getParent() == null)
				return getUrlRoot();
			else {
				String p = getParent().toString();
				if (p.charAt(p.length() - 1) != '/' && p.charAt(p.length() - 1) != '\\')
					p += "/";
				return p + getName();
			}
		}
	}

	class SessionHoldingInputStream extends InputStream {
		private final SftpFile theSftpFile;
		private Resource<SftpSession> theSession;
		private RemoteFile theFile;
		private long theOffset;
		private boolean isClosed;
		private byte[] theSingleBuffer;

		SessionHoldingInputStream(SftpFile file, long startFrom) throws IOException {
			theSftpFile = file;
			theOffset = startFrom;
			connect();
		}

		private void checkConnection() throws IOException {
			if (isClosed)
				throw new IOException("Stream is closed");
			else if (theFile == null)
				connect();
		}

		private void connect() throws IOException {
			if (isClosed)
				return;
			if (theFile != null) {
				try {
					theFile.close();
				} catch (IOException e) {
					theFile = null;
					throw e;
				}
			}
			if (theSession == null)
				theSession = getSession();
			theFile = theSession.get().session.open(theSftpFile.getPath(), QommonsUtils.unmodifiableDistinctCopy(OpenMode.READ));
		}

		@Override
		public int read() throws IOException {
			if (theSingleBuffer == null)
				theSingleBuffer = new byte[1];
			if (read(theSingleBuffer, 0, 1) > 0)
				return theSingleBuffer[0] & 0xff;
			else
				return -1;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			checkConnection();
			int read;
			try {
				read = theFile.read(theOffset, b, off, len);
			} catch (IOException e) {
				// Reconnect and try again
				connect();
				read = theFile.read(theOffset, b, off, len);
				if (read <= 0)
					return read;
			}
			if (read <= 0 && theOffset < theSftpFile.length()) {
				connect();
				read = theFile.read(theOffset, b, off, len);
			}
			if (read > 0)
				theOffset += read;
			return read;
		}

		@Override
		public long skip(long n) throws IOException {
			long length = theSftpFile.length();
			long oldOffset = theOffset;
			long newOffset = Math.min(oldOffset + n, length);
			theOffset = newOffset;
			return newOffset - oldOffset;
		}

		@Override
		public int available() throws IOException {
			return 0;
		}

		@Override
		public void close() throws IOException {
			if (isClosed)
				return;
			isClosed = true;
			if (theFile != null) {
				theFile.close();
				theFile = null;
				theSession.close();
				theSession = null;
			}
			super.close();
		}

		@Override
		public String toString() {
			return "Reading:" + theSftpFile.getPath();
		}
	}

	class DynamicSessionInputStream extends InputStream {
		private final SftpFile theSftpFile;
		/** The offset to read from in the remote stream, i.e. the position of the next byte from read() plus the buffer length */
		private long theOffset;
		private final CircularByteBuffer theBuffer;

		private long theMark; // Might as well support it, it's very easy

		DynamicSessionInputStream(SftpFile sftpFile, long offset) {
			theSftpFile = sftpFile;
			theOffset = offset;
			theBuffer = new CircularByteBuffer(4 * 1024);
		}

		@Override
		public int read() throws IOException {
			if (theBuffer.length() == 0) {
				try (Resource<SftpSession> session = getSession(); //
					RemoteFile remote = session.get().session.open(theSftpFile.getPath(),
						QommonsUtils.unmodifiableDistinctCopy(OpenMode.READ))) {
					fillBuffer(remote);
					if (theBuffer.length() == 0)
						return -1;
				}
			}
			return theBuffer.pop();
		}

		private void fillBuffer(RemoteFile remote) throws IOException {
			int target = theBuffer.getCapacity() - theBuffer.length();
			theBuffer.appendFrom(new CircularByteBuffer.Input() {
				private long theLength = -1;

				@Override
				public int read(byte[] buffer, int offset, int length) throws IOException {
					int r = remote.read(theOffset, buffer, offset, length);
					if (r > 0)
						theOffset += r;
					return r;
				}

				@Override
				public long available() throws IOException {
					if (theLength < 0)
						theLength = theSftpFile.length();
					return theLength - theOffset;
				}
			}, target);
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (len <= theBuffer.length()) {
				theBuffer.copyTo(0, b, off, len);
				theBuffer.delete(0, len);
				return len;
			}
			int preBuf = theBuffer.length();
			int remain = len - preBuf;
			theBuffer.copyTo(0, b, off, preBuf);
			theBuffer.clear(false);
			try (Resource<SftpSession> session = getSession(); //
				RemoteFile remote = session.get().session.open(theSftpFile.getPath(),
					QommonsUtils.unmodifiableDistinctCopy(OpenMode.READ))) {
				if (remain < theBuffer.getCapacity()) {
					fillBuffer(remote);
					int read = theBuffer.length();
					if (read == 0 && theOffset >= theSftpFile.length())
						return -1;
					int ret = Math.min(read, remain);
					theBuffer.copyTo(0, b, off, ret);
					theBuffer.delete(0, ret);
					return preBuf + ret;
				} else {
					int read = remote.read(theOffset, b, off, len);
					if (read > 0)
						theOffset += read;
					return read;
				}
			}
		}

		@Override
		public long skip(long n) throws IOException {
			if (n <= theBuffer.length()) {
				theBuffer.delete(0, (int) n);
				return n;
			} else {
				long remain = n - theBuffer.length();
				theBuffer.clear(false);
				long targetOffset = theOffset + remain;
				long length = theSftpFile.length();
				if (targetOffset <= length) {
					theOffset = targetOffset;
					return n;
				} else {
					long skipped = n - (length - targetOffset);
					theOffset = length;
					return skipped;
				}
			}
		}

		@Override
		public int available() throws IOException {
			return theBuffer.length();
		}

		@Override
		public boolean markSupported() {
			return true;
		}

		@Override
		public synchronized void mark(int readlimit) {
			theMark = theOffset - theBuffer.length();
		}

		@Override
		public synchronized void reset() {
			theOffset = theMark;
			theBuffer.clear(false);
		}
	}

	class SessionHoldingOutputStream extends OutputStream {
		private final SftpFile theSftpFile;
		private Resource<SftpSession> theSession;
		private RemoteFile theFile;
		private long theOffset;
		private boolean isClosed;
		private byte[] theSingleBuffer;

		SessionHoldingOutputStream(SftpFile file, long startFrom) throws IOException {
			theSftpFile = file;
			theOffset = startFrom;
			connect();
		}

		private void checkConnection() throws IOException {
			if (isClosed)
				throw new IOException("Stream is closed");
			else if (theFile == null)
				connect();
		}

		private void connect() throws IOException {
			if (isClosed)
				return;
			if (theFile != null)
				theFile.close();
			if (theSession == null)
				theSession = getSession();
			theFile = theSession.get().session.open(theSftpFile.getPath(), WRITE_MODE);
		}

		@Override
		public void write(int b) throws IOException {
			if (theSingleBuffer == null)
				theSingleBuffer = new byte[1];
			theSingleBuffer[0] = (byte) b;
			write(theSingleBuffer, 0, 1);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			checkConnection();
			try {
				theFile.write(theOffset, b, off, len);
				theOffset += len;
			} catch (IOException e) {
				// Reconnect and try again
				connect();
				theFile.write(theOffset, b, off, len);
				theOffset += len;
			}
		}

		@Override
		public void close() throws IOException {
			if (isClosed)
				return;
			isClosed = true;
			if (theFile != null) {
				theFile.close();
				theFile = null;
				theSession.close();
				theSession = null;
			}
			super.close();
		}

		@Override
		public String toString() {
			return "Writing:" + theSftpFile.getPath();
		}
	}

	class DynamicSessionOutputStream extends OutputStream {
		private final SftpFile theSftpFile;
		/** The position to write to in the remote stream, i.e. the number of bytes written so far minus the buffer length */
		private long theOffset;
		private final CircularByteBuffer theBuffer;

		DynamicSessionOutputStream(SftpFile sftpFile, long offset) {
			theSftpFile = sftpFile;
			theOffset = offset;
			theBuffer = new CircularByteBuffer(4 * 1024);
		}

		@Override
		public void write(int b) throws IOException {
			theBuffer.append((byte) b);
			if (theBuffer.length() >= theBuffer.getCapacity() - 1024)
				flush();
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			if (theBuffer.length() + len <= theBuffer.getCapacity()) {
				theBuffer.append(b, off, len);
				if (theBuffer.length() >= theBuffer.getCapacity() - 1024)
					flush();
			} else {
				try (Resource<SftpSession> session = getSession();
					RemoteFile remote = session.get().session.open(theSftpFile.getPath(), WRITE_MODE)) {
					flush(remote);
					remote.write(theOffset, b, off, len);
					theOffset += len;
				}
			}
		}

		@Override
		public void flush() throws IOException {
			if (theBuffer.length() == 0)
				return;
			try (Resource<SftpSession> session = getSession();
				RemoteFile remote = session.get().session.open(theSftpFile.getPath(), WRITE_MODE)) {
				flush(remote);
			}
		}

		private void flush(RemoteFile remote) throws IOException {
			if (theBuffer.length() > 0) {
				theBuffer.writeContent((buf, off, len) -> {
					remote.write(theOffset, buf, off, len);
					theOffset += len;
				}, 0, theBuffer.length());
				theBuffer.clear(false);
			}
		}

		@Override
		public void close() throws IOException {
			flush();
		}

		@Override
		public String toString() {
			return "Writing:" + theSftpFile.getPath();
		}
	}
}

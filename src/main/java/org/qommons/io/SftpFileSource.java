package org.qommons.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.ex.ExBiConsumer;
import org.qommons.io.BetterFile.CheckSumType;
import org.qommons.io.BetterFile.FileBacking;
import org.qommons.io.BetterFile.FileBooleanAttribute;
import org.qommons.io.FileUtils.DirectorySyncResults;
import org.qommons.threading.QommonsTimer;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.OpenMode;
import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
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

	class ThreadSession implements AutoCloseable {
		String threadId;
		final SSHClient client;
		final SFTPClient session;
		final IOException error;
		int usage;

		ThreadSession(String threadId, SSHClient client, SFTPClient s) {
			this.threadId = threadId;
			this.client = client;
			session = s;
			error = null;
		}

		ThreadSession(String threadId, IOException err) {
			this.threadId = threadId;
			client = null;
			session = null;
			error = err;
		}

		void reUse(String thread) {
			this.threadId = thread;
			usage = 0;
		}

		/** SSH sessions in this library are single-use */
		Session getSshSession() throws ConnectionException, TransportException {
			return client.startSession();
		}

		ThreadSession use() {
			usage++;
			return this;
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

		@Override
		public void close() {
			if (--usage == 0) {
				QommonsTimer.getCommonInstance().doAfterInactivity(this, () -> reallyDisconnect(threadId), Duration.ofMillis(1000));
			}
		}
	}

	private final String theHost;
	private final int thePort;
	private final String theUser;
	private final Consumer<SftpSessionConfig> theSessionConfiguration;
	private final String theRootDir;
	private String theKnownOS;

	private final ConcurrentHashMap<String, ThreadSession> theSessions;
	private final ReferenceQueue<Thread> theThreadDeaths;
	private final ConcurrentHashMap<Reference<? extends Thread>, String> theThreadIdsByRef;
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
		theUser = user;
		theSessionConfiguration = sessionConfiguration;
		theRootDir = rootDir;
		theSessions = new ConcurrentHashMap<>();
		theThreadDeaths = new ReferenceQueue<>();
		theThreadIdsByRef = new ConcurrentHashMap<>();
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
		try (ThreadSession session = getSession()) {
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

	ThreadSession getSession() throws IOException {
		ThreadSession session = null;
		// Clean up sessions associated with dead threads
		// If there are any, use the first of such sessions to satisfy this request instead of creating a fresh one
		Reference<? extends Thread> deadThread = theThreadDeaths.poll();
		while (deadThread != null) {
			String threadId = theThreadIdsByRef.remove(deadThread);
			ThreadSession deadSession = theSessions.remove(threadId);
			if (deadSession != null) {
				if (session == null && deadSession.error == null)
					session = deadSession;
				else
					deadSession.dispose();
			}
			deadThread = theThreadDeaths.poll();
		}

		Thread thread = Thread.currentThread();
		String threadId = Long.toHexString(thread.getId()) + ":" + Integer.toHexString(System.identityHashCode(thread));
		if (session != null) {
			theSessions.put(threadId, session);
			theThreadIdsByRef.put(new PhantomReference<>(thread, theThreadDeaths), threadId);
			session.reUse(threadId);
			return session;
		}
		session = theSessions.computeIfAbsent(threadId, th -> {
			theThreadIdsByRef.put(new PhantomReference<>(thread, theThreadDeaths), threadId);
			try {
				return createSession(threadId);
			} catch (IOException e) {
				return new ThreadSession(th, e);
			}
		});
		if (session.error != null)
			throw session.error;
		else
			return session.use();
	}

	protected void cleanup(boolean scavengeOne) {
		Reference<? extends Thread> deadThread = theThreadDeaths.poll();
		while (deadThread != null) {
			String threadId = theThreadIdsByRef.remove(deadThread);
			theSessions.remove(threadId).dispose();
			deadThread = theThreadDeaths.poll();
		}
	}

	@SuppressWarnings("resource") // The session is kept around to be closed later
	ThreadSession createSession(String threadId) throws IOException {
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
				return new ThreadSession(threadId, s, s.newSFTPClient());
			} catch (Exception e) {
				e.getStackTrace();
				ex = e;
			}
		}
		throw new IOException("Could not connect to " + theUser + "@" + theHost + " (" + ex.getMessage() + ")", ex);
	}

	void reallyDisconnect(String threadId) {
		theSessions.compute(threadId, (t, session) -> {
			if (session != null && session.usage == 0) {
				session.dispose();
				return null;
			} else
				return session;
		});
	}

	static final Set<OpenMode> CREATE_MODE = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(OpenMode.CREAT, OpenMode.WRITE)));
	static final Set<OpenMode> WRITE_MODE = Collections
		.unmodifiableSet(new HashSet<>(Arrays.asList(OpenMode.CREAT, OpenMode.TRUNC, OpenMode.WRITE)));

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
			try {
				setAttributes(getSession().session.statExistence(getPath()));
			} catch (Exception e) {
				throw new IOException(e);
			}
		}

		@Override
		public String getCheckSum(CheckSumType type, BooleanSupplier canceled) throws IOException {
			try (ThreadSession session = getSession()) {
				try {
					if (theKnownOS != null) {
						CheckSumCommand cmd = CheckSumCommand.CHECK_SUM_COMMANDS.get(theKnownOS);
						if (cmd != null)
							return getCheckSum(cmd, type, session);
					} else {
						for (Map.Entry<String, CheckSumCommand> cmd : CheckSumCommand.CHECK_SUM_COMMANDS.entrySet()) {
							try {
								String checkSum = getCheckSum(cmd.getValue(), type, session);
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

		private String getCheckSum(CheckSumCommand cmd, CheckSumType type, ThreadSession session) throws IOException {
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
				for (RemoteResourceInfo file : getSession().session.ls(getPath())) {
					if (canceled.getAsBoolean())
						return false;
					if (file.getName().equals(".") || file.getName().equals(".."))
						continue;
					onDiscovered.accept(new SftpFile(this, file.getName()).setAttributes(file.getAttributes()));
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
			return new InputStream() {
				private RemoteFile theFile;
				private long theOffset = startFrom;
				private boolean isClosed;
				private byte[] theSingleBuffer;

				{
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
					theFile = getSession().session.open(getPath());
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
					if (read <= 0 && theOffset < length()) {
						connect();
						read = theFile.read(theOffset, b, off, len);
					}
					if (read > 0)
						theOffset += read;
					return read;
				}

				@Override
				public long skip(long n) throws IOException {
					long length = length();
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
					if (theFile != null)
						theFile.close();
					super.close();
				}

				@Override
				public String toString() {
					return "Reading:" + getPath();
				}
			};
		}

		@Override
		public FileBacking createChild(String fileName, boolean directory) throws IOException {
			if (getParent() != null && !getParent().exists())
				getParent().createChild(getName(), true);
			try {
				if (directory) {
					getSession().session.mkdirs(getPath() + "/" + fileName);
				} else {
					getSession().session.open(getPath() + "/" + fileName, CREATE_MODE).close();
				}
				return getChild(fileName);
			} catch (Exception e) {
				throw new IOException("Could not create " + getPath(), e);
			}
		}

		@Override
		public void delete(DirectorySyncResults results) throws IOException {
			try {
				SFTPClient session = getSession().session;
				String path = getPath();
				delete(session, path, session.lstat(path), results);
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
		public OutputStream write() throws IOException {
			// This isn't tested well yet
			return new OutputStream() {
				private RemoteFile theFile;
				private long theOffset = 0;
				private boolean isClosed;
				private byte[] theSingleBuffer;

				{
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
					theFile = getSession().session.open(getPath(), WRITE_MODE);
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
					if (theFile != null)
						theFile.close();
					super.close();
				}

				@Override
				public String toString() {
					return "Writing:" + getPath();
				}
			};
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
				try {
					SFTPClient session = getSession().session;
					FileAttributes atts = session.lstat(getPath());
					session.setattr(getPath(), new FileAttributes(newPerms, atts.getSize(), atts.getUID(), atts.getGID(), atts.getMode(),
						atts.getAtime(), atts.getMtime(), Collections.emptyMap()));
				} catch (Exception e) {
					return false;
				}
			}
			return false;
		}

		@Override
		public boolean setLastModified(long lastModified) {
			try {
				SFTPClient session = getSession().session;
				FileAttributes atts = session.lstat(getPath());
				session.setattr(getPath(), new FileAttributes(atts.getMode().getPermissionsMask(), atts.getSize(), atts.getUID(),
					atts.getGID(), atts.getMode(), atts.getAtime(), lastModified, Collections.emptyMap()));
				return true;
			} catch (Exception e) {
				return false;
			}
		}

		@Override
		public void move(List<String> newFilePath) throws IOException {
			try {
				String path = String.join("/", newFilePath);
				getSession().session.rename(getPath(), path);
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
			return Objects.hash(theHost, theUser, getPath());
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
				&& theUser.equals(other.getSource().theUser)//
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
}

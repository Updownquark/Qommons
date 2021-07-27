package org.qommons.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.qommons.ex.ExBiConsumer;
import org.qommons.ex.ExConsumer;
import org.qommons.ex.ExFunction;
import org.qommons.io.BetterFile.FileBacking;
import org.qommons.io.BetterFile.FileBooleanAttribute;
import org.qommons.io.FileUtils.DirectorySyncResults;
import org.qommons.threading.QommonsTimer;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

/** Facilitates use of the {@link BetterFile} API with a remote file system via SFTP */
public class SftpFileSource extends RemoteFileSource {
	static class ThreadSession {
		final Session session;
		ChannelSftp channel;
		final IOException error;
		int usage;

		ThreadSession(Session s) {
			session = s;
			error = null;
		}

		ThreadSession(IOException err) {
			session = null;
			error = err;
		}

		ThreadSession use() {
			usage++;
			return this;
		}
	}

	private final JSch theJSch;
	private final String theHost;
	private final String theUser;
	private final ExConsumer<Session, Exception> theSessionConfiguration;
	private final String theRootDir;

	private final ConcurrentHashMap<Thread, ThreadSession> theSessions;

	private SftpFile theRoot;

	/**
	 * Creates the file source
	 * 
	 * @param jSch The JSCH object to use for SFTP communication
	 * @param host The host to communicate with
	 * @param user The user to connect as
	 * @param sessionConfiguration Configures each session as it is needed
	 * @param rootDir The directory to use as the root of this file source
	 */
	public SftpFileSource(JSch jSch, String host, String user, ExConsumer<Session, Exception> sessionConfiguration, String rootDir) {
		theJSch = jSch == null ? new JSch() : jSch;
		theHost = host;
		theUser = user;
		theSessionConfiguration = sessionConfiguration;
		theRootDir = rootDir;
		theSessions = new ConcurrentHashMap<>();
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
		Thread thread = Thread.currentThread();
		ThreadSession session = theSessions.computeIfAbsent(thread, __ -> {
			try {
				Session s = theJSch.getSession(theUser, theHost);
				theSessionConfiguration.accept(s);
				s.connect();
				return new ThreadSession(s);
			} catch (Exception e) {
				IOException ex = new IOException("Could not connect to " + theUser + "@" + theHost, e);
				ex.getStackTrace();
				return new ThreadSession(ex);
			}
		});
		if (session.error != null)
			throw session.error;
		else
			return session.use();
	}

	ChannelSftp getChannel() throws IOException {
		ThreadSession session = getSession();
		if (session.channel == null) {
			try {
				session.channel = (ChannelSftp) session.session.openChannel("sftp");
				session.channel.connect();
			} catch (JSchException e) {
				throw new IOException("Could not connect to " + theUser + "@" + theHost, e);
			}
		}
		return session.channel;
	}

	void disconnect() {
		Thread thread = Thread.currentThread();
		ThreadSession session = theSessions.get(thread);
		if (session == null)
			return;
		if (--session.usage == 0)
			QommonsTimer.getCommonInstance().doAfterInactivity(this, () -> reallyDisconnect(thread), Duration.ofMillis(100));
	}

	void reallyDisconnect(Thread thread) {
		theSessions.compute(thread, (t, session) -> {
			if (session != null && session.usage == 0) {
				if (session.channel != null) {
					session.channel.disconnect();
					session.channel.exit();
				}
				session.session.disconnect();
				return null;
			} else
				return session;
		});
	}

	private class SftpFile extends RemoteFileBacking {
		private String thePath;
		private int thePermissions;

		SftpFile(SftpFile parent, String fileName) {
			super(parent, fileName);
		}

		SftpFile setAttributes(SftpATTRS attrs) {
			thePermissions = attrs.getPermissions();
			if (attrs.isDir())
				setData(true, attrs.getMTime() * 1000L, 0);
			else
				setData(false, attrs.getMTime() * 1000L, attrs.getSize());
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

		<T> T inChannel(ExFunction<ChannelSftp, T, Exception> action) throws Exception {
			ChannelSftp channel = getChannel();
			try {
				return action.apply(channel);
			} finally {
				disconnect();
			}
		}

		@Override
		protected void queryData() throws IOException {
			try {
				inChannel(channel -> {
					String path = getPath();
					SftpATTRS attrs;
					try {
						attrs = channel.lstat(path);
						setAttributes(attrs);
					} catch (SftpException e) {
						if ("No such file".equals(e.getMessage()))
							setData(false, -1, 0);
						else
							throw e;
					}
					return null;
				});
			} catch (IOException e) {
				throw e;
			} catch (Exception e) {
				throw new IOException("Could not query info of " + this, e);
			}
		}

		@Override
		public boolean discoverContents(Consumer<? super FileBacking> onDiscovered, BooleanSupplier canceled) {
			try {
				if (!get(FileBooleanAttribute.Directory))
					return true;
				return inChannel(channel -> {
					String path = getPath();
					Vector<ChannelSftp.LsEntry> listing = channel.ls(path);
					for (ChannelSftp.LsEntry entry : listing) {
						if (entry.getFilename().equals(".") || entry.getFilename().equals(".."))
							continue;
						onDiscovered.accept(new SftpFile(this, entry.getFilename()).setAttributes(entry.getAttrs()));
					}
					return true;
				});
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
			ChannelSftp channel = getChannel();
			boolean[] success = new boolean[1];
			try {
				InputStream stream = channel.get(getPath(), null, startFrom);
				success[0] = true;
				return new InputStream() {
					private boolean isClosed;

					@Override
					public int read() throws IOException {
						return stream.read();
					}

					@Override
					public int read(byte[] b, int off, int len) throws IOException {
						return stream.read(b, off, len);
					}

					@Override
					public long skip(long n) throws IOException {
						return stream.skip(n);
					}

					@Override
					public int available() throws IOException {
						return stream.available();
					}

					@Override
					public synchronized void mark(int readlimit) {
						stream.mark(readlimit);
					}

					@Override
					public synchronized void reset() throws IOException {
						stream.reset();
					}

					@Override
					public boolean markSupported() {
						return stream.markSupported();
					}

					@Override
					public void close() throws IOException {
						if (isClosed)
							return;
						isClosed = true;
						try {
							stream.close();
						} finally {
							disconnect();
						}
						super.close();
					}

					@Override
					protected void finalize() throws Throwable {
						if (!isClosed)
							close();
						super.finalize();
					}
				};
			} catch (SftpException e) {
				throw new IOException(e);
			} finally {
				if (!success[0])
					disconnect();
			}
		}

		@Override
		public FileBacking createChild(String fileName, boolean directory) throws IOException {
			if (getParent() != null && !getParent().exists())
				getParent().createChild(getName(), true);
			try {
				if (directory) {
					inChannel(channel -> {
						String path = getPath() + "/" + fileName;
						channel.mkdir(path);
						return null;
					});
				} else {
					inChannel(channel -> {
						channel.put(new InputStream() {
							@Override
							public int read() throws IOException {
								return -1;
							}
						}, getPath() + "/" + fileName);
						return null;
					});
				}
				return getChild(fileName);
			} catch (Exception e) {
				throw new IOException("Could not delete " + getPath(), e);
			}
		}

		@Override
		public void delete(DirectorySyncResults results) throws IOException {
			try {
				inChannel(channel -> {
					String path = getPath();
					delete(channel, path, channel.lstat(path), results);
					return null;
				});
			} catch (Exception e) {
				throw new IOException("Could not delete " + getPath(), e);
			}
		}

		private void delete(ChannelSftp channel, String path, SftpATTRS attrs, DirectorySyncResults results) throws SftpException {
			if (attrs.isDir()) {
				Vector<ChannelSftp.LsEntry> listing = channel.ls(path);
				for (ChannelSftp.LsEntry entry : listing) {
					if (entry.getFilename().equals(".") || entry.getFilename().equals(".."))
						continue;
					delete(channel, path + "/" + entry.getFilename(), entry.getAttrs(), results);
				}
				channel.rmdir(path);
				if (results != null)
					results.deleted(true);
			} else {
				channel.rm(path);
				if (results != null)
					results.deleted(false);
			}
		}

		@Override
		public OutputStream write() throws IOException {
			ChannelSftp channel = getChannel();
			boolean[] success = new boolean[1];
			try {
				OutputStream stream;
				try {
					stream = channel.put(getPath());
				} catch (SftpException e) {
					throw new IOException("Could not write to " + getPath(), e);
				}
				success[0] = true;
				return new OutputStream() {
					private boolean isClosed;

					@Override
					public void write(int b) throws IOException {
						stream.write(b);
					}

					@Override
					public void write(byte[] b, int off, int len) throws IOException {
						stream.write(b, off, len);
					}

					@Override
					public void flush() throws IOException {
						stream.flush();
					}

					@Override
					public void close() throws IOException {
						if (isClosed)
							return;
						isClosed = true;
						try {
							stream.close();
						} finally {
							disconnect();
						}
						super.close();
					}

					@Override
					protected void finalize() throws Throwable {
						if (!isClosed)
							close();
						super.finalize();
					}
				};
			} finally {
				if (!success[0])
					disconnect();
			}
		}

		@Override
		public boolean set(FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
			checkData();
			if(!exists())
				return false;
			switch(attribute) {
			case Directory:
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
				int fPerms = newPerms;
				try {
					return inChannel(channel -> {
						channel.chmod(fPerms, getPath());
						return true;
					});
				} catch (Exception e) {
					return false;
				}
			}
			return false;
		}

		@Override
		public boolean setLastModified(long lastModified) {
			try {
				return inChannel(channel -> {
					channel.setMtime(getPath(), Math.round(lastModified * 1.0f / 1000));
					return true;
				});
			} catch (Exception e) {
				return false;
			}
		}

		@Override
		public void move(String newFilePath) throws IOException {
			try {
				inChannel(channel -> {
					channel.rename(getPath(), newFilePath);
					return null;
				});
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

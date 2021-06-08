package org.qommons.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
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

public class SftpFileSource extends RemoteFileSource {
	private final JSch theJSch;
	private final String theHost;
	private final String theUser;
	private final ExConsumer<Session, Exception> theSessionConfiguration;
	private final String theRootDir;

	private volatile Session theSession;
	private volatile ChannelSftp theChannel;
	private final AtomicInteger theSessionUsage;

	private SftpFile theRoot;

	public SftpFileSource(JSch jSch, String host, String user, ExConsumer<Session, Exception> sessionConfiguration, String rootDir) {
		theJSch = jSch == null ? new JSch() : jSch;
		theHost = host;
		theUser = user;
		theSessionConfiguration = sessionConfiguration;
		theRootDir = rootDir;
		theSessionUsage = new AtomicInteger();
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

	synchronized Session getSession() throws IOException {
		int usage = theSessionUsage.getAndIncrement();
		if (usage == 0 && theSession == null) {
			try {
				theSession = theJSch.getSession(theUser, theHost);
				theSessionConfiguration.accept(theSession);
				theSession.connect();
			} catch (Exception e) {
				throw new IOException("Could not connect to " + theUser + "@" + theHost, e);
			}
		}
		return theSession;
	}

	synchronized ChannelSftp getChannel() throws IOException {
		Session session = getSession();
		if (theChannel == null) {
			try {
				theChannel = (ChannelSftp) session.openChannel("sftp");
				theChannel.connect();
			} catch (JSchException e) {
				throw new IOException("Could not connect to " + theUser + "@" + theHost, e);
			}
		}
		return theChannel;
	}

	synchronized void disconnect() {
		if (theSessionUsage.decrementAndGet() == 0)
			QommonsTimer.getCommonInstance().doAfterInactivity(this, this::reallyDisconnect, Duration.ofMillis(100));
	}

	private synchronized void reallyDisconnect() {
		if (theSessionUsage.get() == 0) {
			if (theChannel != null)
				theChannel.disconnect();
			theChannel = null;
			theSession.disconnect();
			theSession = null;
		}
	}

	private class SftpFile extends RemoteFileBacking {
		private ChannelSftp theChannel;
		private long theChannelSessionStamp;
		private String thePath;

		SftpFile(SftpFile parent, String fileName) {
			super(parent, fileName);
		}

		SftpFile setAttributes(SftpATTRS attrs) {
			if (attrs.isDir())
				setData(0, 0);
			else
				setData(attrs.getMTime() * 1000, attrs.getSize());
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
					setAttributes(//
						channel.lstat(path));
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
				return inChannel(channel -> {
					String path = getPath();
					Vector<ChannelSftp.LsEntry> listing = channel.ls(path);
					for (ChannelSftp.LsEntry entry : listing) {
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
			throw new IOException("Not yet implemented");
		}

		@Override
		public void delete(DirectorySyncResults results) throws IOException {
			throw new IOException("Not yet implemented");
		}

		@Override
		public OutputStream write() throws IOException {
			ChannelSftp channel = getChannel();
			boolean[] success = new boolean[1];
			try {
				OutputStream stream = channel.getOutputStream();
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
			throw new UnsupportedOperationException("Not yet implemented");
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
			throw new IOException("Not yet implemented");
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

package org.qommons.io;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.qommons.io.BetterFile.FileBacking;
import org.qommons.io.BetterFile.FileBooleanAttribute;

/** Abstract file source for remote file systems */
public abstract class RemoteFileSource implements BetterFile.FileDataSource {
	/** The default check interval to use between checking the stats of remote files */
	public static final long DEFAULT_CHECK_INTERVAL = 1000;

	private long theCheckInterval;

	/** Creates the file source */
	public RemoteFileSource() {
		theCheckInterval = DEFAULT_CHECK_INTERVAL;
	}

	/** @return The length (in ms) between when this file source re-checks URLs for changes */
	public long getCheckInterval() {
		return theCheckInterval;
	}

	/**
	 * @param checkInterval The length (in ms) between when this file source should re-check URLs for changes
	 * @return This file source
	 */
	public RemoteFileSource setCheckInterval(long checkInterval) {
		theCheckInterval = checkInterval;
		return this;
	}

	/** @return The root to use for this file source */
	protected abstract RemoteFileBacking getRoot();

	@Override
	public List<FileBacking> getRoots() {
		return Collections.singletonList(getRoot());
	}

	@Override
	public FileBacking getRoot(String name) {
		return createFile(getRoot(), name);
	}

	@Override
	public String getUrlRoot() {
		String str = getRoot().toString();
		if (str.charAt(str.length() - 1) == '/')
			str = str.substring(0, str.length() - 1);
		return str;
	}

	@Override
	public int hashCode() {
		return getRoot().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof RemoteFileSource && getRoot().equals(((RemoteFileSource) obj).getRoot());
	}

	@Override
	public String toString() {
		return getRoot().toString();
	}

	/**
	 * @param parent The parent file
	 * @param name The name of the child
	 * @return A {@link RemoteFileBacking} representing the child
	 */
	protected abstract RemoteFileBacking createFile(RemoteFileBacking parent, String name);

	/** A {@link FileBacking} for a {@link RemoteFileSource} */
	protected abstract class RemoteFileBacking implements BetterFile.FileBacking {
		private final RemoteFileBacking theParent;
		private final String theName;

		private volatile long theLastCheck;
		private volatile boolean isDirectory;
		private volatile long theLastModified;
		private volatile long theLength;

		/**
		 * @param parent The parent file
		 * @param name The name of this file
		 */
		protected RemoteFileBacking(RemoteFileBacking parent, String name) {
			theParent = parent;
			theName = name;
			theLastCheck = 0;
		}

		/** @return This file's parent */
		protected RemoteFileBacking getParent() {
			return theParent;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public boolean check() {
			return true; // No need to ever regenerate, since this is just a pointer
		}

		/** Checks this file's status with the server, if configured to */
		protected void checkData() {
			long now = System.currentTimeMillis();
			boolean shouldCheck = shouldCheckData(now);
			if (!shouldCheck)
				return;
			try {
				queryData();
			} catch (IOException e) {
				// System.err.println("Metadata Query failed: " + e.getMessage());
				theLength = 0;
				theLastModified = -1;
			}
			theLastCheck = now;
		}

		/**
		 * @param now The current time
		 * @return Whether a check is due for this file's status
		 */
		protected boolean shouldCheckData(long now) {
			return now - theLastCheck >= getCheckInterval();
		}

		/**
		 * Calls the server to update this file's status
		 * 
		 * @throws IOException If an error occurs retrieving the status
		 */
		protected abstract void queryData() throws IOException;

		/**
		 * Sets this file's status, typically as a result of a {@link #queryData()} call
		 * 
		 * @param dir Whether this is a directory
		 * @param lastModified The last modified time of this file, or -1 if it does not exist
		 * @param length The content length of this file
		 */
		protected void setData(boolean dir, long lastModified, long length) {
			isDirectory = dir;
			theLastModified = lastModified;
			theLength = length;
		}

		@Override
		public boolean isRoot() {
			return theParent == null;
		}

		@Override
		public boolean exists() {
			checkData();
			return theLastModified != -1;
		}

		@Override
		public long getLastModified() {
			checkData();
			return theLastModified;
		}

		@Override
		public boolean get(FileBooleanAttribute attribute) {
			switch (attribute) {
			case Directory:
				checkData();
				return isDirectory;
			case Readable:
				return true;
			case Hidden:
			case Symbolic:
			case Writable:
				return false;
			}
			throw new IllegalStateException("" + attribute);
		}

		@Override
		public RemoteFileBacking getChild(String fileName) {
			return createFile(this, fileName);
		}

		@Override
		public long length() {
			checkData();
			return theLength;
		}

		@Override
		public abstract int hashCode();

		@Override
		public abstract boolean equals(Object obj);

		@Override
		public abstract String toString();
	}
}

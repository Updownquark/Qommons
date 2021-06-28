package org.qommons.io;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.qommons.io.BetterFile.FileBacking;
import org.qommons.io.BetterFile.FileBooleanAttribute;

public abstract class RemoteFileSource implements BetterFile.FileDataSource {
	public static final long DEFAULT_CHECK_INTERVAL = 1000;

	private long theCheckInterval;

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

	protected abstract RemoteFileBacking createFile(RemoteFileBacking parent, String name);

	protected abstract class RemoteFileBacking implements BetterFile.FileBacking {
		private final RemoteFileBacking theParent;
		private final String theName;

		private volatile long theLastCheck;
		private volatile boolean isDirectory;
		private volatile long theLastModified;
		private volatile long theLength;

		protected RemoteFileBacking(RemoteFileBacking parent, String name) {
			theParent = parent;
			theName = name;
			theLastCheck = 0;
		}

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

		protected void checkData() {
			long now = System.currentTimeMillis();
			if (!shouldCheckData(now))
				return;
			try {
				queryData();
			} catch (IOException e) {
				theLength = 0;
				theLastModified = -1;
			}
			theLastCheck = now;
		}

		protected boolean shouldCheckData(long now) {
			return now - theLastCheck >= getCheckInterval();
		}

		protected abstract void queryData() throws IOException;

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

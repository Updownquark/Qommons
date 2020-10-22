package org.qommons.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.qommons.io.BetterFile.FileBacking;
import org.qommons.io.BetterFile.FileBooleanAttribute;
import org.qommons.io.BetterFile.FileDataSource;
import org.qommons.io.FileUtils.DirectorySyncResults;

public class UrlFileSource implements FileDataSource {
	private static final long DEFAULT_CHECK_INTERVAL = 1000;

	private final URL theRoot;
	private long theCheckInterval;

	public UrlFileSource(URL root) {
		theRoot = root;
		theCheckInterval = DEFAULT_CHECK_INTERVAL;
	}

	public long getCheckInterval() {
		return theCheckInterval;
	}

	public UrlFileSource setCheckInterval(long checkInterval) {
		theCheckInterval = checkInterval;
		return this;
	}

	@Override
	public List<FileBacking> getRoots() {
		return Collections.unmodifiableList(Arrays.asList(new UrlFileBacking(null, theRoot)));
	}

	class UrlFileBacking implements BetterFile.FileBacking {
		private UrlFileBacking theParent;
		private final URL theURL;
		private final String theName;

		private volatile long theLastCheck;
		private volatile long theLastModified;
		private volatile long theLength;

		UrlFileBacking(UrlFileBacking parent, URL url) {
			theParent = parent;
			theURL = url;
			if (parent == null)
				theName = url.toString();
			else
				theName = url.toString().substring(parent.theURL.toString().length());
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public boolean check() {
			return true;
		}

		private void checkData() {
			long now = System.currentTimeMillis();
			if (now - theLastCheck < getCheckInterval())
				return;
			try {
				URLConnection conn = theURL.openConnection();
				theLength = conn.getContentLengthLong();
				theLastModified = conn.getLastModified();
			} catch (IOException e) {
				theLength = 0;
				theLastModified = -1;
			}
			theLastCheck = now;
		}

		@Override
		public boolean isRoot() {
			return theParent == null;
		}

		@Override
		public boolean exists() {
			return true;
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
				return theLastModified == -1;
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
		public List<? extends FileBacking> listFiles() {
			check();
			if (theLastModified == -1)
				return Collections.emptyList();
			else
				return null;
		}

		@Override
		public UrlFileBacking getChild(String fileName) {
			URL url;
			try {
				url = new URL(theURL, fileName);
			} catch (MalformedURLException e) {
				throw new IllegalArgumentException("Bad URL sub-path \"" + fileName + "\"", e);
			}
			return new UrlFileBacking(this, url);
		}

		@Override
		public long length() {
			checkData();
			return theLength;
		}

		@Override
		public InputStream read() throws IOException {
			return theURL.openStream();
		}

		@Override
		public UrlFileBacking createChild(String fileName, boolean directory) throws IOException {
			UrlFileBacking child = getChild(fileName);
			if (!directory && child.getLastModified() == -1)
				throw new IOException("Cannot create new URL resources");
			return child;
		}

		@Override
		public boolean delete(DirectorySyncResults results) {
			return false;
		}

		@Override
		public OutputStream write() throws IOException {
			throw new IOException("Cannot write to a URL");
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
		public boolean move(String newFilePath) {
			return false;
		}
	}
}

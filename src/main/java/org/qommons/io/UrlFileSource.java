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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.qommons.ex.ExBiConsumer;
import org.qommons.io.BetterFile.FileBacking;
import org.qommons.io.BetterFile.FileBooleanAttribute;
import org.qommons.io.BetterFile.FileDataSource;
import org.qommons.io.FileUtils.DirectorySyncResults;

/** A file source that can read from {@link URL}s */
public class UrlFileSource implements FileDataSource {
	private static final long DEFAULT_CHECK_INTERVAL = 1000;

	private final URL theRoot;
	private long theCheckInterval;

	/** @param root The root URL path */
	public UrlFileSource(URL root) {
		theRoot = root;
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
	public UrlFileSource setCheckInterval(long checkInterval) {
		theCheckInterval = checkInterval;
		return this;
	}

	@Override
	public List<FileBacking> getRoots() {
		return Collections.unmodifiableList(Arrays.asList(new UrlFileBacking(null, theRoot)));
	}

	@Override
	public FileBacking getRoot(String name) {
		URL url;
		try {
			url = new URL(theRoot + "/" + name);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("Bad path: " + name);
		}
		return new UrlFileBacking(null, url);
	}

	@Override
	public int hashCode() {
		return theRoot.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof UrlFileSource && theRoot.equals(((UrlFileSource) obj).theRoot);
	}

	@Override
	public String toString() {
		return theRoot.toString();
	}

	class UrlFileBacking implements BetterFile.FileBacking {
		private UrlFileBacking theParent;
		private final URL theURL;
		private final String theName;

		private volatile long theLastCheck;
		private volatile long theLastModified;
		private volatile long theLength;
		private volatile boolean isRangeAccepted;

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
				isRangeAccepted = "bytes".equalsIgnoreCase(conn.getHeaderField("Accept-Ranges"));
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
		public boolean discoverContents(Consumer<? super FileBacking> onDiscovered, BooleanSupplier canceled) {
			return true;
		}

		@Override
		public UrlFileBacking getChild(String fileName) {
			URL url;
			try {
				url = new URL(theURL + "/" + fileName);
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

		@SuppressWarnings("resource")
		@Override
		public InputStream read(long startFrom, BooleanSupplier canceled) throws IOException {
			checkData();
			if (canceled.getAsBoolean())
				return null;
			URLConnection conn = theURL.openConnection();
			InputStream stream;
			if (canceled.getAsBoolean())
				return null;
			if (startFrom > 0) {
				if (isRangeAccepted) {
					conn.setRequestProperty("Range", startFrom + "-" + (theLength - 1));
					stream = conn.getInputStream();
				} else {
					stream = conn.getInputStream();
					long skipped = stream.skip(startFrom);
					long totalSkipped = skipped;
					while (skipped >= 0 && totalSkipped < startFrom) {
						if (canceled.getAsBoolean()) {
							stream.close();
							return null;
						}
						skipped = stream.skip(startFrom);
						totalSkipped += skipped;
					}
					if (skipped < 0) {
						stream.close();
						return null;
					}
				}
			} else
				stream = conn.getInputStream();
			return stream;
		}

		@Override
		public UrlFileBacking createChild(String fileName, boolean directory) throws IOException {
			UrlFileBacking child = getChild(fileName);
			if (!directory && child.getLastModified() == -1)
				throw new IOException("Cannot create new URL resources");
			return child;
		}

		@Override
		public void delete(DirectorySyncResults results) throws IOException {
			throw new IOException("Cannot delete a URL");
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
		public void move(String newFilePath) throws IOException {
			throw new IOException("Cannot move a URL");
		}

		@Override
		public void visitAll(ExBiConsumer<? super FileBacking, CharSequence, IOException> forEach, BooleanSupplier canceled)
			throws IOException {
			forEach.accept(this, "");
		}

		@Override
		public String toString() {
			return theURL.toString();
		}
	}
}

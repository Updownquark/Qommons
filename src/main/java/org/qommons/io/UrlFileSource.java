package org.qommons.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.qommons.ex.ExBiConsumer;
import org.qommons.io.BetterFile.CheckSumType;
import org.qommons.io.BetterFile.FileBacking;
import org.qommons.io.BetterFile.FileBooleanAttribute;
import org.qommons.io.FileUtils.DirectorySyncResults;

/** A file source that can read from {@link URL}s */
public class UrlFileSource extends RemoteFileSource {
	private final URL theRootURL;
	private UrlFileBacking theRoot;

	/** @param root The root URL path */
	public UrlFileSource(URL root) {
		theRootURL = root;
	}

	@Override
	public UrlFileSource setCheckInterval(long checkInterval) {
		super.setCheckInterval(checkInterval);
		return this;
	}

	@Override
	protected UrlFileBacking getRoot() {
		if (theRoot == null) {
			int lastSlash = theRootURL.getFile().lastIndexOf('/');
			String name = lastSlash >= 0 ? theRootURL.getFile().substring(lastSlash + 1) : theRootURL.getFile();
			theRoot = new UrlFileBacking(null, name, theRootURL);
		}
		return theRoot;
	}

	@Override
	protected RemoteFileBacking createFile(RemoteFileBacking parent, String name) {
		URL url;
		try {
			url = new URL(((UrlFileBacking) parent).getURL() + "/" + name);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("Illegal file name: " + name, e);
		}
		return new UrlFileBacking((UrlFileBacking) parent, name, url);
	}

	class UrlFileBacking extends RemoteFileBacking {
		private final URL theURL;

		private volatile boolean isRangeAccepted;

		UrlFileBacking(UrlFileBacking parent, String name, URL url) {
			super(parent, name);
			theURL = url;
		}

		public URL getURL() {
			return theURL;
		}

		@Override
		protected void queryData() throws IOException {
			URLConnection conn = theURL.openConnection();
			isRangeAccepted = "bytes".equalsIgnoreCase(conn.getHeaderField("Accept-Ranges"));
			setData(false, conn.getLastModified(), conn.getContentLengthLong());
		}

		@Override
		public UrlFileBacking getChild(String fileName) {
			return (UrlFileBacking) super.getChild(fileName);
		}

		@Override
		public boolean discoverContents(Consumer<? super FileBacking> onDiscovered, BooleanSupplier canceled) {
			return true;
		}

		@Override
		public String getCheckSum(CheckSumType type, BooleanSupplier canceled) throws IOException {
			return FileUtils.getCheckSum(() -> read(0, canceled), type, canceled);
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
					conn.setRequestProperty("Range", startFrom + "-" + (length() - 1));
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
			throw new IOException("Cannot delete a URL resource");
		}

		@Override
		public OutputStream write(boolean append) throws IOException {
			throw new IOException("Cannot write to a URL resource");
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
		public void move(List<String> newFilePath) throws IOException {
			throw new IOException("Cannot move a URL resource");
		}

		@Override
		public void visitAll(ExBiConsumer<? super FileBacking, CharSequence, IOException> forEach, BooleanSupplier canceled)
			throws IOException {
			forEach.accept(this, "");
		}

		@Override
		public int hashCode() {
			return theURL.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof UrlFileBacking && theURL.equals(((UrlFileBacking) obj).theURL);
		}

		@Override
		public String toString() {
			return theURL.toString();
		}
	}
}

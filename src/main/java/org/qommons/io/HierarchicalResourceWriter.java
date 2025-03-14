package org.qommons.io;

import java.io.IOException;
import java.io.OutputStream;

/** Acts as a directory structure for creating sources of streamed data */
public interface HierarchicalResourceWriter {
    /**
     * @param path
     *            The path of the resource to create
     * @return The stream to use to write to the resource
     * @throws IOException
     *             If an error occurs creating the resource
     */
    OutputStream writeResource(String path) throws IOException;

	/**
	 * @param subDir The sub directory to write to
	 * @return A resource writer that writes all its files relative to the given sub-directory
	 */
	default HierarchicalResourceWriter subWriter(String subDir) {
		return new SubWriter(this, subDir);
	}

	public static class SubWriter implements HierarchicalResourceWriter {
		private final HierarchicalResourceWriter theParent;
		private final String theSubPath;

		public SubWriter(HierarchicalResourceWriter parent, String subDir) {
			this.theParent = parent;
			if (subDir.endsWith("/") || subDir.endsWith("\\"))
				theSubPath = subDir;
			else
				theSubPath = subDir + "/";
		}

		public HierarchicalResourceWriter getParent() {
			return theParent;
		}

		public String getSubPath() {
			return theSubPath;
		}

		@Override
		public OutputStream writeResource(String path) throws IOException {
			return theParent.writeResource(getSubPath() + path);
		}

		@Override
		public HierarchicalResourceWriter subWriter(String subDir) {
			return new SubWriter(theParent, getSubPath() + subDir);
		}
	}
}
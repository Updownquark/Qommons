package org.qommons.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/** Acts as a directory structure for reading sources of streamed data */
public interface HierarchicalResourceReader {
	/**
	 * @param path The path of the resource to check
	 * @return Whether there is a resource at the given path that can be {@link #readResource(String) read}
	 */
	boolean resourceExists(String path);
    /**
     * @param path
     *            The path of the resources to get
     * @return The stream to use to read the resource, or null if the resource does not exist
     * @throws IOException
     *             If an error occurs accessing the resource
     */
    InputStream readResource(String path) throws IOException;

	/**
	 * @param path The path to get child directories at
	 * @return The collection of child directories in this resource matching the given path
	 * @throws IOException If an error occurs checking this resources structure
	 */
	Collection<String> getSubDirs(String path) throws IOException;

	/**
	 * @param subDir The child directory in which to get child resources
	 * @return The collection of child resources in this resource matching the given path
	 * @throws IOException If an error occurs checking this resources structure
	 */
	Collection<String> getResources(String subDir) throws IOException;

	/**
	 * @param subDir The sub directory to read from
	 * @return A resource writer that reads all its files relative to the given sub-directory
	 */
	default HierarchicalResourceReader subReader(String subDir) {
		return new SubReader(this, subDir);
	}

	public static class SubReader implements HierarchicalResourceReader {
		private final HierarchicalResourceReader theParent;
		private final String theSubPath;

		public SubReader(HierarchicalResourceReader parent, String subDir) {
			this.theParent = parent;
			if (subDir.endsWith("/") || subDir.endsWith("\\"))
				theSubPath = subDir;
			else
				theSubPath = subDir + "/";
		}

		public HierarchicalResourceReader getParent() {
			return theParent;
		}

		public String getSubPath() {
			return theSubPath;
		}

		@Override
		public boolean resourceExists(String path) {
			return theParent.resourceExists(theSubPath + path);
		}

		@Override
		public InputStream readResource(String path) throws IOException {
			return theParent.readResource(theSubPath + path);
		}

		@Override
		public Collection<String> getSubDirs(String path) throws IOException {
			return theParent.getSubDirs(path == null ? theSubPath : theSubPath + path);
		}

		@Override
		public Collection<String> getResources(String subDir2) throws IOException {
			return theParent.getResources(subDir2 == null ? theSubPath : theSubPath + subDir2);
		}

		@Override
		public HierarchicalResourceReader subReader(String subDir2) {
			return theParent.subReader(theSubPath + subDir2);
		}
	}
}
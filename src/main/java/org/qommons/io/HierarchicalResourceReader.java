package org.qommons.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/** Acts as a directory structure for reading sources of streamed data */
public interface HierarchicalResourceReader {
    /**
     * @param path
     *            The path of the resources to get
     * @return The stream to use to read the resource, or null if the resource does not exist
     * @throws IOException
     *             If an error occurs accessing the resource
     */
    InputStream readResource(String path) throws IOException;

	Collection<String> getSubDirs(String path) throws IOException;

	Collection<String> getResources(String subDir) throws IOException;

	/**
	 * @param subDir The sub directory to read from
	 * @return A resource writer that reads all its files relative to the given sub-directory
	 */
	default HierarchicalResourceReader subReader(String subDir) {
		final String subPath;
		if (subDir.endsWith("/") || subDir.endsWith("\\"))
			subPath = subDir;
		else
			subPath = subDir + "/";
		HierarchicalResourceReader outer = this;
		class SubDirReader implements HierarchicalResourceReader {
			@Override
			public InputStream readResource(String path) throws IOException {
				return outer.readResource(subPath + path);
			}

			@Override
			public Collection<String> getSubDirs(String path) throws IOException {
				return outer.getSubDirs(path == null ? subPath : subPath + path);
			}

			@Override
			public Collection<String> getResources(String subDir2) throws IOException {
				return outer.getResources(subDir2 == null ? subPath : subPath + subDir2);
			}

			@Override
			public HierarchicalResourceReader subReader(String subDir2) {
				return outer.subReader(subPath + subDir2);
			}
		}
		return new SubDirReader();
	}
}
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
		final String subPath;
		if (subDir.endsWith("/") || subDir.endsWith("\\"))
			subPath = subDir;
		else
			subPath = subDir + "/";
		HierarchicalResourceWriter outer = this;
		return path -> outer.writeResource(subPath + path);
	}
}
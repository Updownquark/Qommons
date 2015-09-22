package org.qommons.io;

import java.io.IOException;
import java.io.InputStream;

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
}
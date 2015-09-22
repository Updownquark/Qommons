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
}
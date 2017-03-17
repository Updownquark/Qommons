package org.qommons.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

/** A simple resource reader/writer that uses a directory */
public class DefaultFileSource implements HierarchicalResourceReader, HierarchicalResourceWriter {
    private final File theDir;

    /**
     * @param dir
     *            The directory for the file source
     */
    public DefaultFileSource(File dir) {
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException(dir + " is not a directory");
        }
        theDir = dir;
    }

    /** @return The directory used by this file source */
    public File getDir() {
        return theDir;
    }

    @Override
    public OutputStream writeResource(String path) throws IOException {
        File file = new File(theDir, path);
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create parent directory for " + path);
        }
        if (!file.exists()) {
            boolean made;
            try {
                made = file.createNewFile();
            } catch (IOException e) {
                throw new IOException("Could not create file " + path, e);
            }
            if (!made) {
                throw new IOException("Could not create file " + path);
            }
        }
        return new BufferedOutputStream(new FileOutputStream(file));
    }

    @Override
    public InputStream readResource(String path) throws IOException {
        File f = new File(theDir, path);
        if (!f.exists() || f.isDirectory()) {
            return null;
        }
        return new BufferedInputStream(new FileInputStream(f));
    }

    @Override
	public Collection<String> getSubDirs(String path) throws IOException {
		File subDir = new File(theDir, path);
		if (!subDir.exists() || !subDir.isDirectory())
			return Collections.emptyList();
		return Arrays.stream(subDir.listFiles()).filter(f -> f.isDirectory()).map(f -> f.getName()).collect(Collectors.toList());
	}

	@Override
	public Collection<String> getResources(String subDir) throws IOException {
		File subDirectory = new File(theDir, subDir);
		if (!subDirectory.exists() || !subDirectory.isDirectory())
			return Collections.emptyList();
		return Arrays.stream(subDirectory.listFiles()).filter(f -> !f.isDirectory()).map(f -> f.getName()).collect(Collectors.toList());
	}

	@Override
    public String toString() {
        return theDir.getPath();
    }
}
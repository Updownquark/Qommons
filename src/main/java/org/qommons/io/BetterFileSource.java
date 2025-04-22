package org.qommons.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

/** A simple resource reader/writer that uses a directory */
public class BetterFileSource implements HierarchicalResourceReader, HierarchicalResourceWriter {
	private final BetterFile theDir;

	/** @param dir The directory for the file source */
	public BetterFileSource(BetterFile dir) {
		if (!dir.isDirectory()) {
			throw new IllegalArgumentException(dir + " is not a directory");
		}
		theDir = dir;
	}

	/** @return The directory used by this file source */
	public BetterFile getDir() {
		return theDir;
	}

	@Override
	public OutputStream writeResource(String path) throws IOException {
		BetterFile file = theDir.at(path);
		BetterFile parent = file.getParent();
		if (!parent.exists()) {
			parent.create(true);
		}
		if (!file.exists()) {
			try {
				file.create(false);
			} catch (IOException e) {
				throw new IOException("Could not create file " + path, e);
			}
		}
		return new BufferedOutputStream(file.write());
	}

	@Override
	public boolean resourceExists(String path) {
		BetterFile f = theDir.at(path);
		return f.isFile();
	}

	@Override
	public InputStream readResource(String path) throws IOException {
		BetterFile f = theDir.at(path);
		if (!f.isFile()) {
			return null;
		}
		return new BufferedInputStream(f.read());
	}

	@Override
	public Collection<String> getSubDirs(String path) throws IOException {
		BetterFile subDir = theDir.at(path);
		if (!subDir.exists() || !subDir.isDirectory())
			return Collections.emptyList();
		return subDir.listFiles().stream()//
			.filter(f -> f.isDirectory())//
			.map(f -> f.getName())//
			.collect(Collectors.toList());
	}

	@Override
	public Collection<String> getResources(String subDir) throws IOException {
		BetterFile subDirectory = theDir.at(subDir);
		if (!subDirectory.exists() || !subDirectory.isDirectory())
			return Collections.emptyList();
		return subDirectory.listFiles().stream()//
			.filter(f -> !f.isDirectory())//
			.map(f -> f.getName())//
			.collect(Collectors.toList());
	}

	@Override
	public String toString() {
		return theDir.getPath();
	}
}
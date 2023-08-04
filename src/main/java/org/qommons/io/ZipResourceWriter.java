package org.qommons.io;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** A resource writer that writes its data to a zip file or stream */
public class ZipResourceWriter implements HierarchicalResourceWriter, AutoCloseable {
	/** A task done on a read-only resource */
	public static interface ResourceTask {
		/**
		 * @param reader The resource to operate on
		 * @throws IOException If an error occurs reading the resource
		 */
		void doTask(HierarchicalResourceReader reader) throws IOException;
	}

	private OutputStream theZipFile;

	private ZipOutputStream theOutput;

	private boolean hadEntry;

	/**
	 * @param stream The stream to write the zip data to
	 */
	public ZipResourceWriter(OutputStream stream) {
		theZipFile = stream;
		theOutput = new ZipOutputStream(theZipFile);
	}

	@Override
	public OutputStream writeResource(String path) throws IOException {
		path = path.replaceAll("\\\\", "/");
		if(hadEntry) {
			theOutput.closeEntry();
		}
		theOutput.putNextEntry(new ZipEntry(path));
		hadEntry = true;
		return new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				theOutput.write(b);
			}

			@Override
			public void write(byte [] b) throws IOException {
				theOutput.write(b);
			}

			@Override
			public void write(byte [] b, int off, int len) throws IOException {
				theOutput.write(b, off, len);
			}

			@Override
			public void flush() throws IOException {
				theOutput.flush();
			}

			@Override
			public void close() throws IOException {
				theOutput.closeEntry();
				hadEntry = false;
			}
		};
	}

	@Override
	public void close() throws IOException {
		if(hadEntry) {
			theOutput.closeEntry();
		}
		theOutput.close();
	}

	/**
	 * Unpacks a zip file to a temporary directory, calls the task on a resource reader pointed at the temp directory, then deletes the temp
	 * directory when the task finishes
	 *
	 * @param zipFile The zip file to unpack and operate on
	 * @param zipTask The task to perform on the unpacked archive
	 * @throws IOException If an error occurs reading the data
	 */
	public static void doZipTask(File zipFile, ResourceTask zipTask) throws IOException {
		try (InputStream zin = new BufferedInputStream(new FileInputStream(zipFile))) {
			doZipTask(zin, zipTask);
		}
	}

	/**
	 * Unpacks a zip stream to a temporary directory, calls the task on a resource reader pointed at the temp directory, then deletes the
	 * temp directory when the task finishes
	 *
	 * @param zipIn The zip stream to unpack and operate on
	 * @param zipTask The task to perform on the unpacked archive
	 * @throws IOException If an error occurs reading the data
	 */
	public static void doZipTask(InputStream zipIn, ResourceTask zipTask) throws IOException {
		File dir = Files.createTempDirectory("export-temp").toFile();
		byte[] buffer = new byte[16 * 1024];
		DefaultFileSource dfs = new DefaultFileSource(dir);
		try {
			FileUtils.extractZip(zipIn, (entry, zip) -> {
				try (OutputStream writer = dfs.writeResource(entry.getName())) {
					int read = zip.read(buffer);
					while (read > 0) {
						writer.write(buffer, 0, read);
						read = zip.read(buffer);
					}
				}
			}, null);
			zipTask.doTask(dfs);
		} finally {
			deleteDir(dir, null);
		}
	}

	private static boolean deleteDir(File dir, List<File> undeletables) throws IOException {
		if(dir == null || !dir.exists()) {
			throw new FileNotFoundException("File or directory: " + dir + " not found");
		}
		if(dir.isDirectory()) {
			for(String child : dir.list()) {
				deleteDir(new File(dir, child), undeletables);
			}
		}
		if(!dir.delete()) {
			if(undeletables != null) {
				undeletables.add(dir);
			}
		}

		// If there were any undeletable files or directories along the way, this will return false.
		return !dir.exists();
	}
}

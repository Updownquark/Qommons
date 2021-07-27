package org.qommons;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ProcessBuilder.Redirect;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.JOptionPane;

/** A class that assists an app in upgrading itself to a newer version */
public class QuarkVersionUpdater {
	private static final long MAX_DELAY_TIME = 5000;

	private static final String UPDATER_MANIFEST = "Manifest-Version: 1.0"//
			+ "\nMain-Class: " + QuarkVersionUpdater.class.getName()//
			+ "\nClass-Path: ." //
			+ "\n";
	private static final String PRESERVE_UPDATED_PROPERTY = "quark.vu.preserve";

	private boolean isHeadless;
	private boolean isDeleteUpdated;
	private List<String> theProgramArgs;

	/** Creates the updater */
	public QuarkVersionUpdater() {
		isDeleteUpdated = true;
	}

	/** @return Whether the update will occur headlessly */
	public boolean isHeadless() {
		return isHeadless;
	}

	/**
	 * @param headless Whether the update should occur headlessly
	 * @return This updater
	 */
	public QuarkVersionUpdater headless(boolean headless) {
		isHeadless = headless;
		return this;
	}

	/** @return Whether the update will delete the updated jar (e.g. for a temp file downloaded from a server) */
	public boolean isDeleteUpdated() {
		return isDeleteUpdated;
	}

	/**
	 * @param deleteUpdated Whether the update should delete the updated jar (e.g. for a temp file downloaded from a server)
	 * @return This updater
	 */
	public QuarkVersionUpdater deleteUpdated(boolean deleteUpdated) {
		isDeleteUpdated = deleteUpdated;
		return this;
	}

	/**
	 * @param args Program arguments for the application
	 * @return This updater
	 */
	public QuarkVersionUpdater withProgramArgs(String... args) {
		if (theProgramArgs == null)
			theProgramArgs = new ArrayList<>();
		theProgramArgs.addAll(Arrays.asList(args));
		return this;
	}

	/**
	 * Main method. Invoked by this class (from another process) to do the upgrade and start the new version of the app.
	 * 
	 * This method deletes the old version of the jar, renames the updated version of the jar to the name of the old version, and invokes
	 * the updated jar with an environment variable "show.app.version"=true so the app can optionally show its Help->About dialog on
	 * startup.
	 * 
	 * @param args Command line args:
	 *        <ol>
	 *        <li>The path to the jar which is the old version of the app</li>
	 *        <li>The path to the jar which is the new version of the app</li>
	 *        </ol>
	 */
	public static void main(String... args) {
		File oldVersion = new File(args[0]);
		File newVersion = new File(args[1]);
		long start = System.currentTimeMillis();
		IOException ex = null;
		boolean success = false;
		long lastMod = newVersion.lastModified();
		boolean copy = "true".equalsIgnoreCase(System.getProperty(PRESERVE_UPDATED_PROPERTY));
		do {
			if (!oldVersion.exists() || oldVersion.delete()) {
				try {
					if (copy)
						Files.copy(newVersion.toPath(), oldVersion.toPath());
					else
						Files.move(newVersion.toPath(), oldVersion.toPath());
					success = true;
				} catch (IOException e) {
					ex = e;
				}
			}
		} while (!success && System.currentTimeMillis() - start < MAX_DELAY_TIME);
		oldVersion.setLastModified(lastMod);
		if (success) {
			try {
				List<String> command = new ArrayList<>();
				command.add("java");
				command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
				command.addAll(Arrays.asList("-Dshow.app.version=true", "-jar", args[0]));
				if (args.length > 2)
					command.addAll(Arrays.asList(args).subList(2, args.length));
				new ProcessBuilder(command).start();
			} catch (IOException e) {
				e.printStackTrace();
				if (!"true".equals(System.getProperty("java.awt.headless")))
					JOptionPane.showMessageDialog(null, "Could not restart " + args[0], "Could not restart app", JOptionPane.ERROR_MESSAGE);
			}
		} else {
			if (ex != null) {
				ex.printStackTrace();
			}
			if (!"true".equals(System.getProperty("java.awt.headless"))) {
				StringBuilder msg = new StringBuilder();
				msg.append("Unable to replace the application with updated version.<br>");
				if (oldVersion.exists()) {
					msg.append("Please delete ").append(oldVersion.getAbsolutePath()).append("\n and then ");
				} else {
					msg.append("Please ");
				}
				msg.append(copy ? "copy " : "rename ").append(newVersion.getAbsolutePath()).append(" to ").append(oldVersion.getName());
				JOptionPane.showMessageDialog(null, "Update partial failure", msg.toString(), JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	/**
	 * Invoked by an application to update itself
	 * 
	 * @param jarFile The jar file of the application that is currently running
	 * @param updatedJarFile The jar file that is the updated application, presumably obtained from a published source
	 * @throws IOException If preparations to upgrade could not be made
	 */
	public void update(File jarFile, File updatedJarFile) throws IOException {
		// Extract this class into a standalone class file
		File standalone = new File(jarFile.getParentFile(), QuarkVersionUpdater.class.getSimpleName() + ".jar");
		String classFileName = QuarkVersionUpdater.class.getName();
		int dotIdx = classFileName.lastIndexOf('.');
		if (dotIdx >= 0) {
			classFileName = classFileName.substring(dotIdx + 1);
		}
		classFileName += ".class";
		URL resource = QuarkVersionUpdater.class.getResource(classFileName);
		if (resource == null) {
			throw new IllegalStateException("Could not find class file for " + QuarkVersionUpdater.class.getName());
		}
		String classPath = QuarkVersionUpdater.class.getName().replaceAll("\\.", "/") + ".class";
		try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(standalone))) {
			zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zip));
			writer.append(UPDATER_MANIFEST);
			writer.flush();
			zip.putNextEntry(new ZipEntry(classPath));
			copy(resource.openStream(), zip);
		}

		List<String> args = new ArrayList<>();
		args.add("java");
		// args.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8001");
		args.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
		if (isHeadless)
			args.add("-Djava.awt.headless=true");
		if (!isDeleteUpdated)
			args.add("-D" + PRESERVE_UPDATED_PROPERTY + "=true");
		args.add("-jar");
		args.add(standalone.getPath());
		args.add(jarFile.getPath());
		args.add(updatedJarFile.getPath());
		if (theProgramArgs != null)
			args.addAll(theProgramArgs);
		Process process = new ProcessBuilder(args).redirectOutput(Redirect.INHERIT).redirectError(Redirect.INHERIT).start();
		Thread delay = new Thread(() -> {
			while (true) {
				try {
					process.getOutputStream().write(1);
					process.getOutputStream().flush();
				} catch (IOException e) {
				}
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
				}
			}
		}, QuarkVersionUpdater.class.getSimpleName() + " Delay");
		delay.start();
		System.exit(0);
	}

	/**
	 * Deletes the updater jar, if it exists
	 * 
	 * @param jarFile The jar file of the app that may have been updated
	 */
	public static void deleteUpdater(File jarFile) {
		File standalone = new File(jarFile.getParentFile(), QuarkVersionUpdater.class.getSimpleName() + ".jar");
		if (standalone.exists()) {
			standalone.delete();
		}
	}

	private static void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[64 * 1028];
		int read = in.read(buffer);
		while (read >= 0) {
			out.write(buffer, 0, read);
			read = in.read(buffer);
		}
	}
}

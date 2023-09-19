package org.qommons;

import java.awt.BorderLayout;
import java.awt.HeadlessException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.Timer;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.qommons.config.QommonsConfig;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.ex.ExBiConsumer;
import org.qommons.ex.ExConsumer;
import org.qommons.io.BetterFile;
import org.qommons.io.CountingInputStream;
import org.qommons.io.FileUtils;
import org.qommons.io.MiniFileUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

/** A class for creating and applying patches to applications */
public class QuarkJarPatcher {
	private static final String PATCH_MANIFEST = "Manifest-Version: 1.0"//
		+ "\nMain-Class: " + QuarkJarPatcher.class.getName()//
		+ "\nClass-Path: ." //
		+ "\n";

	/**
	 * 
	 * @return All classes which must be bundled into patches--those needed by the {@link #applyPatch()} method. This list should be kept to
	 *         a minimum to keep the size of patch files small. This is a static method instead of a static constant so the patch-bundled
	 *         class doesn't need to include QommonsUtils.
	 */
	private static Map<Class<?>, String> getBundledClasses() {
		return QommonsUtils.<Class<?>, String> buildMap(null)//
			.with(Patch.class, "").with(PatchFileSet.class, "")//
			.with(CountingInputStream.class, "")//
			.with(MiniFileUtils.class, "Zip extraction utility class")//
			.with(MiniFileUtils.ArchiveEntry.class, "").with(MiniFileUtils.ArchiveEntry.Default.class, "")//
			.with(ExBiConsumer.class, "").with(ExConsumer.class, "").with(CheckedExceptionWrapper.class, "")//
			.getUnmodifiable();
	}

	/**
	 * @param clArgs Command-line arguments:
	 *        <ul>
	 *        <li>If empty, this class will look for a bundled patch configuration and apply it to the locally-installed application.</li>
	 *        <li>If length 1, this is assumed to be the location of a patch configuration file to create a patch for</li>
	 *        </ul>
	 */
	public static void main(String... clArgs) {
		if (clArgs.length == 0)
			applyPatch();
		else if (clArgs.length == 1) {
			try {
				System.out.println("Created patch " + createPatch(clArgs[0]));
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else
			throw new IllegalArgumentException(
				"To create a patch specify the location of the '*.patch' file as the only command-line argument");
	}

	public static class Patch {
		private final String theAppName;
		private final String theTargetVersion;
		private final String thePatchName;
		private final String thePatchAuthor;
		private final String thePatchDate;
		private final String thePatchDescription;
		private final List<PatchFileSet> thePatchContents;

		public Patch(String appName, String targetVersion, String patchName, String author, String patchDate, String patchDescription,
			List<PatchFileSet> patchContents) {
			theAppName = appName;
			theTargetVersion = targetVersion;
			thePatchName = patchName;
			thePatchAuthor = author;
			thePatchDate = patchDate;
			thePatchDescription = patchDescription;
			thePatchContents = patchContents;
		}

		public String getAppName() {
			return theAppName;
		}

		public String getTargetVersion() {
			return theTargetVersion;
		}

		public String getPatchName() {
			return thePatchName;
		}

		public String getPatchAuthor() {
			return thePatchAuthor;
		}

		public String getPatchDate() {
			return thePatchDate;
		}

		public String getPatchDescription() {
			return thePatchDescription;
		}

		public List<PatchFileSet> getPatchContents() {
			return thePatchContents;
		}
	}

	public static class PatchFileSet {
		private final String theUpdateTarget;
		private final String theSourceDir;
		private final List<String> theFileSetContents;

		public PatchFileSet(String updateTarget, String sourceDir, List<String> fileSetContents) {
			theUpdateTarget = updateTarget;
			theSourceDir = sourceDir;
			theFileSetContents = fileSetContents;
		}

		public String getUpdateTarget() {
			return theUpdateTarget;
		}

		public String getSourceDir() {
			return theSourceDir;
		}

		public List<String> getFileSetContents() {
			return theFileSetContents;
		}
	}

	public static void applyPatch() {
		// IMPORTANT!! This method uses very few java-external utilities, including other Qommons classes,
		// because they are not packaged in the patch file for size
		URL classFile = QuarkJarPatcher.class.getResource(QuarkJarPatcher.class.getSimpleName() + ".class");

		JDialog dialog = null;
		JEditorPane description = null;
		String[] status = new String[2];
		int[] progress = new int[2];
		boolean[] uiDirty = new boolean[1];
		boolean[] finished = new boolean[1];
		try {
			dialog = new JDialog((JDialog) null, "Applying Patch", false);
			dialog.getContentPane().setLayout(new BoxLayout(dialog.getContentPane(), BoxLayout.Y_AXIS));
			description = new JEditorPane();
			description.setEditable(false);
			description.setContentType("text/html");
			dialog.getContentPane().add(description);
			JPanel panel = new JPanel(new BorderLayout());
			dialog.getContentPane().add(panel);
			JLabel patchAction = new JLabel();
			panel.add(patchAction, BorderLayout.CENTER);
			JProgressBar progressBar = new JProgressBar(JProgressBar.HORIZONTAL, 1000);
			dialog.getContentPane().add(progressBar);

			patchAction.setText("Extracting patch");
			progressBar.setIndeterminate(true);
			dialog.setSize(600, 400);
			dialog.setLocationRelativeTo(null);
			dialog.setVisible(true);

			JDialog fDialog = dialog;
			new Timer(100, evt -> {
				if (!fDialog.isVisible())
					System.exit(0); // User closed the dialog--cancel
				if (!uiDirty[0])
					return;
				uiDirty[0] = false;
				patchAction.setText(status[0]);
				progressBar.setStringPainted(status[1] != null);
				progressBar.setString(status[1]);
				if (progress[1] <= 0 || progress[0] < 0)
					progressBar.setIndeterminate(true);
				else {
					progressBar.setIndeterminate(false);
					progressBar.setMaximum(progress[1]);
					progressBar.setValue(progress[0]);
				}
			}).start();
		} catch (HeadlessException e) {
			// Ok, just means we can't give graphical progress or ask the user for input
		}

		Map<String, File> extractedFiles = new HashMap<>();
		try {
			// Read the patch configuration and extract the patch contents
			Patch[] patch = new Patch[1];
			byte[] buffer = new byte[256 * 1024];
			try {
				String path = classFile.getPath();
				int slash = path.lastIndexOf('/'); // Slash after qommons
				slash = path.lastIndexOf('/', slash - 1); // Slash after org
				slash = path.lastIndexOf('/', slash - 1); // Should be the jar location
				int offset = 0;
				if (path.charAt(slash - 1) == '!') {
					// Jar bang, expected. Strip it and the "jar:" prefix
					slash--;
					offset += 4;
				}
				String zipUrl = classFile.toString();
				zipUrl = zipUrl.substring(offset, zipUrl.length() - path.length() + slash);
				File tempDir = Files.createTempDirectory("QuarkJarPatcher").toFile();
				try (InputStream zipIn = new URL(zipUrl).openStream()) {
					String fZipUrl = zipUrl;
					Set<String> patchContents = new HashSet<>();
					JDialog fDialog = dialog;
					JEditorPane fDescrip = description;
					MiniFileUtils.extractZip(zipIn, tempDir, (f, p) -> {
						if (patch[0] == null) {
							if (!p.endsWith(".patch"))
								throw new IOException("Expected *.patch configuration file as first entry in " + fZipUrl);
							status("Reading patch configuration", null, -1, status, progress, uiDirty);
							Element patchRoot;
							try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
								patchRoot = DocumentBuilderFactory.newInstance()//
									.newDocumentBuilder()//
									.parse(in)//
									.getDocumentElement();
							} catch (ParserConfigurationException | SAXException e) {
								throw new IOException("Could not read XML", e);
							}
							patch[0] = parsePatch(patchRoot);
							f.delete();

							System.out.println("Read configuration for " + patch[0].getAppName() + " patch " + patch[0].getPatchName());
							System.out.println("By " + patch[0].getPatchAuthor() + " " + patch[0].getPatchDate());
							System.out.println(patch[0].getPatchDescription());
							if (fDialog != null) {
								fDialog.setTitle("Applying " + patch[0].getAppName() + " patch " + patch[0].getPatchName());
								fDescrip.setText("<html>For " + patch[0].getAppName() + " " + patch[0].getTargetVersion() + "<br>"//
									+ "By " + patch[0].getPatchAuthor() + " " + patch[0].getPatchDate() + "<br>"//
									+ patch[0].getPatchDescription());
							}
							status("Extracting patch contents", null, -1, status, progress, uiDirty);

							for (PatchFileSet fs : patch[0].getPatchContents())
								patchContents.addAll(fs.getFileSetContents());
							progress[0] = 0;
							progress[1] = patchContents.size();
							status(null, null, 0, status, progress, uiDirty);
						} else {
							status(null, p, progress[0], status, progress, uiDirty);

							if (patchContents.remove(p)) {
								extractedFiles.put(p, f);
							} else
								f.delete();
						}
					});
					if (!patchContents.isEmpty()) {
						System.err.println("Missing contents: " + patchContents);
						throw new IOException("Patch file is missing required contents");
					}
				}
			} catch (Throwable e) {
				e.printStackTrace();
				if (dialog != null) {
					JOptionPane.showMessageDialog(dialog, e.getMessage(), "Failed to extract patch", JOptionPane.ERROR_MESSAGE);
				}
				return;
			}

			File installDir = new File(System.getProperty("user.dir"));
			if (dialog != null) {
				// Ask the user for the installation directory
				progress[1] = 0;
				status("Requesting installation directory", null, 0, status, progress, uiDirty);
				JFileChooser chooser = new JFileChooser(installDir);
				chooser.setDialogTitle("Select " + patch[0].getAppName() + " installation directory to patch");
				chooser.setDialogType(JFileChooser.OPEN_DIALOG);
				chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				chooser.setFileHidingEnabled(false);
				if (chooser.showDialog(dialog, "Select") != JFileChooser.APPROVE_OPTION) {
					System.out.println("User cancelled patch application");
					return;
				}
				installDir = chooser.getSelectedFile();
			}

			// Apply the patch
			try {
				status("Locating patch targets", null, 0, status, progress, uiDirty);
				// First, find the patch targets
				File[] targets = new File[patch[0].getPatchContents().size()];
				long totalLength = 0;
				for (int i = 0; i < targets.length; i++) {
					if (patch[0].getPatchContents().get(i).getUpdateTarget() == null) {
						targets[i] = installDir;
						for (String content : patch[0].getPatchContents().get(i).getFileSetContents())
							totalLength += extractedFiles.get(content).length();
					} else {
						targets[i] = new File(installDir, patch[0].getPatchContents().get(i).getUpdateTarget());
						if (!targets[i].exists())
							throw new IOException("Patch target not found: " + targets[i].getAbsolutePath());
						else if (!targets[i].canWrite())
							throw new IOException("Patch target not writable: " + targets[i].getAbsolutePath());
						totalLength += targets[i].length();
					}
				}
				if (totalLength == 0)
					throw new IllegalStateException("No patch targets or empty ones!");

				progress[1] = 1000;
				// Apply each patch file set
				long fileContentSoFar = 0;
				for (int i = 0; i < targets.length; i++) {
					status("Applying patch target " + targets[i].getName(), null, Math.round(fileContentSoFar * 1000.0f / totalLength),
						status, progress, uiDirty);
					if (targets[i].isDirectory()) { // Just replace the target files
						for (String content : patch[0].getPatchContents().get(i).getFileSetContents()) {
							status[1] = content;
							File targetFile = new File(targets[i], content);
							File parent = targetFile.getParentFile();
							if (!parent.exists() && !parent.mkdirs())
								throw new IOException("Could not create " + parent.getAbsolutePath());
							File patchFile = extractedFiles.remove(content);
							try (
								CountingInputStream in = new CountingInputStream(
									new BufferedInputStream(new FileInputStream(patchFile))); //
								OutputStream out = new BufferedOutputStream(new FileOutputStream(targetFile))) {
								int read = in.read(buffer);
								while (read >= 0) {
									out.write(buffer, 0, read);
									status(null, content, Math.round((fileContentSoFar + in.getPosition()) * 1000.0f / totalLength), status,
										progress, uiDirty);
									read = in.read(buffer);
								}
							} catch (IOException e) {
								throw new IOException("Could not write " + targetFile.getAbsolutePath(), e);
							} finally {
								patchFile.delete();
							}
							fileContentSoFar += targetFile.length();
						}
					} else { // Replace the entire zip file with a new one with target entries replaced
						String targetName = targets[i].getName();
						int lastDot = targetName.lastIndexOf('.');
						File replacement = File.createTempFile(//
							lastDot >= 0 ? targetName.substring(0, lastDot) : targetName, //
							lastDot >= 0 ? targetName.substring(lastDot) : null);
						// For progress, assume 90% of the work is parsing the target zip file and creating the replacement
						try (
							CountingInputStream targetIn = new CountingInputStream(
								new BufferedInputStream(new FileInputStream(targets[i])));
							ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(replacement)))) {
							long fcsf = fileContentSoFar, tl = totalLength;
							MiniFileUtils.extractZip(targetIn, entry -> {
								status[1] = entry.getPath();
								status(null, entry.getPath(), progress[0], status, progress, uiDirty);
								File patchFile = extractedFiles.remove(entry.getPath());
								ZipEntry zipEntry = new ZipEntry(entry.getPath());
								if (patchFile != null) {
									zipEntry.setLastModifiedTime(FileTime.fromMillis(patchFile.lastModified()));
									zipOut.putNextEntry(zipEntry);
									// The Snyk vulnerability testing tool is flagging this as vulnerable to zip-slip
									// This is incorrect. All zip access occurring in this file is using the extractZip methods
									// in Qommons FileUtils, which check the paths of all entries.
									// But no matter what I do here, I can't suppress the error from Snyk.
									try (InputStream in = new BufferedInputStream(new FileInputStream(patchFile))) {
										int read = in.read(buffer);
										while (read >= 0) {
											zipOut.write(buffer, 0, read);
											read = in.read(buffer);
										}
									}
									patchFile.delete();
								} else if (!entry.isDirectory()) {
									zipEntry.setLastModifiedTime(FileTime.fromMillis(entry.getLastModified()));
									zipOut.putNextEntry(zipEntry);
									MiniFileUtils.copy(entry.getContent(), zipOut);
								}
								status(null, entry.getPath(),
									Math.round((fcsf + targetIn.getPosition() * 0.9f) * 1000.0f / tl), status,
									progress, uiDirty);
							}, null);
							// Now insert added files
							for (Map.Entry<String, File> file : extractedFiles.entrySet()) {
								File patchFile = file.getValue();
								ZipEntry entry = new ZipEntry(file.getKey());
								status[1] = entry.getName();
								status(null, entry.getName(), progress[0], status, progress, uiDirty);
								entry.setLastModifiedTime(FileTime.fromMillis(patchFile.lastModified()));
								zipOut.putNextEntry(entry);
								try (InputStream in = new BufferedInputStream(new FileInputStream(patchFile))) {
									int read = in.read(buffer);
									while (read >= 0) {
										zipOut.write(buffer, 0, read);
										read = in.read(buffer);
									}
								}
								patchFile.delete();
								status(null, entry.getName(),
									Math.round((fileContentSoFar + targetIn.getPosition() * 0.9f) * 1000.0f / totalLength), status,
									progress, uiDirty);
							}
						}
						// The remaining 10% is replacing the target zip file
						long targetLen = targets[i].length();
						int progressOffset = Math.round((fileContentSoFar + targetLen * .9f) * 1000.0f / totalLength);
						float progressMult = replacement.length() * 0.1f * 1000.0f / targetLen / totalLength;
						String status0 = "Replacing target file " + targets[i].getName();
						status(null, status0, progress[0], status, progress, uiDirty);
						try (CountingInputStream in = new CountingInputStream(new BufferedInputStream(new FileInputStream(replacement))); //
							OutputStream out = new BufferedOutputStream(new FileOutputStream(targets[i]))) {
							int read = in.read(buffer);
							while (read >= 0) {
								out.write(buffer, 0, read);
								status(null, status0, Math.round(progressOffset + in.getPosition() * progressMult), status, progress,
									uiDirty);
								read = in.read(buffer);
							}
						}
						fileContentSoFar += targetLen;
					}
				}
			} catch (Throwable e) {
				e.printStackTrace();
				if (dialog != null)
					JOptionPane.showMessageDialog(dialog, e.getMessage(), "Failed to apply patch", JOptionPane.ERROR_MESSAGE);
				return;
			}
			if (dialog != null)
				JOptionPane.showMessageDialog(dialog, "Patch applied successfully", "Patch Applied Successfully",
					JOptionPane.INFORMATION_MESSAGE);
			progress[1] = 0;
			status("Finishing up", null, 0, status, progress, uiDirty);
		} finally {
			finished[0] = true;
			System.exit(0);
		}
	}

	private static void status(String newStatus, String newSubStatus, int newProgress, String[] status, int[] progress, boolean[] uiDirty) {
		if (newStatus != null && newStatus != status[0]) {
			System.out.println();
			System.out.print(newStatus);
			status[0] = newStatus;
			progress[0] = 0;
			uiDirty[0] = true;
		}
		if (status[1] != newSubStatus) {
			status[1] = newSubStatus;
			uiDirty[0] = true;
		}
		if (progress[1] > 0) {
			int oldProgressPct = Math.round(progress[0] * 100.0f / progress[1]);
			int newProgressPct = Math.round(newProgress * 100.0f / progress[1]);
			if (newProgressPct > oldProgressPct) {
				while (newProgressPct > oldProgressPct) {
					oldProgressPct++;
					if (oldProgressPct % 10 == 0)
						System.out.print(oldProgressPct + "%");
					else
						System.out.print('.');
				}
				System.out.flush();
			}
			progress[0] = newProgress;
			uiDirty[0] = true;
		}
	}

	public static File createPatch(String patchFileLocation) throws IOException {
		// This method is free to use any utilities it needs, as this isn't called from the patch installation
		BetterFile patchConfigFile;
		InputStream patchStream = null;
		Patch patch;
		try {
			if (Pattern.matches("[a-z]{2,}://.+", patchFileLocation)) {// URL
				URL url = new URL(patchFileLocation);
				String patchFileName = url.getPath();
				int lastSlash = patchFileName.lastIndexOf('/');
				if (lastSlash > 0)
					patchFileName = patchFileName.substring(lastSlash + 1);
				patchConfigFile = FileUtils.ofUrl(url);
			} else {
				File file = new File(patchFileLocation);
				patchConfigFile = FileUtils.better(file);
			}
			patch = parsePatch(QommonsConfig.getRootElement(new BufferedInputStream(patchStream = patchConfigFile.read())));
		} catch (FileNotFoundException e) {
			throw new IOException("No such patch file found: " + patchFileLocation, e);
		} catch (IOException e) {
			throw new IOException("Could not read/parse patch file " + patchFileLocation, e);
		} catch (IllegalArgumentException e) {
			throw new IOException("Could not parse patch file " + patchFileLocation, e);
		} finally {
			if (patchStream != null) {
				try {
					patchStream.close();
				} catch (IOException e) {
				}
			}
		}
		String patchFileName = patchConfigFile.getName();
		if (!patchFileName.endsWith(".patch")) { // Needed to be recognized by the patch application code
			patchFileName += ".patch";
		}
		File patchFile = new File(patchFileName + ".jar"); // Create patch file in current working dir
		try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(patchFile)))) {
			// First, the patch file itself
			ZipEntry entry = new ZipEntry(patchFileName); // Put in the root of the patch file
			entry.setLastModifiedTime(FileTime.fromMillis(patchConfigFile.getLastModified()));
			entry.setComment("The specification file for the patch");
			zip.putNextEntry(entry);
			try (InputStream in = patchConfigFile.read()) {
				MiniFileUtils.copy(in, zip);
			}

			// Now this class file and the few dependency classes we need
			BetterFile patcherClassFile = bundleClass(QuarkJarPatcher.class, "The patch file application class", zip);
			for (Map.Entry<Class<?>, String> dependency : getBundledClasses().entrySet())
				bundleClass(dependency.getKey(), dependency.getValue(), zip);

			// Manifest file so this class is executed as the jar's main class
			entry = new ZipEntry("META-INF/MANIFEST.MF");
			entry.setLastModifiedTime(FileTime.fromMillis(System.currentTimeMillis()));
			entry.setComment("Jar Manifest file");
			zip.putNextEntry(entry);
			Writer w = new OutputStreamWriter(zip);
			w.write(PATCH_MANIFEST);
			w.flush();

			// Now the actual patch contents
			BetterFile searchRoot = patcherClassFile.getParent().getParent().getParent(); // class root
			if (!searchRoot.getName().endsWith(".jar")) // If we're not in a jar, use the Qommons project root
				searchRoot = searchRoot.getParent().getParent().getParent(); // Qommons/target/classes
			for (PatchFileSet fileSet : patch.getPatchContents()) {
				BetterFile fileSetRoot = fileSet.getSourceDir() == null ? null : searchRoot.at(fileSet.getSourceDir());
				if (fileSetRoot != null && !fileSetRoot.isDirectory())
					throw new IllegalArgumentException("No directory found for patch file set at " + fileSetRoot.getPath());
				for (String content : fileSet.getFileSetContents()) {
					BetterFile found;
					try {
						if (fileSetRoot != null)
							found = fileSetRoot.at(content);
						else {
							URL foundUrl = QuarkJarPatcher.class.getResource("/" + content); // See if it's on the classpath
							if (foundUrl == null)
								throw new FileNotFoundException("No such resource on classpath");
							found = FileUtils.ofUrl(foundUrl);
						}
					} catch (IOException e) {
						throw new IOException(
							"Could not locate or read resource '" + content + "' for patch file set " + fileSet.getUpdateTarget(), e);
					}
					entry = new ZipEntry(content);
					entry.setLastModifiedTime(FileTime.fromMillis(found.getLastModified()));
					zip.putNextEntry(entry);
					try (InputStream in = found.read()) {
						MiniFileUtils.copy(in, zip);
					}
				}
			}
			return patchFile;
		} catch (IOException e) {
			throw new IOException("Patch creation failed", e);
		}
	}

	private static BetterFile bundleClass(Class<?> clazz, String comment, ZipOutputStream zip) throws IOException {
		BetterFile classFile = FileUtils.getClassFile(clazz);
		// We'll just assume these classes aren't in the default package
		String dir = clazz.getName().substring(0, clazz.getName().lastIndexOf('.') + 1).replace(".", "/");
		ZipEntry entry = new ZipEntry(dir + classFile.getName());
		entry.setLastModifiedTime(FileTime.fromMillis(classFile.getLastModified()));
		if (comment != null && !comment.isEmpty())
			entry.setComment(comment);
		zip.putNextEntry(entry);
		try (InputStream in = classFile.read()) {
			MiniFileUtils.copy(in, zip);
		}
		return classFile;
	}

	private static String className(String name) {
		int dot = name.lastIndexOf('.');
		if (dot >= 0)
			name = name.substring(dot + 1);
		return name;
	}

	public static final Patch parsePatch(Element rootElement) throws IllegalArgumentException {
		if (!"patch".equals(rootElement.getNodeName()))
			throw new IllegalArgumentException("Expected 'patch' as root element, not '" + rootElement.getNodeName() + "'");
		String appName = rootElement.getAttribute("app-name");
		String targetVersion = rootElement.getAttribute("target-version");
		String patchName = rootElement.getAttribute("patch-name");
		String author = rootElement.getAttribute("author");
		String date = rootElement.getAttribute("patch-date");
		String description = getElementText(rootElement);
		List<PatchFileSet> contents = new ArrayList<>();
		for (int i = 0; i < rootElement.getChildNodes().getLength(); i++) {
			Node child = rootElement.getChildNodes().item(i);
			switch (child.getNodeType()) {
			case Node.ELEMENT_NODE:
				if ("file-set".equals(child.getNodeName())) {
					contents.add(parseFileSet((Element) child));
				} else
					throw new IllegalArgumentException("Unexpected element '" + child.getNodeName() + " in patch configuration");
				break;
			case Node.TEXT_NODE:
				if (!((Text) child).isElementContentWhitespace())
					description += ((Text) child).getWholeText();
				break;
			}
		}
		return new Patch(appName, targetVersion, patchName, author, date, description, Collections.unmodifiableList(contents));
	}

	private static PatchFileSet parseFileSet(Element element) {
		String target = element.getAttribute("target");
		String source = element.getAttribute("source");
		List<String> contents = new ArrayList<>();
		for (int i = 0; i < element.getChildNodes().getLength(); i++) {
			Node child = element.getChildNodes().item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				if ("file".equals(child.getNodeName())) {
					contents.add(getElementText((Element) child));
				} else
					throw new IllegalArgumentException(
						"Unexpected element '" + child.getNodeName() + " in file-set of patch configuration");
			}
		}
		return new PatchFileSet(target, source, Collections.unmodifiableList(contents));
	}

	private static String getElementText(Element element) {
		String content = "";
		for (int i = 0; i < element.getChildNodes().getLength(); i++) {
			Node child = element.getChildNodes().item(i);
			if (child.getNodeType() == Node.TEXT_NODE) {
				if (!((Text) child).isElementContentWhitespace())
					content += ((Text) child).getWholeText();
			}
		}
		return content;
	}
}

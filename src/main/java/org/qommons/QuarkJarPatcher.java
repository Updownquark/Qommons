package org.qommons;

import java.awt.BorderLayout;
import java.awt.HeadlessException;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.*;
import javax.swing.Timer;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.qommons.config.QommonsConfig;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.ex.ExBiConsumer;
import org.qommons.ex.ExConsumer;
import org.qommons.ex.ExRunnable;
import org.qommons.io.BetterFile;
import org.qommons.io.CountingInputStream;
import org.qommons.io.FileUtils;
import org.qommons.io.MiniFileUtils;
import org.qommons.io.XmlSerialWriter;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

/**
 * <p>
 * A class for creating and applying patches to applications.
 * </p>
 * <p>
 * The use case for this class is a very small modification (consisting of changes to and/or additions of a small number of classes or other
 * files) to a large application. The patch files created by this class are extremely small--currently less than 30KB in addition to the
 * (compressed) application files needed for the patched functionality. These files can usually be sent over email (though, being executable
 * jar files, they are sometimes blocked by email filters and firewalls), as opposed to sending a new version of the application, which may
 * require large uploads and downloads using commercial applications facilitating transfer of large files.
 * </p>
 * <p>
 * The patch files created by this class are executable jar files. To apply them, the end user should:
 * <ol>
 * <li>Shut down the application if it is running.</li>
 * <li>Execute the patch file (e.g. via double-click) on the system where the application is installed. (The Java runtime environment (or
 * SDK) must be installed on the system as well.)</li>
 * <li>In the file chooser the patch shows, select the installation directory of the application (the patch file may contain one or more
 * initial guesses for the parent file of the installation directory).</li>
 * <li>After the patch apples itself, restart the application.</li>
 * </ol>
 * </p>
 * <p>
 * To create a patch:
 * <ol>
 * <li>Create an XML file with extension ".patch". This patch file contains the list of files to add or replace in the installed
 * application, including the resource location in the source environment (where the patch creation is executing). An example patch file is
 * provided co-located with this source file.</li>
 * <li>Run the main method for this class, with a single command-line argument that is the location of the patch file.</li>
 * <li>This class will create a file with the same name as the patch file with ".jar" appended in the location where this class is executed
 * from (not necessarily co-located with the patch file).</li>
 * <li>Send the patch file to the end user with the above instructions for applying it.</li>
 * </ol>
 * </p>
 */
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
			.with(Patch.class, "").with(PatchFileSet.class, "").with(PatchFile.class, "")//
			.with(CountingInputStream.class, "")//
			.with(MiniFileUtils.class, "Zip extraction utility class")//
			.with(MiniFileUtils.ArchiveEntry.class, "").with(MiniFileUtils.ArchiveEntry.Default.class, "")//
			.with(ExBiConsumer.class, "").with(ExConsumer.class, "").with(ExConsumer.DO_NOTHING.getClass(), "").with(ExRunnable.class, "")//
			.with(CheckedExceptionWrapper.class, "")//
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

	/** Represents a patch to be applied to an application */
	public static class Patch {
		/** The name of the XML element that should be the root of a patch file */
		public static final String PATCH = "patch";
		/** The name of the XML attribute where the name of the application should be specified */
		public static final String APP_NAME = "app-name";
		/** The name of the XML attribute where the version of the application should be specified */
		public static final String TARGET_VERSION = "target-version";
		/** The name of the XML attribute where the name of the patch should be specified */
		public static final String PATCH_NAME = "patch-name";
		/** The name of the XML attribute where the author of the patch should be specified */
		public static final String AUTHOR = "author";
		/** The name of the XML attribute where the date of the patch should be specified */
		public static final String PATCH_DATE = "patch-date";
		/** The name of the XML elements where file locations to look for installed application directories may be specified */
		public static final String LOOK_IN = "look-in";

		private final String theAppName;
		private final String theTargetVersion;
		private final String thePatchName;
		private final String thePatchAuthor;
		private final String thePatchDate;
		private final String thePatchDescription;
		private final List<PatchFileSet> thePatchContents;
		private final List<String> theLookInDirs;

		/**
		 * @param appName The name of the application to patch
		 * @param targetVersion The version of the application that this patch should be applied to
		 * @param patchName The name of this patch
		 * @param author The author of this patch
		 * @param patchDate The date this patch was created
		 * @param patchDescription A description of this patch
		 * @param patchContents The file contents of the patch-the modifications to make to effect the change that is the purpose of this
		 *        patch
		 * @param lookInDirs The directories to look in for installations where the patch may apply
		 */
		public Patch(String appName, String targetVersion, String patchName, String author, String patchDate, String patchDescription,
			List<PatchFileSet> patchContents, List<String> lookInDirs) {
			theAppName = appName;
			theTargetVersion = targetVersion;
			thePatchName = patchName;
			thePatchAuthor = author;
			thePatchDate = patchDate;
			thePatchDescription = patchDescription;
			thePatchContents = patchContents;
			theLookInDirs = lookInDirs;
		}

		/** @return The name of the application to patch */
		public String getAppName() {
			return theAppName;
		}

		/** @return The version of the application that this patch should be applied to */
		public String getTargetVersion() {
			return theTargetVersion;
		}

		/** @return The name of this patch */
		public String getPatchName() {
			return thePatchName;
		}

		/** @return The author of this patch */
		public String getPatchAuthor() {
			return thePatchAuthor;
		}

		/** @return The date this patch was created */
		public String getPatchDate() {
			return thePatchDate;
		}

		/** @return A description of this patch */
		public String getPatchDescription() {
			return thePatchDescription;
		}

		/** @return The file contents of the patch-the modifications to make to effect the change that is the purpose of this patch */
		public List<PatchFileSet> getPatchContents() {
			return thePatchContents;
		}

		/** @return The directories to look in for installations where the patch may apply */
		public List<String> getLookInDirs() {
			return theLookInDirs;
		}

		/**
		 * Parses patch content from an XML element
		 * 
		 * @param rootElement The XML element to parse
		 * @return The parsed patch content
		 * @throws IllegalArgumentException If the XML element could not be parsed as a patch
		 */
		public static final Patch parse(Element rootElement) throws IllegalArgumentException {
			return parse(rootElement, PatchFileSet::parse);
		}

		/**
		 * Parses a patch from XML
		 * 
		 * @param rootElement The root XML element to parse
		 * @param fileSetParser The parser for parsing {@link PatchFileSet}s from XML elements
		 * @return The parsed patch
		 * @throws IllegalArgumentException If the patch could not be parsed
		 */
		protected static final Patch parse(Element rootElement, Function<Element, PatchFileSet> fileSetParser)
			throws IllegalArgumentException {
			if (!PATCH.equals(rootElement.getNodeName()))
				throw new IllegalArgumentException("Expected '" + PATCH + "' as root element, not '" + rootElement.getNodeName() + "'");
			String appName = rootElement.getAttribute(APP_NAME);
			String targetVersion = rootElement.getAttribute(TARGET_VERSION);
			String patchName = rootElement.getAttribute(PATCH_NAME);
			String author = rootElement.getAttribute(AUTHOR);
			String date = rootElement.getAttribute(PATCH_DATE);
			String description = getElementText(rootElement);
			List<PatchFileSet> contents = new ArrayList<>();
			List<String> lookInDirs = new ArrayList<>();
			for (int i = 0; i < rootElement.getChildNodes().getLength(); i++) {
				Node child = rootElement.getChildNodes().item(i);
				if (child.getNodeType() == Node.ELEMENT_NODE) {
					switch (child.getNodeName()) {
					case PatchFileSet.FILE_SET:
						contents.add(fileSetParser.apply((Element) child));
						break;
					case LOOK_IN:
						lookInDirs.add(getElementText((Element) child));
						break;
					default:
						throw new IllegalArgumentException("Unexpected element '" + child.getNodeName() + " in patch configuration");
					}
				}
			}
			return new Patch(appName, targetVersion, patchName, author, date, description, Collections.unmodifiableList(contents),
				Collections.unmodifiableList(lookInDirs));
		}
	}

	/** An archive file or directory to update in a patch */
	public static class PatchFileSet {
		/** The name of the XML element containing a set of files to replace when the patch is applied */
		public static final String FILE_SET = "file-set";
		/** The name of the XML attribute where the target directory or archive that the patch will modify should be specified */
		public static final String TARGET = "target";
		/** The name of the XML attribute where the target directory containing the files to construct the patch should be specified */
		public static final String SOURCE = "source";
		/** The name of the XML element containing a file to replace when the patch is applied */
		public static final String FILE = "file";
		/** The name of the XML attribute where an alternate storage name in the patch archive may be specified */
		public static final String STORE_AS = "store-as";

		private final String theUpdateTarget;
		private final String theSourceDir;
		private final Set<PatchFile> theFiles;

		/**
		 * @param updateTarget The installation directory or archive file to be updated
		 * @param sourceDir The path to the source directory containing the patch contents to be packaged
		 * @param files The files that need to be replaced in the installation
		 */
		public PatchFileSet(String updateTarget, String sourceDir, Set<PatchFile> files) {
			theUpdateTarget = updateTarget;
			theSourceDir = sourceDir;
			theFiles = files;
		}

		/** @return The installation directory or archive file to be updated */
		public String getUpdateTarget() {
			return theUpdateTarget;
		}

		/** @return The path to the source directory containing the patch contents to be packaged */
		public String getSourceDir() {
			return theSourceDir;
		}

		/** @return The resources in this file set */
		public Set<PatchFile> getFiles() {
			return theFiles;
		}

		/**
		 * @param element The XML element to parse
		 * @return The parsed {@link PatchFileSet}
		 */
		public static PatchFileSet parse(Element element) {
			return parse(element, Collections.emptySet());
		}

		private static PatchFileSet parse(Element element, Set<String> otherAcceptableElements) {
			String target = element.getAttribute(TARGET);
			String source = element.getAttribute(SOURCE);
			Set<PatchFile> files = new LinkedHashSet<>();
			for (int i = 0; i < element.getChildNodes().getLength(); i++) {
				Node child = element.getChildNodes().item(i);
				if (child.getNodeType() == Node.ELEMENT_NODE) {
					if (FILE.equals(child.getNodeName())) {
						String resource = getElementText((Element) child);
						String storeAs = ((Element) child).getAttribute(STORE_AS);
						if (storeAs == null || storeAs.isEmpty())
							storeAs = resource;
						files.add(new PatchFile(resource, storeAs));
					} else if (otherAcceptableElements.contains(child.getNodeName())) { //
					} else
						throw new IllegalArgumentException(
							"Unexpected element '" + child.getNodeName() + " in file-set of patch configuration");
				}
			}
			return new PatchFileSet(target, source, Collections.unmodifiableSet(files));
		}
	}

	static String getElementText(Element element) {
		StringBuilder content = null;
		for (int i = 0; i < element.getChildNodes().getLength(); i++) {
			Node child = element.getChildNodes().item(i);
			if (child.getNodeType() == Node.TEXT_NODE) {
				if (!((Text) child).isElementContentWhitespace()) {
					String elText = ((Text) child).getWholeText();
					int end = -1;
					for (int j = 0; j < elText.length(); j++) {
						if (!Character.isWhitespace(elText.charAt(j))) {
							end = j;
						}
					}
					if (end >= 0) {
						if (content == null)
							content = new StringBuilder();
						else
							content.append('\n');
						content.append(elText, 0, end + 1);
					}
				}
			}
		}
		return content == null ? null : content.toString();
	}

	/** Represents a file to be replaced when a patch is applied. Path names are relative to the owning {@link PatchFileSet}. */
	public static class PatchFile {
		/** The path of the resource in the source directory and when replaced in the target directory */
		public final String resourceName;
		/** The path of the resource as stored in the patch archive */
		public final String archiveEntry;

		/**
		 * @param resourceName The path for the resource in the source directory and when replaced in the target directory
		 * @param archiveEntry The path for the resource as stored in the patch archive
		 */
		public PatchFile(String resourceName, String archiveEntry) {
			this.resourceName = resourceName;
			this.archiveEntry = archiveEntry;
		}

		@Override
		public int hashCode() {
			return resourceName.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof PatchFile))
				return false;
			return resourceName.equals(((PatchFile) obj).resourceName);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(resourceName);
			if (archiveEntry != null)
				str.append(" (as ").append(archiveEntry).append(')');
			return str.toString();
		}
	}

	/** A {@link PatchFileSet} parsed for the purpose of creating a new patch */
	public static class CreationPatchFileSet extends PatchFileSet {
		private static final Set<String> PATTERN = Collections.singleton("pattern");

		private final List<PatchFilePattern> thePatterns;

		/**
		 * @param updateTarget The installation directory or archive file to be updated
		 * @param sourceDir The path to the source directory containing the patch contents to be packaged
		 * @param files The files that need to be replaced in the installation
		 * @param patterns Patterns for matching files en masse
		 */
		public CreationPatchFileSet(String updateTarget, String sourceDir, Set<PatchFile> files, List<PatchFilePattern> patterns) {
			super(updateTarget, sourceDir, files);
			thePatterns = patterns;
		}

		/** @return File patterns for matching files en masse */
		public List<PatchFilePattern> getPatterns() {
			return thePatterns;
		}

		/**
		 * @param element The XML element to parse
		 * @return A {@link PatchFileSet} or {@link CreationPatchFileSet} parsed with the knowledge that the element may specify "pattern"
		 *         elements
		 */
		public static Patch parsePatchWithPatterns(Element element) {
			return Patch.parse(element, CreationPatchFileSet::parse);
		}

		public static PatchFileSet parse(Element element) {
			PatchFileSet base = PatchFileSet.parse(element, PATTERN);
			List<PatchFilePattern> patterns = null;
			for (int i = 0; i < element.getChildNodes().getLength(); i++) {
				Node child = element.getChildNodes().item(i);
				if (child.getNodeType() == Node.ELEMENT_NODE) {
					if ("pattern".equals(child.getNodeName())) {
						String directory = ((Element) child).getAttribute("directory");
						if ("".equals(directory))
							directory = null;
						String patternStr = getElementText((Element) child);
						String inSubDirS = ((Element) child).getAttribute("sub-directories");
						String storeIn = ((Element) child).getAttribute("store-in");
						if ("".equals(storeIn))
							storeIn = null;
						Pattern pattern = Pattern.compile(patternStr);
						boolean inSubDir;
						if (inSubDirS == null || inSubDirS.isEmpty())
							inSubDir = false;
						else {
							switch (inSubDirS) {
							case "true":
								inSubDir = true;
								break;
							case "false":
								inSubDir = false;
								break;
							default:
								throw new IllegalArgumentException("'sub-directories' must be 'true' or 'false'");
							}
						}
						if (patterns == null)
							patterns = new ArrayList<>();
						patterns.add(new PatchFilePattern(directory, pattern, inSubDir, storeIn));
					}
				}
			}
			if (patterns == null)
				return base;
			return new CreationPatchFileSet(base.getUpdateTarget(), base.getSourceDir(), base.getFiles(),
				Collections.unmodifiableList(patterns));
		}
	}

	/** A pattern that may match any number of files in a source directory */
	public static class PatchFilePattern {
		/** The path to the sub-directory (relative to the {@link CreationPatchFileSet}) to match files in */
		public final String directory;
		/** The regex pattern for the names of files to match */
		public final Pattern pattern;
		/** Whether to also search in sub-directories for files */
		public final boolean inSubDirs;
		/** An alternate name for the folder to store the files in in the patch archive */
		public final String storeIn;

		/**
		 * @param directory The path to the sub-directory (relative to the {@link CreationPatchFileSet}) to match files in
		 * @param pattern The regex pattern for the names of files to match
		 * @param inSubDirs Whether to also search in sub-directories for files
		 * @param storeIn An alternate name for the folder to store the files in in the patch archive
		 */
		public PatchFilePattern(String directory, Pattern pattern, boolean inSubDirs, String storeIn) {
			this.directory = directory;
			this.pattern = pattern;
			this.inSubDirs = inSubDirs;
			this.storeIn = storeIn;
		}

		/**
		 * Searches through a source directory for files matching this pattern
		 * 
		 * @param searchRoot The {@link CreationPatchFileSet} root to search in
		 * @param onFound A callback to call for each matching file. First argument is the file, second argument is the PatchFile to store
		 *        the file as in the patch.
		 */
		public void search(BetterFile searchRoot, BiConsumer<BetterFile, PatchFile> onFound) {
			BetterFile dir = directory == null ? searchRoot : searchRoot.at(directory);
			if (!dir.isDirectory()) {
				System.err.println(dir.getPath() + " is not " + (dir.exists() ? "a directory" : "found"));
				return;
			}
			StringBuilder resourcePath = new StringBuilder();
			StringBuilder storePath = new StringBuilder();
			if (directory != null) {
				resourcePath.append(directory);
				if (directory.endsWith("/") || directory.endsWith("\\"))
					resourcePath.setLength(resourcePath.length() - 1);
			}
			if (storeIn != null) {
				storePath.append(storeIn);
				if (storeIn.endsWith("/") || storeIn.endsWith("\\"))
					storePath.setLength(storePath.length() - 1);
			} else
				storePath.append(resourcePath);
			if (0 == search(dir, resourcePath, storePath, onFound)) {
				System.err.println("No files matching " + pattern + " found" + (directory == null ? "" : (" in " + directory)));
			}
		}

		private int search(BetterFile dir, StringBuilder resourcePath, StringBuilder storePath, BiConsumer<BetterFile, PatchFile> onFound) {
			resourcePath.append('/');
			storePath.append('/');
			int preRsrcLen = resourcePath.length();
			int preStoreLen = storePath.length();
			int found = 0;
			for (BetterFile file : dir.listFiles()) {
				if (file.isFile() && pattern.matcher(file.getName()).matches()) {
					found++;
					resourcePath.append(file.getName());
					storePath.append(file.getName());
					onFound.accept(file, new PatchFile(resourcePath.toString(), storePath.toString()));
					resourcePath.setLength(preRsrcLen);
					storePath.setLength(preStoreLen);
				} else if (inSubDirs) {
					resourcePath.append(file.getName());
					storePath.append(file.getName());
					found += search(file, resourcePath, storePath, onFound);
					resourcePath.setLength(preRsrcLen);
					storePath.setLength(preStoreLen);
				}
			}
			return found;
		}

		@Override
		public String toString() {
			return directory + "/" + pattern;
		}
	}

	/** Called from the executable patch jar to apply the patch */
	public static void applyPatch() {
		// IMPORTANT!! This method uses very few java-external utilities, including other Qommons classes,
		// so they don't have to be packaged into the patch file, so we can keep the file size of patches as small as possible.

		// This means we can't use my BetterFile API or my nifty utilities for XML parsing or anything else.
		URL classFile = QuarkJarPatcher.class.getResource(QuarkJarPatcher.class.getSimpleName() + ".class");

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
			e.printStackTrace();
		}

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
							patch[0] = Patch.parse(patchRoot);
							f.delete();

							System.out.println("Read configuration for " + patch[0].getAppName() + " patch " + patch[0].getPatchName());
							System.out.println("By " + patch[0].getPatchAuthor() + " " + patch[0].getPatchDate());
							System.out.println(patch[0].getPatchDescription());
							if (fDialog != null && fDescrip != null) {
								fDialog.setTitle("Applying " + patch[0].getAppName() + " patch " + patch[0].getPatchName());
								fDescrip.setText("<html>For " + patch[0].getAppName() + " " + patch[0].getTargetVersion() + "<br>"//
									+ "By " + patch[0].getPatchAuthor() + " " + patch[0].getPatchDate() + "<br>"//
									+ patch[0].getPatchDescription());
							}
							status("Extracting patch contents", null, -1, status, progress, uiDirty);

							for (PatchFileSet fs : patch[0].getPatchContents()) {
								for (PatchFile file : fs.getFiles())
									patchContents.add(file.archiveEntry);
							}
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
			// If we have a head, ask for the installation directory.
			// If we're in a headless environment, assume this is being run from the installation directory
			if (dialog != null) {
				// Ask the user for the installation directory
				progress[1] = 0;
				status("Requesting installation directory", null, 0, status, progress, uiDirty);
				JFileChooser chooser = new JFileChooser(installDir);
				chooser.setDialogTitle("Select " + patch[0].getAppName() + " installation directory to patch");
				chooser.setDialogType(JFileChooser.OPEN_DIALOG);
				chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				chooser.setFileHidingEnabled(false);
				if (!patch[0].getLookInDirs().isEmpty()) {
					System.out.println(); // Previous status() call doesn't print the newline
					File startDir = getStartingDir(patch[0]);
					if (startDir != null)
						chooser.setCurrentDirectory(startDir);
				}
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
						for (PatchFile file : patch[0].getPatchContents().get(i).getFiles())
							totalLength += extractedFiles.get(file.archiveEntry).length();
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
					PatchFileSet fileSet = patch[0].getPatchContents().get(i);
					if (targets[i].isDirectory()) { // Just replace the target files
						for (PatchFile file : fileSet.getFiles()) {
							status[1] = file.resourceName;
							File targetFile = new File(targets[i], file.resourceName);
							File parent = targetFile.getParentFile();
							if (!parent.exists() && !parent.mkdirs())
								throw new IOException("Could not create " + parent.getAbsolutePath());
							File patchFile = extractedFiles.remove(file.archiveEntry);
							try (CountingInputStream in = new CountingInputStream(new BufferedInputStream(new FileInputStream(patchFile))); //
								OutputStream out = new BufferedOutputStream(new FileOutputStream(targetFile))) {
								int read = in.read(buffer);
								while (read >= 0) {
									out.write(buffer, 0, read);
									status(null, file.resourceName,
										Math.round((fileContentSoFar + in.getPosition()) * 1000.0f / totalLength), status, progress,
										uiDirty);
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
						Map<String, PatchFile> filesByName = new HashMap<>();
						for (PatchFile file : fileSet.getFiles())
							filesByName.put(file.resourceName, file);
						// For progress, assume 90% of the work is parsing the target zip file and creating the replacement
						try (
							CountingInputStream targetIn = new CountingInputStream(
								new BufferedInputStream(new FileInputStream(targets[i])));
							ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(replacement)))) {
							long fcsf = fileContentSoFar, tl = totalLength;
							MiniFileUtils.extractZip(targetIn, entry -> {
								status[1] = entry.getPath();
								status(null, entry.getPath(), progress[0], status, progress, uiDirty);
								PatchFile patchPath = filesByName.get(entry.getPath());
								File patchFile = patchPath == null ? null : extractedFiles.remove(patchPath.archiveEntry);
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
								status(null, entry.getPath(), Math.round((fcsf + targetIn.getPosition() * 0.9f) * 1000.0f / tl), status,
									progress, uiDirty);
							}, null);
							// Now insert added files
							for (PatchFile file : fileSet.getFiles()) {
								File patchFile = extractedFiles.remove(file.archiveEntry);
								if (patchFile == null)
									continue; // Already replaced
								ZipEntry entry = new ZipEntry(file.resourceName);
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

	private static final Pattern SYS_PROP_PATTERN = Pattern.compile("\\$\\{(?<prop>[^}]+)\\}");

	private static File getStartingDir(Patch patch) {
		for (String dir : patch.getLookInDirs()) {
			Matcher match = SYS_PROP_PATTERN.matcher(dir);
			int offset = 0;
			boolean valid = true;
			while (match.find()) {
				String propName = match.group("prop");
				String propValue = System.getProperty(propName);
				if (propValue == null) {
					valid = false;
					System.out.println("No such property found: '" + propName + "'");
					break;
				}
				int matchLength = match.end() - match.start();
				dir = dir.substring(0, offset + match.start())//
					+ propValue//
					+ dir.substring(offset + matchLength);
				offset += propValue.length() - matchLength;
			}
			if (!valid)
				continue;
			File f = new File(dir);
			if (!f.exists()) {
				System.out.println("Look-in directory not found: " + dir);
				continue;
			} else if (!f.isDirectory()) {
				System.out.println("Look-in directory is not a directory: " + dir);
				continue;
			}
			for (File sub : f.listFiles()) {
				if (isPatchable(sub, patch))
					return f;
			}
			System.out.println("No patchable " + patch.getAppName() + " installations found in " + dir);
		}
		return null;
	}

	private static boolean isPatchable(File dir, Patch patch) {
		if (!dir.isDirectory())
			return false;
		for (PatchFileSet fileSet : patch.getPatchContents()) {
			File target = new File(dir, fileSet.getUpdateTarget());
			if (!target.exists())
				return false;
		}
		return true;
	}

	/**
	 * Creates a patch file
	 * 
	 * @param patchFileLocation The location of the patch file--a URL or an absolute or relative file path
	 * @return The executable patch jar
	 * @throws IOException If the patch file could not be read or parsed, or any resources referred to by the patch could not be read
	 * @throws IllegalArgumentException If the patch file was invalid or if a patch could not be created based on the configuration in the
	 *         patch file
	 */
	public static File createPatch(String patchFileLocation) throws IOException, IllegalArgumentException {
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
			patch = CreationPatchFileSet
				.parsePatchWithPatterns(QommonsConfig.getRootElement(new BufferedInputStream(patchStream = patchConfigFile.read())));
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

		BetterFile patcherClassFile = FileUtils.getClassFile(QuarkJarPatcher.class);
		BetterFile searchRoot = patcherClassFile.getParent().getParent().getParent(); // class root
		if (!searchRoot.getName().endsWith(".jar")) // If we're not in a jar, use the Qommons project root
			searchRoot = searchRoot.getParent().getParent().getParent(); // Qommons/target/classes

		// The patch file needs to be the first file in the archive so the application code only needs to read the archive once.
		// The application code doesn't know anything about patterns, so we need to find all pattern-matched files for each file set first,
		// then add those files to the patch.
		Map<PatchFile, BetterFile>[] patternMatchedFiles = new Map[patch.getPatchContents().size()];
		for (int i = 0; i < patch.getPatchContents().size(); i++) {
			if (!(patch.getPatchContents().get(i) instanceof CreationPatchFileSet)) {
				patternMatchedFiles[i] = Collections.emptyMap();
			} else {
				CreationPatchFileSet fileSet = (CreationPatchFileSet) patch.getPatchContents().get(i);
				BetterFile fileSetRoot = fileSet.getSourceDir() == null ? null : searchRoot.at(fileSet.getSourceDir());
				if (fileSetRoot != null && !fileSetRoot.isDirectory())
					throw new IllegalArgumentException("No directory found for patch file set at " + fileSetRoot.getPath());
				Map<PatchFile, BetterFile> fileSetMatches = new HashMap<>();
				for (PatchFilePattern pattern : fileSet.getPatterns()) {
					System.out.println("For pattern " + pattern + ":");
					pattern.search(fileSetRoot, (file, patchFile) -> {
						System.out.println("\t" + patchFile.resourceName);
						fileSetMatches.put(patchFile, file);
					});
				}
				if (!fileSetMatches.isEmpty())
					patternMatchedFiles[i] = fileSetMatches;
				else
					patternMatchedFiles[i] = Collections.emptyMap();
			}
		}
		boolean hasPatterns = !Arrays.stream(patternMatchedFiles).allMatch(Map::isEmpty);

		String patchFileName = patchConfigFile.getName();
		if (!patchFileName.endsWith(".patch")) { // Needed to be recognized by the patch application code
			patchFileName += ".patch";
		}
		File patchFile = new File(patchFileName + ".jar"); // Create patch file in current working dir
		try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(patchFile)))) {
			zip.setLevel(9);
			zip.setComment(patch.getPatchDescription());

			// First, the patch file itself
			ZipEntry entry = new ZipEntry(patchFileName); // Put in the root of the patch file
			entry.setLastModifiedTime(FileTime.fromMillis(patchConfigFile.getLastModified()));
			entry.setComment("The specification file for the patch");
			zip.putNextEntry(entry);
			if (hasPatterns) {
				// Write a new patch file containing the pattern-matched files
				Writer writer = new OutputStreamWriter(zip);
				XmlSerialWriter.createDocument(writer)//
					.writeWhitespace("\n\n")//
					.writeRoot(Patch.PATCH, root -> {
						root//
							.addAttribute(Patch.APP_NAME, patch.getAppName())//
							.addAttribute(Patch.TARGET_VERSION, patch.getTargetVersion())//
							.addAttribute(Patch.PATCH_NAME, patch.getPatchName())//
							.addAttribute(Patch.AUTHOR, patch.getPatchAuthor())//
							.addAttribute(Patch.PATCH_DATE, patch.getPatchDate());
						if (patch.getPatchDescription() != null)
							root.addContent(patch.getPatchDescription()).addContent("\n\n");
						for (String lookIn : patch.getLookInDirs())
							root.addChild(Patch.LOOK_IN, lookInEl -> lookInEl.addContent(lookIn));
						for (int i = 0; i < patternMatchedFiles.length; i++) {
							PatchFileSet fileSet = patch.getPatchContents().get(i);
							int fi = i;
							root.addChild(PatchFileSet.FILE_SET, fsEl -> {
								fsEl//
									.addAttribute(PatchFileSet.TARGET, fileSet.getUpdateTarget())//
									.addAttribute(PatchFileSet.SOURCE, fileSet.getSourceDir());
								for (PatchFile file : IterableUtils.concat(fileSet.getFiles(), patternMatchedFiles[fi].keySet())) {
									fsEl.addChild(PatchFileSet.FILE, fileEl -> {
										if (!file.archiveEntry.equals(file.resourceName))
											fileEl.addAttribute(PatchFileSet.STORE_AS, file.archiveEntry);
										fileEl.addContent(file.resourceName);
									});
								}
							});
						}
					});
				writer.flush();
			} else { // Just copy the patch file input, comments and all
				try (InputStream in = patchConfigFile.read()) {
					MiniFileUtils.copy(in, zip);
				}
			}

			// Now this class file and the few dependency classes we need
			bundleClass(QuarkJarPatcher.class, "The patch file application class", zip);
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
			for (int i = 0; i < patch.getPatchContents().size(); i++) {
				PatchFileSet fileSet = patch.getPatchContents().get(i);
				BetterFile fileSetRoot = fileSet.getSourceDir() == null ? null : searchRoot.at(fileSet.getSourceDir());
				if (fileSetRoot != null && !fileSetRoot.isDirectory())
					throw new IllegalArgumentException("No directory found for patch file set at " + fileSetRoot.getPath());
				for (PatchFile file : fileSet.getFiles()) {
					BetterFile found;
					try {
						if (fileSetRoot != null)
							found = fileSetRoot.at(file.resourceName);
						else {
							URL foundUrl = QuarkJarPatcher.class.getResource("/" + file.resourceName); // See if it's on the classpath
							if (foundUrl == null)
								throw new FileNotFoundException("No such resource on classpath");
							found = FileUtils.ofUrl(foundUrl);
						}
					} catch (IOException e) {
						throw new IOException(
							"Could not locate or read resource '" + file.resourceName + "' for patch file set " + fileSet.getUpdateTarget(),
							e);
					}
					entry = new ZipEntry(file.archiveEntry);
					entry.setLastModifiedTime(FileTime.fromMillis(found.getLastModified()));
					zip.putNextEntry(entry);
					try (InputStream in = found.read()) {
						MiniFileUtils.copy(in, zip);
					}
				}
				for (Map.Entry<PatchFile, BetterFile> patternMatchedFile : patternMatchedFiles[i].entrySet()) {
					entry = new ZipEntry(patternMatchedFile.getKey().archiveEntry);
					BetterFile found = patternMatchedFile.getValue();
					entry.setLastModifiedTime(FileTime.fromMillis(found.getLastModified()));
					zip.putNextEntry(entry);
					try (InputStream in = found.read()) {
						MiniFileUtils.copy(in, zip);
					}
				}
			}
			return patchFile;
		} catch (IOException e) {
			throw new IllegalArgumentException("Patch creation failed", e);
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
}

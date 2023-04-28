package org.qommons;

import java.awt.GraphicsEnvironment;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.qommons.TimeUtils.ParsedInstant;
import org.qommons.collect.BetterList;
import org.qommons.config.StrictXmlReader;
import org.qommons.ex.ExFunction;
import org.qommons.io.ArchiveEnabledFileSource;
import org.qommons.io.BetterFile;
import org.qommons.io.BetterFile.CheckSumType;
import org.qommons.io.FileUtils;
import org.qommons.io.NativeFileSource;
import org.qommons.io.SimpleXMLParser.XmlParseException;
import org.qommons.io.TextParseException;
import org.qommons.io.UrlFileSource;
import org.qommons.io.XmlSerialWriter;
import org.qommons.io.XmlSerialWriter.Element;

public class QuarkApplicationVersioning {
	public static class ApplicationVersionDescription implements Named, Comparable<ApplicationVersionDescription> {
		private final String theName;
		private final Version theVersion;
		private final ReleaseDate theReleaseDate;
		private final String theDescription;
		private final String theUpdateJar;
		private final List<ApplicationChange> theChangeList;
		private final List<Distribution> theDistributions;
		private final BetterFile.CheckSumType theHashType;

		public ApplicationVersionDescription(String name, Version version, ReleaseDate releaseDate, String description, String updateJar,
			List<ApplicationChange> changeList, List<Distribution> distributions, BetterFile.CheckSumType hashType) {
			theName = name;
			theVersion = version;
			theReleaseDate = releaseDate;
			theDescription = description;
			theUpdateJar = updateJar;
			theChangeList = changeList;
			theDistributions = distributions;
			theHashType = hashType;
		}

		@Override
		public String getName() {
			return theName;
		}

		public Version getVersion() {
			return theVersion;
		}

		public ReleaseDate getReleaseDate() {
			return theReleaseDate;
		}

		public String getDescription() {
			return theDescription;
		}

		public String getUpdateJar() {
			return theUpdateJar;
		}

		public List<ApplicationChange> getChangeList() {
			return theChangeList;
		}

		public List<Distribution> getDistributions() {
			return theDistributions;
		}

		public BetterFile.CheckSumType getHashType() {
			return theHashType;
		}

		@Override
		public int compareTo(ApplicationVersionDescription o) {
			return theVersion.compareTo(o.theVersion);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ApplicationVersionDescription //
				&& theName.equals(((ApplicationVersionDescription) obj).getName())//
				&& theVersion.equals(((ApplicationVersionDescription) obj).getVersion());
		}

		@Override
		public String toString() {
			return theName + " " + theVersion;
		}
	}

	public static class ReleaseDate {
		private final int theYear;
		private final int theMonth;
		private final int theDay;

		public ReleaseDate(int year, int month, int day) {
			if (month < 1 || month > 12)
				throw new IllegalArgumentException("Month must be 1-12");
			if (day < 1 || day > 31)
				throw new IllegalArgumentException("Day must be 1-31");
			theYear = year;
			theMonth = month;
			theDay = day;
		}

		public int getYear() {
			return theYear;
		}

		public int getMonth() {
			return theMonth;
		}

		public int getDay() {
			return theDay;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			switch (theMonth) {
			case 1:
				str.append("Jan");
				break;
			case 2:
				str.append("Feb");
				break;
			case 3:
				str.append("Mar");
				break;
			case 4:
				str.append("Apr");
				break;
			case 5:
				str.append("May");
				break;
			case 6:
				str.append("Jun");
				break;
			case 7:
				str.append("Jul");
				break;
			case 8:
				str.append("Aug");
				break;
			case 9:
				str.append("Sep");
				break;
			case 10:
				str.append("Oct");
				break;
			case 11:
				str.append("Nov");
				break;
			case 12:
				str.append("Dec");
				break;
			}
			str.append(' ').append(theDay).append(", ").append(theYear);
			return str.toString();
		}
	}

	public static class ApplicationChange implements Named {
		private final String theName;
		private final String theDescription;

		public ApplicationChange(String name, String description) {
			theName = name;
			theDescription = description;
		}

		@Override
		public String getName() {
			return theName;
		}

		public String getDescription() {
			return theDescription;
		}
	}

	public static class ApplicationVersionDeployment extends ApplicationVersionDescription {
		private final DirectoryDeployment theDeployPolicy;

		public ApplicationVersionDeployment(String name, Version version, ReleaseDate releaseDate, String description, String updateJar,
			List<ApplicationChange> changeList, List<Distribution> distributions, BetterFile.CheckSumType hashType,
			DirectoryDeployment deployPolicy) {
			super(name, version, releaseDate, description, updateJar, changeList, distributions, hashType);
			theDeployPolicy = deployPolicy;
		}

		public DirectoryDeployment getDeployPolicy() {
			return theDeployPolicy;
		}
	}

	public static class Distribution implements Named {
		private final ApplicationVersionDescription theApplication;
		private final String theName;
		private final String theDescription;
		private final String theFile;
		private final String theHash;
		private final boolean isExplode;
		private final Map<String, Requirement> theRequirements;

		public Distribution(ApplicationVersionDescription application, String name, String description, String file, String hash,
			boolean explode, Map<String, Requirement> requirements) {
			theApplication = application;
			theName = name;
			theDescription = description;
			theFile = file;
			theHash = hash;
			isExplode = explode;
			theRequirements = requirements;
		}

		public ApplicationVersionDescription getApplication() {
			return theApplication;
		}

		@Override
		public String getName() {
			return theName;
		}

		public String getDescription() {
			return theDescription;
		}

		public String getFile() {
			return theFile;
		}

		public String getHash() {
			return theHash;
		}

		public boolean isExplode() {
			return isExplode;
		}

		public Map<String, Requirement> getRequirements() {
			return theRequirements;
		}
	}

	public static class Requirement {
		private final Set<String> theAllowedValues;
		private final Version theMinimum;
		private final Version theMaximum;

		public Requirement(Set<String> allowedValues, Version minimum, Version maximum) {
			theAllowedValues = allowedValues;
			theMinimum = minimum;
			theMaximum = maximum;
		}

		public Set<String> getAllowedValues() {
			return theAllowedValues;
		}

		public Version getMinimum() {
			return theMinimum;
		}

		public Version getMaximum() {
			return theMaximum;
		}

		@Override
		public String toString() {
			if (theMinimum != null) {
				if (theMaximum != null)
					return theMinimum + ".." + theMaximum;
				else
					return theMinimum + "+";
			} else
				return theAllowedValues.toString();
		}
	}

	public static class FileDeployment {
		private final Pattern theNamePattern;
		private final String theRename;
		private final boolean isCompress;
		private final boolean isIgnored;

		public FileDeployment(Pattern namePattern, String rename, boolean compress, boolean ignored) {
			theNamePattern = namePattern;
			theRename = rename;
			isCompress = compress;
			isIgnored = ignored;
		}

		public Pattern getNamePattern() {
			return theNamePattern;
		}

		public String getRename() {
			return theRename;
		}

		public boolean isCompress() {
			return isCompress;
		}

		public boolean isIgnored() {
			return isIgnored;
		}
	}

	public static class DirectoryDeployment extends FileDeployment {
		private final List<FileDeployment> theFiles;
		private final List<DirectoryDeployment> theDirectories;

		public DirectoryDeployment(Pattern namePattern, String rename, boolean compress, boolean ignored, List<FileDeployment> files,
			List<DirectoryDeployment> directories) {
			super(namePattern, rename, compress, ignored);
			theFiles = files;
			theDirectories = directories;
		}

		public List<FileDeployment> getFiles() {
			return theFiles;
		}

		public List<DirectoryDeployment> getDirectories() {
			return theDirectories;
		}
	}

	public static class ApplicationVersionFull extends ApplicationVersionDescription {
		private final ApplicationDirectory theRootDirectory;

		public ApplicationVersionFull(String name, Version version, ReleaseDate releaseDate, String description, String updateJar,
			List<ApplicationChange> changeList, BetterFile.CheckSumType hashType, List<Distribution> distributions,
			ApplicationDirectory rootDirectory) {
			super(name, version, releaseDate, description, updateJar, changeList, distributions, hashType);
			theRootDirectory = rootDirectory;
		}

		public ApplicationDirectory getRootDirectory() {
			return theRootDirectory;
		}
	}

	public static abstract class ApplicationFileOrDirectory {
		private final String theRename;

		public ApplicationFileOrDirectory(String rename) {
			theRename = rename;
		}

		public String getRename() {
			return theRename;
		}
	}

	public static class ApplicationDirectory extends ApplicationFileOrDirectory {
		private final Map<String, ApplicationFile> theFiles;
		private final Map<String, ApplicationDirectory> theSubDirectories;
		private final List<Pattern> theIgnoredFiles;
		private final List<Pattern> theIgnoredDirectories;

		public ApplicationDirectory(String rename, Map<String, ApplicationFile> files, Map<String, ApplicationDirectory> subDirectories,
			List<Pattern> ignoredFiles, List<Pattern> ignoredDirectories) {
			super(rename);
			theFiles = files;
			theSubDirectories = subDirectories;
			theIgnoredFiles = ignoredFiles;
			theIgnoredDirectories = ignoredDirectories;
		}

		public Map<String, ApplicationFile> getFiles() {
			return theFiles;
		}

		public Map<String, ApplicationDirectory> getSubDirectories() {
			return theSubDirectories;
		}

		public List<Pattern> getIgnoredFiles() {
			return theIgnoredFiles;
		}

		public List<Pattern> getIgnoredDirectories() {
			return theIgnoredDirectories;
		}
	}

	public static class ApplicationFile extends ApplicationFileOrDirectory {
		private final String theHash;
		private final boolean isExplode;

		public ApplicationFile(String rename, String hash, boolean explode) {
			super(rename);
			theHash = hash;
			isExplode = explode;
		}

		public String getHash() {
			return theHash;
		}

		public boolean isExplode() {
			return isExplode;
		}
	}

	public ApplicationVersionDeployment parseDeployment(InputStream in, BetterFile distributionDir) throws IOException, TextParseException {
		StrictXmlReader root = StrictXmlReader.ofRoot(in);
		if (!root.getName().equals("application-deploy"))
			throw new TextParseException("Expected 'application-deploy' for root, not '" + root.getName() + "'", root.getNamePosition());

		ApplicationVersionDescription app = parseApplication(root, distributionDir, true, true);
		DirectoryDeployment deploy = parseDeployPolicy(root.getElement("deploy"), true);
		List<Distribution> distributions = new ArrayList<>();
		ApplicationVersionDeployment deployment = new ApplicationVersionDeployment(app.getName(), app.getVersion(), app.getReleaseDate(),
			app.getDescription(), app.getUpdateJar(), app.getChangeList(), Collections.unmodifiableList(distributions), app.getHashType(),
			deploy);
		for (Distribution dist : app.getDistributions()) {
			distributions.add(new Distribution(deployment, dist.getName(), dist.getDescription(), dist.getFile(), dist.getHash(),
				dist.isExplode(), dist.getRequirements()));
		}
		root.check();
		return deployment;
	}

	public ApplicationVersionFull deployVersion(ApplicationVersionDeployment deployment, BetterFile sourceDirectory, BetterFile deployRoot,
		String distributionDirPath, BetterFile distributionSourceDir) throws IOException {
		BetterFile appDeployDir = deployRoot.at(deployment.getName() + "-" + deployment.getVersion());
		if (appDeployDir.exists()) {
			for (BetterFile f : appDeployDir.listFiles())
				f.delete(null);
		} else
			appDeployDir.create(true);
		if (!distributionDirPath.endsWith("/"))
			distributionDirPath += "/";
		ApplicationDirectory dir = deployDirectory(new StringBuilder(), deployment.getDeployPolicy(), sourceDirectory, appDeployDir,
			deployment.getHashType());
		List<Distribution> distributions = new ArrayList<>(deployment.getDistributions().size());
		ApplicationVersionFull full = new ApplicationVersionFull(deployment.getName(), deployment.getVersion(), deployment.getReleaseDate(),
			deployment.getDescription(), deployment.getUpdateJar(), deployment.getChangeList(), deployment.getHashType(),
			Collections.unmodifiableList(distributions), dir);
		for (Distribution deployDist : deployment.getDistributions()) {
			String deployFile = deployDist.getFile();
			int lastSlash = deployFile.lastIndexOf('/');
			if (lastSlash >= 0)
				deployFile = deployFile.substring(lastSlash + 1);
			deployFile = distributionDirPath + deployFile;
			distributions.add(new Distribution(full, deployDist.getName(), deployDist.getDescription(), deployFile, deployDist.getHash(),
				deployDist.isExplode(), deployDist.getRequirements()));
		}
		writeVersionXml(full, appDeployDir.at("application-version.xml"));
		if (distributionSourceDir != null) {
			BetterFile targetDir = appDeployDir.at(distributionDirPath).create(true);
			for (Distribution dist : deployment.getDistributions()) {
				BetterFile target = targetDir.at(dist.getFile());
				FileUtils.copy(//
					() -> distributionSourceDir.at(dist.getFile()).read(), //
					target::write);
			}
		}
		return full;
	}

	public List<ApplicationVersionDescription> getDeployedApplications(String baseServiceUrl) throws IOException, TextParseException {
		return getDeployedApplications(BetterFile.getRoots(new UrlFileSource(new URL(baseServiceUrl))).get(0));
	}

	public List<ApplicationVersionDescription> getDeployedApplications(BetterFile serviceRoot) throws IOException, TextParseException {
		StrictXmlReader avXml;
		try (InputStream in = serviceRoot.at("application-versions.xml").read()) {
			avXml = StrictXmlReader.ofRoot(in);
		}
		if (!"application-versions".equals(avXml.getName()))
			throw new TextParseException("Expected 'application-versions'", avXml.getNamePosition());
		List<ApplicationVersionDescription> apps = new ArrayList<>();
		for (StrictXmlReader appXml : avXml.getElements("application-version")) {
			try {
				apps.add(parseApplication(appXml, null, true, false));
			} catch (IOException e) {
				throw new IllegalStateException("Should not be here", e);
			}
			appXml.check();
		}
		avXml.check();
		Collections.sort(apps);
		return apps;
	}

	public interface UpdateProgress {
		void setTotal(int files, int totalSize);

		void setTotalProgress(int progress);

		void setCurrentFile(String file);

		void setCurrentFileSize(int size);

		void setCurrentFileProgress(int progress);

		boolean isCanceled();
	}

	public void update(String baseServiceUrl, ApplicationVersionDescription version, BetterFile installDirectory, Runnable customShutdown,
		UpdateProgress progress) throws IOException, TextParseException {
		BetterFile tempDir = createDefaultTempDirectory(version);
		BetterFile baseServiceRoot = FileUtils.ofUrl(new URL(baseServiceUrl));
		downloadUpdate(baseServiceRoot, version, installDirectory, tempDir, progress);
		shutdownAndUpdate(version, installDirectory, tempDir, customShutdown, null);
	}

	public static BetterFile createDefaultTempDirectory(ApplicationVersionDescription version) throws IOException {
		return BetterFile.at(new ArchiveEnabledFileSource(new NativeFileSource()).setMaxArchiveDepth(1)//
			.withArchival(new ArchiveEnabledFileSource.ZipCompression(), new ArchiveEnabledFileSource.TarArchival(),
				new ArchiveEnabledFileSource.GZipCompression()),
			Files.createTempDirectory(version.getName() + "-" + version.getVersion() + "-Update").toString());
	}

	public void downloadUpdate(BetterFile baseServiceRoot, ApplicationVersionDescription version, BetterFile installDirectory,
		BetterFile tempDir, UpdateProgress progress) throws IOException, XmlParseException, TextParseException {
		BetterFile versionServiceRoot = baseServiceRoot.at(version.getName() + "-" + version.getVersion());
		BetterFile applicationXml = versionServiceRoot.at("application-version.xml");
		ApplicationVersionFull fullVersion;
		try (InputStream in = applicationXml.read()) {
			fullVersion = parseFullVersion(StrictXmlReader.ofRoot(in), false);
		}
		BetterFile deployedAppXml = tempDir.at(applicationXml.getName());
		if (progress != null) {
			int[] files = new int[] { 1 };
			long totalSize = deployedAppXml.length()//
				+ getTotalSize(fullVersion.getRootDirectory(), versionServiceRoot, installDirectory, version.getHashType(), files,
					progress);
			if (progress.isCanceled())
				return;
			progress.setTotal(files[0], (int) (totalSize / 10240));
			progress.setCurrentFile("application-version.xml");
			progress.setCurrentFileSize((int) deployedAppXml.length());
		}
		try {
			// writeVersionXml(fullVersion, tempDir.at("application-version.xml"));
			// Copy the XML instead of writing from our structure.
			// Possibly the updated version of this class can interpret XML components which we ignore
			FileUtils.copy(applicationXml::read, deployedAppXml::write, p -> {
				if (progress != null)
					progress.setCurrentFileProgress((int) p);
			});
			copyUpdateFiles(fullVersion.getRootDirectory(), new StringBuilder(), versionServiceRoot, tempDir, installDirectory,
				version.getHashType(), progress, 0);
		} catch (IOException e) {
			tempDir.delete(null);
			throw e;
		}
	}

	public void shutdownAndUpdate(ApplicationVersionDescription version, BetterFile installDirectory, BetterFile replacementDirectory,
		Runnable customShutdown, List<String> reLaunchCommand) throws IOException {
		BetterFile updateJar = replacementDirectory.at(version.getUpdateJar());
		BetterFile jarCopy = installDirectory.at(getClass().getSimpleName() + ".jar");
		FileUtils.copy(updateJar::read, jarCopy::write);
		List<String> doUpdateCommand = new ArrayList<>(Arrays.asList("java", //
			// "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8001", //
			"-classpath", "\"" + jarCopy.getPath() + "\"", //
			QuarkApplicationVersioning.class.getName(), //
			"update", //
			"\"" + replacementDirectory.getPath() + "\""));
		if (reLaunchCommand != null)
			doUpdateCommand.addAll(reLaunchCommand);
		new ProcessBuilder(doUpdateCommand)//
			.directory(new File(installDirectory.getPath()))//
			.start();
		if (customShutdown != null)
			customShutdown.run();
		System.exit(0);
	}

	public boolean doUpdate(BetterFile installDirectory, BetterFile replacementDirectory) {
		return doUpdate(installDirectory, replacementDirectory, version -> FileUtils
			.better(Files.createTempDirectory(version.getName() + "-" + version.getVersion() + "-UpdateBackup").toFile()));
	}

	public boolean doUpdate(BetterFile installDirectory, BetterFile replacementDirectory,
		ExFunction<ApplicationVersionDescription, BetterFile, IOException> backup) {
		JFrame updateFrame;
		JLabel fileLabel = new JLabel("Parsing Application Version");
		JProgressBar fileProgress = new JProgressBar();
		if (GraphicsEnvironment.isHeadless()) {
			updateFrame = null;
		} else {
			JPanel updatePanel = new JPanel();
			updatePanel.setLayout(new BoxLayout(updatePanel, BoxLayout.Y_AXIS));
			updatePanel.add(fileLabel);
			fileProgress.setIndeterminate(true);
			updatePanel.add(fileProgress);
			updateFrame = new JFrame("Updating...");
			updateFrame.getContentPane().add(updatePanel);
			updateFrame.setSize(400, 200);
			updateFrame.setLocationRelativeTo(null);
			updateFrame.setVisible(true);
		}

		ApplicationVersionFull version;
		BetterFile backupDir;
		try (InputStream in = replacementDirectory.at("application-version.xml").read()) {
			version = parseFullVersion(StrictXmlReader.ofRoot(in), true);
			if (updateFrame != null)
				updateFrame.setTitle("Updating to " + version.getName() + " " + version.getVersion());

			backupDir = backup.apply(version);
		} catch (IOException | TextParseException e) {
			System.err.println("Could not parse application XML");
			e.printStackTrace();
			if (!GraphicsEnvironment.isHeadless()) {
				updateFrame.setVisible(false);
				JOptionPane.showMessageDialog(null, "Application failed to update", "Update Failed", JOptionPane.ERROR_MESSAGE);
			}
			return false;
		}
		try {
			UpdateProgress progress = new UpdateProgress() {
				private int theFilesComplete;

				@Override
				public void setTotal(int files, int totalSize) {
					fileProgress.setMaximum(files + 1);
				}

				@Override
				public void setTotalProgress(int progress2) {
				}

				@Override
				public void setCurrentFile(String file) {
					fileLabel.setText(file);
				}

				@Override
				public void setCurrentFileSize(int size) {
					theFilesComplete++;
					fileProgress.setIndeterminate(false);
					fileProgress.setValue(theFilesComplete);
				}

				@Override
				public void setCurrentFileProgress(int progress2) {
				}

				@Override
				public boolean isCanceled() {
					return false;
				}
			};
			fileLabel.setText("Calculating...");
			int files=getFileCount(replacementDirectory);
			progress.setTotal(files, 1);
			update(installDirectory, replacementDirectory, backupDir, progress);
		} catch (IOException | RuntimeException | Error e) {
			System.err.println("Could not update application files");
			e.printStackTrace();
			if (!GraphicsEnvironment.isHeadless()) {
				updateFrame.setVisible(false);
				JOptionPane.showMessageDialog(null, "Application failed to update", "Update Failed", JOptionPane.ERROR_MESSAGE);
			}
			restore(installDirectory, backupDir);
			if (updateFrame != null) {
				fileLabel.setText("Restoring previous version");
				updateFrame.setVisible(false);
			}
			return false;
		}
		try {
			backupDir.delete(null);
		} catch (IOException e) {
			System.err.println("Failed to delete backup directory " + backupDir);
			e.printStackTrace();
		}
		try {
			replacementDirectory.delete(null);
		} catch (IOException e) {
			System.err.println("Failed to delete temporary update directory " + replacementDirectory);
			e.printStackTrace();
		}
		if (updateFrame != null)
			updateFrame.setVisible(false);
		return true;
	}

	public BetterFile retrieveDistribution(String baseServiceUrl, Distribution distribution, BetterFile toDirectory, boolean explode,
		UpdateProgress progress) throws IOException {
		return retrieveDistribution(BetterFile.getRoots(new UrlFileSource(new URL(baseServiceUrl))).get(0), distribution, toDirectory,
			explode, progress);
	}

	public BetterFile retrieveDistribution(BetterFile serviceRoot, Distribution distribution, BetterFile toDirectory, boolean explode,
		UpdateProgress progress) throws IOException {
		BetterFile sourceFile = serviceRoot.at(distribution.getApplication().getName() + "-" + distribution.getApplication().getVersion())
			.at(distribution.getFile());
		String fileName = distribution.getFile();
		int lastSlash = fileName.lastIndexOf('/');
		if (lastSlash > 0)
			fileName = fileName.substring(lastSlash + 1);
		BetterFile file = toDirectory.at(//
			StringUtils.getNewItemName(name -> toDirectory.at(name).exists(), fileName, StringUtils.PAREN_DUPLICATES));

		if (progress != null) {
			long len = sourceFile.length();
			progress.setTotal(1, (int) (len / 1024));
			progress.setCurrentFile("Downloading " + fileName + "...");
			progress.setCurrentFileSize((int) (len / 1024));
		}
		FileUtils.copy(//
			sourceFile::read, //
			file::write, p -> {
				if (progress != null) {
					int p2 = (int) (p / 1024);
					progress.setTotalProgress(p2);
					progress.setCurrentFileProgress(p2);
				}
			});
		// Check the hash
		if (progress != null)
			progress.setCurrentFile("Verifying " + file.getName() + "...");
		String hash = file.getCheckSum(distribution.getApplication().getHashType(), () -> progress != null && progress.isCanceled());
		if (progress != null && progress.isCanceled())
			return null;
		if (!hash.equals(distribution.getHash())) {
			file.delete(null);
			throw new IOException("Downloaded distribution file " + fileName + " could not be confirmed--hash is different than reported");
		}
		if (!explode)
			return file;
		if (distribution.isExplode()) {
			progress.setCurrentFile("Extracting " + file.getName() + "...");
			BetterFile copyDir;
			int dot = fileName.indexOf('.');
			copyDir = toDirectory.at(fileName.substring(0, dot));
			BetterFile distZip = BetterFile.at(new ArchiveEnabledFileSource(new NativeFileSource())//
				.withArchival(new ArchiveEnabledFileSource.ZipCompression(), //
					new ArchiveEnabledFileSource.TarArchival(), //
					new ArchiveEnabledFileSource.GZipCompression()), //
				file.getPath());
			FileUtils.sync().from(distZip).to(copyDir).sync();
			return copyDir;
		} else
			return file;
	}

	public void writeApplicationList(BetterFile deployDirectoryRoot) throws IOException, TextParseException {
		List<ApplicationVersionDescription> versions = new ArrayList<>();
		for (BetterFile dir : deployDirectoryRoot.listFiles()) {
			BetterFile appXml = dir.at("application-version.xml");
			if (appXml.exists()) {
				try (InputStream in = appXml.read()) {
					ApplicationVersionDescription app = parseApplication(StrictXmlReader.ofRoot(in), null, true, false);
					if (!dir.getName().equals(app.getName() + "-" + app.getVersion())) {
						System.err.println("Application version " + app.getName() + " " + app.getVersion() + " must be deployed at "
							+ deployDirectoryRoot.getPath() + "/" + app.getName() + "-" + app.getVersion() + ", not " + dir.getName());
					}
					versions.add(app);
				}
			}
		}
		try (Writer w = new OutputStreamWriter(deployDirectoryRoot.at("application-versions.xml").write())) {
			XmlSerialWriter.createDocument(w).writeRoot("application-versions", root -> {
				for (ApplicationVersionDescription version : versions) {
					root.addChild("application-version", xml -> writeVersionXml(xml, version));
				}
			});
		}
	}

	public static void main(String... args) {
		QuarkApplicationVersioning versioning = new QuarkApplicationVersioning();
		NativeFileSource fileSource = new NativeFileSource();
		switch (args[0]) {
		case "deploy":
			BetterFile deployXml = BetterFile.at(fileSource, args[1]);
			BetterFile sourceDir = BetterFile.at(fileSource, args[2]);
			BetterFile deployDir = BetterFile.at(fileSource, args[3]);
			BetterFile distDir = BetterFile.at(fileSource, args[4]);
			if (!deployXml.exists())
				throw new IllegalArgumentException(deployXml.getPath() + " not found");
			ApplicationVersionDeployment deploy;
			System.out.print("Parsing deployment XML...");
			System.out.flush();
			try (InputStream in = deployXml.read()) {
				deploy = versioning.parseDeployment(in, distDir);
			} catch (IOException | TextParseException e) {
				throw new IllegalArgumentException("Could not read deployment configuration", e);
			}
			System.out.println("Parsed deployment for " + deploy.getName() + " " + deploy.getVersion());
			System.out.println("Deploying...");
			try {
				versioning.deployVersion(deploy, sourceDir, deployDir, "distributions", distDir);
			} catch (IOException e) {
				throw new IllegalStateException("Deployment failed", e);
			}
			System.out.println("Deployment success.  Generating application list file...");
			try {
				versioning.writeApplicationList(deployDir);
				System.out.println("Success.");
			} catch (IOException | TextParseException e) {
				System.err.println("Failed to generate application list file");
				e.printStackTrace();
			}
			break;
		case "update":
			try {
				Thread.sleep(3000); // Wait for the process to truly die
			} catch (InterruptedException e) {
			}
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
				e.printStackTrace();
			}
			BetterFile replacementDir = BetterFile.at(fileSource, args[1]);
			versioning.doUpdate(BetterFile.at(fileSource, System.getProperty("user.dir")), replacementDir);
			if (args.length > 2) {
				List<String> lauchCommand = Arrays.asList(args).subList(2, args.length);
				try {
					new ProcessBuilder(lauchCommand).start();
				} catch (IOException e) {
					JOptionPane.showMessageDialog(null, "Could not re-launch application", e.getMessage(), JOptionPane.ERROR_MESSAGE);
					System.err.println("Could not re-launch application");
					e.printStackTrace();
				}
			}
			System.exit(0);
			break;
		default:
			throw new IllegalArgumentException("Unrecognized action: " + args[0]);
		}
	}

	public static ApplicationVersionDescription parseApplication(StrictXmlReader appXml, BetterFile distributionDir,
		boolean withDistributions, boolean strict) throws IOException, TextParseException {
		String name = appXml.getAttribute("name");
		String versionS = appXml.getAttribute("version");
		String releaseS = appXml.getAttribute("release-date");
		String description = appXml.getAttribute("description").replaceAll("\\s+", " ");
		String updateJar = appXml.getAttribute("update-jar");
		String hashTypeS = appXml.getAttribute("hash-type");

		Version version;
		try {
			version = Version.parse(versionS);
		} catch (IllegalArgumentException e) {
			throw new TextParseException("Version is unrecognizable", appXml.getAttributeValuePosition("version").getPosition(0), e);
		}
		ReleaseDate releaseDate;
		try {
			ParsedInstant releaseInst = TimeUtils.parseInstant(releaseS, true, true, teo -> teo.gmt());
			if (releaseInst.getField(TimeUtils.DateElementType.Year) == null
				|| releaseInst.getField(TimeUtils.DateElementType.Month) == null
				|| releaseInst.getField(TimeUtils.DateElementType.Day) == null)
				throw new TextParseException("Release date must include day, month, and year",
					appXml.getAttributeValuePosition("release-date").getPosition(0));
			releaseDate = new ReleaseDate(//
				releaseInst.getField(TimeUtils.DateElementType.Year).getValue(), //
				releaseInst.getField(TimeUtils.DateElementType.Month).getValue() + 1, //
				releaseInst.getField(TimeUtils.DateElementType.Day).getValue());
		} catch (ParseException e) {
			throw new TextParseException("Release date is unrecognizable",
				appXml.getAttributeValuePosition("release-date").getPosition(e.getErrorOffset()), e);
		}
		BetterFile.CheckSumType hashType;
		try {
			hashType = BetterFile.CheckSumType.parse(hashTypeS);
		} catch (IllegalArgumentException e) {
			throw new TextParseException("Unrecognized hash type: " + hashTypeS,
				appXml.getAttributeValuePosition("hash-type").getPosition(0));
		}
		List<ApplicationChange> changeList = new ArrayList<>();
		try (StrictXmlReader changeListXml = appXml.getElement("change-list")) {
			for (StrictXmlReader changeXml : changeListXml.getElements("change")) {
				String changeName = changeXml.getAttribute("name");
				String value = changeXml.getTextTrimIfExists();
				changeList.add(new ApplicationChange(//
					changeName.replaceAll("\\s+", " "), //
					value == null ? null : value.replaceAll("\\s+", " ")));
				if (strict)
					changeXml.check();
			}
			if (strict)
				changeListXml.check();
		}
		List<Distribution> distributions = new ArrayList<>();
		ApplicationVersionDescription app = new ApplicationVersionDescription(name, version, releaseDate, description, updateJar,
			Collections.unmodifiableList(changeList), Collections.unmodifiableList(distributions), hashType);
		if (withDistributions) {
			try (StrictXmlReader distributionsXml = appXml.getElement("distributions")) {
				for (StrictXmlReader distXml : distributionsXml.getElements("distribution"))
					distributions.add(parseDistribution(distXml, distributionDir, app, hashType, strict));
				if (strict)
					distributionsXml.check();
			}
		}
		return app;
	}

	private static Distribution parseDistribution(StrictXmlReader distXml, BetterFile distributionDir,
		ApplicationVersionDescription application, BetterFile.CheckSumType hashType, boolean strict)
		throws TextParseException, IOException {
		String name = distXml.getAttribute("name");
		String descrip = distXml.getAttribute("description").replaceAll("\\s+", " ");
		String file = distXml.getAttribute("file");
		String explodeS = distXml.getAttributeIfExists("explode");
		boolean explode = explodeS != null && "true".equals(explodeS);
		if (!explode && explodeS != null && !"false".equals(explodeS))
			throw new TextParseException("'explode' must be 'true' or 'false'",
				distXml.getAttributeValuePosition("explode").getPosition(0));
		String hashS = distXml.getAttributeIfExists("hash");
		if (hashS == null && distributionDir == null)
			throw new TextParseException("No 'hash' attribute found", distXml.getNamePosition());
		Map<String, Requirement> requirements = new LinkedHashMap<>();
		try (StrictXmlReader reqsXml = distXml.getElementIfExists("requirements")) {
			for (StrictXmlReader reqXml : reqsXml.getElements("require")) {
				String type = reqXml.getAttribute("type");
				List<String> values = BetterList.of2(reqXml.getElements("value", 0, -1).stream(), el -> el.getTextTrim());
				String minS = reqXml.getAttributeIfExists("min");
				if (values.isEmpty() && minS == null)
					throw new TextParseException("Either a min value or a list of values must be specified on a requirement",
						reqXml.getNamePosition());
				else if (!values.isEmpty() && minS != null)
					throw new TextParseException("Either a min value or a list of values, but not both, must be specified on a requirement",
						reqXml.getNamePosition());
				String maxS = reqXml.getAttributeIfExists("max");
				if (strict)
					reqXml.check();
				Version min, max;
				try {
					min = minS == null ? null : Version.parse(minS);
				} catch (IllegalArgumentException e) {
					throw new TextParseException("min version '" + minS + "' is unrecognizable",
						reqXml.getElement("min").getTextTrimPosition().getPosition(0), e);
				}
				try {
					max = maxS == null ? null : Version.parse(minS);
				} catch (IllegalArgumentException e) {
					throw new TextParseException("min version '" + minS + "' is unrecognizable",
						reqXml.getElement("min").getTextTrimPosition().getPosition(0), e);
				}
				requirements.put(type, new Requirement(QommonsUtils.unmodifiableDistinctCopy(values), min, max));
			}
			if (strict)
				reqsXml.check();
		}
		if (strict)
			distXml.check();
		String hash;
		if (hashS == null) {
			BetterFile distributionFile = distributionDir.at(file);
			if (!distributionDir.exists())
				throw new TextParseException("Distribution file '" + file + "' not found",
					distXml.getAttributeValuePosition("file").getPosition(0));
			hash = distributionFile.getCheckSum(hashType, null);
		} else
			hash = hashS;
		return new Distribution(application, name, descrip, file, hash, explode, Collections.unmodifiableMap(requirements));
	}

	private static DirectoryDeployment parseDeployPolicy(StrictXmlReader policyXml, boolean root) throws TextParseException {
		FileDeployment file = parseDeployFile(policyXml, root);
		List<FileDeployment> files = null;
		for (StrictXmlReader fileXml : policyXml.getElements("file")) {
			if (file.isIgnored())
				throw new TextParseException("If ignore is true, contents don't matter", fileXml.getNamePosition());
			if (files == null)
				files = new ArrayList<>();
			files.add(parseDeployFile(fileXml, false));
		}
		if (files == null)
			files = Collections.emptyList();
		else
			files = Collections.unmodifiableList(files);
		List<DirectoryDeployment> directories = null;
		for (StrictXmlReader dirXml : policyXml.getElements("directory")) {
			if (file.isIgnored())
				throw new TextParseException("If ignore is true, contents don't matter", dirXml.getNamePosition());
			if (directories == null)
				directories = new ArrayList<>();
			directories.add(parseDeployPolicy(dirXml, false));
		}
		if (directories == null)
			directories = Collections.emptyList();
		else
			directories = Collections.unmodifiableList(directories);
		policyXml.check();
		DirectoryDeployment policy = new DirectoryDeployment(file.getNamePattern(), file.getRename(), file.isCompress(), file.isIgnored(),
			files, directories);
		return policy;
	}

	private static FileDeployment parseDeployFile(StrictXmlReader fileXml, boolean root) throws TextParseException {
		Pattern namePattern;
		if (root)
			namePattern = null;
		else {
			try {
				namePattern = Pattern.compile(fileXml.getAttribute("name"));
			} catch (PatternSyntaxException e) {
				throw new TextParseException("Bad name pattern: " + e.getMessage(),
					fileXml.getAttributeValuePosition("name").getPosition(e.getIndex()), e);
			}
		}
		String ignoreS = fileXml.getAttributeIfExists("ignore");
		boolean ignore = "true".equalsIgnoreCase(ignoreS);
		if (!ignore && ignoreS != null && !"false".equalsIgnoreCase(ignoreS))
			throw new TextParseException("'ignore' must be 'true' or 'false'", fileXml.getAttributeValuePosition("ignore").getPosition(0));
		String rename = fileXml.getAttributeIfExists("rename");
		String compressS = fileXml.getAttributeIfExists("compress");
		if (ignore) {
			if (rename != null)
				throw new TextParseException("If ignore is true, rename doesn't matter", fileXml.getAttributeNamePosition("rename"));
			if (compressS != null)
				throw new TextParseException("If ignore is true, compression doesn't matter", fileXml.getAttributeNamePosition("compress"));
		}
		boolean compress = "true".equalsIgnoreCase(compressS);
		if (!compress && compressS != null && !"false".equalsIgnoreCase(compressS))
			throw new TextParseException("'compress' must be 'true' or 'false'",
				fileXml.getAttributeValuePosition("compress").getPosition(0));
		return new FileDeployment(namePattern, rename, compress, ignore);
	}

	private static ApplicationDirectory deployDirectory(StringBuilder path, DirectoryDeployment deployPolicy, BetterFile sourceDirectory,
		BetterFile deployDirectory, BetterFile.CheckSumType hashType) throws IOException {
		if (!deployDirectory.exists())
			deployDirectory.create(true);
		Map<String, ApplicationFile> files = new LinkedHashMap<>();
		Map<String, ApplicationDirectory> dirs = new LinkedHashMap<>();
		List<Pattern> ignoredFiles = QommonsUtils.filterMap(deployPolicy.getFiles(), f -> f.isIgnored(), f -> f.getNamePattern());
		List<Pattern> ignoredDirs = QommonsUtils.filterMap(deployPolicy.getDirectories(), f -> f.isIgnored(), f -> f.getNamePattern());
		int pathLen = path.length();
		for (BetterFile file : sourceDirectory.listFiles()) {
			path.append(file.getName());
			FileDeployment filePolicy = null;
			Matcher nameMatch = null;
			for (FileDeployment f : (file.isDirectory() ? deployPolicy.getDirectories() : deployPolicy.getFiles())) {
				nameMatch = f.getNamePattern().matcher(file.getName());
				if (nameMatch.matches()) {
					filePolicy = f;
					break;
				}
			}
			if (filePolicy == null) {
				System.err.println("No " + (file.isDirectory() ? "directory" : "file") + " matching " + file.getName() + "--ignored");
			} else if (filePolicy.isIgnored()) {//
			} else {
				String rename = filePolicy.getRename();
				if (rename != null)
					rename = getRename(rename, nameMatch, path, filePolicy);
				else if (filePolicy.isCompress())
					rename = file.getName() + ".zip";
				else
					rename = file.getName();
				if (file.isDirectory()) {
					path.append('/');
					if (filePolicy.isCompress()) {
						System.out.println("Compressing " + path + " to " + rename);
						BetterFile targetZip = deployDirectory.at(rename);
						if (targetZip.exists())
							targetZip.delete(null);
						try (OutputStream out = targetZip.write(); //
							ZipOutputStream zip = new ZipOutputStream(out)) {
							compressDirectory(file, zip, new StringBuilder(), (DirectoryDeployment) filePolicy);
						}
						files.put(rename, new ApplicationFile(file.getName(), targetZip.getCheckSum(hashType, null), true));
					} else {
						if (rename.equals(file.getName()))
							System.out.println("Deploying " + path);
						else
							System.out.println("Deploying " + path + " as " + rename);
						dirs.put(rename,
							deployDirectory(path, (DirectoryDeployment) filePolicy, file, deployDirectory.at(rename), hashType));
					}
				} else {
					if (rename.equals(file.getName()))
						System.out.println("Copying " + path);
					else
						System.out.println("Copying " + path + " as " + rename);
					files.put(rename, copyFile(path, file, deployDirectory.at(rename), file.getName(), hashType, filePolicy.isCompress()));
				}
			}
			path.setLength(pathLen);
		}
		return new ApplicationDirectory(sourceDirectory.getName(), Collections.unmodifiableMap(files), Collections.unmodifiableMap(dirs),
			ignoredFiles, ignoredDirs);
	}
	private static final Pattern GROUP_REPLACE_PATTERN = Pattern.compile("\\$(?<group>\\d+)");

	private static String getRename(String rename, Matcher sourceName, StringBuilder path, FileDeployment dirPolicy) {
		Matcher renameMatch = GROUP_REPLACE_PATTERN.matcher(rename);
		int diff = 0;
		while (renameMatch.find()) {
			int group = Integer.parseInt(renameMatch.group("group"));
			String groupValue;
			try {
				groupValue = sourceName.group(group);
			} catch (IndexOutOfBoundsException e) {
				throw new IllegalStateException("Bad deployment '" + dirPolicy.getNamePattern() + "' for " + path + ": rename uses group "
					+ group + ", but there are not that many", e);
			}
			rename = rename.substring(0, diff + renameMatch.start()) + groupValue + rename.substring(diff + renameMatch.end());
			diff += groupValue.length() - (renameMatch.end() - renameMatch.start());
		}
		return rename;
	}

	private static ApplicationFile copyFile(StringBuilder path, BetterFile source, BetterFile target, String sourceName,
		CheckSumType hashType, boolean compress) throws IOException {
		if (target.exists())
			target.delete(null);
		try {
			FileUtils.copy(source::read, target::write);
		} catch (IOException e) {
			throw new IOException("Could not deploy " + path, e);
		}
		return new ApplicationFile(sourceName, target.getCheckSum(hashType, null), false);
	}

	private static void compressDirectory(BetterFile dir, ZipOutputStream zip, StringBuilder path, DirectoryDeployment deployPolicy)
		throws IOException {
		int pathLen = path.length();
		for (BetterFile file : dir.listFiles()) {
			path.append(file.getName());
			FileDeployment filePolicy = null;
			Matcher nameMatch = null;
			if (deployPolicy != null) {
				for (FileDeployment f : (file.isDirectory() ? deployPolicy.getDirectories() : deployPolicy.getFiles())) {
					nameMatch = f.getNamePattern().matcher(file.getName());
					if (nameMatch.matches()) {
						filePolicy = f;
						break;
					}
				}
			}
			if (filePolicy != null && filePolicy.isIgnored()) {//
			} else {
				String rename = filePolicy == null ? null : filePolicy.getRename();
				if (rename != null)
					rename = getRename(rename, nameMatch, path, filePolicy);
				else
					rename = file.getName() + ".zip";
				if (file.isDirectory()) {
					path.append('/');
					ZipEntry entry = new ZipEntry(path.toString());
					zip.putNextEntry(entry);
					zip.closeEntry();
					compressDirectory(file, zip, path, (DirectoryDeployment) filePolicy);
				} else {
					ZipEntry entry = new ZipEntry(path.toString());
					entry.setCreationTime(FileTime.fromMillis(file.getLastModified()));
					zip.putNextEntry(entry);
					try (InputStream in = file.read()) {
						FileUtils.copy(in, zip);
					}
					zip.closeEntry();
				}
			}
			path.setLength(pathLen);
		}
	}

	private static void writeVersionXml(ApplicationVersionFull full, BetterFile file) throws IOException {
		try (Writer writer = new BufferedWriter(new OutputStreamWriter(file.write()))) {
			XmlSerialWriter.createDocument(writer).writeRoot("application-version", root -> {
				writeVersionXml(root, full);
				root.addChild("files", el -> writeFiles(full.getRootDirectory(), el));
			});
		}
	}

	private static void writeVersionXml(Element xml, ApplicationVersionDescription version) throws IOException {
		xml.addAttribute("name", version.getName())//
			.addAttribute("version", version.getVersion().toString())//
			.addAttribute("release-date", version.getReleaseDate().toString())//
			.addAttribute("description", version.getDescription())//
			.addAttribute("update-jar", version.getUpdateJar())//
			.addAttribute("hash-type", version.getHashType().toString())//
			.addChild("change-list", el -> writeChangeList(version.getChangeList(), el))//
			.addChild("distributions", el -> writeDistributions(version.getDistributions(), el))//
		;
	}

	private static void writeChangeList(List<ApplicationChange> changeList, XmlSerialWriter.Element el) throws IOException {
		for (ApplicationChange change : changeList) {
			el.addChild("change", chXml -> {
				chXml.addAttribute("name", change.getName());
				if (change.getDescription() != null)
					chXml.addContent(change.getDescription());
			});
		}
	}

	private static void writeDistributions(List<Distribution> distributions, XmlSerialWriter.Element el) throws IOException {
		for (Distribution distribution : distributions) {
			el.addChild("distribution", distXml -> {
				distXml.addAttribute("name", distribution.getName())//
					.addAttribute("file", distribution.getFile());
				if (distribution.isExplode())
					distXml.addAttribute("explode", "true");
				if (distribution.getHash() != null)
					distXml.addAttribute("hash", distribution.getHash());
				distXml.addAttribute("description", distribution.getDescription());
				if (!distribution.getRequirements().isEmpty()) {
					distXml.addChild("requirements", reqsXml -> {
						for (Map.Entry<String, Requirement> req : distribution.getRequirements().entrySet()) {
							reqsXml.addChild("require", reqXml -> {
								reqXml.addAttribute("type", req.getKey());
								if (req.getValue().getMinimum() != null) {
									reqXml.addAttribute("min", req.getValue().getMinimum().toString());
									if (req.getValue().getMaximum() != null)
										reqXml.addAttribute("max", req.getValue().getMaximum().toString());
								} else if (!req.getValue().getAllowedValues().isEmpty()) {
									for (String value : req.getValue().getAllowedValues())
										reqXml.addChild("value", vXml -> vXml.addContent(value));
								} else
									System.err.println("Neither min or allowed values specified");
							});
						}
					});
				}
			});
		}
	}

	private static void writeFiles(ApplicationDirectory dir, XmlSerialWriter.Element el) throws IOException {
		for (Map.Entry<String, ApplicationFile> file : dir.getFiles().entrySet()) {
			el.addChild("file", fileXml -> {
				fileXml.addAttribute("name", file.getKey()).addAttribute("hash", file.getValue().getHash());
				if (file.getValue().isExplode())
					fileXml.addAttribute("explode", "true");
				if (file.getValue().getRename() != null)
					fileXml.addAttribute("rename", file.getValue().getRename());
			});
		}
		for (Pattern file : dir.getIgnoredFiles())
			el.addChild("file", fileXml -> fileXml.addAttribute("name", file.pattern()).addAttribute("ignore", "true"));
		for (Map.Entry<String, ApplicationDirectory> subDir : dir.getSubDirectories().entrySet()) {
			el.addChild("directory", dirXml -> {
				dirXml.addAttribute("name", subDir.getKey());
				writeFiles(subDir.getValue(), dirXml);
			});
		}
		for (Pattern subDir : dir.getIgnoredDirectories())
			el.addChild("directory", fileXml -> fileXml.addAttribute("name", subDir.pattern()).addAttribute("ignore", "true"));
	}

	private static ApplicationVersionFull parseFullVersion(StrictXmlReader xml, boolean strict) throws TextParseException {
		ApplicationVersionDescription app;
		try {
			app = parseApplication(xml, null, true, strict);
		} catch (IOException e) {
			throw new IllegalStateException("Should not be here", e);
		}
		ApplicationDirectory root = parseAppDirectory(xml.getElement("files"), null, strict);
		if (strict)
			xml.check();
		List<Distribution> distributions = new ArrayList<>();
		ApplicationVersionFull full = new ApplicationVersionFull(app.getName(), app.getVersion(), app.getReleaseDate(),
			app.getDescription(), app.getUpdateJar(), app.getChangeList(), app.getHashType(), Collections.unmodifiableList(distributions),
			root);
		for (Distribution dist : app.getDistributions()) {
			distributions.add(new Distribution(full, dist.getName(), dist.getDescription(), dist.getFile(), dist.getHash(),
				dist.isExplode(), dist.getRequirements()));
		}
		return full;
	}

	private static ApplicationDirectory parseAppDirectory(StrictXmlReader xml, String name, boolean strict) throws TextParseException {
		Map<String, ApplicationFile> files = new LinkedHashMap<>();
		List<Pattern> ignoredFiles = new ArrayList<>();
		String rename = xml.getAttribute("rename", name);
		for (StrictXmlReader fileXml : xml.getElements("file")) {
			String ignoreS = fileXml.getAttributeIfExists("ignore");
			boolean ignore = ignoreS != null && "true".equals(ignoreS);
			if (!ignore && ignoreS != null && !"false".equals(ignoreS))
				throw new TextParseException("'ignore' must be 'true' or 'false', not '" + ignoreS + "'",
					fileXml.getAttributeValuePosition("ignore").getPosition(0));
			String explodeS = fileXml.getAttributeIfExists("explode");
			if (ignore && explodeS != null)
				throw new TextParseException("If 'ignore' is true, 'explode' doesn't matter",
					fileXml.getAttributeValuePosition("explode").getPosition(0));
			boolean explode = explodeS != null && "true".equals(explodeS);
			if (!explode && explodeS != null && !"false".equals(explodeS))
				throw new TextParseException("'explode' must be 'true' or 'false', not '" + explodeS + "'",
					fileXml.getAttributeValuePosition("explode").getPosition(0));
			if (ignore)
				ignoredFiles.add(Pattern.compile(fileXml.getAttribute("name")));
			else
				files.put(fileXml.getAttribute("name"),
					new ApplicationFile(fileXml.getAttributeIfExists("rename"), fileXml.getAttribute("hash"), explode));
			if (strict)
				fileXml.check();
		}
		Map<String, ApplicationDirectory> subDirs = new LinkedHashMap<>();
		List<Pattern> ignoredDirs = new ArrayList<>();
		for (StrictXmlReader dirXml : xml.getElements("directory")) {
			String dirName = dirXml.getAttribute("name");
			String ignoreS = dirXml.getAttributeIfExists("ignore");
			boolean ignore = ignoreS != null && "true".equals(ignoreS);
			if (!ignore && ignoreS != null && !"false".equals(ignoreS))
				throw new TextParseException("'ignore' must be 'true' or 'false', not '" + ignoreS + "'",
					dirXml.getAttributeValuePosition("ignore").getPosition(0));
			if (ignore) {
				ignoredDirs.add(Pattern.compile(dirName));
				if (strict)
					dirXml.check();
			} else
				subDirs.put(dirName, parseAppDirectory(dirXml, dirName, strict));
		}
		if (strict)
			xml.check();
		return new ApplicationDirectory(rename, Collections.unmodifiableMap(files), Collections.unmodifiableMap(subDirs), ignoredFiles,
			ignoredDirs);
	}

	private long getTotalSize(ApplicationDirectory appDir, BetterFile deployDir, BetterFile installDir, CheckSumType hashType, int[] files,
		UpdateProgress progress) throws IOException {
		long total = 0;
		files[0] += appDir.getFiles().size();
		for (Map.Entry<String, ApplicationFile> file : appDir.getFiles().entrySet()) {
			String newName;
			if (file.getValue().getRename() != null)
				newName = file.getValue().getRename();
			else
				newName = file.getKey();
			BetterFile existing = installDir.at(newName);
			String existingHash = (existing.exists() && !existing.isDirectory())
				? existing.getCheckSum(hashType, () -> progress != null && progress.isCanceled()) : null;
			if (progress != null && progress.isCanceled())
				return 0;
			if (existing.exists() && !existing.isDirectory() && existingHash.equals(file.getValue().getHash())) {
				// Existing file hasn't changed, use it. Should be much faster than the grabbing remote files.
			} else
				total += deployDir.at(file.getKey()).length();
		}
		for (Map.Entry<String, ApplicationDirectory> dir : appDir.getSubDirectories().entrySet())
			total += getTotalSize(dir.getValue(), deployDir.at(dir.getKey()), installDir.at(dir.getKey()), hashType, files, progress);
		return total;
	}

	private long copyUpdateFiles(ApplicationDirectory appDir, StringBuilder path, BetterFile deployDir, BetterFile tempDir,
		BetterFile installDir, CheckSumType hashType, UpdateProgress progress, long progressBytes) throws IOException {
		int preLen = path.length();
		if (!tempDir.exists())
			tempDir.create(true);
		for (Map.Entry<String, ApplicationFile> file : appDir.getFiles().entrySet()) {
			if (progress != null && progress.isCanceled())
				return progressBytes;
			String newName;
			if (file.getValue().getRename() != null)
				newName = file.getValue().getRename();
			else
				newName = file.getKey();
			BetterFile target = tempDir.at(newName);
			BetterFile existing = installDir.at(newName);
			if (progress != null)
				progress.setCurrentFile("Checking " + path.toString() + "...");
			String existingHash = (existing.exists() && !existing.isDirectory())
				? existing.getCheckSum(hashType, () -> progress != null && progress.isCanceled()) : null;
			if (progress != null && progress.isCanceled())
				return 0;
			if (existing.exists() && !existing.isDirectory() && existingHash.equals(file.getValue().getHash())) {
				if (progress != null)
					progress.setCurrentFile("Preserving " + path.toString() + "...");
				FileUtils.copy(existing::read, target::write); // Existing file hasn't changed, use it
			} else {
				if (progress != null)
					progress.setCurrentFile("Downloading " + path.toString() + "...");
				BetterFile deployFile = deployDir.at(file.getKey());
				if (progress != null)
					progress.setCurrentFileSize((int) (deployFile.length() / 1024));
				path.append(newName);
				long pb = progressBytes;
				progressBytes += FileUtils.copy(//
					deployFile::read, //
					target::write, p -> {
						if (progress != null) {
							progress.setCurrentFileProgress((int) (p / 1024));
							progress.setTotalProgress((int) ((pb + p) / 10240));
						}
					});
				if (progress != null)
					progress.setCurrentFile("Verifying " + path.toString() + "...");
				String hash = target.getCheckSum(hashType, () -> progress != null && progress.isCanceled());
				if (progress != null && progress.isCanceled())
					return 0;
				if (!hash.equals(file.getValue().getHash()))
					throw new IOException("Update file " + path + " could not be confirmed--hashes do not match");
				path.setLength(preLen);
			}
			if (file.getValue().isExplode()) {
				if (progress != null)
					progress.setCurrentFile("Extracting " + path.toString() + newName + "...");
				if (file.getValue().getRename() == null) {
					int dot = file.getKey().indexOf('.');
					newName = file.getKey().substring(0, dot);
				}
				if (!target.isDirectory() //
					&& file.getValue().getRename() != null && file.getValue().getRename().indexOf('.') < 0) { // Assume it's a zip then
					target = target.move(target.getParent().at(newName + ".zip"));
				} else
					throw new IOException("Could not explode " + newName + "--unrecognized archive extension");
				FileUtils.sync().from(target).to(tempDir.at(newName)).sync();
				target.delete(null);
			}
		}
		for (Map.Entry<String, ApplicationDirectory> subDir : appDir.getSubDirectories().entrySet()) {
			if (progress != null && progress.isCanceled())
				return progressBytes;
			path.append(subDir.getKey()).append('/');
			progressBytes = copyUpdateFiles(subDir.getValue(), path, deployDir.at(subDir.getKey()), tempDir.at(subDir.getKey()),
				installDir.at(subDir.getKey()), hashType, progress, progressBytes);
			path.setLength(preLen);
		}
		return progressBytes;
	}
	
	private static int getFileCount(BetterFile dir) {
		if(!dir.isDirectory())
			return 1;
		int files=0;
		for(BetterFile file : dir.listFiles())
			files+=getFileCount(file);
		return files;
	}

	private void update(BetterFile install, BetterFile updated, BetterFile backupDir, UpdateProgress progress) throws IOException {
		if (backupDir != null && !backupDir.exists())
			backupDir.create(true);
		for (BetterFile updateFile : updated.listFiles()) {
			BetterFile installFile = install.at(updateFile.getName());
			if (updateFile.isDirectory()) {
				BetterFile backupFile;
				if (installFile.isDirectory()) {
					backupFile = backupDir == null ? null : backupDir.at(updateFile.getName());
					if (backupFile != null)
						backupFile.create(true);
				} else {
					if (installFile.exists()) {
						if (backupDir != null)
							installFile.move(backupDir.at(updateFile.getName()));
						else
							installFile.delete(null);
					}
					backupFile = null;
				}
				update(installFile, updateFile, backupFile, progress);
			} else {
				progress.setCurrentFile(updateFile.getName());
				progress.setCurrentFileSize(1);
				if (installFile.exists()) {
					if (backupDir != null)
						installFile.move(backupDir.at(updateFile.getName()));
					else
						installFile.delete(null);
				}
				updateFile.move(installFile);
			}
		}
		updated.delete(null);
	}

	private void restore(BetterFile installDir, BetterFile backupDir) {
		for (BetterFile backupFile : backupDir.listFiles()) {
			BetterFile installFile = installDir.at(backupFile.getName());
			try {
				if (installFile.exists())
					installFile.delete(null);
				installFile.create(!backupFile.isFile());
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (backupFile.isDirectory())
				restore(installFile, backupFile);
			else {
				try {
					FileUtils.copy(backupDir::read, installDir::write);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}

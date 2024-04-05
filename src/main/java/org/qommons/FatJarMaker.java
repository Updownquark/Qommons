package org.qommons;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.qommons.io.ArchiveEnabledFileSource;
import org.qommons.io.BetterFile;
import org.qommons.io.BetterFile.FileDataSource;
import org.qommons.io.FileUtils;
import org.qommons.io.Format;
import org.qommons.io.NativeFileSource;

/** Creates an executable jar file containing all dependencies included from the classpath, with configured exclusions */
public class FatJarMaker {
	/**
	 * @param clArgs Command-line arguments:
	 *        <ul>
	 *        <li>--target=<file-path> The jar file to create</li>
	 *        <li>--manifest=<file-path> The manifest file for the jar</li>
	 *        <li>--exclude=pattern1,pattern2,... Patterns of entries to exclude. The entry must end with a pattern to be excluded.</li>
	 *        </ul>
	 * @throws IOException If the jar file could not be created
	 */
	public static void main(String... clArgs) throws IOException {
		ArgumentParsing.Arguments args = ArgumentParsing.build()//
			.forValuePattern(p -> p//
				.addBetterFileArgument("target", a -> a.optional().directory(false))//
				.addBetterFileArgument("manifest", a -> a.optional().directory(false).mustExist(true))//
			).forMultiValuePattern(p -> p//
				.addArgument("exclude", Pattern.class, (text, otherArgs) -> {
					try {
						return Pattern.compile(text);
					} catch (PatternSyntaxException e) {
						throw new ParseException(e.getMessage(), e.getIndex());
					}
				}, a -> a.anyTimes())//
			)//
			.build()//
			.parse(clArgs);

		FileDataSource cpSource = new ArchiveEnabledFileSource(new NativeFileSource())//
			.withArchival(new ArchiveEnabledFileSource.ZipCompression());

		BetterFile target = args.get("target", BetterFile.class);
		if (target == null) {
			String currentDir = new File(".").getCanonicalFile().getName();
			target = BetterFile.at(new NativeFileSource(), "target/" + currentDir + ".jar");
			System.out.println("Using default target jar " + target.getPath());
		} else
			System.out.println("Creating target jar " + target.getPath());
		long targetFileSize = 0;
		StringBuilder path = new StringBuilder();
		int entryCount = 0, fileCount = 0;
		Set<String> entries = new HashSet<>();
		Format<Double> fileSizeFormat = Format.doubleFormat(3).withUnit("B", false).withMetricPrefixesPower2().build();

		TreeMap<Long, String> entriesBySize = new TreeMap<>();
		try (ZipOutputStream jarStream = new ZipOutputStream(target.write())) {
			BetterFile manifest = args.get("manifest", BetterFile.class);
			if (manifest != null) {
				ZipEntry entry = new ZipEntry("META-INF/MANIFEST.MF");
				entry.setTime(manifest.getLastModified());
				jarStream.putNextEntry(entry);
				try (InputStream mfIn = manifest.read()) {
					FileUtils.copy(mfIn, jarStream);
				}
			}

			List<? extends Pattern> excludes = args.getAll("exclude", Pattern.class);
			String[] classPath = System.getProperty("java.class.path").split(";");
			for (String cp : classPath) {
				cp = cp.replace("\\", "/");
				boolean excluded = false;
				for (Pattern exclude : excludes) {
					Matcher match = exclude.matcher(cp);
					while (match.find()) {
						if (match.end() == cp.length()) {
							excluded = true;
							break;
						}
					}
					if (excluded)
						break;
				}
				if (excluded) {
					System.out.println("Excluded entry " + cp);
					continue;
				}

				BetterFile cpDir;
				try {
					cpDir = BetterFile.at(cpSource, cp);
				} catch (RuntimeException e) {
					System.err.println("Could not include classpath entry " + cp);
					e.printStackTrace();
					continue;
				}
				if (!cpDir.isDirectory()) {
					System.err.println("Classpath entry could not be read: " + cp);
					continue;
				}
				System.out.println("Packaging entry " + cp);
				path.setLength(0);
				entryCount++;
				int entryFileCount = copyCpEntry(cpDir, jarStream, path, true, entries);
				jarStream.flush();
				long newFS = target.length();
				long entrySize = newFS - targetFileSize;
				System.out.println(
					"\t" + entryFileCount + " files\n\t" + fileSizeFormat.format(Double.valueOf(entrySize)) + " compressed");
				targetFileSize = newFS;
				fileCount += entryFileCount;

				while (entriesBySize.containsKey(entrySize))
					entrySize++;
				entriesBySize.put(entrySize, cp);
			}
		}
		targetFileSize=target.length();
		String fileSize = fileSizeFormat.format(Double.valueOf(targetFileSize));
		System.out.println("\nCreated " + target.getPath() + " (" + fileSize + ") containing " + fileCount + " files from " + entryCount
			+ " classpath entries");
		for (Map.Entry<Long, String> entry : entriesBySize.descendingMap().entrySet()) {
			long pct=entry.getKey()*1000/targetFileSize;
			System.out.println(
				"\t" + entry.getValue() + ": " + fileSizeFormat.format(Double.valueOf(entry.getKey())) + " (" + (pct / 10.0) + "%)");
		}
	}

	private static int copyCpEntry(BetterFile cpFile, ZipOutputStream jarStream, StringBuilder path, boolean root, Set<String> entries)
		throws IOException {
		int preLen = path.length();
		try {
			if (!root)
				path.append(cpFile.getName());
			if (!root && cpFile.isFile()) {
				String entryPath = path.toString();
				if (entries.add(entryPath)) {
					ZipEntry entry = new ZipEntry(entryPath);
					entry.setTime(cpFile.getLastModified());
					jarStream.putNextEntry(entry);
					try (InputStream cpIn = cpFile.read()) {
						FileUtils.copy(cpIn, jarStream);
					}
					return 1;
				} else
					return 0; // Duplicate entry
			}
			boolean mfDir = path.length() == "META-INF".length() && path.toString().equals("META-INF");
			if (mfDir) {
				// Java's zip output stream does not allow multiple entries with the same name.
				// Many of these can occur in manifest entries.
				// Ideally, we would include many of these, such as the service entries, but it can't be helped here.
				return 0;
			}
			if (!root)
				path.append('/');
			int count = 0;
			for (BetterFile content : cpFile.listFiles())
				count += copyCpEntry(content, jarStream, path, false, entries);
			return count;
		} finally {
			path.setLength(preLen);
		}
	}
}

package org.qommons;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.qommons.io.CsvParser;
import org.qommons.io.TextParseException;
import org.qommons.io.XmlSerialWriter;
import org.qommons.io.XmlSerialWriter.Element;

/**
 * <p>
 * Parses the CSV export of the EclEmma code coverage feature in Eclipse and prints out an human-readable XML-formatted summary of the code
 * coverage.
 * </p>
 * <p>
 * This is intended to allow for tracking of coverage statistics across versions of a code base.
 * </p>
 */
public class EmmaCoverageSummarizer {
	private static final DecimalFormat COVERAGE_FORMAT = new DecimalFormat("0.0");
	private static final Pattern ANONYMOUS_CLASS = Pattern.compile("\\.new (?<class>.+)\\(\\)\\s+\\{\\.\\.\\.\\}");

	/**
	 * Reads the EclEmma CSV export file (whose path is the first argument) and writes 'coverage-summary.xml' in the same directory
	 * 
	 * @param args Command-line arguments. The first is the path of the CSV export file to summarize, the rest are ignored
	 * @throws IOException If the file cannot be read or the summary cannot be written
	 * @throws TextParseException If the file cannot be parsed
	 */
	public static void main(String... args) throws IOException, TextParseException {
		CoveragePackage root = new CoveragePackage(null);
		File coverageCsv = new File(args[0]);
		try (Reader r = new FileReader(coverageCsv)) {
			CsvParser parser = new CsvParser(r, ',');
			String[] line = parser.parseNextLine();
			int pkgIdx = -1, classIdx = -1, coveredIdx = -1, missedIdx = -1;
			for (int i = 0; i < line.length; i++) {
				line[i] = line[i].toLowerCase();
				if (pkgIdx < 0 && line[i].equals("package"))
					pkgIdx = i;
				else if (classIdx < 0 && line[i].equals("class"))
					classIdx = i;
				else if (coveredIdx < 0 && line[i].startsWith("instruction") && line[i].endsWith("covered"))
					coveredIdx = i;
				else if (missedIdx < 0 && line[i].startsWith("instruction") && line[i].endsWith("missed"))
					missedIdx = i;
			}
			if (pkgIdx < 0)
				throw new IllegalArgumentException("No package column");
			if (classIdx < 0)
				throw new IllegalArgumentException("No class column");
			if (coveredIdx < 0)
				throw new IllegalArgumentException("No instruction covered column");
			if (missedIdx < 0)
				throw new IllegalArgumentException("No intruction missed column");

			while (parser.parseNextLine(line)) {
				String[] pkg = line[pkgIdx].split("\\.");
				String classCol = line[classIdx];
				Matcher anonMatcher = ANONYMOUS_CLASS.matcher(classCol);
				if (anonMatcher.find())
					classCol = classCol.substring(0, anonMatcher.start());
				String[] clazz = classCol.split("\\.");
				int covered = Integer.parseInt(line[coveredIdx]);
				int missed = Integer.parseInt(line[missedIdx]);
				root.add(pkg, clazz, 0, covered, missed);
			}
		}
		try (Writer w = new FileWriter(new File(coverageCsv.getParentFile(), "coverage-summary.xml"))) {
			XmlSerialWriter.createDocument(w).writeRoot("coverage", root::write);
		}
	}

	static abstract class CoverageNode {
		public final String name;
		private int theInstructionsCovered;
		private int theInstructionsTotal;

		private int hasWrittenAttributes;

		CoverageNode(String name) {
			this.name = name;
		}

		int getInstructionsCovered() {
			return theInstructionsCovered;
		}

		void add(int covered, int missed) {
			theInstructionsCovered += covered;
			theInstructionsTotal += covered + missed;
		}

		void writeAttributes(XmlSerialWriter.Element xml) throws IOException {
			hasWrittenAttributes = xml.hashCode();
			if (name != null)
				xml.addAttribute("name", name);
			xml.addAttribute("coverage", COVERAGE_FORMAT.format(theInstructionsCovered * 100.0 / theInstructionsTotal) + "%");
		}

		void write(XmlSerialWriter.Element xml) throws IOException {
			if (hasWrittenAttributes != xml.hashCode())
				writeAttributes(xml);
		}
	}

	static class CoverageClass extends CoverageNode {
		final Map<String, CoverageClass> members;

		public CoverageClass(String name) {
			super(name);
			members = new TreeMap<>(StringUtils.DISTINCT_NUMBER_TOLERANT);
		}

		void add(String[] clazz, int index, int covered, int missed) {
			add(covered, missed);
			if (index < clazz.length)
				members.computeIfAbsent(clazz[index], c -> new CoverageClass(c)).add(clazz, index + 1, covered, missed);
		}

		@Override
		void write(Element xml) throws IOException {
			super.write(xml);
			if (getInstructionsCovered() > 0) {
				for (CoverageClass clazz : members.values()) {
					if (clazz.getInstructionsCovered() > 0)
						xml.addChild("class", clazz::write);
				}
			}
		}
	}

	static class CoveragePackage extends CoverageNode {
		private final CoverageClasses classes;
		private final Map<String, CoveragePackage> packages;

		public CoveragePackage(String name) {
			super(name);
			classes = new CoverageClasses();
			packages = new TreeMap<>(StringUtils.DISTINCT_NUMBER_TOLERANT);
		}

		void add(String[] pkg, String[] clazz, int index, int covered, int missed) {
			add(covered, missed);
			if (index < pkg.length)
				packages.computeIfAbsent(pkg[index], n -> new CoveragePackage(n))//
					.add(pkg, clazz, index + 1, covered, missed);
			else
				classes.add(clazz, 0, covered, missed);
		}

		@Override
		void write(Element xml) throws IOException {
			super.write(xml);
			if (getInstructionsCovered() > 0) {
				if (!classes.members.isEmpty())
					xml.addChild("classes", classes::write);
				for (CoveragePackage pkg : packages.values())
					xml.addChild("package", pkg::write);
			}
		}
	}

	static class CoverageClasses extends CoverageClass {
		CoverageClasses() {
			super("classes");
		}

		@Override
		void write(Element xml) throws IOException {
			super.writeAttributes(xml);
			int missed = 0;
			for (CoverageClass clazz : members.values()) {
				if (clazz.getInstructionsCovered() == 0)
					missed++;
			}
			xml.addAttribute("missed", "" + missed);
			// super.write(xml);
		}
	}
}

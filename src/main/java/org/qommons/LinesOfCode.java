package org.qommons;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.qommons.config.StrictXmlReader;
import org.qommons.io.BetterFile;
import org.qommons.io.FileUtils;
import org.qommons.io.Format;
import org.qommons.io.TextParseException;

/**
 * <p>
 * A fairly simple lines-of-code counter that takes language-specific comments into account.
 * </p>
 * <p>
 * The main method accepts 2 main arguments:
 * <ul>
 * <li>--lang-config=path/to/language-config-file.xml The language XML file allows the specification of languages, each of which may specify
 * file extensions that identify a file as belonging to the language, and comment structures of the language.</li>
 * <li>--project=path/to/project-dir. A project directory to scan for source files.</li>
 * </ul>
 * Both of these arguments may be specified multiple times, or multiple values may be specified for the arguments, separated by commas.
 * </p>
 * <p>
 * Other accepted arguments are:
 * <ul>
 * <li>--as-repo. If this flag is present, the files from the --project argument will not be treated as projects, but as directories holding
 * any number of projects. All directories under each --project argument will be treated as projects.</li>
 * <li>--include=&lt;path> If specified, only directories matching a specified --include path will be scanned for source files.</li>
 * <li>--exclude=&lt;path> Any files or directories ending with a --exclude path will be ignored.</li>
 * </ul>
 * </p>
 * <p>
 * Results are printed for each project and language independently, as well as totals. Metrics include:
 * <ul>
 * <li>Files</li>
 * <li>Content Lines</li>
 * <li>Comment Lines</li>
 * <li>Blank Lines</li>
 * <li>Content Chars</li>
 * <li>Comment Chars</li>
 * <li>White Space Chars</li>
 * </ul>
 * </p>
 */
public class LinesOfCode {
	/**
	 * @param clArgs Command-line arguments
	 * @throws IOException If any project files cannot be read
	 * @see LinesOfCode
	 */
	public static void main(String... clArgs) throws IOException {
		ArgumentParsing.Arguments args = ArgumentParsing.build()//
			.forMultiValuePattern(p -> p//
				.addBetterFileArgument("lang-config", f -> f.mustExist(true).directory(false).anyTimes())//
				.addBetterFileArgument("project", f -> f.mustExist(true).directory(true).times(1, Integer.MAX_VALUE))//
				.addStringArgument("include", a -> a.anyTimes())//
				.addStringArgument("exclude", a -> a.anyTimes())//
			)//
			.forFlagPattern(p -> p//
				.add("as-repo", arg -> arg.withDescription(
					"Whether the files in the 'project' argument should be interpreted as repositories, with each directory under it being a project"))//
			)//
			.build()//
			.parse(clArgs);

		Map<String, LanguageConfig> languages = new HashMap<>();
		for (BetterFile langConfig : args.getAll("lang-config", BetterFile.class))
			parseLangConfig(languages, langConfig);
		if (languages.isEmpty())
			parseLangConfig(languages, FileUtils.getClassFile(LinesOfCode.class).at("../default-loc-languages.xml"));

		Map<LanguageConfig, Map<String, FileStats>> results = new TreeMap<>();

		List<String> includes = args.getAll("include", String.class).stream().map(s -> s.replace("\\", "/")).collect(Collectors.toList());
		List<String> excludes = args.getAll("exclude", String.class).stream().map(s -> s.replace("\\", "/")).collect(Collectors.toList());
		List<? extends BetterFile> projects = args.getAll("project", BetterFile.class);
		for (BetterFile project : projects) {
			if (args.has("as-repo")) {
				for (BetterFile realProject : project.listFiles()) {
					if (realProject.isDirectory()) {
						String projectName;
						if (projects.size() == 1)
							projectName = realProject.getName();
						else
							projectName = project.getName() + "/" + realProject.getName();
						scanProject(realProject, projectName, languages, results, includes, excludes);
					}
				}
			} else {
				scanProject(project, project.getName(), languages, results, includes, excludes);
			}
		}
		if (results.isEmpty()) {
			System.out.println("No recognized files found in any projects");
			return;
		}
		System.out.println("Analysis Completed");
		Set<String> projectNames = results.values().stream().flatMap(m -> m.keySet().stream())
			.collect(Collectors.toCollection(TreeSet::new));

		System.out.println("\nFiles");
		printResults(results, projectNames, stat -> stat.files);
		System.out.println("\nContent Lines");
		printResults(results, projectNames, stat -> stat.contentLines);
		System.out.println("\nComment Lines");
		printResults(results, projectNames, stat -> stat.commentLines);
		System.out.println("\nBlank Lines");
		printResults(results, projectNames, stat -> stat.blankLines);
		System.out.println("\nContent Chars");
		printResults(results, projectNames, stat -> stat.contentChars);
		System.out.println("\nComment Chars");
		printResults(results, projectNames, stat -> stat.commentChars);
	}

	private static void parseLangConfig(Map<String, LanguageConfig> languages, BetterFile langConfig) {
		StrictXmlReader xml;
		try {
			xml = StrictXmlReader.read(langConfig::read);
		} catch (IOException e) {
			System.err.println("Could not read/parse language configuration file " + langConfig);
			e.printStackTrace();
			return;
		}
		try {
			for (StrictXmlReader lang : xml.getElements("language")) {
				List<CommentType> comments = new ArrayList<>();
				for (StrictXmlReader comment : lang.getElements("comment", 1, Integer.MAX_VALUE)) {
					String start = comment.getAttribute("start");
					List<String> ends = new ArrayList<>();
					for (StrictXmlReader end : comment.getElements("end", 1, Integer.MAX_VALUE)) {
						ends.add(end.getCompleteText(false));
						end.check();
					}
					comments.add(new CommentType(start, ends));
					comment.check();
				}
				LanguageConfig config = new LanguageConfig(lang.getAttribute("name"), comments);
				for (String ext : lang.getAttribute("file-extension").toLowerCase().split(","))
					languages.put(ext, config);
			}
			xml.check();
		} catch (TextParseException e) {
			System.err.println("Could not interpret language configuration file " + langConfig);
			e.printStackTrace();
			return;
		}
	}

	private static void scanProject(BetterFile project, String name, Map<String, LanguageConfig> languages,
		Map<LanguageConfig, Map<String, FileStats>> stats, List<String> includes, List<String> excludes) throws IOException {
		System.out.println("Scanning project directory " + project);
		findProjectFiles(project, name, languages, stats, includes, excludes, includes.isEmpty());
	}

	private static void findProjectFiles(BetterFile file, String project, Map<String, LanguageConfig> languages,
		Map<LanguageConfig, Map<String, FileStats>> stats, List<String> includes, List<String> excludes, boolean included)
		throws IOException {
		if (!excludes.isEmpty() || !included) {
			String path = file.getPath();
			for (String exclude : excludes) {
				if (pathEndsWith(path, exclude))
					return; // Excluded
			}
			if (!included) {
				for (String include : includes) {
					if (pathEndsWith(path, include)) {
						included = true;
						break;
					}
				}
			}
		}
		if (file.isDirectory()) {
			for (BetterFile child : file.listFiles())
				findProjectFiles(child, project, languages, stats, includes, excludes, included);
		} else if (included) {
			int lastDot = file.getName().lastIndexOf('.');
			if (lastDot < 0)
				return;
			LanguageConfig language = languages.get(file.getName().substring(lastDot + 1).toLowerCase());
			if (language != null) {
				try (Reader r = new InputStreamReader(file.read())) {
					FileStats langStats = stats//
						.computeIfAbsent(language, __ -> new HashMap<>())//
						.computeIfAbsent(project, __ -> new FileStats());
					language.parse(r, langStats);
				}
			}
		}
	}

	private static boolean pathEndsWith(String path, String test) {
		return path.endsWith(test) //
			&& path.length() > test.length()//
			&& path.charAt(path.length() - test.length() - 1) == '/';
	}

	private static final Format<Integer> INT_FORMAT = Format.INT.withGroupingSeparator(',');

	private static void printResults(Map<LanguageConfig, Map<String, FileStats>> results, Set<String> projects,
		ToIntFunction<FileStats> metric) {
		int maxProjLength = projects.stream().mapToInt(String::length).max().getAsInt();
		System.out.print("Project");
		for (int c = "Project".length(); c < maxProjLength + 1; c++)
			System.out.print(' ');
		for (LanguageConfig lang : results.keySet()) {
			System.out.print(lang.getName());
			// Leave space for 100M lines/chars and a space
			for (int c = lang.getName().length(); c < 12; c++)
				System.out.print(' ');
		}
		if (results.size() > 1)
			System.out.println("Total");
		else
			System.out.println();

		Map<LanguageConfig, FileStats> total = new HashMap<>();
		for (String project : projects) {
			System.out.print(project);
			for (int c = project.length(); c < maxProjLength + 1; c++)
				System.out.print(' ');
			FileStats projectTotal = new FileStats();
			for (Map.Entry<LanguageConfig, Map<String, FileStats>> langStats : results.entrySet()) {
				FileStats projLangStats = langStats.getValue().get(project);
				String result;
				if (projLangStats == null) {
					result = "----";
				} else {
					result = INT_FORMAT.format(metric.applyAsInt(projLangStats));
					total.computeIfAbsent(langStats.getKey(), __ -> new FileStats()).add(projLangStats);
					projectTotal.add(projLangStats);
				}
				System.out.print(result);
				for (int c = result.length(); c < 12; c++)
					System.out.print(' ');
			}
			System.out.println(INT_FORMAT.format(metric.applyAsInt(projectTotal)));
		}
		if (projects.size() > 1) {
			System.out.print("Total");
			for (int c = "Total".length(); c < maxProjLength + 1; c++)
				System.out.print(' ');
			FileStats grandTotal = new FileStats();
			for (FileStats langStat : total.values()) {
				String result = INT_FORMAT.format(metric.applyAsInt(langStat));
				System.out.print(result);
				for (int c = result.length(); c < 12; c++)
					System.out.print(' ');
				grandTotal.add(langStat);
			}
			System.out.println(INT_FORMAT.format(metric.applyAsInt(grandTotal)));
		}
	}

	static class FileStats {
		int files;
		int contentLines;
		int contentChars;
		int blankLines;
		int whiteSpaceChars;
		int commentLines;
		int commentChars;

		public void add(FileStats other) {
			files += other.files;
			contentLines += other.contentLines;
			contentChars += other.contentChars;
			blankLines += other.blankLines;
			whiteSpaceChars += other.whiteSpaceChars;
			commentLines += other.commentLines;
			commentChars += other.commentChars;
		}
	}

	static class LanguageConfig implements Named, Comparable<LanguageConfig> {
		private final String theName;
		private final List<CommentType> theComments;

		LanguageConfig(String name, List<CommentType> comments) {
			theName = name;
			theComments = comments;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public int compareTo(LanguageConfig o) {
			return StringUtils.compareNumberTolerant(theName, o.theName, true, true);
		}

		public FileStats parse(Reader r, FileStats stats) throws IOException {
			CommentAccumulator[] comments = new CommentAccumulator[theComments.size()];
			for (int i = 0; i < comments.length; i++)
				comments[i] = theComments.get(i).accumulate();
			CommentAccumulator currentComment = null;
			stats.files++;
			int c = r.read();
			boolean lineBlank = true;
			while (c >= 0) {
				if (c == '\r') {
					c = r.read();
					continue; // Ignore stupid windows carriage return completely
				}
				else if (c == '\n') {
					if (lineBlank)
						stats.blankLines++;
					else if (currentComment != null)
						stats.commentLines++;
					else
						stats.contentLines++;
					lineBlank = true;
				} else {
					if (Character.isWhitespace((char) c)) {
						stats.whiteSpaceChars++;
					} else {
						lineBlank = false;
						if (currentComment != null)
							stats.commentChars++;
						else
							stats.contentChars++;
					}
				}
				if (currentComment != null) {
					int finished = currentComment.isFinished((char) c);
					if (finished >= 0) {
						currentComment = null;
						// Don't count the non-whitespace characters bounding the comment as anything
						stats.commentChars -= finished;
					}
				} else {
					for (CommentAccumulator comment : comments) {
						if (comment.isStarted((char) c)) {
							for (int i = 0; i < comments.length; i++) {
								if (comments[i] != comment)
									comments[i].reset();
							}
							currentComment = comment;
							break;
						}
					}
				}

				c = r.read();
			}
			return stats;
		}
	}

	interface CommentAccumulator {
		boolean isStarted(char c);

		int isFinished(char c);

		void reset();
	}

	static class CommentType {
		final String start;
		final List<String> end;
		final int startNonWS;
		final int[] endNonWS;

		CommentType(String start, List<String> end) {
			this.start = start;
			this.end = end;
			startNonWS = countNonWS(start);
			endNonWS = new int[end.size()];
			for (int e = 0; e < end.size(); e++)
				endNonWS[e] = countNonWS(end.get(e));
		}

		CommentAccumulator accumulate() {
			return new CommentAccumulator() {
				private int theOffset;
				private int[] theEndOffsets = new int[end.size()];

				@Override
				public boolean isStarted(char c) {
					if (c == start.charAt(theOffset)) {
						theOffset++;
						if (theOffset == start.length()) { // Started
							theOffset = 0;
							return true;
						}
					} else
						theOffset = 0;
					return false;
				}

				@Override
				public int isFinished(char c) {
					for (int e = 0; e < theEndOffsets.length; e++) {
						if (c == end.get(e).charAt(theEndOffsets[e])) {
							theEndOffsets[e]++;
							if (theEndOffsets[e] == end.get(e).length()) {
								reset();
								return startNonWS + endNonWS[e];
							}
						} else
							theEndOffsets[e] = 0;
					}
					return -1;
				}

				@Override
				public void reset() {
					theOffset = 0;
					Arrays.fill(theEndOffsets, 0);
				}
			};
		}

		static int countNonWS(String string) {
			int count = 0;
			for (int i = 0; i < string.length(); i++) {
				if (!Character.isWhitespace(string.charAt(i)))
					count++;
			}
			return count;
		}
	}
}

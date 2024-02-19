package org.qommons;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * The main method accepts 2 arguments:
 * <ul>
 * <li>--lang-config=path/to/language-config-file.xml The language XML file allows the specification of languages, each of which may specify
 * file extensions that identify a file as belonging to the language, and comment structures of the language.</li>
 * <li>--project=path/to/project-dir. A project directory to scan for source files.</li>
 * </ul>
 * Both of these arguments may be specified multiple times, or multiple values may be specified for the arguments, separated by commas.
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
			)//
			.build()//
			.parse(clArgs);

		Map<String, LanguageConfig> languages = new HashMap<>();
		for (BetterFile langConfig : args.getAll("lang-config", BetterFile.class))
			parseLangConfig(languages, langConfig);
		if (languages.isEmpty())
			parseLangConfig(languages, FileUtils.getClassFile(LinesOfCode.class).at("../default-loc-languages.xml"));

		Map<LanguageConfig, FileStats> total = new HashMap<>();

		int projCount = 0;
		for (BetterFile project : args.getAll("project", BetterFile.class)) {
			Map<LanguageConfig, FileStats> projectStats = new HashMap<>();
			findProjectFiles(project, languages, projectStats);

			reportStats(project + ":", projectStats);
			for (Map.Entry<LanguageConfig, FileStats> stat : projectStats.entrySet())
				total.computeIfAbsent(stat.getKey(), __ -> new FileStats()).add(stat.getValue());
			projCount++;
		}
		if (projCount > 0)
			reportStats("All Projects:", total);
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

	private static void findProjectFiles(BetterFile file, Map<String, LanguageConfig> languages, Map<LanguageConfig, FileStats> stats)
		throws IOException {
		if (file.isDirectory()) {
			for (BetterFile child : file.listFiles())
				findProjectFiles(child, languages, stats);
		} else {
			int lastDot = file.getName().lastIndexOf('.');
			if (lastDot < 0)
				return;
			LanguageConfig language = languages.get(file.getName().substring(lastDot + 1).toLowerCase());
			if (language != null) {
				try (Reader r = new InputStreamReader(file.read())) {
					FileStats langStats = stats.computeIfAbsent(language, __ -> new FileStats());
					language.parse(r, langStats);
				}
			}
		}
	}

	private static void reportStats(String label, Map<LanguageConfig, FileStats> stats) {
		System.out.println(label);
		FileStats total = new FileStats();
		for (Map.Entry<LanguageConfig, FileStats> lang : stats.entrySet()) {
			System.out.println("\t" + lang.getKey().getName() + ":");
			reportStats(lang.getValue());
			total.add(lang.getValue());
		}
		if (stats.size() > 1) {
			System.out.println("\tAll Languages:");
			reportStats(total);
		}
	}

	private static final Format<Integer> INT_FORMAT = Format.INT.withGroupingSeparator(',');

	private static void reportStats(FileStats stats) {
		System.out.println("\t\t            Files: " + INT_FORMAT.format(stats.files));
		System.out.println("\t\t    Content Lines: " + INT_FORMAT.format(stats.contentLines));
		System.out.println("\t\t    Comment Lines: " + INT_FORMAT.format(stats.commentLines));
		System.out.println("\t\t      Blank Lines: " + INT_FORMAT.format(stats.blankLines));
		System.out.println("\t\t    Content Chars: " + INT_FORMAT.format(stats.contentChars));
		System.out.println("\t\t    Comment Chars: " + INT_FORMAT.format(stats.commentChars));
		System.out.println("\t\tWhite Space Chars: " + INT_FORMAT.format(stats.whiteSpaceChars));
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

	static class LanguageConfig implements Named {
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

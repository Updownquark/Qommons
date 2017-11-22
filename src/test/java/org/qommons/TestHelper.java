package org.qommons;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.qommons.ArgumentParsing.ArgumentParser;
import org.qommons.ArgumentParsing.Arguments;
import org.qommons.io.Format;

/**
 * <p>
 * This class may be used by test classes to generate pseudo-random numbers that would be used to generate pseudo-random test cases. These
 * test class would attempt to use the randomness to cover every possible permutation of code invocation in order to test a system
 * more-or-less completely.
 * </p>
 *
 * <p>
 * For example, to test a stateful class with 3 methods, a(int arg), b(boolean arg), and c(double arg), the class could do something like
 *
 * <pre>
 * (TestHelper helper) -> {
 * 	ToTest test = new ToTest();
 * 	int tries = 5000;
 * 	for (int i = 0; i &lt; tries; i++) {
 * 		switch (helper.getInt(0, 3)) {
 * 		case 0:
 * 			int intArg = helper.getInt(-1, 1000000); // Constrain to the expected argument range of method a()
 * 			assertOutputOfACorrect(intArg, a(intArg));
 * 			break;
 * 		case 1:
 * 			boolean boolArg = helper.getBoolean();
 * 			assertOutputOfBCorrect(boolArg, b(boolArg));
 * 			break;
 * 		case 2:
 * 			double doubleArg = helper.getDouble();
 * 			assertOutputOfCCorrect(doubleArg, C(doubleArg));
 * 			break;
 * 		}
 * 	}
 * }
 * </pre>
 *
 * This would generate 5000 random states in the tested class for each test case.
 * </p>
 * <p>
 * Far more than just a utility wrapper around {@link Random}, this class may be used to generate unlimited test cases that can be run
 * ad-infinitum. It keeps track of the history of each test case. It has several modes:
 * <ul>
 * <li><b>--random</b> In this mode, random test cases are generated and failures are logged for later reproduction. Options::
 * <ul>
 * <li><b>--max-cases=&lt;integer&gt;</b> The maximum number of test cases to run.</li>
 * <li><b>--max-time=&lt;duration&gt;</b> The maximum amount of time to run.</li>
 * <li><b>--max-failures=&lt;integer&gt;</b> The maximum number of failures to encounter.</li>
 * <li><b>--only-new=&lt;boolean&gt;</b> If true, this class will not re-attempt test cases that were last known to be broken. Default is
 * true.</li>
 * </ul>
 * </li>
 * <li><b>--reproduce</b> In this mode, tests that have broken recently are re-run. Options:
 * <ul>
 * <li><b>--debug</b> If this flag is set, the helper will invoke a break point (via {@link org.qommons.BreakpointHere}) just before the
 * most recent point of failure for each test.</li>
 * <li><b>--hash=&lt;debug-hash&gt;</b> The hash or comma-separated hashes to re-execute. If unspecified, all test cases that were last
 * known to be broken will be run.</li>
 * <li><b>--debug-at=&lt;long integer&gt;</b> The number of bytes in the test case at which to catch a breakpoint. Must be used with -hash
 * for a single hash.</li>
 * </ul>
 * </li>
 * </ul>
 * All modes have the following options:
 * <ul>
 * <li><b>--test-class=&lt;qualified.className&gt;</b> The {@link Testable} class to test. Must have a no-argument constructor.</li>
 * <li><b>--hold-time=&lt;duration&gt;</b> The maximum time to wait between invocations to this helper (if unspecified by execution) before
 * failure is assumed.</li>
 * </ul>
 * </p>
 * <p>
 * The console output of this class can be used to monitor the progress of the test. When each test case is started, the hash for that test
 * is printed immediately. When the test case finishes successfully, SUCCESS is printed, followed by the percent success for the current
 * testing session. Upon failure, FAILURE or TIMEOUT is printed, followed by "@" and the number of bytes generated by the helper for the
 * test case before its failure. This may be used (in conjunction with the -hash argument) in -reproduce mode with the -debug-at argument to
 * invoke a break point just prior to the failure. Then the stack trace of the exception or of the execution upon timeout is printed.
 * </p>
 */
public class TestHelper {
	public static interface Testable extends Consumer<TestHelper> {}

	public static class TestFailure {
		public final long seed;
		public final long bytes;
		public final long placemark;

		public TestFailure(long seed, long bytes, long placemark) {
			this.seed = seed;
			this.bytes = bytes;
			this.placemark = placemark;
		}

		public long getPlacemarkOrBytes() {
			return placemark >= 0 ? placemark : bytes;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof TestFailure))
				return false;
			TestFailure other = (TestFailure) obj;
			return seed == other.seed && bytes == other.bytes && placemark == other.placemark;
		}

		@Override
		public String toString() {
			return Long.toHexString(seed) + "@" + bytes + (placemark >= 0 ? "(placemark " + placemark + ")" : "");
		}
	}

	private final long theSeed;
	private final Random theRandomness;
	private long theBytes;
	private long theLastPlacemark;
	private long theBreak;

	private TestHelper() {
		this(Double.doubleToLongBits(Math.random()), 0, -1);
	}

	private TestHelper(long seed, long bytes, long breakPoint) {
		theSeed=seed;
		theRandomness=new Random(seed);
		while (theBytes < bytes - 7)
			getAnyLong();
		while (theBytes < bytes)
			getBoolean();
		theLastPlacemark = -1;
		theBreak = breakPoint < 0 ? Long.MAX_VALUE : breakPoint;
	}

	private void getBytes(int bytes) {
		boolean postBreak = theBytes >= theBreak;
		theBytes += bytes;
		if (!postBreak && theBytes >= theBreak)
			BreakpointHere.breakpoint();
	}

	public long getSeed() {
		return theSeed;
	}

	public long getPosition() {
		return theBytes;
	}

	public long getLastPlacemark() {
		return theLastPlacemark;
	}

	public TestHelper fork() {
		long forkSeed = getAnyLong();
		return new TestHelper(forkSeed, 0, -1);
	}

	public void placemark() {
		getBoolean();
		theLastPlacemark = theBytes;
	}

	public TestHelper tolerate(Duration timeout) {
		return this;
	}

	public int getAnyInt() {
		getBytes(4);
		return theRandomness.nextInt();
	}

	public int getInt(int min, int max) {
		if (min == max)
			return min;
		return min + Math.abs(getAnyInt() % (max - min));
	}

	public long getAnyLong() {
		getBytes(8);
		return theRandomness.nextLong();
	}

	public long getLong(long min, long max) {
		return min + Math.abs(getAnyLong() % (max - min));
	}

	public double getDouble() {
		getBytes(8);
		return theRandomness.nextDouble();
	}

	public double getGaussian() {
		getBytes(8);
		return theRandomness.nextGaussian();
	}

	public double getAnyDouble() {
		return Double.longBitsToDouble(getAnyLong());
	}

	public double getDouble(double min, double max) {
		return min + getDouble() * (max - min);
	}

	public boolean getBoolean() {
		getBytes(1);
		byte[] bytes = new byte[1];
		theRandomness.nextBytes(bytes);
		return bytes[0] >= 0;
	}

	public boolean getBoolean(double odds) {
		return getDouble() < odds;
	}

	public static class Testing {
		private final Class<? extends Testable> theTestable;
		private final Constructor<? extends Testable> theCreator;
		private boolean isRevisitingKnownFailures = true;
		private List<TestFailure> theSpecifiedCases = new ArrayList<>();
		private boolean isDebugging = false;
		private int theMaxRandomCases = 0;
		private int theMaxFailures = 1;
		private Duration theMaxTotalDuration;
		private Duration theMaxCaseDuration;
		private boolean isPrintingProgress = true;
		private boolean isPrintingFailures = true;
		private boolean isThrowingOnFailure = true;
		private boolean isPersistingFailures = true;
		private File theFailureDir = null;
		private boolean isFailureFileQualified = true;

		private Testing(Class<? extends Testable> testable) {
			theTestable = testable;
			theCreator = getCreator(testable);
		}

		public Testing revisitKnownFailures(boolean b) {
			isRevisitingKnownFailures = b;
			return this;
		}

		public Testing withDebug(boolean debug) {
			isDebugging = debug;
			return this;
		}

		public Testing withCase(long hash, long breakPoint) {
			theSpecifiedCases.add(new TestFailure(hash, 0, breakPoint));
			return this;
		}

		public Testing withRandomCases(int cases) {
			theMaxRandomCases = cases;
			return this;
		}

		public Testing withMaxFailures(int failures) {
			theMaxFailures = failures;
			return this;
		}

		public Testing withMaxTotalDuration(Duration duration) {
			theMaxTotalDuration = duration;
			return this;
		}

		public Testing withMaxCaseDuration(Duration duration) {
			theMaxCaseDuration = duration;
			return this;
		}

		public Testing withPrinting(boolean onProgress, boolean onFailure) {
			isPrintingProgress = onProgress;
			isPrintingFailures = onFailure;
			return this;
		}

		public Testing throwOnFailure(boolean doThrow) {
			isThrowingOnFailure = doThrow;
			return this;
		}

		public Testing withFailurePersistence(boolean persist) {
			isPersistingFailures = persist;
			return this;
		}

		public Testing withPersistenceDir(File dir, boolean qualifiedName) {
			if (dir != null)
				isPersistingFailures = true;
			theFailureDir = dir;
			isFailureFileQualified = qualifiedName;
			return this;
		}

		public int execute() throws AssertionError {
			int maxCases = theMaxRandomCases;
			int maxFailures = theMaxFailures;
			if (maxCases < 0)
				maxCases = Integer.MAX_VALUE;
			if (maxFailures <= 0)
				maxFailures = Integer.MAX_VALUE;
			Instant termination = theMaxTotalDuration == null ? Instant.MAX : Instant.now().plus(theMaxTotalDuration);
			int failures = 0;
			Throwable firstError = null;
			List<TestFailure> knownFailures;
			if (isRevisitingKnownFailures || isPersistingFailures)
				knownFailures = getKnownFailures(theFailureDir, theTestable, isFailureFileQualified);
			else
				knownFailures = null;
			if (isRevisitingKnownFailures) {
				if (!knownFailures.isEmpty()) {
					for (int i = 0; i < knownFailures.size() && failures < maxFailures && Instant.now().compareTo(termination) < 0; i++) {
						TestFailure failure = knownFailures.get(i);
						TestHelper helper = new TestHelper(failure.seed, 0, isDebugging ? failure.getPlacemarkOrBytes() : -1);
						Throwable err = doTest(theCreator, helper, i + 1, isPrintingProgress, isPrintingFailures, true);
						if (err != null) {
							TestFailure newFailure = new TestFailure(helper.getSeed(), helper.getPosition(), helper.getLastPlacemark());
							if (!newFailure.equals(failure)) {
								knownFailures.set(i, newFailure);
								writeTestFailures(theFailureDir, theTestable, isFailureFileQualified, knownFailures);
							}
							if (firstError == null)
								firstError = err;
							failures++;
						} else {
							System.out.println("Test failure fixed");
							knownFailures.remove(i);
							i--;
							writeTestFailures(theFailureDir, theTestable, isFailureFileQualified, knownFailures);
						}
					}
				}
			}
			for (int i = 0; i < maxCases && failures < maxFailures && Instant.now().compareTo(termination) < 0; i++) {
				TestHelper helper = new TestHelper();
				Throwable err = doTest(theCreator, helper, i + 1, isPrintingProgress, isPrintingFailures, false);
				if (err != null) {
					if (isPersistingFailures) {
						TestFailure failure = new TestFailure(helper.getSeed(), helper.getPosition(), helper.getLastPlacemark());
						knownFailures.add(failure);
						writeTestFailures(theFailureDir, theTestable, isFailureFileQualified, knownFailures);
					}
					if (firstError == null)
						firstError = err;
					failures++;
				}
			}
			if (isThrowingOnFailure && failures > 0) {
				if (firstError instanceof AssertionError)
					throw (AssertionError) firstError;
				throw new AssertionError(firstError.getMessage(), firstError);
			}
			return failures;
		}
	}

	public static Testing createTester(Class<? extends Testable> testable) {
		return new Testing(testable);
	}

	public static void main(String[] args) {
		ArgumentParser parser = new ArgumentParser()//
			.forDefaultFlagPattern()//
			/**/.flagArg("random").requiredIfNot("reproduce").requiresNot("reproduce")//
			/**/.flagArg("reproduce").requiredIfNot("random").requiresNot("random")//
			/**/.flagArg("debug").requires("reproduce")//
			/**/
			.forDefaultPattern()//
			/**/.stringArg("test-class").required()//
			/**/.durationArg("hold-time").defValue(Duration.ofSeconds(60))//
			/**/.intArg("max-cases").requires("random").atLeast(1)//
			/**/.durationArg("max-time").requires("random")//
			/**/.intArg("max-failures").requires("random").atLeast(1)//
			/**/.booleanArg("only-new").requires("random").defValue(true)//
			/**/.intArg("debug-at").requires("random").atLeast(0)//
			.forDefaultMultiValuePattern()//
			/**/.patternArg("hash", Pattern.compile("[0-9a-fA-F]{16}"))//
			.getParser();
		Arguments parsed = parser.parse(args);
		Class<? extends Testable> testable;
		try {
			testable = Class.forName(parsed.getString("test-class")).asSubclass(Testable.class);
		} catch (ClassNotFoundException e) {
			System.err.println("Test class " + parsed.getString("test-class") + " not found");
			e.printStackTrace();
			return;
		} catch (ClassCastException e) {
			System.err.println("Test class " + parsed.getString("test-class") + " is not " + Testable.class.getName());
			return;
		}
		Constructor<? extends Testable> creator = getCreator(testable);

		/* TODO Append broken test cases to a file. Remove fixed test cases.
		 * Attempt to co-locate this file with the class being tested.
		 * If co-located, the file should be the simple name of the class.broken.
		 * Otherwise make a sub-dir called testhelper under the current working dir and
		 * put the file in there, named fully.qualified.ClassName.broken. */
		if (parsed.hasFlag("random")) {
			testRandom(testable, false, //
				(int) parsed.getInt("max-cases", Integer.MAX_VALUE), //
				(int) parsed.getInt("max-failures", Integer.MAX_VALUE), //
				parsed.getDuration("max-time", null), true, true, false, false);
		} else {
			long debugAt = parsed.get("debug-at") != null ? parsed.getLong("debug-at") : -1;
			int i = 0;
			for (String hash : parsed.getAll("hash", String.class))
				doTest(creator, new TestHelper(Long.parseLong(hash, 16), 0, debugAt), ++i, true, true, true);
		}
	}

	private static List<TestFailure> getKnownFailures(File failureDir, Class<? extends Testable> testable, boolean qualifiedName) {
		File testFile = getFailureFile(failureDir, testable, qualifiedName, false);
		if (testFile == null || !testFile.exists())
			return new ArrayList<>();

		try (BufferedReader reader = new BufferedReader(new FileReader(testFile))) {
			List<TestFailure> failures = new ArrayList<>();
			String line = reader.readLine();
			if (line != null) // Header row
				line = reader.readLine();
			while (line != null) {
				String[] split = line.split(",");
				if (split.length == 3)
					failures.add(new TestFailure(Long.parseLong(split[0], 16), Long.parseLong(split[1]),
						split[2].length() == 0 ? -1 : Long.parseLong(split[2])));
				else
					throw new IllegalStateException("Need 3 columns");
				line = reader.readLine();
			}
			return failures;
		} catch (IOException e) {
			e.printStackTrace();
			return new ArrayList<>();
		}
	}

	private static File getFailureFile(File failureDir, Class<? extends Testable> testable, boolean qualifiedName, boolean create) {
		File testFile = null;
		if (failureDir != null)
			return new File(failureDir, (qualifiedName ? testable.getName() : testable.getSimpleName()) + ".broken");

		if (failureDir == null) {
			// Attempt to co-locate the failure file with the class file
			String classFileName = testable.getName();
			classFileName = classFileName.replaceAll("\\.", "/") + ".class";
			URL classLoc = testable.getClassLoader().getResource(classFileName);
			if (classLoc != null && classLoc.getProtocol().equals("file")) {
				File classFile = new File(classLoc.getPath());
				testFile = new File(classFile.getParent(), testable.getSimpleName() + ".broken");
				if (testFile.exists())
					return testFile;
				try {
					if (testFile.createNewFile()) {
						if (!create)
							testFile.delete();
						return testFile;
					}
				} catch (IOException e) {
					System.err.println(
						"Could not create failure file " + testFile.getAbsolutePath() + " for class " + testable.getName() + ": " + e);
				}
			}
		}
		if (testFile == null) {
			File currentDir = new File(System.getProperty("user.dir"));
			testFile = new File(currentDir, testable.getName() + ".broken");
			if (testFile.exists())
				return testFile;
			try {
				if (testFile.createNewFile()) {
					if (!create)
						testFile.delete();
					return testFile;
				}
			} catch (IOException e) {
				System.err
					.println("Could not create failure file " + testFile.getAbsolutePath() + " for class " + testable.getName() + ": " + e);
			}
		}
		return testFile;
	}

	private static void writeTestFailures(File failureDir, Class<? extends Testable> testable, boolean qualifiedName,
		List<TestFailure> failures) {
		File testFile = getFailureFile(failureDir, testable, qualifiedName, true);
		if (testFile == null)
			return;
		try (BufferedWriter writer = new BufferedWriter(new java.io.FileWriter(testFile))) {
			writer.write("Seed,Position,Placemark\n");
			for (TestFailure failure : failures) {
				String csvLine = Long.toHexString(failure.seed) + "," + failure.bytes + ","
					+ (failure.placemark < 0 ? "" : "" + failure.placemark);
				writer.write((csvLine + "\n").toCharArray());
			}
		} catch (IOException e) {
			System.err.println("Could not write to failure file " + testFile.getAbsolutePath() + " for class " + testable.getName());
			e.printStackTrace();
		}
	}

	private static <T extends Testable> Constructor<T> getCreator(Class<T> testable) {
		Constructor<T> creator;
		try {
			try {
				creator = testable.getConstructor();
			} catch (NoSuchMethodException e) {
				creator = testable.getDeclaredConstructor();
			}
		} catch (NoSuchMethodException | SecurityException e) {
			throw new IllegalStateException("Test class " + testable.getName() + " does not have a no-argument constructor", e);
		}
		if (!creator.isAccessible()) {
			try {
				creator.setAccessible(true);
			} catch (SecurityException e) {
				throw new IllegalStateException(
					"No-argument constructor for test class " + testable.getName() + " is not accessible (try making it public)", e);
			}
		}
		return creator;
	}

	private static Throwable doTest(Constructor<? extends Testable> creator, TestHelper testHelper, int caseNumber,
		boolean printProgress, boolean printFailures, boolean reproduction) {
		Testable tester;
		try {
			tester = creator.newInstance();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new IllegalStateException("Could not instantiate tester " + creator.getDeclaringClass().getName(), e);
		}
		return doTest(tester, testHelper, caseNumber, printProgress, printFailures, reproduction);
	}

	private static Throwable doTest(Testable tester, TestHelper testHelper, int caseNumber, boolean printProgress, boolean printFailures,
		boolean reproduction) {
		String caseLabel = "";
		if (caseNumber > 0)
			caseLabel += "[" + caseNumber + "] ";
		caseLabel += Long.toHexString(testHelper.getSeed()).toUpperCase() + ": ";
		if (printProgress) {
			if (reproduction)
				System.out.print("Reproducing ");
			System.out.print(caseLabel);
			System.out.flush();
		}
		long start = System.nanoTime();
		try {
			tester.accept(testHelper);
			if (printProgress)
				System.out.println("SUCCESS in " + Format.durationFormat().format(Duration.ofNanos(System.nanoTime() - start)));
			return null;
		} catch (Throwable e) {
			if (printProgress || printFailures) {
				if (!printProgress)
					System.err.print(caseLabel);
				StringBuilder msg = new StringBuilder();
				msg.append("FAILURE@").append(testHelper.getPosition()).append(" in ");
				Format.durationFormat().append(msg, Duration.ofNanos(System.nanoTime() - start));
				long placemark = testHelper.getLastPlacemark();
				if (placemark >= 0)
					msg.append("\nPlacemark@").append(placemark);
				System.err.println(msg);
				e.printStackTrace();
			}
			return e;
		}
	}
}

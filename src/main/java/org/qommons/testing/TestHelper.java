package org.qommons.testing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.qommons.ArgumentParsing;
import org.qommons.BreakpointHere;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.TimeUtils;
import org.qommons.TimeUtils.RelativeInstantEvaluation;
import org.qommons.collect.BetterList;
import org.qommons.collect.ListenerList;
import org.qommons.collect.ListenerList.Element;
import org.qommons.debug.Debug;
import org.qommons.io.CsvParser;
import org.qommons.io.Format;
import org.qommons.io.TextParseException;
import org.qommons.tree.BetterTreeList;

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
public class TestHelper extends TestUtil {
	/** A test case to execute against a {@link TestHelper} */
	public static interface Testable extends Consumer<TestHelper> {}

	/** Represents a test failure */
	public static class TestFailure implements Comparable<TestFailure> {
		/** The date that the test first encountered this failed test case */
		public final Instant failed;
		/** The date that the test case was fixed (or null if it is still broken) */
		public Instant fixed;
		/** The seed of the test case that failed */
		public final long seed;
		/** The random generation sequence position of the failure */
		public final long bytes;
		/** The placemark positions at the failure */
		public final NavigableMap<String, Long> placemarks;

		/**
		 * @param failed The date that the test first encountered this failed test case
		 * @param fixed The date that the test case was fixed (or null if it is still broken)
		 * @param seed The seed of the test case that failed
		 * @param bytes The random generation sequence position of the failure
		 * @param placemarks The random generation sequence position of the failure
		 */
		public TestFailure(Instant failed, Instant fixed, long seed, long bytes, NavigableMap<String, Long> placemarks) {
			this.failed = failed;
			this.fixed = fixed;
			this.seed = seed;
			this.bytes = bytes;
			TreeMap<String, Long> ps = new TreeMap<>();
			placemarks.entrySet().stream().filter(e -> e.getValue() != null && e.getValue() >= 0)
				.forEach(e -> ps.put(e.getKey(), e.getValue()));
			this.placemarks = Collections.unmodifiableNavigableMap(ps);
		}

		/** @return the placemark positions at the failure */
		public NavigableSet<Long> getBreakpoints() {
			if (placemarks.isEmpty() && bytes > 0)
				return new TreeSet<>(Arrays.asList(bytes));
			else {
				NavigableSet<Long> breakpoints = new TreeSet<>(placemarks.values());
				if (bytes > 0)
					breakpoints.add(bytes);
				return breakpoints;
			}
		}

		@Override
		public int compareTo(TestFailure o) {
			return Long.compare(bytes, o.bytes);
		}

		@Override
		public int hashCode() {
			return Objects.hash(seed, bytes, placemarks);
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof TestFailure))
				return false;
			TestFailure other = (TestFailure) obj;
			return seed == other.seed && bytes == other.bytes && placemarks.equals(other.placemarks);
		}

		@Override
		public String toString() {
			StringBuilder s = new StringBuilder();
			s.append(Long.toHexString(seed)).append('@').append(bytes);
			for (Map.Entry<String, Long> entry : placemarks.entrySet())
				s.append(' ').append(entry.getKey()).append(": ").append(entry.getValue());
			return s.toString();
		}
	}

	private final boolean isReproducing;
	private final NavigableSet<String> thePlacemarkNames;
	private final NavigableMap<String, Long> thePlacemarks;
	private final NavigableSet<Long> theBreakpoints;
	private long theNextBreak;
	private final boolean isCheckingIn;
	private volatile Instant theLastCheckIn;

	private TestHelper(Set<String> placemarkNames) {
		this(false, false, Double.doubleToLongBits(Math.random()), 0, Collections.emptyNavigableSet(), placemarkNames);
	}

	private TestHelper(boolean reproducing, boolean checkIn, long seed, long bytes, NavigableSet<Long> breakPoints,
		Set<String> placemarkNames) {
		super(seed, bytes);
		isReproducing = reproducing;
		TreeSet<String> pns = new TreeSet<>(placemarkNames);
		pns.add("Placemark");
		thePlacemarkNames = Collections.unmodifiableNavigableSet(pns);
		thePlacemarks = new TreeMap<>();
		theBreakpoints = Collections.unmodifiableNavigableSet(new TreeSet<>(breakPoints));
		theNextBreak = theBreakpoints.isEmpty() ? Long.MAX_VALUE : theBreakpoints.first();
		isCheckingIn = checkIn;
	}

	@Override
	protected void advanced(long oldPosition, long newPosition) {
		if (newPosition >= theNextBreak) {
			Debug.d().debug(this, true).setField("break", true);
			BreakpointHere.breakpoint();
			Long next = theBreakpoints.higher(theNextBreak);
			theNextBreak = next == null ? Long.MAX_VALUE : next;
		}
	}

	/** @return Whether the current test case is a reproduction of a previous failure */
	public boolean isReproducing() {
		return isReproducing;
	}

	/** @return The set of placemark names recognized by this test case */
	public NavigableSet<String> getPlacemarkNames() {
		return thePlacemarkNames;
	}

	/**
	 * @param name The name of the placemark
	 * @return The position of the last call to the given placemark
	 */
	public long getLastPlacemark(String name) {
		Long placemark = thePlacemarks.get(name);
		return placemark == null ? -1 : placemark.longValue();
	}

	/** @return Whether this debugging session has previously hit a breakpoint */
	public boolean hasHitBreak() {
		return !theBreakpoints.isEmpty() && getPosition() >= theBreakpoints.iterator().next();
	}

	/** @return A new {@link TestHelper} whose sequence is determined by this helper's seed and its position */
	public TestHelper fork() {
		long forkSeed = getAnyLong();
		return new TestHelper(isReproducing, isCheckingIn, forkSeed, 0, theBreakpoints, thePlacemarkNames);
	}

	/** Sets a generic placemark */
	public void placemark() {
		placemark("Placemark");
	}

	/**
	 * Sets a named placemark (must be configured with {@link TestConfig#withPlacemarks(String...)})
	 * 
	 * @param name The name for the placemark
	 */
	public void placemark(String name) {
		if (!thePlacemarkNames.contains(name))
			throw new IllegalArgumentException("Unrecognized placemark name: " + name);
		if (isCheckingIn)
			theLastCheckIn = Instant.now();
		getBoolean();
		thePlacemarks.put(name, getPosition());
	}

	/** @return If configured, the last time this helper was used */
	public Instant getLastCheckIn() {
		return theLastCheckIn;
	}


	/** @return A RandomAction that can be configured to execute one of a number of weighted-probability tasks */
	public RandomAction createAction() {
		return new RandomAction(this);
	}

	/**
	 * Equivalent to {@link #createAction()}.{@link RandomAction#or(double, Runnable) or(relativeProbability, action)}
	 * 
	 * @param relativeProbability The probability (relative to the sum of all probabilities in the {@link RandomAction}) that the given
	 *        action will be performed
	 * @param action The action to perform
	 * @return The {@link RandomAction} to add other tasks to
	 */
	public RandomAction doAction(double relativeProbability, Runnable action) {
		return createAction().or(relativeProbability, action);
	}

	/**
	 * @param <T> The type of value to supply
	 * @return A RandomSupplier that can be configured to execute one of a number of weighted-probability suppliers
	 */
	public <T> RandomSupplier<T> createSupplier() {
		return new RandomSupplier<>(this);
	}

	/**
	 * Equivalent to {@link #createSupplier()}.{@link RandomSupplier#or(double, Supplier) or(relativeProbability, action)}
	 * 
	 * @param <T> The type of value to supply
	 * @param relativeProbability The probability (relative to the sum of all probabilities in the {@link RandomSupplier}) that the given
	 *        action will be performed
	 * @param action The action to perform
	 * @return The {@link RandomSupplier} to add other tasks to
	 */
	public <T> RandomSupplier<T> supply(double relativeProbability, Supplier<? extends T> action) {
		return this.<T> createSupplier().or(relativeProbability, action);
	}

	static abstract class RandomExecutor<T, A> {
		private final TestHelper theHelper;
		private final TreeMap<Double, A> theActions = new TreeMap<>();
		private double theTotalProbability;

		private RandomExecutor(TestHelper helper) {
			theHelper = helper;
		}

		public TestHelper getHelper() {
			return theHelper;
		}

		protected RandomExecutor<T, A> or(double relativeProbability, A action) {
			if (relativeProbability < 0 || Double.isNaN(relativeProbability) || Double.isInfinite(relativeProbability))
				throw new IllegalArgumentException("Illegal probability: " + relativeProbability);
			else if (relativeProbability != 0) {
				theActions.put(theTotalProbability, action);
				theTotalProbability += relativeProbability;
			}
			return this;
		}

		protected A getAction(String placemark) {
			double random = theHelper.getDouble();
			random *= theTotalProbability;
			A action = theActions.floorEntry(random).getValue();
			if (placemark != null)
				theHelper.placemark(placemark);
			return action;
		}
	}

	/**
	 * A supplier that calls one of its probability-weighted delegates
	 * 
	 * @param <T> The type of value supplied
	 */
	public static class RandomSupplier<T> extends RandomExecutor<T, Supplier<? extends T>> {
		private RandomSupplier(TestHelper helper) {
			super(helper);
		}

		@Override
		public RandomSupplier<T> or(double relativeProbability, Supplier<? extends T> action) {
			super.or(relativeProbability, action);
			return this;
		}

		/**
		 * @param placemark The placemark to set before calling the supplier
		 * @return The supplied value
		 */
		public T get(String placemark) {
			return getAction(placemark)//
				.get();
		}
	}

	/** An action that calls one of its probability-weighted delegates */
	public static class RandomAction extends RandomExecutor<Void, Runnable> {
		private RandomAction(TestHelper helper) {
			super(helper);
		}

		@Override
		public RandomAction or(double relativeProbability, Runnable action) {
			super.or(relativeProbability, action);
			return this;
		}

		/** @param placemark The placemark to set before calling the supplier */
		public void execute(String placemark) {
			getAction(placemark)//
				.run();
		}
	}

	/** Configures a {@link TestHelper}-backed test case */
	public static class TestConfig {
		private final Class<? extends Testable> theTestable;
		private final Constructor<? extends Testable> theCreator;
		private boolean isRevisitingKnownFailures = true;
		private List<TestFailure> theSpecifiedCases = new ArrayList<>();
		private final NavigableSet<String> thePlacemarkNames;
		private boolean isDebugging = false;
		private int theMaxRandomCases = 0;
		private int theMaxFailures = 1;
		private int theMaxRememberedFixes = 5;
		private Duration theMaxTotalDuration;
		private Duration theMaxCaseDuration;
		private Duration theMaxProgressInterval;
		private boolean isPrintingProgress = true;
		private boolean isPrintingFailures = true;
		private boolean isPersistingFailures = true;
		private File theFailureDir = null;
		private boolean isFailureFileQualified = true;
		private int theConcurrency;

		private TestConfig(Class<? extends Testable> testable) {
			theTestable = testable;
			theCreator = getCreator(testable);
			thePlacemarkNames = new TreeSet<>();
			theConcurrency = 1;
		}

		/**
		 * @param b Whether the tester should look for a known failure file and, if found, re-try the test cases contained. Default is
		 *        false.
		 * @return This configuration
		 */
		public TestConfig revisitKnownFailures(boolean b) {
			isRevisitingKnownFailures = b;
			return this;
		}

		/**
		 * @param debug Whether, when {@link #revisitKnownFailures(boolean) revisiting} previous failures, this class should attempt to
		 *        catch a {@link BreakpointHere#breakpoint() breakpoint} just before the failure. Default is false.
		 * @return This configuration
		 */
		public TestConfig withDebug(boolean debug) {
			isDebugging = debug;
			return this;
		}

		/**
		 * Specifies a specific test case in code
		 * 
		 * @param hash The test case seed
		 * @param placemarks The set of placemarks
		 * @return This configuration
		 */
		public TestConfig withCase(long hash, NavigableMap<String, Long> placemarks) {
			theSpecifiedCases.add(new TestFailure(Instant.now(), null, hash, 0, placemarks));
			return this;
		}

		/**
		 * @param cases The number of new, random cases to execute. Default is zero.
		 * @return This configuration
		 */
		public TestConfig withRandomCases(int cases) {
			theMaxRandomCases = cases;
			return this;
		}

		/**
		 * @param failures The maximum number of failures to tolerate before aborting the tests. Default is 1.
		 * @return This configuration
		 */
		public TestConfig withMaxFailures(int failures) {
			theMaxFailures = failures;
			return this;
		}

		/**
		 * @param maxRememberedFixes The number of recent fixed failures to remember and re-attempt
		 * @return This configuration
		 */
		public TestConfig withMaxRememberedFixes(int maxRememberedFixes) {
			theMaxRememberedFixes = maxRememberedFixes;
			return this;
		}

		/**
		 * @param duration The maximum amount of time to allow testing to continue. This setting will abort any executing test and will stop
		 *        test execution if the total time since testing began passes this duration.
		 * @return This configuration
		 */
		public TestConfig withMaxTotalDuration(Duration duration) {
			theMaxTotalDuration = duration;
			return this;
		}

		/**
		 * @param duration The maximum amount of time a test case is allowed to take. If a test case exceeds this duration, the test will be
		 *        stopped and no further tests willl be executed.
		 * @return This configuration
		 */
		public TestConfig withMaxCaseDuration(Duration duration) {
			theMaxCaseDuration = duration;
			return this;
		}

		/**
		 * @param duration If the test helper is called used by a test case for the given interval, the test will fail. This is to detect
		 *        infinite loops or too-long-running routines.
		 * @return This configuration
		 * @see TestHelper#getLastCheckIn()
		 */
		public TestConfig withMaxProgressInterval(Duration duration) {
			theMaxProgressInterval = duration;
			return this;
		}

		/**
		 * @param concurrency The number of test cases to execute simultaneously
		 * @return This configuration
		 */
		public TestConfig withConcurrency(int concurrency) {
			theConcurrency = concurrency;
			return this;
		}

		/**
		 * @param fromMax The number of test cases to execute simultaneously, as a function of the number of available processors on this
		 *        system
		 * @return This configuration
		 */
		public TestConfig withConcurrency(UnaryOperator<Integer> fromMax) {
			int max = Runtime.getRuntime().availableProcessors();
			theConcurrency = fromMax.apply(max);
			return this;
		}

		/**
		 * @param names The names of placemarks to recognize during testing
		 * @return This configuration
		 */
		public TestConfig withPlacemarks(String... names) {
			for (String name : names)
				thePlacemarkNames.add(name);
			return this;
		}

		/**
		 * @param onProgress If true, the tester will print a status message to {@link System#out} after each test case. Default is true.
		 * @param onFailure If true, the tester will print a status message to {@link System#err} after each test failure. Default is true.
		 * @return This configuration
		 */
		public TestConfig withPrinting(boolean onProgress, boolean onFailure) {
			isPrintingProgress = onProgress;
			isPrintingFailures = onFailure;
			return this;
		}

		/**
		 * @param persist Whether to persist failed tests to a failure file (*.broken) for {@link #revisitKnownFailures(boolean) revisiting}
		 *        later. Default is false.
		 * @return This configuration
		 */
		public TestConfig withFailurePersistence(boolean persist) {
			isPersistingFailures = persist;
			return this;
		}

		/**
		 * @param dir The directory in which to place the failure file
		 * @param qualifiedName Whether the file should contain the qualified name of the test, or its {@link Class#getSimpleName() simple}
		 *        name
		 * @return This configuration
		 */
		public TestConfig withPersistenceDir(File dir, boolean qualifiedName) {
			if (dir != null)
				isPersistingFailures = true;
			theFailureDir = dir;
			isFailureFileQualified = qualifiedName;
			return this;
		}

		/**
		 * Executes the test
		 * 
		 * @return The results of the test
		 */
		public TestSummary execute() {
			int maxCases = theMaxRandomCases;
			int maxFailures = theMaxFailures;
			if (maxCases < 0)
				maxCases = Integer.MAX_VALUE;
			else if (maxCases == 0 && theMaxTotalDuration != null)
				maxCases = Integer.MAX_VALUE;
			if (maxFailures <= 0)
				maxFailures = Integer.MAX_VALUE;
			Instant termination = theMaxTotalDuration == null ? Instant.MAX : Instant.now().plus(theMaxTotalDuration);
			int failures = 0;
			int successes = 0;
			Throwable firstError = null;
			List<TestFailure> knownFailures;
			if (isRevisitingKnownFailures || isPersistingFailures)
				knownFailures = getKnownFailures(theFailureDir, theTestable, isFailureFileQualified, thePlacemarkNames);
			else
				knownFailures = null;
			if (theMaxTotalDuration == null && maxCases == 0 && (!isRevisitingKnownFailures || knownFailures.isEmpty()))
				throw new IllegalStateException("No cases configured.  Use withRandomCases() or withMaxTotalDuration()");
			try (TestSetExecution exec = new TestSetExecution(theCreator, isPrintingProgress, isPrintingFailures, Instant.now(),
				theMaxTotalDuration, theMaxCaseDuration, theMaxProgressInterval)) {
				if (isRevisitingKnownFailures) {
					// Always revisit known failures and specified cases linearly
					if (!knownFailures.isEmpty()) {
						for (int i = 0; i < knownFailures.size() && failures < maxFailures
							&& Instant.now().compareTo(termination) < 0; i++) {
							TestFailure failure = knownFailures.get(i);
							boolean repo = failure.fixed == null;
							TestHelper helper = new TestHelper(repo, theMaxProgressInterval != null, failure.seed, 0,
								(repo && isDebugging) ? failure.getBreakpoints() : Collections.emptyNavigableSet(), thePlacemarkNames);
							Throwable err = exec.executeTestCase(i + 1, helper, repo);
							if (err != null) {
								if (failure.fixed != null)
									System.err.print("Test fix regressed: ");
								TestFailure newFailure = new TestFailure(failure.failed, null, helper.getSeed(), helper.getPosition(),
									helper.thePlacemarks);
								if (!newFailure.equals(failure)) {
									if (newFailure.bytes != failure.bytes)
										System.err.println("Test failed "//
											+ (newFailure.bytes > failure.bytes ? "later" : "earlier") + " than before: " + newFailure.bytes
											+ " instead of " + failure.bytes);
									else
										System.err.println("Test failure reproduced");
									knownFailures.set(i, newFailure);
									if (isPersistingFailures)
										writeTestFailures(theFailureDir, theTestable, isFailureFileQualified, thePlacemarkNames,
											knownFailures);
								} else
									System.err.println("Test failure reproduced");
								if (firstError == null)
									firstError = err;
								failures++;
							} else {
								if (failure.fixed == null) {
									System.out.println("Test failure fixed");
									successes++;
									failure.fixed = Instant.now();
									if (tooManyFixedFailures(knownFailures, theMaxRememberedFixes)) {
										knownFailures.remove(i);
										i--;
									}
									if (isPersistingFailures)
										writeTestFailures(theFailureDir, theTestable, isFailureFileQualified, thePlacemarkNames,
											knownFailures);
								} else
									System.out.println("Test failure still fixed");
							}
						}
					}
				}
				int i = 0;
				for (TestFailure test : theSpecifiedCases) {
					TestHelper helper = new TestHelper(true, theMaxProgressInterval != null, test.seed, test.bytes, test.getBreakpoints(),
						test.placemarks.keySet());
					Throwable err = exec.executeTestCase(i + 1, helper, false);
					if (err != null) {
						if (isPersistingFailures) {
							TestFailure failure = new TestFailure(test.failed, test.fixed, helper.getSeed(), helper.getPosition(),
								helper.thePlacemarks);
							knownFailures.add(failure);
							writeTestFailures(theFailureDir, theTestable, isFailureFileQualified, thePlacemarkNames, knownFailures);
						}
						if (firstError == null)
							firstError = err;
						failures++;
					} else {
						successes++;
						if (test.fixed == null) {
							test.fixed = Instant.now();
							writeTestFailures(theFailureDir, theTestable, isFailureFileQualified, thePlacemarkNames, knownFailures);
						}
					}
					i++;
				}
				TestSummary summary;
				if (maxCases <= 0 || failures >= maxFailures || Instant.now().compareTo(termination) >= 0) {
					Duration testDuration = Duration.between(exec.getStart(), Instant.now());
					summary = new TestSummary(successes, failures, testDuration, firstError);
				} else if (theConcurrency <= 1)
					summary = executeLinear(exec, knownFailures, i, successes, failures, maxCases, maxFailures, termination, firstError);
				else
					summary = executeParallel(exec, knownFailures, i, successes, failures, maxCases, maxFailures, termination, firstError);
				return summary;
			}
		}

		private static boolean tooManyFixedFailures(List<TestFailure> knownFailures, int max) {
			int fixed = 0;
			for (TestFailure tf : knownFailures) {
				if (tf.fixed != null)
					fixed++;
			}
			return fixed > max;
		}

		private TestSummary executeLinear(TestSetExecution exec, List<TestFailure> knownFailures, int cases, int successes, int failures,
			int maxCases, int maxFailures, Instant termination, Throwable firstError) {
			for (int i = cases; i < maxCases && failures < maxFailures && Instant.now().compareTo(termination) < 0; i++) {
				TestHelper helper = new TestHelper(false, theMaxProgressInterval != null, Double.doubleToLongBits(Math.random()), 0,
					Collections.emptyNavigableSet(), thePlacemarkNames);
				Throwable err = exec.executeTestCase(i + 1, helper, false);
				if (err != null) {
					if (isPersistingFailures) {
						TestFailure failure = new TestFailure(Instant.now(), null, helper.getSeed(), helper.getPosition(),
							helper.thePlacemarks);
						knownFailures.add(failure);
						writeTestFailures(theFailureDir, theTestable, isFailureFileQualified, thePlacemarkNames, knownFailures);
					}
					if (firstError == null)
						firstError = err;
					failures++;
				} else
					successes++;
			}
			Duration testDuration = Duration.between(exec.getStart(), Instant.now());
			return new TestSummary(successes, failures, testDuration, firstError);
		}

		private TestSummary executeParallel(TestSetExecution exec, List<TestFailure> knownFailures, int cases, int successes, int failures,
			int maxCases, int maxFailures, Instant termination, Throwable firstError) {
			// Since we're delegating the rest of the testing to slave processes, the executor won't be executing any more test cases.
			// So we can let its test execution thread die
			exec.close();
			System.gc();
			TestExecutionMaster master = new TestExecutionMaster(exec, thePlacemarkNames, theConcurrency, cases, successes, failures);
			Throwable[] error = new Throwable[] { firstError };

			master.start();
			while (true) {
				String stopReason = null;
				if (master.getFailures() >= maxFailures)
					stopReason = master.getFailures() + " failures--ending test set";
				else if (master.getCases() >= maxCases)
					stopReason = "Test set complete after " + master.getCases() + " cases";
				else if (Instant.now().compareTo(termination) >= 0)
					stopReason = "Test set complete after "
						+ QommonsUtils.printDuration(Duration.between(exec.getStart(), Instant.now()), false);
				if (stopReason != null) {
					System.out.println(stopReason);
					master.stop();
					break;
				}

				System.gc();
				if (!master.execute(//
					(failure, err) -> {
						synchronized (exec) {
							if (error[0] == null)
								error[0] = err;
							knownFailures.add(failure);
							writeTestFailures(theFailureDir, theTestable, isFailureFileQualified, thePlacemarkNames, knownFailures);
						}
					})) {
					System.err.println(stopReason = "All slaves died");
					break;
				}
			}

			// Wait for slaves to die
			while (master.isAlive()) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {}
			}

			Duration testDuration = Duration.between(exec.getStart(), Instant.now());
			return new TestSummary(master.getSuccesses(), master.getFailures(), testDuration, error[0]);
		}
	}

	static class TestSetExecution implements AutoCloseable {
		private final Constructor<? extends Testable> theCreator;
		private final boolean isPrintingProgress;
		private final boolean isPrintingFailures;
		private final Instant theOriginalStart;
		private final Duration theMaxTotalDuration;
		private final Duration theMaxCaseDuration;
		private final Duration theMaxProgressInterval;
		private final long theDebugHitCount;

		private final Thread theTestSetExecThread;
		private final Thread theTestExecThread;
		private Runnable theTestCase;
		private boolean isTestCaseDone;
		private Throwable theTestCaseError;
		private boolean isTestSetDone;
		private volatile Instant theCaseStart;

		TestSetExecution(Constructor<? extends Testable> creator, boolean isPrintingProgress, boolean isPrintingFailures,
			Instant originalStart, Duration maxTotalDuration, Duration maxCaseDuration, Duration maxProgressInterval) {
			theCreator = creator;
			this.isPrintingProgress = isPrintingProgress;
			this.isPrintingFailures = isPrintingFailures;
			theOriginalStart = originalStart;
			theMaxTotalDuration = maxTotalDuration;
			theMaxCaseDuration = maxCaseDuration;
			theMaxProgressInterval = maxProgressInterval;
			theDebugHitCount = BreakpointHere.getBreakpointCatchCount();

			theTestSetExecThread = Thread.currentThread();
			theTestExecThread = new Thread(() -> {
				while (!isTestSetDone) {
					Runnable testCase = theTestCase;
					if (testCase != null) {
						// Clean out the garbage before each test so we don't mistakenly think the test itself is taking too long
						System.gc();
						theCaseStart = Instant.now();
						if (isPrintingProgress)
							System.out.println("Started at " + theCaseStart);
						theTestCase = null;
						try {
							testCase.run();
						} catch (Throwable e) {
							theTestCaseError = e;
						}
						isTestCaseDone = true;
						theTestSetExecThread.interrupt();
					}
					try {
						Thread.sleep(1000000);
					} catch (InterruptedException e) {}
				}
			}, "Test Case Runner");
			theTestExecThread.setDaemon(true);
			theTestExecThread.start();
		}

		public Instant getStart() {
			return theOriginalStart;
		}

		public Throwable executeTestCase(int caseNumber, TestHelper helper, boolean reproduction) {
			Testable tester;
			try {
				tester = theCreator.newInstance();
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new IllegalStateException("Could not instantiate tester " + theCreator.getDeclaringClass().getName(), e);
			}
			return doTest(tester, caseNumber, helper, reproduction);
		}

		private Throwable doTest(Testable tester, int caseNumber, TestHelper helper, boolean reproduction) {
			String caseLabel = "";
			if (caseNumber > 0)
				caseLabel += "[" + caseNumber + "] ";
			caseLabel += Long.toHexString(helper.getSeed()).toUpperCase() + ": ";
			if (isPrintingProgress) {
				if (reproduction)
					System.out.print("Reproducing ");
				System.out.print(caseLabel);
				System.out.flush();
			}
			theTestCase = () -> {
				tester.accept(helper);
			};
			theTestExecThread.interrupt(); // Start test execution
			Instant caseStart = theCaseStart;
			while (caseStart == null) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {}

				caseStart = theCaseStart;
			}
			Instant totalMax = theMaxTotalDuration == null ? null : caseStart.plus(theMaxTotalDuration);
			Instant caseMax = theMaxCaseDuration == null ? null : caseStart.plus(theMaxCaseDuration);
			Instant checkInMax = theMaxProgressInterval == null ? null : caseStart.plus(theMaxProgressInterval);
			Instant checkInMin = theMaxProgressInterval == null ? null : caseStart;
			Duration sleep = Duration.ofDays(1);
			boolean debug = reproduction && BreakpointHere.isDebugEnabled() != null;
			if (!debug) {
				if (totalMax != null && caseStart.plus(sleep).compareTo(totalMax) > 0)
					sleep = Duration.between(caseStart, totalMax);
				if (caseMax != null && caseStart.plus(sleep).compareTo(caseMax) > 0)
					sleep = Duration.between(caseStart, caseMax);
				if (checkInMax != null && caseStart.plus(sleep).compareTo(checkInMax) > 0)
					sleep = Duration.between(caseStart, checkInMax);
			}
			long caseDebugHitCount = BreakpointHere.getBreakpointCatchCount();
			boolean first = true;
			while (!isTestCaseDone) {
				long debugHits = BreakpointHere.getBreakpointCatchCount();
				if (first)
					first = false;
				else if (reproduction) {// No timeout checking for reproduction
				} else if (debugHits == caseDebugHitCount) {
					// Make sure the test case doesn't take longer than configured limits
					Instant now = Instant.now();
					if (caseMax != null && now.compareTo(caseMax) > 0) {
						theTestCaseError = new IllegalStateException("Timeout: Test case took longer than "
							+ QommonsUtils.printTimeLength(theMaxCaseDuration.getSeconds(), theMaxCaseDuration.getNano()));
						theTestCaseError.setStackTrace(theTestExecThread.getStackTrace());
						break;
					} else if (debugHits == theDebugHitCount && totalMax != null && now.compareTo(totalMax) > 0) {
						theTestCaseError = new IllegalStateException("Timeout: Test set took longer than "
							+ QommonsUtils.printTimeLength(theMaxTotalDuration.getSeconds(), theMaxTotalDuration.getNano()));
						theTestCaseError.setStackTrace(theTestExecThread.getStackTrace());
						break;
					} else if (checkInMax != null && now.compareTo(checkInMax) > 0) {
						Instant checkIn = helper.getLastCheckIn();
						if (checkIn == null || checkIn.compareTo(checkInMin) < 0) {
							theTestCaseError = new IllegalStateException("Timeout: No progress in longer than "
								+ QommonsUtils.printTimeLength(theMaxProgressInterval.getSeconds(), theMaxProgressInterval.getNano()));
							theTestCaseError.setStackTrace(theTestExecThread.getStackTrace());
							break;
						}
						checkInMax = checkIn.plus(theMaxProgressInterval);
						checkInMin = checkIn.plusNanos(1);
						sleep = Duration.between(now, checkInMax);
						if (totalMax != null && now.plus(sleep).compareTo(totalMax) > 0)
							sleep = Duration.between(now, totalMax);
						if (caseMax != null && now.plus(sleep).compareTo(caseMax) > 0)
							sleep = Duration.between(now, caseMax);
					}
				} else {
					System.out.println("Breakpoint detected--no more timeout checking for this case");
					sleep = Duration.ofDays(1);
				}
				try {
					if (sleep.compareTo(Duration.ZERO) > 0)
						Thread.sleep(sleep.toMillis(), sleep.getNano() % 1000000);
				} catch (InterruptedException e) {}
			}
			isTestCaseDone = false;
			theCaseStart = null;
			Throwable e = theTestCaseError;
			theTestCaseError = null;
			if (e != null) {
				if (isPrintingProgress || isPrintingFailures) {
					Instant end = Instant.now();
					if (!isPrintingProgress)
						System.err.print(caseLabel);
					StringBuilder msg = new StringBuilder();
					msg.append("FAILURE@").append(helper.getPosition()).append(" in ");
					Format.DURATION.append(msg, Duration.between(caseStart, end));
					for (String pn : helper.getPlacemarkNames()) {
						long placemark = helper.getLastPlacemark(pn);
						if (placemark >= 0)
							msg.append("\n\t" + pn + "@").append(placemark);
					}
					System.err.println(msg);
					e.printStackTrace();
				}
				return e;
			}
			if (isPrintingProgress) {
				Instant end = Instant.now();
				StringBuilder msg = new StringBuilder().append("SUCCESS in ");
				Format.DURATION.append(msg, Duration.between(caseStart, end));
				if (caseNumber > 1) {
					msg.append(" (");
					Format.DURATION.append(msg, Duration.between(theOriginalStart, end));
					msg.append(" total)");
				}
				System.out.println(msg);
			}
			return null;
		}

		@SuppressWarnings("deprecation")
		@Override
		public void close() {
			if (isTestSetDone)
				return;
			isTestSetDone = true;
			theTestExecThread.interrupt();
			if (theTestExecThread.isAlive()) {
				if (BreakpointHere.getBreakpointCatchCount() > 1)
					System.out.println("Declining to kill test on account of breakpoint");
				else {
					try {
						Thread.sleep(10); // Wait for the thread to die naturally if possible
					} catch (InterruptedException e) {}
					if (theTestExecThread.isAlive())
						theTestExecThread.stop();
				}
			}
		}
	}

	private static final String DATE_FORMAT_STR = "ddMMMyyyy HHmmss.SSS"; // Can't have colons in the format because those are special
	private static final boolean DEBUG_CONCURRENT = false;

	/** Controls a set of {@link TestExecutionSlave}s to execute test cases from multiple processes simultaneously */
	public static class TestExecutionMaster {
		private static final Random RANDOM = new Random();

		private final TestSetExecution theExecutor;
		private final NavigableSet<String> thePlacemarkNames;
		private final TestSlaveHandle[] theSlaves;
		private final AtomicInteger theLivingSlaves;
		private final ListenerList<TestSlaveHandle> theAvailableSlaves;
		private final AtomicInteger theTotalCases;
		private final AtomicInteger theTotalSuccesses;
		private final AtomicInteger theTotalFailures;
		private final Random theRandom;

		private Thread theStreamDrainer;

		/**
		 * @param exec The test executor to execute tests with
		 * @param placemarkNames The placemark names the tester may encounter
		 * @param concurrency The number of tests to run concurrently
		 * @param cases The number of random cases to execute
		 * @param successes The number of successful test cases so far
		 * @param failures The number of test case failures so far
		 */
		public TestExecutionMaster(TestSetExecution exec, NavigableSet<String> placemarkNames, int concurrency,
			int cases, int successes, int failures) {
			theExecutor = exec;
			thePlacemarkNames = placemarkNames;
			theSlaves = new TestSlaveHandle[DEBUG_CONCURRENT ? 1 : concurrency];
			theLivingSlaves = new AtomicInteger(theSlaves.length);
			theAvailableSlaves = ListenerList.build().build();
			theTotalCases = new AtomicInteger(cases);
			theTotalSuccesses = new AtomicInteger(successes);
			theTotalFailures = new AtomicInteger(failures);
			theRandom = new Random();
		}

		/**
		 * Spins up the slave processes and all the threads and resources to monitor and control them
		 * 
		 * @return This master
		 */
		public TestExecutionMaster start() {
			BetterList<String> args = BetterTreeList.<String> build().build().with("java");
			if (DEBUG_CONCURRENT)
				args.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000");
			args.with("-classpath", System.getProperty("java.class.path"), //
				TestExecutionSlave.class.getName());
			int testerIdIndex = args.size();
			args.add("--testerID=0");
			args.add("--testable=" + theExecutor.theCreator.getDeclaringClass().getName());
			args.add("--start=" + new SimpleDateFormat(DATE_FORMAT_STR).format(Date.from(theExecutor.theOriginalStart)));
			if (theExecutor.theMaxTotalDuration != null)
				args.add("--max-total-duration=" + Format.DURATION.format(theExecutor.theMaxTotalDuration));
			if (theExecutor.theMaxCaseDuration != null)
				args.add("--max-case-duration=" + Format.DURATION.format(theExecutor.theMaxCaseDuration));
			if (theExecutor.theMaxProgressInterval != null)
				args.add("--max-progress-interval=" + Format.DURATION.format(theExecutor.theMaxProgressInterval));
			if (!thePlacemarkNames.isEmpty()) {
				StringBuilder placemarkArg = new StringBuilder("--placemarks=");
				for (String placemark : thePlacemarkNames)
					placemarkArg.append(placemark).append(',');
				placemarkArg.deleteCharAt(placemarkArg.length() - 1);
				args.add(placemarkArg.toString());
			}

			for (int p = 0; p < theSlaves.length; p++) {
				String testerID = Long.toHexString(RANDOM.nextLong());
				args.set(testerIdIndex, "--testerID=" + testerID);
				Process process;
				try {
					process = new ProcessBuilder(args).start();
					theSlaves[p] = new TestSlaveHandle(testerID, process);
					theAvailableSlaves.add(theSlaves[p], false);
				} catch (IOException e) {
					theLivingSlaves.getAndDecrement();
					throw new IllegalStateException("Could not start tester slave", e);
				}
			}

			theStreamDrainer = new Thread(() -> {
				AccumulatedMessage message=new AccumulatedMessage();
				while (true) {
					for (TestSlaveHandle slave : theSlaves)
						slave.printOutput(message);
					
					message.flush();

					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {}
				}
			}, "Stream Drainer");
			theStreamDrainer.setDaemon(true);
			theStreamDrainer.start();
			return this;
		}

		boolean execute(BiConsumer<TestFailure, Throwable> onFail) {
			int caseNumber = theTotalCases.incrementAndGet();
			long seed = theRandom.nextLong();
			while (true) {
				if (theLivingSlaves.get() == 0)
					return false;
				ListenerList.Element<TestSlaveHandle> slave = theAvailableSlaves.poll(Long.MAX_VALUE);
				if (slave.get().execute(caseNumber, seed, onFail))
					return true;
			}
		}

		void kill() {
			for (TestSlaveHandle slave : theSlaves)
				slave.kill();
		}

		void stop() {
			for (TestSlaveHandle slave : theSlaves)
				slave.stop();
		}

		int getCases() {
			return theTotalCases.get();
		}

		int getSuccesses() {
			return theTotalSuccesses.get();
		}

		int getFailures() {
			return theTotalFailures.get();
		}

		boolean isAlive() {
			for (TestSlaveHandle slave : theSlaves) {
				if (slave.theProcess.isAlive())
					return true;
			}
			return false;
		}
		
		static class AccumulatedMessage{
			private final StringBuilder text;
			private boolean error;
			
			AccumulatedMessage() {
				this.text = new StringBuilder();
			}
			
			void append(MessageLine message){
				if (text.length() > 0) {
					if (error != message.err) {
						flush();
						error = message.err;
					} else
						text.append('\n');
				}
				message.print(text);
				if(text.length()>=1_000_000)
					flush();
			}
			
			void flush(){
				if (text.length() > 0) {
					(error ? System.err : System.out).println(text);
					text.setLength(0);
				}
			}
		}

		static class MessageLine {
			final int testCase;
			final String message;
			final boolean err;

			MessageLine(int testCase, String message, boolean err) {
				this.testCase = testCase;
				this.message = message;
				this.err = err;
			}

			void print(StringBuilder str) {
				if (testCase >= 0)
					str.append(testCase).append(": ");
				str.append(message);
			}
		}

		class TestSlaveHandle {
			private final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(TestHelper.DATE_FORMAT_STR);
			private final String theTesterId;
			private final Process theProcess;
			private final BufferedReader theSystemOut;
			private final BufferedReader theSystemErr;
			private final BufferedWriter theSystemIn;
			private final Thread theOutListener;
			private final Thread theErrListener;
			private final ListenerList<MessageLine> theMessages;
			volatile int theTestCase;
			volatile long theSeed;
			volatile BiConsumer<TestFailure, Throwable> theFailListener;
			private volatile Instant theTestCaseStart;
			private volatile boolean isStopped;
			private volatile boolean isDead;

			TestSlaveHandle(String testerId, Process process) {
				theTesterId = testerId;
				theProcess = process;
				theTestCase = -1;
				isDead = false;
				theMessages = ListenerList.build().build();

				theSystemOut = new BufferedReader(new InputStreamReader(theProcess.getInputStream()));
				theSystemErr = new BufferedReader(new InputStreamReader(theProcess.getErrorStream()));
				theSystemIn = new BufferedWriter(new OutputStreamWriter(theProcess.getOutputStream()));

				Thread heartbeatThread = new Thread(() -> {
					long lastHeartBeat = System.currentTimeMillis();
					while (!isDead && sendHeartBeat()) {
						long now = System.currentTimeMillis();
						if (now - lastHeartBeat < TestExecutionSlave.HEARTBEAT_FREQUENCY) {
							try {
								Thread.sleep(TestExecutionSlave.HEARTBEAT_FREQUENCY - now + lastHeartBeat);
							} catch (InterruptedException e) {}
							lastHeartBeat = System.currentTimeMillis();
						} else
							lastHeartBeat = now;
					}
					isDead = true;
					theLivingSlaves.getAndDecrement();
				}, "Tester " + theTesterId + " Heartbeat");
				heartbeatThread.setPriority(Thread.MAX_PRIORITY);
				heartbeatThread.setDaemon(true);
				heartbeatThread.start();
				theOutListener = new Thread(() -> {
					try {
						String line = theSystemOut.readLine();
						while (line != null) {
							theMessages.add(new MessageLine(theTestCase, line, false), false);

							if (isDead)
								return;
							line = theSystemOut.readLine();
						}
					} catch (IOException e) {
						System.err.println("I/O error reading System out?");
						e.printStackTrace();
						kill();
					}
				}, "Tester " + theTesterId + " Out Listener");
				theOutListener.setDaemon(true);
				theErrListener = new Thread(() -> {
					try {
						String line = theSystemErr.readLine();
						while (line != null) {
							if (line.startsWith(theTesterId)) {
								try {
									processSlaveRequest(line.substring(theTesterId.length() + 1));// Drop the colon too
								} catch (RuntimeException e) {
									System.err.println("Could not process slave request: " + line);
									e.printStackTrace();
								}
							} else
								theMessages.add(new MessageLine(theTestCase, line, true), false);

							if (isDead)
								return;
							line = theSystemErr.readLine();
						}
					} catch (IOException e) {
						System.err.println("I/O error reading System out?");
						e.printStackTrace();
						kill();
					}
				}, "Tester " + theTesterId + " Err Listener");
				theErrListener.setDaemon(true);
				theOutListener.start();
				theErrListener.start();
			}

			boolean execute(int testCase, long seed, BiConsumer<TestFailure, Throwable> onFail) {
				if (isDead)
					return false;
				if (theTestCase >= 0)
					throw new IllegalStateException("This slave is already executing test case " + theTestCase);
				theTestCase = testCase;
				theSeed = seed;
				this.theFailListener = onFail;
				try {
					theSystemIn.write(Integer.toHexString(testCase) + ":" + Long.toHexString(seed) + "\n");
					theSystemIn.flush();
					return true;
				} catch (IOException e) {
					isDead = true;
					System.err.println("I/O Error writing to system in stream");
					return false;
				}
			}

			@SuppressWarnings("deprecation")
			void kill() {
				isDead = true;
				theProcess.destroy();
				theOutListener.stop();
				theErrListener.stop();
			}

			void stop() {
				isStopped = true;
			}

			boolean sendHeartBeat() {
				if (isDead || !theProcess.isAlive())
					return false;
				try {
					if (isStopped)
						theSystemIn.write("X\n");
					else
						theSystemIn.write('\n');
					theSystemIn.flush();
					return theProcess.isAlive();
				} catch (IOException e) {
					System.err.println("I/O Error writing to system in stream");
					e.printStackTrace();
					return false;
				}
			}

			void processSlaveRequest(String request) {
				if (request.startsWith("X:")) {
					int nextColon = request.indexOf(':', 2);
					int testCase = Integer.parseInt(request.substring(2, nextColon), 16);
					if (testCase != theTestCase)
						theMessages.add(new MessageLine(theTestCase, "Received error for test case " + testCase + ": " + request, true),
							false);
					else {
						int start = nextColon + 1;
						nextColon = request.indexOf(':', start);
						String endTimeStr = request.substring(start, nextColon);
						Instant endTime;
						try {
							endTime = DATE_FORMAT.parse(endTimeStr).toInstant();
						} catch (ParseException e) {
							theMessages.add(new MessageLine(theTestCase,
								"Bad end time \"" + endTimeStr + "\" in " + request.substring(2, nextColon + 1) + ": " + e.getMessage(),
								true), false);
							endTime = Instant.now();
						}
						start = nextColon + 1;
						nextColon = request.indexOf(':', start);
						long position = parseHexLong(request.substring(start, nextColon));
						NavigableMap<String, Long> placemarks = new TreeMap<>();
						for (String placemark : thePlacemarkNames) {
							start = nextColon + 1;
							nextColon = request.indexOf(':', start);
							position = parseHexLong(request.substring(start, nextColon));
							placemarks.put(placemark, position);
						}
						TestFailure failure = new TestFailure(Instant.now(), null, theSeed, position, placemarks);
						endTestCase(endTime, failure, new AssertionError("\n" + request.substring(nextColon + 1).replaceAll("\\n", "\n")),
							position);
					}
				} else if (request.startsWith("I:")) {
					int nextColon = request.indexOf(':', 2);
					int testCase = Integer.parseInt(request.substring(2, nextColon), 16);
					if (testCase != theTestCase)
						theMessages.add(new MessageLine(theTestCase, "Received initialization for test case " + testCase, true), false);
					else {
						String startTimeStr=request.substring(nextColon+1);
						Instant startTime;
						try {
							startTime = DATE_FORMAT.parse(startTimeStr).toInstant();
						} catch (ParseException | RuntimeException e) {
							theMessages.add(new MessageLine(theTestCase, "Bad start time \"" + startTimeStr + "\" in " + request, true),
								false);
							startTime = Instant.now();
						}
						theTestCaseStart = startTime;
						beginTestCase();
					}
				} else if (request.startsWith("D:")) {
					int nextColon = request.indexOf(':', 2);
					int testCase = Integer.parseInt(request.substring(2, nextColon), 16);
					if (testCase != theTestCase)
						throw new IllegalStateException("Received finish notification for test case " + testCase);
					String endTimeStr = request.substring(nextColon + 1);
					Instant endTime;
					try {
						endTime = DATE_FORMAT.parse(endTimeStr).toInstant();
					} catch (ParseException | RuntimeException e) {
						theMessages.add(new MessageLine(theTestCase, "Bad end time \"" + endTimeStr + "\" in " + request, true), false);
						endTime = Instant.now();
					}
					endTestCase(endTime, null, null, 0);
				} else
					theMessages.add(new MessageLine(theTestCase, "Tester error: " + request, true), false);
			}

			void printOutput(AccumulatedMessage accumulated) {
				Element<MessageLine> message = theMessages.poll(0);
				while (message != null) {
					accumulated.append(message.get());

					message = theMessages.poll(0);
				}
			}

			private void beginTestCase() {
				if (theExecutor.isPrintingProgress)
					theMessages.add(new MessageLine(theTestCase,
						Long.toHexString(theSeed) + ": Executing at "
							+ QommonsUtils.printEndTime(theExecutor.theOriginalStart.toEpochMilli(), theTestCaseStart.toEpochMilli(),
								TimeZone.getDefault(), Calendar.MINUTE),
						false), false);
			}

			private void endTestCase(Instant endTime, TestFailure failure, Throwable error, long position) {
				if (failure != null)
					theFailListener.accept(failure, error);
				if ((theExecutor.isPrintingProgress && failure == null) || (theExecutor.isPrintingFailures && failure != null)) {
					StringBuilder msg = new StringBuilder();
					msg.append(failure == null ? "Succeeded" : "Failed").append(" at ")//
						.append(QommonsUtils.printEndTime(theExecutor.theOriginalStart.toEpochMilli(), theTestCaseStart.toEpochMilli(),
							TimeZone.getDefault(), Calendar.MINUTE))//
						.append(" (");
					QommonsUtils.printDuration(Duration.between(theTestCaseStart, endTime), msg, false);
					msg.append(", ");
					QommonsUtils.printDuration(Duration.between(theExecutor.theOriginalStart, endTime), msg, false);
					msg.append(" total)");
					if (failure != null)
						msg.append(", position=" + position);
					theMessages.add(new MessageLine(theTestCase, msg.toString(), error != null), false);
				}
				theTestCase = -1;
				theTestCaseStart = null;
				if (failure != null)
					theTotalFailures.getAndIncrement();
				else
					theTotalSuccesses.getAndIncrement();
				if (!isDead)
					theAvailableSlaves.add(this, false);
			}
		}
	}

	static long parseHexLong(String hexLong) {
		byte[] bytes = StringUtils.encodeHex().parse(hexLong);
		long result = 0;
		for (byte b : bytes)
			result = (result << 8) | ((b + 0x100) & 0xff);
		return result;
	}

	/** A slave process that accepts test cases via stdin, executes them, and provides results via stderr */
	public static class TestExecutionSlave {
		private static class TestCase {
			final int caseNumber;
			final TestHelper helper;

			TestCase(int caseNumber, TestHelper helper) {
				this.caseNumber = caseNumber;
				this.helper = helper;
			}
		}

		static final int HEARTBEAT_FREQUENCY = 500;

		private final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_STR);

		private final String theTesterId;
		private final Thread theExecutionThread;
		private final TestSetExecution theTesting;
		private volatile TestCase theTestCase;

		/**
		 * @param testerId The master-supplied ID of this slave
		 * @param testable The testable class constructor to use to create fresh test cases
		 * @param originalStart The start time of the test set
		 * @param maxTotalDuration The total duration for the test set
		 * @param maxCaseDuration The maximum test case duration
		 * @param maxProgressInterval The maximum progress interval, or time without calling the {@link TestHelper}
		 */
		public TestExecutionSlave(String testerId, Constructor<? extends Testable> testable, Instant originalStart,
			Duration maxTotalDuration, Duration maxCaseDuration, Duration maxProgressInterval) {
			theTesterId = testerId;
			TestSetExecution[] exec = new TestSetExecution[1];
			theExecutionThread = new Thread(() -> {
				exec[0] = new TestSetExecution(testable, false, true, originalStart, maxTotalDuration, maxCaseDuration,
					maxProgressInterval);
				while (true) {
					try {
						Thread.sleep(24L * 60 * 60 * 1000);
					} catch (InterruptedException e) {}
					TestCase testCase = theTestCase;
					if (testCase != null)
						executeTestCase(testCase.caseNumber, testCase.helper);
				}
			}, "Test Case Queue");
			theExecutionThread.setDaemon(true);
			theExecutionThread.start();
			while (exec[0] == null) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {}
			}
			theTesting = exec[0];
		}

		/**
		 * Starts executing a test case
		 * 
		 * @param caseNumber The case number (usually a sequence)
		 * @param helper The test helper randomness source for the test case
		 */
		public void queueTestCase(int caseNumber, TestHelper helper) {
			theTestCase = new TestCase(caseNumber, helper);
			theExecutionThread.interrupt();
		}

		void executeTestCase(int caseNumber, TestHelper helper) {
			System.err.println(theTesterId + ":I:" + Integer.toHexString(caseNumber) + ":" + DATE_FORMAT.format(new Date()));
			Throwable error = theTesting.executeTestCase(caseNumber, helper, false);
			if (error != null) {
				StringBuilder msg = new StringBuilder(theTesterId).append(":X:").append(Integer.toHexString(caseNumber)).append(':');
				msg.append(DATE_FORMAT.format(new Date())).append(':');
				msg.append(Long.toHexString(helper.getPosition())).append(':');
				for (String placemark : helper.getPlacemarkNames())
					msg.append(Long.toHexString(helper.getLastPlacemark(placemark))).append(':');
				msg.append(formatException(error));
				System.err.println(msg);
			} else
				System.err.println(theTesterId + ":D:" + Integer.toHexString(caseNumber) + ":" + DATE_FORMAT.format(new Date()));
			theTestCase = null;
		}

		boolean isCheckIn() {
			return theTesting.theMaxProgressInterval != null;
		}

		/**
		 * The main method to spin up the slave
		 * 
		 * @param clArgs Command-line arguments:
		 *        <ul>
		 *        <li><b>--testerID=</b>The ID for this test slave</li>
		 *        <li><b>--testable=</b>The fully-qualified name of the {@link Testable} implementation to test</li>
		 *        <li><b>--start=</b>The start time of the test set</li>
		 *        <li><b>--max-total-duration=</b>The total duration for the test set</li>
		 *        <li><b>--max-case-duration=</b>The maximum test case duration</li>
		 *        <li><b>--max-progress-interval</b>The maximum progress interval, or time without calling the {@link TestHelper}</li>
		 *        <li><b>--placemarks=</b>The comma-separated list of placemarks to expect for the test cases</li>
		 *        </ul>
		 */
		public static void main(String[] clArgs) {
			ArgumentParsing.Arguments args = ArgumentParsing.build()//
				.forValuePattern(patt -> patt//
					.addStringArgument("testerID", a -> a.required())//
					.addStringArgument("testable", a -> a.required())//
					.addInstantArgument("start", a -> a.required())//
					.addDurationArgument("max-total-duration", a -> a.optional())//
					.addDurationArgument("max-case-duration", a -> a.optional())//
					.addDurationArgument("max-progress-interval", a -> a.optional())//
				)//
				.forMultiValuePattern(patt -> patt//
					.addStringArgument("placemarks", a -> a.anyTimes())//
				)//
				.build().parse(clArgs);

			String testerID = args.get("testerID", String.class);
			String testClassName = args.get("testable", String.class);
			Class<? extends Testable> testClass;
			try {
				testClass = Class.forName(testClassName).asSubclass(Testable.class);
			} catch (ClassNotFoundException e) {
				System.err.println(testerID + ":Test class " + testClassName + " not found");
				System.exit(1);
				return;
			} catch (ClassCastException e) {
				System.err.println(testerID + ":Class " + testClassName + " does not extend testable");
				System.exit(1);
				return;
			}
			Constructor<? extends Testable> creator;
			try {
				creator = getCreator(testClass);
			} catch (RuntimeException e) {
				System.err.println(e.getMessage());
				System.exit(1);
				return;
			}
			NavigableSet<String> placemarkNames = new TreeSet<>(args.getAll("placemarks", String.class));

			TestExecutionSlave slave = new TestExecutionSlave(testerID, creator, //
				args.get("start", Instant.class), args.get("max-total-duration", Duration.class),
				args.get("max-case-duration", Duration.class), args.get("max-progress-interval", Duration.class));
			// Set up a heart beat listener that expects some input from the master every so often.
			// If no input is received in that interval, the tester is assumed to have been killed and we will exit.
			boolean[] stopped = new boolean[1];
			class HeartBeat implements Runnable {
				volatile long lastHeartbeatReceived = System.currentTimeMillis();

				@Override
				public void run() {
					if (System.currentTimeMillis() - lastHeartbeatReceived > HEARTBEAT_FREQUENCY * 5) {
						System.err.println(testerID + ":No heartbeat detected--parent process must have terminated--exiting");
						stopped[0] = true;
					}
				}
			}
			Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
			HeartBeat heartBeat = new HeartBeat();
			Thread heartBeatThread = new Thread(() -> {
				while (!stopped[0]) {
					System.gc(); // Do this here so the heartbeat isn't disrupted
					try {
						Thread.sleep(HEARTBEAT_FREQUENCY);
					} catch (InterruptedException e) {}
					heartBeat.run();
				}
			}, "Heart Beat Listener");
			heartBeatThread.setDaemon(true);
			heartBeatThread.start();
			try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
				String line = in.readLine();
				while (line != null) {
					heartBeat.lastHeartbeatReceived = System.currentTimeMillis();
					if (stopped[0]) {
						if (slave.theTestCase == null)
							break;
					} else if (line.length() == 0) { // Heartbeat
					} else if (line.length() == 1) { // Stop command
						stopped[0] = true;
						if (slave.theTestCase == null)
							break;
					} else {
						int colonIdx = line.indexOf(':');
						if (colonIdx < 0) {
							System.err.println(testerID + ":Illegal test case input: " + line);
							System.exit(1);
							return;
						}
						int caseNumber;
						try {
							caseNumber = Integer.parseInt(line.substring(0, colonIdx), 16);
						} catch (NumberFormatException e) {
							System.err.println(testerID + ":Illegal case number input: " + line);
							System.exit(1);
							return;
						}
						long seed;
						try {
							seed = parseHexLong(line.substring(colonIdx + 1));
						} catch (NumberFormatException e) {
							System.err.println(testerID + ":Illegal random seed input: " + line);
							System.exit(1);
							return;
						}
						TestHelper helper = new TestHelper(false, slave.isCheckIn(), seed, 0, Collections.emptyNavigableSet(),
							placemarkNames);
						slave.queueTestCase(caseNumber, helper);
					}

					line = in.readLine();
				}
			} catch (IOException e) {
				System.err
					.println(testerID + ":I/O Error occurred with input: " + formatException(e));
				System.exit(1);
				return;
			}
		}

		private static String formatException(Throwable e) {
			StringWriter str = new StringWriter();
			e.printStackTrace(new PrintWriter(str));
			return str.toString().replaceAll("\r", "").replaceAll("\n", "\\n");
		}
	}

	/** The results of executing a set of test cases */
	public static class TestSummary {
		private final int theSuccesses;
		private final int theFailures;
		private final Duration theDuration;
		private final Throwable theFirstError;

		/**
		 * @param successes The number of successful test cases
		 * @param failures The number of failed test cases
		 * @param duration The total duration of the testing
		 * @param firstError The first error thrown by any failed test
		 */
		public TestSummary(int successes, int failures, Duration duration, Throwable firstError) {
			theSuccesses = successes;
			theFailures = failures;
			theDuration = duration;
			theFirstError = firstError;
		}

		/** @return The number of successful test cases */
		public int getSuccesses() {
			return theSuccesses;
		}

		/** @return The number of failed test cases */
		public int getFailures() {
			return theFailures;
		}

		/** @return The total duration of the testing */
		public Duration getDuration() {
			return theDuration;
		}

		/** @return The first error thrown by any failed test */
		public Throwable getFirstError() {
			return theFirstError;
		}

		/**
		 * @return This summary
		 * @throws AssertionError If any test failed
		 */
		public TestSummary throwErrorIfFailed() throws AssertionError {
			if (theFirstError instanceof AssertionError)
				throw (AssertionError) theFirstError;
			else if (theFirstError != null)
				throw new AssertionError(theFirstError.getMessage(), theFirstError);
			return this;
		}

		/** @return Prints this summary to {@link System#out} */
		public TestSummary printResults() {
			System.out.println("Summary: " + this);
			return this;
		}

		@Override
		public String toString() {
			StringBuilder summary = new StringBuilder();
			if (theSuccesses > 0)
				summary.append(theSuccesses).append(" successful case").append(theSuccesses > 1 ? "s" : "");
			if (theFailures > 0) {
				if (theSuccesses > 0)
					summary.append(", ");
				summary.append(theFailures).append(" failed case").append(theFailures > 1 ? "s" : "");
			}
			if (theSuccesses == 0 && theFailures == 0)
				summary.append("No cases");
			summary.append(" in ");
			QommonsUtils.printTimeLength(theDuration.toMillis(), summary, false);
			return summary.toString();
		}
	}

	/**
	 * Creates a test configuration
	 * 
	 * @param testable The testable class to execute tests with
	 * @return The test configuration
	 */
	public static TestConfig createTester(Class<? extends Testable> testable) {
		return new TestConfig(testable);
	}

	private static List<TestFailure> getKnownFailures(File failureDir, Class<? extends Testable> testable, boolean qualifiedName,
		NavigableSet<String> placemarkNames) {
		File testFile = getFailureFile(failureDir, testable, qualifiedName, false);
		if (testFile == null || !testFile.exists())
			return new ArrayList<>();

		try (BufferedReader reader = new BufferedReader(new FileReader(testFile))) {
			CsvParser parser = new CsvParser(reader, ',');
			String[] headers = parser.parseNextLine();
			if (headers.length < 4 || !headers[0].equals("Failed") || !headers[1].equals("Fixed") || !headers[2].equals("Seed")
				|| !headers[3].equals("Position")) {
				System.err.println("Unrecognized test file: Expected Failed,Fixed,Seed,Position for first 4 column headers");
				return new ArrayList<>();
			}
			String[] line = new String[headers.length];
			List<TestFailure> failures = new ArrayList<>();

			while (parser.parseNextLine(line)) {
				Instant failed, fixed;
				long seed = -1, position = -1;
				NavigableMap<String, Long> placemarks = null;
				try {
					failed = TimeUtils.parseInstant(line[0], true, true, teo -> teo.withEvaluationType(RelativeInstantEvaluation.Past))
						.evaluate(Instant::now);
				} catch (ParseException e) {
					System.err.println("Could not parse failure time '" + line[0] + "'");
					e.printStackTrace();
					failed = Instant.now();
				}
				fixed = null;
				if (line[1].length() > 0) {
					try {
						fixed = TimeUtils.parseInstant(line[1], true, true, teo -> teo.withEvaluationType(RelativeInstantEvaluation.Past))
							.evaluate(Instant::now);
					} catch (ParseException e) {
						System.err.println("Could not parse fix time '" + line[0] + "' on line " + (parser.getCurrentLineNumber() + 1));
						e.printStackTrace();
						failed = Instant.now();
					}
				}
				try {
					seed = parseHexLong(line[2]);
					position = Long.parseLong(line[3]);
				} catch (NumberFormatException e) {
					System.err.println("Could not parse failure on line " + (parser.getCurrentLineNumber() + 1));
					e.printStackTrace();
					continue;
				}
				if (headers.length == 4)
					placemarks = Collections.emptyNavigableMap();
				else {
					placemarks = new TreeMap<>();
					for (int h = 4; h < headers.length; h++) {
						try {
							placemarks.put(headers[h], Long.parseLong(line[h]));
						} catch (NumberFormatException e) {
							System.err.println("Could not parse placemark " + headers[h] + " position '" + line[h] + "' on line "
								+ (parser.getCurrentLineNumber() + 1));
							placemarks.put(headers[h], -1L);
						}
					}
					placemarks = Collections.unmodifiableNavigableMap(placemarks);
				}
				failures.add(new TestFailure(failed, fixed, seed, position, placemarks));
			}
			return failures;
		} catch (IOException | TextParseException e) {
			e.printStackTrace();
			return new ArrayList<>();
		}
	}

	private static File getFailureFile(File failureDir, Class<? extends Testable> testable, boolean qualifiedName, boolean create) {
		File testFile = null;
		if (failureDir != null)
			return new File(failureDir, (qualifiedName ? testable.getName() : testable.getSimpleName()) + ".test");

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
				System.err
					.println("Could not create failure file " + testFile.getAbsolutePath() + " for class " + testable.getName() + ": " + e);
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

	private static final String TIME_FORMAT = "ddMMMyyyy HH:mm:ss";

	private static void writeTestFailures(File failureDir, Class<? extends Testable> testable, boolean qualifiedName,
		NavigableSet<String> placemarkNames, List<TestFailure> failures) {
		File testFile = getFailureFile(failureDir, testable, qualifiedName, true);
		if (testFile == null)
			return;
		// Write unfixed failures first, fixed ones last
		Collections.sort(failures, (f1, f2) -> {
			if (f1.fixed == null) {
				if (f2.fixed == null)
					return f1.failed.compareTo(f2.failed);
				else
					return -1;
			} else if (f2.fixed == null)
				return 1;
			else
				return f1.fixed.compareTo(f2.fixed);
		});
		SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_FORMAT);
		try (BufferedWriter writer = new BufferedWriter(new java.io.FileWriter(testFile))) {
			writer.write("Failed,Fixed,Seed,Position");
			for (String pn : placemarkNames)
				writer.write("," + pn);
			writer.write("\n");
			StringBuilder csvLine = new StringBuilder();
			for (TestFailure failure : failures) {
				csvLine.setLength(0);
				csvLine.append(timeFormat.format(new Date(failure.failed.toEpochMilli()))).append(',');
				if (failure.fixed != null)
					csvLine.append(timeFormat.format(new Date(failure.fixed.toEpochMilli())));
				csvLine.append(',');
				csvLine.append(Long.toHexString(failure.seed)).append(',').append(failure.bytes);
				for (String pn : placemarkNames) {
					csvLine.append(',');
					Long placemark = failure.placemarks.get(pn);
					if (placemark != null)
						csvLine.append(placemark);
				}
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
}

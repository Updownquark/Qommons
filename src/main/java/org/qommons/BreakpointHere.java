package org.qommons;

import java.lang.management.ManagementFactory;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import org.qommons.io.Format;

/**
 * The BreakpointHere class enables applications to transfer control to the java debugger, where this is VM-enabled. Users should always
 * have a breakpoint set at the indicated line in the source. Typical applicatons for this class are:
 * <ul>
 * <li>A test utility reproducing a failed test, transferring control to the debugger just before the anticipated failure</li>
 * <li>An in-application debugger that may have an option to transfer control to the java debugger for more detailed debugging</li>
 * </ul>
 */
public class BreakpointHere {
	private static long IGNORE_ALL;
	private static final Map<String, Long> IGNORING_CLASSES = new java.util.LinkedHashMap<>();
	private static final Map<StackTraceElement, Long> IGNORING_LOCATIONS = new java.util.LinkedHashMap<>();
	private static final Map<String, IgnoreType> CLI_IGNORE;
	private static boolean HAS_PRINTED_INPUT_UNRESPONSIVE = false;
	static {
		Map<String, IgnoreType> cliIgnore = new LinkedHashMap<>();
		cliIgnore.put("l", IgnoreType.LOCAL);
		cliIgnore.put("c", IgnoreType.CLASS);
		cliIgnore.put("a", IgnoreType.ALL);
		CLI_IGNORE = Collections.unmodifiableMap(cliIgnore);
	}

	private static final AtomicLong theBreakpointCatchCount = new AtomicLong();

	private static enum IgnoreType {
		/** Does nothing */
		NONE,
		/** Ignores future breakpoints from the same location */
		LOCAL,
		/** Ignores future breakpoints from the same class */
		CLASS,
		/** Ignores all future breakpoints */
		ALL;
	}

	/**
	 * <p>
	 * Attempts to transfer control to the debugger. Debugging environments should set a breakpoint on the indicated line in this method.
	 * </p>
	 * <p>
	 * If debugging is not enabled, this method will print a message to that effect to {@link System#err} the first time it is called and
	 * return immediately. Thereafter, invocations of this method will return immediately with no effect.
	 * </p>
	 * <p>
	 * If debugging is enabled but a breakpoint is not set at the appropriate place, this method will print a message to {@link System#err}
	 * instructing the user to do so, along with instructions on how to skip the breakpoint via command-line input.
	 * </p>
	 * <p>
	 * Whether or not the breakpoint is caught, the user may use command-line input to skip the breakpoint and potentially others in the
	 * future:
	 * <ul>
	 * <li>Simply pressing the ENTER key will cause this method to return. Future calls to this method will perform as normal.</li>
	 * <li>Typing 'L' and pressing ENTER will also cause future calls to this method from the same calling line of code to return
	 * immediately.</li>
	 * <li>Typing 'C' and pressing ENTER will also cause future calls to this method from the same calling <b>class</b> to return
	 * immediately.</li>
	 * <li>Typing 'A' and pressing ENTER will also cause all future calls to this method to return immediately.</li>
	 * </ul>
	 * 
	 * @return Whether the breakpoint was actually caught
	 */
	public static boolean breakpoint() {
		long now = System.currentTimeMillis();
		if (IGNORE_ALL > now)
			return false;
		Thread thread=Thread.currentThread();
		StackTraceElement [] stack;
		StackTraceElement source;
		if(IGNORING_CLASSES.isEmpty() && IGNORING_LOCATIONS.isEmpty()) {
			// If there's nothing to ignore, don't unwind the stack twice
			stack = null;
			source = null;
		} else {
			stack = thread.getStackTrace();
			if(stack == null || stack.length == 0) {
				IGNORE_ALL = Long.MAX_VALUE;
				System.err.println("WARNING! Application is attempting to catch a breakpoint, but line numbers seem to not be included");
				return false;
			}
			source = stack[2];
			Long ignoreTime = IGNORING_CLASSES.get(source.getClassName());
			if (ignoreTime != null && ignoreTime.longValue() > now)
				return false;
			ignoreTime = IGNORING_LOCATIONS.get(source);
			if (ignoreTime != null && ignoreTime.longValue() > now)
				return false;
		}

		theBreakpointCatchCount.incrementAndGet();
		boolean breakpointCaught = false;
		boolean alerted = false;
		AsyncInputReader reader = new AsyncInputReader();
		IgnoreType ignore = null;
		long ignoreTime = -1;
		try {
			do {
				long pre = System.nanoTime();
				ignore = IgnoreType.NONE;

				/* ||==\\   ||==\\   ||=====     /\     ||   //     ||    ||  ||=====  ||==\\   ||=====  || ||
				 * ||   \\  ||   \\  ||         //\\    ||  //      ||    ||  ||       ||   \\  ||       || ||
				 * ||   //  ||   //  ||        //  \\   || //       ||    ||  ||       ||   //  ||       || ||
				 * ||===    ||===    ||===    //====\\  ||//        ||====||  ||===    ||===    ||===    || ||
				 * ||   \\  || \\    ||      ||      || || \\       ||    ||  ||       || \\    ||       || ||
				 * ||   ||  ||  \\   ||      ||      || ||  \\      ||    ||  ||       ||  \\   ||
				 * ||===//  ||   \\  ||===== ||      || ||   \\     ||    ||  ||=====  ||   \\  ||=====  () ()
				 *
				 * The user should set a breakpoint on the following line */
				/*         \/ \/ \/ \/ \/ \/ \/ \/ \/ \/ \/ \/ \/ \/ \/ */
				/* >>>> */ stack = Thread.currentThread().getStackTrace(); // <<<< Yeah, right here.
				/*         /\ /\ /\ /\ /\ /\ /\ /\ /\ /\ /\ /\ /\ /\ /\ */
				// Good. If you're here, press step return now.

				// Or you can choose to ignore this breakpoint or others like it in the future by changing the value the ignore variable

				source = stack[2];
				if (System.nanoTime() - pre < 10000000) {
					// There is not a breakpoint set here.
					StackTraceElement stackTop = stack[1];

					if (!alerted) {
						String debugArg = isDebugEnabled();
						if (debugArg == null) {
							theBreakpointCatchCount.decrementAndGet();
							System.err
								.println("WARNING! Application is attempting to catch a breakpoint, but debugging seems to be disabled");
							IGNORE_ALL = Long.MAX_VALUE;
							return false;
						}
						alerted = true;

						StringBuilder msg = new StringBuilder();
						msg.append("ATTENTION! ").append(source.getClassName()).append(" is attempting to catch a breakpoint at ")
							.append(stackTop).append(" on thread ").append(thread.getName()).append(" (").append(thread.getId())
							.append(')');
						msg.append("\nNo break point is set at this location. You may:");
						msg.append("\n 1) Install a breakpoint at ").append(stackTop).append(". We'll wait for you.");
						msg.append("\n 2) Press ENTER to skip the breakpoint and return control to the application this time.");
						msg.append("\n 3) Type \"L\" and press ENTER to ignore this particular break point (").append(source)
							.append(") for this session.");
						msg.append(
							"\n 4) Type \"C\" and press ENTER to ignore all break points from the class that is requesting this break (")
							.append(source.getClassName()).append(") for this session.");
						msg.append("\n 5) Type \"A\" and press ENTER to ignore all break points for this session.");
						msg.append("\n If a duration is appended to the end of the line after a space,")
							.append(" the command will only be effective for the given amount of time.");
						System.err.println(msg);
					}
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				} else
					breakpointCaught = true;

				String command = reader.getCommand();
				if (command != null) {
					if (command.length() == 0)
						break;
					int space = command.indexOf(' ');
					if (space > 0) {
						try {
							ignoreTime = Format.DURATION.parse(command.substring(space).trim()).toMillis();
						} catch (ParseException | RuntimeException e) {
							e.printStackTrace();
							continue;
						}
						command = command.substring(0, space);
					}
					ignore = CLI_IGNORE.get(command.toLowerCase());
					if (ignore == null) {
						System.err.println("Type \"L\", \"C\", \"A\", or just press ENTER");
						continue;
					}
					break;
				}
			} while (!breakpointCaught);
		} finally {
			if (reader != null)
				reader.close();
		}
		if (ignore != null && ignore != IgnoreType.NONE) {
			StringBuilder msg = new StringBuilder("Ignoring ");
			switch(ignore){
			case NONE:
				break;
			case LOCAL:
				msg.append("future breakpoints from ").append(source);
				IGNORING_LOCATIONS.put(source, ignoreTime > 0 ? System.currentTimeMillis() + ignoreTime : Long.MAX_VALUE);
				break;
			case CLASS:
				// Source actually can't be null here, but I'm suppressing a warning
				msg.append("future breakpoints from class ").append(source == null ? "?" : source.getClassName());
				if (source != null)
					IGNORING_CLASSES.put(source.getClassName(), ignoreTime > 0 ? System.currentTimeMillis() + ignoreTime : Long.MAX_VALUE);
				break;
			case ALL:
				msg.append("all future breakpoints");
				IGNORE_ALL = ignoreTime > 0 ? System.currentTimeMillis() + ignoreTime : Long.MAX_VALUE;
				break;
			}
			if (ignoreTime > 0)
				QommonsUtils.printTimeLength(ignoreTime, msg.append(" for "), true);
			System.out.println(msg.toString());
		}
		return breakpointCaught;
	}

	/**
	 * No complexity here, just breaks if the condition is true. I've found use cases where this is useful as a single expression.
	 * 
	 * @param condition The condition to break on
	 */
	public static void breakpointIf(boolean condition) {
		if (condition)
			breakpoint();
	}

	/**
	 * Same as {@link #breakpoint()}, but since stupid Java doesn't allow any statements before the super constructor invocation in a
	 * constructor, this method allows the placement of breakpoints in constructors prior to the super invocation with parameter, as in
	 * <code>super(BreakpointHere.breakpoint(parameter))</code>.
	 * 
	 * @param <T> The type of the value
	 * @param value The parameter value
	 * @return The input value
	 */
	public static <T> T breakpoint(T value) {
		breakpoint();
		return value;
	}

	/** @return The (approximate) number of times a {@link #breakpoint()} was caught during this VM run */
	public static long getBreakpointCatchCount() {
		return theBreakpointCatchCount.get();
	}

	/** The set of known Java VM arguments that indicate that debugger attachment is possible */
	public static Set<String> DEBUG_ARGS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(//
		"-Xdebug", "-agentlib:jdwp")));

	private static Optional<String> DEBUG_ARG;

	/** @return The Java VM argument enabling debugger attachment in this VM, or null if debugging is disabled */
	public static String isDebugEnabled() {
		if (DEBUG_ARG == null) {
			List<String> vmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
			for (String arg : vmArgs) {
				for (String debugArg : DEBUG_ARGS)
					if (arg.startsWith(debugArg))
						DEBUG_ARG = Optional.of(arg);
			}
			if (DEBUG_ARG == null)
				DEBUG_ARG = Optional.empty();
		}
		return DEBUG_ARG.orElse(null);
	}

	/**
	 * @return The StackTraceElement representing the line of code that this method was called from. May be null if the necessary debugging
	 *         info is not available to the VM.
	 */
	public static StackTraceElement getCodeLine() {
		return getCodeLine(0);
	}

	/**
	 * @param level The number of levels up the stack to get
	 * @return The StackTraceElement representing the line of code <code>level</code> calls up the stack from the line that this method was
	 *         called from. May be null if the necessary debugging info is not available to the VM.
	 */
	public static StackTraceElement getCodeLine(int level) {
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		if (stack == null || stack.length < 2)
			return null;
		return stack[2 + level];
	}

	private static class AsyncInputReader {
		private final Scanner theScanner;
		private final Thread theReaderThread;
		private volatile boolean keepReading;
		private volatile String theCommand;
		private volatile boolean isResponsive;

		AsyncInputReader() {
			theScanner = new Scanner(System.in);
			keepReading = true;
			theReaderThread = new Thread(() -> {
				while (keepReading) {
					try {
						if (System.in.available() > 0)
							theCommand = theScanner.nextLine().trim();
						isResponsive = true;
					} catch (java.io.IOException e) {
						System.err.println("Could not read from System.in" + e);
						theCommand = "";
						break;
					}
				}
			}, BreakpointHere.class.getSimpleName() + " Input Reader");
			theReaderThread.start();
		}

		String getCommand() {
			if (!isResponsive) {
				if (!HAS_PRINTED_INPUT_UNRESPONSIVE) {
					HAS_PRINTED_INPUT_UNRESPONSIVE = true;
					StringBuilder msg = new StringBuilder();
					msg.append("Unable to read from System.in due to monitor hold on System.in.");
					msg.append("\nPlace the requested breakpoint before breakpoint() invocation to use this utility.");
					System.err.println(msg);
				}
				return ""; // Causes the wait loop to be terminated with no message.
			}
			return theCommand;
		}

		void close() {
			keepReading = false;
		}
	}
}

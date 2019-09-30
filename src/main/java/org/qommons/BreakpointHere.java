package org.qommons;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The BreakpointHere class enables applications to transfer control to the java debugger, where this is VM-enabled. Users should always
 * have a breakpoint set at the indicated line in the source. The typical application for this is an in-application debugger that may have
 * an option to transfer control to the java debugger for more detailed debugging.
 */
public class BreakpointHere {
	private static boolean IGNORE_ALL;
	private static final Set<String> IGNORING_CLASSES = new java.util.LinkedHashSet<>();
	private static final Set<StackTraceElement> IGNORING_LOCATIONS = new java.util.LinkedHashSet<>();
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
	 * Invoked to transfer control to the debugger. Debugging environments should set a breakpoint on the indicated line in this method.
	 * 
	 * @return Whether the breakpoint was actually caught
	 */
	public static boolean breakpoint() {
		if(IGNORE_ALL)
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
				IGNORE_ALL = true;
				System.err.println("WARNING! Application is attempting to catch a breakpoint, but line numbers seem to not be included");
				return false;
			}
			source = stack[2];
			if(IGNORING_CLASSES.contains(source.getClassName()))
				return false;
			if(IGNORING_LOCATIONS.contains(source))
				return false;
		}

		theBreakpointCatchCount.incrementAndGet();
		boolean breakpointCaught = false;
		boolean alerted = false;
		AsyncInputReader reader = null;
		IgnoreType ignore = null;
		do {
			long pre = System.nanoTime();
			ignore = IgnoreType.NONE;



			/* ||==\\   ||==\\   ||=====     //\\     ||   //     ||    ||  ||=====  ||==\\   ||=====  || ||
			 * ||   \\  ||   \\  ||         //  \\    ||  //      ||    ||  ||       ||   \\  ||       || ||
			 * ||   //  ||   //  ||        ||    ||   || //       ||    ||  ||       ||   //  ||       || ||
			 * ||===    ||===    ||===     //====\\   ||//        ||====||  ||===    ||===    ||===    || ||
			 * ||   \\  || \\    ||       //      \\  || \\       ||    ||  ||       || \\    ||       || ||
			 * ||   ||  ||  \\   ||       ||      ||  ||  \\      ||    ||  ||       ||  \\   ||
			 * ||===//  ||   \\  ||=====  ||      ||  ||   \\     ||    ||  ||=====  ||   \\  ||=====  () ()
			 *
			 * The user should set a breakpoint on the following line */
			/*         \/ \/ \/ \/ \/ \/ \/ \/ \/ \/ \/ \/ \/ \/ \/ */
			/* >>>> */ stack = Thread.currentThread().getStackTrace(); // <<<< Yeah, right here.
			/*         /\ /\ /\ /\ /\ /\ /\ /\ /\ /\ /\ /\ /\ /\ /\ */
			// Good. If you're here, press step return now.

			// Or you can choose to ignore this breakpoint or others like it in the future by changing the value the ignore variable



			source = stack[2];
			if(System.nanoTime() - pre < 10000000) {
				// There is not a breakpoint set here.
				StackTraceElement stackTop = stack[1];

				if(!alerted) {
					String debugArg = isDebugEnabled();
					if (debugArg == null) {
						theBreakpointCatchCount.decrementAndGet();
						System.err.println("WARNING! Application is attempting to catch a breakpoint, but debugging seems to be disabled");
						IGNORE_ALL = true;
						return false;
					}
					alerted = true;

					StringBuilder msg = new StringBuilder();
					msg.append("ATTENTION! ").append(source.getClassName()).append(" is attempting to catch a breakpoint at ")
						.append(stackTop).append(" on thread ").append(thread.getName()).append(" (").append(thread.getId()).append(')');
					msg.append("\nNo break point is set at this location. You may:");
					msg.append("\n 1) Install a breakpoint at ").append(stackTop).append(". We'll wait for you.");
					msg.append("\n 2) Press ENTER to skip the breakpoint and return control to the application this time.");
					msg.append("\n 3) Type \"L\" and press ENTER to ignore this particular break point (").append(source)
						.append(") for this session.");
					msg.append("\n 4) Type \"C\" and press ENTER to ignore all break points from the class that is requesting this break (")
						.append(source.getClassName()).append(") for this session.");
					msg.append("\n 5) Type \"A\" and press ENTER to ignore all break points for this session.");
					System.err.println(msg);

					reader = new AsyncInputReader();
				}
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {}

				String command = reader.getCommand();
				if (command != null) {
					if (command.length() == 0)
						break;
					ignore = CLI_IGNORE.get(command.toLowerCase());
					if (ignore == null) {
						System.err.println("Type \"L\", \"C\", \"A\", or just press ENTER");
						continue;
					}
					break;
				}
			} else
				breakpointCaught = true;
		} while(!breakpointCaught);

		if (reader != null)
			reader.close();
		if(ignore!=null){
			switch(ignore){
			case NONE:
				break;
			case LOCAL:
				System.out.println("Ignoring future breakpoints from " + source);
				IGNORING_LOCATIONS.add(source);
				break;
			case CLASS:
				System.out.println("Ignoring future breakpoints from class " + source.getClassName());
				IGNORING_CLASSES.add(source.getClassName());
				break;
			case ALL:
				System.out.println("Turning all breakpoints off");
				IGNORE_ALL = true;
				break;
			}
		}
		return breakpointCaught;
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
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		if (stack == null || stack.length < 2)
			return null;
		return stack[1];
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

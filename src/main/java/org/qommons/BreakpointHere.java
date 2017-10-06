package org.qommons;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

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
	static {
		Map<String, IgnoreType> cliIgnore = new LinkedHashMap<>();
		cliIgnore.put("l", IgnoreType.LOCAL);
		cliIgnore.put("c", IgnoreType.CLASS);
		cliIgnore.put("a", IgnoreType.ALL);
		CLI_IGNORE = Collections.unmodifiableMap(cliIgnore);
	}

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

	/** Invoked to transfer control to the debugger. Debugging environments should set a breakpoint on the indicated line in this method. */
	public static void breakpoint() {
		if(IGNORE_ALL)
			return;
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
				System.err.println("WARNING! Application is attempting to catch a breakpoint, but debugging seems to be disabled");
				return;
			}
			source = stack[2];
			if(IGNORING_CLASSES.contains(source.getClassName()))
				return;
			if(IGNORING_LOCATIONS.contains(source))
				return;
		}

		boolean breakpointCaught = false;
		boolean alerted = false;
		Scanner scanner = null;
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
					alerted = true;

					System.err.println("ATTENTION! " + source.getClassName() + " is attempting to catch a breakpoint at " + stackTop);
					System.err.println("No break point is set at this location. You may:");
					System.err.println(" 1) Install a breakpoint at " + stackTop + ". We'll wait for you.");
					System.err.println(" 2) Press ENTER to skip the breakpoint and return control to the application this time.");
					System.err.println(" 3) Type \"L\" and press ENTER to ignore this particular break point (" + source
						+ ") for this session.");
					System.err.println(" 4) Type \"C\" and press ENTER to ignore all break points"
						+ " from the class that is requesting this break (" + source.getClassName() + ") for this session.");
					System.err.println(" 5) Type \"A\" and press ENTER to ignore all break points for this session.");

					scanner = new Scanner(System.in);
				}

				int available;
				try {
					available = System.in.available();
				} catch(java.io.IOException e) {
					System.err.println("Could not read from System.in: " + e);
					available = 0;
				}

				if(available > 0) {
					String userEntry = scanner.nextLine().trim();
					if(userEntry.length()==0)
						break;
					ignore = CLI_IGNORE.get(userEntry.toLowerCase());
					if (ignore == null) {
						System.err.println("Type \"L\", \"C\", \"A\", or just press ENTER");
						continue;
					}
					break;
				}

				try {
					Thread.sleep(100);
				} catch(InterruptedException e) {}
			} else
				breakpointCaught = true;
		} while(!breakpointCaught);

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
}

package org.qommons.debug;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.function.Predicate;

import org.qommons.BreakpointHere;

/**
 * This is a class I created to ease debugging. It has several features that can be useful.
 * <ul>
 * <li>Variables. Static variables can be creates without the need to declare them via {@link #d() d()}.{@link #data()
 * data()}.{@link DebugData#set(String, Object) set()}. This is ideal for on-the-fly debugging.</li>
 * <li>Breakpoint utilities. Although the {@link BreakpointHere} class could be used the {@link DebugData#breakIf(Predicate)} and
 * {@link DebugData#breakOn(Predicate)} shortcuts make one-line breakpoints a cinch.</li>
 * </ul>
 * 
 * More features to be added later.
 */
public class Debug {
	private static final Debug singleton = new Debug();

	/** @return The singleton debug instance */
	public static Debug d() {
		return singleton;
	}

	// private final ThreadLocal<Debug> theThreads;
	private final DebugData theData;
	private final ConcurrentLinkedQueue<Runnable> theChecks;

	private Debug() {
		theChecks = new ConcurrentLinkedQueue<>();
		theData = new DebugData(this, "");
	}

	public DebugData data() {
		return theData;
	}

	public Runnable addCheck(Runnable check) {
		theChecks.add(check);
		return () -> theChecks.remove(check);
	}

	public Debug check() {
		for (Runnable check : theChecks)
			check.run();
		return this;
	}

	@Override
	public String toString() {
		return "Debug Singleton";
	}

	// public static class ThreadDebug

	public static class DebugData {
		private final String thePath;
		private final Map<Object, Object> theData;
		private final Collection<Predicate<? super DebugData>> theBreakpoints;
		private final Runnable theCheckRemove;

		private DebugData(Debug debug, String path) {
			thePath = path;
			theData = new LinkedHashMap<>();
			theBreakpoints = new ConcurrentLinkedQueue<>();
			theCheckRemove = debug.addCheck(() -> check());
		}

		public DebugData breakOn(Predicate<? super DebugData> breakpoint) {
			theBreakpoints.add(breakpoint);
			return this;
		}

		public DebugData breakIf(Predicate<? super DebugData> breakpoint) {
			if (breakpoint.test(this))
				BreakpointHere.breakpoint();
			return this;
		}

		public DebugData set(String path, Object value) {
			int slash = path.indexOf('/');
			if (slash < 0)
				theData.put(path, value);
			else {
				String newName = thePath + (thePath.length() > 0 ? "/" : "") + path.substring(0, slash);
				((DebugData) theData.computeIfAbsent(path.substring(0, slash), n -> new DebugData(d(), newName)))
					.set(path.substring(slash + 1),
					value);
			}
			return this;
		}

		public Object get(String path) {
			int slash = path.indexOf('/');
			if (slash < 0)
				return theData.get(path);
			else {
				DebugData data = (DebugData) theData.get(path.substring(0, slash));
				if (data == null)
					return null;
				return data.get(path.substring(slash + 1));
			}
		}

		public <T> T update(String path, Function<? super T, ? extends T> update, T init) {
			int slash = path.indexOf('/');
			if (slash < 0) {
				T value = (T) theData.get(path);
				if (value == null)
					value = init;
				value = update.apply(value);
				theData.put(path, value);
				return value;
			} else {
				DebugData data = (DebugData) theData.get(path.substring(0, slash));
				if (data == null)
					return null;
				return data.update(path.substring(slash + 1), update, init);
			}
		}

		private void check() {
			for (Predicate<? super DebugData> bp : theBreakpoints)
				if (bp.test(this))
					BreakpointHere.breakpoint();
		}

		private void remove() {
			theCheckRemove.run();
		}

		@Override
		public String toString() {
			return "Debug Data" + (thePath.length() > 0 ? "@" + thePath : "");
		}
	}
}

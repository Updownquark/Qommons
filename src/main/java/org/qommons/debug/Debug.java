package org.qommons.debug;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.qommons.BreakpointHere;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;

/**
 * This is a class I created to ease debugging. It has several features that can be useful.
 * <ul>
 * <li>Variables. Static variables can be creates without the need to declare them via {@link #d() d()}.{@link #data()
 * data()}.{@link DebugData#setReference(String, Object) set()}. This is ideal for on-the-fly debugging.</li>
 * <li>Breakpoint utilities. Although the {@link BreakpointHere} class could be used the {@link DebugData#breakIf(Predicate)} and
 * {@link DebugData#breakOn(Predicate)} shortcuts make one-line breakpoints a cinch.</li>
 * </ul>
 * 
 * More features to be added later.
 */
public class Debug {
	private static final char DIV = '.';
	private static final Debug singleton = new Debug(false);
	private static final DebugData INACTIVE = new DebugData(null);

	/** @return The singleton debug instance */
	public static Debug d() {
		return singleton;
	}

	// private final ThreadLocal<Debug> theThreads;
	private final ListenerList<Runnable> theChecks;
	private final Map<String, DebugData> theData;
	private final Map<Object, DebugData> theDataByValue;
	private final ReentrantLock theLock;
	private AtomicLong isActive;

	private Debug(boolean active) {
		theChecks = new ListenerList<>(null);
		theData = new TreeMap<>();
		theDataByValue = new IdentityHashMap<>();
		theLock = new ReentrantLock();
		isActive = new AtomicLong();
	}

	public boolean isActive() {
		return isActive.get() > 0;
	}

	public Debug start() {
		isActive.getAndIncrement();
		return this;
	}

	public Debug end() {
		isActive.getAndDecrement();
		return this;
	}

	public DebugData get(String path) {
		return get(path, false);
	}

	public DebugData get(String path, boolean create) {
		return with(path, create, d -> {});
	}

	public DebugData set(String path, Object value) {
		if (!isActive())
			return INACTIVE;
		try (Transaction t = lock(true)) {
			DebugData d = _debug(value, true);
			int lastDivider = path.lastIndexOf(DIV);
			if (lastDivider < 0) {
				d.addRootName(path);
				theData.compute(path, (p, oldD) -> {
					if (oldD != null)
						d.merge(oldD);
					return d;
				});
			} else {
				String frontPath = path.substring(0, lastDivider);
				String fieldName = path.substring(lastDivider + 1);
				if (frontPath.isEmpty() || fieldName.isEmpty())
					throw new IllegalArgumentException("Empty path or empty path elements not allowed: " + path);
				_with(frontPath, true, fd -> fd._setReference(fieldName, value));
			}
			return d;
		}
	}

	public DebugData with(String path, boolean create, Consumer<DebugData> action) {
		try (Transaction t = lock(true)) {
			return _with(path, create, action);
		}
	}

	private DebugData _with(String path, boolean create, Consumer<DebugData> action) {
		String rootName, subPath;
		int divider = path.indexOf(DIV);
		if (divider >= 0) {
			rootName = path.substring(0, divider);
			subPath = path.substring(divider + 1);
		} else {
			rootName = path;
			subPath = null;
		}
		if (rootName.isEmpty() || (subPath != null && subPath.isEmpty()))
			throw new IllegalArgumentException("Empty path or empty path elements not allowed: " + path);
		DebugData d;
		if (create && isActive())
			d = theData.computeIfAbsent(rootName, n -> new DebugData(this).addRootName(rootName));
		else
			d = theData.get(rootName);
		if (create && d == null)
			return INACTIVE;
		if (d != null && subPath != null) {
			d = d.with(subPath, create, action);
		}
		if (d != null && subPath == null && action != null)
			action.accept(d);
		return d;
	}

	public DebugData debug(Object value) {
		return debug(value, false);
	}

	public DebugData debug(Object value, boolean create) {
		try (Transaction t = lock(create)) {
			return debug(value, create, d -> {});
		}
	}

	private DebugData _debug(Object value, boolean create) {
		DebugData d;
		if (create && isActive())
			d = theDataByValue.computeIfAbsent(value, n -> new DebugData(this).setValue(value));
		else
			d = theDataByValue.get(value);
		if (create && d == null)
			return INACTIVE;
		return d;
	}

	public DebugData debug(Object value, boolean create, Consumer<DebugData> action) {
		try (Transaction t = lock(true)) {
			DebugData d = _debug(value, create);
			if (d != null)
				action.accept(d);
			return d;
		}
	}

	private Transaction lock(boolean write) {
		theLock.lock();
		return () -> theLock.unlock();
	}

	public DebugData remove(String name) {
		try (Transaction t = lock(true)) {
			DebugData data = theData.remove(name);
			if (data != null)
				data.remove();
			return data;
		}
	}

	public Runnable addCheck(Runnable check) {
		if (isActive())
			return theChecks.add(check, false);
		else
			return () -> {};
	}

	public Debug check() {
		if (isActive())
			theChecks.forEach(Runnable::run);
		return this;
	}

	@Override
	public String toString() {
		return "Debug Singleton";
	}

	// public static class ThreadDebug

	public static class DebugData {
		private final Debug theDebug;
		private Object theValue;
		private final NavigableSet<String> theRootNames;
		private final Map<String, DebugData> theRefs;
		private final Map<String, Set<DebugData>> theReverseRefs;
		private final Map<String, Object> theFields;
		private final ListenerList<Predicate<? super DebugData>> theBreakpoints;
		private final Runnable theCheckRemove;

		private DebugData(Debug debug) {
			theDebug = debug;
			if (debug != null) {
				theRootNames = new TreeSet<>();
				theRefs = new TreeMap<>();
				theReverseRefs = new TreeMap<>();
				theFields = new TreeMap<>();
				theBreakpoints = new ListenerList<>(null);
				theCheckRemove = debug.addCheck(() -> check());
			} else {
				theRootNames = null;
				theRefs = null;
				theReverseRefs = null;
				theFields = null;
				theBreakpoints = null;
				theCheckRemove = null;
			}
		}

		private void merge(DebugData other) {
			other.theCheckRemove.run();
			theRootNames.addAll(other.theRootNames);
			theRefs.putAll(other.theRefs);
			for (Map.Entry<String, Set<DebugData>> rf : other.theReverseRefs.entrySet()) {
				theReverseRefs.compute(rf.getKey(), (f, d) -> {
					if (d == null)
						d = rf.getValue();
					else
						d.addAll(rf.getValue());
					return d;
				});
			}
			other.theBreakpoints.forEach(bp -> theBreakpoints.add(bp, true));
		}

		private DebugData addRootName(String name) {
			if (theDebug == null)
				return this;
			theRootNames.add(name);
			return this;
		}

		public NavigableSet<String> getNames() {
			return Collections.unmodifiableNavigableSet(theRootNames);
		}

		public Object getValue() {
			return theValue;
		}

		private DebugData setValue(Object value) {
			theValue = value;
			return this;
		}

		public DebugData breakOn(Predicate<? super DebugData> breakpoint) {
			if (theDebug != null)
				theBreakpoints.add(breakpoint, false);
			return this;
		}

		public DebugData breakIf(Predicate<? super DebugData> breakpoint) {
			if (theDebug != null && breakpoint.test(this))
				BreakpointHere.breakpoint();
			return this;
		}

		public DebugData get(String path) {
			return get(path, false);
		}

		public DebugData get(String path, boolean create) {
			return with(path, create, d -> {});
		}

		public DebugData with(String path, boolean create, Consumer<DebugData> action) {
			try (Transaction t = theDebug.lock(true)) {
				return _with(path, create, action);
			}
		}

		public DebugData setReference(String fieldPath, Object value) {
			if (theDebug == null)
				return this;
			try (Transaction t = theDebug.lock(true)) {
				return _setReference(fieldPath, value);
			}
		}

		public Object getField(String fieldPath) {
			if (theDebug == null)
				return null;
			int divider = fieldPath.indexOf(DIV);
			String fieldName, subPath;
			if (divider >= 0) {
				fieldName = fieldPath.substring(0, divider);
				subPath = fieldPath.substring(divider + 1);
			} else {
				fieldName = fieldPath;
				subPath = null;
			}
			if (fieldName.isEmpty() || (subPath != null && subPath.isEmpty()))
				throw new IllegalArgumentException("Empty path or empty path elements not allowed: " + fieldPath);
			try (Transaction t = theDebug.lock(false)) {
				if (subPath == null)
					return theFields.get(fieldName);
				else {
					DebugData data = theRefs.get(fieldName);
					if (data == null)
						return null;
					return data.getField(subPath);
				}
			}
		}

		public DebugData setField(String fieldPath, Object value) {
			if (theDebug == null)
				return this;
			int lastDivider = fieldPath.lastIndexOf(DIV);
			String refPath, fieldName;
			if (lastDivider >= 0) {
				refPath = fieldPath.substring(0, lastDivider);
				fieldName = fieldPath.substring(lastDivider + 1);
			} else {
				refPath = fieldPath;
				fieldName = null;
			}
			if (refPath.isEmpty() || (fieldName != null && fieldName.isEmpty()))
				throw new IllegalArgumentException("Empty path or empty path elements not allowed: " + fieldPath);
			try (Transaction t = theDebug.lock(true)) {
				if (fieldName == null)
					theFields.put(fieldPath, value);
				else {
					_with(refPath, true, d -> {}).setField(fieldName, value);
				}
			}
			return this;
		}

		public <T> T updateField(String fieldPath, Function<? super T, ? extends T> update, T init) {
			if (theDebug == null)
				return null;
			int divider = fieldPath.indexOf(DIV);
			String fieldName, subPath;
			if (divider >= 0) {
				fieldName = fieldPath.substring(0, divider);
				subPath = fieldPath.substring(divider + 1);
			} else {
				fieldName = fieldPath;
				subPath = null;
			}
			if (fieldName.isEmpty() || (subPath != null && subPath.isEmpty()))
				throw new IllegalArgumentException("Empty path or empty path elements not allowed: " + fieldPath);
			try (Transaction t = theDebug.lock(true)) {
				if (subPath == null) {
					T value = (T) theFields.get(fieldPath);
					if (value == null)
						value = init;
					value = update.apply(value);
					theFields.put(fieldPath, value);
					return value;
				} else {
					DebugData data = theRefs.get(fieldName);
					if (data == null)
						return null;
					return data.updateField(subPath, update, init);
				}
			}
		}

		private DebugData _with(String path, boolean create, Consumer<DebugData> action) {
			if (theDebug == null)
				return create ? this : null;
			int divider = path.indexOf(DIV);
			String fieldName, subPath;
			if (divider >= 0) {
				fieldName = path.substring(0, divider);
				subPath = path.substring(divider + 1);
			} else {
				fieldName = path;
				subPath = null;
			}
			if (fieldName.isEmpty() || (subPath != null && subPath.isEmpty()))
				throw new IllegalArgumentException("Empty path or empty path elements not allowed: " + path);
			DebugData d;
			if (create)
				d = theRefs.computeIfAbsent(fieldName, n -> new DebugData(theDebug).addReverseRef(fieldName, this));
			else
				d = theRefs.get(fieldName);
			if (d != null && subPath != null) {
				d = d.with(subPath, create, action);
			}
			if (d != null && subPath == null && action != null)
				action.accept(d);
			return d;
		}

		private DebugData _setReference(String fieldPath, Object value) {
			if (theDebug == null)
				return this;
			int divider = fieldPath.indexOf(DIV);
			String fieldName, subPath;
			if (divider >= 0) {
				fieldName = fieldPath.substring(0, divider);
				subPath = fieldPath.substring(divider + 1);
			} else {
				fieldName = fieldPath;
				subPath = null;
			}
			if (fieldName.isEmpty() || (subPath != null && subPath.isEmpty()))
				throw new IllegalArgumentException("Empty path or empty path elements not allowed: " + fieldPath);
			if (subPath != null)
				theRefs.computeIfAbsent(fieldName, f -> {
					if (value == null)
						return null;
					else
						return new DebugData(theDebug).addReverseRef(fieldName, this)._setReference(subPath, value);
				});
			else {
				DebugData fieldDebug = value == null ? null : theDebug._debug(value, true);
				theRefs.compute(fieldName, (f, oldD) -> {
					if (oldD != null) {
						if (fieldDebug == null)
							oldD.removeReverseRef(fieldName, this);
						else if (oldD != fieldDebug)
							fieldDebug.merge(oldD);
					} else
						fieldDebug.addReverseRef(fieldName, this);
					return fieldDebug;
				});
			}
			return this;
		}

		private DebugData addReverseRef(String fieldName, DebugData source) {
			theReverseRefs.computeIfAbsent(fieldName, f -> new LinkedHashSet<>()).add(source);
			return this;
		}

		private void removeReverseRef(String fieldName, DebugData source) {
			theReverseRefs.compute(fieldName, (f, srcs) -> {
				if (srcs == null)
					return srcs;
				srcs.remove(source);
				return srcs.isEmpty() ? null : srcs;
			});
		}

		private void check() {
			theBreakpoints.forEach(//
				bp -> {
					if (bp.test(this))
						BreakpointHere.breakpoint();
				});
		}

		private void remove() {
			theCheckRemove.run();
			if (!theRootNames.isEmpty())
				theDebug.theData.keySet().removeAll(theRootNames);
			if (theValue != null)
				theDebug.theDataByValue.remove(theValue);
			for (Map.Entry<String, DebugData> field : theRefs.entrySet())
				field.getValue().theReverseRefs.remove(field.getKey());
			for (Map.Entry<String, Set<DebugData>> field : theReverseRefs.entrySet())
				for (DebugData d : field.getValue())
					d.theRefs.remove(field.getKey());
		}

		public String printInfo(boolean deep) {
			if (theDebug == null)
				return "inactive debug";
			StringBuilder str = new StringBuilder();
			try (Transaction t = theDebug.lock(false)) {
				Set<DebugData> visited = new HashSet<>();
				_printInfo(str, 0, deep, visited);
			}
			return str.toString();
		}

		private void _printInfo(StringBuilder str, int indent, boolean deep, Set<DebugData> visited) {
			_getIdentity(str);
			if (theValue != null)
				str.append(": " + theValue);
			printIndent(str.append('\n'), indent + 1).append("refs: ");
			_printReferences(str, indent + 1, deep, visited);
			printIndent(str.append('\n'), indent + 1).append("reverse-refs: ");
			_printReverseReferences(str, indent + 1, deep, visited);
		}

		public String getIdentity() {
			if (theDebug == null)
				return "inactive debug";
			StringBuilder str = new StringBuilder();
			try (Transaction t = theDebug.lock(false)) {
				_getIdentity(str);
			}
			return str.toString();
		}

		private StringBuilder _getIdentity(StringBuilder str) {
			if (theRootNames.isEmpty()) {
				if (!theReverseRefs.isEmpty()) {
					Map.Entry<String, Set<DebugData>> rr = theReverseRefs.entrySet().iterator().next();
					rr.getValue().iterator().next()._getIdentity(str).append(DIV).append(rr.getKey());
				} else if (theValue != null)
					str.append("System ID ").append(Integer.toHexString(System.identityHashCode(theValue)).toUpperCase());
				else
					str.append("null");
			} else if (theRootNames.size() == 1)
				str.append(theRootNames.iterator().next());
			else
				str.append(theRootNames);
			return str;
		}

		public String printReferences() {
			if (theDebug == null)
				return "{}";
			StringBuilder str = new StringBuilder().append('{');
			try (Transaction t = theDebug.lock(false)) {
				_printReferences(str, 1, false, null);
			}
			return str.toString();
		}

		private void _printReferences(StringBuilder str, int indent, boolean deep, Set<DebugData> visited) {
			indent++;
			str.append('{');
			for (Map.Entry<String, DebugData> ref : theRefs.entrySet()) {
				str.append('\n');
				printIndent(str, indent).append(ref.getKey()).append("= ");
				if (visited != null && !visited.add(ref.getValue()))
					_getIdentity(str);
				else if (deep)
					ref.getValue()._printInfo(str, indent, deep, visited);
				else {
					ref.getValue()._getIdentity(str);
					if (ref.getValue().getValue() != null)
						str.append(": ").append(ref.getValue().getValue());
				}
				str.append(',');
			}
			if (!theRefs.isEmpty()) {
				str.deleteCharAt(str.length() - 1).append('\n');
				printIndent(str, indent);
			}
			str.append('}');
		}

		public String printReverseReferences() {
			if (theDebug == null)
				return "{}";
			StringBuilder str = new StringBuilder().append('{');
			try (Transaction t = theDebug.lock(false)) {
				_printReverseReferences(str, 1, false, null);
			}
			return str.toString();
		}

		private void _printReverseReferences(StringBuilder str, int indent, boolean deep, Set<DebugData> visited) {
			str.append('{');
			for (Map.Entry<String, Set<DebugData>> ref : theReverseRefs.entrySet()) {
				if (!deep) {
					for (DebugData d : ref.getValue()) {
						str.append('\n');
						d._getIdentity(printIndent(str, indent + 1)).append(DIV).append(ref.getKey());
						str.append(',');
					}
				} else {
					str.append('\n');
					printIndent(str, indent + 1).append(ref.getKey()).append(": ");
					for (DebugData d : ref.getValue()) {
						if (visited != null && !visited.add(d))
							_getIdentity(str);
						else
							d._printInfo(str, indent + 1, deep, visited);
						str.append(',');
					}
				}
			}
			if (!theReverseRefs.isEmpty()) {
				str.deleteCharAt(str.length() - 1).append('\n');
				printIndent(str, indent);
			}
			str.append('}');
		}

		private StringBuilder printIndent(StringBuilder str, int indent) {
			for (int i = 0; i < indent; i++)
				str.append("   ");
			return str;
		}

		@Override
		public String toString() {
			return getIdentity();
		}
	}
}

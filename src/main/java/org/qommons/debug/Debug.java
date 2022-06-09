package org.qommons.debug;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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
import java.util.function.Supplier;

import org.qommons.BreakpointHere;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;

/**
 * <p>
 * This is a class I created to ease debugging. Production code can be set up to query this class and print out debugging information or
 * call breakpoints when needed. If called correctly these features will be virtually free in production or when debugging is inactive.
 * <p>
 * <p>
 * It has several features that can be useful.
 * </p>
 * <ul>
 * <li>Variables. Static variables can be created without the need to declare them via {@link #d() d()}.{@link #set(String, Object)}.</li>
 * <li>Debug data structures. {@link #d() d()}.{@link #debug(Object, boolean) debug(Object, true)} can create a {@link DebugData} object
 * that can store its own field data, perform actions based on that data, and power debugging code that only runs when the {@link Debug}
 * architecture is {@link #isActive() active} and a particular object is flagged for debugging.</li>
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

	/** @return An inactive debug data */
	public static DebugData inactive() {
		return INACTIVE;
	}

	private final ThreadLocal<DebugData> theThreads;
	private final ListenerList<Runnable> theChecks;
	private final ListenerList<Consumer<DebugData>> theWatchers;
	private final Map<String, DebugData> theData;
	private final Map<Object, DebugData> theDataByValue;
	private final ReentrantLock theLock;
	private AtomicLong isActive;

	private Debug(boolean active) {
		theThreads = new ThreadLocal<DebugData>() {
			@Override
			protected DebugData initialValue() {
				Thread thread = Thread.currentThread();
				return debug(thread, true);
			}
		};
		theChecks = ListenerList.build().allowReentrant().build();
		theWatchers = ListenerList.build().allowReentrant().build();
		theData = new TreeMap<>();
		theDataByValue = new IdentityHashMap<>();
		theLock = new ReentrantLock();
		isActive = new AtomicLong();
	}

	/** @return Whether the debug architecture is currently active */
	public boolean isActive() {
		return isActive.get() > 0;
	}

	/**
	 * Activates debugging
	 * 
	 * @return This debug instance
	 */
	public Debug start() {
		isActive.getAndIncrement();
		return this;
	}

	/**
	 * Deactivates debugging, unless it has also been activated elsewhere
	 * 
	 * @return This debug instance
	 */
	public Debug end() {
		isActive.getAndDecrement();
		return this;
	}

	/**
	 * Gets the current data associated with a path
	 * 
	 * @param path The path of the debug data to get
	 * @return The data at the given path, or null if data is not set for the path
	 */
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
						d._merge(oldD);
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
		if (!isActive())
			return INACTIVE;
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
			d = theData.computeIfAbsent(rootName, n -> created(new DebugData(this).addRootName(rootName)));
		else
			d = theData.get(rootName);
		if (d == null)
			return INACTIVE;
		if (d != null && subPath != null) {
			d = d.with(subPath, create, action);
		}
		if (d != null && subPath == null && action != null)
			action.accept(d);
		return d;
	}

	public DebugData threadDebug() {
		return theThreads.get();
	}

	public DebugData debug(Object value) {
		return debug(value, false);
	}

	public DebugData debug(Object value, boolean create) {
		if (!isActive())
			return INACTIVE;
		try (Transaction t = lock(create)) {
			return debug(value, create, d -> {});
		}
	}

	private DebugData _debug(Object value, boolean create) {
		DebugData d;
		if (create && isActive())
			d = theDataByValue.computeIfAbsent(value, n -> created(new DebugData(this).setValue(value)));
		else
			d = theDataByValue.get(value);
		if (d == null)
			return INACTIVE;
		return d;
	}

	public DebugData debug(Object value, boolean create, Consumer<DebugData> action) {
		try (Transaction t = lock(true)) {
			DebugData d = _debug(value, create);
			if (d.theDebug != null)
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

	public Debug watchFor(Consumer<DebugData> watcher) {
		theWatchers.add(watcher, true);
		return this;
	}

	private DebugData created(DebugData data) {
		theWatchers.forEach(//
			w -> w.accept(data));
		return data;
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

	public void debugIf(boolean condition) {
		if (condition)
			BreakpointHere.breakpoint();
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
		private final ListenerList<Consumer<? super DebugData>> theChecks;
		private final ListenerList<Consumer<? super DebugAction>> theActionListeners;
		private final Runnable theCheckRemove;

		private DebugData(Debug debug) {
			theDebug = debug;
			if (debug != null) {
				theRootNames = new TreeSet<>();
				theRefs = new TreeMap<>();
				theReverseRefs = new TreeMap<>();
				theFields = new TreeMap<>();
				theChecks = ListenerList.build().allowReentrant().build();
				theActionListeners = ListenerList.build().allowReentrant().build();
				theCheckRemove = debug.addCheck(() -> check());
			} else {
				theRootNames = null;
				theRefs = null;
				theReverseRefs = null;
				theFields = null;
				theChecks = null;
				theActionListeners = null;
				theCheckRemove = null;
			}
		}

		public DebugData merge(DebugData other) {
			if (theDebug == null || other.theDebug == null)
				return this;
			// I don't remember why _merge doesn't merge fields, so I'm leaving it as it is and making this public method do it
			_merge(other);
			theValue = other.theValue;
			theFields.putAll(other.theFields);
			return this;
		}

		private void _merge(DebugData other) {
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
			other.theChecks.forEach(bp -> theChecks.add(bp, true));
			other.theActionListeners.forEach(bp -> theActionListeners.add(bp, true));
		}

		private DebugData addRootName(String name) {
			if (theDebug == null)
				return this;
			theRootNames.add(name);
			return this;
		}

		public boolean isActive() {
			return theDebug != null;
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

		public DebugData debugIf(boolean condition) {
			if (condition)
				return this;
			else
				return INACTIVE;
		}

		public DebugData debugIf(Predicate<DebugData> condition) {
			if (condition.test(this))
				return this;
			else
				return INACTIVE;
		}

		public DebugData addCheck(Consumer<? super DebugData> check) {
			if (theDebug != null)
				theChecks.add(check, true);
			return this;
		}

		public DebugData breakpoint() {
			if (theDebug != null)
				BreakpointHere.breakpoint();
			return this;
		}

		public DebugData print(Supplier<CharSequence> str) {
			if (theDebug != null)
				System.out.println(str.get().toString());
			return this;
		}

		public DebugData get(String path) {
			return get(path, false);
		}

		public DebugData get(String path, boolean create) {
			return with(path, create, d -> {});
		}

		public DebugData add(String path) {
			if (theDebug == null)
				return this;
			try (Transaction t = theDebug.lock(true)) {
				StringBuilder indexed = new StringBuilder(path).append('[');
				for (int i = 0; true; i++) {
					int preLength = indexed.length();
					indexed.append(i).append(']');
					String indexedStr = indexed.toString();
					DebugData data = get(indexedStr);
					if (data == null) {
						return get(indexedStr, true);
					}
					indexed.setLength(preLength);
				}
			}
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

		public <T> T getField(String fieldPath, Class<T> type) {
			return type.cast(getField(fieldPath));
		}

		public boolean is(String fieldPath) {
			Object fieldValue = getField(fieldPath);
			if (fieldValue == null)
				return false;
			else if (fieldValue instanceof Boolean)
				return ((Boolean) fieldValue).booleanValue();
			else
				return true;
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

		public DebugAction act(String action) {
			return new DebugAction(this, action, theDebug != null);
		}

		public DebugAction actWithMinInterval(String action, long interval) {
			boolean active;
			if (theDebug != null) {
				long now = System.currentTimeMillis();
				active = updateField(action, old -> (old.longValue() + interval) <= now ? now : old, now).longValue() == now;
			} else
				active = false;
			return new DebugAction(this, action, active);
		}

		DebugData execAction(DebugAction action) {
			theActionListeners.forEach(//
				listener -> listener.accept(action));
			return this;
		}

		public DebugData onAction(Consumer<DebugAction> onAction) {
			if (theActionListeners != null)
				theActionListeners.add(onAction, true);
			return this;
		}

		public DebugData doNow(Consumer<DebugData> action) {
			if (theDebug != null)
				action.accept(this);
			return this;
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
				d = theRefs.computeIfAbsent(fieldName, n -> theDebug.created(new DebugData(theDebug).addReverseRef(fieldName, this)));
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
						return theDebug.created(new DebugData(theDebug).addReverseRef(fieldName, this)._setReference(subPath, value));
				});
			else {
				DebugData fieldDebug = value == null ? null : theDebug._debug(value, true);
				theRefs.compute(fieldName, (f, oldD) -> {
					if (oldD != null) {
						if (fieldDebug == null)
							oldD.removeReverseRef(fieldName, this);
						else if (oldD != fieldDebug)
							fieldDebug._merge(oldD);
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
			theChecks.forEach(//
				bp -> bp.accept(this));
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

	public static class DebugAction {
		private final DebugData theData;
		private final String theName;
		private final boolean isActive;
		private final Map<String, Object> theParameters;
		private boolean isExecuted;

		DebugAction(DebugData data, String name, boolean active) {
			theData = data;
			theName = name;
			isActive = active;
			theParameters = active ? new LinkedHashMap<>() : Collections.emptyMap();
		}

		public DebugData getData() {
			return theData;
		}

		public String getName() {
			return theName;
		}

		public Map<String, Object> getParameters() {
			return Collections.unmodifiableMap(theParameters);
		}

		public DebugAction param(String paramName, Object paramValue) {
			if (isExecuted)
				throw new IllegalStateException("A debug action cannot be modified after it has begun executing");
			if (isActive)
				theParameters.put(paramName, paramValue);
			return this;
		}

		public DebugAction param(String paramName, Supplier<?> paramGetter) {
			if (isExecuted)
				throw new IllegalStateException("A debug action cannot be modified after it has begun executing");
			if (isActive)
				theParameters.put(paramName, paramGetter.get());
			return this;
		}

		public DebugData exec() {
			if (!isActive)
				return theData;
			isExecuted = true;
			return theData.execAction(this);
		}

		@Override
		public String toString() {
			if (!isActive)
				return "null()";
			StringBuilder str = new StringBuilder(theName).append('(');
			boolean first = true;
			for (Map.Entry<String, Object> param : theParameters.entrySet()) {
				if (!first)
					str.append(", ");
				first = false;
				str.append(param.getKey()).append('=').append(param.getValue());
			}
			str.append(')');
			return str.toString();
		}
	}
}

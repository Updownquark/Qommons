/* ProgramTracker.java Created Dec 10, 2008 by Andrew Butler, PSL */
package org.qommons;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.qommons.json.JsonObject;
import org.qommons.threading.QommonsTimer;

/** A simple utility to help in finding performance bottlenecks in a program */
public class ProgramTracker implements Cloneable {
	private static final String DEFAULT_INDENT_INCREMENT = "   ";

	static final SimpleDateFormat [] [] formats;

	static {
		String [] patterns = new String[] {"ddMMM HH:mm:ss.SSS", "dd HH:mm:ss.SSS", "HH:mm:ss.SSS", "mm:ss.SSS", "ss.SSS"};

		formats = new SimpleDateFormat[2][patterns.length];
		for(int p = 0; p < patterns.length; p++) {
			formats[0][p] = new SimpleDateFormat(patterns[p] + "'Z'");
			formats[0][p].setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
			formats[1][p] = new SimpleDateFormat(patterns[p]);
		}
	}

	/** The format used to print percentages */
	public static final java.text.NumberFormat PERCENT_FORMAT = new java.text.DecimalFormat("0.0");

	/** The format used to print time lengths */
	public static final java.text.NumberFormat LENGTH_FORMAT = new java.text.DecimalFormat("0.000");

	/** The format used to print length statistics */
	public static final java.text.NumberFormat NANO_FORMAT = new java.text.DecimalFormat("0.00E0");

	/**
	 * This singleton variable is to be used for <b>temporary</b> debugging purposes only. It allows for easier profiling of applications
	 * without extensive code changes to access the correct tracker. However, if this variable is used in more than one place, it may lead
	 * to unpredictable results and thrown exceptions. A different mechanism MUST be developed to access a tracker if profiling is to be
	 * integrated into the application permanently.
	 * 
	 * This variable is not initialized by this class, but may be set by other classes.
	 */
	public static ProgramTracker instance;

	private static boolean THREAD_TRACKING_ACTIVE = "true".equalsIgnoreCase(System.getProperty("qommons.thread.tracking"));
	private static final Map<Thread, ProgramTracker> theThreadTrackers;
	private static ProgramTracker ignoring;

	static {
		theThreadTrackers = new ConcurrentHashMap<>();
		ignoring = new ProgramTracker("ignoring") {
			{
				setOn(false);
			}

			@Override
			public ProgramTracker setOn(boolean on) {
				return super.setOn(false);
			}
		};
	}

	/**
	 * Begins thread tracking, such that {@link #getThreadTracker()} will always return an active tracker, initializing new ones as needed.
	 * 
	 * @return A transaction that, when called, will print the tracking results, clear all thread trackers, and revert thread tracking to
	 *         false.
	 */
	public static Transaction startThreadTracking() {
		THREAD_TRACKING_ACTIVE = true;
		return () -> {
			THREAD_TRACKING_ACTIVE = false;
			printThreadTrackers(true);
		};
	}

	/**
	 * @param threadTracking Whether thread tracking should be active, such that {@link #getThreadTracker()} will always return an active
	 *        tracker, initializing new ones as needed.
	 */
	public static void setThreadTracking(boolean threadTracking) {
		THREAD_TRACKING_ACTIVE = threadTracking;
	}

	/**
	 * Sets a tracker as the tracker for the current thread
	 *
	 * @param tracker The tracker to set as a tracker thread
	 */
	public static void setThreadTracker(ProgramTracker tracker) {
		theThreadTrackers.put(Thread.currentThread(), tracker);
	}

	/** @return The tracker for the current thread, initialized if none has been previously set */
	public static ProgramTracker getThreadTracker() {
		if (!THREAD_TRACKING_ACTIVE)
			return ignoring;
		return theThreadTrackers.computeIfAbsent(Thread.currentThread(), thread -> new ProgramTracker(thread.getName()));
	}

	/**
	 * @param thread The thread to get the tracker for
	 * @return The tracker assigned to the given thread, or null if none has been initialized
	 */
	public static ProgramTracker getThreadTracker(Thread thread) {
		return theThreadTrackers.get(thread);
	}

	/** @return All threads for which {@link #setThreadTracker(ProgramTracker)} or {@link #getThreadTracker()} has been called */
	public static Thread [] getTrackedThreads() {
		return theThreadTrackers.keySet().toArray(new Thread[0]);
	}

	/**
	 * @param routine The routine to start on the tracker for this thread. Use {@link #setThreadTracker(ProgramTracker)} to initialize the
	 *        tracker.
	 * @return The track node for the routine
	 */
	public static TrackNode threadStart(String routine) {
		return getThreadTracker().start(routine);
	}

	/**
	 * Prints the data from all thread trackers and, if specified, removes them
	 * 
	 * @param andClear Whether to remove the thread trackers
	 */
	public static void printThreadTrackers(boolean andClear) {
		Iterator<ProgramTracker> trackerIter = theThreadTrackers.values().iterator();
		while (trackerIter.hasNext()) {
			ProgramTracker tracker = trackerIter.next();
			if (andClear)
				trackerIter.remove();
			tracker.printData();
		}
	}

	/**
	 * Starts a listener that, after this method is not called for a certain period of time, prints the data from all thread trackers and,
	 * if specified, removes them
	 * 
	 * @param andClear Whether to remove the thread trackers
	 * @param inactiveTime The amount of time to wait before printing the data
	 */
	public static void printThreadTrackersAfterInactive(boolean andClear, long inactiveTime) {
		QommonsTimer.getCommonInstance().doAfterInactivity("ProgramTracker.printAfterInactive:" + andClear, () -> {
			printThreadTrackers(andClear);
		}, Duration.ofMillis(inactiveTime));
	}

	/**
	 * @param tracker The tracker to start the routine for. May be null, in which case a do-nothing placeholder will be returned.
	 * @param routine The routine to start
	 * @return The track node for the routine
	 */
	public static TrackNode start(ProgramTracker tracker, String routine) {
		if (tracker == null)
			tracker = ignoring;
		return tracker.start(routine);
	}

	/**
	 * @param routine The routine to start on this class' singleton tracker. The tracker must be initialized prior to this call to be
	 *        effective.
	 * @return The track node for the routine
	 */
	public static TrackNode singletonStart(String routine) {
		return start(instance, routine);
	}

	/** A configuration class that allows the printing of results of a tracking session to be customized */
	public static class PrintConfig implements Cloneable {
		private float theDisplayThreshold;
		private float theHighlightThreshold;
		private boolean isAsync;
		private String theIndent;
		private String theInitialIndent;
		private boolean isWithIntro;

		/** Creates a print config */
		public PrintConfig() {
			theDisplayThreshold = 0;
			theHighlightThreshold = 0;
			isAsync = false;
			theIndent = DEFAULT_INDENT_INCREMENT;
			theInitialIndent = "";
			isWithIntro = true;
		}

		/** @return The threshold percent below which a task will not be printed in the result */
		public float getDisplayThreshold() {
			return theDisplayThreshold;
		}

		/**
		 * @param displayThreshold The threshold percent below which a task will not be printed in the result
		 * @return This print config
		 */
		public PrintConfig setDisplayThreshold(float displayThreshold) {
			theDisplayThreshold = displayThreshold;
			return this;
		}

		/** @return The threshold percent above which a task will be highlighted in the result */
		public float getHighlightThreshold() {
			return theHighlightThreshold;
		}

		/**
		 * @param thresh The threshold percent above which a task will be highlighted in the result
		 * @return This print config
		 */
		public PrintConfig setHighlightThreshold(float thresh) {
			theHighlightThreshold = thresh;
			return this;
		}

		/** @return Whether the printing is being done concurrently with the tracker's run */
		public boolean isAsync() {
			return isAsync;
		}

		/**
		 * @param async Whether the printing is being done concurrently with the tracker's run
		 * @return This print config
		 */
		public PrintConfig setAsync(boolean async) {
			isAsync = async;
			return this;
		}

		/** @return The string to indent nested tasks with */
		public String getIndent() {
			return theIndent;
		}

		/**
		 * @param indent The string to indent nested tasks with
		 * @return This print config
		 */
		public PrintConfig setIndent(String indent) {
			theIndent = indent;
			return this;
		}

		/** @return The number of spaces to start the indentation of with */
		public String getInitialIndent() {
			return theInitialIndent;
		}

		/**
		 * @param indent The number of spaces to start the indentation of with
		 * @return This print config
		 */
		public PrintConfig setInitialIndent(String indent) {
			theInitialIndent = indent;
			return this;
		}

		/** @return Whether program trackers printed with this config will also print a description of the tracker */
		public boolean isWithIntro() {
			return isWithIntro;
		}

		/**
		 * @param wi Whether program trackers printed with this config should also print a description of the tracker
		 * @return This print config
		 */
		public PrintConfig setWithIntro(boolean wi) {
			isWithIntro = wi;
			return this;
		}

		@Override
		public PrintConfig clone() {
			PrintConfig ret;
			try {
				ret = (PrintConfig) super.clone();
			} catch(CloneNotSupportedException e) {
				throw new IllegalStateException(e.getMessage(), e);
			}
			return ret;
		}
	}

	/** A node representing a single execution or an aggregate of executions of a routine */
	public class TrackNode implements Cloneable, AutoCloseable {
		/** The name of the routine */
		String name;

		/** The number of executions aggregated in this node */
		int count;

		/** The number of times this node has begun execution since its parent last began execution */
		int latestCount;

		/** The first time this task was executed */
		long startTime;

		/** The last time this task was executed */
		long latestStartTime;

		long latestStartNanos;

		long latestStartCPU;

		/** The last time this task ended */
		long endTime;

		/** The total amount of time the routine executed */
		long runLength;

		long runLengthNanos;

		/** The total amount of CPU time the routine has used */
		long cpuLength;

		/** Statistics kept on the length of this routine */
		RunningStatistic lengthStats;

		/** The parent routine */
		TrackNode parent;

		/** The subroutines of this routine */
		Map<String, TrackNode> children;

		/**
		 * The number of times that this routine was {@link ProgramTracker#start(String) start}ed but not explicitly
		 * {@link ProgramTracker#start(String) end}ed
		 */
		public int unfinished;

		boolean isReleased;

		TrackNode(TrackNode aParent, String aName, boolean withStats) {
			children = new LinkedHashMap<>();
			init(aParent, aName, withStats);
		}

		void init(TrackNode aParent, String aName, boolean withStats) {
			parent = aParent;
			name = aName;
			count = 0;
			startTime = 0;
			latestStartTime = 0;
			latestStartCPU = 0;
			endTime = -1;
			runLength = 0;
			cpuLength = 0;
			unfinished = 0;
			isReleased = false;
			if(withStats) {
				if(lengthStats != null)
					lengthStats.clear();
				else
					lengthStats = new RunningStatistic(25);
			} else if(lengthStats != null)
				lengthStats = null;
		}

		void start() {
			count++;
			latestCount++;
			startTime = System.currentTimeMillis();
			latestStartTime = startTime;
			latestStartCPU = getCpuNow();
			if(lengthStats != null)
				latestStartNanos = System.nanoTime();
		}

		/** Notifies this node's tracker that this routine is over */
		public void done() {
			ProgramTracker.this.end(this);
		}

		/** Same as {@link #done()} */
		public void end() {
			ProgramTracker.this.end(this);
		}

		/** Same as {@link #done()} */
		@Override
		public void close() {
			ProgramTracker.this.end(this);
		}

		/** @return The name of the routine */
		public String getName() {
			return name;
		}

		/** @return The parent routine */
		public TrackNode getParent() {
			return parent;
		}

		/** @return The number of executions aggregated in this node */
		public int getCount() {
			return count;
		}

		/** @return The first time this task was executed */
		public long getFirstStart() {
			return startTime;
		}

		/** @return The last time this task was executed */
		public long getLatestStart() {
			return latestStartTime;
		}

		/** @return The last time this task ended */
		public long getLastEnd() {
			return endTime;
		}

		/** @return The total amount of time the routine executed */
		public long getLength() {
			return runLength;
		}

		/** @return The amount of time the routine executed in nanoseconds (will be 0 if length stats are not enabled) */
		public long getLengthNanos() {
			return runLengthNanos;
		}

		/** @return The total amount of CPU time the routine has used */
		public long getCpuLength() {
			return cpuLength;
		}

		/** @return Statistics kept on the length of this routine */
		public RunningStatistic getLengthStats() {
			return lengthStats;
		}

		/** @return The subroutines of this routine */
		public TrackNode [] getChildren() {
			return children.values().toArray(new TrackNode[children.size()]);
		}

		void clear() {
			if(lengthStats != null)
				lengthStats.clear();
			children.clear();
		}

		/**
		 * @param config The print configuration for printing
		 * @return The amount of time that this routine took exclusive of its child routines
		 */
		public long getLocalLength(PrintConfig config) {
			long ret = getRealLength();
			if(config == null || !config.isAsync())
				for(TrackNode ch : children.values())
					ret -= ch.runLength;
			else
				for(Object ch : children.values().toArray())
					ret -= ((TrackNode) ch).runLength;
			return ret;
		}

		/**
		 * @return The total amount of time this task has been running. Unlike {@link #getLength()}, this method takes into account the
		 *         amount of time since {@link #getLatestStart()} and now if the task is currently running
		 */
		public long getRealLength() {
			long ret = runLength;
			if(endTime < latestStartTime)
				ret += (System.currentTimeMillis() - latestStartTime);
			return ret;
		}

		/**
		 * @param threshold The threshold percent
		 * @param totalTime The total time that this node's tracker ran
		 * @return Whether this task took at least <code>threshold</code>% of <code>totalTime</code>
		 */
		public boolean isAccented(float threshold, long totalTime) {
			if(totalTime <= 0)
				return false;
			boolean accent = false;
			long realLength = getRealLength();
			float localPercent = getLocalLength(null) * 100.0f / totalTime;
			if(threshold > 0) {
				long thresholdTime = (long) (threshold / 100.0f * totalTime);
				if(localPercent >= threshold)
					accent = true;
				else if(realLength >= thresholdTime) {
					long length2 = realLength;
					for(TrackNode child : children.values()) {
						if(child.getRealLength() >= thresholdTime)
							length2 -= child.getRealLength();
					}
					if(length2 >= thresholdTime)
						accent = true;
				}
			}
			return accent;
		}

		/**
		 * Gets a subtask under this task and creates one if one does not already exist
		 *
		 * @param task The name of the task to get or create
		 * @return The subtask with the given name
		 */
		public TrackNode create(String task) {
			return children.computeIfAbsent(task, __->newNode(this, task));
		}

		/**
		 * Adds a run time to this tracker manually
		 *
		 * @param nanos The number of nanoseconds of the run time to add to this tracker
		 */
		public void add(long nanos) {
			long millis = nanos / 1000000;
			if(count == 0) {
				startTime = System.currentTimeMillis() - millis;
			}
			latestStartTime = System.currentTimeMillis() - millis;
			endTime = latestStartTime;
			if(lengthStats != null) {
				runLengthNanos += nanos;
				lengthStats.add(nanos);
			}
			if(cpuLength > 0)
				cpuLength *= (runLength + millis) * 1.0f / runLength;
			count++;
			runLength += millis;
		}

		/**
		 * Merges this node's data with another's
		 *
		 * @param node The node to merge with this one
		 */
		public void merge(TrackNode node) {
			count += node.count;
			if(node.startTime < startTime)
				startTime = node.startTime;
			if(node.latestStartTime > latestStartTime) {
				latestStartTime = node.latestStartTime;
				endTime = node.endTime;
			}
			runLength += node.runLength;
			unfinished += node.unfinished;
			if(lengthStats != null && node.lengthStats != null)
				lengthStats.merge(node.lengthStats);
			for(TrackNode child : node.children.values()) {
				children.compute(child.getName(), (__, old)->{
					if(old==null)
						return child.clone();
					old.merge(child);
					return old;
				});
			}
		}

		@Override
		public boolean equals(Object o) {
			if(!(o instanceof TrackNode))
				return false;
			TrackNode tn = (TrackNode) o;
			if(!name.equals(tn.name))
				return false;
			if(startTime != tn.startTime || count != tn.count || runLength != tn.runLength || endTime != tn.endTime
				|| latestStartTime != tn.latestStartTime)
				return false;
			if(children.equals(tn.children))
				return false;
			return true;
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, startTime, count, runLength, endTime, latestStartTime, children);
		}

		@Override
		public TrackNode clone() {
			TrackNode ret;
			try {
				ret = (TrackNode) super.clone();
			} catch(CloneNotSupportedException e) {
				throw new IllegalStateException("Clone not supported", e);
			}
			if(lengthStats != null)
				ret.lengthStats = lengthStats.clone();
			ret.parent = null;
			ret.children = new LinkedHashMap<>();
			for(TrackNode child : children.values()) {
				TrackNode childClone = child.clone();
				childClone.parent = this;
				ret.children.put(child.getName(), childClone);
			}
			return ret;
		}

		@Override
		public String toString() {
			return toString(0, 0, 0);
		}

		/**
		 * Prints a representation
		 *
		 * @param indent The amount to indent this line
		 * @param lastTime The time that the parent task started
		 * @param totalTime The total time spent in the tracker
		 * @return A string representing this task's execution statistics
		 */
		public String toString(int indent, long lastTime, long totalTime) {
			StringBuilder sb = new StringBuilder();
			write(indent, lastTime, totalTime, sb, null);
			return sb.toString();
		}

		boolean write(int indent, long lastTime, long totalTime, StringBuilder sb, PrintConfig config) {
			long localLength = getLocalLength(config);
			float localPercent = 0;
			float totalPercent = 0;
			boolean highlight = false;
			long realLength = getRealLength();
			float accentThresh = config == null ? 0 : config.getHighlightThreshold();
			if(totalTime > 0) {
				localPercent = localLength * 100.0f / totalTime;
				totalPercent = realLength * 100.0f / totalTime;
				if (config != null && config.getDisplayThreshold() > 0.0f && totalPercent < config.getDisplayThreshold())
					return false;
				if(accentThresh > 0) {
					long thresholdTime = (long) (accentThresh / 100.0f * totalTime);
					if(localPercent >= accentThresh)
						highlight = true;
					else if(realLength >= thresholdTime) {
						long length2 = realLength;
						for(TrackNode child : children.values()) {
							if(child.getRealLength() >= thresholdTime)
								length2 -= child.getRealLength();
						}
						if(length2 >= thresholdTime)
							highlight = true;
					}
				}
			}

			if (config != null) {
				sb.append(config.getInitialIndent());
				for (int i = 0; i < indent; i++)
					sb.append(config.getIndent());
			}
			if (highlight)
				sb.append("* ");
			sb.append(name);
			if(unfinished > 0 || endTime < latestStartTime) {
				sb.append(" (unfinished x");
				int uf = unfinished;
				if(endTime < latestStartTime)
					uf++;
				sb.append(uf);
				sb.append(")");
			}
			sb.append(' ');
			sb.append('(');
			printTime(startTime, lastTime, sb, false);
			sb.append(')');
			if(count > 1) {
				sb.append('x');
				sb.append(count);
			}
			sb.append(':');
			sb.append(' ');
			QommonsUtils.printTimeLength(localLength, sb, true);
			if(localPercent > 0) {
				sb.append(' ');
				sb.append(PERCENT_FORMAT.format(localPercent));
				sb.append('%');
			}
			if(!children.isEmpty()) {
				sb.append(' ');
				sb.append('(');
				QommonsUtils.printTimeLength(realLength, sb, true);
				if(parent != null && totalPercent > 0) {
					sb.append(' ');
					sb.append(PERCENT_FORMAT.format(totalPercent));
					sb.append('%');
				}
				sb.append(" total)");
			}
			if (highlight && lengthStats != null && lengthStats.isInteresting()) {
				sb.append("        ");
				sb.append(lengthStats.toString(NANO_FORMAT));
			}
			if (highlight)
				sb.append(" *");
			return true;
		}

		/**
		 * Serializes this tracking node and its children to JSON
		 *
		 * @return The JSON representation of this node. May be deserialized with {@link #fromJson(JsonObject)}.
		 */
		public JsonObject toJson() {
			JsonObject ret = new JsonObject();
			ret.with("name", name);
			ret.with("count", Integer.valueOf(count));
			ret.with("latestCount", Integer.valueOf(latestCount));
			ret.with("startTime", Long.valueOf(startTime));
			ret.with("latestStartTime", Long.valueOf(latestStartTime));
			ret.with("latestStartNanos", Long.valueOf(latestStartNanos));
			ret.with("latestStartCPU", Long.valueOf(latestStartCPU));
			ret.with("endTime", Long.valueOf(endTime));
			ret.with("length", Long.valueOf(runLength));
			ret.with("unfinished", Integer.valueOf(unfinished));
			if(lengthStats != null)
				ret.with("lengthStats", lengthStats.toJson());
			List<Object> jsonChildren = new ArrayList<>(children.size());
			ret.with("children", jsonChildren);
			for(TrackNode child : children.values())
				jsonChildren.add(child.toJson());
			return ret;
		}
	}

	private String theName;

	private List<TrackNode> theCacheNodes;

	private Map<String, TrackNode> theNodes;

	boolean isWithRTStats;

	boolean isWithCPU;

	private TrackNode theCurrentNode;

	private boolean isOn;

	private java.lang.management.ThreadMXBean theThreadBean;

	private TrackNode INACTIVE = new TrackNode(null, "INACTIVE", false);

	/**
	 * Creates a ProgramTracker
	 *
	 * @param name A label for this tracker
	 */
	public ProgramTracker(String name) {
		this(name, false);
	}

	/**
	 * Creates a ProgramTracker
	 *
	 * @param name A label for this tracker
	 * @param withStats Whether this tracker uses statistical analysis
	 */
	public ProgramTracker(String name, boolean withStats) {
		theName = name;
		theCacheNodes = new java.util.ArrayList<>();
		theNodes = new LinkedHashMap<>();
		isOn = true;
		isWithRTStats = withStats;
	}

	/** @return This tracker's name */
	public String getName() {
		return theName;
	}

	/**
	 * @param name The name for this tracker
	 * @return This tracker
	 */
	public ProgramTracker setName(String name) {
		theName = name;
		return this;
	}

	/** @return Whether this tracker is recording data */
	public boolean isOn() {
		return isOn;
	}

	/**
	 * @param on Whether this tracker should be on or off
	 * @return This tracker
	 */
	public ProgramTracker setOn(boolean on) {
		isOn = on;
		return this;
	}

	/** @return Whether this tracker records run time statistics about each repeated procedure */
	public boolean isWithRTStats() {
		return isWithRTStats;
	}

	/**
	 * @param withStats Whether this tracker should record run time statistics about each repeated procedure
	 * @return This tracker
	 */
	public ProgramTracker setWithRTStats(boolean withStats) {
		isWithRTStats = withStats;
		return this;
	}

	/** @return Whether this tracker keeps track of CPU time in addition to run time */
	public boolean isWithCPU() {
		return isWithCPU;
	}

	/**
	 * @param cpu Whether this tracker should keep track of CPU time in addition to run time
	 * @return This tracker
	 */
	public ProgramTracker setWithCPU(boolean cpu) {
		isWithCPU = cpu;
		if(isWithCPU) {
			java.lang.management.ThreadMXBean tb = java.lang.management.ManagementFactory.getThreadMXBean();
			tb.setThreadCpuTimeEnabled(true);
		}
		return this;
	}

	TrackNode newNode(TrackNode aParent, String aName) {
		TrackNode ret;
		if(theCacheNodes.isEmpty())
			ret = new TrackNode(aParent, aName, isWithRTStats);
		else {
			ret = theCacheNodes.remove(theCacheNodes.size() - 1);
			ret.init(aParent, aName, isWithRTStats);
		}
		return ret;
	}

	long getCpuNow() {
		if(isWithCPU) {
			if(theThreadBean == null)
				theThreadBean = java.lang.management.ManagementFactory.getThreadMXBean();
			return theThreadBean.getCurrentThreadCpuTime();
		} else
			return 0;
	}

	void releaseNode(TrackNode node) {
		if(node.isReleased)
			return;
		theCacheNodes.add(node);
		node.isReleased = true;
		for(TrackNode child : node.children.values())
			releaseNode(child);
		node.clear();
	}

	/**
	 * Clears all previous execution data from the tracker
	 * 
	 * @return This tracker
	 */
	public ProgramTracker clear() {
		theCurrentNode = null;
		if(!theNodes.isEmpty()) {
			for(TrackNode node : theNodes.values())
				releaseNode(node);
			theNodes.clear();
		}
		return this;
	}

	/**
	 * Notifies this tracker that a routine is beginning
	 *
	 * @param routine The name of the routine that is beginning
	 * @return The routine being run. This will be passed to {@link #end(TrackNode)} when the routine is finished.
	 */
	public final TrackNode start(String routine) {
		if(!isOn)
			return INACTIVE;
		/* This code may be quite expensive, since this method is used very often and the call to
		 * Thread.currentThread() is supposed to be fairly expensive. This code may be useful for
		 * debugging in some situations, but it is not worth keeping it here to degrade performance
		 * all the time. See also the beginning of #end(TrackNode)
		Thread ct = Thread.currentThread();
		if(theCurrentThread == null)
			theCurrentThread = ct;
		else if(theCurrentThread != ct)
			throw new IllegalStateException("Program Trackers may not be used by multiple threads!");
		 */
		TrackNode ret = null;
		if(theCurrentNode == null)
			ret=theNodes.computeIfAbsent(routine, __->newNode(null, routine));
		else
			ret = theCurrentNode.create(routine);
		ret.start();
		theCurrentNode = ret;
		return ret;
	}

	/**
	 * Notifies this tracker that a routine is ending
	 *
	 * @param routine The node of the routine that is ending
	 */
	public final void end(TrackNode routine) {
		if(!isOn || routine == null)
			return;
		/*
		Thread ct = Thread.currentThread();
		if(theCurrentThread != ct)
			throw new IllegalStateException("Program Trackers may not be used by multiple threads!");
		 */
		long time = System.currentTimeMillis();
		long nanos = -1, nanoCost = -1;
		if (isWithRTStats) {
			nanos = System.nanoTime();
			nanoCost = nanos - theCurrentNode.latestStartNanos;
		}
		if(theCurrentNode == null || theCurrentNode != routine) {
			TrackNode cn = theCurrentNode;
			while(cn != null && cn != routine)
				cn = cn.parent;
			if(cn != null) {
				cn = theCurrentNode;
				while(cn != null && cn != routine) {
					cn.unfinished++;
					cn.runLength += time - theCurrentNode.latestStartTime;
					if(isWithRTStats && theCurrentNode.lengthStats != null)
						cn.runLengthNanos += nanoCost;
					cn.endTime = time;
					// But don't pollute the statistics
					cn = cn.parent;
				}
				theCurrentNode = cn;
			} else {
				cn = routine;
				while(cn != null && cn != theCurrentNode)
					cn = cn.parent;
				if(cn != null){
					if(routine.parent==null)
						throw new IllegalStateException("Routine " + routine.getName() + " ended twice");
					else
						throw new IllegalStateException(
							"Routine " + routine.getName() + " ended twice or ended after parent routine " + routine.parent.getName());
				} else
					throw new IllegalStateException(
						"Routine " + routine.getName() + " not started or ended twice: " + printData(new StringBuilder()));
			}
		}
		if(theCurrentNode != null) {
			if(isWithRTStats && theCurrentNode.lengthStats != null) {
				theCurrentNode.lengthStats.add(nanoCost / 1.0e9f);
				theCurrentNode.runLengthNanos += nanoCost;
			}
			theCurrentNode.runLength += time - theCurrentNode.latestStartTime;
			theCurrentNode.endTime = time;
			theCurrentNode = theCurrentNode.parent;
		}
		for (TrackNode child : routine.children.values())
			child.latestCount = 0;
	}

	/** @return The node representing the task that is currently executing */
	public TrackNode getCurrentTask() {
		return theCurrentNode;
	}

	/** @return The raw data gathered by this tracker */
	public final TrackNode [] getData() {
		return theNodes.values().toArray(new TrackNode[theNodes.size()]);
	}

	/**
	 * Prints the data gathered by this tracker to {@link System#out}
	 *
	 * @see #printData(StringBuilder)
	 */
	public final void printData() {
		printData(0, 0);
	}

	/**
	 * Prints the data gathered by this tracker to {@link System#out}
	 *
	 * @param displayThreshold The threshold below which percent items in the profiling will not be printed
	 * @param highlightThreshold The threshold above which percent items in the profiling will be highlighted
	 */
	public final void printData(float displayThreshold, float highlightThreshold) {
		printData(System.out, displayThreshold, highlightThreshold);
	}

	/**
	 * Prints the data gathered by this tracker to the given stream
	 *
	 * @param out The stream to print this tracker's compiled information to
	 * @param displayThreshold The threshold below which percent items in the profiling will not be printed
	 * @param highlightTreshold The threshold above which percent items in the profiling will be highlighted
	 */
	public final void printData(java.io.PrintStream out, float displayThreshold, float highlightTreshold) {
		if (!isOn)
			return;
		PrintConfig config = new PrintConfig();
		config.setDisplayThreshold(displayThreshold).setHighlightThreshold(highlightTreshold);
		StringBuilder sb = new StringBuilder();
		printData(sb, config);
		out.println(sb.toString());
	}

	/**
	 * Prints the data gathered by this tracker to a string builder
	 *
	 * @param sb The string builder to append this tracker's compiled information to
	 * @return The string builder passed in
	 */
	public final StringBuilder printData(StringBuilder sb) {
		return printData(sb, new PrintConfig());
	}

	/**
	 * Prints the data gathered by this tracker to a string builder
	 *
	 * @param sb The string builder to append this tracker's compiled information to
	 * @param displayThreshold The threshold below which percent items in the profiling will not be printed
	 * @param highlightThreshold The threshold above which percent items in the profiling will be highlighted
	 * @return The string builder passed in
	 */
	public final StringBuilder printData(StringBuilder sb, float displayThreshold, float highlightThreshold) {
		PrintConfig config = new PrintConfig();
		config.setDisplayThreshold(displayThreshold).setHighlightThreshold(highlightThreshold);
		return printData(sb, config);
	}

	/**
	 * Prints the data gathered by this tracker to a string builder
	 *
	 * @param sb The string builder to append this tracker's compiled information to
	 * @param config The print configuration to use to customize the result
	 * @return The string builder passed in
	 */
	public final StringBuilder printData(StringBuilder sb, PrintConfig config) {
		if(config.isWithIntro())
			sb.append(config.getInitialIndent());
		if(theNodes.isEmpty()) {
			if(config.isWithIntro())
				sb.append("No profiling data for tracker " + theName);
			return sb;
		}
		if(config.isWithIntro())
			sb.append("Profiling data for tracker " + theName + ":");
		long totalTime = 0;
		for (TrackNode node : theNodes.values()) {
			totalTime += node.getRealLength();
		}
		for(TrackNode node : theNodes.values())
			print(node, sb, 0, totalTime, 0, config);
		return sb;
	}

	@Override
	public ProgramTracker clone() {
		ProgramTracker ret;
		try {
			ret = (ProgramTracker) super.clone();
		} catch(CloneNotSupportedException e) {
			throw new IllegalStateException("Clone not supported", e);
		}
		ret.theCacheNodes = new java.util.ArrayList<>();
		ret.theNodes = new LinkedHashMap<>();
		ret.theCurrentNode = null;
		for(TrackNode node : theNodes.values()) {
			TrackNode clone = node.clone();
			ret.theNodes.put(node.getName(), clone);
			if(node == theCurrentNode)
				ret.theCurrentNode = clone;
		}
		return ret;
	}

	/**
	 * Serializes this tracker to JSON
	 *
	 * @return A JSON-serialized representation of this tracker. May be deserialized with {@link #fromJson(JsonObject)}
	 */
	public JsonObject toJson() {
		JsonObject ret = new JsonObject();
		ret.with("name", theName);
		ret.with("withStats", Boolean.valueOf(isWithRTStats));
		List<Object> nodes = new ArrayList<>();
		ret.with("nodes", nodes);
		for(TrackNode node : theNodes.values())
			nodes.add(node.toJson());
		return ret;
	}

	/**
	 * Merges this tracker's data with another's
	 *
	 * @param tracker The tracker whose data to merge
	 */
	public void merge(ProgramTracker tracker) {
		for(TrackNode node : tracker.theNodes.values()) {
			theNodes.compute(node.getName(), (__, old)->{
				if(old==null)
					return node.clone();
				old.merge(node);
				return old;
			});
		}
	}

	private void print(TrackNode node, StringBuilder sb, long lastTime, long totalTime, int indent, PrintConfig config) {
		sb.append('\n');
		if (node.write(indent, lastTime, totalTime, sb, config)) {
			if (!config.isAsync())
				for (TrackNode ch : node.children.values())
					print(ch, sb, lastTime, totalTime, indent + 1, config);
			else
				for (Object ch : node.children.values().toArray())
					print((TrackNode) ch, sb, lastTime, totalTime, indent + 1, config);
		} else
			sb.deleteCharAt(sb.length() - 1); // Remove the \n
	}

	/**
	 * @param node The node to get the depth of
	 * @return The depth of the node
	 */
	public static int getDepth(TrackNode node) {
		int ret;
		for(ret = 1; node != null; ret++)
			node = node.parent;
		return ret;
	}

	/**
	 * Prints a time relative to another time
	 *
	 * @param time The time to print
	 * @param lastTime The relative time
	 * @param sb The string builder to print the result to
	 * @param local Whether to print the time in local format or not
	 */
	public static void printTime(long time, long lastTime, StringBuilder sb, boolean local) {
		int gmt = local ? 1 : 0;
		java.util.Date d = new java.util.Date(time);
		if(lastTime == 0) {
			sb.append(formats[gmt][0].format(d));
			return;
		}
		long diff = time - lastTime;
		int days, hrs, mins;
		diff /= 1000;
		diff /= 60;
		mins = (int) (diff % 60);
		diff /= 60;
		hrs = (int) (diff % 24);
		diff /= 24;
		days = (int) diff;
		if(days > 0)
			sb.append(formats[gmt][1].format(d));
		else if(hrs > 0)
			sb.append(formats[gmt][2].format(d));
		else if(mins > 0)
			sb.append(formats[gmt][3].format(d));
		else
			sb.append(formats[gmt][4].format(d));
	}

	/**
	 * Deserializes a JSON-serialized representation of a tracker
	 *
	 * @param json The JSON representation of a tracker serialized with {@link #toJson()}
	 * @return A tracker with the same content as the one serialized
	 */
	public static ProgramTracker fromJson(JsonObject json) {
		ProgramTracker ret = new ProgramTracker((String) json.get("name"), ((Boolean) json.get("withStats")).booleanValue());
		for (JsonObject jsonNode : (java.util.List<JsonObject>) json.get("nodes")) {
			TrackNode node=ret.nodeFromJson(null, jsonNode);
			ret.theNodes.put(node.getName(), node);
		}
		return ret;
	}

	/**
	 * Deserializes a track node from a JSON representation
	 *
	 * @param parent The parent node for the new node
	 * @param json The JSON-serialized node, serialized with {@link #toJson()}
	 * @return A track node with the same content as the one that was serialized
	 */
	public TrackNode nodeFromJson(TrackNode parent, JsonObject json) {
		TrackNode ret = new TrackNode(parent, (String) json.get("name"), false);
		ret.startTime = ((Number) json.get("startTime")).longValue();
		ret.latestStartCPU = ((Number) json.get("latestStartCPU")).longValue();
		ret.count = ((Number) json.get("count")).intValue();
		ret.latestStartTime = ((Number) json.get("latestStartTime")).longValue();
		ret.latestStartNanos = ((Number) json.get("latestStartNanos")).longValue();
		ret.endTime = ((Number) json.get("endTime")).longValue();
		ret.runLength = ((Number) json.get("length")).longValue();
		ret.unfinished = ((Number) json.get("unfinished")).intValue();
		if(json.get("lengthStats") != null)
			ret.lengthStats = RunningStatistic.fromJson((JsonObject) json.get("lengthStats"));
		for (JsonObject jsonNode : (java.util.List<JsonObject>) json.get("children")) {
			TrackNode node=nodeFromJson(null, jsonNode);
			ret.children.put(node.getName(), node);
		}
		return ret;
	}
}

/* TrackerSet.java Created May 16, 2011 by Andrew Butler, PSL */
package org.qommons;

/**
 * Maintains a set of {@link ProgramTracker}s to keep track of performance over a set of time intervals
 */
public class TrackerSet {
	/** A configuration for one time interval */
	public static class TrackConfig {
		private final long theKeepTime;

		private final boolean isWithStats;

		/**
		 * Creates a TrackConfig
		 *
		 * @param keepTime The time interval to keep statistics for
		 * @param withStats Whether this interval should keep statistics for each time a routine is run
		 */
		public TrackConfig(long keepTime, boolean withStats) {
			theKeepTime = keepTime;
			isWithStats = withStats;
		}

		/**
		 * @return The time interval to keep statistics for
		 */
		public long getKeepTime() {
			return theKeepTime;
		}

		/**
		 * @return Whether to keep statistics for this config
		 */
		public boolean isWithStats() {
			return isWithStats;
		}
	}

	private String theName;

	private TrackConfig [] theConfigs;

	private ProgramTracker [] [] theTrackers;

	private java.util.concurrent.locks.Lock theLock;

	private boolean isConfigured;

	/**
	 * Creates a tracker set
	 *
	 * @param name The name for the tracker set
	 * @param configs The initial configs for this tracker set
	 */
	public TrackerSet(String name, TrackConfig [] configs) {
		theName = name;
		theConfigs = new TrackConfig[0];
		theTrackers = new ProgramTracker[0][];
		theLock = new java.util.concurrent.locks.ReentrantLock();
		if(configs != null)
			addTrackConfigs(configs);
	}

	/**
	 * @param configs Configs to add to this tracker set
	 */
	public synchronized void addTrackConfigs(TrackConfig [] configs) {
		if(isConfigured)
			throw new IllegalStateException("Tracker Set " + theName + " is already configured--no more track configs may be added");
		theLock.lock();
		try {
			ProgramTracker [] [] newTrackers = new ProgramTracker[configs.length][2];
			for(int i = 0; i < configs.length; i++) {
				for(int j = 0; j < newTrackers[i].length; j++) {
					newTrackers[i][j] = new ProgramTracker(theName + " " + QommonsUtils.printTimeLength(configs[i].getKeepTime()));
					newTrackers[i][j].setWithRTStats(configs[i].isWithStats());
				}
			}
			theConfigs = ArrayUtils.addAll(theConfigs, configs);
			theTrackers = ArrayUtils.addAll(theTrackers, newTrackers);
		} finally {
			theLock.unlock();
		}
	}

	/**
	 * @return Whether this track set has finished being configured
	 */
	public boolean isConfigured() {
		return isConfigured;
	}

	/** Marks this tracker set as configured and ready to receive tracking data */
	public synchronized void setConfigured() {
		theLock.lock();
		try {
			isConfigured = true;
		} finally {
			theLock.unlock();
		}
	}

	/**
	 * Populates this track set with a piece of data
	 *
	 * @param tracker The tracking data to add to this track set
	 */
	public void addTrackData(ProgramTracker tracker) {
		if(!isConfigured)
			throw new IllegalStateException("This TrackerSet has not been configured");
		theLock.lock();
		try {
			long now = System.currentTimeMillis();
			for(int c = 0; c < theConfigs.length; c++) {
				checkRotate(c);
				if(theTrackers[c][0].getData().length > 0
					&& theTrackers[c][0].getData()[0].startTime > now - theConfigs[c].getKeepTime() * .5f)
					theTrackers[c][1].merge(tracker);
				theTrackers[c][0].merge(tracker);
			}
		} finally {
			theLock.unlock();
		}
	}

	/**
	 * @return All TrackConfigs that this track set keeps tracking data for
	 */
	public TrackConfig [] getConfigs() {
		return theConfigs.clone();
	}

	/**
	 * Gets tracking data for a given interval
	 *
	 * @param interval The {@link TrackConfig#getKeepTime() keep time} interval to get tracking data for. This number must match the config
	 *            time exactly.
	 * @return The tracking data accumulated for the given time interval. The tracker's start time will not necessarily match the interval
	 *         exactly, but the tracker will have between 50% and 125% of the config's time, or less if there has been no input in the
	 *         config time's interval.
	 */
	public synchronized ProgramTracker getTrackData(long interval) {
		for(int c = 0; c < theConfigs.length; c++)
			if(theConfigs[c].getKeepTime() == interval) {
				theLock.lock();
				try {
					checkRotate(c);
					return theTrackers[c][0].clone();
				} finally {
					theLock.unlock();
				}
			}
		return null;
	}

	private void checkRotate(int c) {
		long now = System.currentTimeMillis();
		if(theTrackers[c][0].getData().length > 0) {
			if(theTrackers[c][0].getData()[0].startTime < now - theConfigs[c].getKeepTime() * 1.25f) {
				theTrackers[c][0].clear();
				if(theTrackers[c][1].getData().length > 0
					&& theTrackers[c][1].getData()[0].startTime < now - theConfigs[c].getKeepTime() * 1.25f)
					theTrackers[c][1].clear();
				else {
					ProgramTracker temp = theTrackers[c][0];
					theTrackers[c][0] = theTrackers[c][1];
					theTrackers[c][1] = temp;
				}
			}
		}
	}
}

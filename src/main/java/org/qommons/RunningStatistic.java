/* RunningStatistic.java Created Mar 22, 2011 by Andrew Butler, PSL */
package org.qommons;

import org.json.simple.JSONObject;

/** Efficiently collects statistics one datum at a time. */
public class RunningStatistic implements Cloneable {
	static class SampleSet {
		final int theIndex;

		final float theMean;

		final float theSigma;

		SampleSet(int index, float mean, float sigma) {
			theIndex = index;
			theMean = mean;
			theSigma = sigma;
		}

		JSONObject toJson() {
			JSONObject ret = new JSONObject();
			ret.put("index", Integer.valueOf(theIndex));
			ret.put("mean", Float.valueOf(theMean));
			ret.put("sigma", Float.valueOf(theSigma));
			return ret;
		}

		static SampleSet fromJson(JSONObject json) {
			return new SampleSet(((Number) json.get("index")).intValue(), ((Number) json.get("mean")).floatValue(),
				((Number) json.get("sigma")).floatValue());
		}
	}

	private static java.text.DecimalFormat PERCENT_FORMAT = new java.text.DecimalFormat("0.0");

	private final int theSampleRate;

	private boolean isOutlierEnabled;

	private FloatList theOutliers;

	private java.util.ArrayList<SampleSet> theSampleSets;

	private int theStartCount;

	private int theCount;

	private int theNormalCount;

	private float theOverallMean;

	private float theVariance;

	private float theMin;

	private float theMax;

	private int theNaNCount;

	private int thePosInfCount;

	private int theNegInfCount;

	private int theSampleSetCount;

	private int theTempCount;

	private float theTempMean;

	private float theTempVariance;

	private boolean isFinished;

	/**
	 * Creates a RunningStatistic
	 *
	 * @param sampleRate The sampling rate to use to determine outliers and sample sets. Must be at least 4.
	 */
	public RunningStatistic(int sampleRate) {
		if(sampleRate < 4)
			throw new IllegalArgumentException("Sample rate too small");
		theOutliers = new FloatList();
		theSampleSets = new java.util.ArrayList<>();
		theSampleRate = sampleRate;
		theStartCount = sampleRate;
		theMin = Float.MAX_VALUE;
		theMax = Float.MIN_VALUE;
	}

	/**
	 * @param enabled Whether outliers are enabled (excluded from mean and standard dev calculations) in this statistic set
	 */
	public void setOutliersEnabled(boolean enabled) {
		isOutlierEnabled = enabled;
	}

	/**
	 * Adds a datum to this statistic
	 *
	 * @param datum The datum to add
	 */
	public void add(float datum) {
		if(isFinished)
			throw new IllegalStateException("Cannot add data to a finished statistic set");
		theCount++;
		if(Float.isNaN(datum))
			theNaNCount++;
		else if(datum == Float.POSITIVE_INFINITY)
			thePosInfCount++;
		else if(datum == Float.NEGATIVE_INFINITY)
			theNegInfCount++;
		else {
			if(datum < theMin)
				theMin = datum;
			if(datum > theMax)
				theMax = datum;
			if(theStartCount < 0) // This object has enough data for normal operation
			{
				addSampleDatum(datum);
				float diff = datum - theOverallMean;
				if(diff < 0)
					diff = -diff;
				if(isOutlierEnabled && diff * diff > 9 * theVariance) // 3sigma
					theOutliers.add(datum); // An outlier
				else { // A normal value
					theVariance = addToVariance(theVariance, theNormalCount, theOverallMean, datum);
					theNormalCount++;
					theOverallMean = addToMean(theOverallMean, theNormalCount, datum);
				}
			} else if(theStartCount > 0) {
				/* This object does not yet have enough data for normal operation. Compile the datum
				 * for later analysis */
				theOutliers.add(datum);
				theStartCount--;
				return;
			} else {
				/* We now have enough data for normal operation henceforward. Analyze the data
				 * gathered so far to initialize the mean and variance. */
				theOutliers.add(datum);
				startForReal();
			}
		}
	}

	private void startForReal() {
		theStartCount = -1;
		FloatList goodValues = new FloatList(theOutliers.toArray());
		theOutliers.clear();
		boolean removed;
		float mean;
		float q;
		float sigma;
		do {
			mean = 0;
			q = 0;
			for(int d = 0; d < goodValues.size(); d++) {
				float value = goodValues.get(d);
				q = addToVariance(q, d, mean, value);
				mean = addToMean(mean, d + 1, value);
			}

			sigma = (float) Math.sqrt(q / (goodValues.size() - 1));

			removed = false;
			for(int d = 0; d < goodValues.size(); d++) {
				float value = goodValues.get(d);
				if(Math.abs(mean - value) > sigma * 2) {
					theOutliers.add(value);
					goodValues.remove(d);
					d--;
					removed = true;
				}
			}
		} while(removed);
		theOverallMean = mean;
		theVariance = q;
		theNormalCount = goodValues.size();

		theTempMean = mean;
		theTempCount = 0;
		theSampleSets.add(new SampleSet(0, mean, sigma));
		theSampleSetCount++;
	}

	private void addSampleDatum(float datum) {
		SampleSet ss = theSampleSets.get(theSampleSets.size() - 1);
		if(!isOutlierEnabled || Math.abs(datum - ss.theMean) < 3 * ss.theSigma) {
			theTempVariance = addToVariance(theTempVariance, theTempCount, theTempMean, datum);
			theTempCount++;
			theTempMean = addToMean(theTempMean, theTempCount, datum);
			if(theTempCount >= theSampleRate) {
				float sigma = (float) Math.sqrt(theTempVariance / (theTempCount - 1));
				theSampleSets.add(new SampleSet(theSampleSetCount, theTempMean, sigma));
				theSampleSetCount++;
				theTempCount = 0;
				theTempMean = 0;
				theTempVariance = 0;
			}
		}
	}

	/** Finalizes this statistic so that no more data points can be added and the results can be viewed */
	public void finish() {
		if(isFinished)
			return;
		isFinished = true;

		if(theStartCount >= 0)
			startForReal();

		// Trim outliers
		for(int i = 0; i < theOutliers.size(); i++) {
			float value = theOutliers.get(i);
			float diff = value - theOverallMean;
			if(diff < 0)
				diff = -diff;
			if(!isOutlierEnabled || diff * diff < 9 * theVariance) // 3 sigma
			{
				theVariance = addToVariance(theVariance, theNormalCount, theOverallMean, value);
				theNormalCount++;
				theOverallMean = addToMean(theOverallMean, theNormalCount, value);
				theOutliers.remove(i);
				i--;
			}
		}

		if(theTempCount >= theSampleRate / 2) {
			float sigma = (float) Math.sqrt(theTempVariance / (theTempCount - 1));
			theSampleSets.add(new SampleSet(theSampleSetCount, theTempMean, sigma));
			theSampleSetCount++;
			theTempCount = 0;
			theTempMean = 0;
			theTempVariance = 0;
		}
		// Trim sample sets
		// Remove all but the peaks and troughs
		boolean removed;
		do {
			removed = false;
			if(theSampleSets.size() > 1) {
				SampleSet set1 = theSampleSets.get(0);
				SampleSet set2 = theSampleSets.get(1);
				boolean increasing = set2.theMean > set1.theMean;
				for(int i = 2; i < theSampleSets.size(); i++) {
					set1 = theSampleSets.get(i - 1);
					set2 = theSampleSets.get(i);
					float diff = set2.theMean - set1.theMean;
					if(diff > 0 == increasing) {
						theSampleSets.remove(i - 1);
						i--;
						removed = true;
					} else
						increasing = !increasing;
				}

				// Remove peaks and troughs that aren't extreme enough to be interesting
				for(int i = 2; i < theSampleSets.size(); i++) {
					set1 = theSampleSets.get(i - 1);
					set2 = theSampleSets.get(i);
					float diff = set2.theMean - set1.theMean;
					if(diff < 0)
						diff = -diff;
					if(diff < set1.theSigma / 2 && diff < set2.theSigma / 2) {
						theSampleSets.remove(i - 1);
						i--;
						removed = true;
					}
				}
			}
		} while(removed);

		if(theSampleSets.size() == 2) {
			SampleSet set1 = theSampleSets.get(0);
			SampleSet set2 = theSampleSets.get(1);
			float diff = set2.theMean - set1.theMean;
			if(diff < 0)
				diff = -diff;
			if(diff < set1.theSigma / 2 && diff < set2.theSigma / 2)
				theSampleSets.remove(1);
		}
	}

	/**
	 * @return A heuristic to determine whether this statistic represents anything other than a simple bell curve
	 */
	public boolean isInteresting() {
		float sigma = getSigma();
		if(theOverallMean - theMin > sigma * 3)
			return true;
		if(theMax - theOverallMean > sigma * 3)
			return true;
		return getTrend().length > 1;
	}

	/**
	 * @return The total number of data points sampled in this statistic
	 */
	public int getCount() {
		return theCount;
	}

	/**
	 * @return The mean of the real, non-outlier values in this statistic's sample set
	 */
	public float getMean() {
		if(theStartCount >= 0)
			return Float.NaN;
		return theOverallMean;
	}

	/**
	 * @return The standard deviation among the real, non-outlier values in this statistic's sample set
	 */
	public float getSigma() {
		if(theStartCount >= 0)
			return Float.NaN;
		if(theNormalCount <= 1)
			return Float.NaN;
		return (float) Math.sqrt(theVariance / (theNormalCount - 1));
	}

	/**
	 * @return The minimum of the real values in this statistic's sample set
	 */
	public float getMin() {
		return theMin;
	}

	/**
	 * @return The maximum of the real values in this statistic's sample set
	 */
	public float getMax() {
		return theMax;
	}

	/**
	 * @return The number of data points that were {@link Float#NaN}
	 */
	public int getNaNCount() {
		return theNaNCount;
	}

	/**
	 * @return The number of data points that were {@link Float#POSITIVE_INFINITY}
	 */
	public int getPosInfCount() {
		return thePosInfCount;
	}

	/**
	 * @return The number of data points that were {@link Float#NEGATIVE_INFINITY}
	 */
	public int getNegInfCount() {
		return theNegInfCount;
	}

	/**
	 * @return Whether outliers are enabled (excluded from mean and standard dev calculations) in this statistic set
	 */
	public boolean isOutlierEnabled() {
		return isOutlierEnabled;
	}

	/**
	 * @return All data given to this statistic set that were more than 3 sigma away from the mean value at the time the data was given
	 */
	public float [] getOutliers() {
		finish();
		float [] retA = theOutliers.toArray();
		java.util.Arrays.sort(retA);
		return retA;
	}

	/**
	 * Returns a trend array, or a list of mean values that this statistic set observed over time. The list is trimmed of uninteresting data
	 * to be more concise.
	 *
	 * @return The trend of this statistic set
	 */
	public float [] getTrend() {
		finish();
		if(theSampleSets.size() < 2)
			return new float[] {theOverallMean};
		FloatList ret = new FloatList();
		for(int i = 0; i < theSampleSets.size(); i++)
			ret.add(theSampleSets.get(i).theMean);
		return ret.toArray();
	}

	/** Clears this statistic set so it can be used again */
	public void clear() {
		theSampleSets.clear();
		theSampleSetCount = 0;
		theOutliers.clear();
		theMin = Float.POSITIVE_INFINITY;
		theMax = Float.NEGATIVE_INFINITY;
		theNaNCount = 0;
		theNegInfCount = 0;
		thePosInfCount = 0;
		theCount = 0;
		theNormalCount = 0;
		theOverallMean = 0;
		theVariance = 0;
		theTempCount = 0;
		theTempMean = 0;
		theTempVariance = 0;
		theStartCount = theSampleRate;
		isFinished = false;
	}

	/**
	 * Merges this statistics set with another. This method assumes that <code>stat</code> was collected after this set. Otherwise the
	 * trending will be wrong. The result of this call will be that this statistics set looks as it would if this set were the one
	 * collecting <code>stat</code>'s data.
	 *
	 * @param stat The statistics set to merge with this one
	 */
	public void merge(RunningStatistic stat) {
		finish();
		stat.finish();
		theCount += stat.theCount;
		if(stat.theMin < theMin)
			theMin = stat.theMin;
		if(stat.theMax > theMax)
			theMax = stat.theMax;
		theNaNCount += stat.theNaNCount;
		theNegInfCount += stat.theNegInfCount;
		thePosInfCount += stat.thePosInfCount;
		float newMean = (theOverallMean * theNormalCount + stat.theOverallMean * stat.theNormalCount)
			/ (theNormalCount + stat.theNormalCount);
		float newVar = (theVariance * theNormalCount + stat.theVariance * stat.theNormalCount) / (theNormalCount + stat.theNormalCount);
		newVar += Math.abs(theOverallMean * theOverallMean - stat.theOverallMean * stat.theOverallMean)
			/ (Math.abs(theNormalCount - stat.theNormalCount) + 1);
		theOverallMean = newMean;
		theVariance = newVar;
		theNormalCount += stat.theNormalCount;
		theOutliers.addAll(stat.theOutliers, -1);
		theSampleSets.addAll(stat.theSampleSets);

		isFinished = false;
		finish();
	}

	@Override
	public RunningStatistic clone() {
		RunningStatistic ret;
		try {
			ret = (RunningStatistic) super.clone();
		} catch(CloneNotSupportedException e) {
			throw new IllegalStateException("Clone not supported", e);
		}
		ret.theOutliers = theOutliers.clone();
		ret.theSampleSets = (java.util.ArrayList<SampleSet>) theSampleSets.clone();
		return ret;
	}

	/**
	 * @param format The format to print the values with
	 * @return The representation of this statistic set
	 */
	public String toString(java.text.NumberFormat format) {
		finish();
		StringBuilder ret = new StringBuilder();
		float sigma = getSigma();
		if(format != null)
			ret.append(format.format(theMin));
		else
			ret.append(theMin);
		ret.append("<<");
		if(format != null)
			ret.append(format.format(theOverallMean - sigma));
		else
			ret.append(theOverallMean - sigma);
		ret.append("<mean(");
		if(format != null)
			ret.append(format.format(theOverallMean));
		else
			ret.append(theOverallMean);
		ret.append(")<");
		if(format != null)
			ret.append(format.format(theOverallMean + sigma));
		else
			ret.append(theOverallMean + sigma);
		ret.append("<<");
		if(format != null)
			ret.append(format.format(theMax));
		else
			ret.append(theMax);
		if(theNaNCount > 0) {
			ret.append(", ");
			ret.append(theNaNCount);
			ret.append("NaN");
		}
		if(theNegInfCount > 0 || thePosInfCount > 0) {
			ret.append(", ");
			ret.append(theNegInfCount + thePosInfCount);
			ret.append("Inf");
		}
		if(theOutliers.size() > 2) {
			ret.append(", ");
			float percentOut = theOutliers.size() * 100.0f / (theNormalCount + theOutliers.size());
			ret.append(theOutliers.size());
			ret.append('(');
			ret.append(PERCENT_FORMAT.format(percentOut));
			ret.append("%) outliers");
		}
		if(theSampleSets.size() > 1) {
			SampleSet set1 = theSampleSets.get(0);
			if(theSampleSets.size() >= 4) {
				ret.append(", varies widely: ");
				float diff = theSampleSets.get(theSampleSets.size() - 1).theMean - set1.theMean;
				if(diff > 0)
					ret.append('+');
				ret.append(PERCENT_FORMAT.format(diff * 100.0f / set1.theMean));
				ret.append('%');
			} else
				for(int i = 1; i < theSampleSets.size(); i++) {
					SampleSet set2 = theSampleSets.get(i);
					ret.append(", ");
					float diff = set2.theMean - set1.theMean;
					if(diff > 0)
						ret.append('+');
					ret.append(PERCENT_FORMAT.format(diff * 100.0f / set1.theMean));
					ret.append('%');
					set1 = set2;
				}
		}
		return ret.toString();
	}

	/**
	 * Serializes this running statistic to JSON
	 *
	 * @return A JSON representation of this statistic that may be deserialized with {@link #fromJson(JSONObject)}
	 */
	public JSONObject toJson() {
		finish();
		JSONObject ret = new JSONObject();
		ret.put("sampleRate", Integer.valueOf(theSampleRate));
		ret.put("startCount", Integer.valueOf(theStartCount));
		ret.put("count", Integer.valueOf(theCount));
		ret.put("normalCount", Integer.valueOf(theNormalCount));
		ret.put("mean", Float.valueOf(theOverallMean));
		ret.put("variance", Float.valueOf(theVariance));
		ret.put("min", Float.valueOf(theMin));
		ret.put("max", Float.valueOf(theMax));
		ret.put("nanCount", Integer.valueOf(theNaNCount));
		ret.put("negInfCount", Integer.valueOf(theNegInfCount));
		ret.put("posInfCount", Integer.valueOf(thePosInfCount));
		ret.put("sampleSetCount", Integer.valueOf(theSampleSetCount));

		org.json.simple.JSONArray samples = new org.json.simple.JSONArray();
		ret.put("sampleSets", samples);
		for(SampleSet ss : theSampleSets)
			samples.add(ss.toJson());
		org.json.simple.JSONArray outliers = new org.json.simple.JSONArray();
		ret.put("outliers", outliers);
		for(float f : theOutliers.toArray())
			outliers.add(Float.valueOf(f));
		return ret;
	}

	/**
	 * Deserializes a running statistic from JSON
	 *
	 * @param json The JSON representation of a statistic generated with {@link #toJson()}
	 * @return A running statistic whose content is the same as the one that was serialized
	 */
	public static RunningStatistic fromJson(org.json.simple.JSONObject json) {
		RunningStatistic ret = new RunningStatistic(((Number) json.get("sampleRate")).intValue());
		ret.theStartCount = ((Number) json.get("startCount")).intValue();
		ret.theCount = ((Number) json.get("count")).intValue();
		ret.theNormalCount = ((Number) json.get("normalCount")).intValue();
		ret.theOverallMean = ((Number) json.get("mean")).floatValue();
		ret.theVariance = ((Number) json.get("variance")).floatValue();
		ret.theMin = ((Number) json.get("min")).floatValue();
		ret.theMax = ((Number) json.get("max")).floatValue();
		ret.theNaNCount = ((Number) json.get("nanCount")).intValue();
		ret.theNegInfCount = ((Number) json.get("negInfCount")).intValue();
		ret.thePosInfCount = ((Number) json.get("posInfCount")).intValue();
		ret.theSampleSetCount = ((Number) json.get("sampleSetCount")).intValue();
		for(org.json.simple.JSONObject sample : (java.util.List<org.json.simple.JSONObject>) json.get("sampleSets"))
			ret.theSampleSets.add(SampleSet.fromJson(sample));
		for(Number outlier : (java.util.List<Number>) json.get("outliers"))
			ret.theOutliers.add(outlier.floatValue());
		return ret;
	}

	/**
	 * Adds a value to a variance
	 *
	 * @param q The variance before the value is included
	 * @param oldCount The number of data before counting the value
	 * @param oldMean The mean value before counting the value
	 * @param value The value to add to the variance
	 * @return The new variance
	 */
	public static float addToVariance(float q, int oldCount, float oldMean, float value) {
		return q + (oldCount * (oldMean - value) * (oldMean - value)) / (oldCount + 1);
	}

	/**
	 * Adds a value to a mean
	 *
	 * @param m The mean before the value is included
	 * @param newCount The number of data after counting the value
	 * @param value The value to add to the mean
	 * @return The new mean value
	 */
	public static float addToMean(float m, int newCount, float value) {
		return m + (value - m) / newCount;
	}
}

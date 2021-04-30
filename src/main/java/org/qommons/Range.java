package org.qommons;

import java.util.Comparator;
import java.util.Objects;

/**
 * An interval in a continuous ordered set of values
 * 
 * @param <C> The type of values in the set
 */
public class Range<C> implements Comparable<C> {
	/**
	 * One bound of a range
	 * 
	 * @param <C> The type of values in the range's set
	 */
	public static class Bound<C> implements Cloneable {
		private final C theValue;
		private final boolean isClosed;
		private Boolean isLowerBound;

		Bound(C value, boolean closed) {
			theValue = value;
			isClosed = closed;
		}

		Bound<C> setLower(boolean lower) {
			isLowerBound = lower;
			return this;
		}

		/**
		 * @return Whether this bound is present. If not present, any value will {@link #contains(Object, Comparator) contain} this bound
		 */
		public boolean isPresent() {
			return this != EMPTY_LOW && this != EMPTY_HIGH && this != EMPTY_EITHER;
		}

		/** @return Whether this bound includes its {@link #getValue() value} */
		public boolean isClosed() {
			return isClosed;
		}

		/** @return This bound's value */
		public C getValue() {
			return theValue;
		}

		/** @return Whether this bound is a {@link Range#getLowerBound()} or a {@link Range#getUpperBound()} */
		public boolean isLowerBound() {
			if (isLowerBound == null)
				throw new IllegalArgumentException("Cannot call this method on an unused empty bound");
			return isLowerBound.booleanValue();
		}

		/**
		 * @param value The value to test
		 * @param compare The comparator to use to test
		 * @return Whether the value satisfies this bound for the range
		 */
		public boolean contains(C value, Comparator<? super C> compare) {
			if (!isPresent())
				return true;
			int comp = compare.compare(value, theValue);
			if (comp == 0)
				return isClosed;
			return (comp > 0) == isLowerBound.booleanValue();
		}

		/**
		 * Tests the divisions of containment for 2 bounds. This is the line between 2 values in the range's ordered set of values where the
		 * result of {@link #contains(Object, Comparator)} differs between the values on the right and left of the line.
		 * 
		 * @param other The other bound to compare with
		 * @param compare The value comparator to use to test
		 * @return
		 *         <ul>
		 *         <li><b>-1</b> if the division of containment for this bound is less than that of the other</li>
		 *         <li><b>0</b> if the dvision of containment for this bound is the same as that of the other</li>
		 *         <li><b>-1</b> if the division of containment for this bound is greater than that of the other</li></li>
		 * @throws IllegalArgumentException If the two bounds are not on the same end of their respective ranges
		 * @see #isLowerBound()
		 */
		public int compareTo(Bound<C> other, Comparator<? super C> compare) throws IllegalArgumentException {
			if (isLowerBound == null || other.isLowerBound == null)
				throw new IllegalArgumentException("Cannot compare unused empty bounds");
			else if (isLowerBound.booleanValue() != other.isLowerBound.booleanValue())
				throw new IllegalArgumentException("Cannot compare upper and lower bounds");
			if (isPresent()) {
				if (other.isPresent()) {
					int comp = compare.compare(theValue, other.theValue);
					if (comp == 0) {
						if (isClosed) {
							if (!other.isClosed)
								comp = isLowerBound.booleanValue() ? -1 : 1;
						} else if (other.isClosed)
							comp = isLowerBound.booleanValue() ? 1 : -1;
					}
				} else {
					return isLowerBound.booleanValue() ? 1 : -1;
				}
			}
			if (other.isPresent()) {
				return isLowerBound.booleanValue() ? -1 : 1;
			} else
				return 0;
		}

		@Override
		public Bound<C> clone() {
			if (!isPresent())
				return this;
			try {
				return (Bound<C>) super.clone();
			} catch (CloneNotSupportedException e) {
				throw new IllegalStateException(e);
			}
		}

		@Override
		public int hashCode() {
			if (!isPresent())
				return 0;
			return Objects.hash(isClosed, theValue);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof Bound))
				return false;
			Bound<?> other = (Bound<?>) obj;
			if (isPresent() || other.isPresent()) // Would be identical if equal
				return false;
			return Objects.equals(isLowerBound, other.isLowerBound) && isClosed == other.isClosed
				&& Objects.equals(theValue, other.theValue);
		}

		@Override
		public String toString() {
			return append(new StringBuilder()).toString();
		}

		StringBuilder append(StringBuilder str) {
			if (isLowerBound == null)
				str.append("??");
			else if (isPresent()) {
				if (isLowerBound.booleanValue()) {
					str.append(isLowerBound.booleanValue() ? '[' : '(').append(theValue);
				} else {
					str.append(theValue).append(isLowerBound.booleanValue() ? ']' : ')');
				}
			} else
				str.append(isLowerBound.booleanValue() ? "(-\u221E" : "\u221E)");
			return str;
		}
	}

	/**
	 * The result of a range {@link Range#compareTo(Range) comparison}. This enum contains information about the relative position of both
	 * ends of both ranges.
	 */
	public enum RangeCompareResult {
		/**
		 * <p>
		 * The {@link Range#getUpperBound() upper bound} of the left range is less than the {@link Range#getLowerBound() lower bound} of the
		 * right range.
		 * <p>
		 * <b>Range 1:</b><code>&nbsp;&lt;----></code><br>
		 * <b>Range 2:</b><code>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;----></code>
		 */
		LESS(-1, -1, -1, false),
		/**
		 * <p>
		 * The left range {@link Range#contains(Object) contains} the right range's {@link Range#getLowerBound() lower bound} and is less
		 * than the right range's {@link Range#getUpperBound() upper bound}.
		 * </p>
		 * <b>Range 1:</b><code>&nbsp;&lt;----></code><br>
		 * <b>Range 2:</b><code>&nbsp;&nbsp;&nbsp;&lt;----></code>
		 */
		OVERLAP_LOW(-1, -1, -1, true), //
		/**
		 * <p>
		 * The left range's {@link Range#getLowerBound() lower bound} is less than the right range's, and the {@link Range#getUpperBound()
		 * upper bounds} are equal.
		 * </p>
		 * 
		 * <b>Range 1:</b><code>&nbsp;&lt;-------></code><br>
		 * <b>Range 2:</b><code>&nbsp;&nbsp;&nbsp;&nbsp;&lt;----></code>
		 */
		CONTAINS_LOW(-1, -1, 0, true),
		/**
		 * <p>
		 * The left range's {@link Range#getLowerBound() lower bound} is less than the right range's, and its {@link Range#getUpperBound()
		 * upper bound} is greater than the right range's.
		 * </p>
		 * 
		 * <b>Range 1:</b><code>&nbsp;&lt;----------></code><br>
		 * <b>Range 2:</b><code>&nbsp;&nbsp;&nbsp;&nbsp;&lt;----></code>
		 */
		CONTAINS(0, -1, 1, true), //
		/**
		 * <p>
		 * The range's two {@link Range#getLowerBound() lower bounds} are equal, and the left range's {@link Range#getUpperBound() upper
		 * bound} is less than than the right range's.
		 * </p>
		 * 
		 * <b>Range 1:</b><code>&nbsp;&lt;----></code><br>
		 * <b>Range 2:</b><code>&nbsp;&lt;-------></code>
		 */
		CONTAINED_LOW(-1, 0, -1, true), //
		/**
		 * <p>
		 * Both the {@link Range#getLowerBound() lower} and {@link Range#getUpperBound() upper} bounds of the left and right ranges are
		 * equal.
		 * </p>
		 * 
		 * <b>Range 1:</b><code>&nbsp;&lt;------></code><br>
		 * <b>Range 2:</b><code>&nbsp;&lt;------></code>
		 */
		EQUAL(0, 0, 0, true), //
		/**
		 * <p>
		 * The range's two {@link Range#getLowerBound() lower bounds} are equal, and the left range's {@link Range#getUpperBound() upper
		 * bound} is greater than than the right range's.
		 * </p>
		 * 
		 * <b>Range 1:</b><code>&nbsp;&lt;-------></code><br>
		 * <b>Range 2:</b><code>&nbsp;&lt;----></code>
		 */
		CONTAINS_HIGH(1, 0, 1, true), //
		/**
		 * <p>
		 * The left range's {@link Range#getLowerBound() lower bound} is greater than the right range's, and its
		 * {@link Range#getUpperBound() upper bound} is less than the right range's.
		 * </p>
		 * 
		 * <b>Range 1:</b><code>&nbsp;&nbsp;&nbsp;&nbsp;&lt;----></code><br>
		 * <b>Range 2:</b><code>&nbsp;&lt;----------></code>
		 */
		CONTAINED(0, 1, -1, true), //
		/**
		 * <p>
		 * The left range's {@link Range#getLowerBound() lower bound} is greater than than the right range's and the range's two
		 * {@link Range#getUpperBound() upper bounds} are equal.
		 * </p>
		 * 
		 * <b>Range 1:</b><code>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;---></code><br>
		 * <b>Range 2:</b><code>&nbsp;&lt;-------></code>
		 */
		CONTAINED_HIGH(1, 1, 0, true), //
		/**
		 * <p>
		 * The left range {@link Range#contains(Object) contains} the right range's {@link Range#getUpperBound() upper bound} and is greater
		 * than the right range's {@link Range#getLowerBound() lower bound}.
		 * </p>
		 * <b>Range 1:</b><code>&nbsp;&nbsp;&nbsp;&lt;----></code><br>
		 * <b>Range 2:</b><code>&nbsp;&lt;----></code>
		 */
		OVERLAP_HIGH(1, 1, 1, true), //
		/**
		 * <p>
		 * The {@link Range#getLowerBound() lower bound} of the left range is greater than the {@link Range#getUpperBound() upper bound} of
		 * the right range.
		 * <p>
		 * <b>Range 1:</b><code>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;----></code><br>
		 * <b>Range 2:</b><code>&nbsp;&lt;----></code>
		 */
		GREATER(1, 1, 1, false);

		private final int side;
		private final int lowCompare;
		private final int highCompare;
		private final boolean isOverlap;

		private RangeCompareResult(int side, int lowCompare, int highCompare, boolean overlap) {
			this.side = side;
			this.lowCompare = lowCompare;
			this.highCompare = highCompare;
			this.isOverlap = overlap;
		}

		/**
		 * @return
		 *         <ul>
		 *         <li><b>-1</b> if the middle of the left range is less than the middle of the right range</li>
		 *         <li><b>0</b> if the middle of the left range is either the same as the right range ({@link #EQUAL}) or could not be
		 *         determined ({@link #CONTAINS} or {@link #CONTAINED})</li>
		 *         <li><b>1</b> if the middle of the left range is greater than the middle of the right range</li>
		 *         </ul>
		 */
		public int getSide() {
			return side;
		}

		/**
		 * @return
		 *         <ul>
		 *         <li><b>-1</b> if the left range's {@link #getLowerBound() lower bound} is less than that of the right range</li>
		 *         <li><b>0</b> if the left range's {@link #getLowerBound() lower bound} is equal to that of the right range</li>
		 *         <li><b>1</b> if the left range's {@link #getLowerBound() lower bound} is greater than that of the right range</li>
		 *         </ul>
		 * 
		 */
		public int getLowCompare() {
			return lowCompare;
		}

		/**
		 * @return
		 *         <ul>
		 *         <li><b>-1</b> if the left range's {@link #getUpperBound() upper bound} is less than that of the right range</li>
		 *         <li><b>0</b> if the left range's {@link #getUpperBound() upper bound} is equal to that of the right range</li>
		 *         <li><b>1</b> if the left range's {@link #getUpperBound() upper bound} is greater than that of the right range</li>
		 *         </ul>
		 * 
		 */
		public int getHighCompare() {
			return highCompare;
		}

		/**
		 * @return Whether any values may exist (assuming the set of values is continuous) that are {@link Range#contains(Object) contained}
		 *         by both ranges
		 */
		public boolean overlaps() {
			return isOverlap;
		}

		/**
		 * @return Whether all possible values {@link Range#contains(Object) contained} in the left range are also contained in the right
		 *         range
		 */
		public boolean isContained() {
			return lowCompare >= 0 && highCompare <= 0;
		}

		/**
		 * @return Whether all possible values {@link Range#contains(Object) contained} in the right range are also contained in the left
		 *         range
		 */
		public boolean contains() {
			return lowCompare <= 0 && highCompare >= 0;
		}

		/** @return Whether the set of values {@link Range#contains(Object) contained} in both ranges are exactly the same */
		public boolean equals() {
			return this == EQUAL;
		}

		/** @return What the result of the comparison would have been if the argument ranges had been switched */
		public RangeCompareResult reverse() {
			switch (this) {
			case LESS:
				return GREATER;
			case OVERLAP_LOW:
				return OVERLAP_HIGH;
			case CONTAINS_LOW:
				return CONTAINED_HIGH;
			case CONTAINS:
				return CONTAINED;
			case CONTAINED_LOW:
				return CONTAINS_HIGH;
			case EQUAL:
				return this;
			case CONTAINS_HIGH:
				return CONTAINED_LOW;
			case CONTAINED:
				return CONTAINS;
			case CONTAINED_HIGH:
				return CONTAINS_LOW;
			case OVERLAP_HIGH:
				return OVERLAP_LOW;
			case GREATER:
				return LESS;
			}
			throw new IllegalStateException("Unrecognized comparison: " + this);
		}
	}

	private static final Bound<?> EMPTY_LOW = new Bound<>(null, false).setLower(true);
	private static final Bound<?> EMPTY_HIGH = new Bound<>(null, false).setLower(false);
	private static final Bound<?> EMPTY_EITHER = new Bound<>(null, false);
	private static final Range<?> ALL = new Range<>(null, (Bound<Object>) EMPTY_LOW, (Bound<Object>) EMPTY_HIGH);

	/**
	 * @param <C> The type of the range set
	 * @return A bound that {@link Bound#contains(Object, Comparator) contains} all values in the set
	 */
	public static <C> Bound<C> emptyBound() {
		return (Bound<C>) EMPTY_EITHER;
	}

	/**
	 * @param <C> The type of the range set
	 * @param value The value for the bound
	 * @param closed Whether the given value is actually {@link Bound#contains(Object, Comparator) contained} in the bound
	 * @return The valued bound
	 */
	public static <C> Bound<C> bound(C value, boolean closed) {
		return new Bound<>(value, closed);
	}

	/**
	 * @param <C> The type of the range set
	 * @param value The value for the bound
	 * @return A bound with the given value that does <b>NOT</b> actually {@link Bound#contains(Object, Comparator) contain} the value
	 */
	public static <C> Bound<C> open(C value) {
		return new Bound<>(value, false);
	}

	/**
	 * @param <C> The type of the range set
	 * @param value The value for the bound
	 * @return A bound with the given value that also {@link Bound#contains(Object, Comparator) contains} the value
	 */
	public static <C> Bound<C> closed(C value) {
		return new Bound<>(value, true);
	}

	/**
	 * @param <C> The type of the range set
	 * @return A range that contains all values in the set
	 */
	public static <C> Range<C> all() {
		return (Range<C>) ALL;
	}

	/**
	 * @param <C> The type of the range set
	 * @param lowerBound The lower bound for the set
	 * @param upperBound The upper bound for the set
	 * @param compare The comparator for testing values
	 * @return The new range
	 */
	public static <C> Range<C> of(Bound<C> lowerBound, Bound<C> upperBound, Comparator<? super C> compare) {
		return new Range<>(compare, lowerBound, upperBound);
	}

	/**
	 * @param <C> The type of the range set
	 * @param lowerBound The lower bound for the set
	 * @param upperBound The upper bound for the set
	 * @return The new range
	 */
	public static <C extends Comparable<C>> Range<C> of(Bound<C> lowerBound, Bound<C> upperBound) {
		return of(lowerBound, upperBound, LambdaUtils.COMPARABLE_COMPARE);
	}

	/**
	 * @param <C> The type of the range set
	 * @param value The value for the boundary
	 * @param compare The comparator for testing values
	 * @return A range that contains all values equal to or greater than the given value
	 */
	public static <C> Range<C> atLeast(C value, Comparator<? super C> compare) {
		return new Range<>(compare, new Bound<>(value, true), emptyBound());
	}

	/**
	 * @param <C> The type of the range set
	 * @param value The value for the boundary
	 * @param compare The comparator for testing values
	 * @return A range that contains all values less than or equal to the given value
	 */
	public static <C> Range<C> atMost(C value, Comparator<? super C> compare) {
		return new Range<>(compare, emptyBound(), new Bound<>(value, true));
	}

	/**
	 * @param <C> The type of the range set
	 * @param value The value for the boundary
	 * @param compare The comparator for testing values
	 * @return A range that contains all values greater than the given value
	 */
	public static <C> Range<C> greaterThan(C value, Comparator<? super C> compare) {
		return new Range<>(compare, new Bound<>(value, false), emptyBound());
	}

	/**
	 * @param <C> The type of the range set
	 * @param value The value for the boundary
	 * @param compare The comparator for testing values
	 * @return A range that contains all values less than the given value
	 */
	public static <C> Range<C> lessThan(C value, Comparator<? super C> compare) {
		return new Range<>(compare, emptyBound(), new Bound<>(value, false));
	}

	/**
	 * @param <C> The type of the range set
	 * @param value The value for the boundary
	 * @param compare The comparator for testing values
	 * @return A range that contains only the given value
	 */
	public static <C> Range<C> exactly(C value, Comparator<? super C> compare) {
		return new Range<>(compare, new Bound<>(value, true), new Bound<>(value, true));
	}

	/**
	 * @param <C> The type of the range set
	 * @param low The low end of the range
	 * @param withLow Whether the low value should be {@link #contains(Object) contained} in the range
	 * @param high The high end of the range
	 * @param withHigh Whether the high value should be {@link #contains(Object) contained} in the range
	 * @param compare The comparator for testing values
	 * @return A range that contains all values between the given values
	 */
	public static <C> Range<C> between(C low, boolean withLow, C high, boolean withHigh, Comparator<? super C> compare) {
		return new Range<>(compare, new Bound<>(low, withLow), new Bound<>(high, withHigh));
	}

	/**
	 * @param <C> The type of the range set
	 * @param value The value for the boundary
	 * @return A range that contains all values equal to or greater than the given value
	 */
	public static <C extends Comparable<C>> Range<C> atLeast(C value) {
		return atLeast(value, LambdaUtils.COMPARABLE_COMPARE);
	}

	/**
	 * @param <C> The type of the range set
	 * @param value The value for the boundary
	 * @return A range that contains all values less than or equal to the given value
	 */
	public static <C extends Comparable<C>> Range<C> atMost(C value) {
		return atMost(value, LambdaUtils.COMPARABLE_COMPARE);
	}

	/**
	 * @param <C> The type of the range set
	 * @param value The value for the boundary
	 * @return A range that contains all values greater than the given value
	 */
	public static <C extends Comparable<C>> Range<C> greaterThan(C value) {
		return greaterThan(value, LambdaUtils.COMPARABLE_COMPARE);
	}

	/**
	 * @param <C> The type of the range set
	 * @param value The value for the boundary
	 * @return A range that contains all values less than the given value
	 */
	public static <C extends Comparable<C>> Range<C> lessThan(C value) {
		return lessThan(value, LambdaUtils.COMPARABLE_COMPARE);
	}

	/**
	 * @param <C> The type of the range set
	 * @param value The value for the boundary
	 * @return A range that contains only the given value
	 */
	public static <C extends Comparable<C>> Range<C> exactly(C value) {
		return exactly(value, LambdaUtils.COMPARABLE_COMPARE);
	}

	/**
	 * @param <C> The type of the range set
	 * @param low The low end of the range
	 * @param withLow Whether the low value should be {@link #contains(Object) contained} in the range
	 * @param high The high end of the range
	 * @param withHigh Whether the high value should be {@link #contains(Object) contained} in the range
	 * @return A range that contains all values between the given values
	 */
	public static <C extends Comparable<C>> Range<C> between(C low, boolean withLow, C high, boolean withHigh) {
		return between(low, withLow, high, withHigh, LambdaUtils.COMPARABLE_COMPARE);
	}

	private final Comparator<? super C> theCompare;
	private final Bound<C> theLowerBound;
	private final Bound<C> theUpperBound;

	Range(Comparator<? super C> compare, Bound<C> lowerBound, Bound<C> upperBound) {
		theCompare = compare;
		if (lowerBound == EMPTY_EITHER)
			theLowerBound = (Bound<C>) EMPTY_LOW;
		else {
			if (lowerBound.isLowerBound == null)
				lowerBound.setLower(true);
			else if (!lowerBound.isLowerBound.booleanValue())
				throw new IllegalStateException("Given low bound is used as a high bound elsewhere");
			theLowerBound = lowerBound;
		}
		if (upperBound == EMPTY_EITHER)
			theUpperBound = (Bound<C>) EMPTY_HIGH;
		else {
			if (upperBound.isLowerBound == null)
				upperBound.setLower(false);
			else if (upperBound.isLowerBound.booleanValue())
				throw new IllegalStateException("Given high bound is used as a low bound elsewhere");
			theUpperBound = upperBound;
		}

		if (theLowerBound.isPresent() && theUpperBound.isPresent()) {
			int comp = compare.compare(theLowerBound.getValue(), theUpperBound.getValue());
			if (comp > 0)
				throw new IllegalArgumentException("Lower bound value (" + theLowerBound.getValue()
					+ ") is greater than upper bound value (" + theUpperBound.getValue() + ")");

		}
	}

	/**
	 * Gets the lower bound of this range. Any values less than the lower bound will not be {@link #contains(Object) contained} in the
	 * range.
	 * 
	 * @return The lower bound of this range
	 */
	public Bound<C> getLowerBound() {
		return theLowerBound;
	}

	/**
	 * Gets the lower bound of this range. Any values greater than the upper bound will not be {@link #contains(Object) contained} in the
	 * range.
	 * 
	 * @return The upper bound of this range
	 */
	public Bound<C> getUpperBound() {
		return theUpperBound;
	}

	/**
	 * @param value The value to compare with this range
	 * @return
	 *         <ul>
	 *         <li><b>-1</b> if the value is less than this range's {@link #getLowerBound() lower bound}</li>
	 *         <li><b>1</b> if the value is greater than this range's {@link #getUpperBound() upper bound}</li>
	 *         <li><b>0</b> if the value is between this range's bounds</li>
	 *         </ul>
	 */
	@Override
	public int compareTo(C value) {
		if (!theLowerBound.contains(value, theCompare))
			return -1;
		else if (!theUpperBound.contains(value, theCompare))
			return 1;
		else
			return 0;
	}

	/**
	 * @param value The value to test
	 * @return Whether the value is contained in this range. This is the same as <code>{@link #compareTo(Object) compareTo}(value)==0</code>
	 */
	public boolean contains(C value) {
		return compareTo(value) == 0;
	}

	/**
	 * Compares this range with another. The result contains information about the relative positions of both end points of both ranges.
	 * 
	 * @param other The range to compare with this one
	 * @return The comparison result
	 */
	public RangeCompareResult compareTo(Range<C> other) {
		int lowComp = theLowerBound.compareTo(other.theLowerBound, theCompare);
		int highComp = theUpperBound.compareTo(other.theUpperBound, theCompare);

		if (lowComp < 0) {
			if (highComp < 0) {
				// Our high bound and their low bound must both be present
				int comp = theCompare.compare(theUpperBound.getValue(), other.theLowerBound.getValue());
				if (comp < 0)
					return RangeCompareResult.LESS;
				else if (comp == 0) {
					if (theUpperBound.isClosed() && other.theLowerBound.isClosed())
						return RangeCompareResult.OVERLAP_LOW;
					else
						return RangeCompareResult.LESS;
				} else
					return RangeCompareResult.OVERLAP_LOW;
			} else if (highComp == 0)
				return RangeCompareResult.CONTAINS_LOW;
			else
				return RangeCompareResult.CONTAINS;
		} else if (lowComp == 0) {
			if (highComp < 0)
				return RangeCompareResult.CONTAINED_LOW;
			else if (highComp == 0)
				return RangeCompareResult.EQUAL;
			else
				return RangeCompareResult.CONTAINS_HIGH;
		} else {
			if (highComp < 0)
				return RangeCompareResult.CONTAINED;
			else if (highComp == 0)
				return RangeCompareResult.CONTAINED_HIGH;
			else {
				// Our low bound and their high bound must both be present
				int comp = theCompare.compare(theLowerBound.getValue(), other.theUpperBound.getValue());
				if (comp > 0)
					return RangeCompareResult.GREATER;
				else if (comp == 0) {
					if (theLowerBound.isClosed() && other.theUpperBound.isClosed())
						return RangeCompareResult.OVERLAP_HIGH;
					else
						return RangeCompareResult.GREATER;
				} else
					return RangeCompareResult.OVERLAP_HIGH;
			}
		}
	}

	@Override
	public int hashCode() {
		return Integer.reverse(theLowerBound.hashCode()) ^ theUpperBound.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof Range))
			return false;
		return theLowerBound.equals(((Range<?>) obj).theLowerBound) && theUpperBound.equals(((Range<?>) obj).theUpperBound);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		theLowerBound.append(str);
		str.append(',');
		theUpperBound.append(str);
		return str.toString();
	}
}

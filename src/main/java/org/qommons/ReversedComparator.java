package org.qommons;

import java.util.Comparator;

/**
 * A simple comparator that is the reverse of another. This class handles {@link Comparator#reversed()} better, as well as
 * {@link #hashCode()}, {@link #equals(Object)}, and {@link #toString()}
 * 
 * @param <T> The type of object that this comparator can compare
 */
public class ReversedComparator<T> implements Comparator<T> {
	private final Comparator<T> theReversed;

	/** @param reversed The comparator that this will be the reverse of */
	public ReversedComparator(Comparator<T> reversed) {
		theReversed = reversed;
	}

	@Override
	public int compare(T o1, T o2) {
		return -theReversed.compare(o1, o2);
	}

	@Override
	public Comparator<T> reversed() {
		return theReversed;
	}

	@Override
	public int hashCode() {
		return -theReversed.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof ReversedComparator))
			return false;
		return theReversed.equals(((ReversedComparator<?>) obj).theReversed);
	}

	@Override
	public String toString() {
		return "reverse(" + theReversed + ")";
	}

	/**
	 * @param <T> The type to compare
	 * @param compare The comparator to reverse
	 * @return The reversed comparator
	 */
	public static <T> Comparator<T> reverse(Comparator<T> compare) {
		return compare instanceof ReversedComparator ? compare.reversed() : new ReversedComparator<>(compare);
	}
}

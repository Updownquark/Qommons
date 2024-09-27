package org.qommons.collect;

import java.util.Comparator;

/**
 * A Comparator that tolerates null values
 * 
 * @param <T> The type of values to compare
 */
public class NullTolerantComparator<T> implements Comparator<T> {
	private final Comparator<T> theWrapped;
	private final boolean nullsFirst;

	/**
	 * @param wrapped The comparator to sort non-null values
	 * @param nullsFirst Whether null values should sort to the beginning or end of a collection
	 */
	public NullTolerantComparator(Comparator<T> wrapped, boolean nullsFirst) {
		theWrapped = wrapped;
		this.nullsFirst = nullsFirst;
	}

	@Override
	public int compare(T o1, T o2) {
		if (o1 == null) {
			if (o2 == null)
				return 0;
			else if (nullsFirst)
				return -1;
			else
				return 1;
		} else if (o2 == null) {
			if (nullsFirst)
				return 1;
			else
				return -1;
		} else
			return theWrapped.compare(o1, o2);
	}

	@Override
	public Comparator<T> reversed() {
		return new NullTolerantComparator<>(theWrapped.reversed(), !nullsFirst);
	}

	@Override
	public int hashCode() {
		return theWrapped.hashCode() + (nullsFirst ? 0x10000 : 0);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof NullTolerantComparator))
			return false;
		NullTolerantComparator<?> other = (NullTolerantComparator<?>) obj;
		return theWrapped.equals(other.theWrapped) && nullsFirst == other.nullsFirst;
	}

	@Override
	public String toString() {
		return theWrapped.toString() + " (nulls " + (nullsFirst ? "first)" : "last)");
	}
}
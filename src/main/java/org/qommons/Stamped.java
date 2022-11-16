package org.qommons;

import java.util.Collection;
import java.util.function.ToLongFunction;

/** An Object that provides a stamp that changes when it is modified */
public interface Stamped {
	/**
	 * <p>
	 * Obtains a stamp with the current status of modifications to this object. Whenever this object is modified, the stamp changes. Thus 2
	 * stamps can be compared to determine whether an object has changed in between 2 calls to this method.
	 * </p>
	 * <p>
	 * The value returned from this method is <b>ONLY</b> for comparison. The value itself is not guaranteed to reveal anything about this
	 * structure or its history, e.g. the actual number of times it has been modified. Also, if 2 stamps obtained from this method are
	 * different, this does not guarantee that the structure was actually changed in any way, only that it might have been. It also cannot
	 * be 100% guaranteed that no modification (of the corresponding type) has been made to the structure if 2 stamps match; but an effort
	 * shall be made so that stamps never repeat during the lifetime of an application and to avoid changing the stamps when no modification
	 * is performed, if possible.
	 * </p>
	 * 
	 * @return The stamp for comparison
	 */
	long getStamp();

	/**
	 * Creates a composite stamp from a collection of other stamped items
	 * 
	 * @param <T> The type of the stamped values
	 * @param values The values to create the composite stamp for
	 * @param stampFn The function to produce stamps from the values
	 * @return The composite stamp
	 */
	public static <T> long compositeStamp(Collection<? extends T> values, ToLongFunction<? super T> stampFn) {
		if (values.isEmpty())
			return 0;
		long stamp = 0;
		int shift = Math.max(1, 63 / values.size());
		int i = 0;
		for (T value : values) {
			if (value == null)
				continue;
			long valueStamp = stampFn.applyAsLong(value);
			if (i > 0)
				valueStamp = Long.rotateRight(valueStamp, shift * i);
			stamp ^= valueStamp;
			i++;
		}
		return stamp;
	}

	/**
	 * Creates a composite stamp from a collection of other stamped items
	 * 
	 * @param <T> The type of the stamped values
	 * @param values The stamped values to create the composite stamp for
	 * @return The composite stamp
	 */
	public static <T> long compositeStamp(Collection<? extends Stamped> values) {
		if (values.isEmpty())
			return 0;
		long stamp = 0;
		int shift = Math.max(1, 63 / values.size());
		int i = 0;
		for (Stamped value : values) {
			if (value == null)
				continue;
			long valueStamp = value.getStamp();
			if (i > 0)
				valueStamp = Long.rotateRight(valueStamp, shift * i);
			stamp ^= valueStamp;
			i++;
		}
		return stamp;
	}

	/**
	 * Creates a composite stamp from an array of stamps
	 * 
	 * @param stamps The stamps to create the composite stamp for
	 * @return The composite stamp
	 */
	static long compositeStamp(long... stamps) {
		if (stamps.length == 0)
			return 0;
		long stamp = 0;
		int shift = Math.max(1, 63 / stamps.length);
		int i = 0;
		for (long s : stamps) {
			if (i > 0)
				s = Long.rotateRight(s, shift * i);
			stamp ^= s;
			i++;
		}
		return stamp;
	}
}

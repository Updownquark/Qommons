package org.qommons;

import java.util.Collection;
import java.util.Iterator;
import java.util.PrimitiveIterator;
import java.util.function.ToLongFunction;
import java.util.stream.LongStream;

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
	 * @param values The stamped values to create the composite stamp for
	 * @return The composite stamp
	 */
	public static long compositeStamp(Collection<? extends Stamped> values) {
		switch (values.size()) {
		case 0:
			return 0;
		case 1:
			Stamped value0 = values.iterator().next();
			return value0 == null ? 0 : value0.getStamp();
		case 2:
			Iterator<? extends Stamped> iter = values.iterator();
			value0 = iter.next();
			Stamped value1 = iter.next();
			return compositeOf2Stamps(//
				value0 == null ? 0 : value0.getStamp(), //
				value1 == null ? 0 : value1.getStamp());
		}
		CompositeStamping stamp = new CompositeStamping(values.size());
		for (Stamped value : values)
			stamp.add(value.getStamp());
		return stamp.getStamp();
	}

	/**
	 * Creates a composite stamp from an array of other stamped items
	 * 
	 * @param values The stamped values to create the composite stamp for
	 * @return The composite stamp
	 */
	public static long compositeStamp(Stamped... values) {
		switch (values.length) {
		case 0:
			return 0;
		case 1:
			return values[0] == null ? 0 : values[0].getStamp();
		case 2:
			return compositeOf2Stamps(//
				values[0] == null ? 0 : values[0].getStamp(), //
				values[1] == null ? 0 : values[1].getStamp());
		}
		CompositeStamping stamp = new CompositeStamping(values.length);
		for (Stamped value : values)
			stamp.add(value == null ? 0 : value.getStamp());
		return stamp.getStamp();
	}

	/**
	 * Creates a composite stamp from a collection of other stamped items
	 * 
	 * @param <T> The type of the stamped values
	 * @param values The values to create the composite stamp for
	 * @param stampFn The function to produce stamps from the values
	 * @return The composite stamp
	 */
	public static <T> long compositeStamp(Collection<? extends T> values, ToLongFunction<? super T> stampFn) {
		switch (values.size()) {
		case 0:
			return 0;
		case 1:
			return stampFn.applyAsLong(values.iterator().next());
		case 2:
			Iterator<? extends T> iter = values.iterator();
			return compositeOf2Stamps(//
				stampFn.applyAsLong(iter.next()), //
				stampFn.applyAsLong(iter.next()));
		}
		CompositeStamping stamp = new CompositeStamping(values.size());
		for (T value : values)
			stamp.add(stampFn.applyAsLong(value));
		return stamp.getStamp();
	}

	/**
	 * Creates a composite stamp from 2 component stamps
	 * 
	 * @param stamp1 The first stamp to create the composite stamp for
	 * @param stamp2 The second stamp to create the composite stamp for
	 * @return The composite stamp
	 */
	static long compositeOf2Stamps(long stamp1, long stamp2) {
		return (stamp1 ^ Long.rotateRight(stamp2, 31)) + stamp2;
	}

	/**
	 * Creates a composite stamp from an array of stamps
	 * 
	 * @param stamps The stamps to create the composite stamp for
	 * @return The composite stamp
	 */
	static long compositeStamp(long... stamps) {
		switch (stamps.length) {
		case 0:
			return 0;
		case 1:
			return stamps[0];
		case 2:
			return compositeOf2Stamps(stamps[0], stamps[1]);
		}
		CompositeStamping stamp = new CompositeStamping(stamps.length);
		for (long s : stamps)
			stamp.add(s);
		return stamp.getStamp();
	}

	/**
	 * Creates a composite stamp from a stream of stamps
	 * 
	 * @param stamps The stamps to create the composite stamp for
	 * @param count The number of stamps in the stream
	 * @return The composite stamp
	 */
	static long compositeStamp(LongStream stamps, int count) {
		switch (count) {
		case 0:
			return 0;
		case 1:
			return stamps.iterator().nextLong();
		case 2:
			PrimitiveIterator.OfLong stampIter = stamps.iterator();
			return compositeOf2Stamps(stampIter.nextLong(), stampIter.nextLong());
		}
		CompositeStamping stamp = new CompositeStamping(count);
		PrimitiveIterator.OfLong stampsIter = stamps.iterator();
		while (stampsIter.hasNext())
			stamp.add(stampsIter.nextLong());
		return stamp.getStamp();
	}

	/** Makes a composite stamp from multiple other stamps */
	public class CompositeStamping implements Stamped {
		private final int theShift;
		private int theStampIndex;
		private long theStamp;

		/** @param count The number of stamps to expect */
		public CompositeStamping(int count) {
			theShift = Math.max(1, 63 / count);
		}

		/** @param valueStamp The stamp to add to this composite */
		public void add(long valueStamp) {
			if (theStampIndex == 0)
				theStamp = valueStamp;
			else if (valueStamp != 0) {
				// I'm changing this to use both XOR and addition operators in generating the composite stamp
				// When using XOR only, I found that in some cases, e.g. with composite structures as op1(op2(v1, v2), op2(v1)),
				// a value's stamp can annihilate itself in the composite. Addition prevents this.
				// The XOR operation is still necessary so that ordering of the stamps is important.
				theStamp = (theStamp ^ Long.rotateRight(valueStamp, theShift * theStampIndex)) + valueStamp;
			}
			theStampIndex++;
		}

		/** @return The compiled composite stamp */
		@Override
		public long getStamp() {
			return theStamp;
		}
	}
}

package org.qommons;

import org.qommons.collect.NullTolerantComparator;

/** An item that has a name */
public interface Named {
	/** @return The name of the item */
	public String getName();

	/** A simple, abstract {@link Named} implementation */
	class AbstractNamed implements Named {
		private String theName;

		protected AbstractNamed(String name) {
			theName = name;
		}

		@Override
		public String getName() {
			return theName;
		}

		protected AbstractNamed setName(String name) {
			theName = name;
			return this;
		}

		@Override
		public String toString() {
			return getName();
		}
	}

	/** A comparator to sort named items in a way that sorts embedded numbers well */
	public static final NullTolerantComparator<Named> DISTINCT_NUMBER_TOLERANT = new NullTolerantComparator<>(LambdaUtils
		.printableComparator((n1, n2) -> StringUtils.compareNumberTolerant(n1.getName(), n2.getName(), true, true), () -> "By Name"),
		false);
}

package org.qommons;

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
}

package org.qommons;

public interface Named {
	public String getName();

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

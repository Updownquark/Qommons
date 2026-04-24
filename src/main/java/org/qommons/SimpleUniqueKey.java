package org.qommons;

/** A simple class for implementing a unique key. This class accepts an implementation for {@link #toString()} and does nothing else. */
public final class SimpleUniqueKey {
	private final String theName;

	/** @param name The {@link #toString()} value for this key */
	public SimpleUniqueKey(String name) {
		theName = name;
	}

	@Override
	public String toString() {
		return theName;
	}
}

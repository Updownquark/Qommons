package org.qommons;

/** A ternian is a boolean that can have an additional NONE state, indicating that it is neither true nor false */
public enum Ternian {
	/** Logically equivalent to {@link Boolean#FALSE} */
	FALSE,
	/** Neither true nor false */
	NONE,
	/** Logically equivalent to {@link Boolean#TRUE} */
	TRUE;
}

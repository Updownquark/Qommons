package org.qommons.io;

/** A component of a {@link ParsedAdjustable} value */
public interface AdjustableComponent {
	/** @return The character position at which this component starts */
	int getStart();

	/** @return The caracter position at which this component ends (exclusive) */
	int getEnd();
}

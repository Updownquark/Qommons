package org.qommons.config;

/** An item that was defined in a file, and makes available the line in the file where it was defined */
public interface LineNumbered {
	/** @return The line in the file where this item was defined */
	int getLineNumber();
}

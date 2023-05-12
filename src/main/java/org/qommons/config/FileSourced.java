package org.qommons.config;

import org.qommons.io.ContentPosition;

/** An item that was defined in a file, and makes available the position in the file where it was defined */
public interface FileSourced {
	/** @return The position in the file where this item was defined */
	ContentPosition getFilePosition();
}

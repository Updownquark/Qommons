package org.qommons.config;

import org.qommons.Named;

/** Basically anything that is owned directly by a toolkit */
public interface QonfigType extends Named, LineNumbered {
	/** @return The toolkit that declared this type */
	public QonfigToolkit getDeclarer();
}

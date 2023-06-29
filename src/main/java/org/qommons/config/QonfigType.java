package org.qommons.config;

import org.qommons.Named;
import org.qommons.SelfDescribed;

/** Basically anything that is owned directly by a toolkit */
public interface QonfigType extends Named, FileSourced, SelfDescribed {
	/** @return The toolkit that declared this type */
	public QonfigToolkit getDeclarer();
}

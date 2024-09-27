package org.qommons.config;

import org.qommons.Named;
import org.qommons.SelfDescribed;
import org.qommons.io.LocatedPositionedContent;

/** Basically anything that is owned directly by a toolkit */
public interface QonfigType extends Named, FileSourced, SelfDescribed {
	/** @return The toolkit that declared this type */
	QonfigToolkit getDeclarer();

	/** @return This type's declared position, as located within its declaring document */
	default LocatedPositionedContent getLocatedPosition() {
		return LocatedPositionedContent.of(getDeclarer().getLocationString(), getFilePosition());
	}
}

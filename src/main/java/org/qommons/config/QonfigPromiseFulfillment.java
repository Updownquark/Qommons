package org.qommons.config;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public interface QonfigPromiseFulfillment {
	QonfigToolkit.ToolkitDef getToolkit();

	String getQonfigType();

	void setQonfigType(QonfigElementOrAddOn qonfigType);

	void fulfillPromise(PartialQonfigElement promise, QonfigElement.Builder parent, List<ElementQualifiedParseItem> declaredRoles,
		Set<QonfigAddOn> inheritance, QonfigParser parser, QonfigParseSession session) throws IOException, QonfigParseException;
}

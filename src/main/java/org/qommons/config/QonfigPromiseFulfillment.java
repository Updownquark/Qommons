package org.qommons.config;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.qommons.config.QonfigElement.AttributeValueInput;

public interface QonfigPromiseFulfillment {
	QonfigToolkit.ToolkitDef getToolkit();

	String getQonfigType();

	void setQonfigType(QonfigElementOrAddOn qonfigType);

	void fulfillPromise(PartialQonfigElement promise, QonfigElement.Builder parent, List<ElementQualifiedParseItem> declaredRoles,
		Set<QonfigAddOn> inheritance, Map<ElementQualifiedParseItem, AttributeValueInput> attributes, QonfigParser parser,
		QonfigParseSession session) throws IOException, QonfigParseException;
}

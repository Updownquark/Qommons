package org.qommons.config;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.qommons.config.QonfigElement.AttributeValueInput;

/**
 * A piece of code that knows how to fulfill a certain type of Qonfig element promise, loading and fulfilling appropriate external content
 */
public interface QonfigPromiseFulfillment {
	/** @return The toolkit owning the promise type that this fulfillment can fulfill */
	QonfigToolkit.ToolkitDef getToolkit();

	/** @return The name of the promise type that this fulfillment can fulfill */
	String getQonfigType();

	/** @param qonfigType The actual Qonfig type that this fulfillment references */
	void setQonfigType(QonfigElementOrAddOn qonfigType);

	/**
	 * @param promise The promise to fulfill
	 * @param parent The builder of the parent element to fulfill the promise in
	 * @param declaredRoles The declared roles that the child is to fulfill
	 * @param inheritance The inheritance for the fulfilled external content
	 * @param attributes The attribute values for the fulfilled external content
	 * @param parser The parser to parse the content
	 * @param session The session parsing the promise
	 * @throws IOException If the external content could not be read
	 * @throws QonfigParseException If the external content could not be loaded or fulfilled
	 */
	void fulfillPromise(PartialQonfigElement promise, QonfigElement.Builder parent, List<ElementQualifiedParseItem> declaredRoles,
		Set<QonfigAddOn> inheritance, Map<ElementQualifiedParseItem, AttributeValueInput> attributes, QonfigParser parser,
		QonfigParseSession session) throws IOException, QonfigParseException;
}

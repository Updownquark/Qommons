package org.qommons.config;

import java.io.IOException;

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
	 * @param parser The parser to parse the content
	 * @param session The session parsing the promise
	 * @throws IOException If the external content could not be read
	 * @throws QonfigParseException If the external content could not be loaded or fulfilled
	 */
	void fulfillPromise(QonfigElement promise, QonfigElement.Builder parent, QonfigParser parser, QonfigParseSession session)
		throws IOException, QonfigParseException;
}

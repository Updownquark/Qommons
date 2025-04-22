package org.qommons.config;

import java.io.IOException;

import org.qommons.MultiInheritanceSet;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.io.ErrorReporting;

/**
 * A piece of code that knows how to fulfill a certain type of Qonfig element promise, loading and fulfilling appropriate external content
 */
public interface QonfigPromiseFulfillment {
	public interface PromiseAttributeGetter {
		QonfigValue getAttribute(String name);

		ErrorReporting reporting();
	}

	public static class PromisedType {
		public final QonfigElementDef type;
		public final MultiInheritanceSet<QonfigAddOn> inheritance;

		public PromisedType(QonfigElementDef type, MultiInheritanceSet<QonfigAddOn> inheritance) {
			this.type = type;
			this.inheritance = inheritance == null ? MultiInheritanceSet.empty() : MultiInheritanceSet.unmodifiable(inheritance);
		}
	}

	/** @return The toolkit owning the promise type that this fulfillment can fulfill */
	QonfigToolkit.ToolkitDef getToolkit();

	/** @param promiseType The actual Qonfig promise type that this fulfillment fulfills */
	void setPromiseType(QonfigPromiseDef promiseType);

	PromisedType getPromisedType(String typeName, QonfigElementDef superType, PromiseAttributeGetter attrs);

	PromisedType getPromisedType(QonfigPromiseDef type, PartialQonfigElement parent, PromiseAttributeGetter attrs);

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

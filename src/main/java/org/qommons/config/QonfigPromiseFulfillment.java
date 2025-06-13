package org.qommons.config;

import java.io.IOException;

import org.qommons.MultiInheritanceSet;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.io.ErrorReporting;

/**
 * A piece of code that knows how to fulfill a certain type of Qonfig element promise, loading and fulfilling appropriate external content
 */
public interface QonfigPromiseFulfillment {
	/**
	 * <p>
	 * Provides access to a Qonfig declaration or element's attribute values before the element has had opportunity to be parsed completely.
	 * </p>
	 * <p>
	 * This is needed because the parser needs information about the type of an element before it parse its attributes.
	 * </p>
	 */
	public interface PromiseAttributeGetter {
		/**
		 * @param name The name of the attribute to get the value of
		 * @return The value of the given attribute, or null if it was not present
		 */
		QonfigValue getAttribute(String name);

		/** @return The error reporting for the element */
		ErrorReporting reporting();
	}

	/** Represents the optional element type and set of add-on types that a promised type extends/inherits */
	public static class PromisedType {
		/** The &lt;element-def> that the promised type promises to extend, or null if no such promise is made */
		public final QonfigElementDef type;
		/** The &lt;add-on>s that the promised type promises to inherit */
		public final MultiInheritanceSet<QonfigAddOn> inheritance;

		/**
		 * @param type The &lt;element-def> that the promised type promises to extend, or null if no such promise is made
		 * @param inheritance The &lt;add-on>s that the promised type promises to inherit
		 */
		public PromisedType(QonfigElementDef type, MultiInheritanceSet<QonfigAddOn> inheritance) {
			this.type = type;
			this.inheritance = inheritance == null ? MultiInheritanceSet.empty() : MultiInheritanceSet.unmodifiable(inheritance);
		}
	}

	/** @return The toolkit owning the promise type that this fulfillment can fulfill */
	QonfigToolkit.ToolkitDef getToolkit();

	/** @param promiseType The actual Qonfig promise type that this fulfillment fulfills */
	void setPromiseType(QonfigPromiseDef promiseType);

	/**
	 * Gets static promised type information for a promise declaration
	 * 
	 * @param typeName The name of the promise type
	 * @param superType The super type declared by the promise declaration
	 * @param attrs Provides access to the attributes of the promise declaration element before the declaration has been completely parsed
	 * @return The promised type for all instances of the promise
	 */
	PromisedType getPromisedType(String typeName, QonfigElementDef superType, PromiseAttributeGetter attrs);

	/**
	 * Gets instance promised type information for a promise usage
	 * 
	 * @param type The promise type
	 * @param parent The parent the promise child is located within
	 * @param attrs Provides access to the promise reference's attribute data before it has been completely parsed
	 * @return The promised type for this instance of the promise
	 */
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

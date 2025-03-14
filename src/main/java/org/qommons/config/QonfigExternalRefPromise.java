package org.qommons.config;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.qommons.config.QonfigElement.AttributeValue;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedPositionedContent;
import org.qommons.io.PositionedContent;
import org.qommons.io.TextParseException;

/** Default promise fulfillment, fulfills Qonfig-Reference.external-reference */
public class QonfigExternalRefPromise implements QonfigPromiseFulfillment {
	/** The definition of the Qonfig-Reference toolkit that this fulfillment's promise type is in */
	public static final QonfigToolkit.ToolkitDef REFERENCE_TOOLKIT = new QonfigToolkit.ToolkitDef("Qonfig-Reference", 0, 1);
	/** This fulfillment's promise type */
	public static final String EXT_REFERENCE_TYPE = "external-reference";
	/** The name of the external document element this type loads */
	public static final String EXT_DOCUMENT_TYPE = "external-document";

	private QonfigElementDef theExtReferenceType;
	private QonfigElementDef theExtContentType;
	private QonfigAttributeDef.Declared theReferenceAttribute;
	private QonfigAttributeDef.Declared theFulfillsAttribute;
	private QonfigChildDef.Declared theFulfillmentChild;

	private final Map<String, QonfigExternalContent> theCachedContent;

	/** Creates the promise fulfillment */
	public QonfigExternalRefPromise() {
		theCachedContent = new HashMap<>();
	}

	@Override
	public QonfigToolkit.ToolkitDef getToolkit() {
		return REFERENCE_TOOLKIT;
	}

	@Override
	public String getQonfigType() {
		return EXT_REFERENCE_TYPE;
	}

	@Override
	public void setQonfigType(QonfigElementOrAddOn qonfigType) {
		theExtReferenceType = (QonfigElementDef) qonfigType;
		theReferenceAttribute = qonfigType.getAttribute("ref").getDeclared();
		theExtContentType = qonfigType.getDeclarer().getElement(EXT_DOCUMENT_TYPE);
		theFulfillsAttribute = theExtContentType.getAttribute("fulfills").getDeclared();
		theFulfillmentChild = theExtContentType.getChild("fulfillment").getDeclared();
	}

	@Override
	public void fulfillPromise(QonfigElement promise, QonfigElement.Builder parent, QonfigParser parser,
		QonfigParseSession session) throws IOException, QonfigParseException {
		QonfigValue refValue = promise.getAttributes().get(theReferenceAttribute);
		String ref = refValue.text;
		try {
			ref = QommonsConfig.resolve(ref, refValue.fileLocation);
		} catch (IOException e) {
			session.error(e.getMessage(), e);
			return;
		}
		QonfigExternalContent content = theCachedContent.get(ref);
		if (content == null) {
			synchronized (this) {
				content = theCachedContent.get(ref);
				if (content == null) {
					// Prevent infinite recursion
					theCachedContent.put(ref,
						new QonfigExternalContent("An external resource seems to be referring to itself", null, null));
					try {
						content = resolveExternalContent(ref, parser, session, promise);
						theCachedContent.put(ref, content);
					} catch (RuntimeException e) {
						content = new QonfigExternalContent(e.getMessage(), null, e);
						theCachedContent.put(ref, content);
					}
				}
			}
		}
		if (!content.check(session))
			return;
		// Validate content is for the right external type
		if (!content.getReferenceType().isAssignableFrom(promise.getType())) {
			session.at(refValue.position)
				.error("External content at '" + refValue.text + "' fulfills " + content.getReferenceType() + ", not " + promise.getType());
			return;
		}

		fulfillWithExternalReference(parent, content, promise, parser,
			session == null ? null : session.at(content.getFulfillment().getFilePosition()));
	}

	/**
	 * @param ref The reference to the external content file to load
	 * @param parser The parser to parse the external content
	 * @param session The parse session for error handling
	 * @param promise The promise to fulfill
	 * @return The loaded external content fulfilling the promise
	 */
	protected QonfigExternalContent resolveExternalContent(String ref, QonfigParser parser, QonfigParseSession session,
		QonfigElement promise) {
		QonfigDocument doc;
		try (InputStream in = new BufferedInputStream(new URL(ref).openStream())) {
			doc = parser.parseDocument(true, ref, in);
		} catch (TextParseException e) {
			return new QonfigExternalContent(e.getMessage(),
				LocatedPositionedContent.of(ref, new PositionedContent.Simple(e.getPosition(), "")), e);
		} catch (IOException | QonfigParseException e) {
			return new QonfigExternalContent(e.getMessage(), null, e);
		}
		QonfigValue fulfills = doc.getPartialRoot().getAttributes().get(theFulfillsAttribute);
		QonfigValueType.QonfigTypeReference<?> fulfillsType = (QonfigValueType.QonfigTypeReference<?>) fulfills.value;
		if (fulfillsType == null)
			return new QonfigExternalContent("No such element found: " + fulfills.text,
				LocatedPositionedContent.of(fulfills.fileLocation, fulfills.position), null);
		if (!theExtReferenceType.isAssignableFrom(fulfillsType.reference))
			return new QonfigExternalContent(
				"Fulfills target '" + fulfillsType + "' does not extent " + theExtReferenceType
					+ ", as this external reference fulfillment expects",
				LocatedPositionedContent.of(fulfills.fileLocation, fulfills.position), null);
		return new QonfigExternalContent(doc.getPartialRoot().getChildrenByRole().get(theFulfillmentChild).getFirst(),
			fulfillsType.reference);
	}

	/**
	 * @param parent The element builder of the parent element to add the external content into
	 * @param content The external content template to fulfill the element with
	 * @param promise The promise to fulfill
	 * @param parser The parser for parsing external content
	 * @param session The parse session for error handling
	 */
	protected void fulfillWithExternalReference(QonfigElement.Builder parent, QonfigExternalContent content, QonfigElement promise,
		QonfigParser parser, QonfigParseSession session) {
		PartialQonfigElement fulfillment = content.getFulfillment();
		parent.withChild2(promise.getParentRoles(), fulfillment.getType(), child -> {
			for (QonfigAddOn inh : fulfillment.getInheritance().values())
				child.inherits(inh, false);
			for (QonfigAddOn inh : promise.getInheritance().values()) {
				if (child.isSupported(inh))
					child.inherits(inh, false);
			}
			child.fulfills(promise, content.getFulfillment());
			for (Map.Entry<QonfigAttributeDef.Declared, AttributeValue> attr : promise.getAttributes().entrySet())
				child.withAttribute(attr.getKey(), attr.getValue());
			buildContent(child, fulfillment, promise, parser, session);
		}, fulfillment.getFilePosition(), fulfillment.getDescription());
	}

	/**
	 * @param builder The element builder to build the external content into
	 * @param content The content to copy into the builder
	 * @param promise The promise to fulfill
	 * @param parser The parser for parsing external content
	 * @param session The parse session for error handling
	 */
	protected void buildContent(QonfigElement.Builder builder, PartialQonfigElement content, PartialQonfigElement promise,
		QonfigParser parser, QonfigParseSession session) {
		content.copy(builder, parser, session, session == null ? null : session.at(promise.getFilePosition()));
	}

	/** External content loaded from a file */
	public static class QonfigExternalContent {
		private final PartialQonfigElement theFulfillment;
		private final QonfigElementOrAddOn theReferenceType;
		private final String theErrorMessage;
		private final LocatedPositionedContent theErrorPosition;
		private final Throwable theErrorThrowable;

		/**
		 * Creates a successfully loaded content object
		 * 
		 * @param fulfillment The external content
		 * @param referenceType The reference type that requested the content
		 */
		public QonfigExternalContent(PartialQonfigElement fulfillment, QonfigElementOrAddOn referenceType) {
			theFulfillment = fulfillment;
			theReferenceType = referenceType;
			theErrorMessage = null;
			theErrorPosition = null;
			theErrorThrowable = null;
		}

		/**
		 * Creates an unsuccessfully loaded content object
		 * 
		 * @param errorMessage The message of the error that occurred when loading the content
		 * @param errorPosition The position in the external content file where the error occurred
		 * @param errorThrowable The error that occurred
		 */
		public QonfigExternalContent(String errorMessage, LocatedPositionedContent errorPosition, Throwable errorThrowable) {
			theErrorMessage = errorMessage;
			theErrorPosition = errorPosition;
			theErrorThrowable = errorThrowable;
			theFulfillment = null;
			theReferenceType = null;
		}

		/**
		 * @param session The session for error handling
		 * @return Whether this content was successfully loaded
		 */
		public boolean check(QonfigParseSession session) {
			if (theErrorMessage != null || theErrorThrowable != null) {
				if (theErrorThrowable instanceof QonfigParseException) {
					for (ErrorReporting.Issue issue : ((QonfigParseException) theErrorThrowable).getIssues())
						session.report(issue);
				} else {
					if (theErrorPosition != null)
						session = session.at(theErrorPosition);
					session.error(theErrorMessage, theErrorThrowable);
				}
				return false;
			} else
				return true;
		}

		/** @return The loaded external content */
		public PartialQonfigElement getFulfillment() {
			return theFulfillment;
		}

		/** @return The reference type that requested the content */
		public QonfigElementOrAddOn getReferenceType() {
			return theReferenceType;
		}
	}
}

package org.qommons.config;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedPositionedContent;
import org.qommons.io.PositionedContent;
import org.qommons.io.TextParseException;

public class QonfigExternalRefPromise implements QonfigPromiseFulfillment {
	public static final QonfigToolkit.ToolkitDef REFERENCE_TOOLKIT = new QonfigToolkit.ToolkitDef("Qonfig-Reference", 0, 1);
	public static final String EXT_REFERENCE_TYPE = "external-reference";
	public static final String EXT_CONTENT_TYPE = "external-content";

	private QonfigElementDef theExtReferenceType;
	private QonfigElementDef theExtContentType;
	private QonfigAttributeDef.Declared theReferenceAttribute;
	private QonfigAttributeDef.Declared theFulfillsAttribute;
	private QonfigChildDef.Declared theFulfillmentChild;

	private final Map<String, QonfigExternalContent> theCachedContent;

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
		theExtContentType = qonfigType.getDeclarer().getElement(EXT_CONTENT_TYPE);
		theFulfillsAttribute = theExtContentType.getAttribute("fulfills").getDeclared();
		theFulfillmentChild = theExtContentType.getChild("fulfillment").getDeclared();
	}

	@Override
	public void fulfillPromise(PartialQonfigElement promise, QonfigElement.Builder parent, List<ElementQualifiedParseItem> declaredRoles,
		Set<QonfigAddOn> inheritance, QonfigParser parser, QonfigParseSession session) throws IOException, QonfigParseException {
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

		fulfillWithExternalReference(parent, declaredRoles, inheritance, content, promise, session);
	}

	protected QonfigExternalContent resolveExternalContent(String ref, QonfigParser parser, QonfigParseSession session,
		PartialQonfigElement promise) {
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
		String ns = ((PatternMatch) fulfills.value).getGroup("ns");
		String element = ((PatternMatch) fulfills.value).getGroup("name");
		QonfigToolkit declarer;
		if (ns != null) {
			QonfigToolkit.ToolkitDef nsDef;
			try {
				nsDef = QonfigToolkit.ToolkitDef.parse(ns);
			} catch (ParseException e) {
				return new QonfigExternalContent("Could not parse toolkit definition for " + theFulfillsAttribute,
					LocatedPositionedContent.of(fulfills.fileLocation, fulfills.position), e);
			}
			declarer = session.getToolkit().getDependenciesByDefinition().getOrDefault(nsDef.name, Collections.emptyNavigableMap())//
				.get(nsDef);
			if (declarer == null)
				return new QonfigExternalContent("No such toolkit found: " + nsDef,
					LocatedPositionedContent.of(fulfills.fileLocation, fulfills.position), null);
		} else
			declarer = session.getToolkit();
		QonfigElementOrAddOn fulfillsType = declarer.getElementOrAddOn(element);
		if (fulfillsType == null)
			return new QonfigExternalContent("No such element found: " + fulfills.text,
				LocatedPositionedContent.of(fulfills.fileLocation, fulfills.position), null);
		if (!theExtReferenceType.isAssignableFrom(fulfillsType))
			return new QonfigExternalContent(
				"Fulfills target '" + fulfillsType + "' does not extent " + theExtReferenceType
					+ ", as this external reference fulfillment expects",
				LocatedPositionedContent.of(fulfills.fileLocation, fulfills.position), null);
		return new QonfigExternalContent(doc.getPartialRoot().getChildrenByRole().get(theFulfillmentChild).getFirst(), fulfillsType);
	}

	protected void fulfillWithExternalReference(QonfigElement.Builder builder, List<ElementQualifiedParseItem> declaredRoles,
		Set<QonfigAddOn> inheritance, QonfigExternalContent content, PartialQonfigElement promise,
		QonfigParseSession session) {
		PartialQonfigElement fulfillment = content.getFulfillment();
		builder.withChild(declaredRoles, fulfillment.getType(), child -> {
			for (QonfigAddOn inh : fulfillment.getInheritance().values())
				child.inherits(inh, false);
			for (QonfigAddOn inh : inheritance)
				child.inherits(inh, false);
			child.fulfills(promise, content.getFulfillment());
			buildContent(child, fulfillment, promise, session);
		}, fulfillment.getFilePosition(), fulfillment.getDescription());
	}

	protected void buildContent(QonfigElement.Builder builder, PartialQonfigElement content, PartialQonfigElement promise,
		QonfigParseSession session) {
		content.copy(builder);
	}

	public static class QonfigExternalContent {
		private final PartialQonfigElement theFulfillment;
		private final QonfigElementOrAddOn theReferenceType;
		private final String theErrorMessage;
		private final LocatedPositionedContent theErrorPosition;
		private final Throwable theErrorThrowable;

		public QonfigExternalContent(PartialQonfigElement fulfillment, QonfigElementOrAddOn referenceType) {
			theFulfillment = fulfillment;
			theReferenceType = referenceType;
			theErrorMessage = null;
			theErrorPosition = null;
			theErrorThrowable = null;
		}

		public QonfigExternalContent(String errorMessage, LocatedPositionedContent errorPosition, Throwable errorThrowable) {
			theErrorMessage = errorMessage;
			theErrorPosition = errorPosition;
			theErrorThrowable = errorThrowable;
			theFulfillment = null;
			theReferenceType = null;
		}

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

		public PartialQonfigElement getFulfillment() {
			return theFulfillment;
		}

		public QonfigElementOrAddOn getReferenceType() {
			return theReferenceType;
		}
	}
}
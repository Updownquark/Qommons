package org.qommons.config;

import java.io.IOException;
import java.util.Map;

import org.qommons.MultiInheritanceSet;
import org.qommons.config.QonfigElement.AttributeValue;
import org.qommons.config.QonfigElement.QonfigValue;

/** Default promise fulfillment for the replacement of references in external content with children specified from the reference document */
public class QonfigChildPlaceholderPromise implements QonfigPromiseFulfillment {
	/** The name of the promise element that this class fulfills */
	public static final String CHILD_PLACEHOLDER = "child-placeholder";
	/**
	 * The name of the attribute on this fulfillment's promise element that refers to the role in the external content that will be
	 * fulfilled by children from the reference
	 */
	public static final String REF_ROLE_ATTR = "ref-role";

	private QonfigPromiseDef theChildPlaceholder;
	private QonfigAttributeDef.Declared theRefRole;
	private QonfigAttributeDef.Declared theFulfillsAttribute;

	@Override
	public QonfigToolkit.ToolkitDef getToolkit() {
		return QonfigExternalRefPromise.REFERENCE_TOOLKIT;
	}

	@Override
	public void setPromiseType(QonfigPromiseDef promiseType) {
		theChildPlaceholder = promiseType;
		theRefRole = theChildPlaceholder.getAttribute(REF_ROLE_ATTR).getDeclared();
		QonfigElementDef extDoc = promiseType.getDeclarer().getElement(QonfigExternalRefPromise.EXT_DOCUMENT_TYPE);
		theFulfillsAttribute = extDoc.getAttribute(QonfigExternalRefPromise.FULFILLS).getDeclared();
		theFulfillsAttribute = promiseType.getDeclarer().getAttribute(QonfigExternalRefPromise.EXT_DOCUMENT_TYPE, "fulfills");
	}

	@Override
	public PromisedType getPromisedType(String typeName, QonfigElementDef superType, PromiseAttributeGetter attrs) {
		if (superType != null)
			return new PromisedType(((QonfigPromiseDef) superType).getPromisedType(),
				((QonfigPromiseDef) superType).getPromisedInheritance());
		else
			return null;
	}

	@Override
	public PromisedType getPromisedType(QonfigPromiseDef type, PartialQonfigElement parent, PromiseAttributeGetter attrs) {
		QonfigValue refRole = attrs.getAttribute(REF_ROLE_ATTR);
		if (refRole == null)
			return null; // This is an error, but we'll let the parser log it later
		PartialQonfigElement root = parent;
		while (root.getParent() != null)
			root = root.getParent();
		QonfigAttributeDef.Declared fulfillsAttr = parent.getDocument().getDocToolkit()
			.getAttribute(QonfigExternalRefPromise.REFERENCE_TOOLKIT + ":" + QonfigExternalRefPromise.EXT_DOCUMENT_TYPE,
				QonfigExternalRefPromise.FULFILLS)
			.getDeclared();
		QonfigElementDef fulfills = (QonfigElementDef) root.getAttribute(fulfillsAttr, QonfigValueType.QonfigTypeReference.class).reference;
		QonfigChildDef child;
		try {
			child = fulfills.getChild(refRole.text);
		} catch (IllegalArgumentException e) {
			attrs.reporting().at(refRole.position).error(e.getMessage(), e);
			return null;
		}
		if (child.getType() == null || child.getType() instanceof QonfigElementDef) {
			if (child.getInheritance().isEmpty())
				return new PromisedType((QonfigElementDef) child.getType(), null);
			else if (child.getType() instanceof QonfigElementDef)
				return new PromisedType((QonfigElementDef) child.getType(),
					MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom).withAll(child.getInheritance()));
		} else if (child.getInheritance().isEmpty())
			return new PromisedType(null, MultiInheritanceSet.singleton((QonfigAddOn) child.getType(), QonfigAddOn::isAssignableFrom));
		MultiInheritanceSet<QonfigAddOn> inh = MultiInheritanceSet.create(QonfigAddOn::isAssignableFrom);
		inh.add((QonfigAddOn) child.getType());
		inh.addAll(child.getInheritance());
		return new PromisedType(null, inh);
	}

	@Override
	public void fulfillPromise(QonfigElement promise, QonfigElement.Builder parent, QonfigParser parser, QonfigParseSession session)
		throws IOException, QonfigParseException {
		PartialQonfigElement extContentRoot = promise.getDocument().getPartialRoot();
		if (!extContentRoot.isInstance(theFulfillsAttribute.getOwner())) {
			session.error("This element is only valid within an " + QonfigExternalRefPromise.EXT_DOCUMENT_TYPE + " document");
			return;
		}
		QonfigElementDef refType = session.getToolkit().getElement(extContentRoot.getAttributeText(theFulfillsAttribute));
		if (refType == null) {
			session.at(extContentRoot.getFilePosition())
				.error("Unable to locate fulfilled type '" + extContentRoot.getAttributeText(theFulfillsAttribute) + "'");
			return;
		}
		QonfigChildDef role;
		try {
			PatternMatch roleMatcher = promise.getAttribute(theRefRole, PatternMatch.class);
			String elementName = roleMatcher.getGroup("name");
			if (elementName != null) {
				String ns = roleMatcher.getGroup("ns");
				if (ns != null)
					elementName = ns + ":" + elementName;
				role = promise.getDocument().getDocToolkit().getChild(elementName, roleMatcher.getGroup("member"));
				if (role != null && !role.getOwner().isAssignableFrom(refType))
					session.at(promise.getAttributes().get(theRefRole).position).error("Role '" + roleMatcher.getWholeText()
						+ "' does not apply to " + QonfigExternalRefPromise.EXT_REFERENCE_TYPE + " extension " + refType);
			} else
				role = refType.getChild(roleMatcher.getGroup("member"));
			if (role == null) {
				session.at(promise.getAttributes().get(theRefRole).position).error("No such role found: " + roleMatcher.getWholeText());
				return;
			}
		} catch (IllegalArgumentException e) {
			session.at(promise.getAttributes().get(theRefRole).position).error(e.getMessage(), e);
			return;
		}

		if (parent.isPartial()) {
			parent.withChild2(promise.getParentRoles(), role.getType(), null, child -> {
				for (QonfigAddOn inh : promise.getInheritance().values()) {
					if (child.isSupported(inh))
						child.inherits(inh, false);
				}
				for (Map.Entry<QonfigAttributeDef.Declared, AttributeValue> attr : promise.getAttributes().entrySet())
					child.withAttribute(attr.getKey(), attr.getValue());
				child.createVariable(role.getMin(), role.getMax(), (child2, parent2) -> fulfillChildren(parent2, promise, role, parser,
					session == null ? null : session.at(child.reporting().getFileLocation())));
			}, promise.getFilePosition(), promise.getDescription());
		} else {
			fulfillChildren(parent, promise, role, parser, session);
		}
	}

	/**
	 * @param parent The parent to fulfill the children into
	 * @param promise The promise that this fulfillment is fulfilling
	 * @param role The role in the external content that the children must fulfill
	 * @param parser The parser to parse external content
	 * @param session The parse session to parse external content
	 */
	protected void fulfillChildren(QonfigElement.Builder parent, QonfigElement promise, QonfigChildDef role, QonfigParser parser,
		QonfigParseSession session) {
		QonfigElement extRefPromise = null;
		if (parent.getDocument() == promise.getDocument()) {
			extRefPromise = parent.getPromise();
			for (PartialQonfigElement p = parent.getParent(); p != null && extRefPromise == null; p = p.getParent())
				extRefPromise = p.getPromise();
		} else {
			// Go look through the ancestors to find the element containing the content to fulfill this promise
			if (parent.getExternalContent() != null && parent.getExternalContent().getDocument() == promise.getDocument())
				extRefPromise = parent.getPromise();
			else {
				for (PartialQonfigElement p = parent.getParent(); p != null; p = p.getParent()) {
					if (p.getExternalContent() != null && p.getExternalContent().getDocument() == promise.getDocument()) {
						extRefPromise = p.getPromise();
						break;
					}
				}
			}
		}
		if (extRefPromise == null) {
			parent.reporting().error("Could not locate external document in element hierarchy");
			return;
		}
		fulfillChildren(parent, role, promise, extRefPromise, parser, session);
	}

	/**
	 * @param parent The parent to fulfill the children into
	 * @param promise The promise that this fulfillment is fulfilling
	 * @param role The role in the external content that the children must fulfill
	 * @param extRefPromise The promise containing the data to fulfill the children
	 * @param parser The parser to parse external content
	 * @param session Error reporting
	 */
	protected void fulfillChildren(QonfigElement.Builder parent, QonfigChildDef role, QonfigElement promise, QonfigElement extRefPromise,
		QonfigParser parser, QonfigParseSession session) {
		for (QonfigElement child : extRefPromise.getChildrenByRole().get(role.getDeclared())) {
			parent.withChild2(promise.getParentRoles(), child.getType(), new PromisedType(promise.getType(), promise.getInheritance()), //
				b -> {
					b.fulfills(promise, child);
					child.copy(b, parser, session.at(child.getFilePosition()), session);
					for (QonfigElement promiseChild : promise.getChildren()) {
						boolean add = false;
						for (QonfigChildDef pcr : promiseChild.getParentRoles()) {
							add = child.isInstance(pcr.getOwner());
							if (add)
								break;
						}
						if (add)
							promiseChild.copyInto(b, parser, session.at(promiseChild.getFilePosition()), session);
					}
				}, child.getFilePosition(), child.getDescription());
		}
		// PartialQonfigElement usePromise;
		// if (extRefPromise instanceof QonfigElement) {
		// Building fully. Full elements must have full promises, so we need to synthesize the promise element
		// child-placeholder doesn't fulfill any roles in any parent, it's just a placeholder.
		// So it can only be synthesized as a root element.

		// QonfigElement.Builder promiseBuilder = QonfigElement.buildRoot(false, session, promise.getDocument(), promise.getType(),
		// promise.getDescription());
		// promise.copy(promiseBuilder, parser, session, session.at(promise.getFilePosition()));
		// // usePromise =
		// promiseBuilder.buildFull();

		// } else
		// usePromise = promise;
		// Set<QonfigChildDef> roles = new LinkedHashSet<>();
		// for (PartialQonfigElement extChild : extRefPromise.getChildrenByRole().get(role.getDeclared())) {
		// roles.addAll(childRef.getParentRoles());
		// parent.withChild2(roles, extChild.getType(), child -> {
		// child.withDocument(extChild.getDocument());
		// child.fulfills(usePromise, extChild);
		// for (QonfigAddOn inh : childRef.getInheritance().values())
		// child.inherits(inh, false);
		// for (QonfigAddOn inh : extChild.getInheritance().values())
		// child.inherits(inh, false);
		//
		// childRef.copyAttributes(child);
		// extChild.copyAttributes(child);
		//
		// extChild.copyChildren(child);
		// }, extChild.getFilePosition(), extChild.getDescription());
		// }
	}
}

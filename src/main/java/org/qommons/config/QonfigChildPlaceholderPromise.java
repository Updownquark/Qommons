package org.qommons.config;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.qommons.io.ErrorReporting;

public class QonfigChildPlaceholderPromise implements QonfigPromiseFulfillment {
	public static final String CHILD_PLACEHOLDER = "child-placeholder";
	public static final String REF_ROLE_ATTR = "ref-role";

	private QonfigElementDef theChildPlaceholder;
	private QonfigAttributeDef.Declared theRefRole;
	private QonfigAttributeDef.Declared theFulfillsAttribute;

	@Override
	public QonfigToolkit.ToolkitDef getToolkit() {
		return QonfigExternalRefPromise.REFERENCE_TOOLKIT;
	}

	@Override
	public String getQonfigType() {
		return CHILD_PLACEHOLDER;
	}

	@Override
	public void setQonfigType(QonfigElementOrAddOn qonfigType) {
		theChildPlaceholder = (QonfigElementDef) qonfigType;
		theRefRole = theChildPlaceholder.getAttribute(REF_ROLE_ATTR).getDeclared();
		theFulfillsAttribute = qonfigType.getDeclarer().getAttribute(QonfigExternalRefPromise.EXT_CONTENT_TYPE, "fulfills");
	}

	@Override
	public void fulfillPromise(PartialQonfigElement promise, QonfigElement.Builder parent, List<ElementQualifiedParseItem> declaredRoles,
		Set<QonfigAddOn> inheritance, QonfigParser parser, QonfigParseSession session) throws IOException, QonfigParseException {
		PartialQonfigElement extContentRoot = promise.getDocument().getPartialRoot();
		if (!extContentRoot.isInstance(theFulfillsAttribute.getOwner())) {
			session.error("This element is only valid within an " + QonfigExternalRefPromise.EXT_CONTENT_TYPE + " document");
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
			if (role == null)
				session.at(promise.getAttributes().get(theRefRole).position).error("No such role found: " + roleMatcher.getWholeText());
		} catch (IllegalArgumentException e) {
			session.at(promise.getAttributes().get(theRefRole).position).error(e.getMessage(), e);
			return;
		}
		
		parent.withChild(declaredRoles, role.getType(), child -> {
			for (QonfigAddOn inh : inheritance)
				child.inherits(inh, false);
			child.createVariable(role.getMin(), role.getMax(), (child2, parent2) -> fulfillChildren(parent2, child2, promise, role));
		}, promise.getFilePosition(), promise.getDescription());
	}

	protected void fulfillChildren(QonfigElement.Builder parent, VariableQonfigElement childRef, PartialQonfigElement promise,
		QonfigChildDef role) {
		PartialQonfigElement extRefPromise = null;
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
		fulfillChildren(parent, childRef, role, promise, extRefPromise, parent.reporting());
	}

	protected void fulfillChildren(QonfigElement.Builder parent, VariableQonfigElement childRef, QonfigChildDef role,
		PartialQonfigElement promise, PartialQonfigElement extRefPromise, ErrorReporting reporting) {
		PartialQonfigElement usePromise;
		if (extRefPromise instanceof QonfigElement) {
			// Building fully. Full elements must have full promises, so we need to synthesize the promise element
			// child-placeholder doesn't fulfill any roles in any parent, it's just a placeholder.
			// So it can only be synthesized as a root element.
			QonfigElement.Builder promiseBuilder = QonfigElement.buildRoot(false, reporting, promise.getDocument(),
				(QonfigElementDef) promise.getType(), promise.getDescription());
			promise.copy(promiseBuilder);
			usePromise = promiseBuilder.buildFull();
		} else
			usePromise = promise;
		Set<QonfigChildDef> roles = new LinkedHashSet<>();
		for (PartialQonfigElement extChild : extRefPromise.getChildrenByRole().get(role.getDeclared())) {
			roles.addAll(childRef.getParentRoles());
			parent.withChild2(roles, extChild.getType(), child -> {
				child.withDocument(extChild.getDocument());
				child.fulfills(usePromise, extChild);
				for (QonfigAddOn inh : childRef.getInheritance().values())
					child.inherits(inh, false);
				for (QonfigAddOn inh : extChild.getInheritance().values())
					child.inherits(inh, false);

				childRef.copyAttributes(child);
				extChild.copyAttributes(child);

				extChild.copyChildren(child);
			}, extChild.getFilePosition(), extChild.getDescription());
		}
	}
}

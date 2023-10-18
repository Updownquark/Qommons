package org.qommons.config;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

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
			Matcher roleMatcher = promise.getAttribute(theRefRole, Matcher.class);
			String elementName = roleMatcher.group("element");
			if (elementName != null) {
				String ns = roleMatcher.group("ns");
				if (ns != null)
					elementName = ns + ":" + elementName;
				role = promise.getDocument().getDocToolkit().getChild(elementName, roleMatcher.group("role"));
				if (role != null && !role.getOwner().isAssignableFrom(refType))
					session.at(promise.getAttributes().get(theRefRole).position).error("Role '" + roleMatcher.group()
						+ "' does not apply to " + QonfigExternalRefPromise.EXT_REFERENCE_TYPE + " extension " + refType);
			} else
				role = refType.getChild(roleMatcher.group("role"));
			if (role == null)
				session.at(promise.getAttributes().get(theRefRole).position).error("No such role found: " + roleMatcher.group());
		} catch (IllegalArgumentException e) {
			session.at(promise.getAttributes().get(theRefRole).position).error(e.getMessage(), e);
			return;
		}
		
		parent.withChild(declaredRoles, role.getType(), child -> {
			for (QonfigAddOn inh : inheritance)
				child.inherits(inh);
			child.createVariable(role.getMin(), role.getMax(), (child2, parent2) -> fulfillChildren(parent2, child2, promise, role));
		}, promise.getFilePosition(), promise.getDescription());
	}

	protected void fulfillChildren(QonfigElement.Builder parent, VariableQonfigElement childRef, PartialQonfigElement promise,
		QonfigChildDef role) {
		// Go look through the ancestors to see if we can fulfill this promise right now
		PartialQonfigElement extRefPromise = null;
		for (PartialQonfigElement p = parent.getParent(); p != null; p = p.getParent()) {
			if (p.getExternalContent() == promise.getDocument()) {
				extRefPromise = p.getPromise();
				break;
			}
		}
		if (extRefPromise == null) {
			parent.reporting().error("Could not locate external document in element hierarchy");
			return;
		}
		fulfillChildren(parent, childRef, role, promise, extRefPromise, parent.reporting());
	}

	protected void fulfillChildren(QonfigElement.Builder parent, VariableQonfigElement childRef, QonfigChildDef role,
		PartialQonfigElement promise,
		PartialQonfigElement extRefPromise, ErrorReporting reporting) {
		Set<QonfigChildDef> roles = new LinkedHashSet<>();
		for (PartialQonfigElement extChild : extRefPromise.getChildrenByRole().get(role.getDeclared())) {
			roles.addAll(childRef.getParentRoles());
			roles.addAll(extChild.getParentRoles());
			parent.withChild2(roles, extChild.getType(), child -> {
				child.fulfills(promise, null);
				for (QonfigAddOn inh : childRef.getInheritance().values())
					child.inherits(inh);
				for (QonfigAddOn inh : extChild.getInheritance().values())
					child.inherits(inh);

				childRef.copyAttributes(child);
				extChild.copyAttributes(child);

				extChild.copyChildren(child);
			}, extChild.getFilePosition(), extChild.getDescription());
		}
	}
}

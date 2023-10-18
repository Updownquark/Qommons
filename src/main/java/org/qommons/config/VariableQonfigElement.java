package org.qommons.config;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.qommons.MultiInheritanceSet;
import org.qommons.collect.BetterMultiMap;
import org.qommons.config.QonfigChildDef.Declared;
import org.qommons.config.QonfigElement.Builder;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.io.LocatedPositionedContent;

public class VariableQonfigElement extends PartialQonfigElement {
	private final int theMinimumCount;
	private final int theMaximumCount;
	private final BiConsumer<VariableQonfigElement, QonfigElement.Builder> theBuilder;

	public VariableQonfigElement(QonfigDocument document, PartialQonfigElement parent, QonfigElementOrAddOn type,
		MultiInheritanceSet<QonfigAddOn> inheritance, Set<QonfigChildDef> parentRoles, Set<Declared> declaredRoles,
		Map<org.qommons.config.QonfigAttributeDef.Declared, QonfigValue> attributes, QonfigValue value,
		LocatedPositionedContent filePosition, String description, PartialQonfigElement promiser, QonfigDocument externalContent,
		int minimumCount, int maximumCount, BiConsumer<VariableQonfigElement, Builder> builder) {
		super(document, parent, type, inheritance, parentRoles, declaredRoles, attributes, Collections.emptyList(), BetterMultiMap.empty(),
			value, filePosition, description, promiser, externalContent);
		theMinimumCount = minimumCount;
		theMaximumCount = maximumCount;
		theBuilder = builder;
	}

	public int getMinimumCount() {
		return theMinimumCount;
	}

	public int getMaximumCount() {
		return theMaximumCount;
	}

	private boolean isCopying;

	@Override
	public void copyInto(QonfigElement.Builder parent) {
		if (isCopying) // Recursive call from the builder
			super.copyInto(parent);
		else
			theBuilder.accept(this, parent);
	}
}

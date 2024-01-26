package org.qommons.config;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.qommons.MultiInheritanceSet;
import org.qommons.collect.BetterMultiMap;
import org.qommons.config.QonfigChildDef.Declared;
import org.qommons.config.QonfigElement.AttributeValue;
import org.qommons.config.QonfigElement.Builder;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.io.LocatedPositionedContent;

/** A partial Qonfig element that represents a placeholder which must be replaced by a variable number of elements */
public class VariableQonfigElement extends PartialQonfigElement {
	private final int theMinimumCount;
	private final int theMaximumCount;
	private final BiConsumer<VariableQonfigElement, QonfigElement.Builder> theBuilder;

	/**
	 * @param document The document containing this element
	 * @param parent The parent of this element
	 * @param type The Qonfig type of this element
	 * @param inheritance The Qonfig add-ons inherited by this element
	 * @param parentRoles The child roles that this element fulfills in its parent
	 * @param declaredRoles The child roles declared on this element
	 * @param attributes The attribute values for the element
	 * @param value The value of the element
	 * @param filePosition The position of this element in its source file
	 * @param description The documentation description of this element
	 * @param promise The promise that is loading this element's external content
	 * @param externalContent The external content that this element is a reference to
	 * @param minimumCount The minimum number of elements required to fulfill this variable element placeholder
	 * @param maximumCount The maximum number of elements required to fulfill this variable element placeholder
	 * @param builder The function to fulfill this element in an element builder
	 */
	public VariableQonfigElement(QonfigDocument document, PartialQonfigElement parent, QonfigElementOrAddOn type,
		MultiInheritanceSet<QonfigAddOn> inheritance, Set<QonfigChildDef> parentRoles, Set<Declared> declaredRoles,
		Map<QonfigAttributeDef.Declared, AttributeValue> attributes, QonfigValue value, LocatedPositionedContent filePosition,
		String description, PartialQonfigElement promise, PartialQonfigElement externalContent, int minimumCount, int maximumCount,
		BiConsumer<VariableQonfigElement, Builder> builder) {
		super(document, parent, type, inheritance, parentRoles, declaredRoles, attributes, Collections.emptyList(), BetterMultiMap.empty(),
			value, filePosition, description, promise, externalContent);
		theMinimumCount = minimumCount;
		theMaximumCount = maximumCount;
		theBuilder = builder;
	}

	/** @return The minimum number of elements required to fulfill this variable element placeholder */
	public int getMinimumCount() {
		return theMinimumCount;
	}

	/** @return The maximum number of elements required to fulfill this variable element placeholder */
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

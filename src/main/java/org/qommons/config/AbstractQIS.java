package org.qommons.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.qommons.MultiInheritanceSet;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.ex.ExFunction;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedContentPosition;
import org.qommons.io.LocatedFilePosition;

/**
 * An interpretation session with lots of capabilities for interpreting {@link QonfigElement}s
 * 
 * @param <QIS> The sub-type of the session
 */
public interface AbstractQIS<QIS extends AbstractQIS<QIS>> {
	/** @return The element being interpreted */
	QonfigElement getElement();

	/**
	 * @return The element or add-on type that the element is being interpreted as. Attributes and children of this type may always be
	 *         referred to by name only (e.g. from {@link #getAttribute(String, Class)} or {@link #interpretChildren(String, Class)}), as
	 *         long as there are no name conflicts within this element/add-on.
	 */
	QonfigElementOrAddOn getFocusType();

	/**
	 * @return All types that this session is aware (excluding the {@link #getFocusType()}) of the element being an instance of. Attributes
	 *         and children of these types may be referred to by name only (e.g. from {@link #getAttribute(String, Class)} or
	 *         {@link #interpretChildren(String, Class)}), as long as there are no name conflicts within these elements/add-ons.
	 */
	MultiInheritanceSet<QonfigElementOrAddOn> getTypes();

	/**
	 * @param focusType The element/add-on type to interpret the element as (the new {@link #getFocusType()} focus type)
	 * @return A session based off this session but being interpreted as the given element
	 */
	QIS asElement(QonfigElementOrAddOn focusType);

	/**
	 * @param type The element/add-on type to interpret the element as (the new {@link #getFocusType()} focus type)
	 * @return A session based off this session, but being interpreted as the given element, with no other {@link #getTypes()} known to it
	 */
	QIS asElementOnly(QonfigElementOrAddOn type);

	/**
	 * @param focusTypeName The name of the element/add-on type to interpret the element as (the new {@link #getFocusType()} focus type)
	 * @return A session based off this session but being interpreted as the given element
	 */
	default QIS asElement(String focusTypeName) {
		return asElement(null, focusTypeName);
	}

	/**
	 * @param toolkit The toolkit of the element to focus on
	 * @param focusTypeName The name of the element to focus on
	 * @return A session based off this session but being interpreted as the given element
	 */
	default QIS asElement(QonfigToolkit toolkit, String focusTypeName) {
		QonfigElementOrAddOn type = Impl.getTargetElement(this, toolkit, focusTypeName);
		return asElement(type);
	}

	/**
	 * Obtains a view of this session as a {@link SpecialSession}. This must have been enabled when the interpreter was built.
	 * 
	 * @param <QIS2> The type of the special session
	 * @param sessionType The type of the special session
	 * @return The special session of the given type
	 * @throws IllegalArgumentException If the given session type is not supported
	 * @throws QonfigInterpretationException If an error occurs configuring the special session
	 * @see {@link QonfigInterpreterCore.Builder#withSpecial(Class, SpecialSessionImplementation)}
	 */
	<QIS2 extends SpecialSession<QIS2>> QIS2 as(Class<QIS2> sessionType) throws IllegalArgumentException, QonfigInterpretationException;

	/**
	 * @param <T> The type of the attribute value to get
	 * @param attributeName The name of the attribute on the element-def/add-on associated with the current creator/modifier
	 * @param type The type of the attribute value to get
	 * @return The value of the target attribute
	 * @throws IllegalArgumentException If:
	 *         <ul>
	 *         <li>No such attribute exists on the element/add-on</li>
	 *         <li>The value for the attribute does not match the given type</li>
	 *         </ul>
	 */
	default <T> T getAttribute(String attributeName, Class<T> type) throws IllegalArgumentException {
		return getAttribute(null, attributeName, type, null);
	}

	/**
	 * @param <T> The type of the attribute value to get
	 * @param attributeName The name of the attribute on the element-def/add-on associated with the current creator/modifier
	 * @param type The type of the attribute value to get
	 * @param defaultValue The value to return if the given attribute was not specified
	 * @return The value of the target attribute
	 * @throws IllegalArgumentException If:
	 *         <ul>
	 *         <li>No such attribute exists on the element/add-on</li>
	 *         <li>The value for the attribute does not match the given type</li>
	 *         </ul>
	 */
	default <T> T getAttribute(String attributeName, Class<T> type, T defaultValue) throws IllegalArgumentException {
		return getAttribute(null, attributeName, type, defaultValue);
	}

	/**
	 * @param attributeName The name of the attribute on the element-def/add-on associated with the current creator/modifier
	 * @return The text given for the target attribute
	 * @throws IllegalArgumentException If no such attribute exists on the element/add-on
	 */
	default String getAttributeText(String attributeName) throws IllegalArgumentException {
		return getAttributeText(null, attributeName);
	}

	/**
	 * @param attributeName The name of the attribute on the element-def/add-on associated with the current creator/modifier
	 * @return The full value of the target attribute
	 * @throws IllegalArgumentException If no such attribute exists on the element/add-on
	 */
	default QonfigValue getAttributeQV(String attributeName) throws IllegalArgumentException {
		return getElement().getAttributes().get(getAttributeDef(null, null, attributeName));
	}

	/**
	 * @param <T> The type of the attribute value to get
	 * @param asElement The name of the element type containing the attribute
	 * @param attributeName The name of the attribute on the element-def/add-on associated with the current creator/modifier
	 * @param type The type of the attribute value to get
	 * @return The value of the target attribute
	 * @throws IllegalArgumentException If:
	 *         <ul>
	 *         <li>No such attribute exists on the element/add-on</li>
	 *         <li>The value for the attribute does not match the given type</li>
	 *         </ul>
	 */
	default <T> T getAttribute(String asElement, String attributeName, Class<T> type) throws IllegalArgumentException {
		return getAttribute(asElement, attributeName, type, null);
	}

	/**
	 * @param <T> The type of the attribute value to get
	 * @param asElement The name of the element type containing the attribute
	 * @param attributeName The name of the attribute on the element-def/add-on associated with the current creator/modifier
	 * @param type The type of the attribute value to get
	 * @param defaultValue The value to return if the given attribute was not specified
	 * @return The value of the target attribute
	 * @throws IllegalArgumentException If:
	 *         <ul>
	 *         <li>No such attribute exists on the element/add-on</li>
	 *         <li>The value for the attribute does not match the given type</li>
	 *         </ul>
	 */
	default <T> T getAttribute(String asElement, String attributeName, Class<T> type, T defaultValue) throws IllegalArgumentException {
		return getAttribute(null, asElement, attributeName, type, defaultValue);
	}

	/**
	 * @param <T> The type of the attribute value to get
	 * @param toolkit The toolkit that defines the element that the target attribute belongs to, or null to use the definer of the
	 *        {@link #getFocusType() focus type}
	 * @param asElement The name of the element type containing the attribute
	 * @param attributeName The name of the attribute on the element-def/add-on associated with the current creator/modifier
	 * @param type The type of the attribute value to get
	 * @param defaultValue The value to return if the given attribute was not specified
	 * @return The value of the target attribute
	 * @throws IllegalArgumentException If:
	 *         <ul>
	 *         <li>No such attribute exists on the element/add-on</li>
	 *         <li>The value for the attribute does not match the given type</li>
	 *         </ul>
	 */
	default <T> T getAttribute(QonfigToolkit toolkit, String asElement, String attributeName, Class<T> type, T defaultValue)
		throws IllegalArgumentException {
		return getAttribute(getAttributeDef(toolkit, asElement, attributeName), type, defaultValue);
	}

	/**
	 * @param <T> The type of the attribute value to get
	 * @param attribute The attribute whose value to get
	 * @param type The type of the attribute value to get
	 * @param defaultValue The value to return if the attribute is not specified on this element
	 * @return The value of the attribute on this element, or the default value if not specified
	 * @throws IllegalArgumentException If the given attribute is owned by a type that this element does not extend/inhert
	 */
	default <T> T getAttribute(QonfigAttributeDef attribute, Class<T> type, T defaultValue) throws IllegalArgumentException {
		T value = getElement().getAttribute(attribute, type);
		if (value == null)
			return defaultValue;
		return value;
	}

	/**
	 * @param toolkit The toolkit that defines the element that the target attribute belongs to, or null to use the definer of the
	 *        {@link #getFocusType() focus type}
	 * @param asElement The name of the element type containing the attribute
	 * @param attributeName The name of the attribute on the element-def/add-on associated with the current creator/modifier
	 * @return The attribute of the given name on the element
	 * @throws IllegalArgumentException If no such element exists, no such attribute exists on the given element, or multiple attributes of
	 *         equal standing with the given name exist on the element
	 */
	default QonfigAttributeDef getAttributeDef(QonfigToolkit toolkit, String asElement, String attributeName)
		throws IllegalArgumentException {
		if (toolkit == null)
			toolkit = getFocusType().getDeclarer();
		QonfigElementOrAddOn element = Impl.getTargetElement(this, toolkit, asElement);
		QonfigAttributeDef attr = element.getAttribute(attributeName);
		if (attr == null) {
			for (QonfigElementOrAddOn type : getTypes().values()) {
				QonfigAttributeDef typeAttr = type.getAttribute(attributeName);
				if (typeAttr != null) {
					if (attr != null)
						throw new IllegalArgumentException("Multiple attributes named '" + attributeName + "' available, including for "
							+ attr.getOwner() + " and " + type);
					attr = typeAttr;
				}
			}
		}
		if (attr == null)
			throw new IllegalArgumentException("No such attribute " + element + "." + attributeName);
		return attr;
	}

	/**
	 * @param asElement The name of the element type containing the attribute
	 * @param attributeName The name of the attribute on the element-def/add-on associated with the current creator/modifier
	 * @return The value of the target attribute
	 * @throws IllegalArgumentException If no such attribute exists on the element/add-on
	 */
	default String getAttributeText(String asElement, String attributeName) throws IllegalArgumentException {
		return getElement().getAttributeText(getAttributeDef(null, asElement, attributeName));
	}

	/**
	 * Retrieves the element's value
	 * 
	 * @param <T> The expected type of the value
	 * @param type The expected type of the value
	 * @param defaultValue The value to return if the element's value is not specified
	 * @return The element's value
	 * @throws IllegalArgumentException If this element's type has no value definition
	 * @throws ClassCastException If the element's value is not of the expected type
	 */
	default <T> T getValue(Class<T> type, T defaultValue) throws IllegalArgumentException, ClassCastException {
		Object value = getElement().getValue();
		if (value == null) {
			boolean hasValue = getFocusType().getValue() != null;
			if (!hasValue) {
				for (QonfigElementOrAddOn elType : getTypes().values()) {
					hasValue = elType.getValue() != null;
					if (hasValue)
						break;
				}
			}
			if (!hasValue)
				throw new IllegalArgumentException("No value defined for " + getFocusType());
		}
		if (value == null)
			return defaultValue;
		return type.cast(value);
	}

	/** @return The element's value as text */
	default String getValueText() {
		return getElement().getValueText();
	}

	/** @return The value definition for this element */
	default QonfigValueDef getValueDef() {
		QonfigValueDef value = getFocusType().getValue();
		if (value != null)
			return value;
		else if (getFocusType() instanceof QonfigElementDef || getFocusType().getSuperElement() != null)
			return null;
		for (QonfigElementOrAddOn type : getTypes().values()) {
			value = type.getValue();
			if (value != null)
				return value;
			else if (type instanceof QonfigElementDef || type.getSuperElement() != null)
				return null;
		}
		return null;
	}

	/**
	 * @param roleName The name of the role to get
	 * @return The role for the given name
	 * @throws IllegalArgumentException If the given role does not exist
	 */
	default QonfigChildDef getRole(String roleName) throws IllegalArgumentException {
		return getRole(null, roleName);
	}

	/**
	 * @param asElement The name of the element or add-on type to interpret the element as
	 * @param roleName The name of the role to get
	 * @return The role with the given name
	 * @throws IllegalArgumentException If the given element type or role does not exist, or this element is not an instance of the given
	 *         type
	 */
	default QonfigChildDef getRole(String asElement, String roleName) throws IllegalArgumentException {
		return getRole(null, asElement, roleName);
	}

	/**
	 * @param toolkit The toolkit that defines the element that the target child belongs to, or null to use the definer of the
	 *        {@link #getFocusType() focus type}
	 * @param asElement The name of the element type containing the child
	 * @param roleName The name of the child on the element-def/add-on associated with the current creator/modifier
	 * @return The child role of the given name on the element
	 * @throws IllegalArgumentException If no such element exists, no such child exists on the given element, or multiple child roles of
	 *         equal standing with the given name exist on the element
	 */
	default QonfigChildDef getRole(QonfigToolkit toolkit, String asElement, String roleName) throws IllegalArgumentException {
		if (toolkit == null)
			toolkit = getFocusType().getDeclarer();
		QonfigElementOrAddOn element = Impl.getTargetElement(this, toolkit, asElement);
		QonfigChildDef role = element.getChild(roleName);
		if (role == null) {
			for (QonfigElementOrAddOn type : getTypes().values()) {
				QonfigChildDef typeRole = type.getChild(roleName);
				if (typeRole != null) {
					if (role != null)
						throw new IllegalArgumentException(
							"Multiple children named '" + roleName + "' available, including for " + role.getOwner() + " and " + type);
					role = typeRole;
				}
			}
		}
		if (role == null)
			throw new IllegalArgumentException("No such role " + element + "." + roleName);
		return role;
	}

	/**
	 * @param role The role to check
	 * @return Whether this element fulfills the given role in its parent
	 */
	default boolean fulfills(QonfigChildDef role) {
		return getElement().getDeclaredRoles().contains(role.getDeclared());
	}

	/**
	 * @param elementName The name of the element/add-on type to test
	 * @return Whether the element is an instance of the given type
	 */
	default boolean isInstance(String elementName) {
		return isInstance(null, elementName);
	}

	/**
	 * @param toolkit The name of the toolkit defining the element to test, or null to use the definer of the {@link #getFocusType() focus
	 *        type}
	 * @param elementName The name of the element to test
	 * @return Whether this element is an instance of the given element
	 * @throws IllegalArgumentException If no such element exists
	 */
	default boolean isInstance(QonfigToolkit toolkit, String elementName) throws IllegalArgumentException {
		if (toolkit == null)
			toolkit = getFocusType().getDeclarer();
		if (getFocusType().getName().equals(elementName))
			return true;
		else {
			QonfigElementOrAddOn element = toolkit.getElementOrAddOn(elementName);
			if (element == null)
				throw new IllegalArgumentException("No such element or add-on " + elementName + " in toolkit " + toolkit);
			else
				return getElement().isInstance(element);
		}
	}

	/**
	 * @param <T> The type of value to interpret
	 * @param asType The type of value to interpret the element as
	 * @return The interpreted value
	 * @throws QonfigInterpretationException If the value cannot be interpreted
	 */
	default <T> T interpret(Class<T> asType) throws QonfigInterpretationException {
		return interpret(getFocusType(), asType);
	}

	/**
	 * @param <T> The type of value to interpret
	 * @param as The element type to interpret the element as
	 * @param asType The type of value to interpret the element as
	 * @return The interpreted value
	 * @throws QonfigInterpretationException If the value cannot be interpreted
	 */
	<T> T interpret(QonfigElementOrAddOn as, Class<T> asType) throws QonfigInterpretationException;

	/**
	 * @param <C> The type of the attribute value to get
	 * @param <T> The type of the mapped value
	 * @param attributeName The name of the attribute on the element-def/add-on associated with the current creator/modifier
	 * @param sourceType The type of the attribute value to get
	 * @param nullToNull Whether, if the given attribute was not specified, to return null without applying the map function
	 * @param map The function to produce the target value
	 * @return The mapped attribute value
	 * @throws IllegalArgumentException If:
	 *         <ul>
	 *         <li>No such attribute exists on the element/add-on</li>
	 *         <li>The value for the attribute does not match the given source type</li>
	 *         </ul>
	 * @throws QonfigInterpretationException If the map function throws an exception
	 */
	default <C, T> T interpretAttribute(String attributeName, Class<C> sourceType, boolean nullToNull,
		ExFunction<? super C, ? extends T, QonfigInterpretationException> map)
		throws IllegalArgumentException, QonfigInterpretationException {
		C attrValue = getAttribute(attributeName, sourceType, null);
		if (nullToNull && attrValue == null)
			return null;
		return map.apply(attrValue);
	}

	/**
	 * @param <C> The type of the attribute value to get
	 * @param <T> The type of the mapped value
	 * @param attr The attribute on the element-def/add-on associated with the current creator/modifier
	 * @param sourceType The type of the attribute value to get
	 * @param nullToNull Whether, if the given attribute was not specified, to return null without applying the map function
	 * @param map The function to produce the target value
	 * @return The mapped attribute value
	 * @throws IllegalArgumentException If:
	 *         <ul>
	 *         <li>No such attribute exists on the element/add-on</li>
	 *         <li>The value for the attribute does not match the given source type</li>
	 *         </ul>
	 * @throws QonfigInterpretationException If the map function throws an exception
	 */
	default <C, T> T interpretAttribute(QonfigAttributeDef attr, Class<C> sourceType, boolean nullToNull,
		ExFunction<? super C, ? extends T, QonfigInterpretationException> map)
		throws IllegalArgumentException, QonfigInterpretationException {
		C attrValue = getAttribute(attr, sourceType, null);
		if (nullToNull && attrValue == null)
			return null;
		return map.apply(attrValue);
	}

	/**
	 * Interprets the element's value
	 * 
	 * @param <C> The expected type of the element's value
	 * @param <T> The type to interpret the value as
	 * @param sourceType The expected type of the element's value
	 * @param nullToNull Whether to return null if the element's value was not specified instead of passing null to the given interpreter
	 *        function
	 * @param map Function to interpret the element's value
	 * @return The interpreted value
	 * @throws IllegalArgumentException If this element has no value definition
	 * @throws ClassCastException If the value is not of the expected type
	 * @throws QonfigInterpretationException If the given interpreter function throws it
	 */
	default <C, T> T interpretValue(Class<C> sourceType, boolean nullToNull,
		ExFunction<? super C, ? extends T, QonfigInterpretationException> map)
		throws IllegalArgumentException, ClassCastException, QonfigInterpretationException {
		Object value = getValue(sourceType, null);
		if (nullToNull && value == null)
			return null;
		return map.apply(sourceType.cast(value));
	}

	/**
	 * @param childName The name of the child on the element-def/add-on associated with the current creator/modifier
	 * @return All children in this element that fulfill the given child role
	 * @throws IllegalArgumentException If no such child exists on the element/add-on
	 */
	default BetterList<QonfigElement> getChildren(String childName) throws IllegalArgumentException {
		QonfigChildDef child = getRole(childName);
		return (BetterList<QonfigElement>) getElement().getChildrenByRole().get(child.getDeclared());
	}

	/**
	 * @param child The child element of this session's {@link #getElement()} to interpret
	 * @param asType The element or add-on to interpret the child as
	 * @return The interpretation session for the child element
	 * @throws QonfigInterpretationException If an error occurs configuring the child interpreter
	 */
	QIS interpretChild(QonfigElement child, QonfigElementOrAddOn asType) throws QonfigInterpretationException;

	/**
	 * @param root The root element to interpret
	 * @return The interpretation session for the element
	 * @throws QonfigInterpretationException If an error occurs configuring the interpreter
	 */
	QIS intepretRoot(QonfigElement root) throws QonfigInterpretationException;

	/**
	 * @param childName The name of the child role to interpret the children for
	 * @return A list of interpretation sessions for each child in this element with the given role
	 * @throws IllegalArgumentException If no such child role exists
	 * @throws QonfigInterpretationException If an error occurs initializing the children's interpretation
	 */
	default BetterList<QIS> forChildren(String childName) throws IllegalArgumentException, QonfigInterpretationException {
		QonfigChildDef child = getRole(childName);
		BetterList<QonfigElement> children = (BetterList<QonfigElement>) getElement().getChildrenByRole().get(child.getDeclared());
		if (children.isEmpty())
			return BetterList.empty();
		AbstractQIS<QIS>[] sessions = new AbstractQIS[children.size()];
		int i = 0;
		for (QonfigElement ch : children) {
			sessions[i] = interpretChild(ch, ch.getType());
			i++;
		}
		return (BetterList<QIS>) BetterList.of(sessions);
	}

	/**
	 * @param childName The name of the child role to interpret the children for
	 * @param defaultChild The type of child to use as a default if no children are specified for the given role
	 * @param builder Configures the synthetic default child element to use
	 * @return A list of interpretation sessions for each child in this element with the given role, or a list with the single default
	 *         session
	 * @throws IllegalArgumentException If no such child role exists
	 * @throws QonfigInterpretationException If an error occurs initializing the children's interpretation
	 */
	default BetterList<QIS> forChildren(String childName, QonfigElementDef defaultChild, Consumer<QonfigElement.Builder> builder)
		throws IllegalArgumentException, QonfigInterpretationException {
		QonfigChildDef child = getRole(childName);
		return forChildren(child, defaultChild, builder);
	}

	/**
	 * @param child The child role to interpret the children for
	 * @param defaultChild The type of child to use as a default if no children are specified for the given role
	 * @param builder Configures the synthetic default child element to use
	 * @return A list of interpretation sessions for each child in this element with the given role, or a list with the single default
	 *         session
	 * @throws IllegalArgumentException If no such child role exists
	 * @throws QonfigInterpretationException If an error occurs initializing the children's interpretation
	 */
	default BetterList<QIS> forChildren(QonfigChildDef child, QonfigElementDef defaultChild, Consumer<QonfigElement.Builder> builder)
		throws IllegalArgumentException, QonfigInterpretationException {
		BetterList<QonfigElement> children = (BetterList<QonfigElement>) getElement().getChildrenByRole().get(child.getDeclared());
		if (!children.isEmpty()) {
			AbstractQIS<QIS>[] sessions = new AbstractQIS[children.size()];
			int i = 0;
			for (QonfigElement ch : children) {
				sessions[i] = interpretChild(ch, ch.getType());
				i++;
			}
			return BetterList.of((QIS[]) sessions);
		}
		QonfigElement.Builder b = getElement().synthesizeChild(child, defaultChild, reporting());
		if (builder != null)
			builder.accept(b);
		QonfigElement element = b.doneWithAttributes().build();
		return BetterList.of(interpretChild(element, defaultChild));
	}

	/**
	 * @return A list of interpretation sessions for each child in this element
	 * @throws QonfigInterpretationException If an error occurs initializing the children's interpretation
	 */
	default BetterList<QIS> forChildren() throws QonfigInterpretationException {
		List<QonfigElement> children = getElement().getChildren();
		if (children.isEmpty())
			return BetterList.empty();
		AbstractQIS<QIS>[] sessions = new AbstractQIS[children.size()];
		int i = 0;
		for (QonfigElement child : children) {
			sessions[i] = interpretChild(child, child.getType());
			i++;
		}
		return BetterList.of((QIS[]) sessions);
	}

	/**
	 * @param <T> The type of value to interpret
	 * @param childName The name of the child on the element-def/add-on associated with the current creator/modifier
	 * @param asType The type of value to interpret the element as. The super wildcard is to allow for generics.
	 * @return The interpreted values for each child in this element that fulfill the given child role
	 * @throws IllegalArgumentException If no such child exists on the element/add-on
	 * @throws QonfigInterpretationException If the value cannot be interpreted
	 */
	default <T> BetterList<T> interpretChildren(String childName, Class<? super T> asType)
		throws IllegalArgumentException, QonfigInterpretationException {
		BetterList<QonfigElement> children = getChildren(childName);
		if (children.isEmpty())
			return BetterList.empty();
		Object[] interpreted = new Object[children.size()];
		int i = 0;
		for (QonfigElement child : children) {
			interpreted[i] = interpretChild(child, child.getType()).interpret(asType);
			i++;
		}
		return (BetterList<T>) (BetterList<?>) BetterList.of(interpreted);
	}

	/**
	 * @param roleName The name of the role to get
	 * @return The role for the given name
	 * @throws IllegalArgumentException If the given role does not exist
	 */
	default QonfigChildDef getMetaRole(String roleName) throws IllegalArgumentException {
		return getMetaRole(null, roleName);
	}

	/**
	 * @param asElement The name of the element or add-on type to interpret the element as
	 * @param roleName The name of the role to get
	 * @return The role with the given name
	 * @throws IllegalArgumentException If the given element type or role does not exist, or this element is not an instance of the given
	 *         type
	 */
	default QonfigChildDef getMetaRole(String asElement, String roleName) throws IllegalArgumentException {
		return getMetaRole(null, asElement, roleName);
	}

	/**
	 * @param toolkit The name of the toolkit defining the metadata to get, or null to use the definer of the {@link #getFocusType() focus
	 *        type}
	 * @param asElement The name of the element or add-on type to interpret the element as
	 * @param roleName The name of the role to get
	 * @return The role with the given name
	 * @throws IllegalArgumentException If the given element type or role does not exist, or this element is not an instance of the given
	 *         type
	 */
	default QonfigChildDef getMetaRole(QonfigToolkit toolkit, String asElement, String roleName) throws IllegalArgumentException {
		if (toolkit == null)
			toolkit = getFocusType().getDeclarer();
		QonfigElementOrAddOn element = Impl.getTargetElement(this, toolkit, asElement);
		QonfigChildDef role = element.getMetaSpec().getChild(roleName);
		if (role == null) {
			for (QonfigElementOrAddOn type : getTypes().values()) {
				QonfigChildDef typeRole = type.getMetaSpec().getChild(roleName);
				if (typeRole != null) {
					if (role != null)
						throw new IllegalArgumentException(
							"Multiple meta roles named '" + roleName + "' available, including for " + role.getOwner() + " and " + type);
					role = typeRole;
				}
			}
		}
		if (role == null)
			throw new IllegalArgumentException("No such meta role " + element.getMetaSpec() + "." + roleName);
		return role;
	}

	/**
	 * @param metadataName The name of the metadata child on this session's element type to interpret the children for
	 * @return A list of interpretation sessions for each metadata on all types in this element with the given metadata role
	 * @throws IllegalArgumentException If no such metadata role exists
	 * @throws QonfigInterpretationException If an error occurs initializing the metadata's interpretation
	 */
	default BetterList<QIS> forMetadata(String metadataName) throws IllegalArgumentException, QonfigInterpretationException {
		QonfigChildDef child = getMetaRole(metadataName);
		if (child == null)
			throw new IllegalArgumentException("No such metadata " + getFocusType() + "." + metadataName);
		return forMetadata(child);
	}

	/**
	 * @param metaRole The metadata child on this session's element type to interpret the children for
	 * @return A list of interpretation sessions for each metadata on all types in this element with the given metadata role
	 * @throws IllegalArgumentException If no such metadata role exists
	 * @throws QonfigInterpretationException If an error occurs initializing the metadata's interpretation
	 */
	default BetterList<QIS> forMetadata(QonfigChildDef metaRole) throws IllegalArgumentException, QonfigInterpretationException {
		List<QonfigElement> metadata = new ArrayList<>();
		Set<QonfigAddOn> addOns = new HashSet<>();
		Impl.addMetadata(metaRole, metadata, addOns, getElement().getType());
		for (QonfigAddOn inh : getElement().getInheritance().getExpanded(QonfigAddOn::getInheritance))
			Impl.addMetadata(metaRole, metadata, addOns, inh);
		if (metadata.isEmpty())
			return BetterList.empty();
		AbstractQIS<?>[] sessions = new AbstractQIS[metadata.size()];
		int i = 0;
		for (QonfigElement ch : metadata) {
			sessions[i] = interpretChild(ch, ch.getType());
			i++;
		}
		return BetterList.of((QIS[]) sessions);
	}

	/**
	 * @param <T> The type of value to interpret
	 * @param metadataName The name of the metadata child on the element-def/add-on associated with the current creator/modifier
	 * @param asType The type of value to interpret the element as. The super wildcard is to allow for generics.
	 * @return The interpreted values for each metadata in all types on this element that fulfill the given metadata role
	 * @throws IllegalArgumentException If no such metadata exists on the type
	 * @throws QonfigInterpretationException If the value cannot be interpreted
	 */
	default <T> BetterList<T> interpretMetadata(String metadataName, Class<? super T> asType)
		throws IllegalArgumentException, QonfigInterpretationException {
		BetterList<QIS> md = forMetadata(metadataName);
		if (md.isEmpty())
			return BetterList.empty();
		Object[] interpreted = new Object[md.size()];
		int i = 0;
		for (QIS child : md) {
			interpreted[i] = child.interpret(asType);
		}
		return (BetterList<T>) (BetterList<?>) BetterList.of(interpreted);
	}

	/**
	 * @param <T> The type of value to interpret
	 * @param metaRole The metadata child on the element-def/add-on associated with the current creator/modifier
	 * @param asType The type of value to interpret the element as. The super wildcard is to allow for generics.
	 * @return The interpreted values for each metadata in all types on this element that fulfill the given metadata role
	 * @throws IllegalArgumentException If no such metadata exists on the type
	 * @throws QonfigInterpretationException If the value cannot be interpreted
	 */
	default <T> BetterList<T> interpretMetadata(QonfigChildDef metaRole, Class<? super T> asType)
		throws IllegalArgumentException, QonfigInterpretationException {
		BetterList<QIS> md = forMetadata(metaRole);
		if (md.isEmpty())
			return BetterList.empty();
		Object[] interpreted = new Object[md.size()];
		int i = 0;
		for (QIS child : md) {
			interpreted[i] = child.interpret(asType);
		}
		return (BetterList<T>) (BetterList<?>) BetterList.of(interpreted);
	}

	/**
	 * @param attribute The name of the attribute to get the position of
	 * @return The file position of the given text location
	 * @throws IllegalArgumentException If no such attribute exists in this element's types
	 */
	default LocatedContentPosition getAttributeValuePosition(String attribute) throws IllegalArgumentException {
		QonfigAttributeDef attr = getAttributeDef(null, null, attribute);
		if (attr == null)
			throw new IllegalArgumentException("Unrecognized attribute: " + attribute);
		QonfigValue value = getElement().getAttributes().get(attr.getDeclared());
		return value == null ? null : LocatedContentPosition.of(value.fileLocation, value.position);
	}

	/**
	 * @param attribute The name of the attribute to get the position of
	 * @param offset The offset within the attribute value to get the position at
	 * @return The file position of the given text location
	 * @throws IllegalArgumentException If no such attribute exists in this element's types
	 */
	default LocatedFilePosition getAttributeValuePosition(String attribute, int offset) throws IllegalArgumentException {
		QonfigAttributeDef attr = getAttributeDef(null, null, attribute);
		if (attr == null)
			throw new IllegalArgumentException("Unrecognized attribute: " + attribute);
		QonfigValue value = getElement().getAttributes().get(attr.getDeclared());
		return value == null ? null : new LocatedFilePosition(value.fileLocation, value.position.getPosition(offset));
	}

	/**
	 * @param offset The offset within the value to get the position at
	 * @return The file position of the given text location
	 * @throws IllegalArgumentException If this element's type has no value
	 */
	default LocatedFilePosition getValuePosition(int offset) throws IllegalArgumentException {
		if (getValueDef() == null)
			throw new IllegalArgumentException("No value possible");
		QonfigValue value = getElement().getValue();
		return value == null ? null : new LocatedFilePosition(value.fileLocation, value.position.getPosition(offset));
	}

	/**
	 * @param asType The type to query for
	 * @return Whether {@link #interpret(Class) interpretation} as a value of the given type is supported by this session
	 */
	boolean supportsInterpretation(Class<?> asType);

	/** @return The error reporting for this session */
	ErrorReporting reporting();

	/**
	 * @param sessionKey The key to get data for
	 * @return Data stored for the given key in this session
	 */
	Object get(String sessionKey);

	/**
	 * @param <T> The expected type of the value
	 * @param sessionKey The key to get data for
	 * @param type The expected type of the value. The super wildcard is to accommodate generics.
	 * @return Data stored for the given key in this session
	 */
	<T> T get(String sessionKey, Class<? super T> type);

	/**
	 * Puts a value into this session that will be visible to all descendants of this session (created after this call)
	 * 
	 * @param sessionKey The key to store data for
	 * @param value The data to store for the given key in this session
	 * @return This session
	 */
	QIS put(String sessionKey, Object value);

	/**
	 * Puts a value into this session that will be visible to all ancestors and descendants of this session (descendants created after this
	 * call)
	 * 
	 * @param sessionKey The key to store data for
	 * @param value The data to store for the given key in this session
	 * @return This session
	 */
	QIS putGlobal(String sessionKey, Object value);

	/**
	 * Puts a value into this session that will be visible only to sessions "parallel" to this session--sessions for the same element
	 * 
	 * @param sessionKey The key to store data for
	 * @param value The data to store for the given key in this session
	 * @return This session
	 */
	QIS putLocal(String sessionKey, Object value);

	/**
	 * @param <T> The type of the data
	 * @param sessionKey The key to store data for
	 * @param creator Creates data to store for the given key in this session (if absent)
	 * @return The previous or new value
	 */
	<T> T computeIfAbsent(String sessionKey, Supplier<T> creator);

	/** Implementation methods for {@link AbstractQIS} that I didn't want to expose */
	class Impl {
		private Impl() {
		}

		static QonfigElementOrAddOn getTargetElement(AbstractQIS<?> session, QonfigToolkit toolkit, String elementName)
			throws IllegalArgumentException {
			QonfigElementOrAddOn element = getTargetElement(toolkit, elementName, session.getFocusType());
			if (element == null) {
				for (QonfigElementOrAddOn type : session.getTypes().values()) {
					element = getTargetElement(toolkit, elementName, type);
					if (element != null)
						break;
				}
			}
			if (element == null)
				throw new IllegalArgumentException("No such element or add-on " + elementName + " in toolkit " + toolkit);
			else if (!session.getElement().isInstance(element))
				throw new IllegalArgumentException("Element is not an instance of " + element);
			return element;
		}

		private static QonfigElementOrAddOn getTargetElement(QonfigToolkit toolkit, String elementName, QonfigElementOrAddOn type) {
			if (elementName == null)
				return type;
			else {
				if (toolkit == null) {
					if (elementName.equals(type.getName()))
						return type;
					toolkit = type.getDeclarer();
					return toolkit.getElementOrAddOn(elementName);
				} else if (type.getDeclarer() == toolkit && type.getName().equals(elementName))
					return type;
				else
					return toolkit.getElementOrAddOn(elementName);
			}
		}

		static void addMetadata(QonfigChildDef child, List<QonfigElement> metadata, Set<QonfigAddOn> addOns, QonfigElementOrAddOn type) {
			if (!child.getOwner().isAssignableFrom(type.getMetaSpec()))
				return;
			else if (type instanceof QonfigAddOn && !addOns.add((QonfigAddOn) type))
				return;
			if (type instanceof QonfigElementDef && type.getSuperElement() != null)
				addMetadata(child, metadata, addOns, type.getSuperElement());
			metadata.addAll(type.getMetadata().getRoot().getChildrenByRole().get(child.getDeclared()));
			for (QonfigAddOn inh : type.getInheritance())
				addMetadata(child, metadata, addOns, inh);
		}
	}
}

package org.qommons.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.qommons.collect.BetterList;
import org.qommons.ex.ExFunction;

/**
 * An interpretation session with lots of capabilities for interpreting {@link QonfigElement}s
 * 
 * @param <QIS> The sub-type of the session
 */
public interface AbstractQIS<QIS extends AbstractQIS<QIS>> {
	/** @return The element being interpreted */
	QonfigElement getElement();

	/** @return The element or add-on type that the element is being interpreted as */
	QonfigElementOrAddOn getType();

	/**
	 * @param type The element/add-on type to interpret the element as
	 * @return A session based off this session but being interpreted as the given element
	 */
	QIS asElement(QonfigElementOrAddOn type);

	/**
	 * @param typeName The name of the element/add-on type to interpret the element as
	 * @return A session based off this session but being interpreted as the given element
	 */
	default QIS asElement(String typeName) {
		QonfigElementOrAddOn type = getType().getDeclarer().getElementOrAddOn(typeName);
		if (type == null)
			throw new IllegalArgumentException(
				"No such element or add-on '" + typeName + "' in toolkit " + getType().getDeclaredChildren());
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
	 * @return The value of the target attribute
	 * @throws IllegalArgumentException If no such attribute exists on the element/add-on
	 */
	default String getAttributeText(String attributeName) throws IllegalArgumentException {
		return getAttributeText(null, attributeName);
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
		QonfigElementOrAddOn element;
		if (asElement == null || getType().getName().equals(asElement))
			element = getType();
		else {
			element = getType().getDeclarer().getElementOrAddOn(asElement);
			if (element == null)
				throw new IllegalArgumentException("No such element or add-on " + asElement + " in toolkit " + getType().getDeclarer());
			else if (!getElement().isInstance(element))
				throw new IllegalArgumentException("Element is not an instance of " + element);
		}
		QonfigAttributeDef attr = element.getAttribute(attributeName);
		if (attr == null)
			throw new IllegalArgumentException("No such attribute " + element + "." + attributeName);
		T value = getElement().getAttribute(attr, type);
		if (value == null)
			return defaultValue;
		return value;
	}

	/**
	 * @param asElement The name of the element type containing the attribute
	 * @param attributeName The name of the attribute on the element-def/add-on associated with the current creator/modifier
	 * @return The value of the target attribute
	 * @throws IllegalArgumentException If no such attribute exists on the element/add-on
	 */
	default String getAttributeText(String asElement, String attributeName) throws IllegalArgumentException {
		QonfigElementOrAddOn element;
		if (asElement == null || getType().getName().equals(asElement))
			element = getType();
		else {
			element = getType().getDeclarer().getElementOrAddOn(asElement);
			if (element == null)
				throw new IllegalArgumentException("No such element or add-on " + asElement + " in toolkit " + getType().getDeclarer());
			else if (!getElement().isInstance(element))
				throw new IllegalArgumentException("Element is not an instance of " + element);
		}
		QonfigAttributeDef attr = element.getAttribute(attributeName);
		if (attr == null)
			throw new IllegalArgumentException("No such attribute " + element + "." + attributeName);
		return getElement().getAttributeText(attr);
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
		if (value == null && getType().getValue() == null)
			throw new IllegalArgumentException("No value defined for " + getType());
		if (value == null)
			return defaultValue;
		return type.cast(value);
	}

	/** @return The element's value as text */
	default String getValueText() {
		return getElement().getValueText();
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
		QonfigElementOrAddOn element;
		if (asElement == null || getType().getName().equals(asElement))
			element = getType();
		else {
			element = getType().getDeclarer().getElementOrAddOn(asElement);
			if (element == null)
				throw new IllegalArgumentException("No such element or add-on " + asElement + " in toolkit " + getType().getDeclarer());
			else if (!getElement().isInstance(element))
				throw new IllegalArgumentException("Element is not an instance of " + element);
		}
		QonfigChildDef role = element.getChild(roleName);
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
		if (getType().getName().equals(elementName))
			return true;
		else {
			QonfigElementOrAddOn element = getType().getDeclarer().getElementOrAddOn(elementName);
			if (element == null)
				throw new IllegalArgumentException("No such element or add-on " + elementName + " in toolkit " + getType().getDeclarer());
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
		return interpret(getType(), asType);
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
		QonfigAttributeDef attr = getType().getAttribute(attributeName);
		if (attr == null)
			throw new IllegalArgumentException("No such attribute " + getType() + "." + attributeName);
		C attrValue = getElement().getAttribute(attr, sourceType);
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
		Object value = getElement().getValue();
		if (value == null && getType().getValueSpec() == null)
			throw new IllegalArgumentException("No value defined for " + getType());
		else if (nullToNull && value == null)
			return null;
		return map.apply(sourceType.cast(value));
	}

	/**
	 * @param childName The name of the child on the element-def/add-on associated with the current creator/modifier
	 * @return All children in this element that fulfill the given child role
	 * @throws IllegalArgumentException If no such child exists on the element/add-on
	 */
	default BetterList<QonfigElement> getChildren(String childName) throws IllegalArgumentException {
		QonfigChildDef child = getType().getChild(childName);
		if (child == null)
			throw new IllegalArgumentException("No such child " + getType() + "." + childName);
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
		QonfigChildDef child = getType().getChild(childName);
		if (child == null)
			throw new IllegalArgumentException("No such child " + getType() + "." + childName);
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
		QonfigChildDef child = getType().getChild(childName);
		if (child == null)
			throw new IllegalArgumentException("No such child " + getType() + "." + childName);
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
		QonfigElement.Builder b = getElement().synthesizeChild(child, defaultChild, getParseSession());
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
	 * @param metadataName The name of the metadata child on this session's element type to interpret the children for
	 * @return A list of interpretation sessions for each metadata on all types in this element with the given metadata role
	 * @throws IllegalArgumentException If no such metadata role exists
	 * @throws QonfigInterpretationException If an error occurs initializing the metadata's interpretation
	 */
	default BetterList<QIS> forMetadata(String metadataName) throws IllegalArgumentException, QonfigInterpretationException {
		QonfigChildDef child = getType().getMetaSpec().getChild(metadataName);
		if (child == null)
			throw new IllegalArgumentException("No such metadata " + getType() + "." + metadataName);
		List<QonfigElement> metadata = new ArrayList<>();
		Set<QonfigAddOn> addOns = new HashSet<>();
		Impl.addMetadata(child, metadata, addOns, getElement().getType());
		for (QonfigAddOn inh : getElement().getInheritance().getExpanded(QonfigAddOn::getInheritance))
			Impl.addMetadata(child, metadata, addOns, inh);
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
	 * @param sessionKey The key to store data for
	 * @param value The data to store for the given key in this session
	 * @return This session
	 */
	QIS put(String sessionKey, Object value);

	/**
	 * @param sessionKey The key to store data for
	 * @param value The data to store for the given key in this session
	 * @return This session
	 */
	QIS putGlobal(String sessionKey, Object value);

	/**
	 * @param <T> The type of the data
	 * @param sessionKey The key to store data for
	 * @param creator Creates data to store for the given key in this session (if absent)
	 * @return The previous or new value
	 */
	<T> T computeIfAbsent(String sessionKey, Supplier<T> creator);

	/**
	 * @param message The warning message to log
	 * @return This session
	 */
	default QIS withWarning(String message) {
		return withWarning(message, null);
	}

	/**
	 * @param message The warning message to log
	 * @param cause The cause of the warning, if any
	 * @return This session
	 */
	default QIS withWarning(String message, Throwable cause) {
		getParseSession().withWarning(message, cause);
		return (QIS) this;
	}

	/**
	 * @param message The error message to log
	 * @return This session
	 */
	default QIS withError(String message) {
		return withError(message, null);
	}

	/**
	 * @param message The error message to log
	 * @param cause The cause of the error, if any
	 * @return This session
	 */
	default QIS withError(String message, Throwable cause) {
		getParseSession().withError(message, cause);
		return (QIS) this;
	}

	/**
	 * The parse session contains the ability to log messages in a way that provides the user with detailed context
	 * 
	 * @return The parse session for this interpretation session
	 */
	QonfigParseSession getParseSession();

	/** Implementation methods for {@link AbstractQIS} that I didn't want to expose */
	class Impl {
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

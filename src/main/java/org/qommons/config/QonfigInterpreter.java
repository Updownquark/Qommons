package org.qommons.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.qommons.BiTuple;
import org.qommons.ClassMap;
import org.qommons.QommonsUtils;
import org.qommons.StatusReportAccumulator;
import org.qommons.StatusReportAccumulator.Status;
import org.qommons.collect.BetterList;
import org.qommons.ex.ExFunction;

/**
 * A class for interpreting parsed {@link QonfigDocument}s into useful structures
 * 
 * @param <QIS> The session type provided by this interpreter
 */
public abstract class QonfigInterpreter<QIS extends QonfigInterpreter.QonfigInterpretingSession<?>> {
	/**
	 * Holds values for communication between parsing components
	 * 
	 * @param <QIS> The sub-type of this session
	 */
	public static class QonfigInterpretingSession<QIS extends QonfigInterpretingSession<QIS>> {
		private final QonfigInterpreter<QIS> theInterpreter;
		private final QIS theParent;
		private final QonfigParseSession theParseSession;
		private final QonfigElement theElement;
		private final QonfigElementOrAddOn theType;
		private final int theChildIndex;
		private final Map<String, Object> theValues;

		/**
		 * Creates the root session for interpretation
		 * 
		 * @param interpreter The interpreter this session is for
		 * @param root The root element of the interpretation
		 * @throws QonfigInterpretationException If an error occurs initializing this session
		 */
		protected QonfigInterpretingSession(QonfigInterpreter<QIS> interpreter, QonfigElement root) throws QonfigInterpretationException {
			theInterpreter = interpreter;
			theParent = null;
			theParseSession = QonfigParseSession.forRoot(root.getType().getName(), root.getDocument().getDocToolkit());
			theElement = root;
			theType = root.getType();
			theValues = new HashMap<>();
			theChildIndex = 0;
		}

		/**
		 * Creates a sub-session for interpretation
		 * 
		 * @param parent The parent session
		 * @param element The element to interpret
		 * @param type The element/add-on type to interpret as (affects what attributes and children are available by name)
		 * @param childIndex The index of the child in its parent, for improved visibility of errors
		 * @throws QonfigInterpretationException If an error occurs initializing this session
		 */
		protected QonfigInterpretingSession(QIS parent, QonfigElement element, QonfigElementOrAddOn type, int childIndex)
			throws QonfigInterpretationException {
			QonfigInterpretingSession<QIS> parent2 = parent;
			theInterpreter = parent2.theInterpreter;
			theParent = parent;
			theElement = element;
			theType = type;
			theChildIndex = childIndex;
			if (parent.getElement() == element) {
				theParseSession = parent2.theParseSession;
				theValues = parent2.theValues;
			} else {
				theParseSession = parent2.theParseSession.forChild(element.getType().getName(), childIndex);
				theValues = new HashMap<>(parent2.theValues);
			}
		}

		/** @return The element being interpreted */
		public QonfigElement getElement() {
			return theElement;
		}

		/** @return The interpreter this session is for */
		public QonfigInterpreter<QIS> getInterpreter() {
			return theInterpreter;
		}

		/** @return The element or add-on type that the element is being interpreted as */
		public QonfigElementOrAddOn getType() {
			return theType;
		}

		/** @return This session's parent */
		protected QIS getParent() {
			return theParent;
		}

		/**
		 * @param type The element/add-on type to interpret the element as
		 * @return A session based off this session but being interpreted as the given element
		 */
		public QIS as(QonfigElementOrAddOn type) {
			if (theType == type)
				return (QIS) this;
			else if (theElement.isInstance(type)) {
				try {
					return theInterpreter.interpret((QIS) this, theElement, type, theChildIndex);
				} catch (QonfigInterpretationException e) {
					throw new IllegalStateException("Initialization failure for same-element session?", e);
				}
			} else {
				String msg = "Element " + theElement + " is not an instance of " + type;
				withError(msg);
				throw new IllegalStateException(msg);
			}
		}

		/**
		 * @param typeName The name of the element/add-on type to interpret the element as
		 * @return A session based off this session but being interpreted as the given element
		 */
		public QIS as(String typeName) {
			QonfigElementOrAddOn type = theType.getDeclarer().getElementOrAddOn(typeName);
			if (type == null)
				throw new IllegalArgumentException(
					"No such element or add-on '" + typeName + "' in toolkit " + theType.getDeclaredChildren());
			return as(type);
		}

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
		public <T> T getAttribute(String attributeName, Class<T> type) throws IllegalArgumentException {
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
		public <T> T getAttribute(String attributeName, Class<T> type, T defaultValue) throws IllegalArgumentException {
			return getAttribute(null, attributeName, type, defaultValue);
		}

		/**
		 * @param attributeName The name of the attribute on the element-def/add-on associated with the current creator/modifier
		 * @return The value of the target attribute
		 * @throws IllegalArgumentException If no such attribute exists on the element/add-on
		 */
		public String getAttributeText(String attributeName) throws IllegalArgumentException {
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
		public <T> T getAttribute(String asElement, String attributeName, Class<T> type) throws IllegalArgumentException {
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
		public <T> T getAttribute(String asElement, String attributeName, Class<T> type, T defaultValue) throws IllegalArgumentException {
			QonfigElementOrAddOn element;
			if (asElement == null || theType.getName().equals(asElement))
				element = theType;
			else {
				element = theType.getDeclarer().getElementOrAddOn(asElement);
				if (element == null)
					throw new IllegalArgumentException("No such element or add-on " + asElement + " in toolkit " + theType.getDeclarer());
				else if (!theElement.isInstance(element))
					throw new IllegalArgumentException("Element is not an instance of " + element);
			}
			QonfigAttributeDef attr = element.getAttribute(attributeName);
			if (attr == null)
				throw new IllegalArgumentException("No such attribute " + element + "." + attributeName);
			T value = theElement.getAttribute(attr, type);
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
		public String getAttributeText(String asElement, String attributeName) throws IllegalArgumentException {
			QonfigElementOrAddOn element;
			if (asElement == null || theType.getName().equals(asElement))
				element = theType;
			else {
				element = theType.getDeclarer().getElementOrAddOn(asElement);
				if (element == null)
					throw new IllegalArgumentException("No such element or add-on " + asElement + " in toolkit " + theType.getDeclarer());
				else if (!theElement.isInstance(element))
					throw new IllegalArgumentException("Element is not an instance of " + element);
			}
			QonfigAttributeDef attr = element.getAttribute(attributeName);
			if (attr == null)
				throw new IllegalArgumentException("No such attribute " + element + "." + attributeName);
			return theElement.getAttributeText(attr);
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
		public <T> T getValue(Class<T> type, T defaultValue) throws IllegalArgumentException, ClassCastException {
			Object value = theElement.getValue();
			if (value == null && theType.getValueSpec() == null)
				throw new IllegalArgumentException("No value defined for " + theType);
			if (value == null)
				return defaultValue;
			return type.cast(value);
		}

		/** @return The element's value as text */
		public String getValueText() {
			return theElement.getValueText();
		}

		/**
		 * @param roleName The name of the role to get
		 * @return The role for the given name
		 * @throws IllegalArgumentException If the given role does not exist
		 */
		public QonfigChildDef getRole(String roleName) throws IllegalArgumentException {
			return getRole(null, roleName);
		}

		/**
		 * @param asElement The name of the element or add-on type to interpret the element as
		 * @param roleName The name of the role to get
		 * @return The role with the given name
		 * @throws IllegalArgumentException If the given element type or role does not exist, or this element is not an instance of the
		 *         given type
		 */
		public QonfigChildDef getRole(String asElement, String roleName) throws IllegalArgumentException {
			QonfigElementOrAddOn element;
			if (asElement == null || theType.getName().equals(asElement))
				element = theType;
			else {
				element = theType.getDeclarer().getElementOrAddOn(asElement);
				if (element == null)
					throw new IllegalArgumentException("No such element or add-on " + asElement + " in toolkit " + theType.getDeclarer());
				else if (!theElement.isInstance(element))
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
		public boolean fulfills(QonfigChildDef role) {
			return theElement.getDeclaredRoles().contains(role.getDeclared());
		}

		/**
		 * @param elementName The name of the element/add-on type to test
		 * @return Whether the element is an instance of the given type
		 */
		public boolean isInstance(String elementName) {
			if (theType.getName().equals(elementName))
				return true;
			else {
				QonfigElementOrAddOn element = theType.getDeclarer().getElementOrAddOn(elementName);
				if (element == null)
					throw new IllegalArgumentException("No such element or add-on " + elementName + " in toolkit " + theType.getDeclarer());
				else
					return theElement.isInstance(element);
			}
		}

		/**
		 * @param <T> The type of value to interpret
		 * @param asType The type of value to interpret the element as
		 * @return The interpreted value
		 * @throws QonfigInterpretationException If the value cannot be interpreted
		 */
		public <T> T interpret(Class<T> asType) throws QonfigInterpretationException {
			return interpret(theType, asType);
		}

		/**
		 * @param <T> The type of value to interpret
		 * @param as The element type to interpret the element as
		 * @param asType The type of value to interpret the element as
		 * @return The interpreted value
		 * @throws QonfigInterpretationException If the value cannot be interpreted
		 */
		public <T> T interpret(QonfigElementOrAddOn as, Class<T> asType) throws QonfigInterpretationException {
			ClassMap<QonfigCreatorHolder<QIS, ?>> creators = theInterpreter.theCreators.get(as);
			QonfigCreatorHolder<? super QIS, T> creator = creators == null ? null
				: (QonfigCreatorHolder<? super QIS, T>) creators.get(asType, ClassMap.TypeMatch.SUB_TYPE);
			if (creator == null) {
				String msg = "No creator registered for element " + as.getName() + " and target type " + asType.getName();
				withError(msg);
				throw new IllegalStateException(msg);
			}
			QIS session;
			if (theType == creator.element)
				session = (QIS) this;
			else if (theElement.isInstance(creator.element)) {
				session = theInterpreter.interpret((QIS) this, theElement, as, theChildIndex);
			} else {
				String msg = "Element " + theElement + " is not an instance of " + as;
				withError(msg);
				throw new IllegalStateException(msg);
			}
			Collection<QonfigModifierHolder<QIS, T>> modifiers = getModifiers(creator.type);
			for (QonfigModifierHolder<QIS, T> modifier : modifiers) {
				try {
					if (theElement.isInstance(modifier.element))
						modifier.modifier.prepareSession(session.as(modifier.element));
				} catch (QonfigInterpretationException | RuntimeException e) {
					if (theInterpreter.loggedThrowable != e) {
						theInterpreter.loggedThrowable = e;
						session.withError("Modifier " + modifier.modifier + " for "//
							+ (modifier.element instanceof QonfigElementDef ? "super type " : "add-on ") + modifier.element + " on type "
							+ modifier.type.getName() + " failed to prepare session for the creator", e);
					}
					throw e;
				}
			}
			T value;
			try {
				value = creator.creator.createValue(session);
			} catch (QonfigInterpretationException | RuntimeException e) {
				if (theInterpreter.loggedThrowable != e) {
					theInterpreter.loggedThrowable = e;
					session.withError(
						"Creator " + creator.creator + " for element " + as + " failed to create value for element " + theElement, e);
				}
				if (theParent != null)
					throw e;
				else
					throw new QonfigInterpretationException(theParseSession.createException(e.getMessage()));
			}
			for (QonfigModifierHolder<QIS, T> modifier : modifiers) {
				try {
					if (theElement.isInstance(modifier.element))
						value = modifier.modifier.modifyValue(value, session.as(modifier.element));
				} catch (QonfigInterpretationException | RuntimeException e) {
					if (theInterpreter.loggedThrowable != e) {
						theInterpreter.loggedThrowable = e;
						session.withError("Modifier " + modifier.modifier + " for "//
							+ (modifier.element instanceof QonfigElementDef ? "super type " : "add-on ") + modifier.element + " on type "
							+ modifier.type.getName() + " failed to modify value " + value + " for element", e);
					}
					throw e;
				}
			}
			try {
				// This is safe because the creator created the value
				value = ((QonfigValueCreator<QIS, T>) creator.creator).postModification(value, session);
			} catch (QonfigInterpretationException | RuntimeException e) {
				if (theInterpreter.loggedThrowable != e) {
					theInterpreter.loggedThrowable = e;
					session.withError("Creator " + creator.creator + " for element " + as + " post-modification failed for value " + value
						+ " for element " + theElement, e);
				}
				if (theParent != null)
					throw e;
				else
					throw new QonfigInterpretationException(theParseSession.createException(e.getMessage()));
			}
			return value;
		}

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
		public <C, T> T interpretAttribute(String attributeName, Class<C> sourceType, boolean nullToNull,
			ExFunction<? super C, ? extends T, QonfigInterpretationException> map)
			throws IllegalArgumentException, QonfigInterpretationException {
			QonfigAttributeDef attr = theType.getAttribute(attributeName);
			if (attr == null)
				throw new IllegalArgumentException("No such attribute " + theType + "." + attributeName);
			C attrValue = theElement.getAttribute(attr, sourceType);
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
		 * @param nullToNull Whether to return null if the element's value was not specified instead of passing null to the given
		 *        interpreter function
		 * @param map Function to interpret the element's value
		 * @return The interpreted value
		 * @throws IllegalArgumentException If this element has no value definition
		 * @throws ClassCastException If the value is not of the expected type
		 * @throws QonfigInterpretationException If the given interpreter function throws it
		 */
		public <C, T> T interpretValue(Class<C> sourceType, boolean nullToNull,
			ExFunction<? super C, ? extends T, QonfigInterpretationException> map)
			throws IllegalArgumentException, ClassCastException, QonfigInterpretationException {
			Object value = theElement.getValue();
			if (value == null && theType.getValueSpec() == null)
				throw new IllegalArgumentException("No value defined for " + theType);
			else if (nullToNull && value == null)
				return null;
			return map.apply(sourceType.cast(value));
		}

		/**
		 * @param childName The name of the child on the element-def/add-on associated with the current creator/modifier
		 * @return All children in this element that fulfill the given child role
		 * @throws IllegalArgumentException If no such child exists on the element/add-on
		 */
		public BetterList<QonfigElement> getChildren(String childName) throws IllegalArgumentException {
			QonfigChildDef child = theType.getChild(childName);
			if (child == null)
				throw new IllegalArgumentException("No such child " + theType + "." + childName);
			return (BetterList<QonfigElement>) theElement.getChildrenByRole().get(child.getDeclared());
		}

		/**
		 * @param childName The name of the child role to interpret the children for
		 * @return A list of interpretation sessions for each child in this element with the given role
		 * @throws IllegalArgumentException If no such child role exists
		 * @throws QonfigInterpretationException If an error occurs initializing the children's interpretation
		 */
		public BetterList<QIS> forChildren(String childName) throws IllegalArgumentException, QonfigInterpretationException {
			QonfigChildDef child = theType.getChild(childName);
			if (child == null)
				throw new IllegalArgumentException("No such child " + theType + "." + childName);
			BetterList<QonfigElement> children = (BetterList<QonfigElement>) theElement.getChildrenByRole().get(child.getDeclared());
			if (children.isEmpty())
				return BetterList.empty();
			QonfigInterpretingSession<QIS>[] sessions = new QonfigInterpretingSession[children.size()];
			int i = 0;
			for (QonfigElement ch : children) {
				sessions[i] = theInterpreter.interpret((QIS) this, ch, ch.getType(), i);
				i++;
			}
			return BetterList.of((QIS[]) sessions);
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
		public BetterList<QIS> forChildren(String childName, QonfigElementDef defaultChild, Consumer<QonfigElement.Builder> builder)
			throws IllegalArgumentException, QonfigInterpretationException {
			QonfigChildDef child = theType.getChild(childName);
			if (child == null)
				throw new IllegalArgumentException("No such child " + theType + "." + childName);
			BetterList<QonfigElement> children = (BetterList<QonfigElement>) theElement.getChildrenByRole().get(child.getDeclared());
			if (!children.isEmpty()) {
				QonfigInterpretingSession<QIS>[] sessions = new QonfigInterpretingSession[children.size()];
				int i = 0;
				for (QonfigElement ch : children) {
					sessions[i] = theInterpreter.interpret((QIS) this, ch, ch.getType(), i);
					i++;
				}
				return BetterList.of((QIS[]) sessions);
			}
			QonfigElement.Builder b = theElement.synthesizeChild(child, defaultChild, theParseSession);
			if (builder != null)
				builder.accept(b);
			QonfigElement element = b.doneWithAttributes().build();
			return BetterList.of(theInterpreter.interpret((QIS) this, element, defaultChild, 0));
		}

		/**
		 * @return A list of interpretation sessions for each child in this element
		 * @throws QonfigInterpretationException If an error occurs initializing the children's interpretation
		 */
		public BetterList<QIS> forChildren() throws QonfigInterpretationException {
			List<QonfigElement> children = getElement().getChildren();
			if (children.isEmpty())
				return BetterList.empty();
			QonfigInterpretingSession<QIS>[] sessions = new QonfigInterpretingSession[children.size()];
			int i = 0;
			for (QonfigElement child : children) {
				sessions[i] = theInterpreter.interpret((QIS) this, child, child.getType(), i);
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
		public <T> BetterList<T> interpretChildren(String childName, Class<? super T> asType)
			throws IllegalArgumentException, QonfigInterpretationException {
			BetterList<QonfigElement> children = getChildren(childName);
			if (children.isEmpty())
				return BetterList.empty();
			Object[] interpreted = new Object[children.size()];
			int i = 0;
			for (QonfigElement child : children) {
				interpreted[i] = theInterpreter.interpret((QIS) this, child, child.getType(), i).interpret(asType);
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
		public BetterList<QIS> forMetadata(String metadataName) throws IllegalArgumentException, QonfigInterpretationException {
			QonfigChildDef child = theType.getMetaSpec().getChild(metadataName);
			if (child == null)
				throw new IllegalArgumentException("No such metadata " + theType + "." + metadataName);
			List<QonfigElement> metadata = new ArrayList<>();
			Set<QonfigAddOn> addOns = new HashSet<>();
			addMetadata(child, metadata, addOns, theElement.getType());
			for (QonfigAddOn inh : theElement.getInheritance().getExpanded(QonfigAddOn::getInheritance))
				addMetadata(child, metadata, addOns, inh);
			if (metadata.isEmpty())
				return BetterList.empty();
			QonfigInterpretingSession<QIS>[] sessions = new QonfigInterpretingSession[metadata.size()];
			int i = 0;
			for (QonfigElement ch : metadata) {
				sessions[i] = theInterpreter.interpret((QIS) this, ch, ch.getType(), i);
				i++;
			}
			return BetterList.of((QIS[]) sessions);
		}

		private void addMetadata(QonfigChildDef child, List<QonfigElement> metadata, Set<QonfigAddOn> addOns, QonfigElementOrAddOn type) {
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

		/**
		 * @param <T> The type of value to interpret
		 * @param metadataName The name of the metadata child on the element-def/add-on associated with the current creator/modifier
		 * @param asType The type of value to interpret the element as. The super wildcard is to allow for generics.
		 * @return The interpreted values for each metadata in all types on this element that fulfill the given metadata role
		 * @throws IllegalArgumentException If no such metadata exists on the type
		 * @throws QonfigInterpretationException If the value cannot be interpreted
		 */
		public <T> BetterList<T> interpretMetadata(String metadataName, Class<? super T> asType)
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
		public Object get(String sessionKey) {
			return theValues.get(sessionKey);
		}

		/**
		 * @param <T> The expected type of the value
		 * @param sessionKey The key to get data for
		 * @param type The expected type of the value. The super wildcard is to accommodate generics.
		 * @return Data stored for the given key in this session
		 */
		public <T> T get(String sessionKey, Class<? super T> type) {
			Object value = theValues.get(sessionKey);
			return (T) type.cast(value);
		}

		/**
		 * @param sessionKey The key to store data for
		 * @param value The data to store for the given key in this session
		 * @return This session
		 */
		public QIS put(String sessionKey, Object value) {
			theValues.put(sessionKey, value);
			return (QIS) this;
		}

		/**
		 * @param sessionKey The key to store data for
		 * @param value The data to store for the given key in this session
		 * @return This session
		 */
		public QIS putGlobal(String sessionKey, Object value) {
			QonfigInterpretingSession<QIS> session = this;
			while (session != null) {
				session.theValues.put(sessionKey, value);
				session = session.theParent;
			}
			return (QIS) this;
		}

		/**
		 * @param <T> The type of the data
		 * @param sessionKey The key to store data for
		 * @param creator Creates data to store for the given key in this session (if absent)
		 * @return The previous or new value
		 */
		public <T> T computeIfAbsent(String sessionKey, Supplier<T> creator) {
			return (T) theValues.computeIfAbsent(sessionKey, __ -> creator.get());
		}

		/**
		 * @param message The warning message to log
		 * @return This session
		 */
		public QIS withWarning(String message) {
			return withWarning(message, null);
		}

		/**
		 * @param message The warning message to log
		 * @param cause The cause of the warning, if any
		 * @return This session
		 */
		public QIS withWarning(String message, Throwable cause) {
			theParseSession.withWarning(message, cause);
			return (QIS) this;
		}

		/**
		 * @param message The error message to log
		 * @return This session
		 */
		public QIS withError(String message) {
			return withError(message, null);
		}

		/**
		 * @param message The error message to log
		 * @param cause The cause of the error, if any
		 * @return This session
		 */
		public QIS withError(String message, Throwable cause) {
			theParseSession.withError(message, cause);
			return (QIS) this;
		}

		<T> Collection<QonfigModifierHolder<QIS, T>> getModifiers(Class<T> type) throws QonfigInterpretationException {
			Map<QonfigValueModifier<? super QIS, T>, QonfigModifierHolder<QIS, T>> modifiers = new LinkedHashMap<>();
			getModifiers(type, theElement.getType().getSuperElement(), modifiers);
			Set<QonfigAddOn> inh = new HashSet<>();
			for (QonfigAddOn el : theElement.getType().getFullInheritance().values()) {
				if (inh.add(el))
					modifyWith(type, el, inh, modifiers);
			}
			doModify(type, theElement.getType(), modifiers);
			for (QonfigAddOn el : theElement.getInheritance().values()) {
				if (inh.add(el))
					modifyWith(type, el, inh, modifiers);
			}
			return modifiers.values();
		}

		private <T> void getModifiers(Class<T> type, QonfigElementDef superType,
			Map<QonfigValueModifier<? super QIS, T>, QonfigModifierHolder<QIS, T>> modifiers) throws QonfigInterpretationException {
			if (superType == null)
				return;
			getModifiers(type, superType.getSuperElement(), modifiers);
			doModify(type, superType, modifiers);
		}

		private <T> void modifyWith(Class<T> type, QonfigAddOn addOn, Set<QonfigAddOn> inh,
			Map<QonfigValueModifier<? super QIS, T>, QonfigModifierHolder<QIS, T>> modifiers) throws QonfigInterpretationException {
			for (QonfigAddOn ext : addOn.getFullInheritance().getExpanded(QonfigAddOn::getInheritance)) {
				if (inh.add(ext))
					modifyWith(type, ext, inh, modifiers);
			}
			doModify(type, addOn, modifiers);
		}

		private <T> void doModify(Class<T> type, QonfigElementOrAddOn modifierType,
			Map<QonfigValueModifier<? super QIS, T>, QonfigModifierHolder<QIS, T>> modifiers2) throws QonfigInterpretationException {
			if (modifierType == null)
				return;
			ClassMap<QonfigModifierHolder<QIS, ?>> modifiers = theInterpreter.theModifiers.get(modifierType);
			if (modifiers == null)
				return;
			List<BiTuple<Class<?>, QonfigModifierHolder<QIS, ?>>> typeModifiers = modifiers.getAllEntries(type,
				ClassMap.TypeMatch.SUPER_TYPE);
			if (typeModifiers == null)
				return;
			for (BiTuple<Class<?>, QonfigModifierHolder<QIS, ?>> modifier : typeModifiers) {
				modifiers2.putIfAbsent((QonfigValueModifier<? super QIS, T>) modifier.getValue2().modifier,
					(QonfigModifierHolder<QIS, T>) modifier.getValue2());
			}
		}

		@Override
		public String toString() {
			return "Interpreting " + theElement + " with " + theType;
		}
	}

	/**
	 * Interprets {@link QonfigElement}s, creating values for them
	 * 
	 * @param <QIS> The session type expected by this creator
	 * @param <T> The type of value created
	 */
	public interface QonfigValueCreator<QIS extends QonfigInterpretingSession<?>, T> {
		/**
		 * @param element The element to create the value for
		 * @param session The active interpreter session
		 * @return The created value
		 * @throws QonfigInterpretationException If the value could not be created
		 */
		T createValue(QIS session) throws QonfigInterpretationException;

		/**
		 * Potentially modifies and/or inspects the value after all modification
		 * 
		 * @param value The interpreted value so far
		 * @param session The interpretation session
		 * @return The final interpreted value
		 * @throws QonfigInterpretationException If an error occurs
		 */
		default T postModification(T value, QIS session) throws QonfigInterpretationException {
			return value;
		}
	}

	/**
	 * Interprets {@link QonfigElement}s using the value interpreted from a super-element
	 * 
	 * @param <QIS> The session type expected by this extension
	 * @param <S> The type of value interpreted by the super-creator
	 * @param <T> The type of value created by this extension
	 */
	public interface QonfigValueExtension<QIS extends QonfigInterpretingSession<?>, S, T> {
		/**
		 * @param superValue The value created by the super-creator
		 * @param session The active interpreter session
		 * @return The value for the element
		 * @throws QonfigInterpretationException If the value could not be created or modified
		 */
		T createValue(S superValue, QIS session) throws QonfigInterpretationException;
	}

	/**
	 * Modifies an interpreted value for an add-on or abstract element-def
	 * 
	 * @param <QIS> The session type expected by this modifier
	 * @param <T> The type of value to modify
	 */
	public interface QonfigValueModifier<QIS extends QonfigInterpretingSession<?>, T> {
		/**
		 * @param session The active interpreter session
		 * @throws QonfigInterpretationException If anything goes wrong with the preparation
		 */
		default void prepareSession(QIS session) throws QonfigInterpretationException {
		}

		/**
		 * @param value The value created for the element
		 * @param session The active interpreter session
		 * @return The modified value
		 * @throws QonfigInterpretationException If the value could not be modified
		 */
		T modifyValue(T value, QIS session) throws QonfigInterpretationException;
	}

	static class QonfigExtensionCreator<QIS extends QonfigInterpretingSession<?>, S, T> implements QonfigValueCreator<QIS, T> {
		final QonfigElementOrAddOn theSuperElement;
		final Class<S> theSuperType;
		final QonfigValueExtension<? super QIS, S, T> theExtension;

		QonfigExtensionCreator(QonfigElementOrAddOn superElement, Class<S> superType, QonfigValueExtension<? super QIS, S, T> extension) {
			theSuperElement = superElement;
			theSuperType = superType;
			theExtension = extension;
		}

		@Override
		public T createValue(QIS session) throws QonfigInterpretationException {
			S superValue = session.as(theSuperElement).interpret(theSuperType);
			return theExtension.createValue(superValue, session);
		}
	}

	static class QonfigCreationDelegator<QIS extends QonfigInterpretingSession<?>, T> implements QonfigValueCreator<QIS, T> {
		private final QonfigAttributeDef.Declared theTypeAttribute;
		private final Class<T> theType;

		public QonfigCreationDelegator(QonfigAttributeDef.Declared typeAttribute, Class<T> type) {
			theTypeAttribute = typeAttribute;
			theType = type;
		}

		@Override
		public T createValue(QIS session) throws QonfigInterpretationException {
			QonfigAddOn delegate = (QonfigAddOn) session.getElement().getAttributes().get(theTypeAttribute);
			return session.interpret(delegate, theType);
		}

		@Override
		public String toString() {
			return "Delegate creation of " + theType + " to " + theTypeAttribute;
		}
	}

	/**
	 * Class holding information regarding creation of a value for Qonfig interpretation
	 * 
	 * @param <QIS> The sub-type of session provided by the interpreter this holder is for
	 * @param <T> The type of value to create
	 */
	protected static class QonfigCreatorHolder<QIS extends QonfigInterpretingSession<?>, T> {
		final QonfigElementOrAddOn element;
		final Class<T> type;
		final QonfigValueCreator<? super QIS, ? extends T> creator;

		QonfigCreatorHolder(QonfigElementOrAddOn element, Class<T> type, QonfigValueCreator<? super QIS, ? extends T> creator) {
			this.element = element;
			this.type = type;
			this.creator = creator;
		}

		@Override
		public String toString() {
			return "Create " + type.getName() + " from " + element;
		}
	}

	/**
	 * Class holding information regarding modification of a value for Qonfig interpretation
	 * 
	 * @param <QIS> The sub-type of session provided by the interpreter this holder is for
	 * @param <T> The type of value to modify
	 */
	protected static class QonfigModifierHolder<QIS extends QonfigInterpretingSession<?>, T> {
		final QonfigElementOrAddOn element;
		final Class<T> type;
		final QonfigValueModifier<? super QIS, T> modifier;

		QonfigModifierHolder(QonfigElementOrAddOn element, Class<T> type, QonfigValueModifier<? super QIS, T> creator) {
			this.element = element;
			this.type = type;
			this.modifier = creator;
		}

		@Override
		public String toString() {
			return "Modify " + type.getName() + " for " + element;
		}
	}

	private final Class<?> theCallingClass;
	private final Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<QIS, ?>>> theCreators;
	private final Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<QIS, ?>>> theModifiers;
	Throwable loggedThrowable;

	/**
	 * @param callingClass The class building the interpreter
	 * @param creators The set of value creators for interpretation
	 * @param modifiers The set of value modifiers for interpretation
	 */
	protected QonfigInterpreter(Class<?> callingClass,
		Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<QIS, ?>>> creators,
		Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<QIS, ?>>> modifiers) {
		theCallingClass = callingClass;
		theCreators = creators;
		theModifiers = modifiers;
	}

	/** @return The class invoking this interpretation--may be needed to access resources on the classpath */
	public Class<?> getCallingClass() {
		return theCallingClass;
	}

	/**
	 * Commences interpretation of a Qonfig element
	 * 
	 * @param element The root element to interpret
	 * @return The session to do the interpretation
	 * @throws QonfigInterpretationException If an error occurs initializing the interpretation
	 */
	public abstract QIS interpret(QonfigElement element) throws QonfigInterpretationException;

	/**
	 * Creates an interpretation sub-session
	 * 
	 * @param parent The parent session
	 * @param element The element to interpret
	 * @param type The element/add-on type to interpret as (affects what attributes and children are available by name)
	 * @param childIndex The index of the child in its parent, for improved visibility of errors
	 * @return The session to interpret the element
	 * @throws QonfigInterpretationException If an error occurs initializing the interpretation
	 */
	protected abstract QIS interpret(QIS parent, QonfigElement element, QonfigElementOrAddOn type, int childIndex)
		throws QonfigInterpretationException;

	/**
	 * Builds {@link QonfigInterpreter}s
	 * 
	 * @param <QIS> The sub-type of session that interpreters built from this builder will provide
	 * @param <B> The sub-type of this builder
	 */
	public abstract static class Builder<QIS extends QonfigInterpretingSession<?>, B extends Builder<QIS, B>> {
		private final Class<?> theCallingClass;
		private final Set<QonfigToolkit> theToolkits;
		private final QonfigToolkit theToolkit;
		private final StatusReportAccumulator<QonfigElementOrAddOn> theStatus;
		private final Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<QIS, ?>>> theCreators;
		private final Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<QIS, ?>>> theModifiers;

		/**
		 * @param callingClass The class building the interpreter
		 * @param toolkits The toolkits to interpret documents for
		 */
		protected Builder(Class<?> callingClass, QonfigToolkit... toolkits) {
			theCallingClass = callingClass;
			theToolkits = QommonsUtils.unmodifiableDistinctCopy(toolkits);
			if (toolkits == null)
				throw new NullPointerException();
			theToolkit = null;
			theCreators = new HashMap<>();
			theModifiers = new HashMap<>();
			theStatus = new StatusReportAccumulator<>();
		}

		/**
		 * @param callingClass The class building the interpreter
		 * @param toolkits The toolkits to interpret documents for
		 * @param toolkit The toolkit to get elements/add-ons for when only names are specified
		 * @param status The error reporting for the interpretation
		 * @param creators The set of value creators for interpretation so far
		 * @param modifiers The set of value modifiers for interpretation so far
		 */
		protected Builder(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status,
			Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<QIS, ?>>> creators,
			Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<QIS, ?>>> modifiers) {
			theCallingClass = callingClass;
			theToolkits = toolkits;
			theToolkit = toolkit;
			theStatus = status;
			theCreators = creators;
			theModifiers = modifiers;
		}

		/**
		 * @param callingClass The class building the interpreter
		 * @param toolkits The toolkits to interpret documents for
		 * @param toolkit The toolkit to get elements/add-ons for when only names are specified
		 * @param status The error reporting for the interpretation
		 * @param creators The set of value creators for interpretation so far
		 * @param modifiers The set of value modifiers for interpretation so far
		 * @return A new builder with the given data
		 */
		protected abstract B builderFor(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status,
			Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<QIS, ?>>> creators,
			Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<QIS, ?>>> modifiers);

		/** @return A new interpreter with this builder's configuration */
		public abstract QonfigInterpreter<QIS> create();

		/** @return The toolkit that will be used to get elements/add-ons when only names are specified */
		public QonfigToolkit getToolkit() {
			return theToolkit;
		}

		/** @return The class building the interpreter */
		public Class<?> getCallingClass() {
			return theCallingClass;
		}

		/** @return The value creators configured in this builder */
		protected Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<QIS, ?>>> getCreators() {
			return QommonsUtils.unmodifiableCopy(theCreators);
		}

		/** @return The value modifiers configured in this builder */
		protected Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<QIS, ?>>> getModifiers() {
			return QommonsUtils.unmodifiableCopy(theModifiers);
		}

		/**
		 * @param toolkit The toolkit to test
		 * @return Whether this interpreter can interpret elements for types declared by the given toolkit
		 */
		public boolean dependsOn(QonfigToolkit toolkit) {
			boolean found = false;
			for (QonfigToolkit tk : theToolkits) {
				found = tk.dependsOn(toolkit);
				if (found)
					break;
			}
			return found;
		}

		/**
		 * @param toolkit The toolkit
		 * @return A builder whose name-typed methods consider the given toolkit first
		 */
		public B forToolkit(QonfigToolkit toolkit) {
			if (!dependsOn(toolkit))
				throw new IllegalArgumentException("Toolkit " + toolkit.getLocation() + " is not used by toolkits " + theToolkits);
			return builderFor(theCallingClass, theToolkits, toolkit, theStatus, theCreators, theModifiers);
		}

		/**
		 * @param <T> The type to create
		 * @param element The element-def to create values for
		 * @param type The type to create
		 * @param creator The creator to interpret elements of the given type
		 * @return This builder
		 */
		public <T> B createWith(QonfigElementOrAddOn element, Class<T> type, QonfigValueCreator<? super QIS, ? extends T> creator) {
			if (!dependsOn(element.getDeclarer()))
				throw new IllegalArgumentException("Element " + element.getName() + " is from a toolkit not included in " + theToolkits);
			theCreators.compute(element, (el, old) -> {
				if (old == null)
					old = new ClassMap<>();
				// If it already exists, assume this just called twice via dependencies
				old.computeIfAbsent(type, () -> new QonfigCreatorHolder<>(element, type, creator));
				return old;
			});
			return (B) this;
		}

		/**
		 * @param <T> The type to create
		 * @param elementName The name of the element-def to create values for
		 * @param type The type to create
		 * @param creator The creator to interpret elements of the given type
		 * @return This builder
		 */
		public <T> B createWith(String elementName, Class<T> type, QonfigValueCreator<? super QIS, ? extends T> creator) {
			if (theToolkit == null)
				throw new IllegalStateException("Use forToolkit(QonfigToolkit) first to get an interpreter for a toolkit");
			QonfigElementOrAddOn element = theToolkit.getElementOrAddOn(elementName);
			if (element == null)
				throw new IllegalArgumentException("No such element '" + elementName + "' in toolkit " + theToolkit.getLocation());
			return createWith(element, type, creator);
		}

		/**
		 * @param <T> The type to create
		 * @param <S> The type created by the super creator
		 * @param superElement The element interpreted by the super creator
		 * @param targetElement The element to create values for
		 * @param superType The type created by the super creator
		 * @param targetType The type to create
		 * @param extension The creator to interpret elements of the given type, given values interpreted by the super creator
		 * @return This builder
		 */
		public <T, S> B extend(QonfigElementOrAddOn superElement, QonfigElementOrAddOn targetElement, Class<S> superType,
			Class<T> targetType, QonfigValueExtension<? super QIS, S, T> extension) {
			if (!superElement.isAssignableFrom(targetElement))
				throw new IllegalArgumentException(targetElement + " does not extend " + superElement.getName());
			else if (superElement.equals(targetElement))
				throw new IllegalArgumentException("Super element is the same as the target: " + targetElement);
			return createWith(targetElement, targetType, new QonfigExtensionCreator<QIS, S, T>(superElement, superType, extension));
		}

		/**
		 * @param <T> The type to create
		 * @param <S> The type created by the super creator
		 * @param superElementName The name of the element interpreted by the super creator
		 * @param targetElementName The name of the element to create values for
		 * @param superType The type created by the super creator
		 * @param targetType The type to create
		 * @param extension The creator to interpret elements of the given type, given values interpreted by the super creator
		 * @return This builder
		 */
		public <T, S> B extend(String superElementName, String targetElementName, Class<S> superType, Class<T> targetType,
			QonfigValueExtension<? super QIS, S, T> extension) {
			if (theToolkit == null)
				throw new IllegalStateException("Use forToolkit(QonfigToolkit) first to get an interpreter for a toolkit");
			QonfigElementOrAddOn superElement = theToolkit.getElementOrAddOn(superElementName);
			if (superElement == null)
				throw new IllegalArgumentException("No such element '" + superElementName + "' in toolkit " + theToolkit.getLocation());
			QonfigElementOrAddOn targetElement = theToolkit.getElementOrAddOn(targetElementName);
			if (targetElement == null)
				throw new IllegalArgumentException("No such element '" + targetElementName + "' in toolkit " + theToolkit.getLocation());
			return extend(superElement, targetElement, superType, targetType, extension);
		}

		/**
		 * @param <T> The type of value to interpret
		 * @param element The element to interpret
		 * @param typeAttribute The add-on-typed attribute to delegate interpretation to for the element
		 * @param type The type of the value to interpret
		 * @return This builder
		 */
		public <T> B delegateToType(QonfigElementOrAddOn element, QonfigAttributeDef.Declared typeAttribute, Class<T> type) {
			if (!typeAttribute.getOwner().isAssignableFrom(element))
				throw new IllegalArgumentException("Element " + element + " does not declare attribute " + typeAttribute);
			else if (!(typeAttribute.getType() instanceof QonfigAddOn))
				throw new IllegalArgumentException(
					"Type attribute delegation can only be done for add-on typed attributes, not " + typeAttribute);
			else if (typeAttribute.getSpecification() != SpecificationType.Required && typeAttribute.getDefaultValue() == null)
				throw new IllegalArgumentException("Type attribute " + typeAttribute + " is " + typeAttribute.getSpecification()
					+ " and does not specify a default--cannot be delegated to");
			return createWith(element, type, new QonfigCreationDelegator<>(typeAttribute, type));
		}

		/**
		 * @param <T> The type of value to interpret
		 * @param elementName The name of the element to interpret
		 * @param typeAttributeName The name of the add-on-typed attribute to delegate interpretation to for the element
		 * @param type The type of the value to interpret
		 * @return This builder
		 */
		public <T> B delegateToType(String elementName, String typeAttributeName, Class<T> type) {
			if (theToolkit == null)
				throw new IllegalStateException("Use forToolkit(QonfigToolkit) first to get an interpreter for a toolkit");
			QonfigElementOrAddOn element = theToolkit.getElementOrAddOn(elementName);
			if (element == null)
				throw new IllegalArgumentException("No such element '" + elementName + "' in toolkit " + theToolkit.getLocation());
			QonfigAttributeDef attr = element.getDeclaredAttributes().get(typeAttributeName);
			if (attr == null) {
				switch (element.getAttributesByName().get(typeAttributeName).size()) {
				case 0:
					throw new IllegalArgumentException("No such attribute '" + typeAttributeName + "' for element " + elementName);
				case 1:
					attr = element.getAttributesByName().get(typeAttributeName).getFirst();
					break;
				default:
					throw new IllegalArgumentException("Multiple attributes named '" + typeAttributeName + "' for element " + elementName);
				}
			}
			delegateToType(element, attr.getDeclared(), type);
			return (B) this;
		}

		/**
		 * @param <T> The type to modify
		 * @param elementOrAddOn The element or add-on add-on to modify values for
		 * @param type The type to modify
		 * @param modifier The modifier to modify values for the given element or add-on
		 * @return This builder
		 */
		public <T> B modifyWith(QonfigElementOrAddOn elementOrAddOn, Class<T> type, QonfigValueModifier<? super QIS, T> modifier) {
			if (!dependsOn(elementOrAddOn.getDeclarer()))
				throw new IllegalArgumentException(
					"Element " + elementOrAddOn.getName() + " is from a toolkit not included in " + theToolkits);
			if (theModifiers.containsKey(elementOrAddOn))
				return (B) this; // Assume it's the same caller
			theModifiers.computeIfAbsent(elementOrAddOn, __ -> new ClassMap<>()).with(type,
				new QonfigModifierHolder<QIS, T>(elementOrAddOn, type, modifier));
			return (B) this;
		}

		/**
		 * @param <T> The type to modify
		 * @param elementOrAddOnName The name of the element or add-on to modify values for
		 * @param type The type to modify
		 * @param modifier The modifier to modify values for the given element or add-on
		 * @return This builder
		 */
		public <T> B modifyWith(String elementOrAddOnName, Class<T> type, QonfigValueModifier<? super QIS, T> modifier) {
			if (theToolkit == null)
				throw new IllegalStateException("Use forToolkit(QonfigToolkit) first to get an interpreter for a toolkit");
			QonfigElementOrAddOn element = theToolkit.getElementOrAddOn(elementOrAddOnName);
			if (element == null)
				throw new IllegalArgumentException(
					"No such element or add-on '" + elementOrAddOnName + "' in toolkit " + theToolkit.getLocation());
			return modifyWith(element, type, modifier);
		}

		/**
		 * Adds a set of interpretation solutions to this interpreter
		 * 
		 * @param interpretation The interpretations to configure
		 * @return This builder
		 */
		public B configure(QonfigInterpretation<? super QIS>... interpretation) {
			for (QonfigInterpretation<? super QIS> interp : interpretation)
				interp.configureInterpreter((B) this);
			return (B) this;
		}

		/** @return The built interpreter */
		public QonfigInterpreter<QIS> build() {
			Set<QonfigAddOn> usedInh = new HashSet<>();
			for (QonfigToolkit tk : theToolkits) {
				for (QonfigElementDef el : tk.getAllElements().values()) {
					if (el.isAbstract())
						continue;
					ClassMap<QonfigCreatorHolder<QIS, ?>> creators = theCreators.get(el);
					if (creators == null) {
						// theStatus.error(el, "No creator configured for element");
					} else {
						for (BiTuple<Class<?>, QonfigCreatorHolder<QIS, ?>> holder : creators.getAllEntries()) {
							if (holder.getValue2().creator instanceof QonfigExtensionCreator) {
								QonfigExtensionCreator<? super QIS, ?, ?> ext = (QonfigExtensionCreator<? super QIS, ?, ?>) holder
									.getValue2().creator;
								ClassMap<QonfigCreatorHolder<QIS, ?>> superHolders = theCreators.get(ext.theSuperElement);
								QonfigCreatorHolder<? super QIS, ?> superHolder = superHolders == null ? null
									: superHolders.get(holder.getValue1(), ClassMap.TypeMatch.SUB_TYPE);
								if (superHolder == null) {
									// If the super element is not abstract, there will be a separate error for its not having a creator
									// If it is abstract, we need one here
									// if (ext.theSuperElement.isAbstract())
									// theStatus.error(el, "No creator configured for element");
								} else if (!ext.theSuperType.isAssignableFrom(superHolder.type))
									theStatus.warn(el, "Extension of " + ext.theSuperType.getName() + " is parsed as "
										+ superHolder.type.getName() + ", not " + ext.theSuperType.getName());
							}
						}
					}
					for (QonfigAddOn inh : el.getInheritance()) {
						usedInh.add(inh);
					}
				}
				// for (QonfigAddOn el : tk.getAllAddOns().values()) {
				// if (!usedInh.contains(el) && !theModifiers.containsKey(el))
				// theStatus.warn(el, "No modifier configured for otherwise-unused add-on");
				// }
			}
			System.err.println(theStatus.print(Status.Warn, Status.Error, StringBuilder::append, 0, null));
			return create();
		}
	}

	/**
	 * @param callingClass The calling class
	 * @param toolkits The toolkits to interpret
	 * @return A builder to create an interpreter
	 */
	public static Builder<?, ?> build(Class<?> callingClass, QonfigToolkit... toolkits) {
		return new DefaultBuilder<>(callingClass, toolkits);
	}

	static class Default<S extends QonfigInterpretingSession<S>> extends QonfigInterpreter<S> {
		Default(Class<?> callingClass, Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<S, ?>>> creators,
			Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<S, ?>>> modifiers) {
			super(callingClass, creators, modifiers);
		}

		@Override
		public S interpret(QonfigElement element) throws QonfigInterpretationException {
			return (S) new QonfigInterpretingSession<>(this, element);
		}

		@Override
		protected S interpret(S parent, QonfigElement element, QonfigElementOrAddOn type, int childIndex)
			throws QonfigInterpretationException {
			return (S) new QonfigInterpretingSession<>(parent, element, type, childIndex);
		}
	}

	static class DefaultBuilder<S extends QonfigInterpretingSession<S>> extends Builder<S, DefaultBuilder<S>> {
		DefaultBuilder(Class<?> callingClass, QonfigToolkit... toolkits) {
			super(callingClass, toolkits);
		}

		DefaultBuilder(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status, Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<S, ?>>> creators,
			Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<S, ?>>> modifiers) {
			super(callingClass, toolkits, toolkit, status, creators, modifiers);
		}

		@Override
		protected DefaultBuilder<S> builderFor(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status, Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<S, ?>>> creators,
			Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<S, ?>>> modifiers) {
			return new DefaultBuilder<>(callingClass, toolkits, toolkit, status, creators, modifiers);
		}

		@Override
		public QonfigInterpreter<S> create() {
			return new Default<>(getCallingClass(), getCreators(), getModifiers());
		}
	}
}

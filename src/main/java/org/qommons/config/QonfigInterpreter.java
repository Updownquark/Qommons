package org.qommons.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.qommons.BiTuple;
import org.qommons.QommonsUtils;
import org.qommons.StatusReportAccumulator;
import org.qommons.StatusReportAccumulator.Status;
import org.qommons.SubClassMap2;
import org.qommons.collect.BetterList;
import org.qommons.ex.ExFunction;

/** A class for interpreting parsed {@link QonfigDocument}s into useful structures */
public abstract class QonfigInterpreter<QIS extends QonfigInterpreter.QonfigInterpretingSession<QIS>> {
	/** Holds values for communication between parsing components */
	public static class QonfigInterpretingSession<QIS extends QonfigInterpretingSession<QIS>> {
		private final QonfigInterpreter<QIS> theInterpreter;
		private final QIS theParent;
		private final QonfigParseSession theParseSession;
		private final QonfigElement theElement;
		private final QonfigElementOrAddOn theType;
		private final int theChildIndex;
		private final Map<String, Object> theValues;

		protected QonfigInterpretingSession(QonfigInterpreter<QIS> interpreter, QonfigElement root) {
			theInterpreter = interpreter;
			theParent = null;
			theParseSession = QonfigParseSession.forRoot(root.getType().getName(), null);
			theElement = root;
			theType = root.getType();
			theValues = new HashMap<>();
			theChildIndex = 0;
		}

		protected QonfigInterpretingSession(QIS parent, QonfigElement element, QonfigElementOrAddOn type, int childIndex) {
			QonfigInterpretingSession<QIS> parent2 = parent;
			theInterpreter = parent2.theInterpreter;
			theParent = parent;
			theParseSession = parent2.theParseSession.forChild(element.getType().getName(), childIndex);
			theElement = element;
			theType = type;
			theValues = new HashMap<>(parent2.theValues);
			theChildIndex = childIndex;
		}

		public QonfigElement getElement() {
			return theElement;
		}

		public int getChildIndex() {
			return theChildIndex;
		}

		public QonfigInterpreter<QIS> getInterpreter() {
			return theInterpreter;
		}

		public QIS as(QonfigElementOrAddOn type) {
			if (theType == type)
				return (QIS) this;
			else if (theElement.isInstance(type)) {
				return theInterpreter.interpret((QIS) this, theElement, type, theChildIndex);
			} else {
				String msg = "Element " + theElement + " is not an instance of " + type;
				withError(msg);
				throw new IllegalStateException(msg);
			}
		}

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
			return getAttribute(attributeName, type, null);
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
			QonfigAttributeDef attr = theType.getAttribute(attributeName);
			if (attr == null)
				throw new IllegalArgumentException("No such attribute " + theType + "." + attributeName);
			T value = theElement.getAttribute(attr, type);
			if (value == null)
				return defaultValue;
			return value;
		}

		/**
		 * @param attributeName The name of the attribute on the element-def/add-on associated with the current creator/modifier
		 * @return The value of the target attribute
		 * @throws IllegalArgumentException If no such attribute exists on the element/add-on
		 */
		public String getAttributeText(String attributeName) throws IllegalArgumentException {
			QonfigAttributeDef attr = theType.getAttribute(attributeName);
			if (attr == null)
				throw new IllegalArgumentException("No such attribute " + theType + "." + attributeName);
			return theElement.getAttributeText(attr);
		}

		public <T> T getValue(Class<T> type, T defaultValue) {
			Object value = theElement.getValue();
			if (value == null)
				return defaultValue;
			return type.cast(value);
		}

		public String getValueText() {
			return theElement.getValueText();
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
			QonfigCreatorHolder<? super QIS, T> creator = (QonfigCreatorHolder<? super QIS, T>) theInterpreter.theCreators.get(as);
			if (creator == null) {
				String msg = "No creator registered for element " + as.getName();
				withError(msg);
				throw new IllegalStateException(msg);
			} else if (asType != null && !asType.isAssignableFrom(creator.type)) {
				String msg = "Element " + theElement.getType().getName() + " is parsed as " + creator.type.getName() + ", not "
					+ asType.getName();
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
			value = session.modify(creator.type, value);
			try {
				value = creator.creator.postModification(value, session);
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

		public <C, T> T interpretValue(Class<C> sourceType, boolean nullToNull,
			ExFunction<? super C, ? extends T, QonfigInterpretationException> map)
			throws IllegalArgumentException, QonfigInterpretationException {
			Object value = theElement.getValue();
			if (value == null && theType.getValueModifier() == null)
				throw new IllegalArgumentException("No value defined for " + theType);
			else if (nullToNull && value == null)
				return null;
			else if (value != null && !sourceType.isInstance(value))
				throw new IllegalArgumentException("Value " + value + " of element (type " + theElement.getType() + ") is typed "
					+ value.getClass().getName() + ", not " + sourceType.getName());
			return map.apply((C) value);
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

		public BetterList<QIS> forChildren(String childName) throws IllegalArgumentException {
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

		public BetterList<QIS> forChildren() throws IllegalArgumentException {
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
		 * @param asType The type of value to interpret the element as
		 * @return The interpreted values for each child in this element that fulfill the given child role
		 * @throws IllegalArgumentException If no such child exists on the element/add-on
		 * @throws QonfigInterpretationException If the value cannot be interpreted
		 */
		public <T> BetterList<T> interpretChildren(String childName, Class<T> asType)
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
		 * @param sessionKey The key to get data for
		 * @return Data stored for the given key in this session
		 */
		public Object get(String sessionKey) {
			return theValues.get(sessionKey);
		}

		/**
		 * @param sessionKey The key to store data for
		 * @param value The data to store for the given key in this session
		 */
		public QIS put(String sessionKey, Object value) {
			theValues.put(sessionKey, value);
			return (QIS) this;
		}

		/**
		 * @param sessionKey The key to store data for
		 * @param value The data to store for the given key in this session
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

		<T> T modify(Class<T> type, T value) throws QonfigInterpretationException {
			Set<QonfigValueModifier<? super QIS, ?>> modified = new HashSet<>();
			value = modifyWith(type, value, theElement.getType().getSuperElement(), modified);
			Set<QonfigAddOn> inh = new HashSet<>();
			for (QonfigAddOn el : theElement.getType().getFullInheritance().values()) {
				if (inh.add(el))
					value = modifyWith(type, value, el, inh, modified);
			}
			for (QonfigAddOn el : theElement.getInheritance().values()) {
				if (inh.add(el))
					value = modifyWith(type, value, el, inh, modified);
			}
			return value;
		}

		private <T> T modifyWith(Class<T> type, T value, QonfigElementDef superType,
			Set<QonfigValueModifier<? super QIS, ?>> modified) throws QonfigInterpretationException {
			if (superType == null)
				return value;
			value = modifyWith(type, value, superType.getSuperElement(), modified);
			return doModify(type, value, superType, modified);
		}

		private <T> T modifyWith(Class<T> type, T value, QonfigAddOn addOn, Set<QonfigAddOn> inh,
			Set<QonfigValueModifier<? super QIS, ?>> modified) throws QonfigInterpretationException {
			for (QonfigAddOn ext : addOn.getFullInheritance().getExpanded(QonfigAddOn::getInheritance)) {
				if (inh.add(ext))
					value = modifyWith(type, value, ext, inh, modified);
			}
			return doModify(type, value, addOn, modified);
		}

		private <T> T doModify(Class<T> type, T value, QonfigElementOrAddOn modifierType,
			Set<QonfigValueModifier<? super QIS, ?>> modified) throws QonfigInterpretationException {
			if (modifierType == null)
				return value;
			SubClassMap2<Object, QonfigModifierHolder<QIS, ?>> modifiers = theInterpreter.theModifiers.get(modifierType);
			if (modifiers == null)
				return value;
			List<BiTuple<Class<?>, QonfigModifierHolder<QIS, ?>>> typeModifiers = modifiers.getAllEntries(type);
			if (typeModifiers == null)
				return value;
			for (BiTuple<Class<?>, QonfigModifierHolder<QIS, ?>> modifier : typeModifiers) {
				if (modified.add(modifier.getValue2().modifier)) {
					QIS modSession = theInterpreter.interpret((QIS) this, theElement, modifier.getValue2().element, theChildIndex);
					try {
						value = ((QonfigModifierHolder<? super QIS, T>) modifier.getValue2()).modifier.modifyValue(value,
							modSession);
					} catch (QonfigInterpretationException | RuntimeException e) {
						if (theInterpreter.loggedThrowable != e) {
							theInterpreter.loggedThrowable = e;
							modSession.withError("Modifier " + modifier.getValue2().modifier + " for "//
								+ (modifierType instanceof QonfigElementDef ? "super type" : "add-on") + modifierType + " on type "
								+ modifier.getValue1().getName() + " failed to modify value " + value + " for element ", e);
						}
						throw e;
					}
				}
			}
			return value;
		}

		@Override
		public String toString() {
			return "Interpreting " + theElement + " with " + theType;
		}
	}

	/**
	 * Interprets {@link QonfigElement}s, creating values for them
	 * 
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

		default T postModification(T value, QIS session) throws QonfigInterpretationException {
			return value;
		}
	}

	/**
	 * Interprets {@link QonfigElement}s using the value interpreted from a super-element
	 * 
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
	 * @param <T> The type of value to modify
	 */
	public interface QonfigValueModifier<QIS extends QonfigInterpretingSession<?>, T> {
		/**
		 * @param value The value created for the element
		 * @param session The active interpreter session
		 * @return The modified value
		 * @throws QonfigInterpretationException If the value could not be modified
		 */
		T modifyValue(T value, QIS session) throws QonfigInterpretationException;
	}

	static class QonfigExtensionCreator<QIS extends QonfigInterpretingSession<QIS>, S, T> implements QonfigValueCreator<QIS, T> {
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

	static class QonfigCreationDelegator<QIS extends QonfigInterpretingSession<QIS>, T> implements QonfigValueCreator<QIS, T> {
		private final QonfigAttributeDef.Declared theTypeAttribute;

		public QonfigCreationDelegator(QonfigAttributeDef.Declared typeAttribute) {
			theTypeAttribute = typeAttribute;
		}

		@Override
		public T createValue(QIS session) throws QonfigInterpretationException {
			return null;
		}

		@Override
		public T postModification(T value, QIS session) throws QonfigInterpretationException {
			if (value == null) {
				QonfigAddOn type = (QonfigAddOn) session.getElement().getAttributes().get(theTypeAttribute);
				throw new QonfigInterpretationException(
					"Delegated creator " + theTypeAttribute + "(" + type + ") is not registered or did not create a value");
			}
			return value;
		}
	}

	protected static class QonfigCreatorHolder<QIS extends QonfigInterpretingSession<QIS>, T> {
		final QonfigElementOrAddOn element;
		final Class<T> type;
		final QonfigValueCreator<? super QIS, T> creator;

		QonfigCreatorHolder(QonfigElementOrAddOn element, Class<T> type, QonfigValueCreator<? super QIS, T> creator) {
			this.element = element;
			this.type = type;
			this.creator = creator;
		}
	}

	protected static class QonfigModifierHolder<QIS extends QonfigInterpretingSession<QIS>, T> {
		final QonfigElementOrAddOn element;
		final Class<T> type;
		final QonfigValueModifier<? super QIS, T> modifier;

		QonfigModifierHolder(QonfigElementOrAddOn element, Class<T> type, QonfigValueModifier<? super QIS, T> creator) {
			this.element = element;
			this.type = type;
			this.modifier = creator;
		}
	}

	private final Class<?> theCallingClass;
	private final Map<QonfigElementOrAddOn, QonfigCreatorHolder<QIS, ?>> theCreators;
	private final Map<QonfigElementOrAddOn, SubClassMap2<Object, QonfigModifierHolder<QIS, ?>>> theModifiers;
	Throwable loggedThrowable;

	protected QonfigInterpreter(Class<?> callingClass, Map<QonfigElementOrAddOn, QonfigCreatorHolder<QIS, ?>> creators,
		Map<QonfigElementOrAddOn, SubClassMap2<Object, QonfigModifierHolder<QIS, ?>>> modifiers) {
		theCallingClass = callingClass;
		theCreators = creators;
		theModifiers = modifiers;
	}

	/** @return The class invoking this interpretation--may be needed to access resources on the classpath */
	public Class<?> getCallingClass() {
		return theCallingClass;
	}

	public abstract QIS interpret(QonfigElement element);

	protected abstract QIS interpret(QIS parent, QonfigElement element, QonfigElementOrAddOn type, int childIndex);

	/** Builds {@link QonfigInterpreter}s */
	public abstract static class Builder<QIS extends QonfigInterpretingSession<QIS>, B extends Builder<QIS, B>> {
		private final Class<?> theCallingClass;
		private final Set<QonfigToolkit> theToolkits;
		private final QonfigToolkit theToolkit;
		private final StatusReportAccumulator<QonfigElementOrAddOn> theStatus;
		private final Map<QonfigElementOrAddOn, QonfigCreatorHolder<QIS, ?>> theCreators;
		private final Map<QonfigElementOrAddOn, SubClassMap2<Object, QonfigModifierHolder<QIS, ?>>> theModifiers;

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

		protected Builder(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status, Map<QonfigElementOrAddOn, QonfigCreatorHolder<QIS, ?>> creators,
			Map<QonfigElementOrAddOn, SubClassMap2<Object, QonfigModifierHolder<QIS, ?>>> modifiers) {
			theCallingClass = callingClass;
			theToolkits = toolkits;
			theToolkit = toolkit;
			theStatus = status;
			theCreators = creators;
			theModifiers = modifiers;
		}

		protected abstract B builderFor(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status, Map<QonfigElementOrAddOn, QonfigCreatorHolder<QIS, ?>> creators,
			Map<QonfigElementOrAddOn, SubClassMap2<Object, QonfigModifierHolder<QIS, ?>>> modifiers);

		public abstract QonfigInterpreter<QIS> create();

		public QonfigToolkit getToolkit() {
			return theToolkit;
		}

		public Class<?> getCallingClass() {
			return theCallingClass;
		}

		protected Map<QonfigElementOrAddOn, QonfigCreatorHolder<QIS, ?>> getCreators() {
			return QommonsUtils.unmodifiableCopy(theCreators);
		}

		protected Map<QonfigElementOrAddOn, SubClassMap2<Object, QonfigModifierHolder<QIS, ?>>> getModifiers() {
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
		public <T> B createWith(QonfigElementOrAddOn element, Class<T> type, QonfigValueCreator<? super QIS, T> creator) {
			if (!dependsOn(element.getDeclarer()))
				throw new IllegalArgumentException("Element " + element.getName() + " is from a toolkit not included in " + theToolkits);
			if (theCreators.containsKey(element))
				return (B) this; // Assume it's the same caller
			QonfigCreatorHolder<QIS, T> holder = new QonfigCreatorHolder<QIS, T>(element, type, creator);
			theCreators.put(element, holder);
			return (B) this;
		}

		/**
		 * @param <T> The type to create
		 * @param elementName The name of the element-def to create values for
		 * @param type The type to create
		 * @param creator The creator to interpret elements of the given type
		 * @return This builder
		 */
		public <T> B createWith(String elementName, Class<T> type, QonfigValueCreator<? super QIS, T> creator) {
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

		public <T> B delegateToType(QonfigElementOrAddOn element, QonfigAttributeDef.Declared typeAttribute, Class<T> type) {
			if (!typeAttribute.getOwner().isAssignableFrom(element))
				throw new IllegalArgumentException("Element " + element + " does not declare attribute " + typeAttribute);
			else if (!(typeAttribute.getType() instanceof QonfigAddOn))
				throw new IllegalArgumentException(
					"Type attribute delegation can only be done for add-on typed attributes, not " + typeAttribute);
			else if (typeAttribute.getSpecification() != SpecificationType.Required && typeAttribute.getDefaultValue() == null)
				throw new IllegalArgumentException("Type attribute " + typeAttribute + " is " + typeAttribute.getSpecification()
					+ " and does not specify a default--cannot be delegated to");
			return createWith(element, type, new QonfigCreationDelegator<>(typeAttribute));
		}

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
			theModifiers.computeIfAbsent(elementOrAddOn, __ -> new SubClassMap2<>(Object.class)).with(type,
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

		/** @return The built interpreter */
		public QonfigInterpreter<QIS> build() {
			Set<QonfigAddOn> usedInh = new HashSet<>();
			for (QonfigToolkit tk : theToolkits) {
				for (QonfigElementDef el : tk.getAllElements().values()) {
					if (el.isAbstract())
						continue;
					QonfigCreatorHolder<? super QIS, ?> holder = theCreators.get(el);
					if (holder == null) {
						// theStatus.error(el, "No creator configured for element");
					} else if (holder.creator instanceof QonfigExtensionCreator) {
						QonfigExtensionCreator<? super QIS, ?, ?> ext = (QonfigExtensionCreator<? super QIS, ?, ?>) holder.creator;
						QonfigCreatorHolder<? super QIS, ?> superHolder = theCreators.get(ext.theSuperElement);
						if (superHolder == null) {
							// If the super element is not abstract, there will be a separate error for its not having a creator
							// If it is abstract, we need one here
							// if (ext.theSuperElement.isAbstract())
							// theStatus.error(el, "No creator configured for element");
						} else if (!ext.theSuperType.isAssignableFrom(superHolder.type))
							theStatus.warn(el, "Extension of " + ext.theSuperType.getName() + " is parsed as " + superHolder.type.getName()
								+ ", not " + ext.theSuperType.getName());
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
}

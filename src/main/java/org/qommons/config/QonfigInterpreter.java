package org.qommons.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
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
import org.qommons.ex.ExRunnable;

/** A class for interpreting parsed {@link QonfigDocument}s into useful structures */
public class QonfigInterpreter {
	public static class QonfigInterpretationException extends Exception {
		public QonfigInterpretationException(String message, Throwable cause) {
			super(message, cause);
		}

		public QonfigInterpretationException(String message) {
			super(message);
		}

		public QonfigInterpretationException(Throwable cause) {
			super(cause);
		}
	}

	/** Holds values for communication between parsing components */
	public static class QonfigInterpretingSession {
		private class StackElement {
			final QonfigElement element;
			final QonfigParseSession session;
			final QonfigElementOrAddOn type;
			private final int childIndex;
			private Map<String, Object> thePreviousValues;

			StackElement(QonfigElement element, QonfigParseSession sesson, QonfigElementOrAddOn type) {
				this.element = element;
				this.session = sesson;
				this.type = type;
				this.childIndex = theNextChildIndex;
			}

			void putPreValue(String name, Object oldValue) {
				if (thePreviousValues == null)
					thePreviousValues = new HashMap<>();
				thePreviousValues.putIfAbsent(name, oldValue);
			}

			void rollBack() {
				if (thePreviousValues != null) {
					for (Map.Entry<String, Object> pv : thePreviousValues.entrySet())
						theValues.put(pv.getKey(), pv.getValue());
				}
				if (type instanceof QonfigElementDef)
					theNextChildIndex = childIndex + 1;
				theStack.removeLast();
			}

			@Override
			public String toString() {
				return element + " with " + type;
			}
		}
		private final QonfigInterpreter theInterpreter;
		private final Map<String, Object> theValues;
		private final LinkedList<StackElement> theStack;
		private int theNextChildIndex;
		Throwable loggedThrowable;

		QonfigInterpretingSession(QonfigInterpreter interpreter, QonfigElement root, QonfigElementOrAddOn type) {
			theInterpreter = interpreter;
			theValues = new HashMap<>();
			theStack = new LinkedList<>();
			theStack.add(new StackElement(root, QonfigParseSession.forRoot(root.getType().getName(), null), type));
		}

		ExRunnable<QonfigInterpretationException> mark(QonfigElement element, QonfigElementOrAddOn type) {
			if (theStack.getLast().element == element && theStack.getLast().type == type)
				return ExRunnable.none();
			QonfigParseSession session = theStack.getLast().session.forChild(element.getType().getName(), theNextChildIndex);
			StackElement stackEl = new StackElement(element, session, type);
			theStack.add(stackEl);
			theNextChildIndex = 0;
			return stackEl::rollBack;
		}

		static Runnable NO_RUN = () -> {
		};

		Runnable markModifier(QonfigElement element, QonfigElementOrAddOn type) {
			if (theStack.getLast().element == element && theStack.getLast().type == type)
				return NO_RUN;
			QonfigParseSession session = theStack.getLast().session.forChild(element.getType().getName(), theNextChildIndex);
			StackElement stackEl = new StackElement(element, session, type);
			theStack.add(stackEl);
			return stackEl::rollBack;
		}

		void finish() throws QonfigInterpretationException {
			theStack.getFirst().session.printWarnings(System.err, "");
			try {
				theStack.getFirst().session.throwErrors("Errors interpreting document");
			} catch (QonfigParseException e) {
				if (e.getCause() != null && e.getCause() != e)
					throw new QonfigInterpretationException(e.getMessage(), e.getCause());
				else
					throw new QonfigInterpretationException(e.getMessage());
			}
		}

		/** @return The active interpreter */
		public QonfigInterpreter getInterpreter() {
			return theInterpreter;
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
			StackElement el = theStack.getLast();
			QonfigAttributeDef attr = el.type.getAttribute(attributeName);
			if (attr == null)
				throw new IllegalArgumentException("No such attribute " + el.type + "." + attributeName);
			T value = el.element.getAttribute(attr, type);
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
			StackElement el = theStack.getLast();
			QonfigAttributeDef attr = el.type.getAttribute(attributeName);
			if (attr == null)
				throw new IllegalArgumentException("No such attribute " + el.type + "." + attributeName);
			return el.element.getAttributeText(attr);
		}

		/**
		 * @param <S> The type of the attribute value to get
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
		public <S, T> T interpretAttribute(String attributeName, Class<S> sourceType, boolean nullToNull,
			ExFunction<? super S, ? extends T, QonfigInterpretationException> map)
			throws IllegalArgumentException, QonfigInterpretationException {
			StackElement el = theStack.getLast();
			QonfigAttributeDef attr = el.type.getAttribute(attributeName);
			if (attr == null)
				throw new IllegalArgumentException("No such attribute " + el.type + "." + attributeName);
			S attrValue = el.element.getAttribute(attr, sourceType);
			if (nullToNull && attrValue == null)
				return null;
			return map.apply(attrValue);
		}

		public <S, T> T interpretValue(Class<S> sourceType, boolean nullToNull,
			ExFunction<? super S, ? extends T, QonfigInterpretationException> map)
			throws IllegalArgumentException, QonfigInterpretationException {
			StackElement el = theStack.getLast();
			Object value = el.element.getValue();
			if (value == null && el.type.getValueModifier() == null)
				throw new IllegalArgumentException("No value defined for " + el.type);
			else if (nullToNull && value == null)
				return null;
			else if (value != null && !sourceType.isInstance(value))
				throw new IllegalArgumentException("Value " + value + " of element (type " + el.element.getType() + ") is typed "
					+ value.getClass().getName() + ", not " + sourceType.getName());
			return map.apply((S) value);
		}

		/**
		 * @param childName The name of the child on the element-def/add-on associated with the current creator/modifier
		 * @return All children in this element that fulfill the given child role
		 * @throws IllegalArgumentException If no such child exists on the element/add-on
		 */
		public BetterList<QonfigElement> getChildren(String childName) throws IllegalArgumentException {
			StackElement el = theStack.getLast();
			QonfigChildDef child = el.type.getChild(childName);
			if (child == null)
				throw new IllegalArgumentException("No such child " + el.type + "." + childName);
			return (BetterList<QonfigElement>) el.element.getChildrenByRole().get(child.getDeclared());
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
			for (QonfigElement child : children)
				interpreted[i++] = getInterpreter().interpret(child, asType);
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
		public void put(String sessionKey, Object value) {
			Object old = theValues.put(sessionKey, value);
			theStack.getLast().putPreValue(sessionKey, old);
		}

		/**
		 * @param sessionKey The key to store data for
		 * @param value The data to store for the given key in this session
		 */
		public void putGlobal(String sessionKey, Object value) {
			theValues.put(sessionKey, value);
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
		public QonfigInterpretingSession withWarning(String message) {
			return withWarning(message, null);
		}

		/**
		 * @param message The warning message to log
		 * @param cause The cause of the warning, if any
		 * @return This session
		 */
		public QonfigInterpretingSession withWarning(String message, Throwable cause) {
			theStack.getLast().session.withWarning(message, cause);
			return this;
		}

		/**
		 * @param message The error message to log
		 * @return This session
		 */
		public QonfigInterpretingSession withError(String message) {
			return withError(message, null);
		}

		/**
		 * @param message The error message to log
		 * @param cause The cause of the error, if any
		 * @return This session
		 */
		public QonfigInterpretingSession withError(String message, Throwable cause) {
			theStack.getLast().session.withError(message, cause);
			return this;
		}

		@Override
		public String toString() {
			return "Interpreting " + theStack.getLast();
		}
	}

	/**
	 * Interprets {@link QonfigElement}s, creating values for them
	 * 
	 * @param <T> The type of value created
	 */
	public interface QonfigValueCreator<T> {
		/**
		 * @param element The element to create the value for
		 * @param session The active interpreter session
		 * @return The created value
		 * @throws QonfigInterpretationException If the value could not be created
		 */
		T createValue(QonfigElement element, QonfigInterpretingSession session) throws QonfigInterpretationException;

		default T postModification(T value, QonfigElement element, QonfigInterpretingSession session) throws QonfigInterpretationException {
			return value;
		}
	}

	/**
	 * Interprets {@link QonfigElement}s using the value interpreted from a super-element
	 * 
	 * @param <S> The type of value interpreted by the super-creator
	 * @param <T> The type of value created by this extension
	 */
	public interface QonfigValueExtension<S, T> {
		/**
		 * @param superValue The value created by the super-creator
		 * @param element The element to interpret the value for
		 * @param session The active interpreter session
		 * @return The value for the element
		 * @throws QonfigInterpretationException If the value could not be created or modified
		 */
		T createValue(S superValue, QonfigElement element, QonfigInterpretingSession session) throws QonfigInterpretationException;
	}

	/**
	 * Modifies an interpreted value for an add-on or abstract element-def
	 * 
	 * @param <T> The type of value to modify
	 */
	public interface QonfigValueModifier<T> {
		/**
		 * @param value The value created for the element
		 * @param element The element to modify the value for
		 * @param session The active interpreter session
		 * @return The modified value
		 * @throws QonfigInterpretationException If the value could not be modified
		 */
		T modifyValue(T value, QonfigElement element, QonfigInterpretingSession session) throws QonfigInterpretationException;
	}

	static class QonfigExtensionCreator<S, T> implements QonfigValueCreator<T> {
		final QonfigElementOrAddOn theSuperElement;
		final Class<S> theSuperType;
		final QonfigValueExtension<S, T> theExtension;

		QonfigExtensionCreator(QonfigElementOrAddOn superElement, Class<S> superType, QonfigValueExtension<S, T> extension) {
			theSuperElement = superElement;
			theSuperType = superType;
			theExtension = extension;
		}

		@Override
		public T createValue(QonfigElement element, QonfigInterpretingSession session) throws QonfigInterpretationException {
			S superValue = session.getInterpreter().parseAs(element, theSuperElement, theSuperType);
			return theExtension.createValue(superValue, element, session);
		}
	}

	static class QonfigCreationDelegator<T> implements QonfigValueCreator<T> {
		private final QonfigAttributeDef.Declared theTypeAttribute;

		public QonfigCreationDelegator(QonfigAttributeDef.Declared typeAttribute) {
			theTypeAttribute = typeAttribute;
		}

		@Override
		public T createValue(QonfigElement element, QonfigInterpretingSession session) throws QonfigInterpretationException {
			return null;
		}

		@Override
		public T postModification(T value, QonfigElement element, QonfigInterpretingSession session) throws QonfigInterpretationException {
			if (value == null) {
				QonfigAddOn type = (QonfigAddOn) element.getAttributes().get(theTypeAttribute);
				throw new QonfigInterpretationException(
					"Delegated creator " + theTypeAttribute + "(" + type + ") is not registered or did not create a value");
			}
			return value;
		}
	}

	static class QonfigCreatorHolder<T> {
		final Class<T> type;
		final QonfigValueCreator<T> creator;

		QonfigCreatorHolder(Class<T> type, QonfigValueCreator<T> creator) {
			this.type = type;
			this.creator = creator;
		}
	}

	static class QonfigModifierHolder<T> {
		final Class<T> type;
		final QonfigValueModifier<T> modifier;

		QonfigModifierHolder(Class<T> type, QonfigValueModifier<T> creator) {
			this.type = type;
			this.modifier = creator;
		}
	}

	private final Class<?> theCallingClass;
	private final Map<QonfigElementOrAddOn, QonfigCreatorHolder<?>> theCreators;
	private final Map<QonfigElementOrAddOn, SubClassMap2<Object, QonfigModifierHolder<?>>> theModifiers;
	private final ThreadLocal<QonfigInterpretingSession> theSessions;

	QonfigInterpreter(Class<?> callingClass, Map<QonfigElementOrAddOn, QonfigCreatorHolder<?>> creators,
		Map<QonfigElementOrAddOn, SubClassMap2<Object, QonfigModifierHolder<?>>> modifiers) {
		theCallingClass = callingClass;
		theCreators = creators;
		theModifiers = modifiers;
		theSessions = new ThreadLocal<>();
	}

	/** @return The class invoking this interpretation--may be needed to access resources on the classpath */
	public Class<?> getCallingClass() {
		return theCallingClass;
	}

	/**
	 * @param <T> The type of value to interpret
	 * @param element The element to interpret
	 * @param asType The type of value to interpret the element as
	 * @return The interpreted value
	 * @throws QonfigInterpretationException If the value cannot be interpreted
	 */
	public <T> T interpret(QonfigElement element, Class<T> asType) throws QonfigInterpretationException {
		return parseAs(element, element.getType(), asType);
	}

	/**
	 * @param <T> The type of value to interpret
	 * @param element The element to interpret
	 * @param as The element type to interpret the element as
	 * @param asType The type of value to interpret the element as
	 * @return The interpreted value
	 * @throws QonfigInterpretationException If the value cannot be interpreted
	 */
	public <T> T parseAs(QonfigElement element, QonfigElementOrAddOn as, Class<T> asType) throws QonfigInterpretationException {
		QonfigCreatorHolder<T> creator = (QonfigCreatorHolder<T>) theCreators.get(as);
		QonfigInterpretingSession session = theSessions.get();
		ExRunnable<QonfigInterpretationException> sessionClose;
		if (session == null) {
			theSessions.set(session = new QonfigInterpretingSession(this, element, as));
			QonfigInterpretingSession fSession = session;
			sessionClose = () -> {
				fSession.finish();
				theSessions.set(null);
			};
		} else
			sessionClose = session.mark(element, as);
		if (creator == null) {
			String msg = "No creator registered for element " + as.getName();
			session.withError(msg);
			sessionClose.run();
			throw new IllegalStateException(msg);
		} else if (asType != null && !asType.isAssignableFrom(creator.type)) {
			String msg = "Element " + element.getType().getName() + " is parsed as " + creator.type.getName() + ", not " + asType.getName();
			session.withError(msg);
			sessionClose.run();
			throw new IllegalStateException(msg);
		}
		T value;
		try {
			value = creator.creator.createValue(element, session);
		} catch (QonfigInterpretationException | RuntimeException e) {
			if (session.loggedThrowable != e) {
				session.loggedThrowable = e;
				session.withError("Creator " + creator.creator + " for element " + as + " failed to create value for element " + element,
					e);
			}
			sessionClose.run();
			throw e;
		}
		try {
			value = modify(creator.type, value, element, session);
		} catch (QonfigInterpretationException | RuntimeException e) {
			sessionClose.run();
			throw e;
		}
		try {
			value = creator.creator.postModification(value, element, session);
		} catch (QonfigInterpretationException | RuntimeException e) {
			if (session.loggedThrowable != e) {
				session.loggedThrowable = e;
				session.withError("Creator " + creator.creator + " for element " + as + " post-modification failed for value " + value
					+ " for element " + element, e);
			}
			throw e;
		} finally {
			sessionClose.run();
		}
		return value;
	}

	<T> T modify(Class<T> type, T value, QonfigElement element, QonfigInterpretingSession session) throws QonfigInterpretationException {
		Set<QonfigValueModifier<?>> modified = new HashSet<>();
		value = modifyWith(type, value, element, element.getType().getSuperElement(), modified, session);
		Set<QonfigAddOn> inh = new HashSet<>();
		for (QonfigAddOn el : element.getType().getFullInheritance().values()) {
			if (inh.add(el))
				value = modifyWith(type, value, element, el, inh, modified, session);
		}
		for (QonfigAddOn el : element.getInheritance().values()) {
			if (inh.add(el))
				value = modifyWith(type, value, element, el, inh, modified, session);
		}
		return value;
	}

	private <T> T modifyWith(Class<T> type, T value, QonfigElement element, QonfigElementDef superType,
		Set<QonfigValueModifier<?>> modified, QonfigInterpretingSession session) throws QonfigInterpretationException {
		if (superType == null)
			return value;
		value = modifyWith(type, value, element, superType.getSuperElement(), modified, session);
		return doModify(type, value, element, superType, modified, session);
	}

	private <T> T modifyWith(Class<T> type, T value, QonfigElement element, QonfigAddOn addOn, Set<QonfigAddOn> inh,
		Set<QonfigValueModifier<?>> modified, QonfigInterpretingSession session) throws QonfigInterpretationException {
		for (QonfigAddOn ext : addOn.getFullInheritance().getExpanded(QonfigAddOn::getInheritance)) {
			if (inh.add(ext))
				value = modifyWith(type, value, element, ext, inh, modified, session);
		}
		return doModify(type, value, element, addOn, modified, session);
	}

	private <T> T doModify(Class<T> type, T value, QonfigElement element, QonfigElementOrAddOn modifierType,
		Set<QonfigValueModifier<?>> modified, QonfigInterpretingSession session) throws QonfigInterpretationException {
		if (modifierType == null)
			return value;
		SubClassMap2<Object, QonfigModifierHolder<?>> modifiers = theModifiers.get(modifierType);
		if (modifiers == null)
			return value;
		List<BiTuple<Class<?>, QonfigModifierHolder<?>>> typeModifiers = modifiers.getAllEntries(type);
		if (typeModifiers == null)
			return value;
		for (BiTuple<Class<?>, QonfigModifierHolder<?>> modifier : typeModifiers) {
			if (modified.add(modifier.getValue2().modifier)) {
				Runnable sessionClose = session.markModifier(element, modifierType);
				try {
					value = ((QonfigModifierHolder<T>) modifier.getValue2()).modifier.modifyValue(value, element, session);
				} catch (QonfigInterpretationException | RuntimeException e) {
					if (session.loggedThrowable != e) {
						session.loggedThrowable = e;
						session.withError("Modifier " + modifier.getValue2().modifier + " for "//
							+ (modifierType instanceof QonfigElementDef ? "super type" : "add-on") + modifierType + " on type "
							+ modifier.getValue1().getName() + " failed to modify value " + value + " for element " + element, e);
					}
					throw e;
				} finally {
					sessionClose.run();
				}
			}
		}
		return value;
	}

	/**
	 * Builds an interpreter
	 * 
	 * @param callingClass The class invoking this interpretation--may be needed to access resources on the classpath
	 * @param toolkits The toolkits that the interpreter will be able to interpret documents of
	 * @return A builder
	 */
	public static Builder build(Class<?> callingClass, QonfigToolkit... toolkits) {
		return new Builder(callingClass, toolkits);
	}

	/** Builds {@link QonfigInterpreter}s */
	public static class Builder {
		private final Class<?> theCallingClass;
		private final Set<QonfigToolkit> theToolkits;
		private final QonfigToolkit theToolkit;
		private final StatusReportAccumulator<QonfigElementOrAddOn> theStatus;
		private final Map<QonfigElementOrAddOn, QonfigCreatorHolder<?>> theCreators;
		private final Map<QonfigElementOrAddOn, SubClassMap2<Object, QonfigModifierHolder<?>>> theModifiers;

		Builder(Class<?> callingClass, QonfigToolkit... toolkits) {
			theCallingClass = callingClass;
			theToolkits = QommonsUtils.unmodifiableDistinctCopy(toolkits);
			if (toolkits == null)
				throw new NullPointerException();
			theToolkit = null;
			theCreators = new HashMap<>();
			theModifiers = new HashMap<>();
			theStatus = new StatusReportAccumulator<>();
		}

		Builder(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status, Map<QonfigElementOrAddOn, QonfigCreatorHolder<?>> creators,
			Map<QonfigElementOrAddOn, SubClassMap2<Object, QonfigModifierHolder<?>>> modifiers) {
			theCallingClass = callingClass;
			theToolkits = toolkits;
			theToolkit = toolkit;
			theStatus = status;
			theCreators = creators;
			theModifiers = modifiers;
		}

		public QonfigToolkit getToolkit() {
			return theToolkit;
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
		public Builder forToolkit(QonfigToolkit toolkit) {
			if (!dependsOn(toolkit))
				throw new IllegalArgumentException("Toolkit " + toolkit.getLocation() + " is not used by toolkits " + theToolkits);
			return new Builder(theCallingClass, theToolkits, toolkit, theStatus, theCreators, theModifiers);
		}

		/**
		 * @param <T> The type to create
		 * @param element The element-def to create values for
		 * @param type The type to create
		 * @param creator The creator to interpret elements of the given type
		 * @return This builder
		 */
		public <T> Builder createWith(QonfigElementOrAddOn element, Class<T> type, QonfigValueCreator<T> creator) {
			if (!dependsOn(element.getDeclarer()))
				throw new IllegalArgumentException("Element " + element.getName() + " is from a toolkit not included in " + theToolkits);
			if (theCreators.containsKey(element))
				theStatus.warn(element, "Replacing creator");
			theCreators.put(element, new QonfigCreatorHolder<>(type, creator));
			return this;
		}

		/**
		 * @param <T> The type to create
		 * @param elementName The name of the element-def to create values for
		 * @param type The type to create
		 * @param creator The creator to interpret elements of the given type
		 * @return This builder
		 */
		public <T> Builder createWith(String elementName, Class<T> type, QonfigValueCreator<T> creator) {
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
		public <T, S> Builder extend(QonfigElementOrAddOn superElement, QonfigElementOrAddOn targetElement, Class<S> superType,
			Class<T> targetType, QonfigValueExtension<S, T> extension) {
			if (!superElement.isAssignableFrom(targetElement))
				throw new IllegalArgumentException(targetElement + " does not extend " + superElement.getName());
			else if (superElement.equals(targetElement))
				throw new IllegalArgumentException("Super element is the same as the target: " + targetElement);
			return createWith(targetElement, targetType, new QonfigExtensionCreator<>(superElement, superType, extension));
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
		public <T, S> Builder extend(String superElementName, String targetElementName, Class<S> superType, Class<T> targetType,
			QonfigValueExtension<S, T> extension) {
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

		public <T> Builder delegateToType(QonfigElementOrAddOn element, QonfigAttributeDef.Declared typeAttribute, Class<T> type) {
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

		public <T> Builder delegateToType(String elementName, String typeAttributeName, Class<T> type) {
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
			return this;
		}

		/**
		 * @param <T> The type to modify
		 * @param elementOrAddOn The element or add-on add-on to modify values for
		 * @param type The type to modify
		 * @param modifier The modifier to modify values for the given element or add-on
		 * @return This builder
		 */
		public <T> Builder modifyWith(QonfigElementOrAddOn elementOrAddOn, Class<T> type, QonfigValueModifier<T> modifier) {
			if (!dependsOn(elementOrAddOn.getDeclarer()))
				throw new IllegalArgumentException(
					"Element " + elementOrAddOn.getName() + " is from a toolkit not included in " + theToolkits);
			if (theModifiers.containsKey(elementOrAddOn))
				theStatus.warn(elementOrAddOn, "Replacing modifier");
			theModifiers.computeIfAbsent(elementOrAddOn, __ -> new SubClassMap2<>(Object.class)).with(type,
				new QonfigModifierHolder<>(type, modifier));
			return this;
		}

		/**
		 * @param <T> The type to modify
		 * @param elementOrAddOnName The name of the element or add-on to modify values for
		 * @param type The type to modify
		 * @param modifier The modifier to modify values for the given element or add-on
		 * @return This builder
		 */
		public <T> Builder modifyWith(String elementOrAddOnName, Class<T> type, QonfigValueModifier<T> modifier) {
			if (theToolkit == null)
				throw new IllegalStateException("Use forToolkit(QonfigToolkit) first to get an interpreter for a toolkit");
			QonfigElementOrAddOn element = theToolkit.getElementOrAddOn(elementOrAddOnName);
			if (element == null)
				throw new IllegalArgumentException(
					"No such element or add-on '" + elementOrAddOnName + "' in toolkit " + theToolkit.getLocation());
			return modifyWith(element, type, modifier);
		}

		/** @return The built interpreter */
		public QonfigInterpreter build() {
			Set<QonfigAddOn> usedInh = new HashSet<>();
			for (QonfigToolkit tk : theToolkits) {
				for (QonfigElementDef el : tk.getAllElements().values()) {
					if (el.isAbstract())
						continue;
					QonfigCreatorHolder<?> holder = theCreators.get(el);
					if (holder == null)
						theStatus.error(el, "No creator configured for element");
					else if (holder.creator instanceof QonfigExtensionCreator) {
						QonfigExtensionCreator<?, ?> ext = (QonfigExtensionCreator<?, ?>) holder.creator;
						QonfigCreatorHolder<?> superHolder = theCreators.get(ext.theSuperElement);
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
			return new QonfigInterpreter(theCallingClass, QommonsUtils.unmodifiableCopy(theCreators),
				QommonsUtils.unmodifiableCopy(theModifiers));
		}
	}
}

package org.qommons.config;

import java.text.ParseException;
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
import org.qommons.Transaction;

/** A class for interpreting parsed {@link QonfigDocument}s into useful structures */
public class QonfigInterpreter {
	/** Holds values for communication between parsing components */
	public static class QonfigInterpretingSession {
		private final QonfigInterpreter theInterpreter;
		private final Map<String, Object> theValues;
		private final LinkedList<BiTuple<String, Object>> theChanges;

		QonfigInterpretingSession(QonfigInterpreter interpreter) {
			theInterpreter = interpreter;
			theValues = new HashMap<>();
			theChanges = new LinkedList<>();
		}

		Transaction mark() {
			int changesSize = theChanges.size();
			return () -> {
				while (theChanges.size() > changesSize) {
					BiTuple<String, Object> change = theChanges.removeLast();
					if (change.getValue2() == null)
						theValues.remove(change.getValue1());
					else
						theValues.put(change.getValue1(), change.getValue2());
				}
			};
		}

		/** @return The active interpreter */
		public QonfigInterpreter getInterpreter() {
			return theInterpreter;
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
			theChanges.add(new BiTuple<>(sessionKey, old));
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
		 * @throws ParseException If the value could not be created
		 */
		T createValue(QonfigElement element, QonfigInterpretingSession session) throws ParseException;
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
		 * @throws ParseException If the value could not be created or modified
		 */
		T createValue(S superValue, QonfigElement element, QonfigInterpretingSession session) throws ParseException;
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
		 * @throws ParseException IF the value could not be modified
		 */
		T modifyValue(T value, QonfigElement element, QonfigInterpretingSession session) throws ParseException;
	}

	static class QonfigExtensionCreator<S, T> implements QonfigValueCreator<T> {
		final QonfigElementDef theSuperElement;
		final Class<S> theSuperType;
		final QonfigValueExtension<S, T> theExtension;

		QonfigExtensionCreator(QonfigElementDef superElement, Class<S> superType, QonfigValueExtension<S, T> extension) {
			theSuperElement = superElement;
			theSuperType = superType;
			theExtension = extension;
		}

		@Override
		public T createValue(QonfigElement element, QonfigInterpretingSession session) throws ParseException {
			S superValue = session.getInterpreter().parseAs(element, theSuperElement, theSuperType);
			return theExtension.createValue(superValue, element, session);
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

	private final Map<QonfigElementDef, QonfigCreatorHolder<?>> theCreators;
	private final Map<QonfigAddOn, SubClassMap2<Object, QonfigModifierHolder<?>>> theModifiers;
	private final ThreadLocal<QonfigInterpretingSession> theSessions;

	QonfigInterpreter(Map<QonfigElementDef, QonfigCreatorHolder<?>> creators,
		Map<QonfigAddOn, SubClassMap2<Object, QonfigModifierHolder<?>>> modifiers) {
		theCreators = creators;
		theModifiers = modifiers;
		theSessions = new ThreadLocal<>();
	}

	/**
	 * @param <T> The type of value to interpret
	 * @param element The element to interpret
	 * @param asType The type of value to interpret the element as
	 * @return The interpreted value
	 * @throws ParseException If the value cannot be interpreted
	 */
	public <T> T interpret(QonfigElement element, Class<T> asType) throws ParseException {
		return parseAs(element, element.getType(), asType);
	}

	<T> T parseAs(QonfigElement element, QonfigElementDef as, Class<T> asType) throws ParseException {
		QonfigCreatorHolder<T> creator = (QonfigCreatorHolder<T>) theCreators.get(as);
		if (creator == null)
			throw new IllegalStateException("No creator registered for element " + as.getName());
		else if (!asType.isAssignableFrom(creator.type))
			throw new IllegalStateException(
				"Element " + element.getType().getName() + " is parsed as " + creator.type.getName() + ", not " + asType.getName());
		QonfigInterpretingSession session = theSessions.get();
		Transaction sessionClose;
		if (session == null) {
			theSessions.set(session = new QonfigInterpretingSession(this));
			sessionClose = () -> theSessions.set(null);
		} else
			sessionClose = session.mark();
		T value = creator.creator.createValue(element, session);
		if (value != null)
			value = modify(value, element, session);
		sessionClose.close();
		return value;
	}

	<T> T modify(T value, QonfigElement element, QonfigInterpretingSession session) throws ParseException {
		Set<QonfigAddOn> inh = new HashSet<>();
		Set<QonfigValueModifier<?>> modified = new HashSet<>();
		for (QonfigAddOn el : element.getType().getFullInheritance().values()) {
			if (inh.add(el))
				value = modifyWith(value, element, el, inh, modified, session);
		}
		for (QonfigAddOn el : element.getInheritance().values()) {
			if (inh.add(el))
				value = modifyWith(value, element, el, inh, modified, session);
		}
		return value;
	}

	private <T> T modifyWith(T value, QonfigElement element, QonfigAddOn el, Set<QonfigAddOn> inh, Set<QonfigValueModifier<?>> modified,
		QonfigInterpretingSession session) throws ParseException {
		for (QonfigAddOn ext : el.getFullInheritance().getExpanded(QonfigAddOn::getInheritance)) {
			if (inh.add(ext))
				value = modifyWith(value, element, ext, inh, modified, session);
		}
		SubClassMap2<Object, QonfigModifierHolder<?>> modifiers = theModifiers.get(el);
		if (modifiers == null)
			return value;
		List<QonfigModifierHolder<?>> typeModifiers = modifiers.getAll(value.getClass());
		if (typeModifiers == null)
			return value;
		for (QonfigModifierHolder<?> modifier : typeModifiers) {
			if (modified.add(modifier.modifier))
				value = ((QonfigModifierHolder<T>) modifier).modifier.modifyValue(value, element, session);
		}
		return value;
	}

	/**
	 * Builds an interpreter
	 * 
	 * @param toolkits The toolkits that the interpreter will be able to interpret documents of
	 * @return A builder
	 */
	public static Builder build(QonfigToolkit... toolkits) {
		return new Builder(toolkits);
	}

	/** Builds {@link QonfigInterpreter}s */
	public static class Builder {
		private final Set<QonfigToolkit> theToolkits;
		private final QonfigToolkit theToolkit;
		private final StatusReportAccumulator<QonfigElementOrAddOn> theStatus;
		private final Map<QonfigElementDef, QonfigCreatorHolder<?>> theCreators;
		private final Map<QonfigAddOn, SubClassMap2<Object, QonfigModifierHolder<?>>> theModifiers;

		Builder(QonfigToolkit... toolkits) {
			theToolkits = QommonsUtils.unmodifiableDistinctCopy(toolkits);
			if (toolkits == null)
				throw new NullPointerException();
			theToolkit = null;
			theCreators = new HashMap<>();
			theModifiers = new HashMap<>();
			theStatus = new StatusReportAccumulator<>();
		}

		Builder(Set<QonfigToolkit> toolkits, QonfigToolkit toolkit, StatusReportAccumulator<QonfigElementOrAddOn> status,
			Map<QonfigElementDef, QonfigCreatorHolder<?>> creators,
			Map<QonfigAddOn, SubClassMap2<Object, QonfigModifierHolder<?>>> modifiers) {
			theToolkits = toolkits;
			theToolkit = toolkit;
			theStatus = status;
			theCreators = creators;
			theModifiers = modifiers;
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
			return new Builder(theToolkits, toolkit, theStatus, theCreators, theModifiers);
		}

		/**
		 * @param <T> The type to create
		 * @param element The element-def to create values for
		 * @param type The type to create
		 * @param creator The creator to interpret elements of the given type
		 * @return This builder
		 */
		public <T> Builder createWith(QonfigElementDef element, Class<T> type, QonfigValueCreator<T> creator) {
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
			QonfigElementDef element = theToolkit.getElement(elementName);
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
		public <T, S> Builder extend(QonfigElementDef superElement, QonfigElementDef targetElement, Class<S> superType, Class<T> targetType,
			QonfigValueExtension<S, T> extension) {
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
			QonfigElementDef superElement = theToolkit.getElement(superElementName);
			if (superElement == null)
				throw new IllegalArgumentException("No such element '" + superElementName + "' in toolkit " + theToolkit.getLocation());
			QonfigElementDef targetElement = theToolkit.getElement(targetElementName);
			if (targetElement == null)
				throw new IllegalArgumentException("No such element '" + targetElementName + "' in toolkit " + theToolkit.getLocation());
			return extend(superElement, targetElement, superType, targetType, extension);
		}

		/**
		 * @param <T> The type to modify
		 * @param addOn The add-on to modify values for
		 * @param type The type to modify
		 * @param modifier The modifier to modify values for the given add-on
		 * @return This builder
		 */
		public <T> Builder modifyWith(QonfigAddOn addOn, Class<T> type, QonfigValueModifier<T> modifier) {
			if (!dependsOn(addOn.getDeclarer()))
				throw new IllegalArgumentException("Element " + addOn.getName() + " is from a toolkit not included in " + theToolkits);
			if (theModifiers.containsKey(addOn))
				theStatus.warn(addOn, "Replacing modifier");
			theModifiers.computeIfAbsent(addOn, __ -> new SubClassMap2<>(Object.class)).with(type,
				new QonfigModifierHolder<>(type, modifier));
			return this;
		}

		/**
		 * @param <T> The type to modify
		 * @param addOnName The name of the add-on to modify values for
		 * @param type The type to modify
		 * @param modifier The modifier to modify values for the given add-on
		 * @return This builder
		 */
		public <T> Builder modifyWith(String addOnName, Class<T> type, QonfigValueModifier<T> modifier) {
			if (theToolkit == null)
				throw new IllegalStateException("Use forToolkit(QonfigToolkit) first to get an interpreter for a toolkit");
			QonfigAddOn addOn = theToolkit.getAddOn(addOnName);
			if (addOn == null)
				throw new IllegalArgumentException("No such add-on '" + addOnName + "' in toolkit " + theToolkit.getLocation());
			return modifyWith(addOn, type, modifier);
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
							if (ext.theSuperElement.isAbstract())
								theStatus.error(el, "No creator configured for element");
						} else if (!ext.theSuperType.isAssignableFrom(superHolder.type))
							theStatus.error(el, "Extension of " + ext.theSuperType.getName() + " is parsed as " + superHolder.type.getName()
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
			return new QonfigInterpreter(QommonsUtils.unmodifiableCopy(theCreators), QommonsUtils.unmodifiableCopy(theModifiers));
		}
	}
}

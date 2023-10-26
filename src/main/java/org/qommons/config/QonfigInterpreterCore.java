package org.qommons.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.qommons.BiTuple;
import org.qommons.ClassMap;
import org.qommons.ClassMap.TypeMatch;
import org.qommons.MultiInheritanceSet;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.ex.ExBiConsumer;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;

/** A class for interpreting parsed {@link QonfigDocument}s into useful structures */
public class QonfigInterpreterCore {
	/** Holds values for communication between parsing components */
	public static class CoreSession implements AbstractQIS<CoreSession> {
		private final QonfigInterpreterCore theInterpreter;
		private final CoreSession theParent;
		private final QonfigElement theElement;
		private final QonfigElementOrAddOn theFocusType;
		private final ExceptionThrowingReporting theReporting;
		private final MultiInheritanceSet<QonfigElementOrAddOn> theTypes;
		private final int theChildIndex;
		private final SessionValues.Default theValues;
		private final ClassMap<SpecialSession<?>> theToolkitSessions;

		/**
		 * Creates the root session for interpretation
		 * 
		 * @param interpreter The interpreter this session is for
		 * @param root The root element of the interpretation
		 * @param source The session (if any) that {@link #interpretRoot(QonfigElement)} was called on
		 * @param reporting The error reporting for this session
		 * @throws QonfigInterpretationException If an error occurs initializing this session
		 */
		protected CoreSession(QonfigInterpreterCore interpreter, QonfigElement root, CoreSession source,
			ExceptionThrowingReporting reporting)
			throws QonfigInterpretationException {
			theInterpreter = interpreter;
			theParent = source;
			theElement = root;
			theReporting = reporting;
			theFocusType = root.getType();
			theTypes = MultiInheritanceSet.empty();
			theValues = SessionValues.newRoot();
			theChildIndex = 0;
			theToolkitSessions = new ClassMap<>();
		}

		/**
		 * Creates a sub-session for interpretation
		 * 
		 * @param parent The parent session
		 * @param element The element to interpret
		 * @param focusType The element/add-on type to interpret as (affects what attributes and children are available by name, see
		 *        {@link #getFocusType()})
		 * @param types All types that this session is to be aware of its element implementing
		 * @param childIndex The index of the child in its parent, for improved visibility of errors
		 * @throws QonfigInterpretationException If an error occurs initializing this session
		 */
		protected CoreSession(CoreSession parent, QonfigElement element, QonfigElementOrAddOn focusType,
			MultiInheritanceSet<QonfigElementOrAddOn> types, int childIndex) throws QonfigInterpretationException {
			theInterpreter = parent.theInterpreter;
			theParent = parent;
			theElement = element;
			theFocusType = focusType;
			theTypes = types;
			theChildIndex = childIndex;
			theToolkitSessions = new ClassMap<>();
			if (parent.getElement() == element) {
				theReporting = parent.theReporting;
				theValues = parent.theValues;
			} else {
				theReporting = parent.theReporting.at(element.getFilePosition());
				theValues = parent.theValues.createChild();
			}
		}

		@Override
		public QonfigElement getElement() {
			return theElement;
		}

		/** @return The interpreter this session is for */
		public QonfigInterpreterCore getInterpreter() {
			return theInterpreter;
		}

		@Override
		public QonfigElementOrAddOn getFocusType() {
			return theFocusType;
		}

		@Override
		public MultiInheritanceSet<QonfigElementOrAddOn> getTypes() {
			return theTypes;
		}

		@Override
		public CoreSession asElement(QonfigElementOrAddOn focusType) {
			if (theFocusType == focusType)
				return this;
			else if (theElement.isInstance(focusType)) {
				MultiInheritanceSet<QonfigElementOrAddOn> types = MultiInheritanceSet.create(QonfigElementOrAddOn::isAssignableFrom);
				if (theFocusType != null)
					types.add(theFocusType);
				types.addAll(theTypes.values());
				try (Transaction t = theReporting.interpreting()) {
					return theInterpreter.interpret(this, theElement, focusType, MultiInheritanceSet.unmodifiable(types), theChildIndex);
				} catch (QonfigInterpretationException e) {
					throw new IllegalStateException("Initialization failure for same-element session?", e);
				}
			} else {
				String msg = "Element " + theElement + " is not an instance of " + focusType;
				reporting().error(msg);
				throw new IllegalStateException(msg);
			}
		}

		@Override
		public CoreSession asElementOnly(QonfigElementOrAddOn type) {
			if (theFocusType == type && theTypes.isEmpty())
				return this;
			else if (theElement.isInstance(type)) {
				try (Transaction t = theReporting.interpreting()) {
					return theInterpreter.interpret(this, theElement, type, MultiInheritanceSet.empty(), theChildIndex);
				} catch (QonfigInterpretationException e) {
					throw new IllegalStateException("Initialization failure for same-element session?", e);
				}
			} else {
				String msg = "Element " + theElement + " is not an instance of " + type;
				reporting().error(msg);
				throw new IllegalStateException(msg);
			}
		}

		@Override
		public <QIS extends SpecialSession<QIS>> QIS as(Class<QIS> interpreter) throws QonfigInterpretationException {
			BiTuple<Class<QIS>, SpecialSessionImplementation<QIS>> found = (BiTuple<Class<QIS>, SpecialSessionImplementation<QIS>>) (BiTuple<?, ?>) theInterpreter.theSpecialSessions
				.getEntry(interpreter, TypeMatch.SUB_TYPE);
			if (found == null)
				throw new IllegalArgumentException("Unsupported toolkit interpreter: " + interpreter.getName());
			return special(found);
		}

		/**
		 * A special method intended for {@link SpecialSession}s to call when one of their interpretation methods is called
		 * 
		 * @param <QIS> The type of special session that is calling this method
		 * @param interpreter The interpreter calling this method
		 * @return The special session of the same type with this core session as its {@link SpecialSession#getWrapped() wrapped}
		 * @throws QonfigInterpretationException If an error occurs configuring the special session
		 * @see SpecialSession#asElement(QonfigElementOrAddOn)
		 * @see SpecialSession#forChild(QonfigElement, QonfigElementOrAddOn, MultiInheritanceSet)
		 * @see SpecialSession#interpretRoot(QonfigElement)
		 */
		public <QIS extends SpecialSession<QIS>> QIS recast(QIS interpreter) throws QonfigInterpretationException {
			BiTuple<Class<QIS>, SpecialSessionImplementation<QIS>> found = (BiTuple<Class<QIS>, SpecialSessionImplementation<QIS>>) (BiTuple<?, ?>) theInterpreter.theSpecialSessions
				.getEntry(interpreter.getClass(), TypeMatch.SUPER_TYPE);
			if (found == null)
				throw new IllegalArgumentException("Unsupported toolkit interpreter: " + interpreter.getClass().getName());
			return special(found);
		}

		private <QIS extends SpecialSession<QIS>> QIS special(BiTuple<Class<QIS>, SpecialSessionImplementation<QIS>> found)
			throws QonfigInterpretationException {
			QIS session = (QIS) theToolkitSessions.get(found.getValue1(), TypeMatch.EXACT);
			if (session == null) {
				if (theParent == null) {
					session = found.getValue2().viewOfRoot(this, null);
					theToolkitSessions.with(found.getValue1(), session);
					found.getValue2().postInitRoot(session, null);
				} else {
					QIS parent = theParent.as(found.getValue1());
					if (theParent.getElement() == getElement()) {
						session = found.getValue2().parallelView(parent, this);
						theToolkitSessions.with(found.getValue1(), session);
						found.getValue2().postInitParallel(session, parent);
					} else if (theElement.getParent() == null) {
						session = found.getValue2().viewOfRoot(this, parent);
						theToolkitSessions.with(found.getValue1(), session);
						found.getValue2().postInitRoot(session, parent);
					} else {
						session = found.getValue2().viewOfChild(parent, this);
						theToolkitSessions.with(found.getValue1(), session);
						found.getValue2().postInitChild(session, parent);
					}
				}
			}
			return session;
		}

		@Override
		public CoreSession forChild(QonfigElement child, QonfigElementOrAddOn focusType, MultiInheritanceSet<QonfigElementOrAddOn> types)
			throws QonfigInterpretationException {
			return theInterpreter.interpret(this, child, focusType, MultiInheritanceSet.unmodifiable(types), //
				theElement.getChildren().indexOf(child));
		}

		@Override
		public CoreSession interpretRoot(QonfigElement root) throws QonfigInterpretationException {
			if (root.getParent() != null)
				throw new IllegalArgumentException("Not a root");
			return theInterpreter.interpret(root);
		}

		@Override
		public ErrorReporting reporting() {
			return theReporting;
		}

		@Override
		public <T> T interpret(QonfigElementOrAddOn as, Class<T> asType,
			ExBiConsumer<? super T, ? super CoreSession, QonfigInterpretationException> action) throws QonfigInterpretationException {
			try (Transaction t = theReporting.interpreting()) {
				ClassMap<QonfigCreatorHolder<?>> creators = theInterpreter.theCreators.get(as);
				QonfigCreatorHolder<T> creator = creators == null ? null
					: (QonfigCreatorHolder<T>) creators.get(asType, ClassMap.TypeMatch.SUB_TYPE);
				if (creator == null) {
					String msg = "No creator registered for element " + as.getName() + " and target type " + asType.getName();
					reporting().error(msg);
					return null;
				}
				CoreSession session;
				if (theFocusType == creator.element)
					session = this;
				else if (theElement.isInstance(creator.element))
					session = asElement(as);
				else {
					String msg = "Element " + theElement + " is not an instance of " + as;
					reporting().error(msg);
					throw new IllegalStateException(msg);
				}
				QonfigModifierHolder<T>[] modifiers = getModifiers(creator.type);
				Object[] modifierPrepValues = new Object[modifiers.length];
				int mIdx = 0;
				for (QonfigModifierHolder<T> modifier : modifiers) {
					try {
						if (theElement.isInstance(modifier.element))
							modifierPrepValues[mIdx++] = modifier.modifier.prepareSession(session.asElement(modifier.element));
					} catch (RuntimeInterpretationException e) {
						throw e.toIntepreterException();
					} catch (RuntimeException e) {
						LocatedFilePosition position = theElement.getFilePosition().getPosition(0);
						throw new QonfigInterpretationException(position, 0, e);
					}
				}
				for (mIdx = modifiers.length - 1; mIdx >= 0; mIdx--) {
					QonfigModifierHolder<T> modifier = modifiers[mIdx];
					try {
						if (theElement.isInstance(modifier.element))
							modifierPrepValues[mIdx] = modifier.modifier.postPrepare(session.asElement(modifier.element),
								modifierPrepValues[mIdx]);
					} catch (RuntimeInterpretationException e) {
						throw e.toIntepreterException();
					} catch (RuntimeException e) {
						LocatedFilePosition position = theElement.getFilePosition().getPosition(0);
						throw new QonfigInterpretationException(position, 0, e);
					}
				}
				T value;
				try {
					value = creator.creator.createValue(session);
				} catch (RuntimeInterpretationException e) {
					throw e.toIntepreterException();
				} catch (RuntimeException e) {
					LocatedFilePosition position = theElement.getFilePosition().getPosition(0);
					throw new QonfigInterpretationException(position, 0, e);
				}
				for (mIdx = 0; mIdx < modifiers.length; mIdx++) {
					QonfigModifierHolder<T> modifier = modifiers[mIdx];
					try {
						if (theElement.isInstance(modifier.element))
							value = modifier.modifier.modifyValue(value, session.asElement(modifier.element), modifierPrepValues[mIdx]);
					} catch (RuntimeInterpretationException e) {
						throw e.toIntepreterException();
					} catch (RuntimeException e) {
						LocatedFilePosition position = theElement.getFilePosition().getPosition(0);
						throw new QonfigInterpretationException(position, 0, e);
					}
				}
				try {
					// This is safe because the creator created the value
					value = ((QonfigValueCreator<T>) creator.creator).postModification(value, session);
				} catch (RuntimeInterpretationException e) {
					throw e.toIntepreterException();
				} catch (RuntimeException e) {
					LocatedFilePosition position = theElement.getFilePosition().getPosition(0);
					throw new QonfigInterpretationException(position, 0, e);
				}
				if (action != null)
					action.accept(value, session);
				return value;
			}
		}

		@Override
		public <T> Class<? extends T> getInterpretationSupport(Class<T> asType) {
			ClassMap<QonfigCreatorHolder<?>> creators = theInterpreter.theCreators.get(theFocusType);
			QonfigCreatorHolder<?> creator = creators == null ? null : creators.get(asType, ClassMap.TypeMatch.SUB_TYPE);
			return creator == null ? null : ((QonfigCreatorHolder<? extends T>) creator).type;
		}

		@Override
		public SessionValues values() {
			return theValues;
		}

		<T> QonfigModifierHolder<T>[] getModifiers(Class<T> type) throws QonfigInterpretationException {
			Map<QonfigValueModifier<T>, QonfigModifierHolder<T>> modifiers = new LinkedHashMap<>();
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
			return modifiers.values().toArray(new QonfigModifierHolder[modifiers.size()]);
		}

		private <T> void getModifiers(Class<T> type, QonfigElementDef superType,
			Map<QonfigValueModifier<T>, QonfigModifierHolder<T>> modifiers) throws QonfigInterpretationException {
			if (superType == null)
				return;
			getModifiers(type, superType.getSuperElement(), modifiers);
			doModify(type, superType, modifiers);
		}

		private <T> void modifyWith(Class<T> type, QonfigAddOn addOn, Set<QonfigAddOn> inh,
			Map<QonfigValueModifier<T>, QonfigModifierHolder<T>> modifiers) throws QonfigInterpretationException {
			for (QonfigAddOn ext : addOn.getFullInheritance().getExpanded(QonfigAddOn::getInheritance)) {
				if (inh.add(ext))
					modifyWith(type, ext, inh, modifiers);
			}
			doModify(type, addOn, modifiers);
		}

		private <T> void doModify(Class<T> type, QonfigElementOrAddOn modifierType,
			Map<QonfigValueModifier<T>, QonfigModifierHolder<T>> modifiers2) throws QonfigInterpretationException {
			if (modifierType == null)
				return;
			ClassMap<QonfigModifierHolder<?>> modifiers = theInterpreter.theModifiers.get(modifierType);
			if (modifiers == null)
				return;
			List<BiTuple<Class<?>, QonfigModifierHolder<?>>> typeModifiers = modifiers.getAllEntries(type, ClassMap.TypeMatch.SUPER_TYPE);
			if (typeModifiers == null)
				return;
			for (BiTuple<Class<?>, QonfigModifierHolder<?>> modifier : typeModifiers) {
				modifiers2.putIfAbsent((QonfigValueModifier<T>) modifier.getValue2().modifier,
					(QonfigModifierHolder<T>) modifier.getValue2());
			}
		}

		@Override
		public String toString() {
			return "Interpreting " + theElement + " with " + theFocusType;
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
		T createValue(CoreSession session) throws QonfigInterpretationException;

		/**
		 * Potentially modifies and/or inspects the value after all modification
		 * 
		 * @param value The interpreted value so far
		 * @param session The interpretation session
		 * @return The final interpreted value
		 * @throws QonfigInterpretationException If an error occurs
		 */
		default T postModification(T value, CoreSession session) throws QonfigInterpretationException {
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
		 * @param session The active interpreter session
		 * @return The value for the element
		 * @throws QonfigInterpretationException If the value could not be created or modified
		 */
		T createValue(S superValue, CoreSession session) throws QonfigInterpretationException;
	}

	/**
	 * Modifies an interpreted value for an add-on or abstract element-def
	 * 
	 * @param <T> The type of value to modify
	 */
	public interface QonfigValueModifier<T> {
		/**
		 * @param session The active interpreter session
		 * @return An Object to pass to {@link #postPrepare(CoreSession, Object)} after the all modifiers have prepared the session
		 * @throws QonfigInterpretationException If anything goes wrong with the preparation
		 */
		default Object prepareSession(CoreSession session) throws QonfigInterpretationException {
			return null;
		}

		/**
		 * @param value The value created for the element
		 * @param session The active interpreter session
		 * @param prepared Object returned from {@link #prepareSession(CoreSession)}, default null
		 * @return The Object to pass to {@link #modifyValue(Object, CoreSession, Object)} after the value has been created
		 * @throws QonfigInterpretationException If the post-modification operation could not be performed
		 */
		default Object postPrepare(CoreSession session, Object prepared) throws QonfigInterpretationException {
			return prepared;
		}

		/**
		 * @param value The value created for the element
		 * @param session The active interpreter session
		 * @param prepared Object returned from {@link #prepareSession(CoreSession)}, default null
		 * @return The modified value
		 * @throws QonfigInterpretationException If the value could not be modified
		 */
		T modifyValue(T value, CoreSession session, Object prepared) throws QonfigInterpretationException;
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
		public T createValue(CoreSession session) throws QonfigInterpretationException {
			S superValue = session.asElement(theSuperElement).interpret(theSuperType);
			return theExtension.createValue(superValue, session);
		}
	}

	static class QonfigCreationDelegator<T> implements QonfigValueCreator<T> {
		private final QonfigAttributeDef.Declared theTypeAttribute;
		private final Class<T> theType;

		public QonfigCreationDelegator(QonfigAttributeDef.Declared typeAttribute, Class<T> type) {
			theTypeAttribute = typeAttribute;
			theType = type;
		}

		@Override
		public T createValue(CoreSession session) throws QonfigInterpretationException {
			QonfigAddOn delegate = (QonfigAddOn) session.getElement().getAttributes().get(theTypeAttribute).value;
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
	 * @param <T> The type of value to create
	 */
	protected static class QonfigCreatorHolder<T> {
		final QonfigElementOrAddOn element;
		final Class<T> type;
		final QonfigValueCreator<? extends T> creator;

		QonfigCreatorHolder(QonfigElementOrAddOn element, Class<T> type, QonfigValueCreator<? extends T> creator) {
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
	 * @param <T> The type of value to modify
	 */
	protected static class QonfigModifierHolder<T> {
		final QonfigElementOrAddOn element;
		final Class<T> type;
		final QonfigValueModifier<T> modifier;

		QonfigModifierHolder(QonfigElementOrAddOn element, Class<T> type, QonfigValueModifier<T> creator) {
			this.element = element;
			this.type = type;
			this.modifier = creator;
		}

		@Override
		public String toString() {
			return "Modify " + type.getName() + " for " + element;
		}
	}

	private final Set<QonfigToolkit> theKnownToolkits;
	private final Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<?>>> theCreators;
	private final Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<?>>> theModifiers;
	private final ClassMap<SpecialSessionImplementation<?>> theSpecialSessions;
	private final ExceptionThrowingReporting theReporting;

	/**
	 * @param allKnownToolkits All toolkits used to build the interpreter
	 * @param creators The set of value creators for interpretation
	 * @param modifiers The set of value modifiers for interpretation
	 * @param specialSessions Special session implementations configured for the interpreter
	 * @param reporting The error reporting for the interpretation
	 */
	protected QonfigInterpreterCore(Set<QonfigToolkit> allKnownToolkits,
		Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<?>>> creators,
		Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<?>>> modifiers, ClassMap<SpecialSessionImplementation<?>> specialSessions,
		ExceptionThrowingReporting reporting) {
		theKnownToolkits = allKnownToolkits;
		theCreators = creators;
		theModifiers = modifiers;
		theReporting = reporting;
		theSpecialSessions = specialSessions.copy();
		// Compile and check dependencies
		StringBuilder error = null;
		for (SpecialSessionImplementation<?> tki : theSpecialSessions.getAll(Object.class, TypeMatch.SUB_TYPE)) {
			for (Class<? extends SpecialSession<?>> dep : tki.getExpectedAPIs()) {
				if (theSpecialSessions.get(dep, TypeMatch.SUB_TYPE) == null) {
					if (error == null)
						error = new StringBuilder();
					else
						error.append('\n');
					error.append("Unmet dependency: " + dep.getName() + " of interpreter " + tki.getClass().getName());
				}
			}
		}
		if (error != null)
			throw new IllegalStateException(error.toString());
	}

	/** @return All toolkits known to this interpreter */
	public Set<QonfigToolkit> getKnownToolkits() {
		return theKnownToolkits;
	}

	/**
	 * Commences interpretation of a Qonfig element
	 * 
	 * @param element The root element to interpret
	 * @return The session to do the interpretation
	 * @throws QonfigInterpretationException If an error occurs initializing the interpretation
	 */
	public CoreSession interpret(QonfigElement element) throws QonfigInterpretationException {
		return interpretRoot(element, null);
	}

	/**
	 * @param element The element to interpret
	 * @param source The element (if any) that {@link CoreSession#interpretRoot(QonfigElement)} was called on
	 * @return The session to use to interpret the element
	 * @throws QonfigInterpretationException If an error occurs initializing the session
	 */
	protected CoreSession interpretRoot(QonfigElement element, CoreSession source) throws QonfigInterpretationException {
		return new CoreSession(this, element, source, theReporting);
	}

	/**
	 * Creates an interpretation sub-session
	 * 
	 * @param parent The parent session
	 * @param element The element to interpret
	 * @param focusType The element/add-on type to interpret as (affects what attributes and children are available by name, see
	 *        {@link AbstractQIS#getFocusType()})
	 * @param types All types that the session is to be aware of its element extending
	 * @param childIndex The index of the child in its parent, for improved visibility of errors
	 * @return The session to interpret the element
	 * @throws QonfigInterpretationException If an error occurs initializing the interpretation
	 */
	protected CoreSession interpret(CoreSession parent, QonfigElement element, QonfigElementOrAddOn focusType,
		MultiInheritanceSet<QonfigElementOrAddOn> types, int childIndex) throws QonfigInterpretationException {
		return new CoreSession(parent, element, focusType, types, childIndex);
	}

	/** Builds {@link QonfigInterpreterCore}s */
	public static class Builder {
		private final Set<QonfigToolkit> theToolkits;
		private final QonfigToolkit theToolkit;
		private final ExceptionThrowingReporting theReporting;
		private final Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<?>>> theCreators;
		private final Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<?>>> theModifiers;
		private final ClassMap<SpecialSessionImplementation<?>> theSpecialSessions;

		/**
		 * Initial constructor called from {@link QonfigInterpreterCore#build(ErrorReporting, QonfigToolkit...)}
		 * 
		 * @param reporting The error reporting for the interpretation
		 * @param toolkits The toolkits to interpret documents for
		 */
		protected Builder(ExceptionThrowingReporting reporting, QonfigToolkit... toolkits) {
			theToolkits = QommonsUtils.unmodifiableDistinctCopy(toolkits);
			if (toolkits == null)
				throw new NullPointerException();
			theToolkit = null;
			theReporting = reporting;
			theCreators = new HashMap<>();
			theModifiers = new HashMap<>();
			theSpecialSessions = new ClassMap<>();

			reporting.ignoreClass(QonfigParseSession.class.getName());
			reporting.ignoreClass(QonfigInterpreterCore.class.getName());
			reporting.ignoreClass(QonfigInterpreterCore.ExceptionThrowingReporting.class.getName());
			reporting.ignoreClass(QonfigInterpreterCore.CoreSession.class.getName());
			reporting.ignoreClass(AbstractQIS.class.getName());
			reporting.ignoreClass(SpecialSession.class.getName());
			reporting.ignoreClass(Builder.class.getName());
		}

		/**
		 * View builder called from {@link #forToolkit(QonfigToolkit)}
		 * 
		 * @param toolkits The toolkits to interpret documents for
		 * @param toolkit The toolkit to get elements/add-ons for when only names are specified
		 * @param reporting The error reporting for the interpretation
		 * @param creators The set of value creators for interpretation so far
		 * @param modifiers The set of value modifiers for interpretation so far
		 * @param specialSessions Special session implementations configured for the builder
		 */
		protected Builder(Set<QonfigToolkit> toolkits, QonfigToolkit toolkit, ExceptionThrowingReporting reporting,
			Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<?>>> creators,
			Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<?>>> modifiers,
			ClassMap<SpecialSessionImplementation<?>> specialSessions) {
			theToolkits = toolkits;
			theToolkit = toolkit;
			theReporting = reporting;
			theCreators = creators;
			theModifiers = modifiers;
			theSpecialSessions = specialSessions;
		}

		/**
		 * @param toolkits The toolkits to interpret documents for
		 * @param toolkit The toolkit to get elements/add-ons for when only names are specified
		 * @param reporting The error reporting for the interpretation
		 * @param creators The set of value creators for interpretation so far
		 * @param modifiers The set of value modifiers for interpretation so far
		 * @param specialSessions Special session implementations configured for the builder
		 * @return A new builder with the given data
		 */
		protected Builder builderFor(Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			ExceptionThrowingReporting reporting, Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<?>>> creators,
			Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<?>>> modifiers,
			ClassMap<SpecialSessionImplementation<?>> specialSessions) {
			return new Builder(toolkits, toolkit, reporting, creators, modifiers, specialSessions);
		}

		/** @return A new interpreter with this builder's configuration */
		public QonfigInterpreterCore create() {
			return new QonfigInterpreterCore(theToolkits, getCreators(), getModifiers(), theSpecialSessions,
				theReporting);
		}

		/** @return The toolkit that will be used to get elements/add-ons when only names are specified */
		public QonfigToolkit getToolkit() {
			return theToolkit;
		}

		/** @return All toolkits supported by this builder */
		public Set<QonfigToolkit> getKnownToolkits() {
			return theToolkits;
		}

		/** @return The value creators configured in this builder */
		protected Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<?>>> getCreators() {
			return QommonsUtils.unmodifiableCopy(theCreators);
		}

		/** @return The value modifiers configured in this builder */
		protected Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<?>>> getModifiers() {
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
		public Builder forToolkit(QonfigToolkit toolkit) {
			if (theToolkit == toolkit)
				return this;
			else if (!dependsOn(toolkit))
				throw new IllegalArgumentException("Toolkit " + toolkit.getLocation() + " is not used by toolkits " + theToolkits);
			else
				return builderFor(theToolkits, toolkit, theReporting, theCreators, theModifiers, theSpecialSessions);
		}

		/**
		 * Enables {@link AbstractQIS#as(Class)} for the given type of special session on any sessions created by the interpreter built from
		 * this builder
		 * 
		 * @param <QIS> The type of special session to enable
		 * @param type The type of special session to enable
		 * @param impl The implementation to create and initialize the special sessions
		 * @return This builder
		 */
		public <QIS extends SpecialSession<QIS>> Builder withSpecial(Class<QIS> type, SpecialSessionImplementation<QIS> impl) {
			boolean foundTK = false;
			for (QonfigToolkit tk : theToolkits) {
				if (tk.getName().equals(impl.getToolkitName())//
					&& tk.getMajorVersion() == impl.getVersion().major//
					&& tk.getMinorVersion() == impl.getVersion().minor) {
					foundTK = true;
					impl.init(tk);
					break;
				}
			}
			if (!foundTK)
				throw new IllegalArgumentException("No such toolkit " + impl.getToolkitName() + " v" + impl.getVersion().major + "."
					+ impl.getVersion().minor + " found for interpreter " + impl.getClass().getName());
			theSpecialSessions.with(type, impl);
			return this;
		}

		/**
		 * @param <T> The type to create
		 * @param element The element-def to create values for
		 * @param type The type to create
		 * @param creator The creator to interpret elements of the given type
		 * @return This builder
		 */
		public <T> Builder createWith(QonfigElementOrAddOn element, Class<T> type, QonfigValueCreator<? extends T> creator) {
			if (!dependsOn(element.getDeclarer()))
				throw new IllegalArgumentException("Element " + element.getName() + " is from a toolkit not included in " + theToolkits);
			theCreators.compute(element, (el, old) -> {
				if (old == null)
					old = new ClassMap<>();
				// If it already exists, assume this just called twice via dependencies
				old.computeIfAbsent(type, () -> new QonfigCreatorHolder<>(element, type, creator));
				return old;
			});
			return this;
		}

		/**
		 * @param <T> The type to create
		 * @param elementName The name of the element-def to create values for
		 * @param type The type to create
		 * @param creator The creator to interpret elements of the given type
		 * @return This builder
		 */
		public <T> Builder createWith(String elementName, Class<T> type, QonfigValueCreator<? extends T> creator) {
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

		/**
		 * @param <T> The type of value to interpret
		 * @param element The element to interpret
		 * @param typeAttribute The add-on-typed attribute to delegate interpretation to for the element
		 * @param type The type of the value to interpret
		 * @return This builder
		 */
		public <T> Builder delegateToType(QonfigElementOrAddOn element, QonfigAttributeDef.Declared typeAttribute, Class<T> type) {
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
				return this; // Assume it's the same caller
			theModifiers.computeIfAbsent(elementOrAddOn, __ -> new ClassMap<>()).with(type,
				new QonfigModifierHolder<>(elementOrAddOn, type, modifier));
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

		/**
		 * Adds a set of interpretation solutions to this interpreter
		 * 
		 * @param interpretation The interpretations to configure
		 * @return This builder
		 */
		public Builder configure(QonfigInterpretation... interpretation) {
			StringBuilder error = null;
			for (QonfigInterpretation interp : interpretation) {
				QonfigToolkit interpTK = null;
				boolean tkok = true;
				for (QonfigToolkit tk : theToolkits) {
					if (tk.getName().equals(interp.getToolkitName())//
						&& tk.getMajorVersion() == interp.getVersion().major//
						&& tk.getMinorVersion() == interp.getVersion().minor) {
						interpTK = tk;
						interp.init(interpTK);
						break;
					}
				}
				if (interpTK == null) {
					tkok = false;
					if (error == null)
						error = new StringBuilder();
					else
						error.append('\n');
					error.append("No such toolkit " + interp.getToolkitName() + " v" + interp.getVersion().major + "."
						+ interp.getVersion().minor + " found for interpretation " + interp.getClass().getName());
				}
				for (Class<? extends SpecialSession<?>> api : interp.getExpectedAPIs()) {
					if (theSpecialSessions.get(api, TypeMatch.SUB_TYPE) == null) {
						tkok = false;
						if (error == null)
							error = new StringBuilder();
						else
							error.append('\n');
						error.append("Unmet dependency " + api.getName() + " of interpretation " + interp.getClass().getName());
					}
				}
				if (error != null)
					theReporting.warn(error.toString());

				if (tkok)
					interp.configureInterpreter(forToolkit(interpTK));
			}
			return this;
		}

		/** @return The built interpreter */
		public QonfigInterpreterCore build() {
			Set<QonfigAddOn> usedInh = new HashSet<>();
			for (QonfigToolkit tk : theToolkits) {
				for (QonfigElementDef el : tk.getAllElements().values()) {
					if (el.isAbstract())
						continue;
					ClassMap<QonfigCreatorHolder<?>> creators = theCreators.get(el);
					if (creators == null) {
						// theStatus.error(el, "No creator configured for element");
					} else {
						for (BiTuple<Class<?>, QonfigCreatorHolder<?>> holder : creators.getAllEntries()) {
							if (holder.getValue2().creator instanceof QonfigExtensionCreator) {
								QonfigExtensionCreator<?, ?> ext = (QonfigExtensionCreator<?, ?>) holder.getValue2().creator;
								ClassMap<QonfigCreatorHolder<?>> superHolders = theCreators.get(ext.theSuperElement);
								QonfigCreatorHolder<?> superHolder = superHolders == null ? null
									: superHolders.get(holder.getValue1(), ClassMap.TypeMatch.SUB_TYPE);
								if (superHolder == null) {
									// If the super element is not abstract, there will be a separate error for its not having a creator
									// If it is abstract, we need one here
									// if (ext.theSuperElement.isAbstract())
									// theStatus.error(el, "No creator configured for element");
								} else if (!ext.theSuperType.isAssignableFrom(superHolder.type))
									theReporting.warn("Extension of " + ext.theSuperType.getName() + " is parsed as "
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
			return create();
		}
	}

	static class RuntimeInterpretationException extends RuntimeException {
		private final LocatedFilePosition thePosition;

		RuntimeInterpretationException(String message, LocatedFilePosition position) {
			super(message);
			thePosition = position;
		}

		RuntimeInterpretationException(String message, Throwable cause, LocatedFilePosition position) {
			super(message, cause);
			thePosition = position;
		}

		public LocatedFilePosition getPosition() {
			return thePosition;
		}

		public QonfigInterpretationException toIntepreterException() {
			QonfigInterpretationException qie;
			if (getCause() != this)
				qie = new QonfigInterpretationException(getMessage(), getPosition(), 0, getCause());
			else
				qie = new QonfigInterpretationException(getMessage(), getPosition(), 0);
			qie.setStackTrace(getStackTrace());
			return qie;
		}

		@Override
		public String toString() {
			if (thePosition != null)
				return new StringBuilder().append(thePosition).append(": ").append(super.toString()).toString();
			else
				return super.toString();
		}
	}

	/**
	 * ErrorReporting that throws runtime exceptions during interpretation, but then delegates to a wrapped reporter after interpreting is
	 * finished
	 */
	protected static class ExceptionThrowingReporting implements ErrorReporting {
		private final ErrorReporting theWrapped;
		private boolean isInterpreting;

		/** @param parent The reporting instance to delegate to after interpretation is finished */
		public ExceptionThrowingReporting(ErrorReporting parent) {
			theWrapped = parent;
		}

		Transaction interpreting() {
			boolean pre = isInterpreting;
			if (pre)
				return Transaction.NONE;
			isInterpreting = true;
			return () -> isInterpreting = false;
		}

		@Override
		public LocatedPositionedContent getFileLocation() {
			return theWrapped.getFileLocation();
		}

		@Override
		public void ignoreClass(String className) {
			theWrapped.ignoreClass(className);
		}

		@Override
		public StackTraceElement getCodeLocation() {
			return theWrapped.getCodeLocation();
		}

		@Override
		public ErrorReporting report(Issue issue) {
			theWrapped.report(issue);
			if (issue.severity == IssueSeverity.ERROR) {
				if (issue.cause == null)
					throw new RuntimeInterpretationException(issue.message, issue.fileLocation);
				else
					throw new RuntimeInterpretationException(issue.message, issue.cause, issue.fileLocation);
			}
			return this;
		}

		@Override
		public ExceptionThrowingReporting at(LocatedPositionedContent frame) {
			return new ExceptionThrowingReporting(theWrapped.at(frame));
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * @param reporting The error reporting for the interpretation
	 * @param toolkits The toolkits to interpret
	 * @return A builder to create an interpreter
	 */
	public static Builder build(ErrorReporting reporting, QonfigToolkit... toolkits) {
		return new Builder(new ExceptionThrowingReporting(reporting), toolkits);
	}
}

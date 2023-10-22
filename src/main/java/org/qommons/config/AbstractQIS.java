package org.qommons.config;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.qommons.ex.ExBiConsumer;
import org.qommons.io.ErrorReporting;

/**
 * An interpretation session with lots of capabilities for interpreting {@link QonfigElement}s
 * 
 * @param <QIS> The sub-type of the session
 */
public interface AbstractQIS<QIS extends AbstractQIS<QIS>>
	extends QonfigElementView<QonfigElement, QonfigInterpretationException, QIS>, SessionValues {
	/** The property that the {@link #getElementRepresentation() element representation} is stored under */
	static final String ELEMENT_REPRESENTATION = "ELEMENT.REPRESENTATION";

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
	 * @param <T> The type of value to interpret
	 * @param asType The type of value to interpret the element as
	 * @return The interpreted value
	 * @throws QonfigInterpretationException If the value cannot be interpreted
	 */
	default <T> T interpret(Class<T> asType) throws QonfigInterpretationException {
		return interpret(getElement().getType(), asType);
	}

	/**
	 * @param <T> The type of value to interpret
	 * @param asType The type of value to interpret the element as
	 * @param action The action to perform after interpretation--may be null
	 * @return The interpreted value
	 * @throws QonfigInterpretationException If the value cannot be interpreted
	 */
	default <T> T interpret(Class<T> asType, ExBiConsumer<? super T, ? super QIS, QonfigInterpretationException> action)
		throws QonfigInterpretationException {
		return interpret(getElement().getType(), asType, action);
	}

	/**
	 * @param <T> The type of value to interpret
	 * @param as The element type to interpret the element as
	 * @param asType The type of value to interpret the element as
	 * @return The interpreted value
	 * @throws QonfigInterpretationException If the value cannot be interpreted
	 */
	default <T> T interpret(QonfigElementOrAddOn as, Class<T> asType) throws QonfigInterpretationException {
		return interpret(as, asType, null);
	}

	/**
	 * @param <T> The type of value to interpret
	 * @param as The element type to interpret the element as
	 * @param asType The type of value to interpret the element as
	 * @param action The action to perform after interpretation--may be null
	 * @return The interpreted value
	 * @throws QonfigInterpretationException If the value cannot be interpreted
	 */
	<T> T interpret(QonfigElementOrAddOn as, Class<T> asType, ExBiConsumer<? super T, ? super QIS, QonfigInterpretationException> action)
		throws QonfigInterpretationException;

	/**
	 * @param root The root element to interpret
	 * @return The interpretation session for the element
	 * @throws QonfigInterpretationException If an error occurs configuring the interpreter
	 */
	QIS interpretRoot(QonfigElement root) throws QonfigInterpretationException;

	/** @return The object being interpreted from this session's element */
	default Object getElementRepresentation() {
		return values().get(ELEMENT_REPRESENTATION);
	}

	/**
	 * @param er The object being interpreted from this session's element
	 * @return This session
	 */
	default QIS setElementRepresentation(Object er) {
		values().put(ELEMENT_REPRESENTATION, er);
		return (QIS) this;
	}

	/**
	 * @param <T> The interpretation support type to query
	 * @param asType The type to query for
	 * @return If {@link #interpret(Class) interpretation} as a value of the given type is supported by this session, the sub-type that the
	 *         interpreted value will be; otherwise null
	 */
	<T> Class<? extends T> getInterpretationSupport(Class<T> asType);

	/** @return The error reporting for this session */
	ErrorReporting reporting();

	/** @return The values for this session */
	SessionValues values();

	@Override
	default Object get(String sessionKey, boolean local) {
		return values().get(sessionKey, local);
	}

	@Override
	default <T> T get(String sessionKey, Class<? super T> type) {
		return values().get(sessionKey, type);
	}

	@Override
	default QIS put(String sessionKey, Object value) {
		values().put(sessionKey, value);
		return (QIS) this;
	}

	@Override
	default QIS putLocal(String sessionKey, Object value) {
		values().putLocal(sessionKey, value);
		return (QIS) this;
	}

	@Override
	default QIS putGlobal(String sessionKey, Object value) {
		values().putGlobal(sessionKey, value);
		return (QIS) this;
	}

	@Override
	default <T> T computeIfAbsent(String sessionKey, Supplier<T> creator) {
		return values().computeIfAbsent(sessionKey, creator);
	}

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

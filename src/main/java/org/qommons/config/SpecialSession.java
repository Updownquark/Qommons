package org.qommons.config;

import java.util.function.Supplier;

import org.qommons.config.QonfigInterpreterCore.CoreSession;

/**
 * A session with added capabilities and utilities. These sessions are provided by a {@link SpecialSessionImplementation} via
 * {@link QonfigInterpreterCore.Builder#withSpecial(Class, SpecialSessionImplementation)} and are accessed from
 * {@link QonfigInterpretation interpretations} by {@link AbstractQIS#as(Class)}.
 * 
 * @param <QIS> The sub-type of the session
 */
public interface SpecialSession<QIS extends SpecialSession<QIS>> extends AbstractQIS<QIS> {
	/** @return The core session backing this special session */
	CoreSession getWrapped();

	@Override
	default QonfigElement getElement() {
		return getWrapped().getElement();
	}

	@Override
	default QonfigElementOrAddOn getType() {
		return getWrapped().getType();
	}

	@Override
	default QIS asElement(QonfigElementOrAddOn type) {
		try {
			return getWrapped().asElement(type).recast((QIS) this);
		} catch (QonfigInterpretationException e) {
			throw new IllegalStateException("Should not encounter an exception", e);
		}
	}

	@Override
	default <QIS2 extends SpecialSession<QIS2>> QIS2 as(Class<QIS2> interpreter) throws QonfigInterpretationException {
		return getWrapped().as(interpreter);
	}

	@Override
	default QIS interpretChild(QonfigElement child, QonfigElementOrAddOn asType) throws QonfigInterpretationException {
		return getWrapped().interpretChild(child, asType).recast((QIS) this);
	}

	@Override
	default QIS intepretRoot(QonfigElement root) throws QonfigInterpretationException {
		return getWrapped().intepretRoot(root).recast((QIS) this);
	}

	@Override
	default <T> T interpret(QonfigElementOrAddOn as, Class<T> asType) throws QonfigInterpretationException {
		return getWrapped().interpret(as, asType);
	}

	@Override
	default Object get(String sessionKey) {
		return getWrapped().get(sessionKey);
	}

	@Override
	default <T> T get(String sessionKey, Class<? super T> type) {
		return getWrapped().get(sessionKey, type);
	}

	@Override
	default QIS put(String sessionKey, Object value) {
		getWrapped().put(sessionKey, value);
		return (QIS) this;
	}

	@Override
	default QIS putGlobal(String sessionKey, Object value) {
		getWrapped().putGlobal(sessionKey, value);
		return (QIS) this;
	}

	@Override
	default <T> T computeIfAbsent(String sessionKey, Supplier<T> creator) {
		return getWrapped().computeIfAbsent(sessionKey, creator);
	}

	@Override
	default QonfigParseSession getParseSession() {
		return getWrapped().getParseSession();
	}
}
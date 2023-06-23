package org.qommons.config;

import org.qommons.MultiInheritanceSet;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.io.ErrorReporting;

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
	default QonfigElementOrAddOn getFocusType() {
		return getWrapped().getFocusType();
	}

	@Override
	default MultiInheritanceSet<QonfigElementOrAddOn> getTypes() {
		return getWrapped().getTypes();
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
	default QIS asElementOnly(QonfigElementOrAddOn type) {
		try {
			return getWrapped().asElementOnly(type).recast((QIS) this);
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
	default <T> Class<? extends T> getInterpretationSupport(Class<T> asType) {
		return getWrapped().getInterpretationSupport(asType);
	}

	@Override
	default SessionValues values() {
		return getWrapped().values();
	}

	@Override
	default ErrorReporting reporting() {
		return getWrapped().reporting();
	}
}
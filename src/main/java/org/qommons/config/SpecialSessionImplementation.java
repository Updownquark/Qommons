package org.qommons.config;

import java.util.Set;

import org.qommons.Version;
import org.qommons.config.QonfigInterpreterCore.CoreSession;

/**
 * Provides a type of {@link SpecialSession}. This is installed with
 * {@link QonfigInterpreterCore.Builder#withSpecial(Class, SpecialSessionImplementation)}.
 * 
 * @param <QIS> The type of special session provided
 */
public interface SpecialSessionImplementation<QIS extends SpecialSession<QIS>> {
	/**
	 * @return The name of the toolkit that this implementation provides a special session for (will be given to
	 *         {@link #init(QonfigToolkit)})
	 */
	String getToolkitName();

	/**
	 * @return The version of this special session implementation. The {@link Version#major major}/{@link Version#minor minor} version must
	 *         match that of this implementation's toolkit. The {@link Version#patch patch} describes this implementation uniquely.
	 */
	Version getVersion();

	/** @return The type of special session provided by this implementation */
	Class<QIS> getProvidedAPI();

	/** @return Any other special session APIs this implementation depends on */
	Set<Class<? extends SpecialSession<?>>> getExpectedAPIs();

	/**
	 * Initializes this implementation with its toolkit
	 * 
	 * @param toolkit The toolkit identified by the {@link #getToolkitName() name} and {@link Version#major major}/{@link Version#minor
	 *        minor} {@link #getVersion() version}
	 */
	void init(QonfigToolkit toolkit);

	/**
	 * @param coreSession The core session representing the element
	 * @param source The special session, if any, that requested the interpretation (via {@link AbstractQIS#interpretRoot(QonfigElement)}
	 * @return The special session for the element
	 * @throws QonfigInterpretationException If an error occurs creating the session
	 */
	QIS viewOfRoot(CoreSession coreSession, QIS source) throws QonfigInterpretationException;

	/**
	 * @param parent The parent for the new special session
	 * @param coreSession The core session representing the element
	 * @return The special session for the element
	 * @throws QonfigInterpretationException If an error occurs creating the session
	 */
	QIS viewOfChild(QIS parent, CoreSession coreSession) throws QonfigInterpretationException;

	/**
	 * @param parallel The special session that the new session will be another view of
	 * @param coreSession The core session representing the element
	 * @return The special session for the element
	 */
	QIS parallelView(QIS parallel, CoreSession coreSession); // No throwing here, because there shouldn't be any computation

	/**
	 * Performs any necessary initialization on the new root session, created with {@link #viewOfRoot(CoreSession, SpecialSession)}
	 * 
	 * @param session The session to initialize
	 * @param source The special session, if any, that requested the interpretation (via {@link AbstractQIS#interpretRoot(QonfigElement)}
	 * @throws QonfigInterpretationException If an error occurs initializing the session
	 */
	void postInitRoot(QIS session, QIS source) throws QonfigInterpretationException;

	/**
	 * Performs any necessary initialization on the new child session, created with {@link #viewOfChild(SpecialSession, CoreSession)}
	 * 
	 * @param session The session to initialize
	 * @param parent The special session of the new session's parent
	 * @throws QonfigInterpretationException If an error occurs initializing the session
	 */
	void postInitChild(QIS session, QIS parent) throws QonfigInterpretationException;

	/**
	 * Performs any necessary initialization on the new child session, created with {@link #parallelView(SpecialSession, CoreSession)}
	 * 
	 * @param session The session to initialize
	 * @param parallel The special session that the new session is another view of
	 * @throws QonfigInterpretationException If an error occurs initializing the session
	 */
	void postInitParallel(QIS session, QIS parallel) throws QonfigInterpretationException;
}

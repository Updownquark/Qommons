package org.qommons.config;

/**
 * Something that can populate interpretation solutions
 * 
 * @param <QIS> The type of session required by this interpretation
 */
public interface QonfigInterpretation<QIS extends QonfigInterpreter.QonfigInterpretingSession<?>> {
	/**
	 * Populates interpretation solutions into an interpreter builder
	 * 
	 * @param <B> The sub-type of builder
	 * @param interpreter The builder for an interpreter
	 * @return The builder
	 */
	<B extends QonfigInterpreter.Builder<? extends QIS, B>> B configureInterpreter(B interpreter);

	/**
	 * @param callingClass The calling class
	 * @param toolkits The toolkits to interpret
	 * @return A builder for a new interpreter
	 */
	QonfigInterpreter.Builder<QIS, ?> createInterpreter(Class<?> callingClass, QonfigToolkit... toolkits);
}

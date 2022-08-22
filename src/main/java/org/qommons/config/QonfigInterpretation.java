package org.qommons.config;

import java.util.Set;

import org.qommons.Version;

/**
 * Something that can populate interpretation solutions
 * 
 * @see QonfigInterpreterCore.Builder#createWith(QonfigElementOrAddOn, Class, org.qommons.config.QonfigInterpreterCore.QonfigValueCreator)
 * @see QonfigInterpreterCore.Builder#modifyWith(QonfigElementOrAddOn, Class, org.qommons.config.QonfigInterpreterCore.QonfigValueModifier)
 */
public interface QonfigInterpretation {
	/**
	 * @return The types of {@link SpecialSession} this interpretation needs
	 * @see AbstractQIS#as(Class)
	 */
	Set<Class<? extends SpecialSession<?>>> getExpectedAPIs();

	/** @return The name of the toolkit that this interpretation can interpret elements of */
	String getToolkitName();

	/**
	 * @return The version of this interpretation. The {@link Version#major}/{@link Version#minor} numbers of the version correspond to
	 *         those of the toolkit that this interpretation can interpret elements of. The {@link Version#patch} and any
	 *         {@link Version#qualifier} are specific to this interpretation
	 */
	Version getVersion();

	/**
	 * Populates interpretation solutions into an interpreter builder. The interpreter's {@link QonfigInterpreterCore.Builder#getToolkit()
	 * toolkit} will be the one indicated by this interpretation's {@link #getToolkitName() toolkit name} and {@link #getVersion() version}.
	 * 
	 * @param interpreter The builder for an interpreter
	 * @return The builder
	 */
	QonfigInterpreterCore.Builder configureInterpreter(QonfigInterpreterCore.Builder interpreter);
}

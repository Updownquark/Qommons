package org.qommons.osgi;

import java.util.Set;

/**
 * Represents something similar to a dependency service that can parse a dependency structure from a set of component classes.
 * 
 * Used by {@link OsgiBundleSet} with the --start-ds=<classname> argument
 */
public interface ComponentBasedExecutor {
	/**
	 * @param <C> The type of the component
	 * @param componentType The component class
	 * @return Something or null, doesn't matter
	 */
	<C> Object loadComponent(Class<C> componentType);

	/**
	 * Called after all initial components have been {@link #loadComponent(Class) loaded}
	 * 
	 * @param startComponents The names of the set of components to activate initially
	 * @return Something or null, doesn't matter
	 */
	Object loadingComplete(Set<String> startComponents);
}

package org.qommons.osgi;

import java.util.Collections;
import java.util.Map;
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
	 * @param loader The class loader that is loading the component
	 * @param configuration Any configuration given for the component
	 * @return Something or null, doesn't matter
	 */
	<C> Object loadComponent(Class<C> componentType, ClassLoader loader, Map<String, String> configuration);

	/**
	 * @param loader The class loader that is loading the components
	 * @param componentTypes The component classes
	 * @return This executor
	 */
	default ComponentBasedExecutor loadComponents(ClassLoader loader, Class<?>... componentTypes) {
		for (Class<?> componentType : componentTypes)
			loadComponent(componentType, loader, Collections.emptyMap());
		return this;
	}

	/**
	 * Called after all initial components have been {@link #loadComponent(Class, ClassLoader, Map) loaded}
	 * 
	 * @param startComponents The names of the set of components to activate initially
	 * @return Something or null, doesn't matter
	 */
	Object loadingComplete(Set<String> startComponents);

	/** @return The current status to display to the user while loading */
	String getLoadStatus();
}

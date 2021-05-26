package org.qommons.osgi;

import java.util.Set;

public interface ComponentBasedExecutor {
	<C> Object loadComponent(Class<C> componentType);

	Object loadingComplete(Set<String> startComponents);
}

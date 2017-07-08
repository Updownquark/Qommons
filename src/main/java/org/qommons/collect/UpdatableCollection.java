package org.qommons.collect;

import java.util.Collection;

/**
 * A {@link Collection} with an additional {@link #update(Object)} method. The effect of the update is implementation-specific.
 * 
 * @param <E> The type of values in the collection
 * @see UpdatableSet
 */
public interface UpdatableCollection<E> extends Collection<E> {
	/**
	 * @param value The value (same identical object as the one in the collection) that may have changed
	 * @return Whether the value was found in the collection
	 */
	boolean update(E value);
}

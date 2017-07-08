package org.qommons.collect;

import java.util.Set;

/**
 * <p>
 * A {@link Set} with an update method. In addition to other potential implementation-specific functionality, the update method of an
 * updatable set can be used to notify the collection that a value has changed in a way that may have affected its storage mechanism in the
 * collection. For example, its hash code may have changed, or one of the fields by which it is sorted.
 * </p>
 * 
 * <p>
 * An UpdatableSet must be able to find and re-store elements after their storage attributes have changed, e.g. by using an
 * {@link java.util.IdentityHashMap}
 * </p>
 * 
 * @param <E> The type of values in the set
 */
public interface UpdatableSet<E> extends UpdatableCollection<E>, Set<E> {
}

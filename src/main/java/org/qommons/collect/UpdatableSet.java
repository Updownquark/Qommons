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
 * An UpdatableSet must be able to find and re-store elements (by identity, at the least) after their storage attributes have changed, e.g.
 * by using an {@link java.util.IdentityHashMap}. The {@link #remove(Object)} method must also tolerate removal of changed elements.
 * </p>
 * 
 * @param <E> The type of values in the set
 */
public interface UpdatableSet<E> extends Set<E> {
	/** The result of an {@link UpdatableSet#update(Object) update} operation */
	enum ElementUpdateResult {
		/** The value was not found in the set */
		NotFound,
		/** The value was found, but its storage attributes were unchanged, so re-storage/removal was unnecessary */
		NotChanged,
		/** The value was found, its storage attributes were changed, and the value was re-stored in the collection in its proper place */
		ReStored,
		/**
		 * The value was found, its storage attributes were changed, and as a result the value could not be re-stored in the collection
		 * (e.g. because another equivalent value was already present in the set)
		 */
		Removed;
	}
	
	/**
	 * @param value The value that may have changed
	 * @return The result of the update
	 */
	ElementUpdateResult update(E value);
}

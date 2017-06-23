package org.qommons.collect;

import org.qommons.value.Settable;

public interface MutableCollectionElement<T> extends CollectionElement<T>, Settable<T> {
	/** @return null if this element can be removed. Non-null indicates a message describing why removal is prevented. */
	String canRemove();

	/**
	 * Removes this element from the source collection
	 * 
	 * @throws IllegalArgumentException If the element cannot be removed
	 * @see #canRemove()
	 */
	void remove() throws IllegalArgumentException;
}

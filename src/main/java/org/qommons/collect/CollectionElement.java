package org.qommons.collect;

import org.qommons.value.Settable;

/**
 * Represents an element in a collection that contains a value (retrieved via {@link Settable#get()}) that may
 * {@link Settable#isAcceptable(Object) possibly} be {@link Settable#set(Object, Object) replaced} or (again {@link #canRemove() possibly})
 * {@link #remove() removed} during iteration.
 * 
 * @param <T> The type of the value in the element, generally the same as the type of the collection
 */
public interface CollectionElement<T> extends Settable<T> {
	/** @return null if this element can be removed. Non-null indicates a message describing why removal is prevented. */
	String canRemove();

	/**
	 * Removes this element from the source collection
	 * 
	 * @param cause The cause of the removal
	 * @throws UnsupportedOperationException If the element cannot be removed
	 * @see #canRemove()
	 */
	void remove(Object cause) throws UnsupportedOperationException;

	String canAdd(T value, boolean before);
	void add(T value, boolean before, Object cause) throws UnsupportedOperationException, IllegalArgumentException;
}
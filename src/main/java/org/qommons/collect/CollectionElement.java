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
	/** Standard messages returned by this class */
	interface StdMsg {
		static String BAD_TYPE = "Object is the wrong type for this collection";
		static String UNSUPPORTED_OPERATION = "Unsupported Operation";
		static String NULL_DISALLOWED = "Null is not allowed";
		static String ELEMENT_EXISTS = "Element already exists";
		static String GROUP_EXISTS = "Group already exists";
		static String WRONG_GROUP = "Item does not belong to this group";
		static String NOT_FOUND = "No such item found";
		static String ILLEGAL_ELEMENT = "Element is not allowed";
	}
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
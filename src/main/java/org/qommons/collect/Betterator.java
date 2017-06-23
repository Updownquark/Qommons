package org.qommons.collect;

import java.util.Iterator;

/**
 * An {@link Iterator} that supports {@link #set(Object, Object) replacement} and also query methods for whether {@link #remove()} or
 * {@link #set(Object, Object)} are allowed.
 * 
 * @param <E> The type of value in the iterator
 */
public interface Betterator<E> extends Iterator<E> {
	/** @return null if the {@link #remove()} method is currently enabled; otherwise, a message saying why it isn't */
	String canRemove();

	/**
	 * @param value The value to check
	 * @return null if the {@link #set(Object, Object)} method is currently enabled for the given value; otherwise, a message saying why it
	 *         isn't
	 */
	String isAcceptable(E value);

	/**
	 * Replaces the most recently retrieved value from this iterator with the given value
	 * 
	 * @param value The value to set
	 * @param cause The cause of the operation. May be null.
	 * @return The replaced value
	 * @throws IllegalArgumentException If the given value is {@link #isAcceptable(Object) unacceptable}
	 * @throws UnsupportedOperationException If this operation is unsupported
	 */
	E set(E value, Object cause) throws IllegalArgumentException, UnsupportedOperationException;
}

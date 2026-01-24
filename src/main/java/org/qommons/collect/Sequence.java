package org.qommons.collect;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * <p>
 * I have issues with the {@link Iterator} API. Because Iterator requires 2 calls ({@link Iterator#hasNext() hasNext} and then
 * {@link Iterator#next() next}) to iterate, the API is inherently thread-unsafe. Sequence accomplishes the same thing, but without this
 * problem.
 * </p>
 * <p>
 * A Sequence is a view into an ordered collection of values. A Sequence typically (though not always) is created just before the first
 * element of a collection. After the first call to {@link #advance(boolean) advance(true)}, the sequence is positioned on an element in the
 * collection, unless the collection is empty. The value at the sequence's element can be accessed via {@link #get()} any number of times,
 * as this call does not advance the sequence.
 * </p>
 * <p>
 * Typical usage is:<code><br /><br />
 * for(Sequence&lt;E> seq=collection.sequence();sequence.advance(true);){<br />
 * 		&nbsp;&nbsp;&nbsp;&nbsp;E value=seq.get();<br />
 *		&nbsp;&nbsp;&nbsp;&nbsp;//Do something<br />
 * }</code>
 * <p>
 * Sequences can be navigated, similarly to {@link ListIterator}, via the argument to {@link #advance(boolean)}.
 * </p>
 * <p>
 * Sequences can also be used to modify their source collection, similarly to {@link Iterator#remove()}, {@link ListIterator#set(Object)},
 * and {@link ListIterator#add(Object)}, but with more flexibility.
 * </p>
 * 
 * @param <E> The type of values this sequence returns
 */
public interface Sequence<E> extends Supplier<E> {
	/** A message for modification operations on a sequence whose element does not {@link #exists() exist} */
	public static final String NO_ELEMENT_AT_POSTION = "No element at this position";

	/**
	 * @param forward Whether to move the sequence to the next or previous element
	 * @return Whether there was such an element and the operation was successful. If not successful, this sequence is not changed and
	 *         {@link #get()} (and other methods) will behave the same as before this call.
	 */
	boolean advance(boolean forward);

	/**
	 * Shorthand for {@link #advance(boolean) advance(true)}
	 * 
	 * @return Whether there was a next element and the sequence is now positioned on it
	 */
	default boolean next() {
		return advance(true);
	}

	/**
	 * Queries the existence of an adjacent element in the sequence <b>WITHOUT MOVING THE SEQUENCE</b>. Calls to {@link #get()} before and
	 * after this call would return the same value (or throw the same exception).
	 * 
	 * @param next Whether to query regarding the next or previous element in the sequence
	 * @return Whether there is currently another element at the requested position, such that {@link #advance(boolean)} would have returned
	 *         true if it had been called instead.
	 */
	boolean has(boolean next);

	/**
	 * @return Whether this sequence is currently looking at an element (i.e. {@link #get()} will not throw an exception). This will
	 *         typically happen if:
	 *         <ul>
	 *         <li>This sequence is a view into a data source with no values</li>
	 *         <li>The value that this sequence was pointing to does not exist in the data source anymore (e.g. because it was
	 *         {@link #remove() removed}). Some implementations may instead return the removed value, but this should not be relied on.</li>
	 *         </ul>
	 */
	boolean exists();

	/**
	 * @return The current value in the sequence
	 * @throws NoSuchElementException If this sequence's element does not {@link #exists() exist}
	 */
	@Override
	E get() throws NoSuchElementException;

	/**
	 * @return null if, to the best of this sequence's knowledge, the current value in the sequence can be removed, or a reason why it can't
	 */
	String canRemove();

	/**
	 * Removes this element in the sequence from the source collection. The sequence will be advanced to the next element, or the previous
	 * element if this sequence is at its end.
	 * 
	 * @throws UnsupportedOperationException If this sequence is unable to perform the removal
	 * @throws IllegalStateException If this sequence's element does not {@link #exists() exist}
	 */
	void remove() throws UnsupportedOperationException, IllegalStateException;

	/**
	 * @return null if, to the best of this sequence's knowledge, the current value in the sequence can be replaced with another, or a
	 *         reason why it can't
	 */
	String isSettable();

	/**
	 * @param newValue The value to test
	 * @return null if, to the best of this sequence's knowledge, the current value in the sequence can be replaced with the given value, or
	 *         a reason why it can't
	 */
	String isAcceptable(E newValue);

	/**
	 * @param newValue The value to replace the sequence's current element with
	 * @throws UnsupportedOperationException If this sequence is unable to perform the replacement
	 * @throws IllegalArgumentException If the operation cannot be performed due to some quality of the argument
	 * @throws IllegalStateException If this sequence's element does not {@link #exists() exist}
	 */
	void set(E newValue) throws UnsupportedOperationException, IllegalArgumentException, IllegalStateException;

	/**
	 * @param value The value to test
	 * @param before Whether to insert the value before or after the sequence's current element
	 * @return null if, to the best of this sequence's knowledge, the given value can be added to this sequence, or a reason why it can't
	 */
	String canAdd(E value, boolean before);

	/**
	 * <p>
	 * Adds a new value to the sequence.
	 * </p>
	 * <p>
	 * If the sequence's current element {@link #exists() exists}, the value will be before or after it as directed by <code>before</code>.
	 * If it does not exist because the collection is empty or the sequence is in its initialization state at one end of the collection, the
	 * element will be added to the same end of the collection.
	 * </p>
	 * 
	 * @param newValue The value to add
	 * @param before Whether to add the value before or after the current element in the sequence
	 * @throws UnsupportedOperationException If this sequence is unable to perform the addition
	 * @throws IllegalArgumentException If the operation cannot be performed due to some quality of the argument
	 */
	void add(E newValue, boolean before) throws UnsupportedOperationException, IllegalArgumentException;

	/**
	 * @return A sequence at the same position in the same content as this sequence, but which will travel the content in the opposite
	 *         direction of this sequence
	 */
	default Sequence<E> reverse() {
		return new ReversedSequence<>(this);
	}

	/**
	 * @param <E> The type for the sequence
	 * @return The empty sequence
	 */
	public static <E> EmptySequence<E> empty() {
		return (EmptySequence<E>) EMPTY;
	}

	/**
	 * @param <E> The type of the value
	 * @param value The value to iterate over
	 * @param index The initial index for the sequence:
	 *        <ul>
	 *        <li>&lt;0 to start before the value, so {@link Sequence#advance(boolean) advance(true)} returns true</li>
	 *        <li>0 to start on the value, so {@link Sequence#advance(boolean) advance(boolean)} returns false for any argument</li>
	 *        <li>&gt;0 to start before the value, so {@link Sequence#advance(boolean) advance(false)} returns true</li>
	 *        </ul>
	 * @return The singleton sequence
	 */
	public static <E> Sequence<E> single(E value, int index) {
		return new SingletonSequence<>(value, index);
	}

	/**
	 * Simple empty sequence implementation
	 * 
	 * @param <E> The type of the sequence
	 */
	static class EmptySequence<E> implements Sequence<E> {
		@Override
		public E get() {
			throw new IllegalStateException(NO_ELEMENT_AT_POSTION);
		}

		@Override
		public boolean advance(boolean forward) {
			return false;
		}

		@Override
		public boolean has(boolean next) {
			return false;
		}

		@Override
		public boolean exists() {
			return false;
		}

		@Override
		public String canRemove() {
			return NO_ELEMENT_AT_POSTION;
		}

		@Override
		public void remove() throws UnsupportedOperationException, IllegalStateException {
			throw new IllegalStateException(NO_ELEMENT_AT_POSTION);
		}

		@Override
		public String isSettable() {
			return NO_ELEMENT_AT_POSTION;
		}

		@Override
		public String isAcceptable(E newValue) {
			return NO_ELEMENT_AT_POSTION;
		}

		@Override
		public void set(E newValue) throws UnsupportedOperationException, IllegalArgumentException, IllegalStateException {
			throw new IllegalStateException(NO_ELEMENT_AT_POSTION);
		}

		@Override
		public String canAdd(E value, boolean before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public void add(E newValue, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}
	}

	/** Static empty sequence constant */
	public static final EmptySequence<?> EMPTY = new EmptySequence<>();

	/**
	 * An {@link Iterator} implemented on a {@link Sequence}
	 * 
	 * @param <E> The type of values to iterate over
	 */
	static class SequenceIterator<E> implements Iterator<E> {
		private final Sequence<E> theSequence;
		private boolean isOnNext;
		private boolean isInIllegalState;

		/** @param sequence The sequence of values to iterate over */
		public SequenceIterator(Sequence<E> sequence) {
			theSequence = sequence;
			isOnNext = sequence.exists(); // If the sequence is on an element, it's on the next one
			isInIllegalState = true;
		}

		protected Sequence<E> getSequence() {
			return theSequence;
		}

		protected boolean isOnNext() {
			return isOnNext;
		}

		@Override
		public boolean hasNext() {
			if (isOnNext)
				return theSequence.exists();
			else if (theSequence.advance(true)) {
				isOnNext = true;
				return true;
			} else
				return false;
		}

		@Override
		public E next() {
			if (!hasNext())
				throw new NoSuchElementException();
			isOnNext = false;
			isInIllegalState = false;
			return theSequence.get();
		}

		public boolean hasPrevious() {
			if (!isOnNext)
				return theSequence.exists();
			else if (theSequence.advance(false)) {
				isOnNext = false;
				return true;
			} else
				return false;
		}

		public E previous() {
			if (!hasPrevious())
				throw new NoSuchElementException();
			isOnNext = true;
			isInIllegalState = false;
			return theSequence.get();
		}

		@Override
		public void remove() {
			if (isInIllegalState)
				throw new IllegalStateException("Not on an element");
			boolean hasNext = theSequence.has(true);
			theSequence.remove();
			isOnNext = hasNext;
			isInIllegalState = true;
		}

		public void set(E e) {
			if (isInIllegalState)
				throw new IllegalStateException("Not on an element");
			theSequence.set(e);
		}

		public void add(E e) {
			theSequence.add(e, isOnNext);
			if (!isOnNext && hasNext())
				next(); // Jump over the new element
		}

		@Override
		public int hashCode() {
			return theSequence.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (obj instanceof SequenceIterator)
				return theSequence.equals(((SequenceIterator<?>) obj).theSequence);
			else
				return false;
		}

		@Override
		public String toString() {
			return theSequence.toString();
		}
	}

	/**
	 * Implement {@link Sequence#single(Object, int)}
	 * 
	 * @param <E> The type of the value in the sequence
	 */
	class SingletonSequence<E> implements Sequence<E> {
		private final E theValue;
		private int theIndex;

		public SingletonSequence(E value, int index) {
			theValue = value;
			theIndex = index;
		}

		@Override
		public boolean exists() {
			return theIndex == 0;
		}

		@Override
		public E get() {
			if (theIndex != 0)
				throw new IllegalStateException(NO_ELEMENT_AT_POSTION);
			return theValue;
		}

		@Override
		public boolean advance(boolean forward) {
			if (forward) {
				if (theIndex < 0) {
					theIndex = 0;
					return true;
				} else
					return false;
			} else {
				if (theIndex > 0) {
					theIndex = 0;
					return true;
				} else
					return false;
			}
		}

		@Override
		public boolean has(boolean next) {
			if (next)
				return theIndex < 0;
			else
				return theIndex > 0;
		}

		public int getIndex() throws IllegalStateException {
			if (theIndex != 0)
				throw new IllegalStateException(NO_ELEMENT_AT_POSTION);
			return 0;
		}

		@Override
		public String canRemove() {
			if (theIndex != 0)
				return NO_ELEMENT_AT_POSTION;
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public void remove() throws UnsupportedOperationException, IllegalStateException {
			if (theIndex != 0)
				throw new IllegalStateException(NO_ELEMENT_AT_POSTION);
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String isSettable() {
			if (theIndex != 0)
				return NO_ELEMENT_AT_POSTION;
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public String isAcceptable(E newValue) {
			if (theIndex != 0)
				return NO_ELEMENT_AT_POSTION;
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public void set(E newValue) throws UnsupportedOperationException, IllegalArgumentException, IllegalStateException {
			if (theIndex != 0)
				throw new IllegalStateException(NO_ELEMENT_AT_POSTION);
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canAdd(E value, boolean before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public void add(E newValue, boolean before) throws UnsupportedOperationException, IllegalArgumentException, IllegalStateException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}
	}

	/**
	 * Default {@link Sequence#reverse()} implementation
	 * 
	 * @param <E> The type of values in the sequence
	 */
	class ReversedSequence<E> implements Sequence<E> {
		private final Sequence<E> theWrapped;

		public ReversedSequence(Sequence<E> wrap) {
			theWrapped = wrap;
		}

		protected Sequence<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public boolean advance(boolean forward) {
			return theWrapped.advance(!forward);
		}

		@Override
		public boolean has(boolean next) {
			return theWrapped.has(!next);
		}

		@Override
		public boolean exists() {
			return theWrapped.exists();
		}

		@Override
		public E get() throws NoSuchElementException {
			return theWrapped.get();
		}

		@Override
		public String canRemove() {
			return theWrapped.canRemove();
		}

		@Override
		public void remove() throws UnsupportedOperationException, IllegalStateException {
			theWrapped.remove();
		}

		@Override
		public String isSettable() {
			return theWrapped.isSettable();
		}

		@Override
		public String isAcceptable(E newValue) {
			return theWrapped.isAcceptable(newValue);
		}

		@Override
		public void set(E newValue) throws UnsupportedOperationException, IllegalArgumentException, IllegalStateException {
			theWrapped.set(newValue);
		}

		@Override
		public String canAdd(E value, boolean before) {
			return theWrapped.canAdd(value, !before);
		}

		@Override
		public void add(E newValue, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
			theWrapped.add(newValue, !before);
		}

		@Override
		public Sequence<E> reverse() {
			return theWrapped;
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ReversedSequence && theWrapped.equals(((ReversedSequence<?>) obj).theWrapped);
		}
	}
}

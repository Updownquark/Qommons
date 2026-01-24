package org.qommons.collect;

import java.io.Serializable;
import java.util.*;

import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * A simple, immutable, fast-as-possible list. This is essentially the same as the result of {@link Arrays#asList(Object...)}, except that
 * that class allows elements in the list to be set.
 * 
 * @param <E> The type of values in the list
 */
public class SimpleImmutableList<E> extends AbstractList<E> implements DequeList<E>, Serializable, RandomAccess, Cloneable {
	final Object[] theValues; // Package-private so the iterators can access directly without synthetic accessors

	/** @param values The values for the list */
	public SimpleImmutableList(E[] values) {
		theValues = values;
	}

	/**
	 * @param values The array containing values for the list
	 * @param off The offset in the array for the first value for this list
	 * @param length The number of objects in the array to put in the list
	 */
	public SimpleImmutableList(E[] values, int off, int length) {
		if (off == 0 && length == values.length)
			theValues = values;
		else {
			theValues = new Object[length];
			System.arraycopy(values, off, theValues, 0, length);
		}
	}

	/** @param values The values for the list */
	public SimpleImmutableList(Collection<? extends E> values) {
		theValues = new Object[values.size()];
		values.toArray(theValues);
	}

	@Override
	public int size() {
		return theValues.length;
	}

	@Override
	public E get(int index) {
		return (E) theValues[index];
	}

	@Override
	public Spliterator<E> spliterator() {
		return Spliterators.spliterator(theValues, Spliterator.ORDERED);
	}

	@Override
	public Object[] toArray() {
		return toArray(new Object[theValues.length]);
	}

	@Override
	public <T> T[] toArray(T[] a) {
		if (a.length < theValues.length)
			a = Arrays.copyOf(a, theValues.length);
		System.arraycopy(theValues, 0, a, 0, theValues.length);
		return a;
	}

	@Override
	public boolean offerFirst(E e) {
		return false;
	}

	@Override
	public boolean offer(E e) {
		return false;
	}

	@Override
	public E pollFirst() {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public E pollLast() {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public E peekFirst() {
		if (theValues.length == 0)
			return null;
		return (E) theValues[0];
	}

	@Override
	public E peekLast() {
		if (theValues.length == 0)
			return null;
		return (E) theValues[theValues.length - 1];
	}

	@Override
	public boolean removeLastOccurrence(Object o) {
		return false;
	}

	@Override
	public void removeRange(int fromIndex, int toIndex) {
		if (fromIndex != toIndex)
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public long getStamp() {
		return 0;
	}

	@Override
	public ListSequence<E> sequence(int start, int end, int position, boolean forward) {
		return new IndexedSequence<>(this, start, end, position, forward);
	}

	@Override
	public boolean containsAny(Collection<?> c) {
		for (Object v : theValues) {
			if (c.contains(v))
				return true;
		}
		return false;
	}

	@Override
	public DequeList<E> subList(int fromIndex, int toIndex) {
		return new SubList<>(this, fromIndex, toIndex);
	}

	@Override
	public SimpleImmutableList<E> clone() {
		return this; // Immutable, so no need to create a copy
	}

	private static class SubList<E> extends DequeList.AbstractSubDequeList<E> {
		SubList(DequeList<E> list, int start, int end) {
			super(list, null, start, end);
		}

		@Override
		protected SimpleImmutableList<E> getRoot() {
			return (SimpleImmutableList<E>) super.getRoot();
		}

		@Override
		public ListIterator<E> iterator(int start, int end, int next, boolean forward) {
			if (start < 0 || end > size() || start > end)
				throw new IndexOutOfBoundsException(start + " to " + end + " of " + size());
			return getRoot().iterator(getStart() + start, getStart() + end, getStart() + next, forward);
		}

		@Override
		public DequeList<E> subList(int fromIndex, int toIndex) {
			if (fromIndex < 0 || toIndex > size() || fromIndex > toIndex)
				throw new IndexOutOfBoundsException(fromIndex + " to " + toIndex + " of " + size());
			return new SubList<>(getRoot(), getStart() + fromIndex, getStart() + toIndex);
		}

		@Override
		public boolean offerFirst(E e) {
			return false;
		}

		@Override
		public boolean offer(E e) {
			return false;
		}

		@Override
		public <T> T[] toArray(T[] a) {
			if (a.length < size())
				a = Arrays.copyOf(a, size());
			System.arraycopy(getRoot().theValues, getStart(), a, 0, size());
			return a;
		}
	}
}

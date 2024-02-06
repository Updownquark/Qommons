package org.qommons.collect;

import java.io.Serializable;
import java.util.*;
import java.util.function.Consumer;

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
	 * @param values The array contining values for the list
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
	public Iterator<E> iterator() {
		return new Itr();
	}

	@Override
	public ListIterator<E> listIterator() {
		return listIterator(0);
	}

	@Override
	public ListIterator<E> listIterator(int index) {
		return new ListItr(0, theValues.length, index);
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
		else
			System.arraycopy(theValues, 0, a, 0, theValues.length);
		return a;
	}

	@Override
	public boolean offerFirst(E e) {
		return false;
	}

	@Override
	public boolean offerLast(E e) {
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
	public ListIterator<E> iterator(int start, int end, int next, boolean forward) {
		if (forward)
			return new ListItr(start, end, next);
		else
			return new ReversedListItr(start, end, next);
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

	/** Copied from ArrayList and trimmed down for immutability */
	private class Itr implements Iterator<E> {
		private int cursor; // index of next element to return

		// prevent creating a synthetic constructor
		Itr() {
		}

		@Override
		public boolean hasNext() {
			return cursor != theValues.length;
		}

		@Override
		public E next() {
			int i = cursor;
			if (i >= theValues.length)
				throw new NoSuchElementException();
			cursor = i + 1;
			return (E) theValues[i];
		}

		@Override
		public void forEachRemaining(Consumer<? super E> action) {
			Objects.requireNonNull(action);
			final int size = theValues.length;
			int i = cursor;
			if (i < size) {
				for (; i < size; i++)
					action.accept((E) theValues[i]);
				// update once at end to reduce heap write traffic
				cursor = i;
			}
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	private abstract class AbstractListItr implements ListIterator<E> {
		private final int offset;
		private final int limit;
		private int cursor;

		AbstractListItr(int offset, int limit, int index) {
			this.offset = offset;
			this.limit = limit;
			cursor = index;
		}

		protected int itrSize() {
			return limit - offset;
		}

		protected boolean hasForward() {
			return cursor != limit;
		}

		public E forward() {
			int i = cursor;
			if (i >= limit)
				throw new NoSuchElementException();
			cursor = i + 1;
			return (E) theValues[i];
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

		protected boolean hasBackward() {
			return cursor != offset;
		}

		protected int forwardIndex() {
			return cursor - offset;
		}

		protected int backwardIndex() {
			return cursor - offset - 1;
		}

		protected E backward() {
			int i = cursor - 1;
			if (i < offset)
				throw new NoSuchElementException();
			cursor = i;
			return (E) theValues[i];
		}

		@Override
		public void set(E e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void add(E e) {
			throw new UnsupportedOperationException();
		}
	}

	private class ListItr extends AbstractListItr {
		ListItr(int offset, int limit, int index) {
			super(offset, limit, index);
		}

		@Override
		public boolean hasNext() {
			return hasForward();
		}

		@Override
		public E next() {
			return forward();
		}

		@Override
		public boolean hasPrevious() {
			return hasBackward();
		}

		@Override
		public int nextIndex() {
			return forwardIndex();
		}

		@Override
		public int previousIndex() {
			return backwardIndex();
		}

		@Override
		public E previous() {
			return backward();
		}
	}

	private class ReversedListItr extends AbstractListItr {
		ReversedListItr(int offset, int limit, int index) {
			super(offset, limit, index);
		}

		@Override
		public boolean hasNext() {
			return hasBackward();
		}

		@Override
		public E next() {
			return backward();
		}

		@Override
		public boolean hasPrevious() {
			return hasForward();
		}

		@Override
		public int nextIndex() {
			return itrSize() - backwardIndex();
		}

		@Override
		public int previousIndex() {
			return itrSize() - forwardIndex();
		}

		@Override
		public E previous() {
			return forward();
		}
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
		public boolean offerLast(E e) {
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

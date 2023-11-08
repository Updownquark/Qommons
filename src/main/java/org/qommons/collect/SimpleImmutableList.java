package org.qommons.collect;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.Consumer;

/**
 * A simple, immutable, fast-as-possible list
 * 
 * @param <E> The type of values in the list
 */
public class SimpleImmutableList<E> extends AbstractList<E> implements Serializable, RandomAccess, Cloneable {
	final Object[] theValues;

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
		return new ListItr(0);
	}

	@Override
	public ListIterator<E> listIterator(int index) {
		return new ListItr(index);
	}

	@Override
	public Object[] toArray() {
		return toArray(new Object[theValues.length]);
	}

	@Override
	public <T> T[] toArray(T[] a) {
		if (a.length < theValues.length)
			a = (T[]) Array.newInstance(a.getClass().getComponentType(), theValues.length);
		System.arraycopy(theValues, 0, a, 0, theValues.length);
		return a;
	}

	@Override
	public SimpleImmutableList<E> clone() {
		return this; // Immutable, so no need to create a copy
	}

	/** Copied from ArrayList and trimmed down for immutability */
	private class Itr implements Iterator<E> {
		int cursor; // index of next element to return

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

	/** Copied from ArrayList and trimmed down for immutability */
	private class ListItr extends Itr implements ListIterator<E> {
		ListItr(int index) {
			super();
			cursor = index;
		}

		@Override
		public boolean hasPrevious() {
			return cursor != 0;
		}

		@Override
		public int nextIndex() {
			return cursor;
		}

		@Override
		public int previousIndex() {
			return cursor - 1;
		}

		@Override
		public E previous() {
			int i = cursor - 1;
			if (i < 0)
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
}

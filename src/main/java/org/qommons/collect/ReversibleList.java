package org.qommons.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;

/**
 * A {@link List} that is also a {@link ReversibleCollection}
 * 
 * @param <E> The type of value in the list
 */
public interface ReversibleList<E> extends ReversibleCollection<E>, List<E> {
	/**
	 * Removes the last occurrence of the given value in this collection, if it exists
	 * 
	 * @param o The value to remove
	 * @return Whether the value was found (and removed)
	 */
	boolean removeLast(Object o);

	@Override
	default Betterator<E> iterator() {
		return ReversibleCollection.super.iterator();
	}

	@Override
	ReversibleSpliterator<E> spliterator();

	@Override
	default ReversibleList<E> reverse() {
		return new ReversedList<>(this);
	}

	@Override
	ReversibleList<E> subList(int fromIndex, int toIndex);

	/**
	 * Implements {@link ReversibleList#reverse()}
	 *
	 * @param <E> The type of elements in the list
	 */
	class ReversedList<E> extends ReversedCollection<E> implements ReversibleList<E> {
		protected ReversedList(ReversibleList<E> wrap) {
			super(wrap);
		}

		@Override
		protected ReversibleList<E> getWrapped() {
			return (ReversibleList<E>) super.getWrapped();
		}

		@Override
		public ReversibleList<E> reverse() {
			return getWrapped();
		}

		@Override
		public boolean add(E e) {
			getWrapped().add(0, e);
			return true;
		}

		@Override
		public boolean remove(Object o) {
			return getWrapped().removeLast(o);
		}

		@Override
		public boolean removeLast(Object o) {
			return getWrapped().remove(o);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return getWrapped().addAll(0, reverse(c));
		}

		private static <T> Collection<T> reverse(Collection<T> coll) {
			List<T> copy = new ArrayList<>(coll);
			java.util.Collections.reverse(copy);
			return copy;
		}

		private int reflect(int index, boolean add) {
			int size = getWrapped().size();
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			if (index > size || (!add && index == size))
				throw new IndexOutOfBoundsException(index + " of " + size);
			return size - index - 1;
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			return getWrapped().addAll(reflect(index, true), reverse(c));
		}

		@Override
		public E get(int index) {
			return getWrapped().get(reflect(index, false));
		}

		@Override
		public E set(int index, E element) {
			return getWrapped().set(reflect(index, false), element);
		}

		@Override
		public void add(int index, E element) {
			getWrapped().add(reflect(index, true), element);
		}

		@Override
		public E remove(int index) {
			return getWrapped().remove(reflect(index, false));
		}

		@Override
		public int indexOf(Object o) {
			return getWrapped().lastIndexOf(o);
		}

		@Override
		public int lastIndexOf(Object o) {
			return getWrapped().indexOf(o);
		}

		@Override
		public ListIterator<E> listIterator() {
			return listIterator(0);
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			int size = getWrapped().size();
			if (index > size)
				throw new IndexOutOfBoundsException(index + " of " + size);
			ListIterator<E> wrap = getWrapped().listIterator(size - index);
			return new ListIterator<E>() {
				@Override
				public boolean hasNext() {
					return wrap.hasPrevious();
				}

				@Override
				public E next() {
					return wrap.previous();
				}

				@Override
				public boolean hasPrevious() {
					return wrap.hasNext();
				}

				@Override
				public E previous() {
					return wrap.next();
				}

				@Override
				public int nextIndex() {
					return reflect(wrap.previousIndex(), false);
				}

				@Override
				public int previousIndex() {
					return reflect(wrap.nextIndex(), true);
				}

				@Override
				public void remove() {
					wrap.remove();
				}

				@Override
				public void set(E e) {
					wrap.set(e);
				}

				@Override
				public void add(E e) {
					wrap.add(e);
					wrap.previous();
				}
			};
		}

		@Override
		public ReversibleList<E> subList(int fromIndex, int toIndex) {
			if (fromIndex < 0)
				throw new IndexOutOfBoundsException("" + fromIndex);
			if (fromIndex > toIndex)
				throw new IndexOutOfBoundsException(fromIndex + ">" + toIndex);
			int size = getWrapped().size();
			if (toIndex > size)
				throw new IndexOutOfBoundsException(toIndex + " of " + size);
			return getWrapped().subList(size - toIndex, size - fromIndex).reverse();
		}
	}
}

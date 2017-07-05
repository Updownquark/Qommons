package org.qommons.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Supplier;

import com.google.common.reflect.TypeToken;

/**
 * A {@link List} that is also a {@link ReversibleCollection}
 * 
 * @param <E> The type of value in the list
 */
public interface ReversibleList<E> extends ReversibleCollection<E>, RRList<E> {
	@Override
	default Betterator<E> iterator() {
		return ReversibleCollection.super.iterator();
	}

	@Override
	default ReversibleElementSpliterator<E> spliterator() {
		return spliterator(0);
	}

	ReversibleElementSpliterator<E> spliterator(int index);

	@Override
	default ReversibleList<E> reverse() {
		return new ReversedList<>(this);
	}

	@Override
	ReversibleList<E> subList(int fromIndex, int toIndex);

	/**
	 * @param <E> The type of the list
	 * @return An empty reversible list
	 */
	public static <E> ReversibleList<E> empty() {
		class EmptyReversibleList implements ReversibleList<E> {
			@Override
			public boolean removeLast(Object o) {
				return false;
			}

			@Override
			public ReversibleElementSpliterator<E> spliterator(boolean fromStart) {
				return ReversibleElementSpliterator.empty((TypeToken<E>) TypeToken.of(Object.class));
			}

			@Override
			public boolean containsAny(Collection<?> c) {
				return false;
			}

			@Override
			public int size() {
				return 0;
			}

			@Override
			public boolean isEmpty() {
				return true;
			}

			@Override
			public boolean contains(Object o) {
				return false;
			}

			@Override
			public Object[] toArray() {
				return new Object[0];
			}

			@Override
			public <T> T[] toArray(T[] a) {
				return a;
			}

			@Override
			public boolean add(E e) {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean remove(Object o) {
				return false;
			}

			@Override
			public boolean containsAll(Collection<?> c) {
				return c.isEmpty();
			}

			@Override
			public boolean addAll(Collection<? extends E> c) {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean removeAll(Collection<?> c) {
				return false;
			}

			@Override
			public boolean retainAll(Collection<?> c) {
				return false;
			}

			@Override
			public void clear() {}

			@Override
			public boolean addAll(int index, Collection<? extends E> c) {
				throw new UnsupportedOperationException();
			}

			@Override
			public E get(int index) {
				throw new IndexOutOfBoundsException(index + " of 0");
			}

			@Override
			public E set(int index, E element) {
				throw new IndexOutOfBoundsException(index + " of 0");
			}

			@Override
			public void add(int index, E element) {
				throw new UnsupportedOperationException();
			}

			@Override
			public E remove(int index) {
				throw new IndexOutOfBoundsException(index + " of 0");
			}

			@Override
			public int indexOf(Object o) {
				return -1;
			}

			@Override
			public int lastIndexOf(Object o) {
				return -1;
			}

			@Override
			public ListIterator<E> listIterator(int index) {
				return Collections.<E> emptyList().listIterator(index);
			}

			@Override
			public ReversibleElementSpliterator<E> spliterator(int index) {
				if (index != 0)
					throw new IndexOutOfBoundsException(index + " of 0");
				return spliterator();
			}

			@Override
			public ReversibleList<E> subList(int fromIndex, int toIndex) {
				if (fromIndex != 0)
					throw new IndexOutOfBoundsException(fromIndex + " of 0");
				if (toIndex != 0)
					throw new IndexOutOfBoundsException(toIndex + " of 0");
				return this;
			}
		}
		return new EmptyReversibleList();
	}

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
		public ReversibleElementSpliterator<E> spliterator(boolean fromStart) {
			return getWrapped().spliterator(!fromStart).reverse();
		}

		@Override
		public ReversibleElementSpliterator<E> spliterator(int index) {
			return getWrapped().spliterator(reflect(index, true));
		}

		@Override
		public boolean add(E e) {
			getWrapped().add(0, e);
			return true;
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

		private int reflect(int index, boolean terminalInclusive) {
			int size = getWrapped().size();
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			if (index > size || (!terminalInclusive && index == size))
				throw new IndexOutOfBoundsException(index + " of " + size);
			int reflected = size - index;
			if (!terminalInclusive)
				reflected--;
			return reflected;
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
		public void removeRange(int fromIndex, int toIndex) {
			getWrapped().removeRange(reflect(toIndex, true), reflect(fromIndex, true));
		}

		@Override
		public int indexOf(Object o) {
			return reflect(getWrapped().lastIndexOf(o), false);
		}

		@Override
		public int lastIndexOf(Object o) {
			return reflect(getWrapped().indexOf(o), false);
		}

		@Override
		public ListIterator<E> listIterator() {
			return listIterator(0);
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			return new ReversedListIterator<>(getWrapped().listIterator(reflect(index, true)), () -> getWrapped().size());
		}

		@Override
		public ReversibleList<E> subList(int fromIndex, int toIndex) {
			if (fromIndex < 0)
				throw new IndexOutOfBoundsException("" + fromIndex);
			if (fromIndex > toIndex)
				throw new IndexOutOfBoundsException(fromIndex + ">" + toIndex);
			return getWrapped().subList(reflect(toIndex, true), reflect(fromIndex, true)).reverse();
		}
	}

	/**
	 * A list iterator that is the reverse for another list iterator
	 * 
	 * @param <E> The type of values supplied by the iterator
	 */
	class ReversedListIterator<E> implements ListIterator<E> {
		private final ListIterator<E> theWrapped;
		private final Supplier<Integer> theSize;

		/**
		 * @param wrapped The list iterator to reverse
		 * @param size A supplier for the size of the collection that the wrapped iterator is for--needed for indexes
		 */
		public ReversedListIterator(ListIterator<E> wrapped, Supplier<Integer> size) {
			theWrapped = wrapped;
			theSize = size;
		}

		@Override
		public boolean hasNext() {
			return theWrapped.hasPrevious();
		}

		@Override
		public E next() {
			return theWrapped.previous();
		}

		@Override
		public boolean hasPrevious() {
			return theWrapped.hasNext();
		}

		@Override
		public E previous() {
			return theWrapped.next();
		}

		@Override
		public int nextIndex() {
			int pi = theWrapped.previousIndex();
			return theSize.get() - pi - 1;
		}

		@Override
		public int previousIndex() {
			return nextIndex() - 1;
		}

		@Override
		public void remove() {
			theWrapped.remove();
		}

		@Override
		public void set(E e) {
			theWrapped.set(e);
		}

		@Override
		public void add(E e) {
			theWrapped.add(e);
			theWrapped.previous();
		}
	}

}

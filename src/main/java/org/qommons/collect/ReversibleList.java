package org.qommons.collect;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

/**
 * A {@link List} that is also a {@link ReversibleCollection}
 * 
 * @param <E> The type of value in the list
 */
public interface ReversibleList<E> extends ReversibleCollection<E>, RRList<E> {
	ReversibleSpliterator<E> spliterator(int index);

	ReversibleElementSpliterator<E> mutableSpliterator(int index);

	@Override
	default boolean contains(Object o) {
		return ReversibleCollection.super.contains(o);
	}

	@Override
	default boolean containsAny(Collection<?> c) {
		return ReversibleCollection.super.containsAny(c);
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		return ReversibleCollection.super.containsAll(c);
	}

	@Override
	default boolean remove(Object o) {
		return ReversibleCollection.super.remove(o);
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		return ReversibleCollection.super.removeAll(c);
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		return ReversibleCollection.super.retainAll(c);
	}

	@Override
	default ReversibleList<E> reverse() {
		return new ReversedList<>(this);
	}

	@Override
	default ReversibleList<E> subList(int fromIndex, int toIndex) {
		return new ReversibleSubList<>(this, fromIndex, toIndex);
	}

	@Override
	default ReversibleSpliterator<E> spliterator() {
		return ReversibleCollection.super.spliterator();
	}

	@Override
	default ImmutableIterator<E> iterator() {
		return ReversibleCollection.super.iterator();
	}

	/**
	 * @param <E> The type of the list
	 * @param type The type of the list
	 * @return An empty reversible list
	 */
	public static <E> ReversibleList<E> empty(TypeToken<E> type) {
		class EmptyReversibleList implements ReversibleList<E> {
			@Override
			public boolean removeLast(Object o) {
				return false;
			}

			@Override
			public ReversibleSpliterator<E> spliterator(boolean fromStart) {
				return ReversibleSpliterator.empty();
			}

			@Override
			public ReversibleSpliterator<E> spliterator(int index) {
				if (index != 0)
					throw new IndexOutOfBoundsException(index + " of 0");
				return ReversibleSpliterator.empty();
			}

			@Override
			public ReversibleElementSpliterator<E> mutableSpliterator(boolean fromStart) {
				return ReversibleElementSpliterator.empty(type);
			}

			@Override
			public ReversibleElementSpliterator<E> mutableSpliterator(int index) {
				if (index != 0)
					throw new IndexOutOfBoundsException(index + " of 0");
				return ReversibleElementSpliterator.empty(type);
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
			public ReversibleList<E> subList(int fromIndex, int toIndex) {
				if (fromIndex != 0)
					throw new IndexOutOfBoundsException(fromIndex + " of 0");
				if (toIndex != 0)
					throw new IndexOutOfBoundsException(toIndex + " of 0");
				return this;
			}

			@Override
			public boolean forElement(E value, Consumer<? super MutableElementHandle<? extends E>> onElement, boolean first) {
				return false;
			}

			@Override
			public boolean belongs(Object o) {
				return false;
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
		public ReversibleSpliterator<E> spliterator(boolean fromStart) {
			return getWrapped().spliterator(!fromStart).reverse();
		}

		@Override
		public ReversibleSpliterator<E> spliterator(int index) {
			return getWrapped().spliterator(reflect(index, true));
		}

		@Override
		public ReversibleElementSpliterator<E> mutableSpliterator(boolean fromStart) {
			return getWrapped().mutableSpliterator(!fromStart).reverse();
		}

		@Override
		public ReversibleElementSpliterator<E> mutableSpliterator(int index) {
			return getWrapped().mutableSpliterator(reflect(index, true));
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

	class ReversibleSubList<E> implements ReversibleList<E> {
		private final ReversibleList<E> theWrapped;
		private int theStart;
		private int theEnd;

		public ReversibleSubList(ReversibleList<E> wrapped, int start, int end) {
			if (end > wrapped.size())
				throw new IndexOutOfBoundsException(end + " of " + wrapped.size());
			if (start < 0)
				throw new IndexOutOfBoundsException("" + start);
			if (start > end)
				throw new IndexOutOfBoundsException(start + ">" + end);
			theWrapped = wrapped;
			theStart = start;
			theEnd = end;
		}

		@Override
		public boolean belongs(Object o) {
			return theWrapped.belongs(o);
		}

		@Override
		public int size() {
			int sz = theWrapped.size();
			if (sz < theStart)
				return 0;
			return Math.min(sz, theEnd) - theStart;
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.size() <= theStart;
		}

		@Override
		public Object[] toArray() {
			Object[] array = new Object[size()];
			for (int i = 0; i < array.length; i++)
				array[i] = get(i);
			return array;
		}

		@Override
		public <T> T[] toArray(T[] a) {
			T[] array = a.length >= size() ? a : (T[]) Array.newInstance(a.getClass().getComponentType(), size());
			for (int i = 0; i < array.length; i++)
				array[i] = (T) get(i);
			return array;
		}

		@Override
		public boolean forElement(E value, Consumer<? super MutableElementHandle<? extends E>> onElement, boolean first) {
			if (!belongs(value))
				return false;
			boolean[] success = new boolean[1];
			MutableElementSpliterator<E> spliter = first ? mutableSpliterator(true) : mutableSpliterator(false).reverse();
			while (!success[0] && spliter.tryAdvanceElement(el -> {
				if (Objects.equals(el.get(), value)) {
					onElement.accept(wrapElement(el));
					success[0] = true;
				}
			})) {
			}
			return success[0];
		}

		protected MutableElementHandle<E> wrapElement(MutableElementHandle<E> el) {
			return new MutableElementHandle<E>() {
				@Override
				public TypeToken<E> getType() {
					return el.getType();
				}

				@Override
				public <V extends E> E set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
					return el.set(value, cause);
				}

				@Override
				public <V extends E> String isAcceptable(V value) {
					return el.isAcceptable(value);
				}

				@Override
				public Value<String> isEnabled() {
					return el.isEnabled();
				}

				@Override
				public E get() {
					return el.get();
				}

				@Override
				public String canRemove() {
					return el.canRemove();
				}

				@Override
				public void remove(Object cause) throws UnsupportedOperationException {
					el.remove(cause);
					theEnd--;
				}

				@Override
				public String canAdd(E value, boolean before) {
					return el.canAdd(value, before);
				}

				@Override
				public void add(E value, boolean before, Object cause) throws UnsupportedOperationException, IllegalArgumentException {
					el.add(value, before, cause);
					theEnd++;
				}
			};
		}

		@Override
		public ReversibleElementSpliterator<E> mutableSpliterator(boolean fromStart) {
			int index = fromStart ? theStart : theEnd;
			return new SubSpliterator(theWrapped.mutableSpliterator(index), index);
		}

		@Override
		public ReversibleSpliterator<E> spliterator(int index) {
			return mutableSpliterator(index).immutable();
		}

		@Override
		public ReversibleElementSpliterator<E> mutableSpliterator(int index) {
			return new SubSpliterator(theWrapped.mutableSpliterator(theStart + checkIndex(index, true)), theStart + index);
		}

		class SubSpliterator implements ReversibleElementSpliterator<E> {
			private final ReversibleElementSpliterator<E> wrapSpliter;
			private int spliterIndex;

			SubSpliterator(ReversibleElementSpliterator<E> spliter, int position) {
				wrapSpliter = spliter;
				spliterIndex = position;
			}

			@Override
			public TypeToken<E> getType() {
				return wrapSpliter.getType();
			}

			@Override
			public boolean tryAdvanceElement(Consumer<? super MutableElementHandle<E>> action) {
				if (spliterIndex >= theEnd)
					return false;
				if (wrapSpliter.tryAdvanceElement(el -> {
					action.accept(wrapElement(el));
				})) {
					spliterIndex++;
					return true;
				}
				return false;
			}

			@Override
			public long estimateSize() {
				return size();
			}

			@Override
			public long getExactSizeIfKnown() {
				return size();
			}

			@Override
			public int characteristics() {
				return wrapSpliter.characteristics();
			}

			@Override
			public boolean tryReverseElement(Consumer<? super MutableElementHandle<E>> action) {
				if (spliterIndex <= theStart)
					return false;
				if (wrapSpliter.tryReverseElement(el -> {
					action.accept(wrapElement(el));
				})) {
					spliterIndex--;
					return true;
				}
				return false;
			}

			@Override
			public ReversibleElementSpliterator<E> trySplit() {
				return null;
			}
		}

		private int checkIndex(int index, boolean includeTerminus) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			if (index > theEnd - theStart || (index == theEnd - theStart && !includeTerminus))
				throw new IndexOutOfBoundsException(index + " of " + (theEnd - theStart));
			return index;
		}

		@Override
		public E get(int index) {
			return theWrapped.get(checkIndex(index, false) + theStart);
		}

		@Override
		public boolean add(E e) {
			theWrapped.add(theEnd, e);
			theEnd++;
			return true;
		}

		@Override
		public void add(int index, E element) {
			theWrapped.add(theStart + checkIndex(index, true), element);
			theEnd++;
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			int preSize = theWrapped.size();
			if (!theWrapped.addAll(theEnd, c))
				return false;
			theEnd += theWrapped.size() - preSize;
			return true;
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			int preSize = theWrapped.size();
			if (!theWrapped.addAll(theStart + checkIndex(index, true), c))
				return false;
			theEnd += theWrapped.size() - preSize;
			return true;
		}

		@Override
		public void clear() {
			int sz = theWrapped.size();
			if (sz <= theStart)
				return;
			int end = theEnd;
			if (sz < end)
				end = sz;
			theWrapped.removeRange(theStart, end);
			theEnd = theStart;
		}

		@Override
		public E set(int index, E element) {
			return theWrapped.set(theStart + checkIndex(index, false), element);
		}

		@Override
		public E remove(int index) {
			E old = theWrapped.remove(theStart + checkIndex(index, false));
			theEnd--;
			return old;
		}

		@Override
		public int indexOf(Object o) {
			if (!belongs(o))
				return -1;
			int[] res = new int[] { -1 };
			Spliterator<E> spliter = spliterator(true);
			int[] index = new int[1];
			while (res[0] < 0 && spliter.tryAdvance(v -> {
				if (Objects.equals(v, o))
					res[0] = index[0];
				index[0]++;
			})) {
			}
			return res[0];
		}

		@Override
		public int lastIndexOf(Object o) {
			if (!belongs(o))
				return -1;
			int[] res = new int[] { -1 };
			Spliterator<E> spliter = spliterator(false).reverse();
			int[] index = new int[1];
			while (res[0] < 0 && spliter.tryAdvance(v -> {
				if (Objects.equals(v, o))
					res[0] = index[0];
				index[0]++;
			})) {
			}
			return res[0];
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			return new ListIterator<E>() {
				private final ListIterator<E> wrapIter = theWrapped.listIterator(theStart + checkIndex(index, true));

				@Override
				public boolean hasNext() {
					return wrapIter.hasNext() && wrapIter.nextIndex() < theEnd;
				}

				@Override
				public E next() {
					if (wrapIter.nextIndex() >= theEnd)
						throw new NoSuchElementException();
					return wrapIter.next();
				}

				@Override
				public boolean hasPrevious() {
					return wrapIter.hasPrevious() && wrapIter.previousIndex() >= theStart;
				}

				@Override
				public E previous() {
					if (wrapIter.previousIndex() < theStart)
						throw new NoSuchElementException();
					return wrapIter.previous();
				}

				@Override
				public int nextIndex() {
					return wrapIter.nextIndex() - theStart;
				}

				@Override
				public int previousIndex() {
					return wrapIter.previousIndex() - theStart;
				}

				@Override
				public void remove() {
					wrapIter.remove();
				}

				@Override
				public void set(E e) {
					wrapIter.set(e);
				}

				@Override
				public void add(E e) {
					wrapIter.add(e);
					theEnd++;
				}
			};
		}
	}
}

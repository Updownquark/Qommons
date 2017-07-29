package org.qommons.collect;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.qommons.Ternian;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * A {@link List} that is also a {@link BetterCollection}
 * 
 * @param <E> The type of value in the list
 */
public interface BetterList<E> extends BetterCollection<E>, TransactableList<E> {
	CollectionElement<E> getElement(int index);
	default ElementSpliterator<E> spliterator(int index) {
		return mutableSpliterator(index).immutable();
	}

	default MutableElementSpliterator<E> mutableSpliterator(int index) {
		return mutableSpliterator(getElement(index), true);
	}

	@Override
	default Object[] toArray() {
		return BetterCollection.super.toArray();
	}

	/**
	 * @param id The element
	 * @return The number of elements in this collection positioned before the given element
	 */
	int getElementsBefore(CollectionElement<E> id);

	/**
	 * @param id The element
	 * @return The number of elements in this collection positioned after the given element
	 */
	int getElementsAfter(CollectionElement<E> id);

	/**
	 * @param value The value to get the index of in this collection
	 * @return The index of the first position in this collection occupied by the given value, or &lt; 0 if the element does not exist in
	 *         this collection
	 */
	@Override
	default int indexOf(Object value) {
		if (!belongs(value))
			return -1;
		CollectionElement<E> element = getElement((E) value, true);
		return element == null ? -1 : getElementsBefore(element);
	}

	/**
	 * @param value The value to get the index of in this collection
	 * @return The index of the last position in this collection occupied by the given value, or &lt; 0 if the element does not exist in
	 *         this collection
	 */
	@Override
	default int lastIndexOf(Object value) {
		if (!belongs(value))
			return -1;
		CollectionElement<E> element = getElement((E) value, false);
		return element == null ? -1 : getElementsBefore(element);
	}

	/**
	 * @param index The index of the element to get
	 * @return The element of this collection at the given index
	 */
	@Override
	default E get(int index) {
		return ofElementAt(index, el -> el.get());
	}

	/**
	 * Addresses an element by index
	 *
	 * @param index The index of the element to get
	 * @param onElement The listener to be called on the element
	 */
	default void forElementAt(int index, Consumer<? super CollectionElement<? extends E>> onElement) {
		ofElementAt(index, el -> {
			onElement.accept(el);
			return null;
		});
	}

	/**
	 * Addresses an element by index
	 *
	 * @param index The index of the element to get
	 * @param onElement The listener to be called on the mutable element
	 */
	default void forMutableElementAt(int index, Consumer<? super MutableCollectionElement<? extends E>> onElement) {
		ofMutableElementAt(index, el -> {
			onElement.accept(el);
			return null;
		});
	}

	/**
	 * Calls a function on an element by index
	 *
	 * @param index The index of the element to call the function on
	 * @param onElement The function to be called on the element
	 * @return The result of the function
	 */
	default <T> T ofElementAt(int index, Function<? super CollectionElement<? extends E>, T> onElement) {
		return onElement.apply(getElement(index));
	}

	/**
	 * Calls a function on an element by index
	 *
	 * @param index The index of the element to call the function on
	 * @param onElement The function to be called on the mutable element
	 * @return The result of the function
	 */
	default <T> T ofMutableElementAt(int index, Function<? super MutableCollectionElement<? extends E>, T> onElement) {
		return ofMutableElement(getElement(index), onElement);
	}

	@Override
	default boolean contains(Object o) {
		return BetterCollection.super.contains(o);
	}

	@Override
	default boolean containsAny(Collection<?> c) {
		return BetterCollection.super.containsAny(c);
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		return BetterCollection.super.containsAll(c);
	}

	@Override
	default boolean addAll(int index, Collection<? extends E> c) {
		try (Transaction t = lock(true, null); Transaction t2 = Transactable.lock(c, false, null)) {
			if (c.isEmpty())
				return false;
			if (index == size()) {
				addAll(c);
				return true;
			}
			forMutableElementAt(index, el -> {
				Spliterator<? extends E> spliter;
				if (c instanceof BetterCollection)
					spliter = ((BetterCollection<? extends E>) c).spliterator(false).reverse();
				else {
					ArrayList<E> list = new ArrayList<>(c);
					Collections.reverse(list);
					spliter = list.spliterator();
				}
				spliter.forEachRemaining(v -> ((MutableCollectionElement<E>) el).add(v, true));
			});
			return true;
		}
	}

	@Override
	default boolean add(E value) {
		return BetterCollection.super.add(value);
	}

	@Override
	default void add(int index, E element) {
		addElement(index, element);
	}

	default CollectionElement<E> addElement(int index, E element) {
		try (Transaction t = lock(true, null)) {
			if (index == size())
				return addElement(element);
			else
				return ofMutableElementAt(index, el -> ((MutableCollectionElement<E>) el).add(element, true));
		}
	}

	@Override
	default boolean remove(Object o) {
		return BetterCollection.super.remove(o);
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		return BetterCollection.super.removeAll(c);
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		return BetterCollection.super.retainAll(c);
	}

	@Override
	default E remove(int index) {
		return ofMutableElementAt(index, el -> {
			E old = el.get();
			el.remove();
			return old;
		});
	}

	@Override
	default void removeRange(int fromIndex, int toIndex) {
		try (Transaction t = lock(true, null)) {
			MutableElementSpliterator<E> spliter = mutableSpliterator(fromIndex);
			for (int i = fromIndex; i < toIndex; i++)
				spliter.tryAdvanceElementM(el -> el.remove());
		}
	}

	@Override
	default void replaceAll(UnaryOperator<E> op) {
		BetterCollection.super.replaceAll(op);
	}

	@Override
	default E set(int index, E element) {
		if (!belongs(element))
			throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
		return ofMutableElementAt(index, el -> {
			E old = el.get();
			((MutableCollectionElement<E>) el).set(element);
			return old;
		});
	}

	@Override
	default BetterList<E> reverse() {
		return new ReversedList<>(this);
	}

	@Override
	default ElementSpliterator<E> spliterator() {
		return BetterCollection.super.spliterator();
	}

	@Override
	default ImmutableIterator<E> iterator() {
		return BetterCollection.super.iterator();
	}

	@Override
	default ListIterator<E> listIterator(int index) {
		return new BetterListIterator<>(this, mutableSpliterator(index));
	}

	@Override
	default BetterList<E> subList(int fromIndex, int toIndex) {
		return new SubList<>(this, fromIndex, toIndex);
	}

	/**
	 * @param <E> The type of the list
	 * @return An empty reversible list
	 */
	public static <E> BetterList<E> empty() {
		return new EmptyList<>();
	}

	/**
	 * Implements {@link BetterList#reverse()}
	 *
	 * @param <E> The type of elements in the list
	 */
	class ReversedList<E> extends ReversedCollection<E> implements BetterList<E> {
		protected ReversedList(BetterList<E> wrap) {
			super(wrap);
		}

		@Override
		protected BetterList<E> getWrapped() {
			return (BetterList<E>) super.getWrapped();
		}

		@Override
		public BetterList<E> reverse() {
			return getWrapped();
		}

		@Override
		public CollectionElement<E> getElement(int index) {
			try (Transaction t = lock(false, null)) {
				return getWrapped().getElement(reflect(index, false));
			}
		}

		@Override
		public int getElementsBefore(CollectionElement<E> id) {
			return getWrapped().getElementsAfter(id);
		}

		@Override
		public int getElementsAfter(CollectionElement<E> id) {
			return getWrapped().getElementsBefore(id);
		}

		@Override
		public ElementSpliterator<E> spliterator(boolean fromStart) {
			return getWrapped().spliterator(!fromStart).reverse();
		}

		protected int reflect(int index, boolean terminalInclusive) {
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
	}

	class EmptyList<E> extends EmptyCollection<E> implements BetterList<E> {
		@Override
		public CollectionElement<E> getElement(int index) {
			throw new IndexOutOfBoundsException(index + " of 0");
		}

		@Override
		public int getElementsBefore(CollectionElement<E> id) {
			throw new IllegalArgumentException(StdMsg.NOT_FOUND);
		}

		@Override
		public int getElementsAfter(CollectionElement<E> id) {
			throw new IllegalArgumentException(StdMsg.NOT_FOUND);
		}

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
		public BetterList<E> subList(int fromIndex, int toIndex) {
			if (fromIndex != 0)
				throw new IndexOutOfBoundsException(fromIndex + " of 0");
			if (toIndex != 0)
				throw new IndexOutOfBoundsException(toIndex + " of 0");
			return this;
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

	public static class BetterListIterator<E> implements ListIterator<E> {
		private final BetterList<E> theList;
		protected final MutableElementSpliterator<E> backing;
		private Ternian hasNext;
		private Ternian hasPrevious;
		private MutableCollectionElement<E> element;
		private boolean elementIsNext;
		// False if the spliterator's cursor is on the leading (left) side of the cached element, true if on the trailing (right) side
		private boolean spliteratorSide;
		private boolean isReadyForRemove;

		public BetterListIterator(BetterList<E> list, MutableElementSpliterator<E> backing) {
			theList = list;
			this.backing = backing;
			hasNext = Ternian.NONE;
			hasPrevious = Ternian.NONE;
		}

		protected MutableCollectionElement<E> getCurrentElement() {
			return element;
		}

		@Override
		public boolean hasNext() {
			if (hasNext == Ternian.NONE)
				getElement(true);
			return hasNext.value;
		}

		@Override
		public E next() {
			if (!hasNext())
				throw new NoSuchElementException();
			if (!elementIsNext)
				getElement(true);
			move(true);
			elementIsNext = false;
			hasPrevious = Ternian.TRUE;
			hasNext = Ternian.NONE;
			isReadyForRemove = true;
			return element.get();
		}

		@Override
		public boolean hasPrevious() {
			if (hasPrevious == Ternian.NONE)
				getElement(false);
			return hasPrevious.value;
		}

		@Override
		public E previous() {
			if (!hasPrevious())
				throw new NoSuchElementException();
			if (elementIsNext)
				getElement(false);
			move(false);
			elementIsNext = true;
			hasPrevious = Ternian.NONE;
			hasNext = Ternian.TRUE;
			isReadyForRemove = true;
			return element.get();
		}

		protected void getElement(boolean forward) {
			if (forward) {
				if (hasPrevious == Ternian.TRUE && !spliteratorSide) // Need to advance the spliterator over the cached previous
					backing.tryAdvance(v -> {
					});
				hasNext = Ternian.of(backing.tryAdvanceElementM(el -> element = el));
			} else {
				if (hasNext == Ternian.TRUE && spliteratorSide) // Need to reverse the spliterator over the cached next
					backing.tryReverse(v -> {
					});
				hasPrevious = Ternian.of(backing.tryReverseElementM(el -> element = el));
			}
			spliteratorSide = forward;
			elementIsNext = forward;
			isReadyForRemove = false;
		}

		protected void move(boolean forward) {}

		@Override
		public void remove() {
			if (!isReadyForRemove)
				throw new UnsupportedOperationException("Element has already been removed or iteration has not begun");
			element.remove();
			clearCache();
		}

		@Override
		public void set(E e) {
			if (!isReadyForRemove)
				throw new UnsupportedOperationException("Element has been removed or iteration has not begun");
			element.set(e);
		}

		protected void clearCache() {
			element = null;
			hasNext = Ternian.NONE;
			hasPrevious = Ternian.NONE;
			isReadyForRemove = false;
		}

		protected int getSpliteratorCursorOffset() {
			if (element == null)
				return 0;
			else if (elementIsNext)
				return spliteratorSide ? 1 : 0;
			else
				return spliteratorSide ? 0 : -1;
		}

		@Override
		public int nextIndex() {
			return theList.getElementsBefore(getCurrentElement()) + getSpliteratorCursorOffset();
		}

		@Override
		public int previousIndex() {
			return theList.getElementsBefore(getCurrentElement()) + getSpliteratorCursorOffset() - 1;
		}

		@Override
		public void add(E e) {
			if (!isReadyForRemove)
				throw new UnsupportedOperationException("Element has been removed or iteration has not begun");
			element.add(e, true);
		}

		@Override
		public String toString() {
			return backing.toString();
		}
	}

	class SubList<E> implements BetterList<E> {
		private final BetterList<E> theWrapped;
		private int theStart;
		private int theEnd;

		public SubList(BetterList<E> wrapped, int start, int end) {
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
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public CollectionElement<E> getElement(E value, boolean first) {
			try (Transaction t = lock(false, null)) {
				CollectionElement<E> firstMatch = theWrapped.getElement(value, first);
				int index = getElementsBefore(firstMatch);
				if ((first && index >= theEnd) || (!first && index < theStart))
					return null;
				ElementSpliterator<E> spliter = theWrapped.spliterator(firstMatch, !first);
				// For !first, we'll switch things just to make the problem easier
				int firstIdx = first ? theStart : theEnd - 1;
				int lastIdx = first ? theEnd - 1 : theStart;
				if (!first) {
					index = firstIdx - (index - lastIdx);
					spliter = spliter.reverse();
				}
				while (index < firstIdx && spliter.tryAdvanceElement(el -> {
				})) {
					index++;
				}
				if (index < firstIdx)
					return null;
				CollectionElement<E>[] found = new CollectionElement[1];
				while (found[0] == null && index <= lastIdx && spliter.tryAdvanceElement(el -> {
					if (Objects.equals(el.get(), value))
						found[0] = el;
				})) {
					index++;
				}
				return found[0];
			}
		}

		@Override
		public <X> X ofMutableElement(CollectionElement<E> element, Function<? super MutableCollectionElement<E>, X> onElement) {
			return theWrapped.ofMutableElement(element, onElement);
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(CollectionElement<E> element, boolean asNext) {
			return new SubSpliterator(theWrapped.mutableSpliterator(element, asNext), getElementsBefore(element));
		}

		@Override
		public CollectionElement<E> getElement(int index) {
			return theWrapped.getElement(checkIndex(index, false));
		}

		@Override
		public int getElementsBefore(CollectionElement<E> id) {
			int wrappedEls = theWrapped.getElementsBefore(id);
			if (wrappedEls < theStart || wrappedEls >= theEnd)
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return wrappedEls - theStart;
		}

		@Override
		public int getElementsAfter(CollectionElement<E> id) {
			int wrappedEls = theWrapped.getElementsBefore(id);
			if (wrappedEls < theStart || wrappedEls >= theEnd)
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return theEnd - wrappedEls;
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



		protected MutableCollectionElement<E> wrapElement(MutableCollectionElement<E> el) {
			return new MutableCollectionElement<E>() {
				@Override
				public boolean isPresent() {
					return el.isPresent();
				}

				@Override
				public int compareTo(CollectionElement<E> o) {
					return el.compareTo(o);
				}

				@Override
				public E get() {
					return el.get();
				}

				@Override
				public String isEnabled() {
					return el.isEnabled();
				}

				@Override
				public String isAcceptable(E value) {
					return el.isAcceptable(value);
				}

				@Override
				public void set(E value) throws IllegalArgumentException, UnsupportedOperationException {
					el.set(value);
				}

				@Override
				public String canRemove() {
					return el.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					el.remove();
					theEnd--;
				}

				@Override
				public String canAdd(E value, boolean before) {
					return el.canAdd(value, before);
				}

				@Override
				public CollectionElement<E> add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
					CollectionElement<E> newEl = el.add(value, before);
					theEnd++;
					return newEl;
				}
			};
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(boolean fromStart) {
			int index = fromStart ? theStart : theEnd;
			return new SubSpliterator(theWrapped.mutableSpliterator(index), index);
		}

		@Override
		public ElementSpliterator<E> spliterator(int index) {
			return mutableSpliterator(index).immutable();
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(int index) {
			return new SubSpliterator(theWrapped.mutableSpliterator(theStart + checkIndex(index, true)), theStart + index);
		}

		class SubSpliterator implements MutableElementSpliterator<E> {
			private final MutableElementSpliterator<E> wrapSpliter;
			private int spliterIndex;

			SubSpliterator(MutableElementSpliterator<E> spliter, int position) {
				wrapSpliter = spliter;
				spliterIndex = position;
			}

			@Override
			public boolean tryAdvanceElementM(Consumer<? super MutableCollectionElement<E>> action) {
				if (spliterIndex >= theEnd)
					return false;
				if (wrapSpliter.tryAdvanceElementM(el -> {
					action.accept(wrapElement(el));
				})) {
					spliterIndex++;
					return true;
				}
				return false;
			}

			@Override
			public boolean tryReverseElementM(Consumer<? super MutableCollectionElement<E>> action) {
				if (spliterIndex <= theStart)
					return false;
				if (wrapSpliter.tryReverseElementM(el -> {
					action.accept(wrapElement(el));
				})) {
					spliterIndex--;
					return true;
				}
				return false;
			}

			@Override
			public boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action) {
				if (spliterIndex >= theEnd)
					return false;
				if (wrapSpliter.tryAdvanceElementM(el -> {
					action.accept(el);
				})) {
					spliterIndex++;
					return true;
				}
				return false;
			}

			@Override
			public boolean tryReverseElement(Consumer<? super CollectionElement<E>> action) {
				if (spliterIndex <= theStart)
					return false;
				if (wrapSpliter.tryAdvanceElement(el -> {
					action.accept(el);
				})) {
					spliterIndex--;
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
			public MutableElementSpliterator<E> trySplit() {
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
		public String canAdd(E value) {
			if (!belongs(value))
				return StdMsg.ILLEGAL_ELEMENT;
			return ofMutableElementAt(size() - 1, el -> ((MutableCollectionElement<E>) el).canAdd(value, false));
		}

		@Override
		public boolean add(E e) {
			return BetterList.super.add(e);
		}

		@Override
		public CollectionElement<E> addElement(E e) {
			CollectionElement<E> newEl = theWrapped.addElement(theEnd, e);
			if (newEl != null)
				theEnd++;
			return newEl;
		}

		@Override
		public CollectionElement<E> addElement(int index, E element) {
			CollectionElement<E> newEl = theWrapped.addElement(theStart + checkIndex(index, true), element);
			if (newEl != null)
				theEnd++;
			return newEl;
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
	}
}

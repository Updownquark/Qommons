package org.qommons.collect;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.qommons.Ternian;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.MutableElementHandle.StdMsg;

/**
 * A {@link List} that is also a {@link BetterCollection}
 * 
 * @param <E> The type of value in the list
 */
public interface BetterList<E> extends BetterCollection<E>, RRList<E>, TransactableList<E> {
	default ElementSpliterator<E> spliterator(int index) {
		return mutableSpliterator(index).immutable();
	}

	MutableElementSpliterator<E> mutableSpliterator(int index);

	@Override
	default Object[] toArray() {
		return BetterCollection.super.toArray();
	}

	/**
	 * @param id The ID of the element
	 * @return The number of elements in this collection positioned before the given element
	 */
	int getElementsBefore(ElementId id);

	/**
	 * @param id The ID of the element
	 * @return The number of elements in this collection positioned after the given element
	 */
	int getElementsAfter(ElementId id);

	/**
	 * @param value The value to get the index of in this collection
	 * @return The index of the first position in this collection occupied by the given value, or &lt; 0 if the element does not exist in
	 *         this collection
	 */
	@Override
	default int indexOf(Object value) {
		if (!belongs(value))
			return -1;
		int[] index = new int[1];
		if (!forElement((E) value, el -> index[0] = getElementsBefore(el.getElementId()), true))
			return -1;
		return index[0];
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
		int[] index = new int[1];
		if (!forElement((E) value, el -> index[0] = getElementsBefore(el.getElementId()), false))
			return -1;
		return index[0];
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
	default void forElementAt(int index, Consumer<? super ElementHandle<? extends E>> onElement) {
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
	default void forMutableElementAt(int index, Consumer<? super MutableElementHandle<? extends E>> onElement) {
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
	<T> T ofElementAt(int index, Function<? super ElementHandle<? extends E>, T> onElement);

	/**
	 * Calls a function on an element by index
	 *
	 * @param index The index of the element to call the function on
	 * @param onElement The function to be called on the mutable element
	 * @return The result of the function
	 */
	<T> T ofMutableElementAt(int index, Function<? super MutableElementHandle<? extends E>, T> onElement);

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
				spliter.forEachRemaining(v -> ((MutableElementHandle<E>) el).add(v, true));
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

	default ElementId addElement(int index, E element) {
		try (Transaction t = lock(true, null)) {
			if (index == size())
				return addElement(element);
			else
				return ofMutableElementAt(index, el -> ((MutableElementHandle<E>) el).add(element, true));
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
			((MutableElementHandle<E>) el).set(element);
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
	 * @param type The type of the list
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
		public int getElementsBefore(ElementId id) {
			return getWrapped().getElementsAfter(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return getWrapped().getElementsBefore(id);
		}

		@Override
		public ElementSpliterator<E> spliterator(boolean fromStart) {
			return getWrapped().spliterator(!fromStart).reverse();
		}

		@Override
		public ElementSpliterator<E> spliterator(int index) {
			return getWrapped().spliterator(reflect(index, true)).reverse();
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(boolean fromStart) {
			return getWrapped().mutableSpliterator(!fromStart).reverse();
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(int index) {
			return getWrapped().mutableSpliterator(reflect(index, true)).reverse();
		}

		@Override
		public <T> T ofElementAt(int index, Function<? super ElementHandle<? extends E>, T> onElement) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			int size = size();
			if (index >= size)
				throw new IndexOutOfBoundsException(index + " of " + size);
			return getWrapped().ofElementAt(reflect(index, false), el -> onElement.apply(el.reverse()));
		}

		@Override
		public <T> T ofMutableElementAt(int index, Function<? super MutableElementHandle<? extends E>, T> onElement) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			int size = size();
			if (index >= size)
				throw new IndexOutOfBoundsException(index + " of " + size);
			return getWrapped().ofMutableElementAt(reflect(index, false), el -> onElement.apply(el.reverse()));
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
		public ElementSpliterator<E> spliterator(int index) {
			if (index != 0)
				throw new IndexOutOfBoundsException(index + " of 0");
			return ElementSpliterator.empty();
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(int index) {
			if (index != 0)
				throw new IndexOutOfBoundsException(index + " of 0");
			return MutableElementSpliterator.empty();
		}

		@Override
		public <T> T ofElementAt(int index, Function<? super ElementHandle<? extends E>, T> onElement) {
			throw new IndexOutOfBoundsException(index + " of 0");
		}

		@Override
		public <T> T ofMutableElementAt(int index, Function<? super MutableElementHandle<? extends E>, T> onElement) {
			throw new IndexOutOfBoundsException(index + " of 0");
		}

		@Override
		public int getElementsBefore(ElementId id) {
			throw new IllegalArgumentException(StdMsg.NOT_FOUND);
		}

		@Override
		public int getElementsAfter(ElementId id) {
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
		private MutableElementHandle<E> element;
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

		protected MutableElementHandle<E> getCurrentElement() {
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
			return theList.getElementsBefore(getCurrentElement().getElementId()) + getSpliteratorCursorOffset();
		}

		@Override
		public int previousIndex() {
			return theList.getElementsBefore(getCurrentElement().getElementId()) + getSpliteratorCursorOffset() - 1;
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
		public int getElementsBefore(ElementId id) {
			int wrappedEls = theWrapped.getElementsBefore(id);
			if (wrappedEls < theStart || wrappedEls >= theEnd)
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return wrappedEls - theStart;
		}

		@Override
		public int getElementsAfter(ElementId id) {
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


		@Override
		public boolean forElement(E value, Consumer<? super ElementHandle<? extends E>> onElement, boolean first) {
			if (!belongs(value))
				return false;
			boolean[] success = new boolean[1];
			MutableElementSpliterator<E> spliter = first ? mutableSpliterator(true) : mutableSpliterator(false).reverse();
			while (!success[0] && spliter.tryAdvanceElement(el -> {
				if (Objects.equals(el.get(), value)) {
					onElement.accept(el);
					success[0] = true;
				}
			})) {
			}
			return success[0];
		}

		@Override
		public boolean forMutableElement(E value, Consumer<? super MutableElementHandle<? extends E>> onElement, boolean first) {
			if (!belongs(value))
				return false;
			boolean[] success = new boolean[1];
			MutableElementSpliterator<E> spliter = first ? mutableSpliterator(true) : mutableSpliterator(false).reverse();
			while (!success[0] && spliter.tryAdvanceElementM(el -> {
				if (Objects.equals(el.get(), value)) {
					onElement.accept(wrapElement(el));
					success[0] = true;
				}
			})) {
			}
			return success[0];
		}

		@Override
		public <T> T ofElementAt(ElementId elementId, Function<? super ElementHandle<? extends E>, T> onElement) {
			return theWrapped.ofElementAt(elementId, el -> {
				int index = theWrapped.getElementsBefore(elementId);
				if (index < theStart || index >= theEnd)
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
				return onElement.apply(el);
			});
		}

		@Override
		public <T> T ofMutableElementAt(ElementId elementId, Function<? super MutableElementHandle<? extends E>, T> onElement) {
			return theWrapped.ofMutableElementAt(elementId, el -> {
				int index = theWrapped.getElementsBefore(elementId);
				if (index < theStart || index >= theEnd)
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
				return onElement.apply(el);
			});
		}

		@Override
		public <T> T ofElementAt(int index, Function<? super ElementHandle<? extends E>, T> onElement) {
			if (index + theStart >= theEnd)
				throw new IndexOutOfBoundsException(index + " of " + (theEnd - theStart));
			return theWrapped.ofElementAt(index, onElement);
		}

		@Override
		public <T> T ofMutableElementAt(int index, Function<? super MutableElementHandle<? extends E>, T> onElement) {
			if (index + theStart >= theEnd)
				throw new IndexOutOfBoundsException(index + " of " + (theEnd - theStart));
			return theWrapped.ofMutableElementAt(index, onElement);
		}

		protected MutableElementHandle<E> wrapElement(MutableElementHandle<E> el) {
			return new MutableElementHandle<E>() {
				@Override
				public ElementId getElementId() {
					return el.getElementId();
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
				public ElementId add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
					ElementId id = el.add(value, before);
					theEnd++;
					return id;
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
			public boolean tryAdvanceElementM(Consumer<? super MutableElementHandle<E>> action) {
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
			public boolean tryReverseElementM(Consumer<? super MutableElementHandle<E>> action) {
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
			public boolean tryAdvanceElement(Consumer<? super ElementHandle<E>> action) {
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
			public boolean tryReverseElement(Consumer<? super ElementHandle<E>> action) {
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
			return ofMutableElementAt(size() - 1, el -> ((MutableElementHandle<E>) el).canAdd(value, false));
		}

		@Override
		public boolean add(E e) {
			return BetterList.super.add(e);
		}

		@Override
		public ElementId addElement(E e) {
			ElementId id = theWrapped.addElement(theEnd, e);
			if (id != null)
				theEnd++;
			return id;
		}

		@Override
		public ElementId addElement(int index, E element) {
			ElementId id = theWrapped.addElement(theStart + checkIndex(index, true), element);
			if (id != null)
				theEnd++;
			return id;
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

package org.qommons.collect;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.MutableElementSpliterator.SimpleMutableSpliterator;

/**
 * A {@link List} that is also a {@link BetterCollection}
 * 
 * @param <E> The type of value in the list
 */
public interface BetterList<E> extends BetterCollection<E>, TransactableList<E> {
	CollectionElement<E> getElement(int index);

	/**
	 * @param index The index of the element to be the next element returned from the spliterator on forward access
	 * @return The spliterator
	 */
	default MutableElementSpliterator<E> spliterator(int index) {
		if (index == 0)
			return spliterator(true);
		try (Transaction t = lock(false, null)) {
			if (index == size())
				return spliterator(false);
			return spliterator(getElement(index).getElementId(), true);
		}
	}

	@Override
	default Object[] toArray() {
		return BetterCollection.super.toArray();
	}

	/**
	 * @param id The element
	 * @return The number of elements in this collection positioned before the given element
	 */
	int getElementsBefore(ElementId id);

	/**
	 * @param id The element
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
		CollectionElement<E> element = getElement((E) value, true);
		return element == null ? -1 : getElementsBefore(element.getElementId());
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
		return element == null ? -1 : getElementsBefore(element.getElementId());
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
	default void forElementAt(int index, Consumer<? super CollectionElement<E>> onElement) {
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
	default void forMutableElementAt(int index, Consumer<? super MutableCollectionElement<E>> onElement) {
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
	default <T> T ofElementAt(int index, Function<? super CollectionElement<E>, T> onElement) {
		return onElement.apply(getElement(index));
	}

	/**
	 * Calls a function on an element by index
	 *
	 * @param index The index of the element to call the function on
	 * @param onElement The function to be called on the mutable element
	 * @return The result of the function
	 */
	default <T> T ofMutableElementAt(int index, Function<? super MutableCollectionElement<E>, T> onElement) {
		return ofMutableElement(getElement(index).getElementId(), onElement);
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
				c.spliterator().forEachRemaining(v -> el.add(v, true));
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
			if (index == 0)
				return addElement(element, true);
			else if (index == size())
				return addElement(element, false);
			else
				return getElement(ofMutableElementAt(index, el -> el.add(element, true)));
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
			MutableElementSpliterator<E> spliter = spliterator(fromIndex);
			for (int i = fromIndex; i < toIndex; i++)
				spliter.forElementM(el -> el.remove(), true);
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
			el.set(element);
			return old;
		});
	}

	@Override
	default BetterList<E> reverse() {
		return new ReversedList<>(this);
	}

	@Override
	default MutableElementSpliterator<E> spliterator() {
		return BetterCollection.super.spliterator();
	}

	@Override
	default Iterator<E> iterator() {
		return BetterCollection.super.iterator();
	}

	@Override
	default ListIterator<E> listIterator(int index) {
		return new BetterListIterator<>(this, spliterator(index));
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
				return getWrapped().getElement(reflect(index, false)).reverse();
			}
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return getWrapped().getElementsAfter(id.reverse());
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return getWrapped().getElementsBefore(id.reverse());
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return super.addAll(c);
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
		public int getElementsBefore(ElementId id) {
			throw new IllegalArgumentException(StdMsg.NOT_FOUND);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			throw new IllegalArgumentException(StdMsg.NOT_FOUND);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return super.addAll(c);
		}
	}

	public static class BetterListIterator<E> implements ListIterator<E> {
		private static final Consumer<CollectionElement<?>> NULL_ACTION = el -> {
		};

		private final BetterList<E> theList;
		private final MutableElementSpliterator<E> backing;
		private CollectionElement<E> element;
		/**
		 * If {@link #element} is non-null, then whether that element is prepped to be used for {@link #next()} or {@link #previous()}.
		 * 
		 * Otherwise, whether {@link #next()} or {@link #previous()} method was called more recently
		 */
		private boolean elementIsNext;
		private ElementId theLastElement;
		private boolean isReadyForMod;

		public BetterListIterator(BetterList<E> list, MutableElementSpliterator<E> backing) {
			theList = list;
			this.backing = backing;
		}

		@Override
		public boolean hasNext() {
			if (element == null || !elementIsNext)
				getElement(true);
			return element != null && elementIsNext;
		}

		@Override
		public E next() {
			if (!hasNext())
				throw new NoSuchElementException();
			E value = element.get();
			element = null;
			isReadyForMod = true;
			return value;
		}

		@Override
		public boolean hasPrevious() {
			if (element == null || elementIsNext)
				getElement(false);
			return element != null && !elementIsNext;
		}

		@Override
		public E previous() {
			if (!hasPrevious())
				throw new NoSuchElementException();
			E value = element.get();
			element = null;
			isReadyForMod = true;
			return value;
		}

		private void getElement(boolean forward) {
			backing.forElement(el -> element = el, forward);
			theLastElement = element == null ? null : element.getElementId();
			elementIsNext = forward;
			isReadyForMod = false;
		}

		private void onLastElement(Consumer<MutableCollectionElement<E>> action, int backup) {
			if (!isReadyForMod)
				throw new IllegalStateException(
					"Modification must come after a call to next() or previous() and before the next call to hasNext() or hasPrevious()");
			boolean next = !elementIsNext;
			Consumer<MutableCollectionElement<E>> elAction = el -> {
				if (!theLastElement.equals(el.getElementId()))
					throw new ConcurrentModificationException("Element appears to have moved or to have been removed");
				action.accept(el);
			};
			backing.forElementM(elAction, next);
			for (int i = 0; i < backup; i++)
				backing.forElement(NULL_ACTION, !next);
		}

		@Override
		public int nextIndex() {
			if (theLastElement == null) {
				if (!backing.forElement(NULL_ACTION, false))
					return 0;
				// Advance back over the element
				backing.forElement(el -> theLastElement = el.getElementId(), true);
				elementIsNext = true;
			}
			int index = theList.getElementsBefore(theLastElement);
			if ((element == null) == elementIsNext)
				index++;
			return index;
		}

		@Override
		public int previousIndex() {
			return nextIndex() - 1;
		}

		@Override
		public void remove() {
			onLastElement(el -> el.remove(), 0);
			theLastElement = null;
			isReadyForMod = false;
		}

		@Override
		public void set(E e) {
			onLastElement(el -> el.set(e), 1);
		}

		@Override
		public void add(E e) {
			// If we need to back up, then we're going to add an element after the previous element;
			// so we'll need to advance twice instead of once
			onLastElement(el -> el.add(e, !elementIsNext), elementIsNext ? 2 : 1);
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
		private long theStructureStamp;

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
			theStructureStamp = wrapped.getStamp(true);
		}

		private void check() {
			if (theWrapped.getStamp(true) != theStructureStamp)
				throw new ConcurrentModificationException(BACKING_COLLECTION_CHANGED);
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			Transaction t = theWrapped.lock(write, structural, cause);
			try {
				check();
				return t;
			} catch (RuntimeException | Error e) {
				t.close();
				throw e;
			}
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			long stamp = theWrapped.getStamp(structuralOnly);
			if (structuralOnly && theStructureStamp != stamp)
				throw new ConcurrentModificationException(BACKING_COLLECTION_CHANGED);
			return stamp;
		}

		@Override
		public CollectionElement<E> getElement(E value, boolean first) {
			try (Transaction t = lock(false, true, null)) {
				CollectionElement<E> firstMatch = theWrapped.getElement(value, first);
				if (firstMatch == null)
					return null;
				int index = theWrapped.getElementsBefore(firstMatch.getElementId());
				if ((first && index >= theEnd) || (!first && index < theStart))
					return null;
				if ((first && index >= theStart) || (!first && index < theEnd))
					return firstMatch;
				// There is a match, but the first such match is outside this sub-list's bounds. Need to just iterate.
				ElementSpliterator<E> spliter = spliterator(first);
				CollectionElement<E>[] found = new CollectionElement[1];
				while (found[0] == null && spliter.forElement(el -> {
					if (Objects.equals(el.get(), value))
						found[0] = el;
				}, first)) {
				}
				return found[0];
			}
		}

		@Override
		public CollectionElement<E> getElement(ElementId id) {
			try (Transaction t = lock(false, true, null)) {
				int index = theWrapped.getElementsBefore(id);
				if (index < theStart || index >= theEnd)
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
				return theWrapped.getElement(id);
			}
		}

		@Override
		public MutableCollectionElement<E> mutableElement(ElementId id) {
			return wrapElement(theWrapped.mutableElement(id));
		}

		@Override
		public MutableElementSpliterator<E> spliterator(ElementId element, boolean asNext) {
			try (Transaction t = lock(false, true, null)) {
				int index = theWrapped.getElementsBefore(element);
				if (index < theStart || index >= theEnd)
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
				return new SubSpliterator(theWrapped.spliterator(element, asNext), asNext ? index : index + 1);
			}
		}

		@Override
		public CollectionElement<E> getElement(int index) {
			try (Transaction t = lock(false, true, null)) {
				return theWrapped.getElement(theStart + checkIndex(index, false));
			}
		}

		@Override
		public int getElementsBefore(ElementId id) {
			try (Transaction t = lock(false, true, null)) {
				int wrappedEls = theWrapped.getElementsBefore(id);
				if (wrappedEls < theStart || wrappedEls >= theEnd)
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
				return wrappedEls - theStart;
			}
		}

		@Override
		public int getElementsAfter(ElementId id) {
			try (Transaction t = lock(false, true, null)) {
				int wrappedEls = theWrapped.getElementsBefore(id);
				if (wrappedEls < theStart || wrappedEls >= theEnd)
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
				return theEnd - wrappedEls - 1;
			}
		}

		@Override
		public boolean belongs(Object o) {
			return theWrapped.belongs(o);
		}

		@Override
		public int size() {
			int sz = theWrapped.size();
			if (sz <= theStart)
				return 0;
			return Math.min(sz, theEnd) - theStart;
		}

		@Override
		public boolean isEmpty() {
			return Math.min(theEnd, theWrapped.size()) <= theStart;
		}

		@Override
		public Object[] toArray() {
			try (Transaction t = lock(false, true, null)) {
				check();
				Object[] array = new Object[size()];
				for (int i = 0; i < array.length; i++)
					array[i] = get(i);
				return array;
			}
		}

		@Override
		public <T> T[] toArray(T[] a) {
			try (Transaction t = lock(false, true, null)) {
				check();
				T[] array = a.length >= size() ? a : (T[]) Array.newInstance(a.getClass().getComponentType(), size());
				for (int i = 0; i < array.length; i++)
					array[i] = (T) get(i);
				return array;
			}
		}

		protected MutableCollectionElement<E> wrapElement(MutableCollectionElement<E> el) {
			return new SubListElement(el);
		}

		protected class SubListElement implements MutableCollectionElement<E> {
			private final MutableCollectionElement<E> theWrappedEl;

			protected SubListElement(MutableCollectionElement<E> wrappedEl) {
				this.theWrappedEl = wrappedEl;
			}

			protected MutableCollectionElement<E> getWrappedEl() {
				return theWrappedEl;
			}

			@Override
			public ElementId getElementId() {
				return theWrappedEl.getElementId();
			}

			@Override
			public int compareTo(CollectionElement<E> o) {
				return theWrappedEl.compareTo(o);
			}

			@Override
			public E get() {
				return theWrappedEl.get();
			}

			@Override
			public String isEnabled() {
				return theWrappedEl.isEnabled();
			}

			@Override
			public String isAcceptable(E value) {
				return theWrappedEl.isAcceptable(value);
			}

			@Override
			public void set(E value) throws IllegalArgumentException, UnsupportedOperationException {
				theWrappedEl.set(value);
			}

			@Override
			public String canRemove() {
				return theWrappedEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				try (Transaction t = lock(true, true, null)) {
					theWrappedEl.remove();
					theEnd--;
					theStructureStamp = theWrapped.getStamp(true);
				}
			}

			@Override
			public String canAdd(E value, boolean before) {
				return theWrappedEl.canAdd(value, before);
			}

			@Override
			public ElementId add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				try (Transaction t = lock(true, true, null)) {
					ElementId newId = theWrappedEl.add(value, before);
					theEnd++;
					theStructureStamp = theWrapped.getStamp(true);
					return newId;
				}
			}
		}

		@Override
		public MutableElementSpliterator<E> spliterator(boolean fromStart) {
			int index = fromStart ? theStart : theEnd;
			return new SubSpliterator(theWrapped.spliterator(index), index);
		}

		class SubSpliterator extends SimpleMutableSpliterator<E> {
			private final MutableElementSpliterator<E> wrapSpliter;
			private int nextIndex;
			private long theSpliterStructureStamp;

			SubSpliterator(MutableElementSpliterator<E> spliter, int position) {
				super(SubList.this);
				wrapSpliter = spliter;
				nextIndex = position;
				theSpliterStructureStamp = theStructureStamp;
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

			private void check() {
				if (theSpliterStructureStamp != theStructureStamp)
					throw new ConcurrentModificationException(BACKING_COLLECTION_CHANGED);
				SubList.this.check();
			}

			@Override
			protected boolean internalForElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
				check();
				if (forward && nextIndex >= theEnd)
					return false;
				if (!forward && nextIndex <= theStart)
					return false;
				if (wrapSpliter.forElement(action, forward)) {
					if (forward)
						nextIndex++;
					else
						nextIndex--;
					return true;
				}
				return false;
			}

			@Override
			protected boolean internalForElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward) {
				check();
				if (forward && nextIndex >= theEnd)
					return false;
				if (!forward && nextIndex <= theStart)
					return false;
				if (wrapSpliter.forElementM(el -> action.accept(wrapSpliterElement(el, forward)), forward)) {
					if (forward)
						nextIndex++;
					else
						nextIndex--;
					return true;
				}
				return false;
			}

			private MutableCollectionElement<E> wrapSpliterElement(MutableCollectionElement<E> el, boolean forward) {
				return new SubSpliterElement(el, forward);
			}

			protected class SubSpliterElement extends SubListElement {
				private final boolean isForward;

				SubSpliterElement(MutableCollectionElement<E> wrappedEl, boolean forward) {
					super(wrappedEl);
					isForward = forward;
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					try (Transaction t = theWrapped.lock(true, true, null)) {
						check();
						super.remove();
						theSpliterStructureStamp = theStructureStamp = theWrapped.getStamp(true);
						if (isForward)
							nextIndex--;
					}
				}

				@Override
				public ElementId add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
					try (Transaction t = theWrapped.lock(true, true, null)) {
						check();
						ElementId newId = super.add(value, before);
						theSpliterStructureStamp = theStructureStamp = theWrapped.getStamp(true);
						if (before)
							nextIndex++;
						return newId;
					}
				}
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
			try (Transaction t = lock(false, true, null)) {
				return theWrapped.get(checkIndex(index, false) + theStart);
			}
		}

		@Override
		public String canAdd(E value) {
			if (!belongs(value))
				return StdMsg.ILLEGAL_ELEMENT;
			try (Transaction t = lock(true, false, null)) {
				if (theWrapped.isEmpty()) {
					if (theStart != 0)
						return StdMsg.UNSUPPORTED_OPERATION;
					else
						return theWrapped.canAdd(value);
				} else
					return theWrapped.ofMutableElementAt(Math.min(theWrapped.size(), theEnd), el -> el.canAdd(value, false));
			}
		}

		@Override
		public boolean add(E e) {
			try (Transaction t = lock(true, true, null)) {
				theWrapped.add(theEnd, e);
				theStructureStamp = theWrapped.getStamp(true);
				theEnd++;
				return true;
			}
		}

		@Override
		public CollectionElement<E> addElement(E e, boolean first) {
			try (Transaction t = lock(true, true, null)) {
				CollectionElement<E> newEl = theWrapped.addElement(first ? theStart : theEnd, e);
				theStructureStamp = theWrapped.getStamp(true);
				if (newEl != null)
					theEnd++;
				return newEl;
			}
		}

		@Override
		public CollectionElement<E> addElement(int index, E element) {
			try (Transaction t = lock(true, true, null)) {
				CollectionElement<E> newEl = theWrapped.addElement(theStart + checkIndex(index, true), element);
				theStructureStamp = theWrapped.getStamp(true);
				if (newEl != null)
					theEnd++;
				return newEl;
			}
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			try (Transaction t = lock(true, true, null)) {
				int preSize = theWrapped.size();
				if (!theWrapped.addAll(theEnd, c))
					return false;
				theStructureStamp = theWrapped.getStamp(true);
				theEnd += theWrapped.size() - preSize;
				return true;
			}
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			try (Transaction t = lock(true, true, null)) {
				int preSize = theWrapped.size();
				if (!theWrapped.addAll(theStart + checkIndex(index, true), c))
					return false;
				theStructureStamp = theWrapped.getStamp(true);
				theEnd += theWrapped.size() - preSize;
				return true;
			}
		}

		@Override
		public void clear() {
			try (Transaction t = lock(true, true, null)) {
				int sz = theWrapped.size();
				if (sz <= theStart)
					return;
				int end = theEnd;
				if (sz < end)
					end = sz;
				theWrapped.removeRange(theStart, end);
				theStructureStamp = theWrapped.getStamp(true);
				theEnd = theStart;
			}
		}

		@Override
		public int hashCode() {
			return BetterCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return BetterCollection.toString(this);
		}
	}
}

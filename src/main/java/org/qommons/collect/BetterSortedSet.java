package org.qommons.collect;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.function.Consumer;

import org.qommons.Ternian;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public interface BetterSortedSet<E> extends BetterSet<E>, BetterList<E>, NavigableSet<E> {
	/**
	 * A filter on sorted search results. The underlying code for searches in a {@link BetterSortedSet} will always return an element unless
	 * the set is empty. The value will either match the search exactly or will be adjacent to where a value matching the search exactly
	 * would be inserted.
	 * 
	 * One of these filters may be used to direct and filter the search results.
	 * 
	 * Note that this class cannot be used to exclude an exact match. This may be done by creating a {@link Comparable search} that never
	 * returns zero. For example, if a result strictly less than a search is desired, create a wrapping search that returns 1 for any values
	 * that the wrapped search returns 0 for.
	 * 
	 * @see BetterSortedSet#search(Comparable, SortedSearchFilter)
	 */
	enum SortedSearchFilter {
		/** Accepts only results for which a search returns &lt;=0 */
		Less(Ternian.TRUE, true),
		/** Prefers results for which a search returns &lt;=0, but accepts a greater result if no lesser result exists */
		PreferLess(Ternian.TRUE, false),
		/** Accepts only results for which a search returns 0 */
		OnlyMatch(Ternian.NONE, true),
		/** Prefers results for which a search returns &gt;=0, but accepts a lesser result if no greater result exists */
		PreferGreater(Ternian.FALSE, false),
		/** Accepts only results for which a search returns &gt;=0 */
		Greater(Ternian.FALSE, true);

		/** Whether this search prefers values less than an exact match, or {@link Ternian#NONE} for {@link #OnlyMatch} */
		public final Ternian less;
		/** Whether this search allows matches that */
		public final boolean strict;

		private SortedSearchFilter(Ternian less, boolean strict) {
			this.less = less;
			this.strict = strict;
		}

		public SortedSearchFilter opposite() {
			switch (this) {
			case Less:
				return Greater;
			case PreferLess:
				return PreferGreater;
			case PreferGreater:
				return PreferLess;
			case Greater:
				return Less;
			default:
				return this;
			}
		}

		public static SortedSearchFilter of(Boolean less, boolean strict) {
			for (SortedSearchFilter ssf : SortedSearchFilter.values())
				if (Objects.equals(ssf.less.value, less) && ssf.strict == strict)
					return ssf;
			throw new IllegalArgumentException("No such filter exists");
		}
	}

	@Override
	default boolean isContentControlled() {
		return true;
	}

	@Override
	default MutableElementSpliterator<E> spliterator() {
		return BetterList.super.spliterator();
	}

	@Override
	default Iterator<E> iterator() {
		return BetterList.super.iterator();
	}

	@Override
	default boolean contains(Object c) {
		return BetterList.super.contains(c);
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		return BetterList.super.containsAll(c);
	}

	@Override
	default Object[] toArray() {
		return BetterList.super.toArray();
	}

	@Override
	default String canAdd(E value) {
		if (!belongs(value))
			return StdMsg.ILLEGAL_ELEMENT;
		try (Transaction t = lock(true, null)) {
			CollectionElement<E> found = search(searchFor(value, 0), SortedSearchFilter.PreferLess);
			if (found != null) {
				return ofMutableElement(found.getElementId(), el -> {
					int compare = comparator().compare(value, el.get());
					if (compare == 0)
						return StdMsg.ELEMENT_EXISTS;
					else
						return el.canAdd(value, compare < 0);
				});
			} else
				return null;
		}
	}

	@Override
	default CollectionElement<E> addElement(E value, boolean first) {
		if (!belongs(value))
			throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
		try (Transaction t = lock(true, null)) {
			CollectionElement<E> found = search(searchFor(value, 0), SortedSearchFilter.PreferLess);
			if (found != null) {
				ElementId id = ofMutableElement(found.getElementId(), el -> {
					int compare = comparator().compare(value, el.get());
					if (compare == 0)
						return null;
					else
						return el.add(value, compare < 0);
				});
				return id == null ? null : getElement(id);
			} else
				return addIfEmpty(value);
		}
	}

	CollectionElement<E> addIfEmpty(E value) throws IllegalStateException;

	@Override
	default boolean addAll(int index, Collection<? extends E> c) {
		if (c.isEmpty())
			return false;
		try (Transaction t = lock(true, null); Transaction t2 = Transactable.lock(c, false, null)) {
			if (isEmpty())
				return addAll(c);
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			int size = size();
			if (index > size)
				throw new IndexOutOfBoundsException(index + " of " + size);
			E low, high;
			if (index == 0) {
				low = null;
				high = get(0);
			} else {
				CollectionElement<E> lowEl = getElement(index);
				low = lowEl.get();
				high = index == size ? null : getAdjacentElement(lowEl.getElementId(), true).get();
			}
			return subSet(low, false, high, false).addAll(c);
		}
	}

	@Override
	default BetterSortedSet<E> with(E... values) {
		BetterSet.super.with(values);
		return this;
	}

	@Override
	default boolean remove(Object c) {
		return BetterList.super.remove(c);
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		return BetterList.super.removeAll(c);
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		return BetterList.super.retainAll(c);
	}

	default Comparable<? super E> searchFor(E value, int onExact) {
		return v -> {
			int compare = comparator().compare(value, v);
			if (compare == 0)
				compare = onExact;
			return compare;
		};
	}

	int indexFor(Comparable<? super E> search);

	@Override
	default int indexOf(Object o) {
		return BetterList.super.indexOf(o);
	}

	@Override
	default int lastIndexOf(Object o) {
		return BetterList.super.lastIndexOf(o);
	}

	/**
	 * Searches this sorted set for an element
	 *
	 * @param search The search to navigate through this set for the target value. The search must follow this set's {@link #comparator()
	 *        order}.
	 * @param filter The filter on the result
	 * @return The element that is the best found result of the search, or null if this set is empty or does not contain any element
	 *         matching the given filter
	 */
	CollectionElement<E> search(Comparable<? super E> search, SortedSearchFilter filter);

	@Override
	default CollectionElement<E> getElement(E value, boolean first) {
		return search(searchFor(value, 0), SortedSearchFilter.OnlyMatch);
	}

	@Override
	default E first() {
		return getFirst();
	}

	@Override
	default E last() {
		return getLast();
	}

	@Override
	default E pollLast() {
		return BetterList.super.pollLast();
	}

	@Override
	default E pollFirst() {
		return BetterList.super.pollFirst();
	}

	@Override
	default E floor(E e) {
		CollectionElement<E> element = search(searchFor(e, 0), SortedSearchFilter.Less);
		return element == null ? null : element.get();
	}

	@Override
	default E lower(E e) {
		CollectionElement<E> element = search(searchFor(e, -1), SortedSearchFilter.Less);
		return element == null ? null : element.get();
	}

	@Override
	default E ceiling(E e) {
		CollectionElement<E> element = search(searchFor(e, 0), SortedSearchFilter.Greater);
		return element == null ? null : element.get();
	}

	@Override
	default E higher(E e) {
		CollectionElement<E> element = search(searchFor(e, 1), SortedSearchFilter.Greater);
		return element == null ? null : element.get();
	}

	@Override
	default BetterSortedSet<E> reverse() {
		return new ReversedSortedSet<>(this);
	}

	@Override
	default BetterSortedSet<E> descendingSet() {
		return reverse();
	}

	@Override
	default Iterator<E> descendingIterator() {
		return reverse().iterator();
	}

	@Override
	default BetterSortedSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return subSet(searchFor(fromElement, fromInclusive ? 0 : 1), searchFor(toElement, toInclusive ? 0 : -1));
	}

	default BetterSortedSet<E> subSet(Comparable<? super E> from, Comparable<? super E> to) {
		return new BetterSubSet<>(this, from, to);
	}

	@Override
	default BetterSortedSet<E> headSet(E toElement, boolean inclusive) {
		return subSet(null, searchFor(toElement, inclusive ? 0 : -1));
	}

	@Override
	default BetterSortedSet<E> tailSet(E fromElement, boolean inclusive) {
		return subSet(searchFor(fromElement, inclusive ? 0 : 1), null);
	}

	@Override
	default BetterSortedSet<E> subSet(E fromElement, E toElement) {
		return subSet(fromElement, true, toElement, false);
	}

	@Override
	default BetterSortedSet<E> headSet(E toElement) {
		return headSet(toElement, false);
	}

	@Override
	default BetterSortedSet<E> tailSet(E fromElement) {
		return tailSet(fromElement, true);
	}

	@Override
	void clear();

	/**
	 * Implements {@link BetterSortedSet#subSet(Comparable, Comparable)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class BetterSubSet<E> implements BetterSortedSet<E> {
		private final BetterSortedSet<E> theWrapped;

		private final Comparable<? super E> from;
		private final Comparable<? super E> to;

		public BetterSubSet(BetterSortedSet<E> set, Comparable<? super E> from, Comparable<? super E> to) {
			theWrapped = set;
			this.from = from;
			this.to = to;
		}

		public BetterSortedSet<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theWrapped.lock(write, structural, cause);
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return theWrapped.getStamp(structuralOnly);
		}

		public Comparable<? super E> getFrom() {
			return from;
		}

		public Comparable<? super E> getTo() {
			return to;
		}

		public int isInRange(E value) {
			if (from != null && from.compareTo(value) > 0)
				return -1;
			if (to != null && to.compareTo(value) < 0)
				return 1;
			return 0;
		}

		protected Comparable<E> boundSearch(Comparable<? super E> search) {
			return v -> {
				int compare = isInRange(v);
				if (compare == 0)
					compare = search.compareTo(v);
				return compare;
			};
		}

		@Override
		public boolean belongs(Object o) {
			return theWrapped.belongs(o) && isInRange((E) o) == 0;
		}

		/** @return The first index in the wrapped sorted set that is included in this set */
		protected int getMinIndex() {
			if (from == null)
				return 0;
			int index = theWrapped.indexFor(from);
			if (index > 0)
				return index;
			else
				return -index - 1;
		}

		/** @return The last index in the wrapped */
		protected int getMaxIndex() {
			if (to == null)
				return theWrapped.size() - 1;
			int index = theWrapped.indexFor(to);
			if (index > 0)
				return index;
			else
				return -index - 2;
		}

		@Override
		public Comparator<? super E> comparator() {
			return theWrapped.comparator();
		}

		@Override
		public int size() {
			int minIndex = getMinIndex();
			int maxIndex = getMaxIndex();
			return maxIndex - minIndex + 1; // Both minIndex and maxIndex are included here
		}

		@Override
		public boolean isEmpty() {
			return getMinIndex() > getMaxIndex(); // Both minIndex and maxIndex are included here
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			return theWrapped.indexFor(boundSearch(search));
		}

		@Override
		public int getElementsBefore(ElementId id) {
			int wIndex = theWrapped.getElementsBefore(id);
			int minIdx = getMinIndex();
			if (wIndex < minIdx)
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			if (wIndex >= getMaxIndex())
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return wIndex - minIdx;
		}

		@Override
		public int getElementsAfter(ElementId id) {
			int wIndex = theWrapped.getElementsBefore(id);
			int maxIdx = getMaxIndex();
			if (wIndex >= maxIdx)
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			if (wIndex < getMinIndex())
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return maxIdx - wIndex;
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
		public boolean addAll(int index, Collection<? extends E> c) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		private int checkIndex(int index, boolean includeTerminus) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			int min = getMinIndex();
			int max = getMaxIndex();
			if (index > max - min || (index == max - min && !includeTerminus))
				throw new IndexOutOfBoundsException(index + " of " + (max - min));
			return min + index;
		}

		@Override
		public E get(int index) {
			return theWrapped.get(checkIndex(index, false));
		}

		@Override
		public CollectionElement<E> addIfEmpty(E value) throws IllegalStateException {
			if (!belongs(value))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			return theWrapped.addElement(value, true);
		}

		@Override
		public E set(int index, E element) {
			if (!belongs(element))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			try (Transaction t = lock(true, null)) {
				return theWrapped.set(checkIndex(index, false), element);
			}
		}

		@Override
		public String canAdd(E value) {
			if (!belongs(value))
				return StdMsg.ILLEGAL_ELEMENT;
			return theWrapped.canAdd(value);
		}

		@Override
		public void add(int index, E element) {
			if (!belongs(element))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			try (Transaction t = lock(true, null)) {
				theWrapped.add(checkIndex(index, true), element);
			}
		}

		@Override
		public E remove(int index) {
			return theWrapped.remove(checkIndex(index, false));
		}

		@Override
		public CollectionElement<E> getElement(int index) {
			return theWrapped.getElement(checkIndex(index, false));
		}

		@Override
		public CollectionElement<E> getElement(E value, boolean first) {
			if (!belongs(value))
				return null;
			return theWrapped.getElement(value, first);
		}

		@Override
		public CollectionElement<E> getElement(ElementId id) {
			CollectionElement<E> el = theWrapped.getElement(id);
			if (isInRange(el.get()) != 0)
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return el;
		}

		@Override
		public CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next) {
			CollectionElement<E> el = theWrapped.getAdjacentElement(elementId, next);
			if (isInRange(el.get()) != 0)
				return null;
			return el;
		}

		@Override
		public MutableCollectionElement<E> mutableElement(ElementId id) {
			MutableCollectionElement<E> el = theWrapped.mutableElement(id);
			if (isInRange(el.get()) != 0)
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return new BoundedMutableElement(el);
		}

		@Override
		public MutableElementSpliterator<E> spliterator(ElementId element, boolean asNext) {
			try (Transaction t = lock(false, null)) {
				if (isInRange(theWrapped.getElement(element).get()) != 0)
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
				return new BoundedMutableSpliterator(theWrapped.spliterator(element, asNext));
			}
		}

		@Override
		public CollectionElement<E> search(Comparable<? super E> search, SortedSearchFilter filter) {
			return theWrapped.search(boundSearch(search), filter);
		}

		@Override
		public BetterSortedSet<E> subSet(Comparable<? super E> innerFrom, Comparable<? super E> innerTo) {
			return new BetterSubSet<>(theWrapped, boundSearch(innerFrom), boundSearch(innerTo));
		}

		@Override
		public MutableElementSpliterator<E> spliterator(boolean fromStart) {
			MutableElementSpliterator<E> wrapSpliter;
			if (fromStart) {
				if (from == null)
					wrapSpliter = theWrapped.spliterator(true);
				else {
					CollectionElement<E> element = theWrapped.search(from, SortedSearchFilter.PreferGreater);
					if (element == null)
						wrapSpliter = theWrapped.spliterator(true);
					else
						wrapSpliter = theWrapped.spliterator(element.getElementId(), from.compareTo(element.get()) <= 0);
				}
			} else {
				if (to == null)
					wrapSpliter = theWrapped.spliterator(false);
				else {
					CollectionElement<E> element = theWrapped.search(to, SortedSearchFilter.PreferLess);
					if (element == null)
						wrapSpliter = theWrapped.spliterator(false);
					else
						wrapSpliter = theWrapped.spliterator(element.getElementId(), !(to.compareTo(element.get()) >= 0));
				}
			}
			return new BoundedMutableSpliterator(wrapSpliter);
		}

		@Override
		public CollectionElement<E> addElement(E value, boolean first) {
			if (isInRange(value) != 0)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			return theWrapped.addElement(value, first);
		}

		@Override
		public boolean addAll(Collection<? extends E> values) {
			for (E value : values)
				if (isInRange(value) != 0)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			return theWrapped.addAll(values);
		}

		@Override
		public boolean removeLast(Object o) {
			if ((o != null && !theWrapped.belongs(o)) || isInRange((E) o) != 0)
				return false;
			return theWrapped.removeLast(o);
		}

		@Override
		public void clear() {
			removeIf(v -> true);
		}

		@Override
		public String toString() {
			StringBuilder ret = new StringBuilder("{");
			boolean first = true;
			for (Object value : this) {
				if (!first) {
					ret.append(", ");
				} else
					first = false;
				ret.append(value);
			}
			ret.append('}');
			return ret.toString();
		}

		private class BoundedMutableSpliterator extends MutableElementSpliterator.SimpleMutableSpliterator<E> {
			private final MutableElementSpliterator<E> theWrappedSpliter;

			BoundedMutableSpliterator(MutableElementSpliterator<E> wrappedSpliter) {
				super(BetterSubSet.this);
				theWrappedSpliter = wrappedSpliter;
			}

			@Override
			public long estimateSize() {
				return theWrappedSpliter.estimateSize();
			}

			@Override
			public long getExactSizeIfKnown() {
				return theWrappedSpliter.getExactSizeIfKnown();
			}

			@Override
			public int characteristics() {
				return DISTINCT | ORDERED | SORTED;
			}

			@Override
			public Comparator<? super E> getComparator() {
				return theWrappedSpliter.getComparator();
			}

			@Override
			protected boolean internalForElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward) {
				boolean[] success = new boolean[1];
				if (theWrappedSpliter.forElementM(el -> {
					if (isInRange(el.get()) == 0) {
						success[0] = true;
						action.accept(new BoundedMutableElement(el));
					}
				}, forward) && !success[0]) {
					// If there was a super-set element that was not in range, need to back up back to the last in-range element
					theWrappedSpliter.forElement(v -> {
					}, !forward);
				}
				return success[0];
			}

			@Override
			protected boolean internalForElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
				boolean[] success = new boolean[1];
				if (theWrappedSpliter.forElement(el -> {
					if (isInRange(el.get()) == 0) {
						success[0] = true;
						action.accept(el);
					}
				}, forward) && !success[0]) {
					// If there was a super-set element that was not in range, need to back up back to the last in-range element
					theWrappedSpliter.forElement(v -> {
					}, !forward);
				}
				return success[0];
			}

			@Override
			public MutableElementSpliterator<E> trySplit() {
				MutableElementSpliterator<E> wrapSplit = theWrappedSpliter.trySplit();
				return wrapSplit == null ? null : new BoundedMutableSpliterator(wrapSplit);
			}
		}

		class BoundedMutableElement implements MutableCollectionElement<E> {
			private final MutableCollectionElement<E> theWrappedEl;

			BoundedMutableElement(MutableCollectionElement<E> wrappedEl) {
				theWrappedEl = wrappedEl;
			}

			@Override
			public ElementId getElementId() {
				return theWrappedEl.getElementId();
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
				if (isInRange(value) != 0)
					return StdMsg.ILLEGAL_ELEMENT;
				return theWrappedEl.isAcceptable(value);
			}

			@Override
			public void set(E value) throws IllegalArgumentException, UnsupportedOperationException {
				if (isInRange(value) != 0)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				theWrappedEl.set(value);
			}

			@Override
			public String canRemove() {
				return theWrappedEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theWrappedEl.remove();
			}

			@Override
			public String canAdd(E value, boolean before) {
				if (isInRange(value) != 0)
					return StdMsg.ILLEGAL_ELEMENT;
				return theWrappedEl.canAdd(value, before);
			}

			@Override
			public ElementId add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				if (isInRange(value) != 0)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				return theWrappedEl.add(value, before);
			}

			@Override
			public String toString() {
				return theWrappedEl.toString();
			}
		}
	}

	/**
	 * Implements {@link BetterSortedSet#reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class ReversedSortedSet<E> extends ReversedList<E> implements BetterSortedSet<E> {
		public ReversedSortedSet(BetterSortedSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected BetterSortedSet<E> getWrapped() {
			return (BetterSortedSet<E>) super.getWrapped();
		}

		@Override
		public Comparator<? super E> comparator() {
			return getWrapped().comparator().reversed();
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			int index = getWrapped().indexFor(reverse(search));
			if (index >= 0)
				return size() - index - 1;
			else {
				index = -index - 1;
				index = size() - index;
				return -(index + 1);
			}
		}

		@Override
		public CollectionElement<E> addIfEmpty(E value) throws IllegalStateException {
			return CollectionElement.reverse(getWrapped().addIfEmpty(value));
		}

		private static <X> Comparable<X> reverse(Comparable<X> compare) {
			return v -> -compare.compareTo(v);
		}

		@Override
		public CollectionElement<E> search(Comparable<? super E> search, SortedSearchFilter filter) {
			return CollectionElement.reverse(getWrapped().search(reverse(search), filter.opposite()));
		}

		@Override
		public BetterSortedSet<E> reverse() {
			return getWrapped();
		}
	}
}

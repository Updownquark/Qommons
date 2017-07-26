package org.qommons.collect;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.Ternian;
import org.qommons.Transaction;
import org.qommons.collect.MutableElementHandle.StdMsg;

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
	 * @see BetterSortedSet#relative(Comparable, SortedSearchFilter)
	 * @see BetterSortedSet#forElement(Comparable, Consumer, boolean)
	 * @see BetterSortedSet#forMutableElement(Comparable, Consumer, boolean)
	 * @see BetterSortedSet#searchFor(Object, boolean)
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
	default ElementSpliterator<E> spliterator() {
		return BetterList.super.spliterator();
	}

	@Override
	default ImmutableIterator<E> iterator() {
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
		String[] msg = new String[1];
		forMutableElement(searchFor(value, 0), el -> {
			int compare = comparator().compare(value, el.get());
			if (compare == 0)
				msg[0] = StdMsg.ELEMENT_EXISTS;
			else
				msg[0] = ((MutableElementHandle<E>) el).canAdd(value, compare < 0);
		}, SortedSearchFilter.PreferLess);
		return msg[0];
	}

	@Override
	default ElementId addElement(E e) {
		try (Transaction t = lock(true, null)) {
			ElementId[] id = new ElementId[1];
			if (forMutableElement(searchFor(e, 0), el -> {
				int compare = comparator().compare(e, el.get());
				if (compare == 0)
					id[0] = null;
				else
					id[0] = ((MutableElementHandle<E>) el).add(e, compare < 0);
			}, SortedSearchFilter.PreferLess))
				return id[0];
			else
				return addIfEmpty(e);
		}
	}

	ElementId addIfEmpty(E value) throws IllegalStateException;

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
	 * Searches this sorted set for a value
	 *
	 * @param search The search to navigate through this set for the target value. The search must follow this set's {@link #comparator()
	 *        order}.
	 * @param filter The filter on the result
	 * @return The result of the search, or null if no such value was found
	 */
	default E relative(Comparable<? super E> search, SortedSearchFilter filter) {
		Object[] found = new Object[1];
		if (!forElement(search, el -> found[0] = el.get(), filter))
			return null;
		return (E) found[0];
	}

	/**
	 * @param value The value to search for
	 * @param onElement The action to perform on the element containing the given value, if found
	 * @return Whether such a value was found
	 */
	@Override
	default boolean forElement(E value, Consumer<? super ElementHandle<? extends E>> onElement, boolean first) {
		return forElement(searchFor(value, 0), onElement, SortedSearchFilter.OnlyMatch);
	}

	/**
	 * @param value The value to search for
	 * @param onElement The action to perform on the element containing the given value, if found
	 * @return Whether such a value was found
	 */
	@Override
	default boolean forMutableElement(E value, Consumer<? super MutableElementHandle<? extends E>> onElement, boolean first) {
		return forMutableElement(searchFor(value, 0), onElement, SortedSearchFilter.OnlyMatch);
	}

	/**
	 * Searches for a value in this sorted set
	 * 
	 * @param search The search to use. Must follow this sorted set's ordering.
	 * @param onValue The action to perform on the closest found value in the sorted set
	 * @param filter The filter on the result
	 * @return True unless this set was empty
	 */
	default boolean forValue(Comparable<? super E> search, Consumer<? super E> onValue, SortedSearchFilter filter) {
		return forElement(search, el -> onValue.accept(el.get()), filter);
	}

	/**
	 * Searches for an element in this sorted set
	 * 
	 * @param search The search to use. Must follow this sorted set's ordering.
	 * @param onElement The action to perform on the closest found element in the sorted set
	 * @param filter The filter on the result
	 * @return Whether an element matching the filter was found in the set
	 */
	boolean forElement(Comparable<? super E> search, Consumer<? super ElementHandle<? extends E>> onElement, SortedSearchFilter filter);

	/**
	 * Like {@link #forElement(Comparable, Consumer, SortedSearchFilter)}, but provides a mutable element
	 * 
	 * @param search The search to use. Must follow this sorted set's ordering.
	 * @param onElement The action to perform on the closest found element in the sorted set
	 * @param filter The filter on the result
	 * @return Whether an element matching the filter was found in the set
	 */
	boolean forMutableElement(Comparable<? super E> search, Consumer<? super MutableElementHandle<? extends E>> onElement,
		SortedSearchFilter filter);

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
		return relative(searchFor(e, 0), SortedSearchFilter.Less);
	}

	@Override
	default E lower(E e) {
		return relative(searchFor(e, 1), SortedSearchFilter.Less);
	}

	@Override
	default E ceiling(E e) {
		return relative(searchFor(e, 0), SortedSearchFilter.Greater);
	}

	@Override
	default E higher(E e) {
		return relative(searchFor(e, -1), SortedSearchFilter.Greater);
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

	/**
	 * Gets a spliterator for this set, positioned according to a search result. If an exact match is found, that will be the spliterator's
	 * next element (the one provided via {@link ElementSpliterator#tryAdvanceElement(Consumer)}). Otherwise, if a preferred match (one for
	 * which ({@link Comparable#compareTo(Object) search.compareTo(value)}>0)<code>==higher</code>), then that will be the next element.
	 * Otherwise the spliterator will be positioned at the beginning or the end of this set, depending on the search.
	 * 
	 * @param searchForStart The search to use to position the search
	 * @param higher Whether to prefer an element that is higher than the search or lower
	 * @return The spliterator
	 */
	default ElementSpliterator<E> spliterator(Comparable<? super E> searchForStart, boolean higher) {
		return mutableSpliterator(searchForStart, higher).immutable();
	}

	/**
	 * Like {@link #spliterator(Comparable, boolean)}, but returns a mutable spliterator.
	 * 
	 * @param searchForStart The search to use to position the search
	 * @param higher Whether to prefer an element that is higher than the search or lower
	 * @return The spliterator
	 */
	MutableElementSpliterator<E> mutableSpliterator(Comparable<? super E> searchForStart, boolean higher);

	@Override
	default BetterSortedSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return subSet(v -> {
			int compare = comparator().compare(fromElement, v);
			if (!fromInclusive && compare == 0)
				compare = 1;
			return compare;
		}, v -> {
			int compare = comparator().compare(toElement, v);
			if (!toInclusive && compare == 0)
				compare = -1;
			return compare;
		});
	}

	default BetterSortedSet<E> subSet(Comparable<? super E> from, Comparable<? super E> to) {
		return new BetterSubSet<>(this, from, to);
	}

	@Override
	default BetterSortedSet<E> headSet(E toElement, boolean inclusive) {
		return subSet(null, v -> {
			int compare = comparator().compare(toElement, v);
			if (!inclusive && compare == 0)
				compare = -1;
			return compare;
		});
	}

	@Override
	default BetterSortedSet<E> tailSet(E fromElement, boolean inclusive) {
		return subSet(v -> {
			int compare = comparator().compare(fromElement, v);
			if (!inclusive && compare == 0)
				compare = 1;
			return compare;
		}, null);
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
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
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
				return size() - 1;
			int index = theWrapped.indexFor(to);
			if (index > 0)
				return index;
			else
				return -index - 1;
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
		public ElementSpliterator<E> spliterator(int index) {
			return new BoundedSpliterator(theWrapped.spliterator(checkIndex(index, true)));
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(int index) {
			return new BoundedMutableSpliterator(theWrapped.mutableSpliterator(checkIndex(index, true)));
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
		public ElementId addIfEmpty(E value) throws IllegalStateException {
			if (!belongs(value))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			return theWrapped.addElement(value);
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
		public boolean forElement(Comparable<? super E> search, Consumer<? super ElementHandle<? extends E>> onElement,
			SortedSearchFilter filter) {
			boolean[] success = new boolean[1];
			theWrapped.forElement(boundSearch(search), el -> {
				if (isInRange(el.get()) == 0) {
					onElement.accept(el);
					success[0] = true;
				}
			}, filter);
			return success[0];
		}

		@Override
		public boolean forMutableElement(Comparable<? super E> search, Consumer<? super MutableElementHandle<? extends E>> onElement,
			SortedSearchFilter filter) {
			boolean[] success = new boolean[1];
			theWrapped.forMutableElement(boundSearch(search), el -> {
				if (isInRange(el.get()) == 0) {
					onElement.accept(el);
					success[0] = true;
				}
			}, filter);
			return success[0];
		}

		@Override
		public <T> T ofElementAt(int index, Function<? super ElementHandle<? extends E>, T> onElement) {
			int minIdx = getMinIndex();
			int maxIdx = getMaxIndex();
			if (index < 0 || index > (maxIdx - minIdx))
				throw new IndexOutOfBoundsException(index + " of " + (maxIdx - minIdx + 1));
			return theWrapped.ofElementAt(index, onElement);
		}

		@Override
		public <T> T ofMutableElementAt(int index, Function<? super MutableElementHandle<? extends E>, T> onElement) {
			int minIdx = getMinIndex();
			int maxIdx = getMaxIndex();
			if (index < 0 || index > (maxIdx - minIdx))
				throw new IndexOutOfBoundsException(index + " of " + (maxIdx - minIdx + 1));
			return theWrapped.ofMutableElementAt(index, onElement);
		}

		@Override
		public <T> T ofElementAt(ElementId elementId, Function<? super ElementHandle<? extends E>, T> onElement) {
			return theWrapped.ofElementAt(elementId, el -> {
				if (isInRange(el.get()) != 0)
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
				return onElement.apply(el);
			});
		}

		@Override
		public <T> T ofMutableElementAt(ElementId elementId, Function<? super MutableElementHandle<? extends E>, T> onElement) {
			return theWrapped.ofMutableElementAt(elementId, el -> {
				if (isInRange(el.get()) != 0)
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
				return onElement.apply(el);
			});
		}

		@Override
		public E relative(Comparable<? super E> search, SortedSearchFilter filter) {
			E v = theWrapped.relative(boundSearch(search), filter);
			return (v != null && isInRange(v) == 0) ? v : null;
		}

		@Override
		public BetterSortedSet<E> subSet(Comparable<? super E> from, Comparable<? super E> to) {
			return new BetterSubSet<>(theWrapped, boundSearch(from), boundSearch(to));
		}

		@Override
		public ElementSpliterator<E> spliterator(boolean fromStart) {
			ElementSpliterator<E> wrapSpliter;
			if (fromStart) {
				if (from == null)
					wrapSpliter = theWrapped.spliterator(true);
				else
					wrapSpliter = theWrapped.spliterator(from, true);
			} else {
				if (to == null)
					wrapSpliter = theWrapped.spliterator(false);
				else
					wrapSpliter = theWrapped.spliterator(to, false);
			}
			return new BoundedSpliterator(wrapSpliter);
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(boolean fromStart) {
			MutableElementSpliterator<E> wrapSpliter;
			if (fromStart) {
				if (from == null)
					wrapSpliter = theWrapped.mutableSpliterator(true);
				else
					wrapSpliter = theWrapped.mutableSpliterator(from, true);
			} else {
				if (to == null)
					wrapSpliter = theWrapped.mutableSpliterator(false);
				else
					wrapSpliter = theWrapped.mutableSpliterator(to, false);
			}
			return new BoundedMutableSpliterator(wrapSpliter);
		}

		@Override
		public ElementSpliterator<E> spliterator(Comparable<? super E> search, boolean higher) {
			return new BoundedSpliterator(theWrapped.spliterator(boundSearch(search), higher));
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(Comparable<? super E> search, boolean higher) {
			return new BoundedMutableSpliterator(theWrapped.mutableSpliterator(boundSearch(search), higher));
		}

		@Override
		public boolean add(E value) {
			if (isInRange(value) != 0)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			return theWrapped.add(value);
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
			mutableSpliterator().forEachElementM(el -> el.remove());
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

		private class BoundedSpliterator implements ElementSpliterator<E> {
			private final ElementSpliterator<E> theWrappedSpliter;

			BoundedSpliterator(ElementSpliterator<E> wrappedSpliter) {
				theWrappedSpliter = wrappedSpliter;
			}

			protected ElementSpliterator<E> getWrappedSpliter() {
				return theWrappedSpliter;
			}

			@Override
			public long estimateSize() {
				return theWrappedSpliter.estimateSize();
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
			public boolean tryAdvanceElement(Consumer<? super ElementHandle<E>> action) {
				boolean[] success = new boolean[1];
				if (theWrappedSpliter.tryAdvanceElement(el -> {
					if (isInRange(el.get()) == 0) {
						success[0] = true;
						action.accept(el);
					}
				}) && !success[0]) {
					// If there was a super-set element that was not in range, need to back up back to the last in-range element
					theWrappedSpliter.tryReverse(v -> {
					});
				}
				return success[0];
			}

			@Override
			public boolean tryReverseElement(Consumer<? super ElementHandle<E>> action) {
				boolean[] success = new boolean[1];
				if (theWrappedSpliter.tryReverseElement(el -> {
					if (isInRange(el.get()) == 0) {
						success[0] = true;
						action.accept(el);
					}
				}) && !success[0]) {
					// If there was a super-set element that was not in range, need to back up back to the last in-range element
					theWrappedSpliter.tryAdvance(v -> {
					});
				}
				return success[0];
			}

			@Override
			public ElementSpliterator<E> trySplit() {
				ElementSpliterator<E> wrapSplit = theWrappedSpliter.trySplit();
				return wrapSplit == null ? null : new BoundedSpliterator(wrapSplit);
			}
		}

		private class BoundedMutableSpliterator extends BoundedSpliterator implements MutableElementSpliterator<E> {
			BoundedMutableSpliterator(MutableElementSpliterator<E> wrappedSpliter) {
				super(wrappedSpliter);
			}

			@Override
			protected MutableElementSpliterator<E> getWrappedSpliter() {
				return (MutableElementSpliterator<E>) super.getWrappedSpliter();
			}

			@Override
			public boolean tryAdvanceElementM(Consumer<? super MutableElementHandle<E>> action) {
				boolean[] success = new boolean[1];
				if (getWrappedSpliter().tryAdvanceElementM(el -> {
					if (isInRange(el.get()) == 0) {
						success[0] = true;
						action.accept(new BoundedMutableElement<>(el));
					}
				}) && !success[0]) {
					// If there was a super-set element that was not in range, need to back up back to the last in-range element
					getWrappedSpliter().tryReverse(v -> {
					});
				}
				return success[0];
			}

			@Override
			public boolean tryReverseElementM(Consumer<? super MutableElementHandle<E>> action) {
				boolean[] success = new boolean[1];
				if (getWrappedSpliter().tryReverseElementM(el -> {
					if (isInRange(el.get()) == 0) {
						success[0] = true;
						action.accept(new BoundedMutableElement<>(el));
					}
				}) && !success[0]) {
					// If there was a super-set element that was not in range, need to back up back to the last in-range element
					getWrappedSpliter().tryAdvance(v -> {
					});
				}
				return success[0];
			}

			@Override
			public MutableElementSpliterator<E> trySplit() {
				MutableElementSpliterator<E> wrapSplit = getWrappedSpliter().trySplit();
				return wrapSplit == null ? null : new BoundedMutableSpliterator(wrapSplit);
			}
		}

		class BoundedMutableElement<T extends E> implements MutableElementHandle<T> {
			private final MutableElementHandle<T> theWrappedEl;

			BoundedMutableElement(MutableElementHandle<T> wrappedEl) {
				theWrappedEl = wrappedEl;
			}

			@Override
			public ElementId getElementId() {
				return theWrappedEl.getElementId();
			}

			@Override
			public T get() {
				return theWrappedEl.get();
			}

			@Override
			public String isEnabled() {
				return theWrappedEl.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				if (isInRange(value) != 0)
					return StdMsg.ILLEGAL_ELEMENT;
				return theWrappedEl.isAcceptable(value);
			}

			@Override
			public void set(T value) throws IllegalArgumentException, UnsupportedOperationException {
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
			public String canAdd(T value, boolean before) {
				if (isInRange(value) != 0)
					return StdMsg.ILLEGAL_ELEMENT;
				return theWrappedEl.canAdd(value, before);
			}

			@Override
			public ElementId add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
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
			int index = getWrapped().indexFor(search);
			if (index >= 0)
				return size() - index - 1;
			else {
				index = -index - 1;
				index = size() - index;
				return -(index + 1);
			}
		}

		private static <X> Comparable<X> reverse(Comparable<X> compare) {
			return v -> -compare.compareTo(v);
		}

		@Override
		public E relative(Comparable<? super E> search, SortedSearchFilter filter) {
			return getWrapped().relative(search, filter.opposite());
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(Comparable<? super E> value, boolean higher) {
			return getWrapped().mutableSpliterator(reverse(value), !higher).reverse();
		}

		@Override
		public BetterSortedSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
			return getWrapped().subSet(toElement, toInclusive, fromElement, fromInclusive).reverse();
		}

		@Override
		public BetterSortedSet<E> headSet(E toElement, boolean inclusive) {
			return getWrapped().tailSet(toElement, inclusive).reverse();
		}

		@Override
		public BetterSortedSet<E> tailSet(E fromElement, boolean inclusive) {
			return getWrapped().headSet(fromElement, inclusive).reverse();
		}

		@Override
		public BetterSortedSet<E> reverse() {
			return getWrapped();
		}

		@Override
		public boolean forElement(Comparable<? super E> search, Consumer<? super ElementHandle<? extends E>> onElement,
			SortedSearchFilter filter) {
			return getWrapped().forElement(reverse(search), el -> onElement.accept(el.reverse()), filter.opposite());
		}

		@Override
		public boolean forMutableElement(Comparable<? super E> search, Consumer<? super MutableElementHandle<? extends E>> onElement,
			SortedSearchFilter filter) {
			return getWrapped().forMutableElement(reverse(search), el -> onElement.accept(el.reverse()), filter.opposite());
		}

		@Override
		public ElementId addIfEmpty(E value) throws IllegalStateException {
			return ElementId.reverse(getWrapped().addIfEmpty(value));
		}
	}
}

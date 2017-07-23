package org.qommons.collect;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.collect.MutableElementHandle.StdMsg;

import com.google.common.reflect.TypeToken;

public interface BetterSortedSet<E> extends BetterList<E>, NavigableSet<E> {
	TypeToken<E> getType();

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
	 * Returns a value at or adjacent to another value
	 *
	 * @param value The relative value
	 * @param up Whether to get the closest value greater or less than the given value
	 * @return An observable value with the result of the operation
	 */
	E relative(Comparable<? super E> search, boolean up);

	/**
	 * @param value The value to search for
	 * @param onElement The action to perform on the element containing the given value, if found
	 * @return Whether such a value was found
	 */
	@Override
	default boolean forElement(E value, Consumer<? super ElementHandle<? extends E>> onElement, boolean first) {
		boolean[] success = new boolean[1];
		forElement(v -> comparator().compare(value, v), el -> {
			if (comparator().compare(value, el.get()) == 0) {
				onElement.accept(el);
				success[0] = true;
			}
		}, true);
		return success[0];
	}

	/**
	 * @param value The value to search for
	 * @param onElement The action to perform on the element containing the given value, if found
	 * @return Whether such a value was found
	 */
	@Override
	default boolean forMutableElement(E value, Consumer<? super MutableElementHandle<? extends E>> onElement, boolean first) {
		boolean[] success = new boolean[1];
		forMutableElement(v -> comparator().compare(value, v), el -> {
			if (comparator().compare(value, el.get()) == 0) {
				onElement.accept(el);
				success[0] = true;
			}
		}, true);
		return success[0];
	}

	default boolean forValue(Comparable<? super E> search, Consumer<? super E> onValue, boolean up) {
		return forElement(search, el -> onValue.accept(el.get()), up);
	}

	boolean forElement(Comparable<? super E> search, Consumer<? super ElementHandle<? extends E>> onElement, boolean up);

	boolean forMutableElement(Comparable<? super E> search, Consumer<? super MutableElementHandle<? extends E>> onElement, boolean up);

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
		return relative(v -> comparator().compare(e, v), false);
	}

	@Override
	default E lower(E e) {
		return relative(v -> {
			int compare = comparator().compare(e, v);
			if (compare == 0)
				return 1;
			return compare;
		}, false);
	}

	@Override
	default E ceiling(E e) {
		return relative(v -> comparator().compare(e, v), true);
	}

	@Override
	default E higher(E e) {
		return relative(v -> {
			int compare = comparator().compare(e, v);
			if (compare == 0)
				compare = -1;
			return compare;
		}, true);
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

	default ElementSpliterator<E> spliterator(Comparable<? super E> search, boolean fromStart) {
		return mutableSpliterator(search, fromStart).immutable();
	}

	MutableElementSpliterator<E> mutableSpliterator(Comparable<? super E> search, boolean fromStart);

	/**
	 * A sub-set of this set. Like {@link #subSet(Object, boolean, Object, boolean)}, but may be reversed.
	 *
	 * @param fromElement The minimum bounding element for the sub set
	 * @param fromInclusive Whether the minimum bound will be included in the sub set (if present in this set)
	 * @param toElement The maximum bounding element for the sub set
	 * @param toInclusive Whether the maximum bound will be included in the sub set (if present in this set)
	 * @return The sub set
	 */
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
		public TypeToken<E> getType() {
			return theWrapped.getType();
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
		public E set(int index, E element) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canAdd(E value) {
			if (!belongs(value))
				return StdMsg.ILLEGAL_ELEMENT;
			return theWrapped.canAdd(value);
		}

		@Override
		public void add(int index, E element) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public E remove(int index) {
			return theWrapped.remove(checkIndex(index, false));
		}

		@Override
		public boolean forValue(Comparable<? super E> search, Consumer<? super E> onValue, boolean up) {
			boolean[] success = new boolean[1];
			theWrapped.forValue(search, v -> {
				if (isInRange(v) == 0) {
					onValue.accept(v);
					success[0] = true;
				}
			}, up);
			return success[0];
		}

		@Override
		public boolean forElement(Comparable<? super E> search, Consumer<? super ElementHandle<? extends E>> onElement, boolean up) {
			boolean[] success = new boolean[1];
			theWrapped.forElement(search, el -> {
				if (isInRange(el.get()) == 0) {
					onElement.accept(el);
					success[0] = true;
				}
			}, up);
			return success[0];
		}

		@Override
		public boolean forMutableElement(Comparable<? super E> search, Consumer<? super MutableElementHandle<? extends E>> onElement,
			boolean up) {
			boolean[] success = new boolean[1];
			theWrapped.forMutableElement(search, el -> {
				if (isInRange(el.get()) == 0) {
					onElement.accept(el);
					success[0] = true;
				}
			}, up);
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
		public E relative(Comparable<? super E> search, boolean up) {
			E v = theWrapped.relative(boundSearch(search), up);
			return isInRange(v) == 0 ? v : null;
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
		public ElementSpliterator<E> spliterator(Comparable<? super E> search, boolean up) {
			return new BoundedSpliterator(theWrapped.spliterator(boundSearch(search), up));
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(Comparable<? super E> search, boolean up) {
			return new BoundedMutableSpliterator(theWrapped.mutableSpliterator(boundSearch(search), up));
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
			if ((o != null && !theWrapped.getType().getRawType().isInstance(o)) || isInRange((E) o) != 0)
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
			public void add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				if (isInRange(value) != 0)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				theWrappedEl.add(value, before);
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
		public TypeToken<E> getType() {
			return getWrapped().getType();
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

		@Override
		public E relative(Comparable<? super E> search, boolean up) {
			return getWrapped().relative(search, !up);
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(Comparable<? super E> value, boolean fromStart) {
			return getWrapped().mutableSpliterator(value, !fromStart).reverse();
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
			return (BetterSortedSet<E>) super.reverse();
		}

		@Override
		public boolean forValue(Comparable<? super E> search, Consumer<? super E> onValue, boolean up) {
			return getWrapped().forValue(search, onValue, !up);
		}

		@Override
		public boolean forElement(Comparable<? super E> search, Consumer<? super ElementHandle<? extends E>> onElement, boolean up) {
			return getWrapped().forElement(search, el -> onElement.accept(el.reverse()), !up);
		}

		@Override
		public boolean forMutableElement(Comparable<? super E> search, Consumer<? super MutableElementHandle<? extends E>> onElement,
			boolean up) {
			return getWrapped().forMutableElement(search, el -> onElement.accept(el.reverse()), !up);
		}
	}
}

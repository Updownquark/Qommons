package org.qommons.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NavigableSet;
import java.util.function.Consumer;

import org.qommons.collect.CollectionElement.StdMsg;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

public interface BetterSortedSet<E> extends ReversibleList<E>, NavigableSet<E> {
	TypeToken<E> getType();

	@Override
	default ReversibleSpliterator<E> spliterator() {
		return ReversibleList.super.spliterator();
	}

	@Override
	default ImmutableIterator<E> iterator() {
		return ReversibleList.super.iterator();
	}

	@Override
	default boolean contains(Object c) {
		return ReversibleList.super.contains(c);
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		return ReversibleList.super.containsAll(c);
	}

	@Override
	default boolean remove(Object c) {
		return ReversibleList.super.remove(c);
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		return ReversibleList.super.removeAll(c);
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		return ReversibleList.super.retainAll(c);
	}

	@Override
	default void clear() {
		ReversibleList.super.clear();
	}

	int indexFor(Comparable<? super E> search);

	@Override
	default int indexOf(Object o) {
		if (!belongs(o))
			return -1;
		return indexFor(v -> comparator().compare((E) o, v));
	}

	@Override
	default int lastIndexOf(Object o) {
		return indexOf(o);
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
	default boolean forElement(E value, Consumer<? super CollectionElement<? extends E>> onElement, boolean first) {
		boolean[] success = new boolean[1];
		forElement(v -> comparator().compare(value, v), true, el -> {
			if (comparator().compare(value, el.get()) == 0) {
				onElement.accept(el);
				success[0] = true;
			}
		});
		return success[0];
	}

	boolean forValue(Comparable<? super E> search, boolean up, Consumer<? super E> onValue);

	boolean forElement(Comparable<? super E> search, boolean up, Consumer<? super CollectionElement<? extends E>> onElement);

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
		return ReversibleList.super.pollLast();
	}

	@Override
	default E pollFirst() {
		return ReversibleList.super.pollFirst();
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

	default ReversibleSpliterator<E> spliterator(Comparable<? super E> search, boolean up) {
		return mutableSpliterator(search, up).immutable();
	}

	ReversibleElementSpliterator<E> mutableSpliterator(Comparable<? super E> search, boolean up);

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
		public Object[] toArray() {
			// TODO Auto-generated method stub
		}

		@Override
		public <T> T[] toArray(T[] a) {
			// TODO Auto-generated method stub
		}

		@Override
		public ReversibleSpliterator<E> spliterator(int index) {
			// TODO Auto-generated method stub
		}

		@Override
		public ReversibleElementSpliterator<E> mutableSpliterator(int index) {
			// TODO Auto-generated method stub
		}

		@Override
		public ReversibleList<E> subList(int fromIndex, int toIndex) {
			// TODO Auto-generated method stub
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public E get(int index) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public E set(int index, E element) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void add(int index, E element) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public E remove(int index) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			// TODO Auto-generated method stub
		}

		@Override
		public boolean forValue(Comparable<? super E> search, boolean up, Consumer<? super E> onValue) {
			boolean[] success = new boolean[1];
			theWrapped.forValue(search, up, v -> {
				if (isInRange(v) == 0) {
					onValue.accept(v);
					success[0] = true;
				}
			});
			return success[0];
		}

		@Override
		public boolean forElement(Comparable<? super E> search, boolean up, Consumer<? super CollectionElement<? extends E>> onElement) {
			boolean[] success = new boolean[1];
			theWrapped.forElement(search, up, el -> {
				if (isInRange(el.get()) == 0) {
					onElement.accept(el);
					success[0] = true;
				}
			});
			return success[0];
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
		public ReversibleSpliterator<E> spliterator(boolean fromStart) {
			ReversibleSpliterator<E> wrapSpliter;
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
		public ReversibleElementSpliterator<E> mutableSpliterator(boolean fromStart) {
			ReversibleElementSpliterator<E> wrapSpliter;
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
		public ReversibleSpliterator<E> spliterator(Comparable<? super E> search, boolean up) {
			return new BoundedSpliterator(theWrapped.spliterator(boundSearch(search), up));
		}

		@Override
		public ReversibleElementSpliterator<E> mutableSpliterator(Comparable<? super E> search, boolean up) {
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
			SimpleCause.doWith(new SimpleCause(), c -> mutableSpliterator().forEachElement(el -> el.remove(c)));
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

		private class BoundedSpliterator implements ReversibleSpliterator<E> {
			private final ReversibleSpliterator<E> theWrappedSpliter;

			BoundedSpliterator(ReversibleSpliterator<E> wrappedSpliter) {
				theWrappedSpliter = wrappedSpliter;
			}

			protected ReversibleSpliterator<E> getWrappedSpliter() {
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
			public boolean tryAdvance(Consumer<? super E> action) {
				boolean[] success = new boolean[1];
				if (theWrappedSpliter.tryAdvance(v -> {
					if (isInRange(v) == 0) {
						success[0] = true;
						action.accept(v);
					}
				}) && !success[0]) {
					// If there was a super-set element that was not in range, need to back up back to the last in-range element
					theWrappedSpliter.tryReverse(v -> {
					});
				}
				return success[0];
			}

			@Override
			public boolean tryReverse(Consumer<? super E> action) {
				boolean[] success = new boolean[1];
				if (theWrappedSpliter.tryReverse(v -> {
					if (isInRange(v) == 0) {
						success[0] = true;
						action.accept(v);
					}
				}) && !success[0]) {
					// If there was a super-set element that was not in range, need to back up back to the last in-range element
					theWrappedSpliter.tryAdvance(v -> {
					});
				}
				return success[0];
			}

			@Override
			public void forEachRemaining(Consumer<? super E> action) {
				boolean[] lastOutOfRange = new boolean[1];
				theWrappedSpliter.forEachRemaining(v -> {
					if (isInRange(v) == 0)
						action.accept(v);
					else
						lastOutOfRange[0] = true;
				});
				if (lastOutOfRange[0]) {
					// Need to back up back to the last in-range element
					theWrappedSpliter.tryReverse(v -> {
					});
				}
			}

			@Override
			public void forEachReverse(Consumer<? super E> action) {
				boolean[] lastOutOfRange = new boolean[1];
				theWrappedSpliter.forEachReverse(v -> {
					if (isInRange(v) == 0)
						action.accept(v);
					else
						lastOutOfRange[0] = true;
				});
				if (lastOutOfRange[0]) {
					// Need to back up back to the last in-range element
					theWrappedSpliter.tryAdvance(v -> {
					});
				}
			}

			@Override
			public ReversibleSpliterator<E> trySplit() {
				ReversibleSpliterator<E> wrapSplit = theWrappedSpliter.trySplit();
				return wrapSplit == null ? null : new BoundedSpliterator(wrapSplit);
			}
		}

		private class BoundedMutableSpliterator extends BoundedSpliterator implements ReversibleElementSpliterator<E> {
			BoundedMutableSpliterator(ReversibleElementSpliterator<E> wrappedSpliter) {
				super(wrappedSpliter);
			}

			@Override
			protected ReversibleElementSpliterator<E> getWrappedSpliter() {
				return (ReversibleElementSpliterator<E>) super.getWrappedSpliter();
			}

			@Override
			public TypeToken<E> getType() {
				return BetterSubSet.this.getType();
			}

			@Override
			public boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action) {
				boolean[] success = new boolean[1];
				if (getWrappedSpliter().tryAdvanceElement(el -> {
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
			public boolean tryReverseElement(Consumer<? super CollectionElement<E>> action) {
				boolean[] success = new boolean[1];
				if (getWrappedSpliter().tryReverseElement(el -> {
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
			public void forEachElement(Consumer<? super CollectionElement<E>> action) {
				boolean[] lastOutOfRange = new boolean[1];
				getWrappedSpliter().forEachElement(el -> {
					if (isInRange(el.get()) == 0)
						action.accept(new BoundedMutableElement<>(el));
					else
						lastOutOfRange[0] = true;
				});
				if (lastOutOfRange[0]) {
					// Need to back up back to the last in-range element
					getWrappedSpliter().tryReverse(v -> {
					});
				}
			}

			@Override
			public void forEachReverseElement(Consumer<? super CollectionElement<E>> action) {
				boolean[] lastOutOfRange = new boolean[1];
				getWrappedSpliter().forEachReverseElement(el -> {
					if (isInRange(el.get()) == 0)
						action.accept(new BoundedMutableElement<>(el));
					else
						lastOutOfRange[0] = true;
				});
				if (lastOutOfRange[0]) {
					// Need to back up back to the last in-range element
					getWrappedSpliter().tryAdvance(v -> {
					});
				}
			}

			@Override
			public ReversibleElementSpliterator<E> trySplit() {
				ReversibleElementSpliterator<E> wrapSplit = getWrappedSpliter().trySplit();
				return wrapSplit == null ? null : new BoundedMutableSpliterator(wrapSplit);
			}
		}

		class BoundedMutableElement<T extends E> implements CollectionElement<T> {
			private final CollectionElement<T> theWrappedEl;

			BoundedMutableElement(CollectionElement<T> wrappedEl) {
				theWrappedEl = wrappedEl;
			}

			@Override
			public TypeToken<T> getType() {
				return theWrappedEl.getType();
			}

			@Override
			public T get() {
				return theWrappedEl.get();
			}

			@Override
			public Value<String> isEnabled() {
				return theWrappedEl.isEnabled();
			}

			@Override
			public <V extends T> String isAcceptable(V value) {
				if (isInRange(value) != 0)
					return StdMsg.ILLEGAL_ELEMENT;
				return theWrappedEl.isAcceptable(value);
			}

			@Override
			public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
				if (isInRange(value) != 0)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				return theWrappedEl.set(value, cause);
			}

			@Override
			public String canRemove() {
				return theWrappedEl.canRemove();
			}

			@Override
			public void remove(Object cause) throws UnsupportedOperationException {
				theWrappedEl.remove(cause);
			}

			@Override
			public String canAdd(T value, boolean before) {
				if (isInRange(value) != 0)
					return StdMsg.ILLEGAL_ELEMENT;
				return theWrappedEl.canAdd(value, before);
			}

			@Override
			public void add(T value, boolean before, Object cause) throws UnsupportedOperationException, IllegalArgumentException {
				if (isInRange(value) != 0)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				theWrappedEl.add(value, before, cause);
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
		public ReversibleElementSpliterator<E> mutableSpliterator(Comparable<? super E> value, boolean up) {
			return getWrapped().mutableSpliterator(value, !up);
		}

		@Override
		public BetterSortedSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
			return getWrapped().subSet(toElement, toInclusive, fromElement, fromInclusive);
		}

		@Override
		public BetterSortedSet<E> headSet(E toElement, boolean inclusive) {
			return getWrapped().tailSet(toElement, inclusive);
		}

		@Override
		public BetterSortedSet<E> tailSet(E fromElement, boolean inclusive) {
			return getWrapped().headSet(fromElement, inclusive);
		}

		@Override
		public BetterSortedSet<E> reverse() {
			return (BetterSortedSet<E>) super.reverse();
		}

		@Override
		public boolean forValue(Comparable<? super E> search, boolean up, Consumer<? super E> onValue) {
			return getWrapped().forValue(search, !up, onValue);
		}

		@Override
		public boolean forElement(Comparable<? super E> search, boolean up, Consumer<? super CollectionElement<? extends E>> onElement) {
			return getWrapped().forElement(search, !up, onElement);
		}
	}
}

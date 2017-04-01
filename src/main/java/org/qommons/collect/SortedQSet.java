package org.qommons.collect;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.qommons.Equalizer;
import org.qommons.Transaction;
import org.qommons.collect.SortedQSet.PartialSortedSetImpl;

import com.google.common.reflect.TypeToken;

/**
 * A {@link NavigableSet}-implementing {@link Qollection}
 * 
 * @param <E> The type of elements in the set
 */
public interface SortedQSet<E> extends OrderedQSet<E>, ReversibleQollection<E>, TransactableSortedSet<E> {
	// De-conflicting declarations required by the compiler

	@Override
	default Iterator<E> iterator() {
		return OrderedQSet.super.iterator();
	}

	@Override
	default E pollFirst() {
		return ReversibleQollection.super.pollFirst();
	}

	@Override
	default E pollLast() {
		return ReversibleQollection.super.pollLast();
	}

	/**
	 * Returns a value at or adjacent to another value
	 *
	 * @param value The relative value
	 * @param up Whether to get the closest value greater or less than the given value
	 * @param withValue Whether to return the given value if it exists in the map
	 * @return An observable value with the result of the operation
	 */
	default E relative(E value, boolean up, boolean withValue) {
		if (up)
			return tailSet(value, withValue).getFirst();
		else
			return headSet(value, withValue).getLast();
	}

	/**
	 * <p>
	 * Starts iteration in either direction from a starting point.
	 * </p>
	 *
	 * <p>
	 * This method is used by the default implementation of {@link #subSet(Object, boolean, Object, boolean, boolean)}, so implementations
	 * and sub-interfaces should <b>NOT</b> call any of the sub-set methods from this method unless the subSet method itself is overridden.
	 * </p>
	 *
	 * @param element The element to start iteration at
	 * @param included Whether to include the given element in the iteration
	 * @param reversed Whether to iterate backward or forward from the given element
	 * @return A Quiterator that starts iteration from the given element
	 */
	Quiterator<E> spliterateFrom(E element, boolean included, boolean reversed);

	/**
	 * Wraps {@link #spliterateFrom(Object, boolean, boolean)} in an Iterable
	 * 
	 * @param element The element to start iteration at
	 * @param included Whether to include the given element in the iteration
	 * @param reversed Whether to iterate backward or forward from the given element
	 * @return An iterable that starts iteration from the given element
	 */
	default Iterable<E> iterateFrom(E element, boolean included, boolean reversed) {
		return () -> new Qollection.QuiteratorIterator<>(spliterateFrom(element, included, reversed));
	}

	/**
	 * A sub-set of this set. Like {@link #subSet(Object, boolean, Object, boolean)}, but may be reversed.
	 *
	 * @param fromElement The minimum bounding element for the sub set
	 * @param fromInclusive Whether the minimum bound will be included in the sub set (if present in this set)
	 * @param toElement The maximum bounding element for the sub set
	 * @param toInclusive Whether the maximum bound will be included in the sub set (if present in this set)
	 * @param reversed Whether the returned sub set will be in the opposite order as this set
	 * @return The sub set
	 */
	default SortedQSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive, boolean reversed) {
		return new SubSortedQSet<>(this, fromElement, fromInclusive, toElement, toInclusive, reversed);
	}

	// Default implementations of redundant methods

	@Override
	default Equalizer equalizer() {
		return (o1, o2) -> {
			if (!getType().getRawType().isInstance(o1) || !getType().getRawType().isInstance(o2))
				return false;
			return comparator().compare((E) o1, (E) o2) == 0;
		};
	}

	@Override
	default Quiterator<E> spliterator() {
		return spliterateFrom(null, true, false);
	}

	@Override
	default Quiterator<E> reverseSpliterator() {
		return spliterateFrom(null, true, true);
	}

	@Override
	default SortedQSet<E> reverse() {
		return subSet(null, true, null, true, true);
	}

	@Override
	default E first() {
		if (isEmpty())
			throw new java.util.NoSuchElementException();
		return getFirst();
	}

	@Override
	default E last() {
		if (isEmpty())
			throw new java.util.NoSuchElementException();
		return getLast();
	}

	@Override
	default E floor(E e) {
		return relative(e, false, true);
	}

	@Override
	default E lower(E e) {
		return relative(e, false, false);
	}

	@Override
	default E ceiling(E e) {
		return relative(e, true, true);
	}

	@Override
	default E higher(E e) {
		return relative(e, true, false);
	}

	@Override
	default SortedQSet<E> descendingSet() {
		return reverse();
	}

	@Override
	default Iterator<E> descendingIterator() {
		return descending().iterator();
	}

	@Override
	default SortedQSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return subSet(fromElement, fromInclusive, toElement, toInclusive, false);
	}

	@Override
	default SortedQSet<E> headSet(E toElement, boolean inclusive) {
		return subSet(null, true, toElement, inclusive);
	}

	@Override
	default SortedQSet<E> tailSet(E fromElement, boolean inclusive) {
		return subSet(fromElement, inclusive, null, true);
	}

	@Override
	default SortedQSet<E> subSet(E fromElement, E toElement) {
		return subSet(fromElement, true, toElement, false);
	}

	@Override
	default SortedQSet<E> headSet(E toElement) {
		return headSet(toElement, false);
	}

	@Override
	default SortedQSet<E> tailSet(E fromElement) {
		return tailSet(fromElement, true);
	}

	// Overridden methods to return a SortedQSet

	@Override
	default SortedQSet<E> filter(Function<? super E, String> filter) {
		return (SortedQSet<E>) OrderedQSet.super.filter(filter);
	}

	@Override
	default <T> SortedQSet<T> filter(Class<T> type) {
		return (SortedQSet<T>) OrderedQSet.super.filter(type);
	}

	@Override
	default <T> SortedQSet<T> filterMap(EquivalentFilterMapDef<E, ?, T> filterMap) {
		// TODO Auto-generated method stub
	}

	@Override
	default SortedQSet<E> immutable(String modMsg) {
		return (SortedQSet<E>) OrderedQSet.super.immutable(modMsg);
	}

	@Override
	default SortedQSet<E> filterRemove(Function<? super E, String> filter) {
		return (SortedQSet<E>) OrderedQSet.super.filterRemove(filter);
	}

	@Override
	default SortedQSet<E> noRemove(String modMsg) {
		return (SortedQSet<E>) OrderedQSet.super.noRemove(modMsg);
	}

	@Override
	default SortedQSet<E> filterAdd(Function<? super E, String> filter) {
		return (SortedQSet<E>) OrderedQSet.super.filterAdd(filter);
	}

	@Override
	default SortedQSet<E> noAdd(String modMsg) {
		return (SortedQSet<E>) OrderedQSet.super.noAdd(modMsg);
	}

	@Override
	default SortedQSet<E> filterModification(Function<? super E, String> removeFilter, Function<? super E, String> addFilter) {
		return new ModFilteredSortedQSet<>(this, removeFilter, addFilter);
	}

	// Static utility methods

	static <K> SortedQSet<K> unique(Qollection<K> keyCollection, Comparator<? super K> compare) {
		// TODO Auto-generated method stub
	}

	// Implementation member classes

	/**
	 * Implements {@link SortedQSet#subSet(Object, boolean, Object, boolean, boolean)}
	 * 
	 * @param <E> The type of elements in this set
	 */
	class SubSortedQSet<E> implements SortedQSet<E> {
		public static final String NOT_IN_RANGE = "Element is outside the range of this sub-set";

		private final SortedQSet<E> theWrapped;

		private final E theMin;
		private final boolean isMinIncluded;
		private final E theMax;
		private final boolean isMaxIncluded;

		private final boolean isReversed;

		public SubSortedQSet(SortedQSet<E> set, E min, boolean includeMin, E max, boolean includeMax, boolean reversed) {
			theWrapped = set;
			theMin = min;
			isMinIncluded = includeMin;
			theMax = max;
			isMaxIncluded = includeMax;
			isReversed = reversed;
		}

		public SortedQSet<E> getWrapped() {
			return theWrapped;
		}

		public E getMin() {
			return theMin;
		}

		public boolean isMinIncluded() {
			return isMinIncluded;
		}

		public E getMax() {
			return theMax;
		}

		public boolean isMaxIncluded() {
			return isMaxIncluded;
		}

		public boolean isReversed() {
			return isReversed;
		}

		public boolean isInRange(E value) {
			Comparator<? super E> compare = theWrapped.comparator();
			if (theMin != null) {
				int comp = compare.compare(value, theMin);
				if (comp < 0 || (!isMinIncluded && comp == 0))
					return false;
			}
			if (theMax != null) {
				int comp = compare.compare(value, theMax);
				if (comp > 0 || (!isMaxIncluded && comp == 0))
					return false;
			}
			return true;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		/** @return The first index in the wrapped sorted set that is included in this set */
		protected int getMinIndex() {
			int minIndex;
			if (theMin == null)
				minIndex = 0;
			else {
				minIndex = theWrapped.indexOf(theMin);
				if (minIndex < 0)
					minIndex = -minIndex - 1; // Include the element at the insertion index
				else if (!isMinIncluded)
					minIndex++;
			}
			return minIndex;
		}

		/** @return The last index in the wrapped */
		protected int getMaxIndex() {
			int maxIndex;
			if (theMax == null)
				maxIndex = theWrapped.size() - 1;
			else {
				maxIndex = theWrapped.indexOf(theMax);
				if (maxIndex < 0) {
					maxIndex = -maxIndex - 1;
					maxIndex--; // Don't include the element at the insertion index
				} else if (!isMaxIncluded)
					maxIndex--;
			}
			return maxIndex;
		}

		@Override
		public int size() {
			int minIndex = getMinIndex();
			int maxIndex = getMaxIndex();
			return maxIndex - minIndex + 1; // Both minIndex and maxIndex are included here
		}

		@Override
		public Iterator<E> iterator() {
			return iterateFrom(null, true, false).iterator();
		}

		@Override
		public Iterable<E> descending() {
			return iterateFrom(null, true, true);
		}

		@Override
		public String canRemove(Object value) {
			if (value != null || !theWrapped.getType().getRawType().isInstance(value))
				return Qollection.StdMsg.BAD_TYPE;
			if (!isInRange((E) value))
				return NOT_IN_RANGE;
			return theWrapped.canRemove(value);
		}

		@Override
		public String canAdd(E value) {
			if (value != null || !theWrapped.getType().getRawType().isInstance(value))
				return Qollection.StdMsg.BAD_TYPE;
			if (!isInRange(value))
				return NOT_IN_RANGE;
			return theWrapped.canAdd(value);
		}

		@Override
		public Quiterator<E> spliterateFrom(E start, boolean included, boolean reversed) {
			E stop;
			boolean includeStop;
			Comparator<? super E> compare = comparator();
			if (isReversed)
				reversed = !reversed;
			if (reversed) {
				if (start == null || (theMax != null && compare.compare(start, theMax) > 0)) {
					start = theMax;
					included &= isMaxIncluded;
				}
				stop = theMin;
				includeStop = isMinIncluded;
			} else {
				if (start == null || (theMin != null && compare.compare(start, theMin) < 0)) {
					start = theMin;
					included &= isMinIncluded;
				}
				stop = theMax;
				includeStop = isMaxIncluded;
			}
			E fStart = stop;
			boolean fIncluded = included;
			boolean fReversed = reversed;
			class Wrapper implements Quiterator<E> {
				private final Quiterator<E> backing;
				private boolean isEnded;

				Wrapper(Quiterator<E> backing) {
					this.backing = backing;
				}

				@Override
				public long estimateSize() {
					return backing.estimateSize(); // Upper bound
				}

				@Override
				public int characteristics() {
					return backing.characteristics() & (~(Spliterator.SIZED | Spliterator.SUBSIZED));
				}

				@Override
				public TypeToken<E> getType() {
					return backing.getType();
				}

				@Override
				public boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action) {
					if (isEnded)
						return false;
					boolean[] advanced = new boolean[1];
					backing.tryAdvanceElement(el -> {
						if (stop != null) {
							int comp = compare.compare(el.get(), stop);
							if (comp > 0 || (comp == 0 && !includeStop))
								isEnded = true;
							else {
								advanced[0] = true;
								action.accept(el);
							}
						}
					});
					return advanced[0];
				}

				@Override
				public Quiterator<E> trySplit() {
					if (isEnded)
						return null;
					Quiterator<E> back = backing.trySplit();
					if (back == null)
						return back;
					return new Wrapper(back);
				}
			}
			return new Wrapper(theWrapped.spliterateFrom(fStart, fIncluded, fReversed));
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public Comparator<? super E> comparator() {
			Comparator<? super E> compare = theWrapped.comparator();
			if (isReversed)
				compare = compare.reversed();
			return compare;
		}

		@Override
		public E relative(E value, boolean up, boolean withValue) {
			if (isReversed)
				up = !up;
			Comparator<? super E> compare = theWrapped.comparator();
			if (!up && theMin != null) {
				int comp = compare.compare(value, theMin);
				if (comp < 0 || (!withValue && comp == 0))
					return null;
			}
			if (up && theMax != null) {
				int comp = compare.compare(value, theMax);
				if (comp > 0 || (!withValue && comp == 0))
					return null;
			}
			E rel = theWrapped.relative(value, up, withValue);
			return isInRange(rel) ? rel : null;
		}

		@Override
		public SortedQSet<E> subSet(E min, boolean includeMin, E max, boolean includeMax, boolean reverse) {
			if (isReversed) {
				E temp = min;
				min = max;
				max = temp;
				boolean tempB = includeMin;
				includeMin = includeMax;
				includeMax = tempB;
			}
			if (min == null)
				min = theMin;
			else if (theMin != null && theWrapped.comparator().compare(min, theMin) <= 0) {
				min = theMin;
				includeMin = isMinIncluded;
			}
			if (max == null)
				max = theMax;
			else if (theMax != null && theWrapped.comparator().compare(max, theMax) >= 0) {
				max = theMax;
				includeMax = isMaxIncluded;
			}
			return new SubSortedQSet<>(theWrapped, min, includeMin, max, includeMax, reverse ^ isReversed);
		}

		@Override
		public E get(int index) {
			int minIndex;
			if (theMin == null)
				minIndex = 0;
			else {
				minIndex = theWrapped.indexOf(theMin);
				if (minIndex < 0)
					minIndex = -minIndex - 1;
				else if (!isMinIncluded)
					minIndex++;
			}
			int maxIndex;
			if (theMax == null)
				maxIndex = theWrapped.size();
			else {
				maxIndex = theWrapped.indexOf(theMax);
				if (maxIndex < 0)
					maxIndex = -maxIndex - 1;
				else if (!isMaxIncluded)
					maxIndex--;
			}
			int size = maxIndex - minIndex;
			if (size < 0)
				size = 0;
			if (index < 0 || index >= size)
				throw new IndexOutOfBoundsException(index + " of " + size);
			return theWrapped.get(index + minIndex);
		}

		@Override
		public int indexOf(Object value) {
			Comparator<? super E> compare = theWrapped.comparator();
			// If it's not in range, we'll return the bound index, even though actually adding it would generate an error
			if (theMin != null) {
				int comp = compare.compare((E) value, theMin);
				if (comp < 0 || (!isMinIncluded && comp == 0))
					return isReversed ? -size() - 1 : -1;
			}
			if (theMax != null) {
				int comp = compare.compare((E) value, theMax);
				if (comp < 0 || (!isMaxIncluded && comp == 0))
					return isReversed ? -1 : -size() - 1;
			}
			return SortedQSet.super.indexOf(value);
		}

		@Override
		public boolean contains(Object o) {
			if (!isInRange((E) o))
				return false;
			return theWrapped.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> values) {
			for (Object o : values)
				if (!isInRange((E) o))
					return false;
			return theWrapped.containsAll(values);
		}

		@Override
		public boolean add(E value) {
			if (!isInRange(value))
				throw new IllegalArgumentException(value + " is not in the range of this sub-set");
			return theWrapped.add(value);
		}

		@Override
		public boolean addAll(Collection<? extends E> values) {
			for (E value : values)
				if (!isInRange(value))
					throw new IllegalArgumentException(value + " is not in the range of this sub-set");
			return theWrapped.addAll(values);
		}

		@Override
		public boolean remove(Object value) {
			if (!isInRange((E) value))
				return false;
			return theWrapped.remove(value);
		}

		@Override
		public boolean removeAll(Collection<?> values) {
			// TODO Type check
			List<?> toRemove = values.stream().filter(v -> isInRange((E) v)).collect(Collectors.toList());
			return theWrapped.removeAll(toRemove);
		}

		@Override
		public boolean retainAll(Collection<?> values) {
			return PartialSortedSetImpl.super.retainAll(values);
		}

		@Override
		public void clear() {
			PartialSortedSetImpl.super.clear();
		}

		@Override
		public String toString() {
			return QSet.toString(this);
		}
	}

	class ModFilteredSortedQSet<E> extends ModFilteredReversibleQollection<E> implements PartialSortedSetImpl<E> {
		public ModFilteredSortedQSet(SortedQSet<E> wrapped, Function<? super E, String> removeFilter,
			Function<? super E, String> addFilter) {
			super(wrapped, removeFilter, addFilter);
		}

		@Override
		protected SortedQSet<E> getWrapped() {
			return (SortedQSet<E>) super.getWrapped();
		}

		@Override
		public Comparator<? super E> comparator() {
			return getWrapped().comparator();
		}

		@Override
		public Quiterator<E> spliterateFrom(E element, boolean included, boolean reversed) {
			return modFilter(getWrapped().spliterateFrom(element, included, reversed));
		}
	}
}

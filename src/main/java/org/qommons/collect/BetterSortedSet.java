package org.qommons.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Spliterator;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * A {@link NavigableSet} that is also a {@link BetterSet}. BetterSortedSets are also indexable (usually in logarithmic time), so
 * BetterSortedSets are also {@link BetterList}s.
 * 
 * BetterSortedSet also contains a great deal more capability than either {@link NavigableSet} and {@link BetterList}. In particular,
 * BetterSortedSets are searchable by {@link Comparable} instead of only by value, so developers can take advantage of optimized
 * searchability based on attributes of values in the set without needing to synthesize an actual value.
 * 
 * See <a href="https://github.com/Updownquark/Qommons/wiki/BetterCollection-API#bettersortedset">the wiki</a> for more detail.
 * 
 * @param <E> The type of values in the set
 */
public interface BetterSortedSet<E> extends BetterSortedList<E>, BetterSet<E>, NavigableSet<E> {
	@Override
	default boolean isContentControlled() {
		return true;
	}

	@Override
	default Spliterator<E> spliterator() {
		return NavigableSet.super.spliterator();
	}

	@Override
	default Iterator<E> iterator() {
		return BetterSortedList.super.iterator();
	}

	@Override
	default boolean contains(Object c) {
		return BetterSortedList.super.contains(c);
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		return BetterSortedList.super.containsAll(c);
	}

	@Override
	default Object[] toArray() {
		return BetterSortedList.super.toArray();
	}

	@Override
	default BetterSortedSet<E> with(E... values) {
		BetterSet.super.with(values);
		return this;
	}

	@Override
	default BetterSortedSet<E> withAll(Collection<? extends E> values) {
		BetterSet.super.withAll(values);
		return this;
	}

	@Override
	default boolean remove(Object c) {
		return BetterSortedList.super.remove(c);
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		return BetterSortedList.super.removeAll(c);
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		return BetterSortedList.super.retainAll(c);
	}

	@Override
	default int indexOf(Object o) {
		return BetterSortedList.super.indexOf(o);
	}

	@Override
	default int lastIndexOf(Object o) {
		return BetterSortedList.super.lastIndexOf(o);
	}

	@Override
	default String canMove(ElementId valueEl, ElementId after, ElementId before) {
		if (after != null && valueEl.compareTo(after) < 0)
			return StdMsg.ILLEGAL_ELEMENT_POSITION;
		if (before != null && valueEl.compareTo(before) > 0)
			return StdMsg.ILLEGAL_ELEMENT_POSITION;
		return null;
	}

	@Override
	default CollectionElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
		throws UnsupportedOperationException, IllegalArgumentException {
		if (after != null && valueEl.compareTo(after) < 0)
			throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
		if (before != null && valueEl.compareTo(before) > 0)
			throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
		return getElement(valueEl);
	}

	@Override
	default E first() {
		return BetterSortedList.super.first();
	}

	@Override
	default E last() {
		return BetterSortedList.super.last();
	}

	@Override
	default E pollLast() {
		return BetterSortedList.super.pollLast();
	}

	@Override
	default E pollFirst() {
		return BetterSortedList.super.pollFirst();
	}

	@Override
	default E floor(E e) {
		return BetterSortedList.super.floor(e);
	}

	@Override
	default E lower(E e) {
		return BetterSortedList.super.lower(e);
	}

	@Override
	default E ceiling(E e) {
		return BetterSortedList.super.ceiling(e);
	}

	@Override
	default E higher(E e) {
		return BetterSortedList.super.higher(e);
	}

	@Override
	default <T> T between(Comparable<? super E> search, Function<? super E, ? extends T> onMatchOrTerminal,
		BiFunction<? super E, ? super E, ? extends T> interpolate, Supplier<? extends T> onEmpty) {
		return BetterSortedList.super.between(search, onMatchOrTerminal, interpolate, onEmpty);
	}

	@Override
	default <T> T betweenElements(Comparable<? super E> search, Function<? super CollectionElement<E>, ? extends T> onMatchOrTerminal,
		BiFunction<? super CollectionElement<E>, ? super CollectionElement<E>, ? extends T> interpolate, Supplier<? extends T> onEmpty) {
		CollectionElement<E> found = search(search, BetterSortedList.SortedSearchFilter.Less);
		if (found == null) { // No element <= search
			found = getTerminalElement(true);
			if (found == null)
				return onEmpty.get();
			else
				return onMatchOrTerminal.apply(found);
		} else if (search.compareTo(found.get()) == 0)
			return onMatchOrTerminal.apply(found);
		else {
			CollectionElement<E> next = getAdjacentElement(found.getElementId(), true);
			if (next == null)
				return onMatchOrTerminal.apply(found);
			else
				return interpolate.apply(found, next);
		}
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
	default BetterSortedSet<E> subSequence(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return subSet(fromElement, fromInclusive, toElement, toInclusive);
	}

	@Override
	default BetterSortedSet<E> subSequence(Comparable<? super E> from, Comparable<? super E> to) {
		return subSet(from, to);
	}

	@Override
	default BetterSortedSet<E> headSequence(E toElement, boolean inclusive) {
		return headSet(toElement, inclusive);
	}

	@Override
	default BetterSortedSet<E> tailSequence(E fromElement, boolean inclusive) {
		return tailSet(fromElement, inclusive);
	}

	@Override
	default BetterSortedSet<E> subSequence(E fromElement, E toElement) {
		return subSet(fromElement, toElement);
	}

	@Override
	default BetterSortedSet<E> headSequence(E toElement) {
		return headSet(toElement);
	}

	@Override
	default BetterSortedSet<E> tailSequence(E fromElement) {
		return tailSet(fromElement);
	}

	@Override
	default BetterSortedSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return subSet(searchFor(fromElement, fromInclusive ? 0 : 1), searchFor(toElement, toInclusive ? 0 : -1));
	}

	/**
	 * @param from The (optional) lower bound for the sub-set
	 * @param to The (optional) upper bound for the sub-set
	 * @return A sub set containing all of this set's <code>value</code>s for which
	 *         <code>from.{@link Comparable#compareTo(Object) compareTo}(value)&gt;=0</code> (if <code>from</code> is specified) and
	 *         <code>to.{@link Comparable#compareTo(Object) compareTo}(value)&lt;=0</code> (if <code>to</code> is specified).
	 */
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
	default BetterList<E> subList(int fromIndex, int toIndex) {
		if(!BetterCollections.simplifyDuplicateOperations())
			return BetterSortedList.super.subList(fromIndex, toIndex);
		try (Transaction t = lock(false, null)) {
			// Be inclusive so that adds succeed as often as possible
			Comparable<? super E> from = fromIndex == 0 ? null : searchFor(get(fromIndex - 1), 1);
			Comparable<? super E> to = toIndex == size() ? null : searchFor(get(toIndex), -1);
			return subSet(from, to);
		}
	}

	/**
	 * @param <E> The type of the set
	 * @param compare The comparator for the set
	 * @return An immutable, empty sorted set
	 */
	public static <E> BetterSortedSet<E> empty(Comparator<? super E> compare) {
		return new EmptySortedSet<>(compare);
	}

	/**
	 * Implements {@link BetterSortedSet#subSet(Comparable, Comparable)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class BetterSubSet<E> extends BetterSubSequence<E> implements BetterSortedSet<E> {
		/**
		 * @param set The sorted set that this sub set is for
		 * @param from The lower bound for the sub set
		 * @param to The upper bound for the sub set
		 */
		public BetterSubSet(BetterSortedSet<E> set, Comparable<? super E> from, Comparable<? super E> to) {
			super(set, from, to);
		}

		@Override
		public BetterSortedSet<E> getWrapped() {
			return (BetterSortedSet<E>) super.getWrapped();
		}

		@Override
		public boolean add(E value) {
			return BetterSortedSet.super.add(value);
		}

		@Override
		public BetterSortedSet<E> subSet(Comparable<? super E> innerFrom, Comparable<? super E> innerTo) {
			if (BetterCollections.simplifyDuplicateOperations())
				return new BetterSubSet<>(getWrapped(), BetterSortedList.and(getFrom(), innerFrom, true),
					BetterSortedList.and(getTo(), innerTo, false));
			else
				return BetterSortedSet.super.subSet(innerFrom, innerTo);
		}

		@Override
		public BetterSortedSet<E> subSequence(Comparable<? super E> innerFrom, Comparable<? super E> innerTo) {
			return subSet(innerFrom, innerTo);
		}

		@Override
		public int hashCode() {
			return BetterSet.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterSet.equals(this, obj);
		}

		@Override
		public String toString() {
			return BetterSet.toString(this);
		}
	}

	/**
	 * Implements {@link BetterSortedSet#reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class ReversedSortedSet<E> extends ReversedSortedList<E> implements BetterSortedSet<E> {
		/** @param wrap The sorted set to reverse */
		public ReversedSortedSet(BetterSortedSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected BetterSortedSet<E> getWrapped() {
			return (BetterSortedSet<E>) super.getWrapped();
		}

		@Override
		public BetterSortedSet<E> reverse() {
			if (BetterCollections.simplifyDuplicateOperations())
				return getWrapped();
			else
				return BetterSortedSet.super.reverse();
		}

		@Override
		public String toString() {
			return BetterSet.toString(this);
		}
	}

	/**
	 * Implements {@link BetterSortedSet#empty(Comparator)}
	 * 
	 * @param <E> The type of the set
	 */
	public static class EmptySortedSet<E> extends EmptySortedList<E> implements BetterSortedSet<E> {
		EmptySortedSet(Comparator<? super E> compare) {
			super(compare);
		}

		@Override
		public String toString() {
			return BetterSet.toString(this);
		}
	}
}

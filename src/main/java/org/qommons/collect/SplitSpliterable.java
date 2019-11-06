package org.qommons.collect;

import java.util.Comparator;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;

import org.qommons.Transaction;

/**
 * A {@link BetterList} that has the performance enhancement of being able to quickly find a midway point between 2 elements
 * 
 * @param <E> The type of values in the list
 */
public interface SplitSpliterable<E> extends BetterList<E> {
	/**
	 * Quickly obtains an element that is well-spaced between two other elements
	 * 
	 * @param element1 The ID of one element
	 * @param element2 The ID of the other element
	 * @return An element in this list that is between the given elements with a spacing suitable for double-bounded binary search; or null
	 *         if the elements are the same or adjacent
	 */
	CollectionElement<E> splitBetween(ElementId element1, ElementId element2);

	@Override
	default Spliterator<E> spliterator() {
		int characteristics = Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
		if (isLockSupported())
			characteristics |= Spliterator.CONCURRENT;
		boolean sorted = this instanceof BetterSortedList;
		if (sorted)
			characteristics |= Spliterator.SORTED;
		if (this instanceof Set)
			characteristics |= Spliterator.DISTINCT;
		return new DefaultSplittableSpliterator<>(this, //
			sorted ? ((BetterSortedList<E>) this).comparator() : null, characteristics, null, true, null, null);
	}

	@Override
	default SplitSpliterable<E> reverse() {
		return new ReversedSplitSpliterable<>(this);
	}

	@Override
	default SplitSpliterable<E> subList(int fromIndex, int toIndex) {
		return new SubSplitSpliterable<>(this, fromIndex, toIndex);
	}

	/**
	 * Implements {@link SplitSpliterable#spliterator()} by default
	 * 
	 * @param <E> The type of values in the list
	 */
	public class DefaultSplittableSpliterator<E> implements Spliterator<E> {
		private final SplitSpliterable<E> theCollection;
		private CollectionElement<E> theElement;
		private boolean elementIsNext;

		private final Comparator<? super E> theSorting;
		private final int theCharacteristics;
		private CollectionElement<E> theLeftBound;
		private CollectionElement<E> theRightBound;

		/**
		 * @param collection The colection to create the spliterator for
		 * @param compare The sorting of the collection (may be null)
		 * @param characteristics The {@link Spliterator#characteristics() characteristics} for the spliterator
		 * @param current The current element
		 * @param currentIsNext Whether the current element is the next element that should should be returned from
		 *        {@link Spliterator#tryAdvance(java.util.function.Consumer)}
		 * @param leftBound The left bound for this spliterator (inclusive)
		 * @param rightBound The right bound for this spliterator (exclusive)
		 */
		public DefaultSplittableSpliterator(SplitSpliterable<E> collection, Comparator<? super E> compare, int characteristics,
			CollectionElement<E> current, boolean currentIsNext, CollectionElement<E> leftBound, CollectionElement<E> rightBound) {
			theCollection = collection;
			theElement = current;
			elementIsNext = currentIsNext;
			theSorting = compare;
			theCharacteristics = characteristics;
			theLeftBound = leftBound;
			theRightBound = rightBound;
		}

		@Override
		public long getExactSizeIfKnown() {
			return estimateSize();
		}

		@Override
		public Comparator<? super E> getComparator() {
			return theSorting;
		}

		@Override
		public int characteristics() {
			return theCharacteristics;
		}

		@Override
		public long estimateSize() {
			try (Transaction t = theCollection.lock(false, null)) {
				int size;
				if (theRightBound != null)
					size = theCollection.getElementsBefore(theRightBound.getElementId());
				else
					size = theCollection.size();
				if (theLeftBound != null)
					size -= theCollection.getElementsBefore(theLeftBound.getElementId());
				return size;
			}
		}

		@Override
		public boolean tryAdvance(Consumer<? super E> action) {
			if (elementIsNext) {
				if (theElement == null && theLeftBound == null)
					theElement = theCollection.getTerminalElement(true);
			} else if (theElement != null)
				theElement = theCollection.getAdjacentElement(theElement.getElementId(), true);
			else
				theElement = null;
			if (theElement == null || (theRightBound != null && theElement.getElementId().compareTo(theRightBound.getElementId()) >= 0))
				return false;
			action.accept(theElement.get());
			elementIsNext = false;
			return true;
		}

		@Override
		public Spliterator<E> trySplit() {
			CollectionElement<E> left = theLeftBound != null ? theLeftBound : theCollection.getTerminalElement(true);
			if (left == null)
				return null;

			CollectionElement<E> right = theRightBound == null ? theRightBound : theCollection.getTerminalElement(true);
			CollectionElement<E> divider = theCollection.splitBetween(left.getElementId(), right.getElementId());
			if (divider == null)
				return null;

			DefaultSplittableSpliterator<E> split;
			int comp;
			if (theElement == null)
				comp = elementIsNext ? -1 : 1;
			else
				comp = theElement.getElementId().compareTo(divider.getElementId());
			if (comp < 0) { // We're on the left of the divider
				split = new DefaultSplittableSpliterator<>(theCollection, getComparator(), characteristics(), divider, true, divider,
					right);
				theRightBound = divider;
			} else {
				split = new DefaultSplittableSpliterator<>(theCollection, getComparator(), characteristics(), divider, true, left,
					divider);
				theLeftBound = divider;
			}
			return split;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			CollectionElement<E> node = theCollection.getTerminalElement(true);
			if (theLeftBound == null && theRightBound != null)
				str.append('<');
			if (theElement == null && elementIsNext)
				str.append('^');
			while (node != null) {
				if (theLeftBound != null && node.getElementId().equals(theLeftBound.getElementId()))
					str.append('<');
				if (theElement != null && node.getElementId().equals(theElement.getElementId())) {
					if (elementIsNext)
						str.append('^');
					str.append('[');
				}
				str.append(node.get());
				if (theElement != null && node.getElementId().equals(theElement.getElementId())) {
					str.append(']');
					if (!elementIsNext)
						str.append('^');
				}
				if (theRightBound != null && node.getElementId().equals(theRightBound.getElementId()))
					str.append('>');
				node = theCollection.getAdjacentElement(node.getElementId(), true);
				if (node != null)
					str.append(", ");
			}
			if (theElement == null && !elementIsNext)
				str.append('^');
			if (theRightBound == null && theLeftBound != null)
				str.append('>');
			return str.toString();
		}
	}

	/**
	 * Implements {@link SplitSpliterable#reverse()}
	 * 
	 * @param <E> The type of values in the list
	 */
	public class ReversedSplitSpliterable<E> extends ReversedList<E> implements SplitSpliterable<E> {
		/** @param wrap The collection to reverse */
		public ReversedSplitSpliterable(SplitSpliterable<E> wrap) {
			super(wrap);
		}

		@Override
		protected SplitSpliterable<E> getWrapped() {
			return (SplitSpliterable<E>) super.getWrapped();
		}

		@Override
		public CollectionElement<E> splitBetween(ElementId element1, ElementId element2) {
			return getWrapped().splitBetween(element1, element2);
		}

		@Override
		public SplitSpliterable<E> reverse() {
			return getWrapped();
		}
	}

	/**
	 * Implements {@link SplitSpliterable#subList(int, int)}
	 * 
	 * @param <E> The type of values in the list
	 */
	public class SubSplitSpliterable<E> extends SubList<E> implements SplitSpliterable<E> {
		/**
		 * @param wrapped The list to create the sub list for
		 * @param start The beginning index for the sub-list (inclusive)
		 * @param end The end index for the sub-list (exclusive)
		 */
		public SubSplitSpliterable(SplitSpliterable<E> wrapped, int start, int end) {
			super(wrapped, start, end);
		}

		@Override
		protected SplitSpliterable<E> getWrapped() {
			return (SplitSpliterable<E>) super.getWrapped();
		}

		@Override
		public CollectionElement<E> splitBetween(ElementId element1, ElementId element2) {
			return getWrapped().splitBetween(element1, element2);
		}
	}

	/**
	 * A {@link SplitSpliterable} that is also a {@link BetterSortedSet}
	 * 
	 * @param <E> The type of values in the set
	 */
	public interface SortedSetSplitSpliterable<E> extends BetterSortedSet<E>, SplitSpliterable<E> {
		@Override
		default Spliterator<E> spliterator() {
			return SplitSpliterable.super.spliterator();
		}

		@Override
		default SortedSetSplitSpliterable<E> subList(int fromIndex, int toIndex) {
			return (SortedSetSplitSpliterable<E>) BetterSortedSet.super.subList(fromIndex, toIndex);
		}

		@Override
		default SortedSetSplitSpliterable<E> descendingSet() {
			return (SortedSetSplitSpliterable<E>) BetterSortedSet.super.reverse();
		}

		@Override
		default SortedSetSplitSpliterable<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
			return (SortedSetSplitSpliterable<E>) BetterSortedSet.super.subSet(fromElement, fromInclusive, toElement, toInclusive);
		}

		@Override
		default SortedSetSplitSpliterable<E> subSet(Comparable<? super E> from, Comparable<? super E> to) {
			return new SSSSSubSet<>(this, from, to);
		}

		@Override
		default SortedSetSplitSpliterable<E> headSet(E toElement, boolean inclusive) {
			return (SortedSetSplitSpliterable<E>) BetterSortedSet.super.headSet(toElement, inclusive);
		}

		@Override
		default SortedSetSplitSpliterable<E> tailSet(E fromElement, boolean inclusive) {
			return (SortedSetSplitSpliterable<E>) BetterSortedSet.super.tailSet(fromElement, inclusive);
		}

		@Override
		default SortedSetSplitSpliterable<E> subSet(E fromElement, E toElement) {
			return (SortedSetSplitSpliterable<E>) BetterSortedSet.super.subSet(fromElement, toElement);
		}

		@Override
		default SortedSetSplitSpliterable<E> headSet(E toElement) {
			return (SortedSetSplitSpliterable<E>) BetterSortedSet.super.headSet(toElement);
		}

		@Override
		default SortedSetSplitSpliterable<E> tailSet(E fromElement) {
			return (SortedSetSplitSpliterable<E>) BetterSortedSet.super.tailSet(fromElement);
		}

		@Override
		default SortedSetSplitSpliterable<E> reverse() {
			return new ReversedSSSS<>(this);
		}

		/**
		 * Implements {@link SplitSpliterable.SortedSetSplitSpliterable#reverse()}
		 * 
		 * @param <E> The type of values in the set
		 */
		public class ReversedSSSS<E> extends ReversedSortedSet<E> implements SortedSetSplitSpliterable<E> {
			/** @param wrap The collection to reverse */
			public ReversedSSSS(SortedSetSplitSpliterable<E> wrap) {
				super(wrap);
			}

			@Override
			protected SortedSetSplitSpliterable<E> getWrapped() {
				return (SortedSetSplitSpliterable<E>) super.getWrapped();
			}

			@Override
			public CollectionElement<E> splitBetween(ElementId element1, ElementId element2) {
				return getWrapped().splitBetween(element1, element2);
			}

			@Override
			public SortedSetSplitSpliterable<E> reverse() {
				return getWrapped();
			}
		}

		/**
		 * Implements {@link SplitSpliterable.SortedSetSplitSpliterable#subSet(Comparable, Comparable)}
		 * 
		 * @param <E> The type of values in the set
		 */
		public class SSSSSubSet<E> extends BetterSortedSet.BetterSubSet<E> implements SortedSetSplitSpliterable<E> {
			/**
			 * @param set The set to create the sub set from
			 * @param from The lower bound for the sub set
			 * @param to The upper bound for the sub set
			 */
			public SSSSSubSet(SortedSetSplitSpliterable<E> set, Comparable<? super E> from, Comparable<? super E> to) {
				super(set, from, to);
			}

			@Override
			public SortedSetSplitSpliterable<E> getWrapped() {
				return (SortedSetSplitSpliterable<E>) super.getWrapped();
			}

			@Override
			public CollectionElement<E> splitBetween(ElementId element1, ElementId element2) {
				return getWrapped().splitBetween(element1, element2);
			}

			@Override
			public SortedSetSplitSpliterable<E> reverse() {
				return SortedSetSplitSpliterable.super.reverse();
			}

			@Override
			public SortedSetSplitSpliterable<E> subSet(Comparable<? super E> innerFrom, Comparable<? super E> innerTo) {
				return new SSSSSubSet<>(getWrapped(), BetterSortedList.and(getFrom(), innerFrom, true),
					BetterSortedList.and(getTo(), innerTo, false));
			}
		}
	}
}

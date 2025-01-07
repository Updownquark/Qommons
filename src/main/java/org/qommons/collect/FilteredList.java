package org.qommons.collect;

import java.util.Collection;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.qommons.Identifiable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * <p>
 * A {@link BetterList} backed by another {@link BetterList}. A {@link FilteredList} cannot be modified except by removing elements, and
 * removing elements from a {@link FilteredList} does not remove them from the source list, but only marks them as removed locally.
 * </p>
 * <p>
 * The inclusion of an element in the source list can be queried via the {@link #isIncluded(int)} method.
 * </p>
 * 
 * @param <T>
 */
public class FilteredList<T> extends AbstractIdentifiable implements BetterList<T> {
	private final BetterList<? extends T> theWrapped;
	private final BetterBitSet theFilter;

	/** @param filterValues The source list to filter */
	public FilteredList(BetterList<? extends T> filterValues) {
		theWrapped = filterValues;
		theFilter = new BetterBitSet();
	}

	/**
	 * @param index The index of the element in the source list to check
	 * @return Whether the element in the source list at the given index is included in this list
	 */
	public boolean isIncluded(int index) {
		return !theFilter.get(index);
	}

	@Override
	protected Object createIdentity() {
		return Identifiable.wrap(theWrapped, "filtered", theFilter);
	}

	@Override
	public CollectionElement<T> getElement(T value, boolean first) {
		int index = 0;
		for (CollectionElement<? extends T> filterValue : theWrapped.elements()) {
			if (!theFilter.get(index) && Objects.equals(filterValue.get(), value)) {
				return new Element(filterValue, index);
			}
			index++;
		}
		return null;
	}

	@Override
	public CollectionElement<T> getElement(ElementId id) {
		int index = theWrapped.getElementsBefore(id);
		if (theFilter.get(index)) {
			throw new NoSuchElementException(StdMsg.ELEMENT_REMOVED);
		}
		return new Element(theWrapped.getElement(id), index);
	}

	@Override
	public CollectionElement<T> getTerminalElement(boolean first) {
		CollectionElement<? extends T> filterValue = theWrapped.getTerminalElement(first);
		int index = first ? 0 : theWrapped.size() - 1;
		while (filterValue != null && theFilter.get(index)) {
			filterValue = theWrapped.getAdjacentElement(filterValue.getElementId(), first);
			if (first) {
				index++;
			} else {
				index--;
			}
		}
		return filterValue == null ? null : new Element(filterValue, index);
	}

	@Override
	public CollectionElement<T> getAdjacentElement(ElementId elementId, boolean next) {
		CollectionElement<? extends T> filterValue = theWrapped.getAdjacentElement(elementId, next);
		int index = theWrapped.getElementsBefore(elementId) + 1;
		while (filterValue != null && theFilter.get(index)) {
			filterValue = theWrapped.getAdjacentElement(filterValue.getElementId(), next);
			if (next) {
				index++;
			} else {
				index--;
			}
		}
		return filterValue == null ? null : new Element(filterValue, index);
	}

	@Override
	public MutableCollectionElement<T> mutableElement(ElementId id) {
		return (MutableCollectionElement<T>) getElement(id);
	}

	@Override
	public BetterList<CollectionElement<T>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection) {
		return BetterList.empty();
	}

	@Override
	public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
		return BetterList.empty();
	}

	@Override
	public ElementId getEquivalentElement(ElementId equivalentEl) {
		return null;
	}

	@Override
	public String canAdd(T value, ElementId after, ElementId before) {
		return StdMsg.UNSUPPORTED_OPERATION;
	}

	@Override
	public CollectionElement<T> addElement(T value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public String canMove(ElementId valueEl, ElementId after, ElementId before) {
		return StdMsg.UNSUPPORTED_OPERATION;
	}

	@Override
	public CollectionElement<T> move(ElementId valueEl, ElementId after, ElementId before, boolean first,
			Runnable afterRemove) throws UnsupportedOperationException, IllegalArgumentException {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public long getStamp() {
		return theFilter.cardinality();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return Transaction.NONE;
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return Transaction.NONE;
	}

	@Override
	public Collection<Cause> getCurrentCauses() {
		return Collections.emptyList();
	}

	@Override
	public CoreId getCoreId() {
		return CoreId.EMPTY;
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return ThreadConstraint.ANY;
	}

	@Override
	public int size() {
		return theWrapped.size() - theFilter.cardinality();
	}

	@Override
	public boolean isEmpty() {
		return theFilter.cardinality() == theWrapped.size();
	}

	@Override
	public CollectionElement<T> getElement(int index) throws IndexOutOfBoundsException {
		int sourceIndex = theFilter.indexOfNthClearBit(index);
		if (sourceIndex < theWrapped.size()) {
			return new Element(theWrapped.getElement(sourceIndex), sourceIndex);
		}
		throw new IndexOutOfBoundsException(index + " of " + size());
	}

	@Override
	public boolean isContentControlled() {
		return true;
	}

	@Override
	public int getElementsBefore(ElementId id) {
		int index = theWrapped.getElementsBefore(id);
		return index - theFilter.countBitsSetBetween(0, index);
	}

	@Override
	public int getElementsAfter(ElementId id) {
		int index = theWrapped.getElementsAfter(id);
		return index - theFilter.countBitsSetBetween(index, theWrapped.size());
	}

	@Override
	public void clear() {
		theFilter.set(0, theWrapped.size());
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

	class Element implements MutableCollectionElement<T> {
		private final CollectionElement<? extends T> theWrappedElement;
		private final int theIndex;

		Element(CollectionElement<? extends T> wrappedElement, int index) {
			theWrappedElement = wrappedElement;
			theIndex = index;
		}

		@Override
		public ElementId getElementId() {
			return theWrappedElement.getElementId();
		}

		@Override
		public T get() {
			return theWrappedElement.get();
		}

		@Override
		public BetterCollection<T> getCollection() {
			return FilteredList.this;
		}

		@Override
		public String isEnabled() {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public String isAcceptable(T value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canRemove() {
			if (theFilter.get(theIndex)) {
				return StdMsg.ELEMENT_REMOVED;
			} else {
				return null;
			}
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			theFilter.set(theIndex);
		}

		@Override
		public String toString() {
			return theWrappedElement.toString();
		}
	}
}
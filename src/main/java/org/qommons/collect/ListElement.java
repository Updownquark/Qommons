package org.qommons.collect;

/**
 * A sub-type of {@link CollectionElement} that knows its absolute position in the list, not just its order relative to other elements
 * 
 * @param <E> The type of the element's value
 */
public interface ListElement<E> extends CollectionElement<E> {
	@Override
	ListElement<E> getAdjacent(boolean next);

	/** @return The number of elements in the collection before this element */
	int getElementsBefore();

	/** @return The number of elements in the collection after this element */
	int getElementsAfter();

	@Override
	default ListElement<E> reverse() {
		return new ReversedListElement<>(this);
	}

	/**
	 * @param <E> The type of the element
	 * @param element The element to reverse
	 * @return The reversed element, or null if the element was null
	 */
	public static <E> ListElement<E> reverse(ListElement<E> element) {
		return element == null ? null : element.reverse();
	}

	/**
	 * Default implementation of {@link ListElement#reverse()}
	 * 
	 * @param <E> The type of the element's value
	 */
	public static class ReversedListElement<E> extends ReversedCollectionElement<E> implements ListElement<E> {
		/** @param wrapped The list element to wrap */
		public ReversedListElement(ListElement<E> wrapped) {
			super(wrapped);
		}

		@Override
		protected ListElement<E> getWrapped() {
			return (ListElement<E>) super.getWrapped();
		}

		@Override
		public ListElement<E> getAdjacent(boolean next) {
			return ListElement.reverse(getWrapped().getAdjacent(!next));
		}

		@Override
		public int getElementsBefore() {
			return getWrapped().getElementsAfter();
		}

		@Override
		public int getElementsAfter() {
			return getWrapped().getElementsBefore();
		}

		@Override
		public ListElement<E> reverse() {
			return getWrapped();
		}
	}
}

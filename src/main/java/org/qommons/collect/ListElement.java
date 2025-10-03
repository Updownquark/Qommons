package org.qommons.collect;

public interface ListElement<E> extends CollectionElement<E> {
	@Override
	ListElement<E> getAdjacent(boolean next);

	int getElementsBefore();

	int getElementsAfter();

	@Override
	default ListElement<E> reverse() {
		return new ReversedListElement<>(this);
	}

	public static <E> ListElement<E> reverse(ListElement<E> element) {
		return element == null ? null : element.reverse();
	}

	public static class ReversedListElement<E> extends ReversedCollectionElement<E> implements ListElement<E> {
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

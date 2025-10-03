package org.qommons.collect;

public interface MutableListElement<E> extends MutableCollectionElement<E>, ListElement<E> {
	@Override
	MutableListElement<E> getAdjacent(boolean next);

	@Override
	default MutableListElement<E> reverse() {
		return new ReversedMutableListElement<>(this);
	}

	static <E> MutableListElement<E> reverse(MutableListElement<E> element) {
		return element == null ? null : element.reverse();
	}

	static class ReversedMutableListElement<E> extends ReversedMutableElement<E> implements MutableListElement<E> {
		public ReversedMutableListElement(MutableListElement<E> wrapped) {
			super(wrapped);
		}

		@Override
		protected MutableListElement<E> getWrapped() {
			return (MutableListElement<E>) super.getWrapped();
		}

		@Override
		public MutableListElement<E> getAdjacent(boolean next) {
			return MutableListElement.reverse(getWrapped().getAdjacent(!next));
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
		public MutableListElement<E> reverse() {
			return getWrapped();
		}
	}
}

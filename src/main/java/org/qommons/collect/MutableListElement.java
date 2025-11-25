package org.qommons.collect;

/**
 * A sub-type of {@link MutableCollectionElement} that knows its absolute position in the list, not just its order relative to other
 * elements
 * 
 * @param <E> The type of the element's value
 */
public interface MutableListElement<E> extends MutableCollectionElement<E>, ListElement<E> {
	@Override
	MutableListElement<E> getAdjacent(boolean next);

	@Override
	default MutableListElement<E> reverse() {
		return new ReversedMutableListElement<>(this);
	}

	/**
	 * @param <E> The type of the element
	 * @param element The element to reverse
	 * @return The reversed element, or null if the element was null
	 */
	static <E> MutableListElement<E> reverse(MutableListElement<E> element) {
		return element == null ? null : element.reverse();
	}

	/**
	 * Default implementation of {@link MutableListElement#reverse()}
	 * 
	 * @param <E> The type of the element's value
	 */
	public static class ReversedMutableListElement<E> extends ReversedMutableElement<E> implements MutableListElement<E> {
		/** @param wrapped The list element to wrap */
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

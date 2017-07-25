package org.qommons.collect;

public interface ElementHandle<E> {
	ElementId getElementId();

	E get();

	default ElementHandle<E> reverse() {
		return new ReversedElementHandle<>(this);
	}

	class ReversedElementHandle<E> implements ElementHandle<E> {
		private final ElementHandle<E> theWrapped;

		public ReversedElementHandle(ElementHandle<E> wrapped) {
			theWrapped = wrapped;
		}

		protected ElementHandle<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public E get() {
			return theWrapped.get();
		}

		@Override
		public ElementId getElementId() {
			return theWrapped.getElementId().reverse();
		}

		@Override
		public ElementHandle<E> reverse() {
			return theWrapped;
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof ReversedElementHandle && theWrapped.equals(((ReversedElementHandle<?>) o).theWrapped);
		}
	}
}

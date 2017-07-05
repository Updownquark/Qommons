package org.qommons.collect;

public interface ReversibleIterable<E> extends Betterable<E> {
	@Override
	default ReversibleSpliterator<E> spliterator() {
		return spliterator(true);
	}

	default ReversibleSpliterator<E> spliterator(boolean fromStart) {
		return mutableSpliterator(fromStart).immutable();
	}

	@Override
	default ReversibleElementSpliterator<E> mutableSpliterator() {
		return mutableSpliterator(true);
	}

	/**
	 * @param fromStart Whether the spliterator should begin at the beginning or the end of this collection
	 * @return The spliterator
	 */
	ReversibleElementSpliterator<E> mutableSpliterator(boolean fromStart);

	default ReversibleIterable<E> reverse() {
		return new ReversedIterable<>(this);
	}

	class ReversedIterable<E> implements ReversibleIterable<E> {
		private final ReversibleIterable<E> theWrapped;

		public ReversedIterable(ReversibleIterable<E> wrapped) {
			theWrapped = wrapped;
		}

		protected ReversibleIterable<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public ReversibleSpliterator<E> spliterator(boolean fromStart) {
			return theWrapped.spliterator(!fromStart).reverse();
		}

		@Override
		public ReversibleElementSpliterator<E> mutableSpliterator(boolean fromStart) {
			return theWrapped.mutableSpliterator(!fromStart).reverse();
		}

		@Override
		public ReversibleIterable<E> reverse() {
			return getWrapped();
		}
	}
}

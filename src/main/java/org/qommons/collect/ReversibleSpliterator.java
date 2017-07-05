package org.qommons.collect;

import java.util.Spliterator;
import java.util.function.Consumer;

public interface ReversibleSpliterator<E> extends Spliterator<E> {
	boolean tryReverse(Consumer<? super E> action);

	default void forEachReverse(Consumer<? super E> action) {
		while (tryReverse(action)) {
		}
	}

	@Override
	ReversibleSpliterator<E> trySplit();

	default ReversibleSpliterator<E> reverse() {
		return new ReversedSpliterator<>(this);
	}

	/**
	 * Implements {@link ReversibleElementSpliterator#reverse()}
	 * 
	 * @param <E> The type of the values in this spliterator
	 */
	class ReversedSpliterator<E> implements ReversibleSpliterator<E> {
		private final ReversibleSpliterator<E> theWrapped;

		public ReversedSpliterator(ReversibleSpliterator<E> wrap) {
			theWrapped = wrap;
		}

		protected ReversibleSpliterator<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public long estimateSize() {
			return theWrapped.estimateSize();
		}

		@Override
		public long getExactSizeIfKnown() {
			return theWrapped.getExactSizeIfKnown();
		}

		@Override
		public int characteristics() {
			return theWrapped.characteristics();
		}

		@Override
		public boolean tryAdvance(Consumer<? super E> action) {
			return theWrapped.tryReverse(action);
		}

		@Override
		public boolean tryReverse(Consumer<? super E> action) {
			return theWrapped.tryAdvance(action);
		}

		@Override
		public void forEachRemaining(Consumer<? super E> action) {
			theWrapped.forEachReverse(action);
		}

		@Override
		public void forEachReverse(Consumer<? super E> action) {
			theWrapped.forEachRemaining(action);
		}

		@Override
		public ReversibleSpliterator<E> reverse() {
			return theWrapped;
		}

		@Override
		public ReversibleSpliterator<E> trySplit() {
			ReversibleSpliterator<E> wrapSpit = theWrapped.trySplit();
			if (wrapSpit == null)
				return null;
			return new ReversedSpliterator<>(wrapSpit);
		}
	}
}

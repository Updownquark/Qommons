package org.qommons.collect;

import java.util.Spliterator;
import java.util.function.Consumer;

public interface ElementSpliterator<E> extends Spliterator<E> {
	/**
	 * Retrieves the next element available to this ElementSpliterator
	 * 
	 * @param action Accepts each element in sequence. Unless a sub-type of ElementSpliterator or a specific supplier of a
	 *        ElementSpliterator advertises otherwise, the element object may only be treated as valid until the next element is returned
	 *        and also should not be kept longer than the reference to the ElementSpliterator.
	 * @return false if no remaining elements existed upon entry to this method, else true.
	 */
	boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action);

	/**
	 * Retrieves the previous element available to this ElementSpliterator
	 * 
	 * @param action Accepts each element in reverse. Unless a sub-type of ElementSpliterator or a specific supplier of a ElementSpliterator
	 *        advertises otherwise, the element object may only be treated as valid until the next element is returned and also should not
	 *        be kept longer than the reference to the ElementSpliterator.
	 * @return false if no remaining elements existed upon entry to this method, else true.
	 */
	boolean tryReverseElement(Consumer<? super CollectionElement<E>> action);

	/**
	 * Operates on each element remaining in this ElementSpliterator
	 * 
	 * @param action The action to perform on each element
	 */
	default void forEachElement(Consumer<? super CollectionElement<E>> action) {
		while (tryAdvanceElement(action)) {
		}
	}

	/**
	 * Operates on each element remaining in this ElementSpliterator
	 * 
	 * @param action The action to perform on each element
	 */
	default void forEachElementReverse(Consumer<? super CollectionElement<E>> action) {
		while (tryReverseElement(action)) {
		}
	}

	@Override
	default boolean tryAdvance(Consumer<? super E> action) {
		return tryAdvanceElement(v -> {
			action.accept(v.get());
		});
	}

	default boolean tryReverse(Consumer<? super E> action) {
		return tryReverseElement(v -> {
			action.accept(v.get());
		});
	}

	@Override
	default void forEachRemaining(Consumer<? super E> action) {
		forEachElement(el -> action.accept(el.get()));
	}

	default void forEachReverse(Consumer<? super E> action) {
		forEachElementReverse(el -> action.accept(el.get()));
	}

	@Override
	ElementSpliterator<E> trySplit();

	/**
	 * @return A reversed view of this spliterator, where {@link #tryAdvance(Consumer)} traverses this spliterator's element in reverse and
	 *         {@link #tryReverse(Consumer)} traverses them in the forward direction
	 */
	default ElementSpliterator<E> reverse() {
		return new ReversedElementSpliterator<>(this);
	}

	/**
	 * @param <E> The type for the spliterator
	 * @return An empty ReversibleElementSpliterator of the given type
	 */
	static <E> ElementSpliterator<E> empty() {
		return new EmptyElementSpliterator<>();
	}

	class EmptyElementSpliterator<E> implements ElementSpliterator<E> {
		@Override
		public long estimateSize() {
			return 0;
		}

		@Override
		public long getExactSizeIfKnown() {
			return 0;
		}

		@Override
		public int characteristics() {
			return Spliterator.IMMUTABLE | Spliterator.SIZED;
		}

		@Override
		public boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action) {
			return false;
		}

		@Override
		public boolean tryReverseElement(Consumer<? super CollectionElement<E>> action) {
			return false;
		}

		@Override
		public ElementSpliterator<E> trySplit() {
			return null;
		}
	}

	/**
	 * Implements {@link ReversibleElementSpliterator#reverse()}
	 * 
	 * @param <E> The type of the values in this spliterator
	 */
	class ReversedElementSpliterator<E> implements ElementSpliterator<E> {
		private final ElementSpliterator<E> theWrapped;

		public ReversedElementSpliterator(ElementSpliterator<E> wrap) {
			theWrapped = wrap;
		}

		protected ElementSpliterator<E> getWrapped() {
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
		public boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action) {
			return theWrapped.tryReverseElement(el -> action.accept(el.reverse()));
		}

		@Override
		public boolean tryReverseElement(Consumer<? super CollectionElement<E>> action) {
			return theWrapped.tryAdvanceElement(el -> action.accept(el.reverse()));
		}

		@Override
		public void forEachElement(Consumer<? super CollectionElement<E>> action) {
			theWrapped.forEachElementReverse(el -> action.accept(el.reverse()));
		}

		@Override
		public void forEachElementReverse(Consumer<? super CollectionElement<E>> action) {
			theWrapped.forEachElement(el -> action.accept(el.reverse()));
		}

		@Override
		public ElementSpliterator<E> reverse() {
			return theWrapped;
		}

		@Override
		public ElementSpliterator<E> trySplit() {
			ElementSpliterator<E> wrapSpit = theWrapped.trySplit();
			if (wrapSpit == null)
				return null;
			return new ReversedElementSpliterator<>(wrapSpit);
		}
	}
}

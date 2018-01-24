package org.qommons.collect;

import java.util.Spliterator;
import java.util.function.Consumer;

import org.qommons.Transactable;
import org.qommons.Transaction;

/**
 * A {@link Spliterator} that also provides iteration by element for a {@link BetterCollection}
 * 
 * @param <E> The type of the elements
 */
public interface ElementSpliterator<E> extends Spliterator<E> {
	/**
	 * Retrieves the next or previous element available to this ElementSpliterator
	 * 
	 * @param action Accepts the next or previous element in the sequence. The element may be treated as valid as long as its
	 *        {@link CollectionElement#getElementId() element ID} remains {@link ElementId#isPresent() present}
	 * @param forward Whether to get the next or the previous element in the sequence
	 * @return True if the element was retrieved, or false if no remaining elements exist in the sequence in the given direction
	 */
	boolean forElement(Consumer<? super CollectionElement<E>> action, boolean forward);

	/**
	 * Operates on each element remaining in this ElementSpliterator
	 * 
	 * @param forward Whether to get the next or the previous element in the sequence
	 * @param action The action to perform on each element
	 */
	void forEachElement(Consumer<? super CollectionElement<E>> action, boolean forward);

	/**
	 * @param action The action to perform on the next or previous value
	 * @param forward Whether to supply the next or previous value
	 * @return Whether there was, in fact, a next or previous value
	 */
	default boolean forValue(Consumer<? super E> action, boolean forward) {
		return forElement(el -> action.accept(el.get()), forward);
	}

	/**
	 * @param action The action to perform on all values after or before the current position
	 * @param forward Whether to perform the action on values after or before the current position
	 */
	default void forEachValue(Consumer<? super E> action, boolean forward) {
		forEachElement(el -> action.accept(el.get()), forward);
	}

	@Override
	default boolean tryAdvance(Consumer<? super E> action) {
		return forValue(action, true);
	}

	/**
	 * Performs an action on the previous value
	 * 
	 * @param action The action to perform on the value
	 * @return Whether there was a previous value
	 */
	default boolean tryReverse(Consumer<? super E> action) {
		return forValue(action, false);
	}

	@Override
	default void forEachRemaining(Consumer<? super E> action) {
		forEachValue(action, true);
	}

	/**
	 * Performs an action on all values before the current position
	 * 
	 * @param action The action to perform
	 */
	default void forEachReverse(Consumer<? super E> action) {
		forEachValue(action, false);
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

	/**
	 * An empty spliterator
	 * 
	 * @param <E> The type of the spliterator
	 */
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
		public boolean forElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
			return false;
		}

		@Override
		public void forEachElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
		}

		@Override
		public ElementSpliterator<E> trySplit() {
			return null;
		}
	}

	/**
	 * Implements {@link ElementSpliterator#reverse()}
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
		public boolean forValue(Consumer<? super E> action, boolean forward) {
			return theWrapped.forValue(action, !forward);
		}

		@Override
		public void forEachValue(Consumer<? super E> action, boolean forward) {
			theWrapped.forEachValue(action, !forward);
		}

		@Override
		public boolean forElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
			return theWrapped.forElement(el -> action.accept(el.reverse()), !forward);
		}

		@Override
		public void forEachElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
			theWrapped.forEachElement(el -> action.accept(el.reverse()), !forward);
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

	/**
	 * A partial {@link ElementSpliterator} implementation
	 * 
	 * @param <E> The type of the spliterator
	 */
	abstract class SimpleSpliterator<E> implements ElementSpliterator<E> {
		protected final Transactable theLocker;

		public SimpleSpliterator(Transactable locker) {
			theLocker = locker;
		}

		protected abstract boolean internalForElement(Consumer<? super CollectionElement<E>> action, boolean forward);

		@Override
		public boolean forElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
			try (Transaction t = theLocker == null ? Transaction.NONE : theLocker.lock(false, null)) {
				return internalForElement(action, forward);
			}
		}

		@Override
		public void forEachElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
			try (Transaction t = theLocker == null ? Transaction.NONE : theLocker.lock(false, null)) {
				while (internalForElement(action, forward)) {
				}
			}
		}
	}
}

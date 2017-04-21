package org.qommons.collect;

import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import org.qommons.Ternian;

import com.google.common.reflect.TypeToken;

/**
 * An {@link ElementSpliterator} that can traverse elements in either direction
 * 
 * @param <T> The type of values returned by the spliterator
 */
public interface ReversibleSpliterator<T> extends ElementSpliterator<T> {
	/**
	 * Gets the previous element in the spliterator, if available
	 * 
	 * @param action The action to perform on the element
	 * @return Whether there was a previous element in the spliterator
	 */
	boolean tryReverseElement(Consumer<? super CollectionElement<T>> action);

	/**
	 * Gets the previous value in the spliterator, if available
	 * 
	 * @param action The action to perform on the value
	 * @return Whether there was a previous value in the spliterator
	 */
	default boolean tryReverse(Consumer<? super T> action) {
		return tryReverseElement(el -> action.accept(el.get()));
	}

	/** @param action The action to perform on all the previous elements in the spliterator */
	default void forEachReverseElement(Consumer<? super CollectionElement<T>> action) {
		while(tryReverseElement(action)){
		}
	}

	/** @param action The action to perform on all the previous values in the spliterator */
	default void forEachReverse(Consumer<? super T> action) {
		while (tryReverse(action)) {
		}
	}

	/**
	 * @return A reversed view of this spliterator, where {@link #tryAdvance(Consumer)} traverses this spliterator's element in reverse and
	 *         {@link #tryReverse(Consumer)} traverses them in the forward direction
	 */
	default ReversibleSpliterator<T> reverse() {
		return new ReversedSpliterator<>(this);
	}

	@Override
	ReversibleSpliterator<T> trySplit();

	/**
	 * Implements {@link ReversibleSpliterator#reverse()}
	 * 
	 * @param <T> The type of the values in this spliterator
	 */
	class ReversedSpliterator<T> implements ReversibleSpliterator<T> {
		private final ReversibleSpliterator<T> theWrapped;

		public ReversedSpliterator(ReversibleSpliterator<T> wrap) {
			theWrapped = wrap;
		}

		@Override
		public TypeToken<T> getType() {
			return theWrapped.getType();
		}

		@Override
		public long estimateSize() {
			return theWrapped.estimateSize();
		}

		@Override
		public int characteristics() {
			return theWrapped.characteristics();
		}

		@Override
		public boolean tryAdvance(Consumer<? super T> action) {
			return theWrapped.tryReverse(action);
		}

		@Override
		public boolean tryReverse(Consumer<? super T> action) {
			return theWrapped.tryAdvance(action);
		}

		@Override
		public void forEachRemaining(Consumer<? super T> action) {
			theWrapped.forEachReverse(action);
		}

		@Override
		public void forEachReverse(Consumer<? super T> action) {
			theWrapped.forEachRemaining(action);
		}

		@Override
		public void forEachElement(Consumer<? super CollectionElement<T>> action) {
			theWrapped.forEachReverseElement(action);
		}

		@Override
		public void forEachReverseElement(Consumer<? super CollectionElement<T>> action) {
			theWrapped.forEachElement(action);
		}

		@Override
		public ReversibleSpliterator<T> reverse() {
			return theWrapped;
		}

		@Override
		public boolean tryAdvanceElement(Consumer<? super CollectionElement<T>> action) {
			return theWrapped.tryReverseElement(action);
		}

		@Override
		public boolean tryReverseElement(Consumer<? super CollectionElement<T>> action) {
			return theWrapped.tryAdvanceElement(action);
		}

		@Override
		public ReversibleSpliterator<T> trySplit() {
			ReversibleSpliterator<T> wrapSpit = theWrapped.trySplit();
			if (wrapSpit == null)
				return null;
			return new ReversedSpliterator<>(wrapSpit);
		}
	}

	/**
	 * A partial {@link ListIterator} implementation backed by a {@link ReversibleSpliterator}
	 * 
	 * @param <T> The type of values in the spliterator
	 */
	abstract class PartialListIterator<T> implements ListIterator<T> {
		protected final ReversibleSpliterator<T> backing;
		private Ternian hasNext;
		private Ternian hasPrevious;
		private CollectionElement<T> element;
		private boolean elementIsNext;

		public PartialListIterator(ReversibleSpliterator<T> backing) {
			this.backing = backing;
			hasNext = Ternian.NONE;
			hasPrevious = Ternian.NONE;
		}

		@Override
		public boolean hasNext() {
			if (hasNext == Ternian.NONE) {
				hasNext = Ternian.of(backing.tryAdvanceElement(el -> {
					element = el;
					elementIsNext = true;
				}));
			}
			return hasNext.value;
		}

		@Override
		public T next() {
			if (!hasNext())
				throw new NoSuchElementException();
			if (!elementIsNext) {
				backing.tryAdvanceElement(el -> {
					element = el;
				});
			}
			elementIsNext = false;
			hasPrevious = Ternian.TRUE;
			hasNext = Ternian.NONE;
			return element.get();
		}

		@Override
		public boolean hasPrevious() {
			if (hasPrevious == Ternian.NONE) {
				hasPrevious = Ternian.of(backing.tryReverseElement(el -> {
					element = el;
					elementIsNext = false;
				}));
			}
			return hasPrevious.value;
		}

		@Override
		public T previous() {
			if (!hasPrevious())
				throw new NoSuchElementException();
			if (elementIsNext) {
				backing.tryReverseElement(el -> {
					element = el;
				});
			}
			elementIsNext = true;
			hasPrevious = Ternian.NONE;
			hasNext = Ternian.TRUE;
			return element.get();
		}

		@Override
		public void remove() {
			if (element == null)
				throw new IllegalStateException("Iteration has not begun");
			element.remove();
			element = null;
			hasPrevious = Ternian.NONE;
		}

		@Override
		public void set(T e) {
			if (element == null)
				throw new IllegalStateException("Iteration has not begun");
			element.set(e, null);
		}

		protected boolean isCachedNext() {
			return element != null && elementIsNext;
		}

		protected void clearCache() {
			element = null;
			hasNext = Ternian.NONE;
			hasPrevious = Ternian.NONE;
		}

		@Override
		public abstract int nextIndex();

		@Override
		public abstract int previousIndex();

		@Override
		public abstract void add(T e);

		@Override
		public String toString() {
			return backing.toString();
		}
	}
}

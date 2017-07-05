package org.qommons.collect;

import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.function.Consumer;

import org.qommons.Ternian;

import com.google.common.reflect.TypeToken;

/**
 * An {@link ElementSpliterator} that can traverse elements in either direction
 * 
 * @param <T> The type of values returned by the spliterator
 */
public interface ReversibleElementSpliterator<T> extends ElementSpliterator<T> {
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
	default ReversibleElementSpliterator<T> reverse() {
		return new ReversedElementSpliterator<>(this);
	}

	@Override
	ReversibleElementSpliterator<T> trySplit();

	/**
	 * @param <E> The compile-time type for the spliterator
	 * @param type The type for the ReversibleElementSpliterator
	 * @return An empty ReversibleElementSpliterator of the given type
	 */
	static <E> ReversibleElementSpliterator<E> empty(TypeToken<E> type) {
		return new ReversibleElementSpliterator<E>() {
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
			public TypeToken<E> getType() {
				return type;
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
			public ReversibleElementSpliterator<E> trySplit() {
				return null;
			}
		};
	}

	/**
	 * Implements {@link ReversibleElementSpliterator#reverse()}
	 * 
	 * @param <T> The type of the values in this spliterator
	 */
	class ReversedElementSpliterator<T> implements ReversibleElementSpliterator<T> {
		private final ReversibleElementSpliterator<T> theWrapped;

		public ReversedElementSpliterator(ReversibleElementSpliterator<T> wrap) {
			theWrapped = wrap;
		}

		protected ReversibleElementSpliterator<T> getWrapped() {
			return theWrapped;
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
		public long getExactSizeIfKnown() {
			return theWrapped.getExactSizeIfKnown();
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
		public boolean tryAdvanceElement(Consumer<? super CollectionElement<T>> action) {
			return theWrapped.tryReverseElement(action);
		}

		@Override
		public boolean tryReverseElement(Consumer<? super CollectionElement<T>> action) {
			return theWrapped.tryAdvanceElement(action);
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
		public ReversibleElementSpliterator<T> reverse() {
			return theWrapped;
		}

		@Override
		public ReversibleElementSpliterator<T> trySplit() {
			ReversibleElementSpliterator<T> wrapSpit = theWrapped.trySplit();
			if (wrapSpit == null)
				return null;
			return new ReversedElementSpliterator<>(wrapSpit);
		}
	}

	/**
	 * A partial {@link ListIterator} implementation backed by a {@link ReversibleElementSpliterator}
	 * 
	 * @param <T> The type of values in the spliterator
	 */
	abstract class PartialListIterator<T> implements ListIterator<T> {
		protected final ReversibleElementSpliterator<T> backing;
		private Ternian hasNext;
		private Ternian hasPrevious;
		private CollectionElement<T> element;
		private boolean elementIsNext;
		// False if the spliterator's cursor is on the leading (left) side of the cached element, true if on the trailing (right) side
		private boolean spliteratorSide;
		private boolean isReadyForRemove;

		public PartialListIterator(ReversibleElementSpliterator<T> backing) {
			this.backing = backing;
			hasNext = Ternian.NONE;
			hasPrevious = Ternian.NONE;
		}

		@Override
		public boolean hasNext() {
			if (hasNext == Ternian.NONE)
				getElement(true);
			return hasNext.value;
		}

		@Override
		public T next() {
			if (!hasNext())
				throw new NoSuchElementException();
			if (!elementIsNext)
				getElement(true);
			move(true);
			elementIsNext = false;
			hasPrevious = Ternian.TRUE;
			hasNext = Ternian.NONE;
			isReadyForRemove = true;
			return element.get();
		}

		@Override
		public boolean hasPrevious() {
			if (hasPrevious == Ternian.NONE)
				getElement(false);
			return hasPrevious.value;
		}

		@Override
		public T previous() {
			if (!hasPrevious())
				throw new NoSuchElementException();
			if (elementIsNext)
				getElement(false);
			move(false);
			elementIsNext = true;
			hasPrevious = Ternian.NONE;
			hasNext = Ternian.TRUE;
			isReadyForRemove = true;
			return element.get();
		}

		protected void getElement(boolean forward) {
			if (forward) {
				if (hasPrevious == Ternian.TRUE && !spliteratorSide) // Need to advance the spliterator over the cached previous
					backing.tryAdvance(v -> {
					});
				hasNext = Ternian.of(backing.tryAdvanceElement(el -> element = el));
			} else {
				if (hasNext == Ternian.TRUE && spliteratorSide) // Need to reverse the spliterator over the cached next
					backing.tryReverse(v -> {
					});
				hasPrevious = Ternian.of(backing.tryReverseElement(el -> element = el));
			}
			spliteratorSide = forward;
			elementIsNext = forward;
			isReadyForRemove = false;
		}

		protected void move(boolean forward) {}

		@Override
		public void remove() {
			if (!isReadyForRemove)
				throw new UnsupportedOperationException("Element has already been removed or iteration has not begun");
			element.remove();
			clearCache();
		}

		@Override
		public void set(T e) {
			if (!isReadyForRemove)
				throw new UnsupportedOperationException("Element has been removed or iteration has not begun");
			element.set(e, null);
		}

		protected void clearCache() {
			element = null;
			hasNext = Ternian.NONE;
			hasPrevious = Ternian.NONE;
			isReadyForRemove = false;
		}

		protected int getSpliteratorCursorOffset() {
			if (element == null)
				return 0;
			else if (elementIsNext)
				return spliteratorSide ? 1 : 0;
			else
				return spliteratorSide ? 0 : -1;
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

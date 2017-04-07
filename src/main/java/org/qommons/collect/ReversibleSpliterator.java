package org.qommons.collect;

import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import org.qommons.Ternian;

public interface ReversibleSpliterator<T> extends ElementSpliterator<T> {
	boolean tryReverseElement(Consumer<? super CollectionElement<T>> action);

	default boolean tryReverse(Consumer<? super T> action) {
		return tryReverseElement(el -> action.accept(el.get()));
	}

	default void forEachReverseElement(Consumer<? super CollectionElement<T>> action) {
		while(tryReverseElement(action)){
		}
	}

	default void forEachReverse(Consumer<? super T> action) {
		while (tryReverse(action)) {
		}
	}

	@Override
	ReversibleSpliterator<T> trySplit();

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
		}

		@Override
		public void set(T e) {
			if (element == null)
				throw new IllegalStateException("Iteration has not begun");
			element.set(e, null);
		}

		@Override
		public abstract int nextIndex();

		@Override
		public abstract int previousIndex();

		@Override
		public abstract void add(T e);
	}
}

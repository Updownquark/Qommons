package org.qommons.collect;

import java.util.Collection;
import java.util.function.Consumer;

public interface BetterCollection<E> extends Collection<E> {
	static String BAD_TYPE = "Object is the wrong type for this collection";

	@Override
	ElementSpliterator<E> spliterator();

	@Override
	default Betterator<E> iterator() {
		return new SpliteratorBetterator<>(spliterator());
	}

	/**
	 * An iterator backed by an {@link ElementSpliterator}
	 *
	 * @param <E> The type of elements to iterate over
	 */
	public static class SpliteratorBetterator<E> implements Betterator<E> {
		private final ElementSpliterator<E> theSpliterator;

		private boolean isNextCached;
		private boolean isDone;
		private CollectionElement<? extends E> cachedNext;

		public SpliteratorBetterator(ElementSpliterator<E> spliterator) {
			theSpliterator = spliterator;
		}

		@Override
		public boolean hasNext() {
			cachedNext = null;
			if (!isNextCached && !isDone) {
				if (theSpliterator.tryAdvanceElement(element -> {
					cachedNext = element;
				}))
					isNextCached = true;
				else
					isDone = true;
			}
			return isNextCached;
		}

		@Override
		public E next() {
			if (!hasNext())
				throw new java.util.NoSuchElementException();
			isNextCached = false;
			return cachedNext.get();
		}

		@Override
		public String canRemove() {
			if (cachedNext == null)
				throw new IllegalStateException(
					"First element has not been read, element has already been removed, or iterator has finished");
			if (isNextCached)
				throw new IllegalStateException("canRemove() must be called after next() and before the next call to hasNext()");
			return cachedNext.canRemove();
		}

		@Override
		public void remove() {
			if (cachedNext == null)
				throw new IllegalStateException(
					"First element has not been read, element has already been removed, or iterator has finished");
			if (isNextCached)
				throw new IllegalStateException("remove() must be called after next() and before the next call to hasNext()");
			cachedNext.remove();
			cachedNext = null;
		}

		@Override
		public String isAcceptable(E value) {
			if (cachedNext == null)
				throw new IllegalStateException(
					"First element has not been read, element has already been removed, or iterator has finished");
			if (isNextCached)
				throw new IllegalStateException("isAcceptable() must be called after next() and before the next call to hasNext()");
			if (!cachedNext.getType().getRawType().isInstance(value))
				return BAD_TYPE;
			return ((CollectionElement<E>) cachedNext).isAcceptable(value);
		}

		@Override
		public E set(E value, Object cause) {
			if (cachedNext == null)
				throw new IllegalStateException(
					"First element has not been read, element has already been removed, or iterator has finished");
			if (isNextCached)
				throw new IllegalStateException("set() must be called after next() and before the next call to hasNext()");
			if (!cachedNext.getType().getRawType().isInstance(value))
				throw new IllegalStateException(BAD_TYPE);
			return ((CollectionElement<E>) cachedNext).set(value, cause);
		}

		@Override
		public void forEachRemaining(Consumer<? super E> action) {
			if (isNextCached)
				action.accept(next());
			cachedNext = null;
			isDone = true;
			theSpliterator.forEachRemaining(action);
		}
	}
}

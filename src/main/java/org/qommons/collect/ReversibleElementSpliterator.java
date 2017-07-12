package org.qommons.collect;

import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.function.Consumer;

import org.qommons.Ternian;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

/**
 * An {@link ElementSpliterator} that can traverse elements in either direction
 * 
 * @param <E> The type of values returned by the spliterator
 */
public interface ReversibleElementSpliterator<E> extends ElementSpliterator<E> {
	/**
	 * Gets the previous element in the spliterator, if available
	 * 
	 * @param action The action to perform on the element
	 * @return Whether there was a previous element in the spliterator
	 */
	boolean tryReverseElement(Consumer<? super CollectionElement<E>> action);

	/**
	 * Gets the previous value in the spliterator, if available
	 * 
	 * @param action The action to perform on the value
	 * @return Whether there was a previous value in the spliterator
	 */
	default boolean tryReverse(Consumer<? super E> action) {
		return tryReverseElement(el -> action.accept(el.get()));
	}

	/** @param action The action to perform on all the previous elements in the spliterator */
	default void forEachReverseElement(Consumer<? super CollectionElement<E>> action) {
		while (tryReverseElement(action)) {
		}
	}

	/** @param action The action to perform on all the previous values in the spliterator */
	default void forEachReverse(Consumer<? super E> action) {
		while (tryReverse(action)) {
		}
	}

	/**
	 * @return A reversed view of this spliterator, where {@link #tryAdvance(Consumer)} traverses this spliterator's element in reverse and
	 *         {@link #tryReverse(Consumer)} traverses them in the forward direction
	 */
	default ReversibleElementSpliterator<E> reverse() {
		return new ReversedElementSpliterator<>(this);
	}

	@Override
	default ReversibleSpliterator<E> immutable() {
		return new ImmutableReversibleElementSpliterator<>(this);
	}

	@Override
	ReversibleElementSpliterator<E> trySplit();

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

	@Override
	default <T> ReversibleElementSpliterator<T> map(ElementSpliteratorMap<E, T> map) {
		return new MappedReversibleElementSpliterator<>(this, map);
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
			return theWrapped.tryReverseElement(el -> action.accept(new ReversedCollectionElement<>(el)));
		}

		@Override
		public boolean tryReverseElement(Consumer<? super CollectionElement<T>> action) {
			return theWrapped.tryAdvanceElement(el -> action.accept(new ReversedCollectionElement<>(el)));
		}

		@Override
		public void forEachElement(Consumer<? super CollectionElement<T>> action) {
			theWrapped.forEachReverseElement(el -> action.accept(new ReversedCollectionElement<>(el)));
		}

		@Override
		public void forEachReverseElement(Consumer<? super CollectionElement<T>> action) {
			theWrapped.forEachElement(el -> action.accept(new ReversedCollectionElement<>(el)));
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

	class ReversedCollectionElement<E> implements CollectionElement<E> {
		private final CollectionElement<E> theWrapped;

		public ReversedCollectionElement(CollectionElement<E> wrapped) {
			theWrapped = wrapped;
		}

		protected CollectionElement<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public E get() {
			return theWrapped.get();
		}

		@Override
		public Value<String> isEnabled() {
			return theWrapped.isEnabled();
		}

		@Override
		public <V extends E> String isAcceptable(V value) {
			return theWrapped.isAcceptable(value);
		}

		@Override
		public <V extends E> E set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			return theWrapped.set(value, cause);
		}

		@Override
		public String canRemove() {
			return theWrapped.canRemove();
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			theWrapped.remove();
		}

		@Override
		public String canAdd(E value, boolean before) {
			return theWrapped.canAdd(value, !before);
		}

		@Override
		public void add(E value, boolean before, Object cause) throws UnsupportedOperationException, IllegalArgumentException {
			theWrapped.add(value, !before, cause);
		}
	}

	class ImmutableReversibleElementSpliterator<E> extends ImmutableElementSpliterator<E> implements ReversibleSpliterator<E> {
		public ImmutableReversibleElementSpliterator(ReversibleElementSpliterator<E> wrapped) {
			super(wrapped);
		}

		@Override
		protected ReversibleElementSpliterator<E> getWrapped() {
			return (ReversibleElementSpliterator<E>) super.getWrapped();
		}

		@Override
		public boolean tryReverse(Consumer<? super E> action) {
			return getWrapped().tryReverse(action);
		}

		@Override
		public ReversibleSpliterator<E> trySplit() {
			ReversibleElementSpliterator<E> split = getWrapped().trySplit();
			return split == null ? null : split.immutable();
		}
	}

	class MappedReversibleElementSpliterator<E, T> extends MappedElementSpliterator<E, T> implements ReversibleElementSpliterator<T> {
		public MappedReversibleElementSpliterator(ReversibleElementSpliterator<E> source, ElementSpliteratorMap<E, T> map) {
			super(source, map);
		}

		@Override
		protected ReversibleElementSpliterator<E> getSource() {
			return (ReversibleElementSpliterator<E>) super.getSource();
		}

		@Override
		public boolean tryReverseElement(Consumer<? super CollectionElement<T>> action) {
			while (getSource().tryReverseElement(el -> {
				getElement().setSource(el);
				if (getElement().isAccepted())
					action.accept(getElement());
			})) {
				if (getElement().isAccepted())
					return true;
			}
			return false;
		}

		@Override
		public void forEachReverseElement(Consumer<? super CollectionElement<T>> action) {
			getSource().forEachReverseElement(el -> {
				getElement().setSource(el);
				if (getElement().isAccepted())
					action.accept(getElement());
			});
		}

		@Override
		public boolean tryReverse(Consumer<? super T> action) {
			if (getMap().canFilterValues()) {
				boolean[] accepted = new boolean[1];
				while (!accepted[0] && getSource().tryReverse(v -> {
					accepted[0] = getMap().test(v);
					if (accepted[0])
						action.accept(getMap().map(v));
				})) {
				}
				return accepted[0];
			} else
				return ReversibleElementSpliterator.super.tryReverse(action);
		}

		@Override
		public void forEachReverse(Consumer<? super T> action) {
			if (getMap().canFilterValues()) {
				getSource().forEachReverse(v -> {
					if (getMap().test(v))
						action.accept(getMap().map(v));
				});
			} else
				ReversibleElementSpliterator.super.forEachReverse(action);
		}

		@Override
		public ReversibleElementSpliterator<T> trySplit() {
			ReversibleElementSpliterator<E> split = getSource().trySplit();
			return split == null ? null : split.map(getMap());
		}

		@Override
		public ReversibleSpliterator<T> immutable() {
			if (getMap().canFilterValues()) {
				ReversibleSpliterator<E> srcSplit = getSource().immutable();
				return new MappedReversibleSpliterator<>(srcSplit, getMap());
			} else
				return ReversibleElementSpliterator.super.immutable();
		}
	}

	/**
	 * A Spliterator mapped by an {@link ElementSpliterator.ElementSpliteratorMap}
	 * 
	 * @param <T> The type of the source spliterator
	 * @param <E> The type of this spliterator
	 */
	class MappedReversibleSpliterator<T, E> extends MappedSpliterator<T, E> implements ReversibleSpliterator<E> {
		public MappedReversibleSpliterator(ReversibleSpliterator<T> source, ElementSpliteratorMap<T, E> map) {
			super(source, map);
		}

		@Override
		protected ReversibleSpliterator<T> getSource() {
			return (ReversibleSpliterator<T>) super.getSource();
		}

		@Override
		public boolean tryReverse(Consumer<? super E> action) {
			boolean[] accepted = new boolean[1];
			while (!accepted[0] && getSource().tryReverse(v -> {
				accepted[0] = getMap().test(v);
				if (accepted[0])
					action.accept(getMap().map(v));
			})) {
			}
			return accepted[0];
		}

		@Override
		public void forEachReverse(Consumer<? super E> action) {
			getSource().forEachReverse(v -> {
				if (getMap().test(v))
					action.accept(getMap().map(v));
			});
		}

		@Override
		public ReversibleSpliterator<E> trySplit() {
			ReversibleSpliterator<T> split = getSource().trySplit();
			return new MappedReversibleSpliterator<>(split, getMap());
		}
	}

	/**
	 * A partial {@link ListIterator} implementation backed by a {@link ReversibleElementSpliterator}
	 * 
	 * @param <T> The type of values in the spliterator
	 */
	abstract class SpliteratorListIterator<T> implements ListIterator<T> {
		protected final ReversibleElementSpliterator<T> backing;
		private Ternian hasNext;
		private Ternian hasPrevious;
		private CollectionElement<T> element;
		private boolean elementIsNext;
		// False if the spliterator's cursor is on the leading (left) side of the cached element, true if on the trailing (right) side
		private boolean spliteratorSide;
		private boolean isReadyForRemove;

		public SpliteratorListIterator(ReversibleElementSpliterator<T> backing) {
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
		public void add(T e) {
			if (!isReadyForRemove)
				throw new UnsupportedOperationException("Element has been removed or iteration has not begun");
			element.add(e, true, null);
		}

		@Override
		public String toString() {
			return backing.toString();
		}
	}
}

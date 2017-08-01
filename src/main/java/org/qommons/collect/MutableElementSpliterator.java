package org.qommons.collect;

import java.util.Comparator;
import java.util.Spliterator;
import java.util.function.Consumer;

import org.qommons.value.Settable;

/**
 * A {@link Spliterator} that allows the option of providing its values wrapped in a {@link MutableCollectionElement}, which allows elements in the
 * source collection to be replaced (using {@link Settable#set(Object, Object)}) or {@link MutableCollectionElement#remove() removed} during
 * iteration.
 * 
 * ElementSpliterators are {@link #trySplit() splittable} just as Spliterators are, though the added functionality (particularly
 * {@link MutableCollectionElement#remove()}) may be disabled for split spliterators.
 * 
 * @param <E> The type of values that this MutableElementSpliterator provides
 */
public interface MutableElementSpliterator<E> extends ElementSpliterator<E> {
	/**
	 * Like {@link #tryAdvanceElement(Consumer)}, but provides a mutable element handle
	 * 
	 * @param action The action to perform on the element
	 * @return false if no element was available
	 */
	boolean tryAdvanceElementM(Consumer<? super MutableCollectionElement<E>> action);

	/**
	 * Like {@link #tryReverseElement(Consumer)}, but provides a mutable element handle
	 * 
	 * @param action The action to perform on the element
	 * @return false if no element was available
	 */
	boolean tryReverseElementM(Consumer<? super MutableCollectionElement<E>> action);

	/**
	 * Operates on each element remaining in this MutableElementSpliterator
	 * 
	 * @param action The action to perform on each element
	 */
	default void forEachElementM(Consumer<? super MutableCollectionElement<E>> action) {
		while (tryAdvanceElementM(action)) {
		}
	}

	/**
	 * Operates on each previous element remaining in this MutableElementSpliterator
	 * 
	 * @param action The action to perform on each element
	 */
	default void forEachElementReverseM(Consumer<? super MutableCollectionElement<E>> action) {
		while (tryReverseElementM(action)) {
		}
	}

	@Override
	MutableElementSpliterator<E> trySplit();

	@Override
	default MutableElementSpliterator<E> reverse() {
		return new ReversedMutableSpliterator<>(this);
	}

	/** @return An immutable spliterator backed by this spliterator */
	default ElementSpliterator<E> immutable() {
		return new ImmutableElementSpliterator<>(this);
	}

	/**
	 * @param <E> The type for the spliterator
	 * @return An empty MutableElementSpliterator of the given type
	 */
	static <E> MutableElementSpliterator<E> empty() {
		return new EmptyMutableSpliterator<>();
	}

	class ReversedMutableSpliterator<E> extends ReversedElementSpliterator<E> implements MutableElementSpliterator<E> {
		public ReversedMutableSpliterator(ElementSpliterator<E> wrap) {
			super(wrap);
		}

		@Override
		protected MutableElementSpliterator<E> getWrapped() {
			return (MutableElementSpliterator<E>) super.getWrapped();
		}

		@Override
		public boolean tryAdvanceElementM(Consumer<? super MutableCollectionElement<E>> action) {
			return getWrapped().tryReverseElementM(el -> action.accept(el.reverse()));
		}

		@Override
		public boolean tryReverseElementM(Consumer<? super MutableCollectionElement<E>> action) {
			return getWrapped().tryAdvanceElementM(el -> action.accept(el.reverse()));
		}

		@Override
		public MutableElementSpliterator<E> reverse() {
			return getWrapped();
		}

		@Override
		public MutableElementSpliterator<E> trySplit() {
			MutableElementSpliterator<E> split = getWrapped().trySplit();
			return split == null ? null : new ReversedMutableSpliterator<>(split);
		}
	}

	class ImmutableElementSpliterator<E> implements ElementSpliterator<E> {
		private final MutableElementSpliterator<E> theWrapped;

		public ImmutableElementSpliterator(MutableElementSpliterator<E> wrapped) {
			theWrapped = wrapped;
		}

		protected MutableElementSpliterator<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action) {
			return theWrapped.tryAdvanceElement(action);
		}

		@Override
		public boolean tryReverseElement(Consumer<? super CollectionElement<E>> action) {
			return theWrapped.tryReverseElement(action);
		}

		@Override
		public ElementSpliterator<E> trySplit() {
			MutableElementSpliterator<E> split = theWrapped.trySplit();
			return split == null ? null : split.immutable();
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
		public Comparator<? super E> getComparator() {
			return theWrapped.getComparator();
		}
	}

	class EmptyMutableSpliterator<E> extends EmptyElementSpliterator<E> implements MutableElementSpliterator<E> {
		@Override
		public boolean tryAdvanceElementM(Consumer<? super MutableCollectionElement<E>> action) {
			return false;
		}

		@Override
		public boolean tryReverseElementM(Consumer<? super MutableCollectionElement<E>> action) {
			return false;
		}

		@Override
		public MutableElementSpliterator<E> trySplit() {
			return null;
		}
	}
}

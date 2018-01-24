package org.qommons.collect;

import java.util.Comparator;
import java.util.Spliterator;
import java.util.function.Consumer;

import org.qommons.Causable;
import org.qommons.Transactable;
import org.qommons.Transaction;

/**
 * A {@link Spliterator} that allows the option of providing its values wrapped in a {@link MutableCollectionElement}, which allows elements
 * in the source collection to be replaced (using {@link MutableCollectionElement#set(Object)}) or {@link MutableCollectionElement#remove()
 * removed} during iteration.
 * 
 * ElementSpliterators are {@link #trySplit() splittable} just as Spliterators are, though the added functionality (particularly
 * {@link MutableCollectionElement#remove()}) may be disabled for split spliterators.
 * 
 * @param <E> The type of values that this MutableElementSpliterator provides
 */
public interface MutableElementSpliterator<E> extends ElementSpliterator<E> {
	/**
	 * Like {@link #forElement(Consumer, boolean)}, but provides a mutable element
	 * 
	 * @param action The action to perform on the element
	 * @param forward Whether to get the next or previous element
	 * @return false if no element was available
	 */
	boolean forElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward);

	/**
	 * Operates on each element remaining in this MutableElementSpliterator
	 * 
	 * @param forward Whether to get the next or previous element
	 * @param action The action to perform on each element
	 */
	void forEachElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward);

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

	/**
	 * Implements {@link MutableElementSpliterator#reverse()}
	 * 
	 * @param <E> The type of the spliterator
	 */
	class ReversedMutableSpliterator<E> extends ReversedElementSpliterator<E> implements MutableElementSpliterator<E> {
		public ReversedMutableSpliterator(ElementSpliterator<E> wrap) {
			super(wrap);
		}

		@Override
		protected MutableElementSpliterator<E> getWrapped() {
			return (MutableElementSpliterator<E>) super.getWrapped();
		}

		@Override
		public boolean forElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward) {
			return getWrapped().forElementM(el -> action.accept(el.reverse()), !forward);
		}

		@Override
		public void forEachElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward) {
			getWrapped().forEachElementM(el -> action.accept(el.reverse()), !forward);
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

	/**
	 * Implements {@link MutableElementSpliterator#immutable()}
	 * 
	 * @param <E> The type of the spliterator
	 */
	class ImmutableElementSpliterator<E> implements ElementSpliterator<E> {
		private final MutableElementSpliterator<E> theWrapped;

		public ImmutableElementSpliterator(MutableElementSpliterator<E> wrapped) {
			theWrapped = wrapped;
		}

		protected MutableElementSpliterator<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public boolean forElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
			return theWrapped.forElement(action, forward);
		}

		@Override
		public void forEachElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
			theWrapped.forEachElement(action, forward);
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

	/**
	 * An empty spliterator
	 * 
	 * @param <E> The type of the spliterator
	 */
	class EmptyMutableSpliterator<E> extends EmptyElementSpliterator<E> implements MutableElementSpliterator<E> {
		@Override
		public boolean forElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward) {
			return false;
		}

		@Override
		public void forEachElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward) {
		}

		@Override
		public MutableElementSpliterator<E> trySplit() {
			return null;
		}
	}

	/**
	 * A partial {@link MutableCollectionElement} implementation
	 * 
	 * @param <E> The type of the spliterator
	 */
	abstract class SimpleMutableSpliterator<E> extends SimpleSpliterator<E> implements MutableElementSpliterator<E> {
		public SimpleMutableSpliterator(Transactable locker) {
			super(locker);
		}

		protected abstract boolean internalForElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward);

		@Override
		protected boolean internalForElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
			return internalForElementM(el -> action.accept(el.immutable()), forward);
		}

		@Override
		public boolean forElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward) {
			try (Transaction t = theLocker == null ? Transaction.NONE : theLocker.lock(true, null)) {
				return internalForElementM(action, forward);
			}
		}

		@Override
		public void forEachElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward) {
			Causable cause = Causable.simpleCause(null);
			try (Transaction cst = Causable.use(cause);
				Transaction t = theLocker == null ? Transaction.NONE : theLocker.lock(true, cause)) {
				while (internalForElementM(action, forward)) {
				}
			}
		}
	}
}

package org.qommons.collect;

import java.util.Collection;
import java.util.Set;

/**
 * A {@link Set} that is also a {@link BetterCollection}
 * 
 * @param <E> The type of values in the set
 */
public interface BetterSet<E> extends BetterCollection<E>, Set<E> {
	@Override
	default ImmutableIterator<E> iterator() {
		return BetterCollection.super.iterator();
	}

	@Override
	default ElementSpliterator<E> spliterator() {
		return BetterCollection.super.spliterator();
	}

	@Override
	default Object[] toArray() {
		return BetterCollection.super.toArray();
	}

	@Override
	default boolean contains(Object o) {
		return BetterCollection.super.contains(o);
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		return BetterCollection.super.containsAll(c);
	}

	@Override
	default boolean remove(Object o) {
		return BetterCollection.super.remove(o);
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		return BetterCollection.super.removeAll(c);
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		return BetterCollection.super.retainAll(c);
	}

	@Override
	default BetterSet<E> reverse() {
		return new ReversedBetterSet<>(this);
	}

	class ReversedBetterSet<E> extends ReversedCollection<E> implements BetterSet<E> {
		public ReversedBetterSet(BetterSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected BetterSet<E> getWrapped() {
			return (BetterSet<E>) super.getWrapped();
		}

		@Override
		public BetterSet<E> reverse() {
			return getWrapped();
		}
	}
}

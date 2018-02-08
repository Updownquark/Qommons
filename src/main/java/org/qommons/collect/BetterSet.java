package org.qommons.collect;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import org.qommons.Transaction;

/**
 * A {@link Set} that is also a {@link BetterCollection}.
 * 
 * See <a href="https://github.com/Updownquark/Qommons/wiki/BetterCollection-API#betterset">the wiki</a> for more detail.
 * 
 * @param <E> The type of values in the set
 */
public interface BetterSet<E> extends BetterCollection<E>, TransactableSet<E> {
	@Override
	default Iterator<E> iterator() {
		return BetterCollection.super.iterator();
	}

	@Override
	default MutableElementSpliterator<E> spliterator() {
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
	default BetterSet<E> with(E... values) {
		BetterCollection.super.with(values);
		return this;
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

	/**
	 * A default toString() method for set implementations to use
	 *
	 * @param set The set to print
	 * @return The string representation of the set
	 */
	public static String toString(BetterSet<?> set) {
		StringBuilder ret = new StringBuilder("{");
		boolean first = true;
		try (Transaction t = set.lock(false, null)) {
			for (Object value : set) {
				if (!first) {
					ret.append(", ");
				} else
					first = false;
				ret.append(value);
			}
		}
		ret.append('}');
		return ret.toString();
	}

	/**
	 * Implements {@link BetterSet#reverse()}
	 * 
	 * @param <E> The type of the set
	 */
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

		@Override
		public String toString() {
			return BetterSet.toString(this);
		}
	}
}

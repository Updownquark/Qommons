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

	/**
	 * Retrieves the element in this set equivalent to the given value, if present. Otherwise, the value is added, the <code>added</code>
	 * runnable is invoked (if supplied), and the new element returned.
	 * 
	 * @param value The value to get or add
	 * @param first Whether (if not present) to prefer to add the value to the beginning or end of the set
	 * @param added The runnable which, if not null will be {@link Runnable#run() invoked} if the value is added to the set in this
	 *        operation
	 * @return The element containing the value, or null if the element was not present AND could not be added for any reason
	 */
	CollectionElement<E> getOrAdd(E value, boolean first, Runnable added);

	@Override
	default BetterSet<E> with(E... values) {
		BetterCollection.super.with(values);
		return this;
	}

	@Override
	default BetterSet<E> withAll(Collection<? extends E> values) {
		BetterCollection.super.withAll(values);
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
	 * @param <E> The type of the set
	 * @return An immutable, empty set
	 */
	public static <E> BetterSet<E> empty() {
		return new EmptySet<>();
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
		public CollectionElement<E> getOrAdd(E value, boolean first, Runnable added) {
			return CollectionElement.reverse(getWrapped().getOrAdd(value, !first, added));
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

	/**
	 * Implements {@link BetterSet#empty()}
	 * 
	 * @param <E> The type of the set
	 */
	class EmptySet<E> extends BetterCollection.EmptyCollection<E> implements BetterSet<E> {
		@Override
		public CollectionElement<E> getOrAdd(E value, boolean first, Runnable added) {
			return null;
		}
	}
}

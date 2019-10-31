package org.qommons.collect;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import org.qommons.Lockable;
import org.qommons.StructuredTransactable;
import org.qommons.Transactable;
import org.qommons.Transaction;

/**
 * A {@link Set} that is also a {@link BetterCollection}.
 * 
 * See <a href="https://github.com/Updownquark/Qommons/wiki/BetterCollection-API#betterset">the wiki</a> for more detail.
 * 
 * @param <E> The type of values in the set
 */
public interface BetterSet<E> extends ValueStoredCollection<E>, TransactableSet<E> {
	@Override
	default Iterator<E> iterator() {
		return ValueStoredCollection.super.iterator();
	}

	@Override
	default MutableElementSpliterator<E> spliterator() {
		return ValueStoredCollection.super.spliterator();
	}

	@Override
	default Object[] toArray() {
		return ValueStoredCollection.super.toArray();
	}

	@Override
	default boolean contains(Object o) {
		return ValueStoredCollection.super.contains(o);
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		return ValueStoredCollection.super.containsAll(c);
	}

	@Override
	default boolean remove(Object o) {
		return ValueStoredCollection.super.remove(o);
	}

	@Override
	default BetterSet<E> with(E... values) {
		ValueStoredCollection.super.with(values);
		return this;
	}

	@Override
	default BetterSet<E> withAll(Collection<? extends E> values) {
		ValueStoredCollection.super.withAll(values);
		return this;
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		return ValueStoredCollection.super.removeAll(c);
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		return ValueStoredCollection.super.retainAll(c);
	}

	@Override
	default BetterSet<E> reverse() {
		return new ReversedBetterSet<>(this);
	}

	/**
	 * A static utility method to be used by {@link BetterCollection#hashCode()} implementations
	 * 
	 * @param c The collection
	 * @return The hash code for the collection's content
	 */
	public static int hashCode(Collection<?> c) {
		try (Transaction t = Transactable.lock(c, false, null)) {
			int hash = 0;
			for (Object v : c)
				hash += v == null ? 0 : v.hashCode();
			return hash;
		}
	}

	/**
	 * A static utility method to be used by {@link BetterCollection#equals(Object)} implementations
	 * 
	 * @param c The collection
	 * @param o The object to compare with the collection
	 * @return Whether <code>o</code> is a collection whose content matches that of <code>c</code>
	 */
	public static boolean equals(Set<?> c, Object o) {
		if (!(o instanceof Set))
			return false;
		try (Transaction t = Lockable.lockAll(//
			c instanceof StructuredTransactable ? Lockable.lockable((StructuredTransactable) c, false, false) : null, //
			o instanceof StructuredTransactable ? Lockable.lockable((StructuredTransactable) o, false, false) : null)) {
			Set<?> c2 = (Set<?>) o;
			Iterator<?> iter = c.iterator();
			while (iter.hasNext()) {
				if (!c2.contains(iter.next()))
					return false;
			}
			return true;
		}
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
		public boolean isConsistent(ElementId element) {
			return getWrapped().isConsistent(element.reverse());
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<E, X> listener) {
			RepairListener<E, X> reversedListener = listener == null ? null : new ReversedRepairListener<>(listener);
			return getWrapped().repair(element, reversedListener);
		}

		@Override
		public boolean checkConsistency() {
			return getWrapped().checkConsistency();
		}

		@Override
		public <X> boolean repair(RepairListener<E, X> listener) {
			RepairListener<E, X> reversedListener = listener == null ? null : new ReversedRepairListener<>(listener);
			return getWrapped().repair(reversedListener);
		}

		@Override
		public BetterSet<E> reverse() {
			return getWrapped();
		}

		@Override
		public String toString() {
			return BetterSet.toString(this);
		}

		public static class ReversedRepairListener<E, X> implements RepairListener<E, X> {
			private final RepairListener<E, X> theWrapped;

			public ReversedRepairListener(org.qommons.collect.ValueStoredCollection.RepairListener<E, X> wrapped) {
				theWrapped = wrapped;
			}

			@Override
			public X removed(CollectionElement<E> element) {
				return theWrapped.removed(element.reverse());
			}

			@Override
			public void disposed(E value, X data) {
				theWrapped.disposed(value, data);
			}

			@Override
			public void transferred(CollectionElement<E> element, X data) {
				theWrapped.transferred(element.reverse(), data);
			}
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

		@Override
		public boolean isConsistent(ElementId element) {
			throw new NoSuchElementException();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<E, X> listener) {
			throw new NoSuchElementException();
		}

		@Override
		public boolean checkConsistency() {
			return false;
		}

		@Override
		public <X> boolean repair(org.qommons.collect.BetterSet.RepairListener<E, X> listener) {
			return false;
		}
	}
}

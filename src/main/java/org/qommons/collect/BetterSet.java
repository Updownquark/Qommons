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
	 * <p>
	 * A search method that works to detect inconsistencies in the set's storage structure during the search.
	 * </p>
	 * <p>
	 * See {@link #checkConsistency()} and <a href="https://github.com/Updownquark/Qommons/wiki/BetterCollection-API#features-2">BetterSet
	 * Features</a>
	 * </p>
	 * <p>
	 * If any inconsistency is found, the invalid flag will be set to true and null will be returned. Otherwise, this method functions
	 * exactly as {@link #getElement(Object, boolean)} (the <code>first</code> flat is irrelevant for a set).
	 * </p>
	 * <p>
	 * This method only checks for inconsistencies along the search path for the given value. A null result with no inconsistencies detected
	 * is NOT a guarantee that the value is not present in the set. Values can be orphaned in the set in ways that are not detectable
	 * without a complete search. For a complete consistency search, use {@link #checkConsistency()}.
	 * </p>
	 * 
	 * @param value The value to search for
	 * @param invalid The invalid flag to set to true if inconsistency is detected
	 * @return The element, or null if inconsistency is detected or no such element is found in the set
	 */
	CollectionElement<E> searchWithConsistencyDetection(E value, boolean[] invalid);

	/**
	 * <p>
	 * Searches for any inconsistencies in the set's storage structure. This typically takes linear time.
	 * </p>
	 * <p>
	 * Such inconsistencies typically arise from changes to the properties of a stored value that this set uses to store by. For example, if
	 * a set of items are stored by name (esp. a sorted set) and then an item's name changes, the storage structure becomes inconsistent.
	 * This may affect the ability to retrieve the element by value and may also affect the searchability of other items in the set.
	 * </p>
	 * <p>
	 * For more information, see <a href="https://github.com/Updownquark/Qommons/wiki/BetterCollection-API#features-2">BetterSet
	 * Features</a>
	 * </p>
	 * 
	 * @return Whether any inconsistency was found in the set
	 */
	boolean checkConsistency();

	/**
	 * An interface to monitor #repair on a set.
	 * 
	 * @param <E> The type of elements in the set
	 * @param <X> The type of the custom data to keep track of transfer operations
	 */
	interface RepairListener<E, X> {
		/**
		 * <p>
		 * Called after an element is removed from the set due to a collision with a different element. It will also be called if the
		 * element is no longer compatible with this set (e.g. a sub-set).
		 * </p>
		 * <p>
		 * For some sets, especially sub-sets, it is not possible for the view to determine whether an element previously belonged to the
		 * set. So this method may be called for elements that were not present.
		 * </p>
		 * 
		 * @param element The element removed due to a collision or due to the element no longer being compatible with this set (i.e. a
		 *        sub-set)
		 */
		void removed(CollectionElement<E> element);

		/**
		 * <p>
		 * Called after an element is removed, before its value is transferred to a new position in the set. This will be immediately
		 * followed by a call to {@link #postTransfer(CollectionElement, Object)} with a new element with the same value.
		 * </p>
		 * <p>
		 * For some sets, especially sub-sets, it is not possible for the view to determine whether an element previously belonged to the
		 * set. So this method may be called for elements that were not present previously, but are now.
		 * </p>
		 * 
		 * @param element The element, having just been removed, which will immediately be transferred to another position in the set, with
		 *        a corresponding call to {@link #postTransfer(CollectionElement, Object)}
		 * @return A piece of data which will be given as the second argument to {@link #postTransfer(CollectionElement, Object)} as a means
		 *         of tracking
		 */
		X preTransfer(CollectionElement<E> element);

		/**
		 * Called after an element is transferred to a new position in the set. Typically, this will be immediately after a corresponding
		 * call to {@link #preTransfer(CollectionElement)}, but if it can be determined that the element was previously not present in this
		 * collection, but now as as a result of the transfer, {@link #preTransfer(CollectionElement)} may not be called first and
		 * <code>data</code> will be null.
		 * 
		 * @param element The element previously removed (with a corresponding call to {@link #preTransfer(CollectionElement)}) and now
		 *        re-added in a different position within the set
		 * @param data The data returned from the {@link #preTransfer(CollectionElement)} call, or null if the pre-transferred element was
		 *        not a member of this set
		 */
		void postTransfer(CollectionElement<E> element, X data);
	}

	/**
	 * <p>
	 * Searches for and fixes any inconsistencies in the set's storage structure.
	 * </p>
	 * <p>
	 * See {@link #checkConsistency()} and <a href="https://github.com/Updownquark/Qommons/wiki/BetterCollection-API#features-2">BetterSet
	 * Features</a>
	 * </p>
	 * 
	 * @param <X> The type of the data transferred for the listener
	 * @param listener The listener to monitor repairs. May be null.
	 * @return Whether any inconsistencies were found
	 */
	<X> boolean repair(RepairListener<E, X> listener);

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
		public CollectionElement<E> searchWithConsistencyDetection(E value, boolean[] invalid) {
			return CollectionElement.reverse(getWrapped().searchWithConsistencyDetection(value, invalid));
		}

		@Override
		public boolean checkConsistency() {
			return getWrapped().checkConsistency();
		}

		@Override
		public <X> boolean repair(RepairListener<E, X> listener) {
			RepairListener<E, X> reversedListener = listener == null ? null : new RepairListener<E, X>() {
				@Override
				public void removed(CollectionElement<E> element) {
					listener.removed(element.reverse());
				}

				@Override
				public X preTransfer(CollectionElement<E> element) {
					return listener.preTransfer(element.reverse());
				}

				@Override
				public void postTransfer(CollectionElement<E> element, X data) {
					listener.postTransfer(element.reverse(), data);
				}
			};
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
	}

	/**
	 * Implements {@link BetterSet#empty()}
	 * 
	 * @param <E> The type of the set
	 */
	class EmptySet<E> extends BetterCollection.EmptyCollection<E> implements BetterSet<E> {
		@Override
		public CollectionElement<E> searchWithConsistencyDetection(E value, boolean[] invalid) {
			return null;
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

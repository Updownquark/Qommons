package org.qommons.collect;

/**
 * A {@link BetterCollection} in which elements are stored by some property of their values
 * 
 * @param <E> The type of value stored in the collection
 * @see BetterSet
 */
public interface ValueStoredCollection<E> extends BetterCollection<E> {
	/**
	 * Retrieves the element in this collection equivalent to the given value, if present. Otherwise, the value is added, the
	 * <code>added</code> runnable is invoked (if supplied), and the new element returned.
	 * 
	 * @param value The value to get or add
	 * @param first Whether (if not present) to prefer to add the value to the beginning or end of the collection
	 * @param added The runnable which, if not null will be {@link Runnable#run() invoked} if the value is added to the collection in this
	 *        operation
	 * @return The element containing the value, or null if the element was not present AND could not be added for any reason
	 */
	CollectionElement<E> getOrAdd(E value, boolean first, Runnable added);

	/**
	 * <p>
	 * A search method that may work to detect inconsistencies in the collection's storage structure during the search.
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
	 * This method only checks for inconsistencies as needed to search for the given value. A null result with no inconsistencies detected
	 * is NOT a guarantee that the value is not present in the collection. Values can be orphaned in the set in ways that are not detectable
	 * without a complete search. For a complete consistency search, use {@link #checkConsistency()}.
	 * </p>
	 * 
	 * @param value The value to search for
	 * @param invalid The invalid flag to set to true if inconsistency is detected
	 * @return The element, or null if inconsistency is detected or no such element is found in the collection
	 */
	CollectionElement<E> searchWithConsistencyDetection(E value, boolean[] invalid);

	/**
	 * <p>
	 * Searches for any inconsistencies in the collection's storage structure. This typically takes linear time.
	 * </p>
	 * <p>
	 * Such inconsistencies typically arise from changes to the properties of a stored value that this collection uses to store by. For
	 * example, if a set of items are stored by name (esp. a sorted set) and then an item's name changes, the storage structure becomes
	 * inconsistent. This may affect the ability to retrieve the element by value and may also affect the searchability of other items in
	 * the collection.
	 * </p>
	 * <p>
	 * For more information, see <a href="https://github.com/Updownquark/Qommons/wiki/BetterCollection-API#features-2">BetterSet
	 * Features</a>
	 * </p>
	 * 
	 * @return Whether any inconsistency was found in the collection
	 */
	boolean checkConsistency();

	/**
	 * An interface to monitor #repair on a collection.
	 * 
	 * @param <E> The type of elements in the collection
	 * @param <X> The type of the custom data to keep track of transfer operations
	 */
	interface RepairListener<E, X> {
		/**
		 * <p>
		 * Called after an element is removed from the collection due to a collision with a different element. It will also be called if the
		 * element is no longer compatible with this collection (e.g. a sub-set).
		 * </p>
		 * <p>
		 * For some collection, especially sub-sets, it is not possible for the view to determine whether an element previously belonged to
		 * the set. So this method may be called for elements that were not present.
		 * </p>
		 * 
		 * @param element The element removed due to a collision or due to the element no longer being compatible with this collection (i.e.
		 *        a sub-set)
		 */
		void removed(CollectionElement<E> element);

		/**
		 * <p>
		 * Called after an element is removed, before its value is transferred to a new position in the collection. This will be immediately
		 * followed by a call to {@link #postTransfer(CollectionElement, Object)} with a new element with the same value.
		 * </p>
		 * <p>
		 * For some collection, especially sub-sets, it is not possible for the view to determine whether an element previously belonged to
		 * the set. So this method may be called for elements that were not present previously, but are now.
		 * </p>
		 * 
		 * @param element The element, having just been removed, which will immediately be transferred to another position in the
		 *        collection, with a corresponding call to {@link #postTransfer(CollectionElement, Object)}
		 * @return A piece of data which will be given as the second argument to {@link #postTransfer(CollectionElement, Object)} as a means
		 *         of tracking
		 */
		X preTransfer(CollectionElement<E> element);

		/**
		 * Called after an element is transferred to a new position in the collection. Typically, this will be immediately after a
		 * corresponding call to {@link #preTransfer(CollectionElement)}, but if it can be determined that the element was previously not
		 * present in this collection, but now is as a result of the transfer, {@link #preTransfer(CollectionElement)} may not be called
		 * first and <code>data</code> will be null.
		 * 
		 * @param element The element previously removed (with a corresponding call to {@link #preTransfer(CollectionElement)}) and now
		 *        re-added in a different position within the collection
		 * @param data The data returned from the {@link #preTransfer(CollectionElement)} call, or null if the pre-transferred element was
		 *        not a member of this collection
		 */
		void postTransfer(CollectionElement<E> element, X data);
	}

	/**
	 * <p>
	 * Searches for and fixes any inconsistencies in the collection's storage structure.
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
}

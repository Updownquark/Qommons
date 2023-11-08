package org.qommons.collect;

/**
 * A {@link BetterCollection} in which elements are stored by some property of their values
 * 
 * @param <E> The type of value stored in the collection
 * @see BetterSet
 */
public interface ValueStoredCollection<E> extends BetterCollection<E> {
	/**
	 * Retrieves the element in this collection equivalent to the given value, if present. Otherwise (if possible), the value is added, the
	 * <code>added</code> runnable is invoked (if supplied), and the new element returned. If the element does not exist and cannot be
	 * added, null is returned.
	 * 
	 * @param value The value to get or add
	 * @param after The element currently occupying the position after which (exclusive) the value's insertion is desirable, or null if the
	 *        element may be added at the beginning of the collection
	 * @param before The element currently occupying the position before which (exclusive) the value's insertion is desirable, or null if
	 *        the element may be added at the end of the collection
	 * @param first Whether (if not present) to prefer to add the value to the beginning or end of the collection
	 * @param preAdd The runnable which, if not null will be {@link Runnable#run() invoked} if the value is added to the collection in this
	 *        operation, before it is added
	 * @param postAdd The runnable which, if not null will be {@link Runnable#run() invoked} if the value is added to the collection in this
	 *        operation, after it is added
	 * @return The element containing the value, or null if the element was not present AND could not be added for any reason
	 */
	CollectionElement<E> getOrAdd(E value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd);

	/**
	 * Checks the collection's storage structure for consistency at the given element
	 * 
	 * @param element The element to check the structure's consistency at
	 * @return Whether the collection's storage appears to be consistent at the given element
	 */
	boolean isConsistent(ElementId element);

	/**
	 * <p>
	 * Searches for any inconsistencies in the entire collection's storage structure. This typically takes linear time.
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
		 * Called immediately before an element is removed from a collection. After removal, its value <b>may</b> be transferred to a new
		 * position in the collection. This call will be followed (but not necessarily immediately) by a call to either:
		 * <ul>
		 * <li>{@link #transferred(CollectionElement, Object)} with a new element with the same value, or</li>
		 * <li>{@link #disposed(Object, Object)}</li>
		 * </ul>
		 * depending on what the collection decided to do with the element after it was removed. When this method is called, the collection
		 * may not even know what the ultimate fate of the node will be.
		 * </p>
		 * <p>
		 * For some derived collections, e.g. sub-sets, it is not possible for the view to determine whether an element previously belonged
		 * to the collection. So this method may be called for elements that were not present previously, but are now.
		 * </p>
		 * 
		 * @param element The element, having just been removed, which may or may not be transferred to another position in the collection,
		 *        with a corresponding call to {@link #transferred(CollectionElement, Object)} or {@link #disposed(Object, Object)}
		 * @return A piece of data which will be given as the second argument to {@link #transferred(CollectionElement, Object)} or
		 *         {@link #disposed(Object, Object)} as a means of tracking
		 */
		X removed(CollectionElement<E> element);

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
		 * @param value The value removed due to a collision or due to the element no longer being compatible with this collection (i.e. a
		 *        sub-set)
		 * @param data The data returned from the {@link #removed(CollectionElement)} call, or null if the pre-transferred element was not a
		 *        member of this collection
		 */
		void disposed(E value, X data);

		/**
		 * Called after an element is transferred to a new position in the collection. Typically, this will be after a corresponding call to
		 * {@link #removed(CollectionElement)}, but if it can be determined that the element was previously not present in this collection,
		 * but now is as a result of the change, {@link #removed(CollectionElement)} may not be called first and <code>data</code> will be
		 * null.
		 * 
		 * @param element The element previously removed (most likely, with a corresponding call to {@link #removed(CollectionElement)}) and
		 *        now re-added in a different position within the collection
		 * @param data The data returned from the {@link #removed(CollectionElement)} call, or null if it is known that the pre-transferred
		 *        element was not a member of this collection
		 */
		void transferred(CollectionElement<E> element, X data);
	}

	/**
	 * <p>
	 * Fixes any inconsistencies in the collection's storage structure at the given element. Nothing is specified about how limited the
	 * scope of the repair will be. Depending on the nature of any inconsistency(ies) found, more than one element may need to be moved.
	 * 
	 * </p>
	 * <p>
	 * See {@link #isConsistent(ElementId)} and
	 * <a href="https://github.com/Updownquark/Qommons/wiki/BetterCollection-API#features-2">BetterSet Features</a>
	 * </p>
	 * 
	 * @param <X> The type of the data transferred for the listener
	 * @param element The element to repair the structure's consistency at
	 * @param listener The listener to monitor repairs. May be null.
	 * @return Whether any inconsistencies were found
	 */
	<X> boolean repair(ElementId element, RepairListener<E, X> listener);

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

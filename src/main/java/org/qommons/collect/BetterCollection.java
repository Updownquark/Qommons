package org.qommons.collect;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.qommons.ArrayUtils;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Lockable;
import org.qommons.Lockable.CoreId;
import org.qommons.QommonsUtils;
import org.qommons.Stamped;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * BetterCollection is an ordered {@link Collection} (also a {@link Deque}) that provides a great deal of extra capability.
 * 
 * Fundamentally, each value in a BetterCollection is stored in an element, each of which has an {@link ElementId ID}. The ID of an element
 * is like an address, which can be {@link ElementId#compareTo(ElementId) compared} to other elements. It is immutable until that element is
 * removed from the collection, so it can be used to access elements even if the properties used to store the elements may have changed,
 * e.g. the hash code of an item in a hash set or the name of an item in a sorted set ordered by name. Since it is a direct link to the
 * placeholder of the element, access or modification to the element by its ID may be significantly faster than access by value. Access and
 * update by ID are typically constant time.
 * 
 * A BetterCollection must provide access to its elements by ID and value; and iteration from a BetterCollection is by element and not value
 * only. BetterCollection itself provides more functionality on top of this and implements most of the {@link Collection} API as well.
 * 
 * See <a href="https://github.com/Updownquark/Qommons/wiki/BetterCollection-API">the wiki</a> for more detail.
 * 
 * @param <E> The type of value in the collection
 */
public interface BetterCollection<E> extends Deque<E>, TransactableCollection<E>, Stamped, Identifiable {
	/** A message for an exception thrown when a view detects that it is invalid due to external modification of the underlying data */
	public static final String BACKING_COLLECTION_CHANGED = "This collection view's backing collection has changed from underneath this view.\n"
		+ "This view is now invalid";

	/**
	 * Determines whether a value could belong to the collection. This is not the same as whether the value is {@link #canAdd(Object)
	 * addable} to the collection. This may return true for unmodifiable collections as long as the value's type or immutable properties are
	 * valid for this collection.
	 * 
	 * @param o The value to check
	 * @return Whether the given value might in any situation belong to this collection
	 */
	boolean belongs(Object o);

	/**
	 * @param value The value to get the element for
	 * @param first Whether to search for the first equivalent element or the last in this collection
	 * @return The first or last equivalent element in this collection whose value is equivalent to the given value, or null if there was no
	 *         such value in this collection
	 */
	CollectionElement<E> getElement(E value, boolean first);

	/**
	 * @param id The ID of the element to get
	 * @return The element in this collection with the given ID
	 */
	CollectionElement<E> getElement(ElementId id);

	/**
	 * @param first Whether to get the first or the last element in this collection
	 * @return The first or last element in this collection
	 */
	CollectionElement<E> getTerminalElement(boolean first);

	/**
	 * @param elementId The ID of the element to get the element adjacent to
	 * @param next Whether to get the element after (true) or before (false) the given element
	 * @return The element adjacent to the given element, or null if the given element is terminal in that direction
	 */
	CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next);

	/**
	 * @param id The ID of the element to get
	 * @return A mutable element for the given element
	 */
	MutableCollectionElement<E> mutableElement(ElementId id);

	/**
	 * @param sourceEl The source element to get derived values from
	 * @param sourceCollection The collection, potentially a source parent or ancestor of this collection, that the given source element is
	 *        from
	 * @return All element in this collection that are derived from the source element. Will be empty if:
	 *         <ul>
	 *         <li>the element belongs to a collection that is not a source for this collection,</li>
	 *         <li>or the given element belongs to a source of this collection but is not a source of any element in this collection</li>
	 *         </ul>
	 */
	BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection);

	/**
	 * @param localElement The element in this collection to get the source(s) for
	 * @param sourceCollection The collection, potentially a source parent or ancestor of this collection, for which to get the elements
	 * @return All elements of the source collection that affect the value of the given element in this collection
	 */
	BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection);

	/**
	 * Retrieves the ID of the element in this collection that is equivalent to some element from a different collection derived from the
	 * same data. The element will only be recognized if the given element's collection is derived from the source data in a very similar
	 * way to this collection.
	 * 
	 * @param equivalentEl An element from a different collection
	 * @return The element in this collection equivalent to the given element, or null if:
	 *         <ul>
	 *         <li>the element is from a collection not derived from the same data as this one,</li>
	 *         <li>the element, though from a related collection, is not represented in this collection</li>
	 *         </ul>
	 */
	ElementId getEquivalentElement(ElementId equivalentEl);

	/**
	 * Tests the ability to add an object into this collection within a given position range
	 * 
	 * @param value The value to test addability for
	 * @param after The element currently occupying the position after which (exclusive) the value's insertion is desirable, or null if the
	 *        element may be added at the beginning of the collection
	 * @param before The element currently occupying the position before which (exclusive) the value's insertion is desirable, or null if
	 *        the element may be added at the end of the collection
	 * @return Null if given value could possibly be added to this collection within the given position range, or a message why it can't
	 */
	String canAdd(E value, ElementId after, ElementId before);

	/**
	 * Adds an element to this collection within a specified position range
	 * 
	 * @param value The value to add
	 * @param after The element currently occupying the position after which (exclusive) the value's insertion is desirable, or null if the
	 *        element may be added at the beginning of the collection
	 * @param before The element currently occupying the position before which (exclusive) the value's insertion is desirable, or null if
	 *        the element may be added at the end of the collection
	 * @param first Whether to prefer a lower position over a higher one. This parameter may be:
	 *        <ul>
	 *        <li>Strictly obeyed, in which the value will be added at the beginning (true) or end (false) of the collection</li>
	 *        <li>Ignored if the collection does not support position-indicated addition</li>
	 *        <li>Used as a suggestion, where the insertion will be closer to the beginning (true) or end (false) of the collection as
	 *        indicated by the parameter</li></li>
	 * @return The element at which the value was added, or null if the value was not added due to a non-erroring condition
	 * @throws UnsupportedOperationException If such an operation is not supported by this collection in general
	 * @throws IllegalArgumentException If something about the value prevents this operation
	 */
	CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException;

	/**
	 * @param valueEl The element to move
	 * @param after The lower bound of the target range to move the element to (may be null to leave the range low-unbounded)
	 * @param before The upper bound of the target range to move the element to (may be null to leave the range high-unbounded)
	 * @return null if the element can be moved into the given range (always true if the element is already in that range), or a message
	 *         describing why it can't be moved there
	 */
	String canMove(ElementId valueEl, ElementId after, ElementId before);

	/**
	 * @param valueEl The element to move
	 * @param after The lower bound of the target range to move the element to (may be null to leave the range low-unbounded)
	 * @param before The upper bound of the target range to move the element to (may be null to leave the range high-unbounded)
	 * @param first Whether to attempt to move the element toward the low (true) or the high (false) end of the range
	 * @param afterRemove A callback that will be invoked after the element is removed from its current position and before it has been
	 *        re-added to its new position (may be null)
	 * @return The element in its new position (elements cannot be re-used, so this will be a new element)
	 * @throws UnsupportedOperationException If the given move is not supported for a general reason
	 * @throws IllegalArgumentException If the given move in particular is not supported
	 */
	CollectionElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
		throws UnsupportedOperationException, IllegalArgumentException;

	/** Removes all {@link MutableCollectionElement#canRemove() removable} values from this collection */
	@Override
	void clear();

	/**
	 * Tests the compatibility of an object with this collection.
	 *
	 * @param value The value to test compatibility for
	 * @return Null if given value could possibly be added to this collection, or a message why it can't
	 */
	default String canAdd(E value) {
		return canAdd(value, null, null);
	}

	/**
	 * Similar to {@link #add(Object)}, but returns the element ID for the new element at which the value was added. Similarly to
	 * {@link #add(Object)}, this method may return null if the collection was not changed as a result of the operation.
	 * 
	 * @param value The value to add
	 * @param first Whether to prefer a lower position over a higher one. This parameter may be:
	 *        <ul>
	 *        <li>Strictly obeyed, in which the value will be added at the beginning (true) or end (false) of the collection</li>
	 *        <li>Ignored if the collection does not support position-indicated addition</li>
	 *        <li>Used as a suggestion, where the insertion will be closer to the beginning (true) or end (false) of the collection as
	 *        indicated by the parameter</li></li>
	 * @return The element at which the value was added, or null if the value was not added due to a non-erroring condition
	 * @throws UnsupportedOperationException If such an operation is not supported by this collection in general
	 * @throws IllegalArgumentException If something about the value prevents this operation
	 */
	default CollectionElement<E> addElement(E value, boolean first) throws UnsupportedOperationException, IllegalArgumentException {
		return addElement(value, null, null, first);
	}

	@Override
	default boolean add(E value) {
		return addElement(value, null, null, false) != null;
	}

	@Override
	default boolean addAll(Collection<? extends E> c) {
		if (c.isEmpty())
			return false;
		Causable cause = Causable.simpleCause();
		try (Transaction cst = cause.use();
			Transaction t = lock(true, cause);
			Transaction ct = Transactable.lock(c, false, cause)) {
			boolean changed = false;
			for (E e : c) {
				if (canAdd(e) == null)
					changed |= add(e);
			}
			return changed;
		}
	}

	/**
	 * @param values The values to add to the collection
	 * @return This collection
	 */
	default BetterCollection<E> with(E... values) {
		Causable cause = Causable.simpleCause();
		try (Transaction cst = cause.use(); Transaction t = lock(true, cause)) {
			for (E e : values) {
				if (canAdd(e) == null)
					add(e);
			}
		}
		return this;
	}

	/**
	 * @param values The values to add to the collection
	 * @return This collection
	 */
	default BetterCollection<E> withAll(Collection<? extends E> values) {
		Causable cause = Causable.simpleCause();
		try (Transaction cst = cause.use(); Transaction t = lock(true, cause)) {
			for (E e : values) {
				if (canAdd(e) == null)
					add(e);
			}
		}
		return this;
	}

	/**
	 * Tests the removability of an element from this collection. This method exposes a "best guess" on whether an element in the collection
	 * could be removed, but does not provide any guarantee. This method should return null for any object for which {@link #remove(Object)}
	 * is successful, but the fact that an object passes this test does not guarantee that it would be removed successfully. E.g. the
	 * position of the element in the collection may be a factor, but may not be tested for here.
	 *
	 * @param value The value to test removability for
	 * @return Null if given value could possibly be removed from this collection, or a message why it can't
	 */
	default String canRemove(Object value) {
		if (!belongs(value))
			return StdMsg.NOT_FOUND;
		try (Transaction t = lock(false, null)) {
			CollectionElement<E> found = getElement((E) value, true);
			return mutableElement(found.getElementId()).canRemove();
		}
	}

	@Override
	default boolean contains(Object o) {
		if (!belongs(o))
			return false;
		CollectionElement<E> el = getElement((E) o, true);
		return el != null;
	}

	/**
	 * @param c The collection to test
	 * @return Whether this collection contains any of the given collection's elements
	 */
	default boolean containsAny(Collection<?> c) {
		try (Transaction t = lock(false, null); Transaction ct = Transactable.lock(c, false, null)) {
			if (c.isEmpty())
				return true;
			if (c.size() < size()) {
				for (Object o : c)
					if (contains(o))
						return true;
				return false;
			} else {
				if (c.isEmpty())
					return false;
				Set<Object> cSet = new HashSet<>(c);
				CollectionElement<E> el = getTerminalElement(true);
				while (el != null) {
					if (cSet.contains(el.get()))
						return true;
					el = getAdjacentElement(el.getElementId(), true);
				}
				return false;
			}
		}
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		try (Transaction t = lock(false, null); Transaction ct = Transactable.lock(c, false, null)) {
			if (c.isEmpty())
				return true;
			if (c.size() < size()) {
				for (Object o : c)
					if (!contains(o))
						return false;
				return true;
			} else {
				Set<Object> cSet = new HashSet<>(c);
				cSet.removeAll(this);
				return cSet.isEmpty();
			}
		}
	}

	@Override
	default Object[] toArray() {
		try (Transaction t = lock(false, null)) {
			Object[] array = new Object[size()];
			CollectionElement<E> el = getTerminalElement(true);
			int index = 0;
			while (el != null) {
				array[index++] = el.get();
				el = getAdjacentElement(el.getElementId(), true);
			}
			return array;
		}
	}

	@Override
	default <T> T[] toArray(T[] a) {
		try (Transaction t = lock(false, null)) {
			int size = size();
			if (a.length < size)
				a = (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
			T[] array = a;
			CollectionElement<E> el = getTerminalElement(true);
			int index = 0;
			while (el != null) {
				array[index++] = (T) el.get();
				el = getAdjacentElement(el.getElementId(), true);
			}
			return a;
		}
	}

	@Override
	default boolean remove(Object o) {
		if (!belongs(o))
			return false;
		try (Transaction t = lock(true, null)) {
			CollectionElement<E> found = getElement((E) o, true);
			if (found == null)
				return false;
			mutableElement(found.getElementId()).remove();
			return true;
		}
	}

	/**
	 * Removes the last element matching the given value from this collection
	 * 
	 * @param o The value to remove
	 * @return Whether the value was found and removed
	 */
	default boolean removeLast(Object o) {
		if (!belongs(o))
			return false;
		try (Transaction t = lock(true, null)) {
			CollectionElement<E> found = getElement((E) o, false);
			if (found == null)
				return false;
			mutableElement(found.getElementId()).remove();
			return true;
		}
	}

	/**
	 * <p>
	 * Removes all values in this collection that are equivalent to any values in the given collection.
	 * </p>
	 * <p>
	 * Importantly, if the notion of element "equivalence" differs between the two collections, the equivalence of the parameter collection
	 * will be used. While it would perhaps be more intuitive to use this collection's equivalence, this decision is to align with the
	 * functionality of {@link #retainAll(Collection)}, for which using this collection's equivalence would be prohibitively expensive.
	 * </p>
	 * 
	 * @param c The collection whose values to remove from this collection
	 * @return Whether any values were removed from this collection
	 */
	@Override
	default boolean removeAll(Collection<?> c) {
		if (c.isEmpty())
			return false;
		return removeIf(//
			LambdaUtils.printablePred(c::contains, () -> "in" + c, null));
	}

	/**
	 * <p>
	 * Removes all values in this collection that are NOT equivalent to any values in the given collection.
	 * </p>
	 * <p>
	 * Importantly, if the notion of element "equivalence" differs between the two collections, the equivalence of the parameter collection
	 * will be used. While it would perhaps be more intuitive to use this collection's equivalence, to do so would require the creation of a
	 * temporary set populated with the given collection's values, which would be prohibitively expensive.
	 * </p>
	 * 
	 * @param c The collection whose values to keep in this collection
	 * @return Whether any values were removed from this collection
	 */
	@Override
	default boolean retainAll(Collection<?> c) {
		if (isEmpty())
			return false;
		if (c.isEmpty()) {
			try (Transaction t = lock(true, null)) {
				int preSize = size();
				clear();
				return size() < preSize;
			}
		}
		return removeIf(//
			LambdaUtils.printablePred(o -> !c.contains(o), () -> "notIn" + c, null));
	}

	@Override
	default boolean removeIf(Predicate<? super E> filter) {
		if (isEmpty())
			return false;
		boolean removed = false;
		try (Transaction t = lock(true, null)) {
			for (CollectionElement<E> el : elements()) {
				if (filter.test(el.get())) {
					MutableCollectionElement<E> mutableEl = mutableElement(el.getElementId());
					if (mutableEl.canRemove() == null) {
						mutableEl.remove();
						removed = true;
					}
				}
			}
		}
		return removed;
	}

	/**
	 * Optionally replaces each value in this collection with a mapped value. For every element, the map will be applied. If the result is
	 * identically (==) different from the existing value, that element will be replaced with the mapped value.
	 *
	 * @param map The map to apply to each value in this collection
	 * @param soft If true, this method will attempt to determine whether each differing mapped value is acceptable as a replacement. This
	 *        may, but is not guaranteed to, prevent {@link IllegalArgumentException}s
	 * @return Whether any elements were replaced
	 * @throws UnsupportedOperationException If an update operation is not supported
	 * @throws IllegalArgumentException If a mapped value is not acceptable as a replacement
	 */
	default boolean replaceAll(Function<? super E, ? extends E> map, boolean soft) {
		try (Transaction t = lock(true, null)) {
			boolean replaced = false;
			for (CollectionElement<E> el : elements()) {
				E newValue = map.apply(el.get());
				if (newValue != el.get()) {
					MutableCollectionElement<E> mutableEl = mutableElement(el.getElementId());
					if (!soft || mutableEl.isAcceptable(newValue) == null) {
						mutableEl.set(newValue);
						replaced = true;
					}
				}
			}
			return replaced;
		}
	}

	/**
	 * Replaces each value in this collection with a mapped value. For every element, the operation will be applied. If the result is
	 * identically (==) different from the existing value, that element will be replaced with the mapped value.
	 *
	 * @param op The operation to apply to each value in this collection
	 */
	default void replaceAll(UnaryOperator<E> op) {
		replaceAll(LambdaUtils.printableFn(v -> op.apply(v), op::toString), false);
	}

	/**
	 * Finds an element in this collection matching the given search
	 * 
	 * @param search The search function
	 * @param first Whether to search for the first matching element or the last one
	 * @return The element of the matching result
	 */
	default CollectionElement<E> find(Predicate<? super E> search, boolean first) {
		try (Transaction t = lock(false, null)) {
			CollectionElement<E> el = getTerminalElement(first);
			while (el != null) {
				if (search.test(el.get()))
					return el;
				el = getAdjacentElement(el.getElementId(), first);
			}
			return null;
		}
	}

	/**
	 * Finds all elements in this collection matching the given search and performs an action on each
	 * 
	 * @param search The search function
	 * @param onElement The action to perform on the search's results
	 * @param forward Whether to search beginning-to-end or end-to-beginning
	 * @return The number of results found
	 */
	default int findAll(Predicate<? super E> search, Consumer<? super CollectionElement<E>> onElement, boolean forward) {
		int found = 0;
		CollectionElement<E> el = getTerminalElement(forward);
		while (el != null) {
			if (search.test(el.get())) {
				found++;
				if (onElement != null)
					onElement.accept(el);
			}
			el = getAdjacentElement(el.getElementId(), forward);
		}
		return found;
	}

	@Override
	default Iterator<E> iterator() {
		return new BetterCollectionIterator<>(this);
	}

	/** @return A collection of this collection's elements */
	default BetterCollection<CollectionElement<E>> elements() {
		return new ElementCollection<>(this);
	}

	/** @return A collection with the same content as this one, but whose order is reversed */
	default BetterCollection<E> reverse() {
		return new ReversedCollection<>(this);
	}

	// Deque methods

	@Override
	default void addFirst(E e) {
		if (addElement(e, true) == null)
			throw new IllegalStateException("Could not add element");
	}

	@Override
	default void addLast(E e) {
		if (addElement(e, false) == null)
			throw new IllegalStateException("Could not add element");
	}

	@Override
	default boolean offerFirst(E e) {
		return addElement(e, true) != null;
	}

	@Override
	default boolean offerLast(E e) {
		return addElement(e, false) != null;
	}

	@Override
	default E removeFirst() {
		try (Transaction t = lock(true, null)) {
			CollectionElement<E> el = getTerminalElement(true);
			if (el == null)
				throw new NoSuchElementException("Empty collection");
			E value = el.get();
			mutableElement(el.getElementId()).remove();
			return value;
		}
	}

	@Override
	default E removeLast() {
		try (Transaction t = lock(true, null)) {
			CollectionElement<E> el = getTerminalElement(false);
			if (el == null)
				throw new NoSuchElementException("Empty collection");
			E value = el.get();
			mutableElement(el.getElementId()).remove();
			return value;
		}
	}

	@Override
	default E pollFirst() {
		try (Transaction t = lock(true, null)) {
			CollectionElement<E> el = getTerminalElement(true);
			if (el == null)
				return null;
			E value = el.get();
			mutableElement(el.getElementId()).remove();
			return value;
		}
	}

	@Override
	default E pollLast() {
		try (Transaction t = lock(true, null)) {
			CollectionElement<E> el = getTerminalElement(false);
			if (el == null)
				return null;
			E value = el.get();
			mutableElement(el.getElementId()).remove();
			return value;
		}
	}

	@Override
	default E getFirst() {
		CollectionElement<E> el = getTerminalElement(true);
		if (el == null)
			throw new NoSuchElementException("Empty collection");
		return el.get();
	}

	@Override
	default E getLast() {
		CollectionElement<E> el = getTerminalElement(false);
		if (el == null)
			throw new NoSuchElementException("Empty collection");
		return el.get();
	}

	@Override
	default E peekFirst() {
		CollectionElement<E> el = getTerminalElement(true);
		if (el == null)
			return null;
		return el.get();
	}

	@Override
	default E peekLast() {
		CollectionElement<E> el = getTerminalElement(false);
		if (el == null)
			return null;
		return el.get();
	}

	@Override
	default boolean removeFirstOccurrence(Object o) {
		return remove(o);
	}

	@Override
	default boolean removeLastOccurrence(Object o) {
		return removeLast(o);
	}

	@Override
	default boolean offer(E e) {
		return add(e);
	}

	@Override
	default E remove() {
		return removeFirst();
	}

	@Override
	default E poll() {
		return pollFirst();
	}

	@Override
	default E element() {
		return getFirst();
	}

	@Override
	default E peek() {
		return peekFirst();
	}

	@Override
	default void push(E e) {
		addFirst(e);
	}

	@Override
	default E pop() {
		return removeFirst();
	}

	@Override
	default Iterator<E> descendingIterator() {
		return reverse().iterator();
	}

	/**
	 * A typical {@link Object#hashCode()} implementation for collections
	 *
	 * @param coll The collection to hash
	 * @return The hash code of the collection's contents
	 */
	static int hashCode(BetterCollection<?> coll) {
		try (Transaction t = coll.lock(false, null)) {
			int hashCode = 1;
			for (Object e : coll)
				hashCode = 31 * hashCode + (e == null ? 0 : e.hashCode());
			return hashCode;
		}
	}

	/**
	 * A typical {@link Object#equals(Object)} implementation for collections
	 * 
	 * @param coll The collection to test
	 * @param o The object to test the collection against
	 * @return Whether the two objects are equal
	 */
	static boolean equals(BetterCollection<?> coll, Object o) {
		if (!(o instanceof Collection))
			return false;
		Collection<?> c = (Collection<?>) o;

		try (Transaction t = Lockable.lockAll(Lockable.lockable(coll, false, false), //
			c instanceof Transactable ? Lockable.lockable((Transactable) c, false, false) : null)) {
			Iterator<?> e1 = coll.iterator();
			Iterator<?> e2 = c.iterator();
			while (e1.hasNext() && e2.hasNext()) {
				Object o1 = e1.next();
				Object o2 = e2.next();
				if (!Objects.equals(o1, o2))
					return false;
			}
			return !(e1.hasNext() || e2.hasNext());
		}
	}

	/**
	 * A simple {@link Object#toString()} implementation for collections
	 *
	 * @param coll The collection to print
	 * @return The string representation of the collection's contents
	 */
	static String toString(BetterCollection<?> coll) {
		StringBuilder ret = new StringBuilder("[");
		boolean first = true;
		try (Transaction t = coll.lock(false, null)) {
			for (Object value : coll) {
				if (!first) {
					ret.append(", ");
				} else
					first = false;
				ret.append(value);
			}
		}
		ret.append(']');
		return ret.toString();
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
				hash = hash * 13 + (v == null ? 0 : v.hashCode());
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
	public static boolean equals(Collection<?> c, Object o) {
		if (o == null)
			return false;
		try (Transaction t = Lockable.lockAll(//
			c instanceof Transactable ? Lockable.lockable((Transactable) c, false, null) : null, //
			o instanceof Transactable ? Lockable.lockable((Transactable) o, false, null) : null)) {
			Collection<?> c2 = (Collection<?>) o;
			if (c.size() != c2.size())
				return false;
			Iterator<?> iter = c.iterator();
			Iterator<?> cIter = c2.iterator();
			while (iter.hasNext()) {
				if (!cIter.hasNext())
					return false;
				if (!Objects.equals(iter.next(), cIter.next()))
					return false;
			}
			if (cIter.hasNext())
				return false;
			return true;
		}
	}

	/**
	 * A static utility method to be used by {@link BetterCollection#toString()} implementations
	 * 
	 * @param c The collection
	 * @return A string represenation of the collection's content
	 */
	public static String toString(Collection<?> c) {
		try (Transaction t = Transactable.lock(c, false, null)) {
			StringBuilder str = new StringBuilder();
			str.append('[');
			boolean first = true;
			for (Object v : c) {
				if (!first)
					str.append(", ");
				first = false;
				str.append(v);
			}
			str.append(']');
			return str.toString();
		}
	}

	/**
	 * @param <E> The type of the collection
	 * @return An empty {@link BetterCollection}
	 */
	public static <E> BetterCollection<E> empty() {
		return (BetterCollection<E>) EMPTY;
	}

	/** Singleton empty better collection */
	static final BetterCollection<Object> EMPTY = new EmptyCollection<>();

	/**
	 * An {@link Iterator} based on a {@link BetterCollection}'s elements
	 * 
	 * @param <E> The type of values to iterate over
	 */
	class BetterCollectionIterator<E> implements Iterator<E> {
		private final BetterCollection<E> theCollection;
		private ElementId previous;
		private CollectionElement<E> next;
		private ElementId theLastReturnedElement;

		public BetterCollectionIterator(BetterCollection<E> collection) {
			theCollection = collection;
		}

		@Override
		public boolean hasNext() {
			if (next != null && next.getElementId().isPresent())
				return true;
			else if (theLastReturnedElement != null && theLastReturnedElement.isPresent()) {
				previous = theLastReturnedElement;
				next = theCollection.getAdjacentElement(theLastReturnedElement, true);
			} else if (previous != null) {
				CollectionElement<E> oldNext = next;
				next = theCollection.getAdjacentElement(previous, true);
				if (oldNext != null && oldNext.getElementId().isPresent())
					previous = oldNext.getElementId();
			} else
				next = theCollection.getTerminalElement(true);
			return next != null;
		}

		@Override
		public E next() {
			if (!hasNext())
				throw new NoSuchElementException();
			if (theLastReturnedElement != null && theLastReturnedElement.isPresent())
				previous = theLastReturnedElement;
			theLastReturnedElement = next.getElementId();
			if (!theLastReturnedElement.isPresent())
				throw new ConcurrentModificationException(BACKING_COLLECTION_CHANGED);
			E value = next.get();
			next = theCollection.getAdjacentElement(theLastReturnedElement, true);
			return value;
		}

		@Override
		public void remove() {
			if (theLastReturnedElement == null)
				throw new IllegalStateException("Iterator is not started or there were no elements");
			else if (!theLastReturnedElement.isPresent())
				throw new IllegalStateException("Element has already been removed");
			theCollection.mutableElement(theLastReturnedElement).remove();
		}
	}

	/**
	 * Implements {@link #reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ReversedCollection<E> implements BetterCollection<E> {
		private final BetterCollection<E> theWrapped;
		private Object theIdentity;

		protected ReversedCollection(BetterCollection<E> wrap) {
			theWrapped = wrap;
		}

		protected BetterCollection<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theWrapped.getThreadConstraint();
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.wrap(theWrapped.getIdentity(), "reverse");
			return theIdentity;
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theWrapped.tryLock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return theWrapped.getCoreId();
		}

		@Override
		public long getStamp() {
			return theWrapped.getStamp();
		}

		@Override
		public boolean belongs(Object o) {
			return getWrapped().belongs(o);
		}

		@Override
		public int size() {
			return getWrapped().size();
		}

		@Override
		public boolean isEmpty() {
			return getWrapped().isEmpty();
		}

		@Override
		public Object[] toArray() {
			Object[] ret = getWrapped().toArray();
			ArrayUtils.reverse(ret);
			return ret;
		}

		@Override
		public <T> T[] toArray(T[] a) {
			T[] ret = getWrapped().toArray(a);
			ArrayUtils.reverse(ret);
			return ret;
		}

		@Override
		public CollectionElement<E> getTerminalElement(boolean first) {
			return CollectionElement.reverse(theWrapped.getTerminalElement(!first));
		}

		@Override
		public CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next) {
			return CollectionElement.reverse(theWrapped.getAdjacentElement(elementId.reverse(), !next));
		}

		@Override
		public CollectionElement<E> getElement(E value, boolean first) {
			return CollectionElement.reverse(getWrapped().getElement(value, !first));
		}

		@Override
		public CollectionElement<E> getElement(ElementId id) {
			return getWrapped().getElement(id.reverse()).reverse();
		}

		@Override
		public MutableCollectionElement<E> mutableElement(ElementId id) {
			return getWrapped().mutableElement(id.reverse()).reverse();
		}

		@Override
		public BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			return QommonsUtils.map2(theWrapped.getElementsBySource(sourceEl, sourceCollection), el -> el.reverse());
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return QommonsUtils.map2(theWrapped.getSourceElements(localElement.reverse(), theWrapped), el -> el.reverse());
			return theWrapped.getSourceElements(localElement.reverse(), sourceCollection);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			return ElementId.reverse(theWrapped.getEquivalentElement(equivalentEl.reverse()));
		}

		@Override
		public String canAdd(E value, ElementId after, ElementId before) {
			return getWrapped().canAdd(value, ElementId.reverse(before), ElementId.reverse(after));
		}

		@Override
		public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return CollectionElement
				.reverse(getWrapped().addElement(value, ElementId.reverse(before), ElementId.reverse(after), !first));
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			return getWrapped()//
				.canMove(//
					valueEl.reverse(), ElementId.reverse(before), ElementId.reverse(after));
		}

		@Override
		public CollectionElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove) {
			return getWrapped()//
				.move(//
					valueEl.reverse(), ElementId.reverse(before), ElementId.reverse(after), !first, afterRemove)
				.reverse();
		}

		@Override
		public BetterCollection<E> reverse() {
			if (BetterCollections.simplifyDuplicateOperations())
				return getWrapped();
			else
				return BetterCollection.super.reverse();
		}

		@Override
		public void clear() {
			getWrapped().clear();
		}

		@Override
		public int hashCode() {
			return BetterCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object o) {
			return BetterCollection.equals(this, o);
		}

		@Override
		public String toString() {
			return BetterCollection.toString(this);
		}
	}

	/**
	 * An immutable, empty {@link BetterCollection}
	 * 
	 * @param <E> The type of the collection
	 */
	class EmptyCollection<E> implements BetterCollection<E> {
		private Object theIdentity;

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.idFor(this, this::toString, this::hashCode, other -> other instanceof EmptyCollection);
			return theIdentity;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return ThreadConstraint.NONE;
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public CoreId getCoreId() {
			return CoreId.EMPTY;
		}

		@Override
		public long getStamp() {
			return 0;
		}

		@Override
		public boolean belongs(Object o) {
			return false;
		}

		@Override
		public int size() {
			return 0;
		}

		@Override
		public boolean isEmpty() {
			return true;
		}

		@Override
		public Object[] toArray() {
			return new Object[0];
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return a;
		}

		@Override
		public String canAdd(E value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return null;
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			throw new NoSuchElementException();
		}

		@Override
		public CollectionElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove) {
			throw new NoSuchElementException();
		}

		@Override
		public void clear() {}

		@Override
		public CollectionElement<E> getTerminalElement(boolean first) {
			return null;
		}

		@Override
		public CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next) {
			return null;
		}

		@Override
		public CollectionElement<E> getElement(E value, boolean first) {
			return null;
		}

		@Override
		public CollectionElement<E> getElement(ElementId id) {
			throw new IllegalArgumentException(StdMsg.NOT_FOUND);
		}

		@Override
		public MutableCollectionElement<E> mutableElement(ElementId id) {
			throw new IllegalArgumentException(StdMsg.NOT_FOUND);
		}

		@Override
		public BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			return BetterList.empty();
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			throw new IllegalArgumentException(StdMsg.NOT_FOUND);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			return null;
		}

		@Override
		public int hashCode() {
			return 0;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Collection && ((Collection<?>) o).isEmpty();
		}

		@Override
		public String toString() {
			return "[]";
		}
	}

	/**
	 * Implements {@link BetterCollection#element()}
	 * 
	 * @param <E> The type of the collection
	 */
	class ElementCollection<E> extends AbstractIdentifiable implements BetterCollection<CollectionElement<E>> {
		private final BetterCollection<E> theCollection;

		public ElementCollection(BetterCollection<E> collection) {
			theCollection = collection;
		}

		/** @return The collection that this element collection is backed by */
		protected BetterCollection<E> getCollection() {
			return theCollection;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theCollection.getThreadConstraint();
		}

		@Override
		public int size() {
			return theCollection.size();
		}

		@Override
		public long getStamp() {
			return theCollection.getStamp();
		}

		@Override
		public boolean isLockSupported() {
			return theCollection.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theCollection.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theCollection.tryLock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return theCollection.getCoreId();
		}

		@Override
		public boolean isEmpty() {
			return theCollection.isEmpty();
		}

		@Override
		public Object createIdentity() {
			return Identifiable.wrap(theCollection.getIdentity(), "elements");
		}

		@Override
		public boolean belongs(Object o) {
			return o instanceof CollectionElement//
				&& !theCollection.getSourceElements(((CollectionElement<?>) o).getElementId(), theCollection).isEmpty();
		}

		@Override
		public CollectionElement<CollectionElement<E>> getElement(CollectionElement<E> value, boolean first) {
			if (theCollection.getSourceElements(value.getElementId(), theCollection).isEmpty())
				return null;
			return wrap(value);
		}

		@Override
		public CollectionElement<CollectionElement<E>> getElement(ElementId id) {
			return wrap(theCollection.getElement(id));
		}

		@Override
		public CollectionElement<CollectionElement<E>> getTerminalElement(boolean first) {
			return wrap(theCollection.getTerminalElement(first));
		}

		@Override
		public CollectionElement<CollectionElement<E>> getAdjacentElement(ElementId elementId, boolean next) {
			return wrap(theCollection.getAdjacentElement(elementId, next));
		}

		@Override
		public MutableCollectionElement<CollectionElement<E>> mutableElement(ElementId id) {
			return wrapMutable(theCollection.mutableElement(id));
		}

		@Override
		public BetterList<CollectionElement<CollectionElement<E>>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			return QommonsUtils.map2(theCollection.getElementsBySource(sourceEl, sourceCollection), this::wrap);
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			return theCollection.getSourceElements(localElement, sourceCollection);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			return theCollection.getEquivalentElement(equivalentEl);
		}

		@Override
		public String canAdd(CollectionElement<E> value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<CollectionElement<E>> addElement(CollectionElement<E> value, ElementId after, ElementId before,
			boolean first) throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			return theCollection.canMove(valueEl, after, before);
		}

		@Override
		public CollectionElement<CollectionElement<E>> move(ElementId valueEl, ElementId after, ElementId before, boolean first,
			Runnable afterRemove) {
			return wrap(theCollection.move(valueEl, after, before, first, afterRemove));
		}

		@Override
		public void clear() {
			theCollection.clear();
		}

		protected CollectionElement<CollectionElement<E>> wrap(CollectionElement<E> el) {
			return el == null ? null : new WrappedCollectionElement(el);
		}

		protected MutableCollectionElement<CollectionElement<E>> wrapMutable(CollectionElement<E> el) {
			return new MutableWrappedCollectionElement(el);
		}

		class WrappedCollectionElement implements CollectionElement<CollectionElement<E>> {
			final CollectionElement<E> theElement;

			WrappedCollectionElement(CollectionElement<E> element) {
				theElement = element;
			}

			@Override
			public ElementId getElementId() {
				return theElement.getElementId();
			}

			@Override
			public CollectionElement<E> get() {
				return theElement;
			}
		}

		class MutableWrappedCollectionElement extends WrappedCollectionElement implements MutableCollectionElement<CollectionElement<E>> {
			MutableWrappedCollectionElement(CollectionElement<E> element) {
				super(element);
			}

			@Override
			public BetterCollection<CollectionElement<E>> getCollection() {
				return ElementCollection.this;
			}

			@Override
			public String isEnabled() {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public String isAcceptable(CollectionElement<E> value) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public void set(CollectionElement<E> value) throws UnsupportedOperationException, IllegalArgumentException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public String canRemove() {
				return theCollection.mutableElement(theElement.getElementId()).canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theCollection.mutableElement(theElement.getElementId()).remove();
			}
		}
	}
}

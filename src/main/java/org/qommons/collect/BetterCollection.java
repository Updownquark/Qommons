package org.qommons.collect;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.qommons.ArrayUtils;
import org.qommons.Causable;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
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
public interface BetterCollection<E> extends Deque<E>, TransactableCollection<E> {
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
	 * <p>
	 * Obtains a stamp with the current status of modifications to the collection, either structural or all changes. Whenever this
	 * collection is modified, the stamp changes. Thus 2 stamps can be compared to determine whether a collection has changed in between 2
	 * calls to this method. For more information on <b>structural</b> changes, see {@link #lock(boolean, boolean, Object)}.
	 * </p>
	 * <p>
	 * The value returned from this method is <b>ONLY</b> for comparison. The value itself is not guaranteed to reveal anything about this
	 * collection or its history, e.g. the actual times it has been modified. Also, if 2 stamps obtained from this method are different,
	 * this does not guarantee that the collection was actually changed in any way, only that it might have been. It <b>IS</b> guaranteed
	 * that if 2 stamps match, then no modification (of the corresponding type) has been made to the collection, and an effort shall be made
	 * to avoid changing the stamps when no modification is performed, if possible.
	 * </p>
	 * <p>
	 * No relationship is specified between stamps obtained with different parameters (structural/update).
	 * </p>
	 * 
	 * @param structuralOnly Whether to monitor only structural changes or all changes.
	 * @return The stamp for comparison
	 */
	long getStamp(boolean structuralOnly);

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

	/** Removes all {@link MutableCollectionElement#canRemove() removable} values from this collection */
	@Override
	void clear();

	/**
	 * @param fromStart Whether the returned spliterator should be initially positioned at the beginning of this collection or its end
	 * @return The spliterator
	 */
	MutableElementSpliterator<E> spliterator(boolean fromStart);

	/**
	 * @param element The ID of the element at which to position the spliterator initially
	 * @param asNext Whether the given element should be the first element returned from the spliterator's
	 *        {@link MutableElementSpliterator#forElement(Consumer, boolean) forElement} method with a true or a false parameter
	 * @return The spliterator
	 */
	MutableElementSpliterator<E> spliterator(ElementId element, boolean asNext);

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
		Causable cause = Causable.simpleCause(null);
		try (Transaction cst = Causable.use(cause);
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
		Causable cause = Causable.simpleCause(null);
		try (Transaction cst = Causable.use(cause); Transaction t = lock(true, cause)) {
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
		Causable cause = Causable.simpleCause(null);
		try (Transaction cst = Causable.use(cause); Transaction t = lock(true, cause)) {
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
			return MutableCollectionElement.StdMsg.NOT_FOUND;
		String[] msg = new String[1];
		if (!forMutableElement((E) value, el -> msg[0] = el.canRemove(), true))
			return MutableCollectionElement.StdMsg.NOT_FOUND;
		return msg[0];
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
		try (Transaction t = lock(false, false, null); Transaction ct = Transactable.lock(c, false, null)) {
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
				Spliterator<E> iter = spliterator();
				boolean[] found = new boolean[1];
				while (iter.tryAdvance(next -> {
					found[0] = cSet.contains(next);
				}) && !found[0]) {
				}
				return found[0];
			}
		}
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		try (Transaction t = lock(false, false, null); Transaction ct = Transactable.lock(c, false, null)) {
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
		try (Transaction t = lock(false, true, null)) {
			Object[] array = new Object[size()];
			int[] index = new int[1];
			spliterator().forEachRemaining(v -> array[index[0]++] = v);
			return array;
		}
	}

	@Override
	default <T> T[] toArray(T[] a) {
		try (Transaction t = lock(false, true, null)) {
			int size = size();
			if (a.length < size)
				a = (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
			int[] index = new int[1];
			T[] array = a;
			spliterator().forEachRemaining(v -> array[index[0]++] = (T) v);
			return a;
		}
	}

	@Override
	default boolean remove(Object o) {
		if (!belongs(o))
			return false;
		return forMutableElement((E) o, //
			el -> el.remove(), true);
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
		return forMutableElement((E) o, el -> el.remove(), false);
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		if (c.isEmpty())
			return false;
		return removeIf(//
			c::contains);
	}

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
			o -> !c.contains(o));
	}

	@Override
	default boolean removeIf(Predicate<? super E> filter) {
		if (isEmpty())
			return false;
		boolean[] removed = new boolean[1];
		findAll(filter, //
			el -> {
				if (el.canRemove() == null) {
					el.remove();
					removed[0] = true;
				}
			}, true);
		return removed[0];
	}

	/**
	 * Optionally replaces each value in this collection with a mapped value. For every element, the map will be applied. If the result is
	 * identically (==) different from the existing value, that element will be replaced with the mapped value.
	 *
	 * @param map The map to apply to each value in this collection
	 * @param soft If true, this method will attempt to determine whether each differing mapped value is acceptable as a replacement. This
	 *        may, but is not guaranteed to, prevent {@link IllegalArgumentException}s
	 * @return Whether any elements were replaced
	 * @throws IllegalArgumentException If a mapped value is not acceptable as a replacement
	 */
	default boolean replaceAll(Function<? super E, ? extends E> map, boolean soft) {
		try (Transaction t = lock(true, false, null)) {
			boolean[] replaced = new boolean[1];
			MutableElementSpliterator<E> iter = spliterator();
			iter.forEachElementM(el -> {
				E value = el.get();
				E newValue = map.apply(value);
				if (value != newValue && (!soft || el.isAcceptable(newValue) == null)) {
					el.set(newValue);
					replaced[0] = true;
				}
			}, true);
			return replaced[0];
		}
	}

	/**
	 * Replaces each value in this collection with a mapped value. For every element, the operation will be applied. If the result is
	 * identically (==) different from the existing value, that element will be replaced with the mapped value.
	 *
	 * @param op The operation to apply to each value in this collection
	 */
	default void replaceAll(UnaryOperator<E> op) {
		replaceAll(v -> op.apply(v), false);
	}

	/**
	 * Finds an equivalent value in this collection
	 *
	 * @param value The value to find
	 * @param onElement The listener to be called with the equivalent element
	 * @param first Whether to find the first or last occurrence of the value
	 * @return Whether the value was found
	 */
	default boolean forElement(E value, Consumer<? super CollectionElement<E>> onElement, boolean first) {
		CollectionElement<E> el = getElement(value, first);
		if (el != null)
			onElement.accept(el);
		return el != null;
	}

	/**
	 * @param value The value to search for
	 * @param onElement The action to perform on the element containing the given value, if found
	 * @param first Whether to search for the first equivalent element or the last one
	 * @return Whether such a value was found
	 */
	default boolean forMutableElement(E value, Consumer<? super MutableCollectionElement<E>> onElement, boolean first) {
		CollectionElement<E> el = getElement(value, first);
		if (el != null)
			onElement.accept(mutableElement(el.getElementId()));
		return el != null;
	}

	/**
	 * Finds a value in this collection matching the given search and performs an action on the {@link MutableCollectionElement} for that
	 * element
	 * 
	 * @param search The search function
	 * @param onElement The action to perform on the search's result
	 * @param first Whether to search for the first matching element or the last one
	 * @return Whether a result was found
	 */
	default boolean find(Predicate<? super E> search, Consumer<? super CollectionElement<E>> onElement, boolean first) {
		try (Transaction t = lock(false, true, null)) {
			ElementSpliterator<E> spliter = spliterator(first);
			boolean[] found = new boolean[1];
			while (!found[0] && spliter.forElement(el -> {
				if (search.test(el.get())) {
					found[0] = true;
					onElement.accept(el);
				}
			}, first)) {
			}
			return found[0];
		}
	}

	/**
	 * Finds all values in this collection matching the given search and performs an action on the {@link MutableCollectionElement} for each
	 * element
	 * 
	 * @param search The search function
	 * @param onElement The action to perform on the search's results
	 * @param forward Whether to search beginning-to-end or end-to-beginning
	 * @return The number of results found
	 */
	default int findAll(Predicate<? super E> search, Consumer<? super MutableCollectionElement<E>> onElement, boolean forward) {
		int[] found = new int[1];
		spliterator(forward).forEachElementM(//
			el -> {
				if (search.test(el.get())) {
					found[0]++;
					onElement.accept(el);
				}
			}, forward);
		return found[0];
	}

	@Override
	default MutableElementSpliterator<E> spliterator() {
		return spliterator(true);
	}

	@Override
	default Iterator<E> iterator() {
		return new BetterCollectionIterator<>(this);
	}

	/** @return An iterable over this collection's elements */
	default Iterable<CollectionElement<E>> elements() {
		return () -> new CollectionElementIterator<>(this);
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
		ValueHolder<E> value = new ValueHolder<>();
		try (Transaction t = lock(true, null)) {
			if (!spliterator(true).forElementM(el -> {
				value.accept(el.get());
				el.remove();
			}, true))
				throw new NoSuchElementException("Empty collection");
		}
		return value.get();
	}

	@Override
	default E removeLast() {
		ValueHolder<E> value = new ValueHolder<>();
		try (Transaction t = lock(true, null)) {
			if (!spliterator(true).forElementM(el -> {
				value.accept(el.get());
				el.remove();
			}, false))
				throw new NoSuchElementException("Empty collection");
		}
		return value.get();
	}

	@Override
	default E pollFirst() {
		ValueHolder<E> value = new ValueHolder<>();
		spliterator(true).forElementM(el -> {
			value.accept(el.get());
			el.remove(); // The Deque contract says nothing about what to do if the element can't be removed, so we'll throw an exception
		}, true);
		return value.get();
	}

	@Override
	default E pollLast() {
		ValueHolder<E> value = new ValueHolder<>();
		spliterator(false).forElementM(el -> {
			value.accept(el.get());
			el.remove(); // The Deque contract says nothing about what to do if the element can't be removed, so we'll throw an exception
		}, false);
		return value.get();
	}

	@Override
	default E getFirst() {
		ValueHolder<E> value = new ValueHolder<>();
		if (!spliterator(true).tryAdvance(value))
			throw new NoSuchElementException("Empty collection");
		return value.get();
	}

	@Override
	default E getLast() {
		ValueHolder<E> value = new ValueHolder<>();
		spliterator(false).tryReverse(value);
		return value.get();
	}

	@Override
	default E peekFirst() {
		ValueHolder<E> value = new ValueHolder<>();
		spliterator(true).tryAdvance(value);
		return value.get();
	}

	@Override
	default E peekLast() {
		ValueHolder<E> value = new ValueHolder<>();
		spliterator(false).tryReverse(value);
		return value.get();
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
	 * Locks the given collection as specified if it is {@link Transactable}, using
	 * {@link TransactableCollection#lock(boolean, boolean, Object)} for a {@link TransactableCollection},
	 * 
	 * @param c The collection to lock
	 * @param write Whether to lock for write or read-only
	 * @param structural Whether to lock structurally or update
	 * @param cause The cause of the transaction
	 * @return The transaction to close to unlock the collection
	 * @see TransactableCollection#lock(boolean, boolean, Object)
	 */
	public static Transaction lock(Collection<?> c, boolean write, boolean structural, Object cause) {
		if (c instanceof TransactableCollection)
			return ((TransactableCollection<?>) c).lock(write, structural, cause);
		else if (c instanceof Transactable)
			return ((Transactable) c).lock(write, cause);
		else
			return Transaction.NONE;
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
	public static boolean equals(Collection<?> c, Object o) {
		try (Transaction t = Transactable.lock(c, false, null); Transaction t2 = Transactable.lock(o, false, null)) {
			if (!(o instanceof Collection))
				return false;
			Collection<?> c2 = (Collection<?>) o;
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
		return new EmptyCollection<>();
	}

	/**
	 * An {@link Iterator} based on a {@link Spliterator}
	 * 
	 * @param <E> The type of values to iterate over
	 */
	class BetterCollectionIterator<E> implements Iterator<E> {
		private final BetterCollection<E> theCollection;
		private CollectionElement<E> next;
		private ElementId theLastElement;

		public BetterCollectionIterator(BetterCollection<E> collection) {
			theCollection = collection;
		}

		@Override
		public boolean hasNext() {
			if (next != null)
				return true;
			else {
				if (theLastElement == null)
					next = theCollection.getTerminalElement(true);
				else if (theLastElement.isPresent())
					next = theCollection.getAdjacentElement(theLastElement, true);
				return next != null;
			}
		}

		@Override
		public E next() {
			if (!hasNext())
				throw new NoSuchElementException();
			theLastElement = next.getElementId();
			if (!theLastElement.isPresent())
				throw new ConcurrentModificationException(BACKING_COLLECTION_CHANGED);
			E value = next.get();
			next = null;
			return value;
		}

		@Override
		public void remove() {
			if (theLastElement == null)
				throw new IllegalStateException("Iterator is not started or there were no elements");
			else if (!theLastElement.isPresent())
				throw new IllegalStateException("Element has already been removed");
			hasNext(); // Since last element will be removed after this, need to grab the next element (if there is one) before removing it
			theCollection.mutableElement(theLastElement).remove();
		}
	}

	class CollectionElementIterator<E> implements Iterator<CollectionElement<E>> {
		private final BetterCollection<E> theCollection;
		private CollectionElement<E> next;
		private ElementId theLastElement;

		public CollectionElementIterator(BetterCollection<E> collection) {
			theCollection = collection;
		}

		@Override
		public boolean hasNext() {
			if (next != null)
				return true;
			else {
				if (theLastElement == null)
					next = theCollection.getTerminalElement(true);
				else if (theLastElement.isPresent())
					next = theCollection.getAdjacentElement(theLastElement, true);
				return next != null;
			}
		}

		@Override
		public CollectionElement<E> next() {
			if (!hasNext())
				throw new NoSuchElementException();
			theLastElement = next.getElementId();
			if (!theLastElement.isPresent())
				throw new ConcurrentModificationException(BACKING_COLLECTION_CHANGED);
			CollectionElement<E> element = next;
			next = null;
			return element;
		}

		@Override
		public void remove() {
			if (theLastElement == null)
				throw new IllegalStateException("Iterator is not started or there were no elements");
			else if (!theLastElement.isPresent())
				throw new IllegalStateException("Element has already been removed");
			hasNext(); // Since last element will be removed after this, need to grab the next element (if there is one) before removing it
			theCollection.mutableElement(theLastElement).remove();
		}
	}

	/**
	 * Implements {@link #reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ReversedCollection<E> implements BetterCollection<E> {
		private final BetterCollection<E> theWrapped;

		protected ReversedCollection(BetterCollection<E> wrap) {
			theWrapped = wrap;
		}

		protected BetterCollection<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theWrapped.lock(write, structural, cause);
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return theWrapped.getStamp(structuralOnly);
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
		public MutableElementSpliterator<E> spliterator(ElementId element, boolean asNext) {
			return getWrapped().spliterator(element.reverse(), !asNext).reverse();
		}

		@Override
		public MutableElementSpliterator<E> spliterator(boolean fromStart) {
			return getWrapped().spliterator(!fromStart).reverse();
		}

		@Override
		public BetterCollection<E> reverse() {
			return getWrapped();
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
		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public long getStamp(boolean structuralOnly) {
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
		public MutableElementSpliterator<E> spliterator(ElementId element, boolean asNext) {
			throw new IllegalArgumentException(StdMsg.NOT_FOUND);
		}

		@Override
		public MutableElementSpliterator<E> spliterator(boolean fromStart) {
			return MutableElementSpliterator.empty();
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
}

package org.qommons.collect;

import java.util.Collection;
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
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * BetterCollection is an ordered {@link Collection} (also a {@link Deque}) that provides a great deal of extra functionality.
 * 
 * Fundamentally, each value in a BetterCollection is stored in an element, each of which has an {@link ElementId}. The ID of an element is
 * like an address. It is immutable until that element is removed from the collection, so it can be used to access elements even if the
 * properties used to store the elements may have changed, e.g. the hash code of an item in a hash set or the name of an item in a sorted
 * set ordered by name. Since it is a direct link to the placeholder of the element, access or modification to the element by its ID may be
 * significantly faster than access by value.
 * 
 * A BetterCollection must provide access to its elements by ID and value; and iteration from a BetterCollection is by element and not value
 * only. BetterCollection itself provides more functionality on top of this and implements most of the {@link Collection} API as well.
 * 
 * @param <E> The type of value in the collection
 */
public interface BetterCollection<E> extends Deque<E>, TransactableCollection<E> {
	boolean belongs(Object o);

	@Override
	abstract boolean isLockSupported();

	CollectionElement<E> getElement(E value, boolean first);

	CollectionElement<E> getElement(ElementId id);

	<X> X ofMutableElement(ElementId element, Function<? super MutableCollectionElement<E>, X> onElement);

	default void forMutableElement(ElementId element, Consumer<? super MutableCollectionElement<E>> onElement) {
		ofMutableElement(element, el -> {
			onElement.accept(el);
			return null;
		});
	}

	/**
	 * Tests the compatibility of an object with this collection. This method exposes a "best guess" on whether an element could be added to
	 * the collection , but does not provide any guarantee. This method should return null for any object for which {@link #add(Object)} is
	 * successful, but the fact that an object passes this test does not guarantee that it would be removed successfully. E.g. the position
	 * of the element in the collection may be a factor, but is tested for here.
	 *
	 * @param value The value to test compatibility for
	 * @return Null if given value could possibly be added to this collection, or a message why it can't
	 */
	String canAdd(E value);

	/**
	 * Similar to {@link #add(Object)}, but returns the element ID for the new element at which the value was added. Similarly to
	 * {@link #add(Object)}, this method may return null if the collection was not changed as a result of the operation.
	 * 
	 * @param value The value to add
	 * @param first Whether to prefer a lower position over a higher one. This parameter may be ignored if the collection does not support
	 *        position-indicated addition
	 * @return The element at which the value was added, or null if the value was not added
	 */
	CollectionElement<E> addElement(E value, boolean first);

	@Override
	default boolean add(E value) {
		return addElement(value, false) != null;
	}

	@Override
	default boolean addAll(Collection<? extends E> c) {
		try (Transaction t = lock(true, null); Transaction ct = Transactable.lock(c, false, null)) {
			for (E e : c)
				add(e);
			return !c.isEmpty();
		}
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
			int[] index = new int[1];
			spliterator().forEachRemaining(v -> array[index[0]++] = v);
			return array;
		}
	}

	@Override
	default <T> T[] toArray(T[] a) {
		try (Transaction t = lock(false, null)) {
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
		return forMutableElement((E) o, el -> el.remove(), true);
	}

	default boolean removeLast(Object o) {
		if (!belongs(o))
			return false;
		return forMutableElement((E) o, el -> el.remove(), false);
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		if (isEmpty() || c.isEmpty())
			return false;
		return findAll(v -> c.contains(v), el -> el.remove(), true) > 0;
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		if (isEmpty())
			return false;
		if (c.isEmpty()) {
			clear();
			return true;
		}
		return findAll(v -> !c.contains(v), el -> el.remove(), true) > 0;
	}

	@Override
	default boolean removeIf(Predicate<? super E> filter) {
		boolean[] removed = new boolean[1];
		findAll(filter, el -> {
			el.remove();
			removed[0] = true;
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
		try (Transaction t = lock(true, null)) {
			boolean[] replaced = new boolean[1];
			MutableElementSpliterator<E> iter = mutableSpliterator();
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
	default boolean forElement(E value, Consumer<? super CollectionElement<? extends E>> onElement, boolean first) {
		CollectionElement<E> el = getElement(value, first);
		if (el != null)
			onElement.accept(el);
		return el != null;
	}

	/**
	 * @param value The value to search for
	 * @param onElement The action to perform on the element containing the given value, if found
	 * @return Whether such a value was found
	 */
	default boolean forMutableElement(E value, Consumer<? super MutableCollectionElement<? extends E>> onElement, boolean first) {
		CollectionElement<E> el = getElement(value, first);
		if (el != null)
			forMutableElement(el.getElementId(), onElement);
		return el != null;
	}

	/**
	 * Finds a value in this collection matching the given search and performs an action on the {@link MutableCollectionElement} for that
	 * element
	 * 
	 * @param search The search function
	 * @param onElement The action to perform on the search's result
	 * @return Whether a result was found
	 */
	default boolean find(Predicate<? super E> search, Consumer<? super CollectionElement<? extends E>> onElement, boolean first) {
		try (Transaction t = lock(false, null)) {
			ElementSpliterator<E> spliter = first ? spliterator(first) : spliterator(first);
			boolean[] found = new boolean[1];
			while (spliter.forElement(el -> {
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
	 * Finds a value in this collection matching the given search and performs an action on the {@link MutableCollectionElement} for that
	 * element
	 * 
	 * @param search The search function
	 * @param onElement The action to perform on the search's result
	 * @return Whether a result was found
	 */
	default boolean findMutable(Predicate<? super E> search, Consumer<? super MutableCollectionElement<? extends E>> onElement,
		boolean first) {
		try (Transaction t = lock(true, null)) {
			MutableElementSpliterator<E> spliter = first ? mutableSpliterator(first) : mutableSpliterator(first);
			boolean[] found = new boolean[1];
			while (spliter.forElementM(el -> {
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
	 * @return The number of results found
	 */
	default int findAll(Predicate<? super E> search, Consumer<? super MutableCollectionElement<? extends E>> onElement, boolean forward) {
		int[] found = new int[1];
		mutableSpliterator(forward).forEachElementM(el -> {
			if (search.test(el.get())) {
				found[0]++;
				onElement.accept(forward ? el : el.reverse());
			}
		}, forward);
		return found[0];
	}

	@Override
	default ElementSpliterator<E> spliterator() {
		return spliterator(true);
	}

	default ElementSpliterator<E> spliterator(boolean fromStart) {
		return mutableSpliterator(fromStart).immutable();
	}

	default ElementSpliterator<E> spliterator(ElementId element, boolean asNext) {
		return mutableSpliterator(element, asNext).immutable();
	}

	/** @return An immutable (Iterator#remove()} will always throw an exception) iterator for iterating across this object's data */
	@Override
	default Iterator<E> iterator() {
		return new SpliteratorIterator<>(mutableSpliterator());
	}

	default MutableElementSpliterator<E> mutableSpliterator() {
		return mutableSpliterator(true);
	}

	MutableElementSpliterator<E> mutableSpliterator(boolean fromStart);

	MutableElementSpliterator<E> mutableSpliterator(ElementId element, boolean asNext);

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
		Object[] value = new Object[1];
		try (Transaction t = lock(true, null)) {
			if (!mutableSpliterator(true).forElementM(el -> {
				value[0] = el.get();
				el.remove();
			}, true))
				throw new NoSuchElementException("Empty collection");
		}
		return (E) value[0];
	}

	@Override
	default E removeLast() {
		Object[] value = new Object[1];
		try (Transaction t = lock(true, null)) {
			if (!mutableSpliterator(true).forElementM(el -> {
				value[0] = el.get();
				el.remove();
			}, false))
				throw new NoSuchElementException("Empty collection");
		}
		return (E) value[0];
	}

	@Override
	default E pollFirst() {
		Object[] value = new Object[1];
		try (Transaction t = lock(true, null)) {
			mutableSpliterator(true).forElementM(el -> {
				value[0] = el.get();
				el.remove(); // The Deque contract says nothing about what to do if the element can't be removed, so we'll throw an
								// exception
			}, true);
		}
		return (E) value[0];
	}

	@Override
	default E pollLast() {
		Object[] value = new Object[1];
		try (Transaction t = lock(true, null)) {
			mutableSpliterator(false).forElementM(el -> {
				value[0] = el.get();
				el.remove(); // The Deque contract says nothing about what to do if the element can't be removed, so we'll throw an
								// exception
			}, false);
		}
		return (E) value[0];
	}

	@Override
	default E getFirst() {
		Object[] value = new Object[1];
		if (!spliterator(true).tryAdvance(v -> value[0] = v))
			throw new NoSuchElementException("Empty collection");
		return (E) value[0];
	}

	@Override
	default E getLast() {
		Object[] value = new Object[1];
		spliterator(false).tryReverse(v -> value[0] = v);
		return (E) value[0];
	}

	@Override
	default E peekFirst() {
		Object[] value = new Object[1];
		spliterator(true).tryAdvance(v -> value[0] = v);
		return (E) value[0];
	}

	@Override
	default E peekLast() {
		Object[] value = new Object[1];
		spliterator(false).tryReverse(v -> value[0] = v);
		return (E) value[0];
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

	public static int hashCode(Collection<?> c) {
		try (Transaction t = Transactable.lock(c, false, null)) {
			int hash = 0;
			for (Object v : c)
				hash += v == null ? 0 : v.hashCode();
			return hash;
		}
	}

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

	public static <E> BetterCollection<E> empty() {
		return new EmptyCollection<>();
	}

	class SpliteratorIterator<E> implements Iterator<E> {
		private final MutableElementSpliterator<E> theSpliterator;

		private boolean isNextCached;
		private CollectionElement<E> cachedNext;
		private ElementId theLastElement;

		public SpliteratorIterator(MutableElementSpliterator<E> spliterator) {
			theSpliterator = spliterator;
		}

		@Override
		public boolean hasNext() {
			if (!isNextCached) {
				cachedNext = null;
				if (theSpliterator.forElement(el -> cachedNext = el, true)) {
					theLastElement = cachedNext.getElementId();
					isNextCached = true;
				}
			}
			return isNextCached;
		}

		@Override
		public E next() {
			if (!hasNext())
				throw new java.util.NoSuchElementException();
			isNextCached = false;
			E value = cachedNext.get();
			cachedNext = null;
			return value;
		}

		@Override
		public void remove() {
			if (theLastElement == null)
				throw new IllegalStateException("iterator is finished, not started, or the element has been removed");
			if (!theSpliterator.forElementM(el -> {
				if (!el.getElementId().equals(theLastElement))
					throw new IllegalStateException("element has been removed");
				el.remove();
			}, false))
				throw new IllegalStateException("element has been removed");
			theLastElement = null;
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
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
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
		public CollectionElement<E> getElement(E value, boolean first) {
			return CollectionElement.reverse(getWrapped().getElement(value, !first));
		}

		@Override
		public CollectionElement<E> getElement(ElementId id) {
			return getWrapped().getElement(id.reverse()).reverse();
		}

		@Override
		public <X> X ofMutableElement(ElementId element, Function<? super MutableCollectionElement<E>, X> onElement) {
			return getWrapped().ofMutableElement(element.reverse(), el -> onElement.apply(el.reverse()));
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(ElementId element, boolean asNext) {
			return getWrapped().mutableSpliterator(element.reverse(), !asNext).reverse();
		}

		@Override
		public ElementSpliterator<E> spliterator(boolean fromStart) {
			return getWrapped().spliterator(!fromStart).reverse();
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(boolean fromStart) {
			return getWrapped().mutableSpliterator(!fromStart).reverse();
		}

		@Override
		public BetterCollection<E> reverse() {
			return getWrapped();
		}

		@Override
		public String canAdd(E value) {
			return getWrapped().canAdd(value);
		}

		@Override
		public CollectionElement<E> addElement(E e, boolean first) {
			return CollectionElement.reverse(getWrapped().addElement(e, !first));
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

	class EmptyCollection<E> implements BetterCollection<E> {
		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Transaction.NONE;
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
		public String canAdd(E value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<E> addElement(E e, boolean first) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {}

		@Override
		public CollectionElement<E> getElement(E value, boolean first) {
			return null;
		}

		@Override
		public CollectionElement<E> getElement(ElementId id) {
			throw new IllegalArgumentException(StdMsg.NOT_FOUND);
		}

		@Override
		public <X> X ofMutableElement(ElementId element, Function<? super MutableCollectionElement<E>, X> onElement) {
			throw new IllegalArgumentException(StdMsg.NOT_FOUND);
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(ElementId element, boolean asNext) {
			throw new IllegalArgumentException(StdMsg.NOT_FOUND);
		}

		@Override
		public MutableElementSpliterator<E> mutableSpliterator(boolean fromStart) {
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

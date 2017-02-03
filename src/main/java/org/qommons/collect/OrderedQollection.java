package org.qommons.collect;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Predicate;

import org.qommons.Transaction;
import org.qommons.value.Value;

public interface OrderedQollection<E> extends Qollection<E> {
	@Override
	IndexedQuiterator<E> spliterator();

	/**
	 * @param filter The filter function
	 * @return The first value in this collection passing the filter, or null if none of this collection's elements pass
	 */
	default Value<E> findFirst(Predicate<E> filter) {
		return new OrderedCollectionFinder<>(this, filter, true);
	}

	/**
	 * @param filter The filter function
	 * @return The first value in this collection passing the filter, or null if none of this collection's elements pass
	 */
	default Value<E> findLast(Predicate<E> filter) {
		return new OrderedCollectionFinder<>(this, filter, false);
	}

	/** @return The first value in this collection, or null if this collection is empty */
	default Value<E> getFirst() {
		return new OrderedCollectionFinder<>(this, value -> true, true);
	}

	/**
	 * Finds the last value in this list. The get() method of this observable may have linear time unless this is an instance of
	 * {@link ObservableRandomAccessList}
	 *
	 * @return The last value in this collection, or null if this collection is empty
	 */
	default Value<E> getLast() {
		return new OrderedCollectionFinder<>(this, value -> true, false);
	}

	/** @return The last value in this collection, or null if the collection is empty */
	default E last() {
		Object[] returned = new Object[1];
		if (spliterator().tryAdvance(v -> returned[0] = v))
			return (E) returned[0];
		else
			return null;
	}

	// Ordered collections need to know the indexes of their elements in a somewhat efficient way, so these index methods make sense here

	/**
	 * @param index The index of the element to get
	 * @return The element of this collection at the given index
	 */
	default E get(int index) {
		try (Transaction t = lock(false, null)) {
			if (index < 0 || index >= size())
				throw new IndexOutOfBoundsException(index + " of " + size());
			Iterator<E> iter = iterator();
			for (int i = 0; i < index; i++)
				iter.next();
			return iter.next();
		}
	}

	/**
	 * @param value The value to get the index of in this collection
	 * @return The index of the first position in this collection occupied by the given value, or &lt; 0 if the element does not exist in
	 *         this collection
	 */
	default int indexOf(Object value) {
		try (Transaction t = lock(false, null)) {
			Iterator<E> iter = iterator();
			for (int i = 0; iter.hasNext(); i++) {
				if (Objects.equals(iter.next(), value))
					return i;
			}
			return -1;
		}
	}

	/**
	 * @param value The value to get the index of in this collection
	 * @return The index of the last position in this collection occupied by the given value, or &lt; 0 if the element does not exist in
	 *         this collection
	 */
	default int lastIndexOf(Object value) {
		try (Transaction t = lock(false, null)) {
			int ret = -1;
			Iterator<E> iter = iterator();
			for (int i = 0; iter.hasNext(); i++) {
				if (Objects.equals(iter.next(), value))
					ret = i;
			}
			return ret;
		}
	}
}

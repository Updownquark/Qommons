package org.qommons.collect;

import java.util.Comparator;
import java.util.function.Function;

import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

public interface SortedQSet<E> extends QSet<E>, TransactableSortedSet<E> {
	/**
	 * Returns a value at or adjacent to another value
	 *
	 * @param value The relative value
	 * @param up Whether to get the closest value greater or less than the given value
	 * @param withValue Whether to return the given value if it exists in the map
	 * @return An observable value with the result of the operation
	 */
	default Value<E> relative(E value, boolean up, boolean withValue) {
		if (up)
			return tailSet(value, withValue).getFirst();
		else
			return headSet(value, withValue).getLast();
	}

	@Override
	default E first() {
		if (isEmpty())
			throw new java.util.NoSuchElementException();
		return getFirst().get();
	}

	@Override
	default E last() {
		// Can't throw NoSuchElementException to comply with ObservableOrderedCollection.last()
		return getLast().get();
	}

	@Override
	default E floor(E e) {
		return relative(e, false, true).get();
	}

	@Override
	default E lower(E e) {
		return relative(e, false, false).get();
	}

	@Override
	default E ceiling(E e) {
		return relative(e, true, true).get();
	}

	@Override
	default E higher(E e) {
		return relative(e, true, false).get();
	}

	default <T> SortedQSet<T> mapEquivalent(TypeToken<T> type, Function<? super E, ? extends T> map, Comparator<? super T> comparator) {}

	static <K> SortedQSet<K> unique(Qollection<K> keyCollection, Comparator<? super K> compare) {
		// TODO Auto-generated method stub
	}

}

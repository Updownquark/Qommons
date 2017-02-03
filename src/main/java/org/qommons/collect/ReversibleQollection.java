package org.qommons.collect;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import org.qommons.ReversibleCollection;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

import javafx.beans.value.ObservableValue;

public interface ReversibleQollection<E> extends OrderedQollection<E>, ReversibleCollection<E> {
	Quiterator<E> reverseSpliterator();

	/** @return A collection that is identical to this one, but with its elements reversed */
	@Override
	default ReversibleQollection<E> reverse() {
		return new ReversedQollection<>(this);
	}

	/* Overridden for performance */
	@Override
	default ObservableValue<E> findLast(Predicate<E> filter) {
		return new OrderedReversibleCollectionFinder<>(this, filter, false);
	}

	/* Overridden for performance.  get() is linear in the super, constant time here */
	@Override
	default ObservableValue<E> getLast() {
		return new OrderedReversibleCollectionFinder<>(this, value -> true, false);
	}

	@Override
	default int lastIndexOf(Object value) {
		try (Transaction t = lock(false, null)) {
			int size = size();
			Iterator<E> iter = descending().iterator();
			for (int i = 0; iter.hasNext(); i++) {
				if (Objects.equals(iter.next(), value))
					return size - i - 1;
			}
			return -1;
		}
	}

	@Override
	default E last() {
		try (Transaction t = lock(false, null)) {
			Iterator<E> iter = descending().iterator();
			return iter.hasNext() ? iter.next() : null;
		}
	}

	@Override
	default <T> ReversibleQollection<T> map(Function<? super E, T> map) {
		return (ReversibleQollection<T>) OrderedQollection.super.map(map);
	}

	@Override
	default <T> ReversibleQollection<T> map(TypeToken<T> type, Function<? super E, T> map) {
		return (ReversibleQollection<T>) OrderedQollection.super.map(type, map);
	}

	@Override
	default <T> ReversibleQollection<T> map(TypeToken<T> type, Function<? super E, T> map, Function<? super T, E> reverse) {
		return new FilterMappedReversibleCollection<>(this, type, map, reverse);
	}

	@Override
	default ReversibleQollection<E> filter(Predicate<? super E> filter) {
		return (ReversibleQollection<E>) OrderedQollection.super.filter(filter);
	}

	@Override
	default <T> ReversibleQollection<T> filter(Class<T> type) {
		return (ReversibleQollection<T>) OrderedQollection.super.filter(type);
	}

	@Override
	default <T> ReversibleQollection<T> filterMap(Function<? super E, T> filterMap) {
		return (ReversibleQollection<T>) OrderedQollection.super.filterMap(filterMap);
	}

	@Override
	default <T> ReversibleQollection<T> filterMap(TypeToken<T> type, Function<? super E, T> map, Function<? super T, E> reverse) {
		return (ReversibleQollection<T>) OrderedQollection.super.filterMap(type, map, reverse);
	}

	@Override
	default <T> ReversibleQollection<T> filterMap2(TypeToken<T> type, Function<? super E, FilterMapResult<T>> map,
		Function<? super T, E> reverse) {
		return new FilteredMappedReversibleQollection<>(this, type, map, reverse);
	}

}

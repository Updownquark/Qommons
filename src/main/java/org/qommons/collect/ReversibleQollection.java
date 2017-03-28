package org.qommons.collect;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.qommons.ReversibleCollection;
import org.qommons.Transaction;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

public interface ReversibleQollection<E> extends OrderedQollection<E>, ReversibleCollection<E> {
	Quiterator<E> reverseSpliterator();

	@Override
	default Iterable<E> descending() {
		return () -> new QuiteratorIterator<>(reverseSpliterator());
	}

	/** @return A collection that is identical to this one, but with its elements reversed */
	@Override
	default ReversibleQollection<E> reverse() {
		return new ReversedQollection<>(this);
	}

	/* Overridden for performance */
	@Override
	default Value<E> findLast(Predicate<E> filter) {
		return new OrderedReversibleCollectionFinder<>(this, filter, false);
	}

	/* Overridden for performance.  get() may be faster here since we can start at the end */
	@Override
	default Value<E> getLast() {
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
	default <T> ReversibleQollection<T> map(TypeToken<T> type, Function<? super E, ? extends T> map) {
		return (ReversibleQollection<T>) OrderedQollection.super.map(type, map);
	}

	@Override
	default <T> ReversibleQollection<T> map(TypeToken<T> type, Function<? super E, ? extends T> map,
		Function<? super T, ? extends E> reverse) {
		return map(type, map, reverse, false);
	}

	@Override
	default <T> ReversibleQollection<T> map(TypeToken<T> type, Function<? super E, ? extends T> map,
		Function<? super T, ? extends E> reverse, boolean mapNulls) {
		return new MappedReversibleQollection<>(this, type, map, reverse, mapNulls);
	}

	@Override
	default ReversibleQollection<E> filter(Function<? super E, String> filter) {
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
	default <T> ReversibleQollection<T> filterMap(TypeToken<T> type, Function<? super E, ? extends T> map,
		Function<? super T, ? extends E> reverse) {
		return (ReversibleQollection<T>) OrderedQollection.super.filterMap(type, map, reverse);
	}

	@Override
	default <T> ReversibleQollection<T> filterMap2(TypeToken<T> type, Function<? super E, FilterMapResult<T>> map,
		Function<? super T, E> reverse) {
		return new FilteredMappedReversibleQollection<>(this, type, map, reverse);
	}

	/**
	 * Implements {@link OrderedQollection#map(Function)}
	 *
	 * @param <E> The type of the collection to map
	 * @param <T> The type of the mapped collection
	 */
	class MappedReversibleQollection<E, T> extends MappedOrderedQollection<E, T> implements ReversibleQollection<T> {
		protected MappedReversibleQollection(ReversibleQollection<E> wrap, TypeToken<T> type, Function<? super E, ? extends T> map,
			Function<? super T, ? extends E> reverse, boolean mapNulls) {
			super(wrap, type, map, reverse, mapNulls);
		}

		@Override
		protected ReversibleQollection<E> getWrapped() {
			return (ReversibleQollection<E>) super.getWrapped();
		}

		@Override
		public Quiterator<T> reverseSpliterator() {
			return map(getWrapped().reverseSpliterator());
		}
	}

	/**
	 * Implements {@link OrderedQollection#filterMap(Function)}
	 *
	 * @param <E> The type of the collection to be filter-mapped
	 * @param <T> The type of the mapped collection
	 */
	class FilterMappedOrderedQollection<E, T> extends FilterMappedQollection<E, T> implements OrderedQollection<T> {
		FilterMappedOrderedQollection(OrderedQollection<E> wrap, TypeToken<T> type, Function<? super E, FilterMapResult<? extends T>> map,
			Function<? super T, ? extends E> reverse) {
			super(wrap, type, map, reverse);
		}

		@Override
		protected OrderedQollection<E> getWrapped() {
			return (OrderedQollection<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link OrderedQollection#combine(Value, BiFunction)}
	 *
	 * @param <E> The type of the collection to be combined
	 * @param <T> The type of the value to combine the collection elements with
	 * @param <V> The type of the combined collection
	 */
	class CombinedOrderedQollection<E, T, V> extends CombinedQollection<E, T, V> implements OrderedQollection<V> {
		CombinedOrderedQollection(OrderedQollection<E> collection, Value<T> value, TypeToken<V> type,
			BiFunction<? super E, ? super T, V> map, BiFunction<? super V, ? super T, E> reverse) {
			super(collection, type, value, map, reverse);
		}

		@Override
		protected OrderedQollection<E> getWrapped() {
			return (OrderedQollection<E>) super.getWrapped();
		}
	}
}

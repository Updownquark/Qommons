package org.qommons.collect;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.qommons.ReversibleCollection;
import org.qommons.Transaction;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

/**
 * An {@link OrderedQollection} that also knows how to return its elements in reverse order
 * 
 * @param <E> The type of elements in the collection
 */
public interface ReversibleQollection<E> extends OrderedQollection<E>, ReversibleCollection<E> {
	/** @return A Quiterator that returns this collection's elements in reverse order */
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

	/**
	 * @param filter The filter function
	 * @return The last value in this collection passing the filter, or empty if none of this collection's elements pass
	 */
	default Optional<E> findLast(Predicate<? super E> filter) {
		try (Transaction t = lock(false, null)) {
			for (E element : descending()) {
				if (filter.test(element))
					return Optional.of(element);
			}
			return Optional.empty();
		}
	}

	/** @return The last value in this collection, or null if this collection is empty */
	default E getLast() {
		Object[] value = new Object[1];
		if (!reverseSpliterator().tryAdvanceElement(el -> {
			value[0] = el.get();
		}))
			return null;
		return (E) value[0];
	}

	/**
	 * Finds and removes the last value in the collection if it is not empty
	 * 
	 * @return The last value in the collection, or null if this collection was empty
	 */
	default E pollLast() {
		Object[] value = new Object[1];
		if (!reverseSpliterator().tryAdvanceElement(el -> {
			value[0] = el.get();
			try {
				el.remove();
			} catch (IllegalArgumentException e) {
				throw new UnsupportedOperationException(e);
			}
		}))
			return null;
		return (E) value[0];
	}

	/* Overridden for performance */

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
	default <T> ReversibleQollection<T> map(Function<? super E, T> map) {
		return (ReversibleQollection<T>) OrderedQollection.super.map(map);
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
	default <T> ReversibleQollection<T> filterMap(FilterMapDef<E, ?, T> filterMap) {
		return new FilterMappedReversibleQollection<>(this, filterMap);
	}

	@Override
	default <T, V> ReversibleQollection<V> combine(Value<T> arg, BiFunction<? super E, ? super T, V> func) {
		return (ReversibleQollection<V>) OrderedQollection.super.combine(arg, func);
	}

	@Override
	default <T, V> ReversibleQollection<V> combine(Value<T> arg, TypeToken<V> type, BiFunction<? super E, ? super T, V> func) {
		return (ReversibleQollection<V>) OrderedQollection.super.combine(arg, type, func);
	}

	@Override
	default <T, V> ReversibleQollection<V> combine(Value<T> arg, TypeToken<V> type, BiFunction<? super E, ? super T, V> func,
		BiFunction<? super V, ? super T, E> reverse) {
		return new CombinedReversibleQollection<>(this, arg, type, func, reverse);
	}

	@Override
	default ReversibleQollection<E> immutable(String modMsg) {
		return (ReversibleQollection<E>) OrderedQollection.super.immutable(modMsg);
	}

	@Override
	default ReversibleQollection<E> filterRemove(Function<? super E, String> filter) {
		return (ReversibleQollection<E>) OrderedQollection.super.filterRemove(filter);
	}

	@Override
	default ReversibleQollection<E> noRemove(String modMsg) {
		return (ReversibleQollection<E>) OrderedQollection.super.noRemove(modMsg);
	}

	@Override
	default ReversibleQollection<E> filterAdd(Function<? super E, String> filter) {
		return (ReversibleQollection<E>) OrderedQollection.super.filterAdd(filter);
	}

	@Override
	default ReversibleQollection<E> noAdd(String modMsg) {
		return (ReversibleQollection<E>) OrderedQollection.super.noAdd(modMsg);
	}

	@Override
	default ReversibleQollection<E> filterModification(Function<? super E, String> removeFilter, Function<? super E, String> addFilter) {
		return new ModFilteredReversibleQollection<>(this, removeFilter, addFilter);
	}

	/**
	 * Implements {@link ReversibleQollection#reverse()} by default
	 * 
	 * @param <E> The type of elements in the collection
	 */
	class ReversedQollection<E> extends ReversedCollection<E> implements ReversibleQollection<E> {
		public ReversedQollection(ReversibleQollection<E> wrap) {
			super(wrap);
		}

		@Override
		protected ReversibleQollection<E> getWrapped() {
			return (ReversibleQollection<E>) super.getWrapped();
		}

		@Override
		public ReversibleQollection<E> reverse() {
			return (ReversibleQollection<E>) super.reverse();
		}

		@Override
		public TypeToken<E> getType() {
			return getWrapped().getType();
		}

		@Override
		public boolean isLockSupported() {
			return getWrapped().isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return getWrapped().lock(write, cause);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return getWrapped().containsAny(c);
		}

		@Override
		public Quiterator<E> spliterator() {
			return getWrapped().reverseSpliterator();
		}

		@Override
		public String canAdd(E value) {
			return getWrapped().canAdd(value);
		}

		@Override
		public Qollection<E> addValues(E... values) {
			getWrapped().addValues(values);
			return this;
		}

		@Override
		public String canRemove(Object value) {
			return getWrapped().canRemove(value);
		}

		@Override
		public Quiterator<E> reverseSpliterator() {
			return getWrapped().spliterator();
		}

		@Override
		public E get(int index) {
			try (Transaction t = getWrapped().lock(false, null)) {
				return getWrapped().get(getWrapped().size() - index - 1);
			}
		}

		@Override
		public int indexOf(Object value) {
			try (Transaction t = getWrapped().lock(false, null)) {
				return getWrapped().size() - getWrapped().lastIndexOf(value) - 1;
			}
		}

		@Override
		public int lastIndexOf(Object value) {
			try (Transaction t = getWrapped().lock(false, null)) {
				return getWrapped().size() - getWrapped().indexOf(value) - 1;
			}
		}

		@Override
		public Optional<E> findFirst(Predicate<? super E> filter) {
			return getWrapped().findLast(filter);
		}

		@Override
		public Optional<E> findLast(Predicate<? super E> filter) {
			return getWrapped().findFirst(filter);
		}

		@Override
		public E getFirst() {
			return getWrapped().getLast();
		}

		@Override
		public E getLast() {
			return getWrapped().getFirst();
		}

		@Override
		public E[] toArray() {
			return (E[]) super.toArray();
		}
	}

	/**
	 * Implements {@link ReversibleQollection#filterMap(Qollection.FilterMapDef)}
	 * 
	 * @param <E> The type of values in the source collection
	 * @param <T> The type of values in this collection
	 */
	class FilterMappedReversibleQollection<E, T> extends FilterMappedOrderedQollection<E, T> implements ReversibleQollection<T> {
		public FilterMappedReversibleQollection(ReversibleQollection<E> wrap, Qollection.FilterMapDef<E, ?, T> def) {
			super(wrap, def);
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
	 * Implements {@link ReversibleQollection#combine(Value, TypeToken, BiFunction, BiFunction)}
	 * 
	 * @param <E> The type of values in the source collection
	 * @param <T> The type of the combined value
	 * @param <V> The type of values in this collection
	 */
	class CombinedReversibleQollection<E, T, V> extends CombinedOrderedQollection<E, T, V> implements ReversibleQollection<V> {
		public CombinedReversibleQollection(ReversibleQollection<E> collection, Value<T> value, TypeToken<V> type,
			BiFunction<? super E, ? super T, V> map, BiFunction<? super V, ? super T, E> reverse) {
			super(collection, value, type, map, reverse);
		}

		@Override
		protected ReversibleQollection<E> getWrapped() {
			return (ReversibleQollection<E>) super.getWrapped();
		}

		@Override
		public Quiterator<V> reverseSpliterator() {
			return combine(getWrapped().reverseSpliterator());
		}
	}

	/**
	 * Implements {@link ReversibleQollection#filterModification(Function, Function)}
	 * 
	 * @param <E> The type of values in this collection
	 */
	class ModFilteredReversibleQollection<E> extends ModFilteredOrderedQollection<E> implements ReversibleQollection<E> {
		public ModFilteredReversibleQollection(ReversibleQollection<E> wrapped, Function<? super E, String> removeFilter,
			Function<? super E, String> addFilter) {
			super(wrapped, removeFilter, addFilter);
		}

		@Override
		protected ReversibleQollection<E> getWrapped() {
			return (ReversibleQollection<E>) super.getWrapped();
		}

		@Override
		public Quiterator<E> reverseSpliterator() {
			return modFilter(getWrapped().reverseSpliterator());
		}
	}
}

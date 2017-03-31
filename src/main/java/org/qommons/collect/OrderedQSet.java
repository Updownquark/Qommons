package org.qommons.collect;

import java.util.function.Function;

/**
 * A {@link QSet} that is also an {@link OrderedQollection}
 * 
 * @param <E> The type of values in this set
 */
public interface OrderedQSet<E> extends QSet<E>, OrderedQollection<E> {
	@Override
	default OrderedQSet<E> filter(Function<? super E, String> filter) {
		return (OrderedQSet<E>) QSet.super.filter(filter);
	}

	@Override
	default <T> OrderedQSet<T> filterMap(EquivalentFilterMapDef<E, ?, T> filterMap) {
		return new FilterMappedOrderedSet<>(this, filterMap);
	}

	@Override
	default <T> OrderedQSet<T> filter(Class<T> type) {
		return (OrderedQSet<T>) QSet.super.filter(type);
	}

	@Override
	default OrderedQSet<E> immutable(String modMsg) {
		return (OrderedQSet<E>) QSet.super.immutable(modMsg);
	}

	@Override
	default OrderedQSet<E> filterRemove(Function<? super E, String> filter) {
		return (OrderedQSet<E>) QSet.super.filterRemove(filter);
	}

	@Override
	default OrderedQSet<E> noRemove(String modMsg) {
		return (OrderedQSet<E>) QSet.super.noRemove(modMsg);
	}

	@Override
	default OrderedQSet<E> filterAdd(Function<? super E, String> filter) {
		return (OrderedQSet<E>) QSet.super.filterAdd(filter);
	}

	@Override
	default OrderedQSet<E> noAdd(String modMsg) {
		return (OrderedQSet<E>) QSet.super.noAdd(modMsg);
	}

	@Override
	default OrderedQSet<E> filterModification(Function<? super E, String> removeFilter, Function<? super E, String> addFilter) {
		return new ModFilteredOrderedSet<>(this, removeFilter, addFilter);
	}

	/**
	 * @param collection The collection to wrap
	 * @return An ordered set consisting of the unique elements in the given collection
	 */
	static <E> OrderedQSet<E> unique(OrderedQollection<E> collection) {
		return new OrderedCollectionWrappingSet<>(collection);
	}

	/**
	 * A filter-mapped ordered set
	 * 
	 * @param <E> The type of values in the source set
	 * @param <T> The type of values in this set
	 */
	class FilterMappedOrderedSet<E, T> extends FilterMappedSet<E, T> implements OrderedQSet<T> {
		public FilterMappedOrderedSet(OrderedQSet<E> wrap, QSet.EquivalentFilterMapDef<E, ?, T> filterMapDef) {
			super(wrap, filterMapDef);
		}

		@Override
		protected OrderedQSet<E> getWrapped() {
			return (OrderedQSet<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link OrderedQSet#filterModification(Function, Function)}
	 * 
	 * @param <E> The type of values in this set
	 */
	class ModFilteredOrderedSet<E> extends ModFilteredSet<E> implements OrderedQSet<E> {
		public ModFilteredOrderedSet(OrderedQSet<E> wrapped, Function<? super E, String> removeFilter,
			Function<? super E, String> addFilter) {
			super(wrapped, removeFilter, addFilter);
		}

		@Override
		protected OrderedQSet<E> getWrapped() {
			return (OrderedQSet<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link OrderedQSet#unique(OrderedQollection)}
	 * 
	 * @param <E> The type of values in this set
	 */
	class OrderedCollectionWrappingSet<E> extends CollectionWrappingSet<E> implements OrderedQSet<E> {
		public OrderedCollectionWrappingSet(OrderedQollection<E> collection) {
			super(collection);
		}

		@Override
		protected OrderedQollection<E> getWrapped() {
			return (OrderedQollection<E>) super.getWrapped();
		}
	}
}

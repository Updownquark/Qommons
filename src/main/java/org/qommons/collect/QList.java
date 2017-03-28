package org.qommons.collect;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

public interface QList<E> extends ReversibleQollection<E>, TransactableList<E> {
	@Override
	default QList<E> reverse() {
		return new ReversedQList<>(this);
	}

	@Override
	default <T> QList<T> map(Function<? super E, T> map) {
		return (QList<T>) ReversibleQollection.super.map(map);
	}

	@Override
	default QList<E> filter(Function<? super E, String> filter) {
		return (QList<E>) ReversibleQollection.super.filter(filter);
	}

	@Override
	default <T> QList<T> filter(Class<T> type) {
		return (QList<T>) ReversibleQollection.super.filter(type);
	}

	@Override
	default <T> QList<T> filterMap(FilterMapDef<E, ?, T> filterMap) {
		return new FilterMappedQList<>(this, filterMap);
	}

	@Override
	default <T, V> QList<V> combine(Value<T> arg, BiFunction<? super E, ? super T, V> func) {
		return (QList<V>) ReversibleQollection.super.combine(arg, func);
	}

	@Override
	default <T, V> QList<V> combine(Value<T> arg, TypeToken<V> type, BiFunction<? super E, ? super T, V> func) {
		return (QList<V>) ReversibleQollection.super.combine(arg, type, func);
	}

	@Override
	default <T, V> QList<V> combine(Value<T> arg, TypeToken<V> type, BiFunction<? super E, ? super T, V> func,
		BiFunction<? super V, ? super T, E> reverse) {
		return new CombinedQList<>(this, arg, type, func, reverse);
	}

	@Override
	default QList<E> immutable(String modMsg) {
		return (QList<E>) ReversibleQollection.super.immutable(modMsg);
	}

	@Override
	default QList<E> filterRemove(Function<? super E, String> filter) {
		return (QList<E>) ReversibleQollection.super.filterRemove(filter);
	}

	@Override
	default QList<E> noRemove(String modMsg) {
		return (QList<E>) ReversibleQollection.super.noRemove(modMsg);
	}

	@Override
	default QList<E> filterAdd(Function<? super E, String> filter) {
		return (QList<E>) ReversibleQollection.super.filterAdd(filter);
	}

	@Override
	default QList<E> noAdd(String modMsg) {
		return (QList<E>) ReversibleQollection.super.noAdd(modMsg);
	}

	@Override
	default QList<E> filterModification(Function<? super E, String> removeFilter, Function<? super E, String> addFilter) {
		return new ModFilteredQList<>(this, removeFilter, addFilter);
	}

	static <E> QList<E> constant(TypeToken<E> type, E... values) {
		return new ConstantQList<>(type, java.util.Arrays.asList(values));
	}

	static <E> QList<E> constant(TypeToken<E> type, List<E> values) {
		return new ConstantQList<>(type, values);
	}

	class ReversedQList<E> extends ReversedQollection<E> implements QList<E> {
		public ReversedQList(QList<E> wrap) {
			super(wrap);
		}

		@Override
		protected QList<E> getWrapped() {
			return (QList<E>) super.getWrapped();
		}
	}

	class FilterMappedQList<E, T> extends FilterMappedReversibleQollection<E, T> implements QList<T> {
		public FilterMappedQList(QList<E> wrap, org.qommons.collect.Qollection.FilterMapDef<E, ?, T> def) {
			super(wrap, def);
		}

		@Override
		protected QList<E> getWrapped() {
			return (QList<E>) super.getWrapped();
		}
	}

	class CombinedQList<E, T, V> extends CombinedReversibleQollection<E, T, V> implements QList<V> {
		public CombinedQList(QList<E> collection, Value<T> value, TypeToken<V> type, BiFunction<? super E, ? super T, V> map,
			BiFunction<? super V, ? super T, E> reverse) {
			super(collection, value, type, map, reverse);
		}

		@Override
		protected QList<E> getWrapped() {
			return (QList<E>) super.getWrapped();
		}
	}

	class ModFilteredQList<E> extends ModFilteredReversibleQollection<E> implements QList<E> {
		public ModFilteredQList(QList<E> wrapped, Function<? super E, String> removeFilter, Function<? super E, String> addFilter) {
			super(wrapped, removeFilter, addFilter);
		}

		@Override
		protected QList<E> getWrapped() {
			return (QList<E>) super.getWrapped();
		}
	}

	class ConstantQList<E> extends ConstantOrderedQollection<E> implements QList<E> {
		public ConstantQList(TypeToken<E> type, List<E> collection) {
			super(type, collection);
		}
	}
}

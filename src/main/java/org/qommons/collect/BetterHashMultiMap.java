package org.qommons.collect;

import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

/**
 * A hash-based {@link BetterMultiMap}
 * 
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public class BetterHashMultiMap<K, V> extends AbstractBetterMultiMap<K, V> {
	/**
	 * @param <K> The key-type for the map
	 * @param <V> The value-type for the map
	 * @return A builder to build a multi-map
	 */
	public static <K, V> Builder<K, V> build() {
		return new Builder<>();
	}

	/**
	 * A builder for a hash-based multi-map
	 * 
	 * @param <K> The key-type for the map
	 * @param <V> The value-type for the map
	 */
	public static class Builder<K, V> extends AbstractBetterMultiMap.Builder<K, V> {
		private final BetterHashMap.HashMapBuilder theMapBuilder;

		Builder() {
			super("BetterHashMultiMap");
			theMapBuilder = BetterHashMap.build();
		}

		@Override
		public Builder<K, V> safe(boolean safe) {
			super.safe(safe);
			return this;
		}

		@Override
		public Builder<K, V> withLocking(CollectionLockingStrategy locking) {
			super.withLocking(locking);
			return this;
		}

		@Override
		public Builder<K, V> withSortedValues(Comparator<? super V> valueCompare, boolean distinctValues) {
			super.withSortedValues(valueCompare, distinctValues);
			return this;
		}

		@Override
		public Builder<K, V> withValues(ValueCollectionSupplier<? super K, ? super V> values) {
			super.withValues(values);
			return this;
		}

		@Override
		public Builder<K, V> withDescription(String description) {
			super.withDescription(description);
			return this;
		}

		/**
		 * @param hasher The hasher to produce hashcode values for keys in the map
		 * @param equals The equality test for keys in the map
		 * @return This builder
		 */
		public Builder<K, V> withEquivalence(ToIntFunction<Object> hasher, BiFunction<Object, Object, Boolean> equals) {
			theMapBuilder.withEquivalence(hasher, equals);
			return this;
		}

		@Override
		public BetterMultiMap<K, V> buildMultiMap() {
			return new BetterHashMultiMap<>(getLocking(), getValues(), getDescription(), //
				theMapBuilder.withLocking(getLocking()).withDescription(getDescription() + " entries").buildMap());
		}
	}

	private BetterHashMultiMap(CollectionLockingStrategy locking, ValueCollectionSupplier<? super K, ? super V> values, String description,
		BetterMap<K, BetterCollection<V>> entries) {
		super(locking, entries, values, description);
	}
}

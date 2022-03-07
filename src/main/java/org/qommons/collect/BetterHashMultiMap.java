package org.qommons.collect;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
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
	public static <K, V> Builder<K, V, ?> build() {
		return new Builder<>();
	}

	/**
	 * A builder for a hash-based multi-map
	 * 
	 * @param <K> The key-type for the map
	 * @param <V> The value-type for the map
	 * @param <B> The sub-type of this builder
	 */
	public static class Builder<K, V, B extends Builder<K, V, ? extends B>> extends AbstractBetterMultiMap.Builder<K, V, B> {
		private final BetterHashMap.HashMapBuilder<?> theMapBuilder;

		Builder() {
			super("BetterHashMultiMap");
			theMapBuilder = BetterHashMap.build();
		}

		@Override
		protected Function<Object, CollectionLockingStrategy> getLocker() {
			return super.getLocker();
		}

		/**
		 * @param hasher The hasher to produce hashcode values for keys in the map
		 * @param equals The equality test for keys in the map
		 * @return This builder
		 */
		public B withEquivalence(ToIntFunction<Object> hasher, BiFunction<Object, Object, Boolean> equals) {
			theMapBuilder.withEquivalence(hasher, equals);
			return (B) this;
		}

		/**
		 * Causes this builder to build a map whose values are stored by identity, instead of notional equivalence
		 * 
		 * @return This builder
		 */
		public B identity() {
			return withEquivalence(System::identityHashCode, (o1, o2) -> o1 == o2);
		}

		/**
		 * @param loadFactor The load factor for the map that this builder creates
		 * @return This builder
		 */
		public B withLoadFactor(double loadFactor) {
			theMapBuilder.withLoadFactor(loadFactor);
			return (B) this;
		}

		/**
		 * @param initExpectedSize The number of values that the set created by this builder should accommodate without re-hashing the table
		 * @return This builder
		 */
		public B withInitialCapacity(int initExpectedSize) {
			theMapBuilder.withInitialCapacity(initExpectedSize);
			return (B) this;
		}

		@Override
		public BetterMultiMap<K, V> buildMultiMap() {
			return new BetterHashMultiMap<>(getLocker(), getValues(), getDescription(), //
				theMapBuilder.withCollectionLocking(getLocker()).withDescription(getDescription() + " entries").build(),
				getInitialValues());
		}
	}

	private BetterHashMultiMap(Function<Object, CollectionLockingStrategy> locking, ValueCollectionSupplier<? super K, ? super V> values,
		String description, BetterMap<K, BetterCollection<V>> entries, Map<K, List<V>> initialValues) {
		super(locking, entries, values, description, initialValues);
	}
}

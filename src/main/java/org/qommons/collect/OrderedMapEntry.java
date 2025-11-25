package org.qommons.collect;

/**
 * A sub-type of {@link MapEntryHandle} that knows its absolute position in the map's entry set, not just its order relative to other
 * entries
 * 
 * @param <K> The key-type of the entry
 * @param <V> The value-type of the entry
 */
public interface OrderedMapEntry<K, V> extends MapEntryHandle<K, V>, ListElement<V> {
	@Override
	OrderedMapEntry<K, V> getAdjacent(boolean next);

	@Override
	default OrderedMapEntry<K, V> reverse() {
		return new ReversedOrderedMapEntry<>(this);
	}

	/**
	 * @param <K> The key type of the entry
	 * @param <V> The value type of the entry
	 * @param entry The entry to reverse
	 * @return The reversed entry, or null if entry was null
	 */
	static <K, V> OrderedMapEntry<K, V> reverse(OrderedMapEntry<K, V> entry) {
		return entry == null ? null : entry.reverse();
	}

	/**
	 * Default implementation of {@link OrderedMapEntry#reverse()}
	 * 
	 * @param <K> The key type of the entry
	 * @param <V> The value type of the entry
	 */
	public class ReversedOrderedMapEntry<K, V> extends ReversedListElement<V> implements OrderedMapEntry<K, V> {
		/** @param wrapped The entry to wrap */
		public ReversedOrderedMapEntry(OrderedMapEntry<K, V> wrapped) {
			super(wrapped);
		}

		@Override
		protected OrderedMapEntry<K, V> getWrapped() {
			return (OrderedMapEntry<K, V>) super.getWrapped();
		}

		@Override
		public K getKey() {
			return getWrapped().getKey();
		}

		@Override
		public OrderedMapEntry<K, V> getAdjacent(boolean next) {
			return OrderedMapEntry.reverse(getWrapped().getAdjacent(!next));
		}

		@Override
		public OrderedMapEntry<K, V> reverse() {
			return getWrapped();
		}
	}
}

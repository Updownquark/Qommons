package org.qommons.collect;

/**
 * A sub-type of {@link MultiEntryHandle} that knows its absolute position in the map's entry set, not just its order relative to other
 * entries
 * 
 * @param <K> The key-type of the entry
 * @param <V> The value-type of the entry
 */
public interface OrderedMultiEntry<K, V> extends MultiEntryHandle<K, V>, ListElement<K> {
	@Override
	OrderedMultiEntry<K, V> getAdjacent(boolean next);

	@Override
	default OrderedMultiEntry<K, V> reverse() {
		return new ReversedOrderedMultiEntry<>(this);
	}

	/**
	 * @param <K> The key type of the entry
	 * @param <V> The value type of the entry
	 * @param entry The entry to reverse
	 * @return The reversed entry, or null if entry was null
	 */
	static <K, V> OrderedMultiEntry<K, V> reverse(OrderedMultiEntry<K, V> entry) {
		return entry == null ? null : entry.reverse();
	}

	/**
	 * A {@link MapEntryHandle} that is reversed
	 * 
	 * @param <K> The key type of the entry
	 * @param <V> The value type of the entry
	 */
	public class ReversedOrderedMultiEntry<K, V> extends ReversedMultiEntryHandle<K, V> implements OrderedMultiEntry<K, V> {
		/** @param wrapped The entry to wrap */
		public ReversedOrderedMultiEntry(OrderedMultiEntry<K, V> wrapped) {
			super(wrapped);
		}

		@Override
		protected OrderedMultiEntry<K, V> getWrapped() {
			return (OrderedMultiEntry<K, V>) super.getWrapped();
		}

		@Override
		public K getKey() {
			return getWrapped().getKey();
		}

		@Override
		public OrderedMultiEntry<K, V> getAdjacent(boolean next) {
			return OrderedMultiEntry.reverse(getWrapped().getAdjacent(!next));
		}

		@Override
		public int getElementsBefore() {
			return getWrapped().getElementsAfter();
		}

		@Override
		public int getElementsAfter() {
			return getWrapped().getElementsBefore();
		}

		@Override
		public OrderedMultiEntry<K, V> reverse() {
			return getWrapped();
		}
	}
}

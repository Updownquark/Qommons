package org.qommons.collect;

/**
 * The mutable value handle in a {@link BetterMultiMap} returned by {@link BetterMultiMap#mutableElement(ElementId, ElementId)}
 * 
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public interface MutableMultiMapHandle<K, V> extends MultiEntryValueHandle<K, V>, MutableMapEntryHandle<K, V> {
	@Override
	MutableMultiMapHandle<K, V> getAdjacent(boolean next);

	@Override
	default MutableMultiMapHandle<K, V> reverse() {
		return new ReversedMutableMultiMapHandle<>(this);
	}

	/**
	 * Implements {@link MutableMultiMapHandle#reverse()}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ReversedMutableMultiMapHandle<K, V> extends ReversedMutableMapEntryHandle<K, V> implements MutableMultiMapHandle<K, V> {
		public ReversedMutableMultiMapHandle(MutableMultiMapHandle<K, V> wrapped) {
			super(wrapped);
		}

		@Override
		protected MutableMultiMapHandle<K, V> getWrapped() {
			return (MutableMultiMapHandle<K, V>) super.getWrapped();
		}

		@Override
		public ElementId getKeyId() {
			return getWrapped().getKeyId().reversed();
		}

		@Override
		public MutableMultiMapHandle<K, V> getAdjacent(boolean next) {
			MutableMultiMapHandle<K, V> adj = getWrapped().getAdjacent(!next);
			return adj == null ? null : adj.reverse();
		}

		@Override
		public MutableMultiMapHandle<K, V> reverse() {
			return getWrapped();
		}
	}
}

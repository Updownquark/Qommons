package org.qommons.collect;

import java.util.Map;

import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * A {@link CollectionElement} for a {@link BetterMap}
 * 
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public interface MapEntryHandle<K, V> extends CollectionElement<V>, Map.Entry<K, V> {
	@Override
	K getKey();

	@Override
	default V getValue() {
		return get();
	}

	@Override
	default V setValue(V value) {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	default MapEntryHandle<K, V> reverse() {
		return new ReversedMapEntryHandle<>(this);
	}

	/**
	 * A {@link MapEntryHandle} that is reversed
	 * 
	 * @param <K> The key type of the entry
	 * @param <V> The value type of the entry
	 */
	class ReversedMapEntryHandle<K, V> extends ReversedCollectionElement<V> implements MapEntryHandle<K, V> {
		public ReversedMapEntryHandle(MapEntryHandle<K, V> wrapped) {
			super(wrapped);
		}

		@Override
		protected MapEntryHandle<K, V> getWrapped() {
			return (MapEntryHandle<K, V>) super.getWrapped();
		}

		@Override
		public K getKey() {
			return getWrapped().getKey();
		}

		@Override
		public MapEntryHandle<K, V> reverse() {
			return getWrapped();
		}
	}

	/**
	 * @param entry The entry to reverse
	 * @return The reversed entry, or null if entry was null
	 */
	static <K, V> MapEntryHandle<K, V> reverse(MapEntryHandle<K, V> entry) {
		return entry == null ? null : entry.reverse();
	}
}

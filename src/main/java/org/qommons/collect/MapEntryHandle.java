package org.qommons.collect;

import java.util.Map;

import org.qommons.collect.MutableCollectionElement.StdMsg;

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

	static <K, V> MapEntryHandle<K, V> reverse(MapEntryHandle<K, V> entry) {
		return entry == null ? null : entry.reverse();
	}
}

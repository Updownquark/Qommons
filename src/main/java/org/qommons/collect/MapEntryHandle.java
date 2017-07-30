package org.qommons.collect;

public interface MapEntryHandle<K, V> extends CollectionElement<V> {
	K getKey();

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
}

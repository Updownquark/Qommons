package org.qommons.collect;

/**
 * Represents a single key-value pair in a {@link BetterMultiMap}
 * 
 * @param <K> The type of the key
 * @param <V> The type of the value
 */
public interface MultiMapEntryHandle<K, V> extends MapEntryHandle<K, V> {
	ElementId getKeyId();

	@Override
	default MultiMapEntryHandle<K, V> reverse() {
		return new ReversedMultiMapEntryHandle<>(this);
	}

	class ReversedMultiMapEntryHandle<K, V> extends ReversedMapEntryHandle<K, V> implements MultiMapEntryHandle<K, V> {
		public ReversedMultiMapEntryHandle(MultiMapEntryHandle<K, V> wrapped) {
			super(wrapped);
		}

		@Override
		protected MultiMapEntryHandle<K, V> getWrapped() {
			return (MultiMapEntryHandle<K, V>) super.getWrapped();
		}

		@Override
		public ElementId getKeyId() {
			return getWrapped().getKeyId().reverse();
		}

		@Override
		public K getKey() {
			return getWrapped().getKey();
		}

		@Override
		public MultiMapEntryHandle<K, V> reverse() {
			return getWrapped();
		}
	}

	static <K, V> MultiMapEntryHandle<K, V> reverse(MultiMapEntryHandle<K, V> entry) {
		return entry == null ? null : entry.reverse();
	}
}

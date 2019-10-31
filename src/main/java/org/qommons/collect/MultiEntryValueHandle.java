package org.qommons.collect;

/**
 * Represents a single key-value pair in a {@link BetterMultiMap}
 * 
 * @param <K> The type of the key
 * @param <V> The type of the value
 */
public interface MultiEntryValueHandle<K, V> extends MapEntryHandle<K, V> {
	ElementId getKeyId();

	@Override
	default MultiEntryValueHandle<K, V> reverse() {
		return new ReversedMultiMapEntryHandle<>(this);
	}

	class ReversedMultiMapEntryHandle<K, V> extends ReversedMapEntryHandle<K, V> implements MultiEntryValueHandle<K, V> {
		public ReversedMultiMapEntryHandle(MultiEntryValueHandle<K, V> wrapped) {
			super(wrapped);
		}

		@Override
		protected MultiEntryValueHandle<K, V> getWrapped() {
			return (MultiEntryValueHandle<K, V>) super.getWrapped();
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
		public MultiEntryValueHandle<K, V> reverse() {
			return getWrapped();
		}
	}

	static <K, V> MultiEntryValueHandle<K, V> reverse(MultiEntryValueHandle<K, V> entry) {
		return entry == null ? null : entry.reverse();
	}
}

package org.qommons.collect;

/**
 * Represents a single key-value pair in a {@link BetterMultiMap}. The {@link #getElementId() element ID} may be used in the value
 * collection returned by {@link BetterMultiMap#get(Object) BetterMultiMap.getEntry}({@link #getKeyId()}).
 * 
 * @param <K> The type of the key
 * @param <V> The type of the value
 */
public interface MultiEntryValueHandle<K, V> extends MapEntryHandle<K, V> {
	/** @return The ID of the key element in the {@link BetterMultiMap#keySet() key set} */
	ElementId getKeyId();

	@Override
	default MultiEntryValueHandle<K, V> reverse() {
		return new ReversedMultiMapEntryHandle<>(this);
	}

	/**
	 * Implements {@link MultiEntryValueHandle#reverse()}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
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

	/**
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param entry The entry to reverse
	 * @return The reversed entry (or null if entry was null)
	 */
	static <K, V> MultiEntryValueHandle<K, V> reverse(MultiEntryValueHandle<K, V> entry) {
		return entry == null ? null : entry.reverse();
	}
}
